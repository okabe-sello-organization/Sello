package com.software.sello.feature.income

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
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
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StampSize
import com.software.sello.designsystem.component.selloWindowLayout
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.SourceName
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.month.monthNames
import com.software.sello.presentation.recurrence.FollowingChoice
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatChoiceSheet
import com.software.sello.presentation.recurrence.RepeatRow
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun IncomeFormRoot(
    viewModels: ViewModelProvider.Factory,
    onClosed: () -> Unit,
    onExpense: (() -> Unit)? = null
) {
    val model: IncomeFormViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    // Once per saved income and before the form closes; the caller says what was saved.
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
    BackHandler { model.onAction(IncomeFormAction.Back) }
    // While Se repite's sheet is open, Back closes it and keeps the choice as it was.
    BackHandler(enabled = state.repeat.sheet != null) {
        model.onAction(IncomeFormAction.Repeat(RepeatAction.Dismiss))
    }
    IncomeFormScreen(state, model::onAction, onExpense = onExpense)
}

@Composable
fun IncomeFormScreen(
    state: IncomeFormState,
    onAction: (IncomeFormAction) -> Unit,
    modifier: Modifier = Modifier,
    onExpense: (() -> Unit)? = null
) {
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.income_edit_title else R.string.income_entry_title
        ),
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = { onAction(IncomeFormAction.Back) }) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        },
        actions = {
            if (state.editing) {
                SelloButton(
                    stringResource(R.string.expense_edit_delete),
                    { onAction(IncomeFormAction.Delete) },
                    kind = ButtonKind.Text,
                    enabled = state.canDelete,
                    modifier = Modifier.testTag("income.delete")
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> IncomePane(padding) {
                SlipSkeleton(Modifier.testTag("income.loading"))
            }

            state.loadFailed -> IncomePane(padding) {
                ErrorSlip(
                    stringResource(R.string.income_load_failed),
                    { onAction(IncomeFormAction.Retry) },
                    modifier = Modifier.testTag("income.loadFailed")
                )
            }

            // The expense form's layout, in green: the amount, action and pad hold still.
            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {
                    if (!state.editing && onExpense != null) {
                        SegmentedSwitch(
                            listOf(
                                stringResource(R.string.entry_expense),
                                stringResource(R.string.entry_income)
                            ),
                            1,
                            { if (it == 0 && state.editable) onExpense() },
                            Modifier.testTag("entry.kind"),
                            enabled = state.editable
                        )
                    }
                },
                amount = { IncomeAmount(state, onAction) },
                choices = { IncomeChoices(state, onAction) },
                action = { IncomeAction(state, onAction) },
                keypad = {
                    AmountKeypad(
                        { onAction(IncomeFormAction.Digit(it)) },
                        { onAction(IncomeFormAction.TripleZero) },
                        { onAction(IncomeFormAction.Backspace) },
                        { onAction(IncomeFormAction.ClearAmount) },
                        enabled = state.editable,
                        modifier = Modifier.testTag("income.keypad"),
                        compact = true
                    )
                }
            )
        }
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = stringResource(R.string.income_discard_title),
            message = AnnotatedString(stringResource(R.string.expense_entry_discard_message)),
            confirmLabel = stringResource(R.string.expense_entry_discard_confirm),
            onConfirm = { onAction(IncomeFormAction.DiscardConfirmed) },
            onDismiss = { onAction(IncomeFormAction.KeepEditing) }
        )
    }
    val dates = dateLabels()
    if (state.datePickerOpen) IncomeDatePicker(state, onAction)
    state.composer?.let { IncomeComposerSheet(state, it, onAction) }
    RepeatChoiceSheet(state.repeat, state.date, state.today, dates) {
        onAction(IncomeFormAction.Repeat(it))
    }
    val following = state.following
    if (state.askFollowing && following != null) {
        FollowingChoice(
            following,
            income = true,
            busy = state.busy,
            onChoose = { onAction(IncomeFormAction.FollowingChosen(it)) },
            onBack = { onAction(IncomeFormAction.FollowingDismissed) }
        )
    }
}

@Composable
private fun IncomePane(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState()).padding(SelloTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) { content() }
}

/** "Ingreso en pesos", the date, and the amount in gain green with a plus. */
@Composable
private fun IncomeAmount(state: IncomeFormState, onAction: (IncomeFormAction) -> Unit) {
    val day = dateLabels().day(state.date, state.today)
    EntryAmountSlip(
        label = stringResource(R.string.income_amount_label),
        // The plus is drawn; what is read stays the amount itself.
        amount = state.amount?.copy(sign = MoneySign.Plus),
        modifier = Modifier.testTag("income.amount"),
        error = when {
            state.atLimit -> stringResource(R.string.expense_entry_amount_limit)
            state.amountProblem != null -> stringResource(R.string.income_amount_invalid)
            else -> null
        },
        date = {
            FieldPill(
                text = day,
                icon = SelloIcon.Event,
                onClick = { onAction(IncomeFormAction.OpenDatePicker) },
                modifier = Modifier.testTag("income.date"),
                enabled = state.editable
            )
        },
        icon = SelloIcon.AccountBalance,
        tone = ButtonTone.Gain,
        limitPulse = state.limitPulse,
        rejectedText = state.amountText,
        onPaste = if (state.editable) {
            { onAction(IncomeFormAction.Paste(it)) }
        } else {
            null
        }
    )
}

/** Source chips (words only), Otro's name, the note, and what a correction changes. */
@Composable
private fun IncomeChoices(state: IncomeFormState, onAction: (IncomeFormAction) -> Unit) {
    ChipRows(state.sourceKeys.size, Modifier.testTag("income.sources")) { index ->
        val key = state.sourceKeys[index]
        SelloChip(
            incomeSourceLabel(key),
            state.sourceKey == key,
            { onAction(IncomeFormAction.SourcePicked(key)) },
            enabled = state.editable,
            modifier = Modifier.testTag("income.source.$key")
        )
    }
    if (state.needsOtherName) {
        EntryLine(
            text = state.otherName.ifBlank { stringResource(R.string.income_source_name) },
            icon = SelloIcon.AccountBalance,
            onClick = { onAction(IncomeFormAction.OpenComposer(IncomeComposer.SourceName)) },
            modifier = Modifier.testTag("income.other"),
            action = stringResource(R.string.income_source_change),
            placeholder = state.otherName.isBlank(),
            enabled = state.editable
        )
        state.otherNameProblem?.let {
            FieldMessageText(FieldMessage(stringResource(R.string.income_source_invalid)))
        }
    }
    EntryLine(
        text = state.note.ifBlank { stringResource(R.string.expense_entry_add_note) },
        icon = SelloIcon.EditNote,
        onClick = { onAction(IncomeFormAction.OpenComposer(IncomeComposer.Note)) },
        modifier = Modifier.testTag("income.note"),
        action = stringResource(R.string.expense_entry_edit_note),
        placeholder = state.note.isBlank(),
        enabled = state.editable
    )
    state.noteProblem?.let {
        FieldMessageText(FieldMessage(stringResource(R.string.income_note_invalid)))
    }
    // Se repite, for a new income only: a recorded one is corrected on its own.
    if (!state.editing) {
        RepeatRow(
            state.repeat,
            state.date,
            state.today,
            dateLabels(),
            state.editable,
            { onAction(IncomeFormAction.Repeat(it)) }
        )
    }
    Text(
        stringResource(R.string.income_history_guidance),
        style = MaterialTheme.typography.bodySmall,
        color = SelloTheme.colors.inkSoft
    )
    if (state.pastMonths.isNotEmpty()) {
        val months = monthNames()
        Text(
            stringResource(
                R.string.income_past_months,
                state.pastMonths.joinToString { months.title(it) }
            ),
            modifier = Modifier.testTag("income.pastMonths")
        )
    }
}

/** What went wrong, then the one button, held with the pad so neither moves. */
@Composable
private fun IncomeAction(state: IncomeFormState, onAction: (IncomeFormAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        state.error?.let { error ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        when (error) {
                            IncomeFormError.Unknown -> R.string.expense_entry_unknown
                            IncomeFormError.IncomeGone -> R.string.income_gone
                            IncomeFormError.IncomeChanged -> R.string.income_changed
                            IncomeFormError.DataChanged -> R.string.income_data_changed
                            IncomeFormError.FutureDate -> R.string.expense_entry_future_date
                            IncomeFormError.InvalidDate -> R.string.expense_entry_invalid_date
                            IncomeFormError.NotDeleted -> R.string.income_not_deleted
                            IncomeFormError.StartTooEarly -> R.string.repeat_start_too_early
                            IncomeFormError.EndTooEarly -> R.string.repeat_end_too_early
                            IncomeFormError.FollowingNotSaved -> R.string.following_not_saved
                            else -> R.string.income_not_saved
                        }
                    )
                ),
                Modifier.testTag("income.error")
            )
            // The following incomes already changed; only this one is still to save.
            if (state.followingSaved) {
                FieldMessageText(FieldMessage(stringResource(R.string.following_saved_this_not)))
            }
        }
        // What is still missing, said where the button is. A missing amount is not: the
        // slip already shows "Sin monto".
        val missing = when (state.missing) {
            IncomeMissing.SourceName -> R.string.income_missing_source
            IncomeMissing.Note -> R.string.expense_entry_missing_note
            IncomeMissing.Repeat -> R.string.repeat_missing
            IncomeMissing.Changes -> R.string.expense_edit_no_changes
            else -> null
        }
        if (missing != null && !state.busy && state.error != IncomeFormError.Unknown) {
            FieldMessageText(
                FieldMessage(stringResource(missing), isError = false),
                Modifier.testTag("income.missing")
            )
        }
        val unknown = state.error == IncomeFormError.Unknown
        SelloButton(
            text = stringResource(
                when {
                    unknown -> R.string.expense_entry_check_again
                    state.busy -> R.string.expense_entry_saving
                    state.editing -> R.string.expense_edit_submit
                    state.repeat.choice != null -> R.string.repeat_save_income
                    else -> R.string.income_save
                }
            ),
            onClick = {
                onAction(if (unknown) IncomeFormAction.Retry else IncomeFormAction.Submit)
            },
            enabled = state.canSubmit || unknown,
            loading = state.busy,
            tone = ButtonTone.Gain,
            modifier = Modifier.fillMaxWidth().testTag("income.submit")
        )
    }
}

/** The note or Otro's name, written above the keyboard; over its limit it cannot finish. */
@Composable
private fun IncomeComposerSheet(
    state: IncomeFormState,
    what: IncomeComposer,
    onAction: (IncomeFormAction) -> Unit
) {
    val note = what == IncomeComposer.Note
    val limit = if (note) Note.MAX_CODE_POINTS else SourceName.MAX_CODE_POINTS
    val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)
    val context = buildAnnotatedString {
        if (note) {
            append(stringResource(R.string.entry_income))
            state.amount?.let {
                append(" · ")
                withStyle(facts) { append("+" + it.digits) }
            }
        } else {
            append(stringResource(R.string.income_source_name))
        }
    }
    val spoken = if (note) {
        listOfNotNull(stringResource(R.string.income_note_spoken), state.amount?.spoken)
            .joinToString(", ")
    } else {
        stringResource(R.string.income_source_name)
    }
    NoteComposer(
        context = context,
        value = state.composerDraft,
        onValueChange = { onAction(IncomeFormAction.ComposerChanged(it)) },
        counter = stringResource(R.string.expense_entry_note_counter, state.composerLength, limit),
        hint = stringResource(
            if (note) R.string.expense_entry_note_optional else R.string.income_source_hint
        ),
        error = state.composerProblem?.let {
            when {
                it == IncomeTextProblem.NotAllowed -> stringResource(
                    R.string.income_text_not_allowed
                )

                note -> stringResource(R.string.expense_entry_note_composer_too_long)

                else -> stringResource(R.string.income_source_composer_too_long)
            }
        },
        doneLabel = stringResource(R.string.expense_entry_note_done),
        cancelLabel = stringResource(R.string.expense_entry_note_cancel),
        onDone = { onAction(IncomeFormAction.ComposerDone) },
        onCancel = { onAction(IncomeFormAction.ComposerCancelled) },
        contextIcon = SelloIcon.AccountBalance,
        contextDescription = spoken
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeDatePicker(state: IncomeFormState, onAction: (IncomeFormAction) -> Unit) {
    val repeating = state.repeat.choice != null
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = state.date.atStartOfDay(
            ZoneOffset.UTC
        ).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            // One income is dated today or earlier; while Se repite is on, today or later.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return if (repeating) !day.isBefore(state.today) else !day.isAfter(state.today)
            }

            override fun isSelectableYear(year: Int) =
                if (repeating) year >= state.today.year else year <= state.today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(IncomeFormAction.CloseDatePicker) },
        confirmButton = {
            SelloButton(stringResource(R.string.expense_entry_date_confirm), {
                picker.selectedDateMillis?.let {
                    onAction(
                        IncomeFormAction.DatePicked(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    )
                } ?: onAction(IncomeFormAction.CloseDatePicker)
            })
        }
    ) { DatePicker(picker) }
}
