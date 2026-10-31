package com.software.sello.feature.bulk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.BulkAmountTone
import com.software.sello.designsystem.component.BulkCaption
import com.software.sello.designsystem.component.BulkLine
import com.software.sello.designsystem.component.BulkMarkTone
import com.software.sello.designsystem.component.BulkReceipt
import com.software.sello.designsystem.component.BulkTotal
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChipRows
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.rememberSelectedFirst
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.SourceName
import com.software.sello.presentation.carry.CarryCorrectionContent
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/** Owns the view model and Back; closes once the receipt is saved or left. */
@Composable
fun BulkEntryRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val viewModel: BulkEntryViewModel = viewModel(factory = viewModels)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    // Deshacer is offered for a moment, then the removal is final.
    LaunchedEffect(state.removed) {
        if (state.removed != null) {
            delay(REMOVED_NOTICE_MILLIS)
            viewModel.onAction(BulkEntryAction.RemoveSeen)
        }
    }
    BackHandler { viewModel.onAction(BulkEntryAction.Back) }
    BulkEntryScreen(state, dateLabels(), viewModel::onAction)
}

/**
 * The receipt takes the free height and scrolls its lines inside; the chips and note of
 * the highlighted line, the action and the pad stay put below it.
 */
@Composable
fun BulkEntryScreen(
    state: BulkEntryState,
    dates: DateLabels,
    onAction: (BulkEntryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    SelloScaffold(
        // Where it was opened from sets the kind of the whole receipt, for good.
        title = stringResource(
            if (state.kind == BulkKind.Income) {
                R.string.bulk_title_incomes
            } else {
                R.string.bulk_title_expenses
            }
        ),
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = { onAction(BulkEntryAction.Back) }, enabled = !state.busy) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        },
        notice = state.removed?.let { removed ->
            {
                val name = removed.categoryName
                    ?: removed.source?.let { incomeSourceLabel(it.key, it.name) }
                SelloNotice(
                    message = name?.let {
                        stringResource(R.string.bulk_removed, it)
                    } ?: stringResource(R.string.bulk_removed_plain),
                    actionLabel = stringResource(R.string.correction_undo),
                    onAction = { onAction(BulkEntryAction.UndoRemove) },
                    modifier = Modifier.testTag(BULK_REMOVED_TAG)
                )
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Receipt(state, dates, onAction, Modifier.weight(1f).heightIn(min = 160.dp))
            Choices(state, onAction)
            Action(state, onAction)
            AmountKeypad(
                onDigit = { onAction(BulkEntryAction.Digit(it)) },
                onTripleZero = { onAction(BulkEntryAction.TripleZero) },
                onBackspace = { onAction(BulkEntryAction.Backspace) },
                onClearAll = { onAction(BulkEntryAction.ClearAmount) },
                modifier = Modifier.testTag(BULK_KEYPAD_TAG),
                enabled = state.editable,
                compact = true
            )
        }
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = pluralStringResource(
                R.plurals.bulk_discard_title,
                state.typedLines,
                state.typedLines
            ),
            message = AnnotatedString(stringResource(R.string.bulk_discard_message)),
            confirmLabel = stringResource(R.string.expense_entry_discard_confirm),
            onConfirm = { onAction(BulkEntryAction.DiscardConfirmed) },
            onDismiss = { onAction(BulkEntryAction.KeepEditing) },
            dismissLabel = stringResource(R.string.expense_entry_discard_keep)
        )
    }
    if (state.datePickerOpen) DayPicker(state.date, state.today, onAction)
    state.noteDraft?.let { NoteSheet(state, it, onAction) }
    state.correction?.let { correction ->
        ConfirmSlip(
            title = stringResource(R.string.bulk_correction_title),
            confirmLabel = stringResource(R.string.carry_correct_create),
            onConfirm = { onAction(BulkEntryAction.CorrectionConfirmed) },
            onDismiss = { onAction(BulkEntryAction.CorrectionDismissed) },
            modifier = Modifier.testTag(BULK_CORRECTION_TAG),
            destructive = false,
            busy = state.busy
        ) {
            CarryCorrectionContent(correction.list, correction.changed)
        }
    }
}

@Composable
private fun Receipt(
    state: BulkEntryState,
    dates: DateLabels,
    onAction: (BulkEntryAction) -> Unit,
    modifier: Modifier
) {
    val income = state.kind == BulkKind.Income
    val noCategory = stringResource(R.string.bulk_no_category)
    val noSource = stringResource(R.string.bulk_no_source)
    val other = stringResource(R.string.income_other)
    val lines = state.lines.map { line ->
        val reason = line.problem?.let { stringResource(it.text()) }
        if (income) {
            // An income in gain green with a plus; Otro's name is its title, "Otro" under it.
            val source = line.source
            BulkLine(
                key = line.key,
                mark = source?.let { SelloIcon.Add },
                markTone = BulkMarkTone.Gain,
                title = source?.let { incomeSourceLabel(it.key, it.name) }
                    ?: line.note.ifBlank { noSource },
                titleSoft = source == null && line.note.isBlank(),
                amount = line.amount?.copy(sign = MoneySign.Plus),
                detail = source?.let {
                    listOfNotNull(
                        other.takeIf { _ ->
                            it.other && it.name != null
                        },
                        line.note.ifBlank { null }
                    )
                        .joinToString(" · ").ifBlank { null }
                },
                amountTone = BulkAmountTone.Gain,
                reason = reason
            )
        } else {
            val category = line.category
            BulkLine(
                key = line.key,
                mark = category?.let { CategoryIcons.iconFor(it.iconKey) },
                markTone = BulkMarkTone.Category,
                title = category?.name ?: line.note.ifBlank { noCategory },
                titleSoft = category == null && line.note.isBlank(),
                amount = line.amount,
                detail = line.note.takeIf { category != null && it.isNotBlank() },
                reason = reason
            )
        }
    }
    val caption = when {
        state.atLineLimit -> BulkCaption.Problem(
            stringResource(R.string.bulk_line_limit),
            icon = false
        )

        state.missingCategories > 0 -> BulkCaption.Problem(
            pluralStringResource(
                if (income) R.plurals.bulk_missing_sources else R.plurals.bulk_missing_categories,
                state.missingCategories,
                state.missingCategories
            )
        )

        state.refusedLines > 0 -> BulkCaption.Problem(
            pluralStringResource(
                R.plurals.bulk_refused_lines,
                state.refusedLines,
                state.refusedLines
            )
        )

        else -> BulkCaption.Count(
            stringResource(R.string.bulk_title),
            pluralStringResource(R.plurals.bulk_lines, state.lines.size, state.lines.size)
        )
    }
    val day = dates.day(state.date, state.today)
    val dateDescription = stringResource(R.string.expense_entry_date, day)
    BulkReceipt(
        lines = lines,
        highlighted = state.highlighted,
        caption = caption,
        total = if (income && state.count > 0) {
            BulkTotal(
                stringResource(R.string.bulk_total),
                state.total.copy(sign = MoneySign.Plus),
                BulkAmountTone.Gain
            )
        } else {
            BulkTotal(stringResource(R.string.bulk_total), state.total)
        },
        onSelect = { onAction(BulkEntryAction.Select(it)) },
        onRemove = { onAction(BulkEntryAction.Remove(it)) },
        onAdd = { onAction(BulkEntryAction.AddLine) },
        modifier = modifier.testTag(BULK_RECEIPT_TAG),
        addEnabled = state.canAdd,
        enabled = state.editable,
        headerEnd = {
            FieldPill(
                text = day,
                icon = SelloIcon.Event,
                onClick = { onAction(BulkEntryAction.OpenDatePicker) },
                modifier = Modifier
                    .testTag(BULK_DATE_TAG)
                    .semantics { contentDescription = dateDescription },
                enabled = state.editable
            )
        }
    )
}

/** The highlighted line's category or source chips, in two rows, Otro's name and its note. */
@Composable
private fun Choices(state: BulkEntryState, onAction: (BulkEntryAction) -> Unit) {
    val income = state.kind == BulkKind.Income
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when {
                state.lines.size <= 1 -> stringResource(
                    if (income) R.string.bulk_sources_heading else R.string.bulk_categories_heading
                )

                income -> stringResource(R.string.bulk_sources_heading_line, state.highlighted + 1)

                else ->
                    stringResource(R.string.bulk_categories_heading_line, state.highlighted + 1)
            }.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.semantics { heading() }
        )
        when {
            state.readFailed -> {
                FieldMessageText(
                    FieldMessage(
                        stringResource(
                            if (income) {
                                R.string.bulk_read_failed_incomes
                            } else {
                                R.string.bulk_read_failed
                            }
                        )
                    ),
                    Modifier.testTag(BULK_READ_FAILED_TAG)
                )
                SelloButton(
                    text = stringResource(R.string.bulk_retry),
                    onClick = { onAction(BulkEntryAction.Retry) },
                    kind = ButtonKind.Outlined
                )
            }

            state.loading -> SlipSkeleton(Modifier.testTag(BULK_LOADING_TAG), lines = 2)

            // Each line starts over, so the highlighted line's choice comes first.
            income -> key(state.highlightedLine.key) {
                val order = rememberSelectedFirst(
                    state.sources,
                    state.highlightedLine.source?.key
                ) { it }
                ChipRows(order.items.size, Modifier.testTag(BULK_SOURCES_TAG)) { index ->
                    val key = order.items[index]
                    SelloChip(
                        label = incomeSourceLabel(key),
                        selected = key == state.highlightedLine.source?.key,
                        onClick = {
                            order.picked()
                            onAction(BulkEntryAction.SourcePicked(key))
                        },
                        modifier = Modifier.testTag("$BULK_SOURCES_TAG.$key"),
                        enabled = state.editable
                    )
                }
            }

            else -> key(state.highlightedLine.key) {
                val order = rememberSelectedFirst(
                    state.categories,
                    state.highlightedLine.category?.id
                ) { it.id }
                ChipRows(order.items.size, Modifier.testTag(BULK_CATEGORIES_TAG)) { index ->
                    val category = order.items[index]
                    SelloChip(
                        label = category.name,
                        selected = category.id == state.highlightedLine.category?.id,
                        onClick = {
                            order.picked()
                            onAction(BulkEntryAction.CategoryPicked(category.id))
                        },
                        icon = CategoryIcons.iconFor(category.iconKey),
                        enabled = state.editable
                    )
                }
            }
        }
        val source = state.highlightedLine.source
        if (source?.other == true && !state.loading && !state.readFailed) {
            EntryLine(
                text = source.name ?: stringResource(R.string.income_source_name),
                icon = SelloIcon.AccountBalance,
                onClick = { onAction(BulkEntryAction.OpenSourceName) },
                modifier = Modifier.testTag(BULK_SOURCE_NAME_TAG),
                action = stringResource(R.string.income_source_change),
                placeholder = source.name == null,
                enabled = state.editable
            )
        }
        val note = state.highlightedLine.note
        EntryLine(
            text = note.ifBlank { stringResource(R.string.expense_entry_add_note) },
            icon = SelloIcon.EditNote,
            onClick = { onAction(BulkEntryAction.OpenNote) },
            modifier = Modifier.testTag(BULK_NOTE_TAG),
            action = stringResource(R.string.expense_entry_edit_note),
            placeholder = note.isBlank(),
            enabled = state.editable
        )
    }
}

/** What went wrong, then the one button: save the receipt, or ask about an unknown outcome. */
@Composable
private fun Action(state: BulkEntryState, onAction: (BulkEntryAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        when {
            state.atLimit -> FieldMessageText(
                FieldMessage(stringResource(R.string.expense_entry_amount_limit), isError = false)
            )

            else -> state.error?.let { error ->
                FieldMessageText(
                    FieldMessage(stringResource(error.text())),
                    Modifier.testTag(BULK_ERROR_TAG)
                )
            }
        }
        if (state.error == BulkError.Unknown) {
            SelloButton(
                text = stringResource(R.string.expense_entry_check_again),
                onClick = { onAction(BulkEntryAction.Retry) },
                modifier = Modifier.fillMaxWidth().testTag(BULK_SUBMIT_TAG),
                loading = state.busy
            )
        } else {
            SelloButton(
                text = if (state.busy) {
                    stringResource(R.string.expense_entry_saving)
                } else {
                    pluralStringResource(
                        if (state.kind == BulkKind.Income) {
                            R.plurals.bulk_submit_incomes
                        } else {
                            R.plurals.bulk_submit
                        },
                        state.count,
                        state.count
                    )
                },
                onClick = { onAction(BulkEntryAction.Submit) },
                modifier = Modifier.fillMaxWidth().testTag(BULK_SUBMIT_TAG),
                icon = SelloIcon.Add.takeIf { !state.busy },
                enabled = state.canSubmit,
                loading = state.busy
            )
        }
    }
}

/** The highlighted line's note, in the composer docked above the keyboard. */
@Composable
private fun NoteSheet(state: BulkEntryState, draft: String, onAction: (BulkEntryAction) -> Unit) {
    if (state.composing == BulkComposer.SourceName) return SourceNameSheet(state, draft, onAction)
    val line = state.highlightedLine
    val position = state.highlighted + 1
    val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)
    val context = buildAnnotatedString {
        append(stringResource(R.string.bulk_note_context, position))
        val name = line.category?.name ?: line.source?.let { incomeSourceLabel(it.key, it.name) }
        name?.let {
            append(" · ")
            withStyle(facts) { append(it) }
        }
        line.amount?.let {
            append(" · ")
            withStyle(facts) {
                append(if (state.kind == BulkKind.Income) "+" + it.digits else it.digits)
            }
        }
    }
    val spoken = listOfNotNull(
        stringResource(R.string.bulk_note_spoken, position),
        line.category?.name ?: line.source?.let { incomeSourceLabel(it.key, it.name) },
        line.amount?.spoken
    ).joinToString(", ")
    NoteComposer(
        context = context,
        value = draft,
        onValueChange = { onAction(BulkEntryAction.NoteDraftChanged(it)) },
        counter = stringResource(
            R.string.expense_entry_note_counter,
            state.noteDraftLength,
            Note.MAX_CODE_POINTS
        ),
        hint = stringResource(R.string.expense_entry_note_optional),
        error = when {
            state.noteDraftTooLong -> stringResource(R.string.expense_entry_note_composer_too_long)
            state.noteDraftNotAllowed -> stringResource(R.string.expense_entry_note_not_allowed)
            else -> null
        },
        doneLabel = stringResource(R.string.expense_entry_note_done),
        cancelLabel = stringResource(R.string.expense_entry_note_cancel),
        onDone = { onAction(BulkEntryAction.NoteDone) },
        onCancel = { onAction(BulkEntryAction.NoteCancelled) },
        contextIcon = when {
            line.category != null -> CategoryIcons.iconFor(line.category.iconKey)
            state.kind == BulkKind.Income -> SelloIcon.AccountBalance
            else -> SelloIcon.ReceiptLong
        },
        contextDescription = spoken
    )
}

/** Otro's name for the highlighted line, in the same composer: "Renglón 3 · Origen Otro · nombre". */
@Composable
private fun SourceNameSheet(
    state: BulkEntryState,
    draft: String,
    onAction: (BulkEntryAction) -> Unit
) {
    val position = state.highlighted + 1
    val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)
    val context = buildAnnotatedString {
        append(stringResource(R.string.bulk_source_name_context, position))
        append(" ")
        withStyle(facts) { append(stringResource(R.string.income_other)) }
        append(" · ")
        append(stringResource(R.string.bulk_source_name_context_end))
    }
    NoteComposer(
        context = context,
        value = draft,
        onValueChange = { onAction(BulkEntryAction.NoteDraftChanged(it)) },
        counter = stringResource(
            R.string.expense_entry_note_counter,
            state.noteDraftLength,
            SourceName.MAX_CODE_POINTS
        ),
        hint = stringResource(R.string.income_source_hint),
        error = when {
            state.noteDraftTooLong -> stringResource(R.string.income_source_composer_too_long)

            state.noteDraftNotAllowed -> stringResource(R.string.income_text_not_allowed)

            state.noteDraftRequired && draft.isNotEmpty() ->
                stringResource(R.string.income_missing_source)

            else -> null
        },
        doneLabel = stringResource(R.string.expense_entry_note_done),
        cancelLabel = stringResource(R.string.expense_entry_note_cancel),
        onDone = { onAction(BulkEntryAction.NoteDone) },
        onCancel = { onAction(BulkEntryAction.NoteCancelled) },
        contextIcon = SelloIcon.AccountBalance,
        contextDescription = stringResource(R.string.bulk_source_name_spoken, position)
    )
}

/** Days after [today] cannot be chosen. The picker works in whole days, with no time or zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(date: LocalDate, today: LocalDate, onAction: (BulkEntryAction) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !day(utcTimeMillis).isAfter(today)

            override fun isSelectableYear(year: Int) = year <= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(BulkEntryAction.CloseDatePicker) },
        confirmButton = {
            SelloButton(
                text = stringResource(R.string.expense_entry_date_confirm),
                onClick = {
                    val picked = picker.selectedDateMillis?.let(::day)
                    onAction(
                        if (picked == null) {
                            BulkEntryAction.CloseDatePicker
                        } else {
                            BulkEntryAction.DatePicked(picked)
                        }
                    )
                },
                modifier = Modifier.testTag(BULK_DATE_CONFIRM_TAG)
            )
        }
    ) {
        DatePicker(state = picker)
    }
}

private fun day(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

private fun LineProblem.text() = when (this) {
    LineProblem.NoCategory -> R.string.bulk_choose_category
    LineProblem.NoSource -> R.string.bulk_choose_source
    LineProblem.NoSourceName -> R.string.bulk_name_source
    LineProblem.CategoryArchived -> R.string.bulk_category_archived
    LineProblem.CategoryMissing -> R.string.bulk_category_missing
    LineProblem.MonthFull -> R.string.bulk_month_full
}

private fun BulkError.text() = when (this) {
    BulkError.NotSaved -> R.string.expense_entry_not_saved
    BulkError.FutureDate -> R.string.bulk_future_date
    BulkError.InvalidDate -> R.string.expense_entry_invalid_date
    BulkError.DataChanged -> R.string.bulk_data_changed
    BulkError.CarryUnavailable -> R.string.carry_unavailable_save_any
    BulkError.Unknown -> R.string.expense_entry_unknown
}

/** How long Deshacer is offered after a line is removed. */
private const val REMOVED_NOTICE_MILLIS = 6_000L

const val BULK_RECEIPT_TAG = "bulk:receipt"
const val BULK_DATE_TAG = "bulk:date"
const val BULK_DATE_CONFIRM_TAG = "bulk:date-confirm"
const val BULK_CATEGORIES_TAG = "bulk:categories"
const val BULK_SOURCES_TAG = "bulk:sources"
const val BULK_SOURCE_NAME_TAG = "bulk:source-name"
const val BULK_LOADING_TAG = "bulk:loading"
const val BULK_READ_FAILED_TAG = "bulk:read-failed"
const val BULK_NOTE_TAG = "bulk:note"
const val BULK_ERROR_TAG = "bulk:error"
const val BULK_SUBMIT_TAG = "bulk:submit"
const val BULK_KEYPAD_TAG = "bulk:keypad"
const val BULK_REMOVED_TAG = "bulk:removed"
const val BULK_CORRECTION_TAG = "bulk:correction"
