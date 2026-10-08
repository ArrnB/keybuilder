package app.keybuilder.model

/*
 * The whole keyboard is described by these classes and nothing else.
 * A layout file (JSON) is turned into a KeyboardDef, the renderer draws it,
 * and the editor (later phases) will change it and save it back.
 *
 * No Android imports here on purpose, so this can be unit tested on a laptop.
 */

/** One complete keyboard: its look plus every layer of keys. */
data class KeyboardDef(
    val id: String,
    val name: String,
    /** The layer shown when the keyboard opens. */
    val startLayer: String,
    val theme: Theme = Theme(),
    val layers: List<Layer>,
    /** Show the strip of shortcuts (clipboard, GIF, emoji) above the keys. */
    val toolbar: Boolean = true,
    /** The layer to open in boxes that only take numbers (phone, amount, PIN), or null for the start layer. */
    val numberLayer: String? = null,
    /**
     * Give keys without long-press choices of their own some sensible ones: accents on
     * letters, relatives on punctuation, and 1 to 0 on a top row of letters.
     */
    val builtInChoices: Boolean = true,
) {
    fun layer(id: String): Layer? = layers.firstOrNull { it.id == id }
}

/** One screen of keys, for example "letters" or "symbols". */
data class Layer(
    val id: String,
    val rows: List<Row>,
)

data class Row(
    val keys: List<KeyDef>,
    /** Relative height. 1 = the theme's normal key height. */
    val height: Float = 1f,
)

data class KeyDef(
    /** Text drawn on the key. Leave empty to get an icon for shift, backspace and enter. */
    val label: String = "",
    /** Text used while shift is on. Defaults to the label in capitals. */
    val shiftLabel: String? = null,
    val action: KeyAction = KeyAction.TypeLabel,
    /** Relative width. A row shares its space between keys according to this. */
    val width: Float = 1f,
    val look: KeyLook = KeyLook.NORMAL,
    /** Optional per-key overrides of the theme. */
    val style: KeyStyle? = null,
    /** Extra characters offered when the key is held down. The first one is shown as a small hint. */
    val alternates: List<String> = emptyList(),
)

/** What happens when a key is tapped. */
sealed interface KeyAction {
    /** Types whatever is written on the key (follows shift). This is the default. */
    data object TypeLabel : KeyAction

    /** Types a fixed piece of text, however long. Shift does not change it. */
    data class Text(val text: String) : KeyAction

    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction

    /** Shows another layer. */
    data class SwitchLayer(val layer: String) : KeyAction

    /** Opens one of the PANELS in place of the keys. */
    data class OpenPanel(val panel: String) : KeyAction

    /** Moves the cursor one step in one of the ARROWS directions. Repeats while held. */
    data class Arrow(val direction: String) : KeyAction

    /** Runs one of the COMMANDS (copy, paste...) on the text box. */
    data class Command(val command: String) : KeyAction

    /** Does nothing. */
    data object None : KeyAction
}

/**
 * The built-in panels. All but the last are the buttons on the left of the shortcut strip;
 * "settings" is the gear at its right end.
 */
val PANELS = listOf("clipboard", "gif", "sticker", "emoji", "symbols", "edit", "settings")

fun panelName(id: String): String = when (id) {
    "clipboard" -> "Clipboard"
    "gif" -> "GIF"
    "sticker" -> "Stickers"
    "emoji" -> "Emoji"
    "symbols" -> "Symbols"
    "edit" -> "Edit"
    "settings" -> "Settings"
    else -> id
}

val ARROWS = listOf("left", "right", "up", "down")

val COMMANDS = listOf("copy", "paste", "cut", "selectAll")

fun commandName(id: String): String = when (id) {
    "copy" -> "Copy"
    "paste" -> "Paste"
    "cut" -> "Cut"
    "selectAll" -> "Select all"
    else -> id
}

/** How many long-press choices a key can have. */
const val MAX_ALTERNATES = 8

/** Height of the shortcut strip in dp. */
const val TOOLBAR_HEIGHT_DP = 42f

/** Which of the theme's colour groups a key uses. */
enum class KeyLook(val jsonName: String) {
    NORMAL("normal"),
    FUNCTION("function"),
    ACCENT("accent"),

    /** An invisible gap that only takes up space. */
    SPACER("spacer"),
}

data class KeyStyle(
    val background: String? = null,
    val textColor: String? = null,
    val fontSizeSp: Float? = null,
    /** This key's own sound, one of KEY_SOUNDS ("none" silences just this key). Null = the theme's. */
    val sound: String? = null,
    /** This key's own press animation, one of KEY_ANIMATIONS. Null = the theme's. */
    val animation: String? = null,
)

/** Colours are "#RRGGBB" or "#AARRGGBB". Sizes are in dp, text in sp. */
data class Theme(
    val background: String = "#1B1D22",
    val keyColor: String = "#343842",
    val functionKeyColor: String = "#262930",
    val accentColor: String = "#4C8DFF",
    val keyPressedColor: String = "#565C6B",
    val textColor: String = "#FFFFFF",
    val accentTextColor: String = "#FFFFFF",
    val borderColor: String = "#00000000",
    val borderWidthDp: Float = 0f,
    val cornerRadiusDp: Float = 8f,
    val keyHeightDp: Float = 52f,
    val keyGapDp: Float = 6f,
    val fontSizeSp: Float = 21f,
    /** One of FONT_FAMILIES. */
    val fontFamily: String = "sans",
    val bold: Boolean = false,
    /** Show a key's first long-press choice as a small character in its corner. */
    val hints: Boolean = true,
    /** A second background colour. With one, the background fades from [background] into it. */
    val backgroundEnd: String? = null,
    /** One of GRADIENT_DIRECTIONS: which way that fade runs. */
    val gradientDirection: String = "down",
    /** 0..1: how solid the background is. Below 1, whatever the app has behind the keyboard shows through. */
    val backgroundOpacity: Float = 1f,
    /** 0..1: how solid the keys are. */
    val keyOpacity: Float = 1f,
    /**
     * How strongly whatever is behind a see-through keyboard is blurred, 0 (not at all) to
     * MAX_BLUR. Only does anything while [backgroundOpacity] is below 1, and only on phones
     * that allow one window to blur another (Android 12 and later, and not all of those).
     */
    val backgroundBlur: Float = 0f,
    /** One of COLOR_SOURCES: use the colours above, or take them from the phone. */
    val colorSource: String = "own",
    /** The sound keys make, one of KEY_SOUNDS. Only heard while key sounds are switched on in Settings. */
    val sound: String = "click",
    /** How keys move when pressed, one of KEY_ANIMATIONS. */
    val animation: String = "none",
)

/**
 * The letter styles that come with every phone, in the order they are offered. A theme can
 * also use a font file you added yourself: its fontFamily is then FONT_FILE_PREFIX + the file's name.
 */
val FONT_FAMILIES = listOf(
    "sans", "serif", "mono", "condensed", "light", "medium", "black", "thin", "casual", "cursive", "smallcaps", "serif-mono",
)

const val FONT_FILE_PREFIX = "file:"

/** True for anything a theme's fontFamily may be: a built-in style or one of your own font files. */
fun isFontFamily(value: String): Boolean =
    value in FONT_FAMILIES || (value.startsWith(FONT_FILE_PREFIX) && fontFileName(value) != null)

/** The file name behind a "file:..." font, or null if this is a built-in style (or not a safe name). */
fun fontFileName(fontFamily: String): String? {
    if (!fontFamily.startsWith(FONT_FILE_PREFIX)) return null
    val name = fontFamily.removePrefix(FONT_FILE_PREFIX)
    val safe = name.isNotEmpty() && name.length <= 80 && !name.startsWith(".") &&
        name.all { it.isLetterOrDigit() || it in "._- " }
    return name.takeIf { safe }
}

/**
 * Turns the name of a font file someone picked into a name that is safe to save under:
 * only letters, digits, dots, dashes, underscores and spaces, ending in .ttf or .otf.
 */
fun safeFontFileName(picked: String): String {
    val base = picked.substringAfterLast('/').substringAfterLast('\\').trim()
    val cleaned = base.map { if (it.isLetterOrDigit() || it in "._- ") it else '_' }.joinToString("").trimStart('.').take(70)
    val lower = cleaned.lowercase()
    return when {
        cleaned.isEmpty() -> "font.ttf"
        lower.endsWith(".ttf") || lower.endsWith(".otf") -> cleaned
        else -> "$cleaned.ttf"
    }
}

/** What a font is called in the style screen. */
fun fontName(fontFamily: String): String = when (fontFamily) {
    "sans" -> "Plain"
    "serif" -> "Serif"
    "mono" -> "Typewriter"
    "condensed" -> "Narrow"
    "light" -> "Light"
    "medium" -> "Medium"
    "black" -> "Heavy"
    "thin" -> "Thin"
    "casual" -> "Casual"
    "cursive" -> "Handwriting"
    "smallcaps" -> "Small caps"
    "serif-mono" -> "Serif typewriter"
    else -> fontFileName(fontFamily)?.substringBeforeLast('.') ?: "Plain"
}

/** The name Android knows a built-in letter style by. Phones that lack one fall back to their normal font. */
fun systemFontName(fontFamily: String): String = when (fontFamily) {
    "serif" -> "serif"
    "mono" -> "monospace"
    "condensed" -> "sans-serif-condensed"
    "light" -> "sans-serif-light"
    "medium" -> "sans-serif-medium"
    "black" -> "sans-serif-black"
    "thin" -> "sans-serif-thin"
    "casual" -> "casual"
    "cursive" -> "cursive"
    "smallcaps" -> "sans-serif-smallcaps"
    "serif-mono" -> "serif-monospace"
    else -> "sans-serif"
}

/** True for fonts whose letters are all the same width, which matters when fitting text into a space. */
fun isFixedWidthFont(fontFamily: String): Boolean = fontFamily == "mono" || fontFamily == "serif-mono"

// ------------------------------------------------------------------ sounds and movement

/** "none", the sounds that come with the app (res/raw/key_*.wav), and "phone" for Android's own key click. */
val KEY_SOUNDS = listOf("none", "click", "pop", "tick", "thock", "drop", "beep", "typewriter", "phone")

fun soundName(id: String): String = when (id) {
    "none" -> "Silent"
    "click" -> "Click"
    "pop" -> "Pop"
    "tick" -> "Tick"
    "thock" -> "Thock"
    "drop" -> "Drop"
    "beep" -> "Beep"
    "typewriter" -> "Typewriter"
    "phone" -> "Phone's own"
    else -> id
}

val KEY_ANIMATIONS = listOf("none", "sink", "pop", "bounce", "glow", "tilt", "fade")

fun animationName(id: String): String = when (id) {
    "none" -> "None"
    "sink" -> "Sink"
    "pop" -> "Pop"
    "bounce" -> "Bounce"
    "glow" -> "Glow"
    "tilt" -> "Tilt"
    "fade" -> "Fade"
    else -> id
}

/**
 * What a press animation does to a key, in numbers the drawing code can follow blindly.
 * [scale] and [tiltDegrees] are where the key goes while pressed (1 and 0 mean it stays put).
 */
data class KeyMotion(
    val scale: Float = 1f,
    val tiltDegrees: Float = 0f,
    /** Springs back past its resting size before settling. */
    val bouncy: Boolean = false,
    /** A ring in the accent colour that fades after the key is let go. */
    val glow: Boolean = false,
    /** The pressed colour lingers and fades out instead of vanishing at once. */
    val lingers: Boolean = false,
) {
    val moves: Boolean get() = scale != 1f || tiltDegrees != 0f
}

fun motionFor(animation: String): KeyMotion = when (animation) {
    "sink" -> KeyMotion(scale = 0.88f)
    "pop" -> KeyMotion(scale = 1.14f)
    "bounce" -> KeyMotion(scale = 0.82f, bouncy = true)
    "glow" -> KeyMotion(glow = true, lingers = true)
    "tilt" -> KeyMotion(scale = 0.95f, tiltDegrees = -7f)
    "fade" -> KeyMotion(lingers = true)
    else -> KeyMotion()
}

/** The sound and the animation [key] really uses: its own if it has one, otherwise the theme's. */
fun Theme.soundFor(key: KeyDef): String = key.style?.sound ?: sound

fun Theme.animationFor(key: KeyDef): String = key.style?.animation ?: animation

const val MAX_BLUR = 40f

/** The blur that should actually be applied: none unless the background lets something show through. */
fun Theme.blurInUse(): Float = if (backgroundOpacity < 1f) backgroundBlur.coerceIn(0f, MAX_BLUR) else 0f

/** Which way a two-colour background fades: top to bottom, side to side, or corner to corner. */
val GRADIENT_DIRECTIONS = listOf("down", "across", "diagonal")

/**
 * Where a theme's colours come from. "own" = the colours saved in the theme. "phone" = the
 * phone's own colours (taken from the wallpaper on Android 12 and later), light or dark to
 * match the phone. The saved colours are kept for switching back.
 */
val COLOR_SOURCES = listOf("own", "phone")

/**
 * Turns "#RRGGBB" or "#AARRGGBB" into an ARGB number, or null if the text is not a colour.
 */
fun parseHexColor(text: String): Int? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("#")) return null
    val hex = trimmed.substring(1)
    if (hex.isEmpty() || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return when (hex.length) {
        6 -> (0xFF000000L or hex.toLong(16)).toInt()
        8 -> hex.toLong(16).toInt()
        else -> null
    }
}
