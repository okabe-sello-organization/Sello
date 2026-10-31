package com.software.sello.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChipRows
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
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.rememberSelectedFirst
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.movement.MovementCloseButton
import com.software.sello.presentation.movement.MovementDatePicker
import com.software.sello.presentation.movement.MovementDiscardSlip
import com.software.sello.presentation.movement.MovementNoteComposer
import com.software.sello.presentation.movement.MovementNoteLine
import com.software.sello.presentation.movement.MovementPane

@Composable
fun InvestmentMovementRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val model: InvestmentMovementViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { model.onAction(InvestmentMovementAction.Back) }
    InvestmentMovementScreen(state, model::onAction)
}

/** The kinds in the order the chips offer them (v2 `TYPES`). */
private val Kinds = listOf(
    InvestmentMovementKind.Contribution,
    InvestmentMovementKind.Valuation,
    InvestmentMovementKind.Withdrawal,
    InvestmentMovementKind.Dividend
)

private fun InvestmentMovementKind.label() = when (this) {
    InvestmentMovementKind.Contribution -> R.string.investment_movement_contribution
    InvestmentMovementKind.Valuation -> R.string.investment_movement_valuation
    InvestmentMovementKind.Withdrawal -> R.string.investment_movement_withdrawal
    InvestmentMovementKind.Dividend -> R.string.investment_movement_dividend
}

/** Aporte "+", Valor de hoy without a sign (it replaces the value), Retiro "−", Dividendo "+". */
private fun InvestmentMovementKind.sign() = when (this) {
    InvestmentMovementKind.Contribution, InvestmentMovementKind.Dividend -> MoneySign.Plus
    InvestmentMovementKind.Valuation -> MoneySign.None
    InvestmentMovementKind.Withdrawal -> MoneySign.Minus
}

/** The figure as it is written in a sentence: "5.242.000", "USD 1.050,50". */
private val MoneyTextValue.written: String
    get() = if (symbol.isEmpty()) digits else "$symbol $digits"

/**
 * Anotar movimiento (v2 Inversiones): the entry screen's layout with one row of type
 * chips above one row of investment chips, the amount in the investment's currency with
 * the type's sign, one plain fact, the date pill and the note, and the ink action.
 * Correcting a movement uses the same form with Borrar; its type and investment are fixed.
 */
@Composable
fun InvestmentMovementScreen(
    state: InvestmentMovementState,
    onAction: (InvestmentMovementAction) -> Unit
) {
    SelloScaffold(
        title = stringResource(R.string.investment_movement_title),
        navigationIcon = {
            MovementCloseButton(
                { onAction(InvestmentMovementAction.Back) },
                Modifier.testTag(INVESTMENT_MOVEMENT_CLOSE_TAG)
            )
        },
        actions = {
            if (state.editing) {
                SelloButton(
                    stringResource(R.string.expense_edit_delete),
                    { onAction(InvestmentMovementAction.Delete) },
                    kind = ButtonKind.Text,
                    enabled = state.canDelete,
                    tone = ButtonTone.Ink,
                    modifier = Modifier.testTag(INVESTMENT_MOVEMENT_DELETE_TAG)
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> MovementPane(padding) {
                SlipSkeleton(Modifier.testTag(INVESTMENT_MOVEMENT_LOADING_TAG))
            }

            state.loadFailed -> MovementPane(padding) {
                ErrorSlip(
                    stringResource(R.string.inversiones_failed_body),
                    { onAction(InvestmentMovementAction.Retry) },
                    Modifier.testTag(INVESTMENT_MOVEMENT_FAILED_TAG),
                    title = stringResource(R.string.inversiones_failed_title)
                )
            }

            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {},
                amount = { Amount(state, onAction) },
                choices = { Choices(state, onAction) },
                action = { Action(state, onAction) },
                keypad = {
                    AmountKeypad(
                        { onAction(InvestmentMovementAction.Digit(it)) },
                        { onAction(InvestmentMovementAction.TripleZero) },
                        { onAction(InvestmentMovementAction.Backspace) },
                        { onAction(InvestmentMovementAction.ClearAmount) },
                        enabled = state.editable,
                        modifier = Modifier.testTag(INVESTMENT_MOVEMENT_KEYPAD_TAG),
                        compact = true,
                        onComma = if (state.usd) {
                            { onAction(InvestmentMovementAction.Comma) }
                        } else {
                            null
                        }
                    )
                }
            )
        }
    }
    if (state.confirmDiscard) {
        MovementDiscardSlip(
            stringResource(R.string.investment_movement_discard_title),
            { onAction(InvestmentMovementAction.DiscardConfirmed) },
            { onAction(InvestmentMovementAction.KeepEditing) }
        )
    }
    if (state.datePickerOpen) {
        MovementDatePicker(
            state.date,
            state.today,
            { onAction(InvestmentMovementAction.DatePicked(it)) },
            { onAction(InvestmentMovementAction.CloseDatePicker) }
        )
    }
    if (state.composerOpen) Composer(state, onAction)
}

/** "Aporte en pesos" or "Retiro en dólares", the signed amount and the investment's icon. */
@Composable
private fun Amount(state: InvestmentMovementState, onAction: (InvestmentMovementAction) -> Unit) {
    val day = dateLabels().day(state.date, state.today)
    val over = state.overValue
    val kind = stringResource(state.kind.label())
    EntryAmountSlip(
        label = stringResource(
            if (state.usd) {
                R.string.investment_movement_label_dollars
            } else {
                R.string.investment_movement_label_pesos
            },
            kind
        ),
        // The sign is drawn; what is read stays the amount itself.
        amount = state.amount?.copy(sign = state.kind.sign()),
        modifier = Modifier.testTag(INVESTMENT_MOVEMENT_AMOUNT_TAG),
        error = when {
            over != null -> stringResource(R.string.investment_movement_over_value, over.written)
            state.atLimit && state.usd -> stringResource(R.string.investment_movement_limit_dollars)
            state.atLimit -> stringResource(R.string.expense_entry_amount_limit)
            state.amountText != null -> stringResource(R.string.investment_movement_amount_invalid)
            else -> null
        },
        date = {
            FieldPill(
                text = day,
                icon = SelloIcon.Event,
                onClick = { onAction(InvestmentMovementAction.OpenDatePicker) },
                modifier = Modifier.testTag(INVESTMENT_MOVEMENT_DATE_TAG),
                enabled = state.editable
            )
        },
        icon = state.investment?.let {
            InvestmentIcons.iconFor(it.iconKey)
        } ?: SelloIcon.TrendingUp,
        tone = ButtonTone.Ink,
        limitPulse = state.limitPulse,
        rejectedText = state.amountText,
        onPaste = if (state.editable && !state.usd) {
            { onAction(InvestmentMovementAction.Paste(it)) }
        } else {
            null
        }
    )
}

/** The type chips, the investment chips, the fact about the chosen one, and the note. */
@Composable
private fun Choices(state: InvestmentMovementState, onAction: (InvestmentMovementAction) -> Unit) {
    Heading(stringResource(R.string.investment_movement_type))
    ChipRows(Kinds.size, Modifier.testTag(INVESTMENT_MOVEMENT_KINDS_TAG), rows = 1) { index ->
        val kind = Kinds[index]
        SelloChip(
            stringResource(kind.label()),
            state.kind == kind,
            { onAction(InvestmentMovementAction.Kind(kind)) },
            enabled = state.editable && !state.editing,
            modifier = Modifier.testTag("$INVESTMENT_MOVEMENT_KIND_TAG.${kind.name}")
        )
    }
    Heading(stringResource(R.string.investment_movement_investment))
    if (state.investments.isEmpty()) {
        FactLine(AnnotatedString(stringResource(R.string.investment_movement_none)))
    } else {
        val order = rememberSelectedFirst(state.investments, state.investmentId) { it.id }
        ChipRows(
            order.items.size,
            Modifier.testTag(INVESTMENT_MOVEMENT_INVESTMENTS_TAG),
            rows = 1
        ) { index ->
            val investment = order.items[index]
            SelloChip(
                investment.name,
                state.investmentId == investment.id,
                {
                    order.picked()
                    onAction(InvestmentMovementAction.InvestmentPicked(investment.id))
                },
                icon = InvestmentIcons.iconFor(investment.iconKey),
                enabled = state.editable && !state.editing,
                modifier = Modifier.testTag("$INVESTMENT_MOVEMENT_INVESTMENT_TAG.${investment.id}")
            )
        }
    }
    state.fact?.let { Fact(it, state, onAction) }
    MovementNoteLine(
        state.note,
        state.editable,
        { onAction(InvestmentMovementAction.OpenComposer) },
        Modifier.testTag(INVESTMENT_MOVEMENT_NOTE_TAG)
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
}

/** One plain sentence with its figure in bold, and Anotar el valor de hoy when a retiro waits. */
@Composable
private fun Fact(
    fact: InvestmentMovementFact,
    state: InvestmentMovementState,
    onAction: (InvestmentMovementAction) -> Unit
) {
    val dates = dateLabels()
    val (text, figure) = when (fact) {
        is InvestmentMovementFact.Contributed -> stringResource(
            R.string.investment_movement_fact_contributed,
            fact.amount.written,
            fact.name
        ) to fact.amount.written

        is InvestmentMovementFact.LastValue -> stringResource(
            R.string.investment_movement_fact_last_value,
            fact.amount.written,
            dates.shortDay(fact.valuedOn, state.today)
        ) to fact.amount.written

        InvestmentMovementFact.FirstValue ->
            stringResource(R.string.investment_movement_fact_first_value) to null

        is InvestmentMovementFact.Worth -> {
            val worth = stringResource(
                R.string.investment_movement_fact_worth,
                fact.amount.written,
                dates.shortDay(fact.valuedOn, state.today)
            )
            val more = stringResource(R.string.investment_movement_fact_worth_more)
            (if (state.overValue != null) "$worth $more" else worth) to fact.amount.written
        }

        InvestmentMovementFact.NoValue ->
            stringResource(R.string.investment_movement_fact_no_value) to null

        is InvestmentMovementFact.Dividends -> stringResource(
            R.string.investment_movement_fact_dividends,
            fact.amount.written,
            fact.name
        ) to fact.amount.written
    }
    Column {
        FactLine(
            buildAnnotatedString {
                append(text)
                val start = figure?.let { text.indexOf(it) } ?: -1
                if (figure != null && start >= 0) {
                    addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + figure.length)
                }
            },
            Modifier.testTag(INVESTMENT_MOVEMENT_FACT_TAG)
        )
        if (state.offersValue) {
            SelloButton(
                stringResource(R.string.investment_movement_record_value_first),
                { onAction(InvestmentMovementAction.RecordValueFirst) },
                kind = ButtonKind.Text,
                tone = ButtonTone.Ink,
                enabled = state.editable,
                modifier = Modifier.testTag(INVESTMENT_MOVEMENT_VALUE_FIRST_TAG)
            )
        }
    }
}

/** What stops the movement, then the one button, held with the pad so neither moves. */
@Composable
private fun Action(state: InvestmentMovementState, onAction: (InvestmentMovementAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        val dates = dateLabels()
        state.firstContribution?.let { first ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        R.string.investment_movement_before_first,
                        dates.dayMonthYear(first)
                    )
                ),
                Modifier.testTag(INVESTMENT_MOVEMENT_BEFORE_FIRST_TAG)
            )
        }
        state.blockedBy?.let { blocked ->
            val day = dates.shortDay(blocked.date, state.today)
            val available = blocked.available
            FieldMessageText(
                FieldMessage(
                    if (available != null) {
                        stringResource(
                            R.string.investment_movement_blocked,
                            blocked.amount.written,
                            day,
                            available.written
                        )
                    } else {
                        stringResource(
                            R.string.investment_movement_blocked_no_value,
                            blocked.amount.written,
                            day
                        )
                    }
                ),
                Modifier.testTag(INVESTMENT_MOVEMENT_BLOCKED_TAG)
            )
        }
        state.error?.let { error ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        when (error) {
                            InvestmentMovementError.Unknown -> R.string.expense_entry_unknown

                            InvestmentMovementError.FutureDate -> R.string.expense_entry_future_date

                            InvestmentMovementError.DataChanged ->
                                R.string.investment_movement_data_changed

                            InvestmentMovementError.Gone -> R.string.investment_movement_gone

                            InvestmentMovementError.Changed -> R.string.investment_movement_changed

                            InvestmentMovementError.CurrencyMismatch ->
                                R.string.investment_movement_currency_mismatch

                            InvestmentMovementError.Unchanged -> R.string.expense_edit_no_changes

                            InvestmentMovementError.LastContribution ->
                                R.string.investment_movement_last_contribution

                            InvestmentMovementError.NotDeleted ->
                                R.string.investment_movement_not_deleted

                            InvestmentMovementError.NotSaved ->
                                R.string.investment_movement_not_saved
                        }
                    )
                ),
                Modifier.testTag(INVESTMENT_MOVEMENT_ERROR_TAG)
            )
        }
        val hint = when {
            state.investment == null && state.investments.isNotEmpty() ->
                R.string.investment_movement_choose

            state.editing && !state.changed -> R.string.expense_edit_no_changes

            else -> null
        }
        if (hint != null && !state.busy && state.error == null) {
            FieldMessageText(
                FieldMessage(stringResource(hint), isError = false),
                Modifier.testTag(INVESTMENT_MOVEMENT_MISSING_TAG)
            )
        }
        val unknown = state.error == InvestmentMovementError.Unknown
        SelloButton(
            text = stringResource(
                when {
                    unknown -> R.string.expense_entry_check_again

                    state.busy -> R.string.expense_entry_saving

                    state.editing -> R.string.expense_edit_submit

                    else -> when (state.kind) {
                        InvestmentMovementKind.Contribution ->
                            R.string.investment_movement_record_contribution

                        InvestmentMovementKind.Valuation ->
                            R.string.investment_movement_record_valuation

                        InvestmentMovementKind.Withdrawal ->
                            R.string.investment_movement_record_withdrawal

                        InvestmentMovementKind.Dividend ->
                            R.string.investment_movement_record_dividend
                    }
                }
            ),
            onClick = {
                onAction(
                    if (unknown) InvestmentMovementAction.Retry else InvestmentMovementAction.Submit
                )
            },
            enabled = state.canSubmit || unknown,
            loading = state.busy,
            tone = ButtonTone.Ink,
            modifier = Modifier.fillMaxWidth().testTag(INVESTMENT_MOVEMENT_SUBMIT_TAG)
        )
    }
}

@Composable
private fun Composer(state: InvestmentMovementState, onAction: (InvestmentMovementAction) -> Unit) {
    val sign = when (state.kind.sign()) {
        MoneySign.Plus -> "+"
        MoneySign.Minus -> "−"
        MoneySign.None -> ""
    }
    MovementNoteComposer(
        kind = stringResource(state.kind.label()),
        figure = state.amount?.let { sign + it.written },
        draft = state.composerDraft,
        length = state.composerLength,
        problem = state.composerProblem,
        icon = state.investment?.let { InvestmentIcons.iconFor(it.iconKey) }
            ?: SelloIcon.TrendingUp,
        spoken = stringResource(R.string.investment_movement_note_spoken),
        amountSpoken = state.amount?.spoken,
        onChange = { onAction(InvestmentMovementAction.ComposerChanged(it)) },
        onDone = { onAction(InvestmentMovementAction.ComposerDone) },
        onCancel = { onAction(InvestmentMovementAction.ComposerCancelled) }
    )
}

const val INVESTMENT_MOVEMENT_CLOSE_TAG = "investment.movement.close"
const val INVESTMENT_MOVEMENT_DELETE_TAG = "investment.movement.delete"
const val INVESTMENT_MOVEMENT_LOADING_TAG = "investment.movement.loading"
const val INVESTMENT_MOVEMENT_FAILED_TAG = "investment.movement.failed"
const val INVESTMENT_MOVEMENT_KINDS_TAG = "investment.movement.kinds"
const val INVESTMENT_MOVEMENT_KIND_TAG = "investment.movement.kind"
const val INVESTMENT_MOVEMENT_INVESTMENTS_TAG = "investment.movement.investments"
const val INVESTMENT_MOVEMENT_INVESTMENT_TAG = "investment.movement.investment"
const val INVESTMENT_MOVEMENT_AMOUNT_TAG = "investment.movement.amount"
const val INVESTMENT_MOVEMENT_DATE_TAG = "investment.movement.date"
const val INVESTMENT_MOVEMENT_FACT_TAG = "investment.movement.fact"
const val INVESTMENT_MOVEMENT_VALUE_FIRST_TAG = "investment.movement.value.first"
const val INVESTMENT_MOVEMENT_NOTE_TAG = "investment.movement.note"
const val INVESTMENT_MOVEMENT_BEFORE_FIRST_TAG = "investment.movement.before.first"
const val INVESTMENT_MOVEMENT_BLOCKED_TAG = "investment.movement.blocked"
const val INVESTMENT_MOVEMENT_ERROR_TAG = "investment.movement.error"
const val INVESTMENT_MOVEMENT_MISSING_TAG = "investment.movement.missing"
const val INVESTMENT_MOVEMENT_KEYPAD_TAG = "investment.movement.keypad"
const val INVESTMENT_MOVEMENT_SUBMIT_TAG = "investment.movement.submit"
