package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.stored
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toNewEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.SchedulePolicy
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedSchedule
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteSchedule
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ResumeSchedule
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleEdit
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.StoredSchedule
import java.time.Instant
import java.time.LocalDate

/**
 * Schedules, by the steps of [RoomExpenseCommands]: check the generation, answer a
 * replay, validate, then write the rows, the revision and the receipt together. Every
 * write raises the schedule's version. No command records an occurrence; one that
 * would change what catch-up has still to record waits for it (`CatchUpPending`).
 */
class RoomScheduleCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource
) : ScheduleCommands {
    override suspend fun submit(command: ScheduleCommand): ScheduleCommandOutcome =
        when (val run = database.runCommand { decide(command) }) {
            is CommandRun.Finished -> run.value
            is CommandRun.RolledBack -> failed(run.failure)
            CommandRun.Unknown -> OutcomeUnknown(command.operationId)
        }

    private fun failed(failure: StorageFailure) = Rejected(CommandRejection.StorageFailed(failure))

    private fun StoredReceipt.committed() =
        CommittedSchedule(receipt, checkNotNull(scheduleId) { "Not a schedule receipt" })

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: ScheduleCommand): ScheduleCommandOutcome {
        val digest = inputDigest(command)
        val profile = when (
            val start =
                database.startCommand(command.operationId, command.generation, command.kind, digest)
        ) {
            is CommandStart.Proceed -> start.profile

            is CommandStart.Stale -> return Rejected(
                CommandRejection.StaleGeneration(start.current)
            )

            is CommandStart.Replay -> return Committed(start.original.committed(), replayed = true)

            CommandStart.Conflict -> return Rejected(CommandRejection.OperationConflict)

            is CommandStart.Damaged -> return failed(start.failure)
        }
        val today = clock.today.value.date
        val now = audit.now()
        val written = when (command) {
            is CreateSchedule -> create(command, today, now)

            is ScheduleEdit -> when (val loaded = load(command)) {
                is Load.Refused -> return loaded.outcome

                is Load.Found -> when (command) {
                    is ChangeSchedule -> change(command, loaded.schedule, today, now)
                    is PauseSchedule -> pause(loaded.schedule, today, now)
                    is ResumeSchedule -> resume(loaded.schedule, today, now)
                    is DeleteSchedule -> delete(loaded.schedule, today)
                }
            }
        }
        val scheduleId = when (written) {
            is Written.Done -> written.scheduleId
            is Written.Refused -> return written.outcome
        }
        val receipt = database.commitCommand(
            profile,
            command.operationId,
            command.kind,
            digest,
            scheduleId,
            now
        )
        return Committed(receipt.committed(), replayed = false)
    }

    private sealed interface Written {
        data class Done(val scheduleId: String) : Written

        data class Refused(val outcome: ScheduleCommandOutcome) : Written
    }

    private fun refused(reason: ScheduleRejection) = Written.Refused(Rejected(Specific(reason)))

    /** A schedule as stored, read inside the transaction. */
    private class Loaded(val row: ScheduleEntity, val stored: StoredSchedule) {
        val schedule: Schedule get() = stored.schedule
        val recurrence: Recurrence get() = stored.schedule.recurrence
        val openPause: SchedulePause? get() = recurrence.pauses.lastOrNull()?.takeIf {
            it.until ==
                null
        }

        /** Whether catch-up still has an occurrence up to [today] to record. */
        fun pending(today: LocalDate) =
            SchedulePolicy.due(recurrence, stored.handled, today, limit = 1).isNotEmpty()
    }

    private sealed interface Load {
        data class Found(val schedule: Loaded) : Load

        data class Refused(val outcome: ScheduleCommandOutcome) : Load
    }

    private suspend fun load(command: ScheduleEdit): Load {
        val row = database.scheduleDao().byId(command.scheduleId.value)
            ?: return Load.Refused(Rejected(Specific(ScheduleRejection.ScheduleMissing)))
        if (row.version != command.expectedVersion) {
            return Load.Refused(Rejected(CommandRejection.VersionConflict(row.version)))
        }
        return when (val stored = row.toStored(database.scheduleDao().pauses(row.id))) {
            is Outcome.Success -> Load.Found(Loaded(row, stored.value))
            is Outcome.Failure -> Load.Refused(failed(stored.error))
        }
    }

    /** Null when the category exists and takes new entries; otherwise why not. */
    private suspend fun categoryRejection(categoryId: CategoryId): ScheduleRejection? {
        val category = database.categoryDao().byId(categoryId.value)
            ?: return ScheduleRejection.CategoryMissing
        return if (category.archived != 0L) ScheduleRejection.CategoryArchived else null
    }

    private suspend fun create(command: CreateSchedule, today: LocalDate, now: Instant): Written {
        if (command.start.isBefore(today)) {
            return refused(ScheduleRejection.StartTooEarly(today))
        }
        val recurrence = when (
            val built = Recurrence.of(
                command.rule,
                command.start,
                command.end
            )
        ) {
            is Outcome.Success -> built.value
            is Outcome.Failure -> return refused(ScheduleRejection.InvalidRecurrence(built.error))
        }
        (command.entry as? ScheduledEntry.Payment)?.let { payment ->
            categoryRejection(payment.categoryId)?.let { return refused(it) }
        }
        val id = when (val made = ScheduleId.of(ids.next())) {
            is Outcome.Success -> made.value
            is Outcome.Failure -> error("The identifier source made a malformed identifier")
        }
        val sequence = database.scheduleDao().highestSequence()
            ?.let { nextCounter(it, ScheduleEntity.TABLE, null, "sequence") } ?: 0L
        database.scheduleDao().insert(
            Schedule(id, command.entry, recurrence).toNewEntity(sequence, now)
        )
        return Written.Done(id.value)
    }

    private suspend fun change(
        command: ChangeSchedule,
        loaded: Loaded,
        today: LocalDate,
        now: Instant
    ): Written {
        val current = loaded.schedule
        if (command.entry::class != current.entry::class) {
            return refused(ScheduleRejection.KindChanged)
        }
        val ruleChanged = command.rule != current.recurrence.rule ||
            command.start != current.recurrence.start
        val same = !ruleChanged && command.entry == current.entry &&
            command.end == current.recurrence.end
        if (same) return refused(ScheduleRejection.Unchanged)
        if (loaded.pending(today)) return refused(ScheduleRejection.CatchUpPending)
        if (ruleChanged && !command.start.isAfter(today)) {
            return refused(ScheduleRejection.StartTooEarly(today.plusDays(1)))
        }
        if (command.end != current.recurrence.end && command.end?.isBefore(today) == true) {
            return refused(ScheduleRejection.EndTooEarly(today))
        }
        val payment = command.entry as? ScheduledEntry.Payment
        if (payment != null &&
            payment.categoryId != (current.entry as ScheduledEntry.Payment).categoryId
        ) {
            categoryRejection(payment.categoryId)?.let { return refused(it) }
        }
        // Pauses before a new start no longer matter; one across it starts with it.
        val pauses = if (command.start == current.recurrence.start) {
            current.recurrence.pauses
        } else {
            current.recurrence.pauses
                .filter { it.until?.isAfter(command.start) ?: true }
                .map { if (it.from.isBefore(command.start)) it.copy(from = command.start) else it }
        }
        val recurrence =
            when (val built = Recurrence.of(command.rule, command.start, command.end, pauses)) {
                is Outcome.Success -> built.value

                is Outcome.Failure -> return refused(
                    ScheduleRejection.InvalidRecurrence(built.error)
                )
            }
        val entry = command.entry.stored
        val rule = recurrence.rule.stored
        val updated = database.scheduleDao().update(
            id = loaded.row.id,
            expectedVersion = loaded.row.version,
            categoryId = entry.categoryId,
            sourceKey = entry.sourceKey,
            sourceName = entry.sourceName,
            amountMinor = entry.amountMinor,
            currency = entry.currency,
            note = entry.note,
            rule = rule.rule,
            ruleFirst = rule.first,
            ruleSecond = rule.second,
            startDate = EffectiveDates.storedText(recurrence.start),
            endDate = recurrence.end?.let(EffectiveDates::storedText),
            newVersion = nextCounter(
                loaded.row.version,
                ScheduleEntity.TABLE,
                loaded.row.id,
                "version"
            ),
            updatedAt = now.toEpochMilli()
        )
        check(updated == 1) { "The schedule changed inside the transaction that read it" }
        if (pauses != current.recurrence.pauses) {
            database.scheduleDao().removePauses(loaded.row.id)
            pauses.forEach { database.scheduleDao().insertPause(it.toEntity(current.id)) }
        }
        return Written.Done(loaded.row.id)
    }

    /** From today, or from the start when that is later; nothing recorded is removed. */
    private suspend fun pause(loaded: Loaded, today: LocalDate, now: Instant): Written {
        if (loaded.openPause != null) return refused(ScheduleRejection.AlreadyPaused)
        if (loaded.pending(today)) return refused(ScheduleRejection.CatchUpPending)
        val from = maxOf(today, loaded.recurrence.start)
        database.scheduleDao().insertPause(SchedulePause(from, null).toEntity(loaded.schedule.id))
        touch(loaded, now)
        return Written.Done(loaded.row.id)
    }

    /**
     * Today counts again. A pause that never took effect leaves nothing behind. A payment
     * stays paused while its category is archived (rule 9), or catch-up would record into it.
     */
    private suspend fun resume(loaded: Loaded, today: LocalDate, now: Instant): Written {
        val open = loaded.openPause ?: return refused(ScheduleRejection.NotPaused)
        (loaded.schedule.entry as? ScheduledEntry.Payment)?.let { payment ->
            categoryRejection(payment.categoryId)?.let { return refused(it) }
        }
        val id = loaded.row.id
        val from = EffectiveDates.storedText(open.from)
        val changed = if (today.isAfter(open.from)) {
            database.scheduleDao().endPause(id, from, EffectiveDates.storedText(today))
        } else {
            database.scheduleDao().removePause(id, from)
        }
        check(changed == 1) { "The pause changed inside the transaction that read it" }
        touch(loaded, now)
        return Written.Done(id)
    }

    /** The schedule and its pauses go; every entry it recorded stays (rule 10). */
    private suspend fun delete(loaded: Loaded, today: LocalDate): Written {
        if (loaded.pending(today)) return refused(ScheduleRejection.CatchUpPending)
        database.scheduleDao().removePauses(loaded.row.id)
        val removed = database.scheduleDao().delete(loaded.row.id, loaded.row.version)
        check(removed == 1) { "The schedule changed inside the transaction that read it" }
        return Written.Done(loaded.row.id)
    }

    private suspend fun touch(loaded: Loaded, now: Instant) {
        val row = loaded.row
        val touched = database.scheduleDao().touch(
            row.id,
            row.version,
            nextCounter(row.version, ScheduleEntity.TABLE, row.id, "version"),
            now.toEpochMilli()
        )
        check(touched == 1) { "The schedule changed inside the transaction that read it" }
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedSchedule?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)

            is Outcome.Failure -> receipt

            is Outcome.Success -> Outcome.Success(
                receipt.value.scheduleId?.let {
                    receipt.value.committed()
                }
            )
        }
    }
}
