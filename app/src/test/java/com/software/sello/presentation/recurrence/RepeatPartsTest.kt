package com.software.sello.presentation.recurrence

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.feature.recibo.text
import com.software.sello.inEveryScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The repeat sheet's day grid, interval stepper and actions keep their contracts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepeatPartsTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun days(): List<DpRect> = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        .map { node ->
            with(rule.density) {
                val box = node.boundsInRoot
                DpRect(box.left.toDp(), box.top.toDp(), box.right.toDp(), box.bottom.toDp())
            }
        }

    private fun assertTargets(scheme: String, boxes: List<DpRect>) = boxes.forEach {
        assertTrue("$scheme $it", it.width >= 48.dp && it.height >= 48.dp)
    }

    @Test
    fun everyDayIsOneToggleReadAsItsDayWithWhetherItIsChosen() {
        val toggled = mutableListOf<Int>()
        rule.inEveryScheme(reset = { toggled.clear() }, content = {
            DayGrid(setOf(5, 30), { toggled += it }, { "Día $it" })
        }) { scheme ->
            assertEquals(scheme, 31, rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
            rule.onNodeWithContentDescription("Día 5").assert(isCheckbox).assertIsOn()
            rule.onNodeWithContentDescription("Día 30").assertIsOn()
            rule.onNodeWithContentDescription("Día 6").assertIsOff().performClick()
            rule.onNodeWithContentDescription("Día 5").performClick()
            assertEquals(scheme, listOf(6, 5), toggled)
            // The number is not read a second time after the description.
            val day = rule.onNodeWithContentDescription("Día 31").fetchSemanticsNode()
            assertEquals(scheme, null, day.config.getOrNull(SemanticsProperties.Text))
            val boxes = days()
            assertTargets(scheme, boxes)
            // Rows of seven where seven 48dp targets fit; fewer on a narrower screen.
            val width = boxes.maxOf { it.right } - boxes.minOf { it.left }
            val fits = minOf(7, (width.value / 48f).toInt())
            assertEquals(scheme, fits, boxes.map { it.left }.distinct().size)
        }
    }

    @Test
    fun aDayGridTurnedOffIgnoresTaps() {
        var toggles = 0
        rule.inEveryScheme(reset = { toggles = 0 }, content = {
            DayGrid(setOf(5), { toggles++ }, { "Día $it" }, enabled = false)
        }) { scheme ->
            rule.onNodeWithContentDescription("Día 6").assertIsNotEnabled().performClick()
            assertEquals(scheme, 0, toggles)
        }
    }

    @Test
    fun atTwiceTheFontSizeTheDaysTakeFourColumnsWithinANarrowScreen() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            DayGrid(setOf(15, 30), {}, { "Día $it" })
        }) { scheme ->
            val boxes = days()
            assertEquals(scheme, 31, boxes.size)
            assertEquals(scheme, 4, boxes.map { it.left }.distinct().size)
            assertTrue(scheme, boxes.all { it.right <= 320.dp })
            assertTargets(scheme, boxes)
        }
    }

    @Test
    fun whereSevenTargetsDoNotFitTheRowHoldsFewerAndKeepsThemAt48dp() {
        rule.inEveryScheme(width = 320.dp, content = {
            DayGrid(setOf(1), {}, { "Día $it" })
        }) { scheme ->
            val boxes = days()
            assertEquals(scheme, 6, boxes.map { it.left }.distinct().size)
            assertTargets(scheme, boxes)
        }
    }

    @Test
    fun theStepperReadsItsValueWithTheUnitAndStopsAtItsBounds() {
        var value by mutableIntStateOf(1)
        var increases = 0
        var decreases = 0
        rule.inEveryScheme(reset = {
            value = 1
            increases = 0
            decreases = 0
        }, content = {
            IntervalStepper(
                value = value,
                unit = if (value == 1) "semana" else "semanas",
                onDecrease = { decreases++ },
                onIncrease = {
                    increases++
                    value++
                },
                decreaseLabel = "Menos",
                increaseLabel = "Más",
                canDecrease = value > 1,
                canIncrease = value < 52
            )
        }) { scheme ->
            rule.onNodeWithContentDescription("Menos").assert(isButton).assertIsNotEnabled()
                .performClick()
            assertEquals(scheme, 0, decreases)
            val read = rule.onNodeWithContentDescription("1 semana").fetchSemanticsNode()
            assertEquals(
                scheme,
                LiveRegionMode.Polite,
                read.config.getOrNull(SemanticsProperties.LiveRegion)
            )
            rule.onNodeWithContentDescription("Más").assert(isButton).assertIsEnabled()
                .performClick()
            assertEquals(scheme, 1, increases)
            rule.onNodeWithContentDescription("2 semanas").assertIsDisplayed()
            rule.onNodeWithContentDescription("Menos").assertIsEnabled()
            listOf("Menos", "Más").forEach {
                val box = rule.onNodeWithContentDescription(it).getUnclippedBoundsInRoot()
                assertTrue("$scheme $it", box.width >= 48.dp && box.height >= 48.dp)
            }
        }
    }

    @Test
    fun theTopStepIsOff() {
        var increases = 0
        rule.inEveryScheme(reset = { increases = 0 }, content = {
            IntervalStepper(52, "semanas", {}, { increases++ }, "Menos", "Más", canIncrease = false)
        }) { scheme ->
            rule.onNodeWithContentDescription("Más").assertIsNotEnabled().performClick()
            assertEquals(scheme, 0, increases)
            rule.onNodeWithContentDescription("Menos").assertIsEnabled()
        }
    }

    @Test
    fun atTwiceTheFontSizeTheSheetActionsStandOneAboveTheOther() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            RepeatSheetActions("Quitar", {}, "Listo", {})
        }) { scheme ->
            val dismiss = rule.onNodeWithText("Quitar").getUnclippedBoundsInRoot()
            val confirm = rule.onNodeWithText("Listo").getUnclippedBoundsInRoot()
            assertTrue("$scheme $dismiss $confirm", confirm.top >= dismiss.bottom)
            assertTrue(scheme, confirm.right <= 320.dp && dismiss.right <= 320.dp)
        }
    }

    @Test
    fun theSheetActionsShareARowAThirdAndTwoThirds() {
        var quit = 0
        var done = 0
        rule.inEveryScheme(reset = {
            quit = 0
            done = 0
        }, content = {
            RepeatSheetActions("Quitar", { quit++ }, "Listo", { done++ })
        }) { scheme ->
            val dismiss = rule.onNodeWithText("Quitar").getUnclippedBoundsInRoot()
            val confirm = rule.onNodeWithText("Listo").getUnclippedBoundsInRoot()
            assertEquals(scheme, dismiss.top, confirm.top)
            assertTrue("$scheme $dismiss $confirm", confirm.width > dismiss.width * 1.8f)
            rule.onNodeWithText("Quitar").performClick()
            rule.onNodeWithText("Listo").performClick()
            assertEquals(scheme, 1 to 1, quit to done)
        }
    }

    private var dismissed = 0

    private fun showSheet(fontScale: Float = 1f, height: Float = 640f) {
        dismissed = 0
        rule.inEveryScheme(fontScale = fontScale, reset = { dismissed = 0 }, content = {
            Box(Modifier.height(height.dp)) {
                RepeatSheet("Se repite", onDismiss = { dismissed++ }) {
                    DayGrid(setOf(5), {}, { "Día $it" })
                    IntervalStepper(1, "semana", {}, {}, "Menos", "Más")
                    RepeatNext("Próximo: lun 5 nov. Se anota solo en su fecha.")
                    RepeatSheetActions("Quitar", {}, "Listo", {})
                }
            }
        }) { scheme -> check(scheme) }
    }

    private var check: (String) -> Unit = {}

    @Test
    fun theSheetTitleIsAHeadingAndATapOnTheScrimClosesIt() {
        check = { scheme ->
            rule.onNode(isHeading()).assertIsDisplayed()
            assertEquals(scheme, "Se repite", headingText())
            // A tap on the paper leaves it open; one above it, on the scrim, closes it.
            rule.onNodeWithText("Se repite").performClick()
            assertEquals(scheme, 0, dismissed)
            rule.onRoot().performTouchInput { click(Offset(centerX, 4f)) }
            assertEquals(scheme, 1, dismissed)
        }
        showSheet()
    }

    private fun headingText() = rule.onNode(isHeading()).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    @Test
    fun atTwiceTheFontSizeTheSheetScrollsToItsLastAction() {
        check = { scheme ->
            assertTrue(
                scheme,
                rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
            )
            rule.onNodeWithText("Listo").performScrollTo().assertIsDisplayed().performClick()
            rule.onNode(isHeading()).assertExists()
            assertEquals(scheme, 0, dismissed)
        }
        showSheet(fontScale = 2f, height = 560f)
    }
}
