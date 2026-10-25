package com.software.sello.domain.policy

import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.ExpectedIncome
import com.software.sello.domain.model.IncomePlan
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.port.StoredSchedule
import java.time.YearMonth

/**
 * Plan del mes (docs/testing/recurrence-examples.md, rule 13; ADR 0012 BG3). Only for the
 * current month: past months record what they recorded and expect nothing.
 */
object IncomePlanPolicy {
    /**
     * The income occurrences in [month] not yet handled, from schedules active on their
     * date, by date, then creation order, then slot. One still due today or earlier that
     * catch-up has not recorded is expected too; one from an earlier month is not.
     */
    fun expected(schedules: List<StoredSchedule>, month: YearMonth): List<ExpectedIncome> =
        schedules
            .flatMap { stored ->
                val entry =
                    stored.schedule.entry as? ScheduledEntry.Income ?: return@flatMap emptyList()
                SchedulePolicy.due(stored.schedule.recurrence, stored.handled, month.atEndOfMonth())
                    .filter { YearMonth.from(it.date) == month }
                    .map { occurrence ->
                        stored.sequence to ExpectedIncome(
                            stored.schedule.id,
                            entry.source,
                            entry.note,
                            occurrence,
                            entry.amount
                        )
                    }
            }
            .sortedWith(
                compareBy<Pair<Long, ExpectedIncome>>({ it.second.occurrence.date }, { it.first })
                    .thenBy { it.second.occurrence.slot }
            )
            .map { it.second }

    /**
     * The plan from the month's [recorded] income `I`, its category [allocated] `A` and
     * the [expected] incomes, or null when nothing is expected. A total that does not fit
     * an exact amount is a failure, never a rounded or clipped figure.
     */
    fun plan(
        recorded: Money,
        allocated: Money,
        expected: List<ExpectedIncome>
    ): Outcome<IncomePlan?, MoneyError> {
        if (expected.isEmpty()) return Outcome.Success(null)
        val currency = recorded.currency
        val total = ExactTotal.of(currency, expected.map { it.amount.money })
        val planned = total + recorded
        return Outcome.Success(
            IncomePlan(
                recorded = recorded,
                expected = total.toMoney().orReturn { return it },
                planned = planned.toMoney().orReturn { return it },
                unassigned = (planned - allocated).toMoney().orReturn { return it },
                items = expected
            )
        )
    }

    private inline fun <T> Outcome<T, MoneyError>.orReturn(
        failed: (Outcome.Failure<MoneyError>) -> Nothing
    ): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> failed(this)
    }
}
