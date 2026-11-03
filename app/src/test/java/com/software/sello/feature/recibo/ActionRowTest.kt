package com.software.sello.feature.recibo

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A line with actions opens on a press and offers them by long press, by name and by swipe. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionRowTest {
    // Launched work waits its turn, as on a device, so drags race with their own end.
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    private val done = mutableListOf<String>()

    private fun line(swipe: Boolean = true, direction: LayoutDirection = LayoutDirection.Ltr) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                SelloTheme {
                    ActionRow(
                        onClick = { done += "open" },
                        actions = listOf(
                            LineAction("Editar") { done += "edit" },
                            LineAction("Borrar") { done += "delete" }
                        ),
                        modifier = Modifier.testTag("line"),
                        swipeAway = LineAction("Borrar") { done += "swiped" }.takeIf { swipe }
                    ) { pressable ->
                        LeaderLine("Pan", modifier = pressable, caption = "Hoy, 22 oct") {
                            Text("48.700")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun pressingTheLineOnlyOpensIt() {
        line()

        rule.onNodeWithTag("line").performClick()

        assertEquals(listOf("open"), done)
        assertTrue(rule.onNodeWithTag("line").getUnclippedBoundsInRoot().height >= 48.dp)
    }

    @Test
    fun aLongPressOffersEveryActionInAMenuAndTheChosenOneRunsOnce() {
        line()

        rule.onNodeWithTag("line").performTouchInput { longClick() }
        rule.onNodeWithText("Editar").assertIsDisplayed()
        rule.onNodeWithText("Borrar").assertIsDisplayed().performClick()

        assertEquals(listOf("delete"), done)
        // Choosing closes the menu.
        rule.onAllNodes(hasTestTag(ACTION_LINE_MENU_TAG)).assertCountEquals(0)
    }

    @Test
    fun aScreenReaderIsOfferedTheSameActionsByNameWithoutAnyGesture() {
        line()

        val node = rule.onNodeWithTag("line").fetchSemanticsNode()
        val offered = node.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Editar", "Borrar"), offered.map { it.label })
        rule.runOnUiThread { offered.single { it.label == "Borrar" }.action() }

        assertEquals(listOf("delete"), done)
        // Read as one thing: what it is, when, and how much.
        val text = node.config[SemanticsProperties.Text].joinToString { it.text } +
            node.children.joinToString {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString()
            }
        assertTrue(text, "Pan" in text)
    }

    @Test
    fun swipingTheLineAwayPastTheThresholdRunsTheSwipeActionOnce() {
        line()

        rule.onNodeWithTag("line").performTouchInput {
            swipeLeft(
                startX = right - 4f,
                endX =
                    left + 4f
            )
        }
        rule.waitForIdle()

        assertEquals(listOf("swiped"), done)
    }

    @Test
    fun aShortSwipeOrOneTheOtherWayDoesNothing() {
        line()

        rule.onNodeWithTag("line").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * 0.15f)
        }
        rule.onNodeWithTag("line").performTouchInput {
            swipeRight(
                startX = left + 4f,
                endX =
                    right - 4f
            )
        }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun aLineLetGoBeforeTheThresholdGoesBackToItsPlace() {
        line()
        val start = rule.onNodeWithTag("line").getUnclippedBoundsInRoot().left

        // Moves arrive faster than frames, as they do from a real finger.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("line").performTouchInput {
            down(centerRight - Offset(4f, 0f))
            repeat(10) { moveBy(Offset(-width * 0.025f, 0f), delayMillis = 0) }
            up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(start, rule.onNodeWithTag("line").getUnclippedBoundsInRoot().left)
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun aSwipePastTheThresholdThatIsCancelledOnlyGoesBack() {
        line()
        val start = rule.onNodeWithTag("line").getUnclippedBoundsInRoot().left

        // The system takes the gesture, as a pulled-down shade or a call would.
        rule.onNodeWithTag("line").performTouchInput {
            down(centerRight - Offset(4f, 0f))
            moveBy(Offset(-width * 0.8f, 0f))
            cancel()
        }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), done)
        assertEquals(start, rule.onNodeWithTag("line").getUnclippedBoundsInRoot().left)
    }

    @Test
    fun rightToLeftTheLineIsSwipedAwayTowardsItsStartOnTheRight() {
        line(direction = LayoutDirection.Rtl)

        rule.onNodeWithTag("line").performTouchInput {
            swipeLeft(startX = right - 4f, endX = left + 4f)
        }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), done)

        rule.onNodeWithTag("line").performTouchInput {
            swipeRight(startX = left + 4f, endX = right - 4f)
        }
        rule.waitForIdle()
        assertEquals(listOf("swiped"), done)
    }

    @Test
    fun aLineWithoutASwipeActionCannotBeSwipedAway() {
        line(swipe = false)

        rule.onNodeWithTag("line").performTouchInput {
            swipeLeft(
                startX = right - 4f,
                endX =
                    left + 4f
            )
        }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), done - "open")
    }
}
