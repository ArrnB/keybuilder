package app.keybuilder.suggest

import app.keybuilder.model.Shortcuts
import app.keybuilder.model.trailingToken

/*
 * Everything that happens around the cursor while you type: suggestions, automatic
 * corrections and undoing them, text shortcuts, and learning.
 *
 * It talks to the text box through the small TextBox interface below instead of through
 * Android, so the whole flow ("teh", space, backspace...) is unit tested with a pretend
 * text box. The keyboard service wraps the real one (see SuggestController).
 */

/** The little a keyboard needs from the box being typed in. */
interface TextBox {
    /** Up to [count] characters before the cursor, or null if the box will not say. */
    fun before(count: Int): String?

    /** Up to [count] characters after the cursor, or null if the box will not say. */
    fun after(count: Int): String?

    /** Removes [count] characters before the cursor and types [text] there, as one change. */
    fun replaceBefore(count: Int, text: String)
}

/** The user's on/off switches from Settings. */
data class TypingSwitches(
    val suggestions: Boolean = true,
    val autocorrect: Boolean = true,
    val learn: Boolean = true,
)

class TypingSession(private val engine: () -> SuggestionEngine?) {

    /** What the strip should show right now. Empty means: show the shortcut buttons instead. */
    var suggestions: List<Suggestion> = emptyList()
        private set

    /** True after the user asked for the buttons back; typing brings suggestions back. */
    var hidden: Boolean = false
        private set

    private var rules = FieldRules.NOTHING
    private var switches = TypingSwitches()
    private var shortcuts = Shortcuts()
    private var hasSelection = false

    /** The last automatic change, so the next backspace can take it back. */
    private var undo: Undo? = null

    private class Undo(
        val context: TypingContext?, // null for a text shortcut, which teaches the keyboard nothing
        val original: String,
        val changed: String,
        val separator: String,
    )

    /** True right after a suggestion was tapped: it added a space that punctuation should replace. */
    private var addedSpace = false

    private val canLearn: Boolean get() = rules.learn && switches.learn
    private val canCorrect: Boolean get() = rules.correct && switches.autocorrect
    private val canSuggest: Boolean get() = rules.suggest && switches.suggestions
    private val canExpand: Boolean get() = rules.shortcuts && !shortcuts.isEmpty

    /** A text box got the keyboard. [fieldRules] says what is allowed in it. */
    fun start(fieldRules: FieldRules, on: TypingSwitches, texts: Shortcuts, selection: Boolean, box: TextBox?) {
        rules = fieldRules
        switches = on
        shortcuts = texts
        hasSelection = selection
        undo = null
        addedSpace = false
        hidden = false
        refresh(box)
    }

    fun switchesChanged(on: TypingSwitches, box: TextBox?) {
        switches = on
        refresh(box)
    }

    /** The cursor moved or text was selected, by the user or by the app. */
    fun selectionChanged(selection: Boolean, box: TextBox?) {
        hasSelection = selection
        refresh(box)
    }

    /** Nothing to suggest for now (a panel is open, or search words are being typed). */
    fun clear() {
        suggestions = emptyList()
        undo = null
        addedSpace = false
    }

    /** The user tapped the arrow to get the shortcut buttons back. */
    fun showTools() {
        hidden = true
    }

    /** Something other than a key press changed the text (an emoji, a paste). */
    fun textChangedOtherwise(box: TextBox?) {
        undo = null
        addedSpace = false
        refresh(box)
    }

    /**
     * Call just before [text] is typed. If [text] ends a word (a space, a comma, enter...),
     * this is the moment a shortcut is filled in, or the word is corrected and learned.
     */
    fun beforeTyping(box: TextBox, text: String) {
        undo = null
        hidden = false
        if (addedSpace) {
            addedSpace = false
            // "word " + "." should be "word.", not "word ."
            if (text.length == 1 && text[0] in CLOSES_UP) {
                if (box.before(1) == " ") box.replaceBefore(1, "")
                return
            }
        }
        if (rules.nothing || hasSelection || !endsAWord(text)) return
        val engine = engine()
        val wordWork = engine != null && (canLearn || canCorrect)
        if (!canExpand && !wordWork) return

        // From here on the box is read. Never reached in a password box: its rules allow nothing.
        val before = box.before(TypingContext.LOOK_BACK) ?: return
        if (startsWithWordChar(box.after(1))) return // the cursor is in the middle of a word

        if (canExpand) {
            val typed = trailingToken(before)
            val full = shortcuts.expansion(typed)
            if (full != null && full != typed) {
                box.replaceBefore(typed.length, full)
                undo = Undo(null, original = typed, changed = full, separator = text)
                return
            }
        }
        if (engine == null || !wordWork) return
        val typing = TypingContext.from(before)
        if (typing.word.isEmpty()) return
        var finished = typing.word
        // Enter often sends the message, after which a correction could not be undone. So enter never corrects.
        if (canCorrect && text != "\n") {
            val fixed = engine.correction(typing)
            if (fixed != null && fixed != typing.word) {
                box.replaceBefore(typing.word.length, fixed)
                undo = Undo(typing, original = typing.word, changed = fixed, separator = text)
                finished = fixed
            }
        }
        if (canLearn) engine.learn(typing, finished)
    }

    /**
     * Call when backspace is pressed. If the last thing that happened was an automatic
     * change, this puts back what was typed and returns true: the backspace has done its
     * job. After a correction (not a shortcut) the keyboard also leaves that word alone from now on.
     */
    fun undoLastChange(box: TextBox): Boolean {
        val last = undo
        undo = null
        hidden = false
        addedSpace = false
        if (last == null) return false
        val expected = last.changed + last.separator
        if (box.before(expected.length) != expected) return false
        box.replaceBefore(expected.length, last.original + last.separator)
        if (last.context != null) engine()?.correctionUndone(last.context, last.original, last.changed, remember = canLearn)
        return true
    }

    /** A suggestion was tapped: it replaces what is being typed, and a space follows. */
    fun pick(box: TextBox, suggestion: Suggestion) {
        if (rules.nothing || hasSelection) return
        val before = box.before(TypingContext.LOOK_BACK) ?: return
        if (startsWithWordChar(box.after(1))) return
        undo = null
        hidden = false

        // The two suggestions shown for a shortcut: its full text, or what you typed, kept as it is.
        val token = trailingToken(before)
        val pending = if (canExpand) shortcuts.expansion(token) else null
        if (pending != null && (suggestion.kind == SuggestionKind.SHORTCUT || suggestion.kind == SuggestionKind.TYPED)) {
            val text = if (suggestion.kind == SuggestionKind.SHORTCUT) pending else token
            box.replaceBefore(token.length, if (text.lastOrNull()?.isWhitespace() == true) text else "$text ")
            addedSpace = text.lastOrNull()?.isWhitespace() != true
            refresh(box)
            return
        }

        val engine = engine() ?: return
        if (!canSuggest) return
        val typing = TypingContext.from(before)
        box.replaceBefore(typing.word.length, suggestion.text + " ")
        // Tapping your own word means "leave it as I typed it".
        if (suggestion.kind == SuggestionKind.TYPED) engine.keep(suggestion.text, remember = canLearn)
        if (canLearn) {
            engine.learn(typing, suggestion.text)
            // Picking a different word than you typed is remembered as your own correction.
            if (suggestion.kind != SuggestionKind.TYPED) engine.learnChoice(typing, suggestion.text)
        }
        addedSpace = true
        refresh(box)
    }

    /** Works out the suggestions again from the text before the cursor. */
    fun refresh(box: TextBox?) {
        suggestions = emptyList()
        if (box == null || rules.nothing || hasSelection) return
        val engine = engine()
        val wordWork = engine != null && canSuggest
        if (!canExpand && !wordWork) return
        val before = box.before(TypingContext.LOOK_BACK) ?: return
        if (startsWithWordChar(box.after(1))) return

        if (canExpand) {
            val typed = trailingToken(before)
            val full = shortcuts.expansion(typed)
            if (full != null && full != typed) {
                suggestions = listOf(Suggestion(typed, SuggestionKind.TYPED), Suggestion(full, SuggestionKind.SHORTCUT))
                return
            }
        }
        if (engine != null && wordWork) {
            suggestions = engine.suggestions(TypingContext.from(before), autocorrect = canCorrect)
        }
    }

    private fun startsWithWordChar(text: String?): Boolean = !text.isNullOrEmpty() && TypingContext.isWordChar(text[0])

    private fun endsAWord(text: String): Boolean = text.length == 1 && text[0] in ENDS_A_WORD

    private companion object {
        const val ENDS_A_WORD = " \n.,!?;:)\"]"
        const val CLOSES_UP = ".,!?;:)"
    }
}
