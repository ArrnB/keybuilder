package app.keybuilder.store

import android.content.Context
import android.util.Log
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.ClipHistoryJson
import java.io.File

/**
 * Keeps the clipboard history on the phone: one list file plus one file per copied picture,
 * all in the app's private "clipboard" folder.
 */
object ClipboardStore {

    private const val TAG = "ClipboardStore"
    private const val LIST_FILE = "history.json"

    /** Files still being written end like this and are left alone by the tidy-up. */
    const val PART_SUFFIX = ".part"

    fun folder(context: Context): File = File(context.filesDir, "clipboard").apply { mkdirs() }

    fun load(context: Context): ClipHistory {
        val file = File(folder(context), LIST_FILE)
        if (!file.exists()) return ClipHistory()
        return try {
            ClipHistoryJson.parse(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the clipboard history", e)
            ClipHistory()
        }
    }

    /** Writes the list and deletes pictures that are no longer in it. */
    fun save(context: Context, history: ClipHistory) {
        val folder = folder(context)
        try {
            File(folder, LIST_FILE).writeText(ClipHistoryJson.toJson(history))
            val needed = history.imageFiles()
            for (file in folder.listFiles().orEmpty()) {
                val keep = file.name == LIST_FILE || file.name in needed || file.name.endsWith(PART_SUFFIX)
                if (!keep) file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the clipboard history", e)
        }
    }
}
