package app.keybuilder

import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.KeyboardState
import app.keybuilder.model.ARROWS
import app.keybuilder.model.ActionKind
import app.keybuilder.model.COMMANDS
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.ClipHistoryJson
import app.keybuilder.model.FONT_FAMILIES
import app.keybuilder.model.GifItem
import app.keybuilder.model.Giphy
import app.keybuilder.model.KEY_ANIMATIONS
import app.keybuilder.model.KEY_SOUNDS
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyStyle
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.PANELS
import app.keybuilder.model.SavedGifs
import app.keybuilder.model.Shortcuts
import app.keybuilder.model.ThemePresets
import app.keybuilder.model.addLayer
import app.keybuilder.model.argbToHsv
import app.keybuilder.model.deleteKey
import app.keybuilder.model.deleteLayer
import app.keybuilder.model.deleteRow
import app.keybuilder.model.duplicateKey
import app.keybuilder.model.duplicateLayer
import app.keybuilder.model.formatHexColor
import app.keybuilder.model.formatNumber
import app.keybuilder.model.opacityPercent
import app.keybuilder.model.snapKeyWidth
import app.keybuilder.model.snapRowHeight
import app.keybuilder.model.ThemeSize
import app.keybuilder.model.Theme
import app.keybuilder.model.withKeyOpacity
import app.keybuilder.model.withOpacity
import app.keybuilder.model.hsvToArgb
import app.keybuilder.model.insertKey
import app.keybuilder.model.insertRow
import app.keybuilder.model.keyAt
import app.keybuilder.model.moveKey
import app.keybuilder.model.moveKeyToRow
import app.keybuilder.model.moveRow
import app.keybuilder.model.parseHexColor
import app.keybuilder.model.quickClipLabel
import app.keybuilder.model.renameLayer
import app.keybuilder.model.replaceKey
import app.keybuilder.model.scaledForHeight
import app.keybuilder.model.shortcutProblem
import app.keybuilder.model.specialKeys
import app.keybuilder.model.swapKeys
import app.keybuilder.model.warnings
import app.keybuilder.model.withActionKind
import app.keybuilder.model.withAlternates
import app.keybuilder.model.withArrowDirection
import app.keybuilder.model.withBackgroundBlur
import app.keybuilder.model.withBackgroundOpacity
import app.keybuilder.model.withCommand
import app.keybuilder.model.withGradient
import app.keybuilder.model.withKeyLabel
import app.keybuilder.model.withKeyLook
import app.keybuilder.model.withKeyStyle
import app.keybuilder.model.withKeyText
import app.keybuilder.model.withKeyWidth
import app.keybuilder.model.withLayerTarget
import app.keybuilder.model.withLook
import app.keybuilder.model.withNumberLayer
import app.keybuilder.model.withNumbersOnTopRow
import app.keybuilder.model.withPanelTarget
import app.keybuilder.model.withRowHeight
import app.keybuilder.model.withShiftLabel
import app.keybuilder.model.withStartLayer
import app.keybuilder.suggest.FieldRules
import app.keybuilder.suggest.Lexicon
import app.keybuilder.suggest.SuggestionEngine
import app.keybuilder.suggest.SuggestionKind
import app.keybuilder.suggest.TypingContext
import app.keybuilder.suggest.TypingSession
import app.keybuilder.suggest.TypingSwitches
import app.keybuilder.suggest.Typo
import app.keybuilder.suggest.UserWords
import app.keybuilder.suggest.fitSuggestions
import java.io.File
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Stress tests: thousands of random actions thrown at each part, checking after every
 * one that nothing crashed and that a few things which must always be true still are.
 *
 * The random numbers come from fixed seeds, so a failure can be repeated exactly. As it
 * stands this takes a few seconds. To dig deeper, raise ROUNDS below (200 takes a quarter
 * of an hour and is what was run before this version was handed over).
 */
class StressTest {

    private companion object {
        /** Multiplies every loop count. */
        val ROUNDS: Int = (System.getProperty("stress")?.toIntOrNull() ?: 1).coerceIn(1, 1000)

        val lexicon: Lexicon by lazy { File("src/main/assets/words/words.txt").useLines { Lexicon.parse(it) } }

        /** Characters a person, a paste or a careless app might produce, plain ones most often. */
        const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
        val ODD = listOf(
            "'", "’", "-", "_", "@", "#", "/", ".", ",", "!", "?", ";", ":", ")", "(", "\"", "\n", "\t", " ", "  ",
            "0", "7", "A", "Z", "é", "ñ", "ß", "क", "ा", "中", "😀", "👍🏽", "\uD83D", "\uDE00", "‍", "\u0000", "﻿", "…",
        )
        val WORDS = listOf(
            "the", "teh", "hello", "helo", "tomorrow", "tomorow", "becuase", "nahi", "nhi", "kya", "krta", "omw", "@@", "i", "dont",
            "Aryan", "NASA", "sooo", "recieve", "kal", "milte", "tmrw", "a", "to", "thank", "you", "zomato",
        )
    }

    private val qwerty = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())

    private fun Random.pick(text: String): Char = text[nextInt(text.length)]
    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    /** A random string: mostly letters, sometimes anything at all. */
    private fun Random.text(maxLength: Int): String {
        val out = StringBuilder()
        repeat(nextInt(maxLength + 1)) {
            if (nextInt(5) == 0) out.append(pick(ODD)) else out.append(pick(LETTERS))
        }
        return out.toString()
    }

    private fun Random.position(def: KeyboardDef): KeyPos {
        val layer = pick(def.layers)
        // Now and then a place that does not exist: edits must shrug those off.
        if (nextInt(12) == 0) return KeyPos(if (nextBoolean()) layer.id else "nowhere", nextInt(9) - 2, nextInt(14) - 2)
        val row = nextInt(layer.rows.size)
        return KeyPos(layer.id, row, nextInt(layer.rows[row].keys.size.coerceAtLeast(1)))
    }

    // ------------------------------------------------------------------ the layout editor

    /** One random edit, of every kind the layout and style editors can make. */
    private fun Random.edit(def: KeyboardDef): KeyboardDef {
        val pos = position(def)
        val other = position(def)
        val layerId = pick(def.layers).id
        return when (nextInt(34)) {
            0 -> def.insertKey(pos, nextBoolean()).def
            1 -> def.insertKey(pos, nextBoolean(), KeyDef(label = text(3))).def
            2 -> def.duplicateKey(pos).def
            3 -> def.deleteKey(pos).def
            4 -> def.moveKey(pos, nextInt(5) - 2).def
            5 -> def.moveKeyToRow(pos, nextInt(5) - 2).def
            6 -> def.withKeyWidth(pos, nextFloat() * 10f - 2f)
            7 -> def.withKeyLabel(pos, text(4))
            8 -> def.withShiftLabel(pos, text(3))
            9 -> def.withKeyLook(pos, pick(KeyLook.entries))
            10 -> def.withKeyText(pos, text(30))
            11 -> def.replaceKey(pos, pick(def.specialKeys()).key)
            12 -> def.swapKeys(pos, other)
            13 -> def.withActionKind(pos, pick(ActionKind.entries))
            14 -> def.withArrowDirection(pos, if (nextInt(6) == 0) "sideways" else pick(ARROWS))
            15 -> def.withCommand(pos, if (nextInt(6) == 0) "explode" else pick(COMMANDS))
            16 -> def.withAlternates(pos, List(nextInt(12)) { text(2) })
            17 -> def.withNumbersOnTopRow(layerId)
            18 -> def.withNumberLayer(if (nextInt(3) == 0) null else if (nextInt(5) == 0) "nowhere" else layerId)
            19 -> def.withPanelTarget(pos, if (nextInt(6) == 0) "nowhere" else pick(PANELS))
            20 -> def.withLayerTarget(pos, if (nextInt(6) == 0) "nowhere" else layerId)
            21 -> def.insertRow(layerId, nextInt(8) - 2).def
            22 -> def.deleteRow(layerId, nextInt(8) - 2).def
            23 -> def.moveRow(pos, nextInt(5) - 2).def
            24 -> def.withRowHeight(layerId, nextInt(8) - 2, nextFloat() * 4f - 1f)
            25 -> if (def.layers.size < 6) def.addLayer("l" + nextInt(1000), layerId) else def
            26 -> if (def.layers.size < 6) def.duplicateLayer(layerId).first else def
            27 -> def.renameLayer(layerId, if (nextInt(4) == 0) text(6) else "r" + nextInt(1000))
            28 -> def.deleteLayer(layerId)
            29 -> def.withStartLayer(if (nextInt(5) == 0) "nowhere" else layerId)
            30 -> def.withKeyStyle(
                pos,
                KeyStyle(
                    background = if (nextBoolean()) formatHexColor(nextInt()) else null,
                    fontSizeSp = if (nextBoolean()) 8f + nextInt(40) else null,
                    sound = if (nextBoolean()) pick(KEY_SOUNDS) else null,
                    animation = if (nextBoolean()) pick(KEY_ANIMATIONS) else null,
                ),
            )
            31 -> def.copy(theme = def.theme.withLook(pick(ThemePresets.all).theme))
            32 -> def.copy(
                theme = def.theme.withGradient(nextBoolean()).withBackgroundOpacity(nextFloat() * 140f - 20f)
                    .withBackgroundBlur(nextFloat() * 60f - 10f)
                    .copy(sound = pick(KEY_SOUNDS), animation = pick(KEY_ANIMATIONS), fontFamily = pick(FONT_FAMILIES)),
            )
            else -> def.copy(toolbar = nextBoolean(), builtInChoices = nextBoolean())
        }
    }

    /** What must be true of a keyboard no matter how it was edited. */
    private fun check(def: KeyboardDef, how: String) {
        assertTrue("no layers after $how", def.layers.isNotEmpty())
        assertTrue("start layer is gone after $how", def.layer(def.startLayer) != null)
        assertEquals("two layers share a name after $how", def.layers.size, def.layers.map { it.id }.toSet().size)
        for (layer in def.layers) {
            assertTrue("a layer without rows after $how", layer.rows.isNotEmpty())
            for (row in layer.rows) {
                assertTrue("an empty row after $how", row.keys.isNotEmpty())
                assertTrue("row height ${row.height} after $how", row.height > 0f && row.height < 10f)
                for (key in row.keys) assertTrue("key width ${key.width} after $how", key.width > 0f && key.width < 20f)
            }
        }
        // It can be saved and comes back the same, which is what the app does after every change.
        val saved = LayoutJson.toJson(def)
        assertEquals("changed by saving and loading after $how", def, LayoutJson.parse(saved))
        def.warnings()
    }

    @Test
    fun randomEditsNeverBreakAKeyboard() {
        for (seed in 1..(6 * ROUNDS)) {
            val random = Random(seed.toLong())
            var def = qwerty
            repeat(250) { step ->
                def = random.edit(def)
                check(def, "seed $seed, step $step")
            }
        }
    }

    // ------------------------------------------------------------------ pressing keys

    @Test
    fun randomKeyPressesOnRandomKeyboardsNeverCrash() {
        for (seed in 1..(8 * ROUNDS)) {
            val random = Random(1000L + seed)
            var def = qwerty
            repeat(40) { def = random.edit(def) }
            var state = KeyboardEngine.initialState(def)
            var clock = 0L
            repeat(600) {
                // Sometimes the keyboard changes under the state, as when the app saves an edit.
                if (random.nextInt(60) == 0) def = random.edit(def)
                val layer = KeyboardEngine.currentLayer(def, state)
                val row = random.nextInt(layer.rows.size)
                val index = random.nextInt(layer.rows[row].keys.size)
                val key = layer.rows[row].keys[index]
                clock += random.nextInt(500)
                val choices = KeyboardEngine.choicesFor(def, state, key, row, index)
                assertTrue(choices.all.size <= 8 || key.alternates.isNotEmpty())
                val result = if (choices.all.isNotEmpty() && random.nextInt(5) == 0) {
                    KeyboardEngine.pressAlternate(state, random.pick(choices.all))
                } else {
                    KeyboardEngine.press(def, state, key, clock)
                }
                state = result.state
                // A panel is shown by the keyboard service and closed again; here it is just closed.
                if (state.panel != null) {
                    assertTrue(state.panel in PANELS)
                    state = state.copy(panel = null)
                }
                // Even if the layer the state was on has just been deleted, there is always a layer to show.
                assertTrue(KeyboardEngine.currentLayer(def, state).rows.isNotEmpty())
                KeyboardEngine.typeIntoQuery(random.text(8), result.effects)
                val height = KeyboardEngine.heightDp(def, state, strip = random.nextBoolean())
                assertTrue("height $height", height > 0f && height < 5000f)
                state = KeyboardEngine.withAutoCaps(state, random.nextBoolean())
                state = KeyboardEngine.stateForField(def, state, random.nextInt(20) == 0)
            }
            // Any height setting, including silly ones, gives a keyboard that can still be measured.
            val sized = def.scaledForHeight(random.nextInt(400) - 100)
            assertTrue(KeyboardEngine.heightDp(sized, KeyboardEngine.initialState(sized)) > 0f)
        }
    }

    // ------------------------------------------------------------------ damaged layout files

    /** Loading must either work or explain the problem; any other kind of failure is a bug. */
    private fun loadsOrExplains(json: String) {
        try {
            val def = LayoutJson.parse(json)
            // Whatever loads can be used and saved again.
            KeyboardEngine.heightDp(def, KeyboardEngine.initialState(def))
            assertEquals(def, LayoutJson.parse(LayoutJson.toJson(def)))
        } catch (e: LayoutFormatException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun aDamagedLayoutFileIsExplainedNeverACrash() {
        val good = LayoutJson.toJson(qwerty)
        val junk = listOf("", " ", "null", "[]", "{}", "7", "\"x\"", "{\"layers\":7}", "{\"layers\":[]}", "{\"layers\":[{}]}", "[[[[[[[[", "{".repeat(3000))
        for (text in junk) loadsOrExplains(text)

        val replacements = listOf("null", "true", "7", "-1", "1e99", "\"\"", "\"x\"", "[]", "{}", "[[]]", "{\"a\":1}", "\"#GGGGGG\"", "0.0000001", "NaN")
        for (seed in 1..(400 * ROUNDS)) {
            val random = Random(2000L + seed)
            val text = StringBuilder(good)
            when (random.nextInt(4)) {
                // Cut a piece out, as a half-written file would look.
                0 -> {
                    val from = random.nextInt(text.length)
                    text.delete(from, (from + 1 + random.nextInt(200)).coerceAtMost(text.length))
                }
                // Swap one value for something of the wrong kind.
                1 -> {
                    val colon = text.indexOf(":", random.nextInt(text.length))
                    if (colon >= 0) {
                        var end = colon + 1
                        while (end < text.length && text[end] !in ",}\n") end++
                        text.replace(colon + 1, end, " " + random.pick(replacements))
                    }
                }
                // Random characters typed into the middle.
                2 -> repeat(1 + random.nextInt(4)) { text.insert(random.nextInt(text.length), random.pick(ODD)) }
                // Cut it short.
                else -> text.setLength(random.nextInt(text.length))
            }
            loadsOrExplains(text.toString())
        }
    }

    // ------------------------------------------------------------------ suggestions on anything at all

    @Test
    fun suggestionsAndCorrectionsCopeWithAnyText() {
        val user = UserWords()
        val engine = SuggestionEngine(lexicon, user)
        var slowestMillis = 0L
        for (seed in 1..(1500 * ROUNDS)) {
            val random = Random(3000L + seed)
            val before = random.text(90) + if (random.nextBoolean()) random.pick(WORDS) else random.text(30)
            val context = TypingContext.from(before)
            assertTrue(before.endsWith(context.word) || context.word.contains('\''))
            assertTrue(context.previous.size <= 3)

            val started = System.nanoTime()
            val offered = engine.suggestions(context, autocorrect = random.nextBoolean(), limit = 3)
            val fix = engine.correction(context)
            slowestMillis = maxOf(slowestMillis, (System.nanoTime() - started) / 1_000_000)

            assertTrue("more than three suggestions for \"$before\"", offered.size <= 3)
            assertEquals("the same suggestion twice for \"$before\"", offered.size, offered.map { it.text }.toSet().size)
            assertTrue(offered.none { it.text.isEmpty() })
            if (fix != null) {
                assertFalse("\"${context.word}\" corrected into itself", fix == context.word)
                assertTrue("a correction with spaces or nothing in it: \"$fix\"", fix.isNotEmpty() && fix.none { it.isWhitespace() })
            }
            // Learning from anything must be safe too, and now and then a correction is undone.
            engine.learn(context, if (random.nextBoolean()) context.word else random.text(30))
            engine.learnChoice(context, random.text(12))
            if (fix != null && random.nextInt(4) == 0) engine.correctionUndone(context, context.word, fix, random.nextBoolean())
            if (random.nextInt(40) == 0) engine.keep(random.text(10), random.nextBoolean())
        }
        // Everything learned along the way can be saved and loaded.
        val text = user.toText()
        assertEquals(text, UserWords.parse(text).toText())
        assertTrue("${user.wordCount} learned words", user.wordCount <= UserWords.MAX_WORDS + 200)
        // One keystroke must never make the keyboard hang. (This machine is faster than a phone; the limit is generous.)
        assertTrue("slowest suggestion took $slowestMillis ms", slowestMillis < 400)
    }

    @Test
    fun theWordListAnswersAnyQuestion() {
        val random = Random(4000)
        repeat(3000 * ROUNDS) {
            val a = random.text(14)
            val b = if (random.nextBoolean()) random.text(14) else random.pick(WORDS)
            val limit = random.nextFloat() * 3f
            val cost = Typo.cost(a, b, limit)
            assertTrue("cost $cost for \"$a\" and \"$b\"", cost == Typo.TOO_FAR || (cost >= 0f && cost <= limit + 0.001f))
            if (a.isNotEmpty()) assertEquals(0f, Typo.cost(a, a, 1f), 0f)
            val found = lexicon.completions(a, 1 + random.nextInt(8))
            assertEquals(found.map { it.score }.sortedDescending(), found.map { it.score })
            assertTrue(found.all { it.word.startsWith(a) && it.word != a })
            for (near in lexicon.near(a.take(10), limit.coerceAtMost(2f))) assertTrue(near.cost <= limit + 0.001f && near.word != a.take(10))
            lexicon.score(a)
            lexicon.isHinglishOnly(a)
        }
    }

    // ------------------------------------------------------------------ whole typing sessions

    /** One of the kinds of text box the keyboard meets. */
    private fun Random.rules(): FieldRules = when (nextInt(6)) {
        0 -> FieldRules.NOTHING
        1 -> FieldRules.SHORTCUTS_ONLY
        2 -> FieldRules(suggest = true, correct = true, learn = false, shortcuts = true)
        3 -> FieldRules(suggest = true, correct = false, learn = true, shortcuts = true)
        else -> FieldRules.EVERYTHING
    }

    @Test
    fun randomTypingNeverCrashesAndNeverReadsAPasswordBox() {
        val shortcuts = Shortcuts().with("omw", "on my way").with("@@", "me@example.com").with(":)", "😀").with("sig", "Regards,\nAryan")
        for (seed in 1..(40 * ROUNDS)) {
            val random = Random(5000L + seed)
            val user = UserWords()
            val engine = SuggestionEngine(lexicon, user)
            val session = TypingSession { if (random.nextInt(50) == 0) null else engine }
            var box = FakeBox()
            var rules = random.rules()
            session.start(rules, TypingSwitches(), shortcuts, selection = false, box = box)
            var readsAtStart = box.reads

            repeat(400) { step ->
                val where = "seed $seed, step $step"
                when (random.nextInt(40)) {
                    // A new text box, sometimes with text already in it and the cursor anywhere.
                    0 -> {
                        val existing = if (random.nextBoolean()) "" else random.text(40)
                        box = FakeBox(existing, cursorAt = random.nextInt(existing.length + 1))
                        box.silent = random.nextInt(15) == 0
                        rules = random.rules()
                        val switches = TypingSwitches(random.nextInt(4) != 0, random.nextInt(4) != 0, random.nextInt(4) != 0)
                        session.start(rules, switches, if (random.nextInt(4) == 0) Shortcuts() else shortcuts, random.nextInt(10) == 0, box)
                        readsAtStart = if (rules.nothing) box.reads else -1
                    }
                    1, 2, 3 -> box.backspace(session)
                    4, 5 -> if (session.suggestions.isNotEmpty()) session.pick(box, random.pick(session.suggestions))
                    6 -> session.selectionChanged(random.nextInt(4) == 0, box)
                    7 -> session.showTools()
                    8 -> session.textChangedOtherwise(box)
                    9 -> session.clear()
                    10 -> session.switchesChanged(TypingSwitches(random.nextBoolean(), random.nextBoolean(), random.nextBoolean()), box)
                    11, 12, 13, 14 -> box.type(session, random.pick(WORDS) + random.pick(listOf(" ", " ", " ", ".", ",", "!", "\n", "")))
                    15 -> box.type(session, random.pick(ODD))
                    else -> {
                        // An ordinary key: after it, that character is what sits before the cursor.
                        val key = random.pick(LETTERS).toString()
                        val length = box.text.length
                        box.type(session, key)
                        assertTrue("typed \"$key\" but the text ends otherwise ($where)", box.text.substring(0, box.cursor).endsWith(key))
                        assertEquals("a letter changed more than itself ($where)", length + 1, box.text.length)
                    }
                }
                assertTrue("the cursor left the text ($where)", box.cursor in 0..box.text.length)
                assertTrue("more than three suggestions ($where)", session.suggestions.size <= 3)
                if (rules.nothing) {
                    assertEquals("a box where nothing is allowed was read ($where)", readsAtStart, box.reads)
                    assertTrue("suggestions in a box where nothing is allowed ($where)", session.suggestions.isEmpty())
                }
                if (!rules.suggest) assertTrue(session.suggestions.none { it.kind == SuggestionKind.WORD || it.kind == SuggestionKind.CORRECTION })
            }
            assertEquals(user.toText(), UserWords.parse(user.toText()).toText())
        }
    }

    @Test
    fun anyAutomaticChangeIsExactlyUndoneByOneBackspace() {
        val shortcuts = Shortcuts().with("omw", "on my way").with("@@", "me@example.com")
        var changes = 0
        for (seed in 1..(60 * ROUNDS)) {
            val random = Random(6000L + seed)
            val engine = SuggestionEngine(lexicon, UserWords())
            val session = TypingSession { engine }
            val box = FakeBox()
            session.start(FieldRules.EVERYTHING, TypingSwitches(), shortcuts, selection = false, box = box)
            repeat(60) {
                val word = if (random.nextInt(3) == 0) random.text(9).filter { it in LETTERS } else random.pick(WORDS)
                box.type(session, word)
                val typedSoFar = box.text
                val separator = random.pick(listOf(" ", ".", ",", "!", "?"))
                box.type(session, separator)
                if (box.text != typedSoFar + separator) {
                    changes++
                    box.backspace(session)
                    assertEquals("seed $seed: undoing the change to \"$word\"", typedSoFar + separator, box.text)
                }
                if (!box.text.endsWith(" ")) box.type(session, " ")
            }
        }
        assertTrue("the test never saw an automatic change", changes > 50)
    }

    @Test
    fun withEverythingSwitchedOffTheKeyboardTypesExactlyWhatIsPressed() {
        for (seed in 1..(40 * ROUNDS)) {
            val random = Random(7000L + seed)
            val engine = SuggestionEngine(lexicon, UserWords())
            val session = TypingSession { engine }
            val box = FakeBox()
            val off = TypingSwitches(suggestions = random.nextBoolean(), autocorrect = false, learn = random.nextBoolean())
            session.start(FieldRules.EVERYTHING, off, Shortcuts(), selection = false, box = box)
            val expected = StringBuilder()
            repeat(300) {
                if (random.nextInt(8) == 0 && expected.isNotEmpty()) {
                    box.backspace(session)
                    expected.setLength(expected.length - 1)
                } else {
                    val key = if (random.nextInt(6) == 0) random.pick(listOf(" ", ".", ",", "\n", "!")) else random.pick(LETTERS).toString()
                    box.type(session, key)
                    expected.append(key)
                }
            }
            assertEquals("seed $seed", expected.toString(), box.text)
        }
    }

    // ------------------------------------------------------------------ everything that is saved

    @Test
    fun savedDataSurvivesAnyHistoryAndAnyDamage() {
        val random = Random(8000)
        repeat(60 * ROUNDS) { round ->
            // The clipboard history: any mix of copying, pinning, removing and clearing.
            var history = ClipHistory()
            repeat(200) { step ->
                val action = random.nextInt(7)
                val was = history
                history = when (action) {
                    0, 1, 2 -> history.addText(random.text(40), step.toLong())
                    3 -> history.addImage("clip_$step.png", "image/png", step.toLong())
                    4 -> history.togglePin(random.nextInt(step + 1).toLong())
                    5 -> history.remove(random.nextInt(step + 1).toLong())
                    else -> if (random.nextInt(10) == 0) history.clearUnpinned() else history
                }
                // The limit is applied when something is copied. (Unpinning an old item does not
                // throw it away on the spot; it goes when the next thing is copied.)
                if (action <= 3 && history != was) assertTrue(history.entries.count { !it.pinned } <= ClipHistory.MAX_UNPINNED)
                assertEquals(history.entries.size, history.ordered.size)
                assertEquals(history.entries.size, history.entries.map { it.id }.toSet().size)
            }
            assertEquals(history, ClipHistoryJson.parse(ClipHistoryJson.toJson(history)))

            // Shortcuts: any mix of adding, changing and removing.
            var shortcuts = Shortcuts()
            repeat(80) {
                val short = random.text(6)
                val text = random.text(40)
                shortcuts = if (random.nextInt(4) == 0) shortcuts.without(short) else shortcuts.with(short, text)
                assertTrue(shortcuts.items.none { item -> item.short.any { it.isWhitespace() } || item.short.isEmpty() })
                assertEquals(shortcuts.items.size, shortcuts.items.map { it.short.lowercase() }.toSet().size)
                shortcuts.expansion(random.text(6))
                shortcutProblem(short, text, shortcuts)
            }
            assertEquals("round $round", shortcuts, Shortcuts.parse(shortcuts.toJson()))

            // Recent and favourite GIFs.
            var saved = SavedGifs()
            repeat(150) {
                val id = "g" + random.nextInt(60)
                val gif = GifItem(id, random.text(10), "https://x/$id", "https://x/$id.gif", "https://x/p/$id")
                saved = if (random.nextBoolean()) saved.withRecent(gif) else saved.toggleFavourite(gif)
                assertTrue(saved.recent.size <= SavedGifs.MAX_RECENT && saved.favourites.size <= SavedGifs.MAX_FAVOURITES)
                assertEquals(saved.recent.size, saved.recent.map { it.id }.toSet().size)
                assertEquals(saved.favourites.size, saved.favourites.map { it.id }.toSet().size)
            }
            assertEquals(saved.copy(recent = saved.recent.map { it.copy(title = it.title.ifBlank { "GIF" }) }, favourites = saved.favourites.map { it.copy(title = it.title.ifBlank { "GIF" }) }), SavedGifs.parse(saved.toJson()))
        }

        // Files that are damaged or simply something else: reading them gives something usable, never a crash.
        val samples = listOf(
            ClipHistoryJson.toJson(ClipHistory().addText("hello", 1).addImage("a.png", "image/png", 2)),
            Shortcuts().with("omw", "on my way").toJson(),
            SavedGifs().withRecent(GifItem("a", "t", "https://x/a", "https://x/a", "https://x/a")).toJson(),
            UserWords().also { it.learn("hello", "say"); it.keep("teh"); it.learnFix("tmrw", "tomorrow") }.toText(),
            """{"data":[{"id":"a","title":"t","url":"https://giphy.com/a","images":{"fixed_width":{"url":"https://x/a.gif"}}}]}""",
        )
        repeat(600 * ROUNDS) {
            val text = StringBuilder(random.pick(samples))
            repeat(1 + random.nextInt(5)) {
                when (random.nextInt(3)) {
                    0 -> text.insert(random.nextInt(text.length + 1), random.pick(ODD))
                    1 -> if (text.isNotEmpty()) text.deleteCharAt(random.nextInt(text.length))
                    else -> text.setLength(random.nextInt(text.length + 1))
                }
            }
            val damaged = text.toString()
            ClipHistoryJson.parse(damaged)
            Shortcuts.parse(damaged)
            SavedGifs.parse(damaged)
            UserWords.parse(damaged).toText()
            Giphy.parse(damaged)
        }
    }

    // ------------------------------------------------------------------ odds and ends

    @Test
    fun smallHelpersTakeAnyInput() {
        val random = Random(9000)
        repeat(4000 * ROUNDS) {
            // Colours: every number is a colour, and reads back the same.
            val argb = random.nextInt()
            assertEquals(argb, parseHexColor(formatHexColor(argb)))
            val hsv = argbToHsv(argb)
            assertTrue(hsv.h in 0f..360f && hsv.s in 0f..1f && hsv.v in 0f..1f)
            hsvToArgb(random.nextFloat() * 2000f - 1000f, random.nextFloat() * 4f - 2f, random.nextFloat() * 4f - 2f, random.nextFloat() * 4f - 2f)
            parseHexColor(random.text(9))

            // Fitting suggestions: the right number of labels, a sensible letter size, whatever the words and the width.
            val words = List(1 + random.nextInt(3)) { random.text(25) }
            val fit = fitSuggestions(words, random.nextFloat() * 600f - 100f, random.nextBoolean())
            assertEquals(words.size, fit.labels.size)
            assertEquals(words.size, fit.weights.size)
            assertTrue(fit.fontSp in 10f..15f)
            assertTrue(fit.weights.all { it > 0f })

            quickClipLabel(random.text(60), max = 1 + random.nextInt(20))
            val state = KeyboardState(layerId = random.text(5))
            KeyboardEngine.currentLayer(qwerty, state)
            KeyboardEngine.doubleSpaceMakesFullStop(random.text(4))
            KeyboardEngine.buzzFor(random.nextInt(400) - 100)
            KeyboardEngine.searchPanelExtra(random.nextFloat() * 900f - 100f, random.nextFloat() * 900f - 100f)
            KeyboardEngine.resizePanel(random.nextFloat() * 500f, random.nextFloat() * 900f - 400f, random.nextFloat() * 600f - 50f, if (random.nextBoolean()) 0f else 8f)
            KeyboardEngine.popupIndex(random.nextFloat() * 2000f - 500f, random.nextFloat() * 500f, random.nextFloat() * 80f - 10f, random.nextInt(12) - 2)
            KeyboardEngine.cursorSteps(random.nextFloat() * 400f - 200f, random.nextFloat() * 40f - 5f)
        }
    }

    @Test
    fun sizesSurviveNumbersThatAreNotNumbers() {
        // Arithmetic can produce "not a number" and infinities. Nothing that sets a size may choke on them.
        val odd = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MAX_VALUE, -Float.MAX_VALUE, 0f, -0f, Float.MIN_VALUE)
        val at = KeyPos("letters", 0, 0)
        for (value in odd) {
            assertTrue(snapKeyWidth(value) in 0.5f..6f)
            assertTrue(snapRowHeight(value) in 0.6f..1.6f)
            assertTrue(opacityPercent(value) in 0..100)
            formatNumber(value)
            withOpacity(0x80123456.toInt(), value)
            hsvToArgb(value, value, value, value)
            for (size in ThemeSize.entries) {
                assertTrue(size.snap(value) in size.min..size.max)
                size.set(Theme(), value)
            }
            val theme = Theme().withBackgroundOpacity(value).withKeyOpacity(value).withBackgroundBlur(value)
            assertTrue(theme.backgroundOpacity in 0f..1f && theme.keyOpacity in 0f..1f && theme.backgroundBlur in 0f..40f)
            val edited = qwerty.copy(theme = theme).withKeyWidth(at, value).withRowHeight("letters", 0, value)
            check(edited, "a size of $value")
            fitSuggestions(listOf("hello", "world"), value, true)
            KeyboardEngine.resizePanel(value, value, value)
            KeyboardEngine.searchPanelExtra(value, value)
            KeyboardEngine.cursorSteps(value, value)
            KeyboardEngine.popupLeft(value, value, 3, value, value)
            KeyboardEngine.panelInsetTops(0, 100, value, value)
        }
    }

    @Test
    fun everyKeySoundHasItsFileAndThePlayerKnowsIt() {
        val player = File("src/main/java/app/keybuilder/ime/KeySounds.kt").readText()
        for (sound in KEY_SOUNDS) {
            if (sound == "none" || sound == "phone") continue
            val file = File("src/main/res/raw/key_$sound.wav")
            assertTrue("missing ${file.path}", file.exists() && file.length() > 500)
            val head = file.readBytes().take(12).toByteArray()
            assertEquals("RIFF", String(head, 0, 4))
            assertEquals("WAVE", String(head, 8, 4))
            assertTrue("KeySounds.kt does not load $sound", "\"$sound\" to R.raw.key_$sound" in player)
        }
        // And no sound file is left lying around that nothing can play.
        val files = File("src/main/res/raw").listFiles().orEmpty().map { it.name }.filter { it.startsWith("key_") }
        assertEquals(KEY_SOUNDS.size - 2, files.size)
        if (files.isEmpty()) fail("no sound files")
    }
}
