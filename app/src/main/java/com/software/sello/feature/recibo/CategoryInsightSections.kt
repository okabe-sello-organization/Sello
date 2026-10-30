package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.software.sello.R
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ChartReference
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun CategoryInsightSections(
    state: CategoryDetailState.Ready,
    months: MonthNames,
    dates: DateLabels,
    onAction: (CategoryDetailAction) -> Unit
) {
    when (val insights = state.insights) {
        DetailInsightsState.Loading -> SlipSkeleton(Modifier.testTag("detail:insights-loading"))

        DetailInsightsState.Failed -> ErrorSlip(
            stringResource(R.string.category_detail_insights_failed),
            {
                onAction(CategoryDetailAction.Retry)
            },
            modifier = Modifier.testTag("detail:insights-failed")
        )

        is DetailInsightsState.Ready -> {
            val figures = insights.figures
            if (state.category.expenseCount > 0) {
                CategoryNumbers(state, figures, months, dates)
                CategoryWeeks(state.category.id, figures)
            }
            if (figures.historicalAverage != null) CategoryHistory(state.category.id, figures)
        }
    }
}

@Composable
private fun CategoryNumbers(
    state: CategoryDetailState.Ready,
    figures: DetailInsights,
    months: MonthNames,
    dates: DateLabels
) {
    Slip(Modifier.fillMaxWidth().testTag("detail:numbers")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DetailHeading(stringResource(R.string.category_detail_numbers))
            SoftDetail(months.inSentence(state.month))
        }
        Spacer(Modifier.height(SelloTheme.spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
            ) {
                KeyValue(
                    stringResource(R.string.category_detail_expenses),
                    modifier = Modifier.weight(1f),
                    icon = SelloIcon.ReceiptLong
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.category_detail_count_days,
                            figures.elapsedDays,
                            countText(state.category.expenseCount),
                            figures.elapsedDays
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                figures.averageExpense?.let { value ->
                    KeyValue(
                        stringResource(R.string.category_detail_each_time),
                        modifier = Modifier.weight(1f),
                        icon = SelloIcon.Sell
                    ) {
                        MoneyText(value, textStyle = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
            ) {
                figures.largest?.let { expense ->
                    KeyValue(
                        stringResource(R.string.category_detail_largest),
                        modifier = Modifier.weight(1f),
                        icon = SelloIcon.ArrowUpward
                    ) {
                        MoneyText(expense.amount, textStyle = MaterialTheme.typography.labelLarge)
                        Text(
                            listOfNotNull(expense.note, dates.day(expense.date, state.today))
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = SelloTheme.colors.inkSoft
                        )
                    }
                }
                figures.weekday?.let { day ->
                    KeyValue(
                        stringResource(R.string.category_detail_costliest_day),
                        modifier = Modifier.weight(1f),
                        icon = SelloIcon.Event
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.FULL, DetailLocale).replaceFirstChar {
                                it.titlecase(DetailLocale)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryWeeks(categoryId: String, figures: DetailInsights) {
    var selected by rememberSaveable(categoryId, figures.weeks.firstOrNull()?.from) {
        mutableStateOf<Int?>(null)
    }
    Slip(Modifier.fillMaxWidth().testTag("detail:weeks")) {
        ChartDetail(stringResource(R.string.category_detail_weekly), heading = true)
        figures.weeklyAverage?.let {
            MoneyText(it, textStyle = MaterialTheme.typography.headlineSmall)
        }
            ?: SoftDetail(stringResource(R.string.category_detail_no_complete_week))
        Spacer(Modifier.height(SelloTheme.spacing.sm))
        val points = figures.weeks.map { week ->
            val label = if (week.current) {
                stringResource(R.string.category_detail_this_week)
            } else {
                stringResource(R.string.category_detail_week_number, week.number)
            }
            val range =
                stringResource(
                    R.string.category_detail_day_range,
                    week.from.dayOfMonth,
                    week.until.dayOfMonth
                )
            val spent =
                stringResource(R.string.category_detail_week_spoken, range, week.spent.spoken)
            val spoken = week.allowance?.let {
                stringResource(R.string.category_detail_week_allowance_spoken, spent, it.spoken)
            } ?: spent
            ChartPoint(
                week.number.toString(),
                label,
                week.ceiling,
                spoken,
                if (week.current) ChartPeriod.Current else ChartPeriod.Past,
                stripedFromValue = week.value.takeIf { week.allowance != null },
                // Where the label does not fit its column, the week number alone, the current week included.
                shortAxisLabel = week.number.toString()
            )
        }
        ColumnsChart(
            points,
            stringResource(R.string.category_detail_week_chart),
            selectedId = selected?.toString(),
            onSelect = { selected = it.toInt() }
        )
        if (figures.weeks.any { it.allowance != null }) {
            ChartDetail(stringResource(R.string.category_detail_week_legend))
        }
        figures.weeks.firstOrNull { it.number == selected }?.let { week ->
            LeaderLine(
                stringResource(
                    R.string.category_detail_day_range,
                    week.from.dayOfMonth,
                    week.until.dayOfMonth
                )
            ) {
                MoneyText(week.spent)
            }
            week.allowance?.let { value ->
                LeaderLine(stringResource(R.string.category_detail_week_available)) {
                    MoneyText(value)
                }
            }
        }
    }
}

@Composable
private fun CategoryHistory(categoryId: String, figures: DetailInsights) {
    var selected by rememberSaveable(categoryId, figures.history.lastOrNull()?.month) {
        mutableStateOf<String?>(null)
    }
    Slip(Modifier.fillMaxWidth().testTag("detail:history")) {
        val completedMonths = figures.history.count { !it.current }
        ChartDetail(stringResource(R.string.category_detail_normal_month), heading = true)
        MoneyText(
            requireNotNull(figures.historicalAverage),
            textStyle = MaterialTheme.typography.headlineSmall
        )
        SoftDetail(
            pluralStringResource(
                R.plurals.category_detail_history_average,
                completedMonths,
                completedMonths
            )
        )
        val points = figures.history.map { entry ->
            val period = if (entry.current) {
                stringResource(
                    R.string.category_detail_history_current,
                    entry.month.format(HistoryMonth)
                )
            } else {
                entry.month.format(HistoryMonth)
            }
            val spentSpoken =
                stringResource(R.string.category_detail_history_spoken, period, entry.spent.spoken)
            val carry = entry.carry
            val spoken = when {
                entry.limit != null && carry != null -> stringResource(
                    R.string.category_detail_history_carry_spoken,
                    spentSpoken,
                    entry.limit.spoken,
                    carry.base.spoken,
                    carry.carry.spoken
                )

                entry.limit != null -> stringResource(
                    R.string.category_detail_history_limit_spoken,
                    spentSpoken,
                    entry.limit.spoken
                )

                else -> spentSpoken
            }
            ChartPoint(
                entry.month.toString(),
                entry.month.format(AxisMonth),
                entry.value,
                spoken,
                if (entry.limitValue != null &&
                    entry.value > entry.limitValue
                ) {
                    ChartPeriod.Over
                } else if (entry.current) {
                    ChartPeriod.Current
                } else {
                    ChartPeriod.Past
                },
                referenceValue = entry.limitValue
            )
        }
        val commonLimit = figures.history.first().limitValue?.takeIf { value ->
            figures.history.all {
                it.limitValue ==
                    value
            }
        }
        ColumnsChart(
            points,
            stringResource(R.string.category_detail_history_chart),
            selectedId = selected,
            onSelect = { selected = it },
            reference = commonLimit?.let {
                ChartReference(it, stringResource(R.string.category_detail_chart_limit))
            }
        )
        if (figures.history.any {
                it.limit != null
            }
        ) {
            Spacer(Modifier.height(SelloTheme.spacing.sm))
            ChartDetail(
                stringResource(
                    if (figures.history.any { it.carry != null }) {
                        R.string.category_detail_history_carry_legend
                    } else {
                        R.string.category_detail_history_limit_legend
                    }
                )
            )
        }
        figures.history.firstOrNull { it.month.toString() == selected }?.let { entry ->
            LeaderLine(entry.month.format(HistoryMonth)) { MoneyText(entry.spent) }
            val carry = entry.carry
            if (entry.limit != null && carry != null) {
                // Never the capacity alone: base and carry beside it (CP27).
                LeaderLine(stringResource(R.string.carry_base)) { MoneyText(carry.base) }
                LeaderLine(stringResource(R.string.carry_in)) { MoneyText(carry.carry) }
                LeaderLine(stringResource(R.string.carry_capacity)) { MoneyText(entry.limit) }
            } else {
                entry.limit?.let { value ->
                    LeaderLine(stringResource(R.string.category_detail_limit)) { MoneyText(value) }
                }
            }
        }
    }
}

@Composable
internal fun SoftDetail(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = SelloTheme.colors.inkSoft)

@Composable
private fun ChartDetail(text: String, heading: Boolean = false) = Text(
    text,
    style = if (heading) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Normal
        )
    },
    color = SelloTheme.colors.inkSoft,
    modifier = Modifier.semantics { if (heading) heading() }
)

@Composable
private fun DetailHeading(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.semantics {
        heading()
    }
)

private val DetailLocale = Locale.forLanguageTag("es-CO")
private val HistoryMonth = DateTimeFormatter.ofPattern("MMM uuuu", DetailLocale)
private val AxisMonth = DateTimeFormatter.ofPattern("MMM", DetailLocale)
