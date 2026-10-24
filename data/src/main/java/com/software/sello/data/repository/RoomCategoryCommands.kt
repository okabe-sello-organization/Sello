package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.CategoryEntity
import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryBudgetPolicy
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.SchedulePolicy
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommandOutcome.Committed
import com.software.sello.domain.port.CategoryCommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.CategoryCommandOutcome.Rejected
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryEdit
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ChangeCategoryIcon
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import java.time.Instant
import java.time.YearMonth

/**
 * Category and limit changes, each in one transaction with its revision and receipt.
 * A category and its limits are one record for versioning: every committed change
 * raises the category's version by one, and an edit made against an older version is
 * refused. [clock] says which financial month "now" is; [audit] stamps real time.
 */
class RoomCategoryCommands(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource
) : CategoryCommands {
    override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome =
        when (val run = database.runCommand { decide(command) }) {
            is CommandRun.Finished -> run.value
            is CommandRun.RolledBack -> Rejected(CategoryRejection.StorageFailed(run.failure))
            CommandRun.Unknown -> OutcomeUnknown(command.operationId)
        }

    private fun failed(failure: StorageFailure) = Rejected(CategoryRejection.StorageFailed(failure))

    private fun StoredReceipt.change() =
        CommittedCategoryChange(receipt, checkNotNull(categoryId) { "Not a category receipt" })

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: CategoryCommand): CategoryCommandOutcome {
        val digest = inputDigest(command)
        val start =
            database.startCommand(command.operationId, command.generation, command.kind, digest)
        val profile = when (start) {
            is CommandStart.Proceed -> start.profile

            is CommandStart.Stale -> return Rejected(
                CategoryRejection.StaleGeneration(start.current)
            )

            is CommandStart.Replay -> return Committed(start.original.change(), replayed = true)

            CommandStart.Conflict -> return Rejected(CategoryRejection.OperationConflict)

            is CommandStart.Damaged -> return failed(start.failure)
        }
        val month = YearMonth.from(clock.today.value.date)
        val now = audit.now()
        val categoryId = when (command) {
            is CreateCategory -> create(command, month, now)
            is CategoryEdit -> edit(command, month, now)
        }.let { written ->
            when (written) {
                is Written.Done -> written.categoryId
                is Written.Refused -> return written.outcome
            }
        }
        val receipt = database.commitCommand(
            profile,
            command.operationId,
            command.kind,
            digest,
            categoryId.value,
            now
        )
        return Committed(receipt.change(), replayed = false)
    }

    private sealed interface Written {
        data class Done(val categoryId: CategoryId) : Written

        data class Refused(val outcome: Rejected) : Written
    }

    private fun refused(reason: CategoryRejection) = Written.Refused(Rejected(reason))

    /**
     * Archiving pauses every recurring payment in the category from today (rule 9), so
     * nothing is ever recorded into it. Refused, before any write, while one still has
     * an occurrence due that catch-up has not recorded. Null when done.
     */
    private suspend fun pauseSchedules(categoryId: String, now: Instant): Written.Refused? {
        val today = clock.today.value.date
        val schedules = database.scheduleDao().ofCategory(categoryId).map { row ->
            when (val stored = row.toStored(database.scheduleDao().pauses(row.id))) {
                is Outcome.Success -> row to stored.value
                is Outcome.Failure -> return Written.Refused(failed(stored.error))
            }
        }
        val pending = schedules.any { (_, stored) ->
            SchedulePolicy.due(stored.schedule.recurrence, stored.handled, today, limit = 1)
                .isNotEmpty()
        }
        if (pending) return refused(CategoryRejection.SchedulesPending)
        for ((row, stored) in schedules) {
            val recurrence = stored.schedule.recurrence
            val alreadyPaused = recurrence.pauses.lastOrNull()?.let { it.until == null } == true
            if (alreadyPaused) continue
            val from = maxOf(today, recurrence.start)
            database.scheduleDao().insertPause(
                SchedulePause(from, null).toEntity(stored.schedule.id)
            )
            val touched = database.scheduleDao().touch(
                row.id,
                row.version,
                nextCounter(row.version, ScheduleEntity.TABLE, row.id, "version"),
                now.toEpochMilli()
            )
            check(touched == 1) { "The schedule changed inside the transaction that read it" }
        }
        return null
    }

    private suspend fun create(command: CreateCategory, month: YearMonth, now: Instant): Written {
        if (database.categoryDao().byNameKey(command.name.uniquenessKey) != null) {
            return refused(CategoryRejection.NameTaken)
        }
        val id = when (val id = CategoryId.of(ids.next())) {
            is Outcome.Success -> id.value
            is Outcome.Failure -> error("The identifier source returned a malformed identifier")
        }
        val category = Category(id, command.name, command.icon, false, 1, now, now)
        database.categoryDao().insert(category.toEntity())
        // The limit is this month's and the default for the months after it.
        database.limitDao()
            .insert(DefaultLimit(id, month, AutomaticBudget.Limit(command.limit)).toEntity())
        return Written.Done(id)
    }

    private suspend fun edit(command: CategoryEdit, month: YearMonth, now: Instant): Written {
        val id = command.categoryId
        val row = database.categoryDao().byId(id.value)
            ?: return refused(CategoryRejection.CategoryMissing)
        val category = when (val stored = row.toDomain()) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return Written.Refused(failed(stored.error))
        }
        if (category.version != command.expectedVersion) {
            return refused(CategoryRejection.VersionConflict(category.version))
        }
        val next = category.copy(
            version = nextCounter(category.version, CategoryEntity.TABLE, id.value, "version"),
            updatedAt = now
        )
        val limits = database.limitDao()
        val changed = when (command) {
            is RenameCategory -> {
                val holder = database.categoryDao().byNameKey(command.name.uniquenessKey)
                if (holder != null && holder.id != id.value) {
                    return refused(CategoryRejection.NameTaken)
                }
                next.copy(name = command.name)
            }

            is ChangeCategoryIcon -> next.copy(icon = command.icon)

            is ArchiveCategory -> {
                if (category.archived) return refused(CategoryRejection.AlreadyArchived)
                pauseSchedules(id.value, now)?.let { return it }
                val from = CategoryBudgetPolicy.pausedFrom(month)
                limits.insert(DefaultLimit(id, from, AutomaticBudget.Paused).toEntity())
                next.copy(archived = true)
            }

            is UnarchiveCategory -> {
                if (!category.archived) return refused(CategoryRejection.NotArchived)
                val history = when (val stored = history(id)) {
                    is Outcome.Success -> stored.value
                    is Outcome.Failure -> return Written.Refused(failed(stored.error))
                }
                val change = CategoryBudgetPolicy.unarchive(month, history)
                change.remove.forEach { limits.removeDefault(id.value, it.toString()) }
                change.put?.let { limits.put(it.toEntity()) }
                next.copy(archived = false)
            }

            is SetDefaultLimit -> {
                if (category.archived) return refused(CategoryRejection.CategoryArchived)
                limits.put(DefaultLimit(id, month, AutomaticBudget.Limit(command.limit)).toEntity())
                next
            }

            is SetMonthLimit -> {
                if (command.month > month) return refused(CategoryRejection.FutureMonth(month))
                // Refused before the write: once stored it could not be read back.
                if (!EffectiveDates.isStorable(command.month)) {
                    return refused(CategoryRejection.InvalidMonth)
                }
                limits.put(MonthLimit(id, command.month, command.limit).toEntity())
                next
            }
        }
        check(database.categoryDao().update(changed.toEntity()) == 1)
        return Written.Done(id)
    }

    /** One category's whole default history. It grows by at most one entry per month. */
    private suspend fun history(id: CategoryId): Outcome<List<DefaultLimit>, StorageFailure> {
        val rows = database.limitDao().defaults(id.value, MAX_DEFAULT_HISTORY + 1)
        if (rows.size > MAX_DEFAULT_HISTORY) {
            return Outcome.Failure(
                StorageFailure.Integrity(DefaultLimitEntity.TABLE, id.value, "effective_month")
            )
        }
        return rows.decodeAll { it.toDomain() }
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedCategoryChange?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)

            is Outcome.Failure -> receipt

            is Outcome.Success ->
                Outcome.Success(receipt.value.categoryId?.let { receipt.value.change() })
        }
    }
}

/** A century of monthly changes; more than that is not a real history. */
internal const val MAX_DEFAULT_HISTORY = 1_200
