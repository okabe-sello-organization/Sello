package com.software.sello.feature.recurrence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.PlanSlip
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.component.TotalRule
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.month.monthNames
import com.software.sello.presentation.recurrence.repeatRuleLabel
import java.time.LocalDate

const val SCHEDULES_TAG = "schedules"

@Composable
fun SchedulesRoot(
    viewModels: ViewModelProvider.Factory,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
    onAdd: (income: Boolean) -> Unit
) {
    val model: SchedulesViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    SchedulesScreen(state, today, model::onAction, onOpen, onAdd, padding)
}

/**
 * Pagos e ingresos fijos (docs/design/v2/fijos.html): Pagos or Ingresos, the month as a
 * dashed plan, never recorded money, then each schedule by day, paused ones dimmed, and
 * the ended ones apart. A failed read is said, never shown as nothing repeating.
 */
@Composable
fun SchedulesScreen(
    state: SchedulesState,
    today: LocalDate,
    onAction: (SchedulesAction) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: (income: Boolean) -> Unit,
    padding: PaddingValues
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

            is SchedulesState.Ready -> if (state.empty) {
                item {
                    EmptySlip(
                        title = stringResource(R.string.schedules_empty_title),
                        body = stringResource(R.string.schedules_empty_body),
                        icon = SelloIcon.Autorenew,
                        actionLabel = stringResource(R.string.schedules_empty_action),
                        onAction = { onAdd(state.tab == ScheduleTab.Incomes) },
                        modifier = Modifier.testTag("schedules.empty")
                    )
                }
            } else {
                state.plan?.let { plan -> item { MonthPlan(plan, state.tab) } }
                item {
                    Slip(Modifier.fillMaxWidth().testTag("schedules.list"), pinked = false) {
                        Text(
                            stringResource(
                                if (state.tab == ScheduleTab.Incomes) {
                                    R.string.schedules_list_incomes
                                } else {
                                    R.string.schedules_list_payments
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        state.rows.forEachIndexed { index, row ->
                            Row(row, today, dates, onOpen, last = index == state.rows.lastIndex)
                        }
                        if (state.ended.isNotEmpty()) {
                            Text(
                                stringResource(R.string.schedules_ended).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.W800
                                ),
                                color = SelloTheme.colors.inkSoft,
                                modifier = Modifier.padding(top = 14.dp).semantics { heading() }
                            )
                            state.ended.forEachIndexed { index, row ->
                                Row(
                                    row,
                                    today,
                                    dates,
                                    onOpen,
                                    last = index == state.ended.lastIndex
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.schedules_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = SelloTheme.colors.inkSoft,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPlan(plan: ScheduleMonthPlan, tab: ScheduleTab) {
    val income = tab == ScheduleTab.Incomes
    PlanSlip(
        tag = stringResource(R.string.schedules_plan_tag, monthNames().inSentence(plan.month)),
        caption = pluralStringResource(
            if (income) R.plurals.schedules_active_incomes else R.plurals.schedules_active_payments,
            plan.active,
            plan.active
        ),
        modifier = Modifier.testTag("schedules.plan")
    ) {
        LeaderLine(
            stringResource(
                if (income) R.string.schedules_plan_done_incomes else R.string.schedules_plan_done,
                plan.doneCount
            )
        ) { MoneyText(plan.done) }
        LeaderLine(
            stringResource(R.string.schedules_plan_to_come, plan.toComeCount),
            emphasis = LineEmphasis.Soft
        ) { MoneyText(plan.toCome, fontWeight = FontWeight.W700) }
        TotalLine(stringResource(R.string.schedules_plan_total), rule = TotalRule.Single) {
            MoneyText(plan.total)
        }
    }
}

@Composable
private fun Row(
    row: ScheduleRow,
    today: LocalDate,
    dates: DateLabels,
    onOpen: (String) -> Unit,
    last: Boolean
) {
    val income = row.sourceKey != null
    val name =
        row.note ?: row.categoryName ?: row.sourceKey?.let { incomeSourceLabel(it, row.sourceName) }
            ?: ""
    val rule = repeatRuleLabel(row.rule, row.start)
    val state = when {
        row.endedOn != null -> stringResource(
            R.string.schedules_ended_on,
            dates.day(row.endedOn, today)
        )

        row.pausedFrom != null -> stringResource(
            R.string.schedules_paused_since,
            dates.day(row.pausedFrom, today)
        )

        row.next != null -> stringResource(R.string.schedules_next, dates.day(row.next, today))

        else -> null
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
            paused.takeIf { row.pausedFrom != null }
        ).joinToString(", "),
        modifier = Modifier.testTag("schedules.row.${row.id}"),
        soon = row.soon,
        income = income,
        pausedLabel = paused.takeIf { row.pausedFrom != null },
        dimmed = row.pausedFrom != null || row.endedOn != null,
        last = last
    )
}
