package app.keybuilder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.keybuilder.model.KeyboardDef
import kotlinx.coroutines.delay

/* Small building blocks shared by the editor screens. */

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The top line of a screen: Back on the left, a title, and optionally one action on the right. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    actionText: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (actionText != null) {
            TextButton(onClick = onAction, enabled = actionEnabled) { Text(actionText) }
        } else {
            // Keeps the title centred when there is no action.
            TextButton(onClick = {}, enabled = false) { Text("") }
        }
    }
}

/**
 * A rounded button. Used for choices (one of several is [selected]) and for small actions
 * (never selected). A disabled pill is faded and ignores taps.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val fade = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background((if (selected) colors.primary else colors.surfaceVariant).copy(alpha = fade))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = (if (selected) colors.onPrimary else colors.onSurfaceVariant).copy(alpha = fade),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** A line of pills that scrolls sideways when it does not fit. */
@Composable
fun ScrollRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A round sample of a colour. A missing colour shows as an empty circle. */
@Composable
fun ColorSwatch(hex: String?, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(hex?.toColorOrNull() ?: Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** One line of the form "● Letter keys            #343842". Tap it to change the colour. */
@Composable
fun ColorRow(label: String, hex: String?, onClick: () -> Unit, emptyText: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColorSwatch(hex)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = hex ?: emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One line of the form "Written on it                q". Tap it to change the text. */
@Composable
fun TextRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** A label, the current value, and a slider underneath. */
@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

/** A coloured box listing things that need attention. */
@Composable
fun WarningBox(warnings: List<String>) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.errorContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Worth checking", style = MaterialTheme.typography.labelLarge, color = colors.onErrorContainer)
        for (warning in warnings) {
            Text(warning, style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
        }
    }
}

/**
 * A pop-up with one text box. [problem] is asked on every keystroke; while it returns a
 * message, the message is shown and Done is switched off.
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    fieldLabel: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
    hint: String? = null,
    problem: (String) -> String? = { null },
) {
    var text by remember { mutableStateOf(initial) }
    val issue = problem(text)
    // Do not scold an empty box before anything has been typed.
    val shownIssue = if (text.isEmpty()) null else issue
    val note = shownIssue ?: hint

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    isError = shownIssue != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shownIssue != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(text) }, enabled = issue == null) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Saves a little while after the last change, and once more when the screen closes. */
@Composable
fun AutoSave(keyboard: KeyboardDef, onSave: (KeyboardDef) -> Unit) {
    var saved by remember { mutableStateOf(keyboard) }
    val latest by rememberUpdatedState(keyboard)
    val save by rememberUpdatedState(onSave)

    LaunchedEffect(keyboard) {
        if (keyboard != saved) {
            delay(400) // dragging a slider restarts this wait, so only the final value is written
            save(keyboard)
            saved = keyboard
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (latest != saved) save(latest)
        }
    }
}

/**
 * The pop-up for adding or changing a text shortcut: what you type, and what it becomes.
 * [problem] explains why the two cannot be saved, or returns null when they can.
 * [onDelete] is given when an existing shortcut is being changed, and adds a Delete button.
 */
@Composable
fun ShortcutDialog(
    initialShort: String,
    initialText: String,
    problem: (String, String) -> String?,
    onDismiss: () -> Unit,
    onDone: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var short by remember { mutableStateOf(initialShort) }
    var text by remember { mutableStateOf(initialText) }
    val issue = problem(short, text)
    // Do not scold empty boxes before anything has been typed.
    val shownIssue = if (short.isEmpty() || text.isEmpty()) null else issue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "New shortcut" else "Change shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = short,
                    onValueChange = { short = it },
                    label = { Text("Shortcut") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Turns into") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = shownIssue ?: "Type the shortcut, then a space, comma, full stop or enter, and it turns " +
                        "into the text. Capitals do not matter. Backspace straight after puts the shortcut back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (shownIssue != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(short.trim(), text) }, enabled = issue == null) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
