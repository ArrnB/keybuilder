package app.keybuilder.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.model.FONT_FAMILIES
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyStyle
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.Theme
import app.keybuilder.model.ThemeColor
import app.keybuilder.model.ThemePreset
import app.keybuilder.model.ThemePresets
import app.keybuilder.model.ThemeSize
import app.keybuilder.model.backgroundFor
import app.keybuilder.model.describe
import app.keybuilder.model.formatNumber
import app.keybuilder.model.keyAt
import app.keybuilder.model.styledKeyCount
import app.keybuilder.model.textColorFor
import app.keybuilder.model.withKeyStyle
import app.keybuilder.model.withLook
import app.keybuilder.model.withoutKeyStyles
import kotlin.math.roundToInt

private const val TAB_THEME = 0
private const val TAB_KEYS = 1

/**
 * The style editor. Controls on top, a live preview of the keyboard at the bottom.
 *
 * This screen owns no data. It shows [keyboard], and every change is handed back through
 * [onChange] as a new, changed copy. Changes are saved automatically a moment after you
 * stop adjusting.
 */
@Composable
fun StyleScreen(
    keyboard: KeyboardDef,
    onChange: (KeyboardDef) -> Unit,
    onSave: (KeyboardDef) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(TAB_THEME) }
    var previewState by remember { mutableStateOf(KeyboardEngine.initialState(keyboard)) }
    var selectedKey by remember { mutableStateOf<KeyPos?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    AutoSave(keyboard, onSave)

    val pickKey: (KeyPos) -> Unit = { selectedKey = it }
    val shownLayer = KeyboardEngine.currentLayer(keyboard, previewState)

    Scaffold { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "Style",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { confirmReset = true }) { Text("Reset") }
            }

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("Whole keyboard", selected = tab == TAB_THEME, onClick = { tab = TAB_THEME })
                Pill("Single keys", selected = tab == TAB_KEYS, onClick = { tab = TAB_KEYS })
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tab == TAB_THEME) {
                    ThemeControls(
                        theme = keyboard.theme,
                        onTheme = { onChange(keyboard.copy(theme = it)) },
                    )
                } else {
                    KeyControls(
                        keyboard = keyboard,
                        shownLayerId = shownLayer.id,
                        selected = selectedKey,
                        onLayer = { layerId ->
                            previewState = previewState.copy(layerId = layerId)
                            selectedKey = null
                        },
                        onChange = onChange,
                    )
                }
            }

            Text(
                text = if (tab == TAB_THEME) "Preview. Shift and the layer keys work here."
                else "Tap a key to select it.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            KeyboardView(
                def = keyboard,
                state = previewState,
                onKey = { key ->
                    previewState = KeyboardEngine.press(keyboard, previewState, key, SystemClock.uptimeMillis()).state
                },
                selected = if (tab == TAB_KEYS) selectedKey else null,
                onKeyAt = if (tab == TAB_KEYS) pickKey else null,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset the style?") },
            text = {
                Text(
                    "Colours, sizes and letters go back to the original, and every single-key " +
                        "style is removed. Your keys and layers stay as they are."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(keyboard.copy(theme = Theme()).withoutKeyStyles())
                    selectedKey = null
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

// ------------------------------------------------------------------ whole keyboard

@Composable
private fun ThemeControls(theme: Theme, onTheme: (Theme) -> Unit) {
    var editing by remember { mutableStateOf<ThemeColor?>(null) }

    SectionTitle("Ready-made looks")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (preset in ThemePresets.all) {
            PresetCard(preset, onClick = { onTheme(theme.withLook(preset.theme)) })
        }
    }

    SectionTitle("Colours")
    for (field in ThemeColor.entries) {
        ColorRow(field.label, field.get(theme), onClick = { editing = field })
    }

    SectionTitle("Shape and size")
    for (field in ThemeSize.entries) {
        val value = field.get(theme)
        SliderRow(
            label = field.label,
            valueText = formatNumber(value),
            value = value,
            min = field.min,
            max = field.max,
            onChange = { onTheme(field.set(theme, it)) },
        )
    }

    SectionTitle("Letters")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (family in FONT_FAMILIES) {
            Pill(
                text = fontFamilyName(family),
                selected = theme.fontFamily == family,
                onClick = { onTheme(theme.copy(fontFamily = family)) },
            )
        }
        Pill("Bold", selected = theme.bold, onClick = { onTheme(theme.copy(bold = !theme.bold)) })
        // The small character in a key's corner that shows its first long-press choice.
        Pill("Corner hints", selected = theme.hints, onClick = { onTheme(theme.copy(hints = !theme.hints)) })
    }

    val field = editing
    if (field != null) {
        ColorPickerDialog(
            title = field.label,
            initial = field.get(theme),
            onDismiss = { editing = null },
            onPick = { hex ->
                onTheme(field.set(theme, hex))
                editing = null
            },
        )
    }
}

private fun fontFamilyName(family: String): String = when (family) {
    "serif" -> "Serif"
    "mono" -> "Typewriter"
    else -> "Plain"
}

/** A tiny picture of a look: its background with three keys and the accent key. */
@Composable
private fun PresetCard(preset: ThemePreset, onClick: () -> Unit) {
    val t = preset.theme
    val cardShape = RoundedCornerShape(10.dp)
    val keyShape = RoundedCornerShape((t.cornerRadiusDp / 2.5f).dp)
    val borderColor = t.borderColor.toColorOrNull() ?: Color.Transparent
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(cardShape)
                .background(t.background.toColorOrNull() ?: Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outline, cardShape)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val colors = listOf(t.keyColor, t.keyColor, t.functionKeyColor, t.accentColor)
            for (hex in colors) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .background(hex.toColorOrNull() ?: Color.Transparent, keyShape)
                        .then(if (t.borderWidthDp > 0f) Modifier.border(1.dp, borderColor, keyShape) else Modifier),
                )
            }
        }
        Text(preset.name, style = MaterialTheme.typography.labelMedium)
    }
}

// ------------------------------------------------------------------ single keys

private enum class KeyColorField { BACKGROUND, TEXT }

@Composable
private fun KeyControls(
    keyboard: KeyboardDef,
    shownLayerId: String,
    selected: KeyPos?,
    onLayer: (String) -> Unit,
    onChange: (KeyboardDef) -> Unit,
) {
    var editing by remember { mutableStateOf<KeyColorField?>(null) }

    Text(
        "Give one key its own colours or letter size. Everything you leave alone keeps following the theme.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (keyboard.layers.size > 1) {
        SectionTitle("Layer")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (layer in keyboard.layers) {
                Pill(layer.id, selected = layer.id == shownLayerId, onClick = { onLayer(layer.id) })
            }
        }
    }

    val styledCount = keyboard.styledKeyCount()
    val keyDef = if (selected == null) null else keyboard.keyAt(selected)

    if (selected == null || keyDef == null) {
        Text(
            "No key selected. Tap one in the preview below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val theme = keyboard.theme
        val style = keyDef.style ?: KeyStyle()

        SectionTitle("Selected key: ${keyDef.describe()}")
        ColorRow(
            label = "Background",
            hex = style.background,
            emptyText = "Theme colour",
            onClick = { editing = KeyColorField.BACKGROUND },
        )
        ColorRow(
            label = "Letter or icon",
            hex = style.textColor,
            emptyText = "Theme colour",
            onClick = { editing = KeyColorField.TEXT },
        )
        if (keyDef.label.isNotEmpty()) {
            val ownSize = style.fontSizeSp
            SliderRow(
                label = "Letter size",
                valueText = if (ownSize == null) "Theme size" else formatNumber(ownSize),
                value = ownSize ?: theme.fontSizeSp,
                min = 10f,
                max = 34f,
                onChange = { size ->
                    onChange(keyboard.withKeyStyle(selected, style.copy(fontSizeSp = size.roundToInt().toFloat())))
                },
            )
        }
        OutlinedButton(
            onClick = { onChange(keyboard.withKeyStyle(selected, null)) },
            enabled = keyDef.style != null,
        ) { Text("Put this key back to the theme") }

        val field = editing
        if (field != null) {
            val isBackground = field == KeyColorField.BACKGROUND
            val current =
                if (isBackground) style.background ?: theme.backgroundFor(keyDef.look)
                else style.textColor ?: theme.textColorFor(keyDef.look)
            ColorPickerDialog(
                title = if (isBackground) "Key background" else "Letter or icon",
                initial = current,
                onDismiss = { editing = null },
                onPick = { hex ->
                    val changed = if (isBackground) style.copy(background = hex) else style.copy(textColor = hex)
                    onChange(keyboard.withKeyStyle(selected, changed))
                    editing = null
                },
            )
        }
    }

    if (styledCount > 0) {
        TextButton(onClick = { onChange(keyboard.withoutKeyStyles()) }) {
            Text("Clear all single-key styles ($styledCount)")
        }
    }
}
