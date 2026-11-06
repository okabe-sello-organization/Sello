package com.software.sello.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.BulkAmountTone
import com.software.sello.designsystem.component.BulkCaption
import com.software.sello.designsystem.component.BulkLine
import com.software.sello.designsystem.component.BulkMarkTone
import com.software.sello.designsystem.component.BulkReceipt
import com.software.sello.designsystem.component.BulkTotal
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChipRows
import com.software.sello.designsystem.component.ConfirmSlip
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
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.movement.MovementCloseButton
import com.software.sello.presentation.movement.MovementDatePicker
import com.software.sello.presentation.movement.MovementNoteComposer
import com.software.sello.presentation.movement.MovementNoteLine

@Composable
fun GoalBulkRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val model: GoalBulkViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { model.onAction(GoalBulkAction.Back) }
    GoalBulkScreen(state, model::onAction)
}

/**
 * Aportar varios (docs/design/system/features/goals.html#screen-bulk): the goal's name under the
 * title while the lines name one goal, the receipt taking the free height, the goal chips, the
 * Aportar / Retirar chips and the note for the highlighted line, the one button and the pad. Each
 * line carries its own goal and date, the date shown as a pill on the highlighted line. Lines
 * without an amount are ignored; one with an amount and no goal or no type waits.
 */
@Composable
fun GoalBulkScreen(state: GoalBulkState, onAction: (GoalBulkAction) -> Unit) {
    SelloScaffold(
        title = stringResource(R.string.goal_bulk_title),
        subtitle = state.goalName,
        navigationIcon = {
            MovementCloseButton(
                { onAction(GoalBulkAction.Back) },
                Modifier.testTag(GOAL_BULK_CLOSE_TAG)
            )
        }
    ) { padding ->
        when {
            state.loading -> MovementPane(padding) {
                SlipSkeleton(Modifier.testTag("goal.bulk.loading"))
            }

            state.loadFailed -> MovementPane(padding) {
                StatusSlip(
                    kind = StatusKind.LoadFailed(stringResource(R.string.goal_failed_title)),
                    message = stringResource(R.string.goal_failed_body),
                    modifier = Modifier.testTag("goal.bulk.failed"),
                    action = StatusAction(StatusSlipDefaults.retryLabel) {
                        onAction(GoalBulkAction.Retry)
                    }
                )
            }

            else -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Receipt(state, onAction, Modifier.weight(1f))
                Box {
                    Choices(state, onAction)
                    state.removed?.let { removed ->
                        SelloNotice(
                            message = removed.withdrawal?.let {
                                stringResource(R.string.goal_bulk_removed, kindLabel(it))
                            } ?: stringResource(R.string.goal_bulk_removed_untyped),
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .testTag(GOAL_BULK_UNDO_TAG),
                            actionLabel = stringResource(R.string.correction_undo),
                            onAction = { onAction(GoalBulkAction.UndoRemove) }
                        )
                    }
                }
                Action(state, onAction)
                AmountKeypad(
                    { onAction(GoalBulkAction.Digit(it)) },
                    { onAction(GoalBulkAction.TripleZero) },
                    { onAction(GoalBulkAction.Backspace) },
                    { onAction(GoalBulkAction.ClearAmount) },
                    enabled = state.editable,
                    modifier = Modifier.testTag(GOAL_BULK_KEYPAD_TAG),
                    compact = true
                )
            }
        }
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = pluralStringResource(
                R.plurals.goal_bulk_discard_title,
                state.typed,
                state.typed
            ),
            message = AnnotatedString(stringResource(R.string.goal_bulk_discard_message)),
            confirmLabel = stringResource(R.string.expense_entry_discard_confirm),
            onConfirm = { onAction(GoalBulkAction.DiscardConfirmed) },
            onDismiss = { onAction(GoalBulkAction.KeepEditing) },
            dismissLabel = stringResource(R.string.goal_bulk_keep_editing)
        )
    }
    if (state.datePickerOpen) {
        MovementDatePicker(
            state.line.date,
            state.today,
            { onAction(GoalBulkAction.DatePicked(it)) },
            { onAction(GoalBulkAction.CloseDatePicker) }
        )
    }
    if (state.composerOpen) Composer(state, onAction)
}

@Composable
private fun kindLabel(withdrawal: Boolean) = stringResource(
    if (withdrawal) R.string.goal_movement_withdrawal else R.string.goal_movement_contribution
)

/** The receipt: what stops it or the line count, the lines, Otro renglón and the net. */
@Composable
private fun Receipt(state: GoalBulkState, onAction: (GoalBulkAction) -> Unit, modifier: Modifier) {
    val dates = dateLabels()
    val caption = when {
        state.refused > 0 -> BulkCaption.Problem(
            pluralStringResource(R.plurals.goal_bulk_review, state.refused, state.refused)
        )

        state.missingGoals > 0 -> BulkCaption.Problem(
            pluralStringResource(
                R.plurals.goal_bulk_missing_goals,
                state.missingGoals,
                state.missingGoals
            )
        )

        state.missing > 0 -> BulkCaption.Problem(
            pluralStringResource(R.plurals.goal_bulk_missing, state.missing, state.missing)
        )

        state.full -> BulkCaption.Problem(stringResource(R.string.goal_bulk_limit), icon = false)

        else -> BulkCaption.Count(
            pluralStringResource(R.plurals.goal_bulk_lines, state.lines.size, state.lines.size)
        )
    }
    val total = state.total
    BulkReceipt(
        lines = state.lines.mapIndexed { index, line ->
            bulkLine(line, footed = state.editable && index == state.highlighted, state, dates)
        },
        highlighted = state.highlighted,
        caption = caption,
        total = BulkTotal(
            stringResource(R.string.goal_bulk_total),
            total ?: MoneyTextValue(MoneySign.None, "", "—", "—"),
            if (total?.sign == MoneySign.Plus) BulkAmountTone.Gain else BulkAmountTone.Ink
        ),
        onSelect = { onAction(GoalBulkAction.Select(it)) },
        onRemove = { onAction(GoalBulkAction.Remove(it)) },
        onAdd = { onAction(GoalBulkAction.AddLine) },
        modifier = modifier.testTag(GOAL_BULK_RECEIPT_TAG),
        addEnabled = state.canAdd,
        enabled = state.editable,
        highlightedFoot = {
            FieldPill(
                text = dates.day(state.line.date, state.today),
                icon = SelloIcon.Event,
                onClick = { onAction(GoalBulkAction.OpenDatePicker) },
                modifier = Modifier.testTag(GOAL_BULK_DATE_TAG),
                enabled = state.editable
            )
        }
    )
}

/**
 * A receipt line titled with its goal: an aporte in gain green with a +, a retiro neutral
 * with a −. Its type, date and note sit under the goal, except on the [footed] line, whose
 * date is the pill below.
 */
@Composable
private fun bulkLine(
    line: GoalBulkLine,
    footed: Boolean,
    state: GoalBulkState,
    dates: DateLabels
): BulkLine {
    val day = dates.day(line.date, state.today)
    val note = line.note.takeIf { it.isNotBlank() }
    return BulkLine(
        key = line.key,
        mark = when (line.withdrawal) {
            true -> SelloIcon.Remove
            false -> SelloIcon.Add
            null -> null
        },
        markTone = if (line.withdrawal == true) BulkMarkTone.Neutral else BulkMarkTone.Gain,
        title = line.goalName ?: stringResource(R.string.goal_bulk_no_goal),
        titleSoft = line.goalName == null,
        detail = listOfNotNull(
            line.withdrawal?.let { kindLabel(it) } ?: stringResource(R.string.goal_bulk_no_kind),
            day.takeIf { !footed },
            note
        ).reduce { done, next -> stringResource(R.string.goal_movement_note, done, next) },
        amount = line.amount?.copy(
            sign = when (line.withdrawal) {
                true -> MoneySign.Minus
                false -> MoneySign.Plus
                null -> MoneySign.None
            }
        ),
        amountTone = if (line.withdrawal == false) BulkAmountTone.Gain else BulkAmountTone.Ink,
        reason = when {
            line.available != null ->
                stringResource(R.string.goal_bulk_available, line.available.digits)

            line.futureDate -> stringResource(R.string.goal_bulk_future_date)

            line.invalidDate -> stringResource(R.string.goal_bulk_invalid_date)

            line.missingGoal -> stringResource(R.string.goal_bulk_choose_goal)

            line.missingType -> stringResource(R.string.goal_bulk_choose_kind)

            else -> null
        }
    )
}

/**
 * The highlighted line's goal chips in two rows, as Anotar varios shows categories, then
 * Aportar or Retirar and its note.
 */
@Composable
private fun Choices(state: GoalBulkState, onAction: (GoalBulkAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EntryHeading(
            if (state.lines.size > 1) {
                stringResource(R.string.goal_bulk_choices_line, state.highlighted + 1)
            } else {
                stringResource(R.string.goal_bulk_choices)
            }
        )
        if (state.noActiveGoal) {
            FieldMessageText(
                FieldMessage(stringResource(R.string.goal_bulk_no_active_goal)),
                Modifier.testTag(GOAL_BULK_NO_GOAL_TAG)
            )
        }
        // Each line starts over, so the highlighted line's goal comes first.
        key(state.line.key) {
            val order = rememberSelectedFirst(state.goals, state.line.goalId) { it.id }
            ChipRows(order.items.size, Modifier.testTag(GOAL_BULK_GOALS_TAG)) { index ->
                val goal = order.items[index]
                SelloChip(
                    label = goal.name,
                    selected = goal.id == state.line.goalId && state.line.goalName != null,
                    onClick = {
                        order.picked()
                        onAction(GoalBulkAction.GoalPicked(goal.id))
                    },
                    icon = goalIcon(goal.iconKey),
                    enabled = state.editable,
                    modifier = Modifier.testTag("$GOAL_BULK_GOALS_TAG.${goal.id}")
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(GOAL_BULK_KINDS_TAG)
        ) {
            SelloChip(
                stringResource(R.string.goal_movement_contribute),
                state.line.withdrawal == false,
                { onAction(GoalBulkAction.Kind(withdrawal = false)) },
                enabled = state.editable,
                modifier = Modifier.testTag("goal.bulk.kind.contribution")
            )
            SelloChip(
                stringResource(R.string.goal_movement_withdraw),
                state.line.withdrawal == true,
                { onAction(GoalBulkAction.Kind(withdrawal = true)) },
                enabled = state.editable,
                modifier = Modifier.testTag("goal.bulk.kind.withdrawal")
            )
        }
        MovementNoteLine(
            state.line.note,
            state.editable,
            { onAction(GoalBulkAction.OpenComposer) },
            Modifier.testTag(GOAL_BULK_NOTE_TAG)
        )
    }
}

/** What stopped the receipt, then the one button, held with the pad so neither moves. */
@Composable
private fun Action(state: GoalBulkState, onAction: (GoalBulkAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        val message = when {
            state.error != null -> when (state.error) {
                GoalBulkError.Unknown -> R.string.expense_entry_unknown
                GoalBulkError.NotSaved, GoalBulkError.Refused -> R.string.goal_bulk_not_saved
                GoalBulkError.DataChanged -> R.string.goal_movement_data_changed
                GoalBulkError.Changed -> R.string.goal_bulk_changed
            }

            state.atAmountLimit -> R.string.expense_entry_amount_limit

            else -> null
        }
        message?.let {
            val unknown = state.error == GoalBulkError.Unknown
            val tone = if (unknown) FieldTone.Warning else FieldTone.Error
            FieldMessageText(
                FieldMessage(stringResource(it), tone),
                Modifier.testTag("goal.bulk.error")
            )
        }
        val unknown = state.error == GoalBulkError.Unknown
        val count = state.counted.coerceAtLeast(1)
        SelloButton(
            text = when {
                unknown -> stringResource(R.string.expense_entry_check_again)
                state.busy -> stringResource(R.string.expense_entry_saving)
                else -> pluralStringResource(R.plurals.goal_bulk_submit, count, count)
            },
            onClick = { onAction(if (unknown) GoalBulkAction.Retry else GoalBulkAction.Submit) },
            enabled = state.canSubmit || unknown,
            loading = state.busy,
            icon = SelloIcon.Add.takeIf { !unknown },
            tone = ButtonTone.Gain,
            modifier = Modifier.fillMaxWidth().testTag(GOAL_BULK_SUBMIT_TAG)
        )
    }
}

@Composable
private fun Composer(state: GoalBulkState, onAction: (GoalBulkAction) -> Unit) {
    val line = state.line
    MovementNoteComposer(
        kind =
            line.withdrawal?.let { kindLabel(it) } ?: stringResource(R.string.goal_bulk_note_kind),
        figure = line.amount?.let {
            when (line.withdrawal) {
                true -> "−${it.digits}"
                false -> "+${it.digits}"
                null -> it.digits
            }
        },
        draft = state.composerDraft,
        length = state.composerLength,
        problem = state.composerProblem,
        icon = when (line.withdrawal) {
            true -> SelloIcon.Remove
            false -> SelloIcon.Add
            null -> SelloIcon.Flag
        },
        spoken = stringResource(R.string.goal_movement_note_spoken),
        amountSpoken = line.amount?.spoken,
        onChange = { onAction(GoalBulkAction.ComposerChanged(it)) },
        onDone = { onAction(GoalBulkAction.ComposerDone) },
        onCancel = { onAction(GoalBulkAction.ComposerCancelled) }
    )
}

const val GOAL_BULK_CLOSE_TAG = "goal.bulk.close"
const val GOAL_BULK_RECEIPT_TAG = "goal.bulk.receipt"
const val GOAL_BULK_DATE_TAG = "goal.bulk.date"
const val GOAL_BULK_GOALS_TAG = "goal.bulk.goals"
const val GOAL_BULK_NO_GOAL_TAG = "goal.bulk.noGoal"
const val GOAL_BULK_KINDS_TAG = "goal.bulk.kinds"
const val GOAL_BULK_NOTE_TAG = "goal.bulk.note"
const val GOAL_BULK_UNDO_TAG = "goal.bulk.undo"
const val GOAL_BULK_KEYPAD_TAG = "goal.bulk.keypad"
const val GOAL_BULK_SUBMIT_TAG = "goal.bulk.submit"
