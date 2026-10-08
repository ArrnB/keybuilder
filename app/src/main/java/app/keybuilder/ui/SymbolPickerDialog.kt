package app.keybuilder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import app.keybuilder.model.SymbolData

/**
 * A catalogue of characters to tap: letters, numbers, punctuation, maths, money, arrows and
 * more (the list is model/SymbolData.kt). Used to put a character on a key, or to add it
 * to a key's long-press choices, without having to find a way to type it.
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
                // key(...) starts each group scrolled to its top.
                key(group) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(44.dp),
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(groups[group].symbols) { symbol ->
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
