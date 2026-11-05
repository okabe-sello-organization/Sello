package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.SchedulePolicy
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.StoredSchedule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * One batch of catch-up in one transaction (docs/testing/recurrence-examples.md, rules
 * 4 and 6): the occurrences due across every schedule, by date, then creation order,
 * then slot; an expense or income for each, carrying its origin; each schedule's
 * checkpoint moved to the last one it recorded; one revision and one receipt.
 *
 * It needs no operation identifier from a caller to be safe to repeat: what is due is
 * worked out from the checkpoints inside the transaction, and the origin of each entry
 * is unique, so a batch that committed is never written again. Every check comes before
 * the first write, so a batch refused is a batch with nothing written. A history
 * replaced since the caller's generation is not written to at all.
 *
 * A batch is stamped after every moment the catch-up notice may be marked seen up to
 * ([notice]'s mark, an entry with an origin, a catch-up or a restore), so it is listed
 * until Listo whatever the wall clock did since (#340).
 */
class RoomScheduleCatchUp internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val ids: RecordIdSource,
    private val notice: CatchUpNoticePreferences
) : ScheduleCatchUpStore {
    override suspend fun recordDue(generation: Long, today: LocalDate, limit: Int): CatchUpBatch {
        // Most runs find nothing due; they read and never take the write lock.
        when (val anything = reading { anyDue(generation, today) }) {
            is Outcome.Failure -> return CatchUpBatch.Failed(anything.error)
            is Outcome.Success -> anything.value?.let { return it }
        }
        return recordBatch(generation, today, limit)
    }

    /**
     * The batch's own transaction, which checks [generation] again: a restore or an erase
     * can commit between the read above and it.
     */
    internal suspend fun recordBatch(generation: Long, today: LocalDate, limit: Int): CatchUpBatch {
        val seen = seenMark()
        return when (val run = database.runCommand { record(generation, today, limit, seen) }) {
            is CommandRun.Finished -> run.value
            is CommandRun.RolledBack -> CatchUpBatch.Failed(run.failure)
            CommandRun.Unknown -> CatchUpBatch.Unknown
        }
    }

    /**
     * How far the notice is marked seen, in epoch milliseconds; null before the first
     * Listo. Unreadable, its last good value or nothing: recording never waits on the
     * notice, and the stamps stored (see [stamp]) still hold every mark Listo can make.
     */
    private suspend fun seenMark(): Long? =
        when (val read = notice.observeCatchUpNoticeSeen().first()) {
            is PreferenceRead.Ready -> read.value
            is PreferenceRead.Failed -> read.lastGood
        }?.toEpochMilli()

    /**
     * When this batch is recorded: now on the audit clock, unless that is not after the
     * latest of [seen], the newest entry with an origin and the last catch-up or restore,
     * the moments the notice can be marked seen up to. Then one millisecond after it, so
     * a clock set back never puts what catch-up records before a Listo (#340).
     */
    private suspend fun stamp(seen: Long?): Instant {
        val now = audit.now()
        val latest = listOfNotNull(
            seen,
            database.expenseDao().newestRecordedAt(),
            database.incomeDao().newestRecordedAt(),
            database.operationReceiptDao().lastCommittedAt(OperationKind.CatchUpSchedules.key),
            database.operationReceiptDao().lastCommittedAt(OperationKind.RestoreHistory.key)
        ).maxOrNull() ?: return now
        val earliest = nextCounter(latest, "expense", null, "created_at")
        return if (now.toEpochMilli() >= earliest) now else Instant.ofEpochMilli(earliest)
    }

    /**
     * Read without writing: [CatchUpBatch.Stale] for a replaced history, nothing recorded
     * when no schedule has an occurrence due, else null and the batch goes ahead.
     */
    private suspend fun anyDue(
        generation: Long,
        today: LocalDate
    ): Outcome<CatchUpBatch?, StorageFailure> {
        when (val profile = profileFrom(database.profileDao().rows())) {
            is Outcome.Failure -> return profile

            is Outcome.Success -> if (profile.value.generation != generation) {
                return Outcome.Success(CatchUpBatch.Stale)
            }
        }
        val pauses = database.scheduleDao().allPauses().groupBy { it.scheduleId }
        for (row in database.scheduleDao().all()) {
            when (val stored = row.toStored(pauses[row.id].orEmpty())) {
                is Outcome.Failure -> return stored

                is Outcome.Success -> {
                    val schedule = stored.value
                    val due = SchedulePolicy.due(
                        schedule.schedule.recurrence,
                        schedule.handled,
                        today,
                        1
                    )
                    if (due.isNotEmpty()) return Outcome.Success(null)
                }
            }
        }
        return Outcome.Success(CatchUpBatch.Recorded(emptyList()))
    }

    private class Due(val stored: StoredSchedule, val origin: EntryOrigin)

    /**
     * Runs inside the transaction. Every return before the first insert writes nothing,
     * and none comes after it: a refusal there would commit the rows already inserted.
     */
    private suspend fun record(
        generation: Long,
        today: LocalDate,
        limit: Int,
        seen: Long?
    ): CatchUpBatch {
        val profile = when (val stored = profileFrom(database.profileDao().rows())) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return CatchUpBatch.Failed(stored.error)
        }
        if (profile.generation != generation) return CatchUpBatch.Stale
        val pauses = database.scheduleDao().allPauses().groupBy { it.scheduleId }
        val schedules = database.scheduleDao().all().map { row ->
            when (val stored = row.toStored(pauses[row.id].orEmpty())) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return CatchUpBatch.Failed(stored.error)
            }
        }
        val due = schedules
            .flatMap { stored ->
                SchedulePolicy.due(stored.schedule.recurrence, stored.handled, today, limit)
                    .map { Due(stored, EntryOrigin(stored.schedule.id, it)) }
            }
            .sortedWith(
                compareBy<Due>({ it.origin.occurrence.date }, { it.stored.sequence })
                    .thenBy { it.origin.occurrence.slot }
            )
            .take(limit)
        if (due.isEmpty()) return CatchUpBatch.Recorded(emptyList())
        // Archiving pauses a category's schedules, so this is damage, never skipped.
        for (item in due) {
            val entry = item.stored.schedule.entry as? ScheduledEntry.Payment ?: continue
            val category = database.categoryDao().byId(entry.categoryId.value)
            if (category == null || category.archived != 0L) {
                return CatchUpBatch.Failed(
                    StorageFailure.Integrity(
                        "schedule",
                        item.stored.schedule.id.value,
                        "category_id"
                    )
                )
            }
        }

        val now = stamp(seen)
        val recorded = due.map { item ->
            when (val entry = item.stored.schedule.entry) {
                is ScheduledEntry.Payment -> {
                    val id = expenseId()
                    database.expenseDao().insert(
                        Expense(
                            id = id,
                            categoryId = entry.categoryId,
                            date = item.origin.occurrence.date,
                            sequence = nextExpenseSequence(),
                            amount = entry.amount,
                            note = entry.note,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                            origin = item.origin
                        ).toEntity()
                    )
                    RecordedOccurrence.Payment(item.origin, id)
                }

                is ScheduledEntry.Income -> {
                    val id = incomeId()
                    database.incomeDao().insert(
                        Income(
                            id = id,
                            date = item.origin.occurrence.date,
                            sequence = nextIncomeSequence(),
                            amount = entry.amount,
                            source = entry.source,
                            note = entry.note,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                            origin = item.origin
                        ).toEntity()
                    )
                    RecordedOccurrence.Income(item.origin, id)
                }
            }
        }
        // Each schedule's checkpoint is the last of its occurrences in this batch.
        recorded.groupBy { it.origin.schedule }.forEach { (schedule, occurrences) ->
            val last = occurrences.last().origin.occurrence
            val moved = database.scheduleDao().setHandled(
                schedule.value,
                EffectiveDates.storedText(last.date),
                last.slot
            )
            check(moved == 1) { "The schedule changed inside the transaction that read it" }
        }
        val operation = when (val made = OperationId.of(ids.next())) {
            is Outcome.Success -> made.value
            is Outcome.Failure -> error("The identifier source made a malformed identifier")
        }
        database.commitCommand(
            profile,
            operation,
            OperationKind.CatchUpSchedules,
            catchUpDigest(profile.generation, recorded.map { it.origin }),
            null,
            now
        )
        return CatchUpBatch.Recorded(recorded)
    }

    private fun expenseId(): ExpenseId = when (val id = ExpenseId.of(ids.next())) {
        is Outcome.Success -> id.value
        is Outcome.Failure -> error("The identifier source made a malformed identifier")
    }

    private fun incomeId(): IncomeId = when (val id = IncomeId.of(ids.next())) {
        is Outcome.Success -> id.value
        is Outcome.Failure -> error("The identifier source made a malformed identifier")
    }

    /** As for a new expense: after the highest of `expense` and the copy kept for undo. */
    private suspend fun nextExpenseSequence(): Long {
        val highest = maxOf(
            database.expenseDao().highestSequence() ?: 0,
            database.deletedExpenseDao().highestSequence() ?: 0
        )
        return nextCounter(highest, "expense", null, "sequence")
    }

    private suspend fun nextIncomeSequence(): Long {
        val highest = maxOf(
            database.incomeDao().highestSequence() ?: 0,
            database.deletedIncomeDao().highestSequence() ?: 0
        )
        return nextCounter(highest, "income", null, "sequence")
    }
}
