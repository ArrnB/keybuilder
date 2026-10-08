package app.keybuilder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.keybuilder.engine.KeyboardState
import app.keybuilder.model.ActionKind
import app.keybuilder.model.Edit
import app.keybuilder.model.EditHistory
import app.keybuilder.model.KEY_WIDTH_MAX
import app.keybuilder.model.KEY_WIDTH_MIN
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.Layer
import app.keybuilder.model.ROW_HEIGHT_MAX
import app.keybuilder.model.ROW_HEIGHT_MIN
import app.keybuilder.model.addLayer
import app.keybuilder.model.canDeleteKey
import app.keybuilder.model.canDeleteLayer
import app.keybuilder.model.canDeleteRow
import app.keybuilder.model.deleteKey
import app.keybuilder.model.deleteLayer
import app.keybuilder.model.deleteRow
import app.keybuilder.model.describe
import app.keybuilder.model.duplicateKey
import app.keybuilder.model.duplicateLayer
import app.keybuilder.model.formatNumber
import app.keybuilder.model.insertKey
import app.keybuilder.model.insertRow
import app.keybuilder.model.keyAt
import app.keybuilder.model.kind
import app.keybuilder.model.layerNameProblem
import app.keybuilder.model.moveKey
import app.keybuilder.model.moveKeyToRow
import app.keybuilder.model.moveRow
import app.keybuilder.model.renameLayer
import app.keybuilder.model.warnings
import app.keybuilder.model.withActionKind
import app.keybuilder.model.withKeyLabel
import app.keybuilder.model.withKeyLook
import app.keybuilder.model.withKeyText
import app.keybuilder.model.withKeyWidth
import app.keybuilder.model.withLayerTarget
import app.keybuilder.model.withRowHeight
import app.keybuilder.model.withShiftLabel
import app.keybuilder.model.withStartLayer

/** The pop-ups this screen can show. Only one is open at a time. */
private enum class LayoutDialog { LABEL, SHIFT_LABEL, KEY_TEXT, NEW_LAYER, RENAME_LAYER, DELETE_LAYER }

/**
 * The layout editor: which keys exist, where they sit and what they do.
 *
 * You tap a key in the preview at the bottom to select it, then use the controls above.
 * Like the style screen it owns no data: every change goes back through [onChange] as a
 * changed copy made by a function in LayoutEdits.kt. Because each edit produces a whole new
 * keyboard, Undo is simply "go back to the previous one".
 */
@Composable
fun LayoutScreen(
    keyboard: KeyboardDef,
    onChange: (KeyboardDef) -> Unit,
    onSave: (KeyboardDef) -> Unit,
    onBack: () -> Unit,
) {
    var layerId by rememberSaveable { mutableStateOf(keyboard.startLayer) }
    var selected by remember { mutableStateOf<KeyPos?>(null) }
    var history by remember { mutableStateOf(EditHistory()) }
    var dialog by remember { mutableStateOf<LayoutDialog?>(null) }

    BackHandler(onBack = onBack)
    AutoSave(keyboard, onSave)

    // The shown layer or the selected key can disappear (delete, undo), so both are re-checked here.
    val layer = keyboard.layer(layerId) ?: keyboard.layer(keyboard.startLayer) ?: keyboard.layers.first()
    val pos = selected?.takeIf { it.layerId == layer.id && keyboard.keyAt(it) != null }
    val keyDef = if (pos == null) null else keyboard.keyAt(pos)

    // Every edit goes through here. [tag] groups repeated edits (a slider drag) into one undo step.
    val commit: (String?, KeyboardDef) -> Unit = { tag, changed ->
        if (changed != keyboard) {
            history = history.record(keyboard, tag)
            onChange(changed)
        }
    }
    val applyEdit: (Edit) -> Unit = { edit ->
        commit(null, edit.def)
        selected = edit.selection
    }
    val showLayer: (String) -> Unit = { id ->
        layerId = id
        selected = null
    }
    val pickKey: (KeyPos) -> Unit = { selected = it }
    val closeDialog: () -> Unit = { dialog = null }

    Scaffold { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            ScreenHeader(
                title = "Keys",
                onBack = onBack,
                actionText = "Undo",
                actionEnabled = history.canUndo,
                onAction = {
                    val step = history.undo()
                    if (step != null) {
                        history = step.second
                        onChange(step.first)
                        selected = null
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val warnings = keyboard.warnings()
                if (warnings.isNotEmpty()) WarningBox(warnings)

                LayerControls(
                    keyboard = keyboard,
                    layer = layer,
                    onShow = showLayer,
                    onNew = { dialog = LayoutDialog.NEW_LAYER },
                    onRename = { dialog = LayoutDialog.RENAME_LAYER },
                    onCopy = {
                        val (copied, name) = keyboard.duplicateLayer(layer.id)
                        commit(null, copied)
                        showLayer(name)
                    },
                    onDelete = { dialog = LayoutDialog.DELETE_LAYER },
                    onMakeFirst = { commit(null, keyboard.withStartLayer(layer.id)) },
                )

                if (pos == null || keyDef == null) {
                    Text(
                        "Tap a key in the preview below to change it, move it, or add keys next to it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    KeyEditor(
                        keyboard = keyboard,
                        layer = layer,
                        pos = pos,
                        keyDef = keyDef,
                        commit = commit,
                        applyEdit = applyEdit,
                        openDialog = { dialog = it },
                    )
                }
            }

            Text(
                text = "Tap a key to select it. Faint outlines are gaps.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            KeyboardView(
                def = keyboard,
                state = KeyboardState(layerId = layer.id),
                onKey = {},
                selected = pos,
                onKeyAt = pickKey,
            )
        }
    }

    val open = dialog
    if (open == LayoutDialog.LABEL && pos != null && keyDef != null) {
        TextInputDialog(
            title = "Written on the key",
            initial = keyDef.label,
            fieldLabel = "Label",
            hint = "Leave it empty on shift, backspace and enter keys to show their icon.",
            onDismiss = closeDialog,
            onDone = { text ->
                commit(null, keyboard.withKeyLabel(pos, text))
                closeDialog()
            },
        )
    }
    if (open == LayoutDialog.SHIFT_LABEL && pos != null && keyDef != null) {
        TextInputDialog(
            title = "Written on the key with shift",
            initial = keyDef.shiftLabel ?: "",
            fieldLabel = "Shift label",
            hint = "This is also what the key types while shift is on. Leave it empty to use the label in capitals.",
            onDismiss = closeDialog,
            onDone = { text ->
                commit(null, keyboard.withShiftLabel(pos, text))
                closeDialog()
            },
        )
    }
    if (open == LayoutDialog.KEY_TEXT && pos != null && keyDef != null) {
        val action = keyDef.action
        TextInputDialog(
            title = "Text this key types",
            initial = if (action is KeyAction.Text) action.text else "",
            fieldLabel = "Text",
            hint = "Any length: an email address, a greeting, a phrase you type often.",
            onDismiss = closeDialog,
            onDone = { text ->
                commit(null, keyboard.withKeyText(pos, text))
                closeDialog()
            },
        )
    }
    if (open == LayoutDialog.NEW_LAYER) {
        TextInputDialog(
            title = "New layer",
            initial = "",
            fieldLabel = "Name",
            hint = "It starts with one key and a way back to \"${layer.id}\".",
            problem = { keyboard.layerNameProblem(it) },
            onDismiss = closeDialog,
            onDone = { name ->
                commit(null, keyboard.addLayer(name, returnTo = layer.id))
                showLayer(name.trim())
                closeDialog()
            },
        )
    }
    if (open == LayoutDialog.RENAME_LAYER) {
        TextInputDialog(
            title = "Rename layer",
            initial = layer.id,
            fieldLabel = "Name",
            hint = "Keys that open this layer keep working.",
            problem = { keyboard.layerNameProblem(it, renaming = layer.id) },
            onDismiss = closeDialog,
            onDone = { name ->
                commit(null, keyboard.renameLayer(layer.id, name))
                showLayer(name.trim())
                closeDialog()
            },
        )
    }
    if (open == LayoutDialog.DELETE_LAYER) {
        AlertDialog(
            onDismissRequest = closeDialog,
            title = { Text("Delete the layer \"${layer.id}\"?") },
            text = {
                Text(
                    "All its keys are removed. Keys on other layers that opened it stay where " +
                        "they are but do nothing until you point them somewhere else. Undo brings it back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val changed = keyboard.deleteLayer(layer.id)
                    commit(null, changed)
                    showLayer(changed.startLayer)
                    closeDialog()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = closeDialog) { Text("Cancel") }
            },
        )
    }
}

// ------------------------------------------------------------------ layers

@Composable
private fun LayerControls(
    keyboard: KeyboardDef,
    layer: Layer,
    onShow: (String) -> Unit,
    onNew: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMakeFirst: () -> Unit,
) {
    val isFirst = layer.id == keyboard.startLayer

    SectionTitle("Layer")
    ScrollRow {
        for (item in keyboard.layers) {
            Pill(item.id, selected = item.id == layer.id, onClick = { onShow(item.id) })
        }
        Pill("New layer", selected = false, onClick = onNew)
    }
    ScrollRow {
        Pill("Rename", selected = false, onClick = onRename)
        Pill("Copy", selected = false, onClick = onCopy)
        Pill("Delete", selected = false, onClick = onDelete, enabled = keyboard.canDeleteLayer(layer.id))
        Pill(
            text = if (isFirst) "Opens first" else "Make it open first",
            selected = false,
            onClick = onMakeFirst,
            enabled = !isFirst,
        )
    }
}

// ------------------------------------------------------------------ the selected key and its row

@Composable
private fun KeyEditor(
    keyboard: KeyboardDef,
    layer: Layer,
    pos: KeyPos,
    keyDef: KeyDef,
    commit: (String?, KeyboardDef) -> Unit,
    applyEdit: (Edit) -> Unit,
    openDialog: (LayoutDialog) -> Unit,
) {
    val row = layer.rows.getOrNull(pos.row) ?: return
    val kind = keyDef.action.kind()
    val action = keyDef.action
    val lastRow = layer.rows.size - 1
    val lastIndex = row.keys.size - 1

    SectionTitle("Selected key: ${keyDef.describe()}")
    ScrollRow {
        Pill("Move left", selected = false, onClick = { applyEdit(keyboard.moveKey(pos, -1)) }, enabled = pos.index > 0)
        Pill("Move right", selected = false, onClick = { applyEdit(keyboard.moveKey(pos, 1)) }, enabled = pos.index < lastIndex)
        Pill("Move up", selected = false, onClick = { applyEdit(keyboard.moveKeyToRow(pos, -1)) }, enabled = pos.row > 0)
        Pill("Move down", selected = false, onClick = { applyEdit(keyboard.moveKeyToRow(pos, 1)) }, enabled = pos.row < lastRow)
    }
    ScrollRow {
        Pill("Add key before", selected = false, onClick = { applyEdit(keyboard.insertKey(pos, after = false)) })
        Pill("Add key after", selected = false, onClick = { applyEdit(keyboard.insertKey(pos, after = true)) })
        Pill("Copy", selected = false, onClick = { applyEdit(keyboard.duplicateKey(pos)) })
        Pill("Delete", selected = false, onClick = { applyEdit(keyboard.deleteKey(pos)) }, enabled = keyboard.canDeleteKey(pos))
    }

    if (keyDef.look != KeyLook.SPACER) {
        TextRow(
            label = "Written on it",
            value = keyDef.label.ifEmpty { "nothing (icon or blank)" },
            onClick = { openDialog(LayoutDialog.LABEL) },
        )
        if (kind == ActionKind.LABEL) {
            TextRow(
                label = "With shift",
                value = keyDef.shiftLabel ?: "the label in capitals",
                onClick = { openDialog(LayoutDialog.SHIFT_LABEL) },
            )
        }

        SectionTitle("What it does")
        ScrollRow {
            for (option in ActionKind.entries) {
                Pill(
                    text = option.label,
                    selected = option == kind,
                    onClick = { commit(null, keyboard.withActionKind(pos, option)) },
                )
            }
        }
        if (action is KeyAction.Text) {
            TextRow(
                label = "Text it types",
                value = action.text.ifEmpty { "nothing yet" },
                onClick = { openDialog(LayoutDialog.KEY_TEXT) },
            )
        }
        if (action is KeyAction.SwitchLayer) {
            Text("Which layer it opens", style = MaterialTheme.typography.bodyLarge)
            ScrollRow {
                for (item in keyboard.layers) {
                    Pill(
                        text = item.id,
                        selected = item.id == action.layer,
                        onClick = { commit(null, keyboard.withLayerTarget(pos, item.id)) },
                    )
                }
            }
        }
    }

    SectionTitle("Size and look")
    SliderRow(
        label = "Width",
        valueText = formatNumber(keyDef.width),
        value = keyDef.width,
        min = KEY_WIDTH_MIN,
        max = KEY_WIDTH_MAX,
        onChange = { commit("width ${pos.layerId} ${pos.row} ${pos.index}", keyboard.withKeyWidth(pos, it)) },
    )
    ScrollRow {
        for (look in KeyLook.entries) {
            Pill(
                text = lookName(look),
                selected = keyDef.look == look,
                onClick = { commit(null, keyboard.withKeyLook(pos, look)) },
            )
        }
    }

    SectionTitle("The row it is in")
    SliderRow(
        label = "Row height",
        valueText = formatNumber(row.height),
        value = row.height,
        min = ROW_HEIGHT_MIN,
        max = ROW_HEIGHT_MAX,
        onChange = { commit("height ${pos.layerId} ${pos.row}", keyboard.withRowHeight(pos.layerId, pos.row, it)) },
    )
    ScrollRow {
        Pill("Add row below", selected = false, onClick = { applyEdit(keyboard.insertRow(pos.layerId, pos.row)) })
        Pill("Row up", selected = false, onClick = { applyEdit(keyboard.moveRow(pos, -1)) }, enabled = pos.row > 0)
        Pill("Row down", selected = false, onClick = { applyEdit(keyboard.moveRow(pos, 1)) }, enabled = pos.row < lastRow)
        Pill(
            text = "Delete row",
            selected = false,
            onClick = { applyEdit(keyboard.deleteRow(pos.layerId, pos.row)) },
            enabled = keyboard.canDeleteRow(pos.layerId, pos.row),
        )
    }
}

private fun lookName(look: KeyLook): String = when (look) {
    KeyLook.NORMAL -> "Normal"
    KeyLook.FUNCTION -> "Function"
    KeyLook.ACCENT -> "Accent"
    KeyLook.SPACER -> "Gap"
}
