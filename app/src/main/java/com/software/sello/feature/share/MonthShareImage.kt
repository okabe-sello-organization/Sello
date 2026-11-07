package com.software.sello.feature.share

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.feature.recibo.ReciboAnswerLabel
import com.software.sello.feature.recibo.ReciboRenderModel
import com.software.sello.feature.recibo.ReciboStamp
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the image of a month may show, and nothing else: the month, the day its
 * figures are from, Recibo's headline with its stamp, spent against the total limit and
 * each category's spent and limit. It has no field for notes, income, goals, investments
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
 * Turns Recibo's snapshot into [MonthShareImage] through Recibo's own render model, so the
 * image never says a figure Recibo doesn't. Categories come in Recibo's order; an archived
 * one is kept only when it has expenses that month.
 */
class MonthShareRenderModel(money: MoneyFormatter) {
    private val recibo = ReciboRenderModel(money)
    private val format = money

    /** Null when the month has no category to show. */
    fun image(
        snapshot: MonthlySnapshot,
        ordering: CategoryOrderingPreference = CategoryOrderingPreference()
    ): MonthShareImage? {
        val shown = snapshot.copy(
            categories = snapshot.categories.filter {
                !it.category.archived || it.expenseCount > 0
            }
        )
        if (shown.categories.isEmpty()) return null
        val summary = recibo.summary(snapshot)
        val overall = snapshot.overall as? OverallBudget.Limited
        return MonthShareImage(
            month = snapshot.month,
            asOf = snapshot.asOf,
            // A snapshot is read on today's financial day: an earlier month is finished.
            closed = snapshot.month < YearMonth.from(snapshot.asOf),
            label = summary.label,
            amount = summary.amount,
            stamp = summary.stamp,
            spent = format.format(overall?.budget?.spent ?: snapshot.spent),
            limit = overall?.budget?.limit?.let(format::format),
            categories = recibo.categories(shown, ordering).map {
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
}
