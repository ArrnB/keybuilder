package app.keybuilder

import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyLook
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.parseHexColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LayoutJsonTest {

    /** The layout that ships inside the app. Unit tests run from the app/ folder. */
    private val bundled = File("src/main/assets/layouts/qwerty.json").readText()

    @Test
    fun bundledLayoutLoads() {
        val def = LayoutJson.parse(bundled)
        assertEquals("qwerty", def.id)
        assertEquals("letters", def.startLayer)
        assertEquals(listOf("letters", "symbols", "symbols2"), def.layers.map { it.id })
        assertEquals(4, def.layer("letters")!!.rows.size)
    }

    @Test
    fun bundledLayoutRowsAreAllTheSameWidth() {
        val def = LayoutJson.parse(bundled)
        for (layer in def.layers) {
            for ((index, row) in layer.rows.withIndex()) {
                val total = row.keys.sumOf { it.width.toDouble() }
                assertEquals("${layer.id} row ${index + 1}", 10.0, total, 0.001)
            }
        }
    }

    @Test
    fun bundledLayoutColoursAreAllValid() {
        val t = LayoutJson.parse(bundled).theme
        val colours = listOf(
            t.background, t.keyColor, t.functionKeyColor, t.accentColor,
            t.keyPressedColor, t.textColor, t.accentTextColor, t.borderColor,
        )
        colours.forEach { assertNotNull(it, parseHexColor(it)) }
    }

    @Test
    fun savingAndLoadingGivesTheSameKeyboard() {
        val def = LayoutJson.parse(bundled)
        assertEquals(def, LayoutJson.parse(LayoutJson.toJson(def)))
    }

    @Test
    fun shortAndLongFormsMeanTheSame() {
        val short = LayoutJson.parse("""{"layers":[{"id":"a","rows":[["q",{"action":"shift"}]]}]}""")
        val long = LayoutJson.parse(
            """{"layers":[{"id":"a","rows":[{"keys":[{"label":"q"},{"action":{"type":"shift"}}]}]}]}"""
        )
        assertEquals(short, long)
        assertEquals("a", short.startLayer)
        assertEquals(KeyAction.Shift, short.layers[0].rows[0].keys[1].action)
    }

    @Test
    fun everyKeyFieldIsRead() {
        val def = LayoutJson.parse(
            """
            {"layers":[{"id":"a","rows":[{"height":1.5,"keys":[
              {"label":"hi","shiftLabel":"HI!","action":{"type":"text","text":"hello there"},
               "width":2.5,"look":"accent","style":{"background":"#112233","textColor":"#80FFFFFF","fontSizeSp":14}}
            ]}]}]}
            """
        )
        val row = def.layers[0].rows[0]
        val key = row.keys[0]
        assertEquals(1.5f, row.height)
        assertEquals("hi", key.label)
        assertEquals("HI!", key.shiftLabel)
        assertEquals(KeyAction.Text("hello there"), key.action)
        assertEquals(2.5f, key.width)
        assertEquals(KeyLook.ACCENT, key.look)
        assertEquals("#112233", key.style!!.background)
        assertEquals(14f, key.style!!.fontSizeSp)
    }

    @Test
    fun mistakesAreExplained() {
        expectProblem("""not json""", "not valid JSON")
        expectProblem("""{"layers":[]}""", "at least one layer")
        expectProblem("""{"layers":[{"id":"a","rows":[[]]}]}""", "at least one key")
        expectProblem("""{"layers":[{"id":"a","rows":[["q"]]},{"id":"a","rows":[["w"]]}]}""", "both called")
        expectProblem("""{"startLayer":"zzz","layers":[{"id":"a","rows":[["q"]]}]}""", "startLayer")
        expectProblem(
            """{"layers":[{"id":"a","rows":[[{"action":{"type":"layer","to":"nowhere"}}]]}]}""",
            "switches to \"nowhere\"",
        )
        expectProblem("""{"layers":[{"id":"a","rows":[[{"action":"fly"}]]}]}""", "unknown action")
        expectProblem("""{"layers":[{"id":"a","rows":[[{"label":"q","width":0}]]}]}""", "width")
        expectProblem("""{"layers":[{"id":"a","rows":[[{"label":"q","width":"wide"}]]}]}""", "must be a number")
        expectProblem("""{"theme":{"keyColor":"blue"},"layers":[{"id":"a","rows":[["q"]]}]}""", "colour")
        expectProblem("""{"format":99,"layers":[{"id":"a","rows":[["q"]]}]}""", "newer version")
    }

    @Test
    fun problemsSayWhereTheyAre() {
        try {
            LayoutJson.parse("""{"layers":[{"id":"main","rows":[["q"],["w",{"look":"shiny"}]]}]}""")
            fail("expected a LayoutFormatException")
        } catch (e: LayoutFormatException) {
            assertTrue(e.message, e.message!!.contains("layer \"main\", row 2, key 2"))
        }
    }

    @Test
    fun hexColours() {
        assertEquals(0xFF1B1D22.toInt(), parseHexColor("#1B1D22"))
        assertEquals(0x80FFFFFF.toInt(), parseHexColor("#80ffffff"))
        assertEquals(0, parseHexColor("#00000000"))
        assertNull(parseHexColor("1B1D22"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#-12345"))
        assertNull(parseHexColor("blue"))
    }

    private fun expectProblem(json: String, expectedInMessage: String) {
        try {
            LayoutJson.parse(json)
            fail("expected a problem mentioning \"$expectedInMessage\" for: $json")
        } catch (e: LayoutFormatException) {
            assertTrue("message was: ${e.message}", e.message!!.contains(expectedInMessage))
        }
    }
}
