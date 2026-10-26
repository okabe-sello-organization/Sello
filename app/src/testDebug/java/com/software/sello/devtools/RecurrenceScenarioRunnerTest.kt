package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.CatchUp
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.ChangeSchedule
import com.software.sello.devtools.ScenarioStep.DeleteOccurrence
import com.software.sello.devtools.ScenarioStep.DeleteSchedule
import com.software.sello.devtools.ScenarioStep.PauseSchedule
import com.software.sello.devtools.ScenarioStep.ResumeSchedule
import com.software.sello.devtools.ScenarioStep.Schedule
import com.software.sello.devtools.ScenarioStep.Verify
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpensePage
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomePage
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.usecase.CatchUpSchedules
import com.software.sello.feature.recurrence.CatchUpRunner
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

/**
 * Catch-up as storage would answer it: each call asks [answer] for the batch due on the
 * day it was given, and the day and limit of every call are kept.
 */
class ScriptedCatchUp(private val answer: (LocalDate) -> CatchUpBatch) : ScheduleCatchUpStore {
    val asked = mutableListOf<Pair<LocalDate, Int>>()

    override suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch {
        asked += today to limit
        return answer(today)
    }
}

/**
 * The rows the checks read, as the real reads return them: expenses and incomes of a
 * month newest first, schedules in creation order, every category in the month.
 */
class StoredRecords :
    ExpenseReads,
    IncomeReads,
    ScheduleReads,
    CategoryReads {
    val expenses = mutableListOf<Expense>()
    val incomes = mutableListOf<Income>()
    val schedules = mutableListOf<StoredSchedule>()
    val categories = mutableListOf<com.software.sello.domain.model.Category>()

    override suspend fun byId(id: ExpenseId) = Outcome.Success(expenses.firstOrNull { it.id == id })

    override suspend fun inCategory(
        categoryId: CategoryId,
        month: YearMonth,
        after: ExpenseCursor?,
        limit: Int
    ): Outcome<ExpensePage, StorageFailure> = Outcome.Success(
        ExpensePage(
            expenses.filter { it.categoryId == categoryId && YearMonth.from(it.date) == month }
                .sortedWith(
                    compareByDescending<Expense> {
                        it.date
                    }.thenByDescending { it.sequence }
                ),
            null
        )
    )

    override suspend fun lastUsedCategory() = Outcome.Success(null)

    override suspend fun byId(id: IncomeId) = Outcome.Success(incomes.firstOrNull { it.id == id })

    override suspend fun inMonth(
        month: YearMonth,
        after: IncomeCursor?,
        limit: Int
    ): Outcome<IncomePage, StorageFailure> = Outcome.Success(
        IncomePage(incomes.filter { YearMonth.from(it.date) == month }.reversed(), null)
    )

    override suspend fun month(month: YearMonth) = error("unused")

    override fun observeMonth(month: YearMonth) = error("unused")

    override suspend fun book() = Outcome.Success(ScheduleBook(1, 1, schedules.toList()))

    // No automatic run: each catch-up here is one a step asked for.
    override fun observe(): Flow<ScheduleBookState> = emptyFlow()

    override suspend fun monthBudget(month: YearMonth) = Outcome.Success(
        MonthBudget(
            month,
            1,
            1,
            categories.map { CategoryBudget(it, MonthBudgetState.Unconfigured) }
        )
    )
}

/** Expense commands on the [ledger] that also delete, removing the row from [stored]. */
class DeletingExpenses(private val ledger: Ledger, private val stored: StoredRecords) :
    ExpenseCommands {
    val deleted = mutableListOf<DeleteExpense>()

    override suspend fun create(command: CreateExpense) = error("unused")

    override suspend fun delete(command: DeleteExpense): ExpenseCommandOutcome {
        deleted += command
        val written = ledger.write(command.operationId, OperationKind.DeleteExpense, null)
            ?: return ExpenseCommandOutcome.Rejected(ExpenseRejection.ExpenseMissing)
        if (!written.third) stored.expenses.removeAll { it.id == command.expenseId }
        return ExpenseCommandOutcome.Committed(
            CommittedExpense(written.first, command.expenseId),
            written.third
        )
    }

    override suspend fun find(operationId: com.software.sello.domain.model.OperationId) =
        Outcome.Success(
            ledger.receipts[operationId.value]?.let { (receipt, _) ->
                CommittedExpense(receipt, ExpenseId.of(UUID.randomUUID().toString()).value())
            }
        )

    override suspend fun edit(command: EditExpense) = error("unused")

    override suspend fun restore(command: RestoreExpense) = error("unused")

    override fun undoOffer() = null
}

/** Recurrence collaborators no step of the scenario uses. */
fun TestScope.noSchedules(clock: ControlledFinancialClock): ScenarioSchedules {
    val stored = StoredRecords()
    return ScenarioSchedules(
        LedgerSchedules(Ledger()),
        stored,
        CatchUpRunner(
            CatchUpSchedules(ScriptedCatchUp { error("unused") }, clock),
            clock,
            stored,
            backgroundScope
        ),
        CatchUpLog(),
        stored,
        stored,
        stored
    )
}

/**
 * Recurrence steps send the app's own schedule commands, to the schedule their step made,
 * at the version written for them, on the sandbox's day; a catch-up step runs the app's
 * own catch-up on the sandbox's financial day and reports its batches; a check reads what
 * a schedule recorded by its origin and never counts a manual entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecurrenceScenarioRunnerTest {
    private val zone = ZoneId.of("America/Bogota")
    private val ledger = Ledger()
    private val stored = StoredRecords()
    private val schedules = LedgerSchedules(ledger)
    private val expenses = DeletingExpenses(ledger, stored)
    private val log = CatchUpLog()
    private var answer: (LocalDate) -> CatchUpBatch = { CatchUpBatch.Recorded(emptyList()) }
    private val catchUp = ScriptedCatchUp { answer(it) }

    private fun scenario(start: LocalDate, vararg steps: ScenarioStep) = Scenario(
        id = "programados-prueba",
        version = 1,
        seed = 1,
        zone = zone,
        start = start,
        title = "Programados",
        summary = "",
        steps = steps.toList()
    )

    /** A process on the stored date, with the app's catch-up over [catchUp]. */
    private fun TestScope.runner(scenario: Scenario, store: InMemorySessionStore): ScenarioRunner {
        val clock = ControlledFinancialClock(FinancialDay(store.session.date!!, zone))
        return ScenarioRunner(
            scenario,
            7,
            SandboxTime(clock, store, backgroundScope),
            LedgerCategories(ledger),
            expenses,
            LedgerIncomes(ledger),
            LedgerGoals(ledger),
            NoSnapshots,
            NoSnapshots,
            NoGoalReads,
            ScenarioSchedules(
                schedules,
                stored,
                CatchUpRunner(
                    CatchUpSchedules(LoggedScheduleCatchUp(catchUp, log), clock),
                    clock,
                    stored,
                    backgroundScope
                ),
                log,
                stored,
                stored,
                stored
            ),
            noInvestments(),
            store,
            backgroundScope
        )
    }

    private fun store(scenario: Scenario) = InMemorySessionStore(
        SandboxSession(active = true, date = scenario.start, scenario = scenario.key)
    )

    private fun id(scenario: Scenario, step: Int) = ledger.receipts.getValue(
        scenario.operation(step)
    ).second

    private fun expense(
        id: String,
        category: String,
        date: LocalDate,
        pesos: Long,
        note: String,
        origin: EntryOrigin?
    ) = Expense(
        ExpenseId.of(id).value(),
        CategoryId.of(category).value(),
        date,
        stored.expenses.size + 1L,
        TransactionAmount.of(Money.cop(pesos)).value(),
        Note.of(note).value(),
        1,
        Instant.EPOCH,
        Instant.EPOCH,
        origin
    )

    private fun origin(schedule: String, date: LocalDate, slot: Int = 1) =
        EntryOrigin(ScheduleId.of(schedule).value(), Occurrence(date, slot))

    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct5 = LocalDate.of(2026, 10, 5)
    private val oct24 = LocalDate.of(2026, 10, 24)

    private val rent = Scheduled.Payment("hogar", 950_000, "Arriendo")

    @Test
    fun scheduleStepsSendTheAppsOwnCommandsToTheScheduleTheirStepMade() = runTest {
        val scenario = scenario(
            oct1,
            Category("hogar", "Hogar", "home", 2_000_000),
            Schedule("arriendo", rent, Every.Month(5)),
            CatchUp,
            AdvanceTo(oct24),
            CatchUp,
            DeleteOccurrence("arriendo", oct5),
            PauseSchedule("arriendo", 1),
            ResumeSchedule("arriendo", 2),
            ChangeSchedule("arriendo", 3, pesos = 1_000_000),
            ChangeSchedule(
                "arriendo",
                4,
                every = Every.TwiceAMonth(15, 30),
                start = LocalDate.of(2026, 10, 25)
            ),
            Schedule(
                "salario",
                Scheduled.Income("salary", 2_000_000, "Salario"),
                Every.Weeks(2),
                end = LocalDate.of(2026, 12, 31)
            ),
            DeleteSchedule("arriendo", 5)
        )
        // On 24 oct storage records the 5 oct rent, carrying its origin.
        answer = { today ->
            if (today == oct24 && stored.expenses.isEmpty() && expenses.deleted.isEmpty()) {
                val made = origin(id(scenario, 1), oct5)
                stored.expenses += expense(
                    "00000000-0000-4000-8000-000000000005",
                    id(scenario, 0),
                    oct5,
                    950_000,
                    "Arriendo",
                    made
                )
                CatchUpBatch.Recorded(
                    listOf(RecordedOccurrence.Payment(made, stored.expenses.single().id))
                )
            } else {
                CatchUpBatch.Recorded(emptyList())
            }
        }
        val runner = runner(scenario, store(scenario))
        while (runner.step()) Unit

        assertTrue(runner.state.value.problem, runner.state.value.finished)
        val hogar = CategoryId.of(id(scenario, 0)).value()
        val arriendo = ScheduleId.of(id(scenario, 1)).value()
        val payment = ScheduledEntry.Payment(
            hogar,
            TransactionAmount.of(Money.cop(950_000)).value(),
            Note.of("Arriendo").value()
        )
        fun op(step: Int) = com.software.sello.domain.model.OperationId.of(
            scenario.operation(step)
        ).value()
        assertEquals(
            listOf(
                CreateSchedule(op(1), 7, payment, RepeatRule.Monthly.of(5).value(), oct1, null),
                com.software.sello.domain.port.PauseSchedule(op(6), 7, arriendo, 1),
                com.software.sello.domain.port.ResumeSchedule(op(7), 7, arriendo, 2),
                com.software.sello.domain.port.ChangeSchedule(
                    op(8),
                    7,
                    arriendo,
                    3,
                    payment.copy(amount = TransactionAmount.of(Money.cop(1_000_000)).value()),
                    RepeatRule.Monthly.of(5).value(),
                    oct1,
                    null
                ),
                com.software.sello.domain.port.ChangeSchedule(
                    op(9),
                    7,
                    arriendo,
                    4,
                    payment.copy(amount = TransactionAmount.of(Money.cop(1_000_000)).value()),
                    RepeatRule.TwiceMonthly.of(15, 30).value(),
                    LocalDate.of(2026, 10, 25),
                    null
                ),
                CreateSchedule(
                    op(10),
                    7,
                    ScheduledEntry.Income(
                        IncomeSource.Salary,
                        TransactionAmount.of(Money.cop(2_000_000)).value(),
                        Note.of("Salario").value()
                    ),
                    RepeatRule.EveryWeeks.of(2).value(),
                    oct24,
                    LocalDate.of(2026, 12, 31)
                ),
                com.software.sello.domain.port.DeleteSchedule(op(11), 7, arriendo, 5)
            ),
            schedules.sent
        )
        // The occurrence's own entry, found by its origin, at the version it was read.
        assertEquals(
            listOf(
                DeleteExpense(
                    op(5),
                    7,
                    ExpenseId.of("00000000-0000-4000-8000-000000000005").value(),
                    1
                )
            ),
            expenses.deleted
        )
        assertTrue(stored.expenses.isEmpty())
    }

    @Test
    fun catchUpAfterTheJumpRunsTheAppsCatchUpOnTheNewDayAndReportsItsBatches() = runTest {
        val jul31 = LocalDate.of(2026, 7, 31)
        val scenario = scenario(
            jul31,
            Category("comida", "Comida", "restaurant", 500_000),
            Schedule("cafe", Scheduled.Payment("comida", 5_000, "Café"), Every.Days(1)),
            CatchUp,
            AdvanceTo(oct24),
            CatchUp
        )
        // RE11's two batches: 1 ago – 19 sep (50) and 20 sep – 24 oct (35).
        val batches = ArrayDeque(
            listOf(
                LocalDate.of(2026, 8, 1) to 50,
                LocalDate.of(2026, 9, 20) to 35
            )
        )
        answer = { today ->
            val next = if (today == oct24) batches.removeFirstOrNull() else null
            CatchUpBatch.Recorded(
                next?.let { (first, count) ->
                    (0 until count).map { day ->
                        RecordedOccurrence.Payment(
                            origin(id(scenario, 1), first.plusDays(day.toLong())),
                            ExpenseId.of(UUID.randomUUID().toString()).value()
                        )
                    }
                } ?: emptyList()
            )
        }
        val store = store(scenario)
        val runner = runner(scenario, store)
        repeat(3) { runner.step() }

        // The jump itself; here nothing runs catch-up but the step after it.
        assertTrue(runner.step())
        assertEquals(listOf(jul31 to 50), catchUp.asked)
        assertTrue(runner.step())

        assertTrue(runner.state.value.problem, runner.state.value.finished)
        assertEquals(listOf(jul31 to 50, oct24 to 50, oct24 to 50), catchUp.asked)
        assertEquals(
            "Al día: 85 registradas en 2 lotes: 50 del 2026-08-01 al 2026-09-19, " +
                "35 del 2026-09-20 al 2026-10-24",
            runner.state.value.log.last().result
        )
        assertEquals("Al día: nada pendiente", runner.state.value.log[2].result)
        assertEquals(5, store.session.nextStep)
    }

    @Test
    fun aFailedOrUnknownCatchUpStopsAndTheNextTryRunsItAgain() = runTest {
        val scenario = scenario(oct1, CatchUp)
        val answers = ArrayDeque(
            listOf(
                CatchUpBatch.Failed(StorageFailure.Unavailable("x")),
                CatchUpBatch.Unknown,
                CatchUpBatch.Recorded(emptyList())
            )
        )
        answer = { answers.removeFirst() }
        val store = store(scenario)
        val runner = runner(scenario, store)

        assertFalse(runner.step())
        assertTrue(
            runner.state.value.problem,
            runner.state.value.problem!!.startsWith("Falló la puesta al día: Unavailable")
        )
        assertFalse(runner.step())
        assertTrue(
            runner.state.value.problem,
            runner.state.value.problem!!.startsWith("No se sabe si el último lote se guardó")
        )
        assertEquals(0, store.session.nextStep)

        assertTrue(runner.step())
        assertEquals(3, catchUp.asked.size)
        assertEquals(1, store.session.nextStep)
    }

    private fun recordedRent(scenario: Scenario) {
        val schedule = id(scenario, 1)
        val category = id(scenario, 0)
        stored.expenses += expense(
            "00000000-0000-4000-8000-000000000001",
            category,
            oct5,
            950_000,
            "Arriendo",
            origin(schedule, oct5)
        )
        // RE16: the same rent entered by hand, which is never a recorded occurrence.
        stored.expenses += expense(
            "00000000-0000-4000-8000-000000000002",
            category,
            oct5,
            950_000,
            "Arriendo",
            null
        )
        // Another schedule's entry in the same category.
        stored.expenses += expense(
            "00000000-0000-4000-8000-000000000003",
            category,
            LocalDate.of(2026, 10, 10),
            20_000,
            "Arriendo",
            origin("00000000-0000-4000-9000-000000000009", LocalDate.of(2026, 10, 10))
        )
        stored.schedules += StoredSchedule(
            com.software.sello.domain.model.Schedule(
                ScheduleId.of(schedule).value(),
                ScheduledEntry.Payment(
                    CategoryId.of(category).value(),
                    TransactionAmount.of(Money.cop(950_000)).value(),
                    Note.of("Arriendo").value()
                ),
                Recurrence.of(RepeatRule.Monthly.of(5).value(), oct1).value()
            ),
            0,
            Occurrence(oct5, 1),
            1,
            Instant.EPOCH,
            Instant.EPOCH
        )
    }

    private val rentChecks = Verify(
        emptyList(),
        occurrences = listOf(
            OccurrenceCheck("arriendo", oct1, oct24, listOf(Recorded(oct5, 950_000)), "RE16")
        ),
        schedules = listOf(ScheduleCheck("arriendo", Occurrence(oct5, 1), "RE10"))
    )

    @Test
    fun aCheckReadsWhatTheScheduleRecordedByItsOriginAndNeverAManualEntry() = runTest {
        val scenario = scenario(
            oct1,
            Category("hogar", "Hogar", "home", 2_000_000),
            Schedule("arriendo", rent, Every.Month(5)),
            rentChecks,
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        oct1,
                        oct24,
                        listOf(Recorded(oct5, 950_000), Recorded(oct5, 950_000)),
                        "RE16"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck(
                        "arriendo",
                        Occurrence(oct5, 1),
                        "RE21",
                        pausedFrom = LocalDate.of(2026, 10, 3)
                    )
                )
            )
        )
        val runner = runner(scenario, store(scenario))
        repeat(2) { runner.step() }
        recordedRent(scenario)

        assertTrue(runner.step())
        assertEquals(
            listOf(
                "Comprobación: 2 de 2 cifras coinciden",
                "✓ arriendo 2026-10-01 – 2026-10-24: esperado 1 · 950.000: " +
                    "2026-10-05 950.000, real 1 · 950.000: 2026-10-05 950.000 (RE16)",
                "✓ programado arriendo: esperado al día hasta 2026-10-05, activo, " +
                    "real al día hasta 2026-10-05, activo (RE10)"
            ),
            runner.state.value.log.last().result.lines()
        )
        // A manual entry counted as recorded, or a pause that is not there, stops the run.
        assertFalse(runner.step())
        val lines = runner.state.value.log.last().result.lines()
        assertEquals("Comprobación: no coincide", lines[0])
        assertTrue(lines[1], lines[1].startsWith("✗ arriendo"))
        assertTrue(lines[2], lines[2].startsWith("✗ programado arriendo"))
        assertEquals(3, runner.state.value.next)
    }

    @Test
    fun aRestoredHistoryWithoutReceiptsFindsADeletedSchedulesEntriesByNoteAndCategoryName() =
        runTest {
            val scenario = scenario(
                oct1,
                Category("hogar", "Hogar", "home", 2_000_000),
                Schedule("arriendo", rent, Every.Month(5)),
                Verify(
                    emptyList(),
                    occurrences = listOf(
                        OccurrenceCheck(
                            "arriendo",
                            oct1,
                            oct24,
                            listOf(Recorded(oct5, 950_000)),
                            "RE24"
                        )
                    ),
                    schedules = listOf(ScheduleCheck("arriendo", null, "RE24", present = false))
                )
            )
            val first = runner(scenario, store(scenario))
            repeat(2) { first.step() }
            val category = id(scenario, 0)
            // RE24: the schedule is gone; its 5 oct entry stays, beside a manual one.
            stored.expenses += expense(
                "00000000-0000-4000-8000-000000000001",
                category,
                oct5,
                950_000,
                "Arriendo",
                origin(id(scenario, 1), oct5)
            )
            stored.expenses += expense(
                "00000000-0000-4000-8000-000000000002",
                category,
                oct5,
                950_000,
                "Arriendo",
                null
            )
            stored.categories += com.software.sello.domain.model.Category(
                CategoryId.of(category).value(),
                CategoryName.of("Hogar").value(),
                IconKey.of("home").value(),
                false,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            )
            // Restored from a backup into a new process: no receipt survives.
            ledger.receipts.clear()
            val store = InMemorySessionStore(
                SandboxSession(active = true, date = oct1, scenario = scenario.key, nextStep = 2)
            )

            val restored = runner(scenario, store)
            assertTrue(restored.step())
            assertEquals(
                listOf(
                    "Comprobación: 2 de 2 cifras coinciden",
                    "✓ arriendo 2026-10-01 – 2026-10-24: esperado 1 · 950.000: " +
                        "2026-10-05 950.000, real 1 · 950.000: 2026-10-05 950.000 (RE24)",
                    "✓ programado arriendo: esperado borrado, real borrado (RE24)"
                ),
                restored.state.value.log.last().result.lines()
            )
        }

    @Test
    fun theCatchUpLogKeepsEveryBatchThatReachedStorageAndNoEmptyRun() {
        val made = origin("00000000-0000-4000-9000-000000000001", oct5)
        val logged = CatchUpLog()
        logged.record(CatchUpBatch.Recorded(emptyList()))
        logged.record(
            CatchUpBatch.Recorded(
                listOf(
                    RecordedOccurrence.Payment(
                        made,
                        ExpenseId.of("00000000-0000-4000-8000-000000000001").value()
                    )
                )
            )
        )
        logged.record(CatchUpBatch.Unknown)
        logged.record(CatchUpBatch.Failed(StorageFailure.Unavailable("x")))

        assertEquals(
            listOf(
                LoggedBatch.Recorded(1, 1, oct5, oct5),
                LoggedBatch.Unknown(2),
                LoggedBatch.Failed(3, "Unavailable")
            ),
            logged.state.value
        )
    }
}
