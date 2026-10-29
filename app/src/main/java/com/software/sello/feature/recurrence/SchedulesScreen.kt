package com.software.sello.feature.recurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.SplitBar
import com.software.sello.designsystem.component.SplitSegment
import com.software.sello.designsystem.component.StubButton
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.month.monthNames
import com.software.sello.presentation.recurrence.repeatRuleLabel
import java.time.LocalDate
import java.time.YearMonth

const val SCHEDULES_TAG = "schedules"

@Composable
fun SchedulesRoot(
    viewModels: ViewModelProvider.Factory,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
    onAdd: (ScheduleTab) -> Unit
) {
    val model: SchedulesViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    SchedulesScreen(state, today, model::onAction, onOpen, padding, onAdd)
}

/**
 * Pagos e ingresos fijos (SELLO-122): Pagos or Ingresos, the month's total with how much
 * of it went through, then each schedule grouped by what its month holds. The month is
 * a plan, never recorded money. A failed read is said, never shown as nothing repeating.
 * The month's receipt ends in a stub that adds one to the tab with Se repite on (SELLO-125).
 */
@Composable
fun SchedulesScreen(
    state: SchedulesState,
    today: LocalDate,
    onAction: (SchedulesAction) -> Unit,
    onOpen: (String) -> Unit,
    padding: PaddingValues,
    onAdd: (ScheduleTab) -> Unit
) {
    val dates = dateLabels()
    val tab = (state as? SchedulesState.Ready)?.tab ?: ScheduleTab.Payments
    LazyColumn(
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = Modifier.testTag(SCHEDULES_TAG)
    ) {
        item {
            SegmentedSwitch(
                listOf(
                    stringResource(R.string.schedules_payments),
                    stringResource(R.string.schedules_incomes)
                ),
                tab.ordinal,
                { onAction(SchedulesAction.TabPicked(ScheduleTab.entries[it])) },
                Modifier.testTag("schedules.tab")
            )
        }
        when (state) {
            SchedulesState.Loading -> item {
                SlipSkeleton(Modifier.testTag("schedules.loading"))
            }

            SchedulesState.Failed -> item {
                ErrorSlip(
                    message = stringResource(R.string.schedules_failed),
                    onRetry = { onAction(SchedulesAction.Retry) },
                    title = stringResource(R.string.schedules_failed_title),
                    icon = SelloIcon.Error,
                    modifier = Modifier.testTag("schedules.failed")
                )
            }

            is SchedulesState.Ready -> {
                item { Summary(state.plan, state.tab, state.empty, today, onAdd) }
                if (!state.empty) {
                    item { Groups(state, today, dates, onOpen) }
                    item {
                        Text(
                            stringResource(R.string.schedules_footnote),
                            style = MaterialTheme.typography.bodySmall,
                            color = SelloTheme.colors.inkSoft,
                            modifier = Modifier.padding(horizontal = SelloTheme.spacing.xs)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The month's receipt for the tab: its total, a bar of how much of it went through and
 * both parts, incomes in gain ink, no stamp (owner, 2026-10-26). It ends in a tear-off
 * stub that adds one to the tab (SELLO-125). With nothing repeating, the month at 0 and
 * the same stub; with a sum that does not fit, the stub alone.
 */
@Composable
private fun Summary(
    plan: ScheduleMonthPlan?,
    tab: ScheduleTab,
    empty: Boolean,
    today: LocalDate,
    onAdd: (ScheduleTab) -> Unit
) {
    val income = tab == ScheduleTab.Incomes
    // A compact stub: half the room around its words of SELLO-123's (owner, 2026-10-26).
    Slip(Modifier.fillMaxWidth().testTag("schedules.plan"), contentPadding = CompactStubPadding) {
        when {
            empty -> Nothing(plan, income, plan?.month ?: YearMonth.from(today))
            plan != null -> Figures(plan, income)
        }
        if (empty || plan != null) {
            TearLine(Modifier.padding(top = 7.dp), verticalMargin = 7.dp)
        }
        StubButton(
            SelloIcon.Add,
            stringResource(
                if (income) R.string.schedules_new_income else R.string.schedules_new_payment
            ),
            { onAdd(tab) },
            Modifier.testTag("schedules.add"),
            minHeight = SelloTheme.spacing.minTouchTarget
        )
    }
}

private val CompactStubPadding =
    PaddingValues(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 12.dp)

@Composable
private fun SummaryTitle(month: YearMonth, income: Boolean) {
    Text(
        stringResource(
            if (income) R.string.schedules_summary_incomes else R.string.schedules_summary_payments,
            monthNames().inSentence(month)
        ),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.semantics { heading() }
    )
}

/** Nothing repeats on this tab: the month at 0 when there is a plan to say it, and why. */
@Composable
private fun Nothing(plan: ScheduleMonthPlan?, income: Boolean, month: YearMonth) {
    val colors = SelloTheme.colors
    SummaryTitle(month, income)
    plan?.let {
        MoneyText(
            it.total,
            color = colors.inkSoft,
            textStyle = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = SelloTheme.spacing.xs)
        )
    }
    Text(
        stringResource(
            if (income) R.string.schedules_none_incomes else R.string.schedules_none_payments
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.inkSoft,
        modifier = Modifier.padding(top = 6.dp).testTag("schedules.empty")
    )
}

@Composable
private fun Figures(plan: ScheduleMonthPlan, income: Boolean) {
    val colors = SelloTheme.colors
    val accent = if (income) colors.semantic.gain else colors.brand
    val recordedLabel = stringResource(
        if (income) {
            R.string.schedules_summary_recorded_incomes
        } else {
            R.string.schedules_summary_recorded_payments
        }
    )
    val toComeLabel = stringResource(R.string.schedules_summary_to_come)
    val doneUnits = plan.doneUnits
    val totalUnits = plan.totalUnits
    SummaryTitle(plan.month, income)
    MoneyText(
        plan.total,
        color = if (income) colors.semantic.gain else colors.ink,
        textStyle = MaterialTheme.typography.displaySmall,
        modifier = Modifier.padding(top = SelloTheme.spacing.xs)
    )
    SplitBar(
        listOf(
            SplitSegment(
                fraction = if (totalUnits == 0L) 0f else doneUnits.toFloat() / totalUnits,
                spoken = stringResource(
                    R.string.schedules_summary_spoken,
                    recordedLabel,
                    plan.done.spoken,
                    plan.total.spoken
                ),
                color = accent
            ),
            SplitSegment(
                fraction = if (totalUnits == 0L) {
                    1f
                } else {
                    (totalUnits - doneUnits).toFloat() / totalUnits
                },
                spoken = stringResource(
                    R.string.schedules_summary_spoken,
                    toComeLabel,
                    plan.toCome.spoken,
                    plan.total.spoken
                ),
                color = colors.paperDim
            )
        ),
        modifier = Modifier.padding(vertical = 10.dp)
    )
    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Part(recordedLabel, plan.done, accent)
        Part(toComeLabel, plan.toCome, colors.paperDim)
    }
}

@Composable
private fun Part(label: String, amount: MoneyTextValue, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(start = 5.dp, end = SelloTheme.spacing.xs)
        )
        MoneyText(
            amount,
            color = SelloTheme.colors.ink,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)
        )
    }
}

/** The schedules by what their month holds; a group with none is left out. */
@Composable
private fun Groups(
    state: SchedulesState.Ready,
    today: LocalDate,
    dates: DateLabels,
    onOpen: (String) -> Unit
) {
    val income = state.tab == ScheduleTab.Incomes
    val month = monthNames().inSentence(YearMonth.from(today))
    val all = state.rows + state.ended
    val groups = ScheduleGroup.entries.associateWith { group ->
        val rows = all.filter { it.group == group }
        when (group) {
            ScheduleGroup.Due -> rows.sortedBy { it.next ?: LocalDate.MAX }
            ScheduleGroup.Recorded -> rows.sortedBy { it.recordedOn }
            ScheduleGroup.Later -> rows.sortedBy { it.next ?: LocalDate.MAX }
            else -> rows
        }
    }.filterValues { it.isNotEmpty() }
    Slip(Modifier.fillMaxWidth().testTag("schedules.list")) {
        groups.entries.forEachIndexed { index, (group, rows) ->
            Text(
                when (group) {
                    ScheduleGroup.Due -> stringResource(
                        R.string.schedules_group_due,
                        month,
                        rows.size
                    )

                    ScheduleGroup.Recorded -> stringResource(
                        if (income) {
                            R.string.schedules_group_recorded_incomes
                        } else {
                            R.string.schedules_group_recorded_payments
                        },
                        rows.size
                    )

                    ScheduleGroup.Later -> stringResource(R.string.schedules_group_later, rows.size)

                    ScheduleGroup.Paused -> stringResource(
                        R.string.schedules_group_paused,
                        rows.size
                    )

                    ScheduleGroup.Ended -> stringResource(R.string.schedules_group_ended, rows.size)
                }.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier
                    .padding(top = if (index == 0) 0.dp else 14.dp, bottom = 2.dp)
                    .semantics { heading() }
                    .testTag("schedules.group.${group.name.lowercase()}")
            )
            rows.forEachIndexed { at, row ->
                Row(row, today, dates, onOpen, income, last = at == rows.lastIndex)
            }
        }
    }
}

@Composable
private fun Row(
    row: ScheduleRow,
    today: LocalDate,
    dates: DateLabels,
    onOpen: (String) -> Unit,
    income: Boolean,
    last: Boolean
) {
    val name =
        row.note ?: row.categoryName ?: row.sourceKey?.let { incomeSourceLabel(it, row.sourceName) }
            ?: ""
    val rule = repeatRuleLabel(row.rule, row.start)
    val state = when (row.group) {
        ScheduleGroup.Ended -> row.endedOn?.let {
            stringResource(R.string.schedules_ended_on, dates.day(it, today))
        }

        ScheduleGroup.Paused -> row.pausedFrom?.let {
            stringResource(R.string.schedules_paused_since, dates.day(it, today))
        }

        ScheduleGroup.Recorded -> row.recordedOn?.let {
            stringResource(
                if (income) {
                    R.string.schedules_recorded_income
                } else {
                    R.string.schedules_recorded_payment
                },
                dates.day(it, today)
            )
        }

        ScheduleGroup.Due, ScheduleGroup.Later -> null
    }
    val pill = row.next?.let { next ->
        when (row.group) {
            // The day already says "Hoy" for today; tomorrow says so too.
            ScheduleGroup.Due -> if (next == today.plusDays(1)) {
                stringResource(R.string.schedules_due_tomorrow, dates.day(next, today))
            } else {
                dates.day(next, today)
            }

            ScheduleGroup.Recorded, ScheduleGroup.Later ->
                stringResource(R.string.schedules_next, dates.day(next, today))

            else -> null
        }
    }
    val caption = listOfNotNull(row.categoryName?.takeIf { row.note != null }, rule, state)
        .joinToString(" · ")
    val paused = stringResource(R.string.schedules_paused)
    ScheduleRow(
        name = name,
        caption = caption,
        amount = row.amount,
        icon = when {
            income -> SelloIcon.AccountBalance
            row.iconKey != null -> CategoryIcons.iconFor(row.iconKey)
            else -> SelloIcon.ReceiptLong
        },
        onClick = { onOpen(row.id) },
        spoken = listOfNotNull(
            name,
            row.amount.spoken,
            caption,
            pill,
            paused.takeIf { row.group == ScheduleGroup.Paused }
        ).joinToString(", "),
        modifier = Modifier.testTag("schedules.row.${row.id}"),
        soon = false,
        income = income,
        pausedLabel = null,
        dimmed = row.group == ScheduleGroup.Paused || row.group == ScheduleGroup.Ended,
        last = last,
        pill = pill,
        recorded = row.group == ScheduleGroup.Recorded
    )
}
