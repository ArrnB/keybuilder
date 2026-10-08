package app.keybuilder

import app.keybuilder.model.Shortcuts
import app.keybuilder.suggest.FieldRules
import app.keybuilder.suggest.Lexicon
import app.keybuilder.suggest.Suggestion
import app.keybuilder.suggest.SuggestionEngine
import app.keybuilder.suggest.SuggestionKind
import app.keybuilder.suggest.TextBox
import app.keybuilder.suggest.TypingSession
import app.keybuilder.suggest.TypingSwitches
import app.keybuilder.suggest.UserWords
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pretend text box: some text and a cursor. It also counts how often it is looked at. */
class FakeBox(text: String = "", cursorAt: Int = text.length) : TextBox {
    private val content = StringBuilder(text)
    var cursor = cursorAt
        private set
    var reads = 0
        private set

    /** When true the box refuses to say what is in it, as some apps do. */
    var silent = false

    val text: String get() = content.toString()

    override fun before(count: Int): String? {
        reads++
        return if (silent) null else content.substring((cursor - count).coerceAtLeast(0), cursor)
    }

    override fun after(count: Int): String? {
        reads++
        return if (silent) null else content.substring(cursor, (cursor + count).coerceAtMost(content.length))
    }

    override fun replaceBefore(count: Int, text: String) {
        val removed = count.coerceIn(0, cursor)
        content.delete(cursor - removed, cursor)
        content.insert(cursor - removed, text)
        cursor = cursor - removed + text.length
    }

    /** Types [keys] one at a time the way the keyboard service does: ask the session first, then type, then refresh. */
    fun type(session: TypingSession, keys: String) {
        for (key in keys) {
            session.beforeTyping(this, key.toString())
            replaceBefore(0, key.toString())
            session.refresh(this)
        }
    }

    /** One press of backspace, again the way the keyboard service does it. */
    fun backspace(session: TypingSession) {
        if (!session.undoLastChange(this) && cursor > 0) replaceBefore(1, "")
        session.refresh(this)
    }
}

/**
 * Whole typing flows, from key presses to what ends up in the text box: corrections,
 * undoing them, suggestions, text shortcuts, learning, and the boxes where none of it may happen.
 */
class TypingSessionTest {

    private companion object {
        val lexicon: Lexicon by lazy { File("src/main/assets/words/words.txt").useLines { Lexicon.parse(it) } }
    }

    private val user = UserWords()
    private val engine = SuggestionEngine(lexicon, user)
    private val shortcuts = Shortcuts().with("omw", "on my way").with("@@", "aryan@example.com").with("addr", "12 Park Road\nGhaziabad")

    private fun session(
        rules: FieldRules = FieldRules.EVERYTHING,
        switches: TypingSwitches = TypingSwitches(),
        texts: Shortcuts = shortcuts,
        box: FakeBox = FakeBox(),
        loaded: Boolean = true,
    ): TypingSession = TypingSession { if (loaded) engine else null }.also { it.start(rules, switches, texts, selection = false, box = box) }

    private fun words(s: TypingSession) = s.suggestions.map { it.text }

    // ------------------------------------------------------------------ autocorrect

    @Test
    fun aSlipIsFixedWhenTheWordIsFinished() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "I am goign to teh shop.")
        assertEquals("I am going to the shop.", box.text)
    }

    @Test
    fun backspaceStraightAfterAFixPutsYourWordBackForGood() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "teh ")
        assertEquals("the ", box.text)
        box.backspace(s)
        assertEquals("teh ", box.text)
        // The next backspace is an ordinary one.
        box.backspace(s)
        assertEquals("teh", box.text)
        // And from now on that word is left alone.
        box.type(s, " teh ")
        assertEquals("teh teh ", box.text)
        assertTrue(user.isKept("teh"))
    }

    @Test
    fun anythingTypedInBetweenMeansBackspaceJustDeletes() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "teh a")
        assertEquals("the a", box.text)
        box.backspace(s)
        assertEquals("the ", box.text)
        box.backspace(s)
        assertEquals("the", box.text)
    }

    @Test
    fun enterLearnsTheLastWordButNeverCorrectsIt() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "zomato\n")
        box.type(s, "teh\n")
        assertEquals("zomato\nteh\n", box.text)
        assertEquals(1, user.count("zomato"))
    }

    @Test
    fun withAutocorrectOffNothingIsChangedButWordsAreStillOffered() {
        val box = FakeBox()
        val s = session(switches = TypingSwitches(autocorrect = false), box = box)
        box.type(s, "teh")
        assertEquals("the", words(s).first())
        assertTrue(s.suggestions.none { it.kind == SuggestionKind.CORRECTION })
        box.type(s, " ")
        assertEquals("teh ", box.text)
    }

    @Test
    fun aWordIsNotTouchedWhileTheCursorIsInsideIt() {
        val box = FakeBox("tehre", cursorAt = 3)
        val s = session(box = box)
        assertTrue(s.suggestions.isEmpty())
        box.type(s, " ")
        assertEquals("teh re", box.text)
    }

    @Test
    fun nothingHappensWhileTextIsSelected() {
        val box = FakeBox("teh")
        val s = session(box = box)
        s.selectionChanged(true, box)
        assertTrue(s.suggestions.isEmpty())
        box.type(s, " ")
        assertEquals("teh ", box.text)
    }

    // ------------------------------------------------------------------ suggestions

    @Test
    fun suggestionsFollowWhatIsTyped() {
        val box = FakeBox()
        val s = session(box = box)
        assertTrue(s.suggestions.isEmpty())
        box.type(s, "see you tomorr")
        assertEquals("tomorrow", words(s).first())
        box.type(s, "ow ")
        assertEquals("see you tomorrow ", box.text)
    }

    @Test
    fun tappingASuggestionReplacesTheWordAndAddsASpace() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "see you tomorr")
        s.pick(box, Suggestion("tomorrow"))
        assertEquals("see you tomorrow ", box.text)
        // Punctuation typed next closes up to the word.
        box.type(s, ".")
        assertEquals("see you tomorrow.", box.text)
    }

    @Test
    fun aLetterAfterATappedSuggestionKeepsTheSpace() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "tomorr")
        s.pick(box, Suggestion("tomorrow"))
        box.type(s, "we")
        assertEquals("tomorrow we", box.text)
    }

    @Test
    fun tappingYourOwnWordKeepsItAsTyped() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "teh")
        assertEquals(Suggestion("teh", SuggestionKind.TYPED), s.suggestions[0])
        s.pick(box, s.suggestions[0])
        assertEquals("teh ", box.text)
        box.type(s, "teh ")
        assertEquals("teh teh ", box.text)
    }

    @Test
    fun aNextWordGuessIsTypedAfterTheSpace() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "thank ")
        assertEquals(listOf("you"), words(s))
        s.pick(box, s.suggestions[0])
        assertEquals("thank you ", box.text)
    }

    @Test
    fun theArrowHidesSuggestionsUntilTheNextKey() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "tomorr")
        assertFalse(s.hidden)
        s.showTools()
        assertTrue(s.hidden)
        box.type(s, "o")
        assertFalse(s.hidden)
    }

    @Test
    fun pickingAReplacementTwiceMakesItAutomatic() {
        val box = FakeBox()
        val s = session(box = box)
        repeat(2) {
            box.type(s, "tmrw")
            s.pick(box, Suggestion("tomorrow"))
        }
        box.type(s, "tmrw ")
        assertEquals("tomorrow tomorrow tomorrow ", box.text)
    }

    // ------------------------------------------------------------------ text shortcuts

    @Test
    fun aShortcutTurnsIntoItsTextWhenFinished() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "I am omw, see you. Omw!")
        assertEquals("I am on my way, see you. On my way!", box.text)
    }

    @Test
    fun aShortcutCanBeSymbolsAndCanHaveSeveralLines() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "mail @@ ")
        assertEquals("mail aryan@example.com ", box.text)
        box.type(s, "addr\n")
        assertEquals("mail aryan@example.com 12 Park Road\nGhaziabad\n", box.text)
    }

    @Test
    fun backspaceStraightAfterAShortcutBringsBackWhatWasTyped() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "omw ")
        assertEquals("on my way ", box.text)
        box.backspace(s)
        assertEquals("omw ", box.text)
        // Unlike a correction, the shortcut still works next time.
        box.type(s, "omw ")
        assertEquals("omw on my way ", box.text)
        assertFalse(user.isKept("omw"))
    }

    @Test
    fun aShortcutIsShownBeforeItHappensAndEitherChoiceCanBeTapped() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "omw")
        assertEquals(listOf(Suggestion("omw", SuggestionKind.TYPED), Suggestion("on my way", SuggestionKind.SHORTCUT)), s.suggestions)
        s.pick(box, s.suggestions[1])
        assertEquals("on my way ", box.text)
        box.type(s, "omw")
        s.pick(box, s.suggestions[0])
        assertEquals("on my way omw ", box.text)
        assertFalse(user.isKept("omw"))
    }

    @Test
    fun aShortcutInsideALongerWordIsLeftAlone() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "omw2 2omw x@@ ")
        assertEquals("omw2 2omw x@@ ", box.text)
    }

    @Test
    fun shortcutsWorkInEmailBoxesWhereNothingElseDoes() {
        val box = FakeBox()
        val s = session(rules = FieldRules.SHORTCUTS_ONLY, box = box)
        box.type(s, "teh")
        assertTrue(s.suggestions.isEmpty())
        box.type(s, " @@ ")
        assertEquals("teh aryan@example.com ", box.text)
        assertEquals(0, user.wordCount)
    }

    @Test
    fun shortcutsWorkBeforeTheWordListHasLoaded() {
        val box = FakeBox()
        val s = session(box = box, loaded = false)
        box.type(s, "teh omw ")
        assertEquals("teh on my way ", box.text)
    }

    @Test
    fun withoutShortcutsAnEmailBoxIsNotReadAtAll() {
        val box = FakeBox()
        val s = session(rules = FieldRules.SHORTCUTS_ONLY, texts = Shortcuts(), box = box)
        box.type(s, "hello omw ")
        box.backspace(s)
        assertEquals(0, box.reads)
    }

    // ------------------------------------------------------------------ privacy

    @Test
    fun aPasswordBoxIsNeverReadCorrectedOrLearnedFrom() {
        val box = FakeBox()
        val s = session(rules = FieldRules.NOTHING, box = box)
        box.type(s, "teh omw hunter2 ")
        box.backspace(s)
        s.pick(box, Suggestion("anything"))
        s.selectionChanged(false, box)
        s.textChangedOtherwise(box)
        assertEquals("teh omw hunter2", box.text)
        assertEquals("the box was read", 0, box.reads)
        assertTrue(s.suggestions.isEmpty())
        assertEquals(0, user.wordCount)
        assertFalse(user.changed)
    }

    @Test
    fun aPasswordBoxAfterAnOrdinaryOneForgetsEverythingPending() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "teh ")                    // a correction that could be undone...
        val password = FakeBox("the ")
        s.start(FieldRules.NOTHING, TypingSwitches(), shortcuts, selection = false, box = password)
        password.backspace(s)                    // ...must not be "undone" into the password box
        assertEquals("the", password.text)
        assertEquals(0, password.reads)
    }

    @Test
    fun withLearningOffNothingIsRemembered() {
        val box = FakeBox()
        val s = session(switches = TypingSwitches(learn = false), box = box)
        box.type(s, "zomato zomato teh ")
        box.backspace(s)
        assertEquals("zomato zomato teh ", box.text)
        assertEquals(0, user.wordCount)
        assertFalse(user.isKept("teh"))
        assertFalse(user.changed)
        // The undo is still respected until the keyboard restarts.
        box.type(s, "teh ")
        assertEquals("zomato zomato teh teh ", box.text)
    }

    @Test
    fun inPrivateModeWordsAreSuggestedButNotLearned() {
        val box = FakeBox()
        val private = FieldRules(suggest = true, correct = true, learn = false, shortcuts = true)
        val s = session(rules = private, box = box)
        box.type(s, "zomato zomato tomorr")
        assertEquals("tomorrow", words(s).first())
        assertEquals(0, user.wordCount)
    }

    // ------------------------------------------------------------------ awkward boxes

    @Test
    fun aBoxThatWillNotSayWhatIsInItIsTypedIntoBlind() {
        val box = FakeBox()
        box.silent = true
        val s = session(box = box)
        box.type(s, "teh omw ")
        box.backspace(s)
        s.pick(box, Suggestion("word"))
        assertEquals("teh omw", box.text)
        assertTrue(s.suggestions.isEmpty())
    }

    @Test
    fun noBoxAtAllIsNotAProblem() {
        val s = TypingSession { engine }
        s.start(FieldRules.EVERYTHING, TypingSwitches(), shortcuts, selection = false, box = null)
        s.refresh(null)
        s.selectionChanged(false, null)
        s.textChangedOtherwise(null)
        s.clear()
        assertTrue(s.suggestions.isEmpty())
    }

    @Test
    fun switchingSuggestionsOffEmptiesTheStrip() {
        val box = FakeBox()
        val s = session(box = box)
        box.type(s, "tomorr")
        assertTrue(s.suggestions.isNotEmpty())
        s.switchesChanged(TypingSwitches(suggestions = false), box)
        assertTrue(s.suggestions.isEmpty())
        // Autocorrect is a separate switch and still works.
        box.type(s, " teh ")
        assertEquals("tomorr the ", box.text)
    }
}
