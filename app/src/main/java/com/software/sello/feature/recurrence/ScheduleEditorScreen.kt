package com.software.sello.feature.recurrence

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChipRows
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.DangerSlip
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Note
import com.software.sello.feature.expense.NoteProblem
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.recurrence.RepeatAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val SCHEDULE_EDITOR_TAG = "schedule-editor"
const val SCHEDULE_SAVE_TAG = "schedule-editor:save"
const val SCHEDULE_PAUSE_TAG = "schedule-editor:pause"
const val SCHEDULE_RESUME_TAG = "schedule-editor:resume"
const val SCHEDULE_DELETE_TAG = "schedule-editor:delete"

@Composable
fun ScheduleEditorRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val model: ScheduleEditorViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { model.onAction(ScheduleEditorAction.Back) }
    BackHandler(enabled = state.repeat.sheet != null) {
        model.onAction(ScheduleEditorAction.Repeat(RepeatAction.Dismiss))
    }
    ScheduleEditorScreen(state, dateLabels(), model::onAction)
}

/**
 * One schedule in the entry's layout (docs/design/v2/fijos.html): its amount, category or
 * source, note and Se repite; that changes apply from tomorrow; Pausar or, while paused,
 * Reanudar; and a danger card to delete it, keeping what it recorded.
 */
@Composable
fun ScheduleEditorScreen(
    state: ScheduleEditorState,
    dates: DateLabels,
    onAction: (ScheduleEditorAction) -> Unit
) {
    SelloScaffold(
        title = stringResource(
            if (state.income) R.string.schedule_income else R.string.schedule_payment
        ),
        modifier = Modifier.testTag(SCHEDULE_EDITOR_TAG),
        navigationIcon = {
            IconButton(onClick = { onAction(ScheduleEditorAction.Back) }) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        }
    ) { padding ->
        when {
            state.loading -> Column(Modifier.padding(padding)) {
                SlipSkeleton(Modifier.testTag("schedule-editor:loading"))
            }

            state.loadFailed -> Column(Modifier.padding(padding)) {
                ErrorSlip(
                    message = stringResource(R.string.schedule_load_failed),
                    onRetry = { onAction(ScheduleEditorAction.Retry) },
                    modifier = Modifier.testTag("schedule-editor:load-failed")
                )
            }

            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {},
                amount = { Amount(state, dates, onAction) },
                choices = { Choices(state, dates, onAction) },
                action = { Action(state, onAction) },
                keypad = {
                    AmountKeypad(
                        onDigit = { onAction(ScheduleEditorAction.Digit(it)) },
                        onTripleZero = { onAction(ScheduleEditorAction.TripleZero) },
                        onBackspace = { onAction(ScheduleEditorAction.Backspace) },
                        onClearAll = { onAction(ScheduleEditorAction.ClearAmount) },
                        enabled = state.editable,
                        compact = true
                    )
                }
            )
        }
    }
    state.composer?.let { Composer(state, it, onAction) }
    if (state.startPickerOpen) StartPicker(state, onAction)
    val start = state.start ?: state.today
    RepeatChoiceSheet(state.repeat, maxOf(start, state.today), state.today, dates) {
        onAction(ScheduleEditorAction.Repeat(it))
    }
    if (state.confirmDelete) {
        ConfirmSlip(
            title = stringResource(R.string.schedule_delete_confirm_title),
            message = AnnotatedString(
                stringResource(
                    if (state.income) {
                        R.string.schedule_delete_confirm_income
                    } else {
                        R.string.schedule_delete_confirm_payment
                    }
                )
            ),
            confirmLabel = stringResource(R.string.schedule_delete_action),
            onConfirm = { onAction(ScheduleEditorAction.DeleteConfirmed) },
            onDismiss = { onAction(ScheduleEditorAction.KeepIt) }
        )
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = stringResource(R.string.expense_edit_discard_title),
            message = AnnotatedString(stringResource(R.string.expense_edit_discard_message)),
            confirmLabel = stringResource(R.string.expense_entry_discard_confirm),
            onConfirm = { onAction(ScheduleEditorAction.DiscardConfirmed) },
            onDismiss = { onAction(ScheduleEditorAction.KeepEditing) },
            destructive = false,
            dismissLabel = stringResource(R.string.expense_edit_discard_keep)
        )
    }
}

@Composable
private fun Amount(
    state: ScheduleEditorState,
    dates: DateLabels,
    onAction: (ScheduleEditorAction) -> Unit
) {
    val start = state.start
    EntryAmountSlip(
        label = stringResource(
            if (state.income) R.string.schedule_income else R.string.schedule_payment
        ),
        amount = state.amount,
        error = if (state.atLimit) stringResource(R.string.expense_entry_amount_limit) else null,
        date = {
            if (start != null) {
                FieldPill(
                    text = stringResource(R.string.schedule_from, dates.day(start, state.today)),
                    icon = SelloIcon.Event,
                    onClick = { onAction(ScheduleEditorAction.OpenStartPicker) },
                    enabled = state.editable
                )
            }
        },
        icon = state.category?.let { CategoryIcons.iconFor(it.iconKey) },
        tone = if (state.income) ButtonTone.Gain else ButtonTone.Brand,
        limitPulse = state.limitPulse,
        rejectedText = state.amountText,
        onPaste = if (state.editable) {
            { onAction(ScheduleEditorAction.Paste(it)) }
        } else {
            null
        }
    )
}

@Composable
private fun Choices(
    state: ScheduleEditorState,
    dates: DateLabels,
    onAction: (ScheduleEditorAction) -> Unit
) {
    if (state.income) {
        ChipRows(state.sourceKeys.size) { index ->
            val key = state.sourceKeys[index]
            SelloChip(
                incomeSourceLabel(key),
                state.sourceKey == key,
                { onAction(ScheduleEditorAction.SourcePicked(key)) },
                enabled = state.editable
            )
        }
        if (state.sourceKey == OTHER) {
            EntryLine(
                text = state.otherName.ifBlank { stringResource(R.string.income_source_name) },
                icon = SelloIcon.AccountBalance,
                onClick = {
                    onAction(ScheduleEditorAction.OpenComposer(ScheduleComposer.SourceName))
                },
                action = stringResource(R.string.income_source_change),
                placeholder = state.otherName.isBlank(),
                enabled = state.editable
            )
        }
    } else {
        ChipRows(state.categories.size) { index ->
            val category = state.categories[index]
            SelloChip(
                label = category.name,
                selected = category.id == state.categoryId,
                onClick = { onAction(ScheduleEditorAction.CategoryPicked(category.id)) },
                icon = CategoryIcons.iconFor(category.iconKey),
                enabled = state.editable
            )
        }
    }
    EntryLine(
        text = state.note.ifBlank { stringResource(R.string.expense_entry_add_note) },
        icon = SelloIcon.EditNote,
        onClick = { onAction(ScheduleEditorAction.OpenComposer(ScheduleComposer.Note)) },
        action = stringResource(R.string.expense_entry_edit_note),
        placeholder = state.note.isBlank(),
        enabled = state.editable
    )
    RepeatRow(
        state.repeat,
        state.start ?: state.today,
        state.today,
        dates,
        state.editable,
        { onAction(ScheduleEditorAction.Repeat(it)) }
    )
    Text(
        stringResource(R.string.schedule_from_tomorrow),
        style = MaterialTheme.typography.bodySmall,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(top = SelloTheme.spacing.sm)
    )
    val pausedFrom = state.pausedFrom
    if (pausedFrom != null) {
        Slip(Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.sm), pinked = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.pausedByArchive?.let {
                        stringResource(R.string.schedule_paused_by_archive, it)
                    }
                        ?: stringResource(
                            R.string.schedule_paused_since,
                            dates.day(pausedFrom, state.today)
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Stamp(stringResource(R.string.schedules_paused), StampInk.Neutral)
            }
            Text(
                stringResource(R.string.schedule_paused_body),
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
            SelloButton(
                text = stringResource(R.string.schedule_resume),
                onClick = { onAction(ScheduleEditorAction.Resume) },
                kind = ButtonKind.Outlined,
                icon = SelloIcon.PlayArrow,
                enabled = state.editable,
                modifier = Modifier.fillMaxWidth().testTag(SCHEDULE_RESUME_TAG)
            )
        }
    } else {
        EntryLine(
            text = stringResource(R.string.schedule_pause),
            icon = SelloIcon.Pause,
            onClick = { onAction(ScheduleEditorAction.Pause) },
            modifier = Modifier.testTag(SCHEDULE_PAUSE_TAG),
            action = state.next?.let {
                stringResource(R.string.schedule_next, dates.day(it, state.today))
            },
            enabled = state.editable
        )
    }
    DangerSlip(
        title = stringResource(
            if (state.income) R.string.schedule_delete_income else R.string.schedule_delete_payment
        ),
        consequences = AnnotatedString(stringResource(R.string.schedule_delete_body)),
        actionLabel = stringResource(R.string.schedule_delete_action),
        onAction = { onAction(ScheduleEditorAction.Delete) },
        modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag(SCHEDULE_DELETE_TAG)
    )
}

@Composable
private fun Action(state: ScheduleEditorState, onAction: (ScheduleEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        state.error?.let {
            FieldMessageText(
                FieldMessage(stringResource(it.text())),
                Modifier.testTag("schedule-editor:error")
            )
        }
        val missing = when (state.missing) {
            ScheduleMissing.Category -> R.string.expense_entry_missing_category
            ScheduleMissing.SourceName -> R.string.income_missing_source
            ScheduleMissing.Note -> R.string.expense_entry_missing_note
            ScheduleMissing.Repeat -> R.string.repeat_missing
            ScheduleMissing.Changes -> R.string.expense_edit_no_changes
            else -> null
        }
        if (missing != null && !state.busy && state.error != ScheduleEditorError.Unknown) {
            FieldMessageText(FieldMessage(stringResource(missing), isError = false))
        }
        val unknown = state.error == ScheduleEditorError.Unknown
        SelloButton(
            text = stringResource(
                when {
                    unknown -> R.string.expense_entry_check_again
                    state.busy -> R.string.expense_entry_saving
                    else -> R.string.schedule_save
                }
            ),
            onClick = {
                onAction(if (unknown) ScheduleEditorAction.Retry else ScheduleEditorAction.Save)
            },
            icon = SelloIcon.Autorenew,
            enabled = state.canSave || unknown,
            loading = state.busy,
            tone = if (state.income) ButtonTone.Gain else ButtonTone.Brand,
            modifier = Modifier.fillMaxWidth().testTag(SCHEDULE_SAVE_TAG)
        )
    }
}

@Composable
private fun Composer(
    state: ScheduleEditorState,
    what: ScheduleComposer,
    onAction: (ScheduleEditorAction) -> Unit
) {
    val length = state.composerDraft.trim().let { it.codePointCount(0, it.length) }
    NoteComposer(
        context = AnnotatedString(
            stringResource(
                if (state.income) R.string.schedule_income else R.string.schedule_payment
            )
        ),
        value = state.composerDraft,
        onValueChange = { onAction(ScheduleEditorAction.ComposerChanged(it)) },
        counter = stringResource(
            R.string.expense_entry_note_counter,
            length,
            if (what == ScheduleComposer.Note) Note.MAX_CODE_POINTS else SOURCE_NAME_MAX
        ),
        hint = stringResource(
            if (what ==
                ScheduleComposer.Note
            ) {
                R.string.expense_entry_note_optional
            } else {
                R.string.income_source_hint
            }
        ),
        error = state.composerProblem?.let {
            stringResource(
                when (it) {
                    NoteProblem.TooLong -> R.string.expense_entry_note_composer_too_long
                    NoteProblem.NotAllowed -> R.string.expense_entry_note_not_allowed
                }
            )
        },
        doneLabel = stringResource(R.string.expense_entry_note_done),
        cancelLabel = stringResource(R.string.expense_entry_note_cancel),
        onDone = { onAction(ScheduleEditorAction.ComposerDone) },
        onCancel = { onAction(ScheduleEditorAction.ComposerCancelled) },
        contextIcon = SelloIcon.Autorenew
    )
}

/** New terms start after today; nothing earlier can be picked. */
@Composable
private fun StartPicker(state: ScheduleEditorState, onAction: (ScheduleEditorAction) -> Unit) {
    val first = state.today.plusDays(1)
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(state.start ?: first, first).atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !day(utcTimeMillis).isBefore(first)

            override fun isSelectableYear(year: Int) = year >= first.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(ScheduleEditorAction.CloseStartPicker) },
        confirmButton = {
            SelloButton(
                text = stringResource(R.string.expense_entry_date_confirm),
                onClick = {
                    val picked = picker.selectedDateMillis?.let(::day)
                    onAction(
                        if (picked == null) {
                            ScheduleEditorAction.CloseStartPicker
                        } else {
                            ScheduleEditorAction.StartPicked(picked)
                        }
                    )
                }
            )
        }
    ) {
        DatePicker(state = picker)
    }
}

private fun ScheduleEditorError.text() = when (this) {
    ScheduleEditorError.NotSaved -> R.string.schedule_error_not_saved
    ScheduleEditorError.DataChanged -> R.string.schedule_error_data_changed
    ScheduleEditorError.Unknown -> R.string.expense_entry_unknown
    ScheduleEditorError.ScheduleGone -> R.string.schedule_error_gone
    ScheduleEditorError.ScheduleChanged -> R.string.schedule_error_changed
    ScheduleEditorError.CatchUpPending -> R.string.schedule_error_catch_up
    ScheduleEditorError.CategoryGone -> R.string.schedule_error_category
    ScheduleEditorError.StartTooEarly -> R.string.schedule_error_start
    ScheduleEditorError.EndTooEarly -> R.string.repeat_end_too_early
}

private const val OTHER = "other"
private const val SOURCE_NAME_MAX = 24

private fun day(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
