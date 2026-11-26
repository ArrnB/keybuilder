package app.keybuilder.ime

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import app.keybuilder.model.EditAction
import app.keybuilder.model.GifItem
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyStroke
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutRepository
import app.keybuilder.model.PANELS
import app.keybuilder.model.addRecentEmoji
import app.keybuilder.model.blurInUse
import app.keybuilder.model.scaledForHeight
import app.keybuilder.model.soundFor
import app.keybuilder.store.AppSettings
import app.keybuilder.store.TypingSettings
import app.keybuilder.suggest.Suggestion
import app.keybuilder.ui.ClipboardPanel
import app.keybuilder.ui.EditPanel
import app.keybuilder.ui.EmojiPanel
import app.keybuilder.ui.GifPanel
import app.keybuilder.ui.GifSearchBar
import app.keybuilder.ui.KeyboardView
import app.keybuilder.ui.LocalStripIcons
import app.keybuilder.ui.MainActivity
import app.keybuilder.ui.PanelSize
import app.keybuilder.ui.SettingsPanel
import app.keybuilder.ui.StripExtras
import app.keybuilder.ui.SymbolsPanel
import app.keybuilder.ui.backgroundBrush
import app.keybuilder.ui.resolved
import kotlin.math.roundToInt
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
    private lateinit var sticker: GifController

    /** Word suggestions, autocorrect, text shortcuts and learning. */
    private lateinit var suggest: SuggestController

    private lateinit var sounds: KeySounds

    private var recentSymbols by mutableStateOf<List<String>>(emptyList())

    /** Whether "Select" is switched on in the text editing panel. */
    private var selecting by mutableStateOf(false)

    /** The Compose view that draws the keyboard. Needed to measure it for onComputeInsets. */
    private var composeView: ComposeView? = null

    /** Where a panel's edges are while one is open, or null when the keys are showing. */
    private var panelInsets: PanelInsets? = null

    /** Heights in dp, measured up from the bottom of the keyboard. */
    private data class PanelInsets(val baseDp: Float, val shownDp: Float)

    /** Vibration, pop-ups and the double-space rule. Re-read every time the keyboard appears. */
    private var typing by mutableStateOf(TypingSettings())

    private var recentEmoji by mutableStateOf<List<String>>(emptyList())

    /**
     * How much taller than the keyboard the open panel has been dragged, in dp. It goes back
     * to 0 whenever the panel closes or the keyboard is put away, so a panel always opens
     * at its normal height.
     */
    private var panelExtraDp by mutableStateOf(0f)

    /** The blur currently asked of Android for what is behind the keyboard, in pixels. -1 = not asked yet. */
    private var appliedBlurPx = -1

    /** The saved keyboards (id and name), listed in the settings panel. */
    private var savedKeyboards by mutableStateOf<List<Pair<String, String>>>(emptyList())

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
        sticker = GifController(this, lifecycleScope, stickers = true)
        gif.tidyCache()
        suggest = SuggestController(this)
        sounds = KeySounds(this)
        // The word list is read in the background; suggestions start once it is in.
        suggest.load { suggest.refresh(currentInputConnection) }

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
        appliedBlurPx = -1 // a new window: whatever blur was set is gone
        return ComposeView(this).also { composeView = it }.apply {
            setContent {
                val loaded = keyboard
                if (loaded != null) {
                    // At the height chosen in Settings (letters and icons scale with it), and
                    // with the phone's own colours filled in if the theme asks for them.
                    val sized = remember(loaded, typing.keyboardHeight) { loaded.scaledForHeight(typing.keyboardHeight) }
                    val def = sized.resolved()
                    // Tells the strip, wherever it is drawn, whether to show symbols or words.
                    CompositionLocalProvider(LocalStripIcons provides typing.stripIcons) {
                        KeyboardOrPanel(def)
                    }
                }
            }
        }
    }

    /**
     * Decides what fills the keyboard area: the keys, a panel, or (while search words are
     * typed) a panel with the keys lying over its lower part.
     */
    @Composable
    private fun KeyboardOrPanel(def: KeyboardDef) {
        // Panels are as tall as the keyboard so nothing jumps, but never too short to be useful.
        val height = KeyboardEngine.heightDp(def, state, strip = def.toolbar || typing.suggestions)
            .coerceAtLeast(MIN_PANEL_HEIGHT_DP)
        // A panel can be dragged taller, up to most of the screen.
        val metrics = resources.displayMetrics
        val maxExtra = (metrics.heightPixels / metrics.density * MAX_PANEL_SCREEN_SHARE - height).coerceAtLeast(0f)
        val resize: (Float) -> Unit = { panelExtraDp = it }

        // While search words are typed the panel being searched stays open behind the keys.
        val search = searching()
        val panel = if (search != null) panelOf(search) else state.panel
        val panelOpen = panel != null && panel in PANELS
        // The keys cover a keyboard's height of it, so it has to be taller than that to be seen.
        val extra = if (search != null) KeyboardEngine.searchPanelExtra(panelExtraDp, maxExtra) else panelExtraDp
        val size = PanelSize(baseDp = height, extraDp = extra.coerceIn(0f, maxExtra), maxExtraDp = maxExtra)

        SideEffect {
            // Remember where the panel's edges are, for onComputeInsets below.
            val wanted = if (panelOpen) PanelInsets(size.baseDp, size.baseDp + size.extraDp) else null
            if (wanted != panelInsets) {
                panelInsets = wanted
                composeView?.requestLayout()
            }
            // The frosted look is for the keys only. A panel is solid, and its window is taller
            // than the panel, so blurring there would smear the app above the panel too.
            applyBlur(if (panelOpen) 0f else def.theme.blurInUse())
        }

        if (panelOpen) {
            // The window is made as tall as the panel can ever get, once, when the panel opens.
            // Dragging then only changes how much of that space the panel fills, which is just
            // a redraw. Resizing the window itself on every finger movement is what made it jerky.
            Box(
                modifier = Modifier.fillMaxWidth().height((size.baseDp + size.maxExtraDp).dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Panel(def, panel, size, resize)
                if (search != null) {
                    // The keys, on a solid background so the panel does not show through them.
                    // The empty pointerInput stops a tap between two keys from reaching a GIF underneath.
                    val solid: Brush = backgroundBrush(def.theme, opaque = true)
                    Column(modifier = Modifier.fillMaxWidth().background(solid).pointerInput(Unit) {}) {
                        GifSearchBar(
                            theme = def.theme,
                            query = search.query,
                            onCancel = { showPanel(panelOf(search)) },
                            onSearch = { runSearch() },
                        )
                        Keys(def, showToolbar = false)
                    }
                }
            }
        } else {
            Keys(def, showToolbar = true)
        }
    }

    /**
     * Asks Android to blur whatever is behind the keyboard's window (Android 12 and later).
     * Phones that do not allow blur between windows simply ignore this, and the keyboard is
     * see-through without the blur.
     */
    private fun applyBlur(radiusDp: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val pixels = (radiusDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(0)
        if (pixels == appliedBlurPx) return
        try {
            window?.window?.setBackgroundBlurRadius(pixels)
            appliedBlurPx = pixels
        } catch (e: Exception) {
            // Not possible on this phone. The see-through background still works.
        }
    }

    @Composable
    private fun Keys(def: KeyboardDef, showToolbar: Boolean) {
        KeyboardView(
            def = def,
            state = state,
            onKey = { handleKey(it) },
            showToolbar = showToolbar,
            onOpenPanel = { showPanel(it) },
            onAlternate = { _, text -> handleAlternate(text) },
            onKeyDown = { keyTouched(it) },
            showPopups = typing.popups,
            onCursor = { moveCursor(it) },
            strip = StripExtras(
                suggestions = if (suggest.hidden) emptyList() else suggest.suggestions,
                onSuggestion = { pickSuggestion(it) },
                onShowTools = { suggest.showTools() },
                clip = if (typing.quickClip) clipboard.latest?.label else null,
                onClip = { pasteLatest() },
                onClipHeld = { clipboard.dismissLatest() },
                // With suggestions on, the strip shows even on a keyboard without shortcut buttons.
                always = typing.suggestions,
            ),
        )
    }

    @Composable
    private fun Panel(def: KeyboardDef, panel: String, size: PanelSize, resize: (Float) -> Unit) {
        when (panel) {
            "clipboard" -> ClipboardPanel(
                theme = def.theme,
                size = size,
                onResize = resize,
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

            "gif" -> PicturePanel(def, gif, size, resize, title = "GIFs by GIPHY", searchHint = "Search GIFs")

            "sticker" -> PicturePanel(def, sticker, size, resize, title = "Stickers by GIPHY", searchHint = "Search stickers")

            "symbols" -> SymbolsPanel(
                theme = def.theme,
                size = size,
                onResize = resize,
                onClose = { showPanel(null) },
                recent = recentSymbols,
                onBackspace = { currentInputConnection?.let { deleteBackward(it) } },
                onPick = { pickSymbol(it) },
            )

            "edit" -> EditPanel(
                theme = def.theme,
                size = size,
                onResize = resize,
                selecting = selecting,
                onClose = { showPanel(null) },
                onAction = { runEdit(it) },
            )

            "emoji" -> EmojiPanel(
                theme = def.theme,
                size = size,
                onResize = resize,
                onClose = { showPanel(null) },
                recent = recentEmoji,
                onBackspace = { currentInputConnection?.let { deleteBackward(it) } },
                onPick = { pickEmoji(it) },
            )

            else -> SettingsPanel(
                theme = def.theme,
                size = size,
                onResize = resize,
                keyboards = savedKeyboards,
                activeId = def.id,
                settings = typing,
                clipboardOn = clipboard.enabled,
                onClose = { showPanel(null) },
                onUseKeyboard = { useKeyboard(it) },
                onSettings = { changed ->
                    // Let a new vibration strength be felt straight away.
                    if (changed.vibrate && changed.vibrateStrength != typing.vibrateStrength) {
                        Haptics.buzz(this, changed.vibrateStrength)
                    }
                    // Switching sounds on plays one, so you know they work (or that the phone is on silent).
                    if (changed.sounds && !typing.sounds) {
                        sounds.play(def.theme.sound, changed.soundVolume, changed.soundsWhenSilent)
                    }
                    typing = changed
                    AppSettings.setTyping(this, changed)
                    suggest.settingsChanged(changed, currentInputConnection)
                },
                onClipboardOn = { on -> clipboard.setHistoryOn(on) },
                onOpenApp = { openApp() },
                onOtherKeyboards = {
                    getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                },
            )
        }
    }

    /** The GIF panel and the sticker panel are the same panel fed by two different controllers. */
    @Composable
    private fun PicturePanel(
        def: KeyboardDef,
        source: GifController,
        size: PanelSize,
        resize: (Float) -> Unit,
        title: String,
        searchHint: String,
    ) {
        GifPanel(
            theme = def.theme,
            size = size,
            onResize = resize,
            title = title,
            searchHint = searchHint,
            gifs = source.gifs,
            loading = source.loading,
            message = source.message,
            query = source.query,
            hasKey = source.hasKey,
            imageLoader = imageLoader,
            canLoadMore = source.canLoadMore,
            loadingMore = source.loadingMore,
            shelf = source.shelf,
            favourites = source.saved.favourites.map { it.id }.toSet(),
            onShelf = {
                source.typing = false
                source.showShelf(it)
            },
            onFavourite = {
                vibrateOnly()
                source.toggleFavourite(it)
            },
            onMore = { source.loadMore() },
            onClose = { showPanel(null) },
            onSearchBox = {
                // Bring the keys back, but send what they type to the search words.
                state = state.copy(panel = null)
                source.typing = true
                suggest.clear()
            },
            onQuickSearch = {
                source.typing = false // a one-tap search ends typing: the keys step aside
                source.search(it)
            },
            onPick = { sendGif(it, source) },
        )
    }

    /** The controller whose search words are being typed right now, if any. */
    private fun searching(): GifController? = when {
        gif.typing -> gif
        sticker.typing -> sticker
        else -> null
    }

    private fun panelOf(source: GifController): String = if (source === sticker) "sticker" else "gif"

    /**
     * Tells Android where the keyboard really is while a panel is open.
     *
     * With a panel open the window is taller than what is drawn (see KeyboardOrPanel), so
     * without this the app would be squeezed above an invisible area and could not be touched
     * there. This says: lay the app out as if only the normal keyboard were there, and send
     * touches above the panel's top edge to the app.
     */
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val insets = panelInsets ?: return
        val view = composeView ?: return
        val decor = window?.window?.decorView ?: return
        if (view.height == 0 || !view.isShown) return

        val density = resources.displayMetrics.density
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val (contentTop, shownTop) = KeyboardEngine.panelInsetTops(
            viewTop = location[1],
            viewHeight = view.height,
            basePx = insets.baseDp * density,
            shownPx = insets.shownDp * density,
        )
        outInsets.contentTopInsets = contentTop
        outInsets.visibleTopInsets = shownTop
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, shownTop, decor.width, decor.height)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        typing = AppSettings.typing(this)
        recentEmoji = AppSettings.recentEmoji(this)
        recentSymbols = AppSettings.recentSymbols(this)
        if (typing.sounds) sounds.prepare(typing.soundsWhenSilent)
        val reloaded = reloadIfChanged()
        val def = keyboard
        if (def != null && (!restarting || reloaded)) {
            // A new text box: start on the first layer, or on the number layer where only numbers fit.
            state = KeyboardEngine.stateForField(def, KeyboardEngine.initialState(def), isNumberBox(info))
        }
        if (!restarting) {
            gif.typing = false
            sticker.typing = false
        }
        clipboard.refresh()
        clipboard.peek()
        clipboard.importScreenshots() // does nothing unless that setting is on
        syncAutoCaps()
        // Decides what this text box allows. In a password box: nothing, and nothing is read from it.
        suggest.startField(info, typing, AppSettings.shortcuts(this), currentInputConnection)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // The keyboard is going down: next time it comes up with the keys showing and panels at normal height.
        gif.typing = false
        sticker.typing = false
        if (state.panel != null) state = state.copy(panel = null)
        panelExtraDp = 0f
        suggest.clear()
        suggest.save()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    /** Android calls this whenever the cursor moves or text is selected, whoever caused it. */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (searching() == null && state.panel == null) {
            suggest.selectionChanged(newSelStart, newSelEnd, currentInputConnection)
        }
    }

    override fun onDestroy() {
        suggest.save()
        sounds.release()
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
        val search = searching()
        if (search != null) {
            val (words, submitted) = KeyboardEngine.typeIntoQuery(search.query, result.effects)
            search.query = words
            if (submitted) runSearch()
            return
        }

        val connection = currentInputConnection ?: return
        for (effect in result.effects) {
            when (effect) {
                is Effect.Commit -> commit(connection, effect.text)
                Effect.DeleteBackward -> deleteBackward(connection)
                Effect.Enter -> {
                    suggest.beforeTyping(connection, "\n") // the last word is learned, never corrected
                    sendEnter(connection)
                }
                is Effect.MoveCursor -> moveCursor(effect.direction)
                is Effect.Command -> runCommand(connection, effect.command)
            }
        }
        if (result.effects.isNotEmpty() || result.state.layerId != before.layerId) syncAutoCaps()
        if (result.effects.any { it is Effect.Command }) {
            suggest.textChangedOtherwise(connection) // a paste or a cut
        } else if (result.effects.isNotEmpty()) {
            suggest.refresh(connection)
        }
    }

    private fun commit(connection: InputConnection, text: String) {
        // If this ends a word (a space, a comma...), the word is corrected and learned first.
        suggest.beforeTyping(connection, text)
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

    /**
     * Called the moment something is touched: buzz, and play a sound. [key] is the key that
     * was touched, which may have a sound of its own; null for buttons that are not keys.
     */
    private fun keyTouched(key: KeyDef? = null) {
        vibrateOnly()
        if (typing.sounds) {
            val theme = keyboard?.theme ?: return
            sounds.play(if (key != null) theme.soundFor(key) else theme.sound, typing.soundVolume, typing.soundsWhenSilent)
        }
    }

    private fun vibrateOnly() {
        if (typing.vibrate) Haptics.buzz(this, typing.vibrateStrength)
    }

    /** A symbol or a face was picked in the symbols panel. */
    private fun pickSymbol(symbol: String) {
        currentInputConnection?.commitText(symbol, 1)
        recentSymbols = addRecentEmoji(recentSymbols, symbol)
        AppSettings.setRecentSymbols(this, recentSymbols)
    }

    /** A button of the text editing panel was pressed. */
    private fun runEdit(action: EditAction) {
        val connection = currentInputConnection ?: return
        vibrateOnly()
        val stroke = action.keyStroke(selecting)
        val command = action.command()
        if (stroke != null) sendStroke(connection, stroke) else if (command != null) runCommand(connection, command)
        selecting = action.selectingAfter(selecting)
    }

    /**
     * Sends a key press as if from a real keyboard, with Ctrl and Shift held as asked.
     * That is how the edit panel jumps by word (Ctrl+arrow), selects (Shift+arrow) and undoes (Ctrl+Z).
     */
    private fun sendStroke(connection: InputConnection, stroke: KeyStroke) {
        val code = when (stroke.key) {
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "home" -> KeyEvent.KEYCODE_MOVE_HOME
            "end" -> KeyEvent.KEYCODE_MOVE_END
            "delete" -> KeyEvent.KEYCODE_DEL
            "forwardDelete" -> KeyEvent.KEYCODE_FORWARD_DEL
            "z" -> KeyEvent.KEYCODE_Z
            else -> return
        }
        var held = 0
        if (stroke.ctrl) held = held or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (stroke.shift) held = held or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val now = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        val device = KeyCharacterMap.VIRTUAL_KEYBOARD
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, held, device, 0, flags))
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, held, device, 0, flags))
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

    /** A suggestion on the strip was tapped. */
    private fun pickSuggestion(suggestion: Suggestion) {
        val connection = currentInputConnection ?: return
        keyTouched()
        suggest.pick(connection, suggestion)
        syncAutoCaps()
    }

    /** The "last copied thing" button on the strip was tapped: paste it. */
    private fun pasteLatest() {
        val clip = clipboard.latest ?: return
        keyTouched()
        val text = clip.text
        val entry = clip.entry
        if (text != null) {
            currentInputConnection?.commitText(text, 1)
        } else if (entry != null) {
            val file = clipboard.imageFile(entry)
            val sent = file != null && file.exists() && sendPicture(file, entry.mime ?: "image/png", "Copied picture")
            if (!sent) {
                // Say why nothing happened, in the place that has room to say it.
                showPanel("clipboard")
                clipboardMessage = "This text box does not accept pictures."
                return
            }
        }
        suggest.textChangedOtherwise(currentInputConnection)
        syncAutoCaps()
    }

    private fun deleteBackward(connection: InputConnection) {
        // Backspace right after an automatic correction takes the correction back instead of deleting.
        if (suggest.undoCorrection(connection)) return
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
        sticker.typing = false
        state = state.copy(panel = panel)
        selecting = false
        if (panel == null) {
            panelExtraDp = 0f // back to the keys: the next panel opens at normal height
            suggest.textChangedOtherwise(currentInputConnection)
        } else {
            suggest.clear()
        }
        when (panel) {
            "clipboard" -> {
                clipboardMessage = null
                clipboard.refresh()
                clipboard.importScreenshots()
            }
            "gif" -> gif.open()
            "sticker" -> sticker.open()
            "settings" -> {
                clipboard.refresh()
                savedKeyboards = LayoutRepository.list(this).map { it.id to it.name }
            }
        }
    }

    /** Switches to another saved keyboard and shows it straight away. */
    private fun useKeyboard(id: String) {
        LayoutRepository.setActive(this, id)
        if (!reloadIfChanged()) showPanel(null)
    }

    private fun openApp() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Some lock-screen situations do not allow opening an app. Nothing sensible to do.
        }
    }

    /** Enter was pressed (or Search tapped) while typing search words: go back to the panel and search. */
    private fun runSearch() {
        val search = searching() ?: return
        search.typing = false
        state = state.copy(panel = panelOf(search))
        search.search(search.query)
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

    /** Sends a GIF or a sticker (both are GIF pictures) picked in [source]'s panel. */
    private fun sendGif(item: GifItem, source: GifController) {
        if (!fieldAccepts(GIF_TYPE)) {
            // Plenty of text boxes take text only. A link is the next best thing.
            currentInputConnection?.commitText(item.pageUrl + " ", 1)
            source.noteSent(item)
            source.message = "This text box does not accept pictures, so a link was typed instead."
            return
        }
        source.message = "Sending"
        lifecycleScope.launch {
            val file = source.download(item)
            source.message = when {
                file == null -> "Could not download it. Check the internet and try again."
                sendPicture(file, GIF_TYPE, item.title) -> {
                    source.noteSent(item)
                    null
                }
                else -> "The app did not take the picture."
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

        /** How much of the screen's height a panel may take when dragged up. */
        const val MAX_PANEL_SCREEN_SHARE = 0.78f
    }
}
