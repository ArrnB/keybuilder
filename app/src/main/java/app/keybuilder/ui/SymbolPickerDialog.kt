package app.keybuilder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.SymbolData
import app.keybuilder.model.specialKeys
import app.keybuilder.model.typingKey

/** How tall the grid inside the pickers is. */
private val GRID_HEIGHT = 260.dp

/**
 * "Replace this key with...": every key there is. The first group holds the special keys
 * (shift, backspace, enter, space, arrows, copy and paste, panels, layers, a blank key, a gap);
 * the other groups are letters, numbers and symbols, each of which becomes a key that types it.
 *
 * Nothing here checks whether the choice makes sense. That is on purpose: any key can go anywhere.
 */
@Composable
fun KeyPickerDialog(
    keyboard: KeyboardDef,
    title: String,
    onDismiss: () -> Unit,
    onPick: (KeyDef) -> Unit,
) {
    val groups = SymbolData.groups
    val special = remember(keyboard) { keyboard.specialKeys() }
    // Groups 0..n-1 are the character groups; n is "Special keys". Letters are shown first.
    val specialGroup = groups.size
    var group by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScrollRow {
                    Pill("Special keys", selected = group == specialGroup, onClick = { group = specialGroup })
                    groups.forEachIndexed { index, item ->
                        Pill(item.name, selected = index == group, onClick = { group = index })
                    }
                }
                // key(...) starts each group scrolled to its top.
                key(group) {
                    if (group == specialGroup) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(128.dp),
                            modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(special) { template ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onPick(template.key) }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = template.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    } else {
                        SymbolGrid(groups[group].symbols, onPick = { onPick(typingKey(it)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * A catalogue of characters to tap (the list is model/SymbolData.kt). Used to add characters
 * to a key's long-press choices without having to find a way to type them.
 */
@Composable
fun SymbolPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var group by remember { mutableStateOf(0) }
    val groups = SymbolData.groups

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScrollRow {
                    groups.forEachIndexed { index, item ->
                        Pill(item.name, selected = index == group, onClick = { group = index })
                    }
                }
                key(group) {
                    SymbolGrid(groups[group].symbols, onPick)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** A grid of single characters, each a square to tap. */
@Composable
private fun SymbolGrid(symbols: List<String>, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(44.dp),
        modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(symbols) { symbol ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPick(symbol) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
