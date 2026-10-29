package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DailyAllowance
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The approved worked examples (B, V, F, A and H series). Every expected figure is the
 * one written in `docs/testing/mvp-financial-examples.md`, not one computed here.
 */
class MonthlyBudgetPolicyTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val recorded = Instant.parse("2026-10-09T15:04:05Z")
    private var nextExpense = 500

    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun category(number: Int, name: String, archived: Boolean = false) = Category(
        id = CategoryId.of(uuid(number)).valueOrFail(),
        name = CategoryName.of(name).valueOrFail(),
        icon = IconKey.of("shopping_cart").valueOrFail(),
        archived = archived,
        version = 1,
        createdAt = recorded,
        updatedAt = recorded
    )

    private fun limited(number: Int, pesos: Long, name: String = "Categoría $number") =
        CategoryInMonth(
            category(number, name),
            MonthBudgetState.Limited(
                BudgetLimit.finite(Money.cop(pesos)).valueOrFail(),
                explicit = false
            )
        )

    private fun unlimited(number: Int) = CategoryInMonth(
        category(number, "Libre $number"),
        MonthBudgetState.Limited(BudgetLimit.Unlimited, explicit = false)
    )

    private fun expense(categoryNumber: Int, date: String, pesos: Long) = Expense(
        id = ExpenseId.of(uuid(nextExpense)).valueOrFail(),
        categoryId = CategoryId.of(uuid(categoryNumber)).valueOrFail(),
        date = LocalDate.parse(date),
        sequence = (nextExpense++).toLong(),
        amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
        note = null,
        version = 1,
        createdAt = recorded,
        updatedAt = recorded
    )

    private fun snapshot(
        today: String,
        categories: List<CategoryInMonth>,
        expenses: List<Expense>,
        month: YearMonth = YearMonth.from(LocalDate.parse(today))
    ) = MonthlyBudgetPolicy.snapshot(
        month,
        FinancialDay(LocalDate.parse(today), bogota),
        generation = 3,
        revision = 41,
        categories = categories,
        expenses = expenses
    )

    private fun MonthlySnapshot.limited() = overall as OverallBudget.Limited

    private fun MonthlySnapshot.current() = limited().budget.standing as BudgetStanding.Current

    /** One category with limit 100.000 and [spent] recorded on day 1, read on [today]. */
    private fun oneCategory(today: String, spent: Long, limit: Long = 100_000): MonthlySnapshot {
        val first = LocalDate.parse(today).withDayOfMonth(1).toString()
        val expenses = if (spent == 0L) emptyList() else listOf(expense(1, first, spent))
        return snapshot(today, listOf(limited(1, limit)), expenses).valueOrFail()
    }

    @Test
    fun spendingInAnUnlimitedCategoryStillCountsAgainstTheMonth() {
        // B01.
        val read = snapshot(
            "2026-10-15",
            listOf(limited(1, 100_000), unlimited(2)),
            listOf(expense(1, "2026-10-02", 30_000), expense(2, "2026-10-03", 20_000))
        ).valueOrFail()

        assertEquals(Money.cop(50_000), read.spent)
        assertEquals(Money.cop(100_000), read.limited().budget.limit)
        assertEquals(Money.cop(50_000), read.limited().budget.spent)
        assertEquals(Money.cop(50_000), read.limited().budget.remaining)
        assertEquals(
            listOf(Money.cop(30_000), Money.cop(20_000)),
            read.categories.map { it.spent }
        )
        assertNull(read.categories[1].budget)
    }

    @Test
    fun withNoFiniteLimitThereIsNoBudgetAllowanceOrVerdict() {
        // B02, and F08 for a first run with nothing at all.
        val unlimitedOnly = snapshot(
            "2026-10-15",
            listOf(unlimited(1)),
            listOf(expense(1, "2026-10-02", 50_000))
        ).valueOrFail()
        val firstRun = snapshot("2026-10-15", emptyList(), emptyList()).valueOrFail()

        assertEquals(OverallBudget.NoLimit, unlimitedOnly.overall)
        assertEquals(Money.cop(50_000), unlimitedOnly.spent)
        assertEquals(OverallBudget.NoLimit, firstRun.overall)
        assertEquals(Money.cop(0), firstRun.spent)
        assertEquals(emptyList<Any>(), firstRun.categories)
    }

    @Test
    fun onTheLastDayTheAllowanceIsWhatRemainsForToday() {
        // B03 and F03.
        val read = oneCategory("2026-10-31", spent = 90_000)

        assertEquals(Money.cop(10_000), read.limited().budget.remaining)
        assertEquals(DailyAllowance(Money.cop(10_000), availableDays = 1), read.limited().allowance)
        assertEquals(Money.cop(90_000), read.current().forecast.rounded)
        assertEquals(Verdict.OnTrack, read.current().verdict)
    }

    @Test
    fun overspendingLeavesANegativeRemainderAZeroAllowanceAndExcedido() {
        // B04 and F07.
        val read = oneCategory("2026-10-31", spent = 110_000)

        assertEquals(Money.cop(-10_000), read.limited().budget.remaining)
        assertEquals(DailyAllowance(Money.cop(0), availableDays = 1), read.limited().allowance)
        assertEquals(Money.cop(110_000), read.current().forecast.rounded)
        assertEquals(Verdict.Over, read.current().verdict)
    }

    @Test
    fun aZeroBudgetIsOnTrackUntilAnythingIsSpent() {
        // B05: no division by the zero limit.
        val nothingSpent = oneCategory("2026-10-15", spent = 0, limit = 0)
        val onePeso = oneCategory("2026-10-15", spent = 1, limit = 0)

        assertEquals(Verdict.OnTrack, nothingSpent.current().verdict)
        assertEquals(Money.cop(0), nothingSpent.limited().budget.remaining)
        assertEquals(Verdict.Over, onePeso.current().verdict)
        assertEquals(Money.cop(-1), onePeso.limited().budget.remaining)
    }

    @Test
    fun aMonthWithLimitsAndNoExpensesIsZeroSpentAndOnTrack() {
        // B06 and F06.
        val read = oneCategory("2026-10-01", spent = 0)

        assertEquals(Money.cop(0), read.spent)
        assertEquals(Money.cop(0), read.current().forecast.rounded)
        assertEquals(Verdict.OnTrack, read.current().verdict)
        assertEquals(DailyAllowance(Money.cop(3_200), availableDays = 31), read.limited().allowance)
    }

    @Test
    fun oneCategoryOverItsLimitDoesNotZeroTheOverallAllowance() {
        // B08, owner-confirmed: 22 October, three limits of 100.000, Food spent 110.000.
        val read = snapshot(
            "2026-10-22",
            listOf(limited(1, 100_000, "Comida"), limited(2, 100_000), limited(3, 100_000)),
            listOf(expense(1, "2026-10-20", 110_000))
        ).valueOrFail()

        val food = read.categories.first().budget!!
        assertEquals(Money.cop(-10_000), food.remaining)
        assertEquals(Verdict.Over, (food.standing as BudgetStanding.Current).verdict)
        assertEquals(Money.cop(300_000), read.limited().budget.limit)
        assertEquals(Money.cop(110_000), read.limited().budget.spent)
        assertEquals(Money.cop(190_000), read.limited().budget.remaining)
        assertEquals(
            DailyAllowance(Money.cop(19_000), availableDays = 10),
            read.limited().allowance
        )
        assertEquals(Money.cop(100_000), read.categories[1].budget!!.remaining)
    }

    @Test
    fun spendingBelowTheLimitIsAlDiaHoweverFastItWasRecorded() {
        // 2026-10-16 review: 393.700 of 750.000 on day 16. The pace would project
        // 762.794, above the limit, but nothing over the limit has been recorded.
        val read = snapshot(
            "2026-10-16",
            listOf(limited(1, 750_000)),
            listOf(expense(1, "2026-10-01", 393_700))
        ).valueOrFail()

        assertEquals(Money.cop(356_300), read.limited().budget.remaining)
        assertEquals(
            DailyAllowance(Money.cop(22_250), availableDays = 16),
            read.limited().allowance
        )
        assertEquals(Verdict.OnTrack, read.current().verdict)
    }

    @Test
    fun spendingBelowTheOverallLimitIsAlDia() {
        // BG-E01: L 1.000.000, E 400.000, 10 days left (22 October).
        val read = snapshot(
            "2026-10-22",
            listOf(limited(1, 1_000_000)),
            listOf(expense(1, "2026-10-05", 400_000))
        ).valueOrFail()

        assertEquals(Money.cop(600_000), read.limited().budget.remaining)
        assertEquals(
            DailyAllowance(Money.cop(60_000), availableDays = 10),
            read.limited().allowance
        )
        assertEquals(Verdict.OnTrack, read.current().verdict)
    }

    @Test
    fun spendingExactlyTheLimitIsAlLimiteAndOnePesoMoreIsExcedido() {
        // BG-E05: L 1.000.000; E 1.000.000, then 1.000.001; 10 days left.
        val atLimit = snapshot(
            "2026-10-22",
            listOf(limited(1, 1_000_000)),
            listOf(expense(1, "2026-10-05", 1_000_000))
        ).valueOrFail()
        val onePesoOver = snapshot(
            "2026-10-22",
            listOf(limited(1, 1_000_000)),
            listOf(expense(1, "2026-10-05", 1_000_001))
        ).valueOrFail()

        assertEquals(Money.cop(0), atLimit.limited().budget.remaining)
        assertEquals(DailyAllowance(Money.cop(0), availableDays = 10), atLimit.limited().allowance)
        assertEquals(Verdict.AtLimit, atLimit.current().verdict)
        assertEquals(Money.cop(-1), onePesoOver.limited().budget.remaining)
        assertEquals(
            DailyAllowance(Money.cop(0), availableDays = 10),
            onePesoOver.limited().allowance
        )
        assertEquals(Verdict.Over, onePesoOver.current().verdict)
    }

    @Test
    fun aCategoryOverrunLeavesAHealthyMonthAlDia() {
        // BG-E04: L 300.000; 110.000 spent in a category limited to 100.000; 10 days left.
        val read = snapshot(
            "2026-10-22",
            listOf(limited(1, 100_000, "Comida"), limited(2, 200_000)),
            listOf(expense(1, "2026-10-05", 110_000))
        ).valueOrFail()

        val food = read.categories.first().budget!!
        assertEquals(Money.cop(-10_000), food.remaining)
        assertEquals(Verdict.Over, (food.standing as BudgetStanding.Current).verdict)
        assertEquals(Money.cop(190_000), read.limited().budget.remaining)
        assertEquals(
            DailyAllowance(Money.cop(19_000), availableDays = 10),
            read.limited().allowance
        )
        assertEquals(Verdict.OnTrack, read.current().verdict)
    }

    @Test
    fun aCategoryIsJudgedOnWhatItRecordedAgainstItsOwnLimit() {
        // 15 October: one category spent exactly its 100.000, another spent nothing.
        val read = snapshot(
            "2026-10-15",
            listOf(limited(1, 100_000), limited(2, 200_000)),
            listOf(expense(1, "2026-10-02", 100_000))
        ).valueOrFail()

        val full = read.categories[0].budget!!.standing as BudgetStanding.Current
        val untouched = read.categories[1].budget!!.standing as BudgetStanding.Current
        assertEquals(Verdict.AtLimit, full.verdict)
        assertEquals(Verdict.OnTrack, untouched.verdict)
        assertEquals(Verdict.OnTrack, read.current().verdict)
    }

    @Test
    fun unlimitedSpendingCanExceedTheMonthWhileNoCategoryIsOver() {
        // B01 with BG1: limit 100.000; 50.000 in it and 60.000 without a limit, 15 October.
        val read = snapshot(
            "2026-10-15",
            listOf(limited(1, 100_000), unlimited(2)),
            listOf(expense(1, "2026-10-02", 50_000), expense(2, "2026-10-03", 60_000))
        ).valueOrFail()

        val limitedCategory = read.categories[0].budget!!.standing as BudgetStanding.Current
        assertEquals(Verdict.OnTrack, limitedCategory.verdict)
        assertEquals(Money.cop(-10_000), read.limited().budget.remaining)
        assertEquals(Verdict.Over, read.current().verdict)
    }

    @Test
    fun theVerdictBoundariesAreTheLimitItself() {
        // ADR 0012 BG1 table, limit 100.000, and B05's zero limit.
        val limit = Money.cop(100_000)

        assertEquals(Verdict.OnTrack, MonthlyBudgetPolicy.verdict(limit, Money.cop(99_999)))
        assertEquals(Verdict.AtLimit, MonthlyBudgetPolicy.verdict(limit, Money.cop(100_000)))
        assertEquals(Verdict.Over, MonthlyBudgetPolicy.verdict(limit, Money.cop(100_001)))
        assertEquals(Verdict.OnTrack, MonthlyBudgetPolicy.verdict(Money.cop(0), Money.cop(0)))
        assertEquals(Verdict.Over, MonthlyBudgetPolicy.verdict(Money.cop(0), Money.cop(1)))
    }

    @Test
    fun theForecastStaysExactAndTheVerdictIgnoresIt() {
        // V05: 15 February 2027, spent 50.893. Forecast 1.425.004/15, shown as 95.000.
        val read = oneCategory("2027-02-15", spent = 50_893)

        assertEquals(BigInteger.valueOf(1_425_004), read.current().forecast.numerator)
        assertEquals(15, read.current().forecast.denominator)
        assertEquals(Money.cop(95_000), read.current().forecast.rounded)
        // The forecast stays exact for Resumen; Recibo's status ignores it.
        assertEquals(Verdict.OnTrack, read.current().verdict)
    }

    @Test
    fun theForecastAndAllowanceFollowTheApprovedDayCounts() {
        // F01, F02, F04, F05: (today, spent) to (forecast shown, days, allowance, verdict).
        val cases = listOf(
            Triple("2026-10-01", 10_000L, listOf(310_000L, 31L, 2_900L)) to Verdict.OnTrack,
            Triple("2026-10-15", 40_000L, listOf(82_667L, 17L, 3_500L)) to Verdict.OnTrack,
            Triple("2027-02-14", 30_000L, listOf(60_000L, 15L, 4_650L)) to Verdict.OnTrack,
            Triple("2028-02-15", 45_000L, listOf(87_000L, 15L, 3_650L)) to Verdict.OnTrack
        )

        for ((input, verdict) in cases) {
            val (today, spent, expected) = input
            val read = oneCategory(today, spent)

            assertEquals(today, Money.cop(expected[0]), read.current().forecast.rounded)
            assertEquals(
                today,
                DailyAllowance(Money.cop(expected[2]), expected[1].toInt()),
                read.limited().allowance
            )
            assertEquals(today, verdict, read.current().verdict)
        }
    }

    @Test
    fun theAllowanceRoundsDownToFiftyPesosAndNeverBelowZero() {
        // A01 to A08: (remaining, available days) to shown allowance.
        val cases = listOf(
            (59_993L to 17) to 3_500L,
            (60_588L to 17) to 3_550L,
            (849L to 17) to 0L,
            (850L to 17) to 50L,
            (1_699L to 17) to 50L,
            (1_700L to 17) to 100L,
            (49L to 1) to 0L,
            (-10_000L to 1) to 0L
        )

        for ((input, shown) in cases) {
            val (remaining, days) = input

            assertEquals(
                "$input",
                DailyAllowance(Money.cop(shown), days),
                MonthlyBudgetPolicy.allowance(Money.cop(remaining), days)
            )
        }
    }

    @Test
    fun aPastMonthShowsActualsOnlyWithNoForecastOrAllowance() {
        // H01 and H04: September 100.000 with 20.000 spent, read in October.
        val read = snapshot(
            "2026-10-09",
            listOf(limited(1, 100_000)),
            listOf(expense(1, "2026-09-12", 20_000)),
            month = YearMonth.of(2026, 9)
        ).valueOrFail()

        assertEquals(Money.cop(80_000), read.limited().budget.remaining)
        assertEquals(BudgetStanding.Past(PastVerdict.WithinLimit), read.limited().budget.standing)
        assertNull(read.limited().allowance)
        assertEquals(
            BudgetStanding.Past(PastVerdict.WithinLimit),
            read.categories.single().budget!!.standing
        )
    }

    @Test
    fun h05AnUnchangedDefaultRenewsTheWholeLimitWithoutWhatWasLeft() {
        // H05: September 20.000 spent of 100.000; the default unchanged through October.
        val categories = listOf(limited(1, 100_000))
        val expenses = listOf(expense(1, "2026-09-12", 20_000))

        val september =
            snapshot("2026-10-09", categories, expenses, YearMonth.of(2026, 9)).valueOrFail()
        val october = snapshot("2026-10-09", categories, expenses).valueOrFail()

        assertEquals(Money.cop(80_000), september.limited().budget.remaining)
        assertEquals(Money.cop(100_000), october.limited().budget.limit)
        assertEquals(Money.cop(100_000), october.limited().budget.remaining)
        assertEquals(Money.cop(0), october.spent)
    }

    @Test
    fun eachExpenseCountsInTheMonthOfItsDateAcrossMonthYearAndLeapDayEnds() {
        // Worked out by hand: 31 Oct and 1 Nov, 31 Dec and 1 Jan, 28 Feb, 29 Feb and 1 Mar.
        val expenses = listOf(
            expense(1, "2026-10-31", 85_000),
            expense(1, "2026-11-01", 40_000),
            expense(1, "2026-12-31", 38_000),
            expense(1, "2027-01-01", 42_000),
            expense(1, "2028-02-28", 10_000),
            expense(1, "2028-02-29", 20_000),
            expense(1, "2028-03-01", 5_000)
        )
        fun month(month: String) = snapshot(
            "2028-03-01",
            listOf(limited(1, 100_000)),
            expenses,
            YearMonth.parse(month)
        ).valueOrFail()

        assertEquals(Money.cop(85_000), month("2026-10").spent)
        assertEquals(Money.cop(40_000), month("2026-11").spent)
        assertEquals(Money.cop(38_000), month("2026-12").spent)
        assertEquals(Money.cop(42_000), month("2027-01").spent)
        assertEquals(Money.cop(30_000), month("2028-02").spent)
        assertEquals(Money.cop(70_000), month("2028-02").limited().budget.remaining)
        assertEquals(Money.cop(5_000), month("2028-03").spent)
        assertEquals(Money.cop(95_000), month("2028-03").limited().budget.remaining)
    }

    @Test
    fun aPastMonthIsOverOnlyWhenMoreThanTheLimitWasSpent() {
        assertEquals(
            PastVerdict.WithinLimit,
            MonthlyBudgetPolicy.pastVerdict(Money.cop(100_000), Money.cop(100_000))
        )
        assertEquals(
            PastVerdict.Over,
            MonthlyBudgetPolicy.pastVerdict(Money.cop(100_000), Money.cop(100_001))
        )
        assertEquals(
            PastVerdict.WithinLimit,
            MonthlyBudgetPolicy.pastVerdict(Money.cop(0), Money.cop(0))
        )
    }

    @Test
    fun expensesDatedAfterTodayAreLeftOutAndCounted() {
        val read = snapshot(
            "2026-10-15",
            listOf(limited(1, 100_000)),
            listOf(
                expense(1, "2026-10-15", 30_000),
                expense(1, "2026-10-16", 999_000),
                expense(1, "2026-10-31", 1)
            )
        ).valueOrFail()

        assertEquals(Money.cop(30_000), read.spent)
        assertEquals(Money.cop(70_000), read.limited().budget.remaining)
        assertEquals(2, read.futureDatedExpenses)
        assertEquals(Money.cop(30_000), read.categories.single().spent)
    }

    @Test
    fun spendingWithoutAnyLimitStateStillCountsAndArchivedCategoriesShowOnlyWithHistory() {
        val read = snapshot(
            "2026-10-15",
            listOf(
                limited(1, 100_000),
                CategoryInMonth(category(2, "Sin configurar"), MonthBudgetState.Unconfigured),
                CategoryInMonth(category(3, "Archivada con gasto", true), MonthBudgetState.Paused),
                CategoryInMonth(category(4, "Archivada vacía", true), MonthBudgetState.Paused),
                CategoryInMonth(
                    category(5, "Archivada con límite", true),
                    MonthBudgetState.Limited(
                        BudgetLimit.finite(Money.cop(50_000)).valueOrFail(),
                        explicit = true
                    )
                )
            ),
            listOf(expense(2, "2026-10-02", 7_000), expense(3, "2026-10-03", 5_000))
        ).valueOrFail()

        assertEquals(
            listOf("Categoría 1", "Sin configurar", "Archivada con gasto", "Archivada con límite"),
            read.categories.map { it.category.name.value }
        )
        assertEquals(Money.cop(12_000), read.spent)
        // The archived category's saved limit still belongs to the month.
        assertEquals(Money.cop(150_000), read.limited().budget.limit)
        assertEquals(Money.cop(138_000), read.limited().budget.remaining)
    }

    @Test
    fun aTotalThatDoesNotFitIsAFailureNotAClippedAmount() {
        val read = snapshot(
            "2026-10-15",
            listOf(limited(1, Long.MAX_VALUE), limited(2, 1)),
            emptyList()
        )

        assertEquals(SnapshotFailure.Unrepresentable, read.errorOrFail())
    }

    @Test
    fun theLargestLimitOnItsOwnIsStillExact() {
        val read = snapshot("2026-10-15", listOf(limited(1, Long.MAX_VALUE)), emptyList())

        assertEquals(Money.cop(Long.MAX_VALUE), read.valueOrFail().limited().budget.remaining)
    }

    @Test
    fun aMonthAfterTheCurrentOneCannotBeRead() {
        val read = snapshot(
            "2026-10-15",
            listOf(limited(1, 100_000)),
            emptyList(),
            month = YearMonth.of(2026, 11)
        )

        assertEquals(SnapshotFailure.FutureMonth(YearMonth.of(2026, 10)), read.errorOrFail())
    }

    @Test
    fun theSnapshotSaysWhenAndAtWhichRevisionItWasRead() {
        val read = oneCategory("2026-10-15", spent = 40_000)

        assertEquals(YearMonth.of(2026, 10), read.month)
        assertEquals(LocalDate.of(2026, 10, 15), read.asOf)
        assertEquals(bogota, read.zone)
        assertEquals(3L to 41L, read.generation to read.revision)
    }

    private fun paused(number: Int) =
        CategoryInMonth(category(number, "Pausada $number"), MonthBudgetState.Paused)

    private fun id(number: Int) = CategoryId.of(uuid(number)).valueOrFail()

    private fun pesos(amount: Long) = BudgetLimit.finite(Money.cop(amount)).valueOrFail()

    private val october =
        listOf(limited(1, 900_000), limited(2, 300_000), unlimited(3), paused(4))

    @Test
    fun theMonthsLimitTotalAsItIsAddsOnlyTheFiniteLimits() {
        val total = MonthlyBudgetPolicy.limitTotalWith(october, categoryId = null, limit = null)

        assertEquals(Money.cop(1_200_000), total.valueOrFail())
    }

    @Test
    fun aNewCategorysLimitIsAddedToTheMonthsTotal() {
        val total =
            MonthlyBudgetPolicy.limitTotalWith(october, categoryId = null, limit = pesos(200_000))

        assertEquals(Money.cop(1_400_000), total.valueOrFail())
    }

    @Test
    fun aChangedLimitReplacesTheCategorysOldOneInTheTotal() {
        val raised = MonthlyBudgetPolicy.limitTotalWith(october, id(2), pesos(500_000))
        val removed = MonthlyBudgetPolicy.limitTotalWith(october, id(2), BudgetLimit.Unlimited)
        val firstLimit = MonthlyBudgetPolicy.limitTotalWith(october, id(4), pesos(100_000))

        assertEquals(Money.cop(1_400_000), raised.valueOrFail())
        assertEquals(Money.cop(900_000), removed.valueOrFail())
        assertEquals(Money.cop(1_300_000), firstLimit.valueOrFail())
    }

    @Test
    fun aMonthWithNoFiniteLimitHasNoTotal() {
        val total =
            MonthlyBudgetPolicy.limitTotalWith(listOf(unlimited(3)), null, BudgetLimit.Unlimited)

        assertNull(total.valueOrFail())
    }

    @Test
    fun aTotalTooLargeToHoldIsRefusedNotWrapped() {
        val total = MonthlyBudgetPolicy.limitTotalWith(
            listOf(limited(1, Long.MAX_VALUE)),
            categoryId = null,
            limit = pesos(1)
        )

        assertEquals(SnapshotFailure.Unrepresentable, total.errorOrFail())
    }
}
