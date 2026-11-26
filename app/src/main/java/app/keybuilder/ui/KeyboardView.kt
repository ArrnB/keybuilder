package app.keybuilder.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import app.keybuilder.model.animationFor
import app.keybuilder.model.isDarkColor
import app.keybuilder.model.isFixedWidthFont
import app.keybuilder.model.motionFor
import app.keybuilder.model.panelName
import app.keybuilder.model.parseHexColor
import app.keybuilder.model.solid
import app.keybuilder.model.withOpacity
import app.keybuilder.suggest.Suggestion
import app.keybuilder.suggest.SuggestionKind
import app.keybuilder.suggest.fitSuggestions
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How long a key is held before it starts repeating, and how fast it repeats. */
private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 50L

/** How long a key is held before its long-press choices appear (or the space bar starts steering). */
private const val LONG_PRESS_MS = 300L

/** Space-bar steering: how far the finger slides for one step sideways, and for one line up or down. */
private const val CURSOR_STEP_DP = 14f
private const val CURSOR_LINE_DP = 30f

/** How far outside a key the finger may wander before the press is cancelled. */
private const val TOUCH_SLACK_DP = 14f

private const val SETTINGS_PANEL = "settings"

/**
 * Whether the strip's buttons show small symbols instead of words. It is a general setting,
 * so rather than passing it through every screen, whoever shows a keyboard provides it once.
 */
val LocalStripIcons = compositionLocalOf { false }

/**
 * What the real keyboard adds to the strip: word suggestions, and the most recently copied
 * thing for one-tap pasting. Previews inside the app leave this out.
 */
class StripExtras(
    /** Shown in place of the shortcut buttons while there are any. */
    val suggestions: List<Suggestion> = emptyList(),
    val onSuggestion: (Suggestion) -> Unit = {},
    /** The small arrow that brings the shortcut buttons back while suggestions are showing. */
    val onShowTools: () -> Unit = {},
    /** A short label for the last copied thing, or null to show no such button. */
    val clip: String? = null,
    val onClip: () -> Unit = {},
    /** Holding the button hides it until something new is copied. */
    val onClipHeld: () -> Unit = {},
    /** Show the strip even on a keyboard that has its shortcut buttons switched off. */
    val always: Boolean = false,
)

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

/**
 * Draws a keyboard from its description. This one function is used both by the real
 * keyboard (KeyBuilderIme) and by previews inside the app, so they can never look different.
 *
 * It only draws and reports what the finger did. What that means is decided by KeyboardEngine.
 *
 * The editor uses two extras: give [onKeyAt] and taps report WHERE the key is instead of
 * typing (used to pick a key to edit), and [selected] draws a ring around one key.
 *
 * The real keyboard uses four more: [onAlternate] is told which long-press choice was
 * picked, [onKeyDown] is told which key the moment one is touched (for vibration and sound),
 * [showPopups] turns on the enlarged letter shown above a key while it is pressed,
 * [onCursor] is told "left", "right", "up" or "down" while the held space bar is slid, and
 * [strip] puts suggestions and the last copied thing on the strip.
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
    onKeyDown: (KeyDef) -> Unit = {},
    showPopups: Boolean = false,
    onCursor: (String) -> Unit = {},
    strip: StripExtras? = null,
) {
    // The theme as it should look right now: with the phone's colours, if it asks for them.
    val theme = def.theme.resolved()
    val context = LocalContext.current
    val palette = remember(theme) { Palette(theme, Fonts.family(context, theme.fontFamily)) }
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
                .background(palette.backgroundPaint)
                .padding(gap / 2),
        ) {
            if (showToolbar && (def.toolbar || strip?.always == true)) {
                ToolbarStrip(palette, shortcuts = def.toolbar, onOpenPanel = onOpenPanel, extras = strip)
            }

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
                            val choices = KeyboardEngine.choicesFor(def, state, keyDef, rowIndex, keyIndex)
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
                                slides = !editing && keyDef.action == KeyAction.Space,
                                alternates = if (editing) emptyList() else choices.all,
                                hint = if (theme.hints) choices.hint else null,
                                previews = showPopups && !editing,
                                container = container,
                                onDown = { onKeyDown(keyDef) },
                                onPopup = { popup = it },
                                onPopupDone = { if (popup?.owner == pos) popup = null },
                                onTap = { if (onKeyAt != null) onKeyAt(pos) else onKey(keyDef) },
                                onAlternate = { onAlternate(keyDef, it) },
                                onCursor = onCursor,
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

/**
 * The strip above the keys. Normally: one button per panel (clipboard, GIF, stickers, emoji)
 * on the left, then the last copied thing, then the settings gear. While there are word
 * suggestions they take the place of the panel buttons, with a small arrow to get the
 * buttons back. The buttons show words or small symbols, see LocalStripIcons.
 *
 * [shortcuts] is false on a keyboard whose shortcut buttons are switched off; the strip
 * then only carries suggestions and the last copied thing.
 */
@Composable
private fun ToolbarStrip(palette: Palette, shortcuts: Boolean, onOpenPanel: (String) -> Unit, extras: StripExtras?) {
    val icons = LocalStripIcons.current
    val suggestions = extras?.suggestions.orEmpty()
    val suggesting = suggestions.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT_DP.dp)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (suggesting && extras != null) {
            if (shortcuts) {
                StripButton(palette, onClick = extras.onShowTools, padding = 8f) {
                    KeyIcon(KeyAction.Arrow("left"), ShiftState.OFF, palette.text, sizeDp = 16f)
                }
            }
            // BoxWithConstraints tells us how wide the room for the words is, so they can be fitted into it.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Text sizes follow the phone's "font size" setting; widths do not. Allow for that.
                val room = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(0.5f)
                val fit = remember(suggestions, room, palette.fixedWidth) {
                    fitSuggestions(suggestions.map { shownText(it) }, room, palette.fixedWidth)
                }
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    suggestions.forEachIndexed { index, suggestion ->
                        if (index > 0) {
                            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 5.dp).background(palette.text.copy(alpha = 0.2f)))
                        }
                        SuggestionCell(
                            palette = palette,
                            suggestion = suggestion,
                            label = fit.labels.getOrElse(index) { shownText(suggestion) },
                            share = fit.weights.getOrElse(index) { 1f },
                            fontSp = fit.fontSp,
                            onClick = { extras.onSuggestion(suggestion) },
                        )
                    }
                }
            }
        } else if (shortcuts) {
            // The panel buttons scroll sideways if they do not all fit; what is right of them always stays in view.
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (panel in PANELS.filter { it != SETTINGS_PANEL }) {
                    StripButton(palette, onClick = { onOpenPanel(panel) }) {
                        if (icons) {
                            PanelGlyph(panel, palette.text)
                        } else {
                            BasicText(
                                text = panelName(panel),
                                maxLines = 1,
                                style = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = palette.fontFamily),
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        val clip = extras?.clip
        if (clip != null) {
            // While suggestions need the room, the last copied thing shrinks to a symbol.
            QuickClipButton(palette, label = if (suggesting) null else clip, onTap = extras.onClip, onHold = extras.onClipHeld)
        }
        if (shortcuts) {
            StripButton(palette, onClick = { onOpenPanel(SETTINGS_PANEL) }) {
                PanelGlyph(SETTINGS_PANEL, palette.text)
            }
        }
    }
}

/** A suggestion as it is written on the strip: your own typing in quotes, a shortcut's text on one line. */
private fun shownText(suggestion: Suggestion): String = when (suggestion.kind) {
    SuggestionKind.TYPED -> "\u201C${suggestion.text}\u201D"
    SuggestionKind.SHORTCUT -> suggestion.text.replace('\n', ' ')
    else -> suggestion.text
}

/**
 * One suggestion. What will be put in automatically (a correction, or a shortcut's text)
 * is drawn bold. [label], [share] of the width and [fontSp] come from fitSuggestions, which
 * makes sure three long words can still be told apart.
 */
@Composable
private fun RowScope.SuggestionCell(
    palette: Palette,
    suggestion: Suggestion,
    label: String,
    share: Float,
    fontSp: Float,
    onClick: () -> Unit,
) {
    val automatic = suggestion.kind == SuggestionKind.CORRECTION || suggestion.kind == SuggestionKind.SHORTCUT
    val typed = suggestion.kind == SuggestionKind.TYPED
    Box(
        modifier = Modifier
            .weight(share.coerceAtLeast(0.1f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                color = if (typed) palette.text.copy(alpha = 0.7f) else palette.text,
                fontSize = fontSp.sp,
                fontFamily = palette.fontFamily,
                fontWeight = if (automatic) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * The last copied thing: tap to paste it, hold to hide it. With a [label] it shows a
 * clipboard symbol and the start of the text; without one, just the symbol.
 */
@Composable
private fun QuickClipButton(palette: Palette, label: String?, onTap: () -> Unit, onHold: () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnHold by rememberUpdatedState(onHold)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(palette.accentSolid.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnTap() }, onLongPress = { currentOnHold() })
            }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelGlyph("clipboard", palette.text, sizeDp = 16f)
        if (label != null) {
            BasicText(
                text = label,
                modifier = Modifier.widthIn(max = 55.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = palette.fontFamily),
            )
        }
    }
}

@Composable
private fun StripButton(palette: Palette, onClick: () -> Unit, padding: Float = 12f, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            // A faint tint of the text colour, so it shows on any theme (even see-through keys).
            .background(palette.text.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = padding.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A small line drawing for a panel: a clipboard, a boxed "GIF", a smiling face, or a gear. */
@Composable
internal fun PanelGlyph(panel: String, tint: Color, sizeDp: Float = 20f) {
    if (panel == "symbols") {
        BasicText("Ω", style = TextStyle(color = tint, fontSize = (sizeDp * 0.9f).sp, fontWeight = FontWeight.Bold))
        return
    }
    if (panel == "gif") {
        Box(
            modifier = Modifier
                .border(1.5.dp, tint, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            BasicText("GIF", style = TextStyle(color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
        return
    }
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val line = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (panel) {
            "clipboard" -> {
                // The board, the clip at its top, and two lines of "text".
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.20f),
                    size = Size(w * 0.60f, h * 0.70f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = line,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.36f, h * 0.10f),
                    size = Size(w * 0.28f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.05f),
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.52f), Offset(w * 0.66f, h * 0.52f), w * 0.07f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.70f), Offset(w * 0.58f, h * 0.70f), w * 0.07f, StrokeCap.Round)
            }

            "emoji" -> {
                drawCircle(tint, radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.5f), style = line)
                drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.36f, h * 0.42f))
                drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.64f, h * 0.42f))
                drawArc(
                    color = tint,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.30f, h * 0.30f),
                    size = Size(w * 0.40f, h * 0.40f),
                    style = line,
                )
            }

            "edit" -> {
                // A text cursor (an I shape) with an arrow head on each side of it.
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.84f), w * 0.08f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.16f), Offset(w * 0.62f, h * 0.16f), w * 0.08f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.84f), Offset(w * 0.62f, h * 0.84f), w * 0.08f, StrokeCap.Round)
                val left = Path().apply {
                    moveTo(w * 0.24f, h * 0.34f)
                    lineTo(w * 0.08f, h * 0.50f)
                    lineTo(w * 0.24f, h * 0.66f)
                }
                val right = Path().apply {
                    moveTo(w * 0.76f, h * 0.34f)
                    lineTo(w * 0.92f, h * 0.50f)
                    lineTo(w * 0.76f, h * 0.66f)
                }
                drawPath(left, tint, style = line)
                drawPath(right, tint, style = line)
            }

            "sticker" -> {
                // A square note with one corner peeling up.
                val note = Path().apply {
                    moveTo(w * 0.20f, h * 0.20f)
                    lineTo(w * 0.80f, h * 0.20f)
                    lineTo(w * 0.80f, h * 0.56f)
                    lineTo(w * 0.56f, h * 0.80f)
                    lineTo(w * 0.20f, h * 0.80f)
                    close()
                }
                drawPath(note, tint, style = line)
                val peel = Path().apply {
                    moveTo(w * 0.80f, h * 0.56f)
                    lineTo(w * 0.56f, h * 0.56f)
                    lineTo(w * 0.56f, h * 0.80f)
                }
                drawPath(peel, tint, style = line)
            }

            else -> {
                // A gear: a ring, a hole in the middle, and eight teeth around it.
                val centre = Offset(w * 0.5f, h * 0.5f)
                drawCircle(tint, radius = w * 0.13f, center = centre, style = line)
                drawCircle(tint, radius = w * 0.29f, center = centre, style = line)
                for (tooth in 0 until 8) {
                    val angle = Math.toRadians(tooth * 45.0)
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(centre.x + dx * w * 0.32f, centre.y + dy * w * 0.32f),
                        end = Offset(centre.x + dx * w * 0.44f, centre.y + dy * w * 0.44f),
                        strokeWidth = w * 0.13f,
                        cap = StrokeCap.Butt,
                    )
                }
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
            .background(palette.popupBase, shape)
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
                    .background(if (picked) palette.accentSolid else Color.Transparent, RoundedCornerShape(8.dp)),
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
    slides: Boolean,
    alternates: List<String>,
    hint: String?,
    previews: Boolean,
    container: Holder<LayoutCoordinates>,
    onDown: () -> Unit,
    onPopup: (KeyPopup) -> Unit,
    onPopupDone: () -> Unit,
    onTap: () -> Unit,
    onAlternate: (String) -> Unit,
    onCursor: (String) -> Unit,
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
    // How this key moves when pressed: its own animation, or the theme's.
    val motion = remember(theme.animation, keyDef.style?.animation) { motionFor(theme.animationFor(keyDef)) }
    val animated = !editing && !isGap && (motion.moves || motion.glow || motion.lingers)
    // 0 = at rest, 1 = fully pressed. Going down is quick so even a fast tap shows; coming back is slower.
    val press = if (!animated) {
        if (pressed) 1f else 0f
    } else {
        animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = when {
                pressed -> tween(durationMillis = 45)
                motion.bouncy -> spring(dampingRatio = 0.35f, stiffness = 500f)
                motion.lingers -> tween(durationMillis = 320)
                else -> tween(durationMillis = 150)
            },
            label = "key press",
        ).value
    }
    val shade = if (animated && motion.lingers) press.coerceIn(0f, 1f) else if (pressed) 1f else 0f
    val background = when {
        isGap -> Color.Transparent
        shade <= 0f -> restingColor
        shade >= 1f -> palette.pressed
        else -> lerp(restingColor, palette.pressed, shade)
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
    val currentOnCursor by rememberUpdatedState(onCursor)
    val currentAlternates by rememberUpdatedState(alternates)
    val currentPreview by rememberUpdatedState(preview)

    // Outer box = the touch area (it includes the gap, so taps between keys still count).
    Box(
        modifier = Modifier
            .weight(keyDef.width)
            .fillMaxHeight()
            .onGloballyPositioned { cell.value = it }
            .pointerInput(repeats, slides) {
                val cellPx = POPUP_CELL_DP.dp.toPx()
                val stepAcross = CURSOR_STEP_DP.dp.toPx()
                val stepLine = CURSOR_LINE_DP.dp.toPx()
                val slack = TOUCH_SLACK_DP.dp.toPx()
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

                        // What holding this key does, if anything: offer its choices, or (space bar) steer the cursor.
                        val choices = currentAlternates
                        val chooses = !repeats && !slides && choices.isNotEmpty() && origin != null
                        val popupLeft = if (origin != null) {
                            KeyboardEngine.popupLeft(origin.x, keyWidth, choices.size, cellPx, boxWidth)
                        } else {
                            0f
                        }
                        var held = false
                        var index = 0
                        var holdJob: Job? = null
                        if (chooses || slides) {
                            // A plain timer: when it rings and the finger is still down, the hold has begun.
                            holdJob = scope.launch {
                                delay(LONG_PRESS_MS)
                                held = true
                                if (chooses) {
                                    currentOnPopup(KeyPopup(pos, choices, 0, origin.x, origin.y, keyWidth, chooser = true))
                                } else {
                                    currentOnDown() // a second buzz: the space bar now steers the cursor
                                }
                            }
                        }

                        // Follow this finger until it lifts.
                        var lifted = false
                        var steering = false
                        var cursorMoves = 0
                        var anchorX = 0f
                        var anchorY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // A lift that arrives already used up is Android cancelling the touch, not a real lift.
                                if (!change.isConsumed) {
                                    change.consume()
                                    lifted = true
                                }
                                break
                            }
                            val at = change.position
                            if (held && chooses) {
                                val under = KeyboardEngine.popupIndex(origin.x + at.x, popupLeft, cellPx, choices.size)
                                if (under != index) {
                                    index = under
                                    currentOnPopup(KeyPopup(pos, choices, index, origin.x, origin.y, keyWidth, chooser = true))
                                }
                                change.consume()
                            } else if (held && slides) {
                                if (!steering) {
                                    // Count movement from where the finger is when the hold begins.
                                    steering = true
                                    anchorX = at.x
                                    anchorY = at.y
                                }
                                val across = KeyboardEngine.cursorSteps(at.x - anchorX, stepAcross)
                                if (across != 0) {
                                    repeat(abs(across)) { currentOnCursor(if (across > 0) "right" else "left") }
                                    anchorX += across * stepAcross
                                    cursorMoves += abs(across)
                                }
                                val lines = KeyboardEngine.cursorSteps(at.y - anchorY, stepLine)
                                if (lines != 0) {
                                    repeat(abs(lines)) { currentOnCursor(if (lines > 0) "down" else "up") }
                                    anchorY += lines * stepLine
                                    cursorMoves += abs(lines)
                                }
                                change.consume()
                            } else if (!held) {
                                // Sliding off the key before lifting cancels the press.
                                val outside = at.x < -slack || at.y < -slack ||
                                    at.x > size.width + slack || at.y > size.height + slack
                                if (outside) break
                            }
                        }

                        holdJob?.cancel()
                        repeatJob?.cancel()
                        pressed = false
                        currentOnPopupDone()
                        if (lifted) {
                            if (held && chooses) {
                                currentOnAlternate(choices[index.coerceIn(0, choices.size - 1)])
                            } else if (held && slides && cursorMoves > 0) {
                                // The cursor was moved: do not also type a space.
                            } else if (!repeats) {
                                currentOnTap()
                            }
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
                .then(
                    if (animated && motion.moves) {
                        // Scaling the whole key cap keeps its letter or icon in proportion.
                        Modifier.graphicsLayer {
                            val size = 1f + (motion.scale - 1f) * press
                            scaleX = size
                            scaleY = size
                            rotationZ = motion.tiltDegrees * press
                        }
                    } else {
                        Modifier
                    },
                )
                .background(background, shape)
                .then(outline)
                .then(
                    if (animated && motion.glow && press > 0.02f) {
                        Modifier.border(2.5.dp, palette.accentSolid.copy(alpha = press.coerceIn(0f, 1f)), shape)
                    } else {
                        Modifier
                    },
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
private class Palette(theme: Theme, val fontFamily: FontFamily) {
    /** Typewriter fonts need more room per letter, which matters when fitting suggestions. */
    val fixedWidth: Boolean = isFixedWidthFont(theme.fontFamily)

    /** The background as paint: one colour or a fade between two, as see-through as the theme says. */
    val backgroundPaint: Brush = backgroundBrush(theme)

    /** The background colour with nothing showing through, for pop-ups that must cover what is under them. */
    val popupBase = Color(solid(parseHexColor(theme.background) ?: 0xFF1B1D22.toInt()))

    val key = keyColor(theme.keyColor, theme.keyOpacity) ?: Color(0xFF343842)
    val function = keyColor(theme.functionKeyColor, theme.keyOpacity) ?: Color(0xFF262930)
    val accent = keyColor(theme.accentColor, theme.keyOpacity) ?: Color(0xFF4C8DFF)

    /** The accent colour at full strength, for small highlights that must stand out whatever the key opacity. */
    val accentSolid = theme.accentColor.toColorOrNull() ?: Color(0xFF4C8DFF)
    val pressed = theme.keyPressedColor.toColorOrNull() ?: Color(0xFF565C6B)
    val text = theme.textColor.toColorOrNull() ?: Color.White
    val accentText = theme.accentTextColor.toColorOrNull() ?: Color.White
    val border = theme.borderColor.toColorOrNull() ?: Color.Transparent

    private fun keyColor(hex: String, opacity: Float): Color? = parseHexColor(hex)?.let { Color(withOpacity(it, opacity)) }

    /** The ring around a selected key: white on dark keyboards, black on light ones. */
    val selection = if (isDarkColor(parseHexColor(theme.background) ?: 0)) Color.White else Color.Black
}

/** "#RRGGBB" text as a Compose colour, or null if the text is not a colour. */
internal fun String.toColorOrNull(): Color? = parseHexColor(this)?.let { Color(it) }
