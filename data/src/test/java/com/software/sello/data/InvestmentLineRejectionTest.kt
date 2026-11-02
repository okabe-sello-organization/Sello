package com.software.sello.data

import com.software.sello.data.repository.toRejection
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentLineError
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.RefusedInvestmentLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** A batch's history refusal names its line when one is at fault, and only then (SELLO-129). */
class InvestmentLineRejectionTest {
    private val retiro =
        (InvestmentMovementId.of("00000000-0000-4000-8000-000000000001") as Outcome.Success).value

    @Test
    fun aRefusalWithNoLineAtFaultIsTheBareReason() {
        assertEquals(
            InvestmentRejection.Unrepresentable,
            InvestmentLineError(null, InvestmentHistoryError.Unrepresentable).toRejection()
        )
        assertEquals(
            InvestmentRejection.LastContribution,
            InvestmentLineError(null, InvestmentHistoryError.NoContribution).toRejection()
        )
    }

    @Test
    fun aRefusalAtALineNamesThatLine() {
        val error = InvestmentHistoryError.WithdrawalAboveValue(retiro, Money.cop(300_000))

        assertEquals(
            InvestmentRejection.LinesRefused(
                listOf(
                    RefusedInvestmentLine(
                        2,
                        InvestmentRejection.AboveValue(retiro, Money.cop(300_000))
                    )
                )
            ),
            InvestmentLineError(2, error).toRejection()
        )
    }

    @Test
    fun aReceiptAcrossInvestmentsNamesEveryLineAtFaultInLineOrder() {
        val other = (
            InvestmentMovementId.of("00000000-0000-4000-8000-000000000002") as Outcome.Success
            ).value
        val errors = listOf(
            InvestmentLineError(3, InvestmentHistoryError.WithdrawalWithoutValue(other)),
            InvestmentLineError(
                1,
                InvestmentHistoryError.WithdrawalAboveValue(retiro, Money.cop(5))
            )
        )

        assertEquals(
            InvestmentRejection.LinesRefused(
                listOf(
                    RefusedInvestmentLine(1, InvestmentRejection.AboveValue(retiro, Money.cop(5))),
                    RefusedInvestmentLine(3, InvestmentRejection.NoValue(other))
                )
            ),
            errors.toRejection()
        )
    }

    @Test
    fun aReceiptAcrossInvestmentsWithATotalThatDoesNotFitIsTheBareReason() {
        val errors = listOf(
            InvestmentLineError(1, InvestmentHistoryError.WithdrawalWithoutValue(retiro)),
            InvestmentLineError(null, InvestmentHistoryError.Unrepresentable)
        )

        assertEquals(InvestmentRejection.Unrepresentable, errors.toRejection())
    }
}
