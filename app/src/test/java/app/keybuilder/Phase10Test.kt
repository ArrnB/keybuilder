package app.keybuilder

import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.model.EditAction
import app.keybuilder.model.FONT_FAMILIES
import app.keybuilder.model.GifItem
import app.keybuilder.model.KEY_ANIMATIONS
import app.keybuilder.model.KEY_SOUNDS
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyStroke
import app.keybuilder.model.KeyStyle
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.PANELS
import app.keybuilder.model.SavedGifs
import app.keybuilder.model.Shortcuts
import app.keybuilder.model.SymbolData
import app.keybuilder.model.Theme
import app.keybuilder.model.animationFor
import app.keybuilder.model.animationName
import app.keybuilder.model.fontFileName
import app.keybuilder.model.fontName
import app.keybuilder.model.glyphScale
import app.keybuilder.model.isFixedWidthFont
import app.keybuilder.model.isFontFamily
import app.keybuilder.model.keyAt
import app.keybuilder.model.motionFor
import app.keybuilder.model.panelName
import app.keybuilder.model.safeFontFileName
import app.keybuilder.model.scaledForHeight
import app.keybuilder.model.shortcutProblem
import app.keybuilder.model.soundFor
import app.keybuilder.model.soundName
import app.keybuilder.model.systemFontName
import app.keybuilder.model.trailingToken
import app.keybuilder.model.withKeyStyle
import app.keybuilder.suggest.fitSuggestions
import app.keybuilder.suggest.fitWord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Text shortcuts, sounds and animations, fonts, keyboard height, saved GIFs, symbols, the edit panel, fitting suggestions. */
class Phase10Test {

    private val qwerty = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())

    // ------------------------------------------------------------------ text shortcuts

    @Test
    fun aShortcutIsFoundWhateverItsCapitals() {
        val s = Shortcuts().with("omw", "on my way").with("BRB", "be right back")
        assertEquals("on my way", s.expansion("omw"))
        assertEquals("On my way", s.expansion("Omw"))
        assertEquals("On my way", s.expansion("OMW"))
        assertEquals("be right back", s.expansion("brb"))
        assertEquals("be right back", s.expansion("BRB"))
        assertNull(s.expansion("om"))
        assertNull(s.expansion(""))
        assertNull(Shortcuts().expansion("omw"))
    }

    @Test
    fun shortcutsAreAddedChangedAndRemoved() {
        var s = Shortcuts().with("omw", "on my way")
        s = s.with("OMW", "on my way!")               // the same shortcut: its text changes
        assertEquals(1, s.items.size)
        assertEquals("on my way!", s.expansion("omw"))
        s = s.with("@@", "a@b.com")
        assertEquals(2, s.items.size)
        s = s.without("Omw")
        assertEquals(listOf("@@"), s.items.map { it.short })
        assertTrue(s.without("@@").isEmpty)
    }

    @Test
    fun aShortcutThatCannotWorkIsExplained() {
        val have = Shortcuts().with("omw", "on my way")
        assertNotNull(shortcutProblem("", "x", have))
        assertNotNull(shortcutProblem("two words", "x", have))
        assertNotNull(shortcutProblem("x".repeat(21), "x", have))
        assertNotNull(shortcutProblem("ok", "   ", have))
        assertNotNull(shortcutProblem("ok", "x".repeat(2001), have))
        assertTrue(shortcutProblem("OMW", "x", have)!!.contains("already"))
        assertNull(shortcutProblem("omw", "changed", have, replacing = "omw"))   // editing it is fine
        assertNull(shortcutProblem("ok", "okay", have))
        // A shortcut with a problem is simply not added.
        assertEquals(have, have.with("two words", "x"))
    }

    @Test
    fun shortcutsSurviveSavingAndLoading() {
        val s = Shortcuts().with("omw", "on my way").with("addr", "line one\nline \"two\"\ttab").with("@@", "é ₹ 😀")
        assertEquals(s, Shortcuts.parse(s.toJson()))
        assertTrue(Shortcuts.parse("rubbish").isEmpty)
        assertTrue(Shortcuts.parse("{}").isEmpty)
        // Damaged entries are skipped, good ones kept.
        val mixed = """[{"short":"ok","text":"okay"},{"short":"has space","text":"x"},{"short":5},"x",{"short":"ok","text":"again"}]"""
        assertEquals(listOf("ok" to "again"), Shortcuts.parse(mixed).items.map { it.short to it.text })
    }

    @Test
    fun theThingThatMightBeAShortcutIsTheLastRunWithoutSpaces() {
        assertEquals("omw", trailingToken("I am omw"))
        assertEquals("@@", trailingToken("mail\n@@"))
        assertEquals("", trailingToken("omw "))
        assertEquals("", trailingToken(""))
        assertEquals("(omw", trailingToken("see (omw"))
    }

    // ------------------------------------------------------------------ sounds and animations

    @Test
    fun everySoundAndAnimationHasANameAndTheListsStartWithNone() {
        assertEquals("none", KEY_SOUNDS.first())
        assertEquals("none", KEY_ANIMATIONS.first())
        assertEquals(KEY_SOUNDS.size, KEY_SOUNDS.map { soundName(it) }.toSet().size)
        assertEquals(KEY_ANIMATIONS.size, KEY_ANIMATIONS.map { animationName(it) }.toSet().size)
        assertTrue(KEY_SOUNDS.none { soundName(it) == it })
        assertTrue(KEY_ANIMATIONS.none { animationName(it) == it })
    }

    @Test
    fun everyAnimationButNoneDoesSomething() {
        val still = motionFor("none")
        assertFalse(still.moves || still.glow || still.lingers || still.bouncy)
        assertEquals(still, motionFor("no such animation"))
        for (animation in KEY_ANIMATIONS.drop(1)) {
            val m = motionFor(animation)
            assertTrue(animation, m.moves || m.glow || m.lingers)
            assertTrue(animation, m.scale in 0.7f..1.3f)       // never so big it covers its neighbours
            assertTrue(animation, m.tiltDegrees in -15f..15f)
        }
        assertEquals(KEY_ANIMATIONS.size, KEY_ANIMATIONS.map { motionFor(it) }.toSet().size)
    }

    @Test
    fun aKeyUsesItsOwnSoundAndAnimationOrTheThemes() {
        val theme = Theme(sound = "pop", animation = "sink")
        assertEquals("pop", theme.soundFor(KeyDef("a")))
        assertEquals("sink", theme.animationFor(KeyDef("a")))
        val own = KeyDef("a", style = KeyStyle(sound = "none", animation = "bounce"))
        assertEquals("none", theme.soundFor(own))
        assertEquals("bounce", theme.animationFor(own))
        // One of the two can be set without the other.
        assertEquals("pop", theme.soundFor(KeyDef("a", style = KeyStyle(animation = "glow"))))
    }

    @Test
    fun soundsAndAnimationsAreSavedForTheKeyboardAndForSingleKeys() {
        val at = KeyPos("letters", 0, 0)
        val changed = qwerty.copy(theme = qwerty.theme.copy(sound = "thock", animation = "bounce"))
            .withKeyStyle(at, KeyStyle(sound = "drop", animation = "glow"))
        val back = LayoutJson.parse(LayoutJson.toJson(changed))
        assertEquals(changed, back)
        assertEquals("drop", back.keyAt(at)!!.style!!.sound)
        // An older file has neither and gets the defaults.
        assertEquals("click", qwerty.theme.sound)
        assertEquals("none", qwerty.theme.animation)
    }

    @Test
    fun anUnknownSoundOrAnimationInAFileIsExplained() {
        fun layout(theme: String, key: String = "\"a\"") =
            """{"id":"a","name":"A","startLayer":"l","theme":{$theme},"layers":[{"id":"l","rows":[[$key]]}]}"""
        for ((json, mention) in listOf(
            layout("\"sound\": \"boom\"") to "sound",
            layout("\"animation\": \"explode\"") to "animation",
            layout("", """{"label":"a","style":{"sound":"boom"}}""") to "sound",
            layout("", """{"label":"a","style":{"animation":"explode"}}""") to "animation",
        )) {
            try {
                LayoutJson.parse(json)
                fail("expected a problem for $json")
            } catch (e: LayoutFormatException) {
                assertTrue(e.message, e.message!!.contains(mention))
            }
        }
    }

    // ------------------------------------------------------------------ fonts

    @Test
    fun everyBuiltInFontHasANameAndAnAndroidName() {
        assertTrue(FONT_FAMILIES.size >= 10)
        assertEquals(FONT_FAMILIES.size, FONT_FAMILIES.map { fontName(it) }.toSet().size)
        assertEquals(FONT_FAMILIES.size, FONT_FAMILIES.map { systemFontName(it) }.toSet().size)
        assertEquals("sans-serif", systemFontName("sans"))
        assertEquals("sans-serif", systemFontName("something else"))
        assertTrue(isFixedWidthFont("mono"))
        assertFalse(isFixedWidthFont("sans"))
    }

    @Test
    fun yourOwnFontFilesAreAllowedButOnlyUnderSafeNames() {
        assertTrue(isFontFamily("sans"))
        assertTrue(isFontFamily("file:Comic Neue-Bold.ttf"))
        assertEquals("Comic Neue-Bold.ttf", fontFileName("file:Comic Neue-Bold.ttf"))
        assertEquals("Comic Neue-Bold", fontName("file:Comic Neue-Bold.ttf"))
        assertNull(fontFileName("sans"))
        // Nothing that could reach outside the fonts folder.
        for (bad in listOf("file:", "file:../secret.ttf", "file:a/b.ttf", "file:.hidden", "file:" + "x".repeat(200), "comic")) {
            assertFalse(bad, isFontFamily(bad))
        }
        assertEquals("My_Font.ttf", safeFontFileName("/storage/emulated/0/Download/My*Font.ttf"))
        assertEquals("font.otf", safeFontFileName("font.otf"))
        assertEquals("NoEnding.ttf", safeFontFileName("NoEnding"))
        assertEquals("font.ttf", safeFontFileName("///"))
        assertEquals("hidden.ttf", safeFontFileName("..hidden"))
        for (picked in listOf("a.ttf", "../../x.otf", "ü ñ.ttf", "", "C:\\Fonts\\Arial Bold.TTF", "x".repeat(300))) {
            assertTrue(picked, isFontFamily("file:" + safeFontFileName(picked)))
        }
    }

    @Test
    fun aFontFileInALayoutFileLoadsAndANonsenseFontIsExplained() {
        val themed = qwerty.copy(theme = qwerty.theme.copy(fontFamily = "file:Lobster.ttf"))
        assertEquals("file:Lobster.ttf", LayoutJson.parse(LayoutJson.toJson(themed)).theme.fontFamily)
        for (font in FONT_FAMILIES) {
            val t = qwerty.copy(theme = qwerty.theme.copy(fontFamily = font))
            assertEquals(font, LayoutJson.parse(LayoutJson.toJson(t)).theme.fontFamily)
        }
        try {
            LayoutJson.parse(LayoutJson.toJson(themed).replace("file:Lobster.ttf", "file:../x.ttf"))
            fail("expected a problem")
        } catch (e: LayoutFormatException) {
            assertTrue(e.message!!.contains("fontFamily"))
        }
    }

    // ------------------------------------------------------------------ keyboard height

    @Test
    fun aTallerKeyboardHasTallerRowsAndEvenlyBiggerLettersAndIcons() {
        val tall = qwerty.scaledForHeight(130)
        assertEquals(qwerty.theme.keyHeightDp * 1.3f, tall.theme.keyHeightDp, 0.01f)
        assertEquals(qwerty.theme.fontSizeSp * 1.3f, tall.theme.fontSizeSp, 0.01f)
        val state = KeyboardEngine.initialState(qwerty)
        assertTrue(KeyboardEngine.heightDp(tall, state) > KeyboardEngine.heightDp(qwerty, state))
        // Nothing else changes: same keys, same widths, same gaps, same colours.
        assertEquals(qwerty.layers, tall.layers)
        assertEquals(qwerty.theme.copy(keyHeightDp = 0f, fontSizeSp = 0f), tall.theme.copy(keyHeightDp = 0f, fontSizeSp = 0f))
        assertTrue(qwerty.scaledForHeight(100) === qwerty)
    }

    @Test
    fun lettersStopGrowingBeforeTheyNoLongerFitTheirKeys() {
        assertEquals(1f, glyphScale(100), 0f)
        assertEquals(0.8f, glyphScale(70), 0.001f)
        assertEquals(1.3f, glyphScale(150), 0.001f)
        assertEquals(1.3f, glyphScale(999), 0.001f)
        assertEquals(qwerty.theme.keyHeightDp * 1.5f, qwerty.scaledForHeight(400).theme.keyHeightDp, 0.01f)
        assertEquals(qwerty.theme.keyHeightDp * 0.7f, qwerty.scaledForHeight(1).theme.keyHeightDp, 0.01f)
    }

    @Test
    fun aKeysOwnLetterSizeIsScaledTooAndItsOtherSettingsKept() {
        val at = KeyPos("letters", 0, 0)
        val styled = qwerty.withKeyStyle(at, KeyStyle(fontSizeSp = 30f, sound = "pop", background = "#FF0000"))
        val style = styled.scaledForHeight(80).keyAt(at)!!.style!!
        assertEquals(24f, style.fontSizeSp!!, 0.01f)
        assertEquals("pop", style.sound)
        assertEquals("#FF0000", style.background)
    }

    // ------------------------------------------------------------------ fitting suggestions into the strip

    @Test
    fun wordsThatShareAStartCanStillBeToldApart() {
        // The case from the phone: a typewriter font and about 230dp for three words.
        val fit = fitSuggestions(listOf("Carbonate", "Carbonated", "Carbonara"), widthDp = 230f, fixedWidth = true)
        assertEquals(3, fit.labels.toSet().size)
        assertTrue(fit.fontSp < 15f)
        // Even in far too little room they stay different, because the middle goes, not the end.
        val tight = fitSuggestions(listOf("Carbonate", "Carbonated", "Carbonara"), widthDp = 150f, fixedWidth = true)
        assertEquals(3, tight.labels.toSet().size)
        assertTrue(tight.labels.all { "…" in it })
        assertTrue(tight.fontSp >= 10.5f)
    }

    @Test
    fun shortWordsKeepTheNormalSizeAndAreNotCut() {
        val fit = fitSuggestions(listOf("the", "they", "them"), widthDp = 230f, fixedWidth = false)
        assertEquals(15f, fit.fontSp, 0f)
        assertEquals(listOf("the", "they", "them"), fit.labels)
        assertEquals(fit.weights[0], fit.weights[1], 0f)
    }

    @Test
    fun aLongerWordGetsMoreOfTheWidth() {
        val fit = fitSuggestions(listOf("a", "internationalisation"), widthDp = 230f, fixedWidth = false)
        assertTrue(fit.weights[1] > fit.weights[0] * 3)
        assertEquals("a", fit.labels[0])
        assertTrue(fitSuggestions(emptyList(), 230f, false).labels.isEmpty())
        // Silly widths do not break anything.
        for (width in listOf(0f, -50f, 1f, 100000f)) {
            assertEquals(2, fitSuggestions(listOf("hello", "world"), width, true).labels.size)
        }
    }

    @Test
    fun aWordCutInTheMiddleKeepsItsStartAndEnd() {
        assertEquals("Carb…ated", fitWord("Carbonated", 9))
        assertEquals("Carbonated", fitWord("Carbonated", 10))
        assertEquals("C…d", fitWord("Carbonated", 3))
        assertEquals("C…d", fitWord("Carbonated", 0))
        assertEquals("", fitWord("", 5))
        for (room in 3..12) assertTrue(fitWord("abcdefghijklmnop", room).length <= room)
    }

    // ------------------------------------------------------------------ recent and favourite GIFs

    private fun gif(id: String) = GifItem(id, "title $id", "https://x/$id-p.gif", "https://x/$id.gif", "https://giphy.com/$id")

    @Test
    fun recentGifsAreNewestFirstWithoutRepeatsAndLimited() {
        var saved = SavedGifs()
        for (i in 1..40) saved = saved.withRecent(gif("g$i"))
        assertEquals(SavedGifs.MAX_RECENT, saved.recent.size)
        assertEquals("g40", saved.recent.first().id)
        saved = saved.withRecent(gif("g20"))
        assertEquals("g20", saved.recent.first().id)
        assertEquals(1, saved.recent.count { it.id == "g20" })
        assertEquals(SavedGifs.MAX_RECENT, saved.recent.size)
    }

    @Test
    fun aFavouriteIsAddedByOneHoldAndRemovedByTheNext() {
        var saved = SavedGifs().toggleFavourite(gif("a")).toggleFavourite(gif("b"))
        assertTrue(saved.isFavourite("a"))
        assertEquals(listOf("b", "a"), saved.favourites.map { it.id })
        saved = saved.toggleFavourite(gif("a"))
        assertFalse(saved.isFavourite("a"))
        assertEquals(listOf("b"), saved.favourites.map { it.id })
        // Sending a GIF does not touch the favourites.
        assertEquals(saved.favourites, saved.withRecent(gif("z")).favourites)
    }

    @Test
    fun savedGifsSurviveSavingAndLoading() {
        val saved = SavedGifs().withRecent(gif("a")).withRecent(gif("b")).toggleFavourite(gif("c"))
        assertEquals(saved, SavedGifs.parse(saved.toJson()))
        assertEquals(SavedGifs(), SavedGifs.parse("rubbish"))
        assertEquals(SavedGifs(), SavedGifs.parse("[]"))
        // Entries with missing or unsafe addresses are dropped, and repeats too.
        val damaged = """{"recent":[{"id":"a","preview":"https://x/a","send":"https://x/a"},{"id":"b","preview":"http://x/b","send":"https://x/b"},
            {"id":"c","preview":"file:///etc/passwd","send":"https://x/c"},{"id":"a","preview":"https://x/a","send":"https://x/a"},{"preview":"https://x","send":"https://x"},7],
            "favourites":"nope"}"""
        val parsed = SavedGifs.parse(damaged)
        assertEquals(listOf("a"), parsed.recent.map { it.id })
        assertTrue(parsed.favourites.isEmpty())
    }

    // ------------------------------------------------------------------ the symbols and edit panels

    @Test
    fun theNewPanelsAreOnTheStripAndCanBeOpenedByAKey() {
        assertTrue("symbols" in PANELS && "edit" in PANELS)
        assertEquals("settings", PANELS.last())
        assertEquals("Symbols", panelName("symbols"))
        assertEquals("Edit", panelName("edit"))
        val json = """{"id":"a","name":"A","startLayer":"l","layers":[{"id":"l","rows":[[{"label":"ed","action":{"type":"panel","to":"edit"}}]]}]}"""
        val def = LayoutJson.parse(json)
        val pressed = KeyboardEngine.press(def, KeyboardEngine.initialState(def), def.layers[0].rows[0].keys[0], 0L)
        assertEquals("edit", pressed.state.panel)
    }

    @Test
    fun theSymbolsPanelHasGroupsWithoutRepeatsAndTheFaces() {
        val groups = SymbolData.panelGroups
        assertTrue(groups.none { it.name == "Letters" })
        assertEquals("Faces", groups.last().name)
        assertTrue(groups.last().symbols.size >= 30)
        for (group in groups) {
            assertTrue(group.name, group.symbols.isNotEmpty())
            assertTrue(group.name, group.symbols.none { it.isBlank() })
            assertEquals(group.name, group.symbols.size, group.symbols.toSet().size)
        }
    }

    @Test
    fun arrowsMoveAndWithSelectOnTheyStretchTheSelection() {
        assertEquals(KeyStroke("left"), EditAction.LEFT.keyStroke(selecting = false))
        assertEquals(KeyStroke("left", shift = true), EditAction.LEFT.keyStroke(selecting = true))
        assertEquals(KeyStroke("right", ctrl = true), EditAction.WORD_RIGHT.keyStroke(selecting = false))
        assertEquals(KeyStroke("home", shift = true), EditAction.LINE_START.keyStroke(selecting = true))
        assertEquals(KeyStroke("z", ctrl = true), EditAction.UNDO.keyStroke(selecting = true))
        assertEquals(KeyStroke("z", ctrl = true, shift = true), EditAction.REDO.keyStroke(selecting = false))
        assertEquals(KeyStroke("delete"), EditAction.DELETE.keyStroke(selecting = true))
    }

    @Test
    fun everyEditButtonDoesExactlyOneThing() {
        for (action in EditAction.entries) {
            val ways = listOfNotNull(action.keyStroke(false), action.command()).size
            assertEquals(action.name, if (action == EditAction.SELECT) 0 else 1, ways)
            assertTrue(action.name, action.label.isNotBlank())
        }
        assertEquals(setOf("selectAll", "copy", "cut", "paste"), EditAction.entries.mapNotNull { it.command() }.toSet())
        assertTrue(EditAction.LEFT.repeats && EditAction.DELETE.repeats && EditAction.WORD_LEFT.repeats)
        assertFalse(EditAction.COPY.repeats || EditAction.LINE_END.repeats || EditAction.SELECT.repeats)
    }

    @Test
    fun selectStaysOnWhileMovingAndEndsWhenTheSelectionIsUsed() {
        assertTrue(EditAction.SELECT.selectingAfter(false))
        assertFalse(EditAction.SELECT.selectingAfter(true))
        assertTrue(EditAction.LEFT.selectingAfter(true))
        assertFalse(EditAction.LEFT.selectingAfter(false))
        assertTrue(EditAction.SELECT_ALL.selectingAfter(false))
        for (action in listOf(EditAction.COPY, EditAction.CUT, EditAction.PASTE, EditAction.DELETE, EditAction.UNDO)) {
            assertFalse(action.name, action.selectingAfter(true))
        }
    }
}
