package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.expense.HandSnapshots
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.YearMonth
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The limit sheet on jue 22 oct 2026, in the AJ fixture: Café's limit 50.000 with 90.000
 * spent, at version 5 of generation 7; the month 1.000.000 with 1.080.000 spent (AJ04,
 * AJ06). Every expected figure is the one the AJ examples state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LimitSheetViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val cafe = uuid(1)
    private val clock = HandFinancialClock("2026-10-22")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val reads = HandCategoryReads().apply { generation = 7 }
    private var nextId = 500
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private lateinit var notices: Corrections

    /** Commands a test scripts. Unless told otherwise every command commits. */
    private inner class HandCommands : CategoryCommands {
        val submitted = mutableListOf<CategoryCommand>()
        val looked = mutableListOf<OperationId>()
        var answer: (CategoryCommand) -> CategoryCommandOutcome = { committed(it.operationId) }
        var found: (OperationId) -> Outcome<CommittedCategoryChange?, StorageFailure> =
            { Outcome.Success(null) }
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted += command
            hold?.await()
            return answer(command)
        }

        override suspend fun find(
            operationId: OperationId
        ): Outcome<CommittedCategoryChange?, StorageFailure> {
            looked += operationId
            return found(operationId)
        }
    }

    private val commands = HandCommands()

    private fun change(operation: OperationId) = CommittedCategoryChange(
        OperationReceipt(operation, OperationKind.SetMonthLimit, 1, 1, Instant.EPOCH),
        (CategoryId.of(cafe) as Outcome.Success).value
    )

    private fun committed(operation: OperationId) =
        CommandOutcome.Committed(change(operation), replayed = false)

    private fun finite(pesos: Long) =
        (BudgetLimit.finite(Money.cop(pesos)) as Outcome.Success).value

    private val excedido = monthOf(
        "2026-10-22",
        listOf(
            categoryIn(1, "Café", 50_000, icon = "local_cafe"),
            categoryIn(2, "Comida", 200_000),
            categoryIn(3, "Transporte", 100_000),
            categoryIn(4, "Ocio", 100_000),
            categoryIn(5, "Hogar", 400_000),
            categoryIn(6, "Salud", 80_000),
            categoryIn(7, "Ropa", 70_000)
        ),
        listOf(
            storedExpense(50, 1, "2026-10-05", 90_000),
            storedExpense(51, 2, "2026-10-06", 230_000),
            storedExpense(52, 3, "2026-10-07", 120_000),
            storedExpense(53, 4, "2026-10-08", 110_000),
            storedExpense(54, 5, "2026-10-09", 400_000),
            storedExpense(55, 6, "2026-10-10", 60_000),
            storedExpense(56, 7, "2026-10-11", 70_000)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        notices = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main)
        )
        reads.categories = listOf(
            CategoryBudget(
                Category(
                    (CategoryId.of(cafe) as Outcome.Success).value,
                    (CategoryName.of("Café") as Outcome.Success).value,
                    (IconKey.of("local_cafe") as Outcome.Success).value,
                    archived = false,
                    version = 5,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH
                ),
                MonthBudgetState.Limited(finite(50_000), explicit = false)
            )
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun sheet(saved: SavedStateHandle = SavedStateHandle(mapOf("month" to "2026-10"))) =
        LimitSheetViewModel(saved, commands, reads, snapshots, session, {
            uuid(nextId++)
        }, money, notices)

    private fun LimitSheetViewModel.type(pesos: Long) =
        pesos.toString().forEach { onAction(LimitSheetAction.Digit(it - '0')) }

    /** Café's sheet, open on the Excedido month. */
    private fun opened(): LimitSheetViewModel = sheet().apply {
        onAction(LimitSheetAction.Open(cafe))
        snapshots.ready(excedido)
    }

    @Test
    fun openingShowsTheCategoryAndItsShortcuts() {
        val state = opened().state.value

        assertEquals(cafe, state.categoryId)
        assertEquals("Café", state.name)
        assertEquals("50.000 pesos", state.current?.spoken)
        assertEquals("90.000 pesos", state.spent?.spoken)
        assertEquals(
            listOf(ChipKind.Spent to 90_000L, ChipKind.CoverMonth to 130_000L),
            state.chips.map { it.kind to it.pesos }
        )
        assertEquals(LimitScope.ThisMonth, state.scope)
        assertFalse(state.canSave)
    }

    @Test
    fun typingShowsTheFact() {
        val sheet = opened()

        sheet.onAction(LimitSheetAction.Digit(1))
        sheet.onAction(LimitSheetAction.Digit(5))
        sheet.onAction(LimitSheetAction.Digit(0))
        sheet.onAction(LimitSheetAction.TripleZero)
        val typed = sheet.state.value
        sheet.onAction(LimitSheetAction.Chip(ChipKind.CoverMonth))
        val covered = sheet.state.value
        sheet.onAction(LimitSheetAction.Chip(ChipKind.Spent))
        val spent = sheet.state.value

        // AJ06
        assertEquals("150.000 pesos", typed.amount?.spoken)
        assertEquals(
            listOf("1.100.000 pesos", "20.000 pesos", FactOutcome.Left.name, "false"),
            typed.fact?.let {
                listOf(it.total.spoken, it.left.spoken, it.outcome.name, "${it.afterPayments}")
            }
        )
        assertTrue(typed.canSave)
        assertEquals(ChipKind.CoverMonth, covered.chosen)
        assertEquals("1.080.000 pesos", covered.fact?.total?.spoken)
        assertEquals(FactOutcome.Exactly, covered.fact?.outcome)
        assertEquals("1.040.000 pesos", spent.fact?.total?.spoken)
        assertEquals(FactOutcome.Short, spent.fact?.outcome)
        assertEquals("40.000 pesos", spent.fact?.left?.spoken)
    }

    @Test
    fun soloSendsAMonthLimitForTheMonthOpened() {
        val sheet = opened()
        sheet.type(150_000)

        sheet.onAction(LimitSheetAction.Save)

        val sent = commands.submitted.single() as SetMonthLimit
        assertEquals(listOf(7L, 5L), listOf(sent.generation, sent.expectedVersion))
        assertEquals(cafe, sent.categoryId.value)
        assertEquals(october, sent.month)
        assertEquals(finite(150_000), sent.limit)
        assertNull(sheet.state.value.categoryId)
        assertEquals(
            CorrectionNotice.LimitAdjusted(
                "Café",
                money.format(Money.cop(150_000)),
                october,
                false
            ),
            notices.notice.value
        )
    }

    @Test
    fun desdeSendsADefault() {
        val sheet = opened()
        sheet.onAction(LimitSheetAction.Scope(LimitScope.FromThisMonth))
        sheet.type(150_000)

        sheet.onAction(LimitSheetAction.Save)

        val sent = commands.submitted.single() as SetDefaultLimit
        assertEquals(listOf(7L, 5L), listOf(sent.generation, sent.expectedVersion))
        assertEquals(finite(150_000), sent.limit)
        assertEquals(
            CorrectionNotice.LimitAdjusted("Café", money.format(Money.cop(150_000)), october, true),
            notices.notice.value
        )
    }

    @Test
    fun aSecondSaveWhileBusyIsIgnored() {
        val sheet = opened()
        sheet.type(150_000)
        val hold = CompletableDeferred<Unit>().also { commands.hold = it }

        sheet.onAction(LimitSheetAction.Save)
        val busy = sheet.state.value.busy
        sheet.onAction(LimitSheetAction.Save)
        hold.complete(Unit)

        assertTrue(busy)
        assertEquals(1, commands.submitted.size)
    }

    @Test
    fun anUnknownOutcomeIsAskedNotResent() {
        val sheet = opened()
        sheet.type(150_000)
        commands.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }

        sheet.onAction(LimitSheetAction.Save)
        val unknown = sheet.state.value
        commands.found = { Outcome.Success(change(it)) }
        sheet.onAction(LimitSheetAction.Save)
        sheet.onAction(LimitSheetAction.CheckSaved)

        assertEquals(LimitSheetError.Unknown, unknown.error)
        assertFalse(unknown.canSave)
        assertEquals(1, commands.submitted.size)
        assertEquals(2, commands.looked.size)
        assertNull(sheet.state.value.categoryId)
    }

    @Test
    fun restoredUnknownSaveIsAskedNotResent() {
        val operation = uuid(900)
        commands.found = { Outcome.Success(change(it)) }
        snapshots.ready(excedido)

        sheet(
            SavedStateHandle(
                mapOf(
                    "month" to "2026-10",
                    LimitSheetViewModel.KEY_CATEGORY to cafe,
                    LimitSheetViewModel.KEY_DIGITS to "150000",
                    LimitSheetViewModel.KEY_SCOPE to LimitScope.ThisMonth.name,
                    LimitSheetViewModel.KEY_OPERATION to operation
                )
            )
        )

        assertEquals(emptyList<CategoryCommand>(), commands.submitted)
        assertEquals(listOf(operation), commands.looked.map { it.value })
        assertEquals(
            CorrectionNotice.LimitAdjusted(
                "Café",
                money.format(Money.cop(150_000)),
                october,
                false
            ),
            notices.notice.value
        )
    }

    @Test
    fun monthEndedRefusesTheSave() {
        val sheet = opened()
        sheet.type(150_000)
        clock.goTo("2026-11-01")

        sheet.onAction(LimitSheetAction.Save)

        assertEquals(LimitSheetError.MonthEnded, sheet.state.value.error)
        assertEquals(emptyList<CategoryCommand>(), commands.submitted)
    }

    @Test
    fun rejectionsBecomeTheirErrors() {
        val cases = listOf(
            CommandRejection.VersionConflict(6) to LimitSheetError.Conflict,
            CommandRejection.StaleGeneration(8) to LimitSheetError.DataChanged,
            Specific(CategoryRejection.CategoryMissing) to LimitSheetError.CategoryGone,
            Specific(CategoryRejection.CategoryArchived) to LimitSheetError.Archived,
            CommandRejection.StorageFailed(StorageFailure.Unavailable("x")) to
                LimitSheetError.NotSaved
        )

        val seen = cases.map { (reason, _) ->
            commands.answer = { CommandOutcome.Rejected(reason) }
            val sheet = opened()
            sheet.type(150_000)
            sheet.onAction(LimitSheetAction.Save)
            sheet.state.value.error to sheet.state.value.amount?.spoken
        }

        assertEquals(cases.map { it.second to "150.000 pesos" }, seen)
    }

    @Test
    fun theDraftSurvivesProcessDeath() {
        snapshots.ready(excedido)

        val state = sheet(
            SavedStateHandle(
                mapOf(
                    "month" to "2026-10",
                    LimitSheetViewModel.KEY_CATEGORY to cafe,
                    LimitSheetViewModel.KEY_DIGITS to "150000",
                    LimitSheetViewModel.KEY_SCOPE to LimitScope.FromThisMonth.name
                )
            )
        ).state.value

        assertEquals(cafe, state.categoryId)
        assertEquals(LimitScope.FromThisMonth, state.scope)
        assertEquals("150.000 pesos", state.amount?.spoken)
        assertTrue(state.canSave)
    }

    @Test
    fun aFailedSnapshotHidesChipsAndFactButKeepsTheDraft() {
        val sheet = opened()
        sheet.type(150_000)

        snapshots.of(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)

        val state = sheet.state.value
        assertEquals(emptyList<LimitChip>(), state.chips)
        assertNull(state.fact)
        assertEquals("150.000 pesos", state.amount?.spoken)
        assertTrue(state.canSave)
    }
}
