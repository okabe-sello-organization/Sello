package com.software.sello.feature.resumen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartReference
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.ComparisonSlip
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.SpendingRankRow
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.SuggestionNote
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.component.selloWindowLayout
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.presentation.category.CategoryIcons

@Composable
fun ResumenScreen(
    state: ResumenState,
    onAction: (ResumenAction) -> Unit,
    onIncome: () -> Unit,
    onCategory: (String) -> Unit,
    onExpense: () -> Unit,
    padding: PaddingValues,
    onAdjustLimit: (String) -> Unit = {},
    onSchedules: () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = selloWindowLayout(maxWidth, maxHeight)
        // The scaffold's padding already sets the side margins, as on Recibo.
        val content = Modifier.fillMaxSize().padding(padding)
            .padding(vertical = SelloTheme.spacing.lg)
        when (state) {
            ResumenState.Loading -> ResumenPane(content) {
                SlipSkeleton(Modifier.testTag("resumen.loading"))
            }

            ResumenState.Failed -> ResumenPane(content) {
                ErrorSlip(
                    stringResource(R.string.resumen_load_failed),
                    { onAction(ResumenAction.Retry) },
                    Modifier.testTag("resumen.failed")
                )
            }

            is ResumenState.Ready -> {
                if (layout.twoPane && maxWidth >= ResumenTwoPaneWidth) {
                    Row(
                        content,
                        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
                    ) {
                        ResumenPane(Modifier.weight(1f)) {
                            SummarySlips(state, onIncome, onSchedules, onAction)
                        }
                        ResumenPane(Modifier.weight(1f)) {
                            ChartSlips(state, onAction, onCategory, onExpense, onAdjustLimit)
                        }
                    }
                } else {
                    ResumenPane(content) {
                        SummarySlips(state, onIncome, onSchedules, onAction)
                        ChartSlips(state, onAction, onCategory, onExpense, onAdjustLimit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumenPane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg),
        content = content
    )
}

/**
 * How the month compares, then income as receipt lines: what came in, what is left
 * after spending and what no category budget takes, then the way to income history.
 * Nothing is projected.
 */
@Composable
private fun SummarySlips(
    state: ResumenState.Ready,
    onIncome: () -> Unit,
    onSchedules: () -> Unit,
    onAction: (ResumenAction) -> Unit
) {
    if (state.stale) {
        ErrorSlip(
            stringResource(R.string.resumen_stale, state.asOf),
            { onAction(ResumenAction.Retry) },
            Modifier.testTag("resumen.stale")
        )
    }
    Hero(state.hero, state.insufficientHistory)
    Slip(Modifier.fillMaxWidth().testTag("resumen.flow")) {
        ResumenHeading(stringResource(R.string.resumen_income_title))
        LeaderLine(
            stringResource(R.string.resumen_income_recorded),
            onClick = onIncome,
            modifier = Modifier.testTag("resumen.income")
        ) { MoneyText(state.income) }
        LeaderLine(stringResource(R.string.resumen_net_flow)) { MoneyText(state.netFlow) }
        LeaderLine(
            stringResource(R.string.resumen_unallocated_line),
            modifier = Modifier.testTag("resumen.unallocated")
        ) { MoneyText(state.unallocated) }
        ResumenNote(stringResource(R.string.resumen_actual_through, state.asOf))
        ResumenLink(stringResource(R.string.resumen_open_income_destination), onIncome)
        ResumenLink(stringResource(R.string.schedules_title), onSchedules)
    }
}

/** A brand text link with a chevron, at least 48dp tall. */
@Composable
private fun ResumenLink(label: String, onClick: () -> Unit) {
    val ink = SelloTheme.colors.brand
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = SelloTheme.spacing.sm)
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("resumen.openIncome")
    ) {
        Text(label, fontWeight = FontWeight.W800, color = ink)
        Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(20.dp), tint = ink)
    }
}

/**
 * Month by month, where the money goes, which days cost most and, at most, one
 * suggestion. The month in progress is striped, marked and said to be up to today,
 * never drawn as a whole month.
 */
@Composable
private fun ChartSlips(
    state: ResumenState.Ready,
    onAction: (ResumenAction) -> Unit,
    onCategory: (String) -> Unit,
    onExpense: () -> Unit,
    onAdjustLimit: (String) -> Unit
) {
    if (state.empty) {
        EmptySlip(
            stringResource(R.string.resumen_empty),
            stringResource(R.string.resumen_empty_body),
            Modifier.fillMaxWidth().testTag("resumen.empty"),
            stringResource(R.string.resumen_record_expense),
            onExpense
        )
        return
    }
    Slip(Modifier.fillMaxWidth().testTag("resumen.history")) {
        ResumenHeading(stringResource(R.string.resumen_month_by_month))
        state.normalMonth?.let { normal ->
            Row(
                Modifier.fillMaxWidth().testTag("resumen.normal"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.resumen_normal_month))
                MoneyText(
                    normal.amount,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.W900
                    )
                )
            }
            Text(
                pluralStringResource(
                    R.plurals.resumen_normal_month_basis,
                    normal.months,
                    normal.months
                ),
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
        }
        val selected = state.history.firstOrNull { it.chart.id == state.selectedHistory }
        selected?.let { point ->
            Text(point.label)
            MoneyText(
                point.amount,
                style = MoneyStyle.Title,
                modifier = Modifier.testTag("resumen.history.selected")
            )
            point.detail?.let { limit ->
                LeaderLine(stringResource(R.string.resumen_month_limit)) { MoneyText(limit) }
            }
        }
        val inProgress = stringResource(R.string.resumen_month_in_progress)
        val highest = state.history.maxOfOrNull { it.chart.value }
        val limitLabel = state.monthLimit?.let {
            stringResource(R.string.resumen_limit_line, chartLabel(it.value))
        }
        val limitSpoken = state.monthLimit?.let {
            stringResource(R.string.resumen_limit_line, it.amount.spoken)
        }
        ChartWidth(state.history.size, "resumen.history.chart") { modifier ->
            ColumnsChart(
                state.history.map { point ->
                    point.chart.copy(
                        axisLabel = point.chart.axisLabel + if (point.current) "*" else "",
                        spoken = if (point.current) {
                            point.chart.spoken + ", " + inProgress
                        } else {
                            point.chart.spoken
                        },
                        // Short values, only on the highest and the current columns.
                        valueLabel = chartLabel(point.chart.value).takeIf {
                            point.current || point.chart.value == highest
                        }
                    )
                },
                stringResource(R.string.resumen_month_by_month),
                modifier,
                state.selectedHistory,
                { onAction(ResumenAction.SelectHistory(it)) },
                reference = state.monthLimit?.let {
                    ChartReference(it.value, limitLabel.orEmpty(), limitSpoken.orEmpty())
                },
                stripedCurrent = true
            )
        }
        if (state.history.any { it.current }) {
            Text(
                stringResource(R.string.resumen_in_progress_note),
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
        }
        if (state.insufficientHistory) Text(stringResource(R.string.resumen_insufficient_history))
    }
    if (state.categories.isNotEmpty()) {
        Slip(Modifier.fillMaxWidth().testTag("resumen.categories")) {
            ResumenHeading(stringResource(R.string.resumen_where_it_goes))
            Text(
                if (state.hero.inProgress) {
                    stringResource(
                        R.string.resumen_where_basis_in_progress,
                        state.hero.monthName,
                        state.hero.throughDay
                    )
                } else {
                    stringResource(R.string.resumen_where_basis, state.hero.monthName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
            state.categories.forEach { category ->
                SpendingRankRow(
                    name = category.name,
                    icon = CategoryIcons.iconFor(category.iconKey),
                    amount = category.amount,
                    share = category.share,
                    onClick = { onCategory(category.id) },
                    modifier = Modifier.testTag("resumen.category.${category.id}")
                )
            }
            TotalLine(stringResource(R.string.income_list_total)) {
                MoneyText(state.spent, style = MoneyStyle.Title)
            }
        }
    }
    state.weekdays?.let { Weekdays(it, state.selectedWeekday, onAction) }
    state.suggestion?.let { suggestion ->
        SuggestionNote(
            title = stringResource(R.string.resumen_suggestion_title),
            evidence = stringResource(
                R.string.resumen_suggestion_evidence,
                suggestion.name,
                suggestion.months.joinToString(stringResource(R.string.resumen_and)),
                suggestion.averageSpent.digits,
                suggestion.limit.digits
            ),
            advice = stringResource(R.string.resumen_suggestion_advice),
            actionLabel = stringResource(R.string.resumen_suggestion_action, suggestion.name),
            dismissLabel = stringResource(R.string.resumen_suggestion_dismiss),
            onAction = { onAdjustLimit(suggestion.categoryId) },
            onDismiss = { onAction(ResumenAction.DismissSuggestion) },
            modifier = Modifier.testTag("resumen.suggestion")
        )
    }
}

/**
 * "¿Cómo va este mes?", or how a finished month closed: the difference with the month
 * before over the same days, or whole months, with both amounts on one scale. With
 * nothing to compare, what was spent alone.
 */
@Composable
private fun Hero(hero: ResumenHero, firstMonth: Boolean) {
    val question = if (hero.inProgress) {
        stringResource(R.string.resumen_hero_question)
    } else {
        stringResource(R.string.resumen_hero_closed, hero.monthName)
    }
    val comparison = hero.comparison
    if (comparison == null) {
        AnswerSlip(
            label = question,
            amount = hero.spent,
            stamp = null,
            ink = StampInk.Neutral,
            modifier = Modifier.testTag("resumen.hero")
        ) {
            ResumenNote(
                if (hero.inProgress) {
                    stringResource(R.string.resumen_hero_spent_through, hero.throughDay)
                } else {
                    stringResource(R.string.resumen_hero_spent_in, hero.monthName)
                }
            )
            if (firstMonth && hero.inProgress) {
                ResumenNote(stringResource(R.string.resumen_hero_first_month))
            }
        }
        return
    }
    val previous = comparison.previousName
    val explanation = stringResource(
        when (comparison.direction) {
            SpendingDirection.More -> R.string.resumen_hero_more
            SpendingDirection.Less -> R.string.resumen_hero_less
            SpendingDirection.Same -> R.string.resumen_hero_same
        },
        previous
    )
    val arrow = when (comparison.direction) {
        SpendingDirection.More -> "↑ "
        SpendingDirection.Less -> "↓ "
        SpendingDirection.Same -> ""
    }
    val trend = comparison.percent?.let { percent ->
        arrow + if (comparison.throughDay != null) {
            stringResource(R.string.resumen_hero_trend_same_day, percent)
        } else {
            stringResource(R.string.resumen_hero_trend_whole, percent, previous)
        }
    }
    val days = comparison.throughDay
    val previousDays = comparison.previousThroughDay
    val basis = when {
        days == null || previousDays == null -> stringResource(R.string.resumen_hero_whole_months)

        days == previousDays -> stringResource(R.string.resumen_hero_same_days, days)

        else -> stringResource(
            R.string.resumen_hero_capped_days,
            days,
            hero.monthName,
            previousDays,
            previous
        )
    }
    ComparisonSlip(
        question = question,
        difference = comparison.difference,
        explanation = explanation,
        trend = trend,
        basis = basis,
        bars = comparison.bars,
        modifier = Modifier.testTag("resumen.hero")
    )
}

/** The answer first, when a day stands out, then each weekday's exact average. */
@Composable
private fun Weekdays(
    weekdays: ResumenWeekdays,
    selectedId: String?,
    onAction: (ResumenAction) -> Unit
) {
    Slip(Modifier.fillMaxWidth().testTag("resumen.weekdays")) {
        ResumenHeading(stringResource(R.string.resumen_weekdays_title))
        val selected = weekdays.points.firstOrNull { it.chart.id == selectedId }
        val busiest = weekdays.busiest
        val average = weekdays.busiestAverage
        when {
            selected != null -> {
                Text(selected.label.replaceFirstChar { it.titlecase() })
                MoneyText(
                    selected.amount,
                    style = MoneyStyle.Title,
                    modifier = Modifier.testTag("resumen.weekdays.selected")
                )
            }

            busiest != null && average != null -> {
                Text(
                    stringResource(R.string.resumen_weekdays_answer, busiest),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.W800,
                    modifier = Modifier.testTag("resumen.weekdays.answer")
                )
            }
        }
        val period = if (weekdays.lastSixMonths) {
            stringResource(R.string.resumen_weekdays_last_six_months)
        } else {
            stringResource(R.string.resumen_weekdays_since, weekdays.from)
        }
        ResumenNote(
            if (selected == null && average != null) {
                stringResource(R.string.resumen_weekdays_basis_busiest, average.digits, period)
            } else {
                stringResource(R.string.resumen_weekdays_basis, period)
            }
        )
        // Only the busiest day prints its value, short, above its column.
        ColumnsChart(
            weekdays.points.map { point ->
                point.chart.copy(
                    valueLabel = chartLabel(point.chart.value).takeIf {
                        point.chart.period == ChartPeriod.Current
                    }
                )
            },
            stringResource(R.string.resumen_weekdays_title),
            Modifier.fillMaxWidth(),
            selectedId,
            { onAction(ResumenAction.SelectWeekday(it)) },
            height = 120.dp
        )
    }
}

@Composable
private fun ResumenNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SelloTheme.colors.inkSoft)
}

@Composable
private fun ResumenHeading(text: String) {
    // v2 asks a small question over each slip's answer.
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.W600,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(bottom = 6.dp).semantics {
            heading()
        }
    )
}

@Composable
private fun ChartWidth(count: Int, tag: String, chart: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val width = maxOf(maxWidth, (count * 48).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            chart(Modifier.width(width))
        }
    }
}

private val ResumenTwoPaneWidth = 600.dp
