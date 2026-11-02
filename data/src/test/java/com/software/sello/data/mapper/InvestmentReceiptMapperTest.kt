package com.software.sello.data.mapper

import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A receipt of investment movements keeps its investments in its subject, in the order
 * its lines first name them (SELLO-140). One stored by SELLO-129 names its one investment
 * and still reads. Anything else stored there is a damaged receipt.
 */
class InvestmentReceiptMapperTest {
    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun receipt(subject: String?) = OperationReceiptEntity(
        operationId = id(9),
        kind = "investment.record_movements",
        inputDigest = "a".repeat(64),
        generation = 1,
        revision = 3,
        subjectId = subject,
        committedAt = 1_000
    )

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    @Test
    fun aReceiptAcrossInvestmentsReadsBackItsInvestmentsInOrder() {
        val stored = receipt("${id(103)},${id(100)}").toStored().value()

        assertEquals(OperationKind.RecordInvestmentMovements, stored.receipt.kind)
        assertEquals(
            listOf(103, 100).map { InvestmentId.of(id(it)).value() },
            stored.investmentIds
        )
        assertNull(stored.investmentId)
    }

    @Test
    fun aReceiptStoredBySello129ReadsItsOneInvestment() {
        val stored = receipt(id(100)).toStored().value()

        assertEquals(listOf(InvestmentId.of(id(100)).value()), stored.investmentIds)
        assertNull(stored.investmentId)
    }

    @Test
    fun aMissingMalformedOrRepeatedInvestmentIsDamage() {
        val damaged =
            Outcome.Failure(StorageFailure.Integrity("operation_receipt", id(9), "subject_id"))

        for (subject in listOf(null, "", ",", "${id(100)},", "100", "${id(100)},${id(100)}")) {
            assertEquals("$subject", damaged, receipt(subject).toStored())
        }
    }
}
