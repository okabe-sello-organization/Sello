package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.CarriedValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentAmountError
import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateError
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.valueOrFail
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approved examples of docs/testing/investment-examples.md, by their IDs. Every
 * expected figure is copied from that document, where it was worked out by hand.
 */
class InvestmentPolicyTest {
    private companion object {
        val at: Instant = Instant.parse("2026-10-25T12:00:00Z")
        var ids = 0

        fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

        fun d(text: String): LocalDate = LocalDate.parse(text)

        fun cop(pesos: Long) = Money.cop(pesos)

        fun usd(cents: Long) = Money(cents, Currency.USD)

        fun rate(text: String, date: String, sequence: Long = 1) = UsdRateEntry(
            UsdRateId.of(id()).valueOrFail(),
            UsdRate.of(BigDecimal(text)).valueOrFail(),
            d(date),
            sequence,
            at
        )
    }

    /** An investment's history written as it was recorded: each call is the next movement. */
    private class Recorder(name: String, private val currency: Currency = Currency.COP) {
        private val investment = Investment(
            InvestmentId.of(id()).valueOrFail(),
            InvestmentName.of(name).valueOrFail(),
            null,
            IconKey.of("insights").valueOrFail(),
            currency,
            1,
            at,
            at
        )
        val movements = mutableListOf<InvestmentMovement>()
        private var next = 1L

        val history get() = InvestmentHistory(investment, movements.toList())

        fun aporte(units: Long, date: String) =
            add(InvestmentMovementKind.Contribution, units, date)

        fun retiro(units: Long, date: String) = add(InvestmentMovementKind.Withdrawal, units, date)

        fun dividendo(units: Long, date: String) = add(InvestmentMovementKind.Dividend, units, date)

        fun valor(units: Long, date: String) = add(InvestmentMovementKind.Valuation, units, date)

        fun movement(kind: InvestmentMovementKind, units: Long, date: String) = InvestmentMovement(
            InvestmentMovementId.of(id()).valueOrFail(),
            kind,
            amount(kind, units),
            d(date),
            next++,
            null,
            1,
            at,
            at
        )

        private fun add(kind: InvestmentMovementKind, units: Long, date: String) =
            movement(kind, units, date).also { movements += it }

        fun amount(kind: InvestmentMovementKind, units: Long): InvestmentAmount {
            val money = Money(units, currency)
            return if (kind == InvestmentMovementKind.Valuation) {
                InvestmentAmount.ofValue(money).valueOrFail()
            } else {
                InvestmentAmount.of(money).valueOrFail()
            }
        }
    }

    private fun figures(recorder: Recorder): InvestmentFigures =
        InvestmentPolicy.figures(recorder.history).valueOrFail()

    /** The history of [recorder] worked out with [movement] added or corrected. */
    private fun withMovement(recorder: Recorder, movement: InvestmentMovement) =
        InvestmentPolicy.figures(InvestmentPolicy.with(recorder.history, movement))

    /** The history of [recorder] worked out without [movement]. */
    private fun withoutMovement(recorder: Recorder, movement: InvestmentMovement) =
        InvestmentPolicy.figures(InvestmentPolicy.without(recorder.history, movement))

    private fun gana(tenths: Long) = InvestmentReturn(GainDirection.Gain, tenths)

    private fun pierde(tenths: Long) = InvestmentReturn(GainDirection.Loss, tenths)

    // The four investments of the v2 page (IN02–IN05).
    private fun parqueadero() = Recorder("Parqueadero").apply {
        aporte(18_000_000, "2025-03-10")
        valor(18_000_000, "2025-03-10")
        dividendo(175_000, "2025-12-01")
        dividendo(175_000, "2026-06-01")
        valor(19_000_000, "2026-06-12")
    }

    private fun cdt() = Recorder("CDT Banco").apply {
        aporte(10_000_000, "2025-10-01")
        valor(10_000_000, "2025-10-01")
        valor(10_746_000, "2026-09-30")
    }

    private fun fondo() = Recorder("Fondo de acciones").apply {
        aporte(3_000_000, "2025-05-03")
        valor(3_000_000, "2025-05-03")
        aporte(3_000_000, "2025-11-03")
        retiro(500_000, "2026-08-03")
        valor(5_242_000, "2026-10-14")
    }

    private fun ecopetrol() = Recorder("Ecopetrol").apply {
        aporte(2_000_000, "2025-03-15")
        valor(2_000_000, "2025-03-15")
        aporte(1_000_000, "2025-07-15")
        dividendo(89_000, "2026-03-15")
        dividendo(89_000, "2026-09-15")
        valor(3_050_000, "2026-10-09")
    }

    @Test
    fun in01_theBriefsExampleGainsTenPercent() {
        val brief = Recorder("Brief").apply {
            aporte(100_000, "2026-10-01")
            valor(110_000, "2026-10-20")
        }
        val result = figures(brief)
        assertEquals(cop(10_000), result.gain)
        assertEquals(gana(100), result.`return`)
    }

    @Test
    fun in02to05_theFourInvestmentsOfThePage() {
        figures(parqueadero()).run {
            assertEquals(cop(1_350_000), gain)
            assertEquals(gana(75), `return`)
            assertEquals(CarriedValue(cop(19_000_000), d("2026-06-12")), value)
            assertEquals(cop(350_000), dividends)
        }
        figures(cdt()).run {
            assertEquals(cop(746_000), gain)
            assertEquals("7,46 % rounds half up to 7,5 %", gana(75), `return`)
        }
        figures(fondo()).run {
            assertEquals(cop(-258_000), gain)
            assertEquals(pierde(43), `return`)
            assertEquals(cop(6_000_000), contributed)
            assertEquals(cop(500_000), withdrawn)
            assertEquals(YearMonth.of(2025, 5), since)
        }
        figures(ecopetrol()).run {
            assertEquals(cop(178_000), dividends)
            assertEquals(cop(228_000), gain)
            assertEquals(gana(76), `return`)
        }
    }

    @Test
    fun in06_in07_theOverviewAddsUpTheInvestmentsWithAValue() {
        val four = listOf(parqueadero(), cdt(), fondo(), ecopetrol())
            .map { it.history to figures(it) }
        val overview = InvestmentPolicy.overview(four, null).valueOrFail()
        val total = overview.total!!
        assertEquals(cop(38_038_000), total.value)
        assertEquals(cop(500_000), total.withdrawn)
        assertEquals(cop(528_000), total.dividends)
        assertEquals(cop(37_000_000), total.contributed)
        assertEquals(cop(2_066_000), total.gain)
        assertEquals(gana(56), total.`return`)
        assertEquals(d("2026-06-12"), total.valuedFrom)
        assertEquals(d("2026-10-14"), total.valuedTo)
        assertEquals(YearMonth.of(2025, 3), total.since)
        assertNull(overview.rate)
        assertEquals(
            listOf("Parqueadero", "CDT Banco", "Fondo de acciones", "Ecopetrol"),
            overview.items.map { it.history.investment.name.value }
        )

        val two = listOf(fondo(), ecopetrol()).map { it.history to figures(it) }
        InvestmentPolicy.overview(two, null).valueOrFail().total!!.run {
            assertEquals(cop(8_292_000), value)
            assertEquals(cop(-30_000), gain)
            assertEquals(pierde(3), `return`)
        }
    }

    private fun in08() = Recorder("IN08").apply {
        aporte(4_000_000, "2026-09-01")
        valor(4_000_000, "2026-09-01")
        valor(5_000_000, "2026-10-01")
        aporte(1_000_000, "2026-10-10")
    }

    @Test
    fun in08_anAporteCarriesTheValueForwardKeepingItsDate() {
        val result = figures(in08())
        assertEquals(CarriedValue(cop(6_000_000), d("2026-10-01")), result.value)
        assertEquals(cop(5_000_000), result.contributed)
        assertEquals(cop(1_000_000), result.gain)
        assertEquals(gana(200), result.`return`)
    }

    @Test
    fun in09_aWithdrawalIsNotALoss() {
        val result = figures(in08().apply { retiro(2_000_000, "2026-10-12") })
        assertEquals(CarriedValue(cop(4_000_000), d("2026-10-01")), result.value)
        assertEquals(cop(2_000_000), result.withdrawn)
        assertEquals(cop(1_000_000), result.gain)
        assertEquals(gana(200), result.`return`)
    }

    @Test
    fun in10_aWithdrawalIsLimitedToTheValueOnItsDate() {
        val in09 = in08().apply { retiro(2_000_000, "2026-10-12") }
        assertEquals(
            cop(4_000_000),
            InvestmentPolicy.available(in09.history, d("2026-10-12")).valueOrFail()
        )

        val over = in09.movement(InvestmentMovementKind.Withdrawal, 4_000_001, "2026-10-13")
        val refused = withMovement(in09, over).errorOrFail()
        assertEquals(InvestmentHistoryError.WithdrawalAboveValue(over.id, cop(4_000_000)), refused)

        val all = in09.movement(InvestmentMovementKind.Withdrawal, 4_000_000, "2026-10-13")
        val emptied = withMovement(in09, all).valueOrFail()
        assertEquals(cop(0), emptied.value!!.amount)
    }

    private fun in11() = in08().apply {
        retiro(2_000_000, "2026-10-12")
        valor(3_800_000, "2026-10-14")
    }

    @Test
    fun in11_aRecordedValueReplacesTheCarriedOne() {
        val result = figures(in11())
        assertEquals(CarriedValue(cop(3_800_000), d("2026-10-14")), result.value)
        assertEquals(cop(800_000), result.gain)
        assertEquals(gana(160), result.`return`)
    }

    @Test
    fun in12_anAporteDatedBeforeTheLastValueIsAlreadyInIt() {
        val result = figures(in11().apply { aporte(300_000, "2026-10-05") })
        assertEquals(CarriedValue(cop(3_800_000), d("2026-10-14")), result.value)
        assertEquals(cop(5_300_000), result.contributed)
        assertEquals(cop(500_000), result.gain)
        assertEquals(gana(94), result.`return`)
    }

    @Test
    fun in13_onTheSameDateTheRecordingOrderDecides() {
        val valueFirst = Recorder("A").apply {
            aporte(3_000_000, "2026-10-01")
            valor(3_000_000, "2026-10-01")
            valor(4_000_000, "2026-10-20")
            aporte(500_000, "2026-10-20")
        }
        assertEquals(cop(4_500_000), figures(valueFirst).value!!.amount)
        val aporteFirst = Recorder("B").apply {
            aporte(3_000_000, "2026-10-01")
            valor(3_000_000, "2026-10-01")
            aporte(500_000, "2026-10-20")
            valor(4_000_000, "2026-10-20")
        }
        assertEquals(cop(4_000_000), figures(aporteFirst).value!!.amount)
    }

    @Test
    fun in14_aDividendCountsInTheGainNotTheValue() {
        val result = figures(in11().apply { dividendo(50_000, "2026-10-20") })
        assertEquals(cop(3_800_000), result.value!!.amount)
        assertEquals(cop(50_000), result.dividends)
        assertEquals(cop(850_000), result.gain)
        assertEquals(gana(170), result.`return`)
    }

    @Test
    fun in15_in16_theSignFollowsTheGainNotTheRoundedReturn() {
        val even = Recorder("Even").apply {
            aporte(1_000_000, "2026-10-01")
            valor(1_000_000, "2026-10-01")
        }
        assertEquals(InvestmentReturn(GainDirection.Even, 0), figures(even).`return`)
        val tiny = Recorder("Tiny").apply {
            aporte(3_000_000, "2026-10-01")
            valor(2_999_999, "2026-10-02")
        }
        assertEquals(cop(-1), figures(tiny).gain)
        assertEquals(pierde(0), figures(tiny).`return`)
    }

    @Test
    fun in17_in35_anInvestmentWithoutAValueStaysOutOfTheTotal() {
        val cripto = Recorder("Cripto").apply {
            val first = aporte(2_000_000, "2026-08-01")
            valor(2_000_000, "2026-08-01").also { movements.remove(it) }
            assertTrue(movements.contains(first))
        }
        val alone = figures(cripto)
        assertNull(alone.value)
        assertNull(alone.gain)
        assertNull(alone.`return`)
        assertEquals(cop(2_000_000), alone.contributed)

        val four = listOf(parqueadero(), cdt(), fondo(), ecopetrol(), cripto)
            .map { it.history to figures(it) }
        val overview = InvestmentPolicy.overview(four, null).valueOrFail()
        assertEquals(cop(38_038_000), overview.total!!.value)
        assertEquals(cop(37_000_000), overview.total!!.contributed)
        assertEquals("Cripto", overview.items.last().history.investment.name.value)
        assertNull(overview.items.last().valueInPesos)

        val none = InvestmentPolicy.overview(listOf(cripto.history to alone), null).valueOrFail()
        assertNull(none.total)
        assertEquals(1, none.items.size)
    }

    @Test
    fun in18_aWithdrawalWaitsForAValue() {
        val cripto = Recorder("Cripto").apply { aporte(2_000_000, "2026-08-01") }
        assertNull(InvestmentPolicy.available(cripto.history, d("2026-10-01")).valueOrFail())
        val retiro = cripto.movement(InvestmentMovementKind.Withdrawal, 1_000, "2026-10-01")
        assertEquals(
            InvestmentHistoryError.WithdrawalWithoutValue(retiro.id),
            withMovement(cripto, retiro).errorOrFail()
        )
    }

    @Test
    fun in19_in20_aCorrectionThatBreaksALaterWithdrawalIsRefused() {
        val in19 = in08()
        val tenth = in19.movements.last()
        val retiro = in19.retiro(6_000_000, "2026-10-12")
        assertEquals(cop(0), figures(in19).value!!.amount)
        assertEquals(
            InvestmentHistoryError.WithdrawalAboveValue(retiro.id, cop(5_000_000)),
            withoutMovement(in19, tenth).errorOrFail()
        )

        val in09 = in08()
        val twelfth = in09.retiro(2_000_000, "2026-10-12")
        val edited =
            twelfth.copy(amount = in09.amount(InvestmentMovementKind.Withdrawal, 7_000_000))
        assertEquals(
            InvestmentHistoryError.WithdrawalAboveValue(twelfth.id, cop(6_000_000)),
            withMovement(in09, edited).errorOrFail()
        )
    }

    @Test
    fun in21_deletingAWithdrawalBringsTheValueBack() {
        val in09 = in08()
        val retiro = in09.retiro(2_000_000, "2026-10-12")
        val result = withoutMovement(in09, retiro).valueOrFail()
        assertEquals(cop(6_000_000), result.value!!.amount)
        assertEquals(cop(0), result.withdrawn)
        assertEquals(cop(1_000_000), result.gain)
    }

    @Test
    fun in22_aHistoryWithoutAporteIsRefused() {
        val single = Recorder("Single").apply {
            aporte(1_000_000, "2026-10-01")
            valor(1_000_000, "2026-10-01")
        }
        val onlyAporte = single.movements.first()
        assertEquals(
            InvestmentHistoryError.NoContribution,
            withoutMovement(single, onlyAporte).errorOrFail()
        )
    }

    @Test
    fun in24_noMovementInTheFuture() {
        assertTrue(InvestmentPolicy.acceptsDate(d("2026-10-25"), d("2026-10-25")))
        assertFalse(InvestmentPolicy.acceptsDate(d("2026-10-26"), d("2026-10-25")))
    }

    @Test
    fun in25_onlyAnAporteMayComeBeforeTheFirstAporte() {
        val fondo = Recorder("Fondo").apply {
            aporte(3_000_000, "2025-05-03")
            valor(3_000_000, "2025-05-03")
        }
        val early = fondo.movement(InvestmentMovementKind.Valuation, 3_000_000, "2025-05-01")
        assertEquals(
            InvestmentHistoryError.BeforeFirstContribution(early.id, d("2025-05-03")),
            withMovement(fondo, early).errorOrFail()
        )
        val sameMonth = fondo.movement(InvestmentMovementKind.Contribution, 100_000, "2025-05-01")
        assertEquals(
            YearMonth.of(2025, 5),
            withMovement(fondo, sameMonth).valueOrFail().since
        )
        val earlier = fondo.movement(InvestmentMovementKind.Contribution, 100_000, "2025-04-01")
        assertEquals(
            YearMonth.of(2025, 4),
            withMovement(fondo, earlier).valueOrFail().since
        )
    }

    @Test
    fun in26_in34_amountBounds() {
        assertEquals(
            InvestmentAmountError.AboveMaximum,
            InvestmentAmount.of(cop(1_000_000_000_000)).errorOrFail()
        )
        InvestmentAmount.of(cop(999_999_999_999)).valueOrFail()
        InvestmentAmount.of(usd(1)).valueOrFail()
        InvestmentAmount.of(usd(999_999_999_999)).valueOrFail()
        assertEquals(
            InvestmentAmountError.AboveMaximum,
            InvestmentAmount.of(usd(1_000_000_000_000)).errorOrFail()
        )
        assertEquals(InvestmentAmountError.BelowMinimum, InvestmentAmount.of(cop(0)).errorOrFail())
        InvestmentAmount.ofValue(cop(0)).valueOrFail()
        assertEquals(
            InvestmentAmountError.UnsupportedCurrency(Currency.EUR),
            InvestmentAmount.of(Money(100, Currency.EUR)).errorOrFail()
        )
    }

    @Test
    fun aMovementInAnotherCurrencyIsRefused() {
        val dollars = Recorder("Dólares", Currency.USD).apply {
            aporte(100_000, "2026-10-16")
            valor(100_000, "2026-10-16")
        }
        val pesos = Recorder("Pesos").movement(InvestmentMovementKind.Dividend, 5_000, "2026-10-17")
        assertEquals(
            InvestmentHistoryError.CurrencyMismatch(pesos.id),
            withMovement(dollars, pesos).errorOrFail()
        )
    }

    private fun acciones() = Recorder("Acciones EE. UU.", Currency.USD).apply {
        aporte(100_000, "2026-10-16")
        valor(105_050, "2026-10-16")
    }

    @Test
    fun in27_aUsdInvestmentStaysInDollars() {
        val result = figures(acciones())
        assertEquals(usd(5_050), result.gain)
        assertEquals("5,05 % rounds half up to 5,1 %", gana(51), result.`return`)
    }

    @Test
    fun in28_theOverviewConvertsEachUsdSumOnceAtTheCurrentRate() {
        val all = listOf(parqueadero(), cdt(), fondo(), ecopetrol(), acciones())
            .map { it.history to figures(it) }
        val current = rate("4150.25", "2026-10-20")
        val overview = InvestmentPolicy.overview(all, current).valueOrFail()
        val total = overview.total!!
        assertEquals(cop(42_397_838), total.value)
        assertEquals(cop(41_150_250), total.contributed)
        assertEquals(cop(500_000), total.withdrawn)
        assertEquals(cop(528_000), total.dividends)
        assertEquals(cop(2_275_588), total.gain)
        assertEquals(gana(55), total.`return`)
        assertEquals(current, overview.rate)
        val usdItem = overview.items.single { it.history.investment.currency == Currency.USD }
        assertEquals(cop(4_359_838), usdItem.valueInPesos)
        assertEquals(
            "by value in pesos: 4.359.838 comes before Ecopetrol's 3.050.000",
            listOf(
                "Parqueadero",
                "CDT Banco",
                "Fondo de acciones",
                "Acciones EE. UU.",
                "Ecopetrol"
            ),
            overview.items.map { it.history.investment.name.value }
        )
        assertEquals(
            cop(4_359_838),
            InvestmentPolicy.toPesos(usd(105_050), current.rate).valueOrFail()
        )
    }

    @Test
    fun in28_aUsdValueWithoutARateIsNeverGuessed() {
        val all = listOf(cdt(), acciones()).map { it.history to figures(it) }
        assertEquals(PortfolioError.MissingRate, InvestmentPolicy.overview(all, null).errorOrFail())
    }

    @Test
    fun in29_in30_theRateChangesTheTotalOnly() {
        val all = listOf(acciones()).map { it.history to figures(it) }
        InvestmentPolicy.overview(all, rate("4000", "2026-10-24")).valueOrFail().total!!.run {
            assertEquals(cop(4_202_000), value)
            assertEquals(cop(4_000_000), contributed)
            assertEquals(cop(202_000), gain)
        }
        assertEquals(gana(51), figures(acciones()).`return`)
        InvestmentPolicy.overview(all, rate("4010", "2026-10-24")).valueOrFail().total!!.run {
            assertEquals(cop(4_212_505), value)
            assertEquals(cop(4_010_000), contributed)
        }
    }

    @Test
    fun in30_in31_theCurrentRateIsTheLatestDateThenTheLastEntered() {
        val first = rate("4000", "2026-10-24", sequence = 1)
        val second = rate("4010", "2026-10-24", sequence = 2)
        assertEquals(second, InvestmentPolicy.currentRate(listOf(second, first)))
        val older = rate("3900", "2026-10-22", sequence = 3)
        assertEquals(second, InvestmentPolicy.currentRate(listOf(first, second, older)))
        assertNull(InvestmentPolicy.currentRate(emptyList()))
    }

    @Test
    fun in32_rateBoundsAndPrecision() {
        assertEquals(UsdRateError.OutOfBounds, UsdRate.of(BigDecimal("0.99")).errorOrFail())
        assertEquals(UsdRateError.OutOfBounds, UsdRate.of(BigDecimal("100000.01")).errorOrFail())
        assertEquals(UsdRateError.TooPrecise, UsdRate.of(BigDecimal("4150.255")).errorOrFail())
        assertEquals(100L, UsdRate.of(BigDecimal("1.00")).valueOrFail().hundredths)
        assertEquals(10_000_000L, UsdRate.of(BigDecimal("100000")).valueOrFail().hundredths)
        assertEquals(415_025L, UsdRate.of(BigDecimal("4150.250")).valueOrFail().hundredths)
        assertEquals(BigDecimal("4150.25"), UsdRate.ofHundredths(415_025).valueOrFail().pesos)
    }

    @Test
    fun rule16_theLargestUsdSumAtTheHighestRateFits() {
        val top = UsdRate.of(BigDecimal("100000")).valueOrFail()
        assertEquals(
            cop(999_999_999_999_000),
            InvestmentPolicy.toPesos(usd(999_999_999_999), top).valueOrFail()
        )
    }

    @Test
    fun returnNeedsContributions() {
        assertNull(InvestmentPolicy.returnOf(cop(10), cop(0)))
        assertEquals(gana(100), InvestmentPolicy.returnOf(cop(10_000), cop(100_000)))
    }
}
