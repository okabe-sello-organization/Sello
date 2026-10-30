package com.software.sello.feature.goals

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Metas looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MetasScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 23)
    private val money = testMoney()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun goal(
        number: Int,
        name: String,
        target: Long,
        aportes: List<Long>,
        completedOn: LocalDate? = null
    ): GoalSummary {
        val day = LocalDate.of(2026, 10, 2)
        val goal = Goal(
            GoalId.of("00000000-0000-4000-9000-%012d".format(number)).value(),
            GoalName.of(name).value(),
            IconKey.of("flight").value(),
            YearMonth.of(2026, 12).takeIf { completedOn == null },
            completedOn,
            1,
            Instant.ofEpochSecond(number.toLong()),
            Instant.ofEpochSecond(number.toLong())
        )
        val events =
            listOf(GoalTargetChange(TransactionAmount.of(Money.cop(target)).value(), 0, day)) +
                aportes.mapIndexed { index, pesos ->
                    GoalMovement(
                        GoalMovementId.of(
                            "00000000-0000-4000-8000-%04d%08d".format(number, index)
                        ).value(),
                        GoalMovementKind.Contribution,
                        TransactionAmount.of(Money.cop(pesos)).value(),
                        day,
                        index + 1L,
                        day,
                        null,
                        1,
                        Instant.EPOCH,
                        Instant.EPOCH
                    )
                }
        val history = GoalHistory(goal, events)
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    /** One goal under way, one just started and one reached. */
    private val board = GoalBoard(
        1,
        1,
        listOf(
            goal(1, "Viaje a la costa", 3_000_000, listOf(750_000, 960_000)),
            goal(2, "Celular nuevo", 1_200_000, listOf(450_000)),
            goal(3, "Vacunas de Luna", 400_000, listOf(400_000), LocalDate.of(2026, 9, 30))
        ),
        null
    )
    private val metas = GoalsRenderModel(money).metas(ReadState.Ready(board), today)

    private fun capture(look: Look) {
        rule.showSello(look) {
            MetasScreen(metas, {}, dateLabels(), PaddingValues(16.dp))
        }
        rule.captureStill("src/test/screenshots/metas-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
