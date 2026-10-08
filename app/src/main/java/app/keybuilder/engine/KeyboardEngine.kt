package app.keybuilder.engine

import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyboardDef
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
}

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
            }
        }
        return text to submitted
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
