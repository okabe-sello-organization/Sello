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
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FieldTone
import com.software.sello.designsystem.component.MoreMenu
import com.software.sello.designsystem.component.MoreMenuItem
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.rememberSelectedFirst
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Note
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.movement.MovementCloseButton
import com.software.sello.presentation.movement.MovementDatePicker
import com.software.sello.presentation.movement.MovementNoteLine
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatChoiceSheet
import com.software.sello.presentation.recurrence.RepeatRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val SCHEDULE_EDITOR_TAG = "schedule-editor"
const val SCHEDULE_SAVE_TAG = "schedule-editor:save"
const val SCHEDULE_PAUSE_TAG = "schedule-editor:pause"
const val SCHEDULE_RESUME_TAG = "schedule-editor:resume"
const val SCHEDULE_DELETE_TAG = "schedule-editor:delete"
const val SCHEDULE_STALE_TAG = "schedule-editor:stale"

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
 * One schedule in the entry's layout (docs/design/system/features/recurrence.html#screen-editor):
 * its amount, category or source, note and Se repite; that changes apply from tomorrow; Pausar or,
 * while paused, Reanudar; and, in the top bar's ⋮, deleting it, keeping what it recorded.
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
            MovementCloseButton({ onAction(ScheduleEditorAction.Back) })
        },
        actions = {
            if (!state.loading && !state.loadFailed) {
                MoreMenu(
                    listOf(
                        MoreMenuItem(
                            stringResource(
                                if (state.income) {
                                    R.string.schedule_delete_income
                                } else {
                                    R.string.schedule_delete_payment
                                }
                            ),
                            SelloIcon.Delete,
                            { onAction(ScheduleEditorAction.Delete) },
                            loss = true,
                            tag = SCHEDULE_DELETE_TAG
                        )
                    )
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> Column(Modifier.padding(padding)) {
                SlipSkeleton(Modifier.testTag("schedule-editor:loading"))
            }

            state.loadFailed -> Column(Modifier.padding(padding)) {
                StatusSlip(
                    kind = StatusKind.LoadFailed(),
                    message = stringResource(R.string.schedule_load_failed),
                    modifier = Modifier.testTag("schedule-editor:load-failed"),
                    action = StatusAction(StatusSlipDefaults.retryLabel) {
                        onAction(ScheduleEditorAction.Retry)
                    }
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
    // A refresh failed: the choices are those last read and the draft stays.
    if (state.stale) {
        StatusSlip(
            kind = StatusKind.Older,
            message = stringResource(R.string.recibo_stale),
            modifier = Modifier.testTag(SCHEDULE_STALE_TAG),
            action = StatusAction(StatusSlipDefaults.retryLabel) {
                onAction(ScheduleEditorAction.Retry)
            }
        )
    }
    if (state.income) {
        val order = rememberSelectedFirst(state.sourceKeys, state.sourceKey) { it }
        ChipRows(order.items.size) { index ->
            val key = order.items[index]
            SelloChip(
                incomeSourceLabel(key),
                state.sourceKey == key,
                {
                    order.picked()
                    onAction(ScheduleEditorAction.SourcePicked(key))
                },
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
        val order = rememberSelectedFirst(state.categories, state.categoryId) { it.id }
        ChipRows(order.items.size) { index ->
            val category = order.items[index]
            SelloChip(
                label = category.name,
                selected = category.id == state.categoryId,
                onClick = {
                    order.picked()
                    onAction(ScheduleEditorAction.CategoryPicked(category.id))
                },
                icon = CategoryIcons.iconFor(category.iconKey),
                enabled = state.editable
            )
        }
    }
    MovementNoteLine(
        state.note,
        state.editable,
        { onAction(ScheduleEditorAction.OpenComposer(ScheduleComposer.Note)) }
    )
    RepeatRow(
        state.repeat,
        income = state.income,
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
        // Pausar writes at once, so it is a button like Reanudar; the next date is in Se repite.
        SelloButton(
            text = stringResource(R.string.schedule_pause),
            onClick = { onAction(ScheduleEditorAction.Pause) },
            modifier = Modifier.fillMaxWidth()
                .padding(top = SelloTheme.spacing.sm)
                .testTag(SCHEDULE_PAUSE_TAG),
            kind = ButtonKind.Outlined,
            icon = SelloIcon.Pause,
            enabled = state.editable
        )
    }
}

@Composable
private fun Action(state: ScheduleEditorState, onAction: (ScheduleEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        state.error?.let {
            val unknown = it == ScheduleEditorError.Unknown
            FieldMessageText(
                FieldMessage(
                    stringResource(it.text()),
                    if (unknown) FieldTone.Warning else FieldTone.Error
                ),
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
            FieldMessageText(FieldMessage(stringResource(missing), tone = FieldTone.Hint))
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
                    ScheduleTextProblem.TooLong -> R.string.expense_entry_note_composer_too_long
                    ScheduleTextProblem.NotAllowed -> R.string.expense_entry_note_not_allowed
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
    MovementDatePicker(
        maxOf(state.start ?: first, first),
        onPicked = { onAction(ScheduleEditorAction.StartPicked(it)) },
        onDismiss = { onAction(ScheduleEditorAction.CloseStartPicker) },
        first = first
    )
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
