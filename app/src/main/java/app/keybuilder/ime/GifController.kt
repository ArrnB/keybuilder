package app.keybuilder.ime

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keybuilder.model.GifItem
import app.keybuilder.model.Giphy
import app.keybuilder.net.httpDownload
import app.keybuilder.net.httpGetText
import app.keybuilder.store.AppSettings
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the GIF panel needs to know: the search words, the GIFs found, and whether
 * a search is running. It asks GIPHY in the background so the keyboard never freezes.
 * The addresses asked and how answers are read are in model/Giphy.kt.
 */
class GifController(private val context: Context, private val scope: CoroutineScope) {

    /** The search words. Empty means "what is popular right now". */
    var query by mutableStateOf("")

    /** True while the keys are being used to type search words instead of typing into the app. */
    var typing by mutableStateOf(false)

    var gifs by mutableStateOf<List<GifItem>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /** A line of explanation for the panel (an error, "nothing found"...), or null. */
    var message by mutableStateOf<String?>(null)

    var hasKey by mutableStateOf(false)
        private set

    /** True when GIPHY probably has more GIFs for these words than are shown. */
    var canLoadMore by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    private var loadedFor: String? = null
    private var job: Job? = null

    /** Called when the panel opens. Loads something if there is nothing to show yet. */
    fun open() {
        typing = false
        hasKey = AppSettings.giphyKey(context).isNotEmpty()
        if (hasKey && (gifs.isEmpty() || loadedFor != query)) search(query)
    }

    fun search(words: String) {
        query = words.trim()
        val key = AppSettings.giphyKey(context)
        hasKey = key.isNotEmpty()
        if (!hasKey) return
        val wanted = query
        val url = if (wanted.isEmpty()) Giphy.trendingUrl(key) else Giphy.searchUrl(key, wanted)

        job?.cancel() // a newer search replaces one still running
        loadingMore = false
        job = scope.launch {
            loading = true
            message = null
            val result = withContext(Dispatchers.IO) {
                try {
                    httpGetText(url)
                } catch (e: Exception) {
                    null
                }
            }
            loading = false
            when {
                result == null -> message = "No connection. Check the internet and try again."
                !result.ok -> message = Giphy.errorMessage(result.code)
                else -> {
                    gifs = Giphy.parse(result.body)
                    loadedFor = wanted
                    canLoadMore = gifs.size >= Giphy.PAGE_SIZE
                    if (gifs.isEmpty()) message = "Nothing found for \"$wanted\"."
                }
            }
        }
    }

    /** Fetches the next batch for the same words and adds it to the end of the list. */
    fun loadMore() {
        if (loading || loadingMore || !canLoadMore) return
        val key = AppSettings.giphyKey(context)
        if (key.isEmpty()) return
        val wanted = query
        val skip = gifs.size
        val url = if (wanted.isEmpty()) Giphy.trendingUrl(key, skip) else Giphy.searchUrl(key, wanted, skip)

        loadingMore = true
        job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    httpGetText(url)
                } catch (e: Exception) {
                    null
                }
            }
            loadingMore = false
            when {
                result == null -> message = "No connection. Check the internet and try again."
                !result.ok -> message = Giphy.errorMessage(result.code)
                else -> {
                    val more = Giphy.parse(result.body)
                    val known = gifs.map { it.id }.toSet()
                    gifs = gifs + more.filter { it.id !in known }
                    canLoadMore = more.size >= Giphy.PAGE_SIZE
                }
            }
        }
    }

    /** Downloads the GIF to send, or returns null if that fails. Reuses a copy from earlier. */
    suspend fun download(gif: GifItem): File? = withContext(Dispatchers.IO) {
        val safeId = gif.id.filter { it.isLetterOrDigit() }.ifEmpty { "gif" }
        val file = File(File(context.cacheDir, SHARED_FOLDER), "gif_$safeId.gif")
        try {
            if (!file.exists() || file.length() == 0L) httpDownload(gif.sendUrl, file, MAX_GIF_BYTES)
            file
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes downloaded GIFs older than a day, so the cache does not grow for ever. */
    fun tidyCache() {
        val folder = File(context.cacheDir, SHARED_FOLDER)
        thread(name = "gif-cache-tidy") {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            for (file in folder.listFiles().orEmpty()) {
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private companion object {
        const val SHARED_FOLDER = "shared" // must match res/xml/file_paths.xml
        const val MAX_GIF_BYTES = 8L * 1024 * 1024
    }
}
