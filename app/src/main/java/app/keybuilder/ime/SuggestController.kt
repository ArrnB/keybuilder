package app.keybuilder.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keybuilder.store.TypingSettings
import app.keybuilder.store.WordStore
import app.keybuilder.suggest.FieldRules
import app.keybuilder.suggest.Suggestion
import app.keybuilder.suggest.SuggestionKind
import app.keybuilder.suggest.TypingContext

/**
 * Connects the suggestion engine to the text box being typed in.
 *
 * The engine itself (app.keybuilder.suggest) knows nothing about Android. This class reads
 * the few words before the cursor, asks the engine, and types what comes back.
 *
 * Privacy, in one place: [rules] is worked out for every text box by FieldRules. In a
 * password box all three permissions are off, and every function here returns before it
 * reads anything from the box. Learned words go to one file in the app's private storage.
 */
class SuggestController(private val context: Context) {

    /** What the strip shows right now. Empty means: show the shortcut buttons instead. */
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set

    /** True after the user asked for the shortcut buttons back; typing brings suggestions back. */
    var hidden by mutableStateOf(false)
        private set

    private var rules = FieldRules.NOTHING
    private var settings = TypingSettings()
    private var hasSelection = false

    /** The last automatic correction, so the next backspace can take it back. */
    private var undo: Undo? = null

    private class Undo(val context: TypingContext, val original: String, val corrected: String, val separator: String)

    /** True right after a suggestion was tapped: it added a space that punctuation should replace. */
    private var addedSpace = false

    private val canLearn: Boolean get() = rules.learn && settings.learn
    private val canCorrect: Boolean get() = rules.correct && settings.autocorrect
    private val canSuggest: Boolean get() = rules.suggest && settings.suggestions

    fun load(onReady: () -> Unit) = WordStore.load(context, onReady)

    /** A text box got the keyboard. Decides what is allowed in it. */
    fun startField(info: EditorInfo?, typing: TypingSettings, connection: InputConnection?) {
        settings = typing
        rules = if (info == null) FieldRules.NOTHING else FieldRules.forField(info.inputType, info.imeOptions)
        undo = null
        addedSpace = false
        hidden = false
        hasSelection = info != null && info.initialSelStart != info.initialSelEnd
        refresh(connection)
    }

    fun settingsChanged(typing: TypingSettings, connection: InputConnection?) {
        settings = typing
        refresh(connection)
    }

    /** The cursor moved or text was selected, by the user or by the app. */
    fun selectionChanged(start: Int, end: Int, connection: InputConnection?) {
        hasSelection = start != end
        refresh(connection)
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
    fun textChangedOtherwise(connection: InputConnection?) {
        undo = null
        addedSpace = false
        refresh(connection)
    }

    /**
     * Call just before [text] is typed. If [text] ends a word (a space, a comma, enter...),
     * this is the moment the word is corrected and learned.
     */
    fun beforeTyping(connection: InputConnection, text: String) {
        undo = null
        hidden = false
        if (addedSpace) {
            addedSpace = false
            // "word " + "." should be "word.", not "word ."
            if (text.length == 1 && text[0] in CLOSES_UP) {
                if (connection.getTextBeforeCursor(1, 0)?.toString() == " ") connection.deleteSurroundingText(1, 0)
                return
            }
        }
        val engine = WordStore.engine ?: return
        if (!canLearn && !canCorrect) return
        if (hasSelection || !endsAWord(text)) return
        val typing = contextAt(connection) ?: return
        if (typing.word.isEmpty()) return

        var finished = typing.word
        // Enter often sends the message, after which a correction could not be undone. So enter never corrects.
        if (canCorrect && text != "\n") {
            val fixed = engine.correction(typing)
            if (fixed != null && fixed != typing.word) {
                connection.beginBatchEdit()
                connection.deleteSurroundingText(typing.word.length, 0)
                connection.commitText(fixed, 1)
                connection.endBatchEdit()
                undo = Undo(typing, original = typing.word, corrected = fixed, separator = text)
                finished = fixed
            }
        }
        if (canLearn) engine.learn(typing, finished)
    }

    /**
     * Call when backspace is pressed. If the last thing that happened was an automatic
     * correction, this puts back what was typed, remembers to leave that word alone from
     * now on, and returns true: the backspace has done its job.
     */
    fun undoCorrection(connection: InputConnection): Boolean {
        val last = undo
        undo = null
        hidden = false
        addedSpace = false
        if (last == null) return false
        val expected = last.corrected + last.separator
        if (connection.getTextBeforeCursor(expected.length, 0)?.toString() != expected) return false
        connection.beginBatchEdit()
        connection.deleteSurroundingText(expected.length, 0)
        connection.commitText(last.original + last.separator, 1)
        connection.endBatchEdit()
        WordStore.engine?.correctionUndone(last.context, last.original, last.corrected, remember = canLearn)
        return true
    }

    /** A suggestion was tapped: it replaces the word being typed and a space follows. */
    fun pick(connection: InputConnection, suggestion: Suggestion) {
        val engine = WordStore.engine ?: return
        if (!canSuggest || hasSelection) return
        val typing = contextAt(connection) ?: return
        connection.beginBatchEdit()
        if (typing.word.isNotEmpty()) connection.deleteSurroundingText(typing.word.length, 0)
        connection.commitText(suggestion.text + " ", 1)
        connection.endBatchEdit()
        // Tapping your own word means "leave it as I typed it".
        if (suggestion.kind == SuggestionKind.TYPED) engine.keep(suggestion.text, remember = canLearn)
        if (canLearn) engine.learn(typing, suggestion.text)
        undo = null
        addedSpace = true
        hidden = false
        refresh(connection)
    }

    /** Works out the suggestions again from the text before the cursor. */
    fun refresh(connection: InputConnection?) {
        val engine = WordStore.engine
        if (engine == null || connection == null || !canSuggest || hasSelection) {
            suggestions = emptyList()
            return
        }
        val typing = contextAt(connection)
        suggestions = if (typing == null) emptyList() else engine.suggestions(typing, autocorrect = canCorrect)
    }

    /** Saves what was learned. Called when the keyboard is put away. */
    fun save() = WordStore.save(context)

    /**
     * The words around the cursor, or null when the cursor is in the middle of a word
     * (changing half a word would only make a mess) or the box will not say.
     */
    private fun contextAt(connection: InputConnection): TypingContext? {
        val before = connection.getTextBeforeCursor(TypingContext.LOOK_BACK, 0) ?: return null
        val after = connection.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && TypingContext.isWordChar(after[0])) return null
        return TypingContext.from(before.toString())
    }

    private fun endsAWord(text: String): Boolean = text.length == 1 && text[0] in ENDS_A_WORD

    private companion object {
        const val ENDS_A_WORD = " \n.,!?;:)\"]"
        const val CLOSES_UP = ".,!?;:)"
    }
}
