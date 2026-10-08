package app.keybuilder

import app.keybuilder.suggest.FieldRules
import app.keybuilder.suggest.Lexicon
import app.keybuilder.suggest.Suggestion
import app.keybuilder.suggest.SuggestionEngine
import app.keybuilder.suggest.SuggestionKind
import app.keybuilder.suggest.Typo
import app.keybuilder.suggest.TypingContext
import app.keybuilder.suggest.UserWords
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suggestions, autocorrect and learning. These run against the real word list the app
 * ships with, so they also check that the list is there and readable.
 */
class SuggestTest {

    private companion object {
        /** Reading 50,000 words takes a moment, so all tests share one copy. */
        val lexicon: Lexicon by lazy {
            File("src/main/assets/words/words.txt").useLines { Lexicon.parse(it) }
        }
    }

    private fun engine(user: UserWords = UserWords()) = SuggestionEngine(lexicon, user)

    private fun at(text: String) = TypingContext.from(text)

    private fun fix(text: String, engine: SuggestionEngine = engine()): String? = engine.correction(at(text))

    private fun words(list: List<Suggestion>) = list.map { it.text }

    // ------------------------------------------------------------------ the word list

    @Test
    fun theWordListIsThereAndHasBothLanguages() {
        assertTrue("only ${lexicon.size} words", lexicon.size > 30_000)
        assertEquals('e', lexicon.language("keyboard"))
        assertEquals('h', lexicon.language("nahi"))
        assertEquals('h', lexicon.language("krta"))
        assertTrue(lexicon.isHinglishOnly("kyunki"))
        // "the" is English and also Hinglish (थे), so it says nothing about the language.
        assertFalse(lexicon.isHinglishOnly("the"))
        assertNull(lexicon.score("qwzzk"))
        assertTrue(lexicon.score("the")!! > lexicon.score("keyboard")!!)
    }

    @Test
    fun aListLineThatMakesNoSenseIsSkipped() {
        val small = Lexicon.parse(sequenceOf("hello\t500\te", "", "broken line", "nahi\t600\th", "x\tmany\te"))
        assertEquals(2, small.size)
        assertEquals(500, small.score("hello"))
        assertTrue(small.isHinglishOnly("nahi"))
    }

    @Test
    fun completionsAreTheCommonestWordsWithThatStart() {
        val found = lexicon.completions("tomor", 3).map { it.word }
        assertEquals("tomorrow", found.first())
        assertTrue(lexicon.completions("zzzz", 3).isEmpty())
        assertTrue(lexicon.completions("", 3).isEmpty())
        // Best first.
        val scores = lexicon.completions("th", 5).map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    // ------------------------------------------------------------------ how slips are costed

    @Test
    fun commonSlipsAreCheapAndUnlikelyOnesAreNot() {
        assertEquals(Typo.SWAP, Typo.cost("teh", "the", 2f), 0.001f)
        assertEquals(Typo.NEIGHBOUR, Typo.cost("thr", "the", 2f), 0.001f)       // r is next to e
        assertEquals(Typo.WRONG, Typo.cost("thp", "the", 2f), 0.001f)           // p is nowhere near e
        assertEquals(Typo.DOUBLE, Typo.cost("hapy", "happy", 2f), 0.001f)
        assertEquals(Typo.DOUBLE, Typo.cost("thhe", "the", 2f), 0.001f)
        assertEquals(Typo.MISSING_OR_EXTRA, Typo.cost("te", "the", 2f), 0.001f)
        assertEquals(0f, Typo.cost("the", "the", 2f), 0.001f)
        // A wrong first letter costs extra.
        assertEquals(Typo.NEIGHBOUR + Typo.FIRST_LETTER, Typo.cost("rhe", "the", 2f), 0.001f)
        // Leaving out both letters of a pair is not cheap twice over.
        assertTrue(Typo.cost("ho", "hllo", 1.0f) == Typo.TOO_FAR)
        assertTrue(Typo.cost("cat", "elephant", 2f) == Typo.TOO_FAR)
    }

    @Test
    fun neighboursFollowTheQwertyRows() {
        assertTrue(Typo.areNeighbours('q', 'w'))
        assertTrue(Typo.areNeighbours('g', 't'))
        assertTrue(Typo.areNeighbours('g', 'b'))
        assertFalse(Typo.areNeighbours('q', 'p'))
        assertFalse(Typo.areNeighbours('a', 'a'))
    }

    // ------------------------------------------------------------------ reading the text before the cursor

    @Test
    fun theContextIsTheWordBeingTypedAndTheOnesBeforeIt() {
        val c = at("I will see you tomor")
        assertEquals("tomor", c.word)
        assertEquals(listOf("you", "see", "will"), c.previous)
        assertEquals("you", c.before)
        assertFalse(c.sentenceStart)
        assertTrue(c.plain)
    }

    @Test
    fun aFullStopOrANewLineStartsAFreshSentence() {
        assertTrue(at("Hello").sentenceStart)
        assertTrue(at("").sentenceStart)
        val afterStop = at("See you. Tomor")
        assertTrue(afterStop.sentenceStart)
        assertTrue(afterStop.previous.isEmpty())
        assertTrue(at("line one\nline").sentenceStart)
        // A comma does not.
        assertEquals("well", at("well, tha").before)
    }

    @Test
    fun betweenWordsTheWordIsEmptyAndThePreviousOneIsKnown() {
        val c = at("thank ")
        assertEquals("", c.word)
        assertEquals("thank", c.before)
    }

    @Test
    fun emailsLinksAndCodesAreNotPlainWords() {
        assertFalse(at("aryan@gma").plain)
        assertFalse(at("www.goo").plain)
        assertFalse(at("abc123def").plain)
        assertFalse(at("https://exa").plain)
        assertTrue(at("(hello").plain)
        assertEquals("hello", at("'hello").word)
        assertEquals("don't", at("I don’t").word)
    }

    // ------------------------------------------------------------------ autocorrect

    @Test
    fun everydaySlipsAreFixed() {
        assertEquals("the", fix("teh"))
        assertEquals("going", fix("I am goign"))
        assertEquals("receive", fix("please recieve"))
        assertEquals("definitely", fix("definately"))
        assertEquals("tomorrow", fix("see you tomorow"))
        assertEquals("because", fix("becuase"))
        assertEquals("friend", fix("my freind"))
        assertEquals("keyboard", fix("keybaord"))
    }

    @Test
    fun missingApostrophesAndTheLoneIAreFixed() {
        assertEquals("don't", fix("dont"))
        assertEquals("I'm", fix("im"))
        assertEquals("I", fix("when i"))
        assertEquals("Don't", fix("Dont"))
        assertNull(fix("I"))
        assertNull(fix("don't"))
    }

    @Test
    fun aFixKeepsTheCapitalsThatWereTyped() {
        assertEquals("The", fix("Teh"))
        assertEquals("the", fix("teh"))
    }

    @Test
    fun realWordsAreLeftAlone() {
        for (word in listOf("form", "from", "there", "their", "visor", "defuse", "loon", "keyboard", "hello")) {
            assertNull(word, fix(word))
        }
    }

    @Test
    fun wordsWithAnOrdinaryEndingCountAsRealWords() {
        val e = engine()
        assertTrue(e.isBuiltOnKnownWord("anthems"))
        assertTrue(e.isBuiltOnKnownWord("defusing"))   // defuse + ing
        assertFalse(e.isBuiltOnKnownWord("recieve"))
        assertNull(fix("national anthems"))
    }

    @Test
    fun hinglishIsNeverCorrected() {
        // Words from the list, including the short forms people really type.
        for (word in listOf("nahi", "nhi", "kya", "krta", "bhut", "pta", "accha", "kese", "tmhara", "chalo")) {
            assertNull(word, fix(word))
        }
        // An unknown word inside a Hinglish sentence is left alone too, even if it looks like an English slip.
        assertNull(fix("kya kar rhe helo"))
        assertNull(fix("mujhe nahi pata teh"))
        assertEquals("the", fix("I know teh"))
    }

    @Test
    fun whenTheBestGuessIsHinglishNothingIsChanged() {
        // "miltee" is nearest to "milte". That is offered, not forced.
        val e = engine()
        assertNull(e.correction(at("miltee")))
        assertTrue(words(e.suggestions(at("miltee"), autocorrect = true)).contains("milte"))
    }

    @Test
    fun chatShorthandNamesAndStretchedWordsAreLeftAlone() {
        for (word in listOf("hbd", "ngl", "rofl", "idk", "thx", "sooo", "hahaha", "hmm", "yessss")) {
            assertNull(word, fix(word))
        }
        // A capital in the middle of a sentence means it was typed on purpose.
        assertNull(fix("I met Aryna"))
        assertNull(fix("call NASAA"))
    }

    @Test
    fun twoEquallyGoodGuessesMeanNoCorrection() {
        // "helo" could be help or hello. Both are offered; neither is forced.
        val e = engine()
        assertNull(e.correction(at("helo")))
        val offered = words(e.suggestions(at("helo"), autocorrect = true))
        assertTrue(offered.toString(), offered.contains("hello") && offered.contains("help"))
    }

    @Test
    fun nothingIsCorrectedInsideEmailsOrLinks() {
        assertNull(fix("aryan@teh"))
        assertNull(fix("www.teh"))
    }

    // ------------------------------------------------------------------ suggestions

    @Test
    fun suggestionsCompleteTheWordBeingTyped() {
        val offered = words(engine().suggestions(at("tomorr"), autocorrect = true))
        assertEquals("tomorrow", offered.first())
        assertTrue(offered.size <= 3)
        val hinglish = words(engine().suggestions(at("mer"), autocorrect = true))
        assertTrue(hinglish.toString(), hinglish.contains("mera"))
    }

    @Test
    fun aWordAboutToBeCorrectedShowsWhatWasTypedThenTheFix() {
        val offered = engine().suggestions(at("teh"), autocorrect = true)
        assertEquals(Suggestion("teh", SuggestionKind.TYPED), offered[0])
        assertEquals(Suggestion("the", SuggestionKind.CORRECTION), offered[1])
        // With autocorrect off the fix is still offered, as an ordinary suggestion.
        val quiet = engine().suggestions(at("teh"), autocorrect = false)
        assertTrue(quiet.all { it.kind == SuggestionKind.WORD })
        assertEquals("the", quiet.first().text)
    }

    @Test
    fun suggestionsFollowTheCapitalsBeingTyped() {
        assertEquals("Tomorrow", words(engine().suggestions(at("Tomorr"), autocorrect = true)).first())
        assertEquals("TOMORROW", words(engine().suggestions(at("TOMORR"), autocorrect = true)).first())
        // Some words are always written with a capital.
        assertTrue(words(engine().suggestions(at("see you on mond"), autocorrect = true)).contains("Monday"))
    }

    @Test
    fun afterAWordTheNextOneIsGuessed() {
        assertEquals(listOf("you"), words(engine().suggestions(at("thank "), autocorrect = true)))
        assertEquals(listOf("hai", "kar", "hua"), words(engine().suggestions(at("kya "), autocorrect = true)))
        // Nothing to go on: no guesses, and the strip shows its buttons instead.
        assertTrue(engine().suggestions(at(""), autocorrect = true).isEmpty())
        assertTrue(engine().suggestions(at("xylophone "), autocorrect = true).isEmpty())
    }

    @Test
    fun noSuggestionsInsideEmailsOrForRudeWords() {
        assertTrue(engine().suggestions(at("aryan@gma"), autocorrect = true).isEmpty())
        val offered = words(engine().suggestions(at("fuc"), autocorrect = true))
        assertFalse(offered.toString(), offered.any { it.startsWith("fuck") })
        // Typed in full it is simply left alone, never "corrected".
        assertNull(fix("fuck"))
    }

    // ------------------------------------------------------------------ learning

    @Test
    fun aWordTypedTwiceBecomesYours() {
        val user = UserWords()
        val e = engine(user)
        assertFalse(user.knows("zomato"))
        e.learn(at("order from zomato"), "zomato")
        assertFalse(user.knows("zomato"))
        e.learn(at("order from zomato"), "zomato")
        assertTrue(user.knows("zomato"))
        assertTrue(words(e.suggestions(at("zom"), autocorrect = true)).contains("zomato"))
        // And slips for it are fixed towards it.
        assertTrue(words(e.suggestions(at("zomaot"), autocorrect = true)).contains("zomato"))
    }

    @Test
    fun yourOwnWordsAreNotCorrected() {
        val user = UserWords()
        val e = engine(user)
        assertEquals("the", e.correction(at("teh")))
        e.learn(at("teh"), "teh")
        e.learn(at("teh"), "teh")
        assertNull(e.correction(at("teh")))
    }

    @Test
    fun undoingACorrectionTeachesTheKeyboardForGood() {
        val user = UserWords()
        val e = engine(user)
        val context = at("say teh")
        assertEquals("the", e.correction(context))
        e.learn(context, "the")                       // what the keyboard put in
        e.correctionUndone(context, original = "teh", corrected = "the", remember = true)
        assertNull(e.correction(context))
        assertTrue(user.isKept("teh"))
        assertEquals(0, user.count("the"))            // the unwanted "the" was un-counted
        assertEquals(1, user.count("teh"))
    }

    @Test
    fun inPrivateBoxesAnUndoIsNotWrittenDown() {
        val user = UserWords()
        val e = engine(user)
        val context = at("teh")
        e.correctionUndone(context, original = "teh", corrected = "the", remember = false)
        assertNull(e.correction(context))             // left alone for now
        assertFalse(user.isKept("teh"))
        assertFalse(user.changed)                     // nothing to save
        assertEquals("the", engine(user).correction(context)) // and forgotten after a restart
    }

    @Test
    fun theNextWordIsLearnedFromWhatYouType() {
        val user = UserWords()
        val e = engine(user)
        repeat(2) { e.learn(at("chai peene"), "peene") }
        assertEquals("peene", words(e.suggestions(at("chai "), autocorrect = true)).first())
        // One time is not a habit.
        e.learn(at("chai banao"), "banao")
        assertFalse(words(e.suggestions(at("chai "), autocorrect = true)).contains("banao"))
    }

    @Test
    fun aNameTypedWithACapitalIsRememberedWithIt() {
        val user = UserWords()
        val e = engine(user)
        repeat(2) { e.learn(at("I met Bajpai"), "Bajpai") }
        assertEquals("Bajpai", user.shape("bajpai"))
        assertTrue(words(e.suggestions(at("I met baj"), autocorrect = true)).contains("Bajpai"))
        // A capital at the start of a sentence says nothing, so it is not remembered.
        repeat(2) { e.learn(at("Hello"), "Hello") }
        assertNull(user.shape("hello"))
    }

    @Test
    fun rudeWordsAndNonWordsAreNotLearned() {
        val user = UserWords()
        val e = engine(user)
        e.learn(at("fuck"), "fuck")
        e.learn(at("aryan@gmail"), "gmail")
        e.learn(at("x"), "x")
        assertEquals(0, user.wordCount)
    }

    @Test
    fun learnedWordsSurviveSavingAndLoading() {
        val user = UserWords()
        user.learn("zomato", "from")
        user.learn("zomato", "from")
        user.learn("bajpai", null, shape = "Bajpai")
        user.keep("teh")
        assertTrue(user.changed)
        val loaded = UserWords.parse(user.toText())
        assertEquals(2, loaded.count("zomato"))
        assertEquals(2, loaded.pairCount("from", "zomato"))
        assertEquals("Bajpai", loaded.shape("bajpai"))
        assertTrue(loaded.isKept("teh"))
        assertFalse(loaded.changed)
        assertEquals(user.toText(), loaded.toText())
    }

    @Test
    fun aDamagedLearnedWordsFileLosesOnlyTheBadLines() {
        val loaded = UserWords.parse("w\tzomato\t3\nrubbish\nw\tbroken\tmany\np\tfrom\tzomato\t2\nk\t\n")
        assertEquals(3, loaded.count("zomato"))
        assertEquals(1, loaded.wordCount)
        assertEquals(2, loaded.pairCount("from", "zomato"))
        assertTrue(UserWords.parse("").toText().startsWith("#"))
    }

    @Test
    fun forgettingAWordRemovesEveryTraceOfIt() {
        val user = UserWords()
        repeat(3) { user.learn("zomato", "from") }
        user.keep("zomato")
        user.forget("zomato")
        assertEquals(0, user.count("zomato"))
        assertFalse(user.isKept("zomato"))
        assertEquals(0, user.pairCount("from", "zomato"))
        assertTrue(user.nextWords("from", 3).isEmpty())
    }

    @Test
    fun theLearnedListCannotGrowWithoutLimit() {
        val user = UserWords()
        for (i in 0 until UserWords.MAX_WORDS + 1000) user.learn("word" + i.toString(26).replace(Regex("[0-9]"), "z"))
        assertTrue("${user.wordCount}", user.wordCount <= UserWords.MAX_WORDS + 200)
    }

    // ------------------------------------------------------------------ which boxes allow what

    @Test
    fun passwordBoxesGetNothingAtAll() {
        val text = FieldRules.CLASS_TEXT
        for (variation in listOf(0x80, 0x90, 0xe0)) {   // password, visible password, web password
            assertEquals(FieldRules.NOTHING, FieldRules.forField(text or variation, 0))
        }
        // A number box, PINs included, and a box that says nothing about itself.
        assertEquals(FieldRules.NOTHING, FieldRules.forField(0x2 or 0x10, 0))
        assertEquals(FieldRules.NOTHING, FieldRules.forField(0, 0))
    }

    @Test
    fun emailsLinksAndNoSuggestionBoxesGetNothingEither() {
        val text = FieldRules.CLASS_TEXT
        assertEquals(FieldRules.NOTHING, FieldRules.forField(text or FieldRules.VARIATION_EMAIL, 0))
        assertEquals(FieldRules.NOTHING, FieldRules.forField(text or FieldRules.VARIATION_WEB_EMAIL, 0))
        assertEquals(FieldRules.NOTHING, FieldRules.forField(text or FieldRules.VARIATION_URI, 0))
        assertEquals(FieldRules.NOTHING, FieldRules.forField(text or FieldRules.FLAG_NO_SUGGESTIONS, 0))
    }

    @Test
    fun ordinaryBoxesGetEverythingAndPrivateOnesDoNotLearn() {
        val text = FieldRules.CLASS_TEXT
        assertEquals(FieldRules.EVERYTHING, FieldRules.forField(text, 0))
        assertEquals(FieldRules.EVERYTHING, FieldRules.forField(text or 0x40, 0))   // a chat message box
        val incognito = FieldRules.forField(text, FieldRules.OPTION_NO_PERSONALIZED_LEARNING)
        assertEquals(FieldRules(suggest = true, correct = true, learn = false), incognito)
        // Name boxes: suggestions yes, "fixing" names no.
        val name = FieldRules.forField(text or FieldRules.VARIATION_PERSON_NAME, 0)
        assertEquals(FieldRules(suggest = true, correct = false, learn = true), name)
    }
}
