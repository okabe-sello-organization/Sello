package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.presentation.money.MoneyFormatter
import java.math.BigDecimal
import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

sealed interface DetailInsightsState {
    data object Loading : DetailInsightsState
    data object Failed : DetailInsightsState
    data class Ready(val figures: DetailInsights) : DetailInsightsState
}

data class DetailWeek(
    val number: Int,
    val from: LocalDate,
    val until: LocalDate,
    val spent: MoneyTextValue,
    val value: Long,
    val current: Boolean,
    val allowance: MoneyTextValue?,
    val ceiling: Long
)

data class DetailHistoryMonth(
    val month: YearMonth,
    val spent: MoneyTextValue,
    val value: Long,
    val limit: MoneyTextValue?,
    val limitValue: Long?,
    val current: Boolean = false
)

enum class DetailComparisonDirection { Higher, Lower, Equal }

data class DetailInsights(
    val allowance: ReciboPerDay?,
    val comparisonMonth: YearMonth?,
    val comparison: String?,
    val elapsedDays: Int,
    val averageExpense: MoneyTextValue?,
    val largest: ExpenseRow?,
    val weekday: DayOfWeek?,
    val weeklyAverage: MoneyTextValue?,
    val weeks: List<DetailWeek>,
    val historicalAverage: MoneyTextValue?,
    val history: List<DetailHistoryMonth>,
    val comparisonDirection: DetailComparisonDirection = DetailComparisonDirection.Equal,
    /**
     * While nothing can be spent per day because the category is over: by how much, so a
     * zero allowance never hides the deficit (ADR 0020 CP15).
     */
    val allowanceDeficit: MoneyTextValue? = null
)

class CategoryInsightsRenderModel(private val money: MoneyFormatter) {
    fun render(snapshot: CategoryInsights) = DetailInsights(
        allowance = snapshot.allowance?.let {
            ReciboPerDay(money.format(it.amount), it.availableDays)
        },
        comparisonMonth = snapshot.comparison?.previousMonth,
        comparison = snapshot.comparison?.tenthsPercent?.let(::percentage),
        elapsedDays = snapshot.elapsedDays,
        averageExpense = snapshot.averageExpense?.let(money::format),
        largest = snapshot.largestExpense?.let {
            ExpenseRow(it.id.value, it.date, it.note?.value, money.format(it.amount.money), false)
        },
        weekday = snapshot.mostExpensiveWeekday,
        weeklyAverage = snapshot.weeklyAverage?.let(money::format),
        weeks = snapshot.weeks.map {
            DetailWeek(
                it.number,
                it.from,
                it.until,
                money.format(it.spent),
                it.spent.minorUnits,
                it.current,
                it.remainingAllowance?.let(money::format),
                it.ceiling.minorUnits
            )
        },
        historicalAverage = snapshot.historicalAverage?.let(money::format),
        history = history(snapshot),
        comparisonDirection = when (snapshot.comparison?.tenthsPercent?.signum()) {
            1 -> DetailComparisonDirection.Higher
            -1 -> DetailComparisonDirection.Lower
            else -> DetailComparisonDirection.Equal
        },
        allowanceDeficit = snapshot.budget.categories
            .singleOrNull { it.category.id == snapshot.categoryId }?.budget?.remaining
            ?.takeIf { it.minorUnits < 0 && snapshot.allowance?.amount?.minorUnits == 0L }
            ?.let(money::magnitude)
    )

    private fun history(snapshot: CategoryInsights): List<DetailHistoryMonth> {
        val completed = snapshot.history.map {
            DetailHistoryMonth(
                it.month,
                money.format(it.spent),
                it.spent.minorUnits,
                it.limit?.let(money::format),
                it.limit?.minorUnits
            )
        }
        val budget = snapshot.budget
        if (budget.month != YearMonth.from(budget.asOf)) return completed
        val category = budget.categories.singleOrNull { it.category.id == snapshot.categoryId }
            ?: return completed
        // The current point shares the hero's exact snapshot; it is not a completed month.
        return completed + DetailHistoryMonth(
            budget.month,
            money.format(category.spent),
            category.spent.minorUnits,
            category.budget?.limit?.let(money::format),
            category.budget?.limit?.minorUnits,
            current = true
        )
    }

    private fun percentage(value: BigInteger): String {
        val sign = when (value.signum()) {
            1 -> "+"
            -1 -> "−"
            else -> ""
        }
        val digits = BigDecimal(value.abs(), 1).setScale(1).toPlainString().replace('.', ',')
        return "$sign$digits %"
    }
}
