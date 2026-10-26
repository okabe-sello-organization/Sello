package com.software.sello.domain.port

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import java.time.YearMonth

/**
 * A change to a category or its limits. [operationId] names the attempt and
 * [generation] the history it was prepared against, as for every command.
 */
sealed interface CategoryCommand {
    val operationId: OperationId
    val generation: Long
}

/** A change to an existing category, made against the version the caller last saw. */
sealed interface CategoryEdit : CategoryCommand {
    val categoryId: CategoryId
    val expectedVersion: Long
}

/** A new category. [limit] applies to the current financial month and is the default after it. */
data class CreateCategory(
    override val operationId: OperationId,
    override val generation: Long,
    val name: CategoryName,
    val icon: IconKey,
    val limit: BudgetLimit
) : CategoryCommand

/** The identity, history, amounts and limits stay; only the shown name changes. */
data class RenameCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val name: CategoryName
) : CategoryEdit

data class ChangeCategoryIcon(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val icon: IconKey
) : CategoryEdit

/**
 * Stops new expenses in the category. Nothing is deleted: this month keeps its
 * limit, and the category gets no automatic budget from next month while archived.
 */
data class ArchiveCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long
) : CategoryEdit

data class UnarchiveCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long
) : CategoryEdit

/**
 * The limit from the current financial month onwards, replacing that month's own limit
 * if it has one. Earlier months are not touched.
 */
data class SetDefaultLimit(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val limit: BudgetLimit
) : CategoryEdit

/** The limit of exactly [month], current or past. No other month and no default changes. */
data class SetMonthLimit(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val month: YearMonth,
    val limit: BudgetLimit
) : CategoryEdit

/** Why nothing was written. Each of these is certain. */
sealed interface CategoryRejection {
    data class StaleGeneration(val current: Long) : CategoryRejection

    /** This operation already committed with different input. */
    data object OperationConflict : CategoryRejection

    data object CategoryMissing : CategoryRejection

    /** Someone changed the category since the caller read it. */
    data class VersionConflict(val current: Long) : CategoryRejection

    /** Another category, archived or not, already has this name ignoring case and accents. */
    data object NameTaken : CategoryRejection

    /** An archived category has no default to change; unarchive it first. */
    data object CategoryArchived : CategoryRejection

    data object AlreadyArchived : CategoryRejection

    data object NotArchived : CategoryRejection

    /**
     * A recurring payment in this category still has occurrences due up to today that
     * catch-up has not recorded; archiving now would leave them nowhere to go.
     */
    data object SchedulesPending : CategoryRejection

    /** Limits are not set ahead of time. */
    data class FutureMonth(val currentMonth: YearMonth) : CategoryRejection

    /** The month cannot be stored and read back; see `EffectiveDateError.OutOfRange`. */
    data object InvalidMonth : CategoryRejection

    data class StorageFailed(val failure: StorageFailure) : CategoryRejection
}

/** A committed category change and the receipt that proves it. */
data class CommittedCategoryChange(val receipt: OperationReceipt, val categoryId: CategoryId)

sealed interface CategoryCommandOutcome {
    data class Committed(val change: CommittedCategoryChange, val replayed: Boolean) :
        CategoryCommandOutcome

    data class Rejected(val reason: CategoryRejection) : CategoryCommandOutcome

    /** Not known whether it was saved: ask [CategoryCommands.find] or submit the same command. */
    data class OutcomeUnknown(val operationId: OperationId) : CategoryCommandOutcome
}

interface CategoryCommands {
    /**
     * Applies the change once, with its revision and receipt, in one transaction.
     * The same operation and input again returns the original receipt.
     */
    suspend fun submit(command: CategoryCommand): CategoryCommandOutcome

    /** The committed result of [operationId], or null when it committed no category change. */
    suspend fun find(operationId: OperationId): Outcome<CommittedCategoryChange?, StorageFailure>
}

/** One category and what its budget is in a month. */
data class CategoryBudget(val category: Category, val state: MonthBudgetState)

/** Every category's budget in [month], all read at one [revision] of one [generation]. */
data class MonthBudget(
    val month: YearMonth,
    val generation: Long,
    val revision: Long,
    val categories: List<CategoryBudget>
)

interface CategoryReads {
    /**
     * Every category, archived ones included, each with its budget in [month]. Only a
     * category that is not archived can take a new expense; archived ones are kept so
     * that months where they had a limit or spending stay explainable.
     */
    suspend fun monthBudget(month: YearMonth): Outcome<MonthBudget, StorageFailure>
}
