package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryMonthChange
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.DisableCarry
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.usecase.CatchUpResult
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
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.testMoney
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The carry card in Editar categoría (ADR 0020 CP1–CP4, CP9–CP13, CP20, CP28): its own
 * commands and receipts, every drop asked with the figures the command answered, refusals
 * in words, duplicate taps sending once. SC17's figures: Comida base 100.000 on from ene,
 * ene spent 120.000, so feb carries −20.000; 10 feb, 30.000 spent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryCarryEditorTest {
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)

    private inner class HandCommands : CategoryCommands {
        val submitted = mutableListOf<CategoryCommand>()
        val answers = ArrayDeque<CategoryCommandOutcome>()
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted += command
            hold?.await()
            return answers.removeFirstOrNull() ?: committed(command.operationId)
        }

        val lookups = mutableListOf<OperationId>()
        var found: (OperationId) -> Outcome<CommittedCategoryChange?, StorageFailure> =
            { Outcome.Success(null) }

        override suspend fun find(
            operationId: OperationId
        ): Outcome<CommittedCategoryChange?, StorageFailure> {
            lookups += operationId
            return found(operationId)
        }
    }

    private fun committed(operation: OperationId) = CommandOutcome.Committed(
        CommittedCategoryChange(
            OperationReceipt(operation, OperationKind.EnableCarry, 1, 1, Instant.EPOCH),
            categoryId(1)
        ),
        replayed = false
    )

    private fun rejected(reason: CategoryRejection) = CommandOutcome.Rejected(Specific(reason))

    private val commands = HandCommands()
    private val reads = HandCategoryReads().apply { generation = 7 }
    private val snapshots = HandSnapshots()
    private val session = MonthSession(HandFinancialClock("2027-02-10"), HandMonotonicClock())
    private var nextId = 500
    private val money = MoneyFormatter(
        object : MoneyLabels {
            override fun amount(currency: Currency, digits: String, isOne: Boolean) =
                "$digits pesos"

            override fun negative(amount: String) = "menos $amount"

            override fun positive(amount: String) = "más $amount"
        }
    )

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private fun cop(pesos: Long) = Money.cop(pesos)

    private val comida = categoryIn(1, "Comida", 100_000, icon = "restaurant")

    private fun stored(version: Long = 5) = CategoryBudget(
        Category(
            categoryId(1),
            (CategoryName.of("Comida") as Outcome.Success).value,
            (IconKey.of("restaurant") as Outcome.Success).value,
            archived = false,
            version = version,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ),
        MonthBudgetState.Limited((BudgetLimit.finite(cop(100_000)) as Outcome.Success).value, false)
    )

    /** SC17: on from ene, ene spent 120.000, feb 30.000; or the same history with carry off. */
    private fun publish(carrying: Boolean) {
        snapshots.ready(
            carryMonthOf(
                "2027-02-10",
                listOf(comida),
                listOf(
                    storedExpense(100, 1, "2027-01-10", 120_000),
                    storedExpense(101, 1, "2027-02-05", 30_000)
                ),
                if (carrying) mapOf(1 to ene) else emptyMap()
            )
        )
    }

    /** SC17's drop: K −20.000; C 80.000 → 100.000; R 50.000 → 70.000. */
    private val sc17 =
        DropPreview(
            feb,
            cop(-20_000),
            cop(100_000),
            cop(80_000),
            cop(100_000),
            cop(50_000),
            cop(70_000)
        )

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        reads.categories = listOf(stored())
    }

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun saved(month: YearMonth? = null) = SavedStateHandle(
        buildMap {
            put(CategoryManagementViewModel.KEY_CATEGORY, uuid(1))
            if (month != null) put(CategoryManagementViewModel.KEY_MONTH, month.toString())
        }
    )

    private fun form(month: YearMonth? = null, state: SavedStateHandle = saved(month)) =
        CategoryManagementViewModel(
            state,
            commands,
            reads,
            snapshots,
            session,
            { uuid(nextId++) },
            money,
            Corrections(
                HandExpenseCommands(),
                HandIncomeCommands(),
                HandGoalCommands(),
                HandInvestmentCommands(),
                { Duration.ZERO },
                { uuid(nextId++) },
                CoroutineScope(Dispatchers.Main),
                HandCategoryReads(),
                testMoney()
            ),
            { CatchUpResult(emptyList(), stopped = null) }
        )

    private val CategoryManagementViewModel.now get() = state.value

    @Test
    fun theCardShowsTheCarryOfTheMonthInProgress() {
        publish(carrying = true)

        val card = form().now.carry

        assertEquals(
            CarryCard.On(
                ene,
                plain("100.000"),
                minus("20.000"),
                plain("20.000"),
                CarryDirection.Deficit,
                plain("80.000")
            ),
            card
        )
    }

    @Test
    fun turningOnAsksFirstThenSendsItsOwnCommandOnce() {
        publish(carrying = false)
        val form = form()
        assertEquals(CarryCard.Off(feb), form.now.carry)

        form.onAction(CategoryEditorAction.EnableCarry)
        // Asked with the month and what it already spent (SC07: it counts from day 1).
        assertEquals(CarryQuestion.Enable(feb, plain("30.000")), form.now.carryQuestion)
        assertTrue(commands.submitted.isEmpty())

        commands.hold = CompletableDeferred()
        form.onAction(CategoryEditorAction.CarryConfirmed)
        form.onAction(CategoryEditorAction.CarryConfirmed)
        commands.hold!!.complete(Unit)

        assertEquals(1, commands.submitted.size)
        val sent = commands.submitted.single() as EnableCarry
        assertEquals(5L, sent.expectedVersion)
        assertEquals(CarryDone.Enabled, form.now.carryDone)
        assertNull(form.now.carryQuestion)
    }

    @Test
    fun aRefusalIsSaidInTheCardAndNothingTurnsOn() {
        publish(carrying = false)
        commands.answers +=
            rejected(CategoryRejection.CarryRefused(CarryEnableRefusal.DefaultAfterNotFinite))
        val form = form()

        form.onAction(CategoryEditorAction.EnableCarry)
        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertEquals(CarryProblemText.DefaultAfterNotFinite, form.now.carryProblem)
        assertNull(form.now.carryDone)
        assertEquals(CarryCard.Off(feb), form.now.carry)
    }

    @Test
    fun sc17TurningOffShowsTheBalanceAndSendsExactlyTheOneConfirmed() {
        publish(carrying = true)
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        val form = form()

        form.onAction(CategoryEditorAction.DisableCarry)

        assertEquals(
            CarryQuestion.Drop(
                DropReason.Disable,
                DropFigures(
                    feb,
                    minus("20.000"),
                    plain("100.000"),
                    plain("80.000"),
                    plain("100.000"),
                    plain("50.000"),
                    plain("70.000")
                )
            ),
            form.now.carryQuestion
        )
        assertNull((commands.submitted.single() as DisableCarry).confirmedCarryIn)

        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertEquals(cop(-20_000), (commands.submitted.last() as DisableCarry).confirmedCarryIn)
        assertEquals(CarryDone.Disabled, form.now.carryDone)
    }

    @Test
    fun aBalanceThatMovedWhileConfirmingIsShownAgainWithWhatItWas() {
        publish(carrying = true)
        val moved =
            DropPreview(
                feb,
                cop(-25_000),
                cop(100_000),
                cop(75_000),
                cop(100_000),
                cop(45_000),
                cop(70_000)
            )
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(moved))
        val form = form()

        form.onAction(CategoryEditorAction.DisableCarry)
        form.onAction(CategoryEditorAction.CarryConfirmed)

        val question = form.now.carryQuestion as CarryQuestion.Drop
        assertEquals(minus("25.000"), question.figures.carry)
        assertEquals(minus("20.000"), question.figures.changedFrom)
        assertNull(form.now.carryDone)
    }

    @Test
    fun cancellingADropSendsNothingMore() {
        publish(carrying = true)
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        val form = form()

        form.onAction(CategoryEditorAction.DisableCarry)
        form.onAction(CategoryEditorAction.CarryDismissed)

        assertNull(form.now.carryQuestion)
        assertEquals(1, commands.submitted.size)
    }

    @Test
    fun carryWaitsWhileTheFormHasChanges() {
        publish(carrying = true)
        val form = form()

        form.onAction(CategoryEditorAction.NameChanged("Mercado"))
        form.onAction(CategoryEditorAction.DisableCarry)

        assertTrue(commands.submitted.isEmpty())
        assertEquals(false, form.now.canChangeCarry)
    }

    @Test
    fun sc20ArchivingWithABalanceAsksForTheDropAndArchivesWithIt() {
        publish(carrying = true)
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        val form = form()

        form.onAction(CategoryEditorAction.Archive)
        form.onAction(CategoryEditorAction.ArchiveConfirmed)
        assertEquals(DropReason.Archive, (form.now.carryQuestion as CarryQuestion.Drop).reason)
        form.onAction(CategoryEditorAction.CarryConfirmed)

        val sent = commands.submitted.filterIsInstance<ArchiveCategory>()
        assertEquals(listOf(null, cop(-20_000)), sent.map { it.confirmedCarryIn })
        assertEquals(EditorResult.Archived, form.now.result)
    }

    @Test
    fun sc21SinLimiteWithABalanceAsksForTheDropBeforeSaving() {
        publish(carrying = true)
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        val form = form()

        form.onAction(CategoryEditorAction.UnlimitedChanged(true))
        form.onAction(CategoryEditorAction.Submit)
        assertEquals(DropReason.Unlimited, (form.now.carryQuestion as CarryQuestion.Drop).reason)
        form.onAction(CategoryEditorAction.CarryConfirmed)

        val sent = commands.submitted.filterIsInstance<SetDefaultLimit>()
        assertEquals(listOf(null, cop(-20_000)), sent.map { it.confirmedCarryIn })
        assertEquals(BudgetLimit.Unlimited, sent.last().limit)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun sc28APastLimitShowsTheLaterMonthsAndSavesWithWhatCameWithThem() {
        reads.categories = listOf(stored())
        // SC28's feb: C 180.000 → 200.000, R 110.000 → 130.000 (one month shown here).
        val change = CarryMonthChange(
            categoryId(1),
            feb,
            CarryMonth(
                feb,
                ene,
                cop(100_000),
                cop(80_000),
                cop(180_000),
                cop(70_000),
                cop(110_000),
                CarryVerdict.Closed(PastVerdict.WithinLimit)
            ),
            CarryMonth(
                feb,
                ene,
                cop(100_000),
                cop(100_000),
                cop(200_000),
                cop(70_000),
                cop(130_000),
                CarryVerdict.Closed(PastVerdict.WithinLimit)
            ),
            cop(20_000)
        )
        val confirmation = CorrectionConfirmation(41, "change")
        commands.answers += rejected(
            CategoryRejection.CorrectionUnconfirmed(
                confirmation,
                CarryCorrection(listOf(change), emptyList(), emptyList(), feb, emptyMap())
            )
        )
        val form = form(month = ene)

        form.onAction(CategoryEditorAction.LimitChanged("120000"))
        form.onAction(CategoryEditorAction.Submit)

        val question = form.now.carryQuestion as CarryQuestion.Correction
        assertEquals(1, question.list.months)
        assertEquals(plain("180.000"), question.list.groups.single().rows.single().before)
        assertEquals(plain("200.000"), question.list.groups.single().rows.single().after)
        assertNull((commands.submitted.single() as SetMonthLimit).confirmation)

        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertEquals(confirmation, (commands.submitted.last() as SetMonthLimit).confirmation)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun aLimitThatWouldLeaveCarryUncomputableIsRefusedInWords() {
        publish(carrying = true)
        commands.answers +=
            rejected(CategoryRejection.CarryUnavailable(CarryoverFailure.Unrepresentable))
        val form = form()

        form.onAction(CategoryEditorAction.LimitChanged("150000"))
        form.onAction(CategoryEditorAction.Submit)

        assertEquals(CarryProblemText.Unavailable, form.now.carryProblem)
        assertNull(form.now.result)
    }

    private fun unknown() =
        CommandOutcome.OutcomeUnknown((OperationId.of(uuid(999)) as Outcome.Success).value)

    @Test
    fun anUnknownOutcomeIsAskedWithTheSameOperationAndNeverSentAgain() {
        publish(carrying = false)
        commands.answers += unknown()
        commands.found = { Outcome.Success(committed(it).value) }
        val form = form()

        form.onAction(CategoryEditorAction.EnableCarry)
        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertEquals(1, commands.submitted.size)
        assertEquals(listOf(commands.submitted.single().operationId), commands.lookups)
        assertEquals(CarryDone.Enabled, form.now.carryDone)
    }

    @Test
    fun aCarryChangeInterruptedByProcessDeathIsSettledByTheSameOperationOnRestart() {
        publish(carrying = true)
        // −20.000 is asked, confirmed and sent; its outcome is unknown and asking fails.
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        commands.answers += unknown()
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val state = saved()
        val before = form(state = state)
        before.onAction(CategoryEditorAction.DisableCarry)
        before.onAction(CategoryEditorAction.CarryConfirmed)
        assertEquals(SaveError.Unknown, before.now.saveError)
        val sent = commands.submitted.last() as DisableCarry
        assertEquals(cop(-20_000), sent.confirmedCarryIn)

        // The process dies; storage had committed it. The new form only asks.
        publish(carrying = false)
        commands.found = { Outcome.Success(committed(it).value) }
        val after = form(state = state)

        assertEquals(2, commands.submitted.size)
        assertEquals(listOf(sent.operationId), commands.lookups.distinct())
        assertEquals(CarryCard.Off(feb), after.now.carry)
        assertNull(after.now.saveError)
        assertEquals(false, after.now.busy)
    }

    @Test
    fun aDoubleTapOnTheDropConfirmationSendsOnce() {
        publish(carrying = true)
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        val form = form()
        form.onAction(CategoryEditorAction.DisableCarry)

        commands.hold = CompletableDeferred()
        form.onAction(CategoryEditorAction.CarryConfirmed)
        form.onAction(CategoryEditorAction.CarryConfirmed)
        commands.hold!!.complete(Unit)

        val sent = commands.submitted.filterIsInstance<DisableCarry>()
        assertEquals(listOf(null, cop(-20_000)), sent.map { it.confirmedCarryIn })
        assertEquals(CarryDone.Disabled, form.now.carryDone)
    }
}
