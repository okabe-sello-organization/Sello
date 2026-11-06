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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.software.sello.designsystem.component.EntryHeading
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FieldTone
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.MovementPane
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.rememberSelectedFirst
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

/** The types in the order the single movement offers them. */
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
 * Varios movimientos (docs/design/system/features/bulk.html, the investments' receipt): ✕ with the
 * title; the receipt taking the free height, each line with its investment, its type, date and note
 * under it, and its amount in that investment's currency; the investment and type chips and the
 * note of the highlighted line; the ink action; the pad, with the comma while the line is in
 * dollars. The foot says **Aportado**: aportes − retiros, in pesos at today's rate when COP and USD
 * lines mix, or each currency's part when there is no rate.
 */
@Composable
fun InvestmentMovementsScreen(
    state: InvestmentMovementsState,
    onAction: (InvestmentMovementsAction) -> Unit
) {
    SelloScaffold(
        title = stringResource(R.string.investment_movements_title),
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
                StatusSlip(
                    kind = StatusKind.LoadFailed(stringResource(R.string.inversiones_failed_title)),
                    message = stringResource(R.string.inversiones_failed_body),
                    modifier = Modifier.testTag(INVESTMENT_MOVEMENTS_FAILED_TAG),
                    action = StatusAction(StatusSlipDefaults.retryLabel) {
                        onAction(InvestmentMovementsAction.Retry)
                    }
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

        state.missingInvestments > 0 -> BulkCaption.Problem(
            pluralStringResource(
                R.plurals.investment_movements_missing_investments,
                state.missingInvestments,
                state.missingInvestments
            )
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
        total = total(state, dates),
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

/** **Aportado** as the foot writes it: one figure, the figure in pesos and its rate, or the parts. */
@Composable
private fun total(state: InvestmentMovementsState, dates: DateLabels): BulkTotal {
    val label = stringResource(R.string.investment_movements_contributed)
    return when (val total = state.contributed) {
        null -> BulkTotal(label, MoneyTextValue(MoneySign.None, "", "—", "—"))

        is ContributedTotal.One -> BulkTotal(label, total.amount)

        is ContributedTotal.InPesos -> BulkTotal(
            label,
            total.amount,
            note = stringResource(
                R.string.investment_movements_in_pesos,
                total.rate,
                dates.shortDay(total.rateDate, state.today)
            )
        )

        is ContributedTotal.Parts -> BulkTotal(
            label,
            total.pesos,
            second = total.dollars,
            note = stringResource(R.string.investment_movements_no_rate)
        )
    }
}

/**
 * A line as the receipt draws it: its investment, the type, date and note under it, the
 * signed amount.
 */
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
    val type = kind?.let { stringResource(it.label()) }
        ?: stringResource(R.string.investment_movements_no_type)
    val detail = listOf(type, day.takeUnless { highlighted }.orEmpty(), line.note)
        .filter { it.isNotBlank() }.joinToString(" · ")
    return BulkLine(
        key = line.key,
        mark = mark?.first,
        markTone = mark?.second ?: BulkMarkTone.Gain,
        title = line.investmentName ?: stringResource(R.string.investment_movements_no_investment),
        // The sign is drawn; what is read stays the amount itself.
        amount = line.amount?.copy(sign = kind.sign()),
        titleSoft = line.investmentName == null,
        detail = detail,
        reason = reason(line, state)
    )
}

/** Why [line] cannot be saved, in its own words, or null. */
@Composable
private fun reason(line: MovementLineModel, state: InvestmentMovementsState): String? {
    val dates = dateLabels()
    return when {
        line.lost -> stringResource(R.string.investment_movements_investment_gone)

        line.missingInvestment -> stringResource(R.string.investment_movements_choose_investment)

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

/** The investment chips, in two rows, the type chips and the note of the highlighted line. */
@Composable
private fun Choices(
    state: InvestmentMovementsState,
    onAction: (InvestmentMovementsAction) -> Unit
) {
    EntryHeading(
        if (state.lines.size > 1) {
            stringResource(R.string.investment_movements_investment_line, state.highlighted + 1)
        } else {
            stringResource(R.string.investment_movement_investment)
        }
    )
    if (state.investments.isEmpty()) {
        Text(
            stringResource(R.string.investment_movement_none),
            color = SelloTheme.colors.inkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        key(state.current.key) {
            val order = rememberSelectedFirst(state.investments, state.current.investmentId) {
                it.id
            }
            ChipRows(
                order.items.size,
                Modifier.testTag(INVESTMENT_MOVEMENTS_INVESTMENTS_TAG)
            ) { index ->
                val investment = order.items[index]
                SelloChip(
                    investment.name,
                    state.current.investmentId == investment.id,
                    {
                        order.picked()
                        onAction(InvestmentMovementsAction.InvestmentPicked(investment.id))
                    },
                    icon = InvestmentIcons.iconFor(investment.iconKey),
                    enabled = state.editable,
                    modifier = Modifier.testTag(
                        "$INVESTMENT_MOVEMENTS_INVESTMENT_TAG.${investment.id}"
                    )
                )
            }
        }
    }
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
        message = (removed.investmentName ?: removed.kind?.let { stringResource(it.label()) })
            ?.let { stringResource(R.string.investment_movements_removed, it) }
            ?: stringResource(R.string.investment_movements_removed_plain),
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
                FieldMessage(stringResource(R.string.investment_stale), tone = FieldTone.Hint),
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
                    ),
                    if (error == InvestmentMovementsError.Unknown) {
                        FieldTone.Warning
                    } else {
                        FieldTone.Error
                    }
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
    val type = kind?.let { stringResource(it.label()) }
        ?: stringResource(R.string.investment_movements_no_type)
    MovementNoteComposer(
        kind = line.investmentName?.let { "$it · $type" } ?: type,
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
const val INVESTMENT_MOVEMENTS_INVESTMENTS_TAG = "investment.movements.investments"
const val INVESTMENT_MOVEMENTS_INVESTMENT_TAG = "investment.movements.investment"
const val INVESTMENT_MOVEMENTS_KIND_TAG = "investment.movements.kind"
const val INVESTMENT_MOVEMENTS_NOTE_TAG = "investment.movements.note"
const val INVESTMENT_MOVEMENTS_REMOVED_TAG = "investment.movements.removed"
const val INVESTMENT_MOVEMENTS_STALE_TAG = "investment.movements.stale"
const val INVESTMENT_MOVEMENTS_ERROR_TAG = "investment.movements.error"
const val INVESTMENT_MOVEMENTS_KEYPAD_TAG = "investment.movements.keypad"
const val INVESTMENT_MOVEMENTS_SUBMIT_TAG = "investment.movements.submit"
