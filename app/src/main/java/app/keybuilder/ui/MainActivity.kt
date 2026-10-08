package app.keybuilder.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.OutlinedButton
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
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutRepository
import app.keybuilder.model.uniqueKeyboardId
import app.keybuilder.model.uniqueName
import app.keybuilder.store.AppSettings
import app.keybuilder.store.ClipboardStore

private const val SCREEN_HOME = "home"
private const val SCREEN_KEYS = "keys"
private const val SCREEN_STYLE = "style"
private const val SCREEN_KEYBOARDS = "keyboards"
private const val SCREEN_EXTRAS = "extras"
private const val GIPHY_DEVELOPER_SITE = "https://developers.giphy.com/dashboard/"

/**
 * The app you open from the launcher.
 *
 * It holds the list of saved keyboards and which one is in use. Each screen is shown the
 * data it needs and reports back what the user did; all saving happens here.
 */
class MainActivity : ComponentActivity() {

    private var status by mutableStateOf(ImeStatus())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val context = this
        val startingKeyboards = LayoutRepository.list(context)
        val startingActive = LayoutRepository.activeId(context)
            ?.takeIf { id -> startingKeyboards.any { it.id == id } }
            ?: startingKeyboards.first().id.also { LayoutRepository.setActive(context, it) }

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                var keyboards by remember { mutableStateOf(startingKeyboards) }
                var activeId by remember { mutableStateOf(startingActive) }
                var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }

                // Settings for the panels. Read once here, written whenever they change.
                var giphyKey by remember { mutableStateOf(AppSettings.giphyKey(context)) }
                var clipboardOn by remember { mutableStateOf(AppSettings.clipboardEnabled(context)) }
                var clipboardCount by remember { mutableStateOf(0) }

                // The keyboard in use: the one you type with and the one the editors change.
                val keyboard = keyboards.firstOrNull { it.id == activeId } ?: keyboards.first()

                val replace: (KeyboardDef) -> Unit = { changed ->
                    keyboards = keyboards.map { if (it.id == changed.id) changed else it }
                }
                val save: (KeyboardDef) -> Unit = { LayoutRepository.save(context, it) }
                val goHome: () -> Unit = { screen = SCREEN_HOME }

                // Adds a keyboard under a fresh id and a name nobody else has, and saves it.
                val addCopyOf: (KeyboardDef, String) -> Unit = { source, wantedName ->
                    val copy = source.copy(
                        id = uniqueKeyboardId(keyboards.map { it.id }),
                        name = uniqueName(keyboards.map { it.name }, wantedName),
                    )
                    LayoutRepository.save(context, copy)
                    keyboards = keyboards + copy
                }

                when (screen) {
                    SCREEN_KEYS -> LayoutScreen(keyboard = keyboard, onChange = replace, onSave = save, onBack = goHome)

                    SCREEN_STYLE -> StyleScreen(keyboard = keyboard, onChange = replace, onSave = save, onBack = goHome)

                    SCREEN_KEYBOARDS -> KeyboardsScreen(
                        keyboards = keyboards,
                        activeId = keyboard.id,
                        onUse = { id ->
                            LayoutRepository.setActive(context, id)
                            activeId = id
                        },
                        onRename = { id, name ->
                            val renamed = keyboards.firstOrNull { it.id == id }?.copy(name = name)
                            if (renamed != null) {
                                LayoutRepository.save(context, renamed)
                                replace(renamed)
                            }
                        },
                        onCopy = { id ->
                            val source = keyboards.firstOrNull { it.id == id }
                            if (source != null) addCopyOf(source, "${source.name} copy")
                        },
                        onDelete = { id ->
                            val remaining = keyboards.filter { it.id != id }
                            if (remaining.isNotEmpty()) {
                                LayoutRepository.delete(context, id)
                                if (id == keyboard.id) {
                                    LayoutRepository.setActive(context, remaining.first().id)
                                    activeId = remaining.first().id
                                }
                                keyboards = remaining
                            }
                        },
                        onNew = {
                            val builtIn = LayoutRepository.loadDefault(context)
                            addCopyOf(builtIn, builtIn.name)
                        },
                        onBack = goHome,
                    )

                    SCREEN_EXTRAS -> ExtrasScreen(
                        keyboard = keyboard,
                        onChange = replace,
                        onSave = save,
                        clipboardOn = clipboardOn,
                        clipboardCount = clipboardCount,
                        onClipboardOn = { on ->
                            AppSettings.setClipboardEnabled(context, on)
                            clipboardOn = on
                        },
                        onEraseClipboard = {
                            ClipboardStore.save(context, ClipHistory())
                            clipboardCount = 0
                        },
                        giphyKey = giphyKey,
                        onGiphyKey = { key ->
                            AppSettings.setGiphyKey(context, key)
                            giphyKey = key
                        },
                        onOpenGiphySite = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GIPHY_DEVELOPER_SITE)))
                            } catch (e: Exception) {
                                // No browser installed. Nothing sensible to do.
                            }
                        },
                        onBack = goHome,
                    )

                    else -> HomeScreen(
                        status = status,
                        keyboard = keyboard,
                        keyboardCount = keyboards.size,
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                        onPickKeyboard = { inputMethodManager().showInputMethodPicker() },
                        onOpen = { target ->
                            // The keyboard may have copied things since the app last looked.
                            if (target == SCREEN_EXTRAS) {
                                clipboardOn = AppSettings.clipboardEnabled(context)
                                clipboardCount = ClipboardStore.load(context).entries.size
                            }
                            screen = target
                        },
                    )
                }
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
private fun HomeScreen(
    status: ImeStatus,
    keyboard: KeyboardDef,
    keyboardCount: Int,
    onOpenSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var sample by rememberSaveable { mutableStateOf("") }
    // remember(keyboard.id): switching to another keyboard starts the preview on its first layer.
    var previewState by remember(keyboard.id) { mutableStateOf(KeyboardEngine.initialState(keyboard)) }

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

            if (status.enabled && status.selected) {
                Text(
                    "KeyBuilder is switched on and is your current keyboard.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Two steps to start using the keyboard, then try it in the box below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                StepCard(
                    title = "1. Turn it on",
                    body = "Android keeps every new keyboard switched off until you allow it. " +
                        "It shows the same warning for all keyboards. This app only goes online for GIF search.",
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
            }

            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                label = { Text("Try typing here") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("In use: ${keyboard.name}", style = MaterialTheme.typography.titleMedium)
            KeyboardView(
                def = keyboard,
                state = previewState,
                onKey = { key ->
                    previewState = KeyboardEngine.press(keyboard, previewState, key, SystemClock.uptimeMillis()).state
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onOpen(SCREEN_KEYS) }) { Text("Edit keys") }
                Button(onClick = { onOpen(SCREEN_STYLE) }) { Text("Edit style") }
            }
            OutlinedButton(onClick = { onOpen(SCREEN_KEYBOARDS) }) {
                Text("My keyboards ($keyboardCount)")
            }
            OutlinedButton(onClick = { onOpen(SCREEN_EXTRAS) }) { Text("Clipboard and GIFs") }
            Text(
                "Changes are saved as you make them, and the real keyboard shows them the next time it opens.",
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
