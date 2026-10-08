package app.keybuilder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.KeyboardState
import app.keybuilder.engine.ShiftState
import app.keybuilder.engine.displayLabel
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.Theme
import app.keybuilder.model.isDarkColor
import app.keybuilder.model.parseHexColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long backspace is held before it starts repeating, and how fast it repeats. */
private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 50L

/**
 * Draws a keyboard from its description. This one function is used both by the real
 * keyboard (KeyBuilderIme) and by previews inside the app, so they can never look different.
 *
 * It only draws and reports taps. What a tap means is decided by KeyboardEngine.
 *
 * The editor uses two extras: give [onKeyAt] and taps report WHERE the key is instead of
 * typing (used to pick a key to edit), and [selected] draws a ring around one key.
 */
@Composable
fun KeyboardView(
    def: KeyboardDef,
    state: KeyboardState,
    onKey: (KeyDef) -> Unit,
    modifier: Modifier = Modifier,
    selected: KeyPos? = null,
    onKeyAt: ((KeyPos) -> Unit)? = null,
) {
    val theme = def.theme
    val palette = remember(theme) { Palette(theme) }
    val layer = KeyboardEngine.currentLayer(def, state)
    val shift = KeyboardEngine.effectiveShift(def, state)
    val gap = theme.keyGapDp.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .padding(gap / 2),
    ) {
        // key(...) throws away per-key state (like "is pressed") when the layer changes.
        key(layer.id) {
            layer.rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((theme.keyHeightDp * row.height).dp),
                ) {
                    row.keys.forEachIndexed { keyIndex, keyDef ->
                        val pos = KeyPos(layer.id, rowIndex, keyIndex)
                        KeyCell(
                            keyDef = keyDef,
                            theme = theme,
                            palette = palette,
                            shift = shift,
                            selected = pos == selected,
                            // While picking keys, backspace must not start repeating.
                            repeats = onKeyAt == null && keyDef.action == KeyAction.Backspace,
                            onTap = { if (onKeyAt != null) onKeyAt(pos) else onKey(keyDef) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    keyDef: KeyDef,
    theme: Theme,
    palette: Palette,
    shift: ShiftState,
    selected: Boolean,
    repeats: Boolean,
    onTap: () -> Unit,
) {
    if (keyDef.look == KeyLook.SPACER) {
        Spacer(Modifier.weight(keyDef.width))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val currentOnTap by rememberUpdatedState(onTap)

    val restingColor = keyDef.style?.background?.toColorOrNull() ?: when (keyDef.look) {
        KeyLook.ACCENT -> palette.accent
        KeyLook.FUNCTION -> palette.function
        else -> palette.key
    }
    val background = if (pressed) palette.pressed else restingColor
    val foreground = keyDef.style?.textColor?.toColorOrNull()
        ?: if (keyDef.look == KeyLook.ACCENT) palette.accentText else palette.text
    val shape = RoundedCornerShape(theme.cornerRadiusDp.dp)
    val label = keyDef.displayLabel(shift)

    // Outer box = the touch area (it includes the gap, so taps between keys still count).
    Box(
        modifier = Modifier
            .weight(keyDef.width)
            .fillMaxHeight()
            .pointerInput(repeats) {
                coroutineScope {
                    val scope = this
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        pressed = true
                        var repeatJob: Job? = null
                        if (repeats) {
                            // Repeating keys act as soon as they are touched, then keep going while held.
                            currentOnTap()
                            repeatJob = scope.launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    currentOnTap()
                                    delay(REPEAT_EVERY_MS)
                                }
                            }
                        }
                        val up = waitForUpOrCancellation()
                        repeatJob?.cancel()
                        pressed = false
                        if (up != null) {
                            up.consume()
                            // Normal keys act when the finger lifts; sliding off the key cancels.
                            if (!repeats) currentOnTap()
                        }
                    }
                }
            }
            .padding(theme.keyGapDp.dp / 2),
    ) {
        // Inner box = the visible key cap.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background, shape)
                .then(
                    if (theme.borderWidthDp > 0f) Modifier.border(theme.borderWidthDp.dp, palette.border, shape)
                    else Modifier
                )
                .then(if (selected) Modifier.border(2.5.dp, palette.selection, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (label.isNotEmpty()) {
                val ownSize = keyDef.style?.fontSizeSp
                // Word labels such as "?123" are drawn smaller so they fit.
                val fontSize = ownSize ?: if (label.length > 1) theme.fontSizeSp * 0.68f else theme.fontSizeSp
                BasicText(
                    text = label,
                    maxLines = 1,
                    style = TextStyle(
                        color = foreground,
                        fontSize = fontSize.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = palette.fontFamily,
                        fontWeight = if (theme.bold) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            } else {
                KeyIcon(keyDef.action, shift, foreground, sizeDp = theme.fontSizeSp * 1.05f)
            }
        }
    }
}

/** Icons are drawn with lines instead of font characters, so they look the same on every phone. */
@Composable
private fun KeyIcon(action: KeyAction, shift: ShiftState, tint: Color, sizeDp: Float) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val thickness = w * 0.09f
        val outline = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (action) {
            KeyAction.Shift -> {
                val arrow = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.92f, h * 0.52f)
                    lineTo(w * 0.68f, h * 0.52f)
                    lineTo(w * 0.68f, h * 0.84f)
                    lineTo(w * 0.32f, h * 0.84f)
                    lineTo(w * 0.32f, h * 0.52f)
                    lineTo(w * 0.08f, h * 0.52f)
                    close()
                }
                // Hollow = off, filled = next letter is a capital, filled + underline = caps lock.
                drawPath(arrow, tint, style = outline)
                if (shift != ShiftState.OFF) drawPath(arrow, tint, style = Fill)
                if (shift == ShiftState.LOCKED) {
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.30f, h * 0.98f),
                        end = Offset(w * 0.70f, h * 0.98f),
                        strokeWidth = thickness,
                        cap = StrokeCap.Round,
                    )
                }
            }

            KeyAction.Backspace -> {
                val body = Path().apply {
                    moveTo(w * 0.36f, h * 0.22f)
                    lineTo(w * 0.94f, h * 0.22f)
                    lineTo(w * 0.94f, h * 0.78f)
                    lineTo(w * 0.36f, h * 0.78f)
                    lineTo(w * 0.06f, h * 0.50f)
                    close()
                }
                drawPath(body, tint, style = outline)
                drawLine(tint, Offset(w * 0.52f, h * 0.38f), Offset(w * 0.76f, h * 0.62f), thickness, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.38f), Offset(w * 0.52f, h * 0.62f), thickness, StrokeCap.Round)
            }

            KeyAction.Enter -> {
                val arrow = Path().apply {
                    moveTo(w * 0.84f, h * 0.20f)
                    lineTo(w * 0.84f, h * 0.58f)
                    lineTo(w * 0.16f, h * 0.58f)
                    moveTo(w * 0.36f, h * 0.38f)
                    lineTo(w * 0.16f, h * 0.58f)
                    lineTo(w * 0.36f, h * 0.78f)
                }
                drawPath(arrow, tint, style = outline)
            }

            else -> Unit // space and the rest stay blank unless they have a label
        }
    }
}

/** The theme's colour text turned into real colours once, instead of on every redraw. */
private class Palette(theme: Theme) {
    val background = theme.background.toColorOrNull() ?: Color(0xFF1B1D22)
    val key = theme.keyColor.toColorOrNull() ?: Color(0xFF343842)
    val function = theme.functionKeyColor.toColorOrNull() ?: Color(0xFF262930)
    val accent = theme.accentColor.toColorOrNull() ?: Color(0xFF4C8DFF)
    val pressed = theme.keyPressedColor.toColorOrNull() ?: Color(0xFF565C6B)
    val text = theme.textColor.toColorOrNull() ?: Color.White
    val accentText = theme.accentTextColor.toColorOrNull() ?: Color.White
    val border = theme.borderColor.toColorOrNull() ?: Color.Transparent

    /** The ring around a selected key: white on dark keyboards, black on light ones. */
    val selection = if (isDarkColor(parseHexColor(theme.background) ?: 0)) Color.White else Color.Black

    val fontFamily: FontFamily = when (theme.fontFamily) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
}

/** "#RRGGBB" text as a Compose colour, or null if the text is not a colour. */
internal fun String.toColorOrNull(): Color? = parseHexColor(this)?.let { Color(it) }
