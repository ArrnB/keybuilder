package app.keybuilder.store

import android.content.Context
import android.content.SharedPreferences
import app.keybuilder.model.MAX_HEIGHT_PERCENT
import app.keybuilder.model.MIN_HEIGHT_PERCENT
import app.keybuilder.model.SavedGifs
import app.keybuilder.model.Shortcuts

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
    /** Play a sound when a key is touched. Which sound is part of each keyboard's style. */
    val sounds: Boolean = false,
    /** How loud key sounds are, 5 to 100. */
    val soundVolume: Int = 60,
    /** Play key sounds even while the phone is on silent or vibrate. */
    val soundsWhenSilent: Boolean = false,
    /** How tall the keyboard is, as a percentage of its normal height. Letters and icons scale with it. */
    val keyboardHeight: Int = 100,
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
    private const val SOUNDS = "key_sounds"
    private const val SOUND_VOLUME = "key_sound_volume"
    private const val SOUNDS_WHEN_SILENT = "key_sounds_when_silent"
    private const val KEYBOARD_HEIGHT = "keyboard_height_percent"
    private const val SHORTCUTS = "text_shortcuts"
    private const val SAVED_GIFS = "saved_gifs"
    private const val SAVED_STICKERS = "saved_stickers"
    private const val RECENT_SYMBOLS = "recent_symbols"

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
            sounds = p.getBoolean(SOUNDS, false),
            soundVolume = p.getInt(SOUND_VOLUME, 60).coerceIn(5, 100),
            soundsWhenSilent = p.getBoolean(SOUNDS_WHEN_SILENT, false),
            keyboardHeight = p.getInt(KEYBOARD_HEIGHT, 100).coerceIn(MIN_HEIGHT_PERCENT, MAX_HEIGHT_PERCENT),
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
            .putBoolean(SOUNDS, settings.sounds)
            .putInt(SOUND_VOLUME, settings.soundVolume.coerceIn(5, 100))
            .putBoolean(SOUNDS_WHEN_SILENT, settings.soundsWhenSilent)
            .putInt(KEYBOARD_HEIGHT, settings.keyboardHeight.coerceIn(MIN_HEIGHT_PERCENT, MAX_HEIGHT_PERCENT))
            .apply()
    }

    /** The emoji used most recently, newest first. */
    fun recentEmoji(context: Context): List<String> =
        prefs(context).getString(RECENT_EMOJI, "").orEmpty().split("\n").filter { it.isNotEmpty() }

    fun setRecentEmoji(context: Context, emoji: List<String>) {
        prefs(context).edit().putString(RECENT_EMOJI, emoji.joinToString("\n")).apply()
    }

    /** Your text shortcuts ("omw" becomes "on my way"). */
    fun shortcuts(context: Context): Shortcuts = Shortcuts.parse(prefs(context).getString(SHORTCUTS, "").orEmpty())

    fun setShortcuts(context: Context, shortcuts: Shortcuts) {
        prefs(context).edit().putString(SHORTCUTS, shortcuts.toJson()).apply()
    }

    /** The GIFs (or stickers) sent recently and marked as favourites. */
    fun savedGifs(context: Context, stickers: Boolean): SavedGifs =
        SavedGifs.parse(prefs(context).getString(if (stickers) SAVED_STICKERS else SAVED_GIFS, "").orEmpty())

    fun setSavedGifs(context: Context, stickers: Boolean, saved: SavedGifs) {
        prefs(context).edit().putString(if (stickers) SAVED_STICKERS else SAVED_GIFS, saved.toJson()).apply()
    }

    /** The symbols and faces used most recently in the symbols panel, newest first. */
    fun recentSymbols(context: Context): List<String> =
        prefs(context).getString(RECENT_SYMBOLS, "").orEmpty().split("\n").filter { it.isNotEmpty() }

    fun setRecentSymbols(context: Context, symbols: List<String>) {
        prefs(context).edit().putString(RECENT_SYMBOLS, symbols.joinToString("\n")).apply()
    }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
