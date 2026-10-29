package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reservations on Recibo by recurrence-examples RE25–RE34 and RE40 (current month, jue
 * 22 oct 2026, D = 10, L 1.000.000) and ADR 0012's BG-E02, E03, E06 and E11. Every figure
 * is copied from there, where it was worked out by hand.
 */
class ReservationPolicyTest {
    private val october = YearMonth.of(2026, 10)
    private val today = FinancialDay(LocalDate.parse("2026-10-22"), ZoneId.of("America/Bogota"))
    private val home = CategoryId.of("00000000-0000-4000-8000-000000000100").valueOrFail()

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private val category = CategoryInMonth(
        Category(
            home,
            CategoryName.of("Hogar").valueOrFail(),
            IconKey.of("home").valueOrFail(),
            archived = false,
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ),
        MonthBudgetState.Limited(
            BudgetLimit.finite(Money.cop(1_000_000)).valueOrFail(),
            explicit = true
        )
    )
    private val unlimited = category.copy(
        state = MonthBudgetState.Limited(BudgetLimit.Unlimited, explicit = true)
    )

    private fun spent(pesos: Long, date: String = "2026-10-05") = Expense(
        ExpenseId.of("00000000-0000-4000-8000-000000000900").valueOrFail(),
        home,
        d(date),
        1,
        pesos(pesos),
        null,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun payment(
        number: Int,
        day: Int,
        amount: Long,
        handled: String? = null,
        pauses: List<SchedulePause> = emptyList()
    ) = StoredSchedule(
        Schedule(
            ScheduleId.of("00000000-0000-4000-8000-%012d".format(number)).valueOrFail(),
            ScheduledEntry.Payment(home, pesos(amount), null),
            Recurrence.of(RepeatRule.Monthly.of(day).valueOrFail(), d("2026-09-01"), null, pauses)
                .valueOrFail()
        ),
        sequence = number.toLong(),
        handled = handled?.let { Occurrence(d(it), 1) },
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun limited(
        spentPesos: Long,
        schedules: List<StoredSchedule>,
        month: YearMonth = october,
        categories: List<CategoryInMonth> = listOf(category)
    ): OverallBudget {
        val snapshot = MonthlyBudgetPolicy.snapshot(
            month,
            today,
            1,
            1,
            categories,
            listOf(spent(spentPesos, month.atDay(5).toString())),
            ReservationPolicy.reserved(schedules, month)
        ).valueOrFail()
        return snapshot.overall
    }

    /** RE37: a salary still expected on the 30th sets nothing aside: 600.000, 60.000 per day. */
    @Test
    fun re37anIncomeStillExpectedLeavesTheHeadlineAndTheDailyFigureAlone() {
        val salary = StoredSchedule(
            Schedule(
                ScheduleId.of("00000000-0000-4000-8000-000000000002").valueOrFail(),
                ScheduledEntry.Income(
                    IncomeSource.of("salary", null).valueOrFail(),
                    pesos(1_000_000),
                    null
                ),
                Recurrence.of(RepeatRule.Monthly.of(30).valueOrFail(), d("2026-09-01"), null)
                    .valueOrFail()
            ),
            sequence = 2,
            handled = Occurrence(d("2026-09-30"), 1),
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        val with = limited(400_000, listOf(salary)) as OverallBudget.Limited

        assertEquals(limited(400_000, emptyList()), with)
        assertNull(with.reservation)
        assertEquals(Money.cop(600_000), with.budget.remaining)
        assertEquals(Money.cop(60_000), with.allowance?.amount)
    }

    /** RE25, BG-E02: rent 200.000 due 28 oct keeps 400.000 and 40.000 per day, Al día. */
    @Test
    fun re25aReservedPaymentLowersTheHeadlineAndTheDailyFigure() {
        val overall = limited(
            400_000,
            listOf(payment(1, 28, 200_000, "2026-09-28"))
        ) as OverallBudget.Limited

        val reservation = overall.reservation!!
        assertEquals(Money.cop(600_000), overall.budget.remaining)
        assertEquals(Money.cop(200_000), reservation.total)
        assertEquals(Money.cop(400_000), reservation.margin)
        assertEquals(ReservedStanding.Enough, reservation.standing)
        assertEquals(1, reservation.payments.size)
        assertEquals(Money.cop(40_000), overall.allowance?.amount)
    }

    /** RE26, BG-E03: due today and not recorded yet, it is still reserved; recorded, it is not. */
    @Test
    fun re26aPaymentDueTodayStaysReservedUntilCatchUpRecordsIt() {
        val before = limited(
            400_000,
            listOf(payment(1, 22, 200_000, "2026-09-22"))
        ) as OverallBudget.Limited
        val after = limited(
            600_000,
            listOf(payment(1, 22, 200_000, "2026-10-22"))
        ) as OverallBudget.Limited

        assertEquals(Money.cop(400_000), before.reservation?.margin)
        assertEquals(Money.cop(40_000), before.allowance?.amount)
        // The headline never moves: 400.000 either way, and the footnote goes away.
        assertNull(after.reservation)
        assertEquals(Money.cop(400_000), after.budget.remaining)
        assertEquals(Money.cop(40_000), after.allowance?.amount)
    }

    /** RE27, BG-E11: 100.000 left and 100.000 reserved is Sin margen, 0 per day. */
    @Test
    fun re27reservationsThatUseUpWhatIsLeftAreSinMargen() {
        val overall = limited(
            900_000,
            listOf(payment(1, 28, 100_000, "2026-09-28"))
        ) as OverallBudget.Limited

        assertEquals(Money.cop(0), overall.reservation?.margin)
        assertEquals(ReservedStanding.NoMargin, overall.reservation?.standing)
        assertEquals(Money.cop(0), overall.allowance?.amount)
    }

    /** RE28, BG-E06: 150.000 reserved against 100.000 left is Revisar, 50.000 short. */
    @Test
    fun re28moreReservedThanLeftIsRevisarWithTheShortfall() {
        val overall = limited(
            900_000,
            listOf(payment(1, 28, 150_000, "2026-09-28"))
        ) as OverallBudget.Limited

        assertEquals(Money.cop(100_000), overall.budget.remaining)
        assertEquals(Money.cop(-50_000), overall.reservation?.margin)
        assertEquals(ReservedStanding.Short, overall.reservation?.standing)
        assertEquals(Money.cop(50_000), overall.reservation?.shortfall)
        assertNull(overall.reservation?.overLimitBy)
        assertEquals(Money.cop(0), overall.allowance?.amount)
    }

    /** RE29: already 80.000 over with 200.000 reserved is Revisar, not Excedido: 280.000 short. */
    @Test
    fun re29overTheLimitWithPaymentsReservedIsStillRevisar() {
        val overall = limited(
            1_080_000,
            listOf(payment(1, 28, 200_000, "2026-09-28"))
        ) as OverallBudget.Limited

        assertEquals(Money.cop(-80_000), overall.budget.remaining)
        assertEquals(Money.cop(-280_000), overall.reservation?.margin)
        assertEquals(ReservedStanding.Short, overall.reservation?.standing)
        // Falta para tus pagos 280.000; Pasaste tu límite por 80.000.
        assertEquals(Money.cop(280_000), overall.reservation?.shortfall)
        assertEquals(Money.cop(80_000), overall.reservation?.overLimitBy)
        // The recorded verdict is still Excedido; Recibo puts Revisar before it.
        assertEquals(
            Verdict.Over,
            (overall.budget.standing as BudgetStanding.Current).verdict
        )
    }

    /** RE30: 120.000 on the 25th and 80.000 on the 30th are 200.000 for 2 payments. */
    @Test
    fun re30twoPaymentsAreSummedAndCounted() {
        val overall = limited(
            400_000,
            listOf(payment(1, 25, 120_000, "2026-09-25"), payment(2, 30, 80_000, "2026-09-30"))
        ) as OverallBudget.Limited

        assertEquals(Money.cop(200_000), overall.reservation?.total)
        assertEquals(2, overall.reservation?.payments?.size)
        assertEquals(
            listOf(d("2026-10-25"), d("2026-10-30")),
            overall.reservation?.payments?.map {
                it.occurrence.date
            }
        )
    }

    /** RE31 and RE32: next month's payment and a paused one reserve nothing. */
    @Test
    fun re31re32nextMonthAndPausedReserveNothing() {
        val overall = limited(
            400_000,
            listOf(
                payment(1, 5, 950_000, "2026-10-05"),
                payment(2, 28, 200_000, "2026-09-28", listOf(SchedulePause(d("2026-10-20"), null)))
            )
        ) as OverallBudget.Limited

        assertNull(overall.reservation)
        assertEquals(Money.cop(60_000), overall.allowance?.amount)
    }

    /** RE33 and RE34: no finite limit, or a past month, shows no reservation. */
    @Test
    fun re33re34noLimitOrAPastMonthReservesNothing() {
        val noLimit =
            limited(
                400_000,
                listOf(payment(1, 28, 200_000, "2026-09-28")),
                categories = listOf(unlimited)
            )
        val september =
            limited(400_000, listOf(payment(1, 28, 200_000, "2026-08-28")), YearMonth.of(2026, 9))

        assertEquals(OverallBudget.NoLimit, noLimit)
        assertNull((september as OverallBudget.Limited).reservation)
    }

    /** RE40: two payments of 999.999.999.999 reserve 1.999.999.999.998 exactly. */
    @Test
    fun re40theReservedTotalIsExact() {
        val reserved = ReservationPolicy.reserved(
            listOf(
                payment(1, 25, 999_999_999_999, "2026-09-25"),
                payment(2, 30, 999_999_999_999, "2026-09-30")
            ),
            october
        )

        assertEquals(
            Money.cop(1_999_999_999_998),
            com.software.sello.domain.model.ExactTotal.of(
                Currency.COP,
                reserved.map {
                    it.amount.money
                }
            ).toMoney().valueOrFail()
        )
    }
}
