package com.software.sello.feature.goals

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Note
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A goal's movements as v2 draws them (owner, 2026-11-02): the amount at the end with the
 * balance under it, a note as the title, and month lines in the full list only. MT40.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class GoalDetailMovementsTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val id = "00000000-0000-4000-9000-000000000001"

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun movementId(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    /**
     * MT40: aportes 200.000, 250.000, 260.000, 300.000, 350.000 and 350.000 on the 2nd of
     * May to October, then a retiro of 400.000 on 20 oct for the car: 1.310.000 saved.
     */
    private fun summary(lastYear: Boolean = false): GoalSummary {
        val aportes = listOf(200_000L, 250_000L, 260_000L, 300_000L, 350_000L, 350_000L)
        val movements = aportes.mapIndexed { i, pesos ->
            movement(i + 1, GoalMovementKind.Contribution, pesos, LocalDate.of(2026, 5 + i, 2))
        } + movement(
            7,
            GoalMovementKind.Withdrawal,
            400_000,
            LocalDate.of(2026, 10, 20),
            "Arreglo del carro"
        ) + listOfNotNull(
            movement(8, GoalMovementKind.Contribution, 100_000, LocalDate.of(2025, 12, 15))
                .takeIf { lastYear }
        )
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of("Viaje a la costa").value(),
                IconKey.of("flight").value(),
                null,
                null,
                8,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf<GoalEvent>(GoalTargetChange(pesos(3_000_000), 0, LocalDate.of(2026, 5, 1))) +
                movements
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun movement(
        n: Int,
        kind: GoalMovementKind,
        pesos: Long,
        date: LocalDate,
        note: String? = null
    ) = GoalMovement(
        GoalMovementId.of(movementId(n)).value(),
        kind,
        pesos(pesos),
        date,
        n.toLong(),
        date,
        Note.of(note).value(),
        1,
        Instant.EPOCH,
        Instant.EPOCH
    )

    private fun show(all: Boolean = false, look: Look = Look(), lastYear: Boolean = false) {
        val ready = ReadState.Ready(GoalBoard(1, 1, listOf(summary(lastYear)), null))
        val detail = GoalsRenderModel(testMoney()).detail(ready, id, today)
            as GoalDetailState.Ready
        rule.showSello(look) {
            GoalDetailScreen(
                detail.copy(allMovements = all),
                {},
                dateLabels(),
                monthNames(),
                PaddingValues(16.dp),
                onEditMovement = {},
                onBulk = {}
            )
        }
    }

    private fun row(n: Int) = hasTestTag("goal.movement.${movementId(n)}")

    @Test
    fun aNoteNamesTheRetiroAndItsKindJoinsTheDay() {
        show()

        rule.onNode(
            row(7) and hasText("Arreglo del carro") and hasText("Retiro · mar 20 oct")
        ).assertExists()
        rule.onNode(row(6) and hasText("Aporte") and hasText("vie 2 oct")).assertExists()
    }

    @Test
    fun eachRowSaysItsAmountAndTheBalanceAfterIt() {
        show()

        rule.onNode(
            row(7) and hasContentDescription("menos 400.000 pesos") and hasText("saldo ") and
                hasContentDescription("1.310.000 pesos")
        ).assertExists()
        rule.onNode(
            row(6) and hasContentDescription("más 350.000 pesos") and
                hasContentDescription("1.710.000 pesos")
        ).assertExists()
        rule.onNode(row(4) and hasContentDescription("1.010.000 pesos")).assertExists()
    }

    @Test
    fun theShortListHasNoMonthLines() {
        show()

        rule.onNodeWithTag("goal.movements.month.2026-10").assertDoesNotExist()
    }

    @Test
    fun theFullListHasOneLineAMonthWithItsNet() {
        show(all = true)

        rule.onNode(
            hasTestTag("goal.movements.month.2026-10") and hasText("OCTUBRE") and
                hasContentDescription("menos 50.000 pesos")
        ).assertExists()
        rule.onNode(
            hasTestTag("goal.movements.month.2026-05") and hasText("MAYO") and
                hasContentDescription("más 200.000 pesos")
        ).assertExists()
        rule.onNode(row(1) and hasContentDescription("200.000 pesos")).assertExists()
    }

    @Test
    fun aMonthOfAnotherYearSaysItsYear() {
        show(all = true, lastYear = true)

        rule.onNode(
            hasTestTag("goal.movements.month.2025-12") and hasText("DICIEMBRE 2025") and
                hasContentDescription("más 100.000 pesos")
        ).assertExists()
        rule.onNode(hasTestTag("goal.movements.month.2026-10") and hasText("OCTUBRE"))
            .assertExists()
    }

    @Test
    fun theListClaro() = still(dark = false, all = false)

    @Test
    fun theListOscuro() = still(dark = true, all = false)

    @Test
    fun theFullListClaro() = still(dark = false, all = true)

    private fun still(dark: Boolean, all: Boolean) {
        show(all, Look(dark = dark))
        rule.onNodeWithTag(if (all) "goal.movement.${movementId(1)}" else GOAL_BULK_OPEN_TAG)
            .performScrollTo()
        rule.captureStill(
            "src/test/screenshots/goal-detail-movements${if (all) "-all" else ""}-" +
                "${if (dark) "oscuro" else "claro"}.png"
        )
    }
}
