package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloIconButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.presentation.category.CategoryIcons

/**
 * Tu año: the year's expenses against the year before on the same days, how each month
 * closed, what came in and where it went. Nothing is projected.
 */
@Composable
fun YearScreen(state: YearState, onAction: (YearAction) -> Unit, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(vertical = SelloTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) {
        val ready = state as? YearState.Ready
        YearSwitcher(
            state.year,
            canPrevious = ready?.canPrevious == true,
            canNext = ready?.canNext == true,
            onAction
        )
        when (state) {
            is YearState.Loading -> SlipSkeleton(Modifier.testTag("year.loading"))

            is YearState.Failed -> StatusSlip(
                kind = StatusKind.LoadFailed(),
                message = stringResource(R.string.year_load_failed),
                modifier = Modifier.testTag("year.failed"),
                action = StatusAction(StatusSlipDefaults.retryLabel) {
                    onAction(YearAction.Retry)
                }
            )

            is YearState.Ready -> YearSlips(state, onAction)
        }
    }
}

@Composable
private fun YearSlips(state: YearState.Ready, onAction: (YearAction) -> Unit) {
    if (state.stale) {
        StatusSlip(
            kind = StatusKind.Older,
            message = stringResource(R.string.resumen_stale, state.asOf),
            modifier = Modifier.testTag("year.stale"),
            action = StatusAction(StatusSlipDefaults.retryLabel) { onAction(YearAction.Retry) }
        )
    }
    if (state.empty) {
        StatusSlip(
            kind = StatusKind.Empty(
                SelloIcon.Insights,
                stringResource(R.string.year_empty, state.year)
            ),
            message = stringResource(R.string.year_empty_body),
            modifier = Modifier.fillMaxWidth().testTag("year.empty")
        )
        return
    }
    YearHeroSlip(state.year, state.hero)
    MonthByMonth(state, onAction)
    IncomeSlip(state)
    if (state.categories.isNotEmpty()) Categories(state, onAction)
    Records(state.records)
}

/** ‹ 2026 ›: from the first year with records to the current one. */
@Composable
private fun YearSwitcher(
    year: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    onAction: (YearAction) -> Unit
) {
    Slip(Modifier.fillMaxWidth().testTag("year.switcher")) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelloIconButton(
                SelloIcon.ChevronLeft,
                stringResource(R.string.year_previous),
                { onAction(YearAction.Previous) },
                Modifier.testTag("year.previous"),
                enabled = canPrevious,
                tint = if (canPrevious) SelloTheme.colors.brand else SelloTheme.colors.outline
            )
            Text(
                year.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = SelloTheme.type.weight.emphasis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = SelloTheme.spacing.lg)
                    .semantics { heading() }.testTag("year.shown")
            )
            SelloIconButton(
                SelloIcon.ChevronRight,
                stringResource(R.string.year_next),
                { onAction(YearAction.Next) },
                Modifier.testTag("year.next"),
                enabled = canNext,
                tint = if (canNext) SelloTheme.colors.brand else SelloTheme.colors.outline
            )
        }
    }
}

/**
 * The year's expenses. Against the year before, the difference and its rate with both
 * amounts on one scale; with nothing to compare, what was spent alone.
 */
@Composable
private fun YearHeroSlip(year: Int, hero: YearHero) {
    val question = if (hero.inProgress) {
        stringResource(R.string.year_hero_question)
    } else {
        stringResource(R.string.year_hero_closed, year)
    }
    val comparison = hero.comparison
    if (comparison == null) {
        AnswerSlip(
            label = question,
            amount = hero.spent,
            stamp = null,
            ink = StampInk.Neutral,
            modifier = Modifier.testTag("year.hero")
        ) {
            ResumenNote(
                if (hero.inProgress) {
                    stringResource(R.string.year_hero_spent_through, hero.through)
                } else {
                    stringResource(R.string.year_hero_spent_in, year)
                }
            )
        }
        return
    }
    val previous = comparison.previousYear
    val explanation = when (comparison.direction) {
        SpendingDirection.More ->
            stringResource(R.string.year_hero_more, comparison.difference.digits, previous)

        SpendingDirection.Less ->
            stringResource(R.string.year_hero_less, comparison.difference.digits, previous)

        SpendingDirection.Same -> stringResource(R.string.year_hero_same, previous)
    }
    val arrow = when (comparison.direction) {
        SpendingDirection.More -> "↑ "
        SpendingDirection.Less -> "↓ "
        SpendingDirection.Same -> ""
    }
    val trend = comparison.percent?.let { percent ->
        arrow + if (comparison.wholeYears) {
            stringResource(R.string.year_hero_trend_whole, percent, previous.toString())
        } else {
            stringResource(R.string.year_hero_trend_same_day, percent)
        }
    }
    val basis = when {
        comparison.wholeYears -> stringResource(R.string.year_hero_whole_years)

        comparison.previousThrough != null -> stringResource(
            R.string.year_hero_capped_days,
            hero.through,
            year,
            comparison.previousThrough
        )

        else -> stringResource(R.string.year_hero_same_days, hero.through)
    }
    ComparisonSlip(
        question = question,
        difference = hero.spent,
        explanation = explanation,
        trend = trend,
        basis = basis,
        bars = comparison.bars,
        modifier = Modifier.testTag("year.hero")
    )
}

/**
 * Twelve columns: finished months with their verdict mark under them, the month in
 * progress in the brand ink and months to come as outlines. Then the count of verdicts.
 */
@Composable
private fun MonthByMonth(state: YearState.Ready, onAction: (YearAction) -> Unit) {
    val colors = SelloTheme.colors
    Slip(Modifier.fillMaxWidth().testTag("year.months")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ResumenHeading(stringResource(R.string.resumen_month_by_month))
            }
            state.monthInProgress?.let {
                ResumenNote(stringResource(R.string.year_month_in_progress, it))
            }
        }
        state.months.firstOrNull { it.chart.id == state.selectedMonth }?.let { column ->
            column.amount?.let { amount ->
                Text(column.label.replaceFirstChar { it.titlecase() })
                MoneyText(
                    amount,
                    style = MoneyStyle.Title,
                    modifier = Modifier.testTag("year.months.selected")
                )
            }
        }
        val inProgress = stringResource(R.string.resumen_month_in_progress)
        val within = stringResource(R.string.year_mark_within)
        val over = stringResource(R.string.year_mark_over)
        val upcoming = stringResource(R.string.year_month_upcoming)
        ColumnsChart(
            state.months.map { column ->
                val said = when {
                    column.amount == null -> upcoming
                    column.mark == YearMark.Within -> within
                    column.mark == YearMark.Over -> over
                    column.chart.period == ChartPeriod.Current -> inProgress
                    else -> null
                }
                column.chart.copy(spoken = listOfNotNull(column.chart.spoken, said).joinToString())
            },
            stringResource(R.string.resumen_month_by_month),
            Modifier.fillMaxWidth().testTag("year.months.chart"),
            state.selectedMonth,
            { onAction(YearAction.SelectMonth(it)) },
            height = 120.dp
        )
        // Each mark under its own column; spoken with the column above.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            state.months.forEach { column ->
                // The month in progress has no verdict yet: a dot, not a blank.
                val current = column.chart.period == ChartPeriod.Current
                Text(
                    when {
                        column.mark == YearMark.Within -> "✓"
                        column.mark == YearMark.Over -> "✗"
                        current -> "·"
                        else -> ""
                    },
                    color = when {
                        column.mark == YearMark.Over -> colors.semantic.loss
                        current -> colors.inkSoft
                        else -> colors.semantic.gain
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = SelloTheme.type.weight.emphasis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (state.withinMonths + state.overMonths > 0) {
            val withinText = pluralStringResource(
                R.plurals.year_marks_within,
                state.withinMonths,
                state.withinMonths
            )
            val overText = pluralStringResource(
                R.plurals.year_marks_over,
                state.overMonths,
                state.overMonths
            )
            val legend = stringResource(R.string.year_marks_legend)
            val weight = SelloTheme.type.weight.emphasis
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.semantic.gain, fontWeight = weight)) {
                        append(withinText)
                    }
                    append(" · ")
                    withStyle(SpanStyle(color = colors.semantic.loss, fontWeight = weight)) {
                        append(overText)
                    }
                    append(" · ")
                    append(legend)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag("year.marks")
            )
        }
    }
}

/** Lo que entró: the year's income, its expenses and Ingresos menos gastos, signed. */
@Composable
private fun IncomeSlip(state: YearState.Ready) {
    Slip(Modifier.fillMaxWidth().testTag("year.income")) {
        ResumenHeading(stringResource(R.string.year_income_title))
        LeaderLine(
            stringResource(R.string.year_income_in, state.year),
            Modifier.testTag("year.income.in")
        ) {
            MoneyText(state.income, color = SelloTheme.colors.semantic.gain)
        }
        LeaderLine(
            stringResource(R.string.resumen_income_spent),
            Modifier.testTag("year.income.spent")
        ) { MoneyText(state.spent) }
        TotalLine(
            stringResource(R.string.resumen_net_flow),
            Modifier.testTag("year.income.net")
        ) {
            MoneyText(
                state.netFlow,
                style = MoneyStyle.Title,
                color = if (state.overspent) SelloTheme.colors.semantic.loss else Color.Unspecified
            )
        }
    }
}

/** ¿En qué se fue?: the five largest with their share, then the way to all of them. */
@Composable
private fun Categories(state: YearState.Ready, onAction: (YearAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag("year.categories")) {
        ResumenHeading(stringResource(R.string.year_categories_title))
        state.shownCategories.forEach { category ->
            SpendingRankRow(
                name = category.name,
                icon = CategoryIcons.iconFor(category.iconKey),
                amount = category.amount,
                share = category.bar,
                onClick = null,
                modifier = Modifier.testTag("year.category.${category.id}"),
                percent = stringResource(R.string.year_category_percent, category.percent)
            )
        }
        if (state.shownCategories.size < state.categories.size) {
            SelloButton(
                text = pluralStringResource(
                    R.plurals.year_categories_all,
                    state.categories.size,
                    state.categories.size
                ),
                onClick = { onAction(YearAction.ShowAllCategories) },
                modifier = Modifier.testTag("year.categories.all"),
                kind = ButtonKind.Text,
                contentPadding = PaddingValues(0.dp),
                trailingIcon = SelloIcon.ChevronRight
            )
        }
    }
}

/**
 * Récords del año: two by two, each with its icon, what it is, its figure and what it was.
 * With fewer than two finished months, a line saying the records come later.
 */
@Composable
private fun Records(records: YearRecordSlip?) {
    Slip(Modifier.fillMaxWidth().testTag("year.records")) {
        ResumenHeading(stringResource(R.string.year_records_title))
        if (records == null) {
            Text(
                stringResource(R.string.year_records_too_few),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.testTag("year.records.too_few")
            )
            return@Slip
        }
        val cells: List<@Composable (Modifier) -> Unit> = listOfNotNull(
            { modifier ->
                RecordCell(
                    SelloIcon.TrendingUp,
                    stringResource(R.string.year_record_dearest),
                    { MoneyText(records.dearest.amount, style = MoneyStyle.Title) },
                    records.dearest.month,
                    modifier.testTag("year.records.dearest")
                )
            },
            { modifier ->
                RecordCell(
                    SelloIcon.TrendingDown,
                    stringResource(R.string.year_record_calmest),
                    { MoneyText(records.calmest.amount, style = MoneyStyle.Title) },
                    records.calmest.month,
                    modifier.testTag("year.records.calmest")
                )
            },
            { modifier ->
                RecordCell(
                    SelloIcon.ArrowUpward,
                    stringResource(R.string.year_record_biggest),
                    { MoneyText(records.biggest.amount, style = MoneyStyle.Title) },
                    stringResource(
                        R.string.year_record_biggest_detail,
                        records.biggest.label,
                        records.biggest.day
                    ),
                    modifier.testTag("year.records.biggest")
                )
            },
            records.mostOver?.let { over ->
                { modifier ->
                    RecordCell(
                        SelloIcon.Flag,
                        stringResource(R.string.year_record_most_over),
                        {
                            Text(
                                over.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = SelloTheme.type.weight.emphasis
                            )
                        },
                        pluralStringResource(
                            R.plurals.year_record_over_months,
                            over.months,
                            over.months
                        ),
                        modifier.testTag("year.records.most_over")
                    )
                }
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)) {
            cells.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
                    pair.forEach { cell -> cell(Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One record: read as a single sentence, label, figure and detail. */
@Composable
private fun RecordCell(
    icon: SelloIcon,
    label: String,
    value: @Composable () -> Unit,
    detail: String,
    modifier: Modifier
) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs)
        ) {
            Icon(icon.painter(), null, Modifier.size(16.dp), SelloTheme.colors.brand)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
        }
        value()
        Text(detail, style = MaterialTheme.typography.bodySmall, color = SelloTheme.colors.inkSoft)
    }
}
