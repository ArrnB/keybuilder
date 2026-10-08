package app.keybuilder.model

import kotlin.math.roundToInt

/*
 * Everything the layout editor can change: keys, rows and layers.
 *
 * Like StyleEdits, each function takes a keyboard and returns a changed copy, so the
 * rules can be unit tested without a phone. Three rules hold after every edit:
 *   - a layer always has at least one row
 *   - a row always has at least one key
 *   - a keyboard always has at least one layer, and its start layer exists
 */

/** A changed keyboard, plus the key that should be selected afterwards (null = none). */
data class Edit(val def: KeyboardDef, val selection: KeyPos?)

/** What a freshly added key looks like until you change it. */
val NEW_KEY = KeyDef(label = "?")

const val KEY_WIDTH_MIN = 0.5f
const val KEY_WIDTH_MAX = 6f
const val ROW_HEIGHT_MIN = 0.6f
const val ROW_HEIGHT_MAX = 1.6f

private fun KeyboardDef.withRows(layerId: String, change: (List<Row>) -> List<Row>): KeyboardDef =
    copy(layers = layers.map { if (it.id == layerId) it.copy(rows = change(it.rows)) else it })

private fun KeyboardDef.rowAt(layerId: String, row: Int): Row? = layer(layerId)?.rows?.getOrNull(row)

// ------------------------------------------------------------------ keys

/** Adds a key next to [pos] and selects it. */
fun KeyboardDef.insertKey(pos: KeyPos, after: Boolean, key: KeyDef = NEW_KEY): Edit {
    if (keyAt(pos) == null) return Edit(this, null)
    val at = if (after) pos.index + 1 else pos.index
    val def = withRows(pos.layerId) { rows ->
        rows.mapIndexed { i, row ->
            if (i != pos.row) row else row.copy(keys = row.keys.toMutableList().apply { add(at, key) })
        }
    }
    return Edit(def, pos.copy(index = at))
}

fun KeyboardDef.duplicateKey(pos: KeyPos): Edit {
    val key = keyAt(pos) ?: return Edit(this, null)
    return insertKey(pos, after = true, key = key)
}

/** The very last key of a layer cannot be removed. */
fun KeyboardDef.canDeleteKey(pos: KeyPos): Boolean {
    val layer = layer(pos.layerId) ?: return false
    val row = layer.rows.getOrNull(pos.row) ?: return false
    if (pos.index !in row.keys.indices) return false
    return row.keys.size > 1 || layer.rows.size > 1
}

/** Removes a key. Removing the only key of a row removes the row. Selects the neighbour. */
fun KeyboardDef.deleteKey(pos: KeyPos): Edit {
    if (!canDeleteKey(pos)) return Edit(this, pos.takeIf { keyAt(it) != null })
    val row = rowAt(pos.layerId, pos.row)!!
    if (row.keys.size == 1) {
        return Edit(withRows(pos.layerId) { rows -> rows.filterIndexed { i, _ -> i != pos.row } }, null)
    }
    val def = withRows(pos.layerId) { rows ->
        rows.mapIndexed { i, r ->
            if (i != pos.row) r else r.copy(keys = r.keys.filterIndexed { k, _ -> k != pos.index })
        }
    }
    return Edit(def, pos.copy(index = minOf(pos.index, row.keys.size - 2)))
}

/** Moves a key left (-1) or right (+1) inside its row. At the edge nothing happens. */
fun KeyboardDef.moveKey(pos: KeyPos, delta: Int): Edit {
    val row = rowAt(pos.layerId, pos.row) ?: return Edit(this, null)
    val target = pos.index + delta
    if (pos.index !in row.keys.indices) return Edit(this, null)
    if (target !in row.keys.indices || delta == 0) return Edit(this, pos)
    val def = withRows(pos.layerId) { rows ->
        rows.mapIndexed { i, r ->
            if (i != pos.row) r
            else r.copy(keys = r.keys.toMutableList().apply { add(target, removeAt(pos.index)) })
        }
    }
    return Edit(def, pos.copy(index = target))
}

/**
 * Moves a key to the row above (-1) or below (+1), keeping roughly the same place.
 * If that empties its old row, the old row disappears.
 */
fun KeyboardDef.moveKeyToRow(pos: KeyPos, rowDelta: Int): Edit {
    val layer = layer(pos.layerId) ?: return Edit(this, null)
    val key = keyAt(pos) ?: return Edit(this, null)
    val targetRow = pos.row + rowDelta
    if (targetRow !in layer.rows.indices || rowDelta == 0) return Edit(this, pos)

    val insertAt = minOf(pos.index, layer.rows[targetRow].keys.size)
    val sourceEmpties = layer.rows[pos.row].keys.size == 1
    val rows = layer.rows.mapIndexed { i, row ->
        when (i) {
            pos.row -> row.copy(keys = row.keys.filterIndexed { k, _ -> k != pos.index })
            targetRow -> row.copy(keys = row.keys.toMutableList().apply { add(insertAt, key) })
            else -> row
        }
    }.filter { it.keys.isNotEmpty() }

    // If the old row vanished and it was above the target, the target slid up by one.
    val finalRow = if (sourceEmpties && pos.row < targetRow) targetRow - 1 else targetRow
    return Edit(withRows(pos.layerId) { rows }, KeyPos(pos.layerId, finalRow, insertAt))
}

/** Rounds a width to quarter steps inside the allowed range. */
fun snapKeyWidth(width: Float): Float =
    ((width * 4).roundToInt() / 4f).coerceIn(KEY_WIDTH_MIN, KEY_WIDTH_MAX)

fun KeyboardDef.withKeyWidth(pos: KeyPos, width: Float): KeyboardDef =
    withKey(pos) { it.copy(width = snapKeyWidth(width)) }

fun KeyboardDef.withKeyLabel(pos: KeyPos, label: String): KeyboardDef = withKey(pos) { it.copy(label = label) }

/** An empty [text] means "use the label in capitals" again. */
fun KeyboardDef.withShiftLabel(pos: KeyPos, text: String): KeyboardDef =
    withKey(pos) { it.copy(shiftLabel = text.ifEmpty { null }) }

fun KeyboardDef.withKeyLook(pos: KeyPos, look: KeyLook): KeyboardDef = withKey(pos) { it.copy(look = look) }

/** Sets what a text key types. Other kinds of key are left alone. */
fun KeyboardDef.withKeyText(pos: KeyPos, text: String): KeyboardDef =
    withKey(pos) { if (it.action is KeyAction.Text) it.copy(action = KeyAction.Text(text)) else it }

// ------------------------------------------------------------------ what a key does

/** The kinds of action offered in the editor, in the order they are shown. */
enum class ActionKind(val label: String) {
    LABEL("Types its letter"),
    TEXT("Types a text"),
    SHIFT("Shift"),
    BACKSPACE("Backspace"),
    ENTER("Enter"),
    SPACE("Space"),
    LAYER("Opens a layer"),
    NONE("Nothing"),
}

fun KeyAction.kind(): ActionKind = when (this) {
    KeyAction.TypeLabel -> ActionKind.LABEL
    is KeyAction.Text -> ActionKind.TEXT
    KeyAction.Shift -> ActionKind.SHIFT
    KeyAction.Backspace -> ActionKind.BACKSPACE
    KeyAction.Enter -> ActionKind.ENTER
    KeyAction.Space -> ActionKind.SPACE
    is KeyAction.SwitchLayer -> ActionKind.LAYER
    KeyAction.None -> ActionKind.NONE
}

/**
 * Changes what a key does and tidies the key so it makes sense straight away:
 * icon keys lose their label so the icon shows, a layer key is named after its layer,
 * and plain-looking keys get the look such keys normally have.
 */
fun KeyboardDef.withActionKind(pos: KeyPos, kind: ActionKind): KeyboardDef = withKey(pos) { key ->
    if (key.action.kind() == kind) return@withKey key
    val placeholder = key.label == NEW_KEY.label
    val plain = key.look == KeyLook.NORMAL
    when (kind) {
        ActionKind.LABEL -> key.copy(action = KeyAction.TypeLabel)
        ActionKind.TEXT -> key.copy(action = KeyAction.Text(if (placeholder) "" else key.label), shiftLabel = null)
        ActionKind.SHIFT -> key.copy(
            action = KeyAction.Shift, label = "", shiftLabel = null,
            look = if (plain) KeyLook.FUNCTION else key.look,
        )
        ActionKind.BACKSPACE -> key.copy(
            action = KeyAction.Backspace, label = "", shiftLabel = null,
            look = if (plain) KeyLook.FUNCTION else key.look,
        )
        ActionKind.ENTER -> key.copy(
            action = KeyAction.Enter, label = "", shiftLabel = null,
            look = if (plain) KeyLook.ACCENT else key.look,
        )
        ActionKind.SPACE -> key.copy(action = KeyAction.Space, label = "", shiftLabel = null)
        ActionKind.LAYER -> {
            val target = layers.firstOrNull { it.id != pos.layerId }?.id ?: pos.layerId
            key.copy(
                action = KeyAction.SwitchLayer(target),
                label = if (placeholder || key.label.isEmpty()) target else key.label,
                shiftLabel = null,
                look = if (plain) KeyLook.FUNCTION else key.look,
            )
        }
        ActionKind.NONE -> key.copy(action = KeyAction.None, shiftLabel = null)
    }
}

/** Points a layer key at another layer. If the key was named after the old layer, the name follows. */
fun KeyboardDef.withLayerTarget(pos: KeyPos, target: String): KeyboardDef = withKey(pos) { key ->
    val action = key.action
    if (action !is KeyAction.SwitchLayer || layer(target) == null) key
    else key.copy(action = KeyAction.SwitchLayer(target), label = if (key.label == action.layer) target else key.label)
}

// ------------------------------------------------------------------ rows

/** Adds a row with one new key under row [afterRow] and selects that key. */
fun KeyboardDef.insertRow(layerId: String, afterRow: Int): Edit {
    val layer = layer(layerId) ?: return Edit(this, null)
    val at = (afterRow + 1).coerceIn(0, layer.rows.size)
    val def = withRows(layerId) { rows -> rows.toMutableList().apply { add(at, Row(listOf(NEW_KEY))) } }
    return Edit(def, KeyPos(layerId, at, 0))
}

fun KeyboardDef.canDeleteRow(layerId: String, row: Int): Boolean {
    val layer = layer(layerId) ?: return false
    return row in layer.rows.indices && layer.rows.size > 1
}

fun KeyboardDef.deleteRow(layerId: String, row: Int): Edit {
    if (!canDeleteRow(layerId, row)) return Edit(this, null)
    return Edit(withRows(layerId) { rows -> rows.filterIndexed { i, _ -> i != row } }, null)
}

/** Moves the row of the selected key up (-1) or down (+1). The selection goes with it. */
fun KeyboardDef.moveRow(pos: KeyPos, delta: Int): Edit {
    val layer = layer(pos.layerId) ?: return Edit(this, null)
    val target = pos.row + delta
    if (pos.row !in layer.rows.indices) return Edit(this, null)
    if (target !in layer.rows.indices || delta == 0) return Edit(this, pos)
    val def = withRows(pos.layerId) { rows -> rows.toMutableList().apply { add(target, removeAt(pos.row)) } }
    return Edit(def, pos.copy(row = target))
}

fun snapRowHeight(height: Float): Float =
    ((height * 10).roundToInt() / 10f).coerceIn(ROW_HEIGHT_MIN, ROW_HEIGHT_MAX)

fun KeyboardDef.withRowHeight(layerId: String, row: Int, height: Float): KeyboardDef =
    withRows(layerId) { rows ->
        rows.mapIndexed { i, r -> if (i == row) r.copy(height = snapRowHeight(height)) else r }
    }

// ------------------------------------------------------------------ layers

/** Why [name] cannot be used for a layer, or null if it is fine. */
fun KeyboardDef.layerNameProblem(name: String, renaming: String? = null): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Give the layer a name."
        trimmed.length > 24 -> "Keep the name to 24 characters or fewer."
        trimmed != renaming && layer(trimmed) != null -> "Another layer already has that name."
        else -> null
    }
}

/**
 * Adds a small starter layer: one new key, and a bottom row with a way back to
 * [returnTo], a space bar and backspace, so you can never get stuck on it.
 */
fun KeyboardDef.addLayer(name: String, returnTo: String): KeyboardDef {
    val id = name.trim()
    if (layerNameProblem(id) != null) return this
    val back = layer(returnTo)?.id ?: startLayer
    val layer = Layer(
        id = id,
        rows = listOf(
            Row(listOf(NEW_KEY)),
            Row(
                listOf(
                    KeyDef(label = back, action = KeyAction.SwitchLayer(back), width = 1.5f, look = KeyLook.FUNCTION),
                    KeyDef(action = KeyAction.Space, width = 5f),
                    KeyDef(action = KeyAction.Backspace, width = 1.5f, look = KeyLook.FUNCTION),
                )
            ),
        ),
    )
    return copy(layers = layers + layer)
}

/** Copies a layer under a new name and returns the name it got. */
fun KeyboardDef.duplicateLayer(id: String): Pair<KeyboardDef, String> {
    val source = layer(id) ?: return this to id
    val copyId = uniqueName(layers.map { it.id }, "$id copy")
    val at = layers.indexOf(source) + 1
    return copy(layers = layers.toMutableList().apply { add(at, source.copy(id = copyId)) }) to copyId
}

/** Renames a layer and every key that opens it. Keys labelled with the old name are relabelled. */
fun KeyboardDef.renameLayer(oldId: String, newName: String): KeyboardDef {
    val newId = newName.trim()
    if (layer(oldId) == null || newId == oldId || layerNameProblem(newId, renaming = oldId) != null) return this
    return copy(
        startLayer = if (startLayer == oldId) newId else startLayer,
        layers = layers.map { layer ->
            layer.copy(
                id = if (layer.id == oldId) newId else layer.id,
                rows = layer.rows.map { row ->
                    row.copy(keys = row.keys.map { key ->
                        val action = key.action
                        if (action is KeyAction.SwitchLayer && action.layer == oldId) {
                            key.copy(
                                action = KeyAction.SwitchLayer(newId),
                                label = if (key.label == oldId) newId else key.label,
                            )
                        } else key
                    })
                },
            )
        },
    )
}

fun KeyboardDef.canDeleteLayer(id: String): Boolean = layers.size > 1 && layer(id) != null

/** Removes a layer. Keys that opened it are left in place but do nothing, so you can re-point them. */
fun KeyboardDef.deleteLayer(id: String): KeyboardDef {
    if (!canDeleteLayer(id)) return this
    val remaining = layers.filter { it.id != id }.map { layer ->
        layer.copy(rows = layer.rows.map { row ->
            row.copy(keys = row.keys.map { key ->
                if (key.action == KeyAction.SwitchLayer(id)) key.copy(action = KeyAction.None) else key
            })
        })
    }
    return copy(startLayer = if (startLayer == id) remaining.first().id else startLayer, layers = remaining)
}

fun KeyboardDef.withStartLayer(id: String): KeyboardDef = if (layer(id) == null) this else copy(startLayer = id)

/** Things that will trip you up when typing, in plain words. Empty when all is well. */
fun KeyboardDef.warnings(): List<String> {
    val result = mutableListOf<String>()
    val allKeys = layers.flatMap { layer -> layer.rows.flatMap { it.keys } }
    if (layers.size > 1) {
        for (layer in layers) {
            val opened = allKeys.any { it.action == KeyAction.SwitchLayer(layer.id) }
            if (layer.id != startLayer && !opened) {
                result += "No key opens the layer \"${layer.id}\", so you cannot reach it."
            }
            val leaves = layer.rows.any { row ->
                row.keys.any { key -> (key.action as? KeyAction.SwitchLayer)?.layer.let { it != null && it != layer.id } }
            }
            if (!leaves && (layer.id == startLayer || opened)) {
                result += "The layer \"${layer.id}\" has no key that opens another layer."
            }
        }
    }
    val dead = allKeys.count { it.action == KeyAction.None && it.look != KeyLook.SPACER && it.label.isNotEmpty() }
    if (dead > 0) result += if (dead == 1) "One key has a label but does nothing." else "$dead keys have a label but do nothing."
    if (allKeys.none { it.action == KeyAction.Backspace }) result += "There is no backspace key."
    return result
}

// ------------------------------------------------------------------ undo

/**
 * Remembers earlier versions of the keyboard so edits can be undone.
 *
 * Edits made with the same [tag] one after another (dragging one slider) count as a
 * single step, so one undo takes back the whole drag instead of a hair's width of it.
 */
data class EditHistory(
    private val past: List<KeyboardDef> = emptyList(),
    private val lastTag: String? = null,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val steps: Int get() = past.size

    /** Call BEFORE applying an edit, with the keyboard as it is now. */
    fun record(before: KeyboardDef, tag: String?): EditHistory {
        if (tag != null && tag == lastTag && past.isNotEmpty()) return this
        return EditHistory((past + before).takeLast(LIMIT), tag)
    }

    /** The previous keyboard and the history without it, or null if there is nothing to undo. */
    fun undo(): Pair<KeyboardDef, EditHistory>? {
        val previous = past.lastOrNull() ?: return null
        return previous to EditHistory(past.dropLast(1), null)
    }

    companion object {
        const val LIMIT = 50
    }
}

// ------------------------------------------------------------------ names

/** [wanted] if it is free, otherwise "wanted 2", "wanted 3"... */
fun uniqueName(existing: Collection<String>, wanted: String): String {
    val base = wanted.trim().ifEmpty { "Keyboard" }
    if (base !in existing) return base
    var n = 2
    while ("$base $n" in existing) n++
    return "$base $n"
}

/** A file-safe id that is not in use yet: "keyboard-1", "keyboard-2"... */
fun uniqueKeyboardId(existing: Collection<String>): String {
    var n = 1
    while ("keyboard-$n" in existing) n++
    return "keyboard-$n"
}

/** Why [name] cannot be used for a keyboard, or null if it is fine. */
fun keyboardNameProblem(name: String): String? = when {
    name.isBlank() -> "Give the keyboard a name."
    name.trim().length > 30 -> "Keep the name to 30 characters or fewer."
    else -> null
}
