package app.keybuilder.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import app.keybuilder.model.FONT_FILE_PREFIX
import app.keybuilder.model.fontFileName
import app.keybuilder.model.safeFontFileName
import app.keybuilder.model.systemFontName
import java.io.File

/**
 * Turns a theme's font name into a font that can be drawn with.
 *
 * Built-in styles ("condensed", "casual"...) are the font families every Android phone
 * carries; a phone that lacks one answers with its normal font. Your own fonts are .ttf or
 * .otf files copied into the app's private "fonts" folder by the style screen.
 */
object Fonts {

    private const val TAG = "Fonts"
    private const val MAX_FONT_BYTES = 12L * 1024 * 1024

    /** Loading a font file takes a moment, so each is loaded once. */
    private val loaded = HashMap<String, FontFamily>()

    fun folder(context: Context): File = File(context.applicationContext.filesDir, "fonts").apply { mkdirs() }

    fun family(context: Context, fontFamily: String): FontFamily = synchronized(loaded) {
        loaded.getOrPut(fontFamily) { load(context, fontFamily) }
    }

    private fun load(context: Context, fontFamily: String): FontFamily {
        return try {
            val fileName = fontFileName(fontFamily)
            when {
                fileName != null -> {
                    val file = File(folder(context), fileName)
                    val typeface = if (file.exists()) Typeface.Builder(file).build() else null
                    if (typeface != null) FontFamily(typeface) else FontFamily.SansSerif
                }
                fontFamily == "sans" -> FontFamily.SansSerif
                fontFamily == "serif" -> FontFamily.Serif
                fontFamily == "mono" -> FontFamily.Monospace
                else -> FontFamily(Typeface.create(systemFontName(fontFamily), Typeface.NORMAL))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load the font $fontFamily", e)
            FontFamily.SansSerif
        }
    }

    /** The fonts you have added, as theme font names ("file:Lobster.ttf"), in alphabetical order. */
    fun added(context: Context): List<String> =
        folder(context).listFiles().orEmpty()
            .map { FONT_FILE_PREFIX + it.name }
            .filter { fontFileName(it) != null }
            .sortedBy { it.lowercase() }

    /**
     * Copies the font file the user picked into the app and returns its theme font name,
     * or null if it could not be read or is not a font.
     */
    fun add(context: Context, picked: Uri): String? {
        val resolver = context.contentResolver
        return try {
            var name: String? = null
            resolver.query(picked, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0)
            }
            val target = File(folder(context), safeFontFileName(name ?: picked.lastPathSegment ?: "font.ttf"))
            val input = resolver.openInputStream(picked) ?: return null
            input.use { source -> target.outputStream().use { out -> source.copyTo(out) } }
            val usable = target.length() in 1..MAX_FONT_BYTES && Typeface.Builder(target).build() != null
            if (!usable) {
                target.delete()
                return null
            }
            val id = FONT_FILE_PREFIX + target.name
            synchronized(loaded) { loaded.remove(id) } // a file added again under the same name replaces the old one
            id
        } catch (e: Exception) {
            Log.w(TAG, "Could not add the font", e)
            null
        }
    }

    fun delete(context: Context, fontFamily: String) {
        val fileName = fontFileName(fontFamily) ?: return
        File(folder(context), fileName).delete()
        synchronized(loaded) { loaded.remove(fontFamily) }
    }
}
