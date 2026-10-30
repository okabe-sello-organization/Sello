package com.software.sello.feature.resumen

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.ResumenMonthInput
import com.software.sello.domain.policy.ResumenPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.carry.carryInput
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Resumen once a category carries (ADR 0020 CP16, CP17, CP19): the limit line is the
 * month's limit with carry and says so, the month read gives its base, carry and capacity,
 * and Por asignar stays on base limits. SC13 and SC14 from
 * docs/testing/signed-carryover-examples.md.
 */
class ResumenCarryRenderTest {
    private val render = ResumenRenderModel(testMoney())
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)
    private val today = "2027-02-22"

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun plus(digits: String) =
        MoneyTextValue(MoneySign.Plus, "", digits, "más $digits pesos")

    private val categories = listOf(
        categoryIn(1, "Comida", 100_000, icon = "restaurant"),
        categoryIn(2, "Transporte", 50_000),
        categoryIn(3, "Ocio", null)
    )
    private val expenses = listOf(
        storedExpense(100, 1, "2027-01-10", 20_000),
        storedExpense(101, 1, "2027-02-05", 30_000),
        storedExpense(102, 2, "2027-02-06", 60_000),
        storedExpense(103, 3, "2027-02-07", 10_000)
    )

    private fun read(carrying: Boolean): ResumenState.Ready {
        val day = FinancialDay(LocalDate.parse(today), ZoneId.of("America/Bogota"))
        val months = ResumenPolicy.monthsEndingIn(feb).map { period ->
            ResumenMonthInput(
                period,
                if (period >= ene) categories else emptyList(),
                expenses,
                if (carrying && period >= ene) {
                    listOf(carryInput(categories, expenses, 1, ene, period, today))
                } else {
                    emptyList()
                }
            )
        }
        val income = Income(
            id = (IncomeId.of(uuid(900)) as Outcome.Success).value,
            date = LocalDate.parse("2027-02-08"),
            sequence = 1,
            amount = pesos(200_000),
            source = IncomeSource.Salary,
            note = null,
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val snapshot = ResumenPolicy.snapshot(
            feb,
            day,
            1,
            1,
            months,
            listOf(income)
        ) as Outcome.Success
        return render.render(ReadState.Ready(snapshot.value), feb) as ResumenState.Ready
    }

    @Test
    fun sc13TheLineIsTheMonthsLimitWithCarryAndTheMonthGivesItsParts() {
        val state = read(carrying = true)

        assertEquals(ResumenLimit(230_000, plain("230.000"), carried = true), state.monthLimit)
        assertEquals(
            CarryFigures(
                base = plain("150.000"),
                carry = plus("80.000"),
                carryMagnitude = plain("80.000"),
                direction = CarryDirection.Surplus,
                capacity = plain("230.000"),
                spent = null,
                chainStart = null,
                source = null
            ),
            state.carry
        )
    }

    @Test
    fun sc13EachHistoryMonthThatCarriesGivesBaseCarryAndCapacity() {
        val state = read(carrying = true)

        // ene starts the chain: 150.000 + 0. feb: 150.000 + 80.000 = 230.000 (SC13).
        assertEquals(
            listOf(
                Triple("2027-01", "150.000", "0") to "150.000",
                Triple("2027-02", "150.000", "80.000") to "230.000"
            ),
            state.history.map { point ->
                val carry = point.carry!!
                Triple(point.chart.id, carry.base.digits, carry.carry.digits) to
                    carry.capacity.digits
            }
        )
        assertEquals(plain("230.000"), state.history.last().detail)
    }

    @Test
    fun withoutCarryNoHistoryMonthCarries() {
        assertEquals(listOf(null, null), read(carrying = false).history.map { it.carry })
    }

    @Test
    fun withoutCarryTheLineIsTheLimitAndNothingCarries() {
        val state = read(carrying = false)

        assertEquals(ResumenLimit(150_000, plain("150.000"), carried = false), state.monthLimit)
        assertNull(state.carry)
    }
}
