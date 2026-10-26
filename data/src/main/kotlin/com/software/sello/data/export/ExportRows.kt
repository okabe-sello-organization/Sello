package com.software.sello.data.export

import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Money
import java.math.BigDecimal
import java.time.LocalDate

/** Every kind of money movement an export lists, with its stable code for JSON. */
internal enum class ExportKind(val code: String) {
    Expense("expense"),
    Income("income"),
    GoalContribution("goal_contribution"),
    GoalWithdrawal("goal_withdrawal"),
    InvestmentContribution("investment_contribution"),
    InvestmentWithdrawal("investment_withdrawal"),
    Dividend("dividend"),
    InvestmentValue("investment_value")
}

/** Who a row belongs to: a category, a goal, an investment, or an income's source. */
internal sealed interface ExportName {
    data class Named(val text: String) : ExportName

    /** A fixed income source, named by the app's words; `other` sources are [Named]. */
    data class Source(val key: String) : ExportName
}

/** One money movement: a CSV line, or one entry of the JSON's `movements`. */
internal data class ExportRow(
    val date: LocalDate,
    val kind: ExportKind,
    val name: ExportName,
    val amount: Money,
    val note: String?
)

/**
 * Every money movement of [snapshot], by date. On one date: expenses, incomes, goal
 * movements, then investment movements, each in the order they were recorded, so the
 * same history always makes the same rows.
 */
internal fun movements(snapshot: FinancialSnapshot): List<ExportRow> {
    val categories = snapshot.categories.associate { it.id to it.name.value }
    val expenses = snapshot.expenses.sortedBy { it.sequence }.map {
        ExportRow(
            it.date,
            ExportKind.Expense,
            ExportName.Named(categories.getValue(it.categoryId)),
            it.amount.money,
            it.note?.value
        )
    }
    val incomes = snapshot.incomes.sortedBy { it.sequence }.map {
        ExportRow(it.date, ExportKind.Income, it.source.exportName(), it.amount.money, it.note?.value)
    }
    val goals = snapshot.goals.flatMap { history ->
        history.events.filterIsInstance<GoalMovement>().sortedBy { it.sequence }.map {
            ExportRow(
                it.date,
                if (it.kind == GoalMovementKind.Contribution) {
                    ExportKind.GoalContribution
                } else {
                    ExportKind.GoalWithdrawal
                },
                ExportName.Named(history.goal.name.value),
                it.amount.money,
                it.note?.value
            )
        }
    }
    val investments = snapshot.investments.flatMap { history ->
        history.movements.sortedBy { it.sequence }.map {
            ExportRow(
                it.date,
                when (it.kind) {
                    InvestmentMovementKind.Contribution -> ExportKind.InvestmentContribution
                    InvestmentMovementKind.Withdrawal -> ExportKind.InvestmentWithdrawal
                    InvestmentMovementKind.Dividend -> ExportKind.Dividend
                    InvestmentMovementKind.Valuation -> ExportKind.InvestmentValue
                },
                ExportName.Named(history.investment.name.value),
                it.amount.money,
                it.note?.value
            )
        }
    }
    // A stable sort: one date keeps the order above.
    return (expenses + incomes + goals + investments).sortedBy { it.date }
}

internal fun IncomeSource.exportName(): ExportName = when (this) {
    is IncomeSource.Other -> ExportName.Named(name.value)
    else -> ExportName.Source(key)
}

/** Exact, from minor units, with a point: `125000` pesos, `1050.50` dollars. */
internal fun Money.exportText(): String =
    BigDecimal.valueOf(minorUnits, currency.scale).toPlainString()
