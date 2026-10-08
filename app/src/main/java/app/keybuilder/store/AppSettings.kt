package app.keybuilder.store

import android.content.Context
import android.content.SharedPreferences

/** Small settings shared by the app and the keyboard service. */
object AppSettings {

    private const val FILE = "keybuilder"
    private const val GIPHY_KEY = "giphy_key"
    private const val CLIPBOARD_HISTORY = "clipboard_history"

    /** The user's own GIPHY API key, or empty if none has been entered. */
    fun giphyKey(context: Context): String = prefs(context).getString(GIPHY_KEY, "").orEmpty().trim()

    fun setGiphyKey(context: Context, key: String) {
        prefs(context).edit().putString(GIPHY_KEY, key.trim()).apply()
    }

    /** Off until the user turns it on: a clipboard history remembers everything that is copied. */
    fun clipboardEnabled(context: Context): Boolean = prefs(context).getBoolean(CLIPBOARD_HISTORY, false)

    fun setClipboardEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(CLIPBOARD_HISTORY, on).apply()
    }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
