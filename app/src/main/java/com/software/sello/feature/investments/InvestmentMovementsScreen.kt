package com.software.sello.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.BulkCaption
import com.software.sello.designsystem.component.BulkLine
import com.software.sello.designsystem.component.BulkMarkTone
import com.software.sello.designsystem.component.BulkReceipt
import com.software.sello.designsystem.component.BulkTotal
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChipRows
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.movement.MovementCloseButton
import com.software.sello.presentation.movement.MovementDatePicker
import com.software.sello.presentation.movement.MovementDiscardSlip
import com.software.sello.presentation.movement.MovementNoteComposer
import com.software.sello.presentation.movement.MovementNoteLine
import com.software.sello.presentation.movement.MovementPane
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun InvestmentMovementsRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val model: InvestmentMovementsViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { model.onAction(InvestmentMovementsAction.Back) }
    InvestmentMovementsScreen(state, model::onAction)
}

/** The types in the order the single movement offers them (v2 `TYPES`). */
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

/** Aporte and Dividendo "+", Valor de hoy without a sign (it replaces the value), Retiro "−". */
private fun InvestmentMovementKind?.sign() = when (this) {
    InvestmentMovementKind.Contribution, InvestmentMovementKind.Dividend -> MoneySign.Plus
    InvestmentMovementKind.Withdrawal -> MoneySign.Minus
    else -> MoneySign.None
}

private fun InvestmentMovementKind.mark() = when (this) {
    InvestmentMovementKind.Contribution, InvestmentMovementKind.Dividend ->
        SelloIcon.Add to BulkMarkTone.Gain

    InvestmentMovementKind.Valuation -> SelloIcon.Insights to BulkMarkTone.Value

    InvestmentMovementKind.Withdrawal -> SelloIcon.ArrowBack to BulkMarkTone.Neutral
}

/** The figure as it is written in a sentence: "3.600.000", "USD 1.050,50". */
private val MoneyTextValue.written: String
    get() = if (symbol.isEmpty()) digits else "$symbol $digits"

/**
 * Varios movimientos (v2 *Anotar varios*, the investment's receipt): ✕ with the title and
 * the investment's name; the receipt taking the free height, each line with its type,
 * date and amount; the type chips and the note of the highlighted line; the ink action;
 * the pad, with the comma for dollars. The foot says **Aportado**: aportes − retiros.
 */
@Composable
fun InvestmentMovementsScreen(
    state: InvestmentMovementsState,
    onAction: (InvestmentMovementsAction) -> Unit
) {
    SelloScaffold(
        title = stringResource(R.string.investment_movements_title),
        subtitle = state.name.takeIf { it.isNotEmpty() }?.let {
            if (state.usd) stringResource(R.string.investment_movements_in_dollars, it) else it
        },
        navigationIcon = {
            MovementCloseButton(
                { onAction(InvestmentMovementsAction.Back) },
                Modifier.testTag(INVESTMENT_MOVEMENTS_CLOSE_TAG)
            )
        }
    ) { padding ->
        when {
            state.loading -> MovementPane(padding) {
                SlipSkeleton(Modifier.testTag(INVESTMENT_MOVEMENTS_LOADING_TAG))
            }

            state.loadFailed -> MovementPane(padding) {
                ErrorSlip(
                    stringResource(R.string.inversiones_failed_body),
                    { onAction(InvestmentMovementsAction.Retry) },
                    Modifier.testTag(INVESTMENT_MOVEMENTS_FAILED_TAG),
                    title = stringResource(R.string.inversiones_failed_title)
                )
            }

            else -> Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Receipt(state, onAction, Modifier.weight(1f))
                Box {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choices(state, onAction)
                    }
                    state.removed?.let { removed ->
                        Removed(removed, onAction, Modifier.align(Alignment.BottomCenter))
                    }
                }
                Action(state, onAction)
                AmountKeypad(
                    { onAction(InvestmentMovementsAction.Digit(it)) },
                    { onAction(InvestmentMovementsAction.TripleZero) },
                    { onAction(InvestmentMovementsAction.Backspace) },
                    { onAction(InvestmentMovementsAction.ClearAmount) },
                    enabled = state.editable,
                    modifier = Modifier.testTag(INVESTMENT_MOVEMENTS_KEYPAD_TAG),
                    compact = true,
                    onComma = if (state.usd) {
                        { onAction(InvestmentMovementsAction.Comma) }
                    } else {
                        null
                    }
                )
            }
        }
    }
    if (state.confirmDiscard) {
        MovementDiscardSlip(
            pluralStringResource(
                R.plurals.investment_movements_discard,
                state.lines.size,
                state.lines.size
            ),
            { onAction(InvestmentMovementsAction.DiscardConfirmed) },
            { onAction(InvestmentMovementsAction.KeepEditing) }
        )
    }
    if (state.datePickerOpen) {
        MovementDatePicker(
            state.current.date,
            state.today,
            { onAction(InvestmentMovementsAction.DatePicked(it)) },
            { onAction(InvestmentMovementsAction.CloseDatePicker) }
        )
    }
    if (state.composerOpen) Composer(state, onAction)
}

@Composable
private fun Receipt(
    state: InvestmentMovementsState,
    onAction: (InvestmentMovementsAction) -> Unit,
    modifier: Modifier
) {
    val dates = dateLabels()
    val caption = when {
        state.full -> BulkCaption.Problem(
            stringResource(R.string.investment_movements_full),
            icon = false
        )

        state.missingKinds > 0 -> BulkCaption.Problem(
            pluralStringResource(
                R.plurals.investment_movements_missing,
                state.missingKinds,
                state.missingKinds
            )
        )

        state.refused > 0 -> BulkCaption.Problem(
            pluralStringResource(
                R.plurals.investment_movements_refused,
                state.refused,
                state.refused
            )
        )

        else -> BulkCaption.Count(
            pluralStringResource(
                R.plurals.investment_movements_lines,
                state.lines.size,
                state.lines.size
            )
        )
    }
    BulkReceipt(
        lines = state.lines.mapIndexed { index, line ->
            // A locked receipt highlights nothing, so every line writes its own date.
            bulkLine(line, highlighted = index == state.highlighted && state.editable, state, dates)
        },
        highlighted = state.highlighted,
        caption = caption,
        total = BulkTotal(
            stringResource(R.string.investment_movements_contributed),
            state.contributed ?: MoneyTextValue(MoneySign.None, "", "—", "—")
        ),
        onSelect = { onAction(InvestmentMovementsAction.Select(it)) },
        onRemove = { onAction(InvestmentMovementsAction.Remove(it)) },
        onAdd = { onAction(InvestmentMovementsAction.AddLine) },
        modifier = modifier.testTag(INVESTMENT_MOVEMENTS_RECEIPT_TAG),
        addEnabled = state.canAdd,
        enabled = state.editable,
        highlightedFoot = {
            FieldPill(
                text = dates.day(state.current.date, state.today),
                icon = SelloIcon.Event,
                onClick = { onAction(InvestmentMovementsAction.OpenDatePicker) },
                modifier = Modifier.testTag(INVESTMENT_MOVEMENTS_DATE_TAG),
                enabled = state.editable
            )
        }
    )
}

/** A line as the receipt draws it: its type, the date and note under it, the signed amount. */
@Composable
private fun bulkLine(
    line: MovementLineModel,
    highlighted: Boolean,
    state: InvestmentMovementsState,
    dates: DateLabels
): BulkLine {
    val kind = line.kind
    val mark = kind?.mark()
    val day = dates.day(line.date, state.today)
    // The highlighted line carries its date as a pill under it; the others write it.
    val detail = if (highlighted) {
        line.note.ifBlank { null }
    } else {
        listOf(day, line.note).filter { it.isNotBlank() }.joinToString(" · ")
    }
    return BulkLine(
        key = line.key,
        mark = mark?.first,
        markTone = mark?.second ?: BulkMarkTone.Gain,
        title = kind?.let { stringResource(it.label()) }
            ?: stringResource(R.string.investment_movements_no_type),
        // The sign is drawn; what is read stays the amount itself.
        amount = line.amount?.copy(sign = kind.sign()),
        titleSoft = kind == null,
        detail = detail,
        reason = reason(line, state)
    )
}

/** Why [line] cannot be saved, in its own words, or null. */
@Composable
private fun reason(line: MovementLineModel, state: InvestmentMovementsState): String? {
    val dates = dateLabels()
    return when {
        line.missingKind -> stringResource(R.string.investment_movements_choose_type)

        line.invalidAmount -> stringResource(R.string.investment_movements_invalid_amount)

        else -> when (val refusal = line.refusal) {
            null -> null

            is MovementLineRefusal.OverValue ->
                stringResource(R.string.investment_movement_over_value, refusal.available.written)

            MovementLineRefusal.NoValue -> stringResource(
                R.string.investment_movement_fact_no_value
            )

            is MovementLineRefusal.LeavesRetiroShort -> stringResource(
                R.string.investment_movements_leaves_short,
                refusal.amount.written,
                dates.shortDay(refusal.date, state.today)
            )

            is MovementLineRefusal.BeforeFirstContribution -> stringResource(
                R.string.investment_movements_before_first,
                dates.dayMonthYear(refusal.firstContribution)
            )

            MovementLineRefusal.FutureDate -> stringResource(R.string.investment_movements_future)

            MovementLineRefusal.InvalidAmount ->
                stringResource(R.string.investment_movements_invalid_amount)

            MovementLineRefusal.Other -> stringResource(R.string.investment_movements_other)
        }
    }
}

/** The type chips and the note of the highlighted line. */
@Composable
private fun Choices(
    state: InvestmentMovementsState,
    onAction: (InvestmentMovementsAction) -> Unit
) {
    Text(
        if (state.lines.size > 1) {
            stringResource(R.string.investment_movements_type_line, state.highlighted + 1)
        } else {
            stringResource(R.string.investment_movement_type)
        },
        Modifier.semantics { heading() },
        color = SelloTheme.colors.ink,
        style = MaterialTheme.typography.titleSmall
    )
    ChipRows(Kinds.size, Modifier.testTag(INVESTMENT_MOVEMENTS_KINDS_TAG), rows = 1) { index ->
        val kind = Kinds[index]
        SelloChip(
            stringResource(kind.label()),
            state.current.kind == kind,
            { onAction(InvestmentMovementsAction.Kind(kind)) },
            enabled = state.editable,
            modifier = Modifier.testTag("$INVESTMENT_MOVEMENTS_KIND_TAG.${kind.name}")
        )
    }
    MovementNoteLine(
        state.current.note,
        state.editable,
        { onAction(InvestmentMovementsAction.OpenComposer) },
        Modifier.testTag(INVESTMENT_MOVEMENTS_NOTE_TAG)
    )
}

/** The short Deshacer for a line just removed; it goes away by itself. */
@Composable
private fun Removed(
    removed: MovementLineModel,
    onAction: (InvestmentMovementsAction) -> Unit,
    modifier: Modifier
) {
    LaunchedEffect(removed.key) {
        delay(REMOVAL_SHOWN)
        onAction(InvestmentMovementsAction.RemovalShown)
    }
    SelloNotice(
        message = removed.kind?.let {
            stringResource(R.string.investment_movements_removed, stringResource(it.label()))
        } ?: stringResource(R.string.investment_movements_removed_plain),
        modifier = modifier.testTag(INVESTMENT_MOVEMENTS_REMOVED_TAG),
        actionLabel = stringResource(R.string.correction_undo),
        onAction = { onAction(InvestmentMovementsAction.UndoRemove) },
        confirmation = true
    )
}

/** That the investment is as read before, what stopped the save, then the one button: Anotar, Guardando… or Comprobar si se guardó. */
@Composable
private fun Action(state: InvestmentMovementsState, onAction: (InvestmentMovementsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        if (state.stale) {
            FieldMessageText(
                FieldMessage(stringResource(R.string.investment_stale), isError = false),
                Modifier.testTag(INVESTMENT_MOVEMENTS_STALE_TAG)
            )
        }
        state.error?.let { error ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        when (error) {
                            InvestmentMovementsError.NotSaved ->
                                R.string.investment_movements_not_saved

                            InvestmentMovementsError.Unknown -> R.string.expense_entry_unknown

                            InvestmentMovementsError.DataChanged ->
                                R.string.investment_movement_data_changed

                            InvestmentMovementsError.Gone -> R.string.investment_movements_gone

                            InvestmentMovementsError.Changed ->
                                R.string.investment_movement_changed
                        }
                    )
                ),
                Modifier.testTag(INVESTMENT_MOVEMENTS_ERROR_TAG)
            )
        }
        val unknown = state.error == InvestmentMovementsError.Unknown
        SelloButton(
            text = when {
                unknown -> stringResource(R.string.expense_entry_check_again)

                state.busy -> stringResource(R.string.expense_entry_saving)

                else -> pluralStringResource(
                    R.plurals.investment_movements_record,
                    state.counted.coerceAtLeast(1),
                    state.counted
                )
            },
            onClick = {
                onAction(
                    if (unknown) {
                        InvestmentMovementsAction.Retry
                    } else {
                        InvestmentMovementsAction.Submit
                    }
                )
            },
            enabled = state.canSubmit || unknown,
            loading = state.busy,
            tone = ButtonTone.Ink,
            icon = SelloIcon.Add.takeUnless { unknown || state.busy },
            modifier = Modifier.fillMaxWidth().testTag(INVESTMENT_MOVEMENTS_SUBMIT_TAG)
        )
    }
}

@Composable
private fun Composer(
    state: InvestmentMovementsState,
    onAction: (InvestmentMovementsAction) -> Unit
) {
    val line = state.current
    val kind = line.kind
    val sign = when (kind.sign()) {
        MoneySign.Plus -> "+"
        MoneySign.Minus -> "−"
        MoneySign.None -> ""
    }
    MovementNoteComposer(
        kind = kind?.let { stringResource(it.label()) }
            ?: stringResource(R.string.investment_movements_no_type),
        figure = line.amount?.let { sign + it.written },
        draft = state.composerDraft,
        length = state.composerLength,
        problem = state.composerProblem,
        icon = kind?.mark()?.first ?: SelloIcon.TrendingUp,
        spoken = stringResource(R.string.investment_movement_note_spoken),
        amountSpoken = line.amount?.spoken,
        onChange = { onAction(InvestmentMovementsAction.ComposerChanged(it)) },
        onDone = { onAction(InvestmentMovementsAction.ComposerDone) },
        onCancel = { onAction(InvestmentMovementsAction.ComposerCancelled) }
    )
}

/** How long the Deshacer for a removed line stays. */
private val REMOVAL_SHOWN = 4.seconds

const val INVESTMENT_MOVEMENTS_CLOSE_TAG = "investment.movements.close"
const val INVESTMENT_MOVEMENTS_LOADING_TAG = "investment.movements.loading"
const val INVESTMENT_MOVEMENTS_FAILED_TAG = "investment.movements.failed"
const val INVESTMENT_MOVEMENTS_RECEIPT_TAG = "investment.movements.receipt"
const val INVESTMENT_MOVEMENTS_DATE_TAG = "investment.movements.date"
const val INVESTMENT_MOVEMENTS_KINDS_TAG = "investment.movements.kinds"
const val INVESTMENT_MOVEMENTS_KIND_TAG = "investment.movements.kind"
const val INVESTMENT_MOVEMENTS_NOTE_TAG = "investment.movements.note"
const val INVESTMENT_MOVEMENTS_REMOVED_TAG = "investment.movements.removed"
const val INVESTMENT_MOVEMENTS_STALE_TAG = "investment.movements.stale"
const val INVESTMENT_MOVEMENTS_ERROR_TAG = "investment.movements.error"
const val INVESTMENT_MOVEMENTS_KEYPAD_TAG = "investment.movements.keypad"
const val INVESTMENT_MOVEMENTS_SUBMIT_TAG = "investment.movements.submit"
