package app.keybuilder

import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.ShiftState
import app.keybuilder.model.ARROWS
import app.keybuilder.model.ActionKind
import app.keybuilder.model.COMMANDS
import app.keybuilder.model.Giphy
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.MAX_ALTERNATES
import app.keybuilder.model.addRecentEmoji
import app.keybuilder.model.commandName
import app.keybuilder.model.deleteLayer
import app.keybuilder.model.describe
import app.keybuilder.model.formatAlternates
import app.keybuilder.model.insertKey
import app.keybuilder.model.keyAt
import app.keybuilder.model.parseAlternatesText
import app.keybuilder.model.renameLayer
import app.keybuilder.model.withActionKind
import app.keybuilder.model.withAlternates
import app.keybuilder.model.withArrowDirection
import app.keybuilder.model.withCommand
import app.keybuilder.model.withKeyLabel
import app.keybuilder.model.withNumberLayer
import app.keybuilder.model.withNumbersOnTopRow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TypingFeaturesTest {

    private val def = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())
    private val q = KeyPos("letters", 0, 0)
    private val start = KeyboardEngine.initialState(def)

    private fun expectProblem(json: String, expected: String) {
        try {
            LayoutJson.parse(json)
            fail("expected a problem mentioning \"$expected\"")
        } catch (e: LayoutFormatException) {
            assertTrue(e.message, e.message!!.contains(expected))
        }
    }

    // ---------------------------------------------------------------- long-press choices

    @Test
    fun theBuiltInKeyboardHasNumbersOnItsTopRow() {
        val top = def.layer("letters")!!.rows[0].keys
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), top.map { it.alternates.first() })
        assertEquals("qwertyuiop", top.joinToString("") { it.label })
        val stop = def.layer("letters")!!.rows[3].keys.first { it.label == "." }
        assertEquals("?", stop.alternates.first())
        // Tapping still types the letter.
        assertEquals(listOf<Effect>(Effect.Commit("q")), KeyboardEngine.press(def, start, def.keyAt(q)!!, 0).effects)
    }

    @Test
    fun choicesFollowShiftAndUseItUp() {
        val key = KeyDef(label = "e", alternates = listOf("é", "è", "3"))
        assertEquals(listOf("é", "è", "3"), KeyboardEngine.alternatesFor(def, start, key))
        val shifted = start.copy(shift = ShiftState.ONCE)
        assertEquals(listOf("É", "È", "3"), KeyboardEngine.alternatesFor(def, shifted, key))
        assertTrue(KeyboardEngine.alternatesFor(def, start, KeyDef(label = "x")).isEmpty())

        val result = KeyboardEngine.pressAlternate(shifted, "É")
        assertEquals(listOf<Effect>(Effect.Commit("É")), result.effects)
        assertEquals(ShiftState.OFF, result.state.shift)
        assertEquals(ShiftState.LOCKED, KeyboardEngine.pressAlternate(start.copy(shift = ShiftState.LOCKED), "É").state.shift)

        // On the symbols layer there is no shift key, so caps lock does not touch the choices.
        val symbols = start.copy(layerId = "symbols", shift = ShiftState.LOCKED)
        assertEquals(listOf("é"), KeyboardEngine.alternatesFor(def, symbols, KeyDef(label = "e", alternates = listOf("é"))))
    }

    @Test
    fun choicesAreCleanedSavedAndChecked() {
        assertEquals(listOf("1", "!", "é"), parseAlternatesText("  1 !  é 1 "))
        assertTrue(parseAlternatesText("   ").isEmpty())
        assertEquals(MAX_ALTERNATES, parseAlternatesText("a b c d e f g h i j k").size)
        assertEquals("1 ! é", formatAlternates(listOf("1", "!", "é")))
        assertEquals(listOf(":-)", "ok"), parseAlternatesText(":-) ok")) // a choice can be longer than one character

        val changed = def.withAlternates(q, listOf("1", "", "1", "!"))
        assertEquals(listOf("1", "!"), changed.keyAt(q)!!.alternates)
        assertEquals(changed, LayoutJson.parse(LayoutJson.toJson(changed)))
        assertTrue(def.withAlternates(q, emptyList()).keyAt(q)!!.alternates.isEmpty())

        expectProblem("""{"layers":[{"id":"a","rows":[[{"label":"q","alt":"1"}]]}]}""", "\"alt\" must be a list")
        expectProblem("""{"layers":[{"id":"a","rows":[[{"label":"q","alt":[1]}]]}]}""", "every entry")
        // Old files without choices still load.
        assertTrue(LayoutJson.parse("""{"layers":[{"id":"a","rows":[["q"]]}]}""").layers[0].rows[0].keys[0].alternates.isEmpty())
    }

    @Test
    fun numbersCanBeAddedToAnyTopRow() {
        val plain = LayoutJson.parse(
            """{"layers":[{"id":"a","rows":[[{"look":"spacer"},"q",{"label":"w","alt":["ŵ"]},{"action":"shift"},"e"],["a"]]}]}"""
        )
        val numbered = plain.withNumbersOnTopRow("a")
        val top = numbered.layer("a")!!.rows[0].keys
        assertTrue(top[0].alternates.isEmpty()) // the gap is skipped
        assertEquals(listOf("1"), top[1].alternates)
        assertEquals(listOf("2", "ŵ"), top[2].alternates) // existing choices are kept after the digit
        assertTrue(top[3].alternates.isEmpty()) // shift is not a typing key
        assertEquals(listOf("3"), top[4].alternates)
        assertTrue(numbered.layer("a")!!.rows[1].keys[0].alternates.isEmpty())
        // Doing it twice changes nothing, and the built-in keyboard already has them.
        assertEquals(numbered, numbered.withNumbersOnTopRow("a"))
        assertEquals(def, def.withNumbersOnTopRow("letters"))
        assertSame(def, def.withNumbersOnTopRow("missing"))
    }

    // ---------------------------------------------------------------- arrows and commands

    @Test
    fun arrowAndCommandKeysAskTheTextBoxToAct() {
        for (direction in ARROWS) {
            val result = KeyboardEngine.press(def, start, KeyDef(action = KeyAction.Arrow(direction)), 0)
            assertEquals(listOf<Effect>(Effect.MoveCursor(direction)), result.effects)
            assertEquals(start, result.state)
        }
        for (command in COMMANDS) {
            val result = KeyboardEngine.press(def, start, KeyDef(action = KeyAction.Command(command)), 0)
            assertEquals(listOf<Effect>(Effect.Command(command)), result.effects)
        }
        assertEquals(listOf("Copy", "Paste", "Cut", "Select all"), COMMANDS.map { commandName(it) })
    }

    @Test
    fun arrowAndCommandKeysCanBeMadeEditedAndSaved() {
        val edit = def.insertKey(KeyPos("letters", 3, 1), after = false)
        val at = edit.selection!!

        val arrow = edit.def.withActionKind(at, ActionKind.ARROW)
        assertEquals(KeyAction.Arrow("left"), arrow.keyAt(at)!!.action)
        assertEquals("", arrow.keyAt(at)!!.label) // so the arrow icon shows
        assertEquals(KeyLook.FUNCTION, arrow.keyAt(at)!!.look)
        assertEquals("arrow left", arrow.keyAt(at)!!.describe())
        val down = arrow.withArrowDirection(at, "down")
        assertEquals(KeyAction.Arrow("down"), down.keyAt(at)!!.action)
        assertEquals(arrow, arrow.withArrowDirection(at, "sideways"))
        assertEquals(down, LayoutJson.parse(LayoutJson.toJson(down)))

        val command = edit.def.withActionKind(at, ActionKind.COMMAND)
        assertEquals(KeyAction.Command("paste"), command.keyAt(at)!!.action)
        assertEquals("Paste", command.keyAt(at)!!.label)
        val copy = command.withCommand(at, "copy")
        assertEquals("Copy", copy.keyAt(at)!!.label) // named after the command, so the name follows
        assertEquals("mine", command.withKeyLabel(at, "mine").withCommand(at, "cut").keyAt(at)!!.label)
        assertEquals(command, command.withCommand(at, "explode"))
        assertEquals(copy, LayoutJson.parse(LayoutJson.toJson(copy)))

        assertEquals(def, def.withArrowDirection(q, "up")) // not an arrow key
        expectProblem("""{"layers":[{"id":"a","rows":[[{"action":{"type":"arrow","to":"north"}}]]}]}""", "unknown direction")
        expectProblem("""{"layers":[{"id":"a","rows":[[{"action":{"type":"command","to":"format"}}]]}]}""", "unknown command")
    }

    @Test
    fun searchWordsIgnoreArrowsAndCommands() {
        val effects = listOf(Effect.Commit("a"), Effect.MoveCursor("left"), Effect.Command("paste"))
        assertEquals("a" to false, KeyboardEngine.typeIntoQuery("", effects))
    }

    // ---------------------------------------------------------------- number boxes

    @Test
    fun numberBoxesOpenTheNumberLayer() {
        assertEquals("symbols", def.numberLayer)
        assertEquals("symbols", KeyboardEngine.stateForField(def, start, numbersOnly = true).layerId)
        assertEquals("letters", KeyboardEngine.stateForField(def, start, numbersOnly = false).layerId)
        val none = def.withNumberLayer(null)
        assertNull(none.numberLayer)
        assertEquals("letters", KeyboardEngine.stateForField(none, start, numbersOnly = true).layerId)
    }

    @Test
    fun theNumberLayerSurvivesEditsAndSaving() {
        assertEquals("symbols2", def.withNumberLayer("symbols2").numberLayer)
        assertSame(def, def.withNumberLayer("missing"))
        assertEquals("digits", def.renameLayer("symbols", "digits").numberLayer)
        assertNull(def.deleteLayer("symbols").numberLayer)
        assertEquals("symbols", def.deleteLayer("symbols2").numberLayer)

        assertEquals(def, LayoutJson.parse(LayoutJson.toJson(def)))
        val none = def.withNumberLayer(null)
        assertEquals(none, LayoutJson.parse(LayoutJson.toJson(none)))
        expectProblem("""{"numberLayer":"zzz","layers":[{"id":"a","rows":[["q"]]}]}""", "numberLayer")
    }

    // ---------------------------------------------------------------- small typing rules

    @Test
    fun twoSpacesMakeAFullStopOnlyAfterAWord() {
        assertTrue(KeyboardEngine.doubleSpaceMakesFullStop("hello "))
        assertTrue(KeyboardEngine.doubleSpaceMakesFullStop("room 5 "))
        assertTrue(KeyboardEngine.doubleSpaceMakesFullStop("(see below) "))
        assertTrue(KeyboardEngine.doubleSpaceMakesFullStop("नमस्ते दोस्त "))
        assertFalse(KeyboardEngine.doubleSpaceMakesFullStop("hello"))    // no first space yet
        assertFalse(KeyboardEngine.doubleSpaceMakesFullStop("hello. "))  // already ended
        assertFalse(KeyboardEngine.doubleSpaceMakesFullStop("hello  "))  // third space
        assertFalse(KeyboardEngine.doubleSpaceMakesFullStop("what? "))
        assertFalse(KeyboardEngine.doubleSpaceMakesFullStop(" "))
        assertFalse(KeyboardEngine.doubleSpaceMakesFullStop(""))
    }

    @Test
    fun popUpsStayOnScreenAndFollowTheFinger() {
        val cell = 40f
        val width = 360f
        // Centred over a key in the middle: key 160..200, three choices are 120 wide.
        assertEquals(120f, KeyboardEngine.popupLeft(keyLeft = 160f, keyWidth = 40f, count = 3, cellWidth = cell, keyboardWidth = width), 0.01f)
        // Pushed back in at the left and right edges.
        assertEquals(0f, KeyboardEngine.popupLeft(0f, 36f, 5, cell, width), 0.01f)
        assertEquals(width - 200f, KeyboardEngine.popupLeft(324f, 36f, 5, cell, width), 0.01f)
        // Wider than the keyboard: start at the left edge rather than at a negative place.
        assertEquals(0f, KeyboardEngine.popupLeft(100f, 36f, 20, cell, width), 0.01f)

        val left = 120f
        assertEquals(0, KeyboardEngine.popupIndex(125f, left, cell, 3))
        assertEquals(1, KeyboardEngine.popupIndex(170f, left, cell, 3))
        assertEquals(2, KeyboardEngine.popupIndex(239f, left, cell, 3))
        assertEquals(2, KeyboardEngine.popupIndex(900f, left, cell, 3)) // slid far right
        assertEquals(0, KeyboardEngine.popupIndex(-50f, left, cell, 3)) // slid far left
        assertEquals(0, KeyboardEngine.popupIndex(10f, left, cell, 0))
    }

    @Test
    fun themeHintSettingIsSaved() {
        assertTrue(def.theme.hints)
        val off = def.copy(theme = def.theme.copy(hints = false))
        assertFalse(LayoutJson.parse(LayoutJson.toJson(off)).theme.hints)
    }

    // ---------------------------------------------------------------- panels

    @Test
    fun recentEmojiAreNewestFirstWithoutRepeats() {
        var recent = emptyList<String>()
        for (e in listOf("😀", "👍", "😀", "🔥")) recent = addRecentEmoji(recent, e)
        assertEquals(listOf("🔥", "😀", "👍"), recent)
        var many = emptyList<String>()
        repeat(50) { many = addRecentEmoji(many, "e$it", limit = 32) }
        assertEquals(32, many.size)
        assertEquals("e49", many.first())
    }

    @Test
    fun gifPagesAskForTheNextBatch() {
        assertTrue(Giphy.trendingUrl("k").endsWith("&offset=0"))
        assertTrue(Giphy.trendingUrl("k", offset = 24).endsWith("&offset=24"))
        assertTrue(Giphy.searchUrl("k", "cat", offset = 48).endsWith("&offset=48"))
    }
}
