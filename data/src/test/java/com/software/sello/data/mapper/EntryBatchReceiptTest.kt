package com.software.sello.data.mapper

import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A receipt of several entries keeps the new records in its subject, in line order, so a
 * replay can answer with them. Anything else stored there is a damaged receipt.
 */
class EntryBatchReceiptTest {
    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun receipt(kind: OperationKind, subject: String?) = OperationReceiptEntity(
        operationId = id(9),
        kind = kind.key,
        inputDigest = "a".repeat(64),
        generation = 1,
        revision = 3,
        subjectId = subject,
        committedAt = 1_000
    )

    private val damaged =
        Outcome.Failure(StorageFailure.Integrity("operation_receipt", id(9), "subject_id"))

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    @Test
    fun theKindsAreStoredUnderTheirOwnKeys() {
        assertEquals("expense.create_batch", OperationKind.CreateExpenses.key)
        assertEquals("income.create_batch", OperationKind.CreateIncomes.key)
    }

    @Test
    fun aReceiptOfExpensesReadsBackItsExpensesInLineOrder() {
        val stored = receipt(OperationKind.CreateExpenses, "${id(102)},${id(100)},${id(101)}")
            .toStored().value()

        assertEquals(
            listOf(102, 100, 101).map { ExpenseId.of(id(it)).value() },
            stored.expenseIds
        )
        assertEquals(emptyList<IncomeId>(), stored.incomeIds)
        assertNull(stored.expenseId)
        assertNull(stored.categoryId)
    }

    @Test
    fun aReceiptOfIncomesReadsBackItsIncomes() {
        val stored = receipt(OperationKind.CreateIncomes, id(100)).toStored().value()

        assertEquals(listOf(IncomeId.of(id(100)).value()), stored.incomeIds)
        assertEquals(emptyList<ExpenseId>(), stored.expenseIds)
        assertNull(stored.incomeId)
    }

    @Test
    fun thirtyRecordsAreHeldAndThirtyOneAreDamage() {
        val thirty = (100 until 130).joinToString(",") { id(it) }

        assertEquals(
            30,
            receipt(OperationKind.CreateExpenses, thirty).toStored().value().expenseIds.size
        )
        assertEquals(
            damaged,
            receipt(OperationKind.CreateExpenses, "$thirty,${id(130)}").toStored()
        )
    }

    @Test
    fun aMissingMalformedOrRepeatedRecordIsDamage() {
        val subjects = listOf(
            null,
            "",
            ",",
            "${id(100)},",
            "${id(100)}, ${id(101)}",
            "${id(100)};${id(101)}",
            "100",
            "${id(100)},${id(100)}"
        )

        for (subject in subjects) {
            for (kind in listOf(OperationKind.CreateExpenses, OperationKind.CreateIncomes)) {
                assertEquals("$kind $subject", damaged, receipt(kind, subject).toStored())
            }
        }
    }
}
