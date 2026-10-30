package com.software.sello.domain.port

import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.EntryBatchPolicy
import java.time.LocalDate

/** One expense on a receipt of several: its category, amount and note. */
data class ExpenseLine(val categoryId: CategoryId, val amount: TransactionAmount, val note: Note?)

/**
 * A request to record [lines] as expenses, all dated [date], in one operation: every line
 * or none. [EntryBatchPolicy.MAX_LINES] lines at most. Everything except [operationId] is
 * the input a replay is compared against, the lines in their order.
 *
 * [confirmation] is for a receipt that changes later months of a carry chain (ADR 0020
 * CP20–CP22): it covers the whole receipt, as [CreateExpense.confirmation] covers one expense.
 */
data class CreateExpenses(
    val operationId: OperationId,
    val generation: Long,
    val date: LocalDate,
    val lines: List<ExpenseLine>,
    val confirmation: CorrectionConfirmation? = null
)

/** One income on a receipt of several: its source, amount and note. */
data class IncomeLine(val source: IncomeSource, val amount: TransactionAmount, val note: Note?)

/** A request to record [lines] as incomes, all dated [date], as [CreateExpenses] is for expenses. */
data class CreateIncomes(
    val operationId: OperationId,
    val generation: Long,
    val date: LocalDate,
    val lines: List<IncomeLine>
)

/** Why one line of a receipt cannot be recorded. */
sealed interface LineRefusal {
    /** Expenses only: the line's category does not exist. */
    data object CategoryMissing : LineRefusal

    /** Expenses only: the line's category is archived. */
    data object CategoryArchived : LineRefusal

    /** The receipt's date is after the financial current date. */
    data class FutureDate(val today: LocalDate) : LineRefusal

    /** The receipt's date cannot be stored and read back. */
    data object InvalidDate : LineRefusal

    /** The month's total, with this line and every line before it, would overflow. */
    data object MonthTotalFull : LineRefusal
}

/**
 * A line that cannot be recorded: [position] is its 0-based place in the submitted lines.
 * A line with several faults is named once, for the first of: the date, the category, the
 * month's total.
 */
data class RefusedLine(val position: Int, val reason: LineRefusal)

/** Why a receipt of expenses wrote nothing, beyond the reasons every [CommandRejection] has. */
sealed interface ExpenseBatchRejection {
    /** No lines, or more than [EntryBatchPolicy.MAX_LINES]. */
    data class LineCount(val count: Int) : ExpenseBatchRejection

    /** Every line that cannot be recorded, in line order; the others were not written either. */
    data class Lines(val refused: List<RefusedLine>) : ExpenseBatchRejection

    /**
     * The receipt reaches a closed month of a carry chain and changes later months, and it
     * was not confirmed for this very receipt at the current revision (ADR 0020 CP20–CP22).
     * Confirming sends the same receipt with [confirmation].
     */
    data class CorrectionUnconfirmed(
        val confirmation: CorrectionConfirmation,
        val correction: CarryCorrection
    ) : ExpenseBatchRejection

    /** A category's carry could not be calculated before or after the receipt (CP8, CP23). */
    data class CarryUnavailable(val failure: CarryoverFailure) : ExpenseBatchRejection
}

/** Why a receipt of incomes wrote nothing, beyond the reasons every [CommandRejection] has. */
sealed interface IncomeBatchRejection {
    /** No lines, or more than [EntryBatchPolicy.MAX_LINES]. */
    data class LineCount(val count: Int) : IncomeBatchRejection

    /** Every line that cannot be recorded, in line order; the others were not written either. */
    data class Lines(val refused: List<RefusedLine>) : IncomeBatchRejection
}

/** A committed receipt of expenses: its one receipt and the new expenses, in line order. */
data class CommittedExpenses(val receipt: OperationReceipt, val expenseIds: List<ExpenseId>)

/** A committed receipt of incomes: its one receipt and the new incomes, in line order. */
data class CommittedIncomes(val receipt: OperationReceipt, val incomeIds: List<IncomeId>)

typealias ExpenseBatchOutcome = CommandOutcome<CommittedExpenses, ExpenseBatchRejection>

typealias IncomeBatchOutcome = CommandOutcome<CommittedIncomes, IncomeBatchRejection>

/**
 * Records several expenses as one operation. The lines, the revision and the receipt are
 * one transaction: all of it happens or none of it. The expenses are ordinary ones, edited
 * and deleted one by one like any other. Submitting the same operation and input again
 * returns the original receipt and expenses.
 *
 * A cancelled caller gets the cancellation, which says nothing about whether the receipt
 * was saved; [find] does.
 */
interface ExpenseBatchCommands {
    suspend fun create(command: CreateExpenses): ExpenseBatchOutcome

    /** The committed receipt of [operationId], or null when it recorded no receipt of expenses. */
    suspend fun find(operationId: OperationId): Outcome<CommittedExpenses?, StorageFailure>
}

/** Records several incomes as one operation, with the guarantees of [ExpenseBatchCommands]. */
interface IncomeBatchCommands {
    suspend fun create(command: CreateIncomes): IncomeBatchOutcome

    /** The committed receipt of [operationId], or null when it recorded no receipt of incomes. */
    suspend fun find(operationId: OperationId): Outcome<CommittedIncomes?, StorageFailure>
}
