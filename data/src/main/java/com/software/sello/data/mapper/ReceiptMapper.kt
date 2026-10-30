package com.software.sello.data.mapper

import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRateId
import java.time.Instant

/**
 * A stored receipt, decoded. The subject is the record the operation changed: an
 * expense for the four expense kinds, an income for the four income kinds, a goal for
 * the goal kinds, a schedule for the schedule kinds, an investment for the investment
 * kinds, the rate entered for a new USD rate, none for a restore or reset of the whole
 * history, a category for every other kind. A receipt of several expenses or incomes has
 * them all, in line order, in [expenseIds] or [incomeIds].
 */
internal class StoredReceipt(
    val receipt: OperationReceipt,
    val inputDigest: String,
    val expenseId: ExpenseId? = null,
    val categoryId: CategoryId? = null,
    val incomeId: IncomeId? = null,
    val goalId: GoalId? = null,
    val scheduleId: ScheduleId? = null,
    val investmentId: InvestmentId? = null,
    val usdRateId: UsdRateId? = null,
    val expenseIds: List<ExpenseId> = emptyList(),
    val incomeIds: List<IncomeId> = emptyList()
)

private val sha256Hex = Regex("[0-9a-f]{64}")

/**
 * The subjects of a receipt of several entries, as its command stores them: each
 * identifier in line order, joined by [SUBJECT_SEPARATOR], one or more, none twice. The
 * command's line limit is not checked here: a receipt stored under a higher limit must
 * still read, or its outcome could never be found.
 */
private fun <T> RowDecoder.subjects(subject: String, id: (String) -> Outcome<T, *>): List<T> {
    // An empty subject splits into one empty identifier, which is not valid.
    val ids = subject.split(SUBJECT_SEPARATOR).map { valid("subject_id", id(it)) }
    check("subject_id", ids.toSet().size == ids.size)
    return ids
}

/** Between the identifiers of a receipt of several entries; never part of one. */
internal const val SUBJECT_SEPARATOR = ","

/** The kinds whose subject is an expense. Every other kind is about a category. */
private val ExpenseKinds = setOf(
    OperationKind.CreateExpense,
    OperationKind.EditExpense,
    OperationKind.DeleteExpense,
    OperationKind.RestoreExpense
)

/** The kinds whose subject is an income. */
private val IncomeKinds = setOf(
    OperationKind.CreateIncome,
    OperationKind.EditIncome,
    OperationKind.DeleteIncome,
    OperationKind.RestoreIncome
)

/** The kinds whose subject is a goal. */
private val GoalKinds = OperationKind.entries.filter { it.key.startsWith("goal.") }.toSet()

/** The kinds whose subject is a schedule. */
private val ScheduleKinds = OperationKind.entries
    .filter { it.key.startsWith("schedule.") && it != OperationKind.CatchUpSchedules }
    .toSet()

/** The kinds whose subject is an investment; a new USD rate is about the rate. */
private val InvestmentKinds = OperationKind.entries
    .filter { it.key.startsWith("investment.") && it != OperationKind.SetUsdRate }
    .toSet()

internal fun OperationReceiptEntity.toStored(): Outcome<StoredReceipt, StorageFailure.Integrity> =
    decodeRow(OperationReceiptEntity.TABLE, operationId) {
        val kind = OperationKind.entries.firstOrNull { it.key == kind } ?: reject("kind")
        check("generation", generation >= 1)
        // The first commit produces revision 1; a receipt cannot describe revision 0.
        check("revision", revision >= 1)
        check("input_digest", sha256Hex.matches(inputDigest))
        val receipt = OperationReceipt(
            operationId = valid("operation_id", OperationId.of(operationId)),
            kind = kind,
            generation = generation,
            revision = revision,
            committedAt = Instant.ofEpochMilli(committedAt)
        )
        val subject = subjectId.orEmpty()
        when (kind) {
            in ExpenseKinds ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    expenseId = valid("subject_id", ExpenseId.of(subject))
                )

            OperationKind.CreateExpenses ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    expenseIds = subjects(subject, ExpenseId::of)
                )

            OperationKind.CreateIncomes ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    incomeIds = subjects(subject, IncomeId::of)
                )

            OperationKind.RestoreHistory,
            OperationKind.ResetHistory,
            OperationKind.CatchUpSchedules -> {
                check("subject_id", subjectId == null)
                StoredReceipt(receipt, inputDigest)
            }

            in GoalKinds ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    goalId = valid("subject_id", GoalId.of(subject))
                )

            in ScheduleKinds ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    scheduleId = valid("subject_id", ScheduleId.of(subject))
                )

            in InvestmentKinds ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    investmentId = valid("subject_id", InvestmentId.of(subject))
                )

            OperationKind.SetUsdRate ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    usdRateId = valid("subject_id", UsdRateId.of(subject))
                )

            in IncomeKinds ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    incomeId = valid("subject_id", IncomeId.of(subject))
                )

            else ->
                StoredReceipt(
                    receipt,
                    inputDigest,
                    categoryId = valid("subject_id", CategoryId.of(subject))
                )
        }
    }
