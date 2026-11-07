package com.software.sello.presentation.movement

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.MovementCloseButton as CloseButtonPart
import com.software.sello.designsystem.component.MovementDatePicker as DatePickerPart
import com.software.sello.designsystem.component.MovementDiscardSlip as DiscardSlipPart
import com.software.sello.designsystem.component.MovementNoteComposer as NoteComposerPart
import com.software.sello.designsystem.component.MovementNoteLine as NoteLinePart
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.domain.model.Note
import java.time.LocalDate

/*
 * The entry and movement forms' shared parts, given the app's copy and rules.
 * What they look like is the design system's (component/Movement.kt); this only chooses
 * the words, so the forms keep calling them as before.
 */

/** The form's close button; not [enabled] while the form saves. */
@Composable
fun MovementCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    CloseButtonPart(stringResource(R.string.expense_entry_close), onClick, modifier, enabled)
}

/** The note line under the choices: the note, or the invitation to add one; [trailing] at its end. */
@Composable
fun MovementNoteLine(
    note: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    NoteLinePart(
        note = note,
        addLabel = stringResource(R.string.expense_entry_add_note),
        editLabel = stringResource(R.string.expense_entry_edit_note),
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        trailing = trailing
    )
}

/** Asks before a typed movement is thrown away. */
@Composable
fun MovementDiscardSlip(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DiscardSlipPart(
        title = title,
        message = stringResource(R.string.expense_entry_discard_message),
        confirmLabel = stringResource(R.string.expense_entry_discard_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * The note composer over the form, its context the movement's [kind] and, once there is
 * one, its signed [figure] in bold. [spoken] names what the note is for when read aloud,
 * followed by [amountSpoken].
 */
@Composable
fun MovementNoteComposer(
    kind: String,
    figure: String?,
    draft: String,
    length: Int,
    problem: MovementNoteProblem?,
    icon: SelloIcon,
    spoken: String,
    amountSpoken: String?,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    NoteComposerPart(
        kind = kind,
        figure = figure,
        draft = draft,
        counter = stringResource(R.string.expense_entry_note_counter, length, Note.MAX_CODE_POINTS),
        hint = stringResource(R.string.expense_entry_note_optional),
        error = when (problem) {
            MovementNoteProblem.TooLong ->
                stringResource(R.string.expense_entry_note_composer_too_long)

            MovementNoteProblem.NotAllowed -> stringResource(R.string.income_text_not_allowed)

            null -> null
        },
        doneLabel = stringResource(R.string.expense_entry_note_done),
        cancelLabel = stringResource(R.string.expense_entry_note_cancel),
        icon = icon,
        spoken = spoken,
        amountSpoken = amountSpoken,
        onChange = onChange,
        onDone = onDone,
        onCancel = onCancel
    )
}

/**
 * A form's date picker, opened on [date]: only days from [first] to [last] can be picked,
 * either end open when null. A movement already made passes today as [last].
 */
@Composable
fun MovementDatePicker(
    date: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    first: LocalDate? = null,
    last: LocalDate? = null
) {
    DatePickerPart(
        date = date,
        confirmLabel = stringResource(R.string.expense_entry_date_confirm),
        onPicked = onPicked,
        onDismiss = onDismiss,
        first = first,
        last = last
    )
}
