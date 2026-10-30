package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.carry.CarryProblem
import com.software.sello.presentation.carry.CarryProblemKind
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Recibo once a category carries (ADR 0020 CP14–CP16, CP27; docs/design/v2/arrastre.html):
 * the month's breakdown, the carrying cells, why a verdict is Excedido, the closed month and
 * a carry that cannot be calculated. Figures are copied from
 * docs/testing/signed-carryover-examples.md; nothing here works one out.
 */
class ReciboCarryRenderTest {
    private val labels = object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"

        override fun negative(amount: String) = "menos $amount"

        override fun positive(amount: String) = "más $amount"
    }
    private val render = ReciboRenderModel(MoneyFormatter(labels))

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun plus(digits: String) =
        MoneyTextValue(MoneySign.Plus, "", digits, "más $digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)

    private val comida = categoryIn(1, "Comida", 100_000, icon = "restaurant")
    private var next = 100

    private fun spend(category: Int, date: String, pesos: Long) =
        storedExpense(next++, category, date, pesos)

    /** SC13: Comida on from ene, Transporte 50.000 off, Ocio unlimited; 22 feb, D = 7. */
    private fun sc13() = carryMonthOf(
        "2027-02-22",
        listOf(comida, categoryIn(2, "Transporte", 50_000), categoryIn(3, "Ocio", null)),
        listOf(
            spend(1, "2027-01-10", 20_000),
            spend(1, "2027-02-05", 30_000),
            spend(2, "2027-02-06", 60_000),
            spend(3, "2027-02-07", 10_000)
        ),
        mapOf(1 to ene)
    )

    /** SC11 for Comida (ene spent 250.000) with Hogar 400.000 and Transporte 50.000 off. */
    private fun excedido() = carryMonthOf(
        "2027-02-22",
        listOf(comida, categoryIn(2, "Hogar", 400_000), categoryIn(3, "Transporte", 50_000)),
        listOf(
            spend(1, "2027-01-10", 250_000),
            spend(2, "2027-02-06", 380_000),
            spend(3, "2027-02-07", 40_000)
        ),
        mapOf(1 to ene)
    )

    private fun month(state: SnapshotState) = render.recibo(state) as ReciboState.Month

    @Test
    fun sc13TheSlipGivesBaseCarryAndCapacityOfTheMonth() {
        val state = month(ReadState.Ready(sc13()))

        // R 130.000, Al día, 18.550 a day for 7 days.
        assertEquals(plain("130.000"), state.summary.amount)
        assertEquals(ReciboStamp.OnTrack, state.summary.stamp)
        assertEquals(ReciboPerDay(plain("18.550"), 7), state.summary.perDay)
        assertEquals(
            ReciboCarry(
                CarryFigures(
                    base = plain("150.000"),
                    carry = plus("80.000"),
                    carryMagnitude = plain("80.000"),
                    direction = CarryDirection.Surplus,
                    capacity = plain("230.000"),
                    spent = plain("100.000"),
                    chainStart = null,
                    source = null
                ),
                listOf(CarriedCategory("Comida", ene)),
                LocalDate.of(2027, 2, 22),
                why = null
            ),
            state.summary.carry
        )
    }

    @Test
    fun sc13OnlyTheCarryingCellCarriesAndItsLimitIsTheCapacity() {
        val categories = month(ReadState.Ready(sc13())).categories.associateBy { it.name }

        val cell = categories.getValue("Comida")
        assertEquals(plain("150.000"), cell.cellAmount)
        assertEquals(plain("180.000"), cell.limit)
        assertEquals(
            CarryFigures(
                base = plain("100.000"),
                carry = plus("80.000"),
                carryMagnitude = plain("80.000"),
                direction = CarryDirection.Surplus,
                capacity = plain("180.000"),
                spent = plain("30.000"),
                chainStart = ene,
                source = ene
            ),
            cell.carry
        )
        assertNull(categories.getValue("Transporte").carry)
        assertNull(categories.getValue("Ocio").carry)
    }

    @Test
    fun anEarlierDeficitSaysWhyTheMonthAndTheCategoryAreExcedido() {
        val state = month(ReadState.Ready(excedido()))

        // Σ base 550.000 + K −150.000 = C 400.000; spent 420.000: −20.000, Excedido.
        assertEquals(minus("20.000"), state.summary.amount)
        assertEquals(ReciboStamp.Over, state.summary.stamp)
        assertEquals(
            CarryWhy(plain("400.000"), minus("150.000"), plain("150.000"), "Comida"),
            state.summary.carry?.why
        )
        // SC11: Comida C −50.000 with nothing spent: Excedido by 50.000, base 100.000.
        val cell = state.categories.first { it.name == "Comida" }
        assertEquals(ReciboStamp.Over, cell.figure.stamp)
        assertEquals(plain("50.000"), cell.figure.amount)
        assertEquals(
            CarryWhy(
                capacity = minus("50.000"),
                carry = minus("150.000"),
                carryMagnitude = plain("150.000"),
                category = "Comida",
                base = plain("100.000"),
                month = feb
            ),
            cell.why
        )
        assertNull(state.categories.first { it.name == "Hogar" }.why)
    }

    @Test
    fun sc01AClosedMonthNamesItsCapacity() {
        val march = carryMonthOf(
            "2027-04-05",
            listOf(comida),
            listOf(
                spend(1, "2027-01-10", 20_000),
                spend(1, "2027-02-10", 70_000),
                spend(1, "2027-03-10", 250_000)
            ),
            mapOf(1 to ene),
            month = YearMonth.of(2027, 3)
        )

        val state = month(ReadState.Ready(march))

        // mar C 210.000, S 250.000, R −40.000, Excedido.
        assertEquals(
            ReciboClosedMonth(plain("250.000"), plain("210.000"), carried = true),
            state.summary.closed
        )
        assertEquals(minus("40.000"), state.summary.amount)
        assertEquals(ReciboStamp.OverLimit, state.summary.stamp)
    }

    @Test
    fun aCarryThatCannotBeCalculatedIsNeverShownAsZero() {
        val failure = SnapshotFailure.Carryover(CarryoverFailure.NoFiniteBase(feb), categoryId(1))

        assertEquals(
            ReciboState.CarryFailed(
                CarryProblem(categoryId(1).value, null, CarryProblemKind.NoBase, feb)
            ),
            render.recibo(ReadState.Failed(failure, null))
        )
        // With an older read it is shown as older, with the repair named.
        val stale = month(ReadState.Failed(failure, sc13()))
        assertEquals(true, stale.stale)
        assertEquals(
            CarryProblem(categoryId(1).value, "Comida", CarryProblemKind.NoBase, feb),
            stale.carryProblem
        )
    }

    @Test
    fun withoutAChainNothingCarries() {
        val noChain = carryMonthOf(
            "2027-02-22",
            listOf(comida),
            listOf(spend(1, "2027-01-10", 250_000)),
            emptyMap()
        )

        val state = month(ReadState.Ready(noChain))

        assertNull(state.summary.carry)
        assertNull(state.categories.single().carry)
        assertNull(state.categories.single().why)
        assertEquals(plain("100.000"), state.summary.amount)
    }
}
