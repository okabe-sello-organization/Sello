package com.software.sello.devtools

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.SnapshotState
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugFaultDecoratorTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val ledger = Ledger()
    private val faults = DebugFaults()
    private val log = OperationLog()
    private val expenses = FaultyExpenseCommands(LedgerExpenses(ledger), faults, log)
    private val inspector =
        OperationInspector(
            LedgerExpenses(ledger),
            LedgerIncomes(ledger),
            LedgerCategories(ledger),
            LedgerGoals(ledger),
            LedgerSchedules(ledger)
        )

    private fun create(n: Int) = CreateExpense(
        OperationId.of("00000000-0000-4000-8000-00000000000$n").value(),
        1,
        CategoryId.of("00000000-0000-4000-8000-000000000009").value(),
        TransactionAmount.of(Money.cop(10_000)).value(),
        LocalDate.of(2026, 10, 1),
        null
    )

    @Test
    fun aLostAnswerStillCommitsOnceAndTheReceiptIsFoundByItsOperation() = runTest {
        faults.arm(Fault.LostAnswer)
        val command = create(1)

        val told = expenses.create(command)

        assertEquals(ExpenseCommandOutcome.OutcomeUnknown(command.operationId), told)
        assertEquals(1, ledger.writes.size)
        assertEquals(StoredOutcome.Saved(1), inspector.stored(log.state.value.single()))
        // Recovering by the same operation writes nothing new; the fault fired once.
        val again = expenses.create(command) as ExpenseCommandOutcome.Committed
        assertTrue(again.replayed)
        assertEquals(1, ledger.writes.size)
        assertEquals(listOf(Fault.LostAnswer, null), log.state.value.map { it.fault })
    }

    @Test
    fun aFailureBeforeStorageWritesNothingAndSaysSo() = runTest {
        faults.arm(Fault.FailBeforeStorage)

        val told = expenses.create(create(2))

        assertTrue(told is ExpenseCommandOutcome.OutcomeUnknown)
        assertTrue(ledger.writes.isEmpty())
        assertEquals(StoredOutcome.Nothing, inspector.stored(log.state.value.single()))
    }

    @Test
    fun anInterruptionComesAfterTheWriteCommitted() = runTest {
        faults.arm(Fault.InterruptAfterSave)

        val thrown = runCatching { expenses.create(create(3)) }.exceptionOrNull()

        assertTrue("$thrown", thrown is SimulatedInterruption)
        assertEquals(1, ledger.writes.size)
        assertEquals(StoredOutcome.Saved(1), inspector.stored(log.state.value.single()))
    }

    @Test
    fun armedFaultsCountDownAndClearingRemovesThemAll() = runTest {
        faults.arm(Fault.LostAnswer, times = 2)
        faults.arm(Fault.FailBeforeStorage)
        assertEquals(mapOf(Fault.LostAnswer to 2, Fault.FailBeforeStorage to 1), faults.state.value)

        assertEquals(Fault.FailBeforeStorage, faults.takeCommandFault())
        assertEquals(Fault.LostAnswer, faults.takeCommandFault())
        faults.clear()

        assertNull(faults.takeCommandFault())
        val told = expenses.create(create(4)) as ExpenseCommandOutcome.Committed
        assertFalse(told.replayed)
    }

    private val october = YearMonth.of(2026, 10)
    private val good = MonthlySnapshot(
        october,
        LocalDate.of(2026, 10, 3),
        ZoneId.of("America/Bogota"),
        1,
        5,
        emptyList(),
        Money.cop(30_000),
        OverallBudget.NoLimit,
        0
    )

    private class Upstream(val state: MutableStateFlow<SnapshotState>) : MonthlySnapshots {
        override suspend fun read(month: YearMonth) = error("unused")

        override fun observe(month: YearMonth) = state
    }

    @Test
    fun aReadFailureKeepsTheLastGoodFiguresAndARetryReadsAgain() = runTest {
        val snapshots =
            FaultyMonthlySnapshots(Upstream(MutableStateFlow(SnapshotState.Ready(good))), faults)
        val seen = mutableListOf<SnapshotState>()
        val reading = launch { snapshots.observe(october).toList(seen) }
        runCurrent()

        faults.arm(Fault.ReadFailure)
        runCurrent()
        reading.cancel()

        val failed = seen.last() as SnapshotState.Failed
        assertEquals(SimulatedReadFailure, failed.failure)
        assertSame(good, failed.lastGood)
        // A retry observes again and gets the real snapshot.
        assertEquals(SnapshotState.Ready(good), snapshots.observe(october).first())
    }

    @Test
    fun theDiagnosticHasIdentifiersAndOutcomesButNoNamesNotesOrAmounts() = runTest {
        faults.arm(Fault.LostAnswer)
        expenses.create(
            create(
                5
            ).copy(note = com.software.sello.domain.model.Note.of("Almuerzo con Ana").value())
        )
        val logged = log.state.value.map { it to inspector.stored(it) }

        val text = diagnosticText(
            SandboxSession(
                active = true,
                date = LocalDate.of(2026, 10, 1),
                scenario = "g01@1",
                nextStep = 3
            ),
            faults.state.value,
            logged
        )

        assertTrue(text, "00000000-0000-4000-8000-000000000005" in text)
        assertTrue(text, "simulated: LostAnswer" in text && "saved rev 1" in text)
        listOf("Almuerzo", "Ana", "10000", "10.000", "10,000").forEach {
            assertFalse(it, it in text)
        }
    }
}
