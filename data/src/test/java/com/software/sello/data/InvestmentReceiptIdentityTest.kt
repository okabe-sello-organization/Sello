package com.software.sello.data

import com.software.sello.data.repository.inputDigest
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.InvestmentMovementLine
import com.software.sello.domain.port.RecordInvestmentMovements
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A receipt of movements across investments (SELLO-140) is the same input only with the
 * same lines, each for the same investment, in the same order, and the same version read
 * of each investment, whatever order those versions were given in.
 */
class InvestmentReceiptIdentityTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private val operation = OperationId.of(id(9)).value()
    private val fondo = InvestmentId.of(id(1)).value()
    private val cdt = InvestmentId.of(id(2)).value()
    private val day = LocalDate.parse("2026-10-20")

    private val aporte = InvestmentMovementLine(
        fondo,
        InvestmentMovementKind.Contribution,
        InvestmentAmount.of(Money.cop(20_000)).value(),
        day,
        null
    )
    private val dividendo = InvestmentMovementLine(
        cdt,
        InvestmentMovementKind.Dividend,
        InvestmentAmount.of(Money.cop(5_000)).value(),
        day,
        Note.of("Cupón").value()
    )

    private fun receipt(versions: Map<InvestmentId, Long>, vararg lines: InvestmentMovementLine) =
        inputDigest(RecordInvestmentMovements(operation, 4, versions, lines.toList()))

    @Test
    fun theSameVersionsInAnotherOrderAreTheSameReceipt() {
        assertEquals(
            receipt(linkedMapOf(fondo to 3L, cdt to 7L), aporte, dividendo),
            receipt(linkedMapOf(cdt to 7L, fondo to 3L), aporte, dividendo)
        )
    }

    @Test
    fun eachLinesInvestmentEachVersionAndTheLineOrderAreTheReceipt() {
        val versions = mapOf(fondo to 3L, cdt to 7L)

        assertEquals(
            6,
            setOf(
                receipt(versions, aporte, dividendo),
                receipt(versions, dividendo, aporte),
                receipt(versions, aporte, dividendo.copy(investmentId = fondo)),
                receipt(versions, aporte.copy(investmentId = cdt), dividendo),
                receipt(mapOf(fondo to 3L, cdt to 8L), aporte, dividendo),
                receipt(mapOf(fondo to 4L, cdt to 7L), aporte, dividendo)
            ).size
        )
    }
}
