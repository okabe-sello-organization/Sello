package com.software.sello.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FittedMoneyText
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MovementBalance
import com.software.sello.designsystem.component.MovementMonthLine
import com.software.sello.designsystem.component.MovementRow
import com.software.sello.designsystem.component.MovementTone
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalPlace
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import java.time.YearMonth

/**
 * One goal: what it holds and its stamps, its facts, aportes by month and its
 * movements. A completed goal is read-only; deleting it, from the top bar's ⋮, is its
 * only action, after the confirm slip. [onWithdraw] is the movements' Retirar dinero, offered only once that
 * screen exists.
 */
@Composable
fun GoalDetailScreen(
    state: GoalDetailState,
    onAction: (GoalDetailAction) -> Unit,
    dates: DateLabels,
    months: MonthNames,
    padding: PaddingValues,
    onWithdraw: (() -> Unit)? = null,
    onEditMovement: ((String) -> Unit)? = null,
    onBulk: (() -> Unit)? = null,
    landing: Set<GoalPlace> = emptySet(),
    relighting: Set<GoalPlace> = emptySet()
) {
    val spacing = SelloTheme.spacing
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
            .padding(vertical = spacing.lg).testTag(GOAL_DETAIL_TAG),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        when (state) {
            GoalDetailState.Loading -> SlipSkeleton(Modifier.testTag("goal.loading"))

            GoalDetailState.Failed -> StatusSlip(
                kind = StatusKind.LoadFailed(stringResource(R.string.goal_failed_title)),
                message = stringResource(R.string.goal_failed_body),
                modifier = Modifier.testTag("goal.failed"),
                action = StatusAction(StatusSlipDefaults.retryLabel) {
                    onAction(GoalDetailAction.Retry)
                }
            )

            GoalDetailState.Missing -> StatusSlip(
                kind = StatusKind.Gone(stringResource(R.string.goal_missing_title)),
                message = stringResource(R.string.goal_missing_body),
                modifier = Modifier.testTag("goal.missing")
            )

            is GoalDetailState.Ready -> Ready(
                state,
                onAction,
                dates,
                months,
                onWithdraw,
                onEditMovement,
                onBulk,
                landing,
                relighting
            )
        }
    }
}

@Composable
private fun ColumnScope.Ready(
    state: GoalDetailState.Ready,
    onAction: (GoalDetailAction) -> Unit,
    dates: DateLabels,
    months: MonthNames,
    onWithdraw: (() -> Unit)?,
    onEditMovement: ((String) -> Unit)?,
    onBulk: (() -> Unit)?,
    landing: Set<GoalPlace>,
    relighting: Set<GoalPlace>
) {
    val goal = state.goal
    // While the replace sheet is open it says what happened itself.
    state.error?.takeIf { state.replacing == null }?.let { error ->
        GoalSaveProblem(error, { onAction(GoalDetailAction.Retry) }, Modifier.testTag("goal.error"))
    }
    if (state.stale) {
        StatusSlip(
            kind = StatusKind.Older,
            message = stringResource(R.string.metas_stale),
            modifier = Modifier.testTag("goal.stale"),
            action = StatusAction(StatusSlipDefaults.retryLabel) {
                onAction(GoalDetailAction.Retry)
            }
        )
    }
    Hero(goal, state, dates, months, onAction, landing, relighting)
    if (goal.completedOn == null) Facts(goal, dates)
    Aportes(goal, months)
    Movements(
        state,
        onAction,
        dates,
        months,
        onWithdraw.takeIf { goal.completedOn == null },
        onEditMovement.takeIf { goal.completedOn == null },
        onBulk.takeIf { goal.completedOn == null }
    )
    if (goal.completedOn != null) {
        if (state.confirmingDelete) {
            ConfirmSlip(
                title = stringResource(R.string.goal_delete_confirm_title, goal.name),
                message = deleteConsequencesText(goal.movements.size),
                confirmLabel = stringResource(R.string.goal_delete_confirm),
                onConfirm = { onAction(GoalDetailAction.Delete) },
                onDismiss = { onAction(GoalDetailAction.CancelDelete) },
                busy = state.busy
            )
        }
    }
}

/** "Se borran la meta y sus 6 movimientos. …", the count in bold. */
@Composable
internal fun deleteConsequencesText(count: Int): AnnotatedString {
    if (count == 0) return AnnotatedString(stringResource(R.string.goal_delete_consequences_none))
    val movements = pluralStringResource(R.plurals.goal_delete_movements, count, count)
    val sentence = stringResource(R.string.goal_delete_consequences, movements)
    val start = sentence.indexOf(movements)
    return buildAnnotatedString {
        append(sentence)
        if (start >= 0) {
            addStyle(
                SpanStyle(fontWeight = SelloTheme.type.weight.emphasis),
                start,
                start + movements.length
            )
        }
    }
}

/** The pinked card: saved and percentage, the bar, then the stamps and what comes next. */
@Composable
private fun Hero(
    goal: GoalDetailModel,
    state: GoalDetailState.Ready,
    dates: DateLabels,
    months: MonthNames,
    onAction: (GoalDetailAction) -> Unit,
    landing: Set<GoalPlace>,
    relighting: Set<GoalPlace>
) {
    val colors = SelloTheme.colors
    Slip(Modifier.fillMaxWidth().testTag("goal.hero")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.goal_saved_label),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkSoft
            )
            Text(
                stringResource(R.string.goal_percent, goal.percentage),
                color = colors.semantic.gain,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = SelloTheme.type.weight.figure
            )
        }
        FittedMoneyText(goal.saved, Modifier.padding(top = SelloTheme.spacing.xs), startSize = 40)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = SelloTheme.spacing.sm)) {
            Text(
                stringResource(R.string.goal_target_of, goal.target.digits),
                Modifier.weight(1f),
                color = colors.inkSoft,
                style = MaterialTheme.typography.bodySmall
            )
            goal.missing?.let {
                Text(
                    stringResource(R.string.goal_missing, it.digits),
                    color = colors.inkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(colors.paperDim)) {
            Box(
                Modifier.fillMaxWidth(barFill(goal.percentage)).fillMaxHeight()
                    .background(colors.semantic.gain)
            )
        }
        TearLine()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.goal_stamps),
                Modifier.weight(1f).semantics { heading() },
                fontWeight = SelloTheme.type.weight.emphasis
            )
            Text(
                stringResource(R.string.goal_stamps_count, goal.earnedCount),
                color = colors.inkSoft,
                style = MaterialTheme.typography.bodySmall
            )
        }
        // A confirmed save lands what it earned and lights again what came back, in place.
        MilestoneRow(
            goal.places.map { milestone(it) },
            Modifier.testTag("goal.stamps"),
            landing = goal.places.indices.filter { goal.places[it].place in landing }.toSet(),
            relighting = goal.places.indices.filter { goal.places[it].place in relighting }.toSet()
        )
        goal.kept.forEach { kept ->
            FactLine(
                AnnotatedString(
                    stringResource(
                        R.string.goal_kept,
                        placeLabel(kept.place),
                        kept.earnedOn?.let { months.inSentence(java.time.YearMonth.from(it)) }
                            ?: ""
                    )
                ),
                Modifier.testTag("goal.kept.${kept.place.name}")
            )
        }
        val next = goal.next
        val completedOn = goal.completedOn
        FactLine(
            AnnotatedString(
                when {
                    completedOn != null -> stringResource(
                        R.string.goal_completed_on,
                        dates.shortDay(completedOn, state.today)
                    )

                    next == null -> ""

                    next.reached -> stringResource(R.string.goal_reached_mark)

                    else -> stringResource(
                        R.string.goal_next,
                        if (next.place == GoalPlace.Meta) {
                            stringResource(R.string.goal_next_meta)
                        } else {
                            placeLabel(next.place)
                        },
                        next.missing.digits
                    )
                }
            ),
            Modifier.testTag("goal.next")
        )
        if (completedOn == null && next?.reached == true) {
            SelloButton(
                stringResource(R.string.goal_mark_complete),
                { onAction(GoalDetailAction.MarkComplete) },
                Modifier.fillMaxWidth()
                    .padding(top = SelloTheme.spacing.sm)
                    .testTag(GOAL_MARK_COMPLETE_TAG),
                icon = SelloIcon.Check,
                enabled = !state.busy,
                loading = state.completion == CompletionState.Busy,
                tone = ButtonTone.Gain
            )
        }
    }
}

@Composable
private fun Facts(goal: GoalDetailModel, dates: DateLabels) {
    Slip(Modifier.fillMaxWidth().testTag("goal.facts"), pinked = false) {
        LeaderLine(stringResource(R.string.goal_fact_target)) { MoneyText(goal.target) }
        LeaderLine(
            stringResource(R.string.goal_fact_date),
            goal.targetMonth?.let(dates::monthYear) ?: stringResource(R.string.goal_no_date)
        )
        LeaderLine(
            stringResource(R.string.goal_fact_since),
            dates.monthYear(java.time.YearMonth.from(goal.since))
        )
    }
}

@Composable
private fun Aportes(goal: GoalDetailModel, months: MonthNames) {
    if (goal.months.isEmpty()) return
    val points = goal.months.map { month ->
        ChartPoint(
            id = month.month.toString(),
            axisLabel = months.short(month.month),
            value = month.value,
            spoken = stringResource(
                R.string.goal_aportes_point,
                months.title(month.month),
                month.amount.spoken
            ),
            period = if (month.current) ChartPeriod.Current else ChartPeriod.Past
        )
    }
    Slip(Modifier.fillMaxWidth().testTag("goal.aportes"), pinked = false) {
        Text(
            stringResource(R.string.goal_aportes_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = SelloTheme.type.weight.emphasis
        )
        ColumnsChart(
            points = points,
            summary = stringResource(
                R.string.goal_aportes_summary,
                months.title(goal.months.first().month),
                months.title(goal.months.last().month)
            ),
            height = 120.dp
        )
    }
}

@Composable
private fun Movements(
    state: GoalDetailState.Ready,
    onAction: (GoalDetailAction) -> Unit,
    dates: DateLabels,
    months: MonthNames,
    onWithdraw: (() -> Unit)?,
    onEditMovement: ((String) -> Unit)?,
    onBulk: (() -> Unit)?
) {
    val goal = state.goal
    Slip(Modifier.fillMaxWidth().testTag("goal.movements"), pinked = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.goal_movements_title),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = SelloTheme.type.weight.emphasis
            )
            if (onWithdraw != null) {
                SelloButton(
                    stringResource(R.string.goal_withdraw),
                    onWithdraw,
                    Modifier.testTag("goal.withdraw"),
                    kind = ButtonKind.Text
                )
            }
        }
        if (goal.movements.isEmpty()) {
            Text(
                stringResource(R.string.goal_movements_none),
                color = SelloTheme.colors.inkSoft,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        val shown = if (state.allMovements) goal.movements else goal.movements.take(SHORT_LIST)
        val balanceLabel = stringResource(R.string.goal_movement_balance)
        // The full list is grouped by month, each with its net; the short list is not.
        val month: (MovementModel) -> YearMonth = { YearMonth.from(it.date) }
        shown.forEachIndexed { index, movement ->
            val first = index == 0 || month(shown[index - 1]) != month(movement)
            if (state.allMovements && first) {
                MovementMonthLine(
                    // Another year's month says its year, as its rows' days do.
                    month(movement).let {
                        if (it.year == state.today.year) months.name(it) else months.title(it)
                    },
                    goal.movementMonths.getValue(month(movement)),
                    Modifier.testTag("goal.movements.month.${month(movement)}")
                )
            }
            val next = shown.getOrNull(index + 1)
            val day = dates.day(movement.date, state.today)
            val earned = movement.stamps
            val kind = stringResource(
                when (movement.kind) {
                    GoalMovementKind.Contribution -> R.string.goal_movement_contribution
                    GoalMovementKind.Withdrawal -> R.string.goal_movement_withdrawal
                }
            )
            MovementRow(
                // A note names the movement better than its kind (owner, 2026-11-02).
                title = movement.note ?: kind,
                detail = movement.note?.let {
                    stringResource(R.string.goal_movement_kind_day, kind, day)
                } ?: day,
                amount = movement.amount,
                modifier = Modifier.testTag("goal.movement.${movement.id}").then(
                    onEditMovement?.let { edit ->
                        Modifier.clickable(role = Role.Button) { edit(movement.id) }
                    } ?: Modifier
                ),
                milestone = earned.lastOrNull()?.let { place ->
                    milestone(goal.places.first { it.place == place })
                },
                icon = when (movement.kind) {
                    GoalMovementKind.Contribution -> SelloIcon.Add
                    GoalMovementKind.Withdrawal -> SelloIcon.Remove
                },
                annotation = when (earned.size) {
                    0 -> null

                    1 -> stringResource(R.string.goal_movement_earned, placeLabel(earned.single()))

                    else -> stringResource(
                        R.string.goal_movement_earned_many,
                        earned.dropLast(1).map { placeLabel(it) }.joinToString(", "),
                        placeLabel(earned.last())
                    )
                },
                tone = when (movement.kind) {
                    GoalMovementKind.Contribution -> MovementTone.In
                    GoalMovementKind.Withdrawal -> MovementTone.Out
                },
                amountColor = when (movement.kind) {
                    GoalMovementKind.Contribution -> SelloTheme.colors.semantic.gain
                    GoalMovementKind.Withdrawal -> Color.Unspecified
                },
                balance = MovementBalance(balanceLabel, movement.balance),
                rule = next != null && !(state.allMovements && month(next) != month(movement))
            )
        }
        val showAll = !state.allMovements && goal.movements.size > SHORT_LIST
        if (onBulk != null || showAll) {
            // Aportar varios at the start of the foot, Ver los N at its end.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBulk != null) {
                    SelloButton(
                        stringResource(R.string.goal_bulk_title),
                        onBulk,
                        Modifier.testTag(GOAL_BULK_OPEN_TAG),
                        kind = ButtonKind.Text,
                        icon = SelloIcon.ReceiptLong
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showAll) {
                    SelloButton(
                        pluralStringResource(
                            R.plurals.goal_movements_all,
                            goal.movements.size,
                            goal.movements.size
                        ),
                        { onAction(GoalDetailAction.ShowAllMovements) },
                        Modifier.testTag("goal.showAll"),
                        kind = ButtonKind.Text
                    )
                }
            }
        }
    }
}

/**
 * The star pill under the goal's name, on the band: whether the person starred the goal,
 * not whether Recibo shows it (an unstarred goal may fill a place there). Tapping stars or
 * unstars it. Not shown on a completed goal.
 */
@Composable
fun GoalPin(state: GoalDetailState.Ready, onAction: (GoalDetailAction) -> Unit) {
    val starred = state.goal.starred
    Box(Modifier.fillMaxWidth().padding(bottom = SelloTheme.spacing.xs)) {
        GoalPinPill(
            label = stringResource(if (starred) R.string.goal_pin_on else R.string.goal_pin_off),
            selected = starred,
            onClick = { onAction(GoalDetailAction.ToggleStar) },
            enabled = !state.busy,
            modifier = Modifier.testTag("goal.pin")
        )
    }
}

private const val SHORT_LIST = 4

const val GOAL_DETAIL_TAG = "goal.detail"
const val GOAL_MARK_COMPLETE_TAG = "goal.markComplete"

const val GOAL_BULK_OPEN_TAG = "goal.bulk.open"
