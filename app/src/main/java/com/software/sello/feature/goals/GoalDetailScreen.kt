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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FittedMoneyText
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MovementRow
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalPlace
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames

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

            GoalDetailState.Failed -> ErrorSlip(
                message = stringResource(R.string.goal_failed_body),
                onRetry = { onAction(GoalDetailAction.Retry) },
                modifier = Modifier.testTag("goal.failed"),
                title = stringResource(R.string.goal_failed_title),
                icon = SelloIcon.Error
            )

            GoalDetailState.Missing -> Slip(Modifier.fillMaxWidth().testTag("goal.missing")) {
                Text(
                    stringResource(R.string.goal_missing_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.goal_missing_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SelloTheme.colors.inkSoft
                )
            }

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
    state.error?.let { error ->
        ErrorSlip(
            message = stringResource(
                when (error) {
                    GoalDetailError.NotSaved -> R.string.goal_error_not_saved
                    GoalDetailError.Unknown -> R.string.goal_error_unknown
                }
            ),
            onRetry = { onAction(GoalDetailAction.Retry) },
            modifier = Modifier.testTag("goal.error"),
            retryLabel = if (error == GoalDetailError.Unknown) {
                stringResource(R.string.goal_error_check)
            } else {
                stringResource(R.string.goal_error_retry)
            }
        )
    }
    if (state.stale) {
        ErrorSlip(
            stringResource(R.string.metas_stale),
            { onAction(GoalDetailAction.Retry) },
            Modifier.testTag("goal.stale")
        )
    }
    Hero(goal, state, dates, months, onAction, landing, relighting)
    if (goal.completedOn == null) Facts(goal, dates)
    Aportes(goal, months)
    Movements(
        state,
        onAction,
        dates,
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
            addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + movements.length)
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
                fontWeight = FontWeight.W900
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
                fontWeight = FontWeight.W800
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
            fontWeight = FontWeight.W800
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
                fontWeight = FontWeight.W800
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
        shown.forEach { movement ->
            val day = dates.day(movement.date, state.today)
            val earned = movement.stamps
            MovementRow(
                kind = stringResource(
                    when (movement.kind) {
                        GoalMovementKind.Contribution -> R.string.goal_movement_contribution
                        GoalMovementKind.Withdrawal -> R.string.goal_movement_withdrawal
                    }
                ),
                detail = movement.note?.let {
                    stringResource(R.string.goal_movement_note, day, it)
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
                    GoalMovementKind.Withdrawal -> SelloIcon.ArrowBack
                },
                annotation = when (earned.size) {
                    0 -> null

                    1 -> stringResource(R.string.goal_movement_earned, placeLabel(earned.single()))

                    else -> stringResource(
                        R.string.goal_movement_earned_many,
                        earned.dropLast(1).map { placeLabel(it) }.joinToString(", "),
                        placeLabel(earned.last())
                    )
                }
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

/** The star pill under the goal's name, on the band. Not shown on a completed goal. */
@Composable
fun GoalPin(state: GoalDetailState.Ready, onAction: (GoalDetailAction) -> Unit) {
    val featured = state.goal.featured
    Box(Modifier.fillMaxWidth().padding(bottom = SelloTheme.spacing.xs)) {
        GoalPinPill(
            label = stringResource(if (featured) R.string.goal_pin_on else R.string.goal_pin_off),
            selected = featured,
            onClick = { onAction(GoalDetailAction.Feature) },
            modifier = Modifier.testTag("goal.pin")
        )
    }
}

private const val SHORT_LIST = 4

const val GOAL_DETAIL_TAG = "goal.detail"
const val GOAL_MARK_COMPLETE_TAG = "goal.markComplete"

const val GOAL_BULK_OPEN_TAG = "goal.bulk.open"
