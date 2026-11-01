package com.software.sello.feature.goals

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
import androidx.compose.ui.semantics.contentDescription
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
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.rememberSelectedFirst
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.movement.MovementCloseButton
import com.software.sello.presentation.movement.MovementDatePicker
import com.software.sello.presentation.movement.MovementDiscardSlip
import com.software.sello.presentation.movement.MovementNoteComposer
import com.software.sello.presentation.movement.MovementNoteLine
import com.software.sello.presentation.movement.MovementPane

@Composable
fun GoalMovementRoot(
    viewModels: ViewModelProvider.Factory,
    onSeveral: ((GoalSeveral) -> Unit)? = null,
    onClosed: () -> Unit
) {
    val model: GoalMovementViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    LaunchedEffect(state.several) { state.several?.let { onSeveral?.invoke(it) } }
    BackHandler { model.onAction(GoalMovementAction.Back) }
    GoalMovementScreen(state, severalOffered = onSeveral != null, onAction = model::onAction)
}

/**
 * Aportar and Retirar (v2 aportar): the entry screen's layout with the Aportar / Retirar
 * switch, a green "+" amount with the goal's icon or an ink "−" one, the goal chips with
 * their percentage, what the goal holds, the date pill and the note. Correcting a
 * movement uses the same form with Borrar.
 */
@Composable
fun GoalMovementScreen(
    state: GoalMovementState,
    severalOffered: Boolean = false,
    onAction: (GoalMovementAction) -> Unit
) {
    SelloScaffold(
        title = stringResource(
            when {
                !state.editing && state.withdrawal -> R.string.goal_movement_withdraw
                !state.editing -> R.string.goal_movement_contribute
                state.withdrawal -> R.string.goal_movement_edit_withdrawal
                else -> R.string.goal_movement_edit_contribution
            }
        ),
        band = SelloTheme.colors.goalBand,
        navigationIcon = {
            MovementCloseButton(
                { onAction(GoalMovementAction.Back) },
                Modifier.testTag(GOAL_MOVEMENT_CLOSE_TAG)
            )
        },
        actions = {
            // Aportar varios for the chosen goal, taking what is typed so far as its first line.
            if (severalOffered && !state.editing && !state.loading && !state.loadFailed) {
                val description = stringResource(R.string.goal_bulk_title)
                FieldPill(
                    text = stringResource(R.string.expense_entry_several),
                    icon = SelloIcon.ReceiptLong,
                    onClick = { onAction(GoalMovementAction.Several) },
                    modifier = Modifier
                        .testTag(GOAL_MOVEMENT_SEVERAL_TAG)
                        .semantics { contentDescription = description },
                    enabled = state.canOpenSeveral
                )
            }
            if (state.editing) {
                SelloButton(
                    stringResource(R.string.expense_edit_delete),
                    { onAction(GoalMovementAction.Delete) },
                    kind = ButtonKind.Text,
                    enabled = state.canDelete,
                    tone = ButtonTone.OnBand,
                    modifier = Modifier.testTag(GOAL_MOVEMENT_DELETE_TAG)
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> MovementPane(padding) {
                SlipSkeleton(Modifier.testTag("goal.movement.loading"))
            }

            state.loadFailed -> MovementPane(padding) {
                ErrorSlip(
                    stringResource(R.string.goal_failed_body),
                    { onAction(GoalMovementAction.Retry) },
                    Modifier.testTag("goal.movement.failed"),
                    title = stringResource(R.string.goal_failed_title)
                )
            }

            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                band = SelloTheme.colors.goalBand,
                header = {
                    if (!state.editing) {
                        SegmentedSwitch(
                            listOf(
                                stringResource(R.string.goal_movement_contribute),
                                stringResource(R.string.goal_movement_withdraw)
                            ),
                            if (state.withdrawal) 1 else 0,
                            { onAction(GoalMovementAction.Kind(withdrawal = it == 1)) },
                            Modifier.testTag(GOAL_MOVEMENT_KIND_TAG),
                            enabled = state.editable,
                            band = SelloTheme.colors.goalBand
                        )
                    }
                },
                amount = { Amount(state, onAction) },
                choices = { Choices(state, onAction) },
                action = { Action(state, onAction) },
                keypad = {
                    AmountKeypad(
                        { onAction(GoalMovementAction.Digit(it)) },
                        { onAction(GoalMovementAction.TripleZero) },
                        { onAction(GoalMovementAction.Backspace) },
                        { onAction(GoalMovementAction.ClearAmount) },
                        enabled = state.editable,
                        modifier = Modifier.testTag(GOAL_MOVEMENT_KEYPAD_TAG),
                        compact = true
                    )
                }
            )
        }
    }
    if (state.confirmDiscard) {
        MovementDiscardSlip(
            stringResource(R.string.goal_movement_discard_title),
            { onAction(GoalMovementAction.DiscardConfirmed) },
            { onAction(GoalMovementAction.KeepEditing) }
        )
    }
    if (state.datePickerOpen) {
        MovementDatePicker(
            state.date,
            state.today,
            { onAction(GoalMovementAction.DatePicked(it)) },
            { onAction(GoalMovementAction.CloseDatePicker) }
        )
    }
    if (state.composerOpen) Composer(state, onAction)
}

/** "Aporte en pesos" and the green "+" amount, or "Retiro en pesos" and the ink "−". */
@Composable
private fun Amount(state: GoalMovementState, onAction: (GoalMovementAction) -> Unit) {
    val day = dateLabels().day(state.date, state.today)
    val over = state.overSaved
    EntryAmountSlip(
        label = stringResource(
            if (state.withdrawal) {
                R.string.goal_movement_withdrawal_label
            } else {
                R.string.goal_movement_contribution_label
            }
        ),
        // The sign is drawn; what is read stays the amount itself.
        amount = state.amount?.copy(
            sign = if (state.withdrawal) MoneySign.Minus else MoneySign.Plus
        ),
        modifier = Modifier.testTag(GOAL_MOVEMENT_AMOUNT_TAG),
        error = when {
            over != null -> stringResource(R.string.goal_movement_over_saved, over.digits)
            state.atLimit -> stringResource(R.string.expense_entry_amount_limit)
            state.amountText != null -> stringResource(R.string.goal_movement_amount_invalid)
            else -> null
        },
        date = {
            FieldPill(
                text = day,
                icon = SelloIcon.Event,
                onClick = { onAction(GoalMovementAction.OpenDatePicker) },
                modifier = Modifier.testTag(GOAL_MOVEMENT_DATE_TAG),
                enabled = state.editable
            )
        },
        icon = state.goal?.let { goalIcon(it.iconKey) } ?: SelloIcon.Flag,
        tone = if (state.withdrawal) ButtonTone.Ink else ButtonTone.Gain,
        limitPulse = state.limitPulse,
        rejectedText = state.amountText,
        onPaste = if (state.editable) {
            { onAction(GoalMovementAction.Paste(it)) }
        } else {
            null
        }
    )
}

/** The goals with their percentage, what the chosen one holds, and the note. */
@Composable
private fun Choices(state: GoalMovementState, onAction: (GoalMovementAction) -> Unit) {
    Text(
        stringResource(R.string.goal_movement_goal),
        Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall
    )
    if (state.goals.isEmpty()) {
        FactLine(AnnotatedString(stringResource(R.string.goal_movement_no_goals)))
    } else {
        val order = rememberSelectedFirst(state.goals, state.goalId) { it.id }
        ChipRows(order.items.size, Modifier.testTag(GOAL_MOVEMENT_GOALS_TAG)) { index ->
            val goal = order.items[index]
            SelloChip(
                goal.name,
                state.goalId == goal.id,
                {
                    order.picked()
                    onAction(GoalMovementAction.GoalPicked(goal.id))
                },
                icon = goalIcon(goal.iconKey),
                enabled = state.editable && !state.editing,
                trailing = stringResource(R.string.goal_percent, goal.percentage),
                modifier = Modifier.testTag("goal.movement.goal.${goal.id}")
            )
        }
    }
    val goal = state.goal
    if (goal != null) {
        val fact = stringResource(R.string.goal_movement_saved_fact, goal.saved.digits)
        val start = fact.indexOf(goal.saved.digits)
        FactLine(
            buildAnnotatedString {
                append(fact)
                if (start >= 0) {
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.W800),
                        start,
                        start + goal.saved.digits.length
                    )
                }
            },
            Modifier.testTag("goal.movement.saved")
        )
    }
    MovementNoteLine(
        state.note,
        state.editable,
        { onAction(GoalMovementAction.OpenComposer) },
        Modifier.testTag(GOAL_MOVEMENT_NOTE_TAG)
    )
}

/** What stops the movement, then the one button, held with the pad so neither moves. */
@Composable
private fun Action(state: GoalMovementState, onAction: (GoalMovementAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        val dates = dateLabels()
        state.blockedBy?.let { blocked ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        R.string.goal_movement_blocked,
                        blocked.amount.digits,
                        dates.shortDay(blocked.date, state.today)
                    )
                ),
                Modifier.testTag("goal.movement.blocked")
            )
        }
        state.error?.let { error ->
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        when (error) {
                            GoalMovementError.Unknown -> R.string.expense_entry_unknown
                            GoalMovementError.FutureDate -> R.string.expense_entry_future_date
                            GoalMovementError.DataChanged -> R.string.goal_movement_data_changed
                            GoalMovementError.Gone -> R.string.goal_movement_gone
                            GoalMovementError.Changed -> R.string.goal_movement_changed
                            GoalMovementError.Completed -> R.string.goal_movement_completed
                            GoalMovementError.NotDeleted -> R.string.goal_movement_not_deleted
                            GoalMovementError.NotSaved -> R.string.goal_movement_not_saved
                        }
                    )
                ),
                Modifier.testTag("goal.movement.error")
            )
        }
        val hint = when {
            state.goal == null && state.goals.isNotEmpty() -> R.string.goal_movement_choose_goal
            state.editing && !state.changed -> R.string.expense_edit_no_changes
            else -> null
        }
        if (hint != null && !state.busy && state.error == null) {
            FieldMessageText(
                FieldMessage(stringResource(hint), isError = false),
                Modifier.testTag("goal.movement.missing")
            )
        }
        val unknown = state.error == GoalMovementError.Unknown
        SelloButton(
            text = stringResource(
                when {
                    unknown -> R.string.expense_entry_check_again
                    state.busy -> R.string.expense_entry_saving
                    state.editing -> R.string.expense_edit_submit
                    state.withdrawal -> R.string.goal_movement_record_withdrawal
                    else -> R.string.goal_movement_record_contribution
                }
            ),
            onClick = {
                onAction(if (unknown) GoalMovementAction.Retry else GoalMovementAction.Submit)
            },
            enabled = state.canSubmit || unknown,
            loading = state.busy,
            icon = SelloIcon.Flag.takeIf { !state.withdrawal && !state.editing },
            tone = if (state.withdrawal) ButtonTone.Ink else ButtonTone.Gain,
            modifier = Modifier.fillMaxWidth().testTag(GOAL_MOVEMENT_SUBMIT_TAG)
        )
    }
}

@Composable
private fun Composer(state: GoalMovementState, onAction: (GoalMovementAction) -> Unit) {
    MovementNoteComposer(
        kind = stringResource(
            if (state.withdrawal) {
                R.string.goal_movement_withdraw
            } else {
                R.string.goal_movement_contribute
            }
        ),
        figure = state.amount?.let { (if (state.withdrawal) "−" else "+") + it.digits },
        draft = state.composerDraft,
        length = state.composerLength,
        problem = state.composerProblem,
        icon = state.goal?.let { goalIcon(it.iconKey) } ?: SelloIcon.Flag,
        spoken = stringResource(R.string.goal_movement_note_spoken),
        amountSpoken = state.amount?.spoken,
        onChange = { onAction(GoalMovementAction.ComposerChanged(it)) },
        onDone = { onAction(GoalMovementAction.ComposerDone) },
        onCancel = { onAction(GoalMovementAction.ComposerCancelled) }
    )
}

const val GOAL_MOVEMENT_CLOSE_TAG = "goal.movement.close"
const val GOAL_MOVEMENT_DELETE_TAG = "goal.movement.delete"
const val GOAL_MOVEMENT_SEVERAL_TAG = "goal.movement.several"
const val GOAL_MOVEMENT_KIND_TAG = "goal.movement.kind"
const val GOAL_MOVEMENT_AMOUNT_TAG = "goal.movement.amount"
const val GOAL_MOVEMENT_DATE_TAG = "goal.movement.date"
const val GOAL_MOVEMENT_GOALS_TAG = "goal.movement.goals"
const val GOAL_MOVEMENT_NOTE_TAG = "goal.movement.note"
const val GOAL_MOVEMENT_KEYPAD_TAG = "goal.movement.keypad"
const val GOAL_MOVEMENT_SUBMIT_TAG = "goal.movement.submit"
