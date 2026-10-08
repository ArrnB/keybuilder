package app.keybuilder.model

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Where keyboards are kept.
 *
 * Every keyboard you own is one file in the app's private "keyboards" folder, named after the
 * keyboard's id. One of them is the active keyboard: the one the keyboard service shows and
 * the one the editors change. The app and the keyboard service both go through this object.
 */
object LayoutRepository {

    private const val TAG = "LayoutRepository"
    private const val DEFAULT_LAYOUT = "layouts/qwerty.json"
    private const val FOLDER = "keyboards"
    private const val SETTINGS = "keybuilder"
    private const val ACTIVE_KEY = "active_keyboard"

    /** Where phase 2 saved its single keyboard. Moved into the folder the first time we run. */
    private const val OLD_SINGLE_FILE = "active_layout.json"

    /** The keyboard shipped inside the app. */
    fun loadDefault(context: Context): KeyboardDef =
        context.assets.open(DEFAULT_LAYOUT).bufferedReader().use { LayoutJson.parse(it.readText()) }

    /** Every saved keyboard, sorted by name. Never empty: the first run saves the built-in one. */
    fun list(context: Context): List<KeyboardDef> {
        moveOldSingleFile(context)
        val found = keyboardFiles(context).mapNotNull { read(it) }
        if (found.isNotEmpty()) return found.sortedBy { it.name.lowercase() }
        val first = loadDefault(context)
        save(context, first)
        setActive(context, first.id)
        return listOf(first)
    }

    /** The id of the active keyboard, or null if none has been chosen yet. */
    fun activeId(context: Context): String? =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(ACTIVE_KEY, null)

    fun setActive(context: Context, id: String) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putString(ACTIVE_KEY, id).apply()
    }

    /**
     * The keyboard to type with. If the chosen one is missing or damaged, any other saved
     * keyboard is used, and failing that the built-in one, so the keyboard always appears.
     */
    fun loadActive(context: Context): KeyboardDef {
        moveOldSingleFile(context)
        val chosen = activeId(context)?.let { read(fileFor(context, it)) }
        if (chosen != null) return chosen
        return keyboardFiles(context).firstNotNullOfOrNull { read(it) } ?: loadDefault(context)
    }

    fun save(context: Context, def: KeyboardDef) {
        // Write to a second file first and then swap, so a crash can never leave half a file.
        val file = fileFor(context, def.id)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(LayoutJson.toJson(def))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    fun delete(context: Context, id: String) {
        fileFor(context, id).delete()
    }

    /**
     * Text that changes whenever the keyboard to type with changes: either another keyboard
     * was chosen or the chosen one was saved again. The keyboard service compares it to
     * know when to reload.
     */
    fun activeVersion(context: Context): String {
        val id = activeId(context) ?: return "none"
        val file = fileFor(context, id)
        return "$id:${file.lastModified()}:${file.length()}"
    }

    // ------------------------------------------------------------------ helpers

    private fun folder(context: Context): File = File(context.filesDir, FOLDER)

    /** Ids become file names, so anything that is not a plain letter, digit, - or _ is dropped. */
    private fun fileFor(context: Context, id: String): File {
        val safe = id.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
        return File(folder(context), safe.ifEmpty { "keyboard" } + ".json")
    }

    private fun keyboardFiles(context: Context): List<File> =
        (folder(context).listFiles() ?: emptyArray()).filter { it.name.endsWith(".json") }.sortedBy { it.name }

    /** Reads one file. The file name is the truth for the id, whatever the file says inside. */
    private fun read(file: File): KeyboardDef? {
        if (!file.exists()) return null
        return try {
            LayoutJson.parse(file.readText()).copy(id = file.name.removeSuffix(".json"))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read ${file.name}", e)
            null
        }
    }

    private fun moveOldSingleFile(context: Context) {
        val old = File(context.filesDir, OLD_SINGLE_FILE)
        if (!old.exists()) return
        try {
            if (keyboardFiles(context).isEmpty()) {
                val def = LayoutJson.parse(old.readText())
                save(context, def)
                setActive(context, fileFor(context, def.id).name.removeSuffix(".json"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not carry over the keyboard saved by the previous version", e)
        }
        old.delete()
    }
}
