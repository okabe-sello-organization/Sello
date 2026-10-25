package com.software.sello.domain.policy

import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Reservation
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.port.StoredSchedule
import java.time.YearMonth

/**
 * Payments reserved on Recibo (docs/testing/recurrence-examples.md, rules 11 and 12; ADR
 * 0012 BG1–BG2). Only for the month in progress: a past month reserves nothing.
 */
object ReservationPolicy {
    /**
     * The payment occurrences in [month] not yet handled, from schedules active on their
     * date, by date, then creation order, then slot. One due today or earlier that
     * catch-up has not recorded yet is reserved too, so the headline never rises and
     * falls back while catch-up runs; one from an earlier month is not.
     */
    fun reserved(schedules: List<StoredSchedule>, month: YearMonth): List<ReservedPayment> =
        schedules
            .flatMap { stored ->
                val entry =
                    stored.schedule.entry as? ScheduledEntry.Payment ?: return@flatMap emptyList()
                SchedulePolicy.due(stored.schedule.recurrence, stored.handled, month.atEndOfMonth())
                    .filter { YearMonth.from(it.date) == month }
                    .map { occurrence ->
                        stored.sequence to ReservedPayment(
                            stored.schedule.id,
                            entry.categoryId,
                            entry.note,
                            occurrence,
                            entry.amount
                        )
                    }
            }
            .sortedWith(
                compareBy<Pair<Long, ReservedPayment>>({ it.second.occurrence.date }, { it.first })
                    .thenBy { it.second.occurrence.slot }
            )
            .map { it.second }

    /**
     * [payments] against what is [remaining] `R`, or null when none is reserved: `Q`
     * exactly, `M = R − Q` and its standing. A sum that does not fit fails.
     */
    fun reservation(
        remaining: Money,
        payments: List<ReservedPayment>
    ): Outcome<Reservation?, MoneyError> {
        if (payments.isEmpty()) return Outcome.Success(null)
        val total = ExactTotal.of(remaining.currency, payments.map { it.amount.money })
        val reserved = when (val money = total.toMoney()) {
            is Outcome.Success -> money.value
            is Outcome.Failure -> return money
        }
        val margin = when (
            val money = (
                ExactTotal.of(remaining.currency, listOf(remaining)) -
                    reserved
                ).toMoney()
        ) {
            is Outcome.Success -> money.value
            is Outcome.Failure -> return money
        }
        val standing = when {
            margin.minorUnits > 0 -> ReservedStanding.Enough
            margin.minorUnits == 0L -> ReservedStanding.NoMargin
            else -> ReservedStanding.Short
        }
        if (standing != ReservedStanding.Short) {
            return Outcome.Success(Reservation(payments, reserved, margin, standing))
        }
        val zero = ExactTotal.zero(remaining.currency)
        val shortfall = when (val money = (zero - margin).toMoney()) {
            is Outcome.Success -> money.value
            is Outcome.Failure -> return money
        }
        val overLimitBy = if (remaining.minorUnits < 0) {
            when (val money = (zero - remaining).toMoney()) {
                is Outcome.Success -> money.value
                is Outcome.Failure -> return money
            }
        } else {
            null
        }
        return Outcome.Success(
            Reservation(payments, reserved, margin, standing, shortfall, overLimitBy)
        )
    }
}
