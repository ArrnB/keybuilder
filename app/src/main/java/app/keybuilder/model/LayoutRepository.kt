package app.keybuilder.model

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Where layouts come from and go to.
 *
 * There is one "active" keyboard. Until you change anything it is the layout shipped inside
 * the app. The first change saves a copy in the app's private storage, and from then on that
 * copy is used. The keyboard service and the app both read it from here.
 */
object LayoutRepository {

    private const val TAG = "LayoutRepository"
    private const val DEFAULT_LAYOUT = "layouts/qwerty.json"
    private const val ACTIVE_FILE = "active_layout.json"

    fun loadDefault(context: Context): KeyboardDef =
        context.assets.open(DEFAULT_LAYOUT).bufferedReader().use { LayoutJson.parse(it.readText()) }

    /** The saved keyboard, or the built-in one if nothing is saved or the saved file is damaged. */
    fun loadActive(context: Context): KeyboardDef {
        val file = activeFile(context)
        if (file.exists()) {
            try {
                return LayoutJson.parse(file.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Saved layout could not be read, using the built-in one", e)
            }
        }
        return loadDefault(context)
    }

    fun saveActive(context: Context, def: KeyboardDef) {
        // Write to a second file first and then swap, so a crash can never leave half a file.
        val file = activeFile(context)
        val temp = File(file.parentFile, "$ACTIVE_FILE.tmp")
        temp.writeText(LayoutJson.toJson(def))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    /**
     * A number that changes whenever the saved keyboard changes.
     * The keyboard service compares it to know when to reload.
     */
    fun activeVersion(context: Context): Long = activeFile(context).lastModified()

    private fun activeFile(context: Context): File = File(context.filesDir, ACTIVE_FILE)
}
