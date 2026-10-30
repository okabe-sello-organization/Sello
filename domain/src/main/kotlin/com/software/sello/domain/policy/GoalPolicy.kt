package com.software.sello.domain.policy

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalHistoryError
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalMovementLine
import com.software.sello.domain.model.GoalOverview
import com.software.sello.domain.model.GoalPace
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalProgress
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthContribution
import com.software.sello.domain.model.NextPlace
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PlaceProgress
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.model.TransactionAmount
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The goal rules of docs/testing/goal-examples.md. Everything is worked out from a
 * goal's history in recording order, so the same history always gives the same stamps:
 * drawing, reopening, restoring or restarting never earns one again (rule 4).
 */
object GoalPolicy {
    private val hundred: BigInteger = BigInteger.valueOf(100)
    private val stampPlaces = listOf(GoalPlace.Quarter, GoalPlace.Half, GoalPlace.ThreeQuarters)

    /** How many months Aportes por mes shows at most. */
    const val CHART_MONTHS = 6

    /** How many lines one receipt of several movements may hold. */
    const val MAX_RECEIPT_LINES = 30

    /** The history replayed in recording order, and the event that first reached each place. */
    private class Replay(
        val saved: BigInteger,
        val target: TransactionAmount,
        val contributions: Int,
        val earned: Map<GoalPlace, GoalEvent>
    )

    private fun replay(history: GoalHistory): Outcome<Replay, GoalHistoryError> {
        val events = history.events.sortedBy(GoalEvent::sequence)
        if (events.firstOrNull() !is GoalTargetChange) {
            return Outcome.Failure(GoalHistoryError.NoTarget)
        }
        var saved = BigInteger.ZERO
        var target = (events.first() as GoalTargetChange).target
        var contributions = 0
        val earned = mutableMapOf<GoalPlace, GoalEvent>()
        for (event in events) {
            when (event) {
                is GoalTargetChange -> target = event.target

                is GoalMovement -> {
                    val amount = event.amount.money.minorUnits.toBigInteger()
                    when (event.kind) {
                        GoalMovementKind.Contribution -> {
                            saved += amount
                            contributions++
                        }

                        GoalMovementKind.Withdrawal -> {
                            if (amount > saved) {
                                return money(saved)?.let {
                                    Outcome.Failure(
                                        GoalHistoryError.WithdrawalAboveSaved(event.id, it)
                                    )
                                } ?: Outcome.Failure(GoalHistoryError.Unrepresentable)
                            }
                            saved -= amount
                        }
                    }
                }
            }
            stampPlaces.filter { it !in earned && reached(saved, target, it) }
                .forEach { earned[it] = event }
        }
        return Outcome.Success(Replay(saved, target, contributions, earned))
    }

    fun progress(history: GoalHistory): Outcome<GoalProgress, GoalHistoryError> {
        val replay = when (val replayed = replay(history)) {
            is Outcome.Success -> replayed.value
            is Outcome.Failure -> return replayed
        }
        val saved = replay.saved
        val target = replay.target
        val earned = replay.earned.mapValues { (_, event) -> event.recordedOn }
        val total = money(saved) ?: return Outcome.Failure(GoalHistoryError.Unrepresentable)
        val completedOn = history.goal.completedOn
        val places = GoalPlace.entries.map { place ->
            val state = when {
                completedOn != null -> PlaceState.Earned(earned[place] ?: completedOn)

                place == GoalPlace.Meta ->
                    if (reached(saved, target, place)) PlaceState.Ready else PlaceState.Empty

                place !in earned -> PlaceState.Empty

                reached(saved, target, place) -> PlaceState.Earned(earned.getValue(place))

                else -> PlaceState.Kept(earned.getValue(place))
            }
            PlaceProgress(place, state)
        }
        val next = if (completedOn != null) {
            null
        } else {
            places.firstOrNull { it.state == PlaceState.Empty || it.state == PlaceState.Ready }
                ?.let { NextPlace(it.place, missing(saved, target, it.place)) }
        }
        return Outcome.Success(
            GoalProgress(
                saved = total,
                target = target,
                percentage = (saved * hundred / units(target)).toLong(),
                places = places,
                next = next,
                contributions = replay.contributions,
                completed = completedOn != null
            )
        )
    }

    /**
     * The goal as it would stand with [target] recorded [today], while editing: a stamp
     * earned under the old target and no longer reached shows kept (rule 7). The same
     * target changes nothing. Nothing is written.
     */
    fun withTarget(
        history: GoalHistory,
        target: TransactionAmount,
        today: LocalDate
    ): Outcome<GoalProgress, GoalHistoryError> {
        val current = (
            history.events.filterIsInstance<GoalTargetChange>()
                .maxByOrNull(GoalTargetChange::sequence)
            )?.target
        if (current == target) return progress(history)
        val next = (history.events.maxOfOrNull(GoalEvent::sequence) ?: -1) + 1
        return progress(
            history.copy(events = history.events + GoalTargetChange(target, next, today))
        )
    }

    /**
     * The goal as it would stand with [movement]: in its own place when it corrects one
     * already recorded (the same identifier), else at the end of the history. Checked
     * before a form sends it, by the same rules as the commands (rule 1, MT08, MT15).
     */
    fun withMovement(
        history: GoalHistory,
        movement: GoalMovement
    ): Outcome<GoalProgress, GoalHistoryError> {
        val existing = history.events.filterIsInstance<GoalMovement>()
            .firstOrNull { it.id == movement.id }
        val events = if (existing != null) {
            val corrected = movement.copy(sequence = existing.sequence)
            history.events.map { event -> if (event == existing) corrected else event }
        } else {
            val next = (history.events.maxOfOrNull(GoalEvent::sequence) ?: -1) + 1
            history.events + movement.copy(sequence = next)
        }
        return progress(history.copy(events = events))
    }

    /**
     * The 0-based positions of a receipt's [lines] in the order they are recorded after
     * the history: by date, a tie in line order. Stamps and the check that no retiro takes
     * more than is saved follow this order, so a retiro may use an aporte of the same
     * receipt dated before it.
     */
    fun recordingOrder(lines: List<GoalMovementLine>): List<Int> =
        lines.indices.sortedWith(compareBy({ lines[it].date }, { it }))

    /** What [after] changed against [before]: a goal with no [before] earned nothing yet. */
    fun change(before: GoalProgress?, after: GoalProgress): GoalChange {
        fun earnedIn(progress: GoalProgress?, place: GoalPlace) =
            progress?.state(place).let { it is PlaceState.Earned || it is PlaceState.Kept }
        return GoalChange(
            earned = GoalPlace.entries.filter {
                !earnedIn(before, it) && after.state(it) is PlaceState.Earned
            },
            relit = GoalPlace.entries.filter {
                before?.state(it) is PlaceState.Kept && after.state(it) is PlaceState.Earned
            },
            reachedMeta = before?.state(GoalPlace.Meta) != PlaceState.Ready &&
                after.state(GoalPlace.Meta) == PlaceState.Ready
        )
    }

    /**
     * The [GoalEvent.sequence] of the event that first earned each place, as the goal's
     * history now stands: a movement list shows the stamp beside that aporte. The Meta
     * stamp, and any stamp given only at completion (MT26), belong to no event.
     */
    fun earnedBy(history: GoalHistory): Outcome<Map<GoalPlace, Long>, GoalHistoryError> =
        when (val replayed = replay(history)) {
            is Outcome.Success ->
                Outcome.Success(replayed.value.earned.mapValues { (_, event) -> event.sequence })

            is Outcome.Failure -> replayed
        }

    /**
     * Metas' order: active goals by highest exact completion (saved ÷ target, not the
     * rounded percentage), the earlier created on a tie; then completed goals, the most
     * recently completed first, the earlier created on a tie.
     */
    fun <T> ordered(goals: List<T>, goal: (T) -> Goal, progress: (T) -> GoalProgress): List<T> {
        val (active, completed) = goals.partition { goal(it).completedOn == null }
        val byCompletion = Comparator<T> { a, b ->
            val aProgress = progress(a)
            val bProgress = progress(b)
            (units(bProgress.saved) * units(aProgress.target))
                .compareTo(units(aProgress.saved) * units(bProgress.target))
        }.thenBy { goal(it).createdAt }
        val byCompletedOn = compareByDescending<T> { goal(it).completedOn }
            .thenBy { goal(it).createdAt }
        return active.sortedWith(byCompletion) + completed.sortedWith(byCompletedOn)
    }

    /**
     * Metas' band for [month]: active goals only, exact; a total that does not fit an
     * amount is reported, never clipped.
     */
    fun overview(
        goals: List<Pair<GoalHistory, GoalProgress>>,
        month: YearMonth
    ): Outcome<GoalOverview, GoalHistoryError> {
        val active = goals.filter { (history, _) -> history.goal.completedOn == null }
        val saved = active.sumOf { (_, progress) -> units(progress.saved) }
        val contributed = active.sumOf { (history, _) ->
            history.events.filterIsInstance<GoalMovement>()
                .filter {
                    it.kind == GoalMovementKind.Contribution && YearMonth.from(it.date) == month
                }
                .sumOf { units(it.amount) }
        }
        val savedMoney = money(saved) ?: return Outcome.Failure(GoalHistoryError.Unrepresentable)
        val contributedMoney =
            money(contributed) ?: return Outcome.Failure(GoalHistoryError.Unrepresentable)
        return Outcome.Success(GoalOverview(savedMoney, contributedMoney))
    }

    /** What is still missing to the target; nothing once it is reached or passed. */
    fun missing(progress: GoalProgress): Money =
        Money.cop(maxOf(units(progress.target) - units(progress.saved), BigInteger.ZERO).toLong())

    /**
     * Aportes por mes: the aportes dated in each month, withdrawals not subtracted, for
     * up to [CHART_MONTHS] months ending in [today]'s month, or in the month a completed
     * goal was completed. The first month is never earlier than the goal's first: the
     * month it was created in, or an earlier aporte's date.
     */
    fun monthlyContributions(
        history: GoalHistory,
        today: LocalDate
    ): Outcome<List<MonthContribution>, GoalHistoryError> {
        val last = YearMonth.from(history.goal.completedOn ?: today)
        val movements = history.events.filterIsInstance<GoalMovement>()
        val first = (
            history.events.map { YearMonth.from(it.recordedOn) } +
                movements.map { YearMonth.from(it.date) }
            ).minOrNull() ?: last
        val start = maxOf(first, last.minusMonths(CHART_MONTHS - 1L)).coerceAtMost(last)
        val contributions = mutableListOf<MonthContribution>()
        var month = start
        while (month <= last) {
            val sum = movements
                .filter {
                    it.kind == GoalMovementKind.Contribution && YearMonth.from(it.date) == month
                }
                .sumOf { units(it.amount) }
            val amount = money(sum) ?: return Outcome.Failure(GoalHistoryError.Unrepresentable)
            contributions += MonthContribution(month, amount)
            month = month.plusMonths(1)
        }
        return Outcome.Success(contributions)
    }

    /** A movement may be dated [today] or earlier, never later (rule 9). */
    fun acceptsDate(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today)

    /**
     * Recibo's goal (rule 12): the [chosen] one while it is active; otherwise the active
     * goal with the highest exact completion, the earlier created on a tie.
     */
    fun featured(goals: List<Pair<Goal, GoalProgress>>, chosen: GoalId?): GoalId? {
        val active = goals.filter { (goal, _) -> goal.completedOn == null }
        active.firstOrNull { (goal, _) -> goal.id == chosen }?.let { return it.first.id }
        return active.sortedWith { (a, aProgress), (b, bProgress) ->
            val byCompletion = (units(bProgress.saved) * units(aProgress.target))
                .compareTo(units(aProgress.saved) * units(bProgress.target))
            if (byCompletion != 0) byCompletion else a.createdAt.compareTo(b.createdAt)
        }.firstOrNull()?.first?.id
    }

    /**
     * The plan while creating or editing (rule 13). Months run from the month after
     * [today] through [targetMonth]; none is offered for this month or an earlier one, or
     * once nothing is missing. [progress] is absent for a new goal.
     */
    fun pace(
        target: TransactionAmount,
        progress: GoalProgress?,
        today: LocalDate,
        targetMonth: YearMonth
    ): GoalPace? {
        val current = YearMonth.from(today)
        val months = ChronoUnit.MONTHS.between(current, targetMonth)
        if (months <= 0) return null
        val saved = progress?.saved?.let(::units) ?: BigInteger.ZERO
        val missing = units(target) - saved
        if (missing.signum() <= 0) return null
        val count = BigInteger.valueOf(months)
        val monthly = ceilDiv(missing, count)
        val projected = GoalPlace.entries.filter {
            progress == null || progress.state(it) == PlaceState.Empty
        }.associateWith { place ->
            val need =
                ceilDiv(units(target) * BigInteger.valueOf(place.percent.toLong()), hundred) -
                    saved
            current.plusMonths(ceilDiv(need, monthly).toLong())
        }
        return GoalPace(months.toInt(), Money.cop(monthly.toLong()), projected)
    }

    private fun reached(saved: BigInteger, target: TransactionAmount, place: GoalPlace) =
        saved * hundred >= units(target) * BigInteger.valueOf(place.percent.toLong())

    private fun missing(saved: BigInteger, target: TransactionAmount, place: GoalPlace): Money {
        val threshold =
            ceilDiv(units(target) * BigInteger.valueOf(place.percent.toLong()), hundred)
        return Money.cop(maxOf(threshold - saved, BigInteger.ZERO).toLong())
    }

    private fun ceilDiv(a: BigInteger, b: BigInteger): BigInteger {
        val (quotient, remainder) = a.divideAndRemainder(b)
        return if (remainder.signum() > 0) quotient + BigInteger.ONE else quotient
    }

    private fun units(amount: TransactionAmount) = amount.money.minorUnits.toBigInteger()

    private fun units(amount: Money) = amount.minorUnits.toBigInteger()

    private fun money(units: BigInteger): Money? =
        if (units.bitLength() < Long.SIZE_BITS) Money(units.toLong(), Currency.COP) else null
}
