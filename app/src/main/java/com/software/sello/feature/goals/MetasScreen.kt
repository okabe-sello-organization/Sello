package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GoalPlace
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames

/**
 * Metas: a stamp card per active goal, highest completion first, then the completed
 * goals as rows whose LOGRADA comes down once as each first scrolls into view. Loading
 * and a failed read are never "no goals". [onBulk] (Anotar varios, with no goal chosen)
 * sits at the foot of the active goals, and only while there is one.
 */
@Composable
fun MetasScreen(
    state: MetasState,
    onAction: (MetasAction) -> Unit,
    dates: DateLabels,
    padding: PaddingValues,
    onBulk: (() -> Unit)? = null
) {
    val direction = LocalLayoutDirection.current
    val spacing = SelloTheme.spacing
    // Completed rows whose stamp already came down on this visit.
    val landed: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableListOf<String>().toMutableStateList() }
    val list = rememberLazyListState()
    // A completion that did not finish is said first in the list; bring it into view, since
    // the button pressed may be scrolled well below it.
    val failed = ((state as? MetasState.Ready)?.completion as? CompletionState.Failed)?.error
    LaunchedEffect(failed) { if (failed != null) list.animateScrollToItem(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(METAS_LIST_TAG),
        state = list,
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding() + spacing.lg,
            end = padding.calculateEndPadding(direction),
            bottom = padding.calculateBottomPadding() + spacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        when (state) {
            MetasState.Loading -> item {
                Column(
                    Modifier.testTag("metas.loading"),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg)
                ) {
                    SlipSkeleton()
                    SlipSkeleton(lines = 2)
                }
            }

            MetasState.Failed -> item {
                StatusSlip(
                    kind = StatusKind.LoadFailed(stringResource(R.string.metas_failed_title)),
                    message = stringResource(R.string.metas_failed_body),
                    modifier = Modifier.testTag("metas.failed"),
                    action = StatusAction(StatusSlipDefaults.retryLabel) {
                        onAction(MetasAction.Retry)
                    }
                )
            }

            is MetasState.Ready -> {
                // A Marcar como lograda that did not finish, said as the goal's own page
                // says it: Reintentar after a refusal, Comprobar si se guardó when unknown.
                (state.completion as? CompletionState.Failed)?.let { failed ->
                    item(key = METAS_COMPLETION_PROBLEM_TAG) {
                        GoalSaveProblem(
                            failed.error,
                            { onAction(MetasAction.Retry) },
                            Modifier.testTag(METAS_COMPLETION_PROBLEM_TAG)
                        )
                    }
                }
                if (state.stale) {
                    item {
                        StatusSlip(
                            kind = StatusKind.Older,
                            message = stringResource(R.string.metas_stale),
                            modifier = Modifier.testTag("metas.stale"),
                            action = StatusAction(StatusSlipDefaults.retryLabel) {
                                onAction(MetasAction.Retry)
                            }
                        )
                    }
                }
                if (state.empty) {
                    item { EmptyMetas() }
                } else {
                    state.band?.let { band -> item(key = METAS_TOTAL_TAG) { MetasTotal(band) } }
                }
                items(state.goals, key = { it.id }) { goal ->
                    Column {
                        GoalCard(
                            name = goal.name,
                            icon = goalIcon(goal.iconKey),
                            percentage = stringResource(R.string.goal_percent, goal.percentage),
                            saved = goal.saved,
                            target = stringResource(R.string.goal_target_of, goal.target.digits),
                            date = goal.targetMonth?.let(dates::monthYear)
                                ?: stringResource(R.string.goal_no_date),
                            progress = barFill(goal.percentage),
                            places = goal.places.map { milestone(it) },
                            onOpen = { onAction(MetasAction.Open(goal.id)) },
                            modifier = Modifier.testTag("metas.goal.${goal.id}"),
                            flagged = goal.targetMonth != null
                        )
                        // At its target the card carries Marcar como lograda
                        // (docs/design/system/features/goals.html).
                        if (goal.reached) {
                            SelloButton(
                                stringResource(R.string.goal_mark_complete),
                                { onAction(MetasAction.MarkComplete(goal.id)) },
                                Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.sm)
                                    .testTag("metas.markComplete.${goal.id}"),
                                icon = SelloIcon.Check,
                                // Never pressable while an unknown outcome waits for
                                // its check: the completer would send nothing.
                                enabled = state.completion == CompletionState.Idle ||
                                    state.completion ==
                                    CompletionState.Failed(GoalDetailError.NotSaved),
                                loading = state.completion == CompletionState.Busy,
                                tone = ButtonTone.Gain
                            )
                        }
                    }
                }
                if (onBulk != null && state.goals.isNotEmpty()) {
                    item(key = METAS_BULK_TAG) {
                        Row {
                            SelloButton(
                                stringResource(R.string.metas_record_several),
                                onBulk,
                                Modifier.testTag(METAS_BULK_TAG),
                                kind = ButtonKind.Text,
                                icon = SelloIcon.ReceiptLong
                            )
                        }
                    }
                }
                items(state.completed, key = { it.id }) { goal ->
                    val land = remember(goal.id) { goal.id !in landed }
                    LaunchedEffect(goal.id) { if (goal.id !in landed) landed += goal.id }
                    CompletedGoalRow(
                        name = goal.name,
                        detail = stringResource(
                            R.string.goal_completed_detail,
                            goal.saved.digits,
                            dates.shortDay(goal.completedOn, state.today)
                        ),
                        icon = goalIcon(goal.iconKey),
                        stamp = stringResource(R.string.goal_completed_stamp),
                        onOpen = { onAction(MetasAction.Open(goal.id)) },
                        modifier = Modifier.testTag("metas.completed.${goal.id}"),
                        land = land
                    )
                }
            }
        }
    }
}

/** The empty state: the invitation, then four empty stamp places under it, no button. */
@Composable
private fun EmptyMetas() {
    val places = GoalPlace.entries.map { place ->
        MilestoneSpec(
            placeLabel(place),
            stringResource(R.string.goal_place_empty, placeSpoken(place)),
            MilestoneState.Empty
        )
    }
    Column(
        Modifier.fillMaxWidth().testTag("metas.empty"),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusSlip(
            kind = StatusKind.Empty(SelloIcon.Flag, stringResource(R.string.metas_empty_title)),
            message = stringResource(R.string.metas_empty_body)
        )
        MilestoneRow(places)
    }
}

/**
 * The total as the tab's hero card, as Inversiones' and Recibo's: "Guardado en metas",
 * what the active goals hold as the big number, and under it what came in this month,
 * when anything did.
 */
@Composable
private fun MetasTotal(band: MetasBand) {
    val months = monthNames()
    val contributed = band.contributed?.let {
        stringResource(R.string.metas_band_contributed, it.digits, months.inSentence(band.month))
    }
    AnswerSlip(
        label = stringResource(R.string.metas_band_label),
        amount = band.saved,
        stamp = null,
        ink = StampInk.Good,
        modifier = Modifier.testTag(METAS_TOTAL_TAG),
        description = listOfNotNull(
            stringResource(R.string.metas_band_label),
            band.saved.spoken,
            contributed
        ).joinToString(". ")
    ) {
        if (contributed != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Icon(
                    SelloIcon.Add.painter(),
                    contentDescription = null,
                    tint = SelloTheme.colors.semantic.gain,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    contributed,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = SelloTheme.type.weight.strong,
                    color = SelloTheme.colors.semantic.gain
                )
            }
        }
    }
}

const val METAS_LIST_TAG = "metas.list"
const val METAS_TOTAL_TAG = "metas.total"
const val METAS_CREATE_TAG = "metas.create"
const val METAS_BULK_TAG = "metas.bulk"
const val METAS_COMPLETION_PROBLEM_TAG = "metas.completion.problem"
