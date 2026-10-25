package com.software.sello.domain.model

import com.software.sello.domain.policy.EffectiveDates
import java.time.LocalDate

/**
 * What each occurrence of a schedule records: an ordinary expense or income with these
 * fields, dated on the occurrence (docs/testing/recurrence-examples.md, rule 1).
 */
sealed interface ScheduledEntry {
    val amount: TransactionAmount
    val note: Note?

    data class Payment(
        val categoryId: CategoryId,
        override val amount: TransactionAmount,
        override val note: Note?
    ) : ScheduledEntry

    data class Income(
        val source: IncomeSource,
        override val amount: TransactionAmount,
        override val note: Note?
    ) : ScheduledEntry
}

sealed interface RepeatRuleError {
    /** A day of the month outside 1–31. */
    data class DayOutOfRange(val day: Int) : RepeatRuleError

    /** Twice a month needs two different days, the earlier first. */
    data class DaysNotInOrder(val first: Int, val second: Int) : RepeatRuleError

    /** Every N days is 1–365; every N weeks is 1–52. */
    data class IntervalOutOfRange(val interval: Int, val maximum: Int) : RepeatRuleError
}

/** How a schedule repeats (rule 2). Each rule is built through its checked `of`. */
sealed interface RepeatRule {
    /** On [day] of each month, or on the last day of a shorter month; [day] stays the anchor. */
    @ConsistentCopyVisibility
    data class Monthly private constructor(val day: Int) : RepeatRule {
        companion object {
            fun of(day: Int): Outcome<Monthly, RepeatRuleError> =
                checkDay(day) ?: Outcome.Success(Monthly(day))
        }
    }

    /**
     * On [first] and [second] of each month, each clamped like [Monthly]. When both clamp
     * to one date, both occur on it, as slots 1 and 2.
     */
    @ConsistentCopyVisibility
    data class TwiceMonthly private constructor(val first: Int, val second: Int) : RepeatRule {
        companion object {
            fun of(first: Int, second: Int): Outcome<TwiceMonthly, RepeatRuleError> =
                checkDay(first) ?: checkDay(second) ?: if (first < second) {
                    Outcome.Success(TwiceMonthly(first, second))
                } else {
                    Outcome.Failure(RepeatRuleError.DaysNotInOrder(first, second))
                }
        }
    }

    /** On the start date and every [days] days after it. Not the same as [TwiceMonthly]. */
    @ConsistentCopyVisibility
    data class EveryDays private constructor(val days: Int) : RepeatRule {
        companion object {
            const val MAX = 365

            fun of(days: Int): Outcome<EveryDays, RepeatRuleError> =
                checkInterval(days, MAX) ?: Outcome.Success(EveryDays(days))
        }
    }

    /** On the start date and every [weeks] weeks after it. */
    @ConsistentCopyVisibility
    data class EveryWeeks private constructor(val weeks: Int) : RepeatRule {
        companion object {
            const val MAX = 52

            fun of(weeks: Int): Outcome<EveryWeeks, RepeatRuleError> =
                checkInterval(weeks, MAX) ?: Outcome.Success(EveryWeeks(weeks))
        }
    }
}

private fun checkDay(day: Int): Outcome.Failure<RepeatRuleError>? =
    if (day in 1..31) null else Outcome.Failure(RepeatRuleError.DayOutOfRange(day))

private fun checkInterval(interval: Int, maximum: Int): Outcome.Failure<RepeatRuleError>? =
    if (interval in 1..maximum) {
        null
    } else {
        Outcome.Failure(RepeatRuleError.IntervalOutOfRange(interval, maximum))
    }

/**
 * Days on which a schedule records nothing: from [from] up to the day before [until], or
 * from [from] on while [until] is null (rule 8). [until] is the day it was resumed, which
 * itself counts.
 */
data class SchedulePause(val from: LocalDate, val until: LocalDate?)

sealed interface RecurrenceError {
    /** The end date is before the start date. */
    data object EndBeforeStart : RecurrenceError

    /** A pause starts before the schedule or ends on or before its own first day. */
    data class InvalidPause(val pause: SchedulePause) : RecurrenceError

    /** Pauses must be in order, apart, and only the last one may still be open. */
    data class OverlappingPauses(val earlier: SchedulePause, val later: SchedulePause) :
        RecurrenceError

    /** A date that the stored text cannot hold (EffectiveDates). */
    data class DateOutOfRange(val date: LocalDate) : RecurrenceError
}

/**
 * When a schedule occurs: its [rule] from [start] (also the anchor of every N days or
 * weeks) to [end], if any, both included, less its [pauses].
 */
@ConsistentCopyVisibility
data class Recurrence private constructor(
    val rule: RepeatRule,
    val start: LocalDate,
    val end: LocalDate?,
    val pauses: List<SchedulePause>
) {
    companion object {
        fun of(
            rule: RepeatRule,
            start: LocalDate,
            end: LocalDate? = null,
            pauses: List<SchedulePause> = emptyList()
        ): Outcome<Recurrence, RecurrenceError> {
            val dates = listOfNotNull(start, end) +
                pauses.flatMap { listOfNotNull(it.from, it.until) }
            dates.firstOrNull { !EffectiveDates.isStorable(it) }?.let {
                return Outcome.Failure(RecurrenceError.DateOutOfRange(it))
            }
            if (end != null && end.isBefore(start)) {
                return Outcome.Failure(RecurrenceError.EndBeforeStart)
            }
            pauses.firstOrNull { it.from.isBefore(start) || it.until?.isAfter(it.from) == false }
                ?.let { return Outcome.Failure(RecurrenceError.InvalidPause(it)) }
            pauses.zipWithNext().firstOrNull { (earlier, later) ->
                earlier.until == null || later.from.isBefore(earlier.until)
            }?.let { (earlier, later) ->
                return Outcome.Failure(RecurrenceError.OverlappingPauses(earlier, later))
            }
            return Outcome.Success(Recurrence(rule, start, end, pauses))
        }
    }
}

/** A recurring payment or income: what it records and when. */
data class Schedule(val id: ScheduleId, val entry: ScheduledEntry, val recurrence: Recurrence)

/**
 * One occurrence of a schedule: its [date] and its [slot], 1 or 2 for twice a month
 * and 1 otherwise. With the schedule's identity it is the occurrence's origin (rule 4).
 */
data class Occurrence(val date: LocalDate, val slot: Int)

/**
 * Where an automatically recorded expense or income came from: its schedule and the
 * occurrence it records (rule 4). Each origin is recorded at most once; a manual entry
 * has none.
 */
data class EntryOrigin(val schedule: ScheduleId, val occurrence: Occurrence)

/** One income a schedule is still expected to record this month (rule 13). */
data class ExpectedIncome(
    val schedule: ScheduleId,
    val source: IncomeSource,
    val note: Note?,
    val occurrence: Occurrence,
    val amount: TransactionAmount
)

/**
 * The month's plan (ADR 0012 BG3): [recorded] income `I`, [expected] `F`, [planned]
 * `I + F` and what that plan leaves without a category budget, [unassigned]
 * `I + F − A`, signed. Expected money never enters recorded totals.
 */
data class IncomePlan(
    val recorded: Money,
    val expected: Money,
    val planned: Money,
    val unassigned: Money,
    val items: List<ExpectedIncome>
)
