package com.software.sello.domain.policy

import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.StoredSchedule
import java.time.LocalDate
import java.time.YearMonth

enum class ScheduleStatus { Active, Paused, Ended }

/**
 * One schedule as Pagos e ingresos fijos lists it on a day: its [status], the first day of
 * the pause running then ([pausedFrom]), the [next] occurrence not yet recorded, which may
 * be due already while catch-up has not reached it, its first occurrence in the month
 * ([monthFirst]), whether one is still to come that month ([toComeThisMonth]) and the date
 * of the month's last occurrence already recorded ([recordedThisMonth]).
 */
data class ScheduleLine(
    val stored: StoredSchedule,
    val status: ScheduleStatus,
    val pausedFrom: LocalDate?,
    val next: LocalDate?,
    val monthFirst: LocalDate?,
    val toComeThisMonth: Boolean,
    val recordedThisMonth: LocalDate?
)

/**
 * What a month holds for some schedules, as a plan: occurrences already recorded
 * ([done], [doneCount]), those still to come ([toCome], [toComeCount]) and both
 * ([total]), with how many schedules are [active]. Never recorded money.
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
            val pause = recurrence.pauses.lastOrNull {
                !it.from.isAfter(today) && (it.until == null || it.until.isAfter(today))
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
                },
                recordedThisMonth = inMonth.lastOrNull {
                    stored.handled != null && !it.isAfter(stored.handled)
                }?.date
            )
        }
        return lines.sortedWith(
            compareBy<ScheduleLine> { it.status == ScheduleStatus.Ended }
                .thenBy { it.monthFirst ?: it.next ?: LocalDate.MAX }
                .thenBy { it.stored.sequence }
        )
    }

    /** [month] for [lines] of one kind, summed exactly; a sum that does not fit fails. */
    fun month(lines: List<ScheduleLine>, month: YearMonth): Outcome<SchedulesMonth, MoneyError> {
        val done = mutableListOf<Money>()
        val toCome = mutableListOf<Money>()
        lines.forEach { line ->
            val handled = line.stored.handled
            val amount = line.stored.schedule.entry.amount.money
            occurrencesIn(line.stored, month).forEach {
                if (handled != null && !it.isAfter(handled)) done += amount else toCome += amount
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

    private inline fun <T> Outcome<T, MoneyError>.orReturn(
        failed: (Outcome.Failure<MoneyError>) -> Nothing
    ): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> failed(this)
    }
}
