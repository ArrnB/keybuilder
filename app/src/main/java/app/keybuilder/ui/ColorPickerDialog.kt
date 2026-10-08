package app.keybuilder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.keybuilder.model.argbToHsv
import app.keybuilder.model.colorAlpha
import app.keybuilder.model.formatHexColor
import app.keybuilder.model.hsvToArgb
import app.keybuilder.model.parseHexColor
import kotlin.math.roundToInt

private val QUICK_COLORS = listOf(
    "#FFFFFF", "#000000", "#343842", "#9AA0A6", "#E53935", "#FB8C00", "#FDD835",
    "#43A047", "#00ACC1", "#1E88E5", "#5E35B1", "#D81B60",
)

/**
 * Pick a colour with four sliders, a row of quick choices, or by typing its code.
 * [onPick] receives "#RRGGBB" (or "#AARRGGBB" when partly see-through).
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val startArgb = parseHexColor(initial) ?: 0xFF000000.toInt()
    val startHsv = remember(initial) { argbToHsv(startArgb) }
    var hue by remember(initial) { mutableStateOf(startHsv.h) }
    var saturation by remember(initial) { mutableStateOf(startHsv.s) }
    var brightness by remember(initial) { mutableStateOf(startHsv.v) }
    var opacity by remember(initial) { mutableStateOf(colorAlpha(startArgb)) }
    var codeText by remember(initial) { mutableStateOf(formatHexColor(startArgb)) }

    val argb = hsvToArgb(hue, saturation, brightness, opacity)

    // Used by the sliders: change one part, keep the rest, and refresh the code box.
    fun update(h: Float = hue, s: Float = saturation, v: Float = brightness, a: Float = opacity) {
        hue = h
        saturation = s
        brightness = v
        opacity = a
        codeText = formatHexColor(hsvToArgb(h, s, v, a))
    }

    // Used by the quick choices and the code box: jump straight to a colour.
    fun jumpTo(color: Int) {
        val hsv = argbToHsv(color)
        hue = hsv.h
        saturation = hsv.s
        brightness = hsv.v
        opacity = colorAlpha(color)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val sampleShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(sampleShape)
                        .background(Color(argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, sampleShape),
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (hex in QUICK_COLORS) {
                        ColorSwatch(
                            hex = hex,
                            size = 32.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val color = parseHexColor(hex)
                                    if (color != null) {
                                        jumpTo(color)
                                        codeText = hex
                                    }
                                },
                        )
                    }
                }

                SliderRow("Colour", "${hue.roundToInt()}°", hue, 0f, 360f, onChange = { update(h = it) })
                SliderRow("Strength", percent(saturation), saturation, 0f, 1f, onChange = { update(s = it) })
                SliderRow("Brightness", percent(brightness), brightness, 0f, 1f, onChange = { update(v = it) })
                SliderRow("Opacity", percent(opacity), opacity, 0f, 1f, onChange = { update(a = it) })

                OutlinedTextField(
                    value = codeText,
                    onValueChange = { typed ->
                        codeText = typed
                        val color = parseHexColor(typed)
                        if (color != null) jumpTo(color)
                    },
                    label = { Text("Colour code") },
                    singleLine = true,
                    isError = parseHexColor(codeText) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(formatHexColor(argb)) }) { Text("Use colour") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"
