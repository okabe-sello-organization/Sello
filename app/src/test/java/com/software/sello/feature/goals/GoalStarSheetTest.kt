package com.software.sello.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Starring a sixth goal (SELLO-144), as approved in goal.html: the sheet lists the five
 * starred goals with their icon, name and percentage; picking one, Cancelar and
 * Reintentar each say what they mean. Captured over the goal's detail.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class GoalStarSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val id = "00000000-0000-4000-9000-000000000001"
    private val actions = mutableListOf<GoalDetailAction>()

    private val choices = listOf(
        StarChoice("celular", "Celular nuevo", "phone_iphone", 100),
        StarChoice("fondo", "Fondo de emergencia", "home", 42),
        StarChoice("luna", "Cirugía de Luna", "pets", 30),
        StarChoice("bici", "Bicicleta", "fitness_center", 18),
        StarChoice("curso", "Curso de inglés", "school", 5)
    )

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** Viaje a la costa, 1.710.000 of 3.000.000 (57 %), not starred. */
    private fun detail(): GoalDetailState.Ready {
        val day = LocalDate.of(2026, 10, 2)
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of("Viaje a la costa").value(),
                IconKey.of("flight").value(),
                null,
                null,
                3,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf(
                GoalTargetChange(pesos(3_000_000), 0, LocalDate.of(2026, 5, 1)),
                GoalMovement(
                    GoalMovementId.of("00000000-0000-4000-8000-000000000001").value(),
                    GoalMovementKind.Contribution,
                    pesos(1_710_000),
                    day,
                    1,
                    day,
                    null,
                    1,
                    Instant.EPOCH,
                    Instant.EPOCH
                )
            )
        )
        val board = GoalBoard(
            1,
            1,
            listOf(GoalSummary(history, GoalPolicy.progress(history).value())),
            emptySet(),
            emptyList()
        )
        return GoalsRenderModel(testMoney()).detail(ReadState.Ready(board), id, day)
            as GoalDetailState.Ready
    }

    private fun show(
        picked: String? = null,
        error: GoalDetailError? = null,
        busy: Boolean = false,
        look: Look = Look()
    ) {
        val ready = detail()
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                GoalDetailScreen(ready, {}, dateLabels(), monthNames(), PaddingValues(16.dp))
                ReplaceStarSheet(
                    ready.goal.name,
                    ReplaceSheet(choices, picked),
                    busy,
                    error,
                    { actions += it }
                )
            }
        }
    }

    @Test
    fun pickingAGoalReplacesItsStar() {
        show()

        rule.onNodeWithText("Tu Recibo ya tiene 5 metas").assertExists()
        rule.onNodeWithContentDescription("Bicicleta, 18 por ciento").performClick()

        assertEquals(listOf<GoalDetailAction>(GoalDetailAction.Replace("bici")), actions)
    }

    @Test
    fun cancelarChangesNothing() {
        show()

        rule.onNodeWithTag(GOAL_REPLACE_CANCEL_TAG).performClick()

        assertEquals(listOf<GoalDetailAction>(GoalDetailAction.CancelReplace), actions)
    }

    @Test
    fun aRefusedPickSaysSoAndReintentarSendsTheSamePick() {
        show(picked = "bici", error = GoalDetailError.NotSaved)

        rule.onNodeWithText("Tus 5 metas destacadas siguen igual", substring = true).assertExists()
        rule.onNodeWithTag(GOAL_REPLACE_RETRY_TAG).performClick()

        assertEquals(listOf<GoalDetailAction>(GoalDetailAction.Replace("bici")), actions)
    }

    @Test
    fun anUnknownPickIsCheckedNotPickedAgain() {
        show(picked = "bici", error = GoalDetailError.Unknown)

        rule.onNodeWithContentDescription("Fondo de emergencia, 42 por ciento").assertIsNotEnabled()
        rule.onNodeWithText("Comprobar").performClick()

        assertEquals(listOf<GoalDetailAction>(GoalDetailAction.Retry), actions)
    }

    @Test
    fun whileSavingNothingElseCanBePicked() {
        show(picked = "bici", busy = true)

        rule.onNodeWithContentDescription("Fondo de emergencia, 42 por ciento").assertIsNotEnabled()
        rule.onNodeWithTag(GOAL_REPLACE_CANCEL_TAG).assertIsNotEnabled()
    }

    @Test
    fun sheetLight() {
        show()
        rule.captureStill("src/test/screenshots/goal-replace-sheet-claro.png")
    }

    @Test
    fun sheetDark() {
        show(look = Look(dark = true))
        rule.captureStill("src/test/screenshots/goal-replace-sheet-oscuro.png")
    }

    @Test
    fun refusedLight() {
        show(picked = "bici", error = GoalDetailError.NotSaved)
        rule.captureStill("src/test/screenshots/goal-replace-refused-claro.png")
    }

    @Test
    fun refusedDark() {
        show(picked = "bici", error = GoalDetailError.NotSaved, look = Look(dark = true))
        rule.captureStill("src/test/screenshots/goal-replace-refused-oscuro.png")
    }
}
