package com.software.sello.feature.recibo

import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.CategoryOrderPolicy
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.presentation.money.MoneyFormatter

/**
 * Turns one snapshot into what Recibo and the category detail draw. It chooses words
 * and formats figures the snapshot already holds: nothing is added, subtracted or
 * judged here, so the two screens cannot disagree with each other or with the rules.
 */
class ReciboRenderModel(private val money: MoneyFormatter) {
    fun recibo(
        state: SnapshotState,
        ordering: CategoryOrderingPreference = CategoryOrderingPreference()
    ): ReciboState = when (state) {
        SnapshotState.Loading -> ReciboState.Loading

        is SnapshotState.Ready -> month(state.snapshot, stale = false, ordering)

        is SnapshotState.Failed -> state.lastGood?.let { month(it, stale = true, ordering) }
            ?: ReciboState.Failed
    }

    private fun month(
        snapshot: MonthlySnapshot,
        stale: Boolean,
        ordering: CategoryOrderingPreference
    ): ReciboState = if (snapshot.categories.isEmpty()) {
        ReciboState.FirstRun
    } else {
        ReciboState.Month(summary(snapshot), categories(snapshot, ordering), stale)
    }

    /** The month's categories in the order they are shown. */
    fun categories(
        snapshot: MonthlySnapshot,
        ordering: CategoryOrderingPreference = CategoryOrderingPreference()
    ): List<ReciboCategory> = CategoryOrderPolicy.order(
        snapshot.categories,
        ordering.order,
        ordering.direction
    ).map(::category)

    internal fun summary(snapshot: MonthlySnapshot): ReciboSummary =
        when (val overall = snapshot.overall) {
            OverallBudget.NoLimit -> ReciboSummary(
                ReciboAnswerLabel.Spent,
                money.format(snapshot.spent),
                ReciboStamp.NoLimit
            )

            is OverallBudget.Limited -> {
                val budget = overall.budget
                val reservation = overall.reservation
                val margin = reservation?.margin ?: budget.remaining
                val short = reservation?.standing == ReservedStanding.Short
                ReciboSummary(
                    // With payments reserved the headline is what they leave (ADR 0012 BG2).
                    label = if (short) {
                        ReciboAnswerLabel.ShortForPayments
                    } else {
                        ReciboAnswerLabel.Remaining
                    },
                    amount = money.format(reservation?.shortfall ?: margin),
                    stamp = when (reservation?.standing) {
                        ReservedStanding.NoMargin -> ReciboStamp.NoMargin
                        ReservedStanding.Short -> ReciboStamp.Review
                        else -> figure(budget).stamp
                    },
                    perDay = overall.allowance?.let {
                        ReciboPerDay(money.format(it.amount), it.availableDays)
                    },
                    // Spent, limit and remainder all come from this one budget.
                    closed = if (budget.standing is BudgetStanding.Past) {
                        ReciboClosedMonth(money.format(budget.spent), money.format(budget.limit))
                    } else {
                        null
                    },
                    // A finished month is not adjusted from here.
                    adjustable = short || (budget.standing as? BudgetStanding.Current)?.verdict ==
                        Verdict.Over,
                    reserved = reservation?.let {
                        ReciboReserved(money.format(it.total), it.payments.size)
                    },
                    shortfall = reservation?.takeIf { short }?.let {
                        ReciboShortfall(
                            left = money.format(it.overLimitBy ?: budget.remaining),
                            over = it.overLimitBy != null,
                            reserved = money.format(it.total)
                        )
                    }
                )
            }
        }

    private fun category(line: CategoryMonth): ReciboCategory {
        val budget = line.budget
        val spent = money.format(line.spent)
        return ReciboCategory(
            id = line.category.id.value,
            name = line.category.name.value,
            iconKey = line.category.icon.value,
            archived = line.category.archived,
            figure = budget?.let(::figure)
                ?: BudgetFigure(ReciboHeadline.Spent, spent, openStamp(line.state)),
            cellAmount = budget?.let { money.format(it.remaining) } ?: spent,
            spent = spent,
            limit = budget?.let { money.format(it.limit) },
            fraction = budget?.let(::fill),
            over = budget?.isOverspent ?: false,
            expenseCount = line.expenseCount
        )
    }

    private val LimitedBudget.isOverspent get() = remaining.minorUnits < 0

    private fun figure(budget: LimitedBudget): BudgetFigure {
        val current = budget.standing is BudgetStanding.Current
        return BudgetFigure(
            headline = when {
                budget.isOverspent -> ReciboHeadline.Overspent
                current -> ReciboHeadline.Remaining
                else -> ReciboHeadline.LeftOver
            },
            amount = money.magnitude(budget.remaining),
            stamp = when (val standing = budget.standing) {
                is BudgetStanding.Current -> when (standing.verdict) {
                    Verdict.OnTrack -> ReciboStamp.OnTrack
                    Verdict.AtLimit -> ReciboStamp.AtLimit
                    Verdict.Over -> ReciboStamp.Over
                }

                is BudgetStanding.Past -> when (standing.verdict) {
                    PastVerdict.WithinLimit -> ReciboStamp.WithinLimit
                    PastVerdict.Over -> ReciboStamp.OverLimit
                }
            }
        )
    }

    /** Only a category without a finite limit gets here; a finite one has a budget. */
    private fun openStamp(state: MonthBudgetState) = when (state) {
        is MonthBudgetState.Limited -> ReciboStamp.NoLimit
        MonthBudgetState.Paused -> ReciboStamp.Paused
        MonthBudgetState.Unconfigured -> ReciboStamp.NotSet
    }

    /**
     * How full to draw the circle. This is geometry: it is never shown as a number,
     * compared or turned back into money.
     */
    internal fun fill(budget: LimitedBudget): Float {
        val limit = budget.limit.minorUnits
        val spent = budget.spent.minorUnits
        return when {
            spent <= 0 -> 0f
            limit <= 0 || spent >= limit -> 1f
            else -> (spent.toDouble() / limit.toDouble()).toFloat()
        }
    }
}
