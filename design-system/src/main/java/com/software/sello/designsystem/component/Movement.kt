package com.software.sello.designsystem.component

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
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * The parts the goal and investment movement forms draw the same way. Each form keeps
 * its own layout, copy, kinds and rules; these only say the shared parts once. Every
 * text arrives written; the application chooses it.
 */

/** The form's close button, an X read as [label]. */
@Composable
fun MovementCloseButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(SelloIcon.Close.painter(), label)
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

/**
 * The note line under the choices: the [note], or [addLabel] as an invitation while it is
 * blank; [editLabel] names the action.
 */
@Composable
fun MovementNoteLine(
    note: String,
    addLabel: String,
    editLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EntryLine(
        text = note.ifBlank { addLabel },
        icon = SelloIcon.EditNote,
        onClick = onClick,
        modifier = modifier,
        action = editLabel,
        placeholder = note.isBlank(),
        enabled = enabled
    )
}

/**
 * Asks before a typed movement is thrown away: [message], then [confirmLabel] in loss ink,
 * or [dismissLabel] to keep it. It is a [ConfirmSlip].
 */
@Composable
fun MovementDiscardSlip(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.sello_cancel)
) {
    ConfirmSlip(
        title = title,
        message = AnnotatedString(message),
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = dismissLabel
    )
}

/**
 * The note composer over the form, its context the movement's [kind] and, once there is
 * one, its signed [figure] in bold. [spoken] names what the note is for when read aloud,
 * followed by [amountSpoken]. [counter], [hint], [error] and the two labels are the
 * [NoteComposer]'s, already written.
 */
@Composable
fun MovementNoteComposer(
    kind: String,
    figure: String?,
    draft: String,
    counter: String,
    hint: String,
    error: String?,
    doneLabel: String,
    cancelLabel: String,
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
        counter = counter,
        hint = hint,
        error = error,
        doneLabel = doneLabel,
        cancelLabel = cancelLabel,
        onDone = onDone,
        onCancel = onCancel,
        contextIcon = icon,
        contextDescription = listOfNotNull(spoken, amountSpoken).joinToString(", ")
    )
}

/** The date picker of a movement, which offers no day after [today]; [confirmLabel] picks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementDatePicker(
    date: LocalDate,
    today: LocalDate,
    confirmLabel: String,
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
            SelloButton(confirmLabel, {
                picker.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            })
        }
    ) { DatePicker(picker) }
}
