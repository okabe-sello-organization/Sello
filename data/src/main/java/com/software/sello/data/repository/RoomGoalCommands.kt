package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.DeletedGoalEventEntity
import com.software.sello.data.local.entity.FeaturedGoalEntity
import com.software.sello.data.local.entity.GoalEntity
import com.software.sello.data.local.entity.GoalEventEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toHistory
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalHistoryError
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalProgress
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDateError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.EditGoal
import com.software.sello.domain.port.EditGoalMovement
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalEdit
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalUndoOffer
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreGoalMovement
import com.software.sello.domain.port.UndoGoalCompletion
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration

/**
 * Goals, by the steps of [RoomExpenseCommands]: check the generation, answer a replay,
 * validate, then write the rows, the revision and the receipt together. Every write to
 * a goal's history is checked by [GoalPolicy] before it is made, so a stored history
 * always reads (rule 1), and raises the goal's version.
 *
 * A deleted movement shares [undo] with expenses and income. Deshacer after a completion
 * is offered here, in memory, for the time the command asks: it ends with the process,
 * after which the completion is final.
 */
class RoomGoalCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val undo: UndoWindow,
    private val monotonic: MonotonicClock
) : GoalCommands {
    private class CompletionOffer(
        val completionId: OperationId,
        val goalId: GoalId,
        val version: Long,
        val until: Duration
    )

    @Volatile
    private var completion: CompletionOffer? = null

    override fun movementUndoOffer(): GoalUndoOffer? =
        undo.offered(UndoWindow.Kind.GoalMovement)?.let { offer ->
            (GoalId.of(offer.subjectId) as? Outcome.Success)
                ?.let { GoalUndoOffer(offer.deletionId, it.value, offer.until) }
        }

    override fun completionUndoOffer(): GoalUndoOffer? =
        completion?.takeIf { monotonic.elapsed() < it.until }
            ?.let { GoalUndoOffer(it.completionId, it.goalId, it.until) }

    override suspend fun submit(command: GoalCommand): GoalCommandOutcome = when (command) {
        is DeleteGoalMovement -> undo.changing {
            val outcome = run(command.operationId) { decide(command) }
            // A replay is the original deletion, so it never starts the time again.
            if (outcome is Committed && !outcome.replayed) {
                undo.opened(UndoWindow.Kind.GoalMovement, command.operationId, command.goalId.value)
            }
            outcome
        }

        is RestoreGoalMovement -> undo.changing {
            val outcome = run(command.operationId) { decide(command) }
            if (outcome is Committed && !outcome.replayed) undo.closed()
            outcome
        }

        is CompleteGoal -> {
            var completedVersion: Long? = null
            val outcome = run(command.operationId) {
                decide(command) { completedVersion = it }
            }
            if (outcome is Committed && !outcome.replayed) {
                completion = CompletionOffer(
                    command.operationId,
                    command.goalId,
                    checkNotNull(completedVersion),
                    monotonic.elapsed() + command.undoFor
                )
            }
            outcome
        }

        is UndoGoalCompletion -> {
            val outcome = run(command.operationId) { decide(command) }
            if (outcome is Committed && !outcome.replayed) completion = null
            outcome
        }

        is CreateGoal -> run(command.operationId) { decide(command) }

        is EditGoal -> run(command.operationId) { decide(command) }

        is ChangeGoalTarget -> run(command.operationId) { decide(command) }

        is RecordGoalMovement -> run(command.operationId) { decide(command) }

        is EditGoalMovement -> run(command.operationId) { decide(command) }

        is DeleteGoal -> run(command.operationId) { decide(command) }

        is FeatureGoal -> run(command.operationId) { decide(command) }
    }

    private suspend fun run(
        operationId: OperationId,
        decide: suspend () -> GoalCommandOutcome
    ): GoalCommandOutcome = when (val run = database.runCommand(decide)) {
        is CommandRun.Finished -> run.value
        is CommandRun.RolledBack -> failed(run.failure)
        CommandRun.Unknown -> OutcomeUnknown(operationId)
    }

    private fun failed(failure: StorageFailure) = Rejected(CommandRejection.StorageFailed(failure))

    private fun StoredReceipt.committed(change: GoalChange?) =
        CommittedGoal(receipt, checkNotNull(goalId) { "Not a goal receipt" }, change)

    /** Steps 1 and 2 of every command. [proceed] runs only for an operation not seen before. */
    private suspend inline fun starting(
        command: GoalCommand,
        proceed: (FinancialProfile) -> GoalCommandOutcome
    ): GoalCommandOutcome = when (
        val start = database.startCommand(
            command.operationId,
            command.generation,
            command.kind,
            inputDigest(command)
        )
    ) {
        is CommandStart.Proceed -> proceed(start.profile)
        is CommandStart.Stale -> Rejected(CommandRejection.StaleGeneration(start.current))
        is CommandStart.Replay -> Committed(start.original.committed(null), true)
        CommandStart.Conflict -> Rejected(CommandRejection.OperationConflict)
        is CommandStart.Damaged -> failed(start.failure)
    }

    private suspend fun commit(
        profile: FinancialProfile,
        command: GoalCommand,
        goalId: String,
        at: Instant
    ): StoredReceipt = database.commitCommand(
        profile,
        command.operationId,
        command.kind,
        inputDigest(command),
        goalId,
        at
    )

    /** A goal as stored, read inside the transaction. */
    private class Loaded(val row: GoalEntity, val history: GoalHistory, val progress: GoalProgress)

    private sealed interface Load {
        data class Found(val goal: Loaded) : Load

        data class Refused(val outcome: GoalCommandOutcome) : Load
    }

    /**
     * The goal [command] is about, at the version the caller read. A completed goal is
     * refused unless [completedAllowed].
     */
    private suspend fun load(command: GoalEdit, completedAllowed: Boolean = false): Load {
        val row = database.goalDao().byId(command.goalId.value)
            ?: return Load.Refused(Rejected(Specific(GoalRejection.GoalMissing)))
        val history = when (val read = row.toHistory(database.goalDao().events(row.id))) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Load.Refused(failed(read.error))
        }
        if (history.goal.version != command.expectedVersion) {
            return Load.Refused(Rejected(CommandRejection.VersionConflict(history.goal.version)))
        }
        if (history.goal.completedOn != null && !completedAllowed) {
            return Load.Refused(Rejected(Specific(GoalRejection.Completed)))
        }
        val progress = when (val worked = GoalPolicy.progress(history)) {
            is Outcome.Success -> worked.value

            // What is stored was checked before it was written; a history that does not
            // work out was damaged afterwards.
            is Outcome.Failure -> return Load.Refused(
                failed(StorageFailure.Integrity(GoalEventEntity.TABLE, row.id, "amount_minor"))
            )
        }
        return Load.Found(Loaded(row, history, progress))
    }

    /** [history] worked out, or why it may not be written. */
    private fun checked(
        history: GoalHistory
    ): Outcome<GoalProgress, CommandRejection<GoalRejection>> =
        when (val worked = GoalPolicy.progress(history)) {
            is Outcome.Success -> worked

            is Outcome.Failure -> Outcome.Failure(
                when (val error = worked.error) {
                    is GoalHistoryError.WithdrawalAboveSaved ->
                        Specific(GoalRejection.AboveSaved(error.withdrawal, error.available))

                    GoalHistoryError.Unrepresentable -> Specific(GoalRejection.Unrepresentable)

                    GoalHistoryError.NoTarget -> CommandRejection.StorageFailed(
                        StorageFailure.Integrity(
                            GoalEventEntity.TABLE,
                            history.goal.id.value,
                            "kind"
                        )
                    )
                }
            )
        }

    /** Raises the goal's version and writes [changed] over its row. */
    private suspend fun write(row: GoalEntity, now: Instant, changed: GoalEntity = row) {
        val newVersion = nextCounter(row.version, GoalEntity.TABLE, row.id, "version")
        val updated = database.goalDao().update(
            id = row.id,
            expectedVersion = row.version,
            name = changed.name,
            iconKey = changed.iconKey,
            targetMonth = changed.targetMonth,
            completedOn = changed.completedOn,
            nextSequence = changed.nextSequence,
            newVersion = newVersion,
            updatedAt = now.toEpochMilli()
        )
        check(updated == 1) { "The goal changed inside the transaction that read it" }
    }

    private fun today(): LocalDate = clock.today.value.date

    private fun dateRejection(date: LocalDate): GoalRejection? {
        val today = today()
        return when (val checked = EffectiveDates.forManualEntry(date, today)) {
            is Outcome.Success -> null

            is Outcome.Failure -> when (checked.error) {
                is EffectiveDateError.InFuture -> GoalRejection.FutureDate(today)

                EffectiveDateError.OutOfRange, is EffectiveDateError.Malformed ->
                    GoalRejection.InvalidDate
            }
        }
    }

    private fun newId(): String {
        val id = ids.next()
        check(GoalMovementId.of(id) is Outcome.Success) {
            "The identifier source returned a malformed identifier"
        }
        return id
    }

    private fun GoalHistory.with(event: GoalEvent) = copy(events = events + event)

    private fun GoalHistory.replacing(movement: GoalMovement) =
        copy(events = events.map { if ((it as? GoalMovement)?.id == movement.id) movement else it })

    private fun GoalHistory.without(movement: GoalMovementId) =
        copy(events = events.filterNot { (it as? GoalMovement)?.id == movement })

    // --- The decisions. Each runs inside the transaction; every rejection returns before the first write. ---

    private suspend fun decide(command: CreateGoal): GoalCommandOutcome =
        starting(command) { profile ->
            command.targetMonth?.let {
                if (!EffectiveDates.isStorable(
                        it
                    )
                ) {
                    return Rejected(Specific(GoalRejection.InvalidDate))
                }
            }
            val now = audit.now()
            val goalId = when (val id = GoalId.of(ids.next())) {
                is Outcome.Success -> id.value
                is Outcome.Failure -> error("The identifier source returned a malformed identifier")
            }
            val goal =
                Goal(goalId, command.name, command.icon, command.targetMonth, null, 1, now, now)
            val receipt = commit(profile, command, goalId.value, now)
            database.goalDao().insert(goal.toEntity(nextSequence = 1))
            database.goalDao().insertEvent(
                GoalTargetChange(command.target, 0, today()).toEntity(newId(), goalId, now)
            )
            Committed(receipt.committed(null), replayed = false)
        }

    private suspend fun decide(command: EditGoal): GoalCommandOutcome =
        starting(command) { profile ->
            val goal = when (val load = load(command)) {
                is Load.Found -> load.goal
                is Load.Refused -> return load.outcome
            }
            command.targetMonth?.let {
                if (!EffectiveDates.isStorable(
                        it
                    )
                ) {
                    return Rejected(Specific(GoalRejection.InvalidDate))
                }
            }
            val current = goal.history.goal
            val same = current.name == command.name && current.icon == command.icon &&
                current.targetMonth == command.targetMonth
            if (same) return Rejected(Specific(GoalRejection.Unchanged))
            val now = audit.now()
            write(
                goal.row,
                now,
                goal.row.copy(
                    name = command.name.value,
                    iconKey = command.icon.value,
                    targetMonth = command.targetMonth?.let(EffectiveDates::storedText)
                )
            )
            Committed(commit(profile, command, goal.row.id, now).committed(null), replayed = false)
        }

    private suspend fun decide(command: ChangeGoalTarget): GoalCommandOutcome =
        starting(command) { profile ->
            val goal = when (val load = load(command)) {
                is Load.Found -> load.goal
                is Load.Refused -> return load.outcome
            }
            if (goal.progress.target ==
                command.target
            ) {
                return Rejected(Specific(GoalRejection.Unchanged))
            }
            val change = GoalTargetChange(command.target, goal.row.nextSequence, today())
            val after = when (val worked = checked(goal.history.with(change))) {
                is Outcome.Success -> worked.value
                is Outcome.Failure -> return Rejected(worked.error)
            }
            val now = audit.now()
            database.goalDao().insertEvent(
                change.toEntity(newId(), goal.history.goal.id, now)
            )
            write(goal.row, now, goal.row.copy(nextSequence = goal.row.nextSequence + 1))
            val receipt = commit(profile, command, goal.row.id, now)
            Committed(receipt.committed(GoalPolicy.change(goal.progress, after)), replayed = false)
        }

    private suspend fun decide(command: RecordGoalMovement): GoalCommandOutcome =
        starting(command) { profile ->
            val goal = when (val load = load(command)) {
                is Load.Found -> load.goal
                is Load.Refused -> return load.outcome
            }
            dateRejection(command.date)?.let { return Rejected(Specific(it)) }
            val now = audit.now()
            val movement = GoalMovement(
                id = when (val id = GoalMovementId.of(newId())) {
                    is Outcome.Success -> id.value
                    is Outcome.Failure -> error("unreachable")
                },
                kind = command.kind,
                amount = command.amount,
                date = command.date,
                sequence = goal.row.nextSequence,
                recordedOn = today(),
                note = command.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
            val after = when (val worked = checked(goal.history.with(movement))) {
                is Outcome.Success -> worked.value
                is Outcome.Failure -> return Rejected(worked.error)
            }
            database.goalDao().insertEvent(movement.toEntity(goal.history.goal.id))
            write(goal.row, now, goal.row.copy(nextSequence = goal.row.nextSequence + 1))
            val receipt = commit(profile, command, goal.row.id, now)
            Committed(receipt.committed(GoalPolicy.change(goal.progress, after)), replayed = false)
        }

    /** The movement [id] of [goal], as stored, or why it cannot be changed. */
    private suspend fun movement(
        goal: Loaded,
        id: GoalMovementId,
        expectedVersion: Long
    ): Outcome<GoalMovement, CommandRejection<GoalRejection>> {
        val stored =
            goal.history.events.filterIsInstance<GoalMovement>().firstOrNull { it.id == id }
                ?: return Outcome.Failure(Specific(GoalRejection.MovementMissing))
        if (stored.version != expectedVersion) {
            return Outcome.Failure(CommandRejection.VersionConflict(stored.version))
        }
        return Outcome.Success(stored)
    }

    private suspend fun decide(command: EditGoalMovement): GoalCommandOutcome =
        starting(command) { profile ->
            val goal = when (val load = load(command)) {
                is Load.Found -> load.goal
                is Load.Refused -> return load.outcome
            }
            val current = when (
                val found = movement(
                    goal,
                    command.movementId,
                    command.movementVersion
                )
            ) {
                is Outcome.Success -> found.value
                is Outcome.Failure -> return Rejected(found.error)
            }
            val same = current.amount == command.amount && current.date == command.date &&
                current.note == command.note
            if (same) return Rejected(Specific(GoalRejection.Unchanged))
            if (command.date !=
                current.date
            ) {
                dateRejection(command.date)?.let { return Rejected(Specific(it)) }
            }
            val corrected = current.copy(
                amount = command.amount,
                date = command.date,
                note = command.note
            )
            when (val worked = checked(goal.history.replacing(corrected))) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return Rejected(worked.error)
            }
            val now = audit.now()
            val updated = database.goalDao().updateEvent(
                id = current.id.value,
                expectedVersion = current.version,
                amountMinor = command.amount.money.minorUnits,
                currency = command.amount.money.currency.code,
                effectiveDate = EffectiveDates.storedText(command.date),
                note = command.note?.value,
                newVersion = nextCounter(
                    current.version,
                    GoalEventEntity.TABLE,
                    current.id.value,
                    "version"
                ),
                updatedAt = now.toEpochMilli()
            )
            check(updated == 1) { "The movement changed inside the transaction that read it" }
            write(goal.row, now)
            Committed(commit(profile, command, goal.row.id, now).committed(null), replayed = false)
        }

    /**
     * The row is moved, column for column, into the copy kept for undo; any copy an
     * earlier deletion of any kind left is removed, which makes that deletion final.
     */
    private suspend fun decide(command: DeleteGoalMovement): GoalCommandOutcome =
        starting(command) { profile ->
            val goal = when (val load = load(command)) {
                is Load.Found -> load.goal
                is Load.Refused -> return load.outcome
            }
            val current = when (
                val found = movement(
                    goal,
                    command.movementId,
                    command.movementVersion
                )
            ) {
                is Outcome.Success -> found.value
                is Outcome.Failure -> return Rejected(found.error)
            }
            when (val worked = checked(goal.history.without(current.id))) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return Rejected(worked.error)
            }
            val row = checkNotNull(database.goalDao().event(current.id.value))
            val now = audit.now()
            database.discardKeptCopies()
            database.deletedGoalEventDao().insert(
                DeletedGoalEventEntity(
                    deletionId = command.operationId.value,
                    generation = profile.generation,
                    deletedAt = now.toEpochMilli(),
                    eventId = row.id,
                    goalId = row.goalId,
                    sequence = row.sequence,
                    kind = row.kind,
                    amountMinor = row.amountMinor,
                    currency = row.currency,
                    effectiveDate = row.effectiveDate,
                    recordedOn = row.recordedOn,
                    note = row.note,
                    version = row.version,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt
                )
            )
            val removed = database.goalDao().deleteEvent(row.id, row.version)
            check(removed == 1) { "The movement changed inside the transaction that read it" }
            write(goal.row, now)
            Committed(commit(profile, command, goal.row.id, now).committed(null), replayed = false)
        }

    /**
     * A replay is answered first, whatever the time is. Otherwise the deletion must be
     * the one on offer and still in time, belong to this history and to an active goal
     * that still has its place free, and the history with it back must still work out.
     */
    private suspend fun decide(command: RestoreGoalMovement): GoalCommandOutcome =
        starting(command) { profile ->
            val inTime =
                undo.offered(UndoWindow.Kind.GoalMovement)?.deletionId == command.deletionId
            if (!inTime) return Rejected(Specific(GoalRejection.UndoExpired))
            val kept = database.deletedGoalEventDao().find(command.deletionId.value)
                ?: return Rejected(Specific(GoalRejection.UndoExpired))
            val row = database.goalDao().byId(kept.goalId)
            val taken = kept.generation != profile.generation || row == null ||
                row.completedOn != null ||
                database.goalDao().event(kept.eventId) != null ||
                database.goalDao().countAt(kept.goalId, kept.sequence) != 0
            if (taken || row == null) return Rejected(Specific(GoalRejection.UndoConflict))
            val event = GoalEventEntity(
                id = kept.eventId,
                goalId = kept.goalId,
                sequence = kept.sequence,
                kind = kept.kind,
                amountMinor = kept.amountMinor,
                currency = kept.currency,
                effectiveDate = kept.effectiveDate,
                recordedOn = kept.recordedOn,
                note = kept.note,
                version = kept.version,
                createdAt = kept.createdAt,
                updatedAt = kept.updatedAt
            )
            val history = when (
                val read = row.toHistory(
                    database.goalDao().events(row.id) + event
                )
            ) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return failed(read.error)
            }
            if (checked(
                    history
                ) is Outcome.Failure
            ) {
                return Rejected(Specific(GoalRejection.UndoConflict))
            }
            database.goalDao().insertEvent(event)
            val removed = database.deletedGoalEventDao().remove(kept.deletionId)
            check(removed == 1) { "The kept copy changed inside the transaction that read it" }
            val now = audit.now()
            write(row, now)
            Committed(commit(profile, command, row.id, now).committed(null), replayed = false)
        }

    private suspend fun decide(
        command: CompleteGoal,
        completedAt: (Long) -> Unit
    ): GoalCommandOutcome = starting(command) { profile ->
        val goal = when (val load = load(command)) {
            is Load.Found -> load.goal
            is Load.Refused -> return load.outcome
        }
        val today = today()
        val completed = goal.history.copy(goal = goal.history.goal.copy(completedOn = today))
        val after = when (val worked = checked(completed)) {
            is Outcome.Success -> worked.value
            is Outcome.Failure -> return Rejected(worked.error)
        }
        val now = audit.now()
        write(goal.row, now, goal.row.copy(completedOn = EffectiveDates.storedText(today)))
        completedAt(goal.row.version + 1)
        val receipt = commit(profile, command, goal.row.id, now)
        Committed(receipt.committed(GoalPolicy.change(goal.progress, after)), replayed = false)
    }

    /** Only the completion on offer, in time, and only while nothing changed the goal since. */
    private suspend fun decide(command: UndoGoalCompletion): GoalCommandOutcome =
        starting(command) { profile ->
            val offer = completion?.takeIf {
                it.completionId == command.completionId && monotonic.elapsed() < it.until
            } ?: return Rejected(Specific(GoalRejection.UndoExpired))
            val row = database.goalDao().byId(offer.goalId.value)
                ?: return Rejected(Specific(GoalRejection.UndoConflict))
            if (row.version != offer.version || row.completedOn == null) {
                return Rejected(Specific(GoalRejection.UndoConflict))
            }
            val now = audit.now()
            write(row, now, row.copy(completedOn = null))
            Committed(commit(profile, command, row.id, now).committed(null), replayed = false)
        }

    /** The goal, every movement and the choice for Recibo go; no undo. Completed goals too. */
    private suspend fun decide(command: DeleteGoal): GoalCommandOutcome =
        starting(command) { profile ->
            val goal = when (val load = load(command, completedAllowed = true)) {
                is Load.Found -> load.goal
                is Load.Refused -> return load.outcome
            }
            val now = audit.now()
            database.goalDao().unfeature(goal.row.id)
            database.goalDao().deleteEvents(goal.row.id)
            val removed = database.goalDao().delete(goal.row.id, goal.row.version)
            check(removed == 1) { "The goal changed inside the transaction that read it" }
            Committed(commit(profile, command, goal.row.id, now).committed(null), replayed = false)
        }

    private suspend fun decide(command: FeatureGoal): GoalCommandOutcome =
        starting(command) { profile ->
            val row = database.goalDao().byId(command.goalId.value)
                ?: return Rejected(Specific(GoalRejection.GoalMissing))
            if (row.completedOn != null) return Rejected(Specific(GoalRejection.Completed))
            if (database.goalDao().featured()?.goalId ==
                row.id
            ) {
                return Rejected(Specific(GoalRejection.Unchanged))
            }
            val now = audit.now()
            database.goalDao().feature(FeaturedGoalEntity(FeaturedGoalEntity.SINGLE_ID, row.id))
            Committed(commit(profile, command, row.id, now).committed(null), replayed = false)
        }

    override suspend fun find(operationId: OperationId): Outcome<CommittedGoal?, StorageFailure> =
        reading {
            when (
                val receipt = database.operationReceiptDao().find(
                    operationId.value
                )?.toStored()
            ) {
                null -> Outcome.Success(null)

                is Outcome.Failure -> receipt

                is Outcome.Success ->
                    Outcome.Success(receipt.value.goalId?.let { receipt.value.committed(null) })
            }
        }
}
