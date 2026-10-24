package com.software.sello.domain.policy

import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

sealed interface ScheduleStartError {
    /** A schedule may start no earlier than [earliest]. */
    data class TooEarly(val earliest: LocalDate) : ScheduleStartError
}

/**
 * The repeat rules of docs/testing/recurrence-examples.md. "Today" is always passed in
 * from the financial clock; nothing here reads the wall clock.
 */
object SchedulePolicy {
    /**
     * The occurrences of [recurrence] from [from] to [to], both included, in order of date
     * then slot, at most [limit] of them. The range is reached directly, not counted from
     * the start, and splitting it in two gives the same occurrences (rules 2, 3 and 8).
     */
    fun occurrences(
        recurrence: Recurrence,
        from: LocalDate,
        to: LocalDate,
        limit: Int = Int.MAX_VALUE
    ): List<Occurrence> = activeSpans(recurrence, from, to)
        .flatMap { (first, last) -> inSpan(recurrence, first, last) }
        .take(limit.coerceAtLeast(0))
        .toList()

    /** A new schedule starts today or later (rule 3). */
    fun newStart(start: LocalDate, today: LocalDate): Outcome<LocalDate, ScheduleStartError> =
        if (start.isBefore(today)) {
            Outcome.Failure(ScheduleStartError.TooEarly(today))
        } else {
            Outcome.Success(start)
        }

    /**
     * A changed rule starts after today, so it never reaches back to an occurrence that was
     * or could have been recorded (rule 7).
     */
    fun changedStart(start: LocalDate, today: LocalDate): Outcome<LocalDate, ScheduleStartError> =
        if (start.isAfter(today)) {
            Outcome.Success(start)
        } else {
            Outcome.Failure(ScheduleStartError.TooEarly(today.plusDays(1)))
        }

    /** The parts of [from]..[to] inside the start and end and outside every pause. */
    private fun activeSpans(
        recurrence: Recurrence,
        from: LocalDate,
        to: LocalDate
    ): Sequence<Pair<LocalDate, LocalDate>> = sequence {
        var cursor = maxOf(from, recurrence.start)
        val last = recurrence.end?.let { minOf(it, to) } ?: to
        for (pause in recurrence.pauses) {
            if (cursor.isAfter(last) || pause.from.isAfter(last)) break
            if (pause.until != null && !pause.until.isAfter(cursor)) continue
            if (pause.from.isAfter(cursor)) yield(cursor to pause.from.minusDays(1))
            cursor = pause.until ?: return@sequence
        }
        if (!cursor.isAfter(last)) yield(cursor to last)
    }

    private fun inSpan(
        recurrence: Recurrence,
        first: LocalDate,
        last: LocalDate
    ): Sequence<Occurrence> = when (val rule = recurrence.rule) {
        is RepeatRule.Monthly -> monthDays(first, last, listOf(rule.day))
        is RepeatRule.TwiceMonthly -> monthDays(first, last, listOf(rule.first, rule.second))
        is RepeatRule.EveryDays -> steps(recurrence.start, rule.days.toLong(), first, last)
        is RepeatRule.EveryWeeks -> steps(recurrence.start, 7L * rule.weeks, first, last)
    }

    /** Each month's [days], clamped to its last day; slot 1 for the first day, 2 for the second. */
    private fun monthDays(
        first: LocalDate,
        last: LocalDate,
        days: List<Int>
    ): Sequence<Occurrence> = generateSequence(YearMonth.from(first)) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(YearMonth.from(last)) }
        .flatMap { month ->
            days.mapIndexed { index, day ->
                Occurrence(month.atDay(minOf(day, month.lengthOfMonth())), index + 1)
            }
        }
        .filter { !it.date.isBefore(first) && !it.date.isAfter(last) }

    /** [start] plus whole multiples of [step] days, from the first one on or after [first]. */
    private fun steps(
        start: LocalDate,
        step: Long,
        first: LocalDate,
        last: LocalDate
    ): Sequence<Occurrence> {
        val offset = ChronoUnit.DAYS.between(start, first)
        val firstStep = (offset + step - 1) / step
        return generateSequence(start.plusDays(firstStep * step)) { it.plusDays(step) }
            .takeWhile { !it.isAfter(last) }
            .map { Occurrence(it, 1) }
    }
}
