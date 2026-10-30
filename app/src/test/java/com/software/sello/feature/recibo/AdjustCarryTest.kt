package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.YearMonth
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Ajustar presupuesto and its sheet once a category carries (ADR 0020 CP16, CP27; SELLO-038
 * consumer boundary): the capacity said with its base and carry, the sheet editing the base.
 * SC11 for Comida (ene spent 250.000, feb K −150.000) with Hogar 400.000 (380.000 spent) and
 * Transporte 50.000 (40.000 spent), both off; 22 feb 2027. Figures hand-worked in
 * LimitAdjustmentCarryTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustCarryTest {
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)
    private val session = MonthSession(HandFinancialClock("2027-02-22"), HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val money = testMoney()
    private var nextId = 500

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val month = carryMonthOf(
        "2027-02-22",
        listOf(
            categoryIn(1, "Comida", 100_000, icon = "restaurant"),
            categoryIn(2, "Hogar", 400_000),
            categoryIn(3, "Transporte", 50_000)
        ),
        listOf(
            storedExpense(100, 1, "2027-01-10", 250_000),
            storedExpense(101, 2, "2027-02-06", 380_000),
            storedExpense(102, 3, "2027-02-07", 40_000)
        ),
        mapOf(1 to ene)
    )

    private inner class Commands : CategoryCommands {
        val submitted = mutableListOf<CategoryCommand>()
        var answer: (CategoryCommand) -> CategoryCommandOutcome = {
            CommandOutcome.Rejected(
                Specific(CategoryRejection.ChainBaseAboveLimit(Money.cop(1_000_000_000_000)))
            )
        }

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted += command
            return answer(command)
        }

        override suspend fun find(operationId: OperationId) =
            Outcome.Success<CommittedCategoryChange?>(null)
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theScreenSaysTheCapacityWithItsBaseAndCarry() {
        val model = AdjustBudgetViewModel(SavedStateHandle(), session, snapshots, money)
        snapshots.ready(month)

        val ready = model.state.value as AdjustBudgetState.Ready

        assertEquals(
            CarryFigures(
                base = plain("550.000"),
                carry = minus("150.000"),
                carryMagnitude = plain("150.000"),
                direction = CarryDirection.Deficit,
                capacity = plain("400.000"),
                spent = null,
                chainStart = null,
                source = null
            ),
            ready.carry
        )
        val comida = ready.limited.first { it.name == "Comida" }
        assertEquals(true, comida.carried)
        assertEquals(minus("50.000"), comida.limit)
        assertEquals(false, ready.limited.first { it.name == "Hogar" }.carried)
    }

    private fun sheet(commands: Commands): LimitSheetViewModel {
        val reads = HandCategoryReads().apply {
            generation = 7
            categories = listOf(
                CategoryBudget(
                    Category(
                        categoryId(1),
                        (CategoryName.of("Comida") as Outcome.Success).value,
                        (IconKey.of("restaurant") as Outcome.Success).value,
                        archived = false,
                        version = 5,
                        createdAt = Instant.EPOCH,
                        updatedAt = Instant.EPOCH
                    ),
                    MonthBudgetState.Limited(
                        (BudgetLimit.finite(Money.cop(100_000)) as Outcome.Success).value,
                        false
                    )
                )
            )
        }
        val notices = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            money
        )
        return LimitSheetViewModel(
            SavedStateHandle(mapOf("month" to "2027-02")),
            commands,
            reads,
            snapshots,
            session,
            { uuid(nextId++) },
            money,
            notices
        ).apply {
            onAction(LimitSheetAction.Open(uuid(1)))
            snapshots.ready(month)
        }
    }

    @Test
    fun theSheetEditsTheBaseAndSaysTheCapacityItWouldLeave() {
        val sheet = sheet(Commands())

        assertEquals(minus("150.000"), sheet.state.value.carry)
        assertEquals(plain("100.000"), sheet.state.value.current)
        assertEquals(
            listOf(ChipKind.Spent to 150_000L, ChipKind.CoverMonth to 120_000L),
            sheet.state.value.chips.map { it.kind to it.pesos }
        )
        "150000".forEach { sheet.onAction(LimitSheetAction.Digit(it - '0')) }

        val fact = sheet.state.value.fact!!
        assertEquals(plain("450.000"), fact.capacity)
        assertEquals(plain("30.000"), fact.left)
        assertEquals(FactOutcome.Left, fact.outcome)
    }

    @Test
    fun aBaseAboveTheCapWhileCarryingIsRefusedInWords() {
        val commands = Commands()
        val sheet = sheet(commands)
        "999".forEach { sheet.onAction(LimitSheetAction.Digit(it - '0')) }

        sheet.onAction(LimitSheetAction.Save)

        assertEquals(1, commands.submitted.size)
        assertEquals(LimitSheetError.LimitAboveCap, sheet.state.value.error)
    }
}
