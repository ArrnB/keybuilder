package app.keybuilder

import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.KeyboardState
import app.keybuilder.engine.ShiftState
import app.keybuilder.engine.displayLabel
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutJson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEngineTest {

    private val def: KeyboardDef = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())

    /** A tiny pretend phone: remembers the state and collects what was typed. */
    private inner class Typist {
        var state: KeyboardState = KeyboardEngine.initialState(def)
        val text = StringBuilder()
        var enters = 0
        var now = 10_000L

        fun tap(find: (KeyDef) -> Boolean, afterMs: Long = 1_000L): Typist {
            now += afterMs
            val key = KeyboardEngine.currentLayer(def, state).rows.flatMap { it.keys }.first(find)
            val result = KeyboardEngine.press(def, state, key, now)
            state = result.state
            for (effect in result.effects) {
                when (effect) {
                    is Effect.Commit -> text.append(effect.text)
                    Effect.DeleteBackward -> if (text.isNotEmpty()) text.deleteCharAt(text.length - 1)
                    Effect.Enter -> enters++
                }
            }
            return this
        }

        fun type(labels: String): Typist {
            labels.forEach { c -> tap({ it.label == c.toString() }) }
            return this
        }

        fun shift(afterMs: Long = 1_000L) = tap({ it.action == KeyAction.Shift }, afterMs)
        fun space() = tap({ it.action == KeyAction.Space })
        fun backspace() = tap({ it.action == KeyAction.Backspace })
        fun enter() = tap({ it.action == KeyAction.Enter })
        fun layer(id: String) = tap({ it.action == KeyAction.SwitchLayer(id) })
    }

    @Test
    fun startsOnTheStartLayer() {
        assertEquals("letters", KeyboardEngine.initialState(def).layerId)
    }

    @Test
    fun typesLettersSpaceAndPunctuation() {
        val t = Typist().type("hi").space().type("there").type(".")
        assertEquals("hi there.", t.text.toString())
    }

    @Test
    fun backspaceAndEnter() {
        val t = Typist().type("abc").backspace().enter()
        assertEquals("ab", t.text.toString())
        assertEquals(1, t.enters)
    }

    @Test
    fun shiftCapitalisesOneLetter() {
        val t = Typist().shift().type("hi")
        assertEquals("Hi", t.text.toString())
        assertEquals(ShiftState.OFF, t.state.shift)
    }

    @Test
    fun doubleTapShiftIsCapsLockAndOneMoreTapEndsIt() {
        val t = Typist().shift().shift(afterMs = 200)
        assertEquals(ShiftState.LOCKED, t.state.shift)
        t.type("abc")
        assertEquals("ABC", t.text.toString())
        assertEquals(ShiftState.LOCKED, t.state.shift)
        t.shift().type("d")
        assertEquals("ABCd", t.text.toString())
    }

    @Test
    fun twoSlowShiftTapsJustTurnItOffAgain() {
        val t = Typist().shift().shift(afterMs = 2_000)
        assertEquals(ShiftState.OFF, t.state.shift)
    }

    @Test
    fun tapAfterLeavingCapsLockDoesNotBounceBackIntoIt() {
        val t = Typist().shift().shift(afterMs = 200).shift(afterMs = 200)
        assertEquals(ShiftState.OFF, t.state.shift)
        t.shift(afterMs = 200)
        assertEquals(ShiftState.ONCE, t.state.shift)
    }

    @Test
    fun layersSwitchThereAndBack() {
        val t = Typist().type("a").layer("symbols").type("1@").layer("symbols2").type("π").layer("letters").type("b")
        assertEquals("a1@πb", t.text.toString())
        assertEquals("letters", t.state.layerId)
    }

    @Test
    fun capsLockDoesNotChangeSymbols() {
        val t = Typist().shift().shift(afterMs = 200).layer("symbols").layer("symbols2")
        assertEquals(ShiftState.OFF, KeyboardEngine.effectiveShift(def, t.state))
        t.type("π")
        assertEquals("π", t.text.toString())
        t.layer("letters").type("a")
        assertEquals("πA", t.text.toString())
    }

    @Test
    fun autoCapitalsFollowTheTextFieldButNotCapsLock() {
        val off = KeyboardEngine.initialState(def)
        assertEquals(ShiftState.ONCE, KeyboardEngine.withAutoCaps(off, capitalizeNext = true).shift)
        assertEquals(ShiftState.OFF, KeyboardEngine.withAutoCaps(off.copy(shift = ShiftState.ONCE), false).shift)
        val locked = off.copy(shift = ShiftState.LOCKED)
        assertEquals(ShiftState.LOCKED, KeyboardEngine.withAutoCaps(locked, false).shift)
    }

    @Test
    fun labelsFollowShift() {
        assertEquals("q", KeyDef(label = "q").displayLabel(ShiftState.OFF))
        assertEquals("Q", KeyDef(label = "q").displayLabel(ShiftState.ONCE))
        assertEquals("?", KeyDef(label = "/", shiftLabel = "?").displayLabel(ShiftState.LOCKED))
        // Keys that do something else keep their label.
        assertEquals("abc", KeyDef(label = "abc", action = KeyAction.SwitchLayer("x")).displayLabel(ShiftState.ONCE))
    }

    @Test
    fun textKeysTypeTheirWholeTextWhateverShiftSays() {
        val snippet = KeyDef(label = "@", action = KeyAction.Text("me@example.com"))
        val state = KeyboardEngine.initialState(def).copy(shift = ShiftState.LOCKED)
        val result = KeyboardEngine.press(def, state, snippet, nowMs = 0)
        assertEquals(listOf<Effect>(Effect.Commit("me@example.com")), result.effects)
    }

    @Test
    fun spacersAndUnknownLayersDoNothing() {
        val state = KeyboardEngine.initialState(def)
        val spacer = KeyDef(look = KeyLook.SPACER)
        assertEquals(state, KeyboardEngine.press(def, state, spacer, 0).state)
        assertTrue(KeyboardEngine.press(def, state, spacer, 0).effects.isEmpty())
        val broken = KeyDef(label = "x", action = KeyAction.SwitchLayer("missing"))
        assertEquals(state, KeyboardEngine.press(def, state, broken, 0).state)
    }

    @Test
    fun everyKeyOnEveryLayerCanBePressed() {
        for (layer in def.layers) {
            val state = KeyboardState(layerId = layer.id)
            for (key in layer.rows.flatMap { it.keys }) {
                val result = KeyboardEngine.press(def, state, key, 0)
                // Wherever a key sends you, that layer exists.
                assertTrue(def.layer(result.state.layerId) != null)
            }
        }
    }
}
