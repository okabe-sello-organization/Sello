package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentLineError
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentMovementKind.Withdrawal
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Movements of several investments on one receipt (SELLO-140): each investment's lines
 * are checked against its own history and its own earlier lines only, and every
 * investment at fault names its line, counted in the whole receipt. Every expected figure
 * is worked out by hand in the comment above it.
 */
class InvestmentLinesAcrossPolicyTest {
    private val at: Instant = Instant.parse("2026-10-25T12:00:00Z")
    private var ids = 0

    private fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

    /** An investment and its next recording position. */
    private inner class Account(name: String, private val currency: Currency) {
        val investment = Investment(
            InvestmentId.of(id()).valueOrFail(),
            InvestmentName.of(name).valueOrFail(),
            null,
            IconKey.of("insights").valueOrFail(),
            currency,
            1,
            at,
            at
        )
        private val recorded = mutableListOf<InvestmentMovement>()
        private var next = 0L

        private fun movement(kind: InvestmentMovementKind, units: Long, date: String) =
            InvestmentMovement(
                InvestmentMovementId.of(id()).valueOrFail(),
                kind,
                if (kind == Valuation) {
                    InvestmentAmount.ofValue(Money(units, currency)).valueOrFail()
                } else {
                    InvestmentAmount.of(Money(units, currency)).valueOrFail()
                },
                LocalDate.parse(date),
                next++,
                null,
                1,
                at,
                at
            )

        fun record(kind: InvestmentMovementKind, units: Long, date: String) {
            recorded += movement(kind, units, date)
        }

        fun history() = InvestmentHistory(investment, recorded.toList())

        /** A new line of this investment, paired with its history as the receipt gives it. */
        fun line(kind: InvestmentMovementKind, units: Long, date: String) =
            history() to movement(kind, units, date)
    }

    /** Aporte 1.000.000 COP on 1 oct, its value the same. */
    private val fondo = Account("Fondo", Currency.COP).apply {
        record(Contribution, 1_000_000, "2026-10-01")
        record(Valuation, 1_000_000, "2026-10-01")
    }

    /** Aporte 2.000.000 COP on 1 sep, its value the same. */
    private val cdt = Account("CDT", Currency.COP).apply {
        record(Contribution, 2_000_000, "2026-09-01")
        record(Valuation, 2_000_000, "2026-09-01")
    }

    /** Aporte USD 1.000,00 on 20 oct, its value the same. */
    private val acciones = Account("Acciones", Currency.USD).apply {
        record(Contribution, 100_000, "2026-10-20")
        record(Valuation, 100_000, "2026-10-20")
    }

    @Test
    fun linesOfACopAndAUsdInvestmentEachWorkOutInTheirOwnCurrency() {
        val lines = listOf(
            fondo.line(Contribution, 200_000, "2026-10-05"),
            acciones.line(Contribution, 25_050, "2026-10-21"),
            fondo.line(Valuation, 1_300_000, "2026-10-22"),
            acciones.line(Dividend, 199, "2026-10-22")
        )

        assertEquals(Outcome.Success(Unit), InvestmentPolicy.withLinesAcross(lines))
    }

    @Test
    fun aRetiroIsCheckedAgainstItsOwnInvestmentsValueNotAnotherOnTheReceipt() {
        // CDT's value of 5.000.000 is not Fondo's: Fondo has 1.000.000 by 10 oct.
        val lines = listOf(
            cdt.line(Valuation, 5_000_000, "2026-10-05"),
            fondo.line(Withdrawal, 1_500_000, "2026-10-10")
        )

        val error = InvestmentPolicy.withLinesAcross(lines).errorOrFail().single()

        assertEquals(1, error.position)
        assertEquals(
            InvestmentHistoryError.WithdrawalAboveValue(lines[1].second.id, Money.cop(1_000_000)),
            error.error
        )
    }

    @Test
    fun aRetiroMayRelyOnAnEarlierLineOfItsOwnInvestment() {
        // Fondo's own value of 2.000.000 on 6 oct replays before its retiro of 10 oct.
        val lines = listOf(
            cdt.line(Valuation, 5_000_000, "2026-10-05"),
            fondo.line(Withdrawal, 1_500_000, "2026-10-10"),
            fondo.line(Valuation, 2_000_000, "2026-10-06")
        )

        assertEquals(Outcome.Success(Unit), InvestmentPolicy.withLinesAcross(lines))
    }

    @Test
    fun aLineBeforeItsOwnInvestmentsFirstAporteIsRefusedWhateverTheOthersDates() {
        // CDT's first aporte is 1 sep; Fondo's is 1 oct, so a Fondo dividend of 15 sep is early.
        val lines = listOf(
            cdt.line(Contribution, 100_000, "2026-10-02"),
            fondo.line(Dividend, 5_000, "2026-09-15")
        )

        assertEquals(
            listOf(
                InvestmentLineError(
                    1,
                    InvestmentHistoryError.BeforeFirstContribution(
                        lines[1].second.id,
                        LocalDate.parse("2026-10-01")
                    )
                )
            ),
            InvestmentPolicy.withLinesAcross(lines).errorOrFail()
        )
    }

    @Test
    fun everyInvestmentAtFaultIsNamedWithItsPositionInTheWholeReceipt() {
        val lines = listOf(
            cdt.line(Contribution, 100_000, "2026-10-02"),
            fondo.line(Withdrawal, 1_500_000, "2026-10-10"),
            acciones.line(Contribution, 1_000, "2026-10-21"),
            cdt.line(Dividend, 5_000, "2026-08-15")
        )

        val errors = InvestmentPolicy.withLinesAcross(lines).errorOrFail()

        // In the order the investments are first named: CDT (its line 3), then Fondo (line 1).
        assertEquals(listOf(3, 1), errors.map { it.position })
        assertEquals(
            InvestmentHistoryError.BeforeFirstContribution(
                lines[3].second.id,
                LocalDate.parse("2026-09-01")
            ),
            errors[0].error
        )
        assertEquals(
            InvestmentHistoryError.WithdrawalAboveValue(lines[1].second.id, Money.cop(1_000_000)),
            errors[1].error
        )
    }
}
