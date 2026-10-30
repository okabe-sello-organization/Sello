package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.devtools.ScenarioStep.Archive
import com.software.sello.devtools.ScenarioStep.CatchUp
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.DisableCarry
import com.software.sello.devtools.ScenarioStep.EnableCarry
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.SetDefaultLimit
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.CarryoverReads
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DisableCarry as DisableCarryCommand
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.Resets
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UndoOffer
import com.software.sello.domain.usecase.CatchUpNow
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The signed carryover histories of the sandbox (SELLO-042), loaded through the app's own
 * commands on real Room under Robolectric, and the late corrections, restores, resets and
 * restarts made on them through the same commands. Every expected peso is a literal worked
 * out by hand: copied from the signed carryover example named beside it
 * (docs/testing/signed-carryover-examples.md, ADR 0020), or derived by hand from
 * `K(m+1) = K(m) + B(m) − S(m)` and said so. None is computed by the code being checked.
 */
@RunWith(AndroidJUnit4::class)
class CarryoverScenarioTest {
    private var sandbox: JvmSandbox? = null

    @After
    fun end() {
        sandbox?.erase()
    }

    private fun load(scenario: Scenario): JvmSandbox = JvmSandbox(scenario).also {
        sandbox = it
        it.erase()
        it.open()
        it.finish()
        assertEquals(
            scenario.key,
            scenario.today,
            it.koin.get<FinancialClock>().today.value.date
        )
    }

    /** A history opened and stepped to just before step [index], for a test to act there. */
    private fun loadUntil(scenario: Scenario, index: Int): JvmSandbox = JvmSandbox(scenario).also {
        sandbox = it
        it.erase()
        it.open()
        it.stepTo(index)
    }

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** One category in one month: carry-in (null outside a chain), capacity, spent, remaining. */
    private data class Row(
        val carryIn: Long?,
        val capacity: Long,
        val spent: Long,
        val remaining: Long
    )

    private fun JvmSandbox.month(year: Int, month: Int): MonthlySnapshot =
        runBlocking { koin.get<MonthlySnapshots>().read(YearMonth.of(year, month)).value() }

    private fun JvmSandbox.row(year: Int, month: Int, name: String): Row {
        val line = month(year, month).categories.single { it.category.name.value == name }
        val budget = checkNotNull(line.budget) { "$name has no limit in $year-$month" }
        return Row(
            budget.carry?.carryIn?.minorUnits,
            budget.limit.minorUnits,
            line.spent.minorUnits,
            budget.remaining.minorUnits
        )
    }

    private fun JvmSandbox.rowsOf(name: String, vararg months: Pair<Int, Int>) =
        months.map { (year, month) -> row(year, month, name) }

    private fun JvmSandbox.pastVerdict(year: Int, month: Int, name: String): PastVerdict {
        val budget = month(year, month).categories.single { it.category.name.value == name }.budget
        return (checkNotNull(budget).standing as BudgetStanding.Past).verdict
    }

    private fun JvmSandbox.currentVerdict(year: Int, month: Int, name: String): Verdict {
        val budget = month(year, month).categories.single { it.category.name.value == name }.budget
        return (checkNotNull(budget).standing as BudgetStanding.Current).verdict
    }

    private fun MonthlySnapshot.limited() = overall as OverallBudget.Limited

    private fun pesos(amount: Long) = Money.cop(amount)

    private fun operation() = OperationId.of(UUID.randomUUID().toString()).value()

    private fun JvmSandbox.generation(): Long {
        val today = koin.get<FinancialClock>().today.value.date
        return month(today.year, today.monthValue).generation
    }

    private fun JvmSandbox.stepOf(predicate: (ScenarioStep) -> Boolean) =
        scenario.steps.indexOfFirst(predicate).also { assertTrue(scenario.key, it >= 0) }

    private fun JvmSandbox.categoryId(key: String): CategoryId {
        val index = stepOf { it is Category && it.key == key }
        val receipt = runBlocking {
            koin.get<CategoryCommands>().find(OperationId.of(scenario.operation(index)).value())
        }
        return checkNotNull(receipt.value()).categoryId
    }

    /** The expense the history recorded for [pesos] on [on]. */
    private fun JvmSandbox.expenseId(pesos: Long, on: LocalDate): ExpenseId {
        val index = stepOf { it is Expense && it.pesos == pesos && it.on == on }
        val receipt = runBlocking {
            koin.get<ExpenseCommands>().find(OperationId.of(scenario.operation(index)).value())
        }
        return checkNotNull(receipt.value()).expenseId
    }

    private fun JvmSandbox.nameOf(id: CategoryId) =
        rows("SELECT name FROM category WHERE id = '${id.value}'").single()

    private fun amount(pesos: Long) = TransactionAmount.of(pesos(pesos)).value()

    private fun JvmSandbox.create(
        category: String,
        pesos: Long,
        on: LocalDate,
        operation: OperationId = operation(),
        confirmation: CorrectionConfirmation? = null
    ): ExpenseCommandOutcome = runBlocking {
        koin.get<ExpenseCommands>().create(
            CreateExpense(
                operation,
                generation(),
                categoryId(category),
                amount(pesos),
                on,
                null,
                confirmation
            )
        )
    }

    private fun JvmSandbox.delete(
        id: ExpenseId,
        confirmation: CorrectionConfirmation? = null,
        operation: OperationId = operation()
    ): ExpenseCommandOutcome = runBlocking {
        koin.get<ExpenseCommands>().delete(
            DeleteExpense(operation, generation(), id, 1, confirmation)
        )
    }

    private fun JvmSandbox.edit(
        id: ExpenseId,
        category: String,
        pesos: Long,
        on: LocalDate,
        confirmation: CorrectionConfirmation? = null
    ): ExpenseCommandOutcome = runBlocking {
        koin.get<ExpenseCommands>().edit(
            EditExpense(
                operation(),
                generation(),
                id,
                1,
                categoryId(category),
                amount(pesos),
                on,
                Note.of("Mercado").value(),
                confirmation
            )
        )
    }

    private fun ExpenseCommandOutcome.unconfirmed(): ExpenseRejection.CorrectionUnconfirmed =
        ((this as CommandOutcome.Rejected).reason as CommandRejection.Specific<*>).reason
            as ExpenseRejection.CorrectionUnconfirmed

    private fun ExpenseCommandOutcome.committed() = this as CommandOutcome.Committed

    private fun CategoryCommandOutcome.rejection(): CategoryRejection =
        ((this as CommandOutcome.Rejected).reason as CommandRejection.Specific<*>).reason
            as CategoryRejection

    /** A later month of a category that a correction lists: capacity and remaining, before then after. */
    private data class Listed(
        val category: String,
        val month: YearMonth,
        val capacity: Pair<Long, Long>,
        val remaining: Pair<Long, Long>
    )

    private fun JvmSandbox.listed(correction: CarryCorrection) = correction.months
        .map {
            Listed(
                nameOf(it.categoryId),
                it.month,
                it.before.capacity.minorUnits to it.after.capacity.minorUnits,
                it.before.remaining.minorUnits to it.after.remaining.minorUnits
            )
        }
        .sortedWith(compareBy({ it.category }, { it.month }))

    private fun listed(
        category: String,
        month: Int,
        capacity: Pair<Long, Long>,
        remaining: Pair<Long, Long>
    ) = Listed(category, YearMonth.of(2027, month), capacity, remaining)

    // --- Months passing: SC01–SC05, SC41 ---------------------------------------------------

    @Test
    fun consecutiveSkippedAndShortMonthsCarryASurplusAndADeficit() {
        val chains = load(Scenarios.CarryChains)
        val months = arrayOf(2027 to 1, 2027 to 2, 2027 to 3, 2027 to 4)

        // SC01: spent 20.000, 70.000, 250.000; the surplus and then the deficit both carry.
        assertEquals(
            listOf(
                Row(0, 100_000, 20_000, 80_000),
                Row(80_000, 180_000, 70_000, 110_000),
                Row(110_000, 210_000, 250_000, -40_000),
                Row(-40_000, 60_000, 0, 60_000)
            ),
            chains.rowsOf("Comida", *months)
        )
        assertEquals(PastVerdict.WithinLimit, chains.pastVerdict(2027, 2, "Comida"))
        assertEquals(PastVerdict.Over, chains.pastVerdict(2027, 3, "Comida"))
        // SC02: ene 120.000, feb 70.000. mar C is 110.000, not 130.000; mar and abr
        // spend nothing (derived: mar R 110.000 is abr's carry-in).
        assertEquals(
            listOf(
                Row(0, 100_000, 120_000, -20_000),
                Row(-20_000, 80_000, 70_000, 10_000),
                Row(10_000, 110_000, 0, 110_000),
                Row(110_000, 210_000, 0, 210_000)
            ),
            chains.rowsOf("Transporte", *months)
        )
        // SC03: ene 250.000, mar 20.000: feb's deficit is larger than its base.
        assertEquals(
            listOf(
                Row(0, 100_000, 250_000, -150_000),
                Row(-150_000, -50_000, 0, -50_000),
                Row(-50_000, 50_000, 20_000, 30_000),
                Row(30_000, 130_000, 0, 130_000)
            ),
            chains.rowsOf("Hogar", *months)
        )
        assertEquals(PastVerdict.Over, chains.pastVerdict(2027, 2, "Hogar"))
        // SC04: ene 30.000 and nothing after: every skipped month carries its whole base.
        assertEquals(
            listOf(
                Row(0, 100_000, 30_000, 70_000),
                Row(70_000, 170_000, 0, 170_000),
                Row(170_000, 270_000, 0, 270_000),
                Row(270_000, 370_000, 0, 370_000)
            ),
            chains.rowsOf("Salud", *months)
        )
        // SC05: ene 400.000 and nothing after: the deficit is repaid only by later bases.
        assertEquals(
            listOf(
                Row(0, 100_000, 400_000, -300_000),
                Row(-300_000, -200_000, 0, -200_000),
                Row(-200_000, -100_000, 0, -100_000),
                Row(-100_000, 0, 0, 0)
            ),
            chains.rowsOf("Ocio", *months)
        )
        assertEquals(Verdict.OnTrack, chains.currentVerdict(2027, 4, "Ocio"))
    }

    @Test
    fun theOverallBudgetSumsBasesAndCarryAndPorAsignarUsesBasesOnly() {
        val chains = load(Scenarios.CarryChains)

        // April, derived: bases 5 × 100.000 plus the carry-ins −40.000 + 110.000 + 30.000 +
        // 270.000 − 100.000 = 270.000 (CP16) is 770.000 with nothing spent.
        val april = chains.month(2027, 4).limited()
        assertEquals(pesos(770_000), april.budget.limit)
        assertEquals(pesos(770_000), april.budget.remaining)
        // CP17 and SC14: with 1.000.000 of income, Por asignar counts the five bases,
        // 500.000, and leaves 500.000; never the 770.000 capacity (which would leave 230.000).
        val summary = runBlocking {
            chains.koin.get<ResumenSnapshots>().read(YearMonth.of(2027, 4)).value()
        }
        assertEquals(pesos(1_000_000), summary.income)
        assertEquals(pesos(500_000), summary.allocated)
        assertEquals(pesos(500_000), summary.unallocated)
        assertEquals(pesos(1_000_000), summary.netFlow)
        // Resumen's graph, ene–abr, worked out by hand from the categories above. Limit
        // is the capacity Σ B + Σ K: ene 5 × 100.000 + 0; feb 500.000 + (80.000 − 20.000 −
        // 150.000 + 70.000 − 300.000) = 180.000; mar 500.000 + (110.000 + 10.000 − 50.000 +
        // 170.000 − 200.000) = 540.000; abr 500.000 + 270.000 = 770.000. Spent: ene
        // 20.000 + 120.000 + 250.000 + 30.000 + 400.000; feb 70.000 + 70.000; mar 250.000
        // + 20.000; abr nothing.
        val graph = summary.history.associateBy { it.month }
        listOf(
            Triple(1, 500_000L, 820_000L),
            Triple(2, 180_000L, 140_000L),
            Triple(3, 540_000L, 270_000L),
            Triple(4, 770_000L, 0L)
        ).forEach { (month, limit, spent) ->
            val point = graph.getValue(YearMonth.of(2027, month))
            assertEquals("limit $month", pesos(limit), point.limit)
            assertEquals("spent $month", pesos(spent), point.spent)
        }
        // The carry-in behind each capacity is split out beside the bases.
        listOf(1 to 0L, 2 to -320_000L, 3 to 40_000L, 4 to 270_000L).forEach { (month, carry) ->
            val breakdown = checkNotNull(graph.getValue(YearMonth.of(2027, month)).carry)
            assertEquals("base $month", pesos(500_000), breakdown.base)
            assertEquals("carry-in $month", pesos(carry), breakdown.carryIn)
        }
    }

    @Test
    fun aChainCrossesTheNewYearAndALeapFebruary() {
        val leap = load(Scenarios.CarryLeapYear)

        // SC41: dic 90.000 spent, ene 50.000 (derived), so feb K = 60.000 and C = 160.000.
        assertEquals(
            listOf(
                Row(0, 100_000, 90_000, 10_000),
                Row(10_000, 110_000, 50_000, 60_000),
                Row(60_000, 160_000, 0, 160_000)
            ),
            listOf(
                leap.row(2027, 12, "Comida"),
                leap.row(2028, 1, "Comida"),
                leap.row(2028, 2, "Comida")
            )
        )
        // 22 February 2028 has 29 − 22 + 1 = 8 days left: 50 × floor(160.000 / 400) = 20.000.
        val allowance = checkNotNull(leap.month(2028, 2).limited().allowance)
        assertEquals(pesos(20_000), allowance.amount)
        assertEquals(8, allowance.availableDays)
    }

    // --- Opt-in transitions: SC17–SC21 -----------------------------------------------------

    @Test
    fun turningCarryOffArchivingAndMakingUnlimitedDropTheBalanceAndTurningOnStartsAtZero() {
        val transitions = load(Scenarios.CarryTransitions)

        // SC17: Comida ene 120.000 → feb K −20.000, turned off on 10 feb. feb closes with
        // 50.000 spent: mar C is 100.000, not 130.000. ene keeps its figures.
        assertEquals(
            listOf(
                Row(0, 100_000, 120_000, -20_000),
                Row(null, 100_000, 50_000, 50_000),
                Row(null, 100_000, 0, 100_000)
            ),
            transitions.rowsOf("Comida", 2027 to 1, 2027 to 2, 2027 to 3)
        )
        assertEquals(PastVerdict.Over, transitions.pastVerdict(2027, 1, "Comida"))
        // SC19: turned on again on 3 abr, after 15.000 of 1 abr: K 0, C 100.000, the whole
        // month counts (CP2): 15.000 + 20.000 spent, 65.000 left.
        assertEquals(Row(0, 100_000, 35_000, 65_000), transitions.row(2027, 4, "Comida"))
        // SC20: Hogar archived on 12 feb with −20.000 keeps its base in feb, with nothing
        // spent; mar and abr are paused, so the month does not list it.
        assertEquals(Row(null, 100_000, 0, 100_000), transitions.row(2027, 2, "Hogar"))
        listOf(3, 4).forEach { month ->
            assertTrue(
                transitions.month(2027, month).categories.none { it.category.name.value == "Hogar" }
            )
        }
        // SC21: Salud ene 20.000 → +80.000, unlimited from 5 feb: feb is "Sin límite".
        assertEquals(Row(0, 100_000, 20_000, 80_000), transitions.row(2027, 1, "Salud"))
        val salud = transitions.month(2027, 2).categories.single {
            it.category.name.value == "Salud"
        }
        assertNull(salud.budget)
        // SC18: turned off in the month it was turned on, nothing was carried: no chain.
        assertEquals(Row(null, 100_000, 0, 100_000), transitions.row(2027, 4, "Transporte"))
        // SC07: Ocio overspent January by 40.000, which never counts. Turned on on 17 feb,
        // all of feb counts from day 1: 30.000 before and 20.000 after the 17th, so K 0,
        // C 100.000, S 50.000, R 50.000; mar K 50.000, C 150.000 (not 180.000 or 110.000);
        // derived on: abr K 150.000, C 250.000.
        assertEquals(Row(null, 100_000, 140_000, -40_000), transitions.row(2027, 1, "Ocio"))
        assertEquals(Row(0, 100_000, 50_000, 50_000), transitions.row(2027, 2, "Ocio"))
        assertEquals(Row(50_000, 150_000, 0, 150_000), transitions.row(2027, 3, "Ocio"))
        assertEquals(Row(150_000, 250_000, 0, 250_000), transitions.row(2027, 4, "Ocio"))
        // SC19: chains ene–feb (ended feb) and abr onwards; Hogar's and Salud's end in feb.
        assertEquals(
            listOf(
                "Comida|2027-01|2027-02",
                "Comida|2027-04|null",
                "Hogar|2027-01|2027-02",
                "Ocio|2027-02|null",
                "Salud|2027-01|2027-02"
            ),
            transitions.chains()
        )
        // Overall abr (derived): Comida 100.000, Transporte 100.000 and Ocio 250.000 with its
        // carry (Hogar is paused, Salud unlimited), 35.000 spent.
        val overall = transitions.month(2027, 4).limited().budget
        assertEquals(pesos(450_000), overall.limit)
        assertEquals(pesos(415_000), overall.remaining)
    }

    @Test
    fun droppingABalanceShowsTheExactAmountAndAWrongOrMissingConfirmationChangesNothing() {
        val scenario = Scenarios.CarryTransitions
        val salud = scenario.steps.indexOfFirst { it is SetDefaultLimit }
        val comida = scenario.steps.indexOfFirst { it is DisableCarry && it.category == "comida" }
        val archive = scenario.steps.indexOfFirst { it is Archive }
        val transitions = loadUntil(scenario, salud)
        val categories = transitions.koin.get<CategoryCommands>()

        // SC21 (and SC18): +80.000 would be dropped by making Salud unlimited.
        val saludId = transitions.categoryId("salud")
        val unlimited = runBlocking {
            categories.submit(
                com.software.sello.domain.port.SetDefaultLimit(
                    operation(),
                    transitions.generation(),
                    saludId,
                    2,
                    BudgetLimit.Unlimited
                )
            )
        }
        val saludPreview = (unlimited.rejection() as CategoryRejection.DropUnconfirmed).preview
        assertEquals(pesos(80_000), saludPreview.carryIn)
        assertEquals(pesos(180_000), saludPreview.capacityBefore)
        assertEquals(pesos(100_000), saludPreview.base)

        // SC17: Comida shows −20.000, Límite 80.000 → 100.000 and Restante 50.000 → 70.000.
        transitions.stepTo(comida)
        val comidaId = transitions.categoryId("comida")
        fun turnOff(confirmed: Long?) = runBlocking {
            categories.submit(
                DisableCarryCommand(
                    operation(),
                    transitions.generation(),
                    comidaId,
                    2,
                    confirmed?.let { pesos(it) }
                )
            )
        }
        listOf(null, -10_000L, 20_000L).forEach { wrong ->
            val preview = (turnOff(wrong).rejection() as CategoryRejection.DropUnconfirmed).preview
            assertEquals(YearMonth.of(2027, 2), preview.month)
            assertEquals(pesos(-20_000), preview.carryIn)
            assertEquals(pesos(100_000), preview.base)
            assertEquals(pesos(80_000), preview.capacityBefore)
            assertEquals(pesos(100_000), preview.capacityAfter)
            assertEquals(pesos(50_000), preview.remainingBefore)
            assertEquals(pesos(70_000), preview.remainingAfter)
        }
        // Cancelling changes nothing: Comida is still at version 2 with its chain open.
        assertEquals(
            listOf("2"),
            transitions.rows("SELECT version FROM category WHERE name = 'Comida'")
        )
        assertTrue("Comida|2027-01|null" in transitions.chains())

        // SC20: the same exact balance is asked for before archiving Hogar.
        transitions.stepTo(archive)
        val archiveTry = runBlocking {
            categories.submit(
                com.software.sello.domain.port.ArchiveCategory(
                    operation(),
                    transitions.generation(),
                    transitions.categoryId("hogar"),
                    2
                )
            )
        }
        val hogar = (archiveTry.rejection() as CategoryRejection.DropUnconfirmed).preview
        assertEquals(pesos(-20_000), hogar.carryIn)
    }

    // --- Late corrections: SC25–SC34 -------------------------------------------------------

    private val ene15 = LocalDate.of(2027, 1, 15)

    @Test
    fun deletingAJanuaryExpenseListsEveryLaterMonthAndTheSameConfirmedChangeIsNotRepeated() {
        val fixture = load(Scenarios.CarryCorrectable)
        val january = fixture.expenseId(20_000, LocalDate.of(2027, 1, 12))
        val before = fixture.rowsOf("Comida", 2027 to 4)
        assertEquals(listOf(Row(-40_000, 60_000, 0, 60_000)), before)

        // SC25: lists feb 180.000 → 200.000, mar 210.000 → 230.000 (R −40.000 → −20.000),
        // abr 60.000 → 80.000; every base stays 100.000. Remaining is capacity less spent.
        val unconfirmed = fixture.delete(january).unconfirmed()
        assertEquals(
            listOf(
                listed("Comida", 2, 180_000L to 200_000L, 110_000L to 130_000L),
                listed("Comida", 3, 210_000L to 230_000L, -40_000L to -20_000L),
                listed("Comida", 4, 60_000L to 80_000L, 60_000L to 80_000L)
            ),
            fixture.listed(unconfirmed.correction)
        )
        // Nothing is saved before it is confirmed.
        assertEquals(before, fixture.rowsOf("Comida", 2027 to 4))

        val operation = operation()
        val first = fixture.delete(january, unconfirmed.confirmation, operation).committed()
        assertEquals(
            listOf(
                Row(100_000, 200_000, 70_000, 130_000),
                Row(130_000, 230_000, 250_000, -20_000),
                Row(-20_000, 80_000, 0, 80_000)
            ),
            fixture.rowsOf("Comida", 2027 to 2, 2027 to 3, 2027 to 4)
        )
        // A repeated late correction recovers the same receipt and does not cascade twice.
        val again = fixture.delete(january, unconfirmed.confirmation, operation).committed()
        assertTrue(again.replayed)
        assertEquals(first.value, again.value)
        assertEquals(Row(-20_000, 80_000, 0, 80_000), fixture.row(2027, 4, "Comida"))
    }

    @Test
    fun aForgottenJanuaryExpenseAddedLateMakesFebruaryAnOverspendAndAprilBeyondItsBase() {
        val fixture = load(Scenarios.CarryCorrectable)

        // SC30: a 200.000 expense dated in ene. Each later month shifts by −200.000.
        val unconfirmed = fixture.create("comida", 200_000, ene15).unconfirmed()
        assertEquals(
            listOf(
                listed("Comida", 2, 180_000L to -20_000L, 110_000L to -90_000L),
                listed("Comida", 3, 210_000L to 10_000L, -40_000L to -240_000L),
                listed("Comida", 4, 60_000L to -140_000L, 60_000L to -140_000L)
            ),
            fixture.listed(unconfirmed.correction)
        )
        val operation = operation()
        val saved = fixture.create("comida", 200_000, ene15, operation, unconfirmed.confirmation)
            .committed()
        assertEquals(
            listOf(
                Row(0, 100_000, 220_000, -120_000),
                Row(-120_000, -20_000, 70_000, -90_000),
                Row(-90_000, 10_000, 250_000, -240_000),
                Row(-240_000, -140_000, 0, -140_000)
            ),
            fixture.rowsOf("Comida", 2027 to 1, 2027 to 2, 2027 to 3, 2027 to 4)
        )
        assertEquals(PastVerdict.Over, fixture.pastVerdict(2027, 2, "Comida"))
        val recorded = fixture.rows("SELECT COUNT(*) FROM expense").single()

        val again = fixture.create("comida", 200_000, ene15, operation, unconfirmed.confirmation)
            .committed()
        assertTrue(again.replayed)
        assertEquals(saved.value, again.value)
        assertEquals(recorded, fixture.rows("SELECT COUNT(*) FROM expense").single())
        assertEquals(Row(-240_000, -140_000, 0, -140_000), fixture.row(2027, 4, "Comida"))
    }

    @Test
    fun deletingAFebruaryExpenseMovingADateAndCorrectingALimitListTheirOwnMonths() {
        // SC29: the 40.000 expense of feb is deleted: mar 210.000 → 250.000 (R −40.000 → 0),
        // abr 60.000 → 100.000; feb's own R 110.000 → 150.000 is the edited month.
        val deleted = load(Scenarios.CarryCorrectable)
        val feb = deleted.delete(deleted.expenseId(40_000, LocalDate.of(2027, 2, 19))).unconfirmed()
        assertEquals(
            listOf(
                listed("Comida", 3, 210_000L to 250_000L, -40_000L to 0L),
                listed("Comida", 4, 60_000L to 100_000L, 60_000L to 100_000L)
            ),
            deleted.listed(feb.correction)
        )
        val editedFeb = feb.correction.edited.single()
        assertEquals(YearMonth.of(2027, 2), editedFeb.month)
        assertEquals(
            110_000L to 150_000L,
            editedFeb.before.remaining.minorUnits to
                editedFeb.after.remaining.minorUnits
        )

        // SC27: the 20.000 expense of ene moves to mar: feb 180.000 → 200.000 and mar
        // 210.000 → 230.000 with 270.000 spent, so mar R stays −40.000; abr is not listed.
        val moved = load(Scenarios.CarryCorrectable)
        val january = moved.expenseId(20_000, LocalDate.of(2027, 1, 12))
        val toMarch = moved.edit(january, "comida", 20_000, LocalDate.of(2027, 3, 5)).unconfirmed()
        assertEquals(
            listOf(
                listed("Comida", 2, 180_000L to 200_000L, 110_000L to 130_000L),
                listed("Comida", 3, 210_000L to 230_000L, -40_000L to -40_000L)
            ),
            moved.listed(toMarch.correction)
        )

        // SC28: ene's own limit 100.000 → 120.000: feb, mar and abr each gain 20.000 and
        // the base of feb and mar stays 100.000.
        val limit = load(Scenarios.CarryCorrectable)
        val comida = limit.categoryId("comida")
        fun correct(confirmation: CorrectionConfirmation?) = runBlocking {
            limit.koin.get<CategoryCommands>().submit(
                SetMonthLimit(
                    operation(),
                    limit.generation(),
                    comida,
                    2,
                    YearMonth.of(2027, 1),
                    BudgetLimit.finite(pesos(120_000)).value(),
                    null,
                    confirmation
                )
            )
        }
        val corrected = correct(null).rejection() as CategoryRejection.CorrectionUnconfirmed
        assertEquals(
            listOf(
                listed("Comida", 2, 180_000L to 200_000L, 110_000L to 130_000L),
                listed("Comida", 3, 210_000L to 230_000L, -40_000L to -20_000L),
                listed("Comida", 4, 60_000L to 80_000L, 60_000L to 80_000L)
            ),
            limit.listed(corrected.correction)
        )
        assertTrue(correct(corrected.confirmation) is CommandOutcome.Committed)
        assertEquals(Row(0, 120_000, 20_000, 100_000), limit.row(2027, 1, "Comida"))
        val marchBase = limit.month(2027, 3).categories
            .single { it.category.name.value == "Comida" }.budget?.base
        assertEquals(pesos(100_000), marchBase)
    }

    @Test
    fun movingAnExpenseBetweenTwoChainsListsBothAndLeavesTheOverallCapacityAlone() {
        val fixture = load(Scenarios.CarryCorrectable)
        val feb40 = fixture.expenseId(40_000, LocalDate.of(2027, 2, 19))

        // Derived by hand (CP21, not an approved example): the 40.000 of feb moves from
        // Comida to Transporte (base 50.000, ene spent 10.000). Comida gains 40.000 in mar
        // and abr; Transporte, whose feb K is 40.000, loses 40.000 in mar and abr.
        val unconfirmed = fixture.edit(feb40, "transporte", 40_000, LocalDate.of(2027, 2, 19))
            .unconfirmed()
        assertEquals(
            listOf(
                listed("Comida", 3, 210_000L to 250_000L, -40_000L to 0L),
                listed("Comida", 4, 60_000L to 100_000L, 60_000L to 100_000L),
                listed("Transporte", 3, 140_000L to 100_000L, 140_000L to 100_000L),
                listed("Transporte", 4, 190_000L to 150_000L, 190_000L to 150_000L)
            ),
            fixture.listed(unconfirmed.correction)
        )
        // +40.000 and −40.000 cancel: the overall capacity of no month changes.
        assertTrue(unconfirmed.correction.overallCapacityChange.isEmpty())
    }

    @Test
    fun anExpenseOfTheCurrentMonthNeedsNoListAndAChangeInBetweenMakesAnOldConfirmationStale() {
        val fixture = load(Scenarios.CarryCorrectable)
        val today = fixture.scenario.today
        val january = fixture.expenseId(20_000, LocalDate.of(2027, 1, 12))

        val first = fixture.delete(january).unconfirmed()
        // SC31: 15.000 on 10 abr is saved at once; abr C stays 60.000 and R is 45.000.
        fixture.create("comida", 15_000, today).committed()
        assertEquals(Row(-40_000, 60_000, 15_000, 45_000), fixture.row(2027, 4, "Comida"))

        // SC34: the old confirmation is refused, nothing changes and the list is shown again
        // with a newer confirmation.
        val stale = fixture.delete(january, first.confirmation).unconfirmed()
        assertNotEquals(first.confirmation, stale.confirmation)
        assertTrue(stale.confirmation.revision > first.confirmation.revision)
        assertEquals(
            fixture.listed(first.correction).map { it.month to it.capacity },
            fixture.listed(stale.correction).map { it.month to it.capacity }
        )
        assertEquals(Row(-40_000, 60_000, 15_000, 45_000), fixture.row(2027, 4, "Comida"))

        fixture.delete(january, stale.confirmation).committed()
        assertEquals(Row(-20_000, 80_000, 15_000, 65_000), fixture.row(2027, 4, "Comida"))
    }

    @Test
    fun aCorrectionMadeInTheScriptIsListedAndConfirmedBeforeItIsSaved() {
        val corrected = load(Scenarios.CarryCorrected)

        // SC30 as a step of the history: the same figures as the correction made by hand.
        assertEquals(
            listOf(
                Row(0, 100_000, 220_000, -120_000),
                Row(-120_000, -20_000, 70_000, -90_000),
                Row(-90_000, 10_000, 250_000, -240_000),
                Row(-240_000, -140_000, 0, -140_000)
            ),
            corrected.rowsOf("Comida", 2027 to 1, 2027 to 2, 2027 to 3, 2027 to 4)
        )
        assertEquals(listOf("Comida|2027-01|null"), corrected.chains())
    }

    // --- Catch-up inside a chain: SC37, SC47 -----------------------------------------------

    /** The expense catch-up recorded for the "Cuota" schedule, and the day it is dated. */
    private fun JvmSandbox.recordedPayment(): ExpenseId {
        assertEquals(
            listOf("2027-02-05"),
            rows("SELECT effective_date FROM expense WHERE note = 'Cuota'")
        )
        return ExpenseId.of(rows("SELECT id FROM expense WHERE note = 'Cuota'").single()).value()
    }

    private fun JvmSandbox.notice(payment: ExpenseId) = runBlocking {
        koin.get<CarryoverReads>().catchUpNotice(listOf(payment)).value()
    }

    @Test
    fun aPaymentOfAnEarlierMonthRecordedByCatchUpLowersTheCarryOfEveryLaterMonth() {
        // SC37: ene spent 1.000.000, feb 800.000 and a 200.000 payment due 5 feb that Sello
        // records on 10 mar (D = 22), with 500.000 spent in March. The app runs the catch-up
        // itself when the day changes, so the history ends after it.
        val payments = load(Scenarios.CarryPayments)

        // feb R 0 and mar K 0, C 1.000.000, R 500.000, nothing reserved, 22.700 a day.
        assertEquals(Row(0, 1_000_000, 1_000_000, 0), payments.row(2027, 2, "Hogar"))
        assertEquals(PastVerdict.WithinLimit, payments.pastVerdict(2027, 2, "Hogar"))
        assertEquals(Row(0, 1_000_000, 500_000, 500_000), payments.row(2027, 3, "Hogar"))
        val march = payments.month(2027, 3).limited()
        assertNull(march.reservation)
        assertEquals(pesos(22_700), march.allowance?.amount)

        // The opening notice names Hogar: feb Restante 200.000 → 0, mar Límite con arrastre
        // 1.200.000 → 1.000.000 (−200.000).
        val hogar = payments.notice(payments.recordedPayment()).categories.single()
        assertEquals(
            listOf(listed("Hogar", 3, 1_200_000L to 1_000_000L, 700_000L to 500_000L)),
            payments.listed(hogar)
        )
        val feb = hogar.edited.single()
        assertEquals(YearMonth.of(2027, 2), feb.month)
        assertEquals(
            200_000L to 0L,
            feb.before.remaining.minorUnits to feb.after.remaining.minorUnits
        )
    }

    @Test
    fun aLargerPaymentTurnsFebruaryIntoAnOverspendAndLowersMarchByTheSameAmount() {
        // SC47: as SC37 but the payment is 300.000, so feb spends 1.100.000.
        val payments = load(Scenarios.CarryBigPayments)

        // feb R 200.000 → −100.000, now Excedido; mar K −100.000, C 900.000 (−300.000);
        // mar R 900.000 − 500.000 = 400.000 (derived), 50 × floor(400.000 / 1.100) = 18.150.
        assertEquals(Row(0, 1_000_000, 1_100_000, -100_000), payments.row(2027, 2, "Hogar"))
        assertEquals(PastVerdict.Over, payments.pastVerdict(2027, 2, "Hogar"))
        assertEquals(Row(-100_000, 900_000, 500_000, 400_000), payments.row(2027, 3, "Hogar"))
        assertEquals(pesos(18_150), payments.month(2027, 3).limited().allowance?.amount)
        val hogar = payments.notice(payments.recordedPayment()).categories.single()
        assertEquals(
            listOf(listed("Hogar", 3, 1_200_000L to 900_000L, 700_000L to 400_000L)),
            payments.listed(hogar)
        )
        // The notice's feb row: Restante 200.000 → −100.000, and Cumplido becomes Excedido.
        val feb = hogar.edited.single()
        assertEquals(YearMonth.of(2027, 2), feb.month)
        assertEquals(
            200_000L to -100_000L,
            feb.before.remaining.minorUnits to feb.after.remaining.minorUnits
        )
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), feb.before.verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), feb.after.verdict)
    }

    // --- Resume, restore and reset ---------------------------------------------------------

    /** Loads [scenario] straight through, then again with step [index] sent twice. */
    private fun assertStepSentAgainChangesNothing(scenario: Scenario, index: Int) {
        val straight = load(scenario)
        val expected = straight.history()
        straight.erase()

        // The step committed, then the process ended before its checkpoint was written.
        val resumed = JvmSandbox(scenario).also { sandbox = it }
        resumed.open()
        resumed.stepTo(index + 1)
        resumed.store.session = resumed.store.session.copy(nextStep = index)
        resumed.open()
        resumed.finish()

        assertEquals("${scenario.key} step ${index + 1}", expected, resumed.history())
        assertNull(resumed.runner?.state?.value?.problem)
        resumed.erase()
    }

    @Test
    fun aCarryStepThatCommittedBeforeItsCheckpointIsAnsweredFromItsReceiptNotSentAgain() {
        val transitions = Scenarios.CarryTransitions
        // Turning off a second time would be refused as "not carrying"; archiving or
        // unlimiting a second time would find no balance to drop. Each is answered by its
        // receipt, and the chain stays as the first send left it.
        listOf(
            transitions.steps.indexOfFirst { it is DisableCarry && it.category == "comida" },
            transitions.steps.indexOfFirst { it is Archive },
            transitions.steps.indexOfFirst { it is SetDefaultLimit },
            transitions.steps.indexOfLast { it is EnableCarry }
        ).forEach { assertStepSentAgainChangesNothing(transitions, it) }

        // A correction confirmed in the script is found by its receipt too: it does not
        // cascade a second time.
        val corrected = Scenarios.CarryCorrected
        assertStepSentAgainChangesNothing(
            corrected,
            corrected.steps.indexOfFirst { it is Expense && it.confirmsCorrection }
        )
    }

    @Test
    fun aLoadCutInTheMiddleOfAChainCarriesOnWithoutResettingTheCarry() {
        val scenario = Scenarios.CarryChains
        val march = scenario.steps.indexOfFirst { it is Expense && it.on?.monthValue == 3 }
        val first = JvmSandbox(scenario).also { sandbox = it }
        first.erase()
        first.open()
        first.stepTo(march)
        // A new process on the stored progress: February's carry is as the first left it.
        first.open()
        assertEquals(Row(80_000, 180_000, 70_000, 110_000), first.row(2027, 2, "Comida"))
        first.finish()

        assertEquals(Row(-40_000, 60_000, 0, 60_000), first.row(2027, 4, "Comida"))
        assertEquals(
            listOf(
                "Comida|2027-01|null",
                "Hogar|2027-01|null",
                "Ocio|2027-01|null",
                "Salud|2027-01|null",
                "Transporte|2027-01|null"
            ),
            first.chains()
        )
        assertEquals(listOf("9"), first.rows("SELECT COUNT(*) FROM expense"))
    }

    @Test
    fun aBackupKeepsTheChainsAndTheRestoredHistoryReplaysTheSameCarry() {
        val original = load(Scenarios.CarryChains)
        val chains = original.chains()
        val bytes = runBlocking { original.koin.get<BackupExports>().prepare().value().bytes }

        // A new installation holding nothing yet: the file holds the chains, not the −40.000.
        original.erase()
        original.store.session =
            original.store.session.copy(nextStep = original.scenario.steps.size)
        original.open()
        val restores = original.koin.get<Restores>()
        val restored = runBlocking { restores.replace(restores.stage(bytes).value()) }

        assertTrue("$restored", restored is RestoreOutcome.Restored)
        assertEquals(chains, original.chains())
        // SC43: replay gives SC01 again.
        assertEquals(
            Row(-40_000, 60_000, 0, 60_000),
            original.row(2027, 4, "Comida")
        )
        assertEquals(Row(110_000, 210_000, 250_000, -40_000), original.row(2027, 3, "Comida"))
    }

    @Test
    fun aRestoreInterruptedAfterStagingIsFinishedByTheNextProcessWithTheChainsIntact() {
        val original = load(Scenarios.CarryTransitions)
        val chains = original.chains()
        val bytes = runBlocking { original.koin.get<BackupExports>().prepare().value().bytes }
        original.erase()
        original.store.session =
            original.store.session.copy(nextStep = original.scenario.steps.size)
        original.open()
        runBlocking { original.koin.get<Restores>().stage(bytes).value() }

        // The process ends with the file staged and not yet confirmed.
        original.open()
        val restores = original.koin.get<Restores>()
        val staged = checkNotNull(runBlocking { restores.staged() })
        val restored = runBlocking { restores.replace(staged) }

        assertTrue("$restored", restored is RestoreOutcome.Restored)
        assertEquals(chains, original.chains())
        assertEquals(Row(0, 100_000, 35_000, 65_000), original.row(2027, 4, "Comida"))
        assertEquals(Row(null, 100_000, 50_000, 50_000), original.row(2027, 2, "Comida"))
    }

    @Test
    fun restoringAFileWithoutChainsOverOneThatHasThemLeavesEveryCategoryOff() {
        val plain = load(Scenarios.TightMonth)
        val bytes = runBlocking { plain.koin.get<BackupExports>().prepare().value().bytes }
        plain.erase()

        // SC45: the sandbox holds carrying categories; the file has none.
        val carrying = load(Scenarios.CarryChains)
        assertEquals(5, carrying.chains().size)
        val restores = carrying.koin.get<Restores>()
        val restored = runBlocking { restores.replace(restores.stage(bytes).value()) }

        assertTrue("$restored", restored is RestoreOutcome.Restored)
        assertEquals(emptyList<String>(), carrying.chains())
        assertEquals(listOf("0"), carrying.rows("SELECT COUNT(*) FROM carry_chain"))
    }

    @Test
    fun aResetErasesEveryChainAndALoadCutByItIsNotCarriedOnIntoTheEmptyHistory() {
        val scenario = Scenarios.CarryChains
        val march = scenario.steps.indexOfFirst { it is Expense && it.on?.monthValue == 3 }
        val cut = loadUntil(scenario, march)
        assertEquals(5, cut.chains().size)

        // SC45: the app's own reset.
        val resets = cut.koin.get<Resets>()
        val preview = runBlocking { resets.preview().value() }
        val erased = runBlocking { resets.reset(preview, operation()) }
        assertTrue("$erased", erased is ResetOutcome.Erased)
        assertEquals(emptyList<String>(), cut.chains())
        assertEquals(listOf("0"), cut.rows("SELECT COUNT(*) FROM category"))

        // The next process, asked to carry the load on, finds its history gone and writes
        // nothing into the empty one.
        cut.open()
        cut.store.session = cut.store.session.copy(loading = true)
        val scope = CoroutineScope(Dispatchers.Default)
        val runner = cut.runnerOn(cut.generation(), scope)
        assertEquals(march, runner.state.value.next)
        runner.resume()
        runBlocking { runner.load().join() }
        assertEquals(
            "La historia que se estaba cargando se borró; cárgala de nuevo.",
            runner.state.value.problem
        )
        assertEquals(march, runner.state.value.next)
        assertEquals(emptyList<String>(), cut.chains())
        assertEquals(listOf("0"), cut.rows("SELECT COUNT(*) FROM expense"))
        scope.cancel()
    }

    // --- Audit time and undo windows -------------------------------------------------------

    @Test
    fun steppingTheFinancialClockLeavesAuditStampsInRealTime() {
        val before = System.currentTimeMillis()
        val corrected = load(Scenarios.CarryCorrected)
        val after = System.currentTimeMillis()

        // The history spans January to April 2027; its records were stamped when written.
        val (oldest, newest) = corrected.rows(
            "SELECT MIN(created_at), MAX(updated_at) FROM expense"
        )
            .single().split("|").map { it.toLong() }
        assertTrue("$oldest in $before..$after", oldest in before..after)
        assertTrue("$newest in $before..$after", newest in before..after)
        val (categoryCreated, categoryUpdated) =
            corrected.rows("SELECT MIN(created_at), MAX(updated_at) FROM category")
                .single().split("|").map { it.toLong() }
        assertTrue(categoryCreated in before..after)
        assertTrue(categoryUpdated in before..after)
    }

    /** A delete of a new April expense, and the undo window it opens on the controlled clock. */
    private fun JvmSandbox.deleteAnExpense(): UndoOffer {
        val saved = create("comida", 15_000, scenario.today).committed().value
        delete(saved.expenseId).committed()
        return checkNotNull(koin.get<ExpenseCommands>().undoOffer())
    }

    private fun JvmSandbox.restore(offer: UndoOffer, confirmation: CorrectionConfirmation?) =
        runBlocking {
            koin.get<ExpenseCommands>().restore(
                RestoreExpense(operation(), generation(), offer.deletionId, confirmation)
            )
        }

    @Test
    fun anUndoWindowOpenedBeforeTheFinancialClockMovesIsUntouchedByIt() {
        val fixture = load(Scenarios.CarryCorrectable)
        val offer = fixture.deleteAnExpense()
        val elapsed = fixture.koin.get<MonotonicClock>().elapsed()
        val audit = fixture.rows("SELECT id, created_at, updated_at FROM expense ORDER BY id")

        // Two financial months pass at once.
        (fixture.koin.get<FinancialClock>() as ControlledFinancialClock).advanceMonths(2)

        assertEquals(elapsed, fixture.koin.get<MonotonicClock>().elapsed())
        assertEquals(offer, fixture.koin.get<ExpenseCommands>().undoOffer())
        assertEquals(
            audit,
            fixture.rows("SELECT id, created_at, updated_at FROM expense ORDER BY id")
        )
        // April is now a closed month of the chain, so putting the expense back is a
        // correction that lists the later months; it is not an expired undo.
        val listed = fixture.restore(offer, null).unconfirmed()
        fixture.restore(offer, listed.confirmation).committed()
        // abr: C 60.000 less 15.000 spent leaves 45.000 for may (derived).
        assertEquals(Row(45_000, 145_000, 0, 145_000), fixture.row(2027, 5, "Comida"))
    }

    @Test
    fun anUndoWindowEndsOnlyWhenTheMonotonicClockPassesIt() {
        val fixture = load(Scenarios.CarryCorrectable)
        val offer = fixture.deleteAnExpense()
        (fixture.koin.get<FinancialClock>() as ControlledFinancialClock).advanceMonths(2)

        // The 6 s window is real elapsed time, here moved by the test: still open one
        // millisecond before it ends, expired at its end, whatever month it is.
        fixture.monotonic.now = offer.until - 1.milliseconds
        assertTrue(fixture.restore(offer, null).unconfirmed().correction.months.isNotEmpty())
        fixture.monotonic.now = offer.until
        val expired = (fixture.restore(offer, null) as CommandOutcome.Rejected).reason
        assertEquals(CommandRejection.Specific(ExpenseRejection.UndoExpired), expired)
    }
}
