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
import com.software.sello.domain.port.CarrySetting
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.EditCategory
import com.software.sello.domain.port.LimitEdit
import com.software.sello.domain.port.ReadState
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
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.SavedCarry
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
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
 * The carry card in Editar categoría (ADR 0020 CP1–CP4, CP9–CP13, CP20, CP28; SELLO-188):
 * the switch and its questions change only the draft, Guardar saves everything in one
 * command, every drop is asked with the exact figures, refusals are said in words and
 * duplicate taps send once. SC17's figures: Comida base 100.000 on from ene, ene spent
 * 120.000, so feb carries −20.000; 10 feb, 30.000 spent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryCarryEditorTest {
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)

    private inner class HandCommands : CategoryCommands {
        val submitted = mutableListOf<CategoryCommand>()
        val answers = ArrayDeque<CategoryCommandOutcome>()
        var hold: CompletableDeferred<Unit>? = null

        /** The caller is cancelled while this many more sends are in flight. */
        var cancelled = 0

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted += command
            hold?.await()
            if (cancelled > 0) {
                cancelled--
                throw CancellationException("left the screen")
            }
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
            OperationReceipt(operation, OperationKind.EditCategory, 1, 1, Instant.EPOCH),
            categoryId(1)
        ),
        replayed = false
    )

    private fun rejected(reason: CategoryRejection) = CommandOutcome.Rejected(Specific(reason))

    private val commands = HandCommands()
    private val reads = HandCategoryReads().apply { generation = 7 }
    private val snapshots = HandSnapshots()
    private val clock = HandFinancialClock("2027-02-10")
    private val session = MonthSession(clock, HandMonotonicClock())
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

    private val notices by lazy {
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
        )
    }

    private fun form(month: YearMonth? = null, state: SavedStateHandle = saved(month)) =
        CategoryManagementViewModel(
            state,
            commands,
            reads,
            snapshots,
            session,
            { uuid(nextId++) },
            money,
            notices,
            { CatchUpResult(emptyList(), stopped = null) },
            HandRemovalPreviews()
        )

    private val CategoryManagementViewModel.now get() = state.value

    private val edits get() = commands.submitted.filterIsInstance<EditCategory>()

    /** The same saved state, as Android hands it to a new process. */
    private fun SavedStateHandle.restored() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private val sc17Figures = DropFigures(
        feb,
        minus("20.000"),
        plain("100.000"),
        plain("80.000"),
        plain("100.000"),
        plain("50.000"),
        plain("70.000")
    )

    /** Turns carry off in the draft, confirming SC17's −20.000. */
    private fun CategoryManagementViewModel.turnOff() {
        onAction(CategoryEditorAction.DisableCarry)
        onAction(CategoryEditorAction.CarryConfirmed)
    }

    private fun CategoryManagementViewModel.turnOn() {
        onAction(CategoryEditorAction.EnableCarry)
        onAction(CategoryEditorAction.CarryConfirmed)
    }

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
    fun turningOnAsksFirstThenOnlyChangesTheDraftUntilGuardar() {
        publish(carrying = false)
        val form = form()
        assertEquals(CarryCard.Off(feb), form.now.carry)

        form.onAction(CategoryEditorAction.EnableCarry)
        // Asked with the month and what it already spent (SC07: it counts from day 1).
        assertEquals(CarryQuestion.Enable(feb, plain("30.000")), form.now.carryQuestion)
        form.onAction(CategoryEditorAction.CarryConfirmed)
        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertTrue(commands.submitted.isEmpty())
        assertNull(form.now.carryQuestion)
        assertEquals(CarryPending(on = true, month = feb, forgets = null), form.now.carryPending)
        assertTrue(form.now.changed)
        // The card still says what is stored.
        assertEquals(CarryCard.Off(feb), form.now.carry)

        form.onAction(CategoryEditorAction.Submit)

        val sent = commands.submitted.single() as EditCategory
        assertEquals(
            EditCategory(
                operationId = sent.operationId,
                generation = 7,
                categoryId = categoryId(1),
                expectedVersion = 5,
                month = feb,
                carry = CarrySetting.On
            ),
            sent
        )
        assertEquals(EditorResult.Updated, form.now.result)
        assertEquals(
            CorrectionNotice.CategorySaved(null, SavedCarry("Comida", on = true, month = feb)),
            notices.notice.value
        )
    }

    @Test
    fun sc17TurningOffAsksWithTheMonthsFiguresAndSendsNothingUntilGuardar() {
        publish(carrying = true)
        val form = form()

        form.onAction(CategoryEditorAction.DisableCarry)

        assertEquals(CarryQuestion.Drop(DropReason.Disable, sc17Figures), form.now.carryQuestion)
        form.onAction(CategoryEditorAction.CarryConfirmed)
        assertTrue(commands.submitted.isEmpty())
        assertEquals(
            CarryPending(on = false, month = feb, forgets = minus("20.000")),
            form.now.carryPending
        )

        form.onAction(CategoryEditorAction.Submit)

        val sent = edits.single()
        assertEquals(CarrySetting.Off, sent.carry)
        assertEquals(cop(-20_000), sent.confirmedCarryIn)
        assertEquals(
            CorrectionNotice.CategorySaved(null, SavedCarry("Comida", on = false, month = feb)),
            notices.notice.value
        )
    }

    @Test
    fun leavingWithAPendingCarryChangeAsksAndDiscardingStoresNothing() {
        publish(carrying = true)
        val form = form()
        form.turnOff()

        form.onAction(CategoryEditorAction.Back)
        assertTrue(form.now.confirmDiscard)
        form.onAction(CategoryEditorAction.DiscardConfirmed)

        assertEquals(EditorResult.Discarded, form.now.result)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun flippingTheSwitchBackLeavesNothingToSave() {
        publish(carrying = true)
        val form = form()
        form.turnOff()

        form.onAction(CategoryEditorAction.EnableCarry)

        assertNull(form.now.carryPending)
        assertNull(form.now.carryQuestion)
        assertEquals(false, form.now.changed)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun carryAndTheOtherChangesAreSavedTogetherInOneCommand() {
        publish(carrying = true)
        val form = form()

        form.onAction(CategoryEditorAction.NameChanged("Mercado"))
        form.onAction(CategoryEditorAction.LimitChanged("150000"))
        // Carry no longer waits for the other changes to be saved first.
        assertTrue(form.now.canChangeCarry)
        form.turnOff()
        form.onAction(CategoryEditorAction.Submit)

        val sent = edits.single()
        assertEquals(1, commands.submitted.size)
        assertEquals("Mercado", sent.name?.value)
        assertNull(sent.icon)
        assertEquals(
            LimitEdit.FromThisMonth((BudgetLimit.finite(cop(150_000)) as Outcome.Success).value),
            sent.limit
        )
        assertEquals(CarrySetting.Off, sent.carry)
        assertEquals(cop(-20_000), sent.confirmedCarryIn)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun aBalanceThatMovedBeforeGuardarIsAskedAgainWithWhatItWasAndConfirmingSaves() {
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
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(moved))
        val form = form()
        form.turnOff()

        form.onAction(CategoryEditorAction.Submit)

        val question = form.now.carryQuestion as CarryQuestion.Drop
        assertEquals(minus("25.000"), question.figures.carry)
        assertEquals(minus("20.000"), question.figures.changedFrom)
        assertNull(form.now.result)

        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertEquals(listOf(cop(-20_000), cop(-25_000)), edits.map { it.confirmedCarryIn })
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun cancellingTheDropQuestionChangesNothing() {
        publish(carrying = true)
        val form = form()

        form.onAction(CategoryEditorAction.DisableCarry)
        form.onAction(CategoryEditorAction.CarryDismissed)

        assertNull(form.now.carryQuestion)
        assertNull(form.now.carryPending)
        assertEquals(false, form.now.changed)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun withNothingCarriedInTurningOffAsksNothing() {
        // On from feb itself: feb's carry-in is 0, so there is nothing to forget (SC18).
        snapshots.ready(
            carryMonthOf(
                "2027-02-10",
                listOf(comida),
                listOf(storedExpense(101, 1, "2027-02-05", 30_000)),
                mapOf(1 to feb)
            )
        )
        val form = form()

        form.onAction(CategoryEditorAction.DisableCarry)

        assertNull(form.now.carryQuestion)
        assertEquals(CarryPending(on = false, month = feb, forgets = null), form.now.carryPending)
        form.onAction(CategoryEditorAction.Submit)
        assertNull(edits.single().confirmedCarryIn)
    }

    @Test
    fun aPendingCarryChangeIsDroppedWhenTheMonthEndsBeforeGuardar() {
        publish(carrying = false)
        clock.goTo("2027-02-28")
        val form = form()
        form.turnOn()
        clock.goTo("2027-03-01")

        form.onAction(CategoryEditorAction.Submit)

        assertTrue(commands.submitted.isEmpty())
        assertEquals(SaveError.MonthEnded, form.now.saveError)
        assertEquals(MonthEnded(feb, carry = true), form.now.monthEnded)
        assertEquals(YearMonth.of(2027, 3), form.now.targetMonth)
        // "Empieza en 0 en febrero" is no longer true: it is chosen again for March.
        assertNull(form.now.carryPending)
        // March was never published: its card is not guessed.
        assertEquals(CarryCard.Unreadable, form.now.carry)
    }

    @Test
    fun aRestoredFormWithACarryChangeIsNotSavedAfterTheMonthEnded() {
        publish(carrying = false)
        clock.goTo("2027-02-28")
        val state = saved()
        form(state = state).turnOn()
        clock.goTo("2027-03-01")
        val restored = form(state = state.restored())

        restored.onAction(CategoryEditorAction.Submit)

        assertTrue(commands.submitted.isEmpty())
        assertEquals(SaveError.MonthEnded, restored.now.saveError)
    }

    @Test
    fun storageInAnotherMonthRefusesAndTheFormFollowsIt() {
        publish(carrying = false)
        commands.answers += rejected(CategoryRejection.MonthMoved(YearMonth.of(2027, 3)))
        val form = form()
        form.turnOn()
        // The session moves on while the save is on its way.
        commands.hold = CompletableDeferred()
        form.onAction(CategoryEditorAction.Submit)
        clock.goTo("2027-03-01")
        commands.hold!!.complete(Unit)

        assertEquals(SaveError.MonthEnded, form.now.saveError)
        assertEquals(YearMonth.of(2027, 3), form.now.targetMonth)
        assertNull(form.now.carryPending)
        assertNull(form.now.result)
    }

    @Test
    fun aRefusalIsSaidInTheCardAndTheDraftKeepsTheChange() {
        publish(carrying = false)
        commands.answers +=
            rejected(CategoryRejection.CarryRefused(CarryEnableRefusal.DefaultAfterNotFinite))
        val form = form()
        form.turnOn()

        form.onAction(CategoryEditorAction.Submit)

        assertEquals(CarryProblemText.DefaultAfterNotFinite, form.now.carryProblem)
        assertNull(form.now.result)
        assertEquals(CarryCard.Off(feb), form.now.carry)
        assertEquals(true, form.now.carryPending?.on)
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
    fun archivingAsksItsOwnQuestionEvenAfterTurningOffWasConfirmed() {
        publish(carrying = true)
        commands.answers += rejected(CategoryRejection.DropUnconfirmed(sc17))
        val form = form()
        form.turnOff()

        form.onAction(CategoryEditorAction.Archive)
        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        // The −20.000 confirmed for Apagar is never sent for Archivar.
        assertNull(commands.submitted.filterIsInstance<ArchiveCategory>().single().confirmedCarryIn)
        assertEquals(DropReason.Archive, (form.now.carryQuestion as CarryQuestion.Drop).reason)
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

        assertEquals(listOf(null, cop(-20_000)), edits.map { it.confirmedCarryIn })
        assertEquals(LimitEdit.FromThisMonth(BudgetLimit.Unlimited), edits.last().limit)
        assertNull(edits.last().carry)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun turningOffAndSinLimiteTogetherSendTheOneConfirmedBalance() {
        publish(carrying = true)
        val form = form()
        form.turnOff()

        form.onAction(CategoryEditorAction.UnlimitedChanged(true))
        form.onAction(CategoryEditorAction.Submit)

        val sent = edits.single()
        assertEquals(CarrySetting.Off, sent.carry)
        assertEquals(LimitEdit.FromThisMonth(BudgetLimit.Unlimited), sent.limit)
        assertEquals(cop(-20_000), sent.confirmedCarryIn)
    }

    @Test
    fun sinLimiteUndoesADraftTurningCarryOnAndBlocksTheSwitch() {
        publish(carrying = false)
        val form = form()
        form.turnOn()

        form.onAction(CategoryEditorAction.UnlimitedChanged(true))

        assertNull(form.now.carryPending)
        assertEquals(false, form.now.canChangeCarry)
        form.onAction(CategoryEditorAction.EnableCarry)
        assertNull(form.now.carryQuestion)
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
        val first = edits.single()
        assertNull(first.confirmation)
        assertEquals(
            LimitEdit.OfMonth(ene, (BudgetLimit.finite(cop(120_000)) as Outcome.Success).value),
            first.limit
        )
        assertNull(first.carry)

        form.onAction(CategoryEditorAction.CarryConfirmed)

        assertEquals(confirmation, edits.last().confirmation)
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
        form.turnOn()

        form.onAction(CategoryEditorAction.Submit)

        assertEquals(1, commands.submitted.size)
        assertEquals(listOf(commands.submitted.single().operationId), commands.lookups)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun aDoubleTapOnGuardarSendsOnce() {
        publish(carrying = true)
        val form = form()
        form.turnOff()

        commands.hold = CompletableDeferred()
        form.onAction(CategoryEditorAction.Submit)
        form.onAction(CategoryEditorAction.Submit)
        commands.hold!!.complete(Unit)

        assertEquals(1, commands.submitted.size)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun aCarryChangeNotYetSavedComesBackAfterProcessDeathAndIsSavedByGuardar() {
        publish(carrying = true)
        val state = saved()
        form(state = state).turnOff()
        assertTrue(commands.submitted.isEmpty())

        val restored = form(state = state.restored())

        assertEquals(
            CarryPending(on = false, month = feb, forgets = minus("20.000")),
            restored.now.carryPending
        )
        assertTrue(restored.now.changed)
        restored.onAction(CategoryEditorAction.Submit)
        assertEquals(cop(-20_000), edits.single().confirmedCarryIn)
    }

    @Test
    fun aSaveInterruptedByProcessDeathIsSettledByTheSameOperationOnRestart() {
        publish(carrying = true)
        commands.answers += unknown()
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val state = saved()
        val before = form(state = state)
        before.turnOff()
        before.onAction(CategoryEditorAction.Submit)
        assertEquals(SaveError.Unknown, before.now.saveError)
        val sent = edits.single()

        // The process dies; storage had committed it. The new form only asks.
        publish(carrying = false)
        commands.found = { Outcome.Success(committed(it).value) }
        val after = form(state = state.restored())

        assertEquals(1, commands.submitted.size)
        assertEquals(listOf(sent.operationId), commands.lookups.distinct())
        assertEquals(CarryCard.Off(feb), after.now.carry)
        assertNull(after.now.carryPending)
        assertEquals(EditorResult.Updated, after.now.result)
    }

    @Test
    fun aCancelledSaveIsAskedAboutWithItsOperationNeverSentAgain() {
        publish(carrying = true)
        commands.cancelled = 1
        val state = saved()
        val before = form(state = state)
        before.turnOff()
        before.onAction(CategoryEditorAction.Submit)
        val sent = edits.single()
        assertNull(before.now.result)

        // Nothing proves it was rolled back: the next form asks with the same operation.
        commands.found = { Outcome.Success(null) }
        val after = form(state = state.restored())

        assertEquals(1, commands.submitted.size)
        assertEquals(listOf(sent.operationId), commands.lookups)
        assertEquals(SaveError.NotSaved, after.now.saveError)
        // Not saved: the draft still turns carry off with the balance confirmed.
        assertEquals(minus("20.000"), after.now.carryPending?.forgets)
    }

    @Test
    fun aMonthThatCannotBeReadShowsNoSwitchButKeepsTheChangeInSight() {
        publish(carrying = true)
        val state = saved()
        form(state = state).turnOff()
        // The month can no longer be read when the form comes back.
        snapshots.of(feb).value = ReadState.Loading

        val restored = form(state = state.restored())

        assertEquals(CarryCard.Unreadable, restored.now.carry)
        assertEquals(false, restored.now.canChangeCarry)
        assertEquals(
            CarryPending(on = false, month = feb, forgets = minus("20.000")),
            restored.now.carryPending
        )
    }

    @Test
    fun aStaleVersionKeepsTheCarryChangeWhenTheDraftIsKept() {
        publish(carrying = true)
        commands.answers += CommandOutcome.Rejected(CommandRejection.VersionConflict(current = 6))
        val form = form()
        form.turnOff()

        form.onAction(CategoryEditorAction.Submit)
        assertEquals(SaveError.Conflict, form.now.saveError)
        reads.categories = listOf(stored(version = 6))
        form.onAction(CategoryEditorAction.ReloadKeepingDraft)
        form.onAction(CategoryEditorAction.Submit)

        assertEquals(listOf(5L, 6L), edits.map { it.expectedVersion })
        assertEquals(listOf(cop(-20_000), cop(-20_000)), edits.map { it.confirmedCarryIn })
        assertEquals(EditorResult.Updated, form.now.result)
    }
}
