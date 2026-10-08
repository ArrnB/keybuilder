package app.keybuilder.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Text shortcuts: a few characters that turn into a longer text when you type them and
 * then a space, comma, full stop or enter. "omw" becomes "on my way", "@@" your e-mail address.
 * You make the list yourself in Settings. No Android code here.
 */

/** [short] is what you type, [text] is what it becomes. */
data class Shortcut(val short: String, val text: String)

data class Shortcuts(val items: List<Shortcut> = emptyList()) {

    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * What [typed] should turn into, or null if it is not a shortcut. Capitals do not matter
     * when matching; typing the shortcut with a capital first letter ("Omw") gives the text
     * a capital first letter too.
     */
    fun expansion(typed: String): String? {
        if (typed.isEmpty() || typed.length > MAX_SHORT_LENGTH) return null
        items.firstOrNull { it.short == typed }?.let { return it.text }
        val match = items.firstOrNull { it.short.equals(typed, ignoreCase = true) } ?: return null
        val capital = typed[0].isUpperCase() && !match.short[0].isUpperCase()
        return if (capital) match.text.replaceFirstChar { it.uppercase() } else match.text
    }

    /** Adds a shortcut, or changes the text of the one already called [short]. */
    fun with(short: String, text: String): Shortcuts {
        val key = short.trim()
        if (shortcutProblem(key, text, this, replacing = key) != null) return this
        val entry = Shortcut(key, text)
        val exists = items.any { it.short.equals(key, ignoreCase = true) }
        val next = if (exists) items.map { if (it.short.equals(key, ignoreCase = true)) entry else it } else items + entry
        return Shortcuts(next)
    }

    fun without(short: String): Shortcuts = Shortcuts(items.filter { !it.short.equals(short, ignoreCase = true) })

    fun toJson(): String = buildJsonArray {
        for (item in items) {
            add(buildJsonObject {
                put("short", item.short)
                put("text", item.text)
            })
        }
    }.toString()

    companion object {
        const val MAX_SHORT_LENGTH = 20
        const val MAX_TEXT_LENGTH = 2000
        const val MAX_SHORTCUTS = 200

        /** Reads what [toJson] wrote. Anything that does not fit is skipped; nonsense gives an empty list. */
        fun parse(json: String): Shortcuts {
            val root = try {
                Json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                return Shortcuts()
            }
            if (root !is JsonArray) return Shortcuts()
            var result = Shortcuts()
            for (el in root) {
                val obj = el as? JsonObject ?: continue
                val short = (obj["short"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                val text = (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                result = result.with(short, text)
            }
            return result
        }
    }
}

/**
 * Why [short] and [text] cannot be saved as a shortcut, in plain words, or null if they can.
 * [replacing] is the shortcut being edited, which may of course keep its own name.
 */
fun shortcutProblem(short: String, text: String, existing: Shortcuts, replacing: String? = null): String? {
    val key = short.trim()
    return when {
        key.isEmpty() -> "Type the shortcut: the few characters you will type."
        key.any { it.isWhitespace() } -> "A shortcut cannot have spaces in it."
        key.length > Shortcuts.MAX_SHORT_LENGTH -> "A shortcut can be at most ${Shortcuts.MAX_SHORT_LENGTH} characters."
        text.isBlank() -> "Type the text the shortcut should turn into."
        text.length > Shortcuts.MAX_TEXT_LENGTH -> "The text can be at most ${Shortcuts.MAX_TEXT_LENGTH} characters."
        existing.items.any { it.short.equals(key, ignoreCase = true) && !it.short.equals(replacing?.trim(), ignoreCase = true) } ->
            "There is already a shortcut called \"$key\"."
        existing.items.size >= Shortcuts.MAX_SHORTCUTS && existing.items.none { it.short.equals(key, ignoreCase = true) } ->
            "That is the most shortcuts the keyboard keeps (${Shortcuts.MAX_SHORTCUTS})."
        else -> null
    }
}

/** The run of characters without a space at the very end of [text]: the thing that might be a shortcut. */
fun trailingToken(text: String): String {
    var start = text.length
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    return text.substring(start)
}
