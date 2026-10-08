package app.keybuilder

import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.model.ActionKind
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.ClipHistoryJson
import app.keybuilder.model.EmojiData
import app.keybuilder.model.Giphy
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyPos
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.PANELS
import app.keybuilder.model.TOOLBAR_HEIGHT_DP
import app.keybuilder.model.insertKey
import app.keybuilder.model.keyAt
import app.keybuilder.model.panelName
import app.keybuilder.model.withActionKind
import app.keybuilder.model.withKeyLabel
import app.keybuilder.model.withPanelTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PanelsTest {

    private val def = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())

    // ---------------------------------------------------------------- panel keys and the strip

    @Test
    fun aPanelKeyOpensItsPanelAndTypesNothing() {
        val state = KeyboardEngine.initialState(def)
        assertNull(state.panel)
        for (panel in PANELS) {
            val result = KeyboardEngine.press(def, state, KeyDef(label = "x", action = KeyAction.OpenPanel(panel)), 0)
            assertEquals(panel, result.state.panel)
            assertTrue(result.effects.isEmpty())
            assertEquals(state.layerId, result.state.layerId)
        }
        val unknown = KeyboardEngine.press(def, state, KeyDef(action = KeyAction.OpenPanel("nope")), 0)
        assertNull(unknown.state.panel)
    }

    @Test
    fun panelKeysAndTheStripSettingAreSaved() {
        val edit = def.insertKey(KeyPos("letters", 3, 1), after = false)
        val at = edit.selection!!
        val withKey = edit.def.withActionKind(at, ActionKind.PANEL)
        assertEquals(KeyAction.OpenPanel("clipboard"), withKey.keyAt(at)!!.action)
        assertEquals("Clipboard", withKey.keyAt(at)!!.label)
        assertEquals(withKey, LayoutJson.parse(LayoutJson.toJson(withKey)))

        // Old files have no "toolbar" entry: the strip is on.
        assertTrue(def.toolbar)
        val hidden = def.copy(toolbar = false)
        assertFalse(LayoutJson.parse(LayoutJson.toJson(hidden)).toolbar)
    }

    @Test
    fun aPanelKeyCanBePointedAtAnotherPanel() {
        val edit = def.insertKey(KeyPos("letters", 3, 1), after = false)
        val at = edit.selection!!
        val key = edit.def.withActionKind(at, ActionKind.PANEL)

        val gif = key.withPanelTarget(at, "gif")
        assertEquals(KeyAction.OpenPanel("gif"), gif.keyAt(at)!!.action)
        assertEquals("GIF", gif.keyAt(at)!!.label) // named after the panel, so the name follows

        val custom = key.withKeyLabel(at, "clips").withPanelTarget(at, "emoji")
        assertEquals("clips", custom.keyAt(at)!!.label) // a label you chose stays

        assertEquals(key, key.withPanelTarget(at, "nope"))
        assertEquals(def, def.withPanelTarget(KeyPos("letters", 0, 0), "gif")) // not a panel key
    }

    @Test
    fun unknownPanelsInAFileAreExplained() {
        try {
            LayoutJson.parse("""{"layers":[{"id":"a","rows":[[{"action":{"type":"panel","to":"stickers"}}]]}]}""")
            fail("expected a LayoutFormatException")
        } catch (e: LayoutFormatException) {
            assertTrue(e.message, e.message!!.contains("unknown panel"))
        }
        assertEquals(listOf("Clipboard", "GIF", "Stickers", "Emoji", "Symbols", "Edit", "Settings"), PANELS.map { panelName(it) })
    }

    @Test
    fun heightCountsRowsGapAndStrip() {
        val state = KeyboardEngine.initialState(def)
        // 4 rows of 52 + one gap of 6 + the strip
        assertEquals(4 * 52f + 6f + TOOLBAR_HEIGHT_DP, KeyboardEngine.heightDp(def, state), 0.01f)
        assertEquals(4 * 52f + 6f, KeyboardEngine.heightDp(def.copy(toolbar = false), state), 0.01f)
    }

    @Test
    fun searchWordsAreTypedDeletedAndSubmitted() {
        var (text, sent) = KeyboardEngine.typeIntoQuery("", listOf(Effect.Commit("c"), Effect.Commit("at")))
        assertEquals("cat", text)
        assertFalse(sent)
        text = KeyboardEngine.typeIntoQuery(text, listOf(Effect.DeleteBackward)).first
        assertEquals("ca", text)
        assertEquals("" to false, KeyboardEngine.typeIntoQuery("", listOf(Effect.DeleteBackward)))
        assertEquals("ca" to true, KeyboardEngine.typeIntoQuery("ca", listOf(Effect.Enter)))
        // An emoji is stored as two parts but is one character to delete.
        assertEquals("a", KeyboardEngine.typeIntoQuery("a😀", listOf(Effect.DeleteBackward)).first)
    }

    // ---------------------------------------------------------------- clipboard history

    @Test
    fun newestCopyIsOnTopAndRepeatsMoveUp() {
        var h = ClipHistory().addText("one", 1).addText("two", 2).addText("three", 3)
        assertEquals(listOf("three", "two", "one"), h.entries.map { it.text })
        h = h.addText("one", 4)
        assertEquals(listOf("one", "three", "two"), h.entries.map { it.text })
        assertEquals(3, h.entries.size)
        assertEquals(1L, h.entries[0].id) // the same entry, not a new one
    }

    @Test
    fun blankAndHugeTextIsIgnored() {
        val h = ClipHistory().addText("keep", 1)
        assertEquals(h, h.addText("   ", 2))
        assertEquals(h, h.addText("x".repeat(ClipHistory.MAX_TEXT_LENGTH + 1), 3))
    }

    @Test
    fun oldEntriesDropOffButPinnedOnesStay() {
        var h = ClipHistory().addText("precious", 0).togglePin(0)
        for (i in 1..ClipHistory.MAX_UNPINNED + 10) h = h.addText("clip $i", i.toLong())
        assertEquals(ClipHistory.MAX_UNPINNED + 1, h.entries.size)
        assertTrue(h.entries.any { it.text == "precious" })
        assertFalse(h.entries.any { it.text == "clip 1" })
        assertEquals("precious", h.ordered.first().text) // pinned entries are shown first
        assertEquals("clip ${ClipHistory.MAX_UNPINNED + 10}", h.ordered[1].text)
    }

    @Test
    fun pinUnpinDeleteAndClear() {
        var h = ClipHistory().addText("a", 1).addText("b", 2).addImage("pic.png", "image/png", 3)
        h = h.togglePin(1)
        assertTrue(h.entries.first { it.id == 1L }.pinned)
        assertEquals(setOf("pic.png"), h.imageFiles())
        assertTrue(h.entries.first { it.id == 3L }.isImage)

        val cleared = h.clearUnpinned()
        assertEquals(listOf("a"), cleared.entries.map { it.text })
        assertTrue(cleared.imageFiles().isEmpty()) // so the picture file can be deleted

        assertEquals(2, h.remove(2).entries.size)
        assertFalse(h.togglePin(1).entries.first { it.id == 1L }.pinned)
    }

    @Test
    fun historySurvivesSavingAndABrokenFileIsJustEmpty() {
        val h = ClipHistory()
            .addText("line one\nline \"two\"", 1)
            .addImage("clip_5.png", "image/png", 5)
            .addText("₹ 500", 7)
            .togglePin(1)
        assertEquals(h, ClipHistoryJson.parse(ClipHistoryJson.toJson(h)))
        assertEquals(ClipHistory(), ClipHistoryJson.parse("not json"))
        assertEquals(ClipHistory(), ClipHistoryJson.parse("{}"))
        assertEquals(1, ClipHistoryJson.parse("""[{"id":1,"text":"ok"},{"id":2},{"text":"no id"},7]""").entries.size)
    }

    // ---------------------------------------------------------------- GIFs

    private val sampleAnswer = """
        {"data":[
          {"id":"abc123","title":"Happy Cat GIF","url":"https://giphy.com/gifs/abc123",
           "images":{
             "fixed_width":{"url":"https://media.giphy.com/media/abc123/200w.gif","width":"200","height":"150"},
             "fixed_width_downsampled":{"url":"https://media.giphy.com/media/abc123/200w_d.gif","width":"200","height":"150"},
             "downsized":{"url":"https://media.giphy.com/media/abc123/giphy-downsized.gif"},
             "original":{"url":"https://media.giphy.com/media/abc123/giphy.gif"}}},
          {"id":"onlywide","title":"",
           "images":{"fixed_width":{"url":"https://media.giphy.com/media/onlywide/200w.gif"}}},
          {"id":"noimages","title":"broken"},
          {"id":"insecure","images":{"fixed_width":{"url":"http://example.com/a.gif"}}},
          "nonsense"
        ],"pagination":{"total_count":2,"count":2,"offset":0},"meta":{"status":200,"msg":"OK"}}
    """

    @Test
    fun readsGiphysAnswer() {
        val gifs = Giphy.parse(sampleAnswer)
        assertEquals(listOf("abc123", "onlywide"), gifs.map { it.id })

        val cat = gifs[0]
        assertEquals("Happy Cat GIF", cat.title)
        assertEquals("https://media.giphy.com/media/abc123/200w_d.gif", cat.previewUrl)
        assertEquals("https://media.giphy.com/media/abc123/giphy-downsized.gif", cat.sendUrl)
        assertEquals("https://giphy.com/gifs/abc123", cat.pageUrl)

        // Missing sizes fall back to what is there.
        val wide = gifs[1]
        assertEquals("GIF", wide.title)
        assertEquals(wide.previewUrl, wide.sendUrl)
        assertEquals(wide.sendUrl, wide.pageUrl)

        assertTrue(Giphy.parse("oops").isEmpty())
        assertTrue(Giphy.parse("""{"data":[]}""").isEmpty())
        assertTrue(Giphy.parse("""{"meta":{"status":401}}""").isEmpty())
    }

    @Test
    fun addressesAreBuiltSafely() {
        val url = Giphy.searchUrl(" KEY1 ", " happy cat & dog ")
        assertTrue(url, url.startsWith("https://api.giphy.com/v1/gifs/search?api_key=KEY1&q=happy+cat+%26+dog&"))
        assertTrue(url.contains("rating=g"))
        assertTrue(Giphy.trendingUrl("KEY1").startsWith("https://api.giphy.com/v1/gifs/trending?api_key=KEY1&"))
        assertTrue(Giphy.searchUrl("k", "नमस्ते").contains("q=%E0%A4"))
    }

    @Test
    fun errorsAreExplained() {
        assertTrue(Giphy.errorMessage(401).contains("key"))
        assertTrue(Giphy.errorMessage(403).contains("key"))
        assertTrue(Giphy.errorMessage(429).contains("limit"))
        assertTrue(Giphy.errorMessage(500).contains("500"))
        assertTrue(Giphy.QUICK_SEARCHES.isNotEmpty())
    }

    // ---------------------------------------------------------------- emoji

    @Test
    fun emojiListIsCleanAndHasNoRepeats() {
        val all = EmojiData.categories.flatMap { it.emoji }
        assertTrue(all.size > 300)
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.none { it.isBlank() || it.contains(' ') })
        assertTrue(EmojiData.categories.all { it.name.isNotBlank() && it.emoji.size >= 40 })
        // Each entry is one emoji: one character, optionally followed by the "draw as emoji" mark.
        for (emoji in all) {
            val points = emoji.codePoints().toArray().filter { it != 0xFE0F }
            assertEquals(emoji, 1, points.size)
        }
    }
}
