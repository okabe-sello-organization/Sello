package com.software.sello.feature.resumen

import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.WeekdayPattern
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.presentation.carry.carryFigures
import com.software.sello.presentation.money.MoneyFormatter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class ResumenRenderModel(private val money: MoneyFormatter) {
    /** [current] is the financial month in progress; a date from another year shows its year. */
    fun render(state: ResumenSnapshotState, current: YearMonth): ResumenState = when (state) {
        ReadState.Loading -> ResumenState.Loading

        is ReadState.Ready -> month(state.value, false, current)

        is ReadState.Failed -> state.lastGood?.let { month(it, true, current) }
            ?: ResumenState.Failed
    }

    private fun month(
        snapshot: ResumenSnapshot,
        stale: Boolean,
        current: YearMonth
    ): ResumenState.Ready {
        val budget = snapshot.budget
        val firstRecordedMonth = snapshot.history.indexOfFirst { it.expenseCount > 0 }
        // With fewer than two months recorded there is nothing yet to average or compare.
        val insufficient = snapshot.history.count { it.expenseCount > 0 } < 2
        val history = if (firstRecordedMonth < 0) {
            emptyList()
        } else {
            snapshot.history.drop(firstRecordedMonth)
        }
        val carry = (budget.overall as? OverallBudget.Limited)?.budget
            ?.let { money.carryFigures(it, budget.month, withSpent = false) }
        return ResumenState.Ready(
            month = budget.month.toString(),
            hero = hero(snapshot),
            income = money.format(snapshot.income),
            spent = money.format(budget.spent),
            netFlow = money.format(snapshot.netFlow),
            incomeSlip = income(snapshot),
            history = history.map { entry ->
                val label = entry.month.format(monthFormat)
                val amount = money.format(entry.spent)
                val limit = entry.limit
                // The month in progress is always drawn as such, over its limit or not:
                // it is never a whole month.
                val current = entry.month == YearMonth.from(budget.asOf)
                ResumenPoint(
                    ChartPoint(
                        entry.month.toString(),
                        entry.month.format(axisMonthFormat),
                        entry.spent.minorUnits,
                        "$label, ${amount.spoken}",
                        when {
                            current -> ChartPeriod.Current

                            limit != null && entry.spent.minorUnits > limit.minorUnits ->
                                ChartPeriod.Over

                            else -> ChartPeriod.Past
                        }
                    ),
                    label,
                    amount,
                    entry.limit?.let(money::format),
                    current
                )
            },
            monthLimit = snapshot.history.lastOrNull { it.month == budget.month }?.limit?.let {
                ResumenLimit(it.minorUnits, money.format(it), carried = carry != null)
            },
            carry = carry,
            categories = budget.categories.filter { it.expenseCount > 0 }
                .sortedByDescending { it.spent.minorUnits }.map { category ->
                    ResumenCategory(
                        category.category.id.value,
                        category.category.name.value,
                        category.category.icon.value,
                        money.format(category.spent),
                        share(category.spent.minorUnits, budget.spent.minorUnits)
                    )
                },
            normalMonth = snapshot.normalMonth?.takeUnless { insufficient }?.let {
                ResumenNormalMonth(money.format(it.average), it.months)
            },
            weekdays = snapshot.weekdays?.let(::weekdays),
            suggestion = snapshot.suggestion?.let {
                ResumenSuggestion(
                    it.categoryId.value,
                    it.name.value,
                    it.months.map(::monthName),
                    money.format(it.averageSpent),
                    money.format(it.limit)
                )
            },
            empty = history.isEmpty(),
            insufficientHistory = insufficient,
            stale = stale,
            asOf = budget.asOf.format(
                if (budget.asOf.year == current.year) sentenceDate else sentenceDateWithYear
            )
        )
    }

    private fun hero(snapshot: ResumenSnapshot): ResumenHero {
        val budget = snapshot.budget
        val inProgress = budget.month == YearMonth.from(budget.asOf)
        return ResumenHero(
            inProgress = inProgress,
            monthName = monthName(budget.month),
            spent = money.format(budget.spent),
            throughDay = if (inProgress) budget.asOf.dayOfMonth else budget.month.lengthOfMonth(),
            comparison = snapshot.comparison?.let { comparison ->
                // Both on one scale: the larger fills the bar.
                val largest = maxOf(
                    comparison.spent.minorUnits,
                    comparison.previousSpent.minorUnits
                )
                ResumenComparison(
                    previousName = monthName(comparison.previousMonth),
                    difference = money.format(comparison.difference),
                    direction = comparison.direction,
                    percent = comparison.percentTenths?.let(::percent),
                    throughDay = comparison.throughDay,
                    previousThroughDay = comparison.previousThroughDay,
                    bars = listOf(
                        ComparisonBar(
                            monthTitle(budget.month),
                            money.format(comparison.spent),
                            share(comparison.spent.minorUnits, largest),
                            current = true
                        ),
                        ComparisonBar(
                            monthTitle(comparison.previousMonth),
                            money.format(comparison.previousSpent),
                            share(comparison.previousSpent.minorUnits, largest),
                            current = false
                        )
                    )
                )
            }
        )
    }

    private fun income(snapshot: ResumenSnapshot) = ResumenIncome(
        count = snapshot.incomeCount,
        spentShare = share(snapshot.budget.spent.minorUnits, snapshot.income.minorUnits),
        overspent = snapshot.netFlow.minorUnits < 0
    )

    private fun weekdays(pattern: WeekdayPattern): ResumenWeekdays {
        val busiest = pattern.days.firstOrNull { it.day == pattern.busiest }
        return ResumenWeekdays(
            points = pattern.days.map { day ->
                val amount = money.format(day.average)
                val name = dayNames.getValue(day.day)
                ResumenPoint(
                    ChartPoint(
                        day.day.name,
                        dayInitials.getValue(day.day),
                        day.average.minorUnits,
                        "$name, ${amount.spoken}",
                        if (day.day == pattern.busiest) ChartPeriod.Current else ChartPeriod.Past
                    ),
                    name,
                    amount
                )
            },
            busiest = busiest?.let { dayNames.getValue(it.day) },
            busiestAverage = busiest?.let { money.format(it.average) },
            from = pattern.from.format(sentenceDate),
            lastSixMonths = pattern.lastSixMonths
        )
    }

    private fun monthName(month: YearMonth) = month.format(monthNameFormat).lowercase(locale)

    private fun monthTitle(month: YearMonth) = monthName(month).replaceFirstChar {
        it.titlecase(locale)
    }

    /** Tenths of a per cent as written: "11,1". */
    private fun percent(tenths: BigInteger): String =
        BigDecimal(tenths.abs(), 1).setScale(1).toPlainString().replace('.', ',')

    /** How much of a bar to fill: geometry only, never shown or turned back into money. */
    private fun share(part: Long, whole: Long): Float = when {
        whole <= 0 || part <= 0 -> 0f
        part >= whole -> 1f
        else -> (part.toDouble() / whole.toDouble()).toFloat()
    }

    private companion object {
        val locale: Locale = Locale.forLanguageTag("es-CO")
        val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM uuuu", locale)
        val monthNameFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL", locale)
        val sentenceDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", locale)
        val sentenceDateWithYear: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", locale)
        val dayInitials = mapOf(
            DayOfWeek.MONDAY to "L",
            DayOfWeek.TUESDAY to "M",
            DayOfWeek.WEDNESDAY to "X",
            DayOfWeek.THURSDAY to "J",
            DayOfWeek.FRIDAY to "V",
            DayOfWeek.SATURDAY to "S",
            DayOfWeek.SUNDAY to "D"
        )
        val dayNames = mapOf(
            DayOfWeek.MONDAY to "lunes",
            DayOfWeek.TUESDAY to "martes",
            DayOfWeek.WEDNESDAY to "miércoles",
            DayOfWeek.THURSDAY to "jueves",
            DayOfWeek.FRIDAY to "viernes",
            DayOfWeek.SATURDAY to "sábados",
            DayOfWeek.SUNDAY to "domingos"
        )
        val axisMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", locale)
    }
}

/**
 * A column's or the limit line's short label, for reading the chart at a glance:
 * "999", "43 mil", "4,31 M", half up. Only drawn: what is selected or spoken is exact.
 */
internal fun chartLabel(pesos: Long): String {
    if (pesos < 1_000) return pesos.toString()
    val thousands = BigDecimal(pesos).divide(BigDecimal(1_000), 0, RoundingMode.HALF_UP)
    if (thousands < BigDecimal(1_000)) return "$thousands mil"
    val millions = BigDecimal(pesos).divide(BigDecimal(1_000_000), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString().replace('.', ',')
    return "$millions M"
}
