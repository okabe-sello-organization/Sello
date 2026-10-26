package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.SnapshotFailure

/**
 * The categories "Ajustar presupuesto" lists: [limited] are those with a finite limit,
 * overspent first; [open] are those without one that spent something, which still
 * counts against the month.
 */
data class AdjustOrder(val limited: List<CategoryMonth>, val open: List<CategoryMonth>)

/**
 * The limits the sheet offers for one category, each null when it is not offered:
 * [spent] covers what the category spent; [coverMonth] closes the month's overrun;
 * [coverPayments] covers what reserved payments still need.
 */
data class LimitShortcuts(val spent: Money?, val coverMonth: Money?, val coverPayments: Money?)

/**
 * The month were one category's limit changed: its [total] finite limit, and what
 * would be [left] after what was spent, and after reserved payments too when
 * [afterPayments]. A negative [left] is what would still be missing.
 */
data class LimitPreview(val total: Money, val left: Money, val afterPayments: Boolean)

/**
 * Adjusting one category's limit from Recibo (the AJ examples in
 * `docs/testing/mvp-financial-examples.md`). Every figure is exact and comes from one
 * snapshot; a sum that does not fit is a failure, never approximated.
 */
object LimitAdjustmentPolicy {
    private val byRemaining = compareBy<CategoryMonth> { it.budget!!.remaining.minorUnits }
        .thenBy { it.category.name.uniquenessKey }

    private val bySpent = compareByDescending<CategoryMonth> { it.spent.minorUnits }
        .thenBy { it.category.name.uniquenessKey }

    fun order(categories: List<CategoryMonth>): AdjustOrder = AdjustOrder(
        limited = categories.filter { it.budget != null }.sortedWith(byRemaining),
        open = categories.filter { it.budget == null && it.spent.minorUnits > 0 }
            .sortedWith(bySpent)
    )

    fun shortcuts(
        snapshot: MonthlySnapshot,
        categoryId: CategoryId
    ): Outcome<LimitShortcuts, SnapshotFailure> {
        val line = snapshot.categories.firstOrNull { it.category.id == categoryId }
            ?: return Outcome.Success(LimitShortcuts(null, null, null))
        val limit = line.budget?.limit ?: Money.cop(0)
        val spent = line.spent.takeIf { it.minorUnits > limit.minorUnits }
        val overall = snapshot.overall as? OverallBudget.Limited
        val reservation = overall?.reservation
        val overrun = overall?.budget?.remaining?.takeIf {
            reservation == null && it.minorUnits < 0
        }
        val coverMonth = overrun?.let { sum(limit, minus = it) }
        val coverPayments = reservation?.shortfall
            ?.takeIf { reservation.standing == ReservedStanding.Short }
            ?.let { sum(limit, plus = it) }
        if (coverMonth is Outcome.Failure) return coverMonth
        if (coverPayments is Outcome.Failure) return coverPayments
        return Outcome.Success(
            LimitShortcuts(
                spent = spent,
                coverMonth = (coverMonth as? Outcome.Success)?.value,
                coverPayments = (coverPayments as? Outcome.Success)?.value
            )
        )
    }

    fun preview(
        snapshot: MonthlySnapshot,
        categoryId: CategoryId,
        limit: BudgetLimit.Finite
    ): Outcome<LimitPreview, SnapshotFailure> {
        val categories = snapshot.categories.map { CategoryInMonth(it.category, it.state) }
        val total = when (
            val total = MonthlyBudgetPolicy.limitTotalWith(
                categories,
                categoryId,
                limit
            )
        ) {
            is Outcome.Failure -> return total

            is Outcome.Success ->
                total.value
                    ?: return Outcome.Failure(SnapshotFailure.Unrepresentable)
        }
        val reserved = (snapshot.overall as? OverallBudget.Limited)?.reservation?.total
        var left = ExactTotal.of(total.currency, listOf(total)) - snapshot.spent
        if (reserved != null) left -= reserved
        return when (val money = left.toMoney()) {
            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)

            is Outcome.Success -> Outcome.Success(
                LimitPreview(total, money.value, reserved != null)
            )
        }
    }

    /** [base] plus [plus], or minus [minus], exactly. */
    private fun sum(
        base: Money,
        plus: Money? = null,
        minus: Money? = null
    ): Outcome<Money, SnapshotFailure> {
        var total = ExactTotal.of(base.currency, listOf(base))
        if (plus != null) total += plus
        if (minus != null) total -= minus
        return when (val money = total.toMoney()) {
            is Outcome.Failure -> Outcome.Failure(SnapshotFailure.Unrepresentable)
            is Outcome.Success -> money
        }
    }
}
