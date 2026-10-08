package app.keybuilder.ime

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.KeyboardState
import app.keybuilder.engine.PressResult
import app.keybuilder.model.ClipEntry
import app.keybuilder.model.GifItem
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutRepository
import app.keybuilder.model.addRecentEmoji
import app.keybuilder.store.AppSettings
import app.keybuilder.store.TypingSettings
import app.keybuilder.ui.ClipboardPanel
import app.keybuilder.ui.EmojiPanel
import app.keybuilder.ui.GifPanel
import app.keybuilder.ui.GifSearchBar
import app.keybuilder.ui.KeyboardView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import java.io.File
import kotlinx.coroutines.launch

/**
 * The keyboard service. Android creates it when a text field needs a keyboard.
 *
 * It shows the keys (KeyboardView) or one of the panels, passes taps to KeyboardEngine,
 * and sends whatever comes out to the text field through the "input connection".
 *
 * The LifecycleOwner / SavedStateRegistryOwner parts are plumbing: Compose refuses to
 * run in a window that has no lifecycle, and a keyboard service does not come with one.
 */
class KeyBuilderIme : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    // Compose redraws the keyboard automatically whenever either of these changes.
    private var keyboard by mutableStateOf<KeyboardDef?>(null)
    private var state by mutableStateOf(KeyboardState(layerId = ""))

    /** Which saved version of the layout is on screen, see LayoutRepository.activeVersion. */
    private var loadedVersion: String? = null

    private lateinit var clipboard: ClipboardController
    private lateinit var gif: GifController

    /** Vibration, pop-ups and the double-space rule. Re-read every time the keyboard appears. */
    private var typing by mutableStateOf(TypingSettings())

    private var recentEmoji by mutableStateOf<List<String>>(emptyList())

    /** A line shown in the clipboard panel, for example when a picture cannot be pasted here. */
    private var clipboardMessage by mutableStateOf<String?>(null)

    /** Loads pictures for the panels and plays GIFs. Made the first time a panel needs it. */
    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        clipboard = ClipboardController(this)
        clipboard.start()
        gif = GifController(this, lifecycleScope)
        gif.tidyCache()

        reloadIfChanged()
    }

    /**
     * Loads the keyboard the first time, and again whenever the app has saved a new version
     * or switched to another keyboard. Returns true if it reloaded.
     */
    private fun reloadIfChanged(): Boolean {
        val version = LayoutRepository.activeVersion(this)
        if (keyboard != null && version == loadedVersion) return false
        val loaded = LayoutRepository.loadActive(this)
        loadedVersion = version
        keyboard = loaded
        state = KeyboardEngine.initialState(loaded)
        return true
    }

    override fun onCreateInputView(): View {
        // Compose looks for these owners on the window's top view.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setContent {
                val def = keyboard
                if (def != null) KeyboardOrPanel(def)
            }
        }
    }

    /** Decides what fills the keyboard area: the keys, a panel, or the keys with the GIF search bar. */
    @Composable
    private fun KeyboardOrPanel(def: KeyboardDef) {
        // Panels are as tall as the keyboard so nothing jumps, but never too short to be useful.
        val height = KeyboardEngine.heightDp(def, state).coerceAtLeast(MIN_PANEL_HEIGHT_DP)
        val panel = state.panel
        when {
            gif.typing -> Column {
                GifSearchBar(
                    theme = def.theme,
                    query = gif.query,
                    onCancel = { showPanel("gif") },
                    onSearch = { runGifSearch() },
                )
                KeyboardView(
                    def = def,
                    state = state,
                    onKey = { handleKey(it) },
                    showToolbar = false,
                    onAlternate = { _, text -> handleAlternate(text) },
                    onKeyDown = { keyTouched() },
                    showPopups = typing.popups,
                )
            }

            panel == "clipboard" -> ClipboardPanel(
                theme = def.theme,
                heightDp = height,
                history = clipboard.history,
                enabled = clipboard.enabled,
                message = clipboardMessage,
                imageLoader = imageLoader,
                imageFileOf = { clipboard.imageFile(it) },
                onClose = { showPanel(null) },
                onTurnOn = { clipboard.turnOn() },
                onPaste = { pasteClip(it) },
                onPin = { clipboard.togglePin(it) },
                onRemove = { clipboard.remove(it) },
                onClear = { clipboard.clearUnpinned() },
            )

            panel == "gif" -> GifPanel(
                theme = def.theme,
                heightDp = height,
                gifs = gif.gifs,
                loading = gif.loading,
                message = gif.message,
                query = gif.query,
                hasKey = gif.hasKey,
                imageLoader = imageLoader,
                canLoadMore = gif.canLoadMore,
                loadingMore = gif.loadingMore,
                onMore = { gif.loadMore() },
                onClose = { showPanel(null) },
                onSearchBox = {
                    // Bring the keys back, but send what they type to the search words.
                    state = state.copy(panel = null)
                    gif.typing = true
                },
                onQuickSearch = { gif.search(it) },
                onPick = { sendGif(it) },
            )

            panel == "emoji" -> EmojiPanel(
                theme = def.theme,
                heightDp = height,
                onClose = { showPanel(null) },
                recent = recentEmoji,
                onBackspace = { currentInputConnection?.let { deleteBackward(it) } },
                onPick = { pickEmoji(it) },
            )

            else -> KeyboardView(
                def = def,
                state = state,
                onKey = { handleKey(it) },
                onOpenPanel = { showPanel(it) },
                onAlternate = { _, text -> handleAlternate(text) },
                onKeyDown = { keyTouched() },
                showPopups = typing.popups,
            )
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        typing = AppSettings.typing(this)
        recentEmoji = AppSettings.recentEmoji(this)
        val reloaded = reloadIfChanged()
        val def = keyboard
        if (def != null && (!restarting || reloaded)) {
            // A new text box: start on the first layer, or on the number layer where only numbers fit.
            state = KeyboardEngine.stateForField(def, KeyboardEngine.initialState(def), isNumberBox(info))
        }
        if (!restarting) gif.typing = false
        clipboard.refresh()
        syncAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        clipboard.stop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    /** In landscape Android would otherwise cover the app with a full-screen typing box. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------------ keys

    private fun handleKey(key: KeyDef) {
        val def = keyboard ?: return
        val before = state
        carryOut(before, KeyboardEngine.press(def, before, key, SystemClock.uptimeMillis()))
    }

    /** A long-press choice was picked. */
    private fun handleAlternate(text: String) {
        val before = state
        carryOut(before, KeyboardEngine.pressAlternate(before, text))
    }

    /** Takes what the engine decided and makes it happen. */
    private fun carryOut(before: KeyboardState, result: PressResult) {
        state = result.state

        // A key that opens a panel.
        val opened = result.state.panel
        if (opened != null && opened != before.panel) {
            showPanel(opened)
            return
        }

        // While search words are being typed, the keys fill in the words, not the app's text.
        if (gif.typing) {
            val (words, submitted) = KeyboardEngine.typeIntoQuery(gif.query, result.effects)
            gif.query = words
            if (submitted) runGifSearch()
            return
        }

        val connection = currentInputConnection ?: return
        for (effect in result.effects) {
            when (effect) {
                is Effect.Commit -> commit(connection, effect.text)
                Effect.DeleteBackward -> deleteBackward(connection)
                Effect.Enter -> sendEnter(connection)
                is Effect.MoveCursor -> moveCursor(effect.direction)
                is Effect.Command -> runCommand(connection, effect.command)
            }
        }
        if (result.effects.isNotEmpty() || result.state.layerId != before.layerId) syncAutoCaps()
    }

    private fun commit(connection: InputConnection, text: String) {
        if (text == " " && typing.doubleSpace) {
            // "word" + space + space becomes "word. "
            val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (KeyboardEngine.doubleSpaceMakesFullStop(before)) {
                connection.deleteSurroundingText(1, 0)
                connection.commitText(". ", 1)
                return
            }
        }
        connection.commitText(text, 1)
    }

    /** Arrow keys are sent as real key presses, which every text box understands. */
    private fun moveCursor(direction: String) {
        val code = when (direction) {
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
        sendDownUpKeyEvents(code)
    }

    /** Copy, paste, cut and select all are the same commands as in the pop-up menu on selected text. */
    private fun runCommand(connection: InputConnection, command: String) {
        val id = when (command) {
            "copy" -> android.R.id.copy
            "paste" -> android.R.id.paste
            "cut" -> android.R.id.cut
            else -> android.R.id.selectAll
        }
        connection.performContextMenuAction(id)
    }

    /** Called the moment a key is touched. */
    private fun keyTouched() {
        if (typing.vibrate) {
            window?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Boxes for phone numbers, amounts, PINs and dates. */
    private fun isNumberBox(info: EditorInfo?): Boolean =
        when ((info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }

    private fun pickEmoji(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
        recentEmoji = addRecentEmoji(recentEmoji, emoji)
        AppSettings.setRecentEmoji(this, recentEmoji)
    }

    private fun deleteBackward(connection: InputConnection) {
        val selection = connection.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            connection.commitText("", 1) // replace the selection with nothing
        } else {
            // A real "delete" key press: apps handle emoji and other multi-part characters correctly.
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    /** Enter means "search", "send", "next"... in some fields and a new line in others. */
    private fun sendEnter(connection: InputConnection) {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val fieldAction = options and EditorInfo.IME_MASK_ACTION
        val wantsNewLine = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val hasAction = fieldAction != EditorInfo.IME_ACTION_NONE && fieldAction != EditorInfo.IME_ACTION_UNSPECIFIED
        if (hasAction && !wantsNewLine) {
            connection.performEditorAction(fieldAction)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** Turns shift on by itself at the start of a sentence, if the text field asks for that. */
    private fun syncAutoCaps() {
        val info = currentInputEditorInfo ?: return
        val connection = currentInputConnection ?: return
        val capitalizeNext = info.inputType != InputType.TYPE_NULL &&
            connection.getCursorCapsMode(info.inputType) != 0
        state = KeyboardEngine.withAutoCaps(state, capitalizeNext)
    }

    // ------------------------------------------------------------------ panels

    /** Shows a panel, or the keys again when [panel] is null. */
    private fun showPanel(panel: String?) {
        gif.typing = false
        state = state.copy(panel = panel)
        when (panel) {
            "clipboard" -> {
                clipboardMessage = null
                clipboard.refresh()
            }
            "gif" -> gif.open()
        }
    }

    private fun runGifSearch() {
        gif.typing = false
        state = state.copy(panel = "gif")
        gif.search(gif.query)
    }

    private fun pasteClip(entry: ClipEntry) {
        val text = entry.text
        val file = clipboard.imageFile(entry)
        if (text != null) {
            currentInputConnection?.commitText(text, 1)
        } else if (file != null) {
            val sent = file.exists() && sendPicture(file, entry.mime ?: "image/png", "Copied picture")
            clipboardMessage = if (sent) null else "This text box does not accept pictures."
        }
    }

    private fun sendGif(item: GifItem) {
        if (!fieldAccepts(GIF_TYPE)) {
            // Plenty of text boxes take text only. A link is the next best thing.
            currentInputConnection?.commitText(item.pageUrl + " ", 1)
            gif.message = "This text box does not accept GIFs, so a link was typed instead."
            return
        }
        gif.message = "Sending"
        lifecycleScope.launch {
            val file = gif.download(item)
            gif.message = when {
                file == null -> "Could not download that GIF. Check the internet and try again."
                sendPicture(file, GIF_TYPE, item.title) -> null
                else -> "The app did not take the GIF."
            }
        }
    }

    /** True if the text box being typed in says it accepts pictures of this type. */
    private fun fieldAccepts(mime: String): Boolean {
        val info = currentInputEditorInfo ?: return false
        return EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mime, it) }
    }

    /**
     * Hands a picture file to the app being typed in. The app gets permission to read this
     * one file and nothing else. Returns false if the text box does not take such pictures.
     */
    private fun sendPicture(file: File, mime: String, label: String): Boolean {
        val info = currentInputEditorInfo ?: return false
        val connection = currentInputConnection ?: return false
        if (!fieldAccepts(mime)) return false
        return try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val content = InputContentInfoCompat(uri, ClipDescription(label, arrayOf(mime)), null)
            InputConnectionCompat.commitContent(
                connection, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null,
            )
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val GIF_TYPE = "image/gif"
        const val MIN_PANEL_HEIGHT_DP = 240f
    }
}
