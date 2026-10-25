package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpectedIncome
import com.software.sello.domain.model.IncomePlan
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan del mes, by the recurrence examples RE35–RE36 (docs/testing/recurrence-examples.md)
 * and ADR 0012's BG-E08 and BG-E09. Every expected figure is copied from there, where it
 * was worked out by hand.
 */
class IncomePlanPolicyTest {
    private val october = YearMonth.of(2026, 10)
    private val created = Instant.parse("2026-10-01T08:00:00Z")

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private fun id(number: Int) =
        ScheduleId.of("00000000-0000-4000-8000-%012d".format(number)).valueOrFail()

    private fun stored(
        number: Int,
        rule: RepeatRule,
        start: String,
        entry: ScheduledEntry = ScheduledEntry.Income(IncomeSource.Salary, pesos(1_000_000), null),
        handled: Occurrence? = null,
        end: String? = null,
        pauses: List<SchedulePause> = emptyList()
    ) = StoredSchedule(
        schedule = Schedule(
            id(number),
            entry,
            Recurrence.of(rule, d(start), end?.let(::d), pauses).valueOrFail()
        ),
        sequence = number.toLong(),
        handled = handled,
        version = 1,
        createdAt = created,
        updatedAt = created
    )

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).valueOrFail()

    private val salary = stored(1, monthly(30), "2026-10-01")

    /** RE35, BG-E08: I 2.000.000, A 2.800.000, salary 1.000.000 expected on 30 October. */
    @Test
    fun re35theExpectedSalaryIsPlannedButNeverRecorded() {
        val expected = IncomePlanPolicy.expected(listOf(salary), october)
        val plan = IncomePlanPolicy.plan(
            Money.cop(2_000_000),
            Money.cop(2_800_000),
            expected
        ).valueOrFail()

        assertEquals(
            listOf(
                ExpectedIncome(
                    id(1),
                    IncomeSource.Salary,
                    null,
                    Occurrence(d("2026-10-30"), 1),
                    pesos(1_000_000)
                )
            ),
            expected
        )
        assertEquals(
            IncomePlan(
                recorded = Money.cop(2_000_000),
                expected = Money.cop(1_000_000),
                planned = Money.cop(3_000_000),
                unassigned = Money.cop(200_000),
                items = expected
            ),
            plan
        )
    }

    /** RE36, BG-E09: once catch-up records the salary, nothing is expected and there is no plan. */
    @Test
    fun re36aRecordedSalaryIsNoLongerExpected() {
        val recorded =
            stored(1, monthly(30), "2026-10-01", handled = Occurrence(d("2026-10-30"), 1))

        val expected = IncomePlanPolicy.expected(listOf(recorded), october)

        assertEquals(emptyList<ExpectedIncome>(), expected)
        assertEquals(
            null,
            IncomePlanPolicy.plan(
                Money.cop(3_000_000),
                Money.cop(2_800_000),
                expected
            ).valueOrFail()
        )
    }

    @Test
    fun onlyThisMonthsIncomesFromActiveSchedulesAreExpected() {
        val schedules = listOf(
            // RE05's twice-monthly salary, 15 October recorded: 30 October still to come.
            stored(
                2,
                RepeatRule.TwiceMonthly.of(15, 30).valueOrFail(),
                "2026-10-01",
                ScheduledEntry.Income(IncomeSource.Salary, pesos(800_000), null),
                handled = Occurrence(d("2026-10-15"), 1)
            ),
            // A payment is reserved, never expected.
            stored(
                3,
                monthly(28),
                "2026-10-01",
                ScheduledEntry.Payment(
                    CategoryId.of("00000000-0000-4000-8000-000000000100").valueOrFail(),
                    pesos(950_000),
                    null
                )
            ),
            // Paused from 20 October: nothing more this month.
            stored(
                4,
                monthly(25),
                "2026-10-01",
                pauses = listOf(SchedulePause(d("2026-10-20"), null))
            ),
            // Ended on 24 October.
            stored(5, monthly(27), "2026-10-01", end = "2026-10-24"),
            // Due on 30 September and never handled: September's, not October's.
            stored(6, monthly(30), "2026-09-01", handled = Occurrence(d("2026-08-30"), 1)),
            // Starts next month.
            stored(7, monthly(5), "2026-11-01")
        )

        val expected = IncomePlanPolicy.expected(schedules, october)

        assertEquals(
            listOf(id(2) to d("2026-10-30"), id(6) to d("2026-10-30")),
            expected.map { it.schedule to it.occurrence.date }
        )
    }

    @Test
    fun aTotalTooLargeForAnExactAmountIsAFailureNotAFigure() {
        val expected = IncomePlanPolicy.expected(
            listOf(
                stored(
                    1,
                    monthly(30),
                    "2026-10-01",
                    ScheduledEntry.Income(IncomeSource.Salary, pesos(999_999_999_999), null)
                )
            ),
            october
        )

        val plan = IncomePlanPolicy.plan(Money.cop(Long.MAX_VALUE - 1), Money.cop(0), expected)

        assertEquals(Outcome.Failure(MoneyError.Overflow), plan)
    }

    @Test
    fun aPlanAboveTheBudgetsIsNegativeAndKeepsItsSign() {
        val expected = IncomePlanPolicy.expected(listOf(salary), october)

        val plan = IncomePlanPolicy.plan(
            Money.cop(500_000),
            Money.cop(2_800_000),
            expected
        ).valueOrFail()

        // 500.000 + 1.000.000 − 2.800.000.
        assertEquals(Money.cop(-1_300_000), plan?.unassigned)
    }
}
