package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.CategoryEntity
import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.FinancialProfile
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
import com.software.sello.domain.port.CarrySetting
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryEdit
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ChangeCategoryIcon
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.DeleteCategory
import com.software.sello.domain.port.DisableCarry
import com.software.sello.domain.port.EditCategory
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.LimitEdit
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RemovalPreviewFailure
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Category and limit changes, each in one transaction with its revision and receipt.
 * A category and its limits are one record for versioning: every committed change
 * raises the category's version by one, and an edit made against an older version is
 * refused. [clock] says which financial month "now" is; [audit] stamps real time.
 *
 * Its carry chains are part of the same record (ADR 0020 CP28): turning carry on or off,
 * and every change that drops a balance or corrects a chain month, is checked by
 * [CategoryCarry] before the first write and stored with the rest.
 */
class RoomCategoryCommands(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource
) : CategoryCommands {
    override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome =
        database.runOutcome(command.operationId) { decide(command) }

    private fun StoredReceipt.change() =
        CommittedCategoryChange(receipt, checkNotNull(categoryId) { "Not a category receipt" })

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: CategoryCommand): CategoryCommandOutcome {
        val digest = inputDigest(command)
        val profile = database.proceedOr(
            command.operationId,
            command.generation,
            command.kind,
            digest,
            { it.change() }
        ) { return it }
        val month = YearMonth.from(clock.today.value.date)
        val now = audit.now()
        val categoryId = when (command) {
            is CreateCategory -> create(command, month, now)
            is CategoryEdit -> edit(command, profile, month, now)
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

        data class Refused(val outcome: Rejected<CategoryRejection>) : Written
    }

    private fun refused(reason: CommandRejection<CategoryRejection>) =
        Written.Refused(Rejected(reason))

    /** The chains [this] decided, null when they stay as they are; [refuse] otherwise. */
    private inline fun CarryDecision.chainsOr(
        refuse: (Written.Refused) -> Nothing
    ): List<CarryChain>? = when (this) {
        CarryDecision.Unchanged -> null
        is CarryDecision.Chains -> chains
        is CarryDecision.Refused -> refuse(refused(reason))
    }

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
                is Outcome.Failure -> return Written.Refused(storageFailed(stored.error))
            }
        }
        val pending = schedules.any { (_, stored) ->
            SchedulePolicy.due(stored.schedule.recurrence, stored.handled, today, limit = 1)
                .isNotEmpty()
        }
        if (pending) return refused(Specific(CategoryRejection.SchedulesPending))
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
            return refused(Specific(CategoryRejection.NameTaken))
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

    private suspend fun edit(
        command: CategoryEdit,
        profile: FinancialProfile,
        month: YearMonth,
        now: Instant
    ): Written {
        val id = command.categoryId
        val row = database.categoryDao().byId(id.value)
            ?: return refused(Specific(CategoryRejection.CategoryMissing))
        val category = when (val stored = row.toDomain()) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return Written.Refused(storageFailed(stored.error))
        }
        if (category.version != command.expectedVersion) {
            return refused(CommandRejection.VersionConflict(category.version))
        }
        // Nothing is left to update afterwards: the category is gone.
        if (command is DeleteCategory) return delete(command, category, profile, now)
        val next = category.copy(
            version = nextCounter(category.version, CategoryEntity.TABLE, id.value, "version"),
            updatedAt = now
        )
        val limits = database.limitDao()
        val carry = CategoryCarry(database, id, month, clock.today.value.date)
        val changed = when (command) {
            is RenameCategory -> {
                val holder = database.categoryDao().byNameKey(command.name.uniquenessKey)
                if (holder != null && holder.id != id.value) {
                    return refused(Specific(CategoryRejection.NameTaken))
                }
                next.copy(name = command.name)
            }

            is ChangeCategoryIcon -> next.copy(icon = command.icon)

            is ArchiveCategory -> {
                if (category.archived) return refused(Specific(CategoryRejection.AlreadyArchived))
                // CP11: archiving drops the balance carried into this month.
                val dropped = carry.drop(command.confirmedCarryIn).chainsOr { return it }
                pauseSchedules(id.value, now)?.let { return it }
                val from = CategoryBudgetPolicy.pausedFrom(month)
                limits.insert(DefaultLimit(id, from, AutomaticBudget.Paused).toEntity())
                dropped?.let { carry.write(it) }
                next.copy(archived = true)
            }

            // CP11: never turns carry back on; the person turns it on again.
            is UnarchiveCategory -> {
                if (!category.archived) return refused(Specific(CategoryRejection.NotArchived))
                val history = when (val stored = database.defaultHistory(id.value)) {
                    is Outcome.Success -> stored.value
                    is Outcome.Failure -> return Written.Refused(storageFailed(stored.error))
                }
                val change = CategoryBudgetPolicy.unarchive(month, history)
                change.remove.forEach { limits.removeDefault(id.value, it.toString()) }
                change.put?.let { limits.put(it.toEntity()) }
                next.copy(archived = false)
            }

            is SetDefaultLimit -> {
                if (category.archived) return refused(Specific(CategoryRejection.CategoryArchived))
                val dropped = when (val limit = command.limit) {
                    // CP11: an unlimited default drops the balance carried into this month.
                    BudgetLimit.Unlimited -> carry.drop(command.confirmedCarryIn)

                    is BudgetLimit.Finite -> carry.finiteDefault(limit)
                }.chainsOr { return it }
                limits.put(DefaultLimit(id, month, AutomaticBudget.Limit(command.limit)).toEntity())
                // A new default sets this month too (D04), so its own limit gives way.
                limits.removeMonth(id.value, month.toString())
                dropped?.let { carry.write(it) }
                next
            }

            is SetMonthLimit -> {
                if (command.month >
                    month
                ) {
                    return refused(Specific(CategoryRejection.FutureMonth(month)))
                }
                // Refused before the write: once stored it could not be read back.
                if (!EffectiveDates.isStorable(command.month)) {
                    return refused(Specific(CategoryRejection.InvalidMonth))
                }
                val unlimitedNow = command.month == month && command.limit == BudgetLimit.Unlimited
                val dropped = if (unlimitedNow) {
                    // CP11: this month made unlimited drops the balance carried into it.
                    carry.drop(command.confirmedCarryIn)
                } else {
                    carry.monthLimit(
                        command.month,
                        command.limit,
                        profile.revision,
                        correctionIdentity(command),
                        command.confirmation
                    )
                }.chainsOr { return it }
                limits.put(MonthLimit(id, command.month, command.limit).toEntity())
                dropped?.let { carry.write(it) }
                next
            }

            is EnableCarry -> {
                val chains = when (val decided = carry.enable(category.archived)) {
                    is CarryDecision.Chains -> decided.chains
                    is CarryDecision.Refused -> return refused(decided.reason)
                    CarryDecision.Unchanged -> error("Turning carry on always decides the chains")
                }
                carry.write(chains)
                next
            }

            is DisableCarry -> {
                val chains = carry.drop(command.confirmedCarryIn).chainsOr { return it }
                    ?: return refused(Specific(CategoryRejection.NotCarrying))
                carry.write(chains)
                next
            }

            is EditCategory -> when (
                val edited = editAll(
                    command,
                    category,
                    next,
                    profile,
                    month
                )
            ) {
                is Edited.Done -> edited.category
                is Edited.Refused -> return edited.refused
            }

            is DeleteCategory -> error("A deletion is decided above")
        }
        check(database.categoryDao().update(changed.toEntity()) == 1)
        return Written.Done(id)
    }

    private sealed interface Edited {
        data class Done(val category: Category) : Edited

        data class Refused(val refused: Written.Refused) : Edited
    }

    /**
     * The category form's whole save (SELLO-188): name, icon, limit and carry, in this one
     * transaction under one receipt. Every check comes first, each on the state the parts
     * before it leave, since a refusal returned after a write would keep that write; only
     * then is anything written. [next] is the category at its next version.
     */
    private suspend fun editAll(
        command: EditCategory,
        category: Category,
        next: Category,
        profile: FinancialProfile,
        month: YearMonth
    ): Edited {
        fun refuse(reason: CategoryRejection) = Edited.Refused(refused(Specific(reason)))
        val id = command.categoryId
        val limit = command.limit
        val empty = command.name == null && command.icon == null && limit == null &&
            command.carry == null
        // A past month's own limit is all a past month's form changes: carry is today's.
        val pastWithCarry = limit is LimitEdit.OfMonth &&
            (command.carry != null || limit.month >= command.month)
        if (empty || pastWithCarry) return refuse(CategoryRejection.NotOneEdit)
        // A limit from the month on and carry apply to the month the person saw, never to
        // the next one; a name, an icon or a past month's limit name no month of their own.
        val forTheMonth = limit is LimitEdit.FromThisMonth || command.carry != null
        if (forTheMonth && command.month != month) {
            return refuse(CategoryRejection.MonthMoved(month))
        }
        command.name?.let { name ->
            val holder = database.categoryDao().byNameKey(name.uniquenessKey)
            if (holder != null && holder.id != id.value) {
                return refuse(CategoryRejection.NameTaken)
            }
        }
        if (limit is LimitEdit.FromThisMonth && category.archived) {
            return refuse(CategoryRejection.CategoryArchived)
        }
        if (limit is LimitEdit.OfMonth && !EffectiveDates.isStorable(limit.month)) {
            return refuse(CategoryRejection.InvalidMonth)
        }
        val carry = CategoryCarry(database, id, month, clock.today.value.date)
        // CP9–CP11: turning off and an unlimited limit from this month both drop the
        // balance carried into it, once, with the one confirmation.
        val unlimited = (limit as? LimitEdit.FromThisMonth)?.limit == BudgetLimit.Unlimited
        var chains: List<CarryChain>? = null
        if (command.carry == CarrySetting.Off || unlimited) {
            when (val dropped = carry.drop(command.confirmedCarryIn)) {
                CarryDecision.Unchanged -> if (command.carry == CarrySetting.Off) {
                    return refuse(CategoryRejection.NotCarrying)
                }

                is CarryDecision.Chains -> chains = dropped.chains

                is CarryDecision.Refused -> return Edited.Refused(refused(dropped.reason))
            }
        }
        val decided = when (limit) {
            null -> CarryDecision.Unchanged

            is LimitEdit.FromThisMonth -> when (val asked = limit.limit) {
                BudgetLimit.Unlimited -> CarryDecision.Unchanged
                is BudgetLimit.Finite -> carry.finiteDefault(asked, after = chains)
            }

            is LimitEdit.OfMonth -> carry.monthLimit(
                limit.month,
                limit.limit,
                profile.revision,
                correctionIdentity(command),
                command.confirmation
            )
        }
        if (decided is CarryDecision.Refused) return Edited.Refused(refused(decided.reason))
        val default = (limit as? LimitEdit.FromThisMonth)?.let {
            DefaultLimit(id, month, AutomaticBudget.Limit(it.limit))
        }
        if (command.carry == CarrySetting.On) {
            // CP3 on the limit as this edit leaves it.
            chains = when (val enabled = carry.enable(category.archived, default)) {
                is CarryDecision.Chains -> enabled.chains
                is CarryDecision.Refused -> return Edited.Refused(refused(enabled.reason))
                CarryDecision.Unchanged -> error("Turning carry on always decides the chains")
            }
        }
        val limits = database.limitDao()
        when (limit) {
            null -> Unit

            is LimitEdit.FromThisMonth -> {
                limits.put(checkNotNull(default).toEntity())
                // A new default sets this month too (D04), so its own limit gives way.
                limits.removeMonth(id.value, month.toString())
            }

            is LimitEdit.OfMonth -> limits.put(MonthLimit(id, limit.month, limit.limit).toEntity())
        }
        chains?.let { carry.write(it) }
        return Edited.Done(
            next.copy(name = command.name ?: next.name, icon = command.icon ?: next.icon)
        )
    }

    /**
     * Deletes the category for good (ADR 0024). What it holds is read again here, in this
     * transaction, and must be exactly what the person confirmed. Moving its expenses into
     * a carrying category is a correction of that category's chain (CP20–CP22). Its limits
     * and carry go in both cases; nothing cascades, so each row that refers to it is moved
     * or deleted here first. Every refusal comes before the first write.
     */
    private suspend fun delete(
        command: DeleteCategory,
        category: Category,
        profile: FinancialProfile,
        now: Instant
    ): Written {
        val id = command.categoryId.value
        // One day for the figures and the carry check alike.
        val today = clock.today.value.date
        val contents = when (
            val read = database.categoryContents(command.categoryId, command.disposal, today)
        ) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return refused(read.error.rejection())
        }
        if (contents.preview != command.confirmed) {
            return refused(Specific(CategoryRejection.ContentsChanged(contents.preview)))
        }
        when (val target = contents.target) {
            null -> deleteContents(id, contents)

            else -> {
                val moved = contents.expenses.map { it.copy(categoryId = target.id.value) }
                carryRefusal(moved, today, profile, command)?.let { return it }
                moveContents(id, target, contents, now)
            }
        }
        database.carryChainDao().deleteOfCategory(id)
        database.limitDao().deleteMonthsOf(id)
        database.limitDao().deleteDefaultsOf(id)
        val deleted = database.categoryDao().delete(id, category.version)
        check(deleted == 1) { "The category changed inside the transaction that read it" }
        return Written.Done(command.categoryId)
    }

    /** Why [moved] cannot go into their new category for carry, or null when they can. */
    private suspend fun carryRefusal(
        moved: List<ExpenseEntity>,
        today: LocalDate,
        profile: FinancialProfile,
        command: DeleteCategory
    ): Written.Refused? = when (
        val carry = database.expensesCarry(
            null,
            moved,
            YearMonth.from(today),
            today,
            profile.revision,
            correctionIdentity(command),
            command.confirmation
        )
    ) {
        null -> null

        is ExpensesCarry.Failed -> Written.Refused(storageFailed(carry.failure))

        is ExpensesCarry.Unavailable ->
            refused(Specific(CategoryRejection.CarryUnavailable(carry.failure)))

        is ExpensesCarry.Unconfirmed -> refused(
            Specific(CategoryRejection.CorrectionUnconfirmed(carry.confirmation, carry.correction))
        )
    }

    /**
     * Every expense and fixed payment of [id] goes to [target] as it is, and the kept copy
     * of a deleted expense with them, so undoing that deletion puts it where the others
     * went. Only the months [target] spends in change, never its chains' months, so its
     * chains stay as stored. [target] is one record with its limits and carry: its version
     * goes up.
     */
    private suspend fun moveContents(
        id: String,
        target: Category,
        contents: CategoryContents,
        now: Instant
    ) {
        val to = target.id.value
        val at = now.toEpochMilli()
        for (row in contents.expenses) {
            val version = nextCounter(row.version, ExpenseEntity.TABLE, row.id, "version")
            val moved = database.expenseDao().moveTo(row.id, row.version, to, version, at)
            check(moved == 1) { "The expense changed inside the transaction that read it" }
        }
        for (schedule in contents.schedules) {
            val row = schedule.row
            val version = nextCounter(row.version, ScheduleEntity.TABLE, row.id, "version")
            val moved = database.scheduleDao().moveTo(row.id, row.version, to, version, at)
            check(moved == 1) { "The schedule changed inside the transaction that read it" }
        }
        database.deletedExpenseDao().retarget(id, to)
        val next = target.copy(
            version = nextCounter(target.version, CategoryEntity.TABLE, to, "version"),
            updatedAt = now
        )
        check(database.categoryDao().update(next.toEntity()) == 1)
    }

    /** Every expense and fixed payment of [id] goes, and the kept copy of a deleted expense. */
    private suspend fun deleteContents(id: String, contents: CategoryContents) {
        database.deletedExpenseDao().removeOfCategory(id)
        val removed = database.expenseDao().deleteOfCategory(id)
        check(removed == contents.expenses.size) {
            "The expenses changed inside the transaction that read them"
        }
        for (schedule in contents.schedules) {
            val row = schedule.row
            check(database.scheduleDao().removePauses(row.id) == schedule.pauses) {
                "The pauses changed inside the transaction that read them"
            }
            check(database.scheduleDao().delete(row.id, row.version) == 1) {
                "The schedule changed inside the transaction that read it"
            }
        }
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

/** Why a deletion's preview could not be read, as the command's refusal. */
private fun RemovalPreviewFailure.rejection(): CommandRejection<CategoryRejection> = when (this) {
    RemovalPreviewFailure.CategoryMissing -> Specific(CategoryRejection.CategoryMissing)
    RemovalPreviewFailure.TargetMissing -> Specific(CategoryRejection.TargetMissing)
    RemovalPreviewFailure.TargetArchived -> Specific(CategoryRejection.TargetArchived)
    RemovalPreviewFailure.SameCategory -> Specific(CategoryRejection.SameCategory)
    RemovalPreviewFailure.TotalOverflow -> Specific(CategoryRejection.TotalOverflow)
    is RemovalPreviewFailure.Storage -> CommandRejection.StorageFailed(failure)
}

/** A century of monthly changes; more than that is not a real history. */
internal const val MAX_DEFAULT_HISTORY = 1_200
