package app.keybuilder

import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.ShiftState
import app.keybuilder.model.ARROWS
import app.keybuilder.model.ActionKind
import app.keybuilder.model.BuiltInChoices
import app.keybuilder.model.COMMANDS
import app.keybuilder.model.Giphy
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.MAX_ALTERNATES
import app.keybuilder.model.SymbolData
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
import app.keybuilder.model.withAddedAlternate
import app.keybuilder.model.withAlternates
import app.keybuilder.model.withArrowDirection
import app.keybuilder.model.withCommand
import app.keybuilder.model.withKeyLabel
import app.keybuilder.model.withKeySymbol
import app.keybuilder.model.withNumberLayer
import app.keybuilder.model.withNumbersOnTopRow
import app.keybuilder.model.withShiftLabel
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
    fun everyKeyboardGetsChoicesWithoutAnySetup() {
        // The top row of letters offers 1 to 0, shown as the corner hint, then accents.
        val top = def.layer("letters")!!.rows[0].keys
        val hints = top.indices.map { KeyboardEngine.choicesFor(def, start, top[it], 0, it).hint }
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), hints)
        val e = KeyboardEngine.choicesFor(def, start, top[2], 0, 2)
        assertEquals("3", e.all.first())
        assertTrue(e.all.containsAll(listOf("è", "é", "ê")))
        assertTrue(e.all.size <= MAX_ALTERNATES)

        // Other rows: accents but no digit and no hint.
        val a = def.layer("letters")!!.rows[1].keys[1]
        val aChoices = KeyboardEngine.choicesFor(def, start, a, 1, 1)
        assertEquals("à", aChoices.all.first())
        assertNull(aChoices.hint)
        // Letters without accents offer nothing.
        val k = def.layer("letters")!!.rows[1].keys.first { it.label == "k" }
        assertTrue(KeyboardEngine.choicesFor(def, start, k, 1, 8).all.isEmpty())
        // The full stop offers other punctuation.
        val stop = def.layer("letters")!!.rows[3].keys.first { it.label == "." }
        assertEquals("?", KeyboardEngine.choicesFor(def, start, stop, 3, 3).all.first())
        // Keys that do something else, and gaps, offer nothing.
        val shiftKey = def.keyAt(KeyPos("letters", 2, 0))!!
        assertTrue(KeyboardEngine.choicesFor(def, start, shiftKey, 2, 0).all.isEmpty())
        assertTrue(KeyboardEngine.choicesFor(def, start, def.layer("letters")!!.rows[1].keys[0], 1, 0).all.isEmpty())

        // Tapping still types the letter.
        assertEquals(listOf<Effect>(Effect.Commit("q")), KeyboardEngine.press(def, start, def.keyAt(q)!!, 0).effects)
    }

    @Test
    fun builtInChoicesFollowShiftAndCanBeSwitchedOff() {
        val e = def.layer("letters")!!.rows[0].keys[2]
        val shifted = KeyboardEngine.choicesFor(def, start.copy(shift = ShiftState.ONCE), e, 0, 2)
        assertEquals("3", shifted.all.first())
        assertTrue(shifted.all.contains("É"))
        assertFalse(shifted.all.contains("é"))

        val off = def.copy(builtInChoices = false)
        assertTrue(KeyboardEngine.choicesFor(off, start, e, 0, 2).all.isEmpty())
        assertFalse(LayoutJson.parse(LayoutJson.toJson(off)).builtInChoices)
        assertTrue(def.builtInChoices) // on unless a file says otherwise
    }

    @Test
    fun yourOwnChoicesReplaceTheBuiltInOnes() {
        val mine = def.withAlternates(KeyPos("letters", 0, 2), listOf("€", "ε"))
        val key = mine.keyAt(KeyPos("letters", 0, 2))!!
        val choices = KeyboardEngine.choicesFor(mine, start, key, 0, 2)
        assertEquals(listOf("€", "ε"), choices.all)
        assertEquals("€", choices.hint)
        // Even with built-in choices off.
        assertEquals(listOf("€", "ε"), KeyboardEngine.choicesFor(mine.copy(builtInChoices = false), start, key, 0, 2).all)
    }

    @Test
    fun digitsOnlyGoOnATopRowOfLetters() {
        // The symbols layer's top row is already digits: no digit hints there.
        val symbols = start.copy(layerId = "symbols")
        val one = def.layer("symbols")!!.rows[0].keys[0]
        assertNull(KeyboardEngine.choicesFor(def, symbols, one, 0, 0).hint)
        assertEquals("¹", KeyboardEngine.choicesFor(def, symbols, one, 0, 0).all.first())
        // Gaps and other keys in the row are skipped when counting.
        val mixed = LayoutJson.parse(
            """{"layers":[{"id":"a","rows":[[{"look":"spacer"},"q",{"action":"shift"},"w"],["a"]]}]}"""
        )
        val row = mixed.layers[0].rows[0].keys
        val s = KeyboardEngine.initialState(mixed)
        assertEquals("1", KeyboardEngine.choicesFor(mixed, s, row[1], 0, 1).hint)
        assertEquals("2", KeyboardEngine.choicesFor(mixed, s, row[3], 0, 3).hint)
        assertNull(KeyboardEngine.choicesFor(mixed, s, row[2], 0, 2).hint)
    }

    @Test
    fun builtInTableIsSane() {
        for (label in BuiltInChoices.labels) {
            val choices = BuiltInChoices.forLabel(label)
            assertTrue(label, choices.isNotEmpty())
            assertEquals(label, choices.size, choices.toSet().size)
            assertTrue(label, choices.none { it.isBlank() || it == label })
        }
        assertEquals(BuiltInChoices.forLabel("e"), BuiltInChoices.forLabel("E"))
        assertTrue(BuiltInChoices.forLabel("k").isEmpty())
    }

    @Test
    fun slidingOnTheSpaceBarMovesTheCursorInWholeSteps() {
        assertEquals(0, KeyboardEngine.cursorSteps(9f, 10f))
        assertEquals(1, KeyboardEngine.cursorSteps(10f, 10f))
        assertEquals(3, KeyboardEngine.cursorSteps(35f, 10f))
        assertEquals(-2, KeyboardEngine.cursorSteps(-29f, 10f))
        assertEquals(0, KeyboardEngine.cursorSteps(-9f, 10f))
        assertEquals(0, KeyboardEngine.cursorSteps(50f, 0f))
    }

    @Test
    fun panelsGrowAndShrinkWithinLimits() {
        assertEquals(0f, KeyboardEngine.resizePanel(0f, 0f, 300f), 0.01f)
        assertEquals(104f, KeyboardEngine.resizePanel(0f, 100f, 300f), 0.01f)   // nearest step of 8
        assertEquals(300f, KeyboardEngine.resizePanel(0f, 900f, 300f), 0.01f)   // cannot pass the limit
        assertEquals(0f, KeyboardEngine.resizePanel(40f, -200f, 300f), 0.01f)   // cannot go below normal
        assertEquals(160f, KeyboardEngine.resizePanel(200f, -40f, 300f), 0.01f)
        assertEquals(0f, KeyboardEngine.resizePanel(0f, 100f, -50f), 0.01f)     // a screen too small to grow
        assertEquals(101f, KeyboardEngine.resizePanel(1f, 100f, 300f, step = 0f), 0.01f)
    }

    @Test
    fun symbolCatalogueIsCleanAndCanBePutOnKeys() {
        assertTrue(SymbolData.groups.size >= 8)
        assertEquals(SymbolData.groups.size, SymbolData.groups.map { it.name }.toSet().size)
        for (group in SymbolData.groups) {
            assertTrue(group.name, group.symbols.size >= 8)
            assertEquals(group.name, group.symbols.size, group.symbols.toSet().size)
            assertTrue(group.name, group.symbols.none { it.isBlank() })
        }

        // Replace a letter with a symbol: it shows and types the symbol, shift included.
        val shiftedQ = def.withShiftLabel(q, "Q!").withKeySymbol(q, "₹")
        val key = shiftedQ.keyAt(q)!!
        assertEquals("₹", key.label)
        assertNull(key.shiftLabel)
        assertEquals(listOf<Effect>(Effect.Commit("₹")), KeyboardEngine.press(shiftedQ, start, key, 0).effects)
        assertEquals(shiftedQ, LayoutJson.parse(LayoutJson.toJson(shiftedQ)))
        // And back to a letter.
        assertEquals(def, shiftedQ.withKeySymbol(q, "q"))

        // Add long-press choices one at a time from the picker.
        var kb = def.withAddedAlternate(q, "√").withAddedAlternate(q, "π").withAddedAlternate(q, "√")
        assertEquals(listOf("√", "π"), kb.keyAt(q)!!.alternates)
        repeat(20) { kb = kb.withAddedAlternate(q, "x$it") }
        assertEquals(MAX_ALTERNATES, kb.keyAt(q)!!.alternates.size)
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
        // Doing it twice changes nothing.
        assertEquals(numbered, numbered.withNumbersOnTopRow("a"))
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
