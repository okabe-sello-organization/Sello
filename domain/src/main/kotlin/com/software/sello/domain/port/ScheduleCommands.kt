package com.software.sello.domain.port

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RecurrenceError
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import java.time.LocalDate

/**
 * A change to recurring payments and incomes (docs/testing/recurrence-examples.md).
 * Every command names its attempt with [operationId] and the history it was prepared
 * against with [generation], as for expenses. No command records an occurrence: that
 * is catch-up's alone.
 */
sealed interface ScheduleCommand {
    val operationId: OperationId
    val generation: Long
}

/** A command about one existing schedule, as the caller read it at [expectedVersion]. */
sealed interface ScheduleEdit : ScheduleCommand {
    val scheduleId: ScheduleId
    val expectedVersion: Long
}

/** A new schedule from [start], today or later, to [end] if any (rules 1–3). */
data class CreateSchedule(
    override val operationId: OperationId,
    override val generation: Long,
    val entry: ScheduledEntry,
    val rule: RepeatRule,
    val start: LocalDate,
    val end: LocalDate?
) : ScheduleCommand

/**
 * New terms for the occurrences after today (rule 7). A new [rule] or [start] needs a
 * start after today, which is also the anchor of every N days or weeks; the entry and
 * the end alone keep the rule and its anchor. A payment stays a payment and an income
 * an income.
 */
data class ChangeSchedule(
    override val operationId: OperationId,
    override val generation: Long,
    override val scheduleId: ScheduleId,
    override val expectedVersion: Long,
    val entry: ScheduledEntry,
    val rule: RepeatRule,
    val start: LocalDate,
    val end: LocalDate?
) : ScheduleEdit

/** Nothing more from today until it is resumed (rule 8). */
data class PauseSchedule(
    override val operationId: OperationId,
    override val generation: Long,
    override val scheduleId: ScheduleId,
    override val expectedVersion: Long
) : ScheduleEdit

/** Occurrences again from today, which counts; those in the pause are skipped (rule 8). */
data class ResumeSchedule(
    override val operationId: OperationId,
    override val generation: Long,
    override val scheduleId: ScheduleId,
    override val expectedVersion: Long
) : ScheduleEdit

/** Removes the schedule; every entry it recorded stays (rule 10). */
data class DeleteSchedule(
    override val operationId: OperationId,
    override val generation: Long,
    override val scheduleId: ScheduleId,
    override val expectedVersion: Long
) : ScheduleEdit

/** Why nothing was written. Each of these is certain: nothing changed. */
sealed interface ScheduleRejection {
    data class StaleGeneration(val current: Long) : ScheduleRejection

    data object OperationConflict : ScheduleRejection

    data object ScheduleMissing : ScheduleRejection

    /** The schedule changed since the caller read it; [current] is its version now. */
    data class VersionConflict(val current: Long) : ScheduleRejection

    /** The change asks for exactly what is stored. */
    data object Unchanged : ScheduleRejection

    data object CategoryMissing : ScheduleRejection

    /** Nothing is recorded into an archived category (rule 9). */
    data object CategoryArchived : ScheduleRejection

    /** A payment cannot become an income, or the other way round. */
    data object KindChanged : ScheduleRejection

    /** A new schedule starts today or later; a new rule after today (rules 3 and 7). */
    data class StartTooEarly(val earliest: LocalDate) : ScheduleRejection

    /** An end before today would change nothing already recorded and is refused. */
    data class EndTooEarly(val earliest: LocalDate) : ScheduleRejection

    data class InvalidRecurrence(val error: RecurrenceError) : ScheduleRejection

    data object AlreadyPaused : ScheduleRejection

    data object NotPaused : ScheduleRejection

    /**
     * Catch-up has not yet recorded every occurrence due up to today; changing or
     * deleting now would record them with other terms or lose them. Catch up first.
     */
    data object CatchUpPending : ScheduleRejection

    data class StorageFailed(val failure: StorageFailure) : ScheduleRejection
}

/** A committed schedule change and its receipt. */
data class CommittedSchedule(val receipt: OperationReceipt, val scheduleId: ScheduleId)

sealed interface ScheduleCommandOutcome {
    data class Committed(val schedule: CommittedSchedule, val replayed: Boolean) :
        ScheduleCommandOutcome

    data class Rejected(val reason: ScheduleRejection) : ScheduleCommandOutcome

    /** Not known whether it was saved: ask [ScheduleCommands.find] or submit the same command. */
    data class OutcomeUnknown(val operationId: OperationId) : ScheduleCommandOutcome
}

/**
 * Changes to schedules, with the guarantees of [ExpenseCommands]: one operation writes
 * once, with its revision and receipt, in one transaction, and the same operation and
 * input again returns the original result.
 */
interface ScheduleCommands {
    suspend fun submit(command: ScheduleCommand): ScheduleCommandOutcome

    suspend fun find(operationId: OperationId): Outcome<CommittedSchedule?, StorageFailure>
}
