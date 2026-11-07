package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.Verdict
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the image of a month may show, and nothing else: the month, the day its
 * figures are from, what is left of the total limit with its stamp, spent against that
 * limit and each category's spent and limit. Reserved payments never reach it. It has no field for notes, income, goals, investments
 * or settings, so none can reach the image (owner, 2026-11-06).
 *
 * [closed] is a finished month: it says Mes cerrado instead of the day.
 */
data class MonthShareImage(
    val month: YearMonth,
    val asOf: LocalDate,
    val closed: Boolean,
    val label: ReciboAnswerLabel,
    val amount: MoneyTextValue,
    val stamp: ReciboStamp,
    val spent: MoneyTextValue,
    /** The month's total limit; null when no category has one. */
    val limit: MoneyTextValue?,
    val categories: List<MonthShareCategory>
)

/**
 * One category on the image: what it spent and its limit, or none ("sin límite").
 * [fraction] is how full to draw its bar and nothing else.
 */
data class MonthShareCategory(
    val name: String,
    val iconKey: String,
    val spent: MoneyTextValue,
    val limit: MoneyTextValue?,
    val over: Boolean,
    val fraction: Float?,
    val archived: Boolean
)

/**
 * Turns Recibo's snapshot into [MonthShareImage]: the same categories in Recibo's order,
 * archived ones wherever Recibo lists them, so the lines add up to the total. The headline
 * is what is left of the total limit (limit − spent, signed) with the budget's own verdict,
 * never what reserved payments leave, so it matches "Gastado X de Y" and says nothing of
 * payments still to come (orchestrator ruling on #442, R1 and R2).
 */
class MonthShareRenderModel(private val money: MoneyFormatter) {
    private val recibo = ReciboRenderModel(money)

    /** Null when the month has no category to show. */
    fun image(
        snapshot: MonthlySnapshot,
        ordering: CategoryOrderingPreference = CategoryOrderingPreference()
    ): MonthShareImage? {
        if (snapshot.categories.isEmpty()) return null
        val budget = (snapshot.overall as? OverallBudget.Limited)?.budget
        return MonthShareImage(
            month = snapshot.month,
            asOf = snapshot.asOf,
            // A snapshot is read on today's financial day: an earlier month is finished.
            closed = snapshot.month < YearMonth.from(snapshot.asOf),
            label = if (budget == null) ReciboAnswerLabel.Spent else ReciboAnswerLabel.Remaining,
            amount = money.format(budget?.remaining ?: snapshot.spent),
            stamp = budget?.let(::verdict) ?: ReciboStamp.NoLimit,
            spent = money.format(budget?.spent ?: snapshot.spent),
            limit = budget?.limit?.let(money::format),
            categories = recibo.categories(snapshot, ordering).map {
                MonthShareCategory(
                    name = it.name,
                    iconKey = it.iconKey,
                    spent = it.spent,
                    limit = it.limit,
                    over = it.over,
                    fraction = it.fraction,
                    archived = it.archived
                )
            }
        )
    }

    /** The budget's own verdict on what was spent; reserved payments play no part. */
    private fun verdict(budget: LimitedBudget): ReciboStamp =
        when (val standing = budget.standing) {
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
}
