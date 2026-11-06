package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
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
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pagos e ingresos fijos' list (docs/design/system/features/recurrence.html#screen-schedules) on
 * jue 22 oct 2026. Every date and sum below was worked out by hand on the 2026 calendar.
 */
class ScheduleListPolicyTest {
    private val today = LocalDate.parse("2026-10-22")
    private val october = YearMonth.of(2026, 10)
    private val home = CategoryId.of("00000000-0000-4000-8000-000000000100").valueOrFail()

    private fun d(text: String) = LocalDate.parse(text)

    private fun id(number: Int) =
        ScheduleId.of("00000000-0000-4000-8000-%012d".format(number)).valueOrFail()

    private fun payment(
        number: Int,
        rule: RepeatRule,
        start: String,
        pesos: Long,
        handled: String? = null,
        end: String? = null,
        pauses: List<SchedulePause> = emptyList()
    ) = StoredSchedule(
        Schedule(
            id(number),
            ScheduledEntry.Payment(
                home,
                TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
                null
            ),
            Recurrence.of(rule, d(start), end?.let(::d), pauses).valueOrFail()
        ),
        sequence = number.toLong(),
        handled = handled?.let { Occurrence(d(it), 1) },
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).valueOrFail()

    private var entries = 500

    /**
     * The expense schedule [number] recorded for [occurrence], stored now at [pesos] on
     * [date], its occurrence's day unless an edit moved it.
     */
    private fun paid(
        number: Int,
        occurrence: String,
        pesos: Long,
        slot: Int = 1,
        date: String = occurrence
    ) = ScheduleRecord.Payment(
        Expense(
            ExpenseId.of("00000000-0000-4000-8000-%012d".format(entries++)).valueOrFail(),
            home,
            d(date),
            entries.toLong(),
            TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
            null,
            1,
            Instant.EPOCH,
            Instant.EPOCH,
            EntryOrigin(id(number), Occurrence(d(occurrence), slot))
        )
    )

    private fun earned(number: Int, occurrence: String, pesos: Long) = ScheduleRecord.Earning(
        Income(
            IncomeId.of("00000000-0000-4000-8000-%012d".format(entries++)).valueOrFail(),
            d(occurrence),
            entries.toLong(),
            TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
            IncomeSource.Salary,
            null,
            1,
            Instant.EPOCH,
            Instant.EPOCH,
            EntryOrigin(id(number), Occurrence(d(occurrence), 1))
        )
    )

    /** What the schedules in [lines] recorded in October, from their stored [records]. */
    private fun inOctober(lines: List<ScheduleLine>, vararg records: ScheduleRecord) =
        ScheduleListPolicy.month(lines, records.toList(), october).valueOrFail()

    private val rent = payment(1, monthly(5), "2026-10-01", 950_000, handled = "2026-10-05")
    private val gym = payment(2, monthly(15), "2026-10-01", 99_900, handled = "2026-10-15")
    private val bills = payment(3, monthly(28), "2026-10-01", 200_000)

    // Every Saturday from 26 Sep, paused from lun 12 oct: 3 and 10 Oct recorded.
    private val bus = payment(
        4,
        RepeatRule.EveryWeeks.of(1).valueOrFail(),
        "2026-09-26",
        20_000,
        handled = "2026-10-10",
        pauses = listOf(SchedulePause(d("2026-10-12"), null))
    )

    // Ended on 30 Sep, after its 16 Sep payment.
    private val course =
        payment(5, monthly(16), "2026-06-01", 189_000, handled = "2026-09-16", end = "2026-09-30")

    @Test
    fun eachScheduleSaysItsStateAndWhatComesNextInDayOrder() {
        val lines = ScheduleListPolicy.lines(listOf(rent, gym, bills, bus, course), today, october)

        assertEquals(
            listOf(
                Triple(id(4), ScheduleStatus.Paused, null),
                Triple(id(1), ScheduleStatus.Active, d("2026-11-05")),
                Triple(id(2), ScheduleStatus.Active, d("2026-11-15")),
                Triple(id(3), ScheduleStatus.Active, d("2026-10-28")),
                Triple(id(5), ScheduleStatus.Ended, null)
            ),
            lines.map { Triple(it.stored.schedule.id, it.status, it.next) }
        )
        assertEquals(d("2026-10-12"), lines.first().pausedFrom)
        assertEquals(listOf(false, false, false, true, false), lines.map { it.toComeThisMonth })
    }

    @Test
    fun eachScheduleSaysWhenItsLastOccurrenceThisMonthWentThrough() {
        val behind = payment(
            6,
            RepeatRule.EveryWeeks.of(1).valueOrFail(),
            "2026-09-26",
            20_000,
            handled = "2026-10-03"
        )
        // Twice a month on the 1st and 15th, the 1st recorded and the 15th not yet.
        val half = payment(
            7,
            RepeatRule.TwiceMonthly.of(1, 15).valueOrFail(),
            "2026-10-01",
            300_000,
            handled = "2026-10-01"
        )
        val lines = ScheduleListPolicy.lines(
            listOf(rent, gym, bills, bus, course, behind, half),
            today,
            october
        ).associateBy { it.stored.schedule.id }
        val records = listOf(
            paid(1, "2026-10-05", 950_000),
            paid(2, "2026-10-15", 99_900),
            paid(4, "2026-10-03", 20_000),
            paid(4, "2026-10-10", 20_000),
            paid(5, "2026-09-16", 189_000),
            paid(6, "2026-10-03", 20_000),
            paid(7, "2026-10-01", 300_000)
        )

        fun recordedOn(number: Int) =
            ScheduleListPolicy.recordedOn(lines.getValue(id(number)), records, october)

        // Rent on the 5th and the gym on the 15th went through; the bills' 28th has not
        // come. The bus went through on 3 and 10 Oct before its pause; the course ended in
        // September; the late one only reached 3 Oct; the twice-monthly one, the 1st.
        assertEquals(d("2026-10-05"), recordedOn(1))
        assertEquals(d("2026-10-15"), recordedOn(2))
        assertEquals(null, recordedOn(3))
        assertEquals(d("2026-10-10"), recordedOn(4))
        assertEquals(null, recordedOn(5))
        assertEquals(d("2026-10-03"), recordedOn(6))
        assertEquals(d("2026-10-01"), recordedOn(7))
        assertEquals(true, lines.getValue(id(7)).toComeThisMonth)
    }

    @Test
    fun theMonthSaysWhatWentThroughAndWhatIsStillToComeApart() {
        val lines = ScheduleListPolicy.lines(listOf(rent, gym, bills, bus, course), today, october)

        val month = inOctober(
            lines,
            paid(1, "2026-10-05", 950_000),
            paid(2, "2026-10-15", 99_900),
            paid(4, "2026-10-03", 20_000),
            paid(4, "2026-10-10", 20_000),
            paid(5, "2026-09-16", 189_000)
        )

        // 950.000 + 99.900 + 20.000 (3 oct) + 20.000 (10 oct); the bus's 17, 24 and 31 Oct
        // fall in its pause. Still to come: 200.000 on the 28th.
        assertEquals(
            SchedulesMonth(Money.cop(1_089_900), 4, Money.cop(200_000), 1, Money.cop(1_289_900), 3),
            month
        )
    }

    @Test
    fun oneDueButNotYetRecordedIsStillToCome() {
        // Catch-up last handled 3 Oct: 10 and 17 Oct are due and not recorded yet.
        val behind = payment(
            6,
            RepeatRule.EveryWeeks.of(1).valueOrFail(),
            "2026-09-26",
            20_000,
            handled = "2026-10-03"
        )
        val lines = ScheduleListPolicy.lines(listOf(behind), today, october)

        val month = inOctober(lines, paid(6, "2026-10-03", 20_000))

        // 3 Oct went through; 10, 17, 24 and 31 Oct are still to come.
        assertEquals(
            SchedulesMonth(Money.cop(20_000), 1, Money.cop(80_000), 4, Money.cop(100_000), 1),
            month
        )
        assertEquals(d("2026-10-10"), lines.single().next)
    }

    @Test
    fun aScheduleStartingNextMonthHasNothingThisMonth() {
        val later = payment(7, monthly(5), "2026-11-01", 950_000)
        val lines = ScheduleListPolicy.lines(listOf(later), today, october)

        assertEquals(d("2026-11-05"), lines.single().next)
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            inOctober(lines)
        )
    }

    /**
     * SELLO-145: paused before it starts, a schedule's pause begins on its start, 1 Dec,
     * and it is listed under En pausa from that day on, not as active.
     */
    @Test
    fun aSchedulePausedBeforeItStartsIsListedAsPaused() {
        val later = payment(
            8,
            monthly(25),
            "2026-12-01",
            99_900,
            pauses = listOf(SchedulePause(d("2026-12-01"), null))
        )
        val lines = ScheduleListPolicy.lines(listOf(later), today, october)

        assertEquals(ScheduleStatus.Paused, lines.single().status)
        assertEquals(d("2026-12-01"), lines.single().pausedFrom)
        assertEquals(null, lines.single().next)
        assertEquals(null, ScheduleListPolicy.nextDate(lines, today))
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 0),
            inOctober(lines)
        )
    }

    @Test
    fun theNextOneIsTheNearestDateStillToComeAmongActiveSchedules() {
        // Rent next 5 Nov, the gym 15 Nov, the bills 28 Oct; the bus is paused and the
        // course ended, so neither counts.
        val lines = ScheduleListPolicy.lines(listOf(rent, gym, bus, course, bills), today, october)
        assertEquals(d("2026-10-28"), ScheduleListPolicy.nextDate(lines, today))

        // Due on 10 Oct and not recorded yet: it is today's, never a past date.
        val behind = payment(
            6,
            RepeatRule.EveryWeeks.of(1).valueOrFail(),
            "2026-09-26",
            20_000,
            handled = "2026-10-03"
        )
        val late = ScheduleListPolicy.lines(listOf(rent, behind), today, october)
        assertEquals(today, ScheduleListPolicy.nextDate(late, today))

        val inactive = ScheduleListPolicy.lines(listOf(bus, course), today, october)
        assertEquals(null, ScheduleListPolicy.nextDate(inactive, today))
        assertEquals(null, ScheduleListPolicy.nextDate(emptyList(), today))
    }

    /**
     * SELLO-148, RE17: rent on the 5th at 950.000, changed to 1.000.000 on 10 Oct. The 5 Oct
     * entry was stored at 950.000, and that is what went through; the next is 5 Nov.
     */
    @Test
    fun aChangedAmountLeavesWhatWasRecordedAtItsStoredAmount() {
        val raised = payment(1, monthly(5), "2026-10-01", 1_000_000, handled = "2026-10-10")
        val lines = ScheduleListPolicy.lines(listOf(raised), today, october)
        val rent = paid(1, "2026-10-05", 950_000)

        assertEquals(
            SchedulesMonth(Money.cop(950_000), 1, Money.cop(0), 0, Money.cop(950_000), 1),
            inOctober(lines, rent)
        )
        assertEquals(
            d("2026-10-05"),
            ScheduleListPolicy.recordedOn(lines.single(), listOf(rent), october)
        )
    }

    /**
     * SELLO-148, RE18: on 10 Oct the rent moves from the 5th to the 25th. 5 Oct went through
     * at 950.000 and still counts; 25 Oct, after the change, is still to come.
     */
    @Test
    fun aMovedDayStillCountsWhatWasRecordedOnTheOldDay() {
        val moved = payment(1, monthly(25), "2026-10-01", 950_000, handled = "2026-10-10")
        val lines = ScheduleListPolicy.lines(listOf(moved), today, october)
        val rent = paid(1, "2026-10-05", 950_000)

        // 950.000 through, 950.000 to come on the 25th: 1.900.000.
        assertEquals(
            SchedulesMonth(Money.cop(950_000), 1, Money.cop(950_000), 1, Money.cop(1_900_000), 1),
            inOctober(lines, rent)
        )
        assertEquals(
            d("2026-10-05"),
            ScheduleListPolicy.recordedOn(lines.single(), listOf(rent), october)
        )
        assertEquals(true, lines.single().toComeThisMonth)
    }

    /**
     * SELLO-148: the 5 Oct entry edited to 900.000 counts 900.000. Deleted, it counts
     * nothing and the rent has nothing recorded in October.
     */
    @Test
    fun anEditedEntryCountsItsAmountAndADeletedOneNothing() {
        val raised = payment(1, monthly(5), "2026-10-01", 1_000_000, handled = "2026-10-10")
        val lines = ScheduleListPolicy.lines(listOf(raised), today, october)

        assertEquals(
            SchedulesMonth(Money.cop(900_000), 1, Money.cop(0), 0, Money.cop(900_000), 1),
            inOctober(lines, paid(1, "2026-10-05", 900_000))
        )
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            inOctober(lines)
        )
        assertEquals(null, ScheduleListPolicy.recordedOn(lines.single(), emptyList(), october))
    }

    /**
     * SELLO-148, ruling on #329 (R1): rent ended 30 Sep, its end removed on 20 Oct. The
     * checkpoint jumped to 20 Oct but 5 Oct was never recorded: October has nothing
     * through for it and nothing to come; the next is 5 Nov. Its 5 Sep entry is September's.
     */
    @Test
    fun anEndedScheduleRunAgainShowsNothingForWhatItNeverRecorded() {
        val resumed = payment(1, monthly(5), "2026-06-01", 950_000, handled = "2026-10-20")
        val lines = ScheduleListPolicy.lines(listOf(resumed), today, october)
        val september = paid(1, "2026-09-05", 950_000)

        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            inOctober(lines, september)
        )
        assertEquals(
            null,
            ScheduleListPolicy.recordedOn(lines.single(), listOf(september), october)
        )
        assertEquals(d("2026-11-05"), lines.single().next)
    }

    /**
     * SELLO-148, ruling on #329 (R1): a daily café at 8.000 ended on 10 Oct, recorded 1-10
     * Oct, and was extended on 20 Oct. 11-20 Oct were never recorded and count nothing;
     * 21-31 Oct are still to come.
     */
    @Test
    fun anExtendedDailyScheduleCountsOnlyTheDaysItRecorded() {
        val cafe = payment(
            9,
            RepeatRule.EveryDays.of(1).valueOrFail(),
            "2026-10-01",
            8_000,
            handled = "2026-10-20"
        )
        val lines = ScheduleListPolicy.lines(listOf(cafe), today, october)
        val recorded = (1..10).map { paid(9, "2026-10-%02d".format(it), 8_000) }

        // Through: 10 x 8.000 = 80.000. To come: 21-31 Oct, 11 x 8.000 = 88.000.
        assertEquals(
            SchedulesMonth(Money.cop(80_000), 10, Money.cop(88_000), 11, Money.cop(168_000), 1),
            inOctober(lines, *recorded.toTypedArray())
        )
        assertEquals(
            d("2026-10-10"),
            ScheduleListPolicy.recordedOn(lines.single(), recorded, october)
        )
    }

    /**
     * SELLO-148: only the listed schedules' entries for an October occurrence count: not a
     * deleted schedule's, not the other tab's, not September's or November's.
     */
    @Test
    fun onlyTheListedSchedulesEntriesForTheMonthCount() {
        val lines = ScheduleListPolicy.lines(listOf(rent), today, october)

        assertEquals(
            SchedulesMonth(Money.cop(950_000), 1, Money.cop(0), 0, Money.cop(950_000), 1),
            inOctober(
                lines,
                paid(1, "2026-10-05", 950_000),
                paid(1, "2026-09-05", 950_000),
                paid(1, "2026-11-05", 950_000),
                paid(42, "2026-10-07", 70_000),
                earned(43, "2026-10-02", 3_000_000)
            )
        )
    }

    /** SELLO-148: twice a month, each slot's entry counts once, and the day is the 15th. */
    @Test
    fun bothSlotsOfATwiceMonthlyScheduleCount() {
        val half = payment(
            7,
            RepeatRule.TwiceMonthly.of(1, 15).valueOrFail(),
            "2026-10-01",
            300_000
        ).copy(handled = Occurrence(d("2026-10-15"), 2))
        val lines = ScheduleListPolicy.lines(listOf(half), today, october)
        val recorded =
            listOf(paid(7, "2026-10-01", 300_000), paid(7, "2026-10-15", 280_000, slot = 2))

        assertEquals(
            SchedulesMonth(Money.cop(580_000), 2, Money.cop(0), 0, Money.cop(580_000), 1),
            inOctober(lines, *recorded.toTypedArray())
        )
        assertEquals(
            d("2026-10-15"),
            ScheduleListPolicy.recordedOn(lines.single(), recorded, october)
        )
    }

    /**
     * SELLO-148: what was recorded is summed exactly. 9.223.373 entries at the largest
     * amount, 999.999.999.999 each, make 9.223.372.999.990.776.627, past the largest whole
     * number a Money holds: an overflow, never a wrapped figure.
     */
    @Test
    fun aRecordedSumThatDoesNotFitIsAnOverflow() {
        val lines = ScheduleListPolicy.lines(listOf(rent), today, october)
        val records = Collections.nCopies(9_223_373, paid(1, "2026-10-05", 999_999_999_999))

        assertEquals(
            Outcome.Failure(MoneyError.Overflow),
            ScheduleListPolicy.month(lines, records, october)
        )
    }

    /**
     * SELLO-148, ruling on #343 (R1): on mar 10 nov the rent's 5 Nov entry was moved to
     * 31 Oct. It counts where it is stored now: November has nothing through for the rent
     * and nothing to come (5 Nov is past the checkpoint); October has 950.000 twice.
     */
    @Test
    fun anEntryMovedBackToOctoberCountsInOctober() {
        val tenth = d("2026-11-10")
        val november = YearMonth.of(2026, 11)
        val lines = ScheduleListPolicy.lines(
            listOf(payment(1, monthly(5), "2026-10-01", 950_000, handled = "2026-11-05")),
            tenth,
            november
        )
        val records = listOf(
            paid(1, "2026-10-05", 950_000),
            paid(1, "2026-11-05", 950_000, date = "2026-10-31")
        )

        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            ScheduleListPolicy.month(lines, records, november).valueOrFail()
        )
        assertEquals(null, ScheduleListPolicy.recordedOn(lines.single(), records, november))
        // 950.000 + 950.000 = 1.900.000, the last on 31 Oct.
        assertEquals(
            SchedulesMonth(Money.cop(1_900_000), 2, Money.cop(0), 0, Money.cop(1_900_000), 1),
            ScheduleListPolicy.month(lines, records, october).valueOrFail()
        )
        assertEquals(
            d("2026-10-31"),
            ScheduleListPolicy.recordedOn(lines.single(), records, october)
        )
    }

    /**
     * SELLO-148, ruling on #343 (R1): on mar 10 nov the rent's 5 Oct entry was moved to
     * 7 Nov. November has both entries, 950.000 + 950.000 = 1.900.000, the last on 7 Nov;
     * October has nothing through for the rent.
     */
    @Test
    fun anEntryMovedForwardToNovemberCountsInNovember() {
        val tenth = d("2026-11-10")
        val november = YearMonth.of(2026, 11)
        val lines = ScheduleListPolicy.lines(
            listOf(payment(1, monthly(5), "2026-10-01", 950_000, handled = "2026-11-05")),
            tenth,
            november
        )
        val records = listOf(
            paid(1, "2026-10-05", 950_000, date = "2026-11-07"),
            paid(1, "2026-11-05", 950_000)
        )

        assertEquals(
            SchedulesMonth(Money.cop(1_900_000), 2, Money.cop(0), 0, Money.cop(1_900_000), 1),
            ScheduleListPolicy.month(lines, records, november).valueOrFail()
        )
        assertEquals(
            d("2026-11-07"),
            ScheduleListPolicy.recordedOn(lines.single(), records, november)
        )
        assertEquals(
            SchedulesMonth(Money.cop(0), 0, Money.cop(0), 0, Money.cop(0), 1),
            ScheduleListPolicy.month(lines, records, october).valueOrFail()
        )
    }
}
