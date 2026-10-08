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

/** True for colours that need light text on top of them. */
fun isDarkColor(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) < 140f
}
