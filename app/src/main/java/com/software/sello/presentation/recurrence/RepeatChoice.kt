package com.software.sello.presentation.recurrence

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RecurrenceError
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.SchedulePolicy
import java.time.LocalDate

/** The four ways Se repite offers (docs/design/v2 Pagos e ingresos fijos). */
enum class RepeatKind { Monthly, TwiceMonthly, Weeks, Days }

/**
 * What the person has picked in Se repite's sheet, before it is a rule: the [kind], the
 * [days] of the month in the order they were picked, the [interval] in weeks or days and
 * an optional [end]. It always holds something the sheet can show; [rule] is null until
 * it is complete, such as twice a month with one day picked. The rules themselves stay in
 * the domain's [RepeatRule] and [SchedulePolicy].
 */
data class RepeatChoice(
    val kind: RepeatKind,
    val days: List<Int>,
    val interval: Int,
    val end: LocalDate?
) {
    val canDecrease: Boolean get() = interval > 1
    val canIncrease: Boolean get() = interval < maximum(kind)

    /** Keeps what still fits: the first day picked for each month, the interval within bounds. */
    fun withKind(kind: RepeatKind): RepeatChoice = when (kind) {
        RepeatKind.Monthly -> copy(kind = kind, days = days.take(1))

        RepeatKind.TwiceMonthly -> copy(kind = kind, days = days.take(2))

        RepeatKind.Weeks, RepeatKind.Days ->
            copy(kind = kind, interval = interval.coerceAtMost(maximum(kind)))
    }

    /**
     * Each month: [day] becomes the day. Twice a month: a picked day is unpicked, and a
     * third day takes the place of the first of the two.
     */
    fun toggleDay(day: Int): RepeatChoice {
        if (day !in 1..LAST_DAY) return this
        return when (kind) {
            RepeatKind.TwiceMonthly -> when {
                day in days -> copy(days = days - day)
                days.size < 2 -> copy(days = days + day)
                else -> copy(days = days.drop(1) + day)
            }

            else -> copy(days = listOf(day))
        }
    }

    fun decrease(): RepeatChoice = if (canDecrease) copy(interval = interval - 1) else this

    fun increase(): RepeatChoice = if (canIncrease) copy(interval = interval + 1) else this

    fun withEnd(end: LocalDate?): RepeatChoice = copy(end = end)

    fun rule(): RepeatRule? = when (kind) {
        RepeatKind.Monthly -> days.singleOrNull()?.let { RepeatRule.Monthly.of(it).orNull() }

        RepeatKind.TwiceMonthly -> days.takeIf { it.size == 2 }?.sorted()
            ?.let { (first, second) -> RepeatRule.TwiceMonthly.of(first, second).orNull() }

        RepeatKind.Weeks -> RepeatRule.EveryWeeks.of(interval).orNull()

        RepeatKind.Days -> RepeatRule.EveryDays.of(interval).orNull()
    }

    /** The rule from [start], with no pause; an incomplete choice has none. */
    fun recurrence(start: LocalDate): Outcome<Recurrence, RecurrenceError>? =
        rule()?.let { Recurrence.of(it, start, end, emptyList()) }

    /** The first date recorded from [start], or null when nothing ever would be. */
    fun next(start: LocalDate): LocalDate? {
        val recurrence = (recurrence(start) as? Outcome.Success)?.value ?: return null
        return SchedulePolicy.occurrences(recurrence, start, start.plusDays(HORIZON_DAYS), 1)
            .firstOrNull()?.date
    }

    /** As text for saved state, read back by [decode]. */
    fun encode(): String =
        listOf(kind.name, days.joinToString(","), interval.toString(), end?.toString() ?: NO_END)
            .joinToString(";")

    companion object {
        private const val LAST_DAY = 31
        private const val NO_END = "-"

        /** Longer than the longest gap between two occurrences: every 365 days. */
        private const val HORIZON_DAYS = 400L

        /** Each month on [date]'s day, as the entry is dated. */
        fun startingOn(date: LocalDate) =
            RepeatChoice(RepeatKind.Monthly, listOf(date.dayOfMonth), 1, null)

        /** A stored [rule] and [end], as the sheet shows them. */
        fun of(rule: RepeatRule, end: LocalDate?) = when (rule) {
            is RepeatRule.Monthly -> RepeatChoice(RepeatKind.Monthly, listOf(rule.day), 1, end)

            is RepeatRule.TwiceMonthly ->
                RepeatChoice(RepeatKind.TwiceMonthly, listOf(rule.first, rule.second), 1, end)

            is RepeatRule.EveryWeeks -> RepeatChoice(RepeatKind.Weeks, emptyList(), rule.weeks, end)

            is RepeatRule.EveryDays -> RepeatChoice(RepeatKind.Days, emptyList(), rule.days, end)
        }

        /** Text from [encode], or null when it is not one: a choice is never repaired. */
        fun decode(text: String): RepeatChoice? {
            val parts = text.split(";").takeIf { it.size == 4 } ?: return null
            val kind = RepeatKind.entries.firstOrNull { it.name == parts[0] } ?: return null
            val days = if (parts[1].isEmpty()) {
                emptyList()
            } else {
                parts[1].split(",").map { it.toIntOrNull() ?: return null }
            }
            val interval = parts[2].toIntOrNull() ?: return null
            val end = if (parts[3] == NO_END) {
                null
            } else {
                (EffectiveDates.parseDate(parts[3]) as? Outcome.Success)?.value ?: return null
            }
            val valid = days.all { it in 1..LAST_DAY } && days.size == days.distinct().size &&
                days.size <= 2 && interval in 1..maximum(kind)
            return if (valid) RepeatChoice(kind, days, interval, end) else null
        }

        private fun maximum(kind: RepeatKind) = when (kind) {
            RepeatKind.Days -> RepeatRule.EveryDays.MAX
            else -> RepeatRule.EveryWeeks.MAX
        }

        private fun <T> Outcome<T, *>.orNull(): T? = (this as? Outcome.Success)?.value
    }
}
