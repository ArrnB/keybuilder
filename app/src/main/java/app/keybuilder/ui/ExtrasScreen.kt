package app.keybuilder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.keybuilder.model.KeyboardDef
import app.keybuilder.store.TypingSettings

/** A setting that is either on or off: its name, then On and Off. */
@Composable
private fun OnOffRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Pill("On", selected = on, onClick = { onChange(true) })
        Pill("Off", selected = !on, onClick = { onChange(false) })
    }
}

/**
 * Settings for typing, the shortcut strip and its three panels: clipboard, GIFs and emoji.
 * Like the other screens it only shows values and reports what was tapped;
 * MainActivity does the storing.
 */
@Composable
fun ExtrasScreen(
    keyboard: KeyboardDef,
    onChange: (KeyboardDef) -> Unit,
    onSave: (KeyboardDef) -> Unit,
    clipboardOn: Boolean,
    clipboardCount: Int,
    onClipboardOn: (Boolean) -> Unit,
    onEraseClipboard: () -> Unit,
    typing: TypingSettings,
    onTyping: (TypingSettings) -> Unit,
    giphyKey: String,
    onGiphyKey: (String) -> Unit,
    onOpenGiphySite: () -> Unit,
    onBack: () -> Unit,
) {
    var editingKey by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    AutoSave(keyboard, onSave)

    Scaffold { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            ScreenHeader(title = "Settings", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle("Typing")
                Text("These apply to every keyboard.", style = MaterialTheme.typography.bodyMedium)
                OnOffRow(
                    label = "Vibrate when a key is touched",
                    on = typing.vibrate,
                    onChange = { onTyping(typing.copy(vibrate = it)) },
                )
                OnOffRow(
                    label = "Show the letter above a pressed key",
                    on = typing.popups,
                    onChange = { onTyping(typing.copy(popups = it)) },
                )
                OnOffRow(
                    label = "Two spaces type a full stop",
                    on = typing.doubleSpace,
                    onChange = { onTyping(typing.copy(doubleSpace = it)) },
                )
                Text(
                    "Vibration also needs \"touch feedback\" to be on in the phone's own sound settings.",
                    style = MaterialTheme.typography.bodySmall,
                )

                SectionTitle("Shortcut strip")
                Text(
                    "The strip above the keys with the Clipboard, GIF and Emoji buttons. This setting " +
                        "belongs to the keyboard in use (${keyboard.name}). You can also put a key that " +
                        "opens a panel anywhere: Edit keys, select a key, What it does, Opens a panel.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ScrollRow {
                    Pill("Show the strip", selected = keyboard.toolbar, onClick = { onChange(keyboard.copy(toolbar = true)) })
                    Pill("Hide it", selected = !keyboard.toolbar, onClick = { onChange(keyboard.copy(toolbar = false)) })
                }

                SectionTitle("Clipboard history")
                Text(
                    "Keeps the last 30 things you copy, text and pictures, so you can paste them again. " +
                        "It stays on this phone and is never sent anywhere. Things a password manager " +
                        "marks as secret are skipped. It only works while KeyBuilder is your keyboard.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ScrollRow {
                    Pill("On", selected = clipboardOn, onClick = { onClipboardOn(true) })
                    Pill("Off", selected = !clipboardOn, onClick = { onClipboardOn(false) })
                }
                OutlinedButton(onClick = { confirmErase = true }, enabled = clipboardCount > 0) {
                    Text("Erase the history ($clipboardCount)")
                }

                SectionTitle("GIF search")
                Text(
                    "GIFs come from GIPHY, which needs a key that identifies your copy of the app. " +
                        "A key is free and allows 100 searches an hour. Create one on GIPHY's developer " +
                        "site (choose API, not SDK), copy it, and paste it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextRow(
                    label = "GIPHY key",
                    value = if (giphyKey.isEmpty()) "not set" else giphyKey.take(4) + "..." + giphyKey.takeLast(2),
                    onClick = { editingKey = true },
                )
                OutlinedButton(onClick = onOpenGiphySite) { Text("Open GIPHY's developer site") }
                Text(
                    "This is the only thing the keyboard goes online for: the search words you type in " +
                        "the GIF panel go to GIPHY, and the GIF you tap is downloaded. What you type in " +
                        "other apps never leaves the phone. Tap a GIF in a chat app to send it; where an " +
                        "app does not accept pictures, a link is typed instead.",
                    style = MaterialTheme.typography.bodySmall,
                )

                SectionTitle("Emoji")
                Text("Nothing to set up. Open it from the strip.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (editingKey) {
        TextInputDialog(
            title = "GIPHY key",
            initial = giphyKey,
            fieldLabel = "Key",
            hint = "Paste the key exactly as GIPHY shows it. Leave it empty to remove the key.",
            problem = { if (it.any { c -> c.isWhitespace() }) "A key has no spaces in it." else null },
            onDismiss = { editingKey = false },
            onDone = { key ->
                onGiphyKey(key.trim())
                editingKey = false
            },
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase the clipboard history?") },
            text = { Text("All $clipboardCount saved items are removed, pinned ones too. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onEraseClipboard()
                    confirmErase = false
                }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text("Cancel") }
            },
        )
    }
}
