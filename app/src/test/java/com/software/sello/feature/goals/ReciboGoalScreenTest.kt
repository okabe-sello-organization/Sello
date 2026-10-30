package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recibo's goal as drawn: the pill opens the goal and says its exact percentage, the
 * round + is Aportar for that goal, the green row marks the waiting goal, a failed read
 * offers to read again, and nothing is drawn when hidden.
 */
@RunWith(AndroidJUnit4::class)
class ReciboGoalScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val viaje = ReciboGoalPill("viaje", "Viaje a la costa", "flight", 57)
    private val curso = ReciboReachedGoal("curso", "Curso de inglés", "school")
    private val actions = mutableListOf<Any>()

    private fun show(state: ReciboGoalState) {
        rule.showSello(Look()) {
            Box(Modifier.width(320.dp).testTag("area")) {
                ReciboGoal(
                    state,
                    { actions += it },
                    { actions += "open:$it" },
                    { actions += "contribute:$it" }
                )
            }
        }
    }

    @Test
    fun thePillSaysItsExactPercentageOpensTheGoalAndItsPlusIsAportar() {
        show(ReciboGoalState.Shown(viaje, null))

        rule.onNodeWithContentDescription("Viaje a la costa, 57 por ciento. Ver meta")
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0.57f, 0f..1f)
                )
            )
            .performClick()
        rule.onNodeWithContentDescription("Aportar a Viaje a la costa").performClick()

        assertEquals(listOf<Any>("open:viaje", "contribute:viaje"), actions)
        rule.onNodeWithTag(RECIBO_GOAL_REACHED_TAG).assertDoesNotExist()
    }

    @Test
    fun theShownGoalAt100PercentOffersMarcarComoLogradaInsteadOfThePlus() {
        show(ReciboGoalState.Shown(viaje.copy(percentage = 112, reached = true), null))

        rule.onNodeWithContentDescription("Aportar a Viaje a la costa").assertDoesNotExist()
        rule.onNodeWithContentDescription("Marcar Viaje a la costa como lograda")
            .assertHasClickAction()
            .performClick()
        rule.onNodeWithContentDescription("Viaje a la costa, 112 por ciento. Ver meta")
            .performClick()

        assertEquals(
            listOf<Any>(ReciboGoalAction.MarkComplete("viaje"), "open:viaje"),
            actions
        )
    }

    @Test
    fun whileItIsBeingMarkedThePillsButtonWaits() {
        show(
            ReciboGoalState.Shown(
                viaje.copy(percentage = 112, reached = true),
                null,
                CompletionState.Busy
            )
        )

        rule.onNodeWithContentDescription("Marcar Viaje a la costa como lograda")
            .assertIsNotEnabled()
    }

    @Test
    fun anotherGoalAt100PercentIsMarkedFromItsGreenRow() {
        show(ReciboGoalState.Shown(viaje, curso))

        rule.onNodeWithText("Curso de inglés").assertExists()
        rule.onNodeWithText("Llegó al 100 %").assertExists()
        rule.onNode(
            hasText("Marcar como lograda") and hasAnyAncestor(hasTestTag(RECIBO_GOAL_REACHED_TAG))
        ).performClick()

        assertEquals(listOf<Any>(ReciboGoalAction.MarkComplete("curso")), actions)
    }

    @Test
    fun aFailedReadSaysSoAndReadsAgain() {
        show(ReciboGoalState.Failed)

        rule.onNodeWithText("No pudimos leer tus metas").assertExists()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<Any>(ReciboGoalAction.Retry), actions)
    }

    @Test
    fun aMarcarNotSavedSaysSoAndOffersToTryAgain() {
        show(ReciboGoalState.Shown(viaje, curso, CompletionState.Failed(GoalDetailError.NotSaved)))

        rule.onNodeWithText("No se guardó el cambio. Puedes intentarlo de nuevo.").assertExists()
        rule.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag(RECIBO_GOAL_ERROR_TAG)))
            .performClick()

        assertEquals(listOf<Any>(ReciboGoalAction.Retry), actions)
    }

    @Test
    fun hiddenDrawsNothing() {
        show(ReciboGoalState.Hidden)

        rule.onNodeWithTag(RECIBO_GOAL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_GOAL_REACHED_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_GOAL_FAILED_TAG).assertDoesNotExist()
    }
}
