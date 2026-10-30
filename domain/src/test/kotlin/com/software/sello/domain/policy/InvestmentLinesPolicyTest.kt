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
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Several movements of one investment checked together (SELLO-129): the lines replay
 * after the recorded history by date, ties in line order, and a refusal names the line
 * at fault. Every expected figure is worked out by hand in the comment above it.
 */
class InvestmentLinesPolicyTest {
    private val at: Instant = Instant.parse("2026-10-25T12:00:00Z")
    private var ids = 0

    private fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

    private val investment = Investment(
        InvestmentId.of(id()).valueOrFail(),
        InvestmentName.of("Fondo").valueOrFail(),
        null,
        IconKey.of("insights").valueOrFail(),
        Currency.COP,
        1,
        at,
        at
    )
    private val recorded = mutableListOf<InvestmentMovement>()
    private var next = 0L

    private fun movement(kind: InvestmentMovementKind, money: Money, date: String) =
        InvestmentMovement(
            InvestmentMovementId.of(id()).valueOrFail(),
            kind,
            if (kind == Valuation) {
                InvestmentAmount.ofValue(money).valueOrFail()
            } else {
                InvestmentAmount.of(money).valueOrFail()
            },
            LocalDate.parse(date),
            next++,
            null,
            1,
            at,
            at
        )

    /** A movement already recorded, at the next recording position. */
    private fun record(kind: InvestmentMovementKind, pesos: Long, date: String) =
        movement(kind, Money.cop(pesos), date).also { recorded += it }

    /** A new line, at the next recording position after everything made before it. */
    private fun line(kind: InvestmentMovementKind, pesos: Long, date: String) =
        movement(kind, Money.cop(pesos), date)

    private fun history() = InvestmentHistory(investment, recorded.toList())

    /** Aporte 1.000.000 on 1 oct, its value the same. */
    private fun opened() {
        record(Contribution, 1_000_000, "2026-10-01")
        record(Valuation, 1_000_000, "2026-10-01")
    }

    @Test
    fun aLineChecksAgainstTheLinesBeforeItInDateOrder() {
        opened()
        val valor = line(Valuation, 1_500_000, "2026-10-05")
        val retiro = line(Withdrawal, 1_200_000, "2026-10-10")

        val figures = InvestmentPolicy.withLines(history(), listOf(valor, retiro)).valueOrFail()

        // 1.500.000 − 1.200.000 = 300.000 left; gain 300.000 + 1.200.000 − 1.000.000.
        assertEquals(Money.cop(300_000), figures.value!!.amount)
        assertEquals(Money.cop(1_200_000), figures.withdrawn)
        assertEquals(Money.cop(500_000), figures.gain)
    }

    @Test
    fun aRetiroEnteredBeforeTheValueItNeedsStillReplaysAfterIt() {
        opened()
        val retiro = line(Withdrawal, 1_200_000, "2026-10-10")
        val valor = line(Valuation, 1_500_000, "2026-10-05")

        val figures = InvestmentPolicy.withLines(history(), listOf(retiro, valor)).valueOrFail()

        // The value of 5 oct replays first: 1.500.000 − 1.200.000 = 300.000.
        assertEquals(Money.cop(300_000), figures.value!!.amount)
    }

    @Test
    fun onTheSameDateLinesReplayInLineOrderAfterWhatIsRecorded() {
        opened()
        record(Valuation, 4_000_000, "2026-10-20")
        val aporte = line(Contribution, 500_000, "2026-10-20")

        val afterRecorded = InvestmentPolicy.withLines(history(), listOf(aporte)).valueOrFail()

        // The recorded value of 4.000.000 first, then the aporte: 4.500.000.
        assertEquals(Money.cop(4_500_000), afterRecorded.value!!.amount)

        val valueThenAporte = listOf(
            line(Valuation, 2_000_000, "2026-10-21"),
            line(Contribution, 500_000, "2026-10-21")
        )
        // 2.000.000 + 500.000 = 2.500.000.
        assertEquals(
            Money.cop(2_500_000),
            InvestmentPolicy.withLines(history(), valueThenAporte).valueOrFail().value!!.amount
        )
        val aporteThenValue = listOf(
            line(Contribution, 500_000, "2026-10-21"),
            line(Valuation, 2_000_000, "2026-10-21")
        )
        // The value is entered last and stands: 2.000.000.
        assertEquals(
            Money.cop(2_000_000),
            InvestmentPolicy.withLines(history(), aporteThenValue).valueOrFail().value!!.amount
        )
    }

    @Test
    fun aRetiroLineAboveTheValueIsTheLineNamed() {
        opened()
        val aporte = line(Contribution, 100, "2026-10-02")
        val retiro = line(Withdrawal, 2_000_000, "2026-10-05")

        val error = InvestmentPolicy.withLines(history(), listOf(aporte, retiro)).errorOrFail()

        // 1.000.000 + 100 = 1.000.100 available on 5 oct.
        assertEquals(
            InvestmentLineError(
                1,
                InvestmentHistoryError.WithdrawalAboveValue(retiro.id, Money.cop(1_000_100))
            ),
            error
        )
    }

    @Test
    fun aRetiroLineBeforeAnyValueIsTheLineNamed() {
        record(Contribution, 1_000_000, "2026-10-10")
        record(Valuation, 1_000_000, "2026-10-10")
        val aporte = line(Contribution, 500_000, "2026-10-01")
        val retiro = line(Withdrawal, 100_000, "2026-10-05")

        val error = InvestmentPolicy.withLines(history(), listOf(aporte, retiro)).errorOrFail()

        assertEquals(
            InvestmentLineError(1, InvestmentHistoryError.WithdrawalWithoutValue(retiro.id)),
            error
        )
    }

    @Test
    fun aLineBeforeTheFirstAporteIsTheLineNamed() {
        opened()
        val dividend = line(Dividend, 10_000, "2026-10-03")
        val valor = line(Valuation, 900_000, "2026-09-30")

        val error = InvestmentPolicy.withLines(history(), listOf(dividend, valor)).errorOrFail()

        assertEquals(
            InvestmentLineError(
                1,
                InvestmentHistoryError.BeforeFirstContribution(
                    valor.id,
                    LocalDate.parse("2026-10-01")
                )
            ),
            error
        )
    }

    @Test
    fun aLineInAnotherCurrencyIsTheLineNamed() {
        opened()
        val dollars = movement(Contribution, Money(5_000, Currency.USD), "2026-10-02")

        val error = InvestmentPolicy.withLines(history(), listOf(dollars)).errorOrFail()

        assertEquals(
            InvestmentLineError(0, InvestmentHistoryError.CurrencyMismatch(dollars.id)),
            error
        )
    }

    @Test
    fun aRecordedRetiroLeftShortNamesTheLastValueOrRetiroLineBeforeIt() {
        opened()
        val recordedRetiro = record(Withdrawal, 800_000, "2026-10-15")
        val lines = listOf(
            line(Contribution, 200_000, "2026-10-12"),
            line(Valuation, 500_000, "2026-10-05"),
            line(Withdrawal, 50_000, "2026-10-03"),
            line(Dividend, 10_000, "2026-10-14"),
            line(Valuation, 9_000_000, "2026-10-16")
        )

        val error = InvestmentPolicy.withLines(history(), lines).errorOrFail()

        // 1 oct value 1.000.000; 3 oct retiro 50.000 → 950.000; 5 oct value 500.000;
        // 12 oct aporte 200.000 → 700.000; 15 oct the recorded retiro of 800.000 is short.
        // The value of 5 oct is the last line before it that lowered the value; the
        // aporte and the dividend do not, and the value of 16 oct comes after it.
        assertEquals(
            InvestmentLineError(
                1,
                InvestmentHistoryError.WithdrawalAboveValue(recordedRetiro.id, Money.cop(700_000))
            ),
            error
        )
    }
}
