package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RecurrenceError
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.RepeatRuleError
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approved examples of docs/testing/recurrence-examples.md, by their IDs. Every
 * expected date is copied from that document, where it was worked out by hand.
 */
class SchedulePolicyTest {
    private fun d(text: String) = LocalDate.parse(text)

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).valueOrFail()

    private fun twice(first: Int, second: Int) =
        RepeatRule.TwiceMonthly.of(first, second).valueOrFail()

    private fun recurrence(
        rule: RepeatRule,
        start: String,
        end: String? = null,
        pauses: List<SchedulePause> = emptyList()
    ) = Recurrence.of(rule, d(start), end?.let(::d), pauses).valueOrFail()

    private fun dates(recurrence: Recurrence, from: String, to: String) =
        SchedulePolicy.occurrences(recurrence, d(from), d(to)).map { it.date }

    private fun all(vararg dates: String) = dates.map(::d)

    @Test
    fun re01MonthlyOnThe31stKeepsItsAnchorThroughShortMonths() {
        val rent = recurrence(monthly(31), "2027-01-01")
        assertEquals(
            all("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31"),
            dates(rent, "2027-01-01", "2027-05-31")
        )
    }

    @Test
    fun re02MonthlyOnThe29thFollowsLeapYears() {
        val rule = recurrence(monthly(29), "2027-01-01")
        assertEquals(all("2027-02-28"), dates(rule, "2027-02-01", "2027-02-28"))
        assertEquals(all("2028-02-29", "2028-03-29"), dates(rule, "2028-02-01", "2028-03-31"))
    }

    @Test
    fun re03NothingBeforeTheStartDate() {
        assertEquals(
            SchedulePolicy.newStart(d("2026-10-22"), today = d("2026-10-22")).valueOrFail(),
            d("2026-10-22")
        )
        val rent = recurrence(monthly(5), "2026-10-22")
        assertEquals(all("2026-11-05"), dates(rent, "2026-10-01", "2026-11-30"))
    }

    @Test
    fun re04AnOccurrenceOnTheStartDateCounts() {
        val rule = recurrence(monthly(22), "2026-10-22")
        assertEquals(all("2026-10-22", "2026-11-22"), dates(rule, "2026-10-22", "2026-11-30"))
    }

    @Test
    fun re05TwiceAMonthOnThe15thAndThe30th() {
        val salary = recurrence(twice(15, 30), "2026-10-01")
        assertEquals(
            all("2026-10-15", "2026-10-30", "2026-11-15", "2026-11-30"),
            dates(salary, "2026-10-01", "2026-11-30")
        )
        assertEquals(all("2027-02-15", "2027-02-28"), dates(salary, "2027-02-01", "2027-02-28"))
    }

    @Test
    fun re06TwoDaysThatClampToOneDateBothOccurInTheirOwnSlots() {
        val rule = recurrence(twice(30, 31), "2027-02-01")
        assertEquals(
            listOf(
                Occurrence(d("2027-02-28"), 1),
                Occurrence(d("2027-02-28"), 2),
                Occurrence(d("2027-03-30"), 1),
                Occurrence(d("2027-03-31"), 2),
                Occurrence(d("2027-04-30"), 1),
                Occurrence(d("2027-04-30"), 2)
            ),
            SchedulePolicy.occurrences(rule, d("2027-02-01"), d("2027-04-30"))
        )
    }

    @Test
    fun re07EveryFifteenDaysIsNotTwiceAMonth() {
        val every15 = recurrence(RepeatRule.EveryDays.of(15).valueOrFail(), "2026-10-15")
        assertEquals(
            all(
                "2026-10-15",
                "2026-10-30",
                "2026-11-14",
                "2026-11-29",
                "2026-12-14",
                "2026-12-29",
                "2027-01-13"
            ),
            dates(every15, "2026-10-01", "2027-01-13")
        )
        val twiceAMonth = recurrence(twice(15, 30), "2026-10-15")
        assertEquals(
            all("2026-11-15", "2026-11-30"),
            dates(twiceAMonth, "2026-11-01", "2026-11-30")
        )
    }

    @Test
    fun re08EveryTwoWeeksFromAFriday() {
        val rule = recurrence(RepeatRule.EveryWeeks.of(2).valueOrFail(), "2026-10-30")
        assertEquals(
            all("2026-10-30", "2026-11-13", "2026-11-27", "2026-12-11", "2026-12-25", "2027-01-08"),
            dates(rule, "2026-10-01", "2027-01-08")
        )
    }

    @Test
    fun re09TheEndDateCountsAndNothingComesAfterIt() {
        val rent = recurrence(monthly(5), "2026-10-22", end = "2026-12-05")
        assertEquals(all("2026-11-05", "2026-12-05"), dates(rent, "2026-10-22", "2027-03-31"))
        val earlier = recurrence(monthly(5), "2026-10-22", end = "2026-12-04")
        assertEquals(all("2026-11-05"), dates(earlier, "2026-10-22", "2027-03-31"))
    }

    @Test
    fun re18AChangedRuleStartsTomorrow() {
        val today = d("2026-10-10")
        val tomorrow = SchedulePolicy.changedStart(d("2026-10-11"), today).valueOrFail()
        val before = recurrence(monthly(5), "2026-10-01", end = "2026-10-10")
        val after = Recurrence.of(monthly(25), tomorrow).valueOrFail()
        assertEquals(all("2026-10-05"), dates(before, "2026-10-01", "2026-11-30"))
        assertEquals(all("2026-10-25", "2026-11-25"), dates(after, "2026-10-01", "2026-11-30"))
    }

    @Test
    fun re19AnEarlierDayIsNotCaughtUpAfterAChange() {
        val before = recurrence(monthly(25), "2026-10-01", end = "2026-10-10")
        val after = recurrence(monthly(5), "2026-10-11")
        assertEquals(emptyList<LocalDate>(), dates(before, "2026-10-01", "2026-11-30"))
        assertEquals(all("2026-11-05"), dates(after, "2026-10-01", "2026-11-30"))
    }

    @Test
    fun re20ChangingEveryNDaysSetsANewStartAfterToday() {
        val today = d("2026-10-20")
        assertEquals(
            ScheduleStartError.TooEarly(d("2026-10-21")),
            SchedulePolicy.changedStart(today, today).errorOrFail()
        )
        val before =
            recurrence(RepeatRule.EveryDays.of(15).valueOrFail(), "2026-10-15", end = "2026-10-20")
        val after = Recurrence.of(
            RepeatRule.EveryDays.of(10).valueOrFail(),
            SchedulePolicy.changedStart(d("2026-10-25"), today).valueOrFail()
        ).valueOrFail()
        assertEquals(all("2026-10-15"), dates(before, "2026-10-01", "2026-11-14"))
        assertEquals(
            all("2026-10-25", "2026-11-04", "2026-11-14"),
            dates(after, "2026-10-01", "2026-11-14")
        )
    }

    @Test
    fun re21ThePausedMonthsAreSkippedAndTheResumeDayCounts() {
        val rent = recurrence(
            monthly(5),
            "2026-09-01",
            pauses = listOf(SchedulePause(d("2026-10-03"), d("2026-12-05")))
        )
        assertEquals(all("2026-12-05", "2027-01-05"), dates(rent, "2026-10-01", "2027-01-31"))
    }

    @Test
    fun re22ResumedTheDayAfterSkipsThatMonthToo() {
        val rent = recurrence(
            monthly(5),
            "2026-09-01",
            pauses = listOf(SchedulePause(d("2026-10-03"), d("2026-12-06")))
        )
        assertEquals(all("2027-01-05"), dates(rent, "2026-10-01", "2027-01-31"))
    }

    @Test
    fun re23ArchivingPausesFromThatDayAndResumingDoesNotCatchUp() {
        val archived = recurrence(
            monthly(15),
            "2026-09-01",
            pauses = listOf(SchedulePause(d("2026-10-12"), null))
        )
        assertEquals(emptyList<LocalDate>(), dates(archived, "2026-10-01", "2026-12-31"))
        val resumed = recurrence(
            monthly(15),
            "2026-09-01",
            pauses = listOf(SchedulePause(d("2026-10-12"), d("2026-10-20")))
        )
        assertEquals(all("2026-11-15", "2026-12-15"), dates(resumed, "2026-10-01", "2026-12-31"))
    }

    @Test
    fun re10AfterTheLastCatchUpTheMissedOccurrencesAreDueOnTheirOwnDates() {
        // Rent monthly on the 5th; bus every week from 26 September, last handled 3 October.
        val rent = recurrence(monthly(5), "2026-09-01")
        val bus = recurrence(RepeatRule.EveryWeeks.of(1).valueOrFail(), "2026-09-26")
        val today = d("2026-10-24")

        assertEquals(
            listOf(Occurrence(d("2026-10-05"), 1)),
            SchedulePolicy.due(rent, Occurrence(d("2026-09-05"), 1), today)
        )
        assertEquals(
            all("2026-10-10", "2026-10-17", "2026-10-24"),
            SchedulePolicy.due(bus, Occurrence(d("2026-10-03"), 1), today).map { it.date }
        )
    }

    @Test
    fun re11AllMissedOccurrencesAreDueInBoundedBatches() {
        val cafe = recurrence(RepeatRule.EveryDays.of(1).valueOrFail(), "2026-07-01")
        val today = d("2026-10-24")
        val all = SchedulePolicy.due(cafe, Occurrence(d("2026-07-31"), 1), today)
        val first = SchedulePolicy.due(cafe, Occurrence(d("2026-07-31"), 1), today, limit = 50)
        val second = SchedulePolicy.due(cafe, first.last(), today, limit = 50)

        assertEquals(85, all.size)
        assertEquals(d("2026-08-01") to d("2026-09-19"), first.first().date to first.last().date)
        assertEquals(35, second.size)
        assertEquals(d("2026-09-20") to d("2026-10-24"), second.first().date to second.last().date)
    }

    @Test
    fun nothingHandledYetMeansEverythingFromTheStartIsDueAndASlotSplitIsResumed() {
        val rule = recurrence(twice(30, 31), "2027-02-01")

        assertEquals(
            listOf(Occurrence(d("2027-02-28"), 1), Occurrence(d("2027-02-28"), 2)),
            SchedulePolicy.due(rule, null, d("2027-03-01"))
        )
        // A batch that stopped between the two slots of one date resumes at the second.
        assertEquals(
            listOf(Occurrence(d("2027-02-28"), 2)),
            SchedulePolicy.due(rule, Occurrence(d("2027-02-28"), 1), d("2027-03-01"))
        )
        assertEquals(
            emptyList<Occurrence>(),
            SchedulePolicy.due(rule, Occurrence(d("2027-02-28"), 2), d("2027-03-01"))
        )
    }

    @Test
    fun aNewScheduleStartsTodayOrLater() {
        val today = d("2026-10-22")
        assertEquals(
            ScheduleStartError.TooEarly(today),
            SchedulePolicy.newStart(d("2026-10-21"), today).errorOrFail()
        )
        assertEquals(d("2026-10-23"), SchedulePolicy.newStart(d("2026-10-23"), today).valueOrFail())
    }

    @Test
    fun repeatRulesRefuseDaysAndIntervalsOutOfBounds() {
        assertEquals(RepeatRuleError.DayOutOfRange(0), RepeatRule.Monthly.of(0).errorOrFail())
        assertEquals(RepeatRuleError.DayOutOfRange(32), RepeatRule.Monthly.of(32).errorOrFail())
        assertEquals(
            RepeatRuleError.DaysNotInOrder(30, 30),
            RepeatRule.TwiceMonthly.of(30, 30).errorOrFail()
        )
        assertEquals(
            RepeatRuleError.DaysNotInOrder(31, 1),
            RepeatRule.TwiceMonthly.of(31, 1).errorOrFail()
        )
        assertEquals(
            RepeatRuleError.DayOutOfRange(32),
            RepeatRule.TwiceMonthly.of(1, 32).errorOrFail()
        )
        assertEquals(
            RepeatRuleError.IntervalOutOfRange(0, 365),
            RepeatRule.EveryDays.of(0).errorOrFail()
        )
        assertEquals(
            RepeatRuleError.IntervalOutOfRange(366, 365),
            RepeatRule.EveryDays.of(366).errorOrFail()
        )
        assertEquals(
            RepeatRuleError.IntervalOutOfRange(53, 52),
            RepeatRule.EveryWeeks.of(53).errorOrFail()
        )
        assertEquals(365, RepeatRule.EveryDays.of(365).valueOrFail().days)
        assertEquals(52, RepeatRule.EveryWeeks.of(52).valueOrFail().weeks)
    }

    @Test
    fun aRecurrenceRefusesAnEndBeforeItsStartAndPausesThatDoNotFit() {
        val rule = monthly(5)
        assertEquals(
            RecurrenceError.EndBeforeStart,
            Recurrence.of(rule, d("2026-10-22"), d("2026-10-21")).errorOrFail()
        )
        val early = SchedulePause(d("2026-10-01"), null)
        assertEquals(
            RecurrenceError.InvalidPause(early),
            Recurrence.of(rule, d("2026-10-22"), pauses = listOf(early)).errorOrFail()
        )
        val empty = SchedulePause(d("2026-11-01"), d("2026-11-01"))
        assertEquals(
            RecurrenceError.InvalidPause(empty),
            Recurrence.of(rule, d("2026-10-22"), pauses = listOf(empty)).errorOrFail()
        )
        val open = SchedulePause(d("2026-11-01"), null)
        val later = SchedulePause(d("2026-12-01"), d("2026-12-10"))
        assertEquals(
            RecurrenceError.OverlappingPauses(open, later),
            Recurrence.of(rule, d("2026-10-22"), pauses = listOf(open, later)).errorOrFail()
        )
        val first = SchedulePause(d("2026-11-01"), d("2026-11-20"))
        val second = SchedulePause(d("2026-11-10"), d("2026-11-30"))
        assertEquals(
            RecurrenceError.OverlappingPauses(first, second),
            Recurrence.of(rule, d("2026-10-22"), pauses = listOf(first, second)).errorOrFail()
        )
        val far = LocalDate.of(10_000, 1, 1)
        assertEquals(
            RecurrenceError.DateOutOfRange(far),
            Recurrence.of(rule, d("2026-10-22"), far).errorOrFail()
        )
    }

    @Test
    fun aDailyScheduleAcrossCenturiesJumpsToTheRange() {
        val daily = recurrence(RepeatRule.EveryDays.of(1).valueOrFail(), "0001-01-01")
        assertEquals(all("9999-12-30", "9999-12-31"), dates(daily, "9999-12-30", "9999-12-31"))
        assertEquals(
            all("2026-08-01", "2026-08-02", "2026-08-03"),
            SchedulePolicy.occurrences(daily, d("2026-08-01"), d("2026-10-24"), limit = 3).map {
                it.date
            }
        )
    }

    /**
     * Seeded random schedules and ranges against what every occurrence list must be:
     * ordered and unique, on the rule's own days, inside the range, start and end, never
     * in a pause, complete against a day-by-day scan, and the same when the range is split
     * in two (so a catch-up can resume) or cut by a limit.
     */
    @Test
    fun randomSchedulesKeepEveryOccurrenceProperty() {
        val random = Random(20261024)
        repeat(1_500) { case ->
            val recurrence = randomRecurrence(random)
            val from = recurrence.start.plusDays(random.nextLong(-60, 400))
            val to = from.plusDays(random.nextLong(0, 500))
            val found = SchedulePolicy.occurrences(recurrence, from, to)
            val label = "case $case: $recurrence, $from..$to"

            assertEquals(label, found.sortedWith(compareBy({ it.date }, { it.slot })), found)
            assertEquals(label, found.distinct(), found)
            assertEquals(label, scan(recurrence, from, to), found)

            val split = from.plusDays(random.nextLong(0, ChronoUnit.DAYS.between(from, to) + 1))
            val halves = SchedulePolicy.occurrences(recurrence, from, split) +
                SchedulePolicy.occurrences(recurrence, split.plusDays(1), to)
            assertEquals(label, found, halves)

            val limit = random.nextInt(0, 6)
            assertEquals(
                label,
                found.take(limit),
                SchedulePolicy.occurrences(recurrence, from, to, limit)
            )
        }
    }

    private fun randomRecurrence(random: Random): Recurrence {
        val rule = when (random.nextInt(4)) {
            0 -> monthly(random.nextInt(1, 32))
            1 -> random.nextInt(1, 31).let { first -> twice(first, random.nextInt(first + 1, 32)) }
            2 -> RepeatRule.EveryDays.of(random.nextInt(1, 40)).valueOrFail()
            else -> RepeatRule.EveryWeeks.of(random.nextInt(1, 6)).valueOrFail()
        }
        val start = LocalDate.of(2024, 1, 1).plusDays(random.nextLong(0, 2_000))
        val end = if (random.nextBoolean()) start.plusDays(random.nextLong(0, 700)) else null
        val pauses = mutableListOf<SchedulePause>()
        var cursor = start
        repeat(random.nextInt(0, 4)) {
            val from = cursor.plusDays(random.nextLong(0, 200))
            val open = random.nextInt(5) == 0
            val until = if (open) null else from.plusDays(random.nextLong(1, 120))
            pauses += SchedulePause(from, until)
            cursor = until ?: return Recurrence.of(rule, start, end, pauses).valueOrFail()
        }
        return Recurrence.of(rule, start, end, pauses).valueOrFail()
    }

    /** Every day of the range asked on its own: a different way to the same answer. */
    private fun scan(recurrence: Recurrence, from: LocalDate, to: LocalDate): List<Occurrence> {
        val found = mutableListOf<Occurrence>()
        var day = from
        while (!day.isAfter(to)) {
            val active = !day.isBefore(recurrence.start) &&
                (recurrence.end == null || !day.isAfter(recurrence.end)) &&
                recurrence.pauses.none {
                    !day.isBefore(it.from) &&
                        (it.until == null || day.isBefore(it.until))
                }
            if (active) {
                val last = day.lengthOfMonth()
                when (val rule = recurrence.rule) {
                    is RepeatRule.Monthly ->
                        if (day.dayOfMonth == minOf(rule.day, last)) found += Occurrence(day, 1)

                    is RepeatRule.TwiceMonthly -> {
                        if (day.dayOfMonth == minOf(rule.first, last)) found += Occurrence(day, 1)
                        if (day.dayOfMonth == minOf(rule.second, last)) found += Occurrence(day, 2)
                    }

                    is RepeatRule.EveryDays ->
                        if (ChronoUnit.DAYS.between(recurrence.start, day) % rule.days == 0L) {
                            found += Occurrence(day, 1)
                        }

                    is RepeatRule.EveryWeeks ->
                        if (ChronoUnit.DAYS.between(recurrence.start, day) % (7L * rule.weeks) ==
                            0L
                        ) {
                            found += Occurrence(day, 1)
                        }
                }
            }
            day = day.plusDays(1)
        }
        return found
    }

    @Test
    fun aRangeThatEndsBeforeItStartsHasNothing() {
        val rent = recurrence(monthly(5), "2026-10-01")
        assertTrue(dates(rent, "2026-12-01", "2026-11-01").isEmpty())
    }
}
