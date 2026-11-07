package com.software.sello.feature.recibo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ChartReference
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.carry.CarryBreakdownSection
import com.software.sello.presentation.carry.carryLabel
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun CategoryInsightSections(
    state: CategoryDetailState.Ready,
    months: MonthNames,
    dates: DateLabels,
    onAction: (CategoryDetailAction) -> Unit,
    onShowRepeated: (note: String) -> Unit
) {
    when (val insights = state.insights) {
        DetailInsightsState.Loading -> SlipSkeleton(Modifier.testTag("detail:insights-loading"))

        DetailInsightsState.Failed -> StatusSlip(
            kind = StatusKind.LoadFailed(),
            message = stringResource(R.string.category_detail_insights_failed),
            modifier = Modifier.testTag("detail:insights-failed"),
            action = StatusAction(StatusSlipDefaults.retryLabel) {
                onAction(CategoryDetailAction.Retry)
            }
        )

        is DetailInsightsState.Ready -> {
            val figures = insights.figures
            if (figures.repeats.isNotEmpty()) {
                CategoryRepeats(state.month, figures.repeats, months, onShowRepeated)
            }
            if (state.category.expenseCount > 0) {
                CategoryNumbers(state, figures, months, dates)
                CategoryWeeks(state.category.id, figures)
            }
            if (figures.historicalAverage != null) {
                CategoryHistory(state.category.id, figures, dates)
            }
        }
    }
}

/** Lo que más se repite (SELLO-195): a small table, each row opening its note's expenses. */
@Composable
private fun CategoryRepeats(
    month: YearMonth,
    repeats: List<DetailRepeat>,
    months: MonthNames,
    onShow: (note: String) -> Unit
) {
    Slip(Modifier.fillMaxWidth().testTag(CATEGORY_DETAIL_REPEATS_TAG)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DetailHeading(stringResource(R.string.category_detail_repeats))
            SoftDetail(months.inSentence(month))
        }
        Spacer(Modifier.height(SelloTheme.spacing.sm))
        val column = MaterialTheme.typography.labelMedium.copy(
            fontWeight = SelloTheme.type.weight.strong
        )
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Text(
                stringResource(R.string.category_detail_repeats_note_col),
                Modifier.weight(1f),
                style = column,
                color = SelloTheme.colors.inkSoft
            )
            Text(
                stringResource(R.string.category_detail_repeats_times_col),
                Modifier.width(RepeatCountWidth),
                style = column,
                color = SelloTheme.colors.inkSoft,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.category_detail_repeats_total_col),
                Modifier.widthIn(min = RepeatTotalWidth),
                style = column,
                color = SelloTheme.colors.inkSoft,
                textAlign = TextAlign.End
            )
        }
        DashedRule()
        val open = stringResource(R.string.category_detail_repeat_open)
        repeats.forEachIndexed { index, repeat ->
            val spoken = pluralStringResource(
                R.plurals.category_detail_repeat_spoken,
                repeat.count,
                repeat.note,
                countText(repeat.count),
                repeat.total.spoken
            )
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = SelloTheme.spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClickLabel = open) { onShow(repeat.note) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .testTag(categoryRepeatTag(index)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    repeat.note,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    countText(repeat.count),
                    Modifier.width(RepeatCountWidth),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = SelloTheme.type.weight.emphasis
                    ),
                    textAlign = TextAlign.Center
                )
                Box(
                    Modifier.widthIn(min = RepeatTotalWidth),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MoneyText(repeat.total, textStyle = MaterialTheme.typography.labelLarge)
                }
            }
            // A line under every row, the last included, as the mock draws it.
            HorizontalDivider(color = SelloTheme.colors.paperAlt)
        }
        Spacer(Modifier.height(SelloTheme.spacing.sm))
        ChartDetail(stringResource(R.string.category_detail_repeats_note))
    }
}

/** The mock's dashed rule under the column headers. */
@Composable
private fun DashedRule() {
    val rule = SelloTheme.colors.rule
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = rule,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
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
    Slip(Modifier.fillMaxWidth().testTag(CATEGORY_DETAIL_WEEKS_TAG)) {
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

/** [dates] writes months as the rest of the app does: "sep 2026", never the device's "sept.". */
@Composable
private fun CategoryHistory(categoryId: String, figures: DetailInsights, dates: DateLabels) {
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
                    dates.monthYear(entry.month)
                )
            } else {
                dates.monthYear(entry.month)
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
                dates.month(entry.month),
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
            LeaderLine(dates.monthYear(entry.month)) { MoneyText(entry.spent) }
            val carry = entry.carry
            if (entry.limit != null && carry != null) {
                // Never the capacity alone: base and carry beside it (CP27).
                CarryBreakdownSection(
                    carry,
                    footnote = null,
                    tag = CATEGORY_DETAIL_HISTORY_CARRY_TAG
                )
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
            fontWeight = SelloTheme.type.weight.regular
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

private val RepeatCountWidth = 52.dp
private val RepeatTotalWidth = 96.dp

private val DetailLocale = Locale.forLanguageTag("es-CO")

const val CATEGORY_DETAIL_HISTORY_CARRY_TAG = "detail:history-carry"
const val CATEGORY_DETAIL_REPEATS_TAG = "detail:repeats"

fun categoryRepeatTag(index: Int) = "detail:repeat:$index"
