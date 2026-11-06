package com.software.sello.feature.goals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.BULK_LINE_TAG
import com.software.sello.designsystem.component.BULK_RECEIPT_LINES_TAG
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Aportar varios as it is drawn on the reference phone (412×891 dp): the receipt's lines scroll
 * inside it from the third, the button counts the lines, a refused retiro says what is
 * available, and an unknown outcome offers only to ask again.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalBulkScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<GoalBulkAction>()

    private fun show(state: GoalBulkState) = rule.showSello {
        GoalBulkScreen(state) { actions += it }
    }

    /** Whether the receipt's first line is scrolled out of sight, wholly or in part. */
    private fun firstLineHidden(count: Int): Boolean {
        val lines = rule.onAllNodesWithTag(BULK_LINE_TAG).fetchSemanticsNodes()
        val list = rule.onNodeWithTag(BULK_RECEIPT_LINES_TAG).fetchSemanticsNode()
        return lines.size < count || lines.first().positionInRoot.y < list.positionInRoot.y
    }

    /**
     * The highlighted line carries its date pill and the goal chips take two rows, so two
     * lines fit whole; with a third the slip is full and the lines scroll inside it, and the
     * total, the button and the pad stay where they are.
     */
    @Test
    fun twoLinesFitAndFromTheThirdTheyScrollInsideTheReceipt() {
        var lines by mutableStateOf(GoalBulkSamples.lines(2))
        rule.showSello { GoalBulkScreen(lines) {} }
        assertFalse(firstLineHidden(2))
        val button = rule.onNodeWithTag(GOAL_BULK_SUBMIT_TAG).getUnclippedBoundsInRoot()

        for (count in listOf(3, 30)) {
            lines = GoalBulkSamples.lines(count)
            rule.waitForIdle()

            assertTrue("$count lines scroll", firstLineHidden(count))
            rule.onNodeWithText("Total").assertIsDisplayed()
            assertEquals(
                button,
                rule.onNodeWithTag(GOAL_BULK_SUBMIT_TAG).getUnclippedBoundsInRoot()
            )
        }
    }

    @Test
    fun theButtonCountsTheLinesAndSends() {
        show(GoalBulkSamples.three)

        rule.onNodeWithText("Anotar 3 movimientos").assertIsEnabled().performClick()

        assertEquals(listOf<GoalBulkAction>(GoalBulkAction.Submit), actions)
    }

    @Test
    fun aLineWithoutATypeSaysSoAndTheButtonWaits() {
        show(GoalBulkSamples.missing)

        rule.onNodeWithText("Falta 1 tipo").assertExists()
        rule.onNodeWithTag(GOAL_BULK_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aRefusedRetiroSaysWhatIsAvailable() {
        show(GoalBulkSamples.refused)

        rule.onNodeWithText("Revisa 1 renglón").assertExists()
        rule.onNodeWithText("No se guardó. Tu borrador sigue aquí.").assertExists()
        rule.onNodeWithTag(GOAL_BULK_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun anUnknownOutcomeOffersOnlyToAskAgain() {
        show(GoalBulkSamples.unknown)

        rule.onNodeWithText("Comprobar si se guardó").assertIsEnabled().performClick()
        rule.onNodeWithTag(GOAL_BULK_KEYPAD_TAG).assertExists()

        assertEquals(listOf<GoalBulkAction>(GoalBulkAction.Retry), actions)
    }

    @Test
    fun olderGoalsSaySoOfferReintentarAndTheReceiptStillSaves() {
        show(GoalBulkSamples.three.copy(stale = true))

        rule.onNodeWithTag(GOAL_BULK_STALE_TAG).assertExists()
        rule.onNodeWithText(
            "No pudimos actualizar. Esto es lo último que se leyó y puede no estar al día."
        ).assertExists()
        rule.onNodeWithTag(GOAL_BULK_SUBMIT_TAG).assertIsEnabled()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(listOf<GoalBulkAction>(GoalBulkAction.Retry), actions)
    }

    @Test
    fun freshGoalsHaveNoNotice() {
        show(GoalBulkSamples.three)

        rule.onNodeWithTag(GOAL_BULK_STALE_TAG).assertDoesNotExist()
    }

    @Test
    fun aRemovedLineOffersDeshacer() {
        show(GoalBulkSamples.three.copy(removed = RemovedGoalLine(withdrawal = false)))

        rule.onNodeWithText("Quitaste el renglón de Aporte").assertExists()
        rule.onNodeWithText("Deshacer").performClick()

        assertEquals(listOf<GoalBulkAction>(GoalBulkAction.UndoRemove), actions)
    }

    @Test
    fun eachLineIsTitledWithItsGoalAndTheChipsPickTheHighlightedLinesGoal() {
        show(GoalBulkSamples.twoGoals)

        rule.onNodeWithText("Aportar varios").assertExists()
        rule.onAllNodesWithText("Viaje a la costa", useUnmergedTree = true).assertCountEquals(3)
        rule.onNodeWithText("Aporte · lun 12 oct", substring = true, useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithTag("$GOAL_BULK_GOALS_TAG.g-viaje").performClick()

        assertEquals(listOf<GoalBulkAction>(GoalBulkAction.GoalPicked("g-viaje")), actions)
    }

    @Test
    fun aLineWithoutAGoalSaysSoAndTheButtonWaits() {
        show(GoalBulkSamples.missingGoal)

        rule.onNodeWithText("Falta 1 meta").assertExists()
        rule.onNodeWithText("Elige una meta", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(GOAL_BULK_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun withNoActiveGoalTheReceiptSaysSoAndCanBeClosed() {
        show(GoalBulkSamples.missingGoal.copy(goals = emptyList()))

        rule.onNodeWithText("No hay metas activas.").assertExists()
        rule.onNodeWithTag(GOAL_BULK_CLOSE_TAG).performClick()

        assertEquals(listOf<GoalBulkAction>(GoalBulkAction.Back), actions)
    }
}
