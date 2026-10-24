package com.software.sello.feature.goals

import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalProgress
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalSummary
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/**
 * Turns the goal board into what Metas and a goal's detail draw. Every figure comes
 * from [GoalPolicy]; nothing is added or compared here.
 */
class GoalsRenderModel(private val money: MoneyFormatter) {
    fun metas(board: GoalBoardState, today: LocalDate): MetasState = when (board) {
        GoalBoardState.Loading -> MetasState.Loading

        is GoalBoardState.Ready -> metas(board.board, today, stale = false)

        is GoalBoardState.Failed ->
            board.lastGood?.let { metas(it, today, stale = true) } ?: MetasState.Failed
    }

    fun detail(board: GoalBoardState, id: String, today: LocalDate): GoalDetailState =
        when (board) {
            GoalBoardState.Loading -> GoalDetailState.Loading

            is GoalBoardState.Ready -> detail(board.board, id, today, stale = false)

            is GoalBoardState.Failed -> board.lastGood?.let { detail(it, id, today, stale = true) }
                ?: GoalDetailState.Failed
        }

    private fun metas(board: GoalBoard, today: LocalDate, stale: Boolean): MetasState {
        if (board.goals.isEmpty()) {
            return MetasState.Ready(null, emptyList(), emptyList(), today, stale)
        }
        val month = YearMonth.from(today)
        val overview = when (
            val worked = GoalPolicy.overview(board.goals.map { it.history to it.progress }, month)
        ) {
            is Outcome.Success -> worked.value
            is Outcome.Failure -> return MetasState.Failed
        }
        val ordered = GoalPolicy.ordered(board.goals, { it.history.goal }, { it.progress })
        val (active, completed) = ordered.partition { it.history.goal.completedOn == null }
        return MetasState.Ready(
            band = MetasBand(
                saved = money.format(overview.saved),
                contributed = overview.contributed.takeIf { it.minorUnits > 0 }
                    ?.let { money.format(it) },
                month = month
            ),
            goals = active.map(::card),
            completed = completed.map { summary ->
                val goal = summary.history.goal
                CompletedGoalModel(
                    id = goal.id.value,
                    name = goal.name.value,
                    iconKey = goal.icon.value,
                    saved = money.format(summary.progress.saved),
                    completedOn = goal.completedOn!!
                )
            },
            today = today,
            stale = stale
        )
    }

    private fun card(summary: GoalSummary): GoalCardModel {
        val goal = summary.history.goal
        val progress = summary.progress
        return GoalCardModel(
            id = goal.id.value,
            name = goal.name.value,
            iconKey = goal.icon.value,
            percentage = progress.percentage,
            saved = money.format(progress.saved),
            target = money.format(progress.target.money),
            targetMonth = goal.targetMonth,
            places = places(progress)
        )
    }

    private fun places(progress: GoalProgress) = progress.placeModels()

    private fun detail(
        board: GoalBoard,
        id: String,
        today: LocalDate,
        stale: Boolean
    ): GoalDetailState {
        val summary = board.goals.firstOrNull { it.history.goal.id.value == id }
            ?: return GoalDetailState.Missing
        val history = summary.history
        val goal = history.goal
        val progress = summary.progress
        val earnedBy = when (val worked = GoalPolicy.earnedBy(history)) {
            is Outcome.Success -> worked.value
            is Outcome.Failure -> return GoalDetailState.Failed
        }
        val months = when (val worked = GoalPolicy.monthlyContributions(history, today)) {
            is Outcome.Success -> worked.value
            is Outcome.Failure -> return GoalDetailState.Failed
        }
        val current = YearMonth.from(today)
        val missing = GoalPolicy.missing(progress)
        return GoalDetailState.Ready(
            goal = GoalDetailModel(
                id = goal.id.value,
                name = goal.name.value,
                iconKey = goal.icon.value,
                completedOn = goal.completedOn,
                percentage = progress.percentage,
                saved = money.format(progress.saved),
                target = money.format(progress.target.money),
                missing = missing.takeIf { goal.completedOn == null && it.minorUnits > 0 }
                    ?.let { money.format(it) },
                places = places(progress),
                next = progress.next?.let { next ->
                    NextPlaceModel(
                        place = next.place,
                        missing = money.format(next.missing),
                        reached = next.place == GoalPlace.Meta &&
                            progress.state(GoalPlace.Meta) == PlaceState.Ready
                    )
                },
                targetMonth = goal.targetMonth,
                since = history.events.minBy(GoalEvent::sequence).recordedOn,
                months = months.map {
                    MonthAportes(
                        month = it.month,
                        amount = money.format(it.amount),
                        value = it.amount.minorUnits,
                        current = it.month == current
                    )
                },
                movements = history.events.filterIsInstance<GoalMovement>()
                    .sortedWith(
                        compareByDescending<GoalMovement> { it.date }
                            .thenByDescending { it.sequence }
                    )
                    .map { movement ->
                        MovementModel(
                            id = movement.id.value,
                            kind = movement.kind,
                            amount = money.format(signed(movement), signed = true),
                            date = movement.date,
                            note = movement.note?.value,
                            stamps = GoalPlace.entries.filter {
                                earnedBy[it] == movement.sequence
                            }
                        )
                    },
                featured = board.featured == goal.id,
                generation = board.generation,
                version = goal.version
            ),
            today = today,
            stale = stale
        )
    }

    /** A withdrawal is shown with its minus; its amount is never negative itself. */
    private fun signed(movement: GoalMovement): Money = when (movement.kind) {
        GoalMovementKind.Contribution -> movement.amount.money

        GoalMovementKind.Withdrawal ->
            Money(-movement.amount.money.minorUnits, movement.amount.money.currency)
    }
}

/** The four places as they are drawn, with the day each stamp was earned. */
internal fun GoalProgress.placeModels(): List<PlaceModel> = places.map { place ->
    when (val state = place.state) {
        PlaceState.Empty -> PlaceModel(place.place, PlaceLook.Empty, null)
        PlaceState.Ready -> PlaceModel(place.place, PlaceLook.Ready, null)
        is PlaceState.Earned -> PlaceModel(place.place, PlaceLook.Earned, state.on)
        is PlaceState.Kept -> PlaceModel(place.place, PlaceLook.Kept, state.on)
    }
}
