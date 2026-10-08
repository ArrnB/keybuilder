package app.keybuilder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import app.keybuilder.model.PANELS
import app.keybuilder.model.TOOLBAR_HEIGHT_DP
import app.keybuilder.model.Theme
import app.keybuilder.model.isDarkColor
import app.keybuilder.model.panelName
import app.keybuilder.model.parseHexColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long a key is held before it starts repeating, and how fast it repeats. */
private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 50L

/** How long a key is held before its long-press choices appear. */
private const val LONG_PRESS_MS = 320L

/** Size of the pop-up above a key: the width of one choice and the height, in dp. */
private const val POPUP_CELL_DP = 44f
private const val POPUP_HEIGHT_DP = 52f

/** What the pop-up above a key is showing. Positions are in pixels inside the keyboard. */
private data class KeyPopup(
    val owner: KeyPos,
    val options: List<String>,
    val selected: Int,
    val keyLeft: Float,
    val keyTop: Float,
    val keyWidth: Float,
    /** True for long-press choices (one is highlighted), false for the plain "you pressed this" preview. */
    val chooser: Boolean,
)

/** Remembers something without causing a redraw when it changes. */
private class Holder<T>(var value: T? = null)

/** The result of waiting for a finger to lift: the lift itself, or null inside if the press was cancelled. */
private class Lift(val change: PointerInputChange?)

/**
 * Draws a keyboard from its description. This one function is used both by the real
 * keyboard (KeyBuilderIme) and by previews inside the app, so they can never look different.
 *
 * It only draws and reports what the finger did. What that means is decided by KeyboardEngine.
 *
 * The editor uses two extras: give [onKeyAt] and taps report WHERE the key is instead of
 * typing (used to pick a key to edit), and [selected] draws a ring around one key.
 *
 * The real keyboard uses three more: [onAlternate] is told which long-press choice was
 * picked, [onKeyDown] is called the moment any key is touched (for vibration), and
 * [showPopups] turns on the enlarged letter shown above a key while it is pressed.
 */
@Composable
fun KeyboardView(
    def: KeyboardDef,
    state: KeyboardState,
    onKey: (KeyDef) -> Unit,
    modifier: Modifier = Modifier,
    selected: KeyPos? = null,
    onKeyAt: ((KeyPos) -> Unit)? = null,
    showToolbar: Boolean = true,
    onOpenPanel: (String) -> Unit = {},
    onAlternate: (KeyDef, String) -> Unit = { _, _ -> },
    onKeyDown: () -> Unit = {},
    showPopups: Boolean = false,
) {
    val theme = def.theme
    val palette = remember(theme) { Palette(theme) }
    val layer = KeyboardEngine.currentLayer(def, state)
    val shift = KeyboardEngine.effectiveShift(def, state)
    val gap = theme.keyGapDp.dp
    val editing = onKeyAt != null

    // Where the keyboard is on screen, so a key can work out where its pop-up goes.
    val container = remember { Holder<LayoutCoordinates>() }
    // remember(layer.id): a pop-up never survives a change of layer.
    var popup by remember(layer.id) { mutableStateOf<KeyPopup?>(null) }

    Box(modifier = modifier.fillMaxWidth().onGloballyPositioned { container.value = it }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background)
                .padding(gap / 2),
        ) {
            if (def.toolbar && showToolbar) ToolbarStrip(palette, onOpenPanel)

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
                            val choices = KeyboardEngine.alternatesFor(def, state, keyDef)
                            val holds = keyDef.action == KeyAction.Backspace || keyDef.action is KeyAction.Arrow
                            KeyCell(
                                pos = pos,
                                keyDef = keyDef,
                                theme = theme,
                                palette = palette,
                                shift = shift,
                                selected = pos == selected,
                                editing = editing,
                                // While picking keys in the editor nothing repeats and nothing pops up.
                                repeats = !editing && holds,
                                alternates = if (editing) emptyList() else choices,
                                hint = if (theme.hints) choices.firstOrNull() else null,
                                previews = showPopups && !editing,
                                container = container,
                                onDown = onKeyDown,
                                onPopup = { popup = it },
                                onPopupDone = { if (popup?.owner == pos) popup = null },
                                onTap = { if (onKeyAt != null) onKeyAt(pos) else onKey(keyDef) },
                                onAlternate = { onAlternate(keyDef, it) },
                            )
                        }
                    }
                }
            }
        }

        val shown = popup
        if (shown != null) {
            PopupBubble(shown, theme, palette, keyboardWidthPx = container.value?.size?.width ?: 0)
        }
    }
}

/** The strip above the keys with one button per panel (clipboard, GIF, emoji). */
@Composable
private fun ToolbarStrip(palette: Palette, onOpenPanel: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT_DP.dp)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (panel in PANELS) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    // A faint tint of the text colour, so it shows on any theme (even see-through keys).
                    .background(palette.text.copy(alpha = 0.12f))
                    .clickable { onOpenPanel(panel) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = panelName(panel),
                    maxLines = 1,
                    style = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = palette.fontFamily),
                )
            }
        }
    }
}

/**
 * The bubble above a pressed key. It is drawn inside the keyboard (a keyboard cannot draw
 * outside its own area), so for the top row it sits at the very top edge.
 */
@Composable
private fun PopupBubble(popup: KeyPopup, theme: Theme, palette: Palette, keyboardWidthPx: Int) {
    val density = LocalDensity.current
    val cellPx = with(density) { POPUP_CELL_DP.dp.toPx() }
    val heightPx = with(density) { POPUP_HEIGHT_DP.dp.toPx() }
    val left = KeyboardEngine.popupLeft(
        popup.keyLeft, popup.keyWidth, popup.options.size, cellPx, keyboardWidthPx.toFloat(),
    )
    val top = (popup.keyTop - heightPx).coerceAtLeast(0f)
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .height(POPUP_HEIGHT_DP.dp)
            // Two layers: the solid keyboard colour first, so a see-through key colour still hides what is behind.
            .background(palette.background, shape)
            .background(palette.pressed, shape)
            .border(1.dp, palette.text.copy(alpha = 0.25f), shape),
    ) {
        popup.options.forEachIndexed { index, option ->
            val picked = popup.chooser && index == popup.selected
            Box(
                modifier = Modifier
                    .width(POPUP_CELL_DP.dp)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .background(if (picked) palette.accent else Color.Transparent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = option,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (picked) palette.accentText else palette.text,
                        fontSize = (if (option.length > 1) theme.fontSizeSp * 0.7f else theme.fontSizeSp * 1.1f).sp,
                        textAlign = TextAlign.Center,
                        fontFamily = palette.fontFamily,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    pos: KeyPos,
    keyDef: KeyDef,
    theme: Theme,
    palette: Palette,
    shift: ShiftState,
    selected: Boolean,
    editing: Boolean,
    repeats: Boolean,
    alternates: List<String>,
    hint: String?,
    previews: Boolean,
    container: Holder<LayoutCoordinates>,
    onDown: () -> Unit,
    onPopup: (KeyPopup) -> Unit,
    onPopupDone: () -> Unit,
    onTap: () -> Unit,
    onAlternate: (String) -> Unit,
) {
    // A gap is invisible when typing. In the editor it is drawn as a faint outline so it can be picked.
    val isGap = keyDef.look == KeyLook.SPACER
    if (isGap && !editing) {
        Spacer(Modifier.weight(keyDef.width))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val cell = remember { Holder<LayoutCoordinates>() }

    val restingColor = keyDef.style?.background?.toColorOrNull() ?: when (keyDef.look) {
        KeyLook.ACCENT -> palette.accent
        KeyLook.FUNCTION -> palette.function
        else -> palette.key
    }
    val background = when {
        isGap -> Color.Transparent
        pressed -> palette.pressed
        else -> restingColor
    }
    val foreground = keyDef.style?.textColor?.toColorOrNull()
        ?: if (keyDef.look == KeyLook.ACCENT) palette.accentText else palette.text
    val shape = RoundedCornerShape(theme.cornerRadiusDp.dp)
    val label = if (isGap) "" else keyDef.displayLabel(shift)
    val outline: Modifier = when {
        isGap -> Modifier.border(1.dp, palette.selection.copy(alpha = 0.35f), shape)
        theme.borderWidthDp > 0f -> Modifier.border(theme.borderWidthDp.dp, palette.border, shape)
        else -> Modifier
    }
    // Only ordinary typing keys get the enlarged preview; it would be noise on shift or space.
    val preview = if (previews && keyDef.action == KeyAction.TypeLabel && label.isNotEmpty()) label else null

    // The gesture below runs for as long as the key exists, so it must always see the latest values.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnPopup by rememberUpdatedState(onPopup)
    val currentOnPopupDone by rememberUpdatedState(onPopupDone)
    val currentOnAlternate by rememberUpdatedState(onAlternate)
    val currentAlternates by rememberUpdatedState(alternates)
    val currentPreview by rememberUpdatedState(preview)

    // Outer box = the touch area (it includes the gap, so taps between keys still count).
    Box(
        modifier = Modifier
            .weight(keyDef.width)
            .fillMaxHeight()
            .onGloballyPositioned { cell.value = it }
            .pointerInput(repeats) {
                val cellPx = POPUP_CELL_DP.dp.toPx()
                coroutineScope {
                    val scope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        currentOnDown()

                        // Where this key is inside the keyboard, for placing the pop-up.
                        val box = container.value
                        val here = cell.value
                        val origin = if (box != null && here != null && box.isAttached && here.isAttached) {
                            box.localPositionOf(here, Offset.Zero)
                        } else {
                            null
                        }
                        val keyWidth = here?.size?.width?.toFloat() ?: 0f
                        val boxWidth = box?.size?.width?.toFloat() ?: 0f

                        val shownPreview = currentPreview
                        if (shownPreview != null && origin != null) {
                            currentOnPopup(KeyPopup(pos, listOf(shownPreview), 0, origin.x, origin.y, keyWidth, chooser = false))
                        }

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

                        var up: PointerInputChange? = null
                        var chosen: String? = null
                        val choices = currentAlternates
                        if (choices.isNotEmpty() && !repeats && origin != null) {
                            // Lifted quickly: an ordinary tap. Still down after the wait: a long press.
                            val quick = withTimeoutOrNull(LONG_PRESS_MS) { Lift(waitForUpOrCancellation()) }
                            if (quick != null) {
                                up = quick.change
                            } else {
                                // Show the choices and follow the finger until it lifts.
                                val left = KeyboardEngine.popupLeft(origin.x, keyWidth, choices.size, cellPx, boxWidth)
                                var index = 0
                                currentOnPopup(KeyPopup(pos, choices, index, origin.x, origin.y, keyWidth, chooser = true))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        chosen = choices[index]
                                        break
                                    }
                                    val under = KeyboardEngine.popupIndex(origin.x + change.position.x, left, cellPx, choices.size)
                                    if (under != index) {
                                        index = under
                                        currentOnPopup(KeyPopup(pos, choices, index, origin.x, origin.y, keyWidth, chooser = true))
                                    }
                                    change.consume()
                                }
                            }
                        } else {
                            up = waitForUpOrCancellation()
                        }

                        repeatJob?.cancel()
                        pressed = false
                        currentOnPopupDone()
                        val picked = chosen
                        val lifted = up
                        if (picked != null) {
                            currentOnAlternate(picked)
                        } else if (lifted != null) {
                            lifted.consume()
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
                .then(outline)
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
            } else if (!isGap) {
                KeyIcon(keyDef.action, shift, foreground, sizeDp = theme.fontSizeSp * 1.05f)
            }
            // The first long-press choice, small, in the corner.
            if (hint != null && !isGap) {
                BasicText(
                    text = hint,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                    maxLines = 1,
                    style = TextStyle(
                        color = foreground.copy(alpha = 0.6f),
                        fontSize = (theme.fontSizeSp * 0.5f).sp,
                        fontFamily = palette.fontFamily,
                    ),
                )
            }
        }
    }
}

/** Icons are drawn with lines instead of font characters, so they look the same on every phone. */
@Composable
internal fun KeyIcon(action: KeyAction, shift: ShiftState, tint: Color, sizeDp: Float) {
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

            is KeyAction.Arrow -> {
                // A simple chevron pointing the way the cursor will move.
                val chevron = Path().apply {
                    when (action.direction) {
                        "left" -> {
                            moveTo(w * 0.62f, h * 0.20f)
                            lineTo(w * 0.32f, h * 0.50f)
                            lineTo(w * 0.62f, h * 0.80f)
                        }
                        "right" -> {
                            moveTo(w * 0.38f, h * 0.20f)
                            lineTo(w * 0.68f, h * 0.50f)
                            lineTo(w * 0.38f, h * 0.80f)
                        }
                        "up" -> {
                            moveTo(w * 0.20f, h * 0.62f)
                            lineTo(w * 0.50f, h * 0.32f)
                            lineTo(w * 0.80f, h * 0.62f)
                        }
                        else -> {
                            moveTo(w * 0.20f, h * 0.38f)
                            lineTo(w * 0.50f, h * 0.68f)
                            lineTo(w * 0.80f, h * 0.38f)
                        }
                    }
                }
                drawPath(chevron, tint, style = outline)
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
