package com.software.sello.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.dateLabels
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun UsdRateEntryRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val model: UsdRateEntryViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { model.onAction(UsdRateEntryAction.Back) }
    UsdRateEntryScreen(state, model::onAction)
}

/**
 * Cambiar la tasa (v2 *Dólares y la tasa*): the entry layout with "Pesos por 1 dólar",
 * the rate typed with the comma pad, its date (today or earlier), one fact about the
 * current rate and **Guardar tasa**. 1,00 to 100.000,00, two decimals at most (rule 11).
 */
@Composable
fun UsdRateEntryScreen(state: UsdRateEntryState, onAction: (UsdRateEntryAction) -> Unit) {
    SelloScaffold(
        title = stringResource(R.string.usd_rate_entry_title),
        navigationIcon = {
            IconButton(
                onClick = { onAction(UsdRateEntryAction.Back) },
                modifier = Modifier.testTag(USD_RATE_ENTRY_CLOSE_TAG)
            ) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        }
    ) { padding ->
        when {
            state.loading -> Column(
                Modifier.fillMaxSize().padding(padding).padding(SelloTheme.spacing.lg)
            ) {
                SlipSkeleton(Modifier.testTag("usd.rate.entry.loading"))
            }

            state.loadFailed -> Column(
                Modifier.fillMaxSize().padding(padding).padding(SelloTheme.spacing.lg)
            ) {
                ErrorSlip(
                    stringResource(R.string.usd_rate_failed_body),
                    { onAction(UsdRateEntryAction.Retry) },
                    Modifier.testTag("usd.rate.entry.failed"),
                    title = stringResource(R.string.usd_rate_failed_title)
                )
            }

            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {},
                amount = { Amount(state, onAction) },
                choices = { Fact(state) },
                action = { Action(state, onAction) },
                keypad = {
                    AmountKeypad(
                        { onAction(UsdRateEntryAction.Digit(it)) },
                        {},
                        { onAction(UsdRateEntryAction.Backspace) },
                        { onAction(UsdRateEntryAction.Clear) },
                        enabled = state.editable,
                        modifier = Modifier.testTag(USD_RATE_ENTRY_KEYPAD_TAG),
                        compact = true,
                        onComma = { onAction(UsdRateEntryAction.Comma) }
                    )
                }
            )
        }
    }
    if (state.datePickerOpen) RateDatePicker(state, onAction)
}

@Composable
private fun Amount(state: UsdRateEntryState, onAction: (UsdRateEntryAction) -> Unit) {
    val day = dateLabels().day(state.date, state.today)
    EntryAmountSlip(
        label = stringResource(R.string.usd_rate_entry_label),
        amount = state.rate?.let {
            MoneyTextValue(
                MoneySign.None,
                "",
                it,
                stringResource(R.string.usd_rate_entry_spoken, it)
            )
        },
        modifier = Modifier.testTag(USD_RATE_ENTRY_AMOUNT_TAG),
        error = when {
            state.problem == RateProblem.BelowMinimum -> stringResource(R.string.usd_rate_below)
            state.problem == RateProblem.AboveMaximum -> stringResource(R.string.usd_rate_above)
            state.atLimit -> stringResource(R.string.usd_rate_limit)
            else -> null
        },
        date = {
            FieldPill(
                text = day,
                icon = SelloIcon.Event,
                onClick = { onAction(UsdRateEntryAction.OpenDatePicker) },
                modifier = Modifier.testTag(USD_RATE_ENTRY_DATE_TAG),
                enabled = state.editable
            )
        },
        icon = SelloIcon.Autorenew,
        tone = ButtonTone.Ink,
        limitPulse = state.limitPulse
    )
}

/** "La de hoy es 4.150,25, del 20 oct. …", or what the rate is for when there is none. */
@Composable
private fun Fact(state: UsdRateEntryState) {
    val current = state.current
    val bold = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.W800)
    FactLine(
        if (current == null) {
            buildAnnotatedString { append(stringResource(R.string.usd_rate_entry_first)) }
        } else {
            val day = dateLabels().shortDay(current.date, state.today)
            val text = stringResource(R.string.usd_rate_entry_fact, current.rate, day)
            buildAnnotatedString {
                val at = text.indexOf(current.rate)
                append(text.substring(0, at))
                withStyle(bold) { append(current.rate) }
                append(text.substring(at + current.rate.length))
            }
        },
        Modifier.testTag("usd.rate.entry.fact")
    )
}

@Composable
private fun Action(state: UsdRateEntryState, onAction: (UsdRateEntryAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        state.error?.let { error ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        when (error) {
                            RateEntryError.Unknown -> R.string.expense_entry_unknown
                            RateEntryError.FutureDate -> R.string.expense_entry_future_date
                            RateEntryError.DataChanged -> R.string.usd_rate_data_changed
                            RateEntryError.NotSaved -> R.string.usd_rate_not_saved
                        }
                    )
                ),
                Modifier.testTag("usd.rate.entry.error")
            )
        }
        val unknown = state.error == RateEntryError.Unknown
        SelloButton(
            text = stringResource(
                when {
                    unknown -> R.string.expense_entry_check_again
                    state.busy -> R.string.expense_entry_saving
                    else -> R.string.usd_rate_save
                }
            ),
            onClick = {
                onAction(if (unknown) UsdRateEntryAction.Retry else UsdRateEntryAction.Submit)
            },
            enabled = state.canSubmit || unknown,
            loading = state.busy,
            tone = ButtonTone.Ink,
            modifier = Modifier.fillMaxWidth().testTag(USD_RATE_ENTRY_SUBMIT_TAG)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateDatePicker(state: UsdRateEntryState, onAction: (UsdRateEntryAction) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis =
            state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC).toLocalDate().isAfter(state.today)

            override fun isSelectableYear(year: Int) = year <= state.today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(UsdRateEntryAction.CloseDatePicker) },
        confirmButton = {
            SelloButton(stringResource(R.string.expense_entry_date_confirm), {
                picker.selectedDateMillis?.let {
                    onAction(
                        UsdRateEntryAction.DatePicked(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    )
                } ?: onAction(UsdRateEntryAction.CloseDatePicker)
            })
        }
    ) { DatePicker(picker) }
}

const val USD_RATE_ENTRY_CLOSE_TAG = "usd.rate.entry.close"
const val USD_RATE_ENTRY_AMOUNT_TAG = "usd.rate.entry.amount"
const val USD_RATE_ENTRY_DATE_TAG = "usd.rate.entry.date"
const val USD_RATE_ENTRY_KEYPAD_TAG = "usd.rate.entry.keypad"
const val USD_RATE_ENTRY_SUBMIT_TAG = "usd.rate.entry.submit"
