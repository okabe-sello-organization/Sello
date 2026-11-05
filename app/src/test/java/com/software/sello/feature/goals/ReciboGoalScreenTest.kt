package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
        show(ReciboGoalState.Shown(listOf(viaje), null))

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
        show(ReciboGoalState.Shown(listOf(viaje.copy(percentage = 112, reached = true)), null))

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
                listOf(viaje.copy(percentage = 112, reached = true)),
                null,
                CompletionState.Busy
            )
        )

        rule.onNodeWithContentDescription("Marcar Viaje a la costa como lograda")
            .assertIsNotEnabled()
    }

    @Test
    fun anotherGoalAt100PercentIsMarkedFromItsGreenRow() {
        show(ReciboGoalState.Shown(listOf(viaje), curso))

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
        show(
            ReciboGoalState.Shown(
                listOf(viaje),
                curso,
                CompletionState.Failed(GoalDetailError.NotSaved)
            )
        )

        rule.onNodeWithText("No se guardó el cambio. Puedes intentarlo de nuevo.").assertExists()
        // Titled as a save, never as a failed read (#332).
        rule.onNode(hasText("No se guardó") and hasAnyAncestor(hasTestTag(RECIBO_GOAL_ERROR_TAG)))
            .assertExists()
        rule.onNodeWithText("No se pudo cargar").assertDoesNotExist()
        rule.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag(RECIBO_GOAL_ERROR_TAG)))
            .performClick()

        assertEquals(listOf<Any>(ReciboGoalAction.Retry), actions)
    }

    @Test
    fun aMarcarWithAnUnknownOutcomeOnlyOffersToCheckIt() {
        show(
            ReciboGoalState.Shown(
                listOf(viaje),
                curso,
                CompletionState.Failed(GoalDetailError.Unknown)
            )
        )

        val slip = hasAnyAncestor(hasTestTag(RECIBO_GOAL_ERROR_TAG))
        rule.onNode(hasText("Falta comprobar si se guardó") and slip).assertExists()
        rule.onNodeWithText("No se pudo cargar").assertDoesNotExist()
        rule.onNode(hasText("Comprobar si se guardó") and slip).performClick()

        assertEquals(listOf<Any>(ReciboGoalAction.Retry), actions)
    }

    @Test
    fun hiddenDrawsNothing() {
        show(ReciboGoalState.Hidden)

        rule.onNodeWithTag(RECIBO_GOAL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_GOAL_REACHED_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_GOAL_FAILED_TAG).assertDoesNotExist()
    }

    private val fondo = ReciboGoalPill("fondo", "Fondo de emergencia", "home", 42)
    private val bici = ReciboGoalPill("bici", "Bicicleta", "fitness_center", 18)

    private fun pillOf(name: String, place: Int, of: Int, percentage: Int) =
        rule.onNodeWithContentDescription(
            "$name, $percentage por ciento. Meta $place de $of. Ver meta"
        )

    @Test
    fun olderGoalsAreMarkedAndReadAgain() {
        show(ReciboGoalState.Shown(listOf(viaje, fondo), null, stale = true))

        rule.onNodeWithText("No pudimos leer tus metas de nuevo", substring = true).assertExists()
        rule.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag(RECIBO_GOAL_STALE_TAG)))
            .performClick()
        pillOf("Viaje a la costa", 1, 2, 57).assertExists()

        assertEquals(listOf<Any>(ReciboGoalAction.Retry), actions)
    }

    @Test
    fun freshGoalsAreNotMarked() {
        show(ReciboGoalState.Shown(listOf(viaje, fondo), null))

        rule.onNodeWithTag(RECIBO_GOAL_STALE_TAG).assertDoesNotExist()
    }

    @Test
    fun oneGoalIsTodaysPillWithNoDotsAndNoPlace() {
        show(ReciboGoalState.Shown(listOf(viaje), null))

        rule.onNodeWithTag(RECIBO_GOAL_TAG).assertExists()
        rule.onNodeWithTag(RECIBO_GOAL_DOTS_TAG).assertDoesNotExist()
    }

    @Test
    fun severalGoalsOpenOnTheFirstWithDotsAndEachPillSaysItsPlace() {
        show(ReciboGoalState.Shown(listOf(viaje, fondo, bici), null))

        rule.onNodeWithTag(RECIBO_GOAL_DOTS_TAG).assertExists()
        pillOf("Viaje a la costa", 1, 3, 57).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Aportar a Viaje a la costa").performClick()

        assertEquals(listOf<Any>("open:viaje", "contribute:viaje"), actions)
    }

    @Test
    fun talkBackMovesToTheNextAndPreviousGoalAndTheCarouselLoops() {
        show(ReciboGoalState.Shown(listOf(viaje, fondo, bici), null))

        pillOf("Viaje a la costa", 1, 3, 57).performCustomAction("Meta siguiente")
        pillOf("Fondo de emergencia", 2, 3, 42).assertIsDisplayed()
            .performCustomAction("Meta anterior")
        // Before the first comes the last.
        pillOf("Viaje a la costa", 1, 3, 57).assertIsDisplayed()
            .performCustomAction("Meta anterior")
        pillOf("Bicicleta", 3, 3, 18).assertIsDisplayed()
            .performCustomAction("Meta siguiente")
        // After the last comes the first again.
        pillOf("Viaje a la costa", 1, 3, 57).assertIsDisplayed()
    }

    @Test
    fun aSecondPillAt100PercentOffersMarcarComoLograda() {
        val celular =
            ReciboGoalPill("celular", "Celular nuevo", "phone_iphone", 100, reached = true)
        show(ReciboGoalState.Shown(listOf(viaje, celular, fondo), null))

        pillOf("Viaje a la costa", 1, 3, 57).performCustomAction("Meta siguiente")
        rule.onNodeWithContentDescription("Marcar Celular nuevo como lograda").performClick()

        assertEquals(listOf<Any>(ReciboGoalAction.MarkComplete("celular")), actions)
    }

    private fun SemanticsNodeInteraction.performCustomAction(
        label: String
    ): SemanticsNodeInteraction {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == label }
        rule.runOnIdle { action.action() }
        rule.waitForIdle()
        return this
    }
}
