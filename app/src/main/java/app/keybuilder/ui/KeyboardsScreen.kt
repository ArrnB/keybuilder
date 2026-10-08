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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.keyboardNameProblem

/**
 * The list of saved keyboards. One of them is "in use": it is the one you type with and
 * the one the Keys and Style screens change.
 */
@Composable
fun KeyboardsScreen(
    keyboards: List<KeyboardDef>,
    activeId: String,
    onUse: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
) {
    // Ids rather than whole keyboards, so a pop-up never shows a stale copy.
    var renamingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            ScreenHeader(title = "My keyboards", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (keyboard in keyboards) {
                    key(keyboard.id) {
                        val inUse = keyboard.id == activeId
                        val keyCount = keyboard.layers.sumOf { layer -> layer.rows.sumOf { it.keys.size } }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(keyboard.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${keyboard.layers.size} layers, $keyCount keys",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ScrollRow {
                                    Pill(
                                        text = if (inUse) "In use" else "Use this one",
                                        selected = inUse,
                                        onClick = { onUse(keyboard.id) },
                                    )
                                    Pill("Rename", selected = false, onClick = { renamingId = keyboard.id })
                                    Pill("Copy", selected = false, onClick = { onCopy(keyboard.id) })
                                    Pill(
                                        text = "Delete",
                                        selected = false,
                                        onClick = { deletingId = keyboard.id },
                                        enabled = keyboards.size > 1,
                                    )
                                }
                            }
                        }
                    }
                }

                Button(onClick = onNew) { Text("New keyboard") }
                Text(
                    "A new keyboard starts as a copy of the built-in QWERTY. To try an idea without " +
                        "risking a keyboard you like, copy it first and edit the copy.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    val renaming = keyboards.firstOrNull { it.id == renamingId }
    if (renaming != null) {
        TextInputDialog(
            title = "Rename keyboard",
            initial = renaming.name,
            fieldLabel = "Name",
            problem = { keyboardNameProblem(it) },
            onDismiss = { renamingId = null },
            onDone = { name ->
                onRename(renaming.id, name.trim())
                renamingId = null
            },
        )
    }

    val deleting = keyboards.firstOrNull { it.id == deletingId }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete \"${deleting.name}\"?") },
            text = { Text("The keyboard and its style are removed for good. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleting.id)
                    deletingId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("Cancel") }
            },
        )
    }
}
