package app.keybuilder.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put

/** Thrown when a layout file is wrong. The message says where and why, in plain words. */
class LayoutFormatException(message: String) : Exception(message)

/**
 * Reads and writes layout files.
 *
 * The file format is forgiving so it is pleasant to edit by hand:
 *  - a key can be just "q" instead of { "label": "q" }
 *  - a row can be just [ ... ] instead of { "keys": [ ... ] }
 *  - an action can be just "shift" instead of { "type": "shift" }
 *
 * See README.md for the full list of fields.
 */
object LayoutJson {

    const val FORMAT_VERSION = 1

    private val pretty = Json { prettyPrint = true }

    // ---------------------------------------------------------------- reading

    fun parse(text: String): KeyboardDef {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw LayoutFormatException("The file is not valid JSON: ${e.message}")
        }
        if (root !is JsonObject) fail("file", "must start with { and end with }")

        val format = root.number("format", "file")?.toInt() ?: FORMAT_VERSION
        if (format > FORMAT_VERSION) {
            fail("file", "was made by a newer version of the app (format $format)")
        }

        val layersEl = root.field("layers") as? JsonArray
            ?: fail("file", "needs a \"layers\" list")
        if (layersEl.isEmpty()) fail("file", "needs at least one layer")
        val layers = layersEl.mapIndexed { index, el -> parseLayer(el, "layer ${index + 1}") }

        val ids = layers.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()?.let {
            fail("file", "two layers are both called \"$it\"")
        }

        val startLayer = root.string("startLayer", "file") ?: ids.first()
        if (startLayer !in ids) fail("file", "\"startLayer\" is \"$startLayer\" but no layer has that id")

        for (layer in layers) {
            for (row in layer.rows) {
                for (key in row.keys) {
                    val action = key.action
                    if (action is KeyAction.SwitchLayer && action.layer !in ids) {
                        fail("layer \"${layer.id}\"", "a key switches to \"${action.layer}\" but no layer has that id")
                    }
                }
            }
        }

        val numberLayer = root.string("numberLayer", "file")
        if (numberLayer != null && numberLayer !in ids) {
            fail("file", "\"numberLayer\" is \"$numberLayer\" but no layer has that id")
        }

        return KeyboardDef(
            numberLayer = numberLayer,
            builtInChoices = root.boolean("builtInChoices", "file") ?: true,
            id = root.string("id", "file") ?: "keyboard",
            name = root.string("name", "file") ?: "Keyboard",
            startLayer = startLayer,
            toolbar = root.boolean("toolbar", "file") ?: true,
            theme = (root.field("theme") as? JsonObject)?.let { parseTheme(it) } ?: Theme(),
            layers = layers,
        )
    }

    private fun parseLayer(el: JsonElement, where: String): Layer {
        if (el !is JsonObject) fail(where, "must be an object in { }")
        val id = el.string("id", where)?.takeIf { it.isNotBlank() } ?: fail(where, "needs an \"id\"")
        val here = "layer \"$id\""
        val rowsEl = el.field("rows") as? JsonArray ?: fail(here, "needs a \"rows\" list")
        if (rowsEl.isEmpty()) fail(here, "needs at least one row")
        return Layer(id, rowsEl.mapIndexed { index, rowEl -> parseRow(rowEl, "$here, row ${index + 1}") })
    }

    private fun parseRow(el: JsonElement, where: String): Row {
        val keysEl: JsonArray
        var height = 1f
        when (el) {
            is JsonArray -> keysEl = el
            is JsonObject -> {
                keysEl = el.field("keys") as? JsonArray ?: fail(where, "needs a \"keys\" list")
                height = (el.number("height", where) ?: 1f).coerceIn(0.5f, 3f)
            }
            else -> fail(where, "must be a list of keys in [ ]")
        }
        if (keysEl.isEmpty()) fail(where, "needs at least one key")
        return Row(
            keys = keysEl.mapIndexed { index, keyEl -> parseKey(keyEl, "$where, key ${index + 1}") },
            height = height,
        )
    }

    private fun parseKey(el: JsonElement, where: String): KeyDef = when {
        el is JsonPrimitive && el.isString -> KeyDef(label = el.content)
        el is JsonObject -> {
            val lookName = el.string("look", where)
            val look = if (lookName == null) KeyLook.NORMAL else {
                KeyLook.entries.firstOrNull { it.jsonName == lookName }
                    ?: fail(where, "unknown look \"$lookName\" (use ${KeyLook.entries.joinToString { it.jsonName }})")
            }
            val width = el.number("width", where) ?: 1f
            if (width <= 0f || width > 20f) fail(where, "\"width\" must be more than 0 and at most 20")
            KeyDef(
                label = el.string("label", where) ?: "",
                shiftLabel = el.string("shiftLabel", where),
                action = el.field("action")?.let { parseAction(it, where) } ?: KeyAction.TypeLabel,
                width = width,
                look = look,
                style = (el.field("style") as? JsonObject)?.let { parseStyle(it, where) },
                alternates = parseAlternates(el, where),
            )
        }
        else -> fail(where, "must be text in quotes or an object in { }")
    }

    private fun parseAlternates(key: JsonObject, where: String): List<String> {
        val el = key.field("alt") ?: return emptyList()
        if (el !is JsonArray) fail(where, "\"alt\" must be a list in [ ], for example [\"1\", \"!\"]")
        return el.map { item ->
            if (item !is JsonPrimitive || !item.isString || item.content.isEmpty()) {
                fail(where, "every entry of \"alt\" must be text in quotes")
            }
            item.content
        }.distinct().take(MAX_ALTERNATES)
    }

    private fun parseAction(el: JsonElement, where: String): KeyAction {
        val type: String
        val obj: JsonObject?
        when {
            el is JsonPrimitive && el.isString -> {
                type = el.content
                obj = null
            }
            el is JsonObject -> {
                type = el.string("type", where) ?: fail(where, "an action in { } needs a \"type\"")
                obj = el
            }
            else -> fail(where, "\"action\" must be text in quotes or an object in { }")
        }
        return when (type) {
            "label" -> KeyAction.TypeLabel
            "space" -> KeyAction.Space
            "backspace" -> KeyAction.Backspace
            "enter" -> KeyAction.Enter
            "shift" -> KeyAction.Shift
            "none" -> KeyAction.None
            "text" -> KeyAction.Text(
                obj?.string("text", where) ?: fail(where, "a \"text\" action needs a \"text\" value")
            )
            "arrow" -> {
                val direction = obj?.string("to", where) ?: fail(where, "an \"arrow\" action needs \"to\" with a direction")
                if (direction !in ARROWS) fail(where, "unknown direction \"$direction\" (use ${ARROWS.joinToString()})")
                KeyAction.Arrow(direction)
            }
            "command" -> {
                val command = obj?.string("to", where) ?: fail(where, "a \"command\" action needs \"to\" with the command")
                if (command !in COMMANDS) fail(where, "unknown command \"$command\" (use ${COMMANDS.joinToString()})")
                KeyAction.Command(command)
            }
            "panel" -> {
                val panel = obj?.string("to", where) ?: fail(where, "a \"panel\" action needs \"to\" with the panel name")
                if (panel !in PANELS) fail(where, "unknown panel \"$panel\" (use ${PANELS.joinToString()})")
                KeyAction.OpenPanel(panel)
            }
            "layer" -> KeyAction.SwitchLayer(
                obj?.string("to", where) ?: fail(where, "a \"layer\" action needs \"to\" with the layer id")
            )
            else -> fail(where, "unknown action \"$type\"")
        }
    }

    private fun parseStyle(obj: JsonObject, where: String) = KeyStyle(
        background = obj.color("background", where),
        textColor = obj.color("textColor", where),
        fontSizeSp = obj.number("fontSizeSp", where)?.coerceIn(8f, 48f),
    )

    private fun parseTheme(obj: JsonObject): Theme {
        val d = Theme()
        val where = "theme"
        return Theme(
            background = obj.color("background", where) ?: d.background,
            keyColor = obj.color("keyColor", where) ?: d.keyColor,
            functionKeyColor = obj.color("functionKeyColor", where) ?: d.functionKeyColor,
            accentColor = obj.color("accentColor", where) ?: d.accentColor,
            keyPressedColor = obj.color("keyPressedColor", where) ?: d.keyPressedColor,
            textColor = obj.color("textColor", where) ?: d.textColor,
            accentTextColor = obj.color("accentTextColor", where) ?: d.accentTextColor,
            borderColor = obj.color("borderColor", where) ?: d.borderColor,
            borderWidthDp = (obj.number("borderWidthDp", where) ?: d.borderWidthDp).coerceIn(0f, 8f),
            cornerRadiusDp = (obj.number("cornerRadiusDp", where) ?: d.cornerRadiusDp).coerceIn(0f, 48f),
            keyHeightDp = (obj.number("keyHeightDp", where) ?: d.keyHeightDp).coerceIn(28f, 96f),
            keyGapDp = (obj.number("keyGapDp", where) ?: d.keyGapDp).coerceIn(0f, 16f),
            fontSizeSp = (obj.number("fontSizeSp", where) ?: d.fontSizeSp).coerceIn(8f, 48f),
            fontFamily = obj.string("fontFamily", where)?.also {
                if (it !in FONT_FAMILIES) fail(where, "\"fontFamily\" must be one of ${FONT_FAMILIES.joinToString()}, not \"$it\"")
            } ?: d.fontFamily,
            bold = obj.boolean("bold", where) ?: d.bold,
            hints = obj.boolean("hints", where) ?: d.hints,
        )
    }

    // ---------------------------------------------------------------- writing

    fun toJson(def: KeyboardDef): String =
        pretty.encodeToString(JsonElement.serializer(), toElement(def))

    private fun toElement(def: KeyboardDef): JsonObject = buildJsonObject {
        put("format", FORMAT_VERSION)
        put("id", def.id)
        put("name", def.name)
        put("startLayer", def.startLayer)
        put("toolbar", def.toolbar)
        def.numberLayer?.let { put("numberLayer", it) }
        put("builtInChoices", def.builtInChoices)
        put("theme", themeElement(def.theme))
        put("layers", buildJsonArray {
            for (layer in def.layers) {
                add(buildJsonObject {
                    put("id", layer.id)
                    put("rows", buildJsonArray { layer.rows.forEach { add(rowElement(it)) } })
                })
            }
        })
    }

    private fun rowElement(row: Row): JsonElement {
        val keys = buildJsonArray { row.keys.forEach { add(keyElement(it)) } }
        if (row.height == 1f) return keys
        return buildJsonObject {
            put("height", row.height)
            put("keys", keys)
        }
    }

    private fun keyElement(key: KeyDef): JsonElement {
        if (key == KeyDef(label = key.label)) return JsonPrimitive(key.label)
        return buildJsonObject {
            if (key.label.isNotEmpty()) put("label", key.label)
            key.shiftLabel?.let { put("shiftLabel", it) }
            if (key.action != KeyAction.TypeLabel) put("action", actionElement(key.action))
            if (key.width != 1f) put("width", key.width)
            if (key.look != KeyLook.NORMAL) put("look", key.look.jsonName)
            key.style?.let { style ->
                put("style", buildJsonObject {
                    style.background?.let { put("background", it) }
                    style.textColor?.let { put("textColor", it) }
                    style.fontSizeSp?.let { put("fontSizeSp", it) }
                })
            }
            if (key.alternates.isNotEmpty()) {
                put("alt", buildJsonArray { key.alternates.forEach { add(it) } })
            }
        }
    }

    private fun actionElement(action: KeyAction): JsonElement = when (action) {
        KeyAction.TypeLabel -> JsonPrimitive("label")
        KeyAction.Space -> JsonPrimitive("space")
        KeyAction.Backspace -> JsonPrimitive("backspace")
        KeyAction.Enter -> JsonPrimitive("enter")
        KeyAction.Shift -> JsonPrimitive("shift")
        KeyAction.None -> JsonPrimitive("none")
        is KeyAction.Text -> buildJsonObject {
            put("type", "text")
            put("text", action.text)
        }
        is KeyAction.Arrow -> buildJsonObject {
            put("type", "arrow")
            put("to", action.direction)
        }
        is KeyAction.Command -> buildJsonObject {
            put("type", "command")
            put("to", action.command)
        }
        is KeyAction.OpenPanel -> buildJsonObject {
            put("type", "panel")
            put("to", action.panel)
        }
        is KeyAction.SwitchLayer -> buildJsonObject {
            put("type", "layer")
            put("to", action.layer)
        }
    }

    private fun themeElement(t: Theme): JsonObject = buildJsonObject {
        put("background", t.background)
        put("keyColor", t.keyColor)
        put("functionKeyColor", t.functionKeyColor)
        put("accentColor", t.accentColor)
        put("keyPressedColor", t.keyPressedColor)
        put("textColor", t.textColor)
        put("accentTextColor", t.accentTextColor)
        put("borderColor", t.borderColor)
        put("borderWidthDp", t.borderWidthDp)
        put("cornerRadiusDp", t.cornerRadiusDp)
        put("keyHeightDp", t.keyHeightDp)
        put("keyGapDp", t.keyGapDp)
        put("fontSizeSp", t.fontSizeSp)
        put("fontFamily", t.fontFamily)
        put("bold", t.bold)
        put("hints", t.hints)
    }

    // ---------------------------------------------------------------- helpers

    private fun fail(where: String, problem: String): Nothing =
        throw LayoutFormatException("Layout problem in $where: $problem")

    /** A field's value, treating an explicit null the same as a missing field. */
    private fun JsonObject.field(name: String): JsonElement? = this[name]?.takeUnless { it is JsonNull }

    private fun JsonObject.string(name: String, where: String): String? {
        val el = field(name) ?: return null
        if (el !is JsonPrimitive || !el.isString) fail(where, "\"$name\" must be text in quotes")
        return el.content
    }

    private fun JsonObject.number(name: String, where: String): Float? {
        val el = field(name) ?: return null
        val value = (el as? JsonPrimitive)?.takeUnless { it.isString }?.floatOrNull
        return value ?: fail(where, "\"$name\" must be a number")
    }

    private fun JsonObject.boolean(name: String, where: String): Boolean? {
        val el = field(name) ?: return null
        val value = (el as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        return value ?: fail(where, "\"$name\" must be true or false")
    }

    private fun JsonObject.color(name: String, where: String): String? {
        val value = string(name, where) ?: return null
        if (parseHexColor(value) == null) fail(where, "\"$name\" must be a colour like #1B1D22, not \"$value\"")
        return value
    }
}
