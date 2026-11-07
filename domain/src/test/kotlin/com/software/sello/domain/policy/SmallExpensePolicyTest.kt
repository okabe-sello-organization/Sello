package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.SmallTally
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingPeriods
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gastos hormiga (SELLO-194). Every expected figure is worked out by hand in the comment
 * beside it, never with the code under test.
 */
class SmallExpensePolicyTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val recorded = Instant.parse("2026-10-09T15:04:05Z")
    private val october = YearMonth.of(2026, 10)
    private val line = SmallExpenseLine.of(20_000)!!
    private var nextExpense = 500

    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(number: Int) = CategoryId.of(uuid(number)).valueOrFail()

    private fun category(number: Int, name: String) = Category(
        id(number),
        CategoryName.of(name).valueOrFail(),
        IconKey.of("local_cafe").valueOrFail(),
        false,
        1,
        recorded,
        recorded
    )

    private fun unlimited(number: Int, name: String) =
        CategoryInMonth(category(number, name), MonthBudgetState.Limited(BudgetLimit.Unlimited, false))

    private val categories = listOf(
        unlimited(1, "Café"),
        unlimited(2, "Transporte"),
        unlimited(3, "Alimentación"),
        unlimited(4, "Hogar")
    )

    private fun expense(
        date: String,
        pesos: Long,
        category: Int = 1,
        origin: EntryOrigin? = null
    ) = Expense(
        ExpenseId.of(uuid(nextExpense)).valueOrFail(),
        id(category),
        LocalDate.parse(date),
        (nextExpense++).toLong(),
        TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
        null,
        1,
        recorded,
        recorded,
        origin
    )

    private fun read(
        expenses: List<Expense>,
        today: String = "2026-10-09",
        month: YearMonth = october
    ): ResumenSnapshot = ResumenPolicy.snapshot(
        month,
        FinancialDay(LocalDate.parse(today), bogota),
        1,
        1,
        ResumenPolicy.monthsEndingIn(month).map { ResumenMonthInput(it, categories, expenses) },
        emptyList()
    ).valueOrFail()

    private fun summary(snapshot: ResumenSnapshot) =
        SmallExpensePolicy.summary(snapshot, line).valueOrFail()

    // --- The line ---

    @Test
    fun theLineRunsFromOneThousandToOneHundredThousandAndDefaultsToTwentyThousand() {
        assertNull(SmallExpenseLine.of(999))
        assertEquals(1_000L, SmallExpenseLine.of(1_000)?.pesos)
        assertEquals(100_000L, SmallExpenseLine.of(100_000)?.pesos)
        assertNull(SmallExpenseLine.of(100_001))
        assertNull(SmallExpenseLine.of(0))
        assertNull(SmallExpenseLine.of(-20_000))
        assertEquals(20_000L, SmallExpenseLine.DEFAULT.pesos)
    }

    @Test
    fun anExpenseAtTheLineIsSmallAndOnePesoOverIsNot() {
        val tally = SmallExpensePolicy.tally(
            listOf(
                expense("2026-10-01", 20_000),
                expense("2026-10-02", 20_001),
                expense("2026-10-03", 8_000),
                expense("2026-10-04", 2_950)
            ),
            line
        ).valueOrFail()

        // 20.000 + 8.000 + 2.950 = 30.950; 20.001 is over the line.
        assertEquals(SmallTally(3, Money.cop(30_950)), tally)
    }

    // --- When the slip shows ---

    @Test
    fun fourSmallExpensesGiveNoSlip() {
        val snapshot = read(
            listOf(
                expense("2026-10-01", 5_000),
                expense("2026-10-02", 5_000),
                expense("2026-10-03", 5_000),
                expense("2026-10-04", 5_000),
                expense("2026-10-05", 300_000)
            )
        )

        assertNull(summary(snapshot))
    }

    @Test
    fun fiveSmallExpensesGiveTheSlip() {
        val snapshot = read((1..5).map { expense("2026-10-0$it", 3_000) })

        // 5 × 3.000 = 15.000, all of what was spent: 100 %.
        val small = summary(snapshot)!!
        assertEquals(5, small.count)
        assertEquals(Money.cop(15_000), small.total)
        assertEquals(100, small.sharePercent)
    }

    // --- What counts ---

    @Test
    fun theShareOfTheTicketsExampleIsThirteenPercent() {
        // 15 × 20.000 + 12.000 = 312.000 in 16 small expenses; one of 2.168.000 brings the
        // month to 2.480.000. 312.000 / 2.480.000 = 12,58 %, half up 13 %.
        val snapshot = read(
            (1..15).map { expense("2026-10-0${1 + it % 9}", 20_000) } +
                expense("2026-10-05", 12_000) +
                expense("2026-10-06", 2_168_000)
        )

        val small = summary(snapshot)!!
        assertEquals(16, small.count)
        assertEquals(Money.cop(312_000), small.total)
        assertEquals(13, small.sharePercent)
    }

    @Test
    fun halfAPercentRoundsUpAndLessRoundsDown() {
        assertEquals(1, SmallExpensePolicy.percent(Money.cop(5_000), Money.cop(1_000_000)))
        assertEquals(0, SmallExpensePolicy.percent(Money.cop(4_900), Money.cop(1_000_000)))

        // 5 × 1.000 = 5.000 of 5.000 + 995.000 = 1.000.000: 0,5 %, shown 1 %.
        val half = read(
            (1..5).map { expense("2026-10-0$it", 1_000) } + expense("2026-10-06", 995_000)
        )
        assertEquals(1, summary(half)!!.sharePercent)

        // 5 × 980 = 4.900 of 4.900 + 995.100 = 1.000.000: 0,49 %, shown 0 %.
        val less = read(
            (1..5).map { expense("2026-10-0$it", 980) } + expense("2026-10-06", 995_100)
        )
        assertEquals(0, summary(less)!!.sharePercent)
    }

    @Test
    fun fixedPaymentsCountWhenTheyAreSmall() {
        val schedule = ScheduleId.of(uuid(900)).valueOrFail()
        val subscription = EntryOrigin(schedule, Occurrence(LocalDate.parse("2026-10-03"), 1))
        val snapshot = read(
            (1..4).map { expense("2026-10-0$it", 2_000) } +
                expense("2026-10-03", 15_000, origin = subscription)
        )

        // 4 × 2.000 + 15.000 = 23.000 in 5 small expenses.
        val small = summary(snapshot)!!
        assertEquals(5, small.count)
        assertEquals(Money.cop(23_000), small.total)
    }

    @Test
    fun theMonthInProgressCountsUpToToday() {
        val snapshot = read(
            (1..5).map { expense("2026-10-0$it", 4_000) } + expense("2026-10-12", 4_000),
            today = "2026-10-09"
        )

        // The 12th is after today: 5 × 4.000 = 20.000.
        val small = summary(snapshot)!!
        assertEquals(5, small.count)
        assertEquals(Money.cop(20_000), small.total)
    }

    @Test
    fun aFinishedMonthCountsAllOfItsDays() {
        val snapshot = read(
            (1..4).map { expense("2026-09-0$it", 4_000) } + expense("2026-09-30", 4_000),
            today = "2026-10-09",
            month = YearMonth.of(2026, 9)
        )

        // 1 to 4 and the 30th: 5 × 4.000 = 20.000.
        val small = summary(snapshot)!!
        assertEquals(5, small.count)
        assertEquals(Money.cop(20_000), small.total)
    }

    // --- The month before ---

    @Test
    fun theComparisonIsAbsentWhenTheMonthBeforeHasNoData() {
        val snapshot = read((1..5).map { expense("2026-10-0$it", 3_000) })

        assertNull(snapshot.periods.previous)
        assertNull(summary(snapshot)!!.previousTotal)
    }

    @Test
    fun theMonthBeforeIsComparedOverTheSameDays() {
        val snapshot = read(
            (1..5).map { expense("2026-10-0$it", 3_000) } +
                expense("2026-09-02", 7_000) +
                expense("2026-09-09", 6_000) +
                // After the 9th: not part of the same days.
                expense("2026-09-10", 5_000) +
                // Over the line: not small.
                expense("2026-09-03", 80_000),
            today = "2026-10-09"
        )

        // September 1 to 9: 7.000 + 6.000 = 13.000.
        assertEquals(Money.cop(13_000), summary(snapshot)!!.previousTotal)
    }

    @Test
    fun aMonthBeforeWithDataButNothingSmallComparesWithZero() {
        val snapshot = read(
            (1..5).map { expense("2026-10-0$it", 3_000) } + expense("2026-09-02", 90_000)
        )

        assertEquals(Money.cop(0), summary(snapshot)!!.previousTotal)
    }

    @Test
    fun aShorterMonthBeforeIsComparedToItsLastDay() {
        val march = YearMonth.of(2026, 3)
        val snapshot = read(
            (1..5).map { expense("2026-03-0$it", 1_000) } +
                expense("2026-02-28", 2_500) +
                expense("2026-03-31", 1_000),
            today = "2026-03-31",
            month = march
        )

        // March 1 to 31: 6 × 1.000 = 6.000. February 1 to 28: 2.500.
        val small = summary(snapshot)!!
        assertEquals(Money.cop(6_000), small.total)
        assertEquals(Money.cop(2_500), small.previousTotal)
    }

    // --- Donde más se juntan ---

    @Test
    fun theThreeBiggestSumsAreListedWithTiesToTheHigherCountThenTheName() {
        val snapshot = read(
            // Café: 2 × 10.000 = 20.000.
            listOf(expense("2026-10-01", 10_000, 1), expense("2026-10-02", 10_000, 1)) +
                // Transporte: 4 × 5.000 = 20.000, the same sum in more expenses.
                (1..4).map { expense("2026-10-0$it", 5_000, 2) } +
                // Alimentación: 1 × 20.000 = 20.000, the same sum in fewer expenses.
                listOf(expense("2026-10-03", 20_000, 3)) +
                // Hogar: 1 × 30.000 is over the line; 1 × 9.000 is small.
                listOf(expense("2026-10-04", 30_000, 4), expense("2026-10-05", 9_000, 4))
        )

        // Transporte 20.000 (4), Café 20.000 (2), Alimentación 20.000 (1); Hogar 9.000 out.
        val small = summary(snapshot)!!
        assertEquals(
            listOf("Transporte" to 4, "Café" to 2, "Alimentación" to 1),
            small.categories.map { it.category.name.value to it.count }
        )
        assertEquals(
            listOf(Money.cop(20_000), Money.cop(20_000), Money.cop(20_000)),
            small.categories.map { it.total }
        )
    }

    @Test
    fun anEqualSumAndCountGoesToNameOrder() {
        val snapshot = read(
            (1..3).map { expense("2026-10-0$it", 4_000, 2) } +
                (1..3).map { expense("2026-10-0$it", 4_000, 3) }
        )

        // Transporte and Alimentación: 12.000 in 3 each; Alimentación first by name.
        assertEquals(
            listOf("Alimentación", "Transporte"),
            summary(snapshot)!!.categories.map { it.category.name.value }
        )
    }

    // --- What is never guessed ---

    @Test
    fun aPeriodThatAddsUpToMoreThanTheHeroIsRefused() {
        val snapshot = read((1..5).map { expense("2026-10-0$it", 3_000) })
        // An extra expense the hero never counted: 18.000 of 15.000 cannot be a share.
        val broken = snapshot.copy(
            periods = SpendingPeriods(
                snapshot.periods.current + expense("2026-10-06", 3_000),
                null
            )
        )

        assertEquals(
            Outcome.Failure(SnapshotFailure.Unrepresentable),
            SmallExpensePolicy.summary(broken, line)
        )
        assertNotNull(SmallExpensePolicy.summary(snapshot, line).valueOrFail())
    }

    @Test
    fun aCategoryResumenDoesNotListIsNotCounted() {
        // Category 9 is not in the month: the hero does not count it, nor does the slip.
        val snapshot = read(
            (1..5).map { expense("2026-10-0$it", 3_000) } + expense("2026-10-06", 3_000, 9)
        )

        assertEquals(5, summary(snapshot)!!.count)
    }
}
