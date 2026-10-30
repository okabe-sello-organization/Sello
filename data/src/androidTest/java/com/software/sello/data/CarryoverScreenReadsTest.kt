package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateIncome
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Resumen and category detail read carry from a real database file, like Recibo (ADR 0020
 * CP16, CP19; SELLO-041 ruling 1). Expected amounts are copied from
 * docs/testing/signed-carryover-examples.md (SC09, SC13); none is calculated here.
 */
@RunWith(AndroidJUnit4::class)
class CarryoverScreenReadsTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val histories by lazy { CarryHistories(ledger) }

    private fun cop(pesos: Long) = Money.cop(pesos)

    private val feb = YearMonth.parse("2027-02")

    /**
     * SC13: Comida (100) base 100.000 on from ene; Transporte (101) base 50.000, off; Ocio
     * (102) unlimited. ene: Comida 20.000. feb: Comida 30.000, Transporte 60.000, Ocio 10.000;
     * income 200.000. Today 22 feb. With [carry] false, the same history with carry off.
     */
    private suspend fun sc13(carry: Boolean = true) {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        if (carry) histories.enable(comida, version = 1)
        val transporte = histories.category("Transporte", 50_000, "2027-01-05")
        ledger.goTo("2027-01-05")
        val ocio = ledger.nextRecord
        ledger.categories.submit(ledger.createCategory("Ocio", BudgetLimit.Unlimited))
        histories.spend(comida, "2027-01-10", 20_000)
        histories.spend(comida, "2027-02-05", 30_000)
        histories.spend(transporte, "2027-02-06", 60_000)
        histories.spend(ocio, "2027-02-07", 10_000)
        ledger.goTo("2027-02-08")
        val income = ledger.incomes.create(
            CreateIncome(
                operationId = ledger.operation(),
                generation = 1,
                amount = TransactionAmount.of(cop(200_000)).valueOrFail(),
                source = IncomeSource.Salary,
                date = LocalDate.parse("2027-02-08"),
                note = Note.of(null).valueOrFail()
            )
        )
        check(income is CommandOutcome.Committed) { "$income" }
        ledger.goTo("2027-02-22")
    }

    @Test
    fun sc13ResumenJudgesTheMonthOnBasePlusCarryAndAssignsIncomeOnBasesOnly() = runBlocking {
        sc13()

        val resumen = ledger.resumen.read(feb).valueOrFail()

        val overall = (resumen.budget.overall as OverallBudget.Limited).budget
        assertEquals(cop(230_000), overall.limit)
        assertEquals(CarryBreakdown(cop(150_000), cop(80_000), cop(230_000), null), overall.carry)
        assertEquals(cop(130_000), overall.remaining)
        assertEquals(
            cop(18_550),
            (resumen.budget.overall as OverallBudget.Limited).allowance?.amount
        )
        // SC14: Por asignar 200.000 − 150.000 = 50.000; Ingresos menos gastos 100.000.
        assertEquals(cop(150_000), resumen.allocated)
        assertEquals(cop(50_000), resumen.unallocated)
        assertEquals(cop(100_000), resumen.netFlow)
        // The month's column is drawn against the capacity (CP19).
        assertEquals(cop(230_000), resumen.history.last().limit)
    }

    @Test
    fun sc09CategoryDetailTakesItsDayAndWeekFromBasePlusCarry() = runBlocking {
        sc13()

        val insights = ledger.categoryInsights.read(ledger.categoryId(100), feb).valueOrFail()

        val comida = insights.budget.categories.single().budget!!
        assertEquals(cop(180_000), comida.limit)
        assertEquals(cop(150_000), comida.remaining)
        assertEquals(
            CarryBreakdown(cop(100_000), cop(80_000), cop(180_000), YearMonth.parse("2027-01")),
            comida.carry
        )
        assertEquals(cop(21_400), insights.allowance?.amount)
        assertEquals(cop(150_000), insights.weeks.single { it.current }.remainingAllowance)
    }

    @Test
    fun withoutAChainResumenAndDetailAreTheMvpOnes() = runBlocking {
        sc13(carry = false)

        val resumen = ledger.resumen.read(feb).valueOrFail()
        val insights = ledger.categoryInsights.read(ledger.categoryId(100), feb).valueOrFail()

        // Bases only: 150.000 − 100.000 spent; Comida 100.000 − 30.000 = 70.000, 10.000 a day.
        val overall = (resumen.budget.overall as OverallBudget.Limited).budget
        assertEquals(cop(150_000), overall.limit)
        assertEquals(cop(50_000), overall.remaining)
        assertNull(overall.carry)
        assertEquals(cop(50_000), resumen.unallocated)
        val comida = insights.budget.categories.single().budget!!
        assertEquals(cop(70_000), comida.remaining)
        assertNull(comida.carry)
        assertEquals(cop(10_000), insights.allowance?.amount)
    }

    @Test
    fun aChainThatCannotBeCalculatedFailsBothReadsNamingItsCategory() = runBlocking {
        histories.sc01()
        // Damage no command can make (CP13): feb, inside the chain, unlimited.
        ledger.database.insertRaw(
            "month_limit",
            "category_id" to uuid(100),
            "month" to "2027-02",
            "kind" to "unlimited",
            "limit_minor" to null,
            "currency" to "COP"
        )

        val failure = Outcome.Failure(
            SnapshotFailure.Carryover(
                CarryoverFailure.NoFiniteBase(YearMonth.parse("2027-02")),
                ledger.categoryId(100)
            )
        )
        val april = YearMonth.parse("2027-04")
        assertEquals(failure, ledger.resumen.read(april))
        assertEquals(failure, ledger.categoryInsights.read(ledger.categoryId(100), april))
        assertTrue(ledger.snapshots.read(april) == failure)
    }
}
