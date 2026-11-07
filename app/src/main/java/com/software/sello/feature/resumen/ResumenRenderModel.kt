package com.software.sello.feature.resumen

import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.WeekdayPattern
import com.software.sello.domain.policy.SmallExpensePolicy
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.presentation.carry.carryFigures
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.month.MonthNames
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [labels] writes months as the rest of the app does: "sep 2026", never "sept.". */
class ResumenRenderModel(
    private val money: MoneyFormatter,
    private val labels: DateLabels,
    private val months: MonthNames
) {
    /**
     * [current] is the financial month in progress; a date from another year shows its year.
     * [line] is Gastos hormiga's line as last read.
     */
    fun render(
        state: ResumenSnapshotState,
        current: YearMonth,
        line: PreferenceRead<SmallExpenseLine>
    ): ResumenState = when (state) {
        ReadState.Loading -> ResumenState.Loading

        is ReadState.Ready -> month(state.value, false, current, line)

        is ReadState.Failed -> state.lastGood?.let { month(it, true, current, line) }
            ?: ResumenState.Failed
    }

    private fun month(
        snapshot: ResumenSnapshot,
        stale: Boolean,
        current: YearMonth,
        line: PreferenceRead<SmallExpenseLine>
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
                val label = labels.monthYear(entry.month)
                val amount = money.format(entry.spent)
                val limit = entry.limit
                // The month in progress is always drawn as such, over its limit or not:
                // it is never a whole month.
                val current = entry.month == YearMonth.from(budget.asOf)
                ResumenPoint(
                    ChartPoint(
                        entry.month.toString(),
                        labels.month(entry.month),
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
                    current,
                    entry.carry?.let { money.carryFigures(it, entry.month, spent = null) }
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
            ),
            smallExpenses = smallExpenses(snapshot, line)
        )
    }

    /**
     * Gastos hormiga with the line read, or the last one read when it cannot be read again.
     * With no line ever read, a notice where a slip could be: 5 or more expenses at or
     * under the widest line.
     */
    private fun smallExpenses(
        snapshot: ResumenSnapshot,
        read: PreferenceRead<SmallExpenseLine>
    ): ResumenSmallExpenses? {
        val (line, older) = when (read) {
            is PreferenceRead.Ready -> read.value to false
            is PreferenceRead.Failed -> (read.lastGood ?: return unreadableLine(snapshot)) to true
        }
        val summary = when (val worked = SmallExpensePolicy.summary(snapshot, line)) {
            is Outcome.Success -> worked.value ?: return null
            is Outcome.Failure -> return ResumenSmallExpenses.Unreadable
        }
        val budget = snapshot.budget
        val inProgress = budget.month == YearMonth.from(budget.asOf)
        val previous = budget.month.minusMonths(1)
        val throughDay = budget.asOf.dayOfMonth.takeIf { inProgress }
        return ResumenSmallExpenses.Shown(
            line = money.format(line.money).digits,
            total = money.format(summary.total),
            count = summary.count,
            sharePercent = summary.sharePercent,
            previousName = monthName(previous),
            bars = summary.previousTotal?.let { before ->
                // Both on one scale: the larger fills the bar.
                val largest = maxOf(summary.total.minorUnits, before.minorUnits)
                listOf(
                    ComparisonBar(
                        monthTitle(budget.month),
                        money.format(summary.total),
                        share(summary.total.minorUnits, largest),
                        current = true
                    ),
                    ComparisonBar(
                        monthTitle(previous),
                        money.format(before),
                        share(before.minorUnits, largest),
                        current = false
                    )
                )
            },
            throughDay = throughDay,
            previousThroughDay = throughDay?.let { minOf(it, previous.lengthOfMonth()) },
            categories = summary.categories.map {
                ResumenSmallCategory(
                    it.category.id.value,
                    it.category.name.value,
                    it.category.icon.value,
                    it.count,
                    money.format(it.total)
                )
            },
            olderLine = older
        )
    }

    private fun unreadableLine(snapshot: ResumenSnapshot): ResumenSmallExpenses? = when (
        val widest = SmallExpensePolicy.tally(snapshot.periods.current, SmallExpenseLine.WIDEST)
    ) {
        is Outcome.Failure -> ResumenSmallExpenses.Unreadable

        is Outcome.Success ->
            ResumenSmallExpenses.Unreadable
                .takeIf { widest.value.count >= SmallExpensePolicy.MIN_COUNT }
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

    private fun monthName(month: YearMonth) = months.inSentence(month)

    private fun monthTitle(month: YearMonth) = months.name(month)

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
