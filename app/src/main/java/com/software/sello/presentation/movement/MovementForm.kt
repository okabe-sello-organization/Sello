package com.software.sello.presentation.movement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.software.sello.R
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * The parts the goal and investment movement forms draw the same way. Each form keeps
 * its own layout, copy, kinds and rules; these only say the shared parts once.
 */

/** The form's close button. */
@Composable
fun MovementCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
    }
}

/** A scrolling column for the loading and failed states, padded like the form. */
@Composable
fun MovementPane(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState()).padding(SelloTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) { content() }
}

/** The note line under the choices: the note, or the invitation to add one. */
@Composable
fun MovementNoteLine(
    note: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EntryLine(
        text = note.ifBlank { stringResource(R.string.expense_entry_add_note) },
        icon = SelloIcon.EditNote,
        onClick = onClick,
        modifier = modifier,
        action = stringResource(R.string.expense_entry_edit_note),
        placeholder = note.isBlank(),
        enabled = enabled
    )
}

/** Asks before a typed movement is thrown away. */
@Composable
fun MovementDiscardSlip(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmSlip(
        title = title,
        message = AnnotatedString(stringResource(R.string.expense_entry_discard_message)),
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
    val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)
    NoteComposer(
        context = buildAnnotatedString {
            append(kind)
            figure?.let {
                append(" · ")
                withStyle(facts) { append(it) }
            }
        },
        value = draft,
        onValueChange = onChange,
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
        onDone = onDone,
        onCancel = onCancel,
        contextIcon = icon,
        contextDescription = listOfNotNull(spoken, amountSpoken).joinToString(", ")
    )
}

/** The date picker of a movement, which offers no day after [today]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementDatePicker(
    date: LocalDate,
    today: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)

            override fun isSelectableYear(year: Int) = year <= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SelloButton(stringResource(R.string.expense_entry_date_confirm), {
                picker.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            })
        }
    ) { DatePicker(picker) }
}
