package com.software.sello.domain.policy

import com.software.sello.domain.model.CarriedValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PortfolioItem
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The five list orders over one hand-made portfolio. Figures are written here, not worked
 * out by the investment policy: only the order is under test.
 */
class InvestmentOrderPolicyTest {
    private var ids = 0

    private fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

    /**
     * One row: [pesos] is the value in pesos the overview would place it by, [tenths] the
     * signed return in tenths of a percent, [movedOn] each movement's date and recording
     * time ("2026-10-20@10:00").
     */
    private fun item(
        name: String,
        created: String,
        pesos: Long? = null,
        tenths: Long? = null,
        vararg movedOn: String,
        id: String = id()
    ): PortfolioItem {
        val investment = Investment(
            InvestmentId.of(id).valueOrFail(),
            InvestmentName.of(name).valueOrFail(),
            null,
            IconKey.of("insights").valueOrFail(),
            Currency.COP,
            1,
            Instant.parse(created),
            Instant.parse(created)
        )
        val movements = movedOn.mapIndexed { index, text ->
            val (date, time) = text.split("@")
            InvestmentMovement(
                InvestmentMovementId.of(id()).valueOrFail(),
                InvestmentMovementKind.Contribution,
                InvestmentAmount.of(Money.cop(1_000)).valueOrFail(),
                LocalDate.parse(date),
                index + 1L,
                null,
                1,
                Instant.parse("${date}T$time:00Z"),
                Instant.parse("${date}T$time:00Z")
            )
        }
        val figures = InvestmentFigures(
            contributed = Money.cop(1_000),
            withdrawn = Money.cop(0),
            dividends = Money.cop(0),
            value = pesos?.let { CarriedValue(Money.cop(it), LocalDate.parse("2026-10-01")) },
            gain = null,
            `return` = tenths?.let {
                InvestmentReturn(
                    when {
                        it > 0 -> GainDirection.Gain
                        it < 0 -> GainDirection.Loss
                        else -> GainDirection.Even
                    },
                    kotlin.math.abs(it)
                )
            },
            since = YearMonth.of(2026, 1),
            movements = movements.size
        )
        val history = InvestmentHistory(investment, movements)
        return PortfolioItem(history, figures, pesos?.let(Money::cop))
    }

    private val cdt = item("CDT", "2026-01-10T12:00:00Z", 5_000_000, 81, "2026-09-01@10:00")
    private val acciones = item(
        "Acciones",
        "2026-03-01T12:00:00Z",
        9_000_000,
        -25,
        "2026-10-20@08:00",
        "2026-10-02@09:00"
    )
    private val etf = item("ÉTF global", "2026-05-01T12:00:00Z", 9_000_000, 0, "2026-10-20@09:30")
    private val fondo = item("fondo", "2026-02-01T12:00:00Z", 2_000_000, 81, "2026-08-15@10:00")
    private val nuevo = item("Nuevo", "2026-10-24T12:00:00Z")
    private val portfolio = listOf(nuevo, fondo, etf, cdt, acciones)

    private fun names(order: InvestmentOrder) =
        InvestmentOrderPolicy.order(portfolio, order).map { it.history.investment.name.value }

    @Test
    fun mayorValorIsLargestValueFirstThenByNameAndThoseWithoutALast() {
        // Acciones and ÉTF global tie at 9.000.000: by name, accents ignored.
        assertEquals(
            listOf("Acciones", "ÉTF global", "CDT", "fondo", "Nuevo"),
            names(InvestmentOrder.LargestValue)
        )
    }

    @Test
    fun rendimientoIsHighestReturnFirstLossesBelowEvenAndThoseWithoutLast() {
        // CDT and fondo tie at 8,1 %: by name. ÉTF global is even; Acciones lost 2,5 %.
        assertEquals(
            listOf("CDT", "fondo", "ÉTF global", "Acciones", "Nuevo"),
            names(InvestmentOrder.Return)
        )
    }

    @Test
    fun alfabeticoIgnoresCaseAndAccents() {
        assertEquals(
            listOf("Acciones", "CDT", "ÉTF global", "fondo", "Nuevo"),
            names(InvestmentOrder.Alphabetical)
        )
    }

    @Test
    fun masRecienteIsTheNewestInvestmentFirst() {
        assertEquals(
            listOf("Nuevo", "ÉTF global", "Acciones", "fondo", "CDT"),
            names(InvestmentOrder.Newest)
        )
    }

    @Test
    fun ultimaActualizacionIsTheLatestMovementFirstThenWhenItWasRecorded() {
        // Acciones and ÉTF global both last moved on 20 Oct: ÉTF global's was recorded
        // later. Acciones' earlier movement, on 2 Oct, does not count.
        assertEquals(
            listOf("ÉTF global", "Acciones", "CDT", "fondo", "Nuevo"),
            names(InvestmentOrder.LatestUpdate)
        )
    }

    @Test
    fun equalInvestmentsFallBackToTheIdentifierSoReadingOrderNeverMatters() {
        val created = "2026-04-01T12:00:00Z"
        val first = item("Gemelo", created, 1_000, 10, id = "00000000-0000-4000-8000-000000000901")
        val second = item("gemelo", created, 1_000, 10, id = "00000000-0000-4000-8000-000000000902")
        InvestmentOrder.entries.forEach { order ->
            assertEquals(
                order.name,
                listOf(first, second),
                InvestmentOrderPolicy.order(listOf(second, first), order)
            )
        }
    }
}
