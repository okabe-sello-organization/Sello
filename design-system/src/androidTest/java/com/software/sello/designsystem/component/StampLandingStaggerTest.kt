package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two stamps earned by one aporte land one after the other, as on the v2 celebrations
 * page ("both stamps land one after the other"): while the first comes down, the second
 * is not drawn yet; then both are there. Reduced motion shows both at once.
 */
@RunWith(AndroidJUnit4::class)
class StampLandingStaggerTest {
    @get:Rule
    val rule = createComposeRule()

    private var paper = Color.Unspecified

    private val quarter = MilestoneSpec("25 %", "25 por ciento, logrado", MilestoneState.Earned)
    private val half = MilestoneSpec("50 %", "50 por ciento, logrado", MilestoneState.Earned)

    private fun show(reducedMotion: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SelloTheme(reducedMotion = reducedMotion) {
                paper = SelloTheme.colors.paper
                Box(Modifier.background(SelloTheme.colors.desk)) {
                    StampTicket(
                        listOf(quarter, half),
                        "Ganaste los sellos del 25 % y del 50 %",
                        "Curso de inglés · faltan 1.400.000",
                        land = true
                    )
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    /** How many pixels of the stamp said as [description] differ from the paper. */
    private fun drawn(description: String): Int {
        val pixels = rule.onNodeWithContentDescription(description).captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                if (abs(pixel.red - paper.red) + abs(pixel.green - paper.green) +
                    abs(pixel.blue - paper.blue) > 0.15f
                ) {
                    count++
                }
            }
        }
        return count
    }

    @Test
    fun theSecondStampWaitsWhileTheFirstLandsThenBothAreThere() {
        show(reducedMotion = false)

        rule.mainClock.advanceTimeBy(100)
        val first = drawn("25 por ciento, logrado")
        val second = drawn("50 por ciento, logrado")
        assertTrue("first $first", first > 0)
        assertEquals("second", 0, second)

        rule.mainClock.advanceTimeBy(2_000)
        assertTrue(drawn("25 por ciento, logrado") > 40)
        assertTrue(drawn("50 por ciento, logrado") > 40)
    }

    @Test
    fun withReducedMotionBothStampsAreThereAtOnce() {
        show(reducedMotion = true)

        assertTrue(drawn("25 por ciento, logrado") > 40)
        assertTrue(drawn("50 por ciento, logrado") > 40)
    }
}
