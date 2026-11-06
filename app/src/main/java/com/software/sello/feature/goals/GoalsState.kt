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
        val completion: CompletionState = CompletionState.Idle,
        /**
         * How many completions ended in [CompletionState.Failed] so far, so a refusal that
         * repeats the one on screen is still brought into view.
         */
        val completionFailures: Int = 0
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
        /** A star is being saved or the goal deleted: nothing else may be sent. */
        val busy: Boolean = false,
        val confirmingDelete: Boolean = false,
        val error: GoalDetailError? = null,
        val allMovements: Boolean = false,
        /** Marcar como lograda in progress, or its big moment on screen. */
        val completion: CompletionState = CompletionState.Idle,
        /** Starring this goal while five are starred: the sheet to pick the one it replaces. */
        val replacing: ReplaceSheet? = null
    ) : GoalDetailState
}

/**
 * The replace sheet: the starred goals in Recibo's order ([choices]), the one picked to
 * give its star up, kept while its save is refused or not known ([picked]).
 */
@Immutable
data class ReplaceSheet(val choices: List<StarChoice>, val picked: String?)

/** A starred goal as the replace sheet lists it. */
@Immutable
data class StarChoice(val id: String, val name: String, val iconKey: String, val percentage: Long)

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
    /** Each month's net change (aportes less retiros), for the full list's month lines. */
    val movementMonths: Map<YearMonth, MoneyTextValue>,
    /**
     * Whether the person starred it. A goal Recibo shows only to fill a place is not
     * starred.
     */
    val starred: Boolean,
    /** The other starred goals, in Recibo's order: what a sixth star would replace. */
    val starredOthers: List<StarChoice> = emptyList(),
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

/**
 * A contribution or withdrawal; [stamps] are the places it earned, in order. [balance] is
 * what the goal held once it counted, in the list's order (MT40–MT42).
 */
@Immutable
data class MovementModel(
    val id: String,
    val kind: GoalMovementKind,
    val amount: MoneyTextValue,
    val balance: MoneyTextValue,
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

    /** The star pill: stars the goal, unstars it, or with five starred opens the sheet. */
    data object ToggleStar : GoalDetailAction

    /** In the replace sheet: this starred goal gives its star to the one shown. */
    data class Replace(val id: String) : GoalDetailAction

    /** Cancelar or back on the replace sheet: nothing changes. */
    data object CancelReplace : GoalDetailAction

    data object AskToDelete : GoalDetailAction

    data object CancelDelete : GoalDetailAction

    data object Delete : GoalDetailAction

    data object ShowAllMovements : GoalDetailAction

    data object MarkComplete : GoalDetailAction

    /** The big moment is closed. */
    data object CelebrationDone : GoalDetailAction
}

/**
 * Recibo's goals (rule 12): up to five, taking turns in the pill's place, and another goal
 * waiting at 100 %. Read apart from the month, so Recibo's answer never waits on goals.
 */
sealed interface ReciboGoalState {
    /** A past month, no active goal, or the first read still on its way. */
    data object Hidden : ReciboGoalState

    /** Goals could not be read and none was read before: said, never shown as none. */
    data object Failed : ReciboGoalState

    /**
     * [pills] are the goals Recibo shows, in its order; [reached] another active goal at
     * 100 % that is not among them, waiting to be marked. Either may be empty while a
     * completion's moment is still open. [revision] is the board's: the carousel opens on
     * the first goal again whenever it changes. [stale] when the goals are the last good
     * read after a failed one: they are marked as older, with Reintentar.
     */
    data class Shown(
        val pills: List<ReciboGoalPill>,
        val reached: ReciboReachedGoal?,
        val completion: CompletionState = CompletionState.Idle,
        val revision: Long = 0,
        val stale: Boolean = false
    ) : ReciboGoalState
}

/** A goal on Recibo, filled to its rounded-down [percentage]. */
@Immutable
data class ReciboGoalPill(
    val id: String,
    val name: String,
    val iconKey: String,
    val percentage: Long,
    /** At 100 % or more and not yet lograda: the pill offers Marcar como lograda, not +. */
    val reached: Boolean = false
)

/** Another active goal at 100 %, offered to be marked lograda from Recibo. */
@Immutable
data class ReciboReachedGoal(val id: String, val name: String, val iconKey: String)

sealed interface ReciboGoalAction {
    data object Retry : ReciboGoalAction

    data class MarkComplete(val id: String) : ReciboGoalAction

    /** The big moment is closed. */
    data object CelebrationDone : ReciboGoalAction
}
