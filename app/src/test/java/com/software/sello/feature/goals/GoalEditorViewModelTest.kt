package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.EditGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Nueva meta and Editar meta through the real goal rules. Figures are the approved
 * examples (docs/testing/goal-examples.md) by ID; today is 23 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalEditorViewModelTest {
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private val clock = HandFinancialClock("2026-10-23")
    private val reads = HandGoalReads()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private val viajeId = "00000000-0000-4000-9000-000000000001"

    private val scheduler = TestCoroutineScheduler()
    private lateinit var corrections: ExpenseCorrections

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = ExpenseCorrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            HandInvestmentCommands(),
            { 5.hours + scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main)
        )
        reads.read = Outcome.Success(GoalBoard(3, 9, emptyList(), null))
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).value()

    private fun goal(
        target: Long,
        saved: Long,
        targetMonth: YearMonth? = YearMonth.of(2026, 12),
        completedOn: LocalDate? = null,
        version: Long = 4
    ): GoalSummary {
        val day = LocalDate.of(2026, 10, 1)
        val history = GoalHistory(
            Goal(
                GoalId.of(viajeId).value(),
                GoalName.of("Viaje a la costa").value(),
                IconKey.of("flight").value(),
                targetMonth,
                completedOn,
                version,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf(
                GoalTargetChange(pesos(target), 0, day),
                GoalMovement(
                    GoalMovementId.of("00000000-0000-4000-8000-000000000001").value(),
                    GoalMovementKind.Contribution,
                    pesos(saved),
                    day,
                    1,
                    day,
                    null,
                    1,
                    Instant.EPOCH,
                    Instant.EPOCH
                )
            )
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun editing(summary: GoalSummary): SavedStateHandle {
        reads.read = Outcome.Success(GoalBoard(3, 9, listOf(summary), null))
        return SavedStateHandle(mapOf("goalId" to viajeId))
    }

    private fun model(saved: SavedStateHandle = SavedStateHandle()) =
        GoalEditorViewModel(saved, reads, commands, clock, money, ids, corrections) { false }

    private fun GoalEditorViewModel.type(vararg keys: String) {
        keys.forEach {
            onAction(
                if (it == "000") GoalEditorAction.TripleZero else GoalEditorAction.Digit(it.toInt())
            )
        }
    }

    private fun GoalEditorViewModel.casaPropia() {
        onAction(GoalEditorAction.NameChanged("Casa propia"))
        onAction(GoalEditorAction.IconPicked("home"))
        type("2", "0", "000", "000")
    }

    private fun created(id: String = viajeId) = CommandOutcome.Committed(
        CommittedGoal(
            OperationReceipt(
                OperationId.of(ids.next()).value(),
                OperationKind.CreateGoal,
                3,
                10,
                Instant.EPOCH
            ),
            GoalId.of(id).value(),
            null
        ),
        replayed = false
    )

    @Test
    fun mt34CrearMetaWaitsForAllThreeThenPlansWholePesosAndSendsOneCreate() = runTest {
        val model = model()
        model.onAction(GoalEditorAction.NameChanged("Casa propia"))
        assertFalse(model.state.value.canSubmit)
        model.onAction(GoalEditorAction.IconPicked("home"))
        assertFalse(model.state.value.canSubmit)
        model.type("2", "0", "000", "000")
        assertEquals("20.000.000", model.state.value.amount!!.digits)
        assertTrue(model.state.value.canSubmit)

        model.onAction(GoalEditorAction.ChooseMonth)
        model.onAction(GoalEditorAction.PickMonth(YearMonth.of(2028, 12)))

        val state = model.state.value
        assertFalse(state.choosingMonth)
        assertEquals("769.231", state.pace!!.monthly.digits)
        assertEquals(26, state.pace.months)
        assertEquals(
            listOf(
                PlanCaption.Month(YearMonth.of(2027, 5)),
                PlanCaption.Month(YearMonth.of(2027, 11)),
                PlanCaption.Month(YearMonth.of(2028, 6)),
                PlanCaption.Month(YearMonth.of(2028, 12))
            ),
            state.places.map { it.caption }
        )

        commands.next = created()
        model.onAction(GoalEditorAction.Submit)
        model.onAction(GoalEditorAction.Submit)

        val sent = commands.sent.single() as CreateGoal
        assertEquals("Casa propia", sent.name.value)
        assertEquals("home", sent.icon.value)
        assertEquals(pesos(20_000_000), sent.target)
        assertEquals(YearMonth.of(2028, 12), sent.targetMonth)
        assertEquals(3L, sent.generation)
        assertEquals(GoalEditorResult.Created(viajeId), model.state.value.result)
    }

    @Test
    fun sinFechaInTheMonthSheetClearsTheMonthAndClosesTheSheet() = runTest {
        val model = model()
        model.casaPropia()
        model.onAction(GoalEditorAction.ChooseMonth)
        model.onAction(GoalEditorAction.PickMonth(YearMonth.of(2028, 12)))
        model.onAction(GoalEditorAction.ChooseMonth)

        model.onAction(GoalEditorAction.NoDate)

        assertNull(model.state.value.targetMonth)
        assertFalse(model.state.value.choosingMonth)
    }

    @Test
    fun mt36WithoutAMonthThereIsNoPaceAndNoProjectedMonth() = runTest {
        val model = model()
        model.casaPropia()
        model.onAction(GoalEditorAction.PickMonth(YearMonth.of(2028, 12)))
        model.onAction(GoalEditorAction.NoDate)

        assertNull(model.state.value.targetMonth)
        assertNull(model.state.value.pace)
        assertEquals(List(4) { null }, model.state.value.places.map { it.caption })
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun thisMonthOrAnEarlierOneIsNotOffered() = runTest {
        val model = model()
        model.casaPropia()

        model.onAction(GoalEditorAction.PickMonth(YearMonth.of(2026, 10)))
        model.onAction(GoalEditorAction.PickMonth(YearMonth.of(2026, 3)))

        assertNull(model.state.value.targetMonth)
        assertEquals(YearMonth.of(2026, 11), model.state.value.firstMonth)
    }

    @Test
    fun mt37FortyCharactersAreANameAndFortyOneKeepTheTextButWait() = runTest {
        val model = model()
        model.casaPropia()
        model.onAction(GoalEditorAction.NameChanged("a".repeat(40)))
        assertNull(model.state.value.nameError)
        assertTrue(model.state.value.canSubmit)

        model.onAction(GoalEditorAction.NameChanged("a".repeat(41)))

        assertEquals("a".repeat(41), model.state.value.name)
        assertEquals(41, model.state.value.nameLength)
        assertEquals(GoalNameError.TooLong, model.state.value.nameError)
        assertFalse(model.state.value.canSubmit)
        model.onAction(GoalEditorAction.Submit)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun mt38TheTargetTakesTwelveDigitsAndRefusesAThirteenth() = runTest {
        val model = model()
        model.type("9", "9", "9", "9", "9", "9", "9", "9", "9", "9", "9", "9")
        assertEquals("999.999.999.999", model.state.value.amount!!.digits)
        val pulse = model.state.value.amountPulse

        model.type("9")

        assertEquals("999.999.999.999", model.state.value.amount!!.digits)
        assertEquals(pulse + 1, model.state.value.amountPulse)
    }

    @Test
    fun mt17EditingTheTargetPreviewsTheKeptStampAndSendsOnlyTheTarget() = runTest {
        val model = model(editing(goal(3_000_000, 1_710_000)))
        assertEquals("Viaje a la costa", model.state.value.name)
        assertEquals("3.000.000", model.state.value.amount!!.digits)
        assertFalse(model.state.value.changed)
        assertFalse(model.state.value.canSubmit)

        model.onAction(GoalEditorAction.ClearAmount)
        model.type("4", "000", "000")

        assertEquals(
            listOf(PlaceLook.Earned, PlaceLook.Kept, PlaceLook.Empty, PlaceLook.Empty),
            model.state.value.places.map { it.place.look }
        )
        assertEquals(
            listOf(PlanCaption.Earned, PlanCaption.Earned),
            model.state.value.places.take(2).map { it.caption }
        )
        model.onAction(GoalEditorAction.Submit)
        val sent = commands.sent.single() as ChangeGoalTarget
        assertEquals(pesos(4_000_000) to 4L, sent.target to sent.expectedVersion)
        assertEquals(GoalEditorResult.Updated, model.state.value.result)
    }

    /** What storage answers for a target change that did [change] to the stamps. */
    private fun targetChanged(change: GoalChange?, replayed: Boolean = false) =
        CommandOutcome.Committed(
            CommittedGoal(
                OperationReceipt(
                    OperationId.of(ids.next()).value(),
                    OperationKind.ChangeGoalTarget,
                    3,
                    10,
                    Instant.EPOCH
                ),
                GoalId.of(viajeId).value(),
                change
            ),
            replayed
        )

    @Test
    fun mt18ATargetChangeThatReachesSeventyFivePercentCelebratesItOnceSaved() = runTest {
        val model = model(editing(goal(3_000_000, 1_710_000)))
        model.onAction(GoalEditorAction.ClearAmount)
        model.type("2", "000", "000")
        commands.pause = true

        model.onAction(GoalEditorAction.Submit)
        assertNull(corrections.notice.value)
        commands.release(
            targetChanged(GoalChange(listOf(GoalPlace.ThreeQuarters), emptyList(), false))
        )

        val notice = corrections.notice.value as CorrectionNotice.GoalTargetChanged
        assertEquals(
            GoalCelebration(
                viajeId,
                "Viaje a la costa",
                listOf(GoalPlace.ThreeQuarters),
                emptyList(),
                false
            ),
            notice.celebration.copy(missing = null)
        )
        assertEquals("290.000", notice.celebration.missing!!.digits)
        assertEquals(GoalEditorResult.Updated, model.state.value.result)
    }

    @Test
    fun mt17ATargetChangeThatEarnsNothingOrIsAReplayCelebratesNothing() = runTest {
        val raised = model(editing(goal(3_000_000, 1_710_000)))
        raised.onAction(GoalEditorAction.ClearAmount)
        raised.type("4", "000", "000")
        commands.queue += targetChanged(GoalChange(emptyList(), emptyList(), false))
        raised.onAction(GoalEditorAction.Submit)
        assertNull(corrections.notice.value)

        val replay = model(editing(goal(3_000_000, 1_710_000)))
        replay.onAction(GoalEditorAction.ClearAmount)
        replay.type("2", "000", "000")
        commands.queue += targetChanged(change = null, replayed = true)
        replay.onAction(GoalEditorAction.Submit)

        assertNull(corrections.notice.value)
        assertEquals(GoalEditorResult.Updated, replay.state.value.result)
    }

    @Test
    fun anEditOfNameAndTargetSendsBothInOrderAtTheRisingVersion() = runTest {
        val model = model(editing(goal(3_000_000, 1_710_000)))
        model.onAction(GoalEditorAction.NameChanged("Viaje a Cartagena"))
        model.onAction(GoalEditorAction.ClearAmount)
        model.type("2", "000", "000")

        model.onAction(GoalEditorAction.Submit)

        val (details, target) = commands.sent
        assertEquals(
            "Viaje a Cartagena" to 4L,
            (details as EditGoal).name.value to details.expectedVersion
        )
        assertEquals(YearMonth.of(2026, 12), details.targetMonth)
        assertEquals(5L, (target as ChangeGoalTarget).expectedVersion)
        assertEquals(GoalEditorResult.Updated, model.state.value.result)
    }

    @Test
    fun mt35EditingWithSavedMoneyPlansOnlyWhatIsMissing() = runTest {
        val model = model(editing(goal(20_000_000, 2_000_000, YearMonth.of(2028, 12))))

        assertEquals("692.308", model.state.value.pace!!.monthly.digits)
        assertEquals(
            listOf(
                PlanCaption.Month(YearMonth.of(2027, 3)),
                PlanCaption.Month(YearMonth.of(2027, 10)),
                PlanCaption.Month(YearMonth.of(2028, 5)),
                PlanCaption.Month(YearMonth.of(2028, 12))
            ),
            model.state.value.places.map { it.caption }
        )
    }

    @Test
    fun aDateWithNothingMissingHasNoPace() = runTest {
        val model = model(editing(goal(1_000_000, 1_200_000, YearMonth.of(2027, 6))))

        assertNull(model.state.value.pace)
        assertTrue(model.state.value.nothingMissing)
    }

    @Test
    fun anUnknownCreateIsAskedAboutAndNothingNewIsSentUntilKnown() = runTest {
        val model = model()
        model.casaPropia()
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(GoalEditorAction.Submit)
        val operation = commands.sent.single().operationId
        assertEquals(GoalSaveError.Unknown, model.state.value.saveError)
        model.onAction(GoalEditorAction.Submit)
        assertEquals(1, commands.sent.size)

        commands.found = Outcome.Success(created().value)
        model.onAction(GoalEditorAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertEquals(GoalEditorResult.Created(viajeId), model.state.value.result)
    }

    @Test
    fun afterProcessDeathTheDraftIsBackAndASaveInFlightIsFoundNotSentAgain() = runTest {
        val saved = SavedStateHandle()
        val first = model(saved)
        first.casaPropia()
        first.onAction(GoalEditorAction.PickMonth(YearMonth.of(2028, 12)))
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        first.onAction(GoalEditorAction.Submit)

        commands.found = Outcome.Success(created().value)
        val restored = model(saved)

        assertEquals("Casa propia", restored.state.value.name)
        assertEquals("home", restored.state.value.iconKey)
        assertEquals("20.000.000", restored.state.value.amount!!.digits)
        assertEquals(YearMonth.of(2028, 12), restored.state.value.targetMonth)
        assertEquals(1, commands.sent.size)
        assertEquals(GoalEditorResult.Created(viajeId), restored.state.value.result)
    }

    @Test
    fun aDraftWithoutASaveIsRestored() = runTest {
        val saved = SavedStateHandle()
        val first = model(saved)
        first.onAction(GoalEditorAction.NameChanged("Casa"))
        first.onAction(GoalEditorAction.Digit(5))

        val restored = model(saved)

        assertEquals("Casa", restored.state.value.name)
        assertEquals("5", restored.state.value.amount!!.digits)
    }

    @Test
    fun mt27AGoalIsDeletedFromEditAfterTheConfirmSlip() = runTest {
        val model = model(editing(goal(3_000_000, 1_710_000)))
        assertEquals(1, model.state.value.movementCount)

        model.onAction(GoalEditorAction.Delete)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
        model.onAction(GoalEditorAction.AskToDelete)
        assertTrue(model.state.value.confirmDelete)
        model.onAction(GoalEditorAction.Delete)

        val sent = commands.sent.single() as DeleteGoal
        assertEquals(GoalId.of(viajeId).value() to 4L, sent.goalId to sent.expectedVersion)
        assertEquals(GoalEditorResult.Deleted, model.state.value.result)
    }

    @Test
    fun aCompletedGoalCanOnlyBeDeleted() = runTest {
        val model = model(editing(goal(400_000, 400_000, completedOn = LocalDate.of(2026, 9, 30))))
        assertTrue(model.state.value.completed)

        model.onAction(GoalEditorAction.NameChanged("Otra cosa"))
        model.onAction(GoalEditorAction.Submit)
        assertEquals("Viaje a la costa", model.state.value.name)
        assertEquals(emptyList<GoalCommand>(), commands.sent)

        model.onAction(GoalEditorAction.AskToDelete)
        model.onAction(GoalEditorAction.Delete)
        assertTrue(commands.sent.single() is DeleteGoal)
    }

    @Test
    fun aGoalChangedMeanwhileIsReadAgainNotOverwritten() = runTest {
        val model = model(editing(goal(3_000_000, 1_710_000)))
        model.onAction(GoalEditorAction.NameChanged("Viaje a Cartagena"))
        commands.next = CommandOutcome.Rejected(CommandRejection.VersionConflict(5))
        val before = reads.reads

        model.onAction(GoalEditorAction.Submit)

        assertEquals(GoalSaveError.Changed, model.state.value.saveError)
        assertTrue(reads.reads > before)
        assertEquals("Viaje a Cartagena", model.state.value.name)
        assertNull(model.state.value.result)
    }

    @Test
    fun leavingAsksOnlyWhenSomethingWouldBeLost() = runTest {
        val clean = model()
        clean.onAction(GoalEditorAction.Back)
        assertEquals(GoalEditorResult.Discarded, clean.state.value.result)

        val dirty = model()
        dirty.onAction(GoalEditorAction.NameChanged("Casa"))
        dirty.onAction(GoalEditorAction.Back)
        assertTrue(dirty.state.value.confirmDiscard)
        dirty.onAction(GoalEditorAction.KeepEditing)
        assertFalse(dirty.state.value.confirmDiscard)
        dirty.onAction(GoalEditorAction.Back)
        dirty.onAction(GoalEditorAction.DiscardConfirmed)
        assertEquals(GoalEditorResult.Discarded, dirty.state.value.result)
    }

    @Test
    fun aFailedFirstReadIsAFailureWithRetry() = runTest {
        reads.read = Outcome.Failure(StorageFailure.Unavailable("x"))
        val model = model()
        assertTrue(model.state.value.loadFailed)

        reads.read = Outcome.Success(GoalBoard(3, 9, emptyList(), null))
        model.onAction(GoalEditorAction.Retry)

        assertFalse(model.state.value.loadFailed)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun onlyTheOfferedIconsCanBeChosen() = runTest {
        val model = model()
        model.onAction(GoalEditorAction.IconPicked("rocket"))
        assertNull(model.state.value.iconKey)
        // A goal offers the same 35 icons as a category, in the same order.
        model.onAction(GoalEditorAction.IconPicked("savings"))
        assertEquals("savings", model.state.value.iconKey)
        assertEquals(CategoryIcons.keys, GoalIcons.options.map { it.key })
        assertEquals(35, GoalIcons.options.size)
        assertEquals(GoalPlace.entries, model.state.value.places.map { it.place.place })
    }

    @Test
    fun mt26AGoalReachedOutsideSelloIsMarkedFromEditAndTheFormEndsAfterTheMoment() = runTest {
        // 1.710.000 of 3.000.000, 57 %: marked lograda as it is stored.
        val model = model(editing(goal(3_000_000, 1_710_000)))
        model.onAction(GoalEditorAction.NameChanged("Otro nombre"))

        model.onAction(GoalEditorAction.MarkComplete)

        val sent = commands.sent.single() as CompleteGoal
        assertEquals(GoalId.of(viajeId).value() to 4L, sent.goalId to sent.expectedVersion)
        val moment = model.state.value.completion as CompletionState.Celebrating
        assertEquals(
            "Viaje a la costa" to "1.710.000",
            moment.plate.name to moment.plate.saved.digits
        )
        model.onAction(GoalEditorAction.CelebrationDone)
        assertEquals(GoalEditorResult.Updated, model.state.value.result)
    }
}
