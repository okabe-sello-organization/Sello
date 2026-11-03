package com.software.sello.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.launch

/**
 * Recibo's goals, under the month's answer: the pill filled to its percentage, opening the
 * goal ([onOpen]) or Aportar ([onContribute]), and the green row for another goal at
 * 100 % with Marcar como lograda. With two to five goals they take turns in the pill's
 * place, swiping sideways and looping, with dots under it. Nothing when [state] is hidden.
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
            when (state.pills.size) {
                0 -> Unit

                // One goal is today's pill exactly: no swipe, no dots.
                1 -> ReciboPill(
                    state.pills.single(),
                    state.completion,
                    onAction,
                    onOpen,
                    onContribute,
                    Modifier.testTag(RECIBO_GOAL_TAG)
                )

                else -> GoalCarousel(state.pills, state.revision) {
                        pill,
                        pillModifier,
                        place,
                        actions
                    ->
                    ReciboPill(
                        pill,
                        state.completion,
                        onAction,
                        onOpen,
                        onContribute,
                        pillModifier,
                        place,
                        actions
                    )
                }
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

/** One goal's pill; [place] is its spoken description with its place among several. */
@Composable
private fun ReciboPill(
    pill: ReciboGoalPill,
    completion: CompletionState,
    onAction: (ReciboGoalAction) -> Unit,
    onOpen: (String) -> Unit,
    onContribute: (String) -> Unit,
    modifier: Modifier,
    place: String? = null,
    actions: List<CustomAccessibilityAction> = emptyList()
) {
    GoalPill(
        name = pill.name,
        percentage = stringResource(R.string.goal_percent, pill.percentage),
        progress = barFill(pill.percentage),
        icon = goalIcon(pill.iconKey),
        contributeLabel = stringResource(R.string.recibo_goal_contribute, pill.name),
        onOpen = { onOpen(pill.id) },
        onContribute = { onContribute(pill.id) },
        modifier = modifier,
        openDescription = place
            ?: stringResource(R.string.recibo_goal_open, pill.name, pill.percentage.toString()),
        reached = pill.reached,
        completeLabel = stringResource(R.string.recibo_goal_mark_complete, pill.name),
        onComplete = { onAction(ReciboGoalAction.MarkComplete(pill.id)) },
        completing = completion == CompletionState.Busy,
        customActions = actions
    )
}

/**
 * Two to five goals in one slot of the pill's height, one page each, looping: a large
 * virtual page count starting on a multiple of the goals, so the first goal is on screen
 * and either way leads on. The position is not kept: it opens on the first goal whenever
 * Recibo comes back, the goals change or the board is read at a new [revision]. Each pill
 * says its place ("Meta 2 de 4") and offers TalkBack the next and previous goal.
 */
@Composable
private fun GoalCarousel(
    pills: List<ReciboGoalPill>,
    revision: Long,
    pill: @Composable (ReciboGoalPill, Modifier, String, List<CustomAccessibilityAction>) -> Unit
) {
    val count = pills.size
    val ids = pills.map(ReciboGoalPill::id)
    // Not saveable on purpose: back on Recibo, it starts on the first goal again.
    val pager = remember(ids, revision) {
        PagerState(currentPage = count * LOOPS / 2) { count * LOOPS }
    }
    val scope = rememberCoroutineScope()
    val reduced = SelloTheme.reducedMotion
    val go: (Int) -> Unit = { page ->
        scope.launch {
            if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page)
        }
    }
    val next = stringResource(R.string.recibo_goal_next)
    val previous = stringResource(R.string.recibo_goal_previous)
    Column(
        Modifier.testTag(RECIBO_GOAL_TAG),
        verticalArrangement = Arrangement.spacedBy(DOT_GAP)
    ) {
        HorizontalPager(
            state = pager,
            pageSpacing = SelloTheme.spacing.sm,
            key = { it },
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val index = page.mod(count)
            val shown = pills[index]
            pill(
                shown,
                Modifier.testTag(RECIBO_GOAL_TAG + "." + shown.id),
                stringResource(
                    R.string.recibo_goal_open_place,
                    shown.name,
                    shown.percentage.toString(),
                    index + 1,
                    count
                ),
                listOf(
                    CustomAccessibilityAction(next) {
                        go(page + 1)
                        true
                    },
                    CustomAccessibilityAction(previous) {
                        go(page - 1)
                        true
                    }
                )
            )
        }
        Dots(count, pager.currentPage.mod(count))
    }
}

/** Where the carousel is: a short bar for the goal on screen, a dot for each other. */
@Composable
private fun Dots(count: Int, current: Int) {
    val colors = SelloTheme.colors
    Row(
        // Silent for TalkBack: each pill says its place.
        Modifier.fillMaxWidth().height(DOTS_HEIGHT)
            .clearAndSetSemantics { testTag = RECIBO_GOAL_DOTS_TAG },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val on = index == current
            Box(
                Modifier.size(width = if (on) 18.dp else 6.dp, height = 6.dp).clip(CircleShape)
                    .background(if (on) colors.brand else colors.outline.copy(alpha = 0.5f))
            )
        }
    }
}

/** Laps of the goals either way; far more than anyone swipes. */
private const val LOOPS = 2_000
private val DOTS_HEIGHT = 12.dp
private val DOT_GAP = 2.dp

/** The goal slot: the one pill, or the carousel holding several. */
const val RECIBO_GOAL_TAG = "recibo.goal"
const val RECIBO_GOAL_DOTS_TAG = "recibo.goal.dots"
const val RECIBO_GOAL_REACHED_TAG = "recibo.goal.reached"
const val RECIBO_GOAL_FAILED_TAG = "recibo.goal.failed"
const val RECIBO_GOAL_ERROR_TAG = "recibo.goal.error"
