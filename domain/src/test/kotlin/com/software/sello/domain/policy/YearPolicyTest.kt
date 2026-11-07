package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.YearComparison
import com.software.sello.domain.model.YearMonthRecord
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearRecords
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.policy.Carry.chain
import com.software.sello.domain.policy.Carry.comidaInput
import com.software.sello.domain.policy.Carry.ene
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

/** Tu año. Every expected figure is worked out by hand in the comment beside it. */
class YearPolicyTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val recorded = Instant.parse("2026-01-09T15:04:05Z")
    private var next = 1_000

    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(number: Int) = CategoryId.of(uuid(number)).valueOrFail()

    private fun category(number: Int, name: String, archived: Boolean = false) = Category(
        id(number),
        CategoryName.of(name).valueOrFail(),
        IconKey.of("home").valueOrFail(),
        archived,
        1,
        recorded,
        recorded
    )

    private fun line(number: Int, name: String, limit: Long?, archived: Boolean = false) =
        CategoryInMonth(
            category(number, name, archived),
            if (limit == null) {
                MonthBudgetState.Unconfigured
            } else {
                MonthBudgetState.Limited(BudgetLimit.finite(Money.cop(limit)).valueOrFail(), false)
            }
        )

    private fun expense(date: String, pesos: Long, category: Int = 1) = Expense(
        ExpenseId.of(uuid(next)).valueOrFail(),
        id(category),
        LocalDate.parse(date),
        (next++).toLong(),
        TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
        null,
        1,
        recorded,
        recorded
    )

    private fun income(date: String, pesos: Long) = Income(
        IncomeId.of(uuid(next)).valueOrFail(),
        LocalDate.parse(date),
        (next++).toLong(),
        TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
        IncomeSource.of("salary", null).valueOrFail(),
        null,
        1,
        recorded,
        recorded
    )

    private val hogar = listOf(line(1, "Hogar", null))

    private fun read(
        year: Int,
        today: String,
        expenses: List<Expense>,
        previous: List<Expense> = emptyList(),
        incomes: List<Income> = emptyList(),
        firstRecorded: String? = null,
        carry: (YearMonth) -> List<CategoryCarryInput> = { emptyList() },
        categories: (YearMonth) -> List<CategoryInMonth> = { hogar }
    ): YearSnapshot {
        val day = FinancialDay(LocalDate.parse(today), bogota)
        return YearPolicy.snapshot(
            year,
            day,
            1,
            1,
            YearInput(
                YearPolicy.monthsShown(year, day).map { month ->
                    ResumenMonthInput(
                        month,
                        categories(month),
                        expenses.filter { YearMonth.from(it.date) == month },
                        carry(month)
                    )
                },
                incomes,
                previous,
                firstRecorded?.let(LocalDate::parse)
            )
        ).valueOrFail()
    }

    // --- The hero ---

    @Test
    fun aYearInProgressIsComparedWithTheSameDaysOfTheYearBefore() {
        val snapshot = read(
            2026,
            "2026-11-06",
            listOf(expense("2026-03-02", 14_000_000), expense("2026-11-06", 230_000)),
            previous = listOf(
                expense("2025-01-15", 15_000_000),
                expense("2025-11-06", 350_000),
                // After 6 November: not the same days.
                expense("2025-11-07", 500_000)
            ),
            firstRecorded = "2025-01-01"
        )

        // 2026: 14.000.000 + 230.000 = 14.230.000. 2025 to 6 November: 15.000.000 + 350.000
        // = 15.350.000. 1.120.000 less; 1.120.000 / 15.350.000 = 7,296 % → 7,3 %.
        assertEquals(Money.cop(14_230_000), snapshot.spent)
        assertEquals(
            YearComparison(
                previousYear = 2025,
                spent = Money.cop(14_230_000),
                previousSpent = Money.cop(15_350_000),
                through = LocalDate.parse("2026-11-06"),
                previousThrough = LocalDate.parse("2025-11-06"),
                wholeYears = false,
                difference = Money.cop(1_120_000),
                direction = SpendingDirection.Less,
                percentTenths = BigInteger.valueOf(73)
            ),
            snapshot.comparison
        )
    }

    @Test
    fun the29thOfFebruaryIsComparedWithThe28thInAShorterYear() {
        val snapshot = read(
            2028,
            "2028-02-29",
            listOf(expense("2028-02-29", 30_000)),
            previous = listOf(expense("2027-02-28", 20_000), expense("2027-03-01", 99_000)),
            firstRecorded = "2026-12-31"
        )

        // 2028 to 29 February: 30.000. 2027 to 28 February: 20.000. 10.000 more; 50,0 %.
        val comparison = snapshot.comparison!!
        assertEquals(LocalDate.parse("2027-02-28"), comparison.previousThrough)
        assertEquals(Money.cop(20_000), comparison.previousSpent)
        assertEquals(Money.cop(10_000), comparison.difference)
        assertEquals(SpendingDirection.More, comparison.direction)
        assertEquals(BigInteger.valueOf(500), comparison.percentTenths)
    }

    @Test
    fun aYearBeforeWithNothingOnThoseDaysHasNoComparison() {
        val snapshot = read(
            2026,
            "2026-11-06",
            listOf(expense("2026-03-02", 40_000)),
            previous = listOf(expense("2025-12-01", 70_000)),
            firstRecorded = "2024-05-01"
        )

        assertNull(snapshot.comparison)
    }

    @Test
    fun theYearBeforeIsComparedOnlyWhenRecordsBeganByItsFirstDay() {
        val expenses = listOf(expense("2026-03-02", 40_000))
        val previous = listOf(expense("2025-03-02", 50_000))

        val fromFirstDay =
            read(2026, "2026-11-06", expenses, previous, firstRecorded = "2025-01-01")
        val fromSecondDay =
            read(2026, "2026-11-06", expenses, previous, firstRecorded = "2025-01-02")

        // From 1 January 2025: 40.000 against 50.000, 10.000 less; 10.000 / 50.000 = 20,0 %.
        assertEquals(Money.cop(10_000), fromFirstDay.comparison!!.difference)
        assertEquals(BigInteger.valueOf(200), fromFirstDay.comparison!!.percentTenths)
        // From 2 January 2025, 2025 is a partial year: the year is shown alone.
        assertNull(fromSecondDay.comparison)
    }

    @Test
    fun aFinishedYearIsComparedWholeWithTheYearBefore() {
        val snapshot = read(
            2025,
            "2026-11-06",
            listOf(expense("2025-02-10", 100_000), expense("2025-12-31", 50_000)),
            previous = listOf(expense("2024-06-01", 100_000), expense("2024-12-31", 20_000)),
            firstRecorded = "2023-08-15"
        )

        // 2025: 150.000. 2024: 120.000. 30.000 more; 30.000 / 120.000 = 25,0 %.
        val comparison = snapshot.comparison!!
        assertEquals(true, comparison.wholeYears)
        assertEquals(LocalDate.parse("2024-12-31"), comparison.previousThrough)
        assertEquals(Money.cop(150_000), comparison.spent)
        assertEquals(Money.cop(30_000), comparison.difference)
        assertEquals(BigInteger.valueOf(250), comparison.percentTenths)
        assertEquals(12, snapshot.months.count { it.status is YearMonthStatus.Finished })
    }

    @Test
    fun aYearInProgressIsNeverProjected() {
        val snapshot = read(
            2026,
            "2026-05-10",
            listOf(expense("2026-05-10", 25_000), expense("2026-05-11", 999_000))
        )

        // Up to today only: the 11 May expense is not counted.
        assertEquals(Money.cop(25_000), snapshot.spent)
        assertEquals(LocalDate.parse("2026-05-10"), snapshot.asOf)
    }

    // --- Mes a mes ---

    @Test
    fun eachFinishedMonthCarriesItsOwnVerdict() {
        val snapshot = read(
            2026,
            "2026-05-10",
            listOf(
                expense("2026-01-20", 90_000),
                expense("2026-02-20", 100_000),
                expense("2026-03-20", 100_001),
                expense("2026-04-20", 500_000),
                expense("2026-05-02", 10_000)
            ),
            categories = { month ->
                if (month.monthValue == 4) hogar else listOf(line(1, "Hogar", 100_000))
            }
        )

        val months = snapshot.months
        assertEquals(12, months.size)
        // 90.000 under 100.000; 100.000 equal to it; 100.001 over it; April has no limit.
        assertEquals(YearMonthStatus.Finished(PastVerdict.WithinLimit), months[0].status)
        assertEquals(YearMonthStatus.Finished(PastVerdict.WithinLimit), months[1].status)
        assertEquals(YearMonthStatus.Finished(PastVerdict.Over), months[2].status)
        assertEquals(YearMonthStatus.Finished(null), months[3].status)
        assertEquals(YearMonthStatus.InProgress, months[4].status)
        assertEquals(Money.cop(10_000), months[4].spent)
        assertEquals(Money.cop(100_001), months[2].spent)
        months.drop(5).forEach {
            assertEquals(YearMonthStatus.Upcoming, it.status)
            assertNull(it.spent)
        }
    }

    @Test
    fun aMonthIsJudgedOnItsLimitWithCarry() {
        val snapshot = read(
            2027,
            "2027-03-05",
            listOf(
                expense("2027-01-10", 60_000, category = 1),
                expense("2027-02-10", 130_000, category = 1)
            ),
            carry = { month ->
                if (month.monthValue == 2) {
                    listOf(
                        comidaInput(
                            listOf(chain(ene)),
                            ene,
                            month,
                            "2027-03-05",
                            listOf("2027-01-10" to 60_000L, "2027-02-10" to 130_000L)
                        )
                    )
                } else {
                    emptyList()
                }
            },
            categories = { listOf(line(1, "Comida", 100_000)) }
        )

        // January leaves 100.000 − 60.000 = 40.000, so February holds 140.000: 130.000 is
        // within it, though over the base 100.000.
        assertEquals(YearMonthStatus.Finished(PastVerdict.WithinLimit), snapshot.months[1].status)
    }

    // --- Lo que entró ---

    @Test
    fun incomeMinusSpendingIsSigned() {
        val snapshot = read(
            2026,
            "2026-11-06",
            listOf(expense("2026-02-01", 1_500_000)),
            incomes = listOf(
                income("2026-01-15", 600_000),
                income("2026-06-15", 400_000),
                // Later than today, or another year: not the year's.
                income("2026-11-20", 900_000),
                income("2025-12-31", 800_000)
            )
        )

        // 600.000 + 400.000 = 1.000.000 in; 1.000.000 − 1.500.000 = −500.000.
        assertEquals(Money.cop(1_000_000), snapshot.income)
        assertEquals(Money.cop(-500_000), snapshot.netFlow)
        assertEquals(2, snapshot.incomeCount)
    }

    // --- ¿En qué se fue? ---

    @Test
    fun categoriesShowTheirShareOfTheYearHalfUp() {
        val categories = listOf(
            line(1, "Hogar", null),
            line(2, "Alimentación", null),
            line(3, "Transporte", null),
            line(4, "Entretenimiento", null),
            line(5, "Café", null),
            line(6, "Viejo", null, archived = true),
            line(7, "Sin gastos", null)
        )
        val snapshot = read(
            2026,
            "2026-11-06",
            listOf(
                expense("2026-01-05", 3_600_000, category = 1),
                expense("2026-07-05", 3_000_000, category = 1),
                expense("2026-02-05", 3_910_000, category = 2),
                expense("2026-03-05", 1_420_000, category = 3),
                expense("2026-04-05", 1_150_000, category = 4),
                expense("2026-05-05", 620_000, category = 5),
                expense("2026-06-05", 530_000, category = 6)
            ),
            categories = { categories }
        )

        // Of 14.230.000: 6.600.000 is 46,38 % → 46; 3.910.000 is 27,48 % → 27; 1.420.000
        // is 9,98 % → 10; 1.150.000 is 8,08 % → 8; 620.000 is 4,36 % → 4; the archived
        // 530.000 is 3,72 % → 4. A category with nothing spent is not listed.
        assertEquals(
            listOf(
                Triple("Hogar", 6_600_000L, 46),
                Triple("Alimentación", 3_910_000L, 27),
                Triple("Transporte", 1_420_000L, 10),
                Triple("Entretenimiento", 1_150_000L, 8),
                Triple("Café", 620_000L, 4),
                Triple("Viejo", 530_000L, 4)
            ),
            snapshot.categories.map {
                Triple(it.category.name.value, it.spent.minorUnits, it.percent)
            }
        )
    }

    // --- Years to move between ---

    @Test
    fun theFirstYearIsTheFirstWithAnythingRecorded() {
        val recordedBefore = read(2026, "2026-11-06", emptyList(), firstRecorded = "2024-03-01")
        val nothing = read(2026, "2026-11-06", emptyList())

        assertEquals(2024, recordedBefore.firstYear)
        assertEquals(2026, nothing.firstYear)
        assertEquals(0, nothing.expenseCount)
    }

    @Test
    fun aYearAfterTheCurrentOneCannotBeShown() {
        val day = FinancialDay(LocalDate.parse("2026-11-06"), bogota)

        val failure = YearPolicy.snapshot(
            2027,
            day,
            1,
            1,
            YearInput(emptyList(), emptyList(), emptyList(), null)
        ).errorOrFail()

        assertEquals(SnapshotFailure.FutureMonth(YearMonth.of(2026, 11)), failure)
    }

    // --- Récords del año ---

    private fun found(snapshot: YearSnapshot) = snapshot.records as YearRecords.Found

    private fun noted(date: String, pesos: Long, note: String, category: Int = 1) =
        expense(date, pesos, category).copy(note = Note.of(note).valueOrFail())

    @Test
    fun recordsComeFromFinishedMonthsOnly() {
        // To 6 November: January to October are finished, November is in progress.
        // Each month in two expenses so that none is above February's 1.200.000.
        val expenses = listOf(
            expense("2026-01-10", 650_000), expense("2026-01-20", 650_000), // 1.300.000
            noted("2026-02-03", 1_200_000, "Matrícula"), expense("2026-02-10", 50_000),
            expense("2026-03-10", 920_000), expense("2026-03-20", 920_000), // 1.840.000
            expense("2026-04-10", 645_000), expense("2026-04-20", 645_000), // 1.290.000
            expense("2026-05-10", 700_000), expense("2026-05-20", 700_000), // 1.400.000
            expense("2026-06-10", 650_000), expense("2026-06-20", 650_000), // 1.300.000
            expense("2026-07-10", 810_000), expense("2026-07-20", 810_000), // 1.620.000
            expense("2026-08-10", 510_000), expense("2026-08-20", 510_000), // 1.020.000
            expense("2026-09-10", 635_000), expense("2026-09-20", 635_000), // 1.270.000
            expense("2026-10-10", 735_000), expense("2026-10-20", 735_000), // 1.470.000
            // In progress: dearer and bigger than any, and still not a record.
            expense("2026-11-02", 2_500_000)
        )

        val records = found(read(2026, "2026-11-06", expenses))

        assertEquals(YearMonthRecord(YearMonth.of(2026, 3), Money.cop(1_840_000)), records.dearest)
        assertEquals(YearMonthRecord(YearMonth.of(2026, 8), Money.cop(1_020_000)), records.calmest)
        assertEquals(Money.cop(1_200_000), records.biggest.expense.amount.money)
        assertEquals(LocalDate.parse("2026-02-03"), records.biggest.expense.date)
        assertEquals("Matrícula", records.biggest.expense.note?.value)
        assertEquals("Hogar", records.biggest.category.name.value)
        // Hogar has no limit: nothing went over one.
        assertNull(records.mostOver)
    }

    @Test
    fun theCalmestMonthHasAtLeastOneExpense() {
        // 2025 is finished; January and December have nothing recorded.
        val records = found(
            read(
                2025,
                "2026-01-15",
                listOf(expense("2025-02-10", 300_000), expense("2025-03-10", 200_000))
            )
        )

        assertEquals(YearMonthRecord(YearMonth.of(2025, 2), Money.cop(300_000)), records.dearest)
        assertEquals(YearMonthRecord(YearMonth.of(2025, 3), Money.cop(200_000)), records.calmest)
    }

    @Test
    fun theCategoryMostOftenOverItsLimitCountsFromTwoMonths() {
        val categories = listOf(
            line(1, "Hogar", null),
            line(2, "Entretenimiento", 100_000),
            line(3, "Café", 50_000)
        )
        val hogar = (1..12).map { expense("2025-%02d-05".format(it), 400_000) }
        // Entretenimiento over in four months, Café in two and Café's 40.000 within it.
        val over = listOf("02", "04", "06", "09").map { expense("2025-$it-12", 150_000, 2) } +
            listOf("03", "05").map { expense("2025-$it-12", 60_000, 3) } +
            expense("2025-07-12", 40_000, 3)

        val records = found(read(2025, "2026-01-15", hogar + over, categories = { categories }))

        assertEquals("Entretenimiento", records.mostOver?.category?.name?.value)
        assertEquals(4, records.mostOver?.months)
    }

    @Test
    fun oneMonthOverItsLimitIsNoRecord() {
        val categories = listOf(line(1, "Hogar", null), line(2, "Café", 50_000))
        val expenses = listOf(
            expense("2025-02-05", 400_000),
            expense("2025-03-05", 400_000),
            expense("2025-03-12", 60_000, 2)
        )

        val records = found(read(2025, "2026-01-15", expenses, categories = { categories }))

        assertNull(records.mostOver)
    }

    @Test
    fun tiesGoToTheEarlierMonthTheEarlierExpenseAndNameOrder() {
        // Listed Café first, so only name order puts Bar ahead.
        val categories = listOf(
            line(1, "Hogar", null),
            line(2, "Café", 10_000),
            line(3, "Bar", 10_000)
        )
        val expenses = listOf(
            // May and June: 500.000 each, the dearest. February and March: 60.000 of Hogar
            // and 40.000 below, 100.000 each, the calmest. Four expenses of 250.000.
            expense("2025-02-20", 60_000),
            expense("2025-03-20", 60_000),
            noted("2025-05-03", 250_000, "Primero"),
            noted("2025-05-04", 250_000, "Segundo"),
            expense("2025-06-03", 250_000),
            expense("2025-06-20", 250_000),
            // Café and Bar each over in two months: 20.000 against 10.000.
            expense("2025-02-21", 20_000, 2),
            expense("2025-03-21", 20_000, 2),
            expense("2025-02-22", 20_000, 3),
            expense("2025-03-22", 20_000, 3)
        )

        val records = found(read(2025, "2026-01-15", expenses, categories = { categories }))

        assertEquals(YearMonthRecord(YearMonth.of(2025, 5), Money.cop(500_000)), records.dearest)
        assertEquals(YearMonthRecord(YearMonth.of(2025, 2), Money.cop(100_000)), records.calmest)
        assertEquals("Primero", records.biggest.expense.note?.value)
        assertEquals("Bar", records.mostOver?.category?.name?.value)
        assertEquals(2, records.mostOver?.months)
    }

    @Test
    fun onTheSameDayTheExpenseRecordedFirstIsTheBiggest() {
        val expenses = listOf(
            noted("2025-04-04", 300_000, "Antes"),
            noted("2025-04-04", 300_000, "Después"),
            expense("2025-05-04", 100_000)
        )

        val records = found(read(2025, "2026-01-15", expenses))

        assertEquals("Antes", records.biggest.expense.note?.value)
    }

    @Test
    fun withOneFinishedMonthThereAreNoRecordsYet() {
        // January finished, February in progress.
        val inProgress = read(
            2026,
            "2026-02-10",
            listOf(expense("2026-01-10", 300_000), expense("2026-02-05", 100_000))
        )
        // A whole year with spending in December only.
        val lateStart = read(2025, "2026-01-15", listOf(expense("2025-12-10", 300_000)))

        assertEquals(YearRecords.TooFew, inProgress.records)
        assertEquals(YearRecords.TooFew, lateStart.records)
    }
}
