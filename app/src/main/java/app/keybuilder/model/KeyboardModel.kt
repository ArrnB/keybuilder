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

    /** Does nothing. */
    data object None : KeyAction
}

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
)

/** The letter styles a theme can use, in the order they are offered. */
val FONT_FAMILIES = listOf("sans", "serif", "mono")

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
