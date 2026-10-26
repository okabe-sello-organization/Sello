package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
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
 * Pagos e ingresos fijos' list (docs/design/v2/fijos.html) on jue 22 oct 2026. Every date
 * and sum below was worked out by hand on the 2026 calendar.
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

        // Rent on the 5th and the gym on the 15th went through; the bills' 28th has not
        // come. The bus went through on 3 and 10 Oct before its pause; the course ended in
        // September; the late one only reached 3 Oct; the twice-monthly one, the 1st.
        assertEquals(d("2026-10-05"), lines.getValue(id(1)).recordedThisMonth)
        assertEquals(d("2026-10-15"), lines.getValue(id(2)).recordedThisMonth)
        assertEquals(null, lines.getValue(id(3)).recordedThisMonth)
        assertEquals(d("2026-10-10"), lines.getValue(id(4)).recordedThisMonth)
        assertEquals(null, lines.getValue(id(5)).recordedThisMonth)
        assertEquals(d("2026-10-03"), lines.getValue(id(6)).recordedThisMonth)
        assertEquals(d("2026-10-01"), lines.getValue(id(7)).recordedThisMonth)
        assertEquals(true, lines.getValue(id(7)).toComeThisMonth)
    }

    @Test
    fun theMonthSaysWhatWentThroughAndWhatIsStillToComeApart() {
        val lines = ScheduleListPolicy.lines(listOf(rent, gym, bills, bus, course), today, october)

        val month = ScheduleListPolicy.month(lines, october).valueOrFail()

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

        val month = ScheduleListPolicy.month(lines, october).valueOrFail()

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
            ScheduleListPolicy.month(lines, october).valueOrFail()
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
}
