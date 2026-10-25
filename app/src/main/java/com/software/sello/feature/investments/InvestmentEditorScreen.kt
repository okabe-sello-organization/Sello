package com.software.sello.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.DangerSlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FittedMoneyText
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.GoalIconCircle
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceStrip
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlidingKeypad
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Currency
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import java.time.Instant
import java.time.ZoneOffset

/**
 * The form's root. A created investment hands its id to [onCreated] so its detail can
 * open; a deletion calls [onDeleted] so the detail it came from is left too; a saved
 * edit or a discarded form calls [onClosed]. Back asks before a draft is thrown away.
 */
@Composable
fun InvestmentEditorRoot(
    viewModels: ViewModelProvider.Factory,
    onCreated: (String) -> Unit,
    onDeleted: () -> Unit,
    onClosed: () -> Unit
) {
    val model: InvestmentEditorViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) {
        when (val result = state.result) {
            is InvestmentEditorResult.Created -> onCreated(result.investmentId)
            InvestmentEditorResult.Deleted -> onDeleted()
            InvestmentEditorResult.Updated, InvestmentEditorResult.Discarded -> onClosed()
            null -> Unit
        }
    }
    BackHandler { model.onAction(InvestmentEditorAction.Back) }
    InvestmentEditorScreen(state, model::onAction)
}

/**
 * Nueva inversión and Editar inversión (v2 *Agregar / editar inversión*, *Dólares y la
 * tasa*): Crear meta's form in ink. The icon in a large circle beside the name, the
 * optional description and the icon strip; when creating, Moneda, the first
 * contribution as the big number with its date, Vale hoy, and the rate first for
 * dollars when none exists. Editing changes name, description and icon only and adds
 * Borrar esta inversión.
 */
@Composable
fun InvestmentEditorScreen(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit
) {
    val dates = dateLabels()
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.investment_editor_edit else R.string.investment_editor_new
        ),
        navigationIcon = {
            IconButton(
                onClick = { onAction(InvestmentEditorAction.Back) },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_CLOSE_TAG)
            ) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = SelloTheme.spacing.md)
                    .testTag(INVESTMENT_EDITOR_TAG),
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
            ) {
                when {
                    state.loading -> SlipSkeleton(Modifier.testTag("investment.editor.loading"))

                    state.loadFailed -> ErrorSlip(
                        stringResource(R.string.investment_editor_load_failed),
                        { onAction(InvestmentEditorAction.Retry) },
                        Modifier.testTag("investment.editor.failed")
                    )

                    else -> Form(state, onAction, dates)
                }
            }
            if (!state.loading && !state.loadFailed) {
                if (!state.padOpen) Submit(state, onAction)
                val field = state.padField
                val decimal = field == InvestmentField.Rate || state.currency == Currency.USD
                SlidingKeypad(
                    visible = state.padOpen,
                    doneLabel = stringResource(R.string.investment_editor_done),
                    onDone = { onAction(InvestmentEditorAction.ClosePad) },
                    onDigit = { onAction(InvestmentEditorAction.Digit(it)) },
                    onTripleZero = { onAction(InvestmentEditorAction.TripleZero) },
                    onBackspace = { onAction(InvestmentEditorAction.Backspace) },
                    onClearAll = { onAction(InvestmentEditorAction.Clear) },
                    modifier = Modifier.testTag(INVESTMENT_EDITOR_PAD_TAG),
                    onComma = if (decimal) {
                        { onAction(InvestmentEditorAction.Comma) }
                    } else {
                        null
                    }
                )
            }
        }
    }
    if (state.datePicker != null) FormDatePicker(state, onAction)
    if (state.confirmDelete) {
        ConfirmSlip(
            title = stringResource(R.string.investment_delete_confirm_title, state.name.trim()),
            message = deleteConsequences(state.movementCount),
            confirmLabel = stringResource(R.string.investment_delete_confirm),
            onConfirm = { onAction(InvestmentEditorAction.Delete) },
            onDismiss = { onAction(InvestmentEditorAction.CancelDelete) },
            busy = state.busy
        )
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = stringResource(R.string.investment_editor_discard_title),
            message = AnnotatedString(stringResource(R.string.investment_editor_discard_message)),
            confirmLabel = stringResource(R.string.investment_editor_discard_confirm),
            onConfirm = { onAction(InvestmentEditorAction.DiscardConfirmed) },
            onDismiss = { onAction(InvestmentEditorAction.KeepEditing) },
            dismissLabel = stringResource(R.string.investment_editor_discard_keep)
        )
    }
}

@Composable
private fun Submit(state: InvestmentEditorState, onAction: (InvestmentEditorAction) -> Unit) {
    val unknown = state.saveError == InvestmentSaveError.Unknown
    SelloButton(
        text = stringResource(
            when {
                unknown -> R.string.investment_editor_check
                state.editing -> R.string.investment_editor_save
                else -> R.string.investment_editor_create
            }
        ),
        onClick = {
            onAction(if (unknown) InvestmentEditorAction.Retry else InvestmentEditorAction.Submit)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .testTag(INVESTMENT_EDITOR_SUBMIT_TAG),
        icon = if (state.editing) SelloIcon.Check else SelloIcon.TrendingUp,
        enabled = state.canSubmit || (unknown && !state.busy),
        loading = state.busy,
        tone = ButtonTone.Ink
    )
}

/** "Se borran la inversión y sus **9 movimientos**. Esto no se puede deshacer." (IN23) */
@Composable
private fun deleteConsequences(count: Int): AnnotatedString {
    val movements = pluralStringResource(R.plurals.investment_delete_movements, count, count)
    val sentence = stringResource(R.string.investment_delete_consequences, movements)
    val start = sentence.indexOf(movements)
    return buildAnnotatedString {
        append(sentence)
        if (start >= 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + movements.length)
        }
    }
}

@Composable
private fun ColumnScope.Form(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit,
    dates: DateLabels
) {
    state.saveError?.let { error -> SaveError(error, onAction) }
    Identity(state, onAction)
    if (state.editing) {
        DangerSlip(
            title = stringResource(R.string.investment_delete_title),
            consequences = deleteConsequences(state.movementCount),
            actionLabel = stringResource(R.string.investment_delete_action),
            onAction = { onAction(InvestmentEditorAction.AskToDelete) },
            modifier = Modifier.testTag(INVESTMENT_EDITOR_DELETE_TAG)
        )
    } else {
        FirstContribution(state, onAction, dates)
        if (state.rateNeeded) {
            RateFirst(state, onAction, dates)
        } else if (state.currency == Currency.USD) {
            state.currentRate?.let { current ->
                FactLine(
                    AnnotatedString(
                        stringResource(
                            R.string.investment_editor_rate_line,
                            current.rate,
                            dates.shortDay(current.date, state.today)
                        )
                    ),
                    Modifier.padding(horizontal = SelloTheme.spacing.lg)
                        .testTag(INVESTMENT_EDITOR_RATE_LINE_TAG)
                )
            }
        }
    }
}

@Composable
private fun SaveError(error: InvestmentSaveError, onAction: (InvestmentEditorAction) -> Unit) {
    ErrorSlip(
        message = stringResource(
            when (error) {
                InvestmentSaveError.NotSaved -> R.string.investment_editor_not_saved
                InvestmentSaveError.Unknown -> R.string.investment_editor_unknown
                InvestmentSaveError.Changed -> R.string.investment_editor_changed
                InvestmentSaveError.FutureDate -> R.string.investment_editor_future_date
                InvestmentSaveError.Invalid -> R.string.investment_editor_invalid
                InvestmentSaveError.RateRequired -> R.string.investment_editor_rate_required
                InvestmentSaveError.Unchanged -> R.string.investment_editor_unchanged
            }
        ),
        onRetry = { onAction(InvestmentEditorAction.Retry) },
        modifier = Modifier.testTag(INVESTMENT_EDITOR_ERROR_TAG),
        retryLabel = stringResource(
            if (error == InvestmentSaveError.Unknown) {
                R.string.investment_editor_check
            } else {
                R.string.investment_editor_retry
            }
        )
    )
}

@Composable
private fun textMessage(error: InvestmentTextError?, hint: String, empty: Boolean): FieldMessage? =
    when (error) {
        InvestmentTextError.TooLong -> FieldMessage(
            stringResource(R.string.investment_editor_too_long)
        )

        InvestmentTextError.NotAllowed ->
            FieldMessage(stringResource(R.string.investment_editor_not_allowed))

        null -> if (empty) FieldMessage(hint, isError = false) else null
    }

/** The identity slip: icon circle, Nombre, Descripción, the icon strip and, creating, Moneda. */
@Composable
private fun Identity(state: InvestmentEditorState, onAction: (InvestmentEditorAction) -> Unit) {
    val choices = InvestmentIcons.options.map { (key, icon) ->
        IconChoice(key, icon, stringResource(InvestmentIcons.labelFor(key)))
    }
    Slip(Modifier.fillMaxWidth().testTag("investment.editor.identity"), pinked = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GoalIconCircle(
                state.iconKey?.let(InvestmentIcons::iconFor),
                tone = ButtonTone.Ink,
                emptyIcon = SelloIcon.TrendingUp
            )
            FormField(
                label = stringResource(R.string.investment_editor_name),
                value = state.name,
                onValueChange = { onAction(InvestmentEditorAction.NameChanged(it)) },
                modifier = Modifier.weight(1f).testTag(INVESTMENT_EDITOR_NAME_TAG),
                message = textMessage(
                    state.nameError,
                    stringResource(R.string.investment_editor_name_hint),
                    state.name.isEmpty()
                ),
                counter = stringResource(
                    R.string.investment_editor_counter,
                    state.nameLength,
                    InvestmentEditorState.MAX_NAME
                )
            )
        }
        FormField(
            label = stringResource(R.string.investment_editor_description),
            value = state.description,
            onValueChange = { onAction(InvestmentEditorAction.DescriptionChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .testTag(INVESTMENT_EDITOR_DESCRIPTION_TAG),
            message = textMessage(
                state.descriptionError,
                stringResource(R.string.investment_editor_description_hint),
                state.description.isEmpty()
            ),
            counter = stringResource(
                R.string.investment_editor_counter,
                state.descriptionLength,
                InvestmentEditorState.MAX_DESCRIPTION
            )
        )
        IconChoiceStrip(
            choices = choices,
            selectedId = state.iconKey,
            onPick = { onAction(InvestmentEditorAction.IconPicked(it)) },
            modifier = Modifier.padding(top = 8.dp).testTag("investment.editor.icons"),
            tone = ButtonTone.Ink
        )
        if (!state.editing) CurrencyChoice(state, onAction)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChoice(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit
) {
    Column(Modifier.padding(top = 12.dp).testTag(INVESTMENT_EDITOR_CURRENCY_TAG)) {
        Text(
            stringResource(R.string.investment_editor_currency),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            SelloChip(
                label = stringResource(R.string.investment_editor_pesos),
                selected = state.currency == Currency.COP,
                onClick = { onAction(InvestmentEditorAction.CurrencyPicked(Currency.COP)) },
                enabled = state.editable
            )
            SelloChip(
                label = stringResource(R.string.investment_editor_dollars),
                selected = state.currency == Currency.USD,
                onClick = { onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD)) },
                enabled = state.editable
            )
        }
        if (state.currency == Currency.USD) {
            FactLine(
                AnnotatedString(stringResource(R.string.investment_editor_dollars_fact)),
                Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** A label with its date pill on one line, wrapping under it when there is no room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelWithDate(label: String, date: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodyLarge,
            color = SelloTheme.colors.inkSoft
        )
        date()
    }
}

/** A figure typed on the pad: the big number, tapped to open the pad on it. */
@Composable
private fun Figure(
    value: MoneyTextValue?,
    spoken: String,
    tag: String,
    onOpen: () -> Unit,
    startSize: Int = 40
) {
    Column(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag(tag)
    ) {
        if (value != null) {
            FittedMoneyText(value, Modifier.padding(vertical = 4.dp), startSize = startSize)
        } else {
            Text(
                "0",
                style = MaterialTheme.typography.displaySmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun figureSpoken(label: String, value: MoneyTextValue?): String =
    value?.let { stringResource(R.string.investment_editor_figure_spoken, label, it.spoken) }
        ?: stringResource(R.string.investment_editor_figure_empty, label)

@Composable
private fun limitMessage(state: InvestmentEditorState, field: InvestmentField): FieldMessage? =
    if (state.atLimit == field) {
        FieldMessage(
            stringResource(
                if (state.currency == Currency.USD) {
                    R.string.investment_editor_limit_usd
                } else {
                    R.string.investment_editor_limit_cop
                }
            )
        )
    } else {
        null
    }

/** The pinked slip: Primer aporte with its date, the tear, then Vale hoy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstContribution(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit,
    dates: DateLabels
) {
    val label = stringResource(
        if (state.currency == Currency.USD) {
            R.string.investment_editor_first_usd
        } else {
            R.string.investment_editor_first
        }
    )
    Slip(Modifier.fillMaxWidth().testTag("investment.editor.first")) {
        LabelWithDate(label) {
            FieldPill(
                text = dates.day(state.date, state.today),
                icon = SelloIcon.Event,
                onClick = {
                    onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.First))
                },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_DATE_TAG),
                enabled = state.editable
            )
        }
        Figure(
            state.firstContribution,
            figureSpoken(label, state.firstContribution),
            INVESTMENT_EDITOR_FIRST_TAG,
            { onAction(InvestmentEditorAction.OpenPad(InvestmentField.First)) }
        )
        val message = limitMessage(state, InvestmentField.First)
            ?: if (state.firstContribution != null && !state.firstReady) {
                FieldMessage(stringResource(R.string.investment_editor_first_zero))
            } else {
                null
            }
        message?.let { FieldMessageText(it) }
        TearLine()
        val valueLabel = stringResource(R.string.investment_editor_value)
        Text(valueLabel, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            SelloChip(
                label = stringResource(R.string.investment_editor_same),
                selected = state.sameValue,
                onClick = { onAction(InvestmentEditorAction.SameValuePicked(true)) },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_SAME_TAG),
                enabled = state.editable
            )
            SelloChip(
                label = stringResource(R.string.investment_editor_other),
                selected = !state.sameValue,
                onClick = { onAction(InvestmentEditorAction.SameValuePicked(false)) },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_OTHER_TAG),
                enabled = state.editable
            )
        }
        if (!state.sameValue) {
            Figure(
                state.otherValue,
                figureSpoken(valueLabel, state.otherValue),
                INVESTMENT_EDITOR_VALUE_TAG,
                { onAction(InvestmentEditorAction.OpenPad(InvestmentField.Value)) },
                startSize = 31
            )
            limitMessage(state, InvestmentField.Value)?.let { FieldMessageText(it) }
        }
    }
}

/** Rate first (rule 12, IN33): a dollar investment and no rate yet. */
@Composable
private fun RateFirst(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit,
    dates: DateLabels
) {
    val label = stringResource(R.string.investment_editor_rate_ask)
    Slip(Modifier.fillMaxWidth().testTag(INVESTMENT_EDITOR_RATE_SLIP_TAG)) {
        LabelWithDate(label) {
            FieldPill(
                text = dates.day(state.rateDate, state.today),
                icon = SelloIcon.Event,
                onClick = { onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.Rate)) },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_RATE_DATE_TAG),
                enabled = state.editable
            )
        }
        val rate = state.rate
        Figure(
            rate?.let {
                MoneyTextValue(
                    MoneySign.None,
                    "",
                    it,
                    stringResource(R.string.usd_rate_entry_spoken, it)
                )
            },
            rate?.let { stringResource(R.string.investment_editor_rate_spoken, it) }
                ?: stringResource(R.string.investment_editor_rate_empty),
            INVESTMENT_EDITOR_RATE_TAG,
            { onAction(InvestmentEditorAction.OpenPad(InvestmentField.Rate)) },
            startSize = 31
        )
        when {
            state.rateProblem == RateProblem.BelowMinimum -> R.string.usd_rate_below
            state.rateProblem == RateProblem.AboveMaximum -> R.string.usd_rate_above
            state.atLimit == InvestmentField.Rate -> R.string.usd_rate_limit
            else -> null
        }?.let { FieldMessageText(FieldMessage(stringResource(it))) }
        FactLine(
            AnnotatedString(stringResource(R.string.investment_editor_rate_fact)),
            Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDatePicker(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit
) {
    val shown = if (state.datePicker == InvestmentField.Rate) state.rateDate else state.date
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = shown.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC).toLocalDate().isAfter(state.today)

            override fun isSelectableYear(year: Int) = year <= state.today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(InvestmentEditorAction.CloseDatePicker) },
        confirmButton = {
            SelloButton(stringResource(R.string.expense_entry_date_confirm), {
                picker.selectedDateMillis?.let {
                    onAction(
                        InvestmentEditorAction.DatePicked(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    )
                } ?: onAction(InvestmentEditorAction.CloseDatePicker)
            })
        }
    ) { DatePicker(picker) }
}

const val INVESTMENT_EDITOR_TAG = "investment.editor"
const val INVESTMENT_EDITOR_CLOSE_TAG = "investment.editor.close"
const val INVESTMENT_EDITOR_NAME_TAG = "investment.editor.name"
const val INVESTMENT_EDITOR_DESCRIPTION_TAG = "investment.editor.description"
const val INVESTMENT_EDITOR_CURRENCY_TAG = "investment.editor.currency"
const val INVESTMENT_EDITOR_FIRST_TAG = "investment.editor.firstAmount"
const val INVESTMENT_EDITOR_DATE_TAG = "investment.editor.date"
const val INVESTMENT_EDITOR_SAME_TAG = "investment.editor.same"
const val INVESTMENT_EDITOR_OTHER_TAG = "investment.editor.other"
const val INVESTMENT_EDITOR_VALUE_TAG = "investment.editor.value"
const val INVESTMENT_EDITOR_RATE_SLIP_TAG = "investment.editor.rateSlip"
const val INVESTMENT_EDITOR_RATE_TAG = "investment.editor.rate"
const val INVESTMENT_EDITOR_RATE_DATE_TAG = "investment.editor.rateDate"
const val INVESTMENT_EDITOR_RATE_LINE_TAG = "investment.editor.rateLine"
const val INVESTMENT_EDITOR_PAD_TAG = "investment.editor.pad"
const val INVESTMENT_EDITOR_SUBMIT_TAG = "investment.editor.submit"
const val INVESTMENT_EDITOR_DELETE_TAG = "investment.editor.delete"
const val INVESTMENT_EDITOR_ERROR_TAG = "investment.editor.error"
