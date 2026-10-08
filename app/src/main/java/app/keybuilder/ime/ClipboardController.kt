package app.keybuilder.ime

import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.keybuilder.model.ClipEntry
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.SCREENSHOT_FILE_PREFIX
import app.keybuilder.model.isScreenshot
import app.keybuilder.model.quickClipLabel
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
 *
 * Screenshots are a separate matter: Android does not put them on the clipboard. If you
 * switch "Add screenshots to the clipboard" on (and give the permission it needs), new
 * screenshots are copied from the phone's gallery into the history, see importScreenshots.
 */
class ClipboardController(private val context: Context) {

    var history by mutableStateOf(ClipHistory())
        private set

    var enabled by mutableStateOf(false)
        private set

    /**
     * The most recently copied thing, for the button next to the gear, or null if there is
     * nothing to show. This is read straight from the phone's clipboard each time; it is not
     * stored by the keyboard, and it works with the clipboard history switched off.
     */
    var latest by mutableStateOf<QuickClip?>(null)
        private set

    /** What the button shows ([label]) and what tapping it pastes: [text], or the picture in [entry]. */
    class QuickClip(val label: String, val text: String?, val entry: ClipEntry?)

    /** What the user hid by holding the button. It stays hidden until something else is copied. */
    private var dismissed: String? = null

    private val manager: ClipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val mainThread = Handler(Looper.getMainLooper())
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    /** Told by Android whenever a picture is added to the phone's gallery. */
    private val galleryObserver = object : ContentObserver(mainThread) {
        override fun onChange(selfChange: Boolean) {
            // One screenshot causes several of these in a row, and its file needs a moment to be finished.
            mainThread.removeCallbacks(importSoon)
            mainThread.postDelayed(importSoon, 900)
        }
    }
    private val importSoon = Runnable { importScreenshots() }
    private var importing = false

    fun start() {
        refresh()
        manager.addPrimaryClipChangedListener(listener)
        try {
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, galleryObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch for new screenshots", e)
        }
    }

    fun stop() {
        manager.removePrimaryClipChangedListener(listener)
        mainThread.removeCallbacks(importSoon)
        try {
            context.contentResolver.unregisterContentObserver(galleryObserver)
        } catch (e: Exception) {
            // It was never registered. Nothing to undo.
        }
    }

    /** Looks at the phone's clipboard and updates [latest]. Nothing is saved. */
    fun peek() {
        latest = try {
            readLatest()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the clipboard", e)
            null
        }
    }

    /** Hides the button until something new is copied. */
    fun dismissLatest() {
        dismissed = latest?.let { it.text ?: it.entry?.imageFile }
        latest = null
    }

    private fun readLatest(): QuickClip? {
        val clip = manager.primaryClip?.takeIf { it.itemCount > 0 }
        val description = clip?.description
        val copiedAt = description?.timestamp ?: 0L

        // A picture we saved since the last copy: a copied picture, or a screenshot taken after it.
        // (Entry ids are the times the pictures were saved or taken.)
        val newest = history.entries.firstOrNull()
        if (newest != null && newest.isImage && newest.id >= copiedAt - 2000 && newest.imageFile != dismissed) {
            val screenshot = newest.imageFile.orEmpty().startsWith(SCREENSHOT_FILE_PREFIX)
            return QuickClip(if (screenshot) "Screenshot" else "Picture", text = null, entry = newest)
        }

        if (clip == null || description == null) return null
        val item = clip.getItemAt(0)
        val isImage = item.uri != null &&
            (0 until description.mimeTypeCount).any { description.getMimeType(it).startsWith("image/") }
        // A copied picture can only be pasted from our own saved copy, which the lines above look for.
        if (isImage) return null
        val text = item.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank() || text.length > ClipHistory.MAX_TEXT_LENGTH || text == dismissed) return null
        // What a password manager copies can be pasted with one tap, but is not shown on screen.
        val secret = description.extras?.getBoolean(SENSITIVE_FLAG, false) == true
        return QuickClip(if (secret) "•••••" else quickClipLabel(text), text = text, entry = null)
    }

    // ------------------------------------------------------------------ screenshots

    /**
     * Copies screenshots taken since the last look into the history, newest on top.
     * Does nothing unless the history and the screenshot setting are both on and Android has
     * given permission to see the phone's pictures. Called when the keyboard opens, when the
     * clipboard panel opens, and when the gallery reports a new picture.
     */
    fun importScreenshots() {
        if (importing) return
        if (!AppSettings.clipboardEnabled(context) || !AppSettings.screenshotsEnabled(context)) return
        if (!canSeePictures(context)) return
        importing = true
        val since = AppSettings.lastScreenshot(context)
        val folder = ClipboardStore.folder(context)
        thread(name = "screenshots") {
            class Saved(val name: String, val mime: String, val id: Long)

            val saved = ArrayList<Saved>()
            var newest = since
            try {
                for (shot in findScreenshots(since)) {
                    newest = maxOf(newest, shot.addedSeconds)
                    val name = "$SCREENSHOT_FILE_PREFIX${shot.addedSeconds}_${shot.id}.${extensionFor(shot.mime)}"
                    val target = File(folder, name)
                    if (target.exists()) continue
                    val part = File(folder, name + ClipboardStore.PART_SUFFIX)
                    val input = context.contentResolver.openInputStream(shot.uri) ?: continue
                    input.use { source -> part.outputStream().use { out -> source.copyTo(out) } }
                    val size = part.length()
                    if (size == 0L || size > MAX_IMAGE_BYTES || !part.renameTo(target)) {
                        part.delete()
                        continue
                    }
                    // Milliseconds like every other entry id, and different for every screenshot.
                    saved.add(Saved(name, shot.mime, shot.addedSeconds * 1000 + shot.id % 1000))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the new screenshots", e)
            }
            mainThread.post {
                importing = false
                if (newest > since) AppSettings.setLastScreenshot(context, newest)
                if (saved.isNotEmpty()) {
                    refresh()
                    var changed = history
                    for (shot in saved) {
                        if (!changed.hasImage(shot.name)) changed = changed.addImage(shot.name, shot.mime, shot.id)
                    }
                    update(changed)
                    peek()
                }
            }
        }
    }

    private class Shot(val uri: Uri, val id: Long, val addedSeconds: Long, val mime: String)

    /** The screenshots added to the gallery after [sinceSeconds], oldest first, at most a handful. */
    private fun findScreenshots(sinceSeconds: Long): List<Shot> {
        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Newer Android versions say which folder a picture is in; older ones give its full path.
        @Suppress("DEPRECATION")
        val folderColumn =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH
            else MediaStore.Images.Media.DATA
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DISPLAY_NAME,
            folderColumn,
        )
        val found = ArrayList<Shot>()
        context.contentResolver.query(
            images,
            columns,
            "${MediaStore.Images.Media.DATE_ADDED} > ?",
            arrayOf(sinceSeconds.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < MAX_SCREENSHOTS_AT_ONCE) {
                if (!isScreenshot(cursor.getString(4), cursor.getString(3))) continue
                val id = cursor.getLong(0)
                found.add(Shot(ContentUris.withAppendedId(images, id), id, cursor.getLong(1), cursor.getString(2) ?: "image/png"))
            }
        }
        return found.reversed()
    }

    /** Re-reads the setting and the saved list, in case the app changed them. */
    fun refresh() {
        enabled = AppSettings.clipboardEnabled(context)
        history = ClipboardStore.load(context)
    }

    fun turnOn() = setHistoryOn(true)

    fun setHistoryOn(on: Boolean) {
        AppSettings.setClipboardEnabled(context, on)
        enabled = on
        if (on) capture() // start with whatever is on the clipboard right now
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
        dismissed = null // something new was copied
        peek()
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
                    peek() // the picture can be offered on the strip now that we have a copy
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

    companion object {
        private const val TAG = "ClipboardController"
        private const val SENSITIVE_FLAG = "android.content.extra.IS_SENSITIVE"
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

        /** After a long while away only the latest few screenshots are brought in, not dozens. */
        private const val MAX_SCREENSHOTS_AT_ONCE = 5

        /** The permission Android wants before an app may look at the phone's pictures. */
        fun picturesPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "android.permission.READ_MEDIA_IMAGES"
            else "android.permission.READ_EXTERNAL_STORAGE"

        fun canSeePictures(context: Context): Boolean =
            context.checkSelfPermission(picturesPermission()) == PackageManager.PERMISSION_GRANTED
    }
}
