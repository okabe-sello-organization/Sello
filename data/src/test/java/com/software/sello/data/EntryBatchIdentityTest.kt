package com.software.sello.data

import com.software.sello.data.repository.correctionIdentity
import com.software.sello.data.repository.inputDigest
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.CreateIncomes
import com.software.sello.domain.port.ExpenseLine
import com.software.sello.domain.port.IncomeLine
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A receipt of several entries replays only with the very same input: every line, in its
 * order, and nothing else. Any difference is a different identity, so it is a conflict.
 */
class EntryBatchIdentityTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).value()

    private val operation = OperationId.of(id(9)).value()
    private val mercado = CategoryId.of(id(1)).value()
    private val salud = CategoryId.of(id(2)).value()
    private val day = LocalDate.parse("2027-01-10")

    private val pan = ExpenseLine(mercado, pesos(8_000), Note.of("Pan").value())
    private val droga = ExpenseLine(salud, pesos(30_000), null)
    private val expenses = CreateExpenses(operation, 4, day, listOf(pan, droga))

    private val sueldo = IncomeLine(IncomeSource.Salary, pesos(3_000_000), null)
    private val venta = IncomeLine(
        IncomeSource.Other(SourceName.of("Venta").value()),
        pesos(50_000),
        Note.of("Bici").value()
    )
    private val incomes = CreateIncomes(operation, 4, day, listOf(sueldo, venta))

    @Test
    fun theSameReceiptHasTheSameIdentity() {
        assertEquals(inputDigest(expenses), inputDigest(expenses.copy()))
        assertEquals(inputDigest(incomes), inputDigest(incomes.copy()))
        // The operation is not part of it: what names the attempt is not what it asks.
        assertEquals(
            inputDigest(expenses),
            inputDigest(expenses.copy(operationId = OperationId.of(id(10)).value()))
        )
    }

    @Test
    fun everyDifferenceInAReceiptOfExpensesIsADifferentIdentity() {
        val variants = listOf(
            expenses.copy(generation = 5),
            expenses.copy(date = day.plusDays(1)),
            expenses.copy(lines = listOf(droga, pan)),
            expenses.copy(lines = listOf(pan)),
            expenses.copy(lines = listOf(pan, droga, droga)),
            expenses.copy(lines = listOf(pan.copy(categoryId = salud), droga)),
            expenses.copy(lines = listOf(pan.copy(amount = pesos(8_001)), droga)),
            expenses.copy(lines = listOf(pan.copy(note = null), droga)),
            expenses.copy(lines = listOf(pan.copy(note = Note.of("Pa").value()), droga)),
            expenses.copy(lines = listOf(pan, droga.copy(note = Note.of("Pan").value()))),
            expenses.copy(confirmation = CorrectionConfirmation(17, inputDigest(expenses)))
        )

        val digests = (listOf(expenses) + variants).map(::inputDigest)

        assertEquals(digests.size, digests.toSet().size)
    }

    @Test
    fun everyDifferenceInAReceiptOfIncomesIsADifferentIdentity() {
        val variants = listOf(
            incomes.copy(generation = 5),
            incomes.copy(date = day.plusDays(1)),
            incomes.copy(lines = listOf(venta, sueldo)),
            incomes.copy(lines = listOf(sueldo)),
            incomes.copy(lines = listOf(sueldo.copy(source = IncomeSource.Freelance), venta)),
            incomes.copy(lines = listOf(sueldo.copy(amount = pesos(3_000_001)), venta)),
            incomes.copy(lines = listOf(sueldo.copy(note = Note.of("Mayo").value()), venta)),
            incomes.copy(
                lines = listOf(
                    sueldo,
                    venta.copy(source = IncomeSource.Other(SourceName.of("Ventas").value()))
                )
            )
        )

        val digests = (listOf(incomes) + variants).map(::inputDigest)

        assertEquals(digests.size, digests.toSet().size)
    }

    @Test
    fun aReceiptOfOneLineIsNotTheSingleEntryOfTheSameLine() {
        val single = CreateExpense(operation, 4, mercado, pan.amount, day, pan.note)

        assertNotEquals(inputDigest(expenses.copy(lines = listOf(pan))), inputDigest(single))
    }

    @Test
    fun aConfirmationNamesTheReceiptWithoutItself() {
        val confirmed = expenses.copy(confirmation = CorrectionConfirmation(17, "x"))

        assertEquals(inputDigest(expenses), correctionIdentity(confirmed))
        assertEquals(correctionIdentity(expenses), correctionIdentity(confirmed))
    }
}
