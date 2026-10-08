package app.keybuilder.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * The clipboard history: what it holds and the rules for adding, pinning and trimming.
 * No Android code here. Reading the real clipboard and storing files is ClipboardController's job.
 */

/** One copied thing: either [text], or a picture saved as [imageFile] of type [mime]. */
data class ClipEntry(
    val id: Long,
    val text: String? = null,
    val imageFile: String? = null,
    val mime: String? = null,
    val pinned: Boolean = false,
) {
    val isImage: Boolean get() = imageFile != null
}

/** Entries are kept newest first. */
data class ClipHistory(val entries: List<ClipEntry> = emptyList()) {

    /** Pinned entries first, then the rest, each group newest first. This is the order shown. */
    val ordered: List<ClipEntry> get() = entries.filter { it.pinned } + entries.filter { !it.pinned }

    /** Adds copied text on top. Copying the same text again moves it up instead of repeating it. */
    fun addText(text: String, id: Long): ClipHistory {
        if (text.isBlank() || text.length > MAX_TEXT_LENGTH) return this
        val existing = entries.firstOrNull { it.text == text }
        val entry = existing ?: ClipEntry(id = id, text = text)
        return ClipHistory(listOf(entry) + entries.filter { it !== existing }).trimmed()
    }

    fun addImage(fileName: String, mime: String, id: Long): ClipHistory =
        ClipHistory(listOf(ClipEntry(id = id, imageFile = fileName, mime = mime)) + entries).trimmed()

    fun hasImage(fileName: String): Boolean = entries.any { it.imageFile == fileName }

    fun togglePin(id: Long): ClipHistory =
        ClipHistory(entries.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })

    fun remove(id: Long): ClipHistory = ClipHistory(entries.filter { it.id != id })

    /** Empties the history but keeps what you pinned. */
    fun clearUnpinned(): ClipHistory = ClipHistory(entries.filter { it.pinned })

    /** The picture files this history still needs. Anything else in the folder can be deleted. */
    fun imageFiles(): Set<String> = entries.mapNotNull { it.imageFile }.toSet()

    /** Keeps every pinned entry and the newest MAX_UNPINNED others. */
    private fun trimmed(): ClipHistory {
        var kept = 0
        return ClipHistory(entries.filter { it.pinned || kept++ < MAX_UNPINNED })
    }

    companion object {
        const val MAX_UNPINNED = 30
        const val MAX_TEXT_LENGTH = 20_000
    }
}

/** Our own copy of a screenshot is saved under a name that starts like this. */
const val SCREENSHOT_FILE_PREFIX = "shot_"

/**
 * Whether a picture in the phone's gallery is a screenshot, judged by its folder and file
 * name. Phones do not mark screenshots any other way: Samsung, Pixel and most others save
 * them in a "Screenshots" folder as "Screenshot_...".
 */
fun isScreenshot(folder: String?, fileName: String?): Boolean {
    val where = (folder.orEmpty() + "/" + fileName.orEmpty()).lowercase()
    return "screenshot" in where || "screen_shot" in where || "screencapture" in where || "screen capture" in where
}

/**
 * A copied text squeezed onto the small button on the strip: on one line, and cut short
 * with "…" if it is longer than [max] characters.
 */
fun quickClipLabel(text: String, max: Int = 16): String {
    val oneLine = text.trim().split(Regex("\\s+")).joinToString(" ")
    return if (oneLine.length <= max) oneLine else oneLine.take(max - 1).trimEnd() + "…"
}

/** Saves the history as JSON. A damaged or missing file simply reads as an empty history. */
object ClipHistoryJson {

    fun toJson(history: ClipHistory): String = buildJsonArray {
        for (entry in history.entries) {
            add(buildJsonObject {
                put("id", entry.id)
                entry.text?.let { put("text", it) }
                entry.imageFile?.let { put("imageFile", it) }
                entry.mime?.let { put("mime", it) }
                if (entry.pinned) put("pinned", true)
            })
        }
    }.toString()

    fun parse(text: String): ClipHistory {
        val root: JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return ClipHistory()
        }
        if (root !is JsonArray) return ClipHistory()
        val entries = root.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val entry = ClipEntry(
                id = id,
                text = obj.text("text"),
                imageFile = obj.text("imageFile"),
                mime = obj.text("mime"),
                pinned = (obj["pinned"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
            entry.takeIf { it.text != null || it.imageFile != null }
        }
        return ClipHistory(entries)
    }

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
