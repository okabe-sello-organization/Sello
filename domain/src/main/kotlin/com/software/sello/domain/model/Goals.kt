package com.software.sello.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * A personal goal (docs/testing/goal-examples.md). Its target lives in its history as
 * [GoalTargetChange]s, so stamps earned under an earlier target can be told apart.
 * [targetMonth] is optional; [completedOn] is the financial day it was marked achieved.
 */
data class Goal(
    val id: GoalId,
    val name: GoalName,
    val icon: IconKey,
    val targetMonth: YearMonth?,
    val completedOn: LocalDate?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class GoalMovementKind { Contribution, Withdrawal }

/**
 * One step of a goal's history. [sequence] is the order it was recorded in, shared by
 * movements and target changes and never reassigned; [recordedOn] is the financial day
 * it was recorded. Stamps follow this order, never a movement's [GoalMovement.date].
 */
sealed interface GoalEvent {
    val sequence: Long
    val recordedOn: LocalDate
}

/** The goal's target from this point of its history on. The first event is always one. */
data class GoalTargetChange(
    val target: TransactionAmount,
    override val sequence: Long,
    override val recordedOn: LocalDate
) : GoalEvent

/**
 * A contribution or withdrawal. [date] is the day it counts in and may be earlier than
 * [recordedOn]. A correction replaces it in place, keeping its [sequence].
 */
data class GoalMovement(
    val id: GoalMovementId,
    val kind: GoalMovementKind,
    val amount: TransactionAmount,
    val date: LocalDate,
    override val sequence: Long,
    override val recordedOn: LocalDate,
    val note: Note?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) : GoalEvent

/**
 * One line of a receipt of several movements: what [GoalMovement] holds before it is
 * recorded. Each line has its own [date], since such a receipt mostly catches up past months.
 */
data class GoalMovementLine(
    val kind: GoalMovementKind,
    val amount: TransactionAmount,
    val date: LocalDate,
    val note: Note?
)

/** A goal with every event of its history as it now stands, corrections applied. */
data class GoalHistory(val goal: Goal, val events: List<GoalEvent>)

/** The four stamp places. */
enum class GoalPlace(val percent: Int) { Quarter(25), Half(50), ThreeQuarters(75), Meta(100) }

sealed interface PlaceState {
    data object Empty : PlaceState

    /** Earned on [on] and reached now. */
    data class Earned(val on: LocalDate) : PlaceState

    /** Earned on [on] and no longer reached: a past achievement, shown faded. */
    data class Kept(val on: LocalDate) : PlaceState

    /** Meta only: the target is reached and waits for Marcar como lograda. */
    data object Ready : PlaceState
}

data class PlaceProgress(val place: GoalPlace, val state: PlaceState)

/** The first place not yet earned and how much is still missing to reach it. */
data class NextPlace(val place: GoalPlace, val missing: Money)

/**
 * A goal worked out from its history. [percentage] is rounded down and may pass 100;
 * [next] is absent once the goal is completed.
 */
data class GoalProgress(
    val saved: Money,
    val target: TransactionAmount,
    val percentage: Long,
    val places: List<PlaceProgress>,
    val next: NextPlace?,
    val contributions: Int,
    val completed: Boolean
) {
    fun state(place: GoalPlace): PlaceState = places.first { it.place == place }.state
}

sealed interface GoalHistoryError {
    /**
     * In recording order, [withdrawal] takes more than was saved at that point, which
     * held [available]. A new withdrawal and a correction are refused alike.
     */
    data class WithdrawalAboveSaved(val withdrawal: GoalMovementId, val available: Money) :
        GoalHistoryError

    /** A total does not fit an exact amount. Reported, never clipped. */
    data object Unrepresentable : GoalHistoryError

    /** The history does not begin with the goal's target. */
    data object NoTarget : GoalHistoryError
}

/** What a change did to the stamps: places earned for the first time and places lit again. */
data class GoalChange(
    val earned: List<GoalPlace>,
    val relit: List<GoalPlace>,
    val reachedMeta: Boolean
)

/**
 * The plan shown while creating or editing: [monthly] whole pesos, rounded up, for
 * [months] months, and the month each place not yet earned would be reached.
 */
data class GoalPace(val months: Int, val monthly: Money, val projected: Map<GoalPlace, YearMonth>)

/**
 * Metas' band: what the active goals hold now ([saved]) and the aportes dated in one
 * month ([contributed]), withdrawals not subtracted. Completed goals count in neither.
 */
data class GoalOverview(val saved: Money, val contributed: Money)

/** The aportes dated in [month], for Aportes por mes. */
data class MonthContribution(val month: YearMonth, val amount: Money)
