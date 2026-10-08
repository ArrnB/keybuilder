package app.keybuilder.store

import android.content.Context
import android.content.SharedPreferences

/** How typing feels. These apply to every keyboard. */
data class TypingSettings(
    /** A short buzz when a key is touched. */
    val vibrate: Boolean = true,
    /** How strong that buzz is, 5 to 100. */
    val vibrateStrength: Int = 50,
    /** Show the letter enlarged above a key while it is pressed. */
    val popups: Boolean = true,
    /** Two spaces after a word type a full stop and a space. */
    val doubleSpace: Boolean = true,
    /** Show small symbols instead of words on the strip above the keys. */
    val stripIcons: Boolean = false,
    /** Show word suggestions on the strip while typing. */
    val suggestions: Boolean = true,
    /** Fix slips automatically when a word is finished. */
    val autocorrect: Boolean = true,
    /** Remember the words you type, on this phone only, to suggest them later. Never in password boxes. */
    val learn: Boolean = true,
    /** Show the most recently copied thing on the strip, next to the gear, for one-tap pasting. */
    val quickClip: Boolean = true,
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
    private const val SUGGESTIONS = "suggestions"
    private const val AUTOCORRECT = "autocorrect"
    private const val LEARN = "learn_words"
    private const val QUICK_CLIP = "quick_clip"
    private const val VIBRATE_STRENGTH = "vibrate_strength"
    private const val SCREENSHOTS = "clipboard_screenshots"
    private const val LAST_SCREENSHOT = "last_screenshot_seconds"

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

    /**
     * Whether new screenshots are added to the clipboard history. Off until switched on,
     * because it needs permission to see the phone's pictures.
     */
    fun screenshotsEnabled(context: Context): Boolean = prefs(context).getBoolean(SCREENSHOTS, false)

    /** Switching it on starts from now: screenshots taken earlier are not pulled in. */
    fun setScreenshotsEnabled(context: Context, on: Boolean) {
        val edit = prefs(context).edit().putBoolean(SCREENSHOTS, on)
        if (on) edit.putLong(LAST_SCREENSHOT, System.currentTimeMillis() / 1000)
        edit.apply()
    }

    /** When the newest screenshot already added was taken, in seconds since 1970. */
    fun lastScreenshot(context: Context): Long = prefs(context).getLong(LAST_SCREENSHOT, System.currentTimeMillis() / 1000)

    fun setLastScreenshot(context: Context, seconds: Long) {
        prefs(context).edit().putLong(LAST_SCREENSHOT, seconds).apply()
    }

    fun typing(context: Context): TypingSettings {
        val p = prefs(context)
        return TypingSettings(
            vibrate = p.getBoolean(VIBRATE, true),
            vibrateStrength = p.getInt(VIBRATE_STRENGTH, 50).coerceIn(5, 100),
            popups = p.getBoolean(POPUPS, true),
            doubleSpace = p.getBoolean(DOUBLE_SPACE, true),
            stripIcons = p.getBoolean(STRIP_ICONS, false),
            suggestions = p.getBoolean(SUGGESTIONS, true),
            autocorrect = p.getBoolean(AUTOCORRECT, true),
            learn = p.getBoolean(LEARN, true),
            quickClip = p.getBoolean(QUICK_CLIP, true),
        )
    }

    fun setTyping(context: Context, settings: TypingSettings) {
        prefs(context).edit()
            .putBoolean(VIBRATE, settings.vibrate)
            .putInt(VIBRATE_STRENGTH, settings.vibrateStrength.coerceIn(5, 100))
            .putBoolean(POPUPS, settings.popups)
            .putBoolean(DOUBLE_SPACE, settings.doubleSpace)
            .putBoolean(STRIP_ICONS, settings.stripIcons)
            .putBoolean(SUGGESTIONS, settings.suggestions)
            .putBoolean(AUTOCORRECT, settings.autocorrect)
            .putBoolean(LEARN, settings.learn)
            .putBoolean(QUICK_CLIP, settings.quickClip)
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
