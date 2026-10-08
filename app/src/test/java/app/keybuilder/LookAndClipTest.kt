package app.keybuilder

import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.model.COLOR_SOURCES
import app.keybuilder.model.GRADIENT_DIRECTIONS
import app.keybuilder.model.LayoutFormatException
import app.keybuilder.model.LayoutJson
import app.keybuilder.model.MAX_BLUR
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.blurInUse
import app.keybuilder.model.isScreenshot
import app.keybuilder.model.withBackgroundBlur
import app.keybuilder.model.PhoneColors
import app.keybuilder.model.TOOLBAR_HEIGHT_DP
import app.keybuilder.model.Theme
import app.keybuilder.model.ThemePresets
import app.keybuilder.model.colorAlpha
import app.keybuilder.model.gradientDirectionName
import app.keybuilder.model.opacityPercent
import app.keybuilder.model.parseHexColor
import app.keybuilder.model.quickClipLabel
import app.keybuilder.model.solid
import app.keybuilder.model.withBackgroundOpacity
import app.keybuilder.model.withGradient
import app.keybuilder.model.withKeyOpacity
import app.keybuilder.model.withLook
import app.keybuilder.model.withOpacity
import app.keybuilder.model.withPhoneColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** See-through and two-colour backgrounds, the phone's colours, and the last-copied button. */
class LookAndClipTest {

    private val qwerty = LayoutJson.parse(File("src/main/assets/layouts/qwerty.json").readText())

    // ------------------------------------------------------------------ see-through

    @Test
    fun opacityMakesAColourMoreSeeThroughWithoutChangingIt() {
        assertEquals(0x80112233.toInt(), withOpacity(0xFF112233.toInt(), 0.5f))
        assertEquals(0xFF112233.toInt(), withOpacity(0xFF112233.toInt(), 1f))
        assertEquals(0x00112233, withOpacity(0xFF112233.toInt(), 0f))
        // A colour that was already half see-through becomes a quarter.
        assertEquals(0.25f, colorAlpha(withOpacity(0x80FFFFFF.toInt(), 0.5f)), 0.01f)
        // Out-of-range values are treated as fully solid or fully clear.
        assertEquals(0xFF112233.toInt(), withOpacity(0xFF112233.toInt(), 7f))
        assertEquals(0xFF112233.toInt(), solid(0x20112233))
    }

    @Test
    fun opacityIsSetInStepsOfFivePercent() {
        assertEquals(0.5f, Theme().withBackgroundOpacity(52f).backgroundOpacity, 0.001f)
        assertEquals(0.55f, Theme().withBackgroundOpacity(53f).backgroundOpacity, 0.001f)
        assertEquals(0f, Theme().withKeyOpacity(-20f).keyOpacity, 0.001f)
        assertEquals(1f, Theme().withKeyOpacity(400f).keyOpacity, 0.001f)
        assertEquals(45, opacityPercent(0.45f))
        assertEquals(100, opacityPercent(1f))
    }

    // ------------------------------------------------------------------ blur

    @Test
    fun blurOnlyCountsWhileTheBackgroundIsSeeThrough() {
        assertEquals(0f, Theme(backgroundBlur = 30f).blurInUse(), 0f)                       // solid: nothing to blur
        assertEquals(30f, Theme(backgroundBlur = 30f, backgroundOpacity = 0.5f).blurInUse(), 0f)
        assertEquals(0f, Theme(backgroundOpacity = 0.5f).blurInUse(), 0f)
        assertEquals(MAX_BLUR, Theme(backgroundBlur = 999f, backgroundOpacity = 0.5f).blurInUse(), 0f)
    }

    @Test
    fun turningBlurOnMakesASolidBackgroundSeeThrough() {
        val on = Theme().withBackgroundBlur(24.4f)
        assertEquals(24f, on.backgroundBlur, 0f)
        assertTrue(on.backgroundOpacity < 1f)
        // An opacity you already chose is kept.
        assertEquals(0.3f, Theme(backgroundOpacity = 0.3f).withBackgroundBlur(10f).backgroundOpacity, 0f)
        // Turning it off again changes nothing else.
        assertEquals(0.3f, Theme(backgroundOpacity = 0.3f, backgroundBlur = 10f).withBackgroundBlur(0f).backgroundOpacity, 0f)
        assertEquals(MAX_BLUR, Theme().withBackgroundBlur(500f).backgroundBlur, 0f)
        assertEquals(0f, Theme().withBackgroundBlur(-3f).backgroundBlur, 0f)
    }

    @Test
    fun blurIsSavedAndThereIsAFrostedLook() {
        val theme = qwerty.theme.copy(backgroundOpacity = 0.5f, backgroundBlur = 22f)
        assertEquals(theme, LayoutJson.parse(LayoutJson.toJson(qwerty.copy(theme = theme))).theme)
        assertEquals(0f, qwerty.theme.backgroundBlur, 0f)
        val frost = ThemePresets.all.first { it.name == "Frost" }.theme
        assertTrue(frost.blurInUse() > 0f)
        assertEquals(frost.backgroundBlur, Theme().withLook(frost).backgroundBlur, 0f)
        assertEquals(0f, Theme().withLook(frost).withLook(Theme()).backgroundBlur, 0f)
    }

    // ------------------------------------------------------------------ vibration

    @Test
    fun strongerVibrationIsLongerAndHarder() {
        assertNull(KeyboardEngine.buzzFor(0))
        assertNull(KeyboardEngine.buzzFor(-5))
        val light = KeyboardEngine.buzzFor(10)!!
        val medium = KeyboardEngine.buzzFor(50)!!
        val strong = KeyboardEngine.buzzFor(100)!!
        assertTrue(light.milliseconds < medium.milliseconds && medium.milliseconds < strong.milliseconds)
        assertTrue(light.amplitude < medium.amplitude && medium.amplitude < strong.amplitude)
        assertEquals(255, strong.amplitude)
        assertTrue(light.milliseconds >= 5 && strong.milliseconds <= 40)
        assertEquals(strong, KeyboardEngine.buzzFor(900))
        for (s in 1..100) assertTrue(KeyboardEngine.buzzFor(s)!!.amplitude in 1..255)
    }

    // ------------------------------------------------------------------ searching inside a panel

    @Test
    fun aPanelGrowsSoItStaysVisibleAboveTheKeysWhileSearching() {
        assertEquals(260f, KeyboardEngine.searchPanelExtra(0f, 500f), 0f)
        assertEquals(400f, KeyboardEngine.searchPanelExtra(400f, 500f), 0f)   // already dragged taller
        assertEquals(120f, KeyboardEngine.searchPanelExtra(0f, 120f), 0f)     // a small screen: as tall as it gets
        assertEquals(0f, KeyboardEngine.searchPanelExtra(0f, -10f), 0f)
    }

    // ------------------------------------------------------------------ screenshots

    @Test
    fun screenshotsAreToldFromOtherPictures() {
        assertTrue(isScreenshot("DCIM/Screenshots/", "Screenshot_20261008_163200_Chrome.jpg"))
        assertTrue(isScreenshot("Pictures/Screenshots/", "IMG_0001.png"))
        assertTrue(isScreenshot(null, "Screenshot_1.png"))
        assertTrue(isScreenshot("/storage/emulated/0/DCIM/Screen_shots/a.png", null))
        assertFalse(isScreenshot("DCIM/Camera/", "20261008_163200.jpg"))
        assertFalse(isScreenshot("Download/", "invoice.png"))
        assertFalse(isScreenshot(null, null))
    }

    @Test
    fun aScreenshotIsNotAddedToTheClipboardTwice() {
        val history = ClipHistory().addImage("shot_1.png", "image/png", 1L)
        assertTrue(history.hasImage("shot_1.png"))
        assertFalse(history.hasImage("shot_2.png"))
    }

    // ------------------------------------------------------------------ two colours

    @Test
    fun switchingTheGradientOnPicksAVisibleSecondColour() {
        val plain = Theme()
        assertNull(plain.backgroundEnd)
        val on = plain.withGradient(true)
        assertEquals(plain.accentColor, on.backgroundEnd)
        // Already on: the colour you chose is kept.
        assertEquals("#FF0000", on.copy(backgroundEnd = "#FF0000").withGradient(true).backgroundEnd)
        assertNull(on.withGradient(false).backgroundEnd)
        // If the accent is the same as the background, something else is found.
        val same = Theme(background = "#4C8DFF", accentColor = "#4C8DFF").withGradient(true)
        assertNotEquals(parseHexColor("#4C8DFF"), parseHexColor(same.backgroundEnd!!))
    }

    @Test
    fun everyDirectionHasAName() {
        assertEquals(GRADIENT_DIRECTIONS.size, GRADIENT_DIRECTIONS.map { gradientDirectionName(it) }.toSet().size)
    }

    // ------------------------------------------------------------------ the phone's colours

    @Test
    fun phoneColoursReplaceTheColoursAndNothingElse() {
        val mine = Theme(cornerRadiusDp = 20f, keyHeightDp = 60f, keyOpacity = 0.5f, backgroundOpacity = 0.7f, bold = true)
        val phone = PhoneColors.plain(dark = false)
        val worn = mine.withPhoneColors(phone)
        assertEquals(phone.background, parseHexColor(worn.background))
        assertEquals(phone.key, parseHexColor(worn.keyColor))
        assertEquals(phone.text, parseHexColor(worn.textColor))
        assertEquals(phone.accent, parseHexColor(worn.accentColor))
        assertEquals(20f, worn.cornerRadiusDp, 0f)
        assertEquals(60f, worn.keyHeightDp, 0f)
        assertEquals(0.5f, worn.keyOpacity, 0f)
        assertEquals(0.7f, worn.backgroundOpacity, 0f)
        assertTrue(worn.bold)
        // No gradient unless you had one.
        assertNull(worn.backgroundEnd)
        assertEquals(phone.backgroundEnd, parseHexColor(mine.withGradient(true).withPhoneColors(phone).backgroundEnd!!))
    }

    @Test
    fun aBorderStaysVisibleOnThePhoneColours() {
        val bordered = Theme(borderWidthDp = 1.5f, borderColor = "#66FFFFFF")
        assertEquals("#66000000", bordered.withPhoneColors(PhoneColors.plain(dark = false)).borderColor)
        assertEquals("#66FFFFFF", bordered.withPhoneColors(PhoneColors.plain(dark = true)).borderColor)
    }

    @Test
    fun darkAndLightPhoneColoursAreReadable() {
        for (dark in listOf(true, false)) {
            val c = PhoneColors.plain(dark)
            assertEquals(dark, app.keybuilder.model.isDarkColor(c.background))
            assertNotEquals(dark, app.keybuilder.model.isDarkColor(c.text))
        }
    }

    // ------------------------------------------------------------------ ready-made looks

    @Test
    fun choosingALookTakesItsBackgroundAndSwitchesBackToYourOwnColours() {
        val phone = Theme(colorSource = "phone", keyHeightDp = 64f)
        val dusk = ThemePresets.all.first { it.name == "Dusk" }.theme
        val chosen = phone.withLook(dusk)
        assertEquals("own", chosen.colorSource)
        assertEquals(dusk.backgroundEnd, chosen.backgroundEnd)
        assertEquals(dusk.keyOpacity, chosen.keyOpacity, 0f)
        assertEquals(64f, chosen.keyHeightDp, 0f)
        // A plain look removes the gradient and the see-through again.
        val paper = chosen.withLook(ThemePresets.all.first { it.name == "Paper" }.theme)
        assertNull(paper.backgroundEnd)
        assertEquals(1f, paper.keyOpacity, 0f)
        assertEquals(1f, paper.backgroundOpacity, 0f)
    }

    @Test
    fun thereAreGradientAndSeeThroughLooksToStartFrom() {
        assertTrue(ThemePresets.all.any { it.theme.backgroundEnd != null })
        assertTrue(ThemePresets.all.any { it.theme.backgroundOpacity < 1f })
        for (preset in ThemePresets.all) {
            assertTrue(preset.name, parseHexColor(preset.theme.background) != null)
            preset.theme.backgroundEnd?.let { assertTrue(preset.name, parseHexColor(it) != null) }
            assertTrue(preset.name, preset.theme.gradientDirection in GRADIENT_DIRECTIONS)
        }
    }

    // ------------------------------------------------------------------ the layout file

    @Test
    fun theNewLookSettingsSurviveSavingAndLoading() {
        val theme = qwerty.theme.copy(
            backgroundEnd = "#C2416B", gradientDirection = "diagonal",
            backgroundOpacity = 0.45f, keyOpacity = 0.2f, colorSource = "phone",
        )
        val back = LayoutJson.parse(LayoutJson.toJson(qwerty.copy(theme = theme)))
        assertEquals(theme, back.theme)
        // And a plain theme stays plain.
        assertEquals(qwerty.theme, LayoutJson.parse(LayoutJson.toJson(qwerty)).theme)
        assertNull(qwerty.theme.backgroundEnd)
        assertEquals("own", qwerty.theme.colorSource)
    }

    @Test
    fun aLayoutFileFromBeforeTheseSettingsStillLoads() {
        val old = """{"id":"a","name":"A","startLayer":"l","theme":{"background":"#101010"},"layers":[{"id":"l","rows":[["a"]]}]}"""
        val theme = LayoutJson.parse(old).theme
        assertEquals(1f, theme.backgroundOpacity, 0f)
        assertEquals(1f, theme.keyOpacity, 0f)
        assertNull(theme.backgroundEnd)
        assertEquals("down", theme.gradientDirection)
    }

    @Test
    fun wrongLookSettingsAreExplainedOrClamped() {
        fun layout(theme: String) =
            """{"id":"a","name":"A","startLayer":"l","theme":{$theme},"layers":[{"id":"l","rows":[["a"]]}]}"""
        assertEquals(1f, LayoutJson.parse(layout("\"backgroundOpacity\": 9")).theme.backgroundOpacity, 0f)
        assertEquals(0f, LayoutJson.parse(layout("\"keyOpacity\": -1")).theme.keyOpacity, 0f)
        for ((bad, mention) in listOf(
            "\"gradient\": \"sideways\"" to "gradient",
            "\"colors\": \"rainbow\"" to "colors",
            "\"backgroundEnd\": \"pink\"" to "backgroundEnd",
        )) {
            try {
                LayoutJson.parse(layout(bad))
                fail("expected a problem for $bad")
            } catch (e: LayoutFormatException) {
                assertTrue(e.message, e.message!!.contains(mention))
            }
        }
        assertEquals(listOf("own", "phone"), COLOR_SOURCES)
    }

    // ------------------------------------------------------------------ the strip

    @Test
    fun theKeyboardIsTallerWhenTheStripShowsForSuggestions() {
        val noStrip = qwerty.copy(toolbar = false)
        val state = KeyboardEngine.initialState(noStrip)
        val bare = KeyboardEngine.heightDp(noStrip, state)
        assertEquals(bare + TOOLBAR_HEIGHT_DP, KeyboardEngine.heightDp(noStrip, state, strip = true), 0.001f)
        assertEquals(KeyboardEngine.heightDp(qwerty, state), KeyboardEngine.heightDp(noStrip, state, strip = true), 0.001f)
    }

    @Test
    fun theLastCopiedButtonShowsAShortOneLineLabel() {
        assertEquals("hello", quickClipLabel("hello"))
        assertEquals("two lines", quickClipLabel("  two\n   lines  "))
        val long = quickClipLabel("https://example.com/a/very/long/link")
        assertEquals(16, long.length)
        assertTrue(long.endsWith("…"))
        assertFalse(quickClipLabel("exactly 16 chars").endsWith("…"))
        assertEquals("", quickClipLabel("   "))
    }
}
