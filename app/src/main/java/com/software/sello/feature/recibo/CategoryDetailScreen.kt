package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FittedMoneyText
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.carry.CarryBreakdownSection
import com.software.sello.presentation.carry.CarryProblemSlip
import com.software.sello.presentation.carry.categoryCarryFootnote
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * One category in the selected month: what is left of its limit, what was spent, and
 * the expenses themselves. [showName] is for when it sits beside Recibo, where the
 * title bar names the month and not the category.
 */
@Composable
fun CategoryDetailScreen(
    state: CategoryDetailState,
    months: MonthNames,
    dates: DateLabels,
    showName: Boolean,
    onAction: (CategoryDetailAction) -> Unit,
    onRecordHere: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onEditExpense: (String) -> Unit = {},
    onEditCategory: () -> Unit = {},
    showPastLimitAnnouncement: Boolean = false,
    onDismissPastLimitAnnouncement: () -> Unit = {},
    announcementBusy: Boolean = false,
    announcementFailed: Boolean = false,
    onShowAllExpenses: () -> Unit = {},
    onSetMonthLimit: (categoryId: String, month: YearMonth) -> Unit = { _, _ -> },
    onOpenSchedule: (String) -> Unit = {},
    onAllSchedules: () -> Unit = {},
    onAdjustBudget: () -> Unit = {},
    onRecordSeveral: (() -> Unit)? = null
) {
    // The reserved footnote brings Pagos fijos into view, as Recibo's opens Pagos previstos.
    val fixedSlip = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .testTag(CATEGORY_DETAIL_TAG)
    ) {
        when (state) {
            CategoryDetailState.Closed -> Unit

            CategoryDetailState.Loading -> SlipSkeleton(
                Modifier.testTag(CATEGORY_DETAIL_LOADING_TAG)
            )

            CategoryDetailState.Failed -> ErrorSlip(
                message = stringResource(R.string.category_detail_failed),
                onRetry = { onAction(CategoryDetailAction.Retry) },
                modifier = Modifier.testTag(CATEGORY_DETAIL_FAILED_TAG)
            )

            is CategoryDetailState.CarryFailed -> CarryProblemSlip(
                state.problem,
                onRetry = { onAction(CategoryDetailAction.Retry) },
                onSetLimit = onSetMonthLimit
            )

            CategoryDetailState.Gone -> EmptySlip(
                title = stringResource(R.string.category_detail_gone_title),
                body = stringResource(R.string.category_detail_gone_body),
                icon = SelloIcon.Block,
                modifier = Modifier.testTag(CATEGORY_DETAIL_GONE_TAG)
            )

            is CategoryDetailState.Ready -> {
                state.carryProblem?.let {
                    CarryProblemSlip(it, { onAction(CategoryDetailAction.Retry) }, onSetMonthLimit)
                }
                if (state.stale) {
                    StaleSlip(
                        { onAction(CategoryDetailAction.Retry) },
                        Modifier.testTag(CATEGORY_DETAIL_STALE_TAG)
                    )
                }
                HeaderSlip(
                    state,
                    months,
                    showName,
                    onEditCategory,
                    onAdjustBudget,
                    onReserved = { scope.launch { fixedSlip.bringIntoView() } }
                )
                // A month that is over can still have its limit corrected; said where
                // that month is being looked at, every time.
                if (state.month < YearMonth.from(state.today)) {
                    Text(
                        text = stringResource(
                            R.string.category_detail_past_help,
                            months.inSentence(state.month).replaceFirstChar { it.titlecase() }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SelloTheme.colors.inkSoft,
                        modifier = Modifier.testTag(CATEGORY_DETAIL_PAST_HELP_TAG)
                    )
                    if (showPastLimitAnnouncement) {
                        SelloNotice(
                            message = stringResource(
                                if (announcementFailed) {
                                    R.string.correction_announcement_failed
                                } else {
                                    R.string.past_limit_announcement
                                }
                            ),
                            actionLabel = stringResource(
                                if (announcementFailed) {
                                    R.string.correction_announcement_retry
                                } else {
                                    R.string.correction_announcement_dismiss
                                }
                            ),
                            onAction = onDismissPastLimitAnnouncement,
                            busy = announcementBusy,
                            modifier = Modifier.testTag(CATEGORY_DETAIL_PAST_LIMIT_ANNOUNCEMENT_TAG)
                        )
                    }
                }
                CategoryFixedPayments(
                    state,
                    months,
                    dates,
                    onAction,
                    onOpenSchedule,
                    onAllSchedules,
                    Modifier.bringIntoViewRequester(fixedSlip)
                )
                Expenses(
                    state,
                    months,
                    dates,
                    onAction,
                    onRecordHere,
                    onEditExpense,
                    onShowAllExpenses,
                    onRecordSeveral
                )
                CategoryInsightSections(state, months, dates, onAction)
            }
        }
    }
}

@Composable
private fun HeaderSlip(
    state: CategoryDetailState.Ready,
    months: MonthNames,
    showName: Boolean,
    onEditCategory: () -> Unit,
    onAdjustBudget: () -> Unit,
    onReserved: () -> Unit
) {
    val category = state.category
    // While its payments are reserved, what they leave (SELLO-135), as Recibo's answer.
    val figure = category.reserved?.figure ?: category.figure
    val figures = (state.insights as? DetailInsightsState.Ready)?.figures
    Slip(Modifier.fillMaxWidth().testTag(CATEGORY_DETAIL_HEADER_TAG)) {
        if (showName) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                IconButton(
                    onClick = onEditCategory,
                    modifier = Modifier.testTag(CATEGORY_DETAIL_EDIT_TAG)
                ) {
                    Icon(
                        SelloIcon.EditNote.painter(),
                        stringResource(R.string.category_detail_edit)
                    )
                }
            }
        }
        val headline = detailHeadline(figure.headline)
        val stamp = stampText(figure.stamp)
        // An archived category is stamped as such: that, not its verdict, is what it is now.
        val archived = stringResource(R.string.recibo_category_archived)
            .takeIf { category.archived }
        val spoken =
            stringResource(
                R.string.recibo_summary_description,
                headline,
                figure.amount.spoken,
                archived ?: stamp
            )
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SelloTheme.colors.inkSoft,
                    modifier = Modifier.weight(1f)
                )
                if (archived != null) {
                    Stamp(
                        archived,
                        StampInk.Neutral,
                        Modifier.padding(vertical = 6.dp).testTag(CATEGORY_DETAIL_ARCHIVED_TAG)
                    )
                } else if (category.limit != null) {
                    Stamp(
                        stamp,
                        stampInk(figure.stamp),
                        Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 240.dp
                val circle: @Composable () -> Unit = {
                    CategoryCircle(
                        CategoryIcons.iconFor(category.iconKey),
                        category.fraction,
                        size = 56.dp,
                        over = category.over,
                        unlimited = category.unlimited,
                        archived = category.archived
                    )
                }
                val amount: @Composable () -> Unit = {
                    FittedMoneyText(
                        figure.amount,
                        startSize = 44,
                        color = if (category.over && category.reserved == null) {
                            SelloTheme.colors.semantic.loss
                        } else {
                            SelloTheme.colors.ink
                        }
                    )
                }
                if (stacked) {
                    Column {
                        circle()
                        amount()
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
                    ) {
                        circle()
                        Column(Modifier.weight(1f)) { amount() }
                    }
                }
            }
        }
        category.why?.let { CarryWhyNote(it) }
        // Revisar's two facts under the big number, as on Recibo.
        category.reserved?.shortfall?.let {
            ShortfallNote(it)
            AdjustBudgetLink(onAdjustBudget)
        }
        Spacer(Modifier.height(SelloTheme.spacing.sm))
        // With carry the limit is the capacity: it is shown with its base and carry, never
        // alone as "de 180.000" (ADR 0020 CP27).
        category.carry?.let { carry ->
            TearLine()
            CarryBreakdownSection(carry, categoryCarryFootnote(carry))
        } ?: FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs),
                modifier = Modifier.testTag(CATEGORY_DETAIL_SPENT_TAG)
            ) {
                Text(
                    stringResource(R.string.category_detail_spent),
                    style = MaterialTheme.typography.labelMedium,
                    color = SelloTheme.colors.inkSoft
                )
                MoneyText(category.spent, textStyle = MaterialTheme.typography.labelMedium)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs),
                modifier = Modifier.testTag(CATEGORY_DETAIL_LIMIT_TAG)
            ) {
                category.limit?.let {
                    Text(
                        stringResource(R.string.category_detail_of),
                        style = MaterialTheme.typography.labelMedium,
                        color = SelloTheme.colors.inkSoft
                    )
                    MoneyText(it, textStyle = MaterialTheme.typography.labelMedium)
                }
                    ?: Text(stamp, color = SelloTheme.colors.inkSoft)
            }
        }
        if (figures?.allowance != null || figures?.comparison != null) {
            TearLine()
            Row(
                modifier = Modifier.fillMaxWidth().testTag("detail:planning"),
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                figures.allowance?.let { perDay ->
                    KeyValue(
                        stringResource(R.string.category_detail_for),
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.category_detail_days,
                                perDay.days,
                                perDay.days
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    KeyValue(
                        stringResource(R.string.category_detail_per_day),
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MoneyText(perDay.amount, textStyle = MaterialTheme.typography.titleMedium)
                    }
                }

                figures.comparison?.let { comparison ->
                    KeyValue(
                        stringResource(
                            R.string.category_detail_compared_to,
                            months.short(figures.comparisonMonth ?: state.month.minusMonths(1))
                                .lowercase(Locale.forLanguageTag("es-CO"))
                        ),
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val color = when (figures.comparisonDirection) {
                            DetailComparisonDirection.Higher -> SelloTheme.colors.semantic.warn
                            DetailComparisonDirection.Lower -> SelloTheme.colors.semantic.gain
                            DetailComparisonDirection.Equal -> SelloTheme.colors.ink
                        }
                        Text(
                            comparison,
                            style = MaterialTheme.typography.titleMedium,
                            color = color
                        )
                    }
                }
            }
            // A zero allowance never hides the deficit (CP15).
            figures.allowanceDeficit?.let { deficit ->
                Text(
                    stringResource(R.string.carry_per_day_deficit, deficit.digits),
                    style = MaterialTheme.typography.bodySmall,
                    color = SelloTheme.colors.inkSoft,
                    modifier = Modifier.padding(top = 4.dp).testTag(CATEGORY_DETAIL_DEFICIT_TAG)
                )
            }
        }
        category.reserved?.let { ReservedFootnote(it.footnote, onReserved) }
    }
}

const val CATEGORY_DETAIL_DEFICIT_TAG = "detail:deficit"

@Composable
private fun Expenses(
    state: CategoryDetailState.Ready,
    months: MonthNames,
    dates: DateLabels,
    onAction: (CategoryDetailAction) -> Unit,
    onRecordHere: () -> Unit,
    onEditExpense: (String) -> Unit,
    onShowAllExpenses: () -> Unit,
    onRecordSeveral: (() -> Unit)?
) {
    when (val list = state.expenses) {
        ExpenseListState.Loading -> SlipSkeleton(Modifier.testTag(CATEGORY_DETAIL_LIST_LOADING_TAG))

        ExpenseListState.Failed -> ErrorSlip(
            message = stringResource(R.string.category_detail_list_failed),
            onRetry = { onAction(CategoryDetailAction.Retry) },
            modifier = Modifier.testTag(CATEGORY_DETAIL_LIST_FAILED_TAG)
        )

        ExpenseListState.Empty -> EmptySlip(
            title = stringResource(
                R.string.category_detail_empty_title,
                months.inSentence(state.month)
            ),
            body = stringResource(R.string.category_detail_empty_body),
            icon = SelloIcon.ReceiptLong,
            modifier = Modifier.testTag(CATEGORY_DETAIL_EMPTY_TAG),
            actionLabel = stringResource(R.string.category_detail_record_here)
                .takeIf { state.canRecord },
            onAction = onRecordHere.takeIf { state.canRecord }
        )

        is ExpenseListState.Rows -> ExpenseList(
            state,
            list,
            dates,
            onAction,
            onEditExpense,
            onShowAllExpenses,
            onRecordSeveral.takeIf { state.canRecord }
        )
    }
}

@Composable
private fun ExpenseList(
    state: CategoryDetailState.Ready,
    list: ExpenseListState.Rows,
    dates: DateLabels,
    onAction: (CategoryDetailAction) -> Unit,
    onEditExpense: (String) -> Unit,
    onShowAllExpenses: () -> Unit,
    onRecordSeveral: (() -> Unit)?
) {
    val count = state.category.expenseCount
    val edit = stringResource(R.string.category_detail_expense_edit)
    val delete = stringResource(R.string.category_detail_expense_delete)
    Slip(Modifier.testTag(CATEGORY_DETAIL_LIST_TAG), pinked = false) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.category_detail_latest),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = SelloTheme.spacing.sm).semantics {
                    heading()
                }
            )
            SelloButton(
                text = if (list.rows.any { it.notCountedYet }) {
                    stringResource(R.string.category_detail_view_every_expense)
                } else {
                    pluralStringResource(
                        R.plurals.category_detail_view_all,
                        count,
                        countText(count)
                    )
                },
                onClick = onShowAllExpenses,
                kind = ButtonKind.Text,
                // Ends at the slip's content edge, over the amounts, as in v1.1.
                contentPadding = PaddingValues(start = SelloTheme.spacing.md),
                modifier = Modifier.testTag(CATEGORY_DETAIL_MORE_TAG)
            )
        }
        // Always there, so how to correct an expense never has to be remembered.
        Text(
            text = stringResource(R.string.category_detail_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.testTag(CATEGORY_DETAIL_HINT_TAG)
        )
        state.deleteProblem?.let { problem ->
            Text(
                text = stringResource(problem.text()),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.semantic.loss,
                modifier = Modifier
                    .testTag(CATEGORY_DETAIL_DELETE_PROBLEM_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        list.rows.take(4).forEach { row ->
            // Each row is its expense, not its place in the list: a new expense at the
            // top is one row added, instead of every row below it drawn again.
            key(row.id) {
                val day = dates.day(row.date, state.today)
                val remove = LineAction(delete) { onAction(CategoryDetailAction.Delete(row.id)) }
                ActionLine(
                    label = row.note ?: stringResource(R.string.category_detail_no_note),
                    onClick = { onEditExpense(row.id) },
                    actions = listOf(LineAction(edit) { onEditExpense(row.id) }, remove),
                    modifier = Modifier.testTag(categoryExpenseTag(row.id)),
                    caption = if (row.notCountedYet) {
                        day + ". " + stringResource(R.string.category_detail_not_counted)
                    } else {
                        day
                    },
                    swipeAway = remove
                ) { MoneyText(row.amount) }
            }
        }
        // Several expenses at once, every new line starting in this category.
        onRecordSeveral?.let {
            SelloButton(
                text = stringResource(R.string.category_detail_record_several),
                onClick = it,
                kind = ButtonKind.Text,
                icon = SelloIcon.ReceiptLong,
                contentPadding = PaddingValues(end = SelloTheme.spacing.md),
                modifier = Modifier.testTag(CATEGORY_DETAIL_RECORD_SEVERAL_TAG)
            )
        }
    }
}

internal fun DeleteProblem.text() = when (this) {
    DeleteProblem.Gone -> R.string.category_detail_delete_gone
    DeleteProblem.Changed -> R.string.category_detail_delete_changed
    DeleteProblem.DataChanged -> R.string.category_detail_delete_data_changed
    DeleteProblem.Failed -> R.string.category_detail_delete_failed
    DeleteProblem.Unknown -> R.string.correction_delete_unknown
}

@Composable
private fun detailHeadline(headline: ReciboHeadline): String = stringResource(
    when (headline) {
        ReciboHeadline.Remaining -> R.string.category_detail_headline_remaining
        ReciboHeadline.Overspent -> R.string.category_detail_headline_overspent
        ReciboHeadline.LeftOver -> R.string.category_detail_headline_left_over
        ReciboHeadline.Spent -> R.string.category_detail_headline_spent
        ReciboHeadline.ShortForPayments -> R.string.recibo_answer_short
    }
)

fun categoryExpenseTag(id: String) = "detail:expense:$id"

const val CATEGORY_DETAIL_TAG = "detail"
const val CATEGORY_DETAIL_LOADING_TAG = "detail:loading"
const val CATEGORY_DETAIL_FAILED_TAG = "detail:failed"
const val CATEGORY_DETAIL_GONE_TAG = "detail:gone"
const val CATEGORY_DETAIL_STALE_TAG = "detail:stale"
const val CATEGORY_DETAIL_HEADER_TAG = "detail:header"
const val CATEGORY_DETAIL_ARCHIVED_TAG = "detail:archived"
const val CATEGORY_DETAIL_SPENT_TAG = "detail:spent"
const val CATEGORY_DETAIL_LIMIT_TAG = "detail:limit"
const val CATEGORY_DETAIL_LIST_TAG = "detail:list"
const val CATEGORY_DETAIL_LIST_LOADING_TAG = "detail:list-loading"
const val CATEGORY_DETAIL_LIST_FAILED_TAG = "detail:list-failed"
const val CATEGORY_DETAIL_EMPTY_TAG = "detail:empty"
const val CATEGORY_DETAIL_MORE_TAG = "detail:more"
const val CATEGORY_DETAIL_RECORD_SEVERAL_TAG = "detail:record-several"
const val CATEGORY_DETAIL_EDIT_TAG = "detail:edit-category"
const val CATEGORY_DETAIL_PAST_HELP_TAG = "detail:past-help"
const val CATEGORY_DETAIL_PAST_LIMIT_ANNOUNCEMENT_TAG = "detail:past-limit-announcement"
const val CATEGORY_DETAIL_HINT_TAG = "detail:hint"
const val CATEGORY_DETAIL_DELETE_PROBLEM_TAG = "detail:delete-problem"
