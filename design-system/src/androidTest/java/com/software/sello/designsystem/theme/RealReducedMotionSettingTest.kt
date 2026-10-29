package com.software.sello.designsystem.theme

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme follows the device's real "remove animations" setting while it is on screen,
 * and a value passed to the theme always wins. Changing the setting needs the device's
 * shell; the rest of the theme's reduced-motion behaviour is tested on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class RealReducedMotionSettingTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shell(command: String): String {
        val out = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(out).bufferedReader().readText().trim()
    }

    private var systemBefore = "1"

    @Before
    fun rememberTheDevicesSetting() {
        systemBefore = shell("settings get global animator_duration_scale")
    }

    @After
    fun putTheDevicesSettingBack() {
        shell("settings put global animator_duration_scale $systemBefore")
    }

    @Test
    fun theRealSystemSettingIsFollowedWhileTheThemeIsOnScreen() {
        shell("settings put global animator_duration_scale 1")
        var reduced: Boolean? = null
        rule.setContent { SelloTheme { reduced = SelloTheme.reducedMotion } }
        rule.waitForIdle()
        val atFirst = reduced

        shell("settings put global animator_duration_scale 0")
        rule.waitUntil(10_000) { reduced == true }
        shell("settings put global animator_duration_scale 1")
        rule.waitUntil(10_000) { reduced == false }

        assertEquals(false, atFirst)
    }

    @Test
    fun aValueGivenToTheThemeWinsOverTheSystemSettingWhateverItBecomes() {
        shell("settings put global animator_duration_scale 0")
        var full: Boolean? = null
        var reduced: Boolean? = null
        rule.setContent {
            SelloTheme(reducedMotion = false) { full = SelloTheme.reducedMotion }
            SelloTheme(reducedMotion = true) { reduced = SelloTheme.reducedMotion }
        }
        rule.waitForIdle()
        val systemOff = full to reduced

        shell("settings put global animator_duration_scale 1")
        rule.waitForIdle()

        assertEquals(false to true, systemOff)
        assertEquals(false to true, full to reduced)
    }
}
