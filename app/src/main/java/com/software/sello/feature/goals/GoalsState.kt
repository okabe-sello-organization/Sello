package com.software.sello.feature.goals

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalPlace
import java.time.LocalDate
import java.time.YearMonth

/** Metas: active goals, highest completion first, then completed goals. */
sealed interface MetasState {
    data object Loading : MetasState

    /** The goals could not be read. Never shown as having no goals. */
    data object Failed : MetasState

    /**
     * With no goal at all, [band] is null and both lists are empty: the true empty state.
     * [stale] says the last read failed and these are the goals as last read.
     */
    @Immutable
    data class Ready(
        val band: MetasBand?,
        val goals: List<GoalCardModel>,
        val completed: List<CompletedGoalModel>,
        /** The financial day it was read on, for writing dates. */
        val today: LocalDate,
        val stale: Boolean = false,
        /** Marcar como lograda in progress, or its big moment on screen. */
        val completion: CompletionState = CompletionState.Idle
    ) : MetasState {
        val empty: Boolean get() = goals.isEmpty() && completed.isEmpty()
    }
}

/**
 * "Guardado en metas": what the active goals hold, and the aportes dated in [month],
 * null when there were none.
 */
@Immutable
data class MetasBand(
    val saved: MoneyTextValue,
    val contributed: MoneyTextValue?,
    val month: YearMonth
)

/** How a stamp place is drawn: earned and reached, kept (faded), waiting, or empty. */
enum class PlaceLook { Empty, Earned, Kept, Ready }

/** One of the four places, and the day its stamp was earned if it was. */
@Immutable
data class PlaceModel(val place: GoalPlace, val look: PlaceLook, val earnedOn: LocalDate?)

@Immutable
data class GoalCardModel(
    val id: String,
    val name: String,
    val iconKey: String,
    /** Rounded down; may pass 100. */
    val percentage: Long,
    val saved: MoneyTextValue,
    val target: MoneyTextValue,
    val targetMonth: YearMonth?,
    val places: List<PlaceModel>,
    /** At its target and waiting: the card carries Marcar como lograda. */
    val reached: Boolean = false
)

/** A completed goal's row: what it held and the day it was completed. */
@Immutable
data class CompletedGoalModel(
    val id: String,
    val name: String,
    val iconKey: String,
    val saved: MoneyTextValue,
    val completedOn: LocalDate
)

sealed interface MetasAction {
    data object Retry : MetasAction

    data class Open(val id: String) : MetasAction

    data class MarkComplete(val id: String) : MetasAction

    /** The big moment is closed. */
    data object CelebrationDone : MetasAction
}

/** One goal: progress, stamps, facts, aportes by month and its movements. */
sealed interface GoalDetailState {
    data object Loading : GoalDetailState

    data object Failed : GoalDetailState

    /** There is no goal with this identifier, as after it was deleted. */
    data object Missing : GoalDetailState

    @Immutable
    data class Ready(
        val goal: GoalDetailModel,
        val today: LocalDate,
        val stale: Boolean = false,
        /** Recibo's goal is being chosen or the goal deleted: nothing else may be sent. */
        val busy: Boolean = false,
        val confirmingDelete: Boolean = false,
        val error: GoalDetailError? = null,
        val allMovements: Boolean = false,
        /** Marcar como lograda in progress, or its big moment on screen. */
        val completion: CompletionState = CompletionState.Idle
    ) : GoalDetailState
}

@Immutable
data class GoalDetailModel(
    val id: String,
    val name: String,
    val iconKey: String,
    val completedOn: LocalDate?,
    val percentage: Long,
    val saved: MoneyTextValue,
    val target: MoneyTextValue,
    /** What is still missing to the target; null once it is reached or the goal completed. */
    val missing: MoneyTextValue?,
    val places: List<PlaceModel>,
    /** The first place not yet earned and what is missing to it; null once completed. */
    val next: NextPlaceModel?,
    val targetMonth: YearMonth?,
    /** The financial day the goal was created. */
    val since: LocalDate,
    val months: List<MonthAportes>,
    /** Newest first. */
    val movements: List<MovementModel>,
    /** Whether it is the goal Recibo shows, chosen or by the fallback rule. */
    val featured: Boolean,
    /** The history and the goal as read, for a command to name what it was prepared against. */
    val generation: Long,
    val version: Long
) {
    val earnedCount: Int
        get() = places.count { it.look == PlaceLook.Earned || it.look == PlaceLook.Kept }

    val kept: List<PlaceModel> get() = places.filter { it.look == PlaceLook.Kept }

    /** The goal as Marcar como lograda sends it, with what its plate will say. */
    fun completionTarget(): CompletionTarget = CompletionTarget(
        goalId = id,
        version = version,
        generation = generation,
        name = name,
        iconKey = iconKey,
        saved = saved,
        contributions = movements.count { it.kind == GoalMovementKind.Contribution }
    )
}

/** [missing] is zero when the place is the Meta, reached and waiting to be marked. */
@Immutable
data class NextPlaceModel(val place: GoalPlace, val missing: MoneyTextValue, val reached: Boolean)

/** The aportes dated in [month]; [value] is for the column's height only. */
@Immutable
data class MonthAportes(
    val month: YearMonth,
    val amount: MoneyTextValue,
    val value: Long,
    val current: Boolean
)

/** A contribution or withdrawal; [stamps] are the places it earned, in order. */
@Immutable
data class MovementModel(
    val id: String,
    val kind: GoalMovementKind,
    val amount: MoneyTextValue,
    val date: LocalDate,
    val note: String?,
    val stamps: List<GoalPlace>
)

enum class GoalDetailError {
    /** Certainly not saved; the goal is read again. */
    NotSaved,

    /** Not known whether it was saved: asked about again before anything else is sent. */
    Unknown
}

sealed interface GoalDetailAction {
    data object Retry : GoalDetailAction

    data object Feature : GoalDetailAction

    data object AskToDelete : GoalDetailAction

    data object CancelDelete : GoalDetailAction

    data object Delete : GoalDetailAction

    data object ShowAllMovements : GoalDetailAction

    data object MarkComplete : GoalDetailAction

    /** The big moment is closed. */
    data object CelebrationDone : GoalDetailAction
}
