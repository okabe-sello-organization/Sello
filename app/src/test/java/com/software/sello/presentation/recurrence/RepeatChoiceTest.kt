package com.software.sello.presentation.recurrence

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Se repite's choices (docs/design/v2 Pagos e ingresos fijos) as rules of
 * docs/testing/recurrence-examples.md. Every expected date was worked out by hand on the
 * 2026 calendar: jue 22 oct, sáb 24 oct, lun 5 nov, lun 30 nov.
 */
class RepeatChoiceTest {
    private fun d(text: String) = LocalDate.parse(text)

    private fun <T> Outcome<T, *>.value() = (this as Outcome.Success).value

    private val today = d("2026-10-22")

    @Test
    fun itStartsEachMonthOnTheEntrysDayAndTheFirstOneIsThatDay() {
        val choice = RepeatChoice.startingOn(today)

        assertEquals(RepeatKind.Monthly, choice.kind)
        assertEquals(RepeatRule.Monthly.of(22).value(), choice.rule())
        assertEquals(today, choice.next(today))
    }

    @Test
    fun aMonthlyDayAfterTheStartComesThatMonthOrTheNext() {
        val fifth = RepeatChoice.startingOn(today).toggleDay(5)

        assertEquals(RepeatRule.Monthly.of(5).value(), fifth.rule())
        assertEquals(d("2026-11-05"), fifth.next(today))
        // The 31st falls on November's last day (rule 2).
        assertEquals(d("2026-11-30"), fifth.toggleDay(31).next(d("2026-11-01")))
    }

    @Test
    fun twiceAMonthTakesTwoDaysAndAThirdReplacesTheFirstPicked() {
        // The entry's day 22 is kept; one more is needed.
        val one = RepeatChoice.startingOn(today).withKind(RepeatKind.TwiceMonthly)
        val two = one.toggleDay(22).toggleDay(15).toggleDay(30)
        val replaced = two.toggleDay(5)

        assertEquals(listOf(22), one.days)
        assertNull(one.rule())
        assertEquals(RepeatRule.TwiceMonthly.of(15, 30).value(), two.rule())
        assertEquals(d("2026-10-30"), two.next(today))
        assertEquals(RepeatRule.TwiceMonthly.of(5, 30).value(), replaced.rule())
    }

    @Test
    fun weeksAndDaysCountFromTheStartWithinTheirBounds() {
        val weeks = RepeatChoice.startingOn(d("2026-10-24")).withKind(RepeatKind.Weeks)
        val twoWeeks = weeks.increase()
        var most = weeks
        repeat(60) { most = most.increase() }
        val days = RepeatChoice.startingOn(today).withKind(RepeatKind.Days)
        var mostDays = days
        repeat(400) { mostDays = mostDays.increase() }

        assertFalse(weeks.canDecrease)
        assertEquals(weeks, weeks.decrease())
        assertEquals(RepeatRule.EveryWeeks.of(2).value(), twoWeeks.rule())
        assertEquals(d("2026-10-24"), twoWeeks.next(d("2026-10-24")))
        assertEquals(52, most.interval)
        assertFalse(most.canIncrease)
        assertEquals(365, mostDays.interval)
        assertEquals(RepeatRule.EveryDays.of(365).value(), mostDays.rule())
    }

    @Test
    fun movingBetweenKindsKeepsWhatStillFits() {
        val twice = RepeatChoice.startingOn(today).withKind(RepeatKind.TwiceMonthly)
            .toggleDay(15).toggleDay(30)
        val monthly = twice.withKind(RepeatKind.Monthly)
        val weeks = monthly.withKind(RepeatKind.Weeks).increase().increase()
        val days = weeks.withKind(RepeatKind.Days)

        // 22, then 15 and 30: 30 replaced 22, and the first of the two left, 15, stays.
        assertEquals(listOf(15), monthly.days)
        assertEquals(RepeatRule.Monthly.of(15).value(), monthly.rule())
        assertEquals(3, days.interval)
        assertEquals(RepeatRule.EveryDays.of(3).value(), days.rule())
    }

    @Test
    fun anEndBeforeTheStartIsNoRecurrenceAndNothingComes() {
        val ended = RepeatChoice.startingOn(today).withEnd(d("2026-10-21"))

        assertTrue(ended.recurrence(today) is Outcome.Failure)
        assertNull(ended.next(today))
        // Ending on the start keeps that one occurrence (rule 3).
        assertEquals(today, ended.withEnd(today).next(today))
    }

    @Test
    fun itIsKeptAcrossARestartAsTextAndDamagedTextIsNothing() {
        val choice = RepeatChoice.startingOn(today).withKind(RepeatKind.TwiceMonthly)
            .toggleDay(15).withEnd(d("2027-06-30"))
        val weeks = RepeatChoice.startingOn(today).withKind(RepeatKind.Weeks).increase()

        assertEquals(choice, RepeatChoice.decode(choice.encode()))
        assertEquals(weeks, RepeatChoice.decode(weeks.encode()))
        assertNull(RepeatChoice.decode("Monthly;32;1;-"))
        assertNull(RepeatChoice.decode("Weeks;22;53;-"))
        assertNull(RepeatChoice.decode("Hourly;1;1;-"))
        assertNull(RepeatChoice.decode("Monthly;5;1;2026-13-01"))
        assertNull(RepeatChoice.decode(""))
    }

    @Test
    fun aStoredRuleIsShownAsTheChoiceThatMakesIt() {
        val rules = listOf(
            RepeatRule.Monthly.of(5).value(),
            RepeatRule.TwiceMonthly.of(15, 30).value(),
            RepeatRule.EveryWeeks.of(2).value(),
            RepeatRule.EveryDays.of(10).value()
        )

        rules.forEach { assertEquals(it, RepeatChoice.of(it, null).rule()) }
        assertEquals(d("2027-01-31"), RepeatChoice.of(rules[0], d("2027-01-31")).end)
    }
}
