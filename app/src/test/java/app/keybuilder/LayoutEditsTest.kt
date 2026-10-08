package app.keybuilder

import app.keybuilder.engine.Effect
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.KeyboardState
import app.keybuilder.model.ActionKind
import app.keybuilder.model.EditHistory
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyboardDef
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.NEW_KEY
import app.keybuilder.model.addLayer
import app.keybuilder.model.canDeleteKey
import app.keybuilder.model.canDeleteLayer
import app.keybuilder.model.canDeleteRow
import app.keybuilder.model.deleteKey
import app.keybuilder.model.deleteLayer
import app.keybuilder.model.deleteRow
import app.keybuilder.model.duplicateKey
import app.keybuilder.model.duplicateLayer
import app.keybuilder.model.insertKey
import app.keybuilder.model.insertRow
import app.keybuilder.model.keyAt
import app.keybuilder.model.keyboardNameProblem
import app.keybuilder.model.kind
import app.keybuilder.model.layerNameProblem
import app.keybuilder.model.moveKey
import app.keybuilder.model.moveKeyToRow
import app.keybuilder.model.moveRow
import app.keybuilder.model.renameLayer
import app.keybuilder.model.snapKeyWidth
import app.keybuilder.model.snapRowHeight
import app.keybuilder.model.uniqueKeyboardId
import app.keybuilder.model.uniqueName
import app.keybuilder.model.warnings
import app.keybuilder.model.withActionKind
import app.keybuilder.model.withKeyLabel
import app.keybuilder.model.withKeyLook
import app.keybuilder.model.withKeyText
import app.keybuilder.model.withKeyWidth
import app.keybuilder.model.withLayerTarget
import app.keybuilder.model.withRowHeight
import app.keybuilder.model.withShiftLabel
import app.keybuilder.model.withStartLayer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEditsTest {

    private val def = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())

    private fun KeyboardDef.labels(layerId: String, row: Int): String =
        layer(layerId)!!.rows[row].keys.joinToString("") { it.label.ifEmpty { "_" } }

    private fun pos(row: Int, index: Int, layer: String = "letters") = KeyPos(layer, row, index)

    /** Every edit must leave a keyboard that still saves, loads and obeys the three rules. */
    private fun assertHealthy(kb: KeyboardDef) {
        assertTrue(kb.layers.isNotEmpty())
        assertNotNull(kb.layer(kb.startLayer))
        for (layer in kb.layers) {
            assertTrue(layer.rows.isNotEmpty())
            layer.rows.forEach { assertTrue(it.keys.isNotEmpty()) }
        }
        assertEquals(kb, LayoutJson.parse(LayoutJson.toJson(kb)))
    }

    // ---------------------------------------------------------------- keys

    @Test
    fun addsAKeyBeforeOrAfterAndSelectsIt() {
        val after = def.insertKey(pos(0, 0), after = true)
        assertEquals("q?wertyuiop", after.def.labels("letters", 0))
        assertEquals(pos(0, 1), after.selection)

        val before = def.insertKey(pos(0, 0), after = false)
        assertEquals("?qwertyuiop", before.def.labels("letters", 0))
        assertEquals(pos(0, 0), before.selection)
        assertEquals(NEW_KEY, before.def.keyAt(before.selection!!))

        val atEnd = def.insertKey(pos(0, 9), after = true)
        assertEquals("qwertyuiop?", atEnd.def.labels("letters", 0))
        assertHealthy(atEnd.def)
        assertSame(def, def.insertKey(pos(0, 99), after = true).def)
    }

    @Test
    fun duplicatesAKeyWithAllItsSettings() {
        val shift = pos(2, 0)
        val edit = def.duplicateKey(shift)
        assertEquals(def.keyAt(shift), edit.def.keyAt(pos(2, 1)))
        assertEquals(pos(2, 1), edit.selection)
    }

    @Test
    fun deletingSelectsTheNeighbour() {
        val middle = def.deleteKey(pos(0, 3))
        assertEquals("qwetyuiop", middle.def.labels("letters", 0))
        assertEquals(pos(0, 3), middle.selection)

        val last = def.deleteKey(pos(0, 9))
        assertEquals("qwertyuio", last.def.labels("letters", 0))
        assertEquals(pos(0, 8), last.selection)
        assertHealthy(last.def)
    }

    @Test
    fun deletingTheOnlyKeyOfARowRemovesTheRow() {
        val withRow = def.insertRow("letters", afterRow = 0)
        assertEquals(5, withRow.def.layer("letters")!!.rows.size)
        val edit = withRow.def.deleteKey(withRow.selection!!)
        assertEquals(def, edit.def)
        assertNull(edit.selection)
    }

    @Test
    fun theLastKeyOfALayerCannotBeDeleted() {
        val tiny = LayoutJson.parse("""{"layers":[{"id":"a","rows":[["q"]]}]}""")
        val only = KeyPos("a", 0, 0)
        assertFalse(tiny.canDeleteKey(only))
        assertSame(tiny, tiny.deleteKey(only).def)
        assertEquals(only, tiny.deleteKey(only).selection)
        assertTrue(def.canDeleteKey(pos(0, 0)))
        assertFalse(def.canDeleteKey(pos(0, 99)))
    }

    @Test
    fun movesAKeyWithinItsRow() {
        val right = def.moveKey(pos(0, 0), +1)
        assertEquals("wqertyuiop", right.def.labels("letters", 0))
        assertEquals(pos(0, 1), right.selection)
        assertEquals(def, right.def.moveKey(right.selection!!, -1).def)

        // At the edges nothing happens and the selection stays.
        assertSame(def, def.moveKey(pos(0, 0), -1).def)
        assertEquals(pos(0, 0), def.moveKey(pos(0, 0), -1).selection)
        assertSame(def, def.moveKey(pos(0, 9), +1).def)
    }

    @Test
    fun movesAKeyToAnotherRow() {
        val down = def.moveKeyToRow(pos(0, 0), +1)
        assertEquals("wertyuiop", down.def.labels("letters", 0))
        assertEquals("qasdfghjkl", down.def.labels("letters", 1).replace("_", ""))
        assertEquals(pos(1, 0), down.selection)
        assertEquals("q", down.def.keyAt(down.selection!!)!!.label)

        // A key further along than the target row is long lands at its end.
        val longRow = def.insertKey(pos(0, 9), after = true).def.insertKey(pos(0, 10), after = true)
        val tail = longRow.def.moveKeyToRow(longRow.selection!!, +1)
        assertEquals(pos(1, 11), tail.selection)

        assertSame(def, def.moveKeyToRow(pos(0, 0), -1).def)
        assertSame(def, def.moveKeyToRow(pos(3, 0), +1).def)
        assertHealthy(down.def)
    }

    @Test
    fun movingTheOnlyKeyOutOfARowRemovesThatRow() {
        val added = def.insertRow("letters", afterRow = 0) // new single-key row is row 1
        val down = added.def.moveKeyToRow(added.selection!!, +1)
        assertEquals(4, down.def.layer("letters")!!.rows.size)
        // The old row 2 slid up to row 1 and now starts with the moved key.
        assertEquals(pos(1, 0), down.selection)
        assertEquals("?", down.def.keyAt(down.selection!!)!!.label)

        val up = added.def.moveKeyToRow(added.selection!!, -1)
        assertEquals(pos(0, 0), up.selection)
        assertEquals("?qwertyuiop", up.def.labels("letters", 0))
        assertHealthy(up.def)
    }

    @Test
    fun widthsSnapToQuarters() {
        assertEquals(1.25f, snapKeyWidth(1.3f))
        assertEquals(0.5f, snapKeyWidth(0.1f))
        assertEquals(6f, snapKeyWidth(40f))
        assertEquals(2.5f, def.withKeyWidth(pos(0, 0), 2.45f).keyAt(pos(0, 0))!!.width)
    }

    // ---------------------------------------------------------------- what a key does

    @Test
    fun everyActionKindRoundTrips() {
        val start = def.insertKey(pos(0, 0), after = true)
        val at = start.selection!!
        for (kind in ActionKind.entries) {
            val changed = start.def.withActionKind(at, kind)
            assertEquals(kind.name, kind, changed.keyAt(at)!!.action.kind())
            assertHealthy(changed)
        }
    }

    @Test
    fun changingTheActionTidiesTheKey() {
        val start = def.insertKey(pos(0, 0), after = true)
        val at = start.selection!!

        val shift = start.def.withActionKind(at, ActionKind.SHIFT).keyAt(at)!!
        assertEquals("", shift.label) // so the icon shows
        assertEquals(KeyLook.FUNCTION, shift.look)

        assertEquals(KeyLook.ACCENT, start.def.withActionKind(at, ActionKind.ENTER).keyAt(at)!!.look)

        val layerKey = start.def.withActionKind(at, ActionKind.LAYER).keyAt(at)!!
        assertEquals(KeyAction.SwitchLayer("symbols"), layerKey.action)
        assertEquals("symbols", layerKey.label)

        // A real label becomes the text of a text key; the placeholder does not.
        val q = def.withActionKind(pos(0, 0), ActionKind.TEXT).keyAt(pos(0, 0))!!
        assertEquals(KeyAction.Text("q"), q.action)
        assertEquals(KeyAction.Text(""), start.def.withActionKind(at, ActionKind.TEXT).keyAt(at)!!.action)

        // A look that was chosen on purpose is kept.
        val accentQ = def.insertKey(pos(0, 0), after = true, key = KeyDef(label = "x", look = KeyLook.ACCENT))
        assertEquals(KeyLook.ACCENT, accentQ.def.withActionKind(accentQ.selection!!, ActionKind.SHIFT).keyAt(accentQ.selection!!)!!.look)

        // Choosing the kind it already has changes nothing.
        assertEquals(def, def.withActionKind(pos(2, 0), ActionKind.SHIFT))
    }

    @Test
    fun layerKeysCanBeRePointed() {
        val key = pos(3, 0) // the ?123 key
        val changed = def.withLayerTarget(key, "symbols2")
        assertEquals(KeyAction.SwitchLayer("symbols2"), changed.keyAt(key)!!.action)
        assertEquals("?123", changed.keyAt(key)!!.label)
        assertEquals(def, def.withLayerTarget(key, "missing"))
        assertEquals(def, def.withLayerTarget(pos(0, 0), "symbols")) // not a layer key
    }

    @Test
    fun labelsLooksAndTextsCanBeSet() {
        val q = pos(0, 0)
        assertEquals("Q!", def.withKeyLabel(q, "Q!").keyAt(q)!!.label)
        assertEquals("?", def.withShiftLabel(q, "?").keyAt(q)!!.shiftLabel)
        assertNull(def.withShiftLabel(q, "?").withShiftLabel(q, "").keyAt(q)!!.shiftLabel)
        assertEquals(KeyLook.SPACER, def.withKeyLook(q, KeyLook.SPACER).keyAt(q)!!.look)

        val snippet = def.withActionKind(q, ActionKind.TEXT).withKeyText(q, "on my way")
        assertEquals(KeyAction.Text("on my way"), snippet.keyAt(q)!!.action)
        assertEquals("q", snippet.keyAt(q)!!.label)
        assertHealthy(snippet)
        // Only text keys have a text.
        assertEquals(def, def.withKeyText(q, "nope"))
    }

    // ---------------------------------------------------------------- rows

    @Test
    fun rowsCanBeAddedMovedAndDeleted() {
        val added = def.insertRow("letters", afterRow = 3)
        assertEquals(5, added.def.layer("letters")!!.rows.size)
        assertEquals(pos(4, 0), added.selection)

        val up = added.def.moveRow(added.selection!!, -1)
        assertEquals(pos(3, 0), up.selection)
        assertEquals("?", up.def.labels("letters", 3))
        assertEquals(def.labels("letters", 3), up.def.labels("letters", 4))
        assertSame(def, def.moveRow(pos(0, 0), -1).def)
        assertSame(def, def.moveRow(pos(3, 0), +1).def)

        val deleted = up.def.deleteRow("letters", 3)
        assertEquals(def, deleted.def)
        assertNull(deleted.selection)
        assertHealthy(up.def)
    }

    @Test
    fun theLastRowOfALayerCannotBeDeleted() {
        val tiny = LayoutJson.parse("""{"layers":[{"id":"a","rows":[["q","w"]]}]}""")
        assertFalse(tiny.canDeleteRow("a", 0))
        assertSame(tiny, tiny.deleteRow("a", 0).def)
        assertTrue(def.canDeleteRow("letters", 0))
        assertFalse(def.canDeleteRow("letters", 9))
    }

    @Test
    fun rowHeightsSnapAndSave() {
        assertEquals(1.3f, snapRowHeight(1.31f))
        assertEquals(0.6f, snapRowHeight(0.1f))
        assertEquals(1.6f, snapRowHeight(9f))
        val taller = def.withRowHeight("letters", 3, 1.42f)
        assertEquals(1.4f, taller.layer("letters")!!.rows[3].height)
        assertEquals(1f, taller.layer("letters")!!.rows[0].height)
        assertHealthy(taller)
    }

    // ---------------------------------------------------------------- layers

    @Test
    fun aNewLayerAlwaysHasAWayBack() {
        val added = def.addLayer("emoji", returnTo = "letters")
        val layer = added.layer("emoji")!!
        assertEquals(4, added.layers.size)
        assertTrue(layer.rows.flatMap { it.keys }.any { it.action == KeyAction.SwitchLayer("letters") })
        assertTrue(layer.rows.flatMap { it.keys }.any { it.action == KeyAction.Backspace })
        assertHealthy(added)
        // It is not reachable yet, and the editor says so.
        assertTrue(added.warnings().any { it.contains("\"emoji\"") })

        assertSame(def, def.addLayer("letters", "letters")) // name taken
        assertSame(def, def.addLayer("   ", "letters"))
    }

    @Test
    fun layerNamesAreChecked() {
        assertNull(def.layerNameProblem("numbers"))
        assertNotNull(def.layerNameProblem(""))
        assertNotNull(def.layerNameProblem("symbols"))
        assertNotNull(def.layerNameProblem("x".repeat(25)))
        // Keeping your own name while renaming is fine.
        assertNull(def.layerNameProblem("symbols", renaming = "symbols"))
    }

    @Test
    fun renamingALayerUpdatesEveryKeyThatOpensIt() {
        val renamed = def.renameLayer("symbols", "numbers")
        assertNull(renamed.layer("symbols"))
        assertNotNull(renamed.layer("numbers"))
        val allKeys = renamed.layers.flatMap { l -> l.rows.flatMap { it.keys } }
        assertTrue(allKeys.none { it.action == KeyAction.SwitchLayer("symbols") })
        assertEquals(2, allKeys.count { it.action == KeyAction.SwitchLayer("numbers") })
        // "?123" was a custom label, so it stays.
        assertEquals("?123", renamed.keyAt(pos(3, 0))!!.label)
        assertHealthy(renamed)

        val start = def.renameLayer("letters", "abc")
        assertEquals("abc", start.startLayer)
        assertHealthy(start)

        assertSame(def, def.renameLayer("symbols", "letters"))
        assertSame(def, def.renameLayer("missing", "x"))
        assertEquals(def, renamed.renameLayer("numbers", "symbols"))
    }

    @Test
    fun keysNamedAfterALayerFollowItsNewName() {
        val added = def.addLayer("emoji", "letters").renameLayer("letters", "abc")
        val back = added.layer("emoji")!!.rows[1].keys[0]
        assertEquals("abc", back.label)
        assertEquals(KeyAction.SwitchLayer("abc"), back.action)
    }

    @Test
    fun duplicatingALayerPutsTheCopyNextToIt() {
        val (copied, name) = def.duplicateLayer("symbols")
        assertEquals("symbols copy", name)
        assertEquals(listOf("letters", "symbols", "symbols copy", "symbols2"), copied.layers.map { it.id })
        assertEquals(def.layer("symbols")!!.rows, copied.layer(name)!!.rows)
        assertEquals("symbols copy 2", copied.duplicateLayer("symbols").second)
        assertHealthy(copied)
    }

    @Test
    fun deletingALayerLeavesNoBrokenKeys() {
        val deleted = def.deleteLayer("symbols2")
        assertEquals(listOf("letters", "symbols"), deleted.layers.map { it.id })
        val allKeys = deleted.layers.flatMap { l -> l.rows.flatMap { it.keys } }
        assertTrue(allKeys.none { it.action == KeyAction.SwitchLayer("symbols2") })
        // The key that opened it is still there, now doing nothing, and that is reported.
        assertEquals(KeyAction.None, deleted.keyAt(KeyPos("symbols", 2, 0))!!.action)
        assertTrue(deleted.warnings().any { it.contains("does nothing") })
        assertHealthy(deleted)

        val noStart = def.deleteLayer("letters")
        assertEquals("symbols", noStart.startLayer)
        assertHealthy(noStart)

        val tiny = LayoutJson.parse("""{"layers":[{"id":"a","rows":[["q"]]}]}""")
        assertFalse(tiny.canDeleteLayer("a"))
        assertSame(tiny, tiny.deleteLayer("a"))
        assertSame(def, def.deleteLayer("missing"))
    }

    @Test
    fun startLayerCanBeChanged() {
        assertEquals("symbols", def.withStartLayer("symbols").startLayer)
        assertSame(def, def.withStartLayer("missing"))
    }

    @Test
    fun theBuiltInKeyboardHasNoWarnings() {
        assertEquals(emptyList<String>(), def.warnings())
    }

    @Test
    fun warningsCatchDeadEnds() {
        // Remove the ABC keys from symbols2 and symbols: you could get in but not out.
        val noWayBack = def
            .deleteKey(KeyPos("symbols2", 3, 0)).def
            .deleteKey(KeyPos("symbols2", 2, 0)).def
        assertTrue(noWayBack.warnings().toString(), noWayBack.warnings().any { it.contains("\"symbols2\" has no key") })

        val noBackspace = LayoutJson.parse("""{"layers":[{"id":"a","rows":[["q"]]}]}""")
        assertEquals(listOf("There is no backspace key."), noBackspace.warnings())
    }

    // ---------------------------------------------------------------- undo

    @Test
    fun undoWalksBackThroughEdits() {
        var history = EditHistory()
        assertFalse(history.canUndo)
        assertNull(history.undo())

        val one = def.insertKey(pos(0, 0), after = true).def
        history = history.record(def, tag = null)
        val two = one.deleteKey(pos(1, 1)).def
        history = history.record(one, tag = null)
        assertEquals(2, history.steps)

        val (back1, h1) = history.undo()!!
        assertEquals(one, back1)
        val (back2, h2) = h1.undo()!!
        assertEquals(def, back2)
        assertFalse(h2.canUndo)
        assertNotNull(two)
    }

    @Test
    fun draggingASliderIsOneUndoStep() {
        var history = EditHistory()
        var kb = def
        for (w in listOf(1.25f, 1.5f, 1.75f, 2f)) {
            history = history.record(kb, tag = "width")
            kb = kb.withKeyWidth(pos(0, 0), w)
        }
        assertEquals(1, history.steps)
        assertEquals(def, history.undo()!!.first)

        // A different edit in between starts a new step, and so does undo.
        history = history.record(kb, tag = "height").record(kb, tag = "width")
        assertEquals(3, history.steps)
        val afterUndo = history.undo()!!.second
        assertEquals(3, afterUndo.record(kb, tag = "width").steps)
    }

    @Test
    fun historyIsCapped() {
        var history = EditHistory()
        repeat(EditHistory.LIMIT + 20) { history = history.record(def, tag = null) }
        assertEquals(EditHistory.LIMIT, history.steps)
    }

    // ---------------------------------------------------------------- names

    @Test
    fun namesAndIdsAreMadeUnique() {
        assertEquals("QWERTY", uniqueName(emptyList(), "QWERTY"))
        assertEquals("QWERTY 2", uniqueName(listOf("QWERTY"), "QWERTY"))
        assertEquals("QWERTY 3", uniqueName(listOf("QWERTY", "QWERTY 2"), " QWERTY "))
        assertEquals("Keyboard", uniqueName(emptyList(), "  "))
        assertEquals("keyboard-1", uniqueKeyboardId(listOf("qwerty")))
        assertEquals("keyboard-3", uniqueKeyboardId(listOf("keyboard-1", "keyboard-2")))
        assertNull(keyboardNameProblem("Work"))
        assertNotNull(keyboardNameProblem("  "))
        assertNotNull(keyboardNameProblem("x".repeat(31)))
    }

    // ---------------------------------------------------------------- everything together

    @Test
    fun anEditedKeyboardStillTypes() {
        // Build a small numbers layer by hand, the way the editor would.
        var kb = def.addLayer("pad", returnTo = "letters")
        val first = KeyPos("pad", 0, 0)
        kb = kb.withKeyLabel(first, "1")
        var edit = kb.insertKey(first, after = true)
        kb = edit.def.withKeyLabel(edit.selection!!, "2")
        // Give the letters layer a key that opens it.
        edit = kb.insertKey(pos(3, 1), after = false)
        kb = edit.def.withActionKind(edit.selection!!, ActionKind.LAYER).withLayerTarget(edit.selection!!, "pad")
        assertEquals(emptyList<String>(), kb.warnings())
        assertHealthy(kb)

        var state: KeyboardState = KeyboardEngine.initialState(kb)
        val typed = StringBuilder()
        fun tap(find: (KeyDef) -> Boolean) {
            val key = KeyboardEngine.currentLayer(kb, state).rows.flatMap { it.keys }.first(find)
            val result = KeyboardEngine.press(kb, state, key, 0)
            state = result.state
            result.effects.filterIsInstance<Effect.Commit>().forEach { typed.append(it.text) }
        }
        tap { it.label == "a" }
        tap { it.action == KeyAction.SwitchLayer("pad") }
        tap { it.label == "2" }
        tap { it.label == "1" }
        tap { it.action == KeyAction.SwitchLayer("letters") }
        tap { it.label == "b" }
        assertEquals("a21b", typed.toString())
    }
}
