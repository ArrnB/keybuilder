package app.keybuilder.engine

import app.keybuilder.model.BuiltInChoices
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.MAX_ALTERNATES
import app.keybuilder.model.Layer
import app.keybuilder.model.PANELS
import app.keybuilder.model.TOOLBAR_HEIGHT_DP

/*
 * The keyboard's "brain". It knows nothing about Android or drawing:
 * you give it the current state and the key that was tapped, and it answers
 * with the new state and what should be typed. That makes it easy to test.
 */

enum class ShiftState { OFF, ONCE, LOCKED }

data class KeyboardState(
    val layerId: String,
    val shift: ShiftState = ShiftState.OFF,
    /** When shift was last tapped, used to spot a double tap. */
    val shiftTappedAtMs: Long? = null,
    /** The panel shown instead of the keys (see PANELS), or null for the normal keyboard. */
    val panel: String? = null,
)

/** Something the keyboard asks the text field to do. */
sealed interface Effect {
    data class Commit(val text: String) : Effect
    data object DeleteBackward : Effect
    data object Enter : Effect

    /** Move the cursor one step: "left", "right", "up" or "down". */
    data class MoveCursor(val direction: String) : Effect

    /** "copy", "paste", "cut" or "selectAll". */
    data class Command(val command: String) : Effect
}

/** What a key offers when held down, and which of those is shown small in its corner (or null). */
data class KeyChoices(val all: List<String> = emptyList(), val hint: String? = null)

data class PressResult(
    val state: KeyboardState,
    val effects: List<Effect> = emptyList(),
)

/** The text shown on a key right now (capitals while shift is on). */
fun KeyDef.displayLabel(shift: ShiftState): String =
    if (action == KeyAction.TypeLabel && shift != ShiftState.OFF) shiftLabel ?: label.uppercase() else label

object KeyboardEngine {

    /** Two shift taps closer together than this turn on caps lock. */
    const val DOUBLE_TAP_MS = 350L

    fun initialState(def: KeyboardDef): KeyboardState = KeyboardState(layerId = def.startLayer)

    fun currentLayer(def: KeyboardDef, state: KeyboardState): Layer =
        def.layer(state.layerId) ?: def.layer(def.startLayer) ?: def.layers.first()

    /**
     * Shift only counts on layers that have a shift key. Otherwise caps lock would
     * follow you to the symbols layer and turn "π" into "Π".
     */
    fun effectiveShift(def: KeyboardDef, state: KeyboardState): ShiftState {
        val hasShiftKey = currentLayer(def, state).rows.any { row -> row.keys.any { it.action == KeyAction.Shift } }
        return if (hasShiftKey) state.shift else ShiftState.OFF
    }

    fun press(def: KeyboardDef, state: KeyboardState, key: KeyDef, nowMs: Long): PressResult =
        when (val action = key.action) {
            KeyAction.TypeLabel -> typed(state, key.displayLabel(effectiveShift(def, state)))
            is KeyAction.Text -> typed(state, action.text)
            KeyAction.Space -> typed(state, " ")
            KeyAction.Backspace -> PressResult(state, listOf(Effect.DeleteBackward))
            KeyAction.Enter -> PressResult(state, listOf(Effect.Enter))
            KeyAction.Shift -> PressResult(shiftTapped(state, nowMs))
            is KeyAction.SwitchLayer ->
                if (def.layer(action.layer) == null) PressResult(state)
                else PressResult(
                    state.copy(
                        layerId = action.layer,
                        shift = if (state.shift == ShiftState.LOCKED) ShiftState.LOCKED else ShiftState.OFF,
                    )
                )
            is KeyAction.Arrow -> PressResult(state, listOf(Effect.MoveCursor(action.direction)))
            is KeyAction.Command -> PressResult(state, listOf(Effect.Command(action.command)))
            is KeyAction.OpenPanel ->
                if (action.panel in PANELS) PressResult(state.copy(panel = action.panel)) else PressResult(state)
            KeyAction.None -> PressResult(state)
        }

    /**
     * How tall the keyboard is in dp, strip included. Panels use the same height, so the
     * keyboard does not jump when one opens.
     */
    fun heightDp(def: KeyboardDef, state: KeyboardState): Float {
        val rows = currentLayer(def, state).rows.sumOf { (def.theme.keyHeightDp * it.height).toDouble() }.toFloat()
        return rows + def.theme.keyGapDp + if (def.toolbar) TOOLBAR_HEIGHT_DP else 0f
    }

    /**
     * Used while typing a GIF search: the keys fill in the search words instead of the
     * app's text field. Returns the new words and whether enter was pressed.
     */
    fun typeIntoQuery(query: String, effects: List<Effect>): Pair<String, Boolean> {
        var text = query
        var submitted = false
        for (effect in effects) {
            when (effect) {
                is Effect.Commit -> text += effect.text
                Effect.DeleteBackward -> if (text.isNotEmpty()) {
                    // Remove one whole character, even when it is stored as two parts (emoji).
                    text = text.substring(0, text.offsetByCodePoints(text.length, -1))
                }
                Effect.Enter -> submitted = true
                is Effect.MoveCursor, is Effect.Command -> Unit // the search words have no cursor
            }
        }
        return text to submitted
    }

    /**
     * The choices offered when [key] is held down, as they should be shown and typed:
     * in capitals while shift is on.
     */
    fun alternatesFor(def: KeyboardDef, state: KeyboardState, key: KeyDef): List<String> {
        if (key.alternates.isEmpty()) return emptyList()
        val capitals = key.action == KeyAction.TypeLabel && effectiveShift(def, state) != ShiftState.OFF
        return if (capitals) key.alternates.map { it.uppercase() } else key.alternates
    }

    /**
     * Everything a key offers when held down. Choices you set yourself win. Otherwise, if the
     * keyboard allows built-in choices, a typing key gets a digit (on a top row of letters)
     * followed by accents or related marks. Only your own first choice or the digit is shown
     * as the corner hint; accents on every vowel would just be clutter.
     */
    fun choicesFor(def: KeyboardDef, state: KeyboardState, key: KeyDef, rowIndex: Int, keyIndex: Int): KeyChoices {
        val capitals = key.action == KeyAction.TypeLabel && effectiveShift(def, state) != ShiftState.OFF
        fun shown(text: String) = if (capitals) text.uppercase() else text

        if (key.alternates.isNotEmpty()) {
            val all = key.alternates.map { shown(it) }
            return KeyChoices(all, all.first())
        }
        if (!def.builtInChoices || key.action != KeyAction.TypeLabel || key.look == KeyLook.SPACER) return KeyChoices()

        val digit = if (rowIndex == 0) topRowDigit(currentLayer(def, state), keyIndex) else null
        val all = (listOfNotNull(digit) + BuiltInChoices.forLabel(key.label)).map { shown(it) }.distinct()
        return KeyChoices(all.take(MAX_ALTERNATES), digit)
    }

    /** 1 2 3 ... 0 for the first ten single-letter keys of a layer's top row; null for any other key. */
    private fun topRowDigit(layer: Layer, keyIndex: Int): String? {
        val row = layer.rows.firstOrNull() ?: return null
        var seen = 0
        for ((index, key) in row.keys.withIndex()) {
            val letter = key.action == KeyAction.TypeLabel && key.look != KeyLook.SPACER &&
                key.label.length == 1 && key.label[0].isLetter()
            if (!letter) continue
            if (index == keyIndex) return if (seen < 10) "1234567890"[seen].toString() else null
            seen++
        }
        return null
    }

    /**
     * Holding the space bar and sliding moves the cursor. Given how far the finger has
     * moved since the last step and how long one step is, this says how many steps to take
     * now (negative means the other way). Partial steps wait until the finger has moved further.
     */
    fun cursorSteps(distance: Float, stepLength: Float): Int =
        if (stepLength <= 0f) 0 else (distance / stepLength).toInt()

    /**
     * Dragging a panel's handle. [startExtra] is how much taller than normal the panel was
     * when the drag began, [pulledUp] how far the finger has moved up since (down is negative).
     * The result moves in small steps, so the screen is not rearranged for every pixel.
     */
    fun resizePanel(startExtra: Float, pulledUp: Float, maxExtra: Float, step: Float = 8f): Float {
        val wanted = (startExtra + pulledUp).coerceIn(0f, maxExtra.coerceAtLeast(0f))
        if (step <= 0f) return wanted
        val stepped = Math.round(wanted / step) * step
        return stepped.coerceIn(0f, maxExtra.coerceAtLeast(0f))
    }

    /**
     * While a panel is open the keyboard's window is as tall as the panel can ever get, and
     * the panel sits at its bottom; the space above is see-through. Android must be told
     * where things really are, measured from the top of the window:
     *  - first: where the app's own content should end. This stays at the normal keyboard
     *    height, so the app is not rearranged while the panel is dragged.
     *  - second: where the panel's top edge is right now. Touches above it go to the app.
     */
    fun panelInsetTops(viewTop: Int, viewHeight: Int, basePx: Float, shownPx: Float): Pair<Int, Int> {
        val bottom = viewTop + viewHeight
        val contentTop = (bottom - Math.round(basePx)).coerceIn(viewTop, bottom)
        val shownTop = (bottom - Math.round(shownPx)).coerceIn(viewTop, bottom)
        return contentTop to shownTop
    }

    /** Types a long-press choice. Like any typed character it uses up a single shift. */
    fun pressAlternate(state: KeyboardState, text: String): PressResult = typed(state, text)

    /** In a box that only takes numbers, start on the keyboard's number layer if it has one. */
    fun stateForField(def: KeyboardDef, state: KeyboardState, numbersOnly: Boolean): KeyboardState {
        val layer = def.numberLayer
        return if (numbersOnly && layer != null && def.layer(layer) != null) state.copy(layerId = layer) else state
    }

    /**
     * The "two spaces make a full stop" rule. [beforeCursor] is the text just before the
     * cursor, ending with the first space; true means the second space should turn
     * "word " into "word. ".
     */
    fun doubleSpaceMakesFullStop(beforeCursor: String): Boolean {
        if (beforeCursor.length < 2 || beforeCursor.last() != ' ') return false
        val previous = beforeCursor[beforeCursor.length - 2]
        return previous.isLetterOrDigit() || previous in ")]}\"'"
    }

    /** Where the pop-up above a key starts, so it is centred on the key but stays on screen. */
    fun popupLeft(keyLeft: Float, keyWidth: Float, count: Int, cellWidth: Float, keyboardWidth: Float): Float {
        val width = count * cellWidth
        val centred = keyLeft + keyWidth / 2f - width / 2f
        return centred.coerceIn(0f, (keyboardWidth - width).coerceAtLeast(0f))
    }

    /** Which choice is under the finger. Sliding past either end keeps the last one on that side. */
    fun popupIndex(fingerX: Float, popupLeft: Float, cellWidth: Float, count: Int): Int {
        if (count <= 0 || cellWidth <= 0f) return 0
        if (fingerX < popupLeft) return 0
        return ((fingerX - popupLeft) / cellWidth).toInt().coerceIn(0, count - 1)
    }

    /**
     * Called after typing with whether the text field wants a capital next
     * (start of a sentence). Caps lock is left alone.
     */
    fun withAutoCaps(state: KeyboardState, capitalizeNext: Boolean): KeyboardState =
        if (state.shift == ShiftState.LOCKED) state
        else state.copy(shift = if (capitalizeNext) ShiftState.ONCE else ShiftState.OFF)

    private fun typed(state: KeyboardState, text: String): PressResult {
        if (text.isEmpty()) return PressResult(state)
        val next = if (state.shift == ShiftState.ONCE) state.copy(shift = ShiftState.OFF) else state
        return PressResult(next, listOf(Effect.Commit(text)))
    }

    private fun shiftTapped(state: KeyboardState, nowMs: Long): KeyboardState {
        val last = state.shiftTappedAtMs
        val isDoubleTap = last != null && nowMs - last in 0..DOUBLE_TAP_MS
        val next = when {
            state.shift == ShiftState.LOCKED -> ShiftState.OFF
            isDoubleTap -> ShiftState.LOCKED
            state.shift == ShiftState.OFF -> ShiftState.ONCE
            else -> ShiftState.OFF
        }
        // Forget the tap time once caps lock changes, so a third quick tap is a fresh start.
        val remembered = if (next == ShiftState.LOCKED || state.shift == ShiftState.LOCKED) null else nowMs
        return state.copy(shift = next, shiftTappedAtMs = remembered)
    }
}
