package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two parts the goal form adds (v2 create): the goal's icon in a large green circle,
 * an empty dashed one before an icon is chosen, and the Sello pad that slides up over
 * the form with Listo, reporting each key.
 */
@RunWith(AndroidJUnit4::class)
class GoalFormPartsTest {
    @get:Rule
    val rule = createComposeRule()

    private var gain = Color.Unspecified
    private var paper = Color.Unspecified

    private fun near(expected: Color, actual: Color) = abs(expected.red - actual.red) < 0.03f &&
        abs(expected.green - actual.green) < 0.03f &&
        abs(expected.blue - actual.blue) < 0.03f

    private fun circle(icon: SelloIcon?) {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                gain = SelloTheme.colors.semantic.gain
                paper = SelloTheme.colors.paper
                Box(Modifier.requiredSize(100.dp)) {
                    GoalIconCircle(icon, Modifier.align(Alignment.Center).testTag("circle"))
                }
            }
        }
    }

    /** The colour just inside the circle's left edge, clear of the icon in the middle. */
    private fun edgeFill(): Color {
        val pixels = rule.onNodeWithTag("circle").captureToImage().toPixelMap()
        return pixels[pixels.width / 6, pixels.height / 2]
    }

    @Test
    fun aChosenIconSitsInAFilledGreenCircleOfAtLeastFiftySixDp() {
        circle(SelloIcon.Flight)

        assertTrue("fill ${edgeFill()}", near(gain, edgeFill()))
        val bounds = rule.onNodeWithTag("circle").getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 56.dp)
    }

    @Test
    fun beforeAnIconIsChosenTheCircleIsEmpty() {
        circle(null)

        assertTrue("fill ${edgeFill()}", !near(gain, edgeFill()))
    }

    private val keys = mutableListOf<String>()
    private var done = 0

    private fun pad(visible: Boolean, reducedMotion: Boolean) {
        var shown by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloTheme(reducedMotion = reducedMotion) {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) {
                        Box(Modifier.fillMaxSize()) { Text("formulario") }
                        SlidingKeypad(
                            visible = shown,
                            doneLabel = "Listo",
                            onDone = { done++ },
                            onDigit = { keys += "$it" },
                            onTripleZero = { keys += "000" },
                            onBackspace = { keys += "<" },
                            onClearAll = { keys += "clear" },
                            modifier = Modifier.align(Alignment.BottomCenter).testTag("pad")
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        shown = visible
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theHiddenPadOffersNoKeys() {
        pad(visible = false, reducedMotion = true)

        rule.onNodeWithText("Listo").assertDoesNotExist()
        rule.onNodeWithText("000").assertDoesNotExist()
    }

    @Test
    fun theShownPadReportsEveryKeyAndListo() {
        pad(visible = true, reducedMotion = true)
        rule.mainClock.autoAdvance = true

        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText("000").performClick()
        rule.onNodeWithText("Listo").performClick()

        assertEquals(listOf("2", "000"), keys)
        assertEquals(1, done)
    }

    @Test
    fun itSlidesUpFromTheBottomAndWithReducedMotionIsSimplyThere() {
        pad(visible = true, reducedMotion = false)
        val moving = rule.onNodeWithText("Listo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        rule.mainClock.advanceTimeBy(1_000)
        val settled = rule.onNodeWithText(
            "Listo",
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        assertTrue("$moving starts below $settled", moving.top > settled.top)
        assertTrue("ends inside the window", settled.bottom <= 640.dp)
    }

    @Test
    fun withReducedMotionThePadIsAtRestAtOnce() {
        pad(visible = true, reducedMotion = true)
        val first = rule.onNodeWithText("Listo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        rule.mainClock.advanceTimeBy(1_000)
        val later = rule.onNodeWithText("Listo", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(later.top, first.top)
    }
}
