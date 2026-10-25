package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
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
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Recibo's goal (rule 12, MT28–MT31): the goal the board names is shown, with its
 * percentage; another active goal at 100 % gets the green row and can be marked there;
 * nothing on a past month or with no active goal; a failed read is said, never shown as
 * no goal. Goals A *Viaje a la costa* 1.710.000 of 3.000.000 (57 %) and B *Celular
 * nuevo* 450.000 of 1.200.000 (37 %); today is 23 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboGoalViewModelTest {
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private val clock = HandFinancialClock("2026-10-23")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val reads = HandGoalReads()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private val scheduler = TestCoroutineScheduler()
    private lateinit var corrections: ExpenseCorrections

    private val viaje = "00000000-0000-4000-9000-000000000001"
    private val celular = "00000000-0000-4000-9000-000000000002"
    private val curso = "00000000-0000-4000-9000-000000000003"

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = ExpenseCorrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            { 5.hours + scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main)
        )
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun goal(
        id: String,
        name: String,
        saved: Long,
        target: Long,
        completedOn: LocalDate? = null
    ): GoalSummary {
        val day = LocalDate.of(2026, 10, 1)
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of(name).value(),
                IconKey.of("flight").value(),
                null,
                completedOn,
                4,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf(
                GoalTargetChange(TransactionAmount.of(Money.cop(target)).value(), 0, day),
                GoalMovement(
                    GoalMovementId.of(id.dropLast(1) + "9").value(),
                    GoalMovementKind.Contribution,
                    TransactionAmount.of(Money.cop(saved)).value(),
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
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private val a get() = goal(viaje, "Viaje a la costa", 1_710_000, 3_000_000)
    private val b get() = goal(celular, "Celular nuevo", 450_000, 1_200_000)

    /** A board as storage reads it: [featured] is Recibo's goal after the fallback rule. */
    private fun board(vararg goals: GoalSummary, featured: String?) = GoalBoard(
        3,
        9,
        goals.toList(),
        featured?.let { GoalId.of(it).value() }
    )

    private fun model() = ReciboGoalViewModel(
        SavedStateHandle(),
        session,
        reads,
        commands,
        clock,
        money,
        ids,
        corrections
    ) { false }

    private fun TestScope.watch(model: ReciboGoalViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

    private fun shown(model: ReciboGoalViewModel) = model.state.value as ReciboGoalState.Shown

    @Test
    fun mt28AndMt29TheGoalTheBoardNamesIsShownWithItsPercentage() = runTest {
        reads.board.value = GoalBoardState.Ready(board(a, b, featured = viaje))
        val model = model()
        watch(model)

        assertEquals(
            ReciboGoalState.Shown(ReciboGoalPill(viaje, "Viaje a la costa", "flight", 57), null),
            model.state.value
        )

        // MT29: B chosen, whatever A's completion.
        reads.board.value = GoalBoardState.Ready(board(a, b, featured = celular))
        assertEquals(ReciboGoalPill(celular, "Celular nuevo", "flight", 37), shown(model).pill)
    }

    @Test
    fun mt30WhenTheShownGoalIsCompletedRecibosGoalFallsBack() = runTest {
        reads.board.value = GoalBoardState.Ready(board(a, b, featured = celular))
        val model = model()
        watch(model)

        val completedB = goal(
            celular,
            "Celular nuevo",
            1_200_000,
            1_200_000,
            LocalDate.of(2026, 10, 23)
        )
        reads.board.value = GoalBoardState.Ready(board(a, completedB, featured = viaje))

        assertEquals(viaje, shown(model).pill?.id)
        assertEquals(null, shown(model).reached)
    }

    @Test
    fun nothingIsShownWithNoActiveGoalOrWhileTheFirstReadIsOnItsWay() = runTest {
        val model = model()
        watch(model)
        assertEquals(ReciboGoalState.Hidden, model.state.value)

        val done = goal(viaje, "Viaje a la costa", 3_000_000, 3_000_000, LocalDate.of(2026, 10, 2))
        reads.board.value = GoalBoardState.Ready(board(done, featured = null))

        assertEquals(ReciboGoalState.Hidden, model.state.value)
    }

    @Test
    fun nothingIsShownOnAPastMonthAndTheGoalIsBackOnTheCurrentOne() = runTest {
        reads.board.value = GoalBoardState.Ready(board(a, b, featured = viaje))
        val model = model()
        watch(model)

        session.select(YearMonth.of(2026, 9))
        assertEquals(ReciboGoalState.Hidden, model.state.value)

        session.select(YearMonth.of(2026, 10))
        assertEquals(viaje, shown(model).pill?.id)
    }

    @Test
    fun anotherGoalAt100PercentGetsTheRowButTheShownGoalDoesNot() = runTest {
        val reached = goal(curso, "Curso de inglés", 2_000_000, 2_000_000)
        reads.board.value = GoalBoardState.Ready(board(a, reached, featured = viaje))
        val model = model()
        watch(model)

        assertEquals(
            ReciboReachedGoal(curso, "Curso de inglés", "flight"),
            shown(model).reached
        )

        // Chosen and at 100 %: the pill says 100 %, and no row repeats it.
        reads.board.value = GoalBoardState.Ready(board(a, reached, featured = curso))
        assertEquals(100L, shown(model).pill?.percentage)
        assertEquals(null, shown(model).reached)
    }

    @Test
    fun aFailedReadIsSaidKeepsTheLastGoodGoalAndRetryReadsAgain() = runTest {
        reads.board.value = GoalBoardState.Failed(StorageFailure.Unavailable("x"), null)
        val model = model()
        watch(model)
        assertEquals(ReciboGoalState.Failed, model.state.value)

        reads.board.value =
            GoalBoardState.Failed(StorageFailure.Unavailable("x"), board(a, featured = viaje))
        assertEquals(viaje, shown(model).pill?.id)

        val before = reads.started
        model.onAction(ReciboGoalAction.Retry)
        assertEquals(before + 1, reads.started)
    }

    @Test
    fun theWaitingGoalIsMarkedLogradaFromItsRowOnceAndTheMomentStaysOpen() = runTest {
        val reached = goal(curso, "Curso de inglés", 2_000_000, 2_000_000)
        reads.board.value = GoalBoardState.Ready(board(a, reached, featured = viaje))
        val model = model()
        watch(model)

        model.onAction(ReciboGoalAction.MarkComplete(viaje))
        assertEquals(emptyList<GoalCommand>(), commands.sent)
        model.onAction(ReciboGoalAction.MarkComplete(curso))
        model.onAction(ReciboGoalAction.MarkComplete(curso))

        val sent = commands.sent.single() as CompleteGoal
        assertEquals(GoalId.of(curso).value() to 4L, sent.goalId to sent.expectedVersion)
        // Storage now reads it completed: the row is gone, the moment stays until closed.
        val done = goal(curso, "Curso de inglés", 2_000_000, 2_000_000, LocalDate.of(2026, 10, 23))
        reads.board.value = GoalBoardState.Ready(board(a, done, featured = viaje))
        assertEquals(null, shown(model).reached)
        assertTrue(shown(model).completion is CompletionState.Celebrating)

        model.onAction(ReciboGoalAction.CelebrationDone)
        assertEquals(CompletionState.Idle, shown(model).completion)
    }
}
