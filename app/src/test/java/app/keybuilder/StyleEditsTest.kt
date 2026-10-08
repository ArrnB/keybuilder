package app.keybuilder

import app.keybuilder.model.FONT_FAMILIES
import app.keybuilder.model.KeyAction
import app.keybuilder.model.KeyDef
import app.keybuilder.model.KeyLook
import app.keybuilder.model.KeyPos
import app.keybuilder.model.KeyStyle
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.Theme
import app.keybuilder.model.ThemeColor
import app.keybuilder.model.ThemePresets
import app.keybuilder.model.ThemeSize
import app.keybuilder.model.argbToHsv
import app.keybuilder.model.backgroundFor
import app.keybuilder.model.colorAlpha
import app.keybuilder.model.describe
import app.keybuilder.model.formatHexColor
import app.keybuilder.model.formatNumber
import app.keybuilder.model.hsvToArgb
import app.keybuilder.model.isDarkColor
import app.keybuilder.model.keyAt
import app.keybuilder.model.parseHexColor
import app.keybuilder.model.styledKeyCount
import app.keybuilder.model.textColorFor
import app.keybuilder.model.withKeyStyle
import app.keybuilder.model.withLook
import app.keybuilder.model.withoutKeyStyles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StyleEditsTest {

    private val def = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())
    private val q = KeyPos("letters", 0, 0)

    // ---------------------------------------------------------------- single keys

    @Test
    fun findsKeysByPosition() {
        assertEquals("q", def.keyAt(q)!!.label)
        assertEquals(KeyAction.Shift, def.keyAt(KeyPos("letters", 2, 0))!!.action)
        assertEquals("π", def.keyAt(KeyPos("symbols2", 0, 5))!!.label)
        assertNull(def.keyAt(KeyPos("letters", 9, 0)))
        assertNull(def.keyAt(KeyPos("letters", 0, 99)))
        assertNull(def.keyAt(KeyPos("nope", 0, 0)))
    }

    @Test
    fun stylingOneKeyLeavesEveryOtherKeyAlone() {
        val styled = def.withKeyStyle(q, KeyStyle(background = "#FF0000"))
        assertEquals("#FF0000", styled.keyAt(q)!!.style!!.background)
        assertEquals(1, styled.styledKeyCount())
        // Undo the one change and the keyboard is exactly what it was.
        assertEquals(def, styled.withKeyStyle(q, null))
        // The original is never modified.
        assertNull(def.keyAt(q)!!.style)
    }

    @Test
    fun anEmptyStyleCountsAsNoStyle() {
        val styled = def.withKeyStyle(q, KeyStyle(background = "#FF0000"))
        assertNull(styled.withKeyStyle(q, KeyStyle()).keyAt(q)!!.style)
    }

    @Test
    fun stylingAMissingKeyChangesNothing() {
        assertSame(def, def.withKeyStyle(KeyPos("nope", 0, 0), KeyStyle(background = "#FF0000")))
    }

    @Test
    fun clearingRemovesEverySingleKeyStyle() {
        val styled = def
            .withKeyStyle(q, KeyStyle(background = "#FF0000"))
            .withKeyStyle(KeyPos("symbols", 1, 2), KeyStyle(textColor = "#00FF00", fontSizeSp = 30f))
        assertEquals(2, styled.styledKeyCount())
        assertEquals(def, styled.withoutKeyStyles())
    }

    @Test
    fun singleKeyStylesSurviveSavingAndLoading() {
        val styled = def.withKeyStyle(q, KeyStyle(background = "#80FF0000", textColor = "#FFFFFF", fontSizeSp = 26f))
        assertEquals(styled, LayoutJson.parse(LayoutJson.toJson(styled)))
    }

    @Test
    fun keysHaveReadableNames() {
        assertEquals("q", KeyDef(label = "q").describe())
        assertEquals("shift", KeyDef(action = KeyAction.Shift).describe())
        assertEquals("space", KeyDef(action = KeyAction.Space).describe())
        assertEquals("?123", KeyDef(label = "?123", action = KeyAction.SwitchLayer("symbols")).describe())
    }

    @Test
    fun themeColourForEachLook() {
        val t = Theme()
        assertEquals(t.keyColor, t.backgroundFor(KeyLook.NORMAL))
        assertEquals(t.functionKeyColor, t.backgroundFor(KeyLook.FUNCTION))
        assertEquals(t.accentColor, t.backgroundFor(KeyLook.ACCENT))
        assertEquals(t.accentTextColor, t.textColorFor(KeyLook.ACCENT))
        assertEquals(t.textColor, t.textColorFor(KeyLook.FUNCTION))
    }

    // ---------------------------------------------------------------- theme fields

    @Test
    fun everyThemeColourCanBeReadAndChangedOnItsOwn() {
        val base = Theme()
        for (field in ThemeColor.entries) {
            val changed = field.set(base, "#123456")
            assertEquals(field.name, "#123456", field.get(changed))
            // Nothing else moved.
            for (other in ThemeColor.entries) {
                if (other != field) assertEquals("${field.name} changed ${other.name}", other.get(base), other.get(changed))
            }
        }
    }

    @Test
    fun everyThemeSizeCanBeReadAndChangedOnItsOwn() {
        val base = Theme()
        for (field in ThemeSize.entries) {
            val target = field.snap((field.min + field.max) / 2)
            val changed = field.set(base, target)
            assertEquals(field.name, target, field.get(changed))
            for (other in ThemeSize.entries) {
                if (other != field) assertEquals(other.get(base), other.get(changed))
            }
            // Defaults sit inside every slider's range.
            assertTrue(field.name, field.get(base) in field.min..field.max)
        }
    }

    @Test
    fun sizesSnapToStepsAndStayInRange() {
        assertEquals(1.5f, ThemeSize.BORDER.snap(1.4f))
        assertEquals(4f, ThemeSize.BORDER.snap(99f))
        assertEquals(0f, ThemeSize.GAP.snap(-3f))
        assertEquals(52f, ThemeSize.KEY_HEIGHT.snap(52.4f))
    }

    @Test
    fun turningTheBorderOnMakesItVisible() {
        val dark = ThemeSize.BORDER.set(Theme(), 1f)
        assertEquals(1f, dark.borderWidthDp)
        assertEquals("#66FFFFFF", dark.borderColor)

        val light = ThemeSize.BORDER.set(ThemePresets.all.first { it.name == "Paper" }.theme, 1f)
        assertEquals("#66000000", light.borderColor)

        // A border colour that was chosen on purpose is kept.
        val chosen = ThemeSize.BORDER.set(Theme(borderColor = "#FF00FF"), 2f)
        assertEquals("#FF00FF", chosen.borderColor)
    }

    @Test
    fun readyMadeLooksAreValidAndKeepYourSizes() {
        assertTrue(ThemePresets.all.size >= 6)
        assertEquals(ThemePresets.all.size, ThemePresets.all.map { it.name }.toSet().size)
        val mine = Theme(keyHeightDp = 64f, keyGapDp = 2f, fontSizeSp = 28f, fontFamily = "mono", bold = true)
        for (preset in ThemePresets.all) {
            for (field in ThemeColor.entries) {
                assertNotNull("${preset.name} ${field.name}", parseHexColor(field.get(preset.theme)))
            }
            val applied = mine.withLook(preset.theme)
            assertEquals(preset.theme.background, applied.background)
            assertEquals(preset.theme.cornerRadiusDp, applied.cornerRadiusDp)
            assertEquals(64f, applied.keyHeightDp)
            assertEquals(2f, applied.keyGapDp)
            assertEquals(28f, applied.fontSizeSp)
            assertEquals("mono", applied.fontFamily)
            assertTrue(applied.bold)
            // Every look survives a save and load.
            val kb = def.copy(theme = applied)
            assertEquals(kb, LayoutJson.parse(LayoutJson.toJson(kb)))
        }
    }

    @Test
    fun lettersSettingsAreSavedAndChecked() {
        for (family in FONT_FAMILIES) {
            val kb = def.copy(theme = def.theme.copy(fontFamily = family, bold = true))
            assertEquals(kb, LayoutJson.parse(LayoutJson.toJson(kb)))
        }
        // Old files without these fields still load, with the defaults.
        assertEquals("sans", def.theme.fontFamily)
        assertFalse(def.theme.bold)
        try {
            LayoutJson.parse("""{"theme":{"fontFamily":"comic"},"layers":[{"id":"a","rows":[["q"]]}]}""")
            fail("expected a LayoutFormatException")
        } catch (e: LayoutFormatException) {
            assertTrue(e.message, e.message!!.contains("fontFamily"))
        }
        try {
            LayoutJson.parse("""{"theme":{"bold":"yes"},"layers":[{"id":"a","rows":[["q"]]}]}""")
            fail("expected a LayoutFormatException")
        } catch (e: LayoutFormatException) {
            assertTrue(e.message, e.message!!.contains("true or false"))
        }
    }

    @Test
    fun numbersAreShownWithoutPointlessDecimals() {
        assertEquals("8", formatNumber(8f))
        assertEquals("1.5", formatNumber(1.5f))
        assertEquals("0", formatNumber(0f))
    }

    // ---------------------------------------------------------------- colour maths

    @Test
    fun knownColoursConvertCorrectly() {
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), hsvToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), hsvToArgb(200f, 1f, 0f))
        assertEquals(0x80FF0000.toInt(), hsvToArgb(0f, 1f, 1f, alpha = 0.5f))
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(360f, 1f, 1f))

        val yellow = argbToHsv(0xFFFFFF00.toInt())
        assertEquals(60f, yellow.h, 0.01f)
        assertEquals(1f, yellow.s, 0.01f)
        assertEquals(1f, yellow.v, 0.01f)
        assertEquals(300f, argbToHsv(0xFFFF00FF.toInt()).h, 0.01f)
        assertEquals(0f, argbToHsv(0xFF808080.toInt()).s, 0.001f)
    }

    @Test
    fun convertingThereAndBackGivesTheSameColour() {
        val samples = ThemePresets.all.flatMap { p -> ThemeColor.entries.map { parseHexColor(it.get(p.theme))!! } } +
            listOf(0xFF010203.toInt(), 0xFFFEFDFC.toInt(), 0xFF7F8081.toInt(), 0x00000000, 0x33ABCDEF)
        for (argb in samples) {
            val hsv = argbToHsv(argb)
            assertEquals(formatHexColor(argb), argb, hsvToArgb(hsv.h, hsv.s, hsv.v, colorAlpha(argb)))
        }
    }

    @Test
    fun hexTextIsShortForSolidColoursAndReadsBackTheSame() {
        assertEquals("#1B1D22", formatHexColor(0xFF1B1D22.toInt()))
        assertEquals("#80FFFFFF", formatHexColor(0x80FFFFFF.toInt()))
        assertEquals("#00000000", formatHexColor(0))
        assertEquals("#000000", formatHexColor(0xFF000000.toInt()))
        for (argb in listOf(0xFF1B1D22.toInt(), 0x80FFFFFF.toInt(), 0, 0x01020304)) {
            assertEquals(argb, parseHexColor(formatHexColor(argb)))
        }
    }

    @Test
    fun darkAndLightAreToldApart() {
        assertTrue(isDarkColor(0xFF1B1D22.toInt()))
        assertTrue(isDarkColor(0xFF000000.toInt()))
        assertFalse(isDarkColor(0xFFFFFFFF.toInt()))
        assertFalse(isDarkColor(0xFFE8EAED.toInt()))
    }
}
