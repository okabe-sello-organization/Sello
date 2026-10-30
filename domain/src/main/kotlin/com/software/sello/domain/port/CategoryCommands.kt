package com.software.sello.domain.port

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CarryCorrection
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
 * While it carries a balance into this month that is not zero, [confirmedCarryIn] must be
 * exactly that balance, the one the person saw and agreed to drop (ADR 0020 CP9–CP11).
 */
data class ArchiveCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val confirmedCarryIn: Money? = null
) : CategoryEdit

data class UnarchiveCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long
) : CategoryEdit

/**
 * The limit from the current financial month onwards, replacing that month's own limit
 * if it has one. Earlier months are not touched. Making it unlimited while the category
 * carries a balance that is not zero needs [confirmedCarryIn], as for [ArchiveCategory].
 */
data class SetDefaultLimit(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val limit: BudgetLimit,
    val confirmedCarryIn: Money? = null
) : CategoryEdit

/**
 * The limit of exactly [month], current or past. No other month and no default changes.
 * Making the current month unlimited while it carries a balance needs [confirmedCarryIn].
 * A finite correction of a past month of a carry chain changes later months (ADR 0020
 * CP20–CP22): [confirmation] is what came with the list of changes the person saw.
 */
data class SetMonthLimit(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val month: YearMonth,
    val limit: BudgetLimit,
    val confirmedCarryIn: Money? = null,
    val confirmation: CorrectionConfirmation? = null
) : CategoryEdit

/**
 * Turns carry on from the current financial month, which counts whole from its first day
 * with a carry-in of 0 (ADR 0020 CP1–CP4). Earlier months never count.
 */
data class EnableCarry(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long
) : CategoryEdit

/**
 * Turns carry off in the current financial month, dropping its carry-in (CP9, CP10). While
 * that is not zero, [confirmedCarryIn] must be exactly it.
 */
data class DisableCarry(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val confirmedCarryIn: Money? = null
) : CategoryEdit

/** Why nothing was written. Each of these is certain. */
sealed interface CategoryRejection {
    data object CategoryMissing : CategoryRejection

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

    /** Carry cannot be turned on, for [reason] (ADR 0020 CP3, CP25). */
    data class CarryRefused(val reason: CarryEnableRefusal) : CategoryRejection

    /** Carry is not on this month, so there is nothing to turn off. */
    data object NotCarrying : CategoryRejection

    /**
     * The change drops a balance that was not confirmed, or the one confirmed is no longer
     * the balance (CP9, CP10). [preview] is what the person must see and confirm.
     */
    data class DropUnconfirmed(val preview: DropPreview) : CategoryRejection

    /**
     * The correction changes later months of a carry chain and was not confirmed for this
     * very change at the current revision (CP20–CP22). [correction] is the list to show;
     * confirming sends the same change with [confirmation].
     */
    data class CorrectionUnconfirmed(
        val confirmation: CorrectionConfirmation,
        val correction: CarryCorrection
    ) : CategoryRejection

    /** A past month inside a carry chain cannot become unlimited (CP13). */
    data class ChainMonthUnlimited(val month: YearMonth) : CategoryRejection

    /** A base limit of a category in a carry chain is at most `MAX_CHAIN_BASE` (CP25). */
    data class ChainBaseAboveLimit(val base: Money) : CategoryRejection

    /** The category's carry could not be calculated before or after the change (CP8, CP23). */
    data class CarryUnavailable(val failure: CarryoverFailure) : CategoryRejection
}

/** A committed category change and the receipt that proves it. */
data class CommittedCategoryChange(val receipt: OperationReceipt, val categoryId: CategoryId)

typealias CategoryCommandOutcome = CommandOutcome<CommittedCategoryChange, CategoryRejection>

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
