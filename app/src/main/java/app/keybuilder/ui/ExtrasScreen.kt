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
import kotlin.math.roundToInt
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.MAX_HEIGHT_PERCENT
import app.keybuilder.model.MIN_HEIGHT_PERCENT
import app.keybuilder.model.Shortcut
import app.keybuilder.model.Shortcuts
import app.keybuilder.model.shortcutProblem
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
    screenshotsOn: Boolean,
    screenshotNote: String?,
    onScreenshots: (Boolean) -> Unit,
    typing: TypingSettings,
    onTyping: (TypingSettings) -> Unit,
    learnedCount: Int,
    onForgetWords: () -> Unit,
    shortcuts: Shortcuts,
    onShortcuts: (Shortcuts) -> Unit,
    giphyKey: String,
    onGiphyKey: (String) -> Unit,
    onOpenGiphySite: () -> Unit,
    onBack: () -> Unit,
) {
    var editingKey by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var addingShortcut by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }

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
                if (typing.vibrate) {
                    SliderRow(
                        label = "Vibration strength",
                        valueText = "${typing.vibrateStrength}%",
                        value = typing.vibrateStrength.toFloat(),
                        min = 5f,
                        max = 100f,
                        onChange = { value ->
                            // Steps of 5, and you feel each step as you drag (MainActivity buzzes on change).
                            val stepped = ((value / 5f).roundToInt() * 5).coerceIn(5, 100)
                            if (stepped != typing.vibrateStrength) onTyping(typing.copy(vibrateStrength = stepped))
                        },
                    )
                }
                OnOffRow(
                    label = "Key sounds",
                    on = typing.sounds,
                    onChange = { onTyping(typing.copy(sounds = it)) },
                )
                if (typing.sounds) {
                    SliderRow(
                        label = "Sound volume",
                        valueText = "${typing.soundVolume}%",
                        value = typing.soundVolume.toFloat(),
                        min = 5f,
                        max = 100f,
                        onChange = { value ->
                            val stepped = ((value / 5f).roundToInt() * 5).coerceIn(5, 100)
                            if (stepped != typing.soundVolume) onTyping(typing.copy(soundVolume = stepped))
                        },
                    )
                    OnOffRow(
                        label = "Play them even when the phone is on silent",
                        on = typing.soundsWhenSilent,
                        onChange = { onTyping(typing.copy(soundsWhenSilent = it)) },
                    )
                    Text(
                        "Which sound the keys make is part of each keyboard's style: Edit style > Sound and " +
                            "movement. Normally key sounds follow the phone and stay quiet on silent or " +
                            "vibrate. Played \"even when silent\" they follow the media volume instead.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SliderRow(
                    label = "Keyboard height",
                    valueText = "${typing.keyboardHeight}%",
                    value = typing.keyboardHeight.toFloat(),
                    min = MIN_HEIGHT_PERCENT.toFloat(),
                    max = MAX_HEIGHT_PERCENT.toFloat(),
                    onChange = { value ->
                        val stepped = ((value / 5f).roundToInt() * 5).coerceIn(MIN_HEIGHT_PERCENT, MAX_HEIGHT_PERCENT)
                        if (stepped != typing.keyboardHeight) onTyping(typing.copy(keyboardHeight = stepped))
                    },
                )
                Text(
                    "Makes every keyboard taller or shorter. Letters and icons grow and shrink evenly " +
                        "with the keys, so nothing is stretched. It applies to the real keyboard; the " +
                        "previews inside this app stay at normal size.",
                    style = MaterialTheme.typography.bodySmall,
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
                OnOffRow(
                    label = "Symbols instead of words on the top bar",
                    on = typing.stripIcons,
                    onChange = { onTyping(typing.copy(stripIcons = it)) },
                )
                Text(
                    "Stronger means a longer and harder buzz. Some phones can only make it longer.",
                    style = MaterialTheme.typography.bodySmall,
                )

                SectionTitle("Suggestions and autocorrect")
                Text(
                    "English and Hinglish. While you type, the strip above the keys offers words: tap " +
                        "one to use it. When you finish a word, an obvious slip (teh, dont, recieve) is " +
                        "fixed by itself. Press backspace straight after a fix to get your own word " +
                        "back; the keyboard then leaves that word alone for good. Hinglish words are " +
                        "suggested but never changed, because Hinglish has no one right spelling.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OnOffRow(
                    label = "Word suggestions",
                    on = typing.suggestions,
                    onChange = { onTyping(typing.copy(suggestions = it)) },
                )
                OnOffRow(
                    label = "Fix slips automatically",
                    on = typing.autocorrect,
                    onChange = { onTyping(typing.copy(autocorrect = it)) },
                )
                OnOffRow(
                    label = "Learn the words I type",
                    on = typing.learn,
                    onChange = { onTyping(typing.copy(learn = it)) },
                )
                Text(
                    "Learning is how your own words, names and Hinglish spellings get suggested. What " +
                        "is learned is kept in one file on this phone and is never sent anywhere; the " +
                        "keyboard has no way to send it. Nothing is read or learned in password boxes, " +
                        "in boxes for e-mail addresses and links, or in a browser's private mode.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { confirmForget = true }, enabled = learnedCount > 0) {
                    Text("Forget learned words ($learnedCount)")
                }

                SectionTitle("Text shortcuts")
                Text(
                    "A few characters that turn into a longer text: type \"omw\" and a space, and it " +
                        "becomes \"on my way\". Good for your address, e-mail, or anything you type often. " +
                        "They work everywhere except password boxes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                for (item in shortcuts.items) {
                    TextRow(
                        label = item.short,
                        value = item.text.replace('\n', ' ').let { if (it.length > 28) it.take(27) + "…" else it },
                        onClick = { editingShortcut = item },
                    )
                }
                OutlinedButton(onClick = { addingShortcut = true }, enabled = shortcuts.items.size < Shortcuts.MAX_SHORTCUTS) {
                    Text("Add a shortcut")
                }

                SectionTitle("Last copied thing")
                Text(
                    "Shows whatever you copied last on the strip, next to the gear. Tap it to paste; " +
                        "hold it to hide it until you copy something else. It works without the " +
                        "clipboard history (pictures need the history on). Passwords copied from a " +
                        "password manager are shown as dots.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OnOffRow(
                    label = "Show it on the strip",
                    on = typing.quickClip,
                    onChange = { onTyping(typing.copy(quickClip = it)) },
                )

                SectionTitle("Shortcut strip")
                Text(
                    "The strip above the keys with the Clipboard, GIF and Emoji buttons. With the " +
                        "buttons hidden the strip still appears for suggestions, if those are on. This setting " +
                        "belongs to the keyboard in use (${keyboard.name}). You can also put a key that " +
                        "opens a panel anywhere: Edit keys, select a key, What it does, Opens a panel.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ScrollRow {
                    Pill("Show the strip", selected = keyboard.toolbar, onClick = { onChange(keyboard.copy(toolbar = true)) })
                    Pill("Hide it", selected = !keyboard.toolbar, onClick = { onChange(keyboard.copy(toolbar = false)) })
                }

                SectionTitle("Long-press choices")
                Text(
                    "Hold a key, slide to a choice, lift. With built-in choices on, keys you have not " +
                        "given choices of your own get sensible ones: 1 to 0 on a top row of letters, " +
                        "accents on letters, related marks on punctuation. This setting belongs to the " +
                        "keyboard in use (${keyboard.name}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OnOffRow(
                    label = "Built-in choices",
                    on = keyboard.builtInChoices,
                    onChange = { onChange(keyboard.copy(builtInChoices = it)) },
                )

                SectionTitle("Clipboard history")
                Text(
                    "Keeps the last 30 things you copy, text and pictures, so you can paste them again. " +
                        "Pin an item in the clipboard panel and it stays until you remove it. " +
                        "It stays on this phone and is never sent anywhere. Things a password manager " +
                        "marks as secret are skipped. It only works while KeyBuilder is your keyboard.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ScrollRow {
                    Pill("On", selected = clipboardOn, onClick = { onClipboardOn(true) })
                    Pill("Off", selected = !clipboardOn, onClick = { onClipboardOn(false) })
                }
                OutlinedButton(onClick = { confirmErase = true }, enabled = clipboardCount > 0) {
                    Text("Erase unpinned items ($clipboardCount)")
                }
                OnOffRow(
                    label = "Add new screenshots to the clipboard",
                    on = screenshotsOn,
                    onChange = onScreenshots,
                )
                Text(
                    "Android does not put screenshots on the clipboard, so the keyboard has to fetch " +
                        "them from the phone's gallery. For that Android asks you to let KeyBuilder " +
                        "see your photos; choose \"Allow all\" (with \"Select photos\" it cannot see new " +
                        "screenshots). It only ever looks at new screenshots, copies them into the " +
                        "clipboard history on this phone, and sends nothing anywhere. It needs the " +
                        "clipboard history to be on, and starts with screenshots taken from now.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (screenshotNote != null) WarningBox(listOf(screenshotNote))

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

    if (addingShortcut) {
        ShortcutDialog(
            initialShort = "",
            initialText = "",
            problem = { short, text -> shortcutProblem(short, text, shortcuts) },
            onDismiss = { addingShortcut = false },
            onDone = { short, text ->
                onShortcuts(shortcuts.with(short, text))
                addingShortcut = false
            },
        )
    }

    val changing = editingShortcut
    if (changing != null) {
        ShortcutDialog(
            initialShort = changing.short,
            initialText = changing.text,
            problem = { short, text -> shortcutProblem(short, text, shortcuts, replacing = changing.short) },
            onDismiss = { editingShortcut = null },
            onDone = { short, text ->
                // Renaming a shortcut is removing the old one and adding the new one.
                onShortcuts(shortcuts.without(changing.short).with(short, text))
                editingShortcut = null
            },
            onDelete = {
                onShortcuts(shortcuts.without(changing.short))
                editingShortcut = null
            },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget learned words?") },
            text = {
                Text(
                    "The $learnedCount words the keyboard picked up from your typing are deleted, along " +
                        "with the words you told it to leave alone. The built-in word list stays. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onForgetWords()
                    confirmForget = false
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase the clipboard history?") },
            text = {
                Text(
                    "The $clipboardCount unpinned items are removed. Pinned items stay; remove those one " +
                        "by one in the clipboard panel. This cannot be undone."
                )
            },
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
