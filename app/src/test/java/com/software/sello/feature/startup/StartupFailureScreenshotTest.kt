package com.software.sello.feature.startup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What the start-up failure screen looks like, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupFailureScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(look: Look) {
        rule.showSello(look) { StartupFailureScreen(StartupFailureState(), {}) }
        rule.captureStill("src/test/screenshots/startup-failure-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
