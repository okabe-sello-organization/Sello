package com.software.sello.feature.recibo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LimitRow
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.month.monthNames
import java.util.Locale

/** The screen and its sheet, each from its own view model; Back closes the sheet first. */
@Composable
fun AdjustBudgetRoot(
    viewModels: ViewModelProvider.Factory,
    padding: PaddingValues,
    onOpenReserved: () -> Unit,
    onEditCategory: (String) -> Unit
) {
    val screen: AdjustBudgetViewModel = viewModel(factory = viewModels)
    val limits: LimitSheetViewModel = viewModel(factory = viewModels)
    val state by screen.state.collectAsStateWithLifecycle()
    val sheet by limits.state.collectAsStateWithLifecycle()
    BackHandler(enabled = sheet.categoryId != null) {
        limits.onAction(LimitSheetAction.Close)
    }
    AdjustBudgetScreen(
        state = state,
        sheet = sheet,
        onAction = { action ->
            when (action) {
                is AdjustBudgetAction.Open -> limits.onAction(
                    LimitSheetAction.Open(action.categoryId)
                )

                AdjustBudgetAction.OpenReserved -> onOpenReserved()

                AdjustBudgetAction.Retry -> Unit
            }
            screen.onAction(action)
        },
        onSheet = limits::onAction,
        onEditCategory = onEditCategory,
        contentPadding = padding
    )
}

/**
 * Ajustar presupuesto (docs/design/v2/ajustar.html): the month's answer with its limit
 * and spending, then every category that explains it. A category opens the limit sheet
 * over the screen; an archived one opens its editor instead.
 */
@Composable
fun AdjustBudgetScreen(
    state: AdjustBudgetState,
    sheet: LimitSheetState,
    onAction: (AdjustBudgetAction) -> Unit,
    onSheet: (LimitSheetAction) -> Unit,
    onEditCategory: (String) -> Unit,
    contentPadding: PaddingValues
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .testTag(ADJUST_TAG)
        ) {
            when (state) {
                AdjustBudgetState.Loading -> {
                    SlipSkeleton()
                    SlipSkeleton()
                }

                AdjustBudgetState.Failed -> ErrorSlip(
                    message = stringResource(R.string.adjust_failed),
                    onRetry = { onAction(AdjustBudgetAction.Retry) },
                    title = stringResource(R.string.adjust_failed_title),
                    icon = SelloIcon.Error
                )

                is AdjustBudgetState.Ready -> {
                    if (state.stale) StaleSlip({ onAction(AdjustBudgetAction.Retry) })
                    MonthSlip(state, onReserved = { onAction(AdjustBudgetAction.OpenReserved) })
                    CategoriesSlip(state) { row ->
                        if (row.archived) {
                            onEditCategory(row.id)
                        } else {
                            onAction(AdjustBudgetAction.Open(row.id))
                        }
                    }
                }
            }
        }
        if (sheet.categoryId != null) LimitSheet(sheet, onSheet)
    }
}

/** Recibo's answer, then the month's limit and what was spent against it. */
@Composable
private fun MonthSlip(state: AdjustBudgetState.Ready, onReserved: () -> Unit) {
    val summary = state.summary
    val label = answerLabel(summary.label)
    val stamp = stampText(summary.stamp)
    val month = monthNames().inSentence(state.month)
    AnswerSlip(
        label = label,
        amount = summary.amount,
        stamp = stamp,
        ink = stampInk(summary.stamp),
        description = stringResource(
            R.string.recibo_answer_description,
            label,
            summary.amount.spoken,
            stamp
        )
    ) {
        summary.shortfall?.let { ShortfallNote(it) }
        TearLine()
        state.limit?.let { limit ->
            LeaderLine(stringResource(R.string.adjust_limit_line, month)) { MoneyText(limit) }
        }
        LeaderLine(stringResource(R.string.adjust_spent_line)) { MoneyText(state.spent) }
        summary.reserved?.let { ReservedFootnote(it, onReserved) }
    }
}

@Composable
private fun CategoriesSlip(state: AdjustBudgetState.Ready, onOpen: (AdjustRow) -> Unit) {
    val month = monthNames().inSentence(state.month)
    Slip(Modifier.fillMaxWidth(), pinked = false) {
        Text(
            stringResource(R.string.adjust_categories_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(R.string.adjust_categories_caption, month),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        state.limited.forEach { CategoryRow(it, onOpen) }
        if (state.open.isNotEmpty()) {
            Text(
                stringResource(R.string.adjust_open_title),
                style = MaterialTheme.typography.titleSmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .semantics { heading() }
            )
            state.open.forEach { CategoryRow(it, onOpen) }
        }
        if (state.archived.isNotEmpty()) {
            Text(
                stringResource(R.string.recibo_archived_section),
                style = MaterialTheme.typography.titleSmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .semantics { heading() }
            )
            state.archived.forEach { CategoryRow(it, onOpen) }
        }
    }
}

@Composable
private fun CategoryRow(row: AdjustRow, onOpen: (AdjustRow) -> Unit) {
    val limit = row.limit
    val caption = when {
        row.archived -> stringResource(R.string.adjust_archived_caption)
        limit != null -> stringResource(R.string.adjust_row_caption, row.spent.digits, limit.digits)
        row.unlimited -> stringResource(R.string.recibo_cell_unlimited_caption)
        else -> stringResource(R.string.adjust_open_caption)
    }
    val spoken = when {
        row.archived -> stringResource(R.string.adjust_row_archived_spoken, row.name)

        limit == null ->
            stringResource(R.string.adjust_row_open_spoken, row.name, row.spent.spoken)

        row.overBy != null -> stringResource(
            R.string.adjust_row_over_spoken,
            row.name,
            row.overBy.spoken,
            row.spent.spoken,
            limit.spoken
        )

        else -> stringResource(
            R.string.adjust_row_left_spoken,
            row.name,
            row.remaining?.spoken.orEmpty(),
            row.spent.spoken,
            limit.spoken
        )
    }
    LimitRow(
        name = row.name,
        icon = CategoryIcons.iconFor(row.iconKey),
        caption = caption,
        amount = row.remaining ?: row.spent,
        fraction = row.fraction,
        over = row.over,
        spoken = spoken,
        onClick = { onOpen(row) },
        modifier = Modifier.testTag(adjustRowTag(row.id)),
        unlimited = row.unlimited,
        archived = row.archived
    )
}

/** The limit sheet: scope, amount, shortcuts, what the month would have left, and the pad. */
@Composable
private fun LimitSheet(sheet: LimitSheetState, onSheet: (LimitSheetAction) -> Unit) {
    val names = monthNames()
    val month = names.inSentence(sheet.month)
    val colors = SelloTheme.colors
    val locked = sheet.busy || sheet.error == LimitSheetError.Unknown
    RepeatSheet(
        title = stringResource(R.string.limit_sheet_title, sheet.name),
        onDismiss = { onSheet(LimitSheetAction.Close) },
        modifier = Modifier.testTag(LIMIT_SHEET_TAG)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryCircle(
                CategoryIcons.iconFor(sheet.iconKey),
                fraction = null,
                size = 40.dp,
                animate = false
            )
            Text(
                text = sheet.spent?.let { spent ->
                    sheet.current?.let {
                        stringResource(R.string.limit_sheet_spent, spent.digits, it.digits, month)
                    } ?: stringResource(R.string.limit_sheet_spent_open, spent.digits, month)
                }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSoft,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            IconButton(onClick = { onSheet(LimitSheetAction.Close) }, enabled = !sheet.busy) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.limit_sheet_close))
            }
        }
        if (sheet.loadFailed) {
            Text(stringResource(R.string.limit_sheet_failed), color = colors.semantic.loss)
            SelloButton(
                stringResource(R.string.limit_sheet_retry),
                { onSheet(LimitSheetAction.Retry) },
                kind = ButtonKind.Text
            )
            return@RepeatSheet
        }
        SegmentedSwitch(
            options = listOf(
                stringResource(R.string.limit_sheet_scope_this, month),
                stringResource(R.string.limit_sheet_scope_from, month)
            ),
            selectedIndex = sheet.scope.ordinal,
            onSelect = { onSheet(LimitSheetAction.Scope(LimitScope.entries[it])) },
            enabled = !locked,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            stringResource(
                if (sheet.scope == LimitScope.ThisMonth) {
                    R.string.limit_sheet_scope_this_line
                } else {
                    R.string.limit_sheet_scope_from_line
                },
                month
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSoft,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.paperAlt, SelloTheme.shapes.control)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row {
                Text(
                    stringResource(R.string.limit_sheet_amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft
                )
                Spacer(Modifier.weight(1f))
                Text(
                    sheet.current?.let { stringResource(R.string.limit_sheet_before, it.digits) }
                        ?: stringResource(R.string.limit_sheet_before_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft
                )
            }
            sheet.amount?.let { MoneyText(it, style = MoneyStyle.Total) }
                ?: Text("0", style = MaterialTheme.typography.displaySmall, color = colors.rule)
        }
        if (sheet.chips.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                sheet.chips.forEach { chip ->
                    SelloChip(
                        label = stringResource(chipText(chip.kind), chip.amount.digits),
                        selected = sheet.chosen == chip.kind,
                        onClick = { onSheet(LimitSheetAction.Chip(chip.kind)) },
                        enabled = !locked
                    )
                }
            }
        }
        sheet.fact?.let { fact ->
            val shown = factText(fact, month, spoken = false)
            val spoken = factText(fact, month, spoken = true)
            FactLine(
                AnnotatedString(shown),
                Modifier
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = spoken }
            )
        }
        sheet.error?.let { error ->
            Text(
                errorText(error, sheet),
                color = colors.semantic.loss,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (error == LimitSheetError.Conflict || error == LimitSheetError.DataChanged) {
                SelloButton(
                    stringResource(R.string.limit_sheet_retry),
                    { onSheet(LimitSheetAction.Retry) },
                    kind = ButtonKind.Text
                )
            }
        }
        val unknown = sheet.error == LimitSheetError.Unknown
        SelloButton(
            text = when {
                sheet.busy -> stringResource(R.string.limit_sheet_saving)

                unknown -> stringResource(R.string.limit_sheet_check)

                sheet.scope == LimitScope.ThisMonth ->
                    stringResource(R.string.limit_sheet_save_this, month)

                else -> stringResource(R.string.limit_sheet_save_from, month)
            },
            onClick = {
                onSheet(if (unknown) LimitSheetAction.CheckSaved else LimitSheetAction.Save)
            },
            enabled = (sheet.canSave || unknown) && !sheet.busy,
            loading = sheet.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        AmountKeypad(
            onDigit = { onSheet(LimitSheetAction.Digit(it)) },
            onTripleZero = { onSheet(LimitSheetAction.TripleZero) },
            onBackspace = { onSheet(LimitSheetAction.Backspace) },
            onClearAll = { onSheet(LimitSheetAction.ClearAll) },
            modifier = Modifier.testTag(LIMIT_KEYPAD_TAG),
            enabled = !locked,
            compact = true
        )
    }
}

private fun chipText(kind: ChipKind): Int = when (kind) {
    ChipKind.Spent -> R.string.limit_sheet_chip_spent
    ChipKind.CoverMonth -> R.string.limit_sheet_chip_month
    ChipKind.CoverPayments -> R.string.limit_sheet_chip_payments
}

/** "Tu límite total de octubre pasaría a 1.100.000. Te quedarían 20.000." */
@Composable
private fun factText(fact: LimitFact, month: String, spoken: Boolean): String {
    val total = if (spoken) fact.total.spoken else fact.total.digits
    val left = if (spoken) fact.left.spoken else fact.left.digits
    val outcome = when (fact.outcome) {
        FactOutcome.Left -> if (fact.afterPayments) {
            stringResource(R.string.limit_sheet_fact_left_payments, left)
        } else {
            stringResource(R.string.limit_sheet_fact_left, left)
        }

        FactOutcome.Exactly -> if (fact.afterPayments) {
            stringResource(R.string.limit_sheet_fact_exact_payments)
        } else {
            stringResource(R.string.limit_sheet_fact_exact)
        }

        FactOutcome.Short -> if (fact.afterPayments) {
            stringResource(R.string.limit_sheet_fact_short_payments, left)
        } else {
            stringResource(R.string.limit_sheet_fact_over, left)
        }
    }
    return stringResource(R.string.limit_sheet_fact_total, month, total) + " " + outcome
}

@Composable
private fun errorText(error: LimitSheetError, sheet: LimitSheetState): String = when (error) {
    LimitSheetError.NotSaved -> stringResource(R.string.limit_sheet_not_saved)

    LimitSheetError.Unknown -> stringResource(R.string.limit_sheet_unknown)

    LimitSheetError.Conflict -> stringResource(R.string.limit_sheet_conflict)

    LimitSheetError.DataChanged -> stringResource(R.string.limit_sheet_data_changed)

    LimitSheetError.CategoryGone -> stringResource(R.string.limit_sheet_gone)

    LimitSheetError.Archived -> stringResource(R.string.limit_sheet_archived)

    LimitSheetError.MonthEnded -> {
        val names = monthNames()
        stringResource(
            R.string.limit_sheet_month_ended,
            names.inSentence(sheet.month).replaceFirstChar {
                it.titlecase(Locale.forLanguageTag("es"))
            },
            names.inSentence(sheet.month.plusMonths(1))
        )
    }
}

const val ADJUST_TAG = "adjust"
const val LIMIT_SHEET_TAG = "adjust:sheet"
const val LIMIT_KEYPAD_TAG = "adjust:keypad"

fun adjustRowTag(id: String) = "adjust:row:$id"
