package com.software.sello.domain.policy

import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryHistoryMonth
import com.software.sello.domain.policy.Carry.chain
import com.software.sello.domain.policy.Carry.comida
import com.software.sello.domain.policy.Carry.comidaInput
import com.software.sello.domain.policy.Carry.cop
import com.software.sello.domain.policy.Carry.ene
import com.software.sello.domain.policy.Carry.expense
import com.software.sello.domain.policy.Carry.feb
import com.software.sello.domain.policy.Carry.mar
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Category detail once the category carries (ADR 0020 CP19, SELLO-041 ruling 1): the day
 * and the remaining-week figures use `C` and `R`, from the same snapshot rule as Recibo.
 * Figures from SC09 in docs/testing/signed-carryover-examples.md: Comida base 100.000 on
 * from ene, ene spent 20.000, feb spent 30.000; 22 feb, D = 7.
 */
class CategoryInsightsCarryTest {
    private val today = FinancialDay(LocalDate.parse("2027-02-22"), ZoneId.of("America/Bogota"))
    private val comidaLine = CategoryInMonth(
        Carry.category(comida, "Comida"),
        MonthBudgetState.Limited(Carry.finite(100_000), explicit = false)
    )
    private val expenses = listOf(
        expense(comida, "2027-01-10", 20_000),
        expense(comida, "2027-02-05", 30_000)
    )

    // A feb snapshot reads the chain from its start through the month before (SELLO-038
    // consumer boundary); ene, the start month, reads ene alone. Both are ene..ene here.
    private val carry = comidaInput(
        listOf(chain(ene)),
        ene,
        ene,
        "2027-02-22",
        listOf("2027-01-10" to 20_000L)
    )

    private fun read(carryover: (java.time.YearMonth) -> CategoryCarryInput?) =
        CategoryInsightsPolicy.snapshot(
            comida,
            feb,
            today,
            3,
            41,
            CategoryInsightsPolicy.monthsEndingIn(feb).map { period ->
                CategoryHistoryInput(
                    period,
                    comidaLine.takeIf { period >= ene }
                        ?: comidaLine.copy(state = MonthBudgetState.Unconfigured),
                    expenses,
                    carryover(period)
                )
            }
        ).valueOrFail()

    @Test
    fun sc09TheDayAndTheWeekAreTakenFromBasePlusCarry() {
        val insights = read { period -> carry.takeIf { period >= ene } }

        val category = insights.budget.categories.single().budget!!
        assertEquals(cop(180_000), category.limit)
        assertEquals(cop(150_000), category.remaining)
        assertEquals(CarryBreakdown(cop(100_000), cop(80_000), cop(180_000), ene), category.carry)
        // SC09: 50 × floor(150.000 / 350) = 21.400 a day for the 7 days left.
        assertEquals(cop(21_400), insights.allowance?.amount)
        assertEquals(7, insights.allowance?.availableDays)
        // The week of the 22nd to the 28th is the rest of the month: all of R, 150.000.
        val week = insights.weeks.single { it.current }
        assertEquals(cop(150_000), week.remainingAllowance)
    }

    @Test
    fun withoutCarryTheDetailIsTheMvpOne() {
        val insights = read { null }

        val category = insights.budget.categories.single().budget!!
        // Base alone: 100.000 − 30.000 = 70.000; 50 × floor(70.000 / 350) = 10.000.
        assertEquals(cop(100_000), category.limit)
        assertEquals(cop(70_000), category.remaining)
        assertNull(category.carry)
        assertEquals(cop(10_000), insights.allowance?.amount)
        assertEquals(cop(70_000), insights.weeks.single { it.current }.remainingAllowance)
    }

    @Test
    fun sc09HistoryJudgesEachPastChainMonthOnBasePlusCarry() {
        val march = FinancialDay(LocalDate.parse("2027-03-10"), ZoneId.of("America/Bogota"))
        val throughFeb = comidaInput(
            listOf(chain(ene)),
            ene,
            feb,
            "2027-03-10",
            listOf("2027-01-10" to 20_000L, "2027-02-05" to 30_000L)
        )
        val insights = CategoryInsightsPolicy.snapshot(
            comida,
            mar,
            march,
            3,
            41,
            CategoryInsightsPolicy.monthsEndingIn(mar).map { period ->
                CategoryHistoryInput(
                    period,
                    comidaLine.takeIf { period >= ene }
                        ?: comidaLine.copy(state = MonthBudgetState.Unconfigured),
                    expenses,
                    when {
                        period == mar -> throughFeb
                        period >= ene -> carry
                        else -> null
                    }
                )
            }
        ).valueOrFail()

        // ene starts the chain at 0: 100.000. feb carries ene's 80.000: 180.000, so its
        // 30.000 is within, never judged against the 100.000 base alone.
        assertEquals(
            listOf(
                CategoryHistoryMonth(
                    ene,
                    cop(20_000),
                    cop(100_000),
                    CarryBreakdown(cop(100_000), cop(0), cop(100_000), ene)
                ),
                CategoryHistoryMonth(
                    feb,
                    cop(30_000),
                    cop(180_000),
                    CarryBreakdown(cop(100_000), cop(80_000), cop(180_000), ene)
                )
            ),
            insights.history
        )
    }

    @Test
    fun historyWithoutCarryKeepsTheConfiguredLimit() {
        val insights = read { null }

        assertEquals(listOf(cop(100_000)), insights.history.map { it.limit })
        assertEquals(listOf(null), insights.history.map { it.carry })
    }
}
