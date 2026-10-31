package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.pesos
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
 * A category's own payments still to come, on category detail (SELLO-135). Jue 22 oct 2026,
 * 10 days left including today. Hogar has a 1.500.000 limit; Arriendo (950.000, the 5th) is
 * recorded, Internet (90.000, the 23rd) and Servicios públicos (200.000, the 28th) are still
 * to come: 290.000 reserved. Comida's 50.000 on the 25th is another category's. Every
 * expected figure was worked out by hand.
 */
class CategoryReservationTest {
    private val october = YearMonth.of(2026, 10)
    private val today = FinancialDay(LocalDate.parse("2026-10-22"), ZoneId.of("America/Bogota"))
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val home = CategoryId.of(uuid(100)).valueOrFail()
    private val food = CategoryId.of(uuid(200)).valueOrFail()

    private fun category(id: CategoryId, name: String, limit: Long?) = CategoryInMonth(
        Category(
            id,
            CategoryName.of(name).valueOrFail(),
            IconKey.of("home").valueOrFail(),
            archived = false,
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ),
        MonthBudgetState.Limited(
            limit?.let { BudgetLimit.finite(Money.cop(it)).valueOrFail() } ?: BudgetLimit.Unlimited,
            explicit = true
        )
    )

    private fun spent(n: Int, pesos: Long, date: String, id: CategoryId = home) = Expense(
        ExpenseId.of(uuid(900 + n)).valueOrFail(),
        id,
        LocalDate.parse(date),
        n.toLong(),
        pesos(pesos),
        null,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun monthly(n: Int, id: CategoryId, day: Int, amount: Long, handled: String) =
        StoredSchedule(
            Schedule(
                ScheduleId.of(uuid(n)).valueOrFail(),
                ScheduledEntry.Payment(id, pesos(amount), null),
                Recurrence.of(
                    RepeatRule.Monthly.of(day).valueOrFail(),
                    LocalDate.parse("2026-09-01")
                ).valueOrFail()
            ),
            sequence = n.toLong(),
            handled = Occurrence(LocalDate.parse(handled), 1),
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

    private val schedules = listOf(
        monthly(1, home, 5, 950_000, "2026-10-05"),
        monthly(2, home, 23, 90_000, "2026-09-23"),
        monthly(3, home, 28, 200_000, "2026-09-28"),
        monthly(4, food, 25, 50_000, "2026-09-25")
    )

    private fun hogar(
        spentPesos: Long,
        limit: Long? = 1_500_000,
        month: YearMonth = october
    ): CategoryMonth = MonthlyBudgetPolicy.snapshot(
        month,
        today,
        1,
        1,
        listOf(category(home, "Hogar", limit), category(food, "Comida", 400_000)),
        listOf(spent(1, spentPesos, month.atDay(5).toString())),
        ReservationPolicy.reserved(schedules, month)
    ).valueOrFail().categories.single { it.category.id == home }

    private fun insights(spentPesos: Long): CategoryInsights = CategoryInsightsPolicy.snapshot(
        home,
        october,
        today,
        1,
        1,
        CategoryInsightsPolicy.monthsEndingIn(october).map { period ->
            CategoryHistoryInput(
                period,
                category(home, "Hogar", 1_500_000),
                if (period == october) listOf(spent(1, spentPesos, "2026-10-05")) else emptyList()
            )
        },
        ReservationPolicy.reserved(schedules, october)
    ).valueOrFail()

    @Test
    fun theCategoryReservesOnlyItsOwnPaymentsStillToCome() {
        val line = hogar(950_000)

        assertEquals(listOf(uuid(2), uuid(3)), line.upcoming.map { it.schedule.value })
        val reservation = line.reservation!!
        assertEquals(Money.cop(290_000), reservation.total)
        assertEquals(Money.cop(260_000), reservation.margin)
        assertEquals(ReservedStanding.Enough, reservation.standing)
        // What is left of the category itself is still what was recorded against it.
        assertEquals(Money.cop(550_000), line.budget!!.remaining)
    }

    @Test
    fun theMonthStillReservesEveryCategorysPayments() {
        val snapshot = MonthlyBudgetPolicy.snapshot(
            october,
            today,
            1,
            1,
            listOf(category(home, "Hogar", 1_500_000), category(food, "Comida", 400_000)),
            listOf(spent(1, 950_000, "2026-10-05")),
            ReservationPolicy.reserved(schedules, october)
        ).valueOrFail()

        val overall = snapshot.overall as OverallBudget.Limited
        assertEquals(Money.cop(340_000), overall.reservation!!.total)
        val food = snapshot.categories.single { it.category.id == food }
        assertEquals(Money.cop(50_000), food.reservation!!.total)
    }

    @Test
    fun paymentsTakingExactlyWhatIsLeftLeaveNoMargin() {
        val reservation = hogar(1_210_000).reservation!!

        assertEquals(Money.cop(0), reservation.margin)
        assertEquals(ReservedStanding.NoMargin, reservation.standing)
    }

    @Test
    fun paymentsNeedingMoreThanIsLeftAreShortByTheDifference() {
        val reservation = hogar(1_300_000).reservation!!

        assertEquals(Money.cop(-90_000), reservation.margin)
        assertEquals(ReservedStanding.Short, reservation.standing)
        assertEquals(Money.cop(90_000), reservation.shortfall)
        assertNull(reservation.overLimitBy)
    }

    @Test
    fun aCategoryAlreadyOverItsLimitIsShortByTheLimitPassedAndThePayments() {
        val reservation = hogar(1_600_000).reservation!!

        assertEquals(Money.cop(390_000), reservation.shortfall)
        assertEquals(Money.cop(100_000), reservation.overLimitBy)
    }

    @Test
    fun aCategoryWithoutALimitListsItsPaymentsAndReservesNothing() {
        val line = hogar(950_000, limit = null)

        assertEquals(listOf(uuid(2), uuid(3)), line.upcoming.map { it.schedule.value })
        assertNull(line.reservation)
    }

    @Test
    fun aPastMonthHasNothingToCome() {
        val line = hogar(1_450_000, month = YearMonth.of(2026, 9))

        assertEquals(emptyList<Any>(), line.upcoming)
        assertNull(line.reservation)
    }

    /** Before: 550.000 ÷ 10 = 55.000 a day. Now: (550.000 − 290.000) ÷ 10 = 26.000. */
    @Test
    fun theDailyFigureIsFromWhatThePaymentsLeave() {
        val allowance = insights(950_000).allowance!!

        assertEquals(Money.cop(26_000), allowance.amount)
        assertEquals(10, allowance.availableDays)
    }

    /** Week 22–28 has 7 of the 10 days left: 260.000 × 7 ÷ 10 = 182.000. */
    @Test
    fun theWeeksRemainingAllowanceIsFromWhatThePaymentsLeave() {
        val week = insights(950_000).weeks.single { it.current }

        assertEquals(Money.cop(182_000), week.remainingAllowance)
        assertEquals(Money.cop(182_000), week.ceiling)
    }

    @Test
    fun paymentsNeedingMoreThanIsLeftLeaveNothingPerDayOrThisWeek() {
        val read = insights(1_300_000)

        assertEquals(Money.cop(0), read.allowance!!.amount)
        assertEquals(Money.cop(0), read.weeks.single { it.current }.remainingAllowance)
    }

    @Test
    fun withNothingReservedTheDailyFigureIsWhatIsLeft() {
        val read = CategoryInsightsPolicy.snapshot(
            home,
            october,
            today,
            1,
            1,
            CategoryInsightsPolicy.monthsEndingIn(october).map { period ->
                CategoryHistoryInput(
                    period,
                    category(home, "Hogar", 1_500_000),
                    if (period == october) {
                        listOf(spent(1, 950_000, "2026-10-05"))
                    } else {
                        emptyList()
                    }
                )
            }
        ).valueOrFail()

        assertEquals(Money.cop(55_000), read.allowance!!.amount)
    }
}
