package app.keybuilder.model

import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/*
 * Talking to GIPHY, the GIF library: building the web addresses to ask, and reading the answers.
 * No Android code and no networking here (that is net/Http.kt), so this is unit tested
 * against a sample answer.
 *
 * To use another provider later, write the same three things for it: a search address,
 * a trending address, and a function that turns its answer into GifItems.
 */

/** One GIF: a small moving preview for the grid, and the file that is actually sent. */
data class GifItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val sendUrl: String,
    /** The GIF's page on giphy.com, typed as a link where an app does not accept pictures. */
    val pageUrl: String,
)

object Giphy {

    private const val BASE = "https://api.giphy.com/v1/gifs"
    const val PAGE_SIZE = 24

    /** Words offered as one-tap searches. */
    val QUICK_SEARCHES = listOf("hello", "thanks", "yes", "no", "lol", "love", "happy", "sad", "wow", "ok", "bye", "congrats")

    fun trendingUrl(apiKey: String): String =
        "$BASE/trending?api_key=${encode(apiKey.trim())}&limit=$PAGE_SIZE&rating=g"

    fun searchUrl(apiKey: String, query: String): String =
        "$BASE/search?api_key=${encode(apiKey.trim())}&q=${encode(query.trim())}&limit=$PAGE_SIZE&rating=g"

    /** Reads GIPHY's answer. GIFs with missing parts are skipped; nonsense gives an empty list. */
    fun parse(json: String): List<GifItem> {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return emptyList()
        }
        val data = (root as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val gif = el as? JsonObject ?: return@mapNotNull null
            val images = gif["images"] as? JsonObject ?: return@mapNotNull null
            val id = gif.text("id") ?: return@mapNotNull null
            // Small and light for the grid; a size-limited version for sending.
            val preview = images.url("fixed_width_downsampled") ?: images.url("fixed_width") ?: return@mapNotNull null
            val send = images.url("downsized") ?: images.url("fixed_width") ?: images.url("original") ?: preview
            GifItem(
                id = id,
                title = gif.text("title").orEmpty().ifBlank { "GIF" },
                previewUrl = preview,
                sendUrl = send,
                pageUrl = gif.text("url") ?: send,
            )
        }
    }

    /** What to tell the user when GIPHY answers with an error number. */
    fun errorMessage(httpCode: Int): String = when (httpCode) {
        401, 403 -> "GIPHY did not accept the key. Check it in the app under Clipboard and GIFs."
        429 -> "GIPHY's hourly limit for this key is used up. Try again later."
        else -> "GIPHY answered with error $httpCode."
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.url(rendition: String): String? =
        (this[rendition] as? JsonObject)?.text("url")?.takeIf { it.startsWith("https://") }
}
