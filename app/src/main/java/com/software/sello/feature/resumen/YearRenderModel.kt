package com.software.sello.feature.resumen

import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.YearComparison
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.YearSnapshotState
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.month.MonthNames
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tu año's figures as shown. Every amount is exact; bars and columns are geometry only. */
class YearRenderModel(private val money: MoneyFormatter, private val months: MonthNames) {
    fun render(year: Int, state: YearSnapshotState): YearState = when (state) {
        ReadState.Loading -> YearState.Loading(year)

        is ReadState.Ready -> year(state.value, stale = false)

        is ReadState.Failed -> state.lastGood?.let { year(it, stale = true) }
            ?: YearState.Failed(year)
    }

    private fun year(snapshot: YearSnapshot, stale: Boolean): YearState.Ready {
        val inProgress = snapshot.year == snapshot.asOf.year
        val highest = snapshot.months.maxOfOrNull { it.spent?.minorUnits ?: 0L } ?: 0L
        val finished = snapshot.months.mapNotNull { it.status as? YearMonthStatus.Finished }
        val largest = snapshot.categories.firstOrNull()?.spent?.minorUnits ?: 0L
        return YearState.Ready(
            year = snapshot.year,
            canPrevious = snapshot.year > snapshot.firstYear,
            canNext = snapshot.year < snapshot.asOf.year,
            hero = YearHero(
                inProgress = inProgress,
                spent = money.format(snapshot.spent),
                through = snapshot.asOf.format(sentenceDate),
                comparison = snapshot.comparison?.let { comparison(it, snapshot.year) }
            ),
            months = snapshot.months.map { line ->
                val label = "${months.inSentence(line.month)} de ${line.month.year}"
                val amount = line.spent?.let(money::format)
                val status = line.status
                YearColumn(
                    ChartPoint(
                        id = line.month.toString(),
                        axisLabel = months.name(line.month).take(1),
                        // A month not yet begun is an outline the height of the tallest.
                        value = line.spent?.minorUnits ?: highest,
                        spoken = amount?.let { "$label, ${it.spoken}" } ?: label,
                        period = when (status) {
                            YearMonthStatus.Upcoming -> ChartPeriod.Upcoming
                            YearMonthStatus.InProgress -> ChartPeriod.Current
                            is YearMonthStatus.Finished -> ChartPeriod.Past
                        }
                    ),
                    label = label,
                    amount = amount,
                    mark = when ((status as? YearMonthStatus.Finished)?.verdict) {
                        PastVerdict.WithinLimit -> YearMark.Within
                        PastVerdict.Over -> YearMark.Over
                        null -> YearMark.None
                    }
                )
            },
            monthInProgress = if (inProgress) {
                months.inSentence(YearMonth.from(snapshot.asOf))
            } else {
                null
            },
            withinMonths = finished.count { it.verdict == PastVerdict.WithinLimit },
            overMonths = finished.count { it.verdict == PastVerdict.Over },
            income = money.format(snapshot.income),
            spent = money.format(snapshot.spent),
            netFlow = money.format(snapshot.netFlow),
            overspent = snapshot.netFlow.minorUnits < 0,
            categories = snapshot.categories.map {
                YearCategoryRow(
                    it.category.id.value,
                    it.category.name.value,
                    it.category.icon.value,
                    money.format(it.spent),
                    it.percent,
                    share(it.spent.minorUnits, largest)
                )
            },
            empty = snapshot.expenseCount == 0 && snapshot.incomeCount == 0,
            stale = stale,
            asOf = snapshot.asOf.format(
                if (inProgress) sentenceDate else sentenceDateWithYear
            )
        )
    }

    private fun comparison(comparison: YearComparison, year: Int): YearHeroComparison {
        // Both on one scale: the larger fills the bar.
        val largest = maxOf(comparison.spent.minorUnits, comparison.previousSpent.minorUnits)
        val capped = comparison.previousThrough.dayOfMonth != comparison.through.dayOfMonth
        return YearHeroComparison(
            previousYear = comparison.previousYear,
            difference = money.format(comparison.difference),
            direction = comparison.direction,
            percent = comparison.percentTenths?.let(::percentText),
            wholeYears = comparison.wholeYears,
            previousThrough = comparison.previousThrough.takeIf { capped }?.let(::withYear),
            bars = listOf(
                ComparisonBar(
                    year.toString(),
                    money.format(comparison.spent),
                    share(comparison.spent.minorUnits, largest),
                    current = true
                ),
                ComparisonBar(
                    comparison.previousYear.toString(),
                    money.format(comparison.previousSpent),
                    share(comparison.previousSpent.minorUnits, largest),
                    current = false
                )
            )
        )
    }

    private fun withYear(date: LocalDate) = date.format(sentenceDateWithYear)

    private companion object {
        val locale: Locale = Locale.forLanguageTag("es-CO")
        val sentenceDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", locale)
        val sentenceDateWithYear: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", locale)
    }
}
