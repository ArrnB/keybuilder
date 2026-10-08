package app.keybuilder.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * The GIFs (or stickers) you sent recently and the ones you marked as favourites.
 * Only their addresses are kept, not the pictures. No Android code here.
 */
data class SavedGifs(
    /** Newest first. */
    val recent: List<GifItem> = emptyList(),
    /** Newest first. */
    val favourites: List<GifItem> = emptyList(),
) {
    /** Puts [gif] at the front of the recent list, without repeats. */
    fun withRecent(gif: GifItem): SavedGifs =
        copy(recent = (listOf(gif) + recent.filter { it.id != gif.id }).take(MAX_RECENT))

    fun isFavourite(id: String): Boolean = favourites.any { it.id == id }

    /** Adds [gif] to the favourites, or removes it if it is one already. */
    fun toggleFavourite(gif: GifItem): SavedGifs =
        if (isFavourite(gif.id)) copy(favourites = favourites.filter { it.id != gif.id })
        else copy(favourites = (listOf(gif) + favourites).take(MAX_FAVOURITES))

    fun toJson(): String = buildJsonObject {
        put("recent", list(recent))
        put("favourites", list(favourites))
    }.toString()

    private fun list(gifs: List<GifItem>): JsonArray = buildJsonArray {
        for (gif in gifs) {
            add(buildJsonObject {
                put("id", gif.id)
                put("title", gif.title)
                put("preview", gif.previewUrl)
                put("send", gif.sendUrl)
                put("page", gif.pageUrl)
            })
        }
    }

    companion object {
        const val MAX_RECENT = 30
        const val MAX_FAVOURITES = 100

        /** Reads what [toJson] wrote. Entries with missing parts are skipped; nonsense gives empty lists. */
        fun parse(json: String): SavedGifs {
            val root = try {
                Json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                return SavedGifs()
            }
            val obj = root as? JsonObject ?: return SavedGifs()
            return SavedGifs(
                recent = read(obj["recent"]).take(MAX_RECENT),
                favourites = read(obj["favourites"]).take(MAX_FAVOURITES),
            )
        }

        private fun read(element: JsonElement?): List<GifItem> {
            val array = element as? JsonArray ?: return emptyList()
            val seen = HashSet<String>()
            return array.mapNotNull { el ->
                val gif = el as? JsonObject ?: return@mapNotNull null
                fun text(name: String) = (gif[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val id = text("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                // Only addresses that go to a web server over a safe connection are ever loaded.
                val preview = text("preview")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val send = text("send")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                if (!seen.add(id)) return@mapNotNull null
                GifItem(id, text("title").orEmpty().ifBlank { "GIF" }, preview, send, text("page") ?: send)
            }
        }
    }
}
