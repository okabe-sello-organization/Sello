package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryHistoryMonth
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Forecast
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.pesos
import com.software.sello.domain.valueOrFail
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The feature nudges' triggers (SELLO-212), with hand-worked days and amounts. */
class FeatureNudgePolicyTest {
    private val bogota = ZoneId.of("America/Bogota")
    private var next = 0
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val comida = CategoryId.of(uuid(1)).valueOrFail()
    private val casa = CategoryId.of(uuid(2)).valueOrFail()
    private val schedule = ScheduleId.of(uuid(3)).valueOrFail()

    /** Bogotá is UTC−5: 2026-11-08 there runs from 05:00Z on the 8th to 05:00Z on the 9th. */
    private val today = FinancialDay(LocalDate.parse("2026-11-08"), bogota)

    private fun expense(
        date: String,
        pesos: Long,
        note: String? = null,
        category: CategoryId = comida,
        added: String = "${date}T15:00:00Z",
        byPagoFijo: Boolean = false
    ): Expense {
        next += 1
        val at = Instant.parse(added)
        return Expense(
            ExpenseId.of(uuid(100 + next)).valueOrFail(),
            category,
            LocalDate.parse(date),
            next.toLong(),
            pesos(pesos),
            Note.of(note).valueOrFail(),
            1,
            at,
            at,
            origin = if (byPagoFijo) {
                EntryOrigin(schedule, Occurrence(LocalDate.parse(date), 1))
            } else {
                null
            }
        )
    }

    private fun payment(pesos: Long, note: String?, category: CategoryId = comida) = Schedule(
        schedule,
        ScheduledEntry.Payment(category, pesos(pesos), Note.of(note).valueOrFail()),
        Recurrence.of(RepeatRule.Monthly.of(5).valueOrFail(), LocalDate.parse("2026-09-05"))
            .valueOrFail()
    )

    // Anotar varios

    @Test
    fun threeExpensesAddedTodayByHandPointAtAnotarVarios() {
        val expenses = listOf(
            expense("2026-11-08", 4_500),
            expense("2026-11-08", 12_000),
            expense("2026-11-08", 8_000)
        )
        assertTrue(FeatureNudgePolicy.bulkEntryDue(expenses, today))
    }

    @Test
    fun twoAreNotEnough() {
        val expenses = listOf(expense("2026-11-08", 4_500), expense("2026-11-08", 12_000))
        assertFalse(FeatureNudgePolicy.bulkEntryDue(expenses, today))
    }

    @Test
    fun oneRecordedByAPagoFijoDoesNotCount() {
        val expenses = listOf(
            expense("2026-11-08", 4_500),
            expense("2026-11-08", 12_000),
            expense("2026-11-08", 80_000, byPagoFijo = true)
        )
        assertFalse(FeatureNudgePolicy.bulkEntryDue(expenses, today))
    }

    @Test
    fun oneDatedTodayButAddedYesterdayDoesNotCount() {
        // 03:00Z on the 8th is 22:00 on the 7th in Bogotá: added yesterday, dated today.
        val expenses = listOf(
            expense("2026-11-08", 4_500),
            expense("2026-11-08", 12_000),
            expense("2026-11-08", 8_000, added = "2026-11-08T03:00:00Z")
        )
        assertFalse(FeatureNudgePolicy.bulkEntryDue(expenses, today))
    }

    @Test
    fun oneAddedTodayButDatedYesterdayDoesNotCount() {
        val expenses = listOf(
            expense("2026-11-08", 4_500),
            expense("2026-11-08", 12_000),
            expense("2026-11-07", 8_000, added = "2026-11-08T15:00:00Z")
        )
        assertFalse(FeatureNudgePolicy.bulkEntryDue(expenses, today))
    }

    @Test
    fun theDayIsTheFinancialZonesNotUtcs() {
        // 04:30Z on the 9th is still 23:30 on the 8th in Bogotá.
        val expenses = listOf(
            expense("2026-11-08", 4_500),
            expense("2026-11-08", 12_000),
            expense("2026-11-08", 8_000, added = "2026-11-09T04:30:00Z")
        )
        assertTrue(FeatureNudgePolicy.bulkEntryDue(expenses, today))
    }

    // Pagos fijos

    private val november = YearMonth.of(2026, 11)

    @Test
    fun theSameAmountAndNoteInEachOfTheTwoMonthsBeforeIsAPagoFijo() {
        val expenses = listOf(
            expense("2026-09-05", 44_900, "Netflix"),
            expense("2026-10-05", 44_900, "Netflix")
        )
        assertTrue(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun notesCompareAsSearchFoldsThem() {
        val expenses = listOf(
            expense("2026-09-05", 120_000, "Gimnasio "),
            expense("2026-10-06", 120_000, "GIMNASIO")
        )
        assertTrue(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun aDifferentAmountIsNotTheSamePayment() {
        val expenses = listOf(
            expense("2026-09-05", 44_900, "Netflix"),
            expense("2026-10-05", 49_900, "Netflix")
        )
        assertFalse(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun onlyOneOfTheTwoMonthsIsNotEnough() {
        val expenses = listOf(
            expense("2026-08-05", 44_900, "Netflix"),
            expense("2026-10-05", 44_900, "Netflix"),
            expense("2026-10-20", 44_900, "Netflix")
        )
        assertFalse(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun expensesWithoutANoteNeverCount() {
        val expenses = listOf(expense("2026-09-05", 44_900), expense("2026-10-05", 44_900))
        assertFalse(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun anotherCategorysExpensesDoNotCount() {
        val expenses = listOf(
            expense("2026-09-05", 44_900, "Netflix"),
            expense("2026-10-05", 44_900, "Netflix", category = casa)
        )
        assertFalse(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun expensesAPagoFijoRecordedDoNotCount() {
        val expenses = listOf(
            expense("2026-09-05", 44_900, "Netflix", byPagoFijo = true),
            expense("2026-10-05", 44_900, "Netflix", byPagoFijo = true)
        )
        assertFalse(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, emptyList()))
    }

    @Test
    fun aPagoFijoWithThatAmountAndNoteAlreadyCoversIt() {
        val expenses = listOf(
            expense("2026-09-05", 44_900, "Netflix"),
            expense("2026-10-05", 44_900, "Netflix")
        )
        assertFalse(
            FeatureNudgePolicy.fixedPaymentDue(
                expenses,
                comida,
                november,
                listOf(payment(44_900, "netflix"))
            )
        )
    }

    @Test
    fun aPagoFijoOfAnotherCategoryOrAmountDoesNotCoverIt() {
        val expenses = listOf(
            expense("2026-09-05", 44_900, "Netflix"),
            expense("2026-10-05", 44_900, "Netflix")
        )
        val others = listOf(payment(44_900, "Netflix", category = casa), payment(49_900, "Netflix"))
        assertTrue(FeatureNudgePolicy.fixedPaymentDue(expenses, comida, november, others))
    }

    @Test
    fun theTwoMonthsBeforeCrossTheYear() {
        val expenses = listOf(
            expense("2025-11-30", 60_000, "Celular"),
            expense("2025-12-01", 60_000, "Celular")
        )
        assertTrue(
            FeatureNudgePolicy.fixedPaymentDue(expenses, comida, YearMonth.of(2026, 1), emptyList())
        )
    }

    // Arrastre

    private val recorded = Instant.parse("2026-09-01T12:00:00Z")
    private val october = YearMonth.of(2026, 10)
    private val current = BudgetStanding.Current(
        Forecast(BigInteger.ZERO, 1, Money.cop(0)),
        Verdict.OnTrack
    )

    /** Comida in November, the month in progress. */
    private fun now(limit: Long?, carry: CarryBreakdown? = null, archived: Boolean = false) =
        CategoryMonth(
            category = Category(
                comida,
                CategoryName.of("Comida").valueOrFail(),
                IconKey.of("restaurant").valueOrFail(),
                archived = archived,
                version = 1,
                createdAt = recorded,
                updatedAt = recorded
            ),
            state = MonthBudgetState.Limited(
                limit?.let { BudgetLimit.finite(Money.cop(it)).valueOrFail() }
                    ?: BudgetLimit.Unlimited,
                explicit = false
            ),
            spent = Money.cop(0),
            budget = limit?.let {
                LimitedBudget(Money.cop(it), Money.cop(0), Money.cop(it), current, carry)
            },
            expenseCount = 0
        )

    /** Comida's October as its history has it. */
    private fun october(limit: Long?, spent: Long) =
        CategoryHistoryMonth(october, Money.cop(spent), limit?.let(Money::cop))

    @Test
    fun aMonthThatEndedUnderItsLimitGivesWhatWasLeft() {
        // October: limit 500.000, spent 420.000, so 80.000 was left.
        assertEquals(
            Money.cop(80_000),
            FeatureNudgePolicy.carryDue(now(500_000), october(500_000, 420_000))
        )
    }

    @Test
    fun aMonthThatEndedOverItsLimitGivesWhatWasMissing() {
        // October: limit 500.000, spent 530.000, so 30.000 was missing.
        assertEquals(
            Money.cop(-30_000),
            FeatureNudgePolicy.carryDue(now(500_000), october(500_000, 530_000))
        )
    }

    @Test
    fun endingExactlyOnTheLimitLeavesNothingToCarry() {
        assertNull(FeatureNudgePolicy.carryDue(now(500_000), october(500_000, 500_000)))
    }

    @Test
    fun aCategoryThatAlreadyCarriesIsNotPointedAtIt() {
        val carrying = CarryBreakdown(
            base = Money.cop(500_000),
            carryIn = Money.cop(0),
            capacity = Money.cop(500_000),
            chainStart = november
        )
        assertNull(FeatureNudgePolicy.carryDue(now(500_000, carrying), october(500_000, 420_000)))
    }

    @Test
    fun withoutALimitThisMonthItCannotCarry() {
        assertNull(FeatureNudgePolicy.carryDue(now(null), october(500_000, 420_000)))
    }

    @Test
    fun withoutALimitLastMonthNothingWasLeftOrMissing() {
        assertNull(FeatureNudgePolicy.carryDue(now(500_000), october(null, 420_000)))
    }

    @Test
    fun anArchivedCategoryIsNotPointedAtIt() {
        assertNull(
            FeatureNudgePolicy.carryDue(now(500_000, archived = true), october(500_000, 420_000))
        )
    }

    @Test
    fun withoutLastMonthInItsHistoryItIsNotDue() {
        assertNull(FeatureNudgePolicy.carryDue(now(500_000), null))
    }
}
