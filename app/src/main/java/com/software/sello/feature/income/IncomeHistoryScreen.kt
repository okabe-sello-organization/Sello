package com.software.sello.feature.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.ExpectedChip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.PlanSlip
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.SplitBar
import com.software.sello.designsystem.component.SplitSegment
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.SuggestionNote
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.component.TotalRule
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.month.monthNames
import java.time.LocalDate

/**
 * The month's income: what came in and where it went, from the same summary Resumen
 * shows, then each income. Planning from the person's own records, never a balance.
 */
@Composable
fun IncomeHistoryScreen(
    state: IncomeHistoryState,
    split: IncomeSplit?,
    plan: IncomePlanState,
    assign: AssignSuggestion?,
    today: LocalDate,
    onAction: (IncomeHistoryAction) -> Unit,
    onCreate: () -> Unit,
    onAssign: (limit: String?) -> Unit,
    onEdit: (String) -> Unit,
    padding: PaddingValues
) {
    val dates = dateLabels()
    val months = monthNames()
    LazyColumn(
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = Modifier.testTag("income.history")
    ) {
        when (state) {
            IncomeHistoryState.Loading -> item {
                Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
                    SlipSkeleton()
                    SlipSkeleton()
                }
            }

            IncomeHistoryState.Failed -> item {
                ErrorSlip(
                    message = stringResource(R.string.recibo_failed),
                    onRetry = { onAction(IncomeHistoryAction.Retry) },
                    title = stringResource(R.string.income_failed_title),
                    icon = SelloIcon.Error
                )
            }

            is IncomeHistoryState.Ready -> if (state.lines.isEmpty() && !state.more) {
                item {
                    EmptySlip(
                        title = stringResource(
                            R.string.income_empty_title,
                            months.inSentence(state.month)
                        ),
                        body = stringResource(R.string.income_empty_body),
                        actionLabel = stringResource(R.string.income_save),
                        onAction = onCreate,
                        icon = SelloIcon.AccountBalance,
                        modifier = Modifier.testTag("income.empty")
                    )
                }
                planItem(plan, today, onAction)
            } else {
                item { CameIn(state, split, months.inSentence(state.month)) }
                planItem(plan, today, onAction)
                assign?.let { item { AssignNote(it, onAssign, onAction) } }
                item {
                    Slip(Modifier.fillMaxWidth().testTag("income.list"), pinked = false) {
                        Text(
                            stringResource(R.string.income_list_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        state.lines.forEach { line ->
                            val source = incomeSourceLabel(line.sourceKey, line.sourceName)
                            val date = dates.day(line.date, today)
                            val description = stringResource(
                                R.string.income_edit_description,
                                source,
                                line.amount.spoken,
                                date
                            )
                            LeaderLine(
                                label = source,
                                caption = listOfNotNull(date, line.note).joinToString(" · "),
                                onClick = { onEdit(line.id) },
                                modifier = Modifier.testTag("income.line.${line.id}")
                                    .semantics { contentDescription = description }
                            ) { MoneyText(line.amount) }
                        }
                        if (state.pageFailed) {
                            ErrorSlip(stringResource(R.string.income_load_failed), {
                                onAction(IncomeHistoryAction.More)
                            })
                        }
                        if (state.more) {
                            SelloButton(
                                stringResource(R.string.income_more),
                                { onAction(IncomeHistoryAction.More) },
                                kind = ButtonKind.Text,
                                loading = state.busy,
                                modifier = Modifier.testTag("income.more")
                            )
                        }
                        TotalLine(stringResource(R.string.income_list_total)) {
                            MoneyText(state.total, style = MoneyStyle.Title)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "{monto} de lo que entró aún no tiene un presupuesto": the offer to give it a category of
 * its own. It opens the new-category form and changes nothing by itself; the × hides it for
 * the month.
 */
@Composable
private fun AssignNote(
    assign: AssignSuggestion,
    onAssign: (String?) -> Unit,
    onAction: (IncomeHistoryAction) -> Unit
) {
    SuggestionNote(
        title = stringResource(R.string.income_assign_title),
        evidence = stringResource(R.string.income_assign_evidence, assign.unassigned.digits),
        evidenceSpoken = stringResource(R.string.income_assign_evidence, assign.unassigned.spoken),
        advice = stringResource(R.string.income_assign_advice),
        actionLabel = stringResource(R.string.income_assign_action),
        onAction = { onAssign(assign.limit) },
        modifier = Modifier.testTag("income.assign"),
        dismissLabel = stringResource(R.string.income_assign_dismiss),
        onDismiss = { onAction(IncomeHistoryAction.DismissAssign) }
    )
}

private fun LazyListScope.planItem(
    plan: IncomePlanState,
    today: LocalDate,
    onAction: (IncomeHistoryAction) -> Unit
) {
    when (plan) {
        IncomePlanState.None -> Unit

        IncomePlanState.Failed -> item {
            ErrorSlip(
                stringResource(R.string.income_plan_failed),
                { onAction(IncomeHistoryAction.Retry) },
                modifier = Modifier.testTag("income.plan.failed")
            )
        }

        is IncomePlanState.Shown -> item { MonthPlan(plan.plan, today) }
    }
}

/**
 * Plan del mes: what is recorded, what is still expected, both together and what that
 * leaves unassigned, then each expected income. Apart from the records, in a dashed
 * box: none of it is in a total until catch-up records it on its date.
 */
@Composable
private fun MonthPlan(plan: IncomePlanView, today: LocalDate) {
    val colors = SelloTheme.colors
    val dates = dateLabels()
    PlanSlip(
        tag = stringResource(R.string.income_plan_tag),
        caption = stringResource(R.string.income_plan_caption),
        modifier = Modifier.testTag("income.plan")
    ) {
        LeaderLine(stringResource(R.string.income_plan_recorded)) { MoneyText(plan.recorded) }
        LeaderLine(
            stringResource(R.string.income_plan_expected),
            emphasis = LineEmphasis.Soft
        ) { MoneyText(plan.expected, fontWeight = FontWeight.W700) }
        TotalLine(stringResource(R.string.income_plan_planned), rule = TotalRule.Single) {
            MoneyText(plan.planned)
        }
        LeaderLine(
            stringResource(R.string.income_plan_unassigned),
            emphasis = LineEmphasis.Strong,
            modifier = Modifier.testTag("income.plan.unassigned")
        ) {
            MoneyText(
                plan.unassigned,
                color = if (plan.over) colors.semantic.loss else colors.ink,
                fontWeight = FontWeight.W900
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        ) {
            plan.items.forEach { item ->
                val source = incomeSourceLabel(item.sourceKey, item.sourceName)
                val date = dates.day(item.date, today)
                ExpectedChip(
                    text = stringResource(R.string.income_plan_item, source, date),
                    amount = item.amount,
                    spoken = stringResource(
                        R.string.income_plan_item_spoken,
                        source,
                        date,
                        item.amount.spoken
                    ),
                    modifier = Modifier.testTag("income.plan.item.${item.key}")
                )
            }
        }
        Text(
            stringResource(R.string.income_plan_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
    }
}

/**
 * "Entró en octubre" and the recorded total; under the tear line, the split between
 * category budgets and what is unassigned. A negative unassigned amount is signed and
 * red, with the two facts behind it, and draws no segment of its own. Without the
 * summary, only the total is shown: no figure stands in for one that was not read.
 */
@Composable
private fun CameIn(state: IncomeHistoryState.Ready, split: IncomeSplit?, month: String) {
    AnswerSlip(
        label = stringResource(R.string.income_came_in, month),
        amount = state.total,
        stamp = null,
        ink = StampInk.Neutral,
        modifier = Modifier.testTag("income.hero")
    ) {
        if (split != null) {
            val colors = SelloTheme.colors
            TearLine()
            SplitBar(
                listOf(
                    SplitSegment(
                        split.allocatedShare,
                        stringResource(R.string.income_split_in_budgets, split.allocated.spoken),
                        colors.brand
                    ),
                    SplitSegment(
                        1f - split.allocatedShare,
                        stringResource(R.string.income_split_unassigned, split.unallocated.spoken),
                        colors.brand,
                        striped = true
                    )
                ),
                Modifier.padding(vertical = SelloTheme.spacing.sm).testTag("income.split")
            )
            LeaderLine(stringResource(R.string.income_in_budgets)) { MoneyText(split.allocated) }
            LeaderLine(
                stringResource(R.string.income_unassigned),
                modifier = Modifier.testTag("income.unassigned")
            ) {
                MoneyText(
                    split.unallocated,
                    color = if (split.over) colors.semantic.loss else colors.ink
                )
            }
            if (split.over) {
                Text(
                    stringResource(
                        R.string.income_over_basis,
                        split.allocated.digits,
                        split.income.digits
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                    modifier = Modifier.testTag("income.overBasis")
                )
            }
        }
    }
}
