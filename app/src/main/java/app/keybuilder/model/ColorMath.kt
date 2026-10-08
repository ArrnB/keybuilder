package app.keybuilder.model

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Colour arithmetic for the colour picker. No Android code, so it is unit tested.
 * A colour is an ARGB number: opacity, red, green, blue, each 0..255.
 */

/** Hue 0..360 (position on the colour wheel), saturation 0..1 (grey to vivid), value 0..1 (dark to bright). */
data class Hsv(val h: Float, val s: Float, val v: Float)

fun argbToHsv(argb: Int): Hsv {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    val saturation = if (max == 0f) 0f else delta / max
    return Hsv(hue, saturation, max)
}

/** [alpha] is 0..1, where 0 is fully see-through. */
fun hsvToArgb(h: Float, s: Float, v: Float, alpha: Float = 1f): Int {
    val hue = h.mod(360f)
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val x = c * (1f - abs((hue / 60f).mod(2f) - 1f))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    return (a shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

/** "#RRGGBB" for solid colours, "#AARRGGBB" when partly see-through. */
fun formatHexColor(argb: Int): String {
    val alpha = (argb ushr 24) and 0xFF
    val rgb = argb and 0xFFFFFF
    return if (alpha == 0xFF) "#%06X".format(rgb) else "#%02X%06X".format(alpha, rgb)
}

fun colorAlpha(argb: Int): Float = ((argb ushr 24) and 0xFF) / 255f

/** The same colour, [opacity] (0..1) times as solid as it was. A colour that was already see-through gets more so. */
fun withOpacity(argb: Int, opacity: Float): Int {
    val alpha = (((argb ushr 24) and 0xFF) * opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (argb and 0xFFFFFF)
}

/** The same colour with nothing showing through it. Used for pop-ups, which must hide what is under them. */
fun solid(argb: Int): Int = argb or (0xFF shl 24)

/**
 * The colours a phone offers a keyboard, as ARGB numbers. On Android 12 and later they come
 * from the wallpaper; reading them is the keyboard service's job, see PhoneColors.kt.
 */
data class PhoneColors(
    val dark: Boolean,
    val background: Int,
    /** A second, slightly different background, used when the theme has a gradient. */
    val backgroundEnd: Int,
    val key: Int,
    val functionKey: Int,
    val pressed: Int,
    val accent: Int,
    val text: Int,
    val accentText: Int,
) {
    companion object {
        /** For phones older than Android 12, which have no wallpaper colours: a plain dark or light set. */
        fun plain(dark: Boolean): PhoneColors = if (dark) {
            PhoneColors(
                dark = true, background = 0xFF1B1D22.toInt(), backgroundEnd = 0xFF2A2F3A.toInt(),
                key = 0xFF343842.toInt(), functionKey = 0xFF262930.toInt(), pressed = 0xFF565C6B.toInt(),
                accent = 0xFF8AB4F8.toInt(), text = 0xFFFFFFFF.toInt(), accentText = 0xFF10223F.toInt(),
            )
        } else {
            PhoneColors(
                dark = false, background = 0xFFE8EAED.toInt(), backgroundEnd = 0xFFD5DAE3.toInt(),
                key = 0xFFFFFFFF.toInt(), functionKey = 0xFFCDD1D8.toInt(), pressed = 0xFFB8BDC6.toInt(),
                accent = 0xFF1A73E8.toInt(), text = 0xFF1F1F1F.toInt(), accentText = 0xFFFFFFFF.toInt(),
            )
        }
    }
}

/**
 * This theme wearing the phone's colours. Shape, sizes, letters and see-through settings
 * stay yours; a gradient stays a gradient, between two of the phone's colours.
 * The result is for drawing, not for saving: its colours are now fixed ("own").
 */
fun Theme.withPhoneColors(colors: PhoneColors): Theme = copy(
    colorSource = "own",
    background = formatHexColor(colors.background),
    backgroundEnd = if (backgroundEnd != null) formatHexColor(colors.backgroundEnd) else null,
    keyColor = formatHexColor(colors.key),
    functionKeyColor = formatHexColor(colors.functionKey),
    keyPressedColor = formatHexColor(colors.pressed),
    accentColor = formatHexColor(colors.accent),
    textColor = formatHexColor(colors.text),
    accentTextColor = formatHexColor(colors.accentText),
    // A border keeps its thickness but must stay visible on the new background.
    borderColor = if (borderWidthDp > 0f) (if (colors.dark) "#66FFFFFF" else "#66000000") else borderColor,
)

/** True for colours that need light text on top of them. */
fun isDarkColor(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) < 140f
}
