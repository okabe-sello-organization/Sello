package com.software.sello.data.mapper

import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.StorageFailure
import java.time.Instant

/**
 * A stored receipt, decoded. The subject is the record the operation changed: an
 * expense for the four expense kinds, an income for the four income kinds, a goal for
 * the goal kinds, a schedule for the schedule kinds, none for a restore or reset of the
 * whole history, a category for every other kind.
 */
internal class StoredReceipt(
    val receipt: OperationReceipt,
    val inputDigest: String,
    val expenseId: ExpenseId? = null,
    val categoryId: CategoryId? = null,
    val incomeId: IncomeId? = null,
    val goalId: GoalId? = null,
    val scheduleId: ScheduleId? = null
)

private val sha256Hex = Regex("[0-9a-f]{64}")

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

            OperationKind.RestoreHistory, OperationKind.ResetHistory, OperationKind.CatchUpSchedules -> {
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
