package app.keybuilder.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keybuilder.model.Shortcuts
import app.keybuilder.store.TypingSettings
import app.keybuilder.store.WordStore
import app.keybuilder.suggest.FieldRules
import app.keybuilder.suggest.Suggestion
import app.keybuilder.suggest.TextBox
import app.keybuilder.suggest.TypingSession
import app.keybuilder.suggest.TypingSwitches

/**
 * Connects suggestions, autocorrect and text shortcuts to the text box being typed in.
 *
 * All the deciding is done by TypingSession (app.keybuilder.suggest), which knows nothing
 * about Android and is unit tested with a pretend text box. This class only hands it the
 * real text box and copies what it wants shown into something Compose redraws from.
 *
 * Privacy, in one place: what a text box allows is worked out by FieldRules. In a password
 * box nothing is allowed, and TypingSession returns before reading anything from the box.
 * Learned words go to one file in the app's private storage.
 */
class SuggestController(private val context: Context) {

    /** What the strip shows right now. Empty means: show the shortcut buttons instead. */
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set

    /** True after the user asked for the shortcut buttons back; typing brings suggestions back. */
    var hidden by mutableStateOf(false)
        private set

    private val session = TypingSession { WordStore.engine }

    fun load(onReady: () -> Unit) = WordStore.load(context, onReady)

    /** A text box got the keyboard. Decides what is allowed in it. */
    fun startField(info: EditorInfo?, typing: TypingSettings, shortcuts: Shortcuts, connection: InputConnection?) {
        val rules = if (info == null) FieldRules.NOTHING else FieldRules.forField(info.inputType, info.imeOptions)
        val selection = info != null && info.initialSelStart != info.initialSelEnd
        session.start(rules, switches(typing), shortcuts, selection, box(connection))
        show()
    }

    fun settingsChanged(typing: TypingSettings, connection: InputConnection?) {
        session.switchesChanged(switches(typing), box(connection))
        show()
    }

    /** The cursor moved or text was selected, by the user or by the app. */
    fun selectionChanged(start: Int, end: Int, connection: InputConnection?) {
        session.selectionChanged(start != end, box(connection))
        show()
    }

    /** Nothing to suggest for now (a panel is open, or search words are being typed). */
    fun clear() {
        session.clear()
        show()
    }

    /** The user tapped the arrow to get the shortcut buttons back. */
    fun showTools() {
        session.showTools()
        show()
    }

    /** Something other than a key press changed the text (an emoji, a paste). */
    fun textChangedOtherwise(connection: InputConnection?) {
        session.textChangedOtherwise(box(connection))
        show()
    }

    /** Call just before [text] is typed: a word that ends here is corrected, expanded and learned. */
    fun beforeTyping(connection: InputConnection, text: String) {
        session.beforeTyping(Box(connection), text)
        show()
    }

    /** Call when backspace is pressed. True if it took back an automatic change instead of deleting. */
    fun undoCorrection(connection: InputConnection): Boolean {
        val undone = session.undoLastChange(Box(connection))
        show()
        return undone
    }

    /** A suggestion was tapped. */
    fun pick(connection: InputConnection, suggestion: Suggestion) {
        session.pick(Box(connection), suggestion)
        show()
    }

    /** Works out the suggestions again from the text before the cursor. */
    fun refresh(connection: InputConnection?) {
        session.refresh(box(connection))
        show()
    }

    /** Saves what was learned. Called when the keyboard is put away. */
    fun save() = WordStore.save(context)

    private fun show() {
        suggestions = session.suggestions
        hidden = session.hidden
    }

    private fun switches(typing: TypingSettings) =
        TypingSwitches(suggestions = typing.suggestions, autocorrect = typing.autocorrect, learn = typing.learn)

    private fun box(connection: InputConnection?): TextBox? = connection?.let { Box(it) }

    /** The real text box, seen through the little window TypingSession looks through. */
    private class Box(private val connection: InputConnection) : TextBox {
        override fun before(count: Int): String? = connection.getTextBeforeCursor(count, 0)?.toString()

        override fun after(count: Int): String? = connection.getTextAfterCursor(count, 0)?.toString()

        override fun replaceBefore(count: Int, text: String) {
            connection.beginBatchEdit()
            if (count > 0) connection.deleteSurroundingText(count, 0)
            if (text.isNotEmpty()) connection.commitText(text, 1)
            connection.endBatchEdit()
        }
    }
}
