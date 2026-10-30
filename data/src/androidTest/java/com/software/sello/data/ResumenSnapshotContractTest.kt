package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResumenSnapshotContractTest {
    @get:Rule
    val files = DatabaseFiles()
    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val october = YearMonth.of(2026, 10)

    private suspend fun category(limit: Long = 100_000) {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(limit)))
    }

    private suspend fun spend(pesos: Long, date: String = "2026-10-01") {
        assertTrue(
            ledger.expenses.create(ledger.createExpense(100, date, pesos))
                is CommandOutcome.Committed
        )
    }

    private suspend fun income(pesos: Long, date: String = "2026-10-01") {
        assertTrue(
            ledger.incomes.create(
                CreateIncome(
                    ledger.operation(),
                    1,
                    TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
                    IncomeSource.of("salary", null).valueOrFail(),
                    LocalDate.parse(date),
                    null
                )
            ) is CommandOutcome.Committed
        )
    }

    private suspend fun read(month: YearMonth = october) = ledger.resumen.read(month).valueOrFail()

    @Test
    fun aNewInstallationIsAnExactEmptySnapshotAndTheReadWritesNothing() = runBlocking {
        val snapshot = read()
        assertEquals(Money.cop(0), snapshot.budget.spent)
        assertEquals(Money.cop(0), snapshot.income)
        assertEquals(Money.cop(0), snapshot.netFlow)
        assertEquals(OverallBudget.NoLimit, snapshot.budget.overall)
        assertEquals(0, snapshot.history.sumOf { it.expenseCount })
        assertEquals(12, snapshot.history.size)
        assertEquals(0L, ledger.revision())
        for (table in listOf("income", "expense", "month_limit", "operation_receipt")) {
            assertEquals(0L, ledger.database.count(table))
        }
    }

    @Test
    fun i01AndTheCorrectedGraphAgreeWithStoredRowsAndKeepAllocationIndependent() = runBlocking {
        category()
        spend(10_000)
        spend(15_000, "2026-10-02")
        spend(5_000, "2026-10-03")
        income(150_000)
        val first = read()
        assertEquals(Money.cop(120_000), first.netFlow)
        assertEquals(Money.cop(50_000), first.unallocated)
        assertEquals(PastVerdict.WithinLimit, first.actualVerdict)
        assertEquals(
            listOf(10_000L, 25_000L, 30_000L),
            first.daily.take(3).map {
                it.cumulative.minorUnits
            }
        )
        assertTrue(
            ledger.expenses.edit(
                EditExpense(
                    ledger.operation(),
                    1,
                    com.software.sello.domain.model.ExpenseId.of(uuid(102)).valueOrFail(),
                    1,
                    ledger.categoryId(100),
                    TransactionAmount.of(Money.cop(12_000)).valueOrFail(),
                    LocalDate.of(2026, 10, 2),
                    null
                )
            ) is CommandOutcome.Committed
        )
        val corrected = read()
        assertEquals(
            listOf(10_000L, 22_000L, 27_000L),
            corrected.daily.take(3).map {
                it.cumulative.minorUnits
            }
        )
        assertEquals(Money.cop(123_000), corrected.netFlow)
        assertEquals(Money.cop(50_000), corrected.unallocated)
        assertEquals(27_000L, ledger.long("SELECT SUM(amount_minor) FROM expense"))
        assertEquals(150_000L, ledger.long("SELECT SUM(amount_minor) FROM income"))
        assertEquals(Money.cop(27_000), corrected.history.last().spent)
        assertEquals(ledger.revision(), corrected.budget.revision)
        ledger.reopen()
        assertEquals(corrected, read())
    }

    @Test
    fun historicalBudgetChangesDoNotLeakIntoEarlierBars() = runBlocking {
        ledger.goTo("2026-09-20")
        category()
        spend(20_000, "2026-09-03")
        ledger.goTo("2026-10-09")
        ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, ledger.categoryId(100), 1, finite(120_000))
        )
        spend(30_000)
        income(150_000)
        val snapshot = read()
        val september = snapshot.history[snapshot.history.lastIndex - 1]
        assertEquals(YearMonth.of(2026, 9), september.month)
        assertEquals(Money.cop(100_000), september.limit)
        assertEquals(Money.cop(20_000), september.spent)
        assertEquals(Money.cop(120_000), snapshot.allocated)
        assertEquals(Money.cop(30_000), snapshot.unallocated)
        assertEquals(0L, ledger.database.count("month_limit"))
    }

    @Test
    fun simultaneousWritesCannotMixIncomeExpensesAndLimitsFromDifferentRevisions() = runBlocking {
        category()
        val seen = mutableListOf<ResumenSnapshot>()
        coroutineScope {
            val writer = launch(Dispatchers.Default) {
                for (round in 1..12) {
                    spend(1_000)
                    income(2_000)
                    ledger.categories.submit(
                        SetMonthLimit(
                            ledger.operation(),
                            1,
                            ledger.categoryId(100),
                            round.toLong(),
                            october,
                            finite(100_000 + 5_000L * round)
                        )
                    )
                }
            }
            launch(Dispatchers.Default) {
                while (writer.isActive) seen += read()
                seen += read()
            }
        }
        for (snapshot in seen) {
            val changes = snapshot.budget.revision - 1
            val spent = 1_000 * ((changes + 2) / 3)
            val received = 2_000 * ((changes + 1) / 3)
            val allocated = 100_000 + 5_000 * (changes / 3)
            assertEquals(Money.cop(spent), snapshot.budget.spent)
            assertEquals(Money.cop(received), snapshot.income)
            assertEquals(Money.cop(allocated), snapshot.allocated)
            assertEquals(Money.cop(received - spent), snapshot.netFlow)
            assertEquals(Money.cop(received - allocated), snapshot.unallocated)
            assertEquals(Money.cop(spent), snapshot.daily.last().cumulative)
            assertEquals(Money.cop(spent), snapshot.history.last().spent)
        }
        assertEquals(37L, seen.last().budget.revision)
    }

    @Test
    fun goalAndInvestmentRecordsNeverAssignIncomeSoUnassignedStaysTheSame() = runBlocking {
        // ADR 0012 BG4: 150.000 came in and one category takes 100.000, so 50.000 is unassigned.
        category()
        income(150_000)
        val before = read()
        assertEquals(Money.cop(50_000), before.unallocated)

        // A goal with a contribution of 30.000 and an investment of 20.000 are only tracked.
        assertTrue(
            ledger.goals.submit(
                CreateGoal(
                    ledger.operation(),
                    1,
                    GoalName.of("Viaje").valueOrFail(),
                    IconKey.of("flight").valueOrFail(),
                    transactionPesos(300_000),
                    YearMonth.of(2026, 12)
                )
            ) is CommandOutcome.Committed
        )
        val goal = ledger.goalReads.board().valueOrFail().goals.single().history.goal
        assertTrue(
            ledger.goals.submit(
                RecordGoalMovement(
                    ledger.operation(),
                    1,
                    goal.id,
                    goal.version,
                    GoalMovementKind.Contribution,
                    transactionPesos(30_000),
                    LocalDate.of(2026, 10, 2),
                    null
                )
            ) is CommandOutcome.Committed
        )
        ledger.created(ledger.createInvestment("Fondo", pesos(20_000), "2026-10-03"))

        val after = read()
        assertEquals(Money.cop(50_000), after.unallocated)
        assertEquals(Money.cop(100_000), after.allocated)
        assertEquals(before.income, after.income)
        assertEquals(before.netFlow, after.netFlow)
    }

    @Test
    fun aMalformedHistoricalRowFailsTheWholeReadRatherThanDrawingAFalseZeroBar() = runBlocking {
        category()
        spend(20_000, "2026-09-03")
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE expense SET amount_minor = 0")
        assertEquals(
            Outcome.Failure<SnapshotFailure>(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("expense", uuid(101), "amount_minor")
                )
            ),
            ledger.resumen.read(october)
        )
    }

    @Test
    fun aFailedRefreshKeepsItsOriginalRevisionAndRecoversOnTheFinancialClock() = runBlocking {
        category()
        income(150_000)
        val states = Channel<ResumenSnapshotState>(Channel.UNLIMITED)
        val collector = launch { ledger.resumen.observe(october).collect(states::send) }
        suspend fun next(matches: (ResumenSnapshotState) -> Boolean): ResumenSnapshotState =
            withTimeout(15_000) {
                var state = states.receive()
                while (!matches(state)) state = states.receive()
                state
            }
        try {
            assertEquals(ReadState.Loading, states.receive())
            val good = (
                next {
                    it is ReadState.Ready
                } as ReadState.Ready
                ).value
            ledger.database.openHelper.writableDatabase.execSQL(
                "UPDATE income SET amount_minor = 0"
            )
            ledger.goTo("2026-10-10")
            val failed = next { it is ReadState.Failed } as ReadState.Failed
            assertEquals(good, failed.lastGood)
            assertEquals(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("income", uuid(101), "amount_minor")
                ),
                failed.failure
            )
            assertEquals(LocalDate.of(2026, 10, 9), good.budget.asOf)
            assertEquals(2L, good.budget.revision)
            ledger.database.openHelper.writableDatabase.execSQL(
                "UPDATE income SET amount_minor = 150000"
            )
            ledger.goTo("2026-10-11")
            val recovered = (
                next {
                    it is ReadState.Ready &&
                        it.value.budget.asOf == LocalDate.of(2026, 10, 11)
                } as ReadState.Ready
                ).value
            assertEquals(Money.cop(150_000), recovered.income)
            assertEquals(LocalDate.of(2026, 10, 11), recovered.budget.asOf)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun timeTravelExcludesBothFutureIncomeAndFutureExpenseFromActuals() = runBlocking {
        category()
        spend(30_000, "2026-10-09")
        income(150_000, "2026-10-09")
        ledger.goTo("2026-10-08")
        val earlier = read()
        assertEquals(Money.cop(0), earlier.budget.spent)
        assertEquals(Money.cop(0), earlier.income)
        assertEquals(1, earlier.budget.futureDatedExpenses)
        assertEquals(1, earlier.futureDatedIncomes)
        ledger.goTo("2026-10-09")
        val current = read()
        assertEquals(Money.cop(30_000), current.budget.spent)
        assertEquals(Money.cop(150_000), current.income)
    }

    @Test
    fun futureMonthsAndUnavailableStorageHaveTypedFailures() = runBlocking {
        assertEquals(
            Outcome.Failure<SnapshotFailure>(SnapshotFailure.FutureMonth(october)),
            ledger.resumen.read(october.plusMonths(1))
        )
        ledger.database.close()
        val failed = ledger.resumen.read(october) as Outcome.Failure
        assertTrue((failed.error as SnapshotFailure.Storage).failure is StorageFailure.Unavailable)
        val incomeFailure = ledger.incomeReads.month(october) as Outcome.Failure
        assertTrue(incomeFailure.error is StorageFailure.Unavailable)
    }

    @Test
    fun observingClosedRoomPropagatesItsCancellationRatherThanReturningEmptyIncome() = runBlocking {
        ledger.database.close()
        val states = mutableListOf<ResumenSnapshotState>()
        try {
            withTimeout(15_000) { ledger.resumen.observe(october).take(2).toList(states) }
            throw AssertionError("A closed Room observation returned financial data")
        } catch (cancelled: CancellationException) {
            assertTrue(cancelled !is TimeoutCancellationException)
        }
        assertEquals(listOf(ReadState.Loading), states)
    }

    @Test
    fun calculationUsesTheInjectedDispatcherAfterTheTransactionHasFinished() = runBlocking {
        val arrived = Channel<Runnable>(Channel.UNLIMITED)
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                check(arrived.trySend(block).isSuccess)
            }
        }
        val controlled = TestLedger(files, dispatcher).also { it.start() }
        val pending = async { controlled.resumen.read(october) }
        val calculation = withTimeout(15_000) { arrived.receive() }
        assertTrue(!pending.isCompleted)
        controlled.categories.submit(controlled.createCategory("Después", finite(100_000)))
        controlled.goTo("2026-10-10")
        calculation.run()
        val snapshot = pending.await().valueOrFail()
        assertEquals(0L, snapshot.budget.revision)
        assertEquals(OverallBudget.NoLimit, snapshot.budget.overall)
        assertEquals(1L, controlled.revision())
        assertEquals(LocalDate.of(2026, 10, 9), snapshot.budget.asOf)
    }

    @Test
    fun aFirstReadFailureHasNoLastGoodSnapshotAndIsNotAnEmptySuccess() = runBlocking {
        income(150_000)
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE income SET amount_minor = 0")
        val states = withTimeout(15_000) { ledger.resumen.observe(october).take(2).toList() }
        assertEquals(ReadState.Loading, states.first())
        assertEquals(
            ReadState.Failed(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("income", uuid(100), "amount_minor")
                ),
                null
            ),
            states.last()
        )
    }

    @Test
    fun cancellationWhileCalculationIsQueuedPropagatesWithoutReturningFinancialData() =
        runBlocking {
            val arrived = Channel<Runnable>(Channel.UNLIMITED)
            val dispatcher = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    check(arrived.trySend(block).isSuccess)
                }
            }
            val controlled = TestLedger(files, dispatcher).also { it.start() }
            val pending = async { controlled.resumen.read(october) }
            val calculation = withTimeout(15_000) { arrived.receive() }
            pending.cancel()
            calculation.run()
            try {
                pending.await()
                throw AssertionError("A cancelled read returned a result")
            } catch (_: CancellationException) {
                assertTrue(pending.isCancelled)
            }
            assertEquals(0L, controlled.revision())
            assertEquals(0L, controlled.database.count("operation_receipt"))
        }

    @Test
    fun twoActiveObserversEachPerformOneCalculationInitiallyAndAfterACommit() = runBlocking {
        val computations = AtomicInteger()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                computations.incrementAndGet()
                Dispatchers.Default.dispatch(context, block)
            }
        }
        val controlled = TestLedger(files, dispatcher).also { it.start() }
        val first = Channel<ResumenSnapshotState>(Channel.UNLIMITED)
        val second = Channel<ResumenSnapshotState>(Channel.UNLIMITED)
        val collectors = listOf(
            launch { controlled.resumen.observe(october).collect(first::send) },
            launch { controlled.resumen.observe(october).collect(second::send) }
        )
        suspend fun Channel<ResumenSnapshotState>.ready(revision: Long): ResumenSnapshot =
            withTimeout(15_000) {
                var state = receive()
                while (state !is ReadState.Ready ||
                    state.value.budget.revision != revision
                ) {
                    state = receive()
                }
                state.value
            }
        try {
            first.ready(0)
            second.ready(0)
            assertEquals(2, computations.get())
            assertTrue(
                controlled.incomes.create(
                    CreateIncome(
                        controlled.operation(),
                        1,
                        TransactionAmount.of(Money.cop(150_000)).valueOrFail(),
                        IncomeSource.of("salary", null).valueOrFail(),
                        LocalDate.of(2026, 10, 1),
                        null
                    )
                ) is CommandOutcome.Committed
            )
            assertEquals(Money.cop(150_000), first.ready(1).income)
            assertEquals(Money.cop(150_000), second.ready(1).income)
            assertEquals(4, computations.get())
        } finally {
            collectors.forEach { it.cancel() }
        }
    }

    @Test
    fun totalsAndChartValuesIncludeRowsBeyondTheFirstStoragePage() = runBlocking {
        category()
        repeat(501) {
            spend(10)
            income(20)
        }
        val snapshot = read()
        assertEquals(Money.cop(5_010), snapshot.budget.spent)
        assertEquals(Money.cop(10_020), snapshot.income)
        assertEquals(Money.cop(5_010), snapshot.netFlow)
        assertEquals(Money.cop(-89_980), snapshot.unallocated)
        assertEquals(Money.cop(5_010), snapshot.daily.first().spent)
        assertEquals(Money.cop(5_010), snapshot.daily.last().cumulative)
        assertEquals(501, snapshot.history.last().expenseCount)
        assertEquals(501L, ledger.database.count("expense"))
        assertEquals(501L, ledger.database.count("income"))
        assertEquals(1_003L, ledger.database.count("operation_receipt"))
    }
}
