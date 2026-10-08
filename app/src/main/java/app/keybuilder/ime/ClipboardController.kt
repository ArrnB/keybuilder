package app.keybuilder.ime

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keybuilder.model.ClipEntry
import app.keybuilder.model.ClipHistory
import app.keybuilder.store.AppSettings
import app.keybuilder.store.ClipboardStore
import java.io.File
import kotlin.concurrent.thread

/**
 * Watches the phone's clipboard and keeps the history shown in the clipboard panel.
 *
 * Android only lets the keyboard that is currently selected (and the app on screen) read the
 * clipboard, so this works while KeyBuilder is your keyboard. Nothing leaves the phone.
 * The rules for the history itself (order, pinning, the 30-item limit) are in ClipHistory.
 */
class ClipboardController(private val context: Context) {

    var history by mutableStateOf(ClipHistory())
        private set

    var enabled by mutableStateOf(false)
        private set

    private val manager: ClipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val mainThread = Handler(Looper.getMainLooper())
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    fun start() {
        refresh()
        manager.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        manager.removePrimaryClipChangedListener(listener)
    }

    /** Re-reads the setting and the saved list, in case the app changed them. */
    fun refresh() {
        enabled = AppSettings.clipboardEnabled(context)
        history = ClipboardStore.load(context)
    }

    fun turnOn() {
        AppSettings.setClipboardEnabled(context, true)
        capture() // start with whatever is on the clipboard right now
    }

    fun togglePin(id: Long) = update(history.togglePin(id))

    fun remove(id: Long) = update(history.remove(id))

    fun clearUnpinned() = update(history.clearUnpinned())

    fun imageFile(entry: ClipEntry): File? = entry.imageFile?.let { File(ClipboardStore.folder(context), it) }

    private fun update(changed: ClipHistory) {
        if (changed == history) return
        history = changed
        ClipboardStore.save(context, changed)
    }

    private fun capture() {
        // The app may have switched the history off or erased it since we last looked.
        refresh()
        if (!enabled) return
        try {
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val description = clip.description
            // Password managers mark what they copy as sensitive. Never keep those.
            if (description.extras?.getBoolean(SENSITIVE_FLAG, false) == true) return

            val item = clip.getItemAt(0)
            val uri = item.uri
            val imageType = (0 until description.mimeTypeCount)
                .map { description.getMimeType(it) }
                .firstOrNull { it.startsWith("image/") }

            if (uri != null && imageType != null) {
                captureImage(uri, imageType)
            } else {
                val text = item.coerceToText(context)?.toString() ?: return
                update(history.addText(text, System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the clipboard", e)
        }
    }

    /** Copies the picture into our own folder, because the original may disappear at any time. */
    private fun captureImage(uri: Uri, mime: String) {
        val folder = ClipboardStore.folder(context)
        val id = System.currentTimeMillis()
        val name = "clip_$id.${extensionFor(mime)}"
        thread(name = "clipboard-image") {
            val part = File(folder, name + ClipboardStore.PART_SUFFIX)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@thread
                input.use { source -> part.outputStream().use { target -> source.copyTo(target) } }
                val size = part.length()
                if (size == 0L || size > MAX_IMAGE_BYTES) {
                    part.delete()
                    return@thread
                }
                mainThread.post {
                    // Some phones report one copy twice. The same size right after means the same picture.
                    val newest = history.entries.firstOrNull()?.let { imageFile(it) }
                    val repeat = newest != null && newest.length() == size
                    if (!repeat && part.renameTo(File(folder, name))) {
                        update(history.addImage(name, mime, id))
                    } else {
                        part.delete()
                    }
                }
            } catch (e: Exception) {
                part.delete()
                Log.w(TAG, "Could not copy the picture from the clipboard", e)
            }
        }
    }

    private fun extensionFor(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "img"
    }

    private companion object {
        const val TAG = "ClipboardController"
        const val SENSITIVE_FLAG = "android.content.extra.IS_SENSITIVE"
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }
}
