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

/** A limit the category form changes: from [EditCategory.month] onwards, or of one past month. */
sealed interface LimitEdit {
    /** As [SetDefaultLimit]: the default from the month on, replacing that month's own limit. */
    data class FromThisMonth(val limit: BudgetLimit) : LimitEdit

    /** As [SetMonthLimit] for a month before the current one. */
    data class OfMonth(val month: YearMonth, val limit: BudgetLimit) : LimitEdit
}

/** Carry turned on or off in [EditCategory.month]. */
enum class CarrySetting { On, Off }

/**
 * Everything the category form changes, saved together in one transaction under one
 * receipt, or nothing at all (SELLO-188). A null part stays as it is; at least one part
 * changes. [month] is the current financial month the person saw: a limit from it onwards
 * and a carry change apply to it, and are refused once another month has begun. A past
 * month's limit is of a month before [month].
 *
 * [carry] goes with no limit or with [LimitEdit.FromThisMonth]: turning it on is checked
 * against the limit as the edit leaves it (CP3), and turning it off, or making the limit
 * unlimited, drops the balance carried into [month] once, which must be [confirmedCarryIn]
 * when it is not zero (CP9–CP11). [confirmation] is for a past month's correction (CP20).
 */
data class EditCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val month: YearMonth,
    val name: CategoryName? = null,
    val icon: IconKey? = null,
    val limit: LimitEdit? = null,
    val carry: CarrySetting? = null,
    val confirmedCarryIn: Money? = null,
    val confirmation: CorrectionConfirmation? = null
) : CategoryEdit

/**
 * Deletes the category for good (ADR 0024), in one transaction: its limits and carry
 * always, and its expenses and fixed payments as [disposal] says. [confirmed] is the
 * preview the person saw; while the category, the target or the month no longer match it
 * exactly, the command is refused with [CategoryRejection.ContentsChanged] and the
 * current preview. Moving into a category whose carry chain the moved expenses change
 * needs [confirmation], as a correction does (ADR 0020 CP20–CP22).
 */
data class DeleteCategory(
    override val operationId: OperationId,
    override val generation: Long,
    override val categoryId: CategoryId,
    override val expectedVersion: Long,
    val disposal: Disposal,
    val confirmed: RemovalPreview,
    val confirmation: CorrectionConfirmation? = null
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

    /**
     * The edit changes nothing, or changes carry together with a past month's limit:
     * the category form never sends either.
     */
    data object NotOneEdit : CategoryRejection

    /** The edit was made for a month that is no longer the current one, [currentMonth]. */
    data class MonthMoved(val currentMonth: YearMonth) : CategoryRejection

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

    /**
     * The category, the target or the month no longer match what the person confirmed.
     * [current] is what deleting would do now; it must be shown and confirmed again.
     */
    data class ContentsChanged(val current: RemovalPreview) : CategoryRejection

    /** The category that was to receive the expenses no longer exists. */
    data object TargetMissing : CategoryRejection

    /** The category that was to receive the expenses is archived and takes none. */
    data object TargetArchived : CategoryRejection

    /** A category cannot receive its own expenses. */
    data object SameCategory : CategoryRejection

    /** The sum of the category's expenses does not fit in `Money`; no total can be confirmed. */
    data object TotalOverflow : CategoryRejection

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
