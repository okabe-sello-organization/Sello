package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomHistoryStartReads
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.HistoryStart
import com.software.sello.domain.port.HistoryStartState
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordGoalMovement
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where the history begins, for the Guía hint and the backup reminder (SELLO-211). */
@RunWith(AndroidJUnit4::class)
class HistoryStartReadsTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val reads by lazy { RoomHistoryStartReads(ledger.database) }

    private fun read(): HistoryStartState = runBlocking {
        withTimeout(15_000) { reads.observe().first { it !is ReadState.Loading } }
    }

    private fun receive(date: String) = runBlocking {
        ledger.incomes.create(
            CreateIncome(
                operationId = ledger.operation(),
                generation = 1,
                amount = TransactionAmount.of(Money.cop(50_000)).valueOrFail(),
                source = IncomeSource.Salary,
                date = LocalDate.parse(date),
                note = Note.of(null).valueOrFail()
            )
        )
    }

    @Test
    fun anEmptyHistoryHasNoStart() {
        assertEquals(ReadState.Ready(HistoryStart(null, null)), read())
    }

    @Test
    fun anIncomeAloneIsAMovementButNoExpense() {
        receive("2026-09-15")

        assertEquals(ReadState.Ready(HistoryStart(null, LocalDate.of(2026, 9, 15))), read())
    }

    @Test
    fun theEarliestOfEachIsReadWhicheverWasAddedFirst() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        // Added later, dated earlier.
        ledger.expenses.create(ledger.createExpense(100, "2026-08-20", 7_000))
        receive("2026-09-01")

        assertEquals(
            ReadState.Ready(HistoryStart(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20))),
            read()
        )
    }

    @Test
    fun anIncomeBeforeEveryExpenseStartsTheMovements() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        receive("2026-07-31")

        assertEquals(
            ReadState.Ready(HistoryStart(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 7, 31))),
            read()
        )
    }

    @Test
    fun aStoredDateThatCannotBeReadFailsTheReadInsteadOfMeaningNoHistory() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        ledger.database.openHelper.writableDatabase
            .execSQL("UPDATE expense SET effective_date = '2026-13-40'")

        val read = read()

        assertTrue(read is ReadState.Failed)
        assertEquals(
            StorageFailure.Integrity("expense", null, "effective_date"),
            (read as ReadState.Failed).failure
        )
    }

    // Goals and investments count as movements too (ADR 0027's "using Sello").

    /** Viaje (100, its target 101); its first movement takes 102. Today is 9 October. */
    private val viaje = GoalId.of(uuid(100)).valueOrFail()

    private suspend fun goal(command: GoalCommand) =
        assertTrue(ledger.goals.submit(command) is CommandOutcome.Committed)

    private suspend fun goalVersion() =
        ledger.goalReads.goal(viaje).valueOrFail()!!.history.goal.version

    private suspend fun createViaje() = goal(
        CreateGoal(
            ledger.operation(),
            1,
            GoalName.of("Viaje a la costa").valueOrFail(),
            IconKey.of("flight").valueOrFail(),
            transactionPesos(3_000_000),
            YearMonth.of(2026, 12)
        )
    )

    private suspend fun aporte(date: String) = goal(
        RecordGoalMovement(
            ledger.operation(),
            1,
            viaje,
            goalVersion(),
            GoalMovementKind.Contribution,
            transactionPesos(200_000),
            LocalDate.parse(date),
            null
        )
    )

    @Test
    fun aGoalTargetIsNoMovementButItsAporteIsAndADeletedOneNoLonger() = runBlocking {
        val states = Channel<HistoryStartState>(Channel.UNLIMITED)
        // One observation throughout: each committed change is read again.
        val collector = launch { reads.observe().collect(states::send) }
        suspend fun next(expected: HistoryStartState): HistoryStartState = withTimeout(15_000) {
            var state = states.receive()
            while (state != expected) state = states.receive()
            state
        }
        try {
            next(ReadState.Ready(HistoryStart(null, null)))

            createViaje()
            next(ReadState.Ready(HistoryStart(null, null)))
            aporte("2026-09-01")
            next(ReadState.Ready(HistoryStart(null, LocalDate.of(2026, 9, 1))))
            goal(
                DeleteGoalMovement(
                    ledger.operation(),
                    1,
                    viaje,
                    goalVersion(),
                    GoalMovementId.of(uuid(102)).valueOrFail(),
                    1
                )
            )

            assertEquals(
                ReadState.Ready(HistoryStart(null, null)),
                next(ReadState.Ready(HistoryStart(null, null)))
            )
            assertEquals(1L, ledger.long("SELECT COUNT(*) FROM deleted_goal_event"))
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun anInvestmentsMovementsCountFromTheirDate() = runBlocking {
        ledger.created(ledger.createInvestment("CDT", pesos(1_000_000), "2026-08-15"))
        receive("2026-09-01")

        assertEquals(ReadState.Ready(HistoryStart(null, LocalDate.of(2026, 8, 15))), read())
    }

    @Test
    fun aGoalMovementStoredWithoutADateFailsTheRead() = runBlocking {
        createViaje()
        aporte("2026-09-01")
        ledger.database.openHelper.writableDatabase
            .execSQL("UPDATE goal_event SET effective_date = NULL WHERE kind = 'contribution'")

        val read = read()

        assertTrue(read is ReadState.Failed)
        assertEquals(
            StorageFailure.Integrity("goal_event", null, "effective_date"),
            (read as ReadState.Failed).failure
        )
    }
}
