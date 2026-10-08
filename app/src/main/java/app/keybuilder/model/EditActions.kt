package app.keybuilder.model

/*
 * The buttons of the text editing panel and what each one asks of the text box.
 * No Android code here: the keyboard service turns a KeyStroke into a real key press.
 */

/** One press on a (pretend) hardware keyboard: a key, with Ctrl and/or Shift held or not. */
data class KeyStroke(val key: String, val ctrl: Boolean = false, val shift: Boolean = false)

enum class EditAction(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
    UP("Up"),
    DOWN("Down"),
    WORD_LEFT("Word left"),
    WORD_RIGHT("Word right"),
    LINE_START("Line start"),
    LINE_END("Line end"),
    SELECT("Select"),
    SELECT_ALL("Select all"),
    COPY("Copy"),
    CUT("Cut"),
    PASTE("Paste"),
    UNDO("Undo"),
    REDO("Redo"),
    DELETE("Delete"),
    DELETE_FORWARD("Delete ahead");

    /** Buttons that move the cursor. With "Select" switched on they stretch the selection instead. */
    val moves: Boolean get() = this in MOVES

    /** Buttons that keep going while held. */
    val repeats: Boolean get() = moves && this != LINE_START && this != LINE_END || this == DELETE || this == DELETE_FORWARD

    /**
     * The key press that does this, or null for buttons done another way (see [command]).
     * Text boxes everywhere understand these because real keyboards send them: Ctrl+arrow
     * jumps a word, Shift+arrow selects, Ctrl+Z undoes.
     */
    fun keyStroke(selecting: Boolean): KeyStroke? = when (this) {
        LEFT -> KeyStroke("left", shift = selecting)
        RIGHT -> KeyStroke("right", shift = selecting)
        UP -> KeyStroke("up", shift = selecting)
        DOWN -> KeyStroke("down", shift = selecting)
        WORD_LEFT -> KeyStroke("left", ctrl = true, shift = selecting)
        WORD_RIGHT -> KeyStroke("right", ctrl = true, shift = selecting)
        LINE_START -> KeyStroke("home", shift = selecting)
        LINE_END -> KeyStroke("end", shift = selecting)
        UNDO -> KeyStroke("z", ctrl = true)
        REDO -> KeyStroke("z", ctrl = true, shift = true)
        DELETE -> KeyStroke("delete")
        DELETE_FORWARD -> KeyStroke("forwardDelete")
        SELECT, SELECT_ALL, COPY, CUT, PASTE -> null
    }

    /** One of COMMANDS for the buttons that are the same as the pop-up menu on selected text. */
    fun command(): String? = when (this) {
        SELECT_ALL -> "selectAll"
        COPY -> "copy"
        CUT -> "cut"
        PASTE -> "paste"
        else -> null
    }

    /** Whether "Select" is on after this button, given whether it was on before. */
    fun selectingAfter(before: Boolean): Boolean = when (this) {
        SELECT -> !before
        SELECT_ALL -> true
        // Once the selection has been used, or the text changed, selecting is over.
        COPY, CUT, PASTE, UNDO, REDO, DELETE, DELETE_FORWARD -> false
        else -> before
    }

    private companion object {
        val MOVES = setOf(LEFT, RIGHT, UP, DOWN, WORD_LEFT, WORD_RIGHT, LINE_START, LINE_END)
    }
}
