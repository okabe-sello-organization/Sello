package com.software.sello.feature.expense

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AddChip
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ChipRows
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StampSize
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.component.selloWindowLayout
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Note
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.corrections.CorrectionAnnouncementState
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.presentation.recurrence.FollowingChoice
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatChoiceSheet
import com.software.sello.presentation.recurrence.RepeatRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Owns the view model, Back, and the one-time feedback when a new expense is saved. */
@Composable
fun ExpenseEntryRoot(
    viewModels: ViewModelProvider.Factory,
    onIncome: (() -> Unit)? = null,
    onCreateCategory: (() -> Unit)? = null,
    announcements: CorrectionAnnouncementState = CorrectionAnnouncementState(loading = false),
    onDismissPastExpense: () -> Unit = {},
    announcementFailed: Boolean = false,
    createdCategoryId: String? = null,
    onCreatedCategoryTaken: () -> Unit = {},
    onClosed: () -> Unit
) {
    val viewModel: ExpenseEntryViewModel = viewModel(factory = viewModels)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A category created from Nueva, handed back by the editor: chosen here, taken once.
    LaunchedEffect(createdCategoryId) {
        if (createdCategoryId != null) {
            viewModel.onAction(ExpenseEntryAction.CategoryCreated(createdCategoryId))
            onCreatedCategoryTaken()
        }
    }
    // Feedback for a save that already happened, once per saved expense and before the
    // form closes. Nothing depends on it: if the device cannot vibrate, the expense is
    // saved all the same. The caller says what was saved.
    val haptics = LocalHapticFeedback.current
    var acknowledged by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.closed) {
        if (state.closed) {
            val recorded = state.recorded
            if (recorded != null && acknowledged != recorded) {
                acknowledged = recorded
                runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            }
            onClosed()
        }
    }
    BackHandler { viewModel.onAction(ExpenseEntryAction.Back) }
    // While Se repite's sheet is open, Back closes it and keeps the choice as it was.
    BackHandler(enabled = state.repeat.sheet != null) {
        viewModel.onAction(ExpenseEntryAction.Repeat(RepeatAction.Dismiss))
    }
    ExpenseEntryScreen(
        state,
        dateLabels(),
        viewModel::onAction,
        onIncome = onIncome,
        onCreateCategory = onCreateCategory,
        showPastExpenseAnnouncement = !announcements.loading &&
            state.editing && state.pastMonths.isNotEmpty() &&
            !announcements.preference.pastExpenseSeen,
        dismissPastExpenseAnnouncement = onDismissPastExpense,
        announcementBusy = announcements.busy,
        announcementFailed = announcementFailed
    )
}

@Composable
fun ExpenseEntryScreen(
    state: ExpenseEntryState,
    dates: DateLabels,
    onAction: (ExpenseEntryAction) -> Unit,
    modifier: Modifier = Modifier,
    onIncome: (() -> Unit)? = null,
    onCreateCategory: (() -> Unit)? = null,
    showPastExpenseAnnouncement: Boolean = false,
    dismissPastExpenseAnnouncement: () -> Unit = {},
    announcementBusy: Boolean = false,
    announcementFailed: Boolean = false
) {
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.expense_edit_title else R.string.expense_entry_title
        ),
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = { onAction(ExpenseEntryAction.Back) }) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        },
        actions = {
            // Only for an expense that is recorded and has been read: nothing else
            // is there to delete. Undo is offered afterwards, so it does not ask first.
            if (state.editing && !state.loading && !state.loadFailed) {
                val description = stringResource(R.string.expense_edit_delete_description)
                SelloButton(
                    text = stringResource(R.string.expense_edit_delete),
                    onClick = { onAction(ExpenseEntryAction.Delete) },
                    kind = ButtonKind.Text,
                    enabled = state.canDelete,
                    modifier = Modifier
                        .testTag(ENTRY_DELETE_TAG)
                        .semantics { contentDescription = description }
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> Pane(padding) { SlipSkeleton(Modifier.testTag(ENTRY_LOADING_TAG)) }

            state.loadFailed -> Pane(padding) {
                ErrorSlip(
                    message = stringResource(
                        if (state.editing) {
                            R.string.expense_edit_load_failed
                        } else {
                            R.string.expense_entry_load_failed
                        }
                    ),
                    onRetry = { onAction(ExpenseEntryAction.Retry) },
                    modifier = Modifier.testTag(ENTRY_LOAD_FAILED_TAG)
                )
            }

            // The amount, the action and the pad hold still; only the choices scroll.
            // A wide or short window puts the action and pad beside the rest.
            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {
                    if (!state.editing && onIncome != null) {
                        SegmentedSwitch(
                            listOf(
                                stringResource(R.string.entry_expense),
                                stringResource(R.string.entry_income)
                            ),
                            0,
                            { if (it == 1 && state.editable) onIncome() },
                            Modifier.testTag("entry.kind"),
                            enabled = state.editable
                        )
                    }
                },
                amount = { Amount(state, dates, onAction) },
                choices = {
                    Choices(
                        state,
                        dates,
                        onAction,
                        onCreateCategory,
                        showPastExpenseAnnouncement,
                        dismissPastExpenseAnnouncement,
                        announcementBusy,
                        announcementFailed
                    )
                },
                action = { Action(state, onAction) },
                keypad = {
                    AmountKeypad(
                        onDigit = { onAction(ExpenseEntryAction.Digit(it)) },
                        onTripleZero = { onAction(ExpenseEntryAction.TripleZero) },
                        onBackspace = { onAction(ExpenseEntryAction.Backspace) },
                        onClearAll = { onAction(ExpenseEntryAction.ClearAmount) },
                        modifier = Modifier.testTag(ENTRY_KEYPAD_TAG),
                        enabled = state.editable,
                        compact = true
                    )
                }
            )
        }
    }
    if (state.confirmDiscard) {
        val editing = state.editing
        ConfirmSlip(
            title = stringResource(
                if (editing) {
                    R.string.expense_edit_discard_title
                } else {
                    R.string.expense_entry_discard_title
                }
            ),
            message = AnnotatedString(
                stringResource(
                    if (editing) {
                        R.string.expense_edit_discard_message
                    } else {
                        R.string.expense_entry_discard_message
                    }
                )
            ),
            confirmLabel = stringResource(R.string.expense_entry_discard_confirm),
            onConfirm = { onAction(ExpenseEntryAction.DiscardConfirmed) },
            onDismiss = { onAction(ExpenseEntryAction.KeepEditing) },
            destructive = false,
            dismissLabel = stringResource(
                if (editing) {
                    R.string.expense_edit_discard_keep
                } else {
                    R.string.expense_entry_discard_keep
                }
            )
        )
    }
    if (state.datePickerOpen) {
        DayPicker(state.date, state.today, state.repeat.choice != null, onAction)
    }
    state.noteDraft?.let { NoteSheet(state, it, onAction) }
    RepeatChoiceSheet(state.repeat, state.date, state.today, dates) {
        onAction(ExpenseEntryAction.Repeat(it))
    }
    val following = state.following
    if (state.askFollowing && following != null) {
        FollowingChoice(
            following,
            income = false,
            busy = state.busy,
            onChoose = { onAction(ExpenseEntryAction.FollowingChosen(it)) },
            onBack = { onAction(ExpenseEntryAction.FollowingDismissed) }
        )
    }
}

@Composable
private fun Pane(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
    ) { content() }
}

/**
 * The slip being written: "Gasto en pesos" (or why the amount cannot be used, in its
 * place), the date, the chosen category's icon and the amount, which steps down in
 * size as digits are added.
 */
@Composable
private fun Amount(
    state: ExpenseEntryState,
    dates: DateLabels,
    onAction: (ExpenseEntryAction) -> Unit
) {
    val day = dates.day(state.date, state.today)
    val dateDescription = stringResource(R.string.expense_entry_date, day)
    EntryAmountSlip(
        label = stringResource(R.string.expense_entry_amount_label),
        amount = state.amount,
        modifier = Modifier.testTag(ENTRY_AMOUNT_TAG),
        error = when {
            state.atLimit -> stringResource(R.string.expense_entry_amount_limit)
            else -> state.amountProblem?.let { stringResource(it.text()) }
        },
        date = {
            FieldPill(
                text = day,
                icon = SelloIcon.Event,
                onClick = { onAction(ExpenseEntryAction.OpenDatePicker) },
                modifier = Modifier
                    .testTag(ENTRY_DATE_TAG)
                    .semantics { contentDescription = dateDescription },
                enabled = state.editable
            )
        },
        icon = state.category?.let { CategoryIcons.iconFor(it.iconKey) },
        limitPulse = state.limitPulse,
        rejectedText = state.amountText,
        onPaste = if (state.editable) {
            { onAction(ExpenseEntryAction.Paste(it)) }
        } else {
            null
        }
    )
}

/**
 * Category chips in at most two rows, ending with Nueva for a new expense; the note's
 * row; then what is missing or went wrong, and what a correction would change.
 */
@Composable
private fun Choices(
    state: ExpenseEntryState,
    dates: DateLabels,
    onAction: (ExpenseEntryAction) -> Unit,
    onCreateCategory: (() -> Unit)?,
    showPastExpenseAnnouncement: Boolean,
    dismissPastExpenseAnnouncement: () -> Unit,
    announcementBusy: Boolean,
    announcementFailed: Boolean
) {
    val addNew = onCreateCategory.takeIf { !state.editing }
    val count = state.categories.size + if (addNew != null) 1 else 0
    ChipRows(count, Modifier.testTag(ENTRY_CATEGORIES_TAG)) { index ->
        val category = state.categories.getOrNull(index)
        if (category == null) {
            AddChip(
                stringResource(R.string.expense_entry_new_category),
                { addNew?.invoke() },
                Modifier.testTag(ENTRY_NEW_CATEGORY_TAG),
                enabled = state.editable
            )
        } else {
            SelloChip(
                label = category.name,
                selected = category.id == state.categoryId,
                onClick = { onAction(ExpenseEntryAction.CategoryPicked(category.id)) },
                icon = CategoryIcons.iconFor(category.iconKey),
                enabled = state.editable
            )
        }
    }
    EntryLine(
        text = state.note.ifBlank { stringResource(R.string.expense_entry_add_note) },
        icon = SelloIcon.EditNote,
        onClick = { onAction(ExpenseEntryAction.OpenNote) },
        modifier = Modifier.testTag(ENTRY_NOTE_TAG),
        action = stringResource(R.string.expense_entry_edit_note),
        placeholder = state.note.isBlank(),
        enabled = state.editable
    )
    state.noteProblem?.let {
        FieldMessageText(FieldMessage(stringResource(it.text())))
    }
    // Se repite, for a new expense only: a recorded one is corrected on its own.
    if (!state.editing) {
        RepeatRow(
            state.repeat,
            income = false,
            state.date,
            state.today,
            dates,
            state.editable,
            { onAction(ExpenseEntryAction.Repeat(it)) }
        )
    }
    // Before anything is saved: which other months' figures a correction touches.
    if (state.pastMonths.isNotEmpty()) {
        val months = monthNames()
        val named = state.pastMonths.map(months::inSentence)
        FieldMessageText(
            FieldMessage(
                if (named.size == 1) {
                    stringResource(R.string.expense_edit_past_month, named[0])
                } else {
                    stringResource(R.string.expense_edit_past_months, named[0], named[1])
                },
                isError = false
            ),
            Modifier.testTag(ENTRY_PAST_MONTHS_TAG)
        )
    }
    if (showPastExpenseAnnouncement) {
        SelloNotice(
            message = stringResource(
                if (announcementFailed) {
                    R.string.correction_announcement_failed
                } else {
                    R.string.past_expense_announcement
                }
            ),
            actionLabel = stringResource(
                if (announcementFailed) {
                    R.string.correction_announcement_retry
                } else {
                    R.string.correction_announcement_dismiss
                }
            ),
            onAction = dismissPastExpenseAnnouncement,
            busy = announcementBusy,
            modifier = Modifier.testTag(ENTRY_PAST_EXPENSE_ANNOUNCEMENT_TAG)
        )
    }
}

/**
 * What is missing or went wrong. A missing amount is not said here: the slip already
 * shows "Sin monto" and the button is off.
 */
@Composable
private fun Said(state: ExpenseEntryState) {
    state.error?.let { error ->
        FieldMessageText(
            FieldMessage(stringResource(error.text(state.editing))),
            Modifier.testTag(ENTRY_ERROR_TAG)
        )
        // The following payments already changed; only this one is still to save.
        if (state.followingSaved) {
            FieldMessageText(FieldMessage(stringResource(R.string.following_saved_this_not)))
        }
    }
    val missing = state.missing?.text()
    if (missing != null && !state.busy && state.error != EntryError.Unknown) {
        FieldMessageText(
            FieldMessage(stringResource(missing), isError = false),
            Modifier.testTag(ENTRY_MISSING_TAG)
        )
    }
}

/**
 * What is missing or went wrong, then the one button: record, save the correction, or
 * ask again about an unknown outcome. Held with the pad, so what is said is always in
 * view and grows upwards without moving the button or a key.
 */
@Composable
private fun Action(state: ExpenseEntryState, onAction: (ExpenseEntryAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        Said(state)
        SubmitButton(state, onAction)
    }
}

@Composable
private fun SubmitButton(state: ExpenseEntryState, onAction: (ExpenseEntryAction) -> Unit) {
    if (state.error == EntryError.Unknown) {
        SelloButton(
            text = stringResource(R.string.expense_entry_check_again),
            onClick = { onAction(ExpenseEntryAction.Retry) },
            modifier = Modifier.fillMaxWidth().testTag(ENTRY_SUBMIT_TAG),
            loading = state.busy
        )
        return
    }
    // Read aloud as the whole sentence, so it is clear what will be recorded and where.
    val repeating = state.repeat.choice != null && !state.editing
    val label = stringResource(
        when {
            state.busy -> R.string.expense_entry_saving
            state.editing -> R.string.expense_edit_submit
            repeating -> R.string.repeat_save_payment
            else -> R.string.expense_entry_submit
        }
    )
    val category = state.category
    val sentence = if (state.amount != null && category != null && !state.busy && !repeating) {
        stringResource(
            if (state.editing) {
                R.string.expense_edit_submit_description
            } else {
                R.string.expense_entry_submit_description
            },
            state.amount.spoken,
            category.name
        )
    } else {
        label
    }
    SelloButton(
        text = label,
        onClick = { onAction(ExpenseEntryAction.Submit) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ENTRY_SUBMIT_TAG)
            .semantics { contentDescription = sentence },
        enabled = state.canSubmit,
        loading = state.busy
    )
}

/**
 * The note, written in a composer docked above the keyboard. It says what the note
 * belongs to; over 60 characters the text stays and it cannot be finished.
 */
@Composable
private fun NoteSheet(
    state: ExpenseEntryState,
    draft: String,
    onAction: (ExpenseEntryAction) -> Unit
) {
    val kind = stringResource(R.string.entry_expense)
    val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)
    val category = state.category
    val context = buildAnnotatedString {
        append(kind)
        category?.let {
            append(stringResource(R.string.expense_entry_note_in))
            withStyle(facts) { append(it.name) }
        }
        state.amount?.let {
            append(" · ")
            withStyle(facts) { append(it.digits) }
        }
    }
    val spoken = listOfNotNull(
        stringResource(R.string.expense_entry_note_spoken) +
            (category?.let { stringResource(R.string.expense_entry_note_for, it.name) } ?: ""),
        state.amount?.spoken
    ).joinToString(", ")
    NoteComposer(
        context = context,
        value = draft,
        onValueChange = { onAction(ExpenseEntryAction.NoteDraftChanged(it)) },
        counter = stringResource(
            R.string.expense_entry_note_counter,
            state.noteDraftLength,
            Note.MAX_CODE_POINTS
        ),
        hint = stringResource(R.string.expense_entry_note_optional),
        error = state.noteDraftProblem?.let { stringResource(it.composerText()) },
        doneLabel = stringResource(R.string.expense_entry_note_done),
        cancelLabel = stringResource(R.string.expense_entry_note_cancel),
        onDone = { onAction(ExpenseEntryAction.NoteDone) },
        onCancel = { onAction(ExpenseEntryAction.NoteCancelled) },
        contextIcon = category?.let { CategoryIcons.iconFor(it.iconKey) } ?: SelloIcon.ReceiptLong,
        contextDescription = spoken
    )
}

/** Days after [today] cannot be chosen. The picker works in whole days, with no time or zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(
    date: LocalDate,
    today: LocalDate,
    repeating: Boolean,
    onAction: (ExpenseEntryAction) -> Unit
) {
    // One expense is dated today or earlier; while Se repite is on, today or later.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = if (repeating) {
                !day(utcTimeMillis).isBefore(today)
            } else {
                !day(utcTimeMillis).isAfter(today)
            }

            override fun isSelectableYear(year: Int) =
                if (repeating) year >= today.year else year <= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(ExpenseEntryAction.CloseDatePicker) },
        confirmButton = {
            SelloButton(
                text = stringResource(R.string.expense_entry_date_confirm),
                onClick = {
                    val picked = picker.selectedDateMillis?.let(::day)
                    if (picked == null) {
                        onAction(ExpenseEntryAction.CloseDatePicker)
                    } else {
                        onAction(ExpenseEntryAction.DatePicked(picked))
                    }
                },
                modifier = Modifier.testTag(ENTRY_DATE_CONFIRM_TAG)
            )
        }
    ) {
        DatePicker(state = picker)
    }
}

private fun day(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

private fun AmountProblem.text() = when (this) {
    AmountProblem.NotAnAmount -> R.string.expense_entry_amount_not_amount
    AmountProblem.Zero -> R.string.expense_entry_amount_zero
    AmountProblem.TooLarge -> R.string.expense_entry_amount_too_large
}

private fun NoteProblem.text() = when (this) {
    NoteProblem.TooLong -> R.string.expense_entry_note_too_long
    NoteProblem.NotAllowed -> R.string.expense_entry_note_not_allowed
}

/** In the composer the text is still there, so it says so. */
private fun NoteProblem.composerText() = when (this) {
    NoteProblem.TooLong -> R.string.expense_entry_note_composer_too_long
    NoteProblem.NotAllowed -> R.string.expense_entry_note_not_allowed
}

private fun Missing.text(): Int? = when (this) {
    Missing.Amount -> null
    Missing.Category -> R.string.expense_entry_missing_category
    Missing.Note -> R.string.expense_entry_missing_note
    Missing.Repeat -> R.string.repeat_missing
    Missing.Changes -> R.string.expense_edit_no_changes
}

/** A correction that was refused left the expense as it was; a new one left nothing recorded. */
private fun EntryError.text(editing: Boolean) = when (this) {
    EntryError.NotSaved ->
        if (editing) R.string.expense_edit_not_saved else R.string.expense_entry_not_saved

    EntryError.CategoryGone ->
        if (editing) R.string.expense_edit_category_gone else R.string.expense_entry_category_gone

    EntryError.FutureDate ->
        if (editing) R.string.expense_edit_future_date else R.string.expense_entry_future_date

    EntryError.InvalidDate ->
        if (editing) R.string.expense_edit_invalid_date else R.string.expense_entry_invalid_date

    EntryError.DataChanged ->
        if (editing) R.string.expense_edit_data_changed else R.string.expense_entry_data_changed

    EntryError.Unknown -> R.string.expense_entry_unknown

    EntryError.ExpenseGone -> R.string.expense_edit_gone

    EntryError.ExpenseChanged -> R.string.expense_edit_changed

    EntryError.NotDeleted -> R.string.expense_edit_not_deleted

    EntryError.StartTooEarly -> R.string.repeat_start_too_early

    EntryError.EndTooEarly -> R.string.repeat_end_too_early

    EntryError.FollowingNotSaved -> R.string.following_not_saved
}

const val ENTRY_LOADING_TAG = "expense-entry:loading"
const val ENTRY_LOAD_FAILED_TAG = "expense-entry:load-failed"
const val ENTRY_AMOUNT_TAG = "expense-entry:amount"
const val ENTRY_CATEGORIES_TAG = "expense-entry:categories"
const val ENTRY_NEW_CATEGORY_TAG = "expense-entry:new-category"
const val ENTRY_DATE_TAG = "expense-entry:date"
const val ENTRY_DATE_CONFIRM_TAG = "expense-entry:date-confirm"
const val ENTRY_NOTE_TAG = "expense-entry:note"
const val ENTRY_KEYPAD_TAG = "expense-entry:keypad"

/** The most of the window the keypad and button may take. */
private const val PINNED_SHARE = 0.6f
const val ENTRY_MISSING_TAG = "expense-entry:missing"
const val ENTRY_ERROR_TAG = "expense-entry:error"
const val ENTRY_SUBMIT_TAG = "expense-entry:submit"
const val ENTRY_DELETE_TAG = "expense-entry:delete"
const val ENTRY_PAST_MONTHS_TAG = "expense-entry:past-months"
const val ENTRY_PAST_EXPENSE_ANNOUNCEMENT_TAG = "expense-entry:past-expense-announcement"
