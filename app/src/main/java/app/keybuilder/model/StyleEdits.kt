package app.keybuilder.model

import kotlin.math.roundToInt

/*
 * Everything the style screen can change, as small functions that take a keyboard
 * and return a changed copy. The screen itself only shows controls and calls these,
 * so the rules live here where they can be unit tested.
 */

/** Where a key sits: which layer, which row (from 0) and which place in the row (from 0). */
data class KeyPos(val layerId: String, val row: Int, val index: Int)

fun KeyboardDef.keyAt(pos: KeyPos): KeyDef? =
    layer(pos.layerId)?.rows?.getOrNull(pos.row)?.keys?.getOrNull(pos.index)

/** A copy of the keyboard with one key replaced. Unknown positions change nothing. */
fun KeyboardDef.withKey(pos: KeyPos, change: (KeyDef) -> KeyDef): KeyboardDef {
    if (keyAt(pos) == null) return this
    return copy(layers = layers.map { layer ->
        if (layer.id != pos.layerId) layer
        else layer.copy(rows = layer.rows.mapIndexed { rowIndex, row ->
            if (rowIndex != pos.row) row
            else row.copy(keys = row.keys.mapIndexed { keyIndex, key ->
                if (keyIndex != pos.index) key else change(key)
            })
        })
    })
}

/** Sets one key's own style. A style with nothing in it counts as "no style". */
fun KeyboardDef.withKeyStyle(pos: KeyPos, style: KeyStyle?): KeyboardDef =
    withKey(pos) { it.copy(style = style?.takeUnless { s -> s == KeyStyle() }) }

/** Removes every single-key style, so all keys follow the theme again. */
fun KeyboardDef.withoutKeyStyles(): KeyboardDef = copy(layers = layers.map { layer ->
    layer.copy(rows = layer.rows.map { row -> row.copy(keys = row.keys.map { it.copy(style = null) }) })
})

fun KeyboardDef.styledKeyCount(): Int =
    layers.sumOf { layer -> layer.rows.sumOf { row -> row.keys.count { it.style != null } } }

/** A short name for a key, for showing in the editor. */
fun KeyDef.describe(): String = when {
    label.isNotEmpty() -> label
    action == KeyAction.Shift -> "shift"
    action == KeyAction.Backspace -> "backspace"
    action == KeyAction.Enter -> "enter"
    action == KeyAction.Space -> "space"
    action is KeyAction.Arrow -> "arrow ${action.direction}"
    else -> "blank key"
}

/** The theme colour a key uses when it has no background of its own. */
fun Theme.backgroundFor(look: KeyLook): String = when (look) {
    KeyLook.ACCENT -> accentColor
    KeyLook.FUNCTION -> functionKeyColor
    else -> keyColor
}

fun Theme.textColorFor(look: KeyLook): String = if (look == KeyLook.ACCENT) accentTextColor else textColor

/** The colours of a theme, each with a readable name and a way to read and change it. */
enum class ThemeColor(val label: String) {
    BACKGROUND("Keyboard background"),
    KEY("Letter keys"),
    FUNCTION_KEY("Function keys"),
    ACCENT("Accent key"),
    PRESSED("Key while pressed"),
    TEXT("Letters and icons"),
    ACCENT_TEXT("Icon on accent key"),
    BORDER("Key border");

    fun get(theme: Theme): String = when (this) {
        BACKGROUND -> theme.background
        KEY -> theme.keyColor
        FUNCTION_KEY -> theme.functionKeyColor
        ACCENT -> theme.accentColor
        PRESSED -> theme.keyPressedColor
        TEXT -> theme.textColor
        ACCENT_TEXT -> theme.accentTextColor
        BORDER -> theme.borderColor
    }

    fun set(theme: Theme, hex: String): Theme = when (this) {
        BACKGROUND -> theme.copy(background = hex)
        KEY -> theme.copy(keyColor = hex)
        FUNCTION_KEY -> theme.copy(functionKeyColor = hex)
        ACCENT -> theme.copy(accentColor = hex)
        PRESSED -> theme.copy(keyPressedColor = hex)
        TEXT -> theme.copy(textColor = hex)
        ACCENT_TEXT -> theme.copy(accentTextColor = hex)
        BORDER -> theme.copy(borderColor = hex)
    }
}

/** The sizes of a theme, each with its slider range. */
enum class ThemeSize(val label: String, val min: Float, val max: Float, val step: Float) {
    CORNER("Corner roundness", 0f, 26f, 1f),
    BORDER("Border thickness", 0f, 4f, 0.5f),
    KEY_HEIGHT("Key height", 40f, 72f, 1f),
    GAP("Gap between keys", 0f, 12f, 1f),
    FONT("Letter size", 14f, 30f, 1f);

    fun get(theme: Theme): Float = when (this) {
        CORNER -> theme.cornerRadiusDp
        BORDER -> theme.borderWidthDp
        KEY_HEIGHT -> theme.keyHeightDp
        GAP -> theme.keyGapDp
        FONT -> theme.fontSizeSp
    }

    /** Rounds to the nearest step and keeps the value inside the range. */
    fun snap(value: Float): Float = ((value / step).roundToInt() * step).coerceIn(min, max)

    fun set(theme: Theme, value: Float): Theme {
        val v = snap(value)
        return when (this) {
            CORNER -> theme.copy(cornerRadiusDp = v)
            BORDER -> {
                // A border with an invisible colour would look like the slider is broken,
                // so the first time the border is switched on it gets a visible colour.
                val invisible = (parseHexColor(theme.borderColor) ?: 0) ushr 24 == 0
                if (v > 0f && invisible) {
                    val onDark = isDarkColor(parseHexColor(theme.background) ?: 0)
                    theme.copy(borderWidthDp = v, borderColor = if (onDark) "#66FFFFFF" else "#66000000")
                } else {
                    theme.copy(borderWidthDp = v)
                }
            }
            KEY_HEIGHT -> theme.copy(keyHeightDp = v)
            GAP -> theme.copy(keyGapDp = v)
            FONT -> theme.copy(fontSizeSp = v)
        }
    }
}

// ------------------------------------------------------------------ background

/** Opacity is shown and set as a percentage in steps of 5. */
fun opacityPercent(opacity: Float): Int = ((opacity.coerceIn(0f, 1f) * 20f).roundToInt() * 5)

fun Theme.withBackgroundOpacity(percent: Float): Theme = copy(backgroundOpacity = opacityPercent(percent / 100f) / 100f)

fun Theme.withKeyOpacity(percent: Float): Theme = copy(keyOpacity = opacityPercent(percent / 100f) / 100f)

/**
 * Sets the blur in whole steps. Blur only shows through a see-through background, so turning
 * it on while the background is solid also makes the background see-through.
 */
fun Theme.withBackgroundBlur(value: Float): Theme {
    val blur = value.roundToInt().toFloat().coerceIn(0f, MAX_BLUR)
    return copy(backgroundBlur = blur, backgroundOpacity = if (blur > 0f && backgroundOpacity >= 1f) 0.6f else backgroundOpacity)
}

/**
 * Turns the two-colour background on or off. Switching it on picks a second colour that is
 * sure to be visible: the accent colour, or a shifted background if those two are the same.
 */
fun Theme.withGradient(on: Boolean): Theme = when {
    !on -> copy(backgroundEnd = null)
    backgroundEnd != null -> this
    else -> {
        val start = parseHexColor(background) ?: 0
        val accent = parseHexColor(accentColor) ?: 0
        val end = if ((accent and 0xFFFFFF) != (start and 0xFFFFFF)) solid(accent) else {
            val hsv = argbToHsv(start)
            hsvToArgb(hsv.h + 40f, maxOf(hsv.s, 0.4f), if (hsv.v < 0.5f) hsv.v + 0.3f else hsv.v - 0.3f)
        }
        copy(backgroundEnd = formatHexColor(end))
    }
}

fun gradientDirectionName(direction: String): String = when (direction) {
    "across" -> "Side to side"
    "diagonal" -> "Corner to corner"
    else -> "Top to bottom"
}

/** 8.0 becomes "8", 1.5 stays "1.5". */
fun formatNumber(value: Float): String =
    if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else value.toString()

// ------------------------------------------------------------------ ready-made looks

data class ThemePreset(val name: String, val theme: Theme)

/**
 * Takes the colours, background and key shape from [look] but keeps this theme's sizes and
 * letters, so choosing a look never undoes the key height you settled on.
 */
fun Theme.withLook(look: Theme): Theme = copy(
    backgroundEnd = look.backgroundEnd,
    gradientDirection = look.gradientDirection,
    backgroundOpacity = look.backgroundOpacity,
    keyOpacity = look.keyOpacity,
    backgroundBlur = look.backgroundBlur,
    colorSource = "own",
    background = look.background,
    keyColor = look.keyColor,
    functionKeyColor = look.functionKeyColor,
    accentColor = look.accentColor,
    keyPressedColor = look.keyPressedColor,
    textColor = look.textColor,
    accentTextColor = look.accentTextColor,
    borderColor = look.borderColor,
    borderWidthDp = look.borderWidthDp,
    cornerRadiusDp = look.cornerRadiusDp,
)

object ThemePresets {
    val all: List<ThemePreset> = listOf(
        ThemePreset("Midnight", Theme()),
        ThemePreset(
            "Paper",
            Theme(
                background = "#E8EAED", keyColor = "#FFFFFF", functionKeyColor = "#CDD1D8",
                accentColor = "#1A73E8", keyPressedColor = "#B8BDC6", textColor = "#1F1F1F",
                accentTextColor = "#FFFFFF",
            ),
        ),
        ThemePreset(
            "Black",
            Theme(
                background = "#000000", keyColor = "#1C1C1E", functionKeyColor = "#111113",
                accentColor = "#0A84FF", keyPressedColor = "#3A3A3C", textColor = "#FFFFFF",
                accentTextColor = "#FFFFFF",
            ),
        ),
        ThemePreset(
            "Ocean",
            Theme(
                background = "#0B2233", keyColor = "#14405C", functionKeyColor = "#0F3147",
                accentColor = "#2EC4B6", keyPressedColor = "#1F5E86", textColor = "#E6F4F1",
                accentTextColor = "#06202B", cornerRadiusDp = 10f,
            ),
        ),
        ThemePreset(
            "Forest",
            Theme(
                background = "#16241B", keyColor = "#24402E", functionKeyColor = "#1B3023",
                accentColor = "#8BC34A", keyPressedColor = "#36604A", textColor = "#EAF4E4",
                accentTextColor = "#10200F", cornerRadiusDp = 6f,
            ),
        ),
        ThemePreset(
            "Sunset",
            Theme(
                background = "#2B1B2E", keyColor = "#4A2C4F", functionKeyColor = "#38213B",
                accentColor = "#FF7A59", keyPressedColor = "#6A3F70", textColor = "#FFEFE6",
                accentTextColor = "#2B1B2E", cornerRadiusDp = 12f,
            ),
        ),
        ThemePreset(
            "Outline",
            Theme(
                background = "#101114", keyColor = "#00000000", functionKeyColor = "#00000000",
                accentColor = "#FFFFFF", keyPressedColor = "#33FFFFFF", textColor = "#FFFFFF",
                accentTextColor = "#101114", borderColor = "#80FFFFFF", borderWidthDp = 1.5f,
                cornerRadiusDp = 14f,
            ),
        ),
        ThemePreset(
            "Bubble",
            Theme(
                background = "#F3E8FF", keyColor = "#FFFFFF", functionKeyColor = "#E3D0FB",
                accentColor = "#7C3AED", keyPressedColor = "#D4B8F5", textColor = "#2E1A47",
                accentTextColor = "#FFFFFF", cornerRadiusDp = 22f,
            ),
        ),
        ThemePreset(
            "Dusk",
            Theme(
                background = "#2B1E5C", backgroundEnd = "#C2416B", gradientDirection = "down",
                keyColor = "#FFFFFF", functionKeyColor = "#000000", keyOpacity = 0.2f,
                accentColor = "#FFD166", keyPressedColor = "#66FFFFFF", textColor = "#FFFFFF",
                accentTextColor = "#2B1E5C", cornerRadiusDp = 12f,
            ),
        ),
        ThemePreset(
            "Aurora",
            Theme(
                background = "#0F2027", backgroundEnd = "#2C7A6B", gradientDirection = "diagonal",
                keyColor = "#FFFFFF", functionKeyColor = "#000000", keyOpacity = 0.15f,
                accentColor = "#7CF5C4", keyPressedColor = "#55FFFFFF", textColor = "#F1FFFA",
                accentTextColor = "#0F2027", cornerRadiusDp = 10f,
            ),
        ),
        ThemePreset(
            "Frost",
            Theme(
                background = "#1A1C22", backgroundOpacity = 0.5f, backgroundBlur = 28f,
                keyColor = "#FFFFFF", functionKeyColor = "#FFFFFF", keyOpacity = 0.14f,
                accentColor = "#FFFFFF", keyPressedColor = "#66FFFFFF", textColor = "#FFFFFF",
                accentTextColor = "#1A1C22", cornerRadiusDp = 12f,
            ),
        ),
        ThemePreset(
            "Glass",
            Theme(
                background = "#101418", backgroundOpacity = 0.45f,
                keyColor = "#FFFFFF", functionKeyColor = "#FFFFFF", keyOpacity = 0.15f,
                accentColor = "#FFFFFF", keyPressedColor = "#66FFFFFF", textColor = "#FFFFFF",
                accentTextColor = "#101418", borderColor = "#40FFFFFF", borderWidthDp = 1f,
                cornerRadiusDp = 12f,
            ),
        ),
    )
}
