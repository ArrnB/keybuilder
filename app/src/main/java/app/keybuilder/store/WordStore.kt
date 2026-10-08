package app.keybuilder.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.keybuilder.suggest.Lexicon
import app.keybuilder.suggest.SuggestionEngine
import app.keybuilder.suggest.UserWords
import java.io.File
import kotlin.concurrent.thread

/**
 * Holds the words behind suggestions and autocorrect: the built-in list (read once from
 * the app's assets) and what the keyboard has learned from you (one small file in the
 * app's private storage). Nothing here ever leaves the phone; there is no network code.
 *
 * The keyboard and the app's settings screen run in the same process, so they share this
 * one copy: "Forget learned words" in the app takes effect in the keyboard immediately.
 */
object WordStore {

    private const val TAG = "WordStore"
    private const val WORD_LIST = "words/words.txt"
    private const val LEARNED_FILE = "learned_words.txt"

    /** Null until the word list has been read, which takes a moment the first time. */
    @Volatile
    var engine: SuggestionEngine? = null
        private set

    private var loading = false
    private val mainThread = Handler(Looper.getMainLooper())

    /** Reads the words in the background. [onReady] is called on the main thread when they are in. */
    fun load(context: Context, onReady: () -> Unit = {}) {
        if (engine != null) {
            onReady()
            return
        }
        if (loading) return
        loading = true
        val app = context.applicationContext
        thread(name = "word-list") {
            val loaded = try {
                val lexicon = app.assets.open(WORD_LIST).bufferedReader().useLines { Lexicon.parse(it) }
                SuggestionEngine(lexicon, readLearned(app))
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the word list; suggestions stay off", e)
                null
            }
            mainThread.post {
                engine = loaded
                loading = false
                if (loaded != null) onReady()
            }
        }
    }

    /** Writes the learned words to their file if anything changed. Call from the main thread. */
    fun save(context: Context) {
        val user = engine?.user ?: return
        if (!user.changed) return
        // The text is put together here, on the thread that changes the words; only the slow
        // part, writing the file, happens in the background.
        val text = user.toText()
        user.saved()
        val app = context.applicationContext
        thread(name = "learned-words") {
            try {
                val part = File(app.filesDir, "$LEARNED_FILE.part")
                part.writeText(text)
                part.renameTo(File(app.filesDir, LEARNED_FILE))
            } catch (e: Exception) {
                Log.w(TAG, "Could not save the learned words", e)
            }
        }
    }

    /** How many words the keyboard has picked up from you. */
    fun learnedCount(context: Context): Int =
        engine?.user?.wordCount ?: readLearned(context.applicationContext).wordCount

    /** Forgets everything learned: deletes the file and empties the copy in memory. */
    fun forgetEverything(context: Context) {
        val app = context.applicationContext
        File(app.filesDir, LEARNED_FILE).delete()
        File(app.filesDir, "$LEARNED_FILE.part").delete()
        val current = engine ?: return
        engine = SuggestionEngine(current.lexicon, UserWords())
    }

    private fun readLearned(app: Context): UserWords {
        val file = File(app.filesDir, LEARNED_FILE)
        if (!file.exists()) return UserWords()
        return try {
            UserWords.parse(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the learned words", e)
            UserWords()
        }
    }
}
