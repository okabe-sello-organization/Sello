package com.software.sello.domain.policy

import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.StoredSchedule
import java.time.LocalDate
import java.time.YearMonth

enum class ScheduleStatus { Active, Paused, Ended }

/**
 * One schedule as Pagos e ingresos fijos lists it on a day: its [status], the first day of
 * the pause running then or still open ([pausedFrom]), the [next] occurrence not yet
 * recorded, which may be due already while catch-up has not reached it, its first
 * occurrence in the month ([monthFirst]) and whether one is still to come that month
 * ([toComeThisMonth]). What it recorded comes from its entries
 * ([ScheduleListPolicy.recordedOn]).
 */
data class ScheduleLine(
    val stored: StoredSchedule,
    val status: ScheduleStatus,
    val pausedFrom: LocalDate?,
    val next: LocalDate?,
    val monthFirst: LocalDate?,
    val toComeThisMonth: Boolean
)

/**
 * What a month holds for some schedules: the entries they recorded, as stored now
 * ([done], [doneCount]), the occurrences still to come ([toCome], [toComeCount]) and both
 * ([total]), with how many schedules are [active].
 */
data class SchedulesMonth(
    val done: Money,
    val doneCount: Int,
    val toCome: Money,
    val toComeCount: Int,
    val total: Money,
    val active: Int
)

/** The list of schedules and its month (docs/design/v2 Pagos e ingresos fijos). */
object ScheduleListPolicy {
    /** Longer than the longest gap between two occurrences: every 365 days. */
    private const val HORIZON_DAYS = 400L

    /**
     * Every schedule on [today], by its first day in [month] (else its next), then
     * creation order; ended ones last.
     */
    fun lines(
        schedules: List<StoredSchedule>,
        today: LocalDate,
        month: YearMonth
    ): List<ScheduleLine> {
        val lines = schedules.map { stored ->
            val recurrence = stored.schedule.recurrence
            val end = recurrence.end
            // The pause running today, or one still open: paused before its start, a
            // schedule's pause begins on that start (rule 8).
            val pause = recurrence.pauses.lastOrNull {
                it.until == null || (!it.from.isAfter(today) && it.until.isAfter(today))
            }
            val inMonth = occurrencesIn(stored, month)
            ScheduleLine(
                stored = stored,
                status = when {
                    end != null && end.isBefore(today) -> ScheduleStatus.Ended
                    pause != null -> ScheduleStatus.Paused
                    else -> ScheduleStatus.Active
                },
                pausedFrom = pause?.from,
                next = SchedulePolicy.due(
                    recurrence,
                    stored.handled,
                    today.plusDays(HORIZON_DAYS),
                    1
                )
                    .firstOrNull()?.date,
                monthFirst = inMonth.firstOrNull()?.date,
                toComeThisMonth = inMonth.any {
                    stored.handled == null || it.isAfter(stored.handled)
                }
            )
        }
        return lines.sortedWith(
            compareBy<ScheduleLine> { it.status == ScheduleStatus.Ended }
                .thenBy { it.monthFirst ?: it.next ?: LocalDate.MAX }
                .thenBy { it.stored.sequence }
        )
    }

    /**
     * [month] for [lines] of one kind, summed exactly; a sum that does not fit fails. What
     * went through is what the schedules' [records] for an occurrence in [month] hold as
     * stored now: an edited entry at its edited amount, a deleted one not at all, and an
     * occurrence the checkpoint passed without recording counts nothing. What is still to
     * come is each occurrence after the checkpoint, at the schedule's amount.
     */
    fun month(
        lines: List<ScheduleLine>,
        records: List<ScheduleRecord>,
        month: YearMonth
    ): Outcome<SchedulesMonth, MoneyError> {
        val listed = lines.mapTo(HashSet()) { it.stored.schedule.id }
        val done = records.filter { it.dayIn({ id -> id in listed }, month) != null }
            .map { it.amount }
        val toCome = mutableListOf<Money>()
        lines.forEach { line ->
            val handled = line.stored.handled
            val amount = line.stored.schedule.entry.amount.money
            occurrencesIn(line.stored, month).forEach {
                if (handled == null || it.isAfter(handled)) toCome += amount
            }
        }
        val currency = lines.firstOrNull()?.stored?.schedule?.entry?.amount?.money?.currency
            ?: Money.cop(0).currency
        val doneTotal = ExactTotal.of(currency, done)
        return Outcome.Success(
            SchedulesMonth(
                done = doneTotal.toMoney().orReturn { return it },
                doneCount = done.size,
                toCome = ExactTotal.of(currency, toCome).toMoney().orReturn { return it },
                toComeCount = toCome.size,
                total = toCome.fold(doneTotal) { total, money -> total + money }.toMoney()
                    .orReturn { return it },
                active = lines.count { it.status == ScheduleStatus.Active }
            )
        )
    }

    /**
     * The day of the last occurrence in [month] that [line]'s schedule has an entry for in
     * [records], as stored now; null when it has none.
     */
    fun recordedOn(
        line: ScheduleLine,
        records: List<ScheduleRecord>,
        month: YearMonth
    ): LocalDate? =
        records.mapNotNull { it.dayIn({ id -> id == line.stored.schedule.id }, month) }.maxOrNull()

    /**
     * The nearest date still to come among active [lines]; one already due that catch-up
     * has not recorded yet is [today]'s. Null when none is active.
     */
    fun nextDate(lines: List<ScheduleLine>, today: LocalDate): LocalDate? =
        lines.filter { it.status == ScheduleStatus.Active }.mapNotNull { it.next }.minOrNull()
            ?.let { maxOf(it, today) }

    private fun occurrencesIn(stored: StoredSchedule, month: YearMonth) =
        SchedulePolicy.occurrences(stored.schedule.recurrence, month.atDay(1), month.atEndOfMonth())

    private fun Occurrence.isAfter(other: Occurrence) =
        date.isAfter(other.date) || (date == other.date && slot > other.slot)

    /** Every record carries its origin (rule 4); an entry without one is no schedule's. */
    private val ScheduleRecord.origin: EntryOrigin?
        get() = when (this) {
            is ScheduleRecord.Payment -> expense.origin
            is ScheduleRecord.Earning -> income.origin
        }

    private val ScheduleRecord.amount: Money
        get() = when (this) {
            is ScheduleRecord.Payment -> expense.amount.money
            is ScheduleRecord.Earning -> income.amount.money
        }

    /** The day of the occurrence [this] recorded for [schedule], when it falls in [month]. */
    private fun ScheduleRecord.dayIn(schedule: (ScheduleId) -> Boolean, month: YearMonth) =
        origin?.takeIf { schedule(it.schedule) }?.occurrence?.date
            ?.takeIf { YearMonth.from(it) == month }

    private inline fun <T> Outcome<T, MoneyError>.orReturn(
        failed: (Outcome.Failure<MoneyError>) -> Nothing
    ): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> failed(this)
    }
}
