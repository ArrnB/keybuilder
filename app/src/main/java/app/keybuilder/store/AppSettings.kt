package app.keybuilder.store

import android.content.Context
import android.content.SharedPreferences

/** How typing feels. These apply to every keyboard. */
data class TypingSettings(
    /** A short buzz when a key is touched. Follows the phone's own "touch feedback" setting too. */
    val vibrate: Boolean = true,
    /** Show the letter enlarged above a key while it is pressed. */
    val popups: Boolean = true,
    /** Two spaces after a word type a full stop and a space. */
    val doubleSpace: Boolean = true,
    /** Show small symbols instead of words on the strip above the keys. */
    val stripIcons: Boolean = false,
)

/** Small settings shared by the app and the keyboard service. */
object AppSettings {

    private const val FILE = "keybuilder"
    private const val GIPHY_KEY = "giphy_key"
    private const val CLIPBOARD_HISTORY = "clipboard_history"
    private const val VIBRATE = "vibrate"
    private const val POPUPS = "key_popups"
    private const val DOUBLE_SPACE = "double_space"
    private const val RECENT_EMOJI = "recent_emoji"
    private const val STRIP_ICONS = "strip_icons"

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

    fun typing(context: Context): TypingSettings {
        val p = prefs(context)
        return TypingSettings(
            vibrate = p.getBoolean(VIBRATE, true),
            popups = p.getBoolean(POPUPS, true),
            doubleSpace = p.getBoolean(DOUBLE_SPACE, true),
            stripIcons = p.getBoolean(STRIP_ICONS, false),
        )
    }

    fun setTyping(context: Context, settings: TypingSettings) {
        prefs(context).edit()
            .putBoolean(VIBRATE, settings.vibrate)
            .putBoolean(POPUPS, settings.popups)
            .putBoolean(DOUBLE_SPACE, settings.doubleSpace)
            .putBoolean(STRIP_ICONS, settings.stripIcons)
            .apply()
    }

    /** The emoji used most recently, newest first. */
    fun recentEmoji(context: Context): List<String> =
        prefs(context).getString(RECENT_EMOJI, "").orEmpty().split("\n").filter { it.isNotEmpty() }

    fun setRecentEmoji(context: Context, emoji: List<String>) {
        prefs(context).edit().putString(RECENT_EMOJI, emoji.joinToString("\n")).apply()
    }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
