package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.CategoryHistoryInput
import com.software.sello.domain.policy.CategoryInsightsPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandExpenseReads
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.CarryProblem
import com.software.sello.presentation.carry.CarryProblemKind
import com.software.sello.presentation.carry.carryInput
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Category detail once the category carries (ADR 0020 CP8, CP15, CP19): the day figures
 * from base plus carry, a zero allowance that still says the deficit, and a carry that
 * cannot be calculated. Figures copied from docs/testing/signed-carryover-examples.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailCarryTest {
    private val labels = object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"

        override fun negative(amount: String) = "menos $amount"

        override fun positive(amount: String) = "más $amount"
    }
    private val money = MoneyFormatter(labels)
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)
    private val comida = categoryIn(1, "Comida", 100_000, icon = "restaurant")

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun insights(vararg spent: Pair<String, Long>): DetailInsights {
        val expenses = spent.mapIndexed { i, (date, pesos) ->
            storedExpense(100 + i, 1, date, pesos)
        }
        val today = "2027-02-22"
        val carry = carryInput(listOf(comida), expenses, 1, ene, feb, today)
        val read = CategoryInsightsPolicy.snapshot(
            categoryId(1),
            feb,
            FinancialDay(LocalDate.parse(today), ZoneId.of("America/Bogota")),
            1,
            1,
            CategoryInsightsPolicy.monthsEndingIn(feb).map { period ->
                CategoryHistoryInput(
                    period,
                    comida.takeIf { period >= ene },
                    expenses,
                    carry.takeIf { period >= ene }
                )
            }
        ) as Outcome.Success
        return CategoryInsightsRenderModel(money).render(read.value)
    }

    @Test
    fun sc09TheDayFigureIsFromBasePlusCarryAndNoDeficitIsSaid() {
        val figures = insights("2027-01-10" to 20_000L, "2027-02-05" to 30_000L)

        assertEquals(ReciboPerDay(plain("21.400"), 7), figures.allowance)
        assertNull(figures.allowanceDeficit)
    }

    @Test
    fun sc11AZeroAllowanceStillSaysTheDeficit() {
        val figures = insights("2027-01-10" to 250_000L)

        // C −50.000 with nothing spent: 0 a day, and 50.000 still over.
        assertEquals(ReciboPerDay(plain("0"), 7), figures.allowance)
        assertEquals(plain("50.000"), figures.allowanceDeficit)
    }

    @Test
    fun aCarryThatCannotBeCalculatedIsSaidWithItsRepair() = runTest {
        val snapshots = HandSnapshots()
        val corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { testScheduler.currentTime.milliseconds },
            { uuid(900) },
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        val viewModel = CategoryDetailViewModel(
            MonthSession(HandFinancialClock("2027-03-10"), HandMonotonicClock()),
            snapshots,
            HandExpenseReads(),
            money,
            corrections,
            HandCategoryInsights(),
            HandScheduleReads()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        viewModel.onAction(CategoryDetailAction.Show(uuid(1)))

        snapshots.of(YearMonth.of(2027, 3)).value = ReadState.Failed(
            SnapshotFailure.Carryover(CarryoverFailure.NoFiniteBase(feb), categoryId(1)),
            null
        )

        assertEquals(
            CategoryDetailState.CarryFailed(
                CarryProblem(categoryId(1).value, null, CarryProblemKind.NoBase, feb)
            ),
            viewModel.state.value
        )
    }
}
