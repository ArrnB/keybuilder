package app.keybuilder.ui

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.PhoneColors
import app.keybuilder.model.Theme
import app.keybuilder.model.parseHexColor
import app.keybuilder.model.solid
import app.keybuilder.model.withOpacity
import app.keybuilder.model.withPhoneColors

/*
 * Two things every place that draws a keyboard needs: the theme with the phone's colours
 * filled in (when the theme asks for them), and the background as something that can be
 * painted, whether it is one colour or a fade between two.
 *
 * About "the phone's colours": a keyboard is not allowed to see the app it is typing into,
 * so it cannot match an app's colours directly. What it can do is (1) use the colours
 * Android makes from the wallpaper, the same ones the phone's own settings and many apps
 * use, switching between light and dark with the phone; and (2) be see-through, so the
 * app behind it shows through. Both are options in the style screen.
 */

/** This theme as it should be drawn: with the phone's colours if it asks for them, otherwise unchanged. */
@Composable
fun Theme.resolved(): Theme {
    if (colorSource != "phone") return this
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Reading the configuration means this is worked out again when the wallpaper colours change.
    val configuration = LocalConfiguration.current
    val colors = remember(dark, configuration) { readPhoneColors(context, dark) }
    return remember(this, colors) { withPhoneColors(colors) }
}

@Composable
fun KeyboardDef.resolved(): KeyboardDef {
    val shown = theme.resolved()
    return if (shown === theme) this else copy(theme = shown)
}

/** Android 12 and later keep a palette made from the wallpaper. Older phones get a plain light or dark set. */
private fun readPhoneColors(context: Context, dark: Boolean): PhoneColors {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return PhoneColors.plain(dark)
    return try {
        fun color(id: Int): Int = context.getColor(id)
        if (dark) {
            PhoneColors(
                dark = true,
                background = color(android.R.color.system_neutral1_900),
                backgroundEnd = color(android.R.color.system_accent2_800),
                key = color(android.R.color.system_neutral2_700),
                functionKey = color(android.R.color.system_neutral2_800),
                pressed = color(android.R.color.system_neutral2_500),
                accent = color(android.R.color.system_accent1_200),
                text = color(android.R.color.system_neutral1_50),
                accentText = color(android.R.color.system_accent1_800),
            )
        } else {
            PhoneColors(
                dark = false,
                background = color(android.R.color.system_neutral2_100),
                backgroundEnd = color(android.R.color.system_accent2_200),
                key = color(android.R.color.system_neutral1_10),
                functionKey = color(android.R.color.system_neutral2_200),
                pressed = color(android.R.color.system_neutral2_400),
                accent = color(android.R.color.system_accent1_600),
                text = color(android.R.color.system_neutral1_900),
                accentText = color(android.R.color.system_accent1_0),
            )
        }
    } catch (e: Exception) {
        PhoneColors.plain(dark)
    }
}

/**
 * Whether this phone lets one window blur what is behind it, which the frosted look needs.
 * Android 12 added it; phone makers can leave it out, and battery saver switches it off.
 */
fun blurAllowed(context: Context): Boolean = try {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
} catch (e: Exception) {
    false
}

/**
 * The keyboard background as paint: one colour, or a fade between two, at the theme's opacity.
 * With [opaque] the opacity is ignored and nothing shows through: panels use that, because
 * a list of GIFs or copied text over a see-through background cannot be read.
 */
internal fun backgroundBrush(theme: Theme, opaque: Boolean = false): Brush {
    fun paint(argb: Int): Color = Color(if (opaque) solid(argb) else withOpacity(argb, theme.backgroundOpacity))
    val start = paint(parseHexColor(theme.background) ?: 0xFF1B1D22.toInt())
    val endColor = theme.backgroundEnd?.let { parseHexColor(it) } ?: return SolidColor(start)
    val end = paint(endColor)
    return when (theme.gradientDirection) {
        "across" -> Brush.horizontalGradient(listOf(start, end))
        "diagonal" -> Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset.Infinite)
        else -> Brush.verticalGradient(listOf(start, end))
    }
}

/** The colour at the top of the background, solid, for the search bar drawn above the keys. */
internal fun backgroundTopColor(theme: Theme): Color =
    Color(solid(parseHexColor(theme.background) ?: 0xFF1B1D22.toInt()))
