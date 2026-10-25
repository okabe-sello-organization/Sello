package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.GoalPill
import com.software.sello.designsystem.component.ReachedRow
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Recibo's goal, under the month's answer: the pill filled to its percentage, opening the
 * goal ([onOpen]) or Aportar ([onContribute]), and the green row for another goal at
 * 100 % with Marcar como lograda. Nothing when [state] is hidden.
 */
@Composable
fun ReciboGoal(
    state: ReciboGoalState,
    onAction: (ReciboGoalAction) -> Unit,
    onOpen: (String) -> Unit,
    onContribute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        ReciboGoalState.Hidden -> Unit

        ReciboGoalState.Failed -> ErrorSlip(
            message = stringResource(R.string.metas_failed_body),
            onRetry = { onAction(ReciboGoalAction.Retry) },
            modifier = modifier.testTag(RECIBO_GOAL_FAILED_TAG),
            title = stringResource(R.string.metas_failed_title)
        )

        is ReciboGoalState.Shown -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
        ) {
            state.pill?.let { pill ->
                GoalPill(
                    name = pill.name,
                    percentage = stringResource(R.string.goal_percent, pill.percentage),
                    progress = barFill(pill.percentage),
                    icon = goalIcon(pill.iconKey),
                    contributeLabel = stringResource(R.string.recibo_goal_contribute, pill.name),
                    onOpen = { onOpen(pill.id) },
                    onContribute = { onContribute(pill.id) },
                    modifier = Modifier.testTag(RECIBO_GOAL_TAG),
                    openDescription = stringResource(
                        R.string.recibo_goal_open,
                        pill.name,
                        pill.percentage.toString()
                    )
                )
            }
            (state.completion as? CompletionState.Failed)?.let { failed ->
                ErrorSlip(
                    message = stringResource(
                        when (failed.error) {
                            GoalDetailError.NotSaved -> R.string.goal_error_not_saved
                            GoalDetailError.Unknown -> R.string.goal_error_unknown
                        }
                    ),
                    onRetry = { onAction(ReciboGoalAction.Retry) },
                    modifier = Modifier.testTag(RECIBO_GOAL_ERROR_TAG),
                    retryLabel = if (failed.error == GoalDetailError.Unknown) {
                        stringResource(R.string.goal_error_check)
                    } else {
                        stringResource(R.string.goal_error_retry)
                    }
                )
            }
            state.reached?.let { reached ->
                ReachedRow(
                    name = reached.name,
                    message = stringResource(R.string.recibo_goal_reached),
                    actionLabel = stringResource(R.string.goal_mark_complete),
                    onComplete = { onAction(ReciboGoalAction.MarkComplete(reached.id)) },
                    modifier = Modifier.testTag(RECIBO_GOAL_REACHED_TAG),
                    busy = state.completion == CompletionState.Busy
                )
            }
        }
    }
}

const val RECIBO_GOAL_TAG = "recibo.goal"
const val RECIBO_GOAL_REACHED_TAG = "recibo.goal.reached"
const val RECIBO_GOAL_FAILED_TAG = "recibo.goal.failed"
const val RECIBO_GOAL_ERROR_TAG = "recibo.goal.error"
