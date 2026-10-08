package app.keybuilder.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.KeyboardState
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutRepository
import app.keybuilder.ui.KeyboardView

/**
 * The keyboard service. Android creates it when a text field needs a keyboard.
 *
 * Its job is small: show KeyboardView, pass taps to KeyboardEngine, and send whatever
 * the engine answers to the text field through the "input connection".
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

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val loaded = LayoutRepository.loadDefault(this)
        keyboard = loaded
        state = KeyboardEngine.initialState(loaded)
    }

    override fun onCreateInputView(): View {
        // Compose looks for these owners on the window's top view.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setContent {
                keyboard?.let { def ->
                    KeyboardView(def = def, state = state, onKey = { handleKey(it) })
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (!restarting) {
            keyboard?.let { state = KeyboardEngine.initialState(it) }
        }
        syncAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    /** In landscape Android would otherwise cover the app with a full-screen typing box. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun handleKey(key: KeyDef) {
        val def = keyboard ?: return
        val before = state
        val result = KeyboardEngine.press(def, before, key, SystemClock.uptimeMillis())
        state = result.state

        val connection = currentInputConnection ?: return
        for (effect in result.effects) {
            when (effect) {
                is Effect.Commit -> connection.commitText(effect.text, 1)
                Effect.DeleteBackward -> deleteBackward(connection)
                Effect.Enter -> sendEnter(connection)
            }
        }
        if (result.effects.isNotEmpty() || result.state.layerId != before.layerId) syncAutoCaps()
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
}
