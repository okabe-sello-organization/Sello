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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.CompletedGoalRow
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.GoalCard
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StampInk
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
 * and a failed read are never "no goals".
 */
@Composable
fun MetasScreen(
    state: MetasState,
    onAction: (MetasAction) -> Unit,
    dates: DateLabels,
    padding: PaddingValues
) {
    val direction = LocalLayoutDirection.current
    val spacing = SelloTheme.spacing
    // Completed rows whose stamp already came down on this visit.
    val landed: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableListOf<String>().toMutableStateList() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(METAS_LIST_TAG),
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
                ErrorSlip(
                    message = stringResource(R.string.metas_failed_body),
                    onRetry = { onAction(MetasAction.Retry) },
                    modifier = Modifier.testTag("metas.failed"),
                    title = stringResource(R.string.metas_failed_title),
                    icon = SelloIcon.Error
                )
            }

            is MetasState.Ready -> {
                if (state.stale) {
                    item {
                        ErrorSlip(
                            stringResource(R.string.metas_stale),
                            { onAction(MetasAction.Retry) },
                            Modifier.testTag("metas.stale")
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
                        // At its target the card carries Marcar como lograda (v2 celebrations).
                        if (goal.reached) {
                            SelloButton(
                                stringResource(R.string.goal_mark_complete),
                                { onAction(MetasAction.MarkComplete(goal.id)) },
                                Modifier.fillMaxWidth().padding(top = 8.dp)
                                    .testTag("metas.markComplete.${goal.id}"),
                                icon = SelloIcon.Check,
                                enabled = state.completion == CompletionState.Idle ||
                                    state.completion is CompletionState.Failed,
                                loading = state.completion == CompletionState.Busy,
                                tone = ButtonTone.Gain
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

/** v1.1's empty state: the invitation and four empty stamp places, no button. */
@Composable
private fun EmptyMetas() {
    val places = GoalPlace.entries.map { place ->
        MilestoneSpec(
            placeLabel(place),
            stringResource(R.string.goal_place_empty, placeSpoken(place)),
            MilestoneState.Empty
        )
    }
    Slip(Modifier.fillMaxWidth().testTag("metas.empty")) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = SelloTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                SelloIcon.Flag.painter(),
                contentDescription = null,
                tint = SelloTheme.colors.semantic.gain,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(SelloTheme.spacing.sm))
            Text(
                stringResource(R.string.metas_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(SelloTheme.spacing.xs))
            Text(
                stringResource(R.string.metas_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = SelloTheme.colors.inkSoft,
                textAlign = TextAlign.Center
            )
            MilestoneRow(places)
        }
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    fontWeight = FontWeight.W700,
                    color = SelloTheme.colors.semantic.gain
                )
            }
        }
    }
}

const val METAS_LIST_TAG = "metas.list"
const val METAS_TOTAL_TAG = "metas.total"
const val METAS_CREATE_TAG = "metas.create"
