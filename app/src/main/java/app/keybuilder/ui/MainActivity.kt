package app.keybuilder.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutRepository

/** The screen you see when you open the app. For now: setup help, a test box and a preview. */
class MainActivity : ComponentActivity() {

    private var status by mutableStateOf(ImeStatus())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val keyboard = LayoutRepository.loadDefault(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SetupScreen(
                    status = status,
                    keyboard = keyboard,
                    onOpenSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    onPickKeyboard = { inputMethodManager().showInputMethodPicker() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // The keyboard picker is a pop-up, so closing it gives focus back without a full "resume".
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun inputMethodManager(): InputMethodManager = getSystemService(InputMethodManager::class.java)

    private fun refreshStatus() {
        val enabled = inputMethodManager().enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        status = ImeStatus(enabled = enabled, selected = current.startsWith("$packageName/"))
    }
}

private data class ImeStatus(val enabled: Boolean = false, val selected: Boolean = false)

@Composable
private fun SetupScreen(
    status: ImeStatus,
    keyboard: KeyboardDef,
    onOpenSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
) {
    var sample by rememberSaveable { mutableStateOf("") }
    var previewState by remember { mutableStateOf(KeyboardEngine.initialState(keyboard)) }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("KeyBuilder", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Two steps to start using the keyboard, then try it in the box below.",
                style = MaterialTheme.typography.bodyMedium,
            )

            StepCard(
                title = "1. Turn it on",
                body = "Android keeps every new keyboard switched off until you allow it. " +
                    "It shows the same warning for all keyboards. This app has no internet access.",
                done = status.enabled,
                buttonText = "Open keyboard settings",
                buttonEnabled = true,
                onClick = onOpenSettings,
            )
            StepCard(
                title = "2. Switch to it",
                body = "Pick KeyBuilder from the list of keyboards.",
                done = status.selected,
                buttonText = "Choose keyboard",
                buttonEnabled = status.enabled,
                onClick = onPickKeyboard,
            )

            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                label = { Text("Try typing here") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Current layout: ${keyboard.name}", style = MaterialTheme.typography.titleMedium)
            KeyboardView(
                def = keyboard,
                state = previewState,
                onKey = { key ->
                    previewState = KeyboardEngine.press(keyboard, previewState, key, SystemClock.uptimeMillis()).state
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            )
            Text(
                "This preview is drawn by the same code as the real keyboard. " +
                    "Shift and the layer keys work here; it does not type.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    done: Boolean,
    buttonText: String,
    buttonEnabled: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onClick, enabled = buttonEnabled) { Text(buttonText) }
                if (done) Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
