package com.software.sello.feature.resumen

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.ResumenMonthInput
import com.software.sello.domain.policy.ResumenPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.testing.testDateLabels
import com.software.sello.testing.testMoney
import com.software.sello.testing.testMonthNames
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** October 2026 up to the 13th: 5 expenses of 3.000 in Café, nothing the month before. */
internal fun smallSnapshot(): ResumenSnapshot {
    val cafe = listOf(categoryIn(1, "Café", null, icon = "local_cafe"))
    val expenses = (1..5).map { storedExpense(800 + it, 1, "2026-10-0$it", 3_000) }
    val october = YearMonth.of(2026, 10)
    return (
        ResumenPolicy.snapshot(
            october,
            FinancialDay(LocalDate.parse("2026-10-13"), ZoneId.of("America/Bogota")),
            1,
            1,
            ResumenPolicy.monthsEndingIn(october).map { ResumenMonthInput(it, cafe, expenses) },
            emptyList()
        ) as Outcome.Success
        ).value
}

/**
 * Gastos hormiga's slip in Resumen (SELLO-194): what it shows, and how it follows Resumen's
 * older and failed reads. Money values are worked out by hand beside each test.
 */
class ResumenSmallExpensesTest {
    private val render = ResumenRenderModel(testMoney(), testDateLabels(), testMonthNames())
    private val october = YearMonth.of(2026, 10)
    private val default = PreferenceRead.Ready(SmallExpenseLine.DEFAULT)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val categories = listOf(
        categoryIn(1, "Café", null, icon = "local_cafe"),
        categoryIn(2, "Transporte", null, icon = "directions_bus"),
        categoryIn(3, "Alimentación", null, icon = "restaurant"),
        categoryIn(4, "Hogar", null)
    )

    private var next = 100

    private fun row(category: Int, name: String, icon: String, count: Int, digits: String) =
        ResumenSmallCategory(uuid(category), name, icon, count, plain(digits))

    private fun spend(date: String, pesos: Long, category: Int = 1) =
        storedExpense(next++, category, date, pesos)

    private fun snapshot(
        expenses: List<Expense>,
        today: String = "2026-10-28",
        month: YearMonth = october
    ): ResumenSnapshot = (
        ResumenPolicy.snapshot(
            month,
            FinancialDay(LocalDate.parse(today), ZoneId.of("America/Bogota")),
            1,
            1,
            ResumenPolicy.monthsEndingIn(month).map { ResumenMonthInput(it, categories, expenses) },
            emptyList()
        ) as Outcome.Success
        ).value

    private fun small(
        snapshot: ResumenSnapshot,
        line: PreferenceRead<SmallExpenseLine> = default,
        stale: Boolean = false
    ): ResumenSmallExpenses? {
        val read = if (stale) {
            ReadState.Failed(SnapshotFailure.Unrepresentable, snapshot)
        } else {
            ReadState.Ready(snapshot)
        }
        val state = render.render(read, october, line) as ResumenState.Ready
        assertEquals(stale, state.stale)
        return state.smallExpenses
    }

    /** October up to the 28th, and September up to its 28th. */
    private val month = listOf(
        // Café: 3 × 4.000 = 12.000.
        spend("2026-10-01", 4_000, 1),
        spend("2026-10-02", 4_000, 1),
        spend("2026-10-03", 4_000, 1),
        // Transporte: 2 × 3.000 = 6.000.
        spend("2026-10-04", 3_000, 2),
        spend("2026-10-05", 3_000, 2),
        // Alimentación: 1 × 20.000 = 20.000, at the line.
        spend("2026-10-06", 20_000, 3),
        // Hogar: 1 × 2.000 = 2.000; and 1 × 158.000, over the line.
        spend("2026-10-07", 2_000, 4),
        spend("2026-10-08", 158_000, 4),
        // September 1 to 28: 5.000 + 9.000 small; the 29th is after the same days.
        spend("2026-09-02", 5_000, 1),
        spend("2026-09-20", 9_000, 2),
        spend("2026-09-29", 7_000, 2)
    )

    @Test
    fun theSlipSaysTheTotalTheShareTheMonthBeforeAndTheTopThree() {
        val shown = small(snapshot(month)) as ResumenSmallExpenses.Shown

        // Small: 12.000 + 6.000 + 20.000 + 2.000 = 40.000 in 7 expenses. Spent:
        // 40.000 + 158.000 = 198.000; 40.000 / 198.000 = 20,20 %, half up 20 %.
        assertEquals("20.000", shown.line)
        assertEquals(plain("40.000"), shown.total)
        assertEquals(7, shown.count)
        assertEquals(20, shown.sharePercent)
        // September 1 to 28: 5.000 + 9.000 = 14.000. October fills the bar.
        assertEquals(
            listOf(
                ComparisonBar("Octubre", plain("40.000"), 1f, current = true),
                ComparisonBar("Septiembre", plain("14.000"), 14_000f / 40_000f, current = false)
            ),
            shown.bars
        )
        assertEquals(28 to 28, shown.throughDay to shown.previousThroughDay)
        assertEquals("septiembre", shown.previousName)
        // Alimentación 20.000 (1), Café 12.000 (3), Transporte 6.000 (2); Hogar 2.000 is fourth.
        assertEquals(
            listOf(
                row(3, "Alimentación", "restaurant", 1, "20.000"),
                row(1, "Café", "local_cafe", 3, "12.000"),
                row(2, "Transporte", "directions_bus", 2, "6.000")
            ),
            shown.categories
        )
        assertFalse(shown.olderLine)
    }

    @Test
    fun aSavedLineAppliesToTheMonthShown() {
        val shown = small(
            snapshot(month),
            PreferenceRead.Ready(SmallExpenseLine.of(4_000)!!)
        ) as ResumenSmallExpenses.Shown

        // At or under 4.000: 3 × 4.000 + 2 × 3.000 + 2.000 = 20.000 in 6.
        assertEquals("4.000", shown.line)
        assertEquals(plain("20.000"), shown.total)
        assertEquals(6, shown.count)
    }

    @Test
    fun withoutDataTheMonthBeforeThereAreNoBars() {
        val shown = small(snapshot(month.filter { it.date.monthValue == 10 }))
            as ResumenSmallExpenses.Shown

        assertNull(shown.bars)
    }

    @Test
    fun aFinishedMonthComparesWholeMonths() {
        val september = month.filter { it.date.monthValue == 9 } +
            (1..4).map { spend("2026-09-1$it", 1_000, 3) }
        val read = snapshot(september + spend("2026-08-31", 6_000), month = YearMonth.of(2026, 9))
        val state = render.render(ReadState.Ready(read), october, default) as ResumenState.Ready
        val shown = state.smallExpenses as ResumenSmallExpenses.Shown

        // 5.000 + 9.000 + 7.000 + 4 × 1.000 = 25.000; August: 6.000.
        assertEquals(plain("25.000"), shown.total)
        assertEquals(plain("6.000"), shown.bars!![1].amount)
        assertNull(shown.throughDay)
        assertNull(shown.previousThroughDay)
    }

    @Test
    fun fourSmallExpensesGiveNoSlipAndNoEmptyCard() {
        val four = (1..4).map { spend("2026-10-0$it", 1_000) } + spend("2026-10-05", 30_000)

        assertNull(small(snapshot(four)))
    }

    @Test
    fun anOlderResumenShowsTheSlipFromItsLastRead() {
        val shown = small(snapshot(month), stale = true) as ResumenSmallExpenses.Shown

        assertEquals(plain("40.000"), shown.total)
    }

    @Test
    fun aLineThatCannotBeReadAgainUsesTheLastOneAndSaysSo() {
        val shown = small(
            snapshot(month),
            PreferenceRead.Failed(PreferenceFailure.Unavailable, SmallExpenseLine.of(4_000))
        ) as ResumenSmallExpenses.Shown

        assertTrue(shown.olderLine)
        assertEquals(plain("20.000"), shown.total)
    }

    @Test
    fun smallExpensesThatCannotBeAddedUpAreSaidApartFromTheLine() {
        val read = snapshot(month)
        // An expense the hero never counted: the slip's sum cannot be a share of it.
        val broken = read.copy(
            periods = read.periods.copy(current = read.periods.current + spend("2026-10-09", 1_000))
        )

        assertEquals(ResumenSmallExpenses.Unavailable, small(broken))
    }

    @Test
    fun aLineNeverReadIsSaidWhereASlipCouldBeAndNeverGuessed() {
        val unread = PreferenceRead.Failed<SmallExpenseLine>(PreferenceFailure.InvalidValue, null)

        assertEquals(ResumenSmallExpenses.Unreadable, small(snapshot(month), unread))
        // Five at or under 100.000, the widest line: a slip could be there.
        val wide = (1..5).map { spend("2026-10-0$it", 90_000) }
        assertEquals(ResumenSmallExpenses.Unreadable, small(snapshot(wide), unread))
        // Four at most: no line would make a slip, so there is nothing to say.
        val four = (1..4).map { spend("2026-10-0$it", 90_000) } + spend("2026-10-05", 100_001)
        assertNull(small(snapshot(four), unread))
    }
}
