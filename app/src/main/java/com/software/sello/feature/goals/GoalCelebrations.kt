package com.software.sello.feature.goals

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.software.sello.R
import com.software.sello.designsystem.component.CelebrationOverlay
import com.software.sello.designsystem.component.StampTicket
import com.software.sello.domain.model.GoalPlace
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.date.dateLabels

/**
 * Marcar como lograda's big moment, opened only after a confirmed completion. [onView] is
 * Ver mi meta; both buttons close it.
 */
@Composable
fun LogradaMoment(completion: CompletionState, onDone: () -> Unit, onView: () -> Unit = onDone) {
    val plate = (completion as? CompletionState.Celebrating)?.plate ?: return
    CelebrationOverlay(
        title = stringResource(R.string.goal_lograda_title),
        plateTitle = stringResource(R.string.goal_lograda_plate),
        name = plate.name,
        icon = goalIcon(plate.iconKey),
        amount = plate.saved,
        detail = pluralStringResource(
            R.plurals.goal_lograda_detail,
            plate.contributions,
            dateLabels().dayMonthYear(plate.completedOn),
            plate.contributions
        ),
        places = GoalPlace.entries.map { milestone(PlaceModel(it, PlaceLook.Earned, null)) },
        doneLabel = stringResource(R.string.goal_lograda_done),
        viewLabel = stringResource(R.string.goal_lograda_view),
        onDone = onDone,
        onView = onView
    )
}

/**
 * What a confirmed save did to the stamps, in one line: "Ganaste el sello del 50 %",
 * "De vuelta en el 50 %" or "Llegaste al 100 %". Null when there is nothing to say.
 */
@Composable
fun celebrationLine(celebration: GoalCelebration): String? {
    val earned = celebration.earned.filter { it != GoalPlace.Meta }
    return when {
        earned.size == 1 -> stringResource(
            R.string.goal_celebration_earned,
            placeLabel(earned.single())
        )

        earned.size > 1 -> stringResource(
            R.string.goal_celebration_earned_many,
            earned.dropLast(1).map { placeLabel(it) }.joinToString(", "),
            placeLabel(earned.last())
        )

        celebration.relit.isNotEmpty() ->
            stringResource(R.string.goal_celebration_relit, placeLabel(celebration.relit.last()))

        celebration.reachedMeta -> stringResource(R.string.goal_celebration_reached)

        else -> null
    }
}

/**
 * The paper ticket a stamp lands on, from Metas where the goal's own stamps are not in
 * view: the earned, relit or waiting stamps, the line, and the goal with what is left.
 * With [land] false the stamp is already down.
 */
@Composable
fun CelebrationTicket(
    celebration: GoalCelebration,
    modifier: Modifier = Modifier,
    land: Boolean = true
) {
    val line = celebrationLine(celebration) ?: return
    val places = when {
        celebration.earned.isNotEmpty() ->
            celebration.earned.map { PlaceModel(it, PlaceLook.Earned, null) }

        celebration.relit.isNotEmpty() ->
            celebration.relit.map { PlaceModel(it, PlaceLook.Earned, null) }

        else -> listOf(PlaceModel(GoalPlace.Meta, PlaceLook.Ready, null))
    }
    val missing = celebration.missing
    StampTicket(
        places = places.map { milestone(it) },
        title = line,
        detail = if (celebration.reachedMeta && celebration.earned.isEmpty() &&
            celebration.relit.isEmpty()
        ) {
            stringResource(R.string.goal_celebration_mark_later, celebration.goalName)
        } else if (missing != null && missing.digits != "0") {
            stringResource(R.string.goal_celebration_left, celebration.goalName, missing.digits)
        } else {
            celebration.goalName
        },
        modifier = modifier.testTag(CELEBRATION_TICKET_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
        land = land
    )
}

const val CELEBRATION_TICKET_TAG = "goal.celebration.ticket"
