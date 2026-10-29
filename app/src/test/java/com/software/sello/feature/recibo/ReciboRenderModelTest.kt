package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.StampInk
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Snapshots here are worked out by the real policy from rows; every expected figure
 * was worked out by hand from those rows and is typed, not computed.
 */
class ReciboRenderModelTest {
    private val render = ReciboRenderModel(testMoney())

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val food = categoryIn(1, "Alimentación", 900_000, icon = "restaurant")
    private val coffee = categoryIn(2, "Café", 300_000)
    private val transport = categoryIn(3, "Transporte", 200_000)
    private val leisure = categoryIn(4, "Entretenimiento", 100_000)
    private val art = categoryIn(5, "Arte", null)
    private val everything = listOf(art, transport, leisure, food, coffee)

    private var next = 100

    private fun spent(category: Int, month: String, vararg amounts: Long): List<Expense> =
        amounts.mapIndexed { index, amount ->
            storedExpense(next++, category, "$month-%02d".format(index + 1), amount)
        }

    /** 23 October: nine days left, today included. */
    private fun october(): List<Expense> = spent(1, "2026-10", 600_000, 12_400) +
        spent(2, "2026-10", 50_000, 30_000, 20_000) +
        spent(3, "2026-10", 145_000) +
        spent(4, "2026-10", 121_500) +
        spent(5, "2026-10", 5_000, 5_000, 5_000, 5_000, 5_000, 5_000, 5_000, 5_000)

    private fun shown(snapshot: SnapshotState) = render.recibo(snapshot) as ReciboState.Month

    @Test
    fun theMonthInProgressSaysWhatIsLeftWithItsVerdictDaysAndDailyAmount() {
        val state = shown(ReadState.Ready(monthOf("2026-10-23", everything, october())))

        // Limits 900.000 + 300.000 + 200.000 + 100.000 = 1.500.000. Spent 612.400 +
        // 100.000 + 145.000 + 121.500 + 40.000 = 1.018.900. Left 481.100; over nine
        // days that is 53.455,5, shown as 53.450. Below the limits: Al día.
        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                plain("481.100"),
                ReciboStamp.OnTrack,
                perDay = ReciboPerDay(plain("53.450"), days = 9)
            ),
            state.summary
        )
        assertEquals(false, state.stale)
    }

    @Test
    fun categoriesComeMostUsedFirstWithUnlimitedLastAndEachOwnExactFigures() {
        val state = shown(ReadState.Ready(monthOf("2026-10-23", everything, october())))

        assertEquals(
            listOf("Café", "Alimentación", "Entretenimiento", "Transporte", "Arte"),
            state.categories.map { it.name }
        )
        val byName = state.categories.associateBy { it.name }
        // 612.400 of 900.000: below its limit.
        assertEquals(
            ReciboCategory(
                id = uuid(1),
                name = "Alimentación",
                iconKey = "restaurant",
                archived = false,
                figure = BudgetFigure(
                    ReciboHeadline.Remaining,
                    plain("287.600"),
                    ReciboStamp.OnTrack
                ),
                cellAmount = plain("287.600"),
                spent = plain("612.400"),
                limit = plain("900.000"),
                fraction = byName.getValue("Alimentación").fraction,
                over = false,
                expenseCount = 2
            ),
            byName.getValue("Alimentación")
        )
        // 145.000 of 200.000: below its limit, even though this pace would end near 195.435.
        assertEquals(
            BudgetFigure(ReciboHeadline.Remaining, plain("55.000"), ReciboStamp.OnTrack),
            byName.getValue("Transporte").figure
        )
        // 121.500 of 100.000: the cell keeps the minus, the sentence says it in words.
        val over = byName.getValue("Entretenimiento")
        assertEquals(
            BudgetFigure(ReciboHeadline.Overspent, plain("21.500"), ReciboStamp.Over),
            over.figure
        )
        assertEquals(minus("21.500"), over.cellAmount)
        assertEquals(true, over.over)
        assertEquals(plain("100.000"), over.limit)
        // No limit: what was spent, nothing to fill and nothing to judge.
        val open = byName.getValue("Arte")
        assertEquals(
            BudgetFigure(ReciboHeadline.Spent, plain("40.000"), ReciboStamp.NoLimit),
            open.figure
        )
        assertEquals(plain("40.000"), open.cellAmount)
        assertNull(open.limit)
        assertNull(open.fraction)
        assertEquals(false, open.over)
        assertEquals(8, open.expenseCount)
    }

    @Test
    fun theCircleFillsWithTheShareSpentAndIsFullWhenOverOrWithNothingToSpend() {
        val nothing = categoryIn(6, "Nada", 0)
        val untouched = categoryIn(7, "Sin tocar", 50_000)
        val rows = october() + spent(6, "2026-10", 1_000)
        val state = shown(
            ReadState.Ready(monthOf("2026-10-23", everything + nothing + untouched, rows))
        )
        val fill = state.categories.associate { it.name to it.fraction }

        // 100.000 of 300.000 and 145.000 of 200.000.
        assertEquals(0.3333f, fill.getValue("Café")!!, 0.0001f)
        assertEquals(0.725f, fill.getValue("Transporte")!!, 0.0001f)
        assertEquals(1f, fill.getValue("Entretenimiento"))
        assertEquals(1f, fill.getValue("Nada"))
        assertEquals(0f, fill.getValue("Sin tocar"))
    }

    @Test
    fun aFinishedMonthShowsWhatWasLeftSignedAndWhatItSpentOfItsLimitButNothingPerDay() {
        val september = YearMonth.of(2026, 9)
        val within = shown(
            ReadState.Ready(
                monthOf("2026-10-23", listOf(food, coffee), spent(1, "2026-09", 700_000), september)
            )
        )
        val over = shown(
            ReadState.Ready(
                monthOf(
                    "2026-10-23",
                    listOf(food, coffee),
                    spent(1, "2026-09", 700_000) + spent(2, "2026-09", 300_000, 250_000),
                    september
                )
            )
        )

        // Limits 1.200.000: 700.000 leaves 500.000; 1.250.000 is 50.000 over.
        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                plain("500.000"),
                ReciboStamp.WithinLimit,
                closed = ReciboClosedMonth(spent = plain("700.000"), limit = plain("1.200.000"))
            ),
            within.summary
        )
        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                minus("50.000"),
                ReciboStamp.OverLimit,
                closed = ReciboClosedMonth(spent = plain("1.250.000"), limit = plain("1.200.000"))
            ),
            over.summary
        )
        assertEquals(
            listOf(
                BudgetFigure(ReciboHeadline.Overspent, plain("250.000"), ReciboStamp.OverLimit),
                BudgetFigure(ReciboHeadline.LeftOver, plain("200.000"), ReciboStamp.WithinLimit)
            ),
            over.categories.map { it.figure }
        )
    }

    @Test
    fun spendingExactlyTheLimitLeavesZeroAndIsNotOver() {
        val state = shown(
            ReadState.Ready(
                monthOf(
                    "2026-10-23",
                    listOf(coffee),
                    spent(2, "2026-09", 300_000),
                    YearMonth.of(2026, 9)
                )
            )
        )

        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                plain("0"),
                ReciboStamp.WithinLimit,
                closed = ReciboClosedMonth(spent = plain("300.000"), limit = plain("300.000"))
            ),
            state.summary
        )
        assertEquals(false, state.categories.single().over)
        assertEquals(plain("0"), state.categories.single().cellAmount)
    }

    @Test
    fun spendingExactlyTheLimitThisMonthIsAlLimiteWithNothingLeftPerDay() {
        val state = shown(
            ReadState.Ready(monthOf("2026-10-23", listOf(coffee), spent(2, "2026-10", 300_000)))
        )

        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                plain("0"),
                ReciboStamp.AtLimit,
                perDay = ReciboPerDay(plain("0"), days = 9)
            ),
            state.summary
        )
        assertEquals(
            BudgetFigure(ReciboHeadline.Remaining, plain("0"), ReciboStamp.AtLimit),
            state.categories.single().figure
        )
        assertEquals(false, state.categories.single().over)
    }

    @Test
    fun eachStampHasTheInkOfWhatItSays() {
        assertEquals(StampInk.Good, stampInk(ReciboStamp.OnTrack))
        assertEquals(StampInk.Good, stampInk(ReciboStamp.WithinLimit))
        assertEquals(StampInk.Neutral, stampInk(ReciboStamp.AtLimit))
        assertEquals(StampInk.Neutral, stampInk(ReciboStamp.NoLimit))
        assertEquals(StampInk.Neutral, stampInk(ReciboStamp.Paused))
        assertEquals(StampInk.Neutral, stampInk(ReciboStamp.NotSet))
        assertEquals(StampInk.Over, stampInk(ReciboStamp.Over))
        assertEquals(StampInk.Over, stampInk(ReciboStamp.OverLimit))
    }

    @Test
    fun anOverspentMonthInProgressShowsAMinusAndStillSaysZeroForItsLastDay() {
        val state = shown(
            ReadState.Ready(
                monthOf("2026-10-31", listOf(coffee), spent(2, "2026-10", 300_001))
            )
        )

        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                minus("1"),
                ReciboStamp.Over,
                perDay = ReciboPerDay(plain("0"), days = 1),
                adjustable = true
            ),
            state.summary
        )
    }

    @Test
    fun withNoLimitAnywhereItSaysWhatWasSpentAndJudgesNothing() {
        val state = shown(
            ReadState.Ready(monthOf("2026-10-23", listOf(art), spent(5, "2026-10", 80_000)))
        )

        assertEquals(
            ReciboSummary(ReciboAnswerLabel.Spent, plain("80.000"), ReciboStamp.NoLimit),
            state.summary
        )
    }

    @Test
    fun aPausedOrUnconfiguredCategoryShowsItsSpendingAndSaysWhyThereIsNoLimit() {
        val paused = categoryIn(8, "Vieja", null, archived = true)
            .copy(state = MonthBudgetState.Paused)
        val unset = categoryIn(9, "Nueva", null).copy(state = MonthBudgetState.Unconfigured)
        val state = shown(
            ReadState.Ready(
                monthOf("2026-10-23", listOf(paused, unset), spent(8, "2026-10", 7_000))
            )
        )

        assertEquals(
            listOf(
                Triple("Vieja", true, ReciboStamp.Paused),
                Triple("Nueva", false, ReciboStamp.NotSet)
            ),
            state.categories.map { Triple(it.name, it.archived, it.figure.stamp) }
        )
        assertEquals(plain("7.000"), state.categories.first().cellAmount)
    }

    @Test
    fun amountsTooLargeForAnyScreenAreStillWholeAndExact() {
        val huge = categoryIn(1, "Grande", 9_000_000_000_000_000)
        val state = shown(
            ReadState.Ready(
                monthOf(
                    "2026-10-31",
                    listOf(huge),
                    spent(1, "2026-10", 999_999_999_999, 999_999_999_999, 1)
                )
            )
        )

        // 9.000.000.000.000.000 less 1.999.999.999.999.
        assertEquals(plain("8.998.000.000.000.001"), state.summary.amount)
        assertEquals(plain("1.999.999.999.999"), state.categories.single().spent)
    }

    @Test
    fun nothingReadYetIsLoadingAndAMonthWithoutCategoriesIsTheFirstRun() {
        assertEquals(ReciboState.Loading, render.recibo(ReadState.Loading))
        assertEquals(
            ReciboState.FirstRun,
            render.recibo(ReadState.Ready(monthOf("2026-10-23", emptyList())))
        )
    }

    @Test
    fun aFailedReadIsNeverAnEmptyMonthAndOlderFiguresAreMarkedAsOlder() {
        val failure = SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException"))
        val earlier = monthOf("2026-10-23", everything, october())

        val first = render.recibo(ReadState.Failed(failure, lastGood = null))
        val later = render.recibo(ReadState.Failed(failure, lastGood = earlier))

        assertEquals(ReciboState.Failed, first)
        assertEquals(shown(ReadState.Ready(earlier)).copy(stale = true), later)
        assertEquals(true, (later as ReciboState.Month).stale)
    }

    @Test
    fun onlyAMonthInProgressThatIsExcedidoOffersToAdjustTheBudget() {
        val september = YearMonth.of(2026, 9)
        fun adjustable(today: String, rows: List<Expense>, month: YearMonth? = null) = shown(
            ReadState.Ready(
                if (month == null) {
                    monthOf(today, listOf(coffee, art), rows)
                } else {
                    monthOf(today, listOf(coffee, art), rows, month)
                }
            )
        ).summary.adjustable

        // Café's limit is 300.000; Arte has none.
        assertEquals(
            listOf(false, false, true, false, false),
            listOf(
                adjustable("2026-10-23", spent(2, "2026-10", 100_000)),
                adjustable("2026-10-23", spent(2, "2026-10", 300_000)),
                adjustable("2026-10-23", spent(2, "2026-10", 300_001)),
                adjustable("2026-10-23", spent(2, "2026-09", 400_000), september),
                adjustable("2026-10-23", spent(2, "2026-09", 100_000), september)
            )
        )
    }

    @Test
    fun aMonthOverOnlyBecauseOfCategoriesWithoutALimitStillOffersToAdjust() {
        // Café keeps 50.000 of its 300.000, but 300.000 more went to Arte, which has no
        // limit: 550.000 against 300.000 overall. No single category is over.
        val state = shown(
            ReadState.Ready(
                monthOf(
                    "2026-10-23",
                    listOf(coffee, art),
                    spent(2, "2026-10", 250_000) + spent(5, "2026-10", 300_000)
                )
            )
        )

        assertEquals(ReciboStamp.Over to true, state.summary.stamp to state.summary.adjustable)
        assertEquals(false, state.categories.any { it.over })
    }

    @Test
    fun withNoLimitThereIsNothingToAdjust() {
        val state = shown(
            ReadState.Ready(monthOf("2026-10-23", listOf(art), spent(5, "2026-10", 80_000)))
        )

        assertEquals(false, state.summary.adjustable)
    }

    // --- Payments reserved (SELLO-101, recurrence examples RE25–RE29) ---

    /** 22 October, ten days left: Hogar limited to 1.000.000 and [spentPesos] spent in it. */
    private fun reserved(spentPesos: Long, vararg payments: Long): ReciboSummary {
        val reserved = payments.mapIndexed { index, pesos ->
            ReservedPayment(
                (ScheduleId.of(uuid(700 + index)) as Outcome.Success).value,
                categoryId(6),
                null,
                Occurrence(LocalDate.parse("2026-10-28"), 1),
                pesos(pesos)
            )
        }
        val snapshot = (
            MonthlyBudgetPolicy.snapshot(
                YearMonth.of(2026, 10),
                FinancialDay(LocalDate.parse("2026-10-22"), ZoneId.of("America/Bogota")),
                1,
                1,
                listOf(categoryIn(6, "Hogar", 1_000_000)),
                listOf(storedExpense(next++, 6, "2026-10-05", spentPesos)),
                reserved
            ) as Outcome.Success
            ).value
        return shown(ReadState.Ready(snapshot)).summary
    }

    @Test
    fun re25aReservedPaymentLeavesAlDiaWithItsFootnote() {
        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                plain("400.000"),
                ReciboStamp.OnTrack,
                ReciboPerDay(plain("40.000"), 10),
                reserved = ReciboReserved(plain("200.000"), 1)
            ),
            reserved(400_000, 200_000)
        )
    }

    @Test
    fun re27reservedPaymentsThatUseUpWhatIsLeftAreSinMargenAtZero() {
        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.Remaining,
                plain("0"),
                ReciboStamp.NoMargin,
                ReciboPerDay(plain("0"), 10),
                reserved = ReciboReserved(plain("100.000"), 1)
            ),
            reserved(900_000, 100_000)
        )
    }

    @Test
    fun re28moreReservedThanLeftIsRevisarWithWhatIsMissingAndTheTwoFacts() {
        assertEquals(
            ReciboSummary(
                ReciboAnswerLabel.ShortForPayments,
                plain("50.000"),
                ReciboStamp.Review,
                ReciboPerDay(plain("0"), 10),
                adjustable = true,
                reserved = ReciboReserved(plain("150.000"), 1),
                shortfall = ReciboShortfall(
                    plain("100.000"),
                    over = false,
                    reserved = plain("150.000")
                )
            ),
            reserved(900_000, 150_000)
        )
    }

    @Test
    fun re29pastTheLimitWithPaymentsReservedIsRevisarNotExcedido() {
        val summary = reserved(1_080_000, 120_000, 80_000)

        assertEquals(ReciboAnswerLabel.ShortForPayments, summary.label)
        assertEquals(plain("280.000"), summary.amount)
        assertEquals(ReciboStamp.Review, summary.stamp)
        assertEquals(
            ReciboShortfall(plain("80.000"), over = true, reserved = plain("200.000")),
            summary.shortfall
        )
        assertEquals(ReciboReserved(plain("200.000"), 2), summary.reserved)
        assertEquals(StampInk.Warn, stampInk(summary.stamp))
    }
}
