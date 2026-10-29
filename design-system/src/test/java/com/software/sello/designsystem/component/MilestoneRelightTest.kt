package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Back above a kept stamp, the faded stamp lights up again in place (v2 celebrations,
 * "De vuelta en el 50 %"): it starts in the kept ink and ends in the earned green, and
 * reduced motion shows it lit at once. It is said as earned throughout.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MilestoneRelightTest {
    @get:Rule
    val rule = createComposeRule()

    private var gain = Color.Unspecified

    private val earned = MilestoneSpec("50 %", "50 por ciento, logrado", MilestoneState.Earned)

    private fun show(reducedMotion: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SelloTheme(reducedMotion = reducedMotion) {
                gain = SelloTheme.colors.semantic.gain
                Box(Modifier.background(SelloTheme.colors.paper).testTag("place")) {
                    MilestonePlace(earned, relight = true)
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    /** How many pixels are drawn in the full earned green. */
    private fun litInk(): Int {
        val pixels = rule.onNodeWithTag("place").captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                if (abs(pixel.red - gain.red) < 0.05f && abs(pixel.green - gain.green) < 0.05f &&
                    abs(pixel.blue - gain.blue) < 0.05f
                ) {
                    count++
                }
            }
        }
        return count
    }

    @Test
    fun aKeptStampLightsUpAgainInPlace() {
        show(reducedMotion = false)

        assertEquals(0, litInk())
        rule.onNodeWithContentDescription("50 por ciento, logrado").assertExists()
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue(litInk() > 40)
    }

    @Test
    fun withReducedMotionItIsLitAtOnce() {
        show(reducedMotion = true)

        assertTrue(litInk() > 40)
    }
}
