package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneyTextValue
import java.time.YearMonth

/** What "Ajustar presupuesto" shows. A failed read is its own state; it is never zero. */
sealed interface AdjustBudgetState {
    data object Loading : AdjustBudgetState

    /** The read failed and there is nothing earlier to show. */
    data object Failed : AdjustBudgetState

    /**
     * [month] as Recibo's answer slip says it, with the month's finite [limit] (null
     * without one) and what was [spent] against it. [limited] are the categories with a
     * limit, overspent first; [open] are those without one that spent something. With
     * [stale] the latest read failed and this is the last one that worked.
     */
    data class Ready(
        val month: YearMonth,
        val summary: ReciboSummary,
        val limit: MoneyTextValue?,
        val spent: MoneyTextValue,
        val limited: List<AdjustRow>,
        val open: List<AdjustRow>,
        val stale: Boolean
    ) : AdjustBudgetState
}

/**
 * One category in the list. [limit] and [remaining] are null without a limit; [overBy]
 * is the overrun without its sign, only when [over]. [fraction] is how full to draw its
 * circle and nothing else. An [archived] one cannot be adjusted here.
 */
data class AdjustRow(
    val id: String,
    val name: String,
    val iconKey: String,
    val spent: MoneyTextValue,
    val limit: MoneyTextValue?,
    val remaining: MoneyTextValue?,
    val overBy: MoneyTextValue?,
    val fraction: Float?,
    val over: Boolean,
    val archived: Boolean
)

sealed interface AdjustBudgetAction {
    data object Retry : AdjustBudgetAction

    /** Change this category's limit, or open its editor when it is archived. */
    data class Open(val categoryId: String) : AdjustBudgetAction

    /** The reserved payments, from the footnote. */
    data object OpenReserved : AdjustBudgetAction
}
