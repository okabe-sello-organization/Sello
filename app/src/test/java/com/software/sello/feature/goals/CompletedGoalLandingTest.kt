package com.software.sello.feature.goals

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.captureToImage
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.recibo.stampInk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Revisiting Metas, a completed goal's LOGRADA comes down as its row appears (v2
 * celebrations). It is a revisit, not a new result: no haptic, and reduced motion shows
 * the stamp already at rest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompletedGoalLandingTest {
    @get:Rule
    val rule = createComposeRule()

    private var haptics = 0
    private var gain = Color.Unspecified

    private fun show(land: Boolean, reducedMotion: Boolean = false) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val counting = object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics++
                }
            }
            CompositionLocalProvider(LocalHapticFeedback provides counting) {
                SelloTheme(reducedMotion = reducedMotion) {
                    gain = SelloTheme.colors.semantic.gain
                    CompletedGoalRow(
                        "Vacunas de Luna",
                        "400.000, 30 sep",
                        SelloIcon.Pets,
                        "Lograda",
                        {},
                        Modifier.testTag("row"),
                        land = land
                    )
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    /** How many pixels of the row are drawn in the stamp's green ink. */
    private fun stampInk(): Int {
        val pixels = rule.onNodeWithTag("row").captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                if (abs(pixel.red - gain.red) < 0.06f &&
                    abs(pixel.green - gain.green) < 0.06f &&
                    abs(pixel.blue - gain.blue) < 0.06f
                ) {
                    count++
                }
            }
        }
        return count
    }

    @Test
    fun aRevisitedRowBringsItsStampDownWithoutAHaptic() {
        show(land = true)

        assertEquals(0, stampInk())
        rule.onNodeWithContentDescription("Lograda", useUnmergedTree = true).assertExists()
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue(stampInk() > 50)
        assertEquals(0, haptics)
    }

    @Test
    fun withoutLandingTheStampIsAlreadyDown() {
        show(land = false)

        assertTrue(stampInk() > 50)
    }

    @Test
    fun withReducedMotionALandingStampIsAlreadyDown() {
        show(land = true, reducedMotion = true)

        assertTrue(stampInk() > 50)
        assertEquals(0, haptics)
    }
}
