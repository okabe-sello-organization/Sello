package com.software.sello.feature.investments

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
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
 * What Inversiones draws for the approved examples (docs/testing/investment-examples.md).
 * The figures come from the board, as in the app; the expected text is the examples'.
 */
class InvestmentsRenderModelTest {
    private val render = InvestmentsRenderModel(
        MoneyFormatter(object : MoneyLabels {
            override fun amount(currency: Currency, digits: String, isOne: Boolean) =
                if (currency == Currency.COP) "$digits pesos" else "$digits dólares"

            override fun negative(amount: String) = "menos $amount"

            override fun positive(amount: String) = "más $amount"
        })
    )
    private val today = LocalDate.of(2026, 10, 25)
    private val at = Instant.parse("2026-10-25T12:00:00Z")
    private var ids = 0

    private fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

    private fun investment(
        name: String,
        description: String?,
        movements: List<Triple<InvestmentMovementKind, Long, String>>,
        currency: Currency = Currency.COP
    ): InvestmentSummary {
        val investment = Investment(
            InvestmentId.of(id()).value(),
            InvestmentName.of(name).value(),
            InvestmentDescription.of(description).value(),
            IconKey.of("insights").value(),
            currency,
            1,
            at,
            at
        )
        val recorded = movements.mapIndexed { index, (kind, units, date) ->
            val money = Money(units, currency)
            InvestmentMovement(
                InvestmentMovementId.of(id()).value(),
                kind,
                if (kind == InvestmentMovementKind.Valuation) {
                    InvestmentAmount.ofValue(money).value()
                } else {
                    InvestmentAmount.of(money).value()
                },
                LocalDate.parse(date),
                index + 1L,
                null,
                1,
                at,
                at
            )
        }
        val history = InvestmentHistory(investment, recorded)
        return InvestmentSummary(history, InvestmentPolicy.figures(history).value())
    }

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val aporte = InvestmentMovementKind.Contribution
    private val retiro = InvestmentMovementKind.Withdrawal
    private val dividendo = InvestmentMovementKind.Dividend
    private val valor = InvestmentMovementKind.Valuation

    private fun parqueadero() = investment(
        "Parqueadero",
        "Finca raíz",
        listOf(
            Triple(aporte, 18_000_000, "2025-03-10"),
            Triple(valor, 18_000_000, "2025-03-10"),
            Triple(dividendo, 350_000, "2026-06-01"),
            Triple(valor, 19_000_000, "2026-06-12")
        )
    )

    private fun fondo() = investment(
        "Fondo de acciones",
        "Fondo colectivo",
        listOf(
            Triple(aporte, 6_000_000, "2025-05-03"),
            Triple(valor, 6_000_000, "2025-05-03"),
            Triple(retiro, 500_000, "2026-08-03"),
            Triple(valor, 5_242_000, "2026-10-14")
        )
    )

    private fun acciones() = investment(
        "Acciones EE. UU.",
        null,
        listOf(Triple(aporte, 100_000, "2026-10-16"), Triple(valor, 105_050, "2026-10-16")),
        Currency.USD
    )

    private fun cripto() = investment(
        "Cripto",
        "Criptomoneda",
        listOf(Triple(aporte, 2_000_000, "2026-08-01"))
    )

    private fun board(vararg investments: InvestmentSummary, rate: UsdRateEntry? = null) =
        InvestmentBoard(
            1,
            1,
            investments.toList(),
            UsdRateBook(rate, listOfNotNull(rate)),
            InvestmentPolicy.overview(investments.map { it.history to it.figures }, rate)
        )

    private val rate = UsdRateEntry(
        UsdRateId.of("00000000-0000-4000-8000-999999999999").value(),
        UsdRate.of(BigDecimal("4150.25")).value(),
        LocalDate.of(2026, 10, 20),
        1,
        at
    )

    private fun ready(board: InvestmentBoard) =
        render.overview(InvestmentBoardState.Ready(board), today) as InversionesState.Ready

    @Test
    fun theTotalLeadsWithTheValueTheReturnAndWhatProducedIt() {
        val state = ready(board(fondo(), parqueadero()))
        val total = state.total!!
        // IN02 + IN04: 19.000.000 + 5.242.000; aportes 18.000.000 + 6.000.000.
        assertEquals("24.242.000", total.value.digits)
        assertEquals("24.000.000", total.contributed.digits)
        assertEquals("500.000", total.withdrawn.digits)
        assertEquals("350.000", total.dividends.digits)
        assertEquals(MoneySign.Plus, total.gain.sign)
        assertEquals("1.092.000", total.gain.digits)
        assertEquals(InvestmentReturn(GainDirection.Gain, 46), total.`return`)
        assertEquals(YearMonth.of(2025, 3), total.since)
        assertEquals(LocalDate.of(2026, 6, 12), total.valuedFrom)
        assertEquals(LocalDate.of(2026, 10, 14), total.valuedTo)
        assertNull(state.rate)
        assertEquals(listOf("Parqueadero", "Fondo de acciones"), state.rows.map { it.name })
        assertEquals(InvestmentReturn(GainDirection.Loss, 43), state.rows[1].`return`)
        assertEquals(LocalDate.of(2026, 10, 14), state.rows[1].valuedOn)
    }

    @Test
    fun in17_anInvestmentWithoutAValueIsListedLastOutOfTheBar() {
        val state = ready(board(cripto(), parqueadero()))
        assertEquals("19.000.000", state.total!!.value.digits)
        val last = state.rows.last()
        assertEquals("Cripto", last.name)
        assertNull(last.value)
        assertNull(last.valuedOn)
        assertEquals("2.000.000", last.contributed.digits)
        assertEquals(0f, last.share)
        assertEquals(1f, state.rows.first().share)
    }

    @Test
    fun in28_dollarsStayInDollarsAndTheTotalNamesItsRate() {
        val state = ready(board(parqueadero(), acciones(), rate = rate))
        val usd = state.rows.single { it.dollars }
        assertEquals("USD", usd.value!!.symbol)
        assertEquals("1.050,50", usd.value!!.digits)
        assertEquals(InvestmentReturn(GainDirection.Gain, 51), usd.`return`)
        assertEquals(RateLineModel("4.150,25", LocalDate.of(2026, 10, 20)), state.rate)
        // 19.000.000 + 4.359.838 (IN28's conversion).
        assertEquals("23.359.838", state.total!!.value.digits)
    }

    @Test
    fun in35_withNoValueThereIsNoTotalButTheRowsShow() {
        val state = ready(board(cripto()))
        assertNull(state.total)
        assertFalse(state.missingRate)
        assertEquals(listOf("Cripto"), state.rows.map { it.name })
    }

    @Test
    fun aDollarValueWithoutARateIsNeverConvertedAtAGuess() {
        val state = ready(board(acciones()))
        assertNull(state.total)
        assertTrue(state.missingRate)
        assertEquals(1, state.rows.size)
    }

    @Test
    fun noInvestmentIsTheTrueEmptyState() {
        assertTrue(ready(board()).empty)
    }

    @Test
    fun aFailedReadIsNeverNoInvestments() {
        val failure = StorageFailure.Unavailable("test")
        assertEquals(
            InversionesState.Failed,
            render.overview(InvestmentBoardState.Failed(failure, null), today)
        )
        val stale = render.overview(
            InvestmentBoardState.Failed(failure, board(parqueadero())),
            today
        ) as InversionesState.Ready
        assertTrue(stale.stale)
        assertEquals("19.000.000", stale.total!!.value.digits)
        assertEquals(InversionesState.Loading, render.overview(InvestmentBoardState.Loading, today))
    }

    @Test
    fun in04_theDetailKeepsItsOwnFiguresAndListsMovementsNewestFirst() {
        val fondo = fondo()
        val id = fondo.history.investment.id.value
        val detail = render.detail(InvestmentBoardState.Ready(board(fondo)), id, today)
            as InvestmentDetailState.Ready
        assertEquals("5.242.000", detail.value!!.digits)
        assertEquals(LocalDate.of(2026, 10, 14), detail.valuedOn)
        assertEquals(MoneySign.Minus, detail.gain!!.sign)
        assertEquals("258.000", detail.gain!!.digits)
        assertEquals(InvestmentReturn(GainDirection.Loss, 43), detail.`return`)
        assertEquals(YearMonth.of(2025, 5), detail.since)
        // Newest first: the value, the retiro (minus), the first value, the aporte (plus).
        assertEquals(
            listOf(
                valor to MoneySign.None,
                retiro to MoneySign.Minus,
                valor to MoneySign.None,
                aporte to MoneySign.Plus
            ),
            detail.movements.map { it.kind to it.amount.sign }
        )
        assertEquals("500.000", detail.movements[1].amount.digits)
    }

    @Test
    fun theChartPlacesOnlyRecordedValuesAndWhatWasPutInAsSteps() {
        val fondo = fondo()
        val chart = (
            render.detail(
                InvestmentBoardState.Ready(board(fondo)),
                fondo.history.investment.id.value,
                today
            ) as InvestmentDetailState.Ready
            ).chart!!
        // Values 6.000.000 and 5.242.000; put in 6.000.000, then 5.500.000 after the retiro.
        assertEquals(listOf("6.000.000", "5.242.000"), chart.values.map { it.amount.digits })
        assertEquals(listOf("6.000.000", "5.500.000"), chart.putIn.map { it.amount.digits })
        assertEquals(1f, chart.values.first().y)
        assertEquals(0f, chart.values.first().x)
        assertEquals(5_500_000f / 6_000_000f, chart.putIn.last().y, 0.0001f)
        assertTrue(chart.values.last().x < 1f)
        assertEquals(YearMonth.of(2025, 5), chart.start)
        assertEquals(YearMonth.of(2026, 10), chart.end)
    }

    @Test
    fun aDeletedInvestmentIsGoneAndAFailedReadIsNeverGone() {
        assertEquals(
            InvestmentDetailState.Gone,
            render.detail(InvestmentBoardState.Ready(board(fondo())), "missing", today)
        )
        assertEquals(
            InvestmentDetailState.Failed,
            render.detail(
                InvestmentBoardState.Failed(StorageFailure.Unavailable("x"), null),
                "any",
                today
            )
        )
    }
}
