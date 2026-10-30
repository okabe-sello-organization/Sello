package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordGoalMovements
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Aportar varios through the real goal rules. Viaje a la costa holds 750.000 + 960.000 =
 * 1.710.000 of 3.000.000, as in the approved mock; today is 23 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalBulkViewModelTest {
    private val money = testMoney()
    private val clock = HandFinancialClock("2026-10-23")
    private val today = LocalDate.of(2026, 10, 23)
    private val reads = HandGoalReads()
    private val commands = HandGoalCommands()
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private lateinit var corrections: Corrections

    private val viaje = "00000000-0000-4000-9000-000000000001"
    private val celular = "00000000-0000-4000-9000-000000000002"
    private val vacunas = "00000000-0000-4000-9000-000000000003"

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            commands,
            HandInvestmentCommands(),
            { scheduler.currentTime.milliseconds },
            ids,
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        reads.board.value = board()
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun board(version: Long = 5, generation: Long = 3) = ReadState.Ready(
        GoalBoard(
            generation,
            9,
            listOf(
                goal(
                    viaje,
                    "Viaje a la costa",
                    3_000_000,
                    version,
                    aporte(11, 750_000),
                    aporte(12, 960_000)
                ),
                goal(celular, "Celular nuevo", 1_200_000, 5, aporte(21, 450_000)),
                goal(vacunas, "Vacunas de Luna", 400_000, 5, aporte(31, 400_000), done = true)
            ),
            null
        )
    )

    private fun aporte(number: Int, amount: Long) = GoalMovement(
        GoalMovementId.of("00000000-0000-4000-8000-%012d".format(number)).value(),
        GoalMovementKind.Contribution,
        pesos(amount),
        LocalDate.of(2026, 10, 1),
        (number % 10).toLong(),
        LocalDate.of(2026, 10, 1),
        null,
        2,
        Instant.EPOCH,
        Instant.EPOCH
    )

    private fun goal(
        id: String,
        name: String,
        target: Long,
        version: Long,
        vararg movements: GoalMovement,
        done: Boolean = false
    ): GoalSummary {
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of(name).value(),
                IconKey.of("flight").value(),
                null,
                LocalDate.of(2026, 10, 2).takeIf { done },
                version,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf<GoalEvent>(GoalTargetChange(pesos(target), 0, LocalDate.of(2026, 10, 1))) +
                movements
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun model(
        goal: String = viaje,
        saved: SavedStateHandle = SavedStateHandle(mapOf("goalId" to goal))
    ) = GoalBulkViewModel(saved, reads, commands, clock, ids, money, corrections)

    private fun GoalBulkViewModel.type(digits: String) = digits.forEach {
        onAction(GoalBulkAction.Digit(it.digitToInt()))
    }

    private fun GoalBulkViewModel.line(withdrawal: Boolean, digits: String, day: Int? = null) {
        onAction(GoalBulkAction.Kind(withdrawal))
        type(digits)
        day?.let { onAction(GoalBulkAction.DatePicked(LocalDate.of(2026, 10, it))) }
    }

    /** The mock's receipt: +100.000 (12 oct), +80.000 (14 oct), −50.000 (16 oct). */
    private fun GoalBulkViewModel.threeLines() {
        line(withdrawal = false, "100000", day = 12)
        onAction(GoalBulkAction.AddLine)
        type("80000")
        onAction(GoalBulkAction.DatePicked(LocalDate.of(2026, 10, 14)))
        onAction(GoalBulkAction.AddLine)
        line(withdrawal = true, "50000", day = 16)
    }

    private fun committed(change: GoalChange?) = CommandOutcome.Committed(
        CommittedGoal(
            OperationReceipt(
                OperationId.of(ids.next()).value(),
                OperationKind.RecordGoalMovements,
                3,
                10,
                Instant.EPOCH
            ),
            GoalId.of(celular).value(),
            change,
            emptyList()
        ),
        replayed = false
    )

    @Test
    fun itOpensWithOneEmptyLineWithoutATypeDatedToday() = runTest {
        val state = model().state.value

        assertEquals("Viaje a la costa", state.goalName)
        assertEquals(1, state.lines.size)
        assertEquals(0, state.highlighted)
        assertNull(state.line.withdrawal)
        assertNull(state.line.amount)
        assertEquals(today, state.line.date)
        assertEquals("0", state.total!!.digits)
        assertFalse(state.canSubmit)
        assertFalse(state.canAdd)
    }

    @Test
    fun aNewLineCopiesTheTypeAndDateAboveAndTheNetIsAportesMinusRetiros() = runTest {
        val model = model()
        model.line(withdrawal = true, "50000", day = 16)
        model.onAction(GoalBulkAction.AddLine)

        val added = model.state.value.line
        assertEquals(1, model.state.value.highlighted)
        assertEquals(true to LocalDate.of(2026, 10, 16), added.withdrawal to added.date)
        assertNull(added.amount)

        model.onAction(GoalBulkAction.Select(0))
        model.onAction(GoalBulkAction.ClearAmount)
        model.line(withdrawal = false, "100000", day = 12)
        model.onAction(GoalBulkAction.Select(1))
        model.onAction(GoalBulkAction.Kind(withdrawal = false))
        model.type("80000")
        model.onAction(GoalBulkAction.AddLine)
        model.line(withdrawal = true, "50000")

        // +100.000 + 80.000 − 50.000 = +130.000
        val total = model.state.value.total!!
        assertEquals(MoneySign.Plus to "130.000", total.sign to total.digits)
        assertEquals(3, model.state.value.counted)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun theReceiptIsOneCommandAtTheVersionReadWithEachLinesKindAmountDateAndNote() = runTest {
        val model = model()
        model.threeLines()
        model.onAction(GoalBulkAction.OpenComposer)
        model.onAction(GoalBulkAction.ComposerChanged("Arreglo del carro"))
        model.onAction(GoalBulkAction.ComposerDone)

        model.onAction(GoalBulkAction.Submit)
        model.onAction(GoalBulkAction.Submit)

        val sent = commands.sent.single() as RecordGoalMovements
        assertEquals(GoalId.of(viaje).value() to 5L, sent.goalId to sent.expectedVersion)
        assertEquals(3L, sent.generation)
        assertEquals(
            listOf(
                Triple(GoalMovementKind.Contribution, pesos(100_000), LocalDate.of(2026, 10, 12)),
                Triple(GoalMovementKind.Contribution, pesos(80_000), LocalDate.of(2026, 10, 14)),
                Triple(GoalMovementKind.Withdrawal, pesos(50_000), LocalDate.of(2026, 10, 16))
            ),
            sent.lines.map { Triple(it.kind, it.amount, it.date) }
        )
        assertEquals(listOf(null, null, "Arreglo del carro"), sent.lines.map { it.note?.value })
        assertTrue(model.state.value.closed)
        val notice = corrections.notice.value as CorrectionNotice.GoalMovementsRecorded
        assertEquals(3, notice.count)
        assertEquals(MoneySign.Plus to "130.000", notice.net!!.sign to notice.net!!.digits)
        assertNull(notice.celebration)
    }

    @Test
    fun aLineWithAnAmountAndNoTypeBlocksAndStaysOutOfTheTotal() = runTest {
        val fresh = model()
        fresh.type("80000")
        assertEquals(1, fresh.state.value.missing)
        assertTrue(fresh.state.value.line.missingType)
        assertFalse(fresh.state.value.canSubmit)
        assertEquals("0", fresh.state.value.total!!.digits)
        fresh.onAction(GoalBulkAction.Submit)
        assertEquals(emptyList<GoalCommand>(), commands.sent)

        fresh.onAction(GoalBulkAction.Kind(withdrawal = true))
        assertEquals(0, fresh.state.value.missing)
        val total = fresh.state.value.total!!
        assertEquals(MoneySign.Minus to "80.000", total.sign to total.digits)
    }

    @Test
    fun linesWithoutAnAmountAreIgnoredAndTheButtonCountsTheRest() = runTest {
        val model = model()
        model.line(withdrawal = false, "100000")
        model.onAction(GoalBulkAction.AddLine)
        model.type("5")
        model.onAction(GoalBulkAction.AddLine)
        model.type("7")
        model.onAction(GoalBulkAction.Select(1))
        model.onAction(GoalBulkAction.ClearAmount)

        assertEquals(2, model.state.value.counted)
        assertTrue(model.state.value.canSubmit)
        model.onAction(GoalBulkAction.Submit)

        val sent = commands.sent.single() as RecordGoalMovements
        assertEquals(listOf(pesos(100_000), pesos(7)), sent.lines.map { it.amount })
        assertEquals(2, (corrections.notice.value as CorrectionNotice.GoalMovementsRecorded).count)
    }

    @Test
    fun theReceiptTakesThirtyLinesAndNoMore() = runTest {
        val model = model()
        model.line(withdrawal = false, "20000")
        repeat(29) {
            model.onAction(GoalBulkAction.AddLine)
            model.type("20000")
        }
        assertEquals(30, model.state.value.lines.size)
        assertEquals(29, model.state.value.highlighted)
        assertTrue(model.state.value.full)
        assertFalse(model.state.value.canAdd)

        model.onAction(GoalBulkAction.AddLine)

        assertEquals(30, model.state.value.lines.size)
        // 30 aportes of 20.000 = +600.000
        assertEquals("600.000", model.state.value.total!!.digits)
        model.onAction(GoalBulkAction.Submit)
        assertEquals(30, (commands.sent.single() as RecordGoalMovements).lines.size)
    }

    @Test
    fun removingALineKeepsAValidHighlightAndTheLastLineStays() = runTest {
        val model = model()
        model.threeLines()
        assertEquals(2, model.state.value.highlighted)

        model.onAction(GoalBulkAction.Remove(2))
        assertEquals(
            listOf("100.000", "80.000"),
            model.state.value.lines.map {
                it.amount!!.digits
            }
        )
        assertEquals(1, model.state.value.highlighted)

        model.onAction(GoalBulkAction.Remove(0))
        assertEquals(0, model.state.value.highlighted)
        assertEquals("80.000", model.state.value.line.amount!!.digits)

        model.onAction(GoalBulkAction.Remove(0))
        model.onAction(GoalBulkAction.Remove(5))
        assertEquals(1, model.state.value.lines.size)
        assertEquals(0, model.state.value.highlighted)
    }

    @Test
    fun removingAnEarlierLineKeepsTheSameLineHighlighted() = runTest {
        val model = model()
        model.threeLines()
        model.onAction(GoalBulkAction.Select(1))

        model.onAction(GoalBulkAction.Remove(0))

        assertEquals(0, model.state.value.highlighted)
        assertEquals("80.000", model.state.value.line.amount!!.digits)
    }

    @Test
    fun aRemovedLineCanBePutBackForAShortWhile() = runTest {
        val model = model()
        model.threeLines()

        model.onAction(GoalBulkAction.Remove(1))
        assertEquals(RemovedGoalLine(withdrawal = false), model.state.value.removed)
        model.onAction(GoalBulkAction.UndoRemove)

        assertEquals(
            listOf("100.000", "80.000", "50.000"),
            model.state.value.lines.map { it.amount!!.digits }
        )
        assertEquals(1, model.state.value.highlighted)
        assertNull(model.state.value.removed)

        model.onAction(GoalBulkAction.Remove(1))
        advanceTimeBy(GoalBulkViewModel.UNDO_WINDOW.inWholeMilliseconds - 1)
        assertNotNull(model.state.value.removed)
        advanceTimeBy(2)
        assertNull(model.state.value.removed)
        model.onAction(GoalBulkAction.UndoRemove)
        assertEquals(2, model.state.value.lines.size)
    }

    @Test
    fun aRefusedRetiroIsMarkedWithWhatIsAvailableAndTheDraftStays() = runTest {
        val model = model()
        model.line(withdrawal = false, "100000", day = 12)
        model.onAction(GoalBulkAction.AddLine)
        model.type("5")
        model.onAction(GoalBulkAction.AddLine)
        model.line(withdrawal = true, "2000000", day = 16)
        model.onAction(GoalBulkAction.Select(1))
        model.onAction(GoalBulkAction.ClearAmount)
        // The empty line is not sent: the retiro is the receipt's line 1, counted from 0.
        // 1.710.000 + 100.000 = 1.810.000 available before it.
        commands.next = CommandOutcome.Rejected(
            CommandRejection.Specific(GoalRejection.LineAboveSaved(1, pesos(1_810_000).money))
        )

        model.onAction(GoalBulkAction.Submit)

        val state = model.state.value
        assertEquals(GoalBulkError.Refused, state.error)
        assertEquals(listOf(null, null, "1.810.000"), state.lines.map { it.available?.digits })
        assertEquals(1, state.refused)
        assertFalse(state.canSubmit)
        assertFalse(state.closed)
        assertNull(corrections.notice.value)
        assertEquals(3, state.lines.size)

        model.onAction(GoalBulkAction.Select(2))
        model.onAction(GoalBulkAction.Backspace)
        assertEquals(0, model.state.value.refused)
        assertNull(model.state.value.error)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun aLineDatedAfterTodayByStorageIsMarkedOnItsLine() = runTest {
        val model = model()
        model.threeLines()
        commands.next = CommandOutcome.Rejected(
            CommandRejection.Specific(GoalRejection.LineFutureDate(0, today))
        )

        model.onAction(GoalBulkAction.Submit)

        assertEquals(listOf(true, false, false), model.state.value.lines.map { it.futureDate })
        assertEquals(GoalBulkError.Refused, model.state.value.error)
    }

    @Test
    fun aGoalThatChangedMeanwhileAsksToReviewAndSavesAgainAtTheNewVersion() = runTest {
        val model = model()
        model.threeLines()
        commands.next = CommandOutcome.Rejected(CommandRejection.VersionConflict(6))

        model.onAction(GoalBulkAction.Submit)
        assertEquals(GoalBulkError.Changed, model.state.value.error)
        assertTrue(model.state.value.editable)
        assertEquals(3, model.state.value.counted)

        reads.board.value = board(version = 6)
        model.onAction(GoalBulkAction.Submit)

        assertEquals(
            listOf(5L, 6L),
            commands.sent.map {
                (it as RecordGoalMovements).expectedVersion
            }
        )
        assertNotEquals(commands.sent[0].operationId, commands.sent[1].operationId)
        assertTrue(model.state.value.closed)
    }

    @Test
    fun replacedDataHoldsTheDraftStill() = runTest {
        val model = model()
        model.threeLines()
        commands.next = CommandOutcome.Rejected(CommandRejection.StaleGeneration(4))

        model.onAction(GoalBulkAction.Submit)

        assertEquals(GoalBulkError.DataChanged, model.state.value.error)
        assertFalse(model.state.value.editable)
        model.type("1")
        assertEquals("50.000", model.state.value.line.amount!!.digits)
    }

    @Test
    fun aStorageFailureSaysNotSavedAndTheSameDraftCanBeSentAgain() = runTest {
        val model = model()
        model.threeLines()
        commands.next = CommandOutcome.Rejected(
            CommandRejection.StorageFailed(StorageFailure.Unavailable("disk"))
        )

        model.onAction(GoalBulkAction.Submit)
        assertEquals(GoalBulkError.NotSaved, model.state.value.error)
        assertTrue(model.state.value.canSubmit)

        model.onAction(GoalBulkAction.Submit)
        assertEquals(2, commands.sent.size)
        assertTrue(model.state.value.closed)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithTheSameOperationAndNothingNewIsSent() = runTest {
        val model = model()
        model.threeLines()
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        model.onAction(GoalBulkAction.Submit)
        val operation = commands.sent.single().operationId
        assertEquals(GoalBulkError.Unknown, model.state.value.error)
        assertFalse(model.state.value.editable)
        model.type("1")
        model.onAction(GoalBulkAction.Submit)
        model.onAction(GoalBulkAction.Back)
        assertEquals(1, commands.sent.size)
        assertFalse(model.state.value.confirmDiscard)

        // Found by asking: saved, but its receipt was not seen, so nothing is celebrated.
        commands.found = Outcome.Success(
            committed(GoalChange(listOf(GoalPlace.Quarter), emptyList(), false)).value
                .copy(movementIds = null)
        )
        model.onAction(GoalBulkAction.Retry)

        assertEquals(listOf(operation, operation), commands.asked)
        assertTrue(model.state.value.closed)
        val notice = corrections.notice.value as CorrectionNotice.GoalMovementsRecorded
        assertEquals(3 to "130.000", notice.count to notice.net!!.digits)
        assertNull(notice.celebration)
    }

    @Test
    fun anUnknownOutcomeFoundNotWrittenSaysNotSaved() = runTest {
        val model = model()
        model.threeLines()
        commands.next = CommandOutcome.OutcomeUnknown(OperationId.of(ids.next()).value())
        commands.found = Outcome.Success(null)

        model.onAction(GoalBulkAction.Submit)

        assertEquals(GoalBulkError.NotSaved, model.state.value.error)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun whileSavingTheDraftHoldsStillAndASecondSubmitSendsNothing() = runTest {
        val model = model()
        model.threeLines()
        commands.pause = true

        model.onAction(GoalBulkAction.Submit)
        assertTrue(model.state.value.busy)
        model.onAction(GoalBulkAction.Submit)
        model.type("9")
        model.onAction(GoalBulkAction.Remove(0))
        model.onAction(GoalBulkAction.Back)

        assertEquals(1, commands.sent.size)
        assertEquals(3, model.state.value.lines.size)
        assertEquals("50.000", model.state.value.line.amount!!.digits)
        commands.release(committed(null))
        assertTrue(model.state.value.closed)
    }

    @Test
    fun aReceiptThatEarnsAStampCelebratesOnceWithWhatIsLeft() = runTest {
        val model = model(goal = celular)
        // 450.000 + 200.000 + 100.000 = 750.000 of 1.200.000: past 50 % (600.000), 450.000
        // left, worked out from the goal as it was when the receipt was sent.
        model.line(withdrawal = false, "200000")
        model.onAction(GoalBulkAction.AddLine)
        model.type("100000")
        commands.pause = true
        model.onAction(GoalBulkAction.Submit)
        // The goal is read again with the receipt in it before the commit is answered.
        reads.board.value = ReadState.Ready(
            GoalBoard(
                3,
                10,
                listOf(goal(celular, "Celular nuevo", 1_200_000, 6, aporte(21, 750_000))),
                null
            )
        )

        commands.release(
            committed(GoalChange(listOf(GoalPlace.Quarter, GoalPlace.Half), emptyList(), false))
        )

        val notice = corrections.notice.value as CorrectionNotice.GoalMovementsRecorded
        val celebration = notice.celebration!!
        assertEquals(celular to "Celular nuevo", celebration.goalId to celebration.goalName)
        assertEquals(listOf(GoalPlace.Quarter, GoalPlace.Half), celebration.earned)
        assertEquals("450.000", celebration.missing!!.digits)
    }

    @Test
    fun aReceiptThatEarnsNothingOrIsAReplayCelebratesNothing() = runTest {
        val plain = model(goal = celular)
        plain.line(withdrawal = false, "1000")
        commands.next = committed(GoalChange(emptyList(), emptyList(), false))
        plain.onAction(GoalBulkAction.Submit)
        assertNull((corrections.notice.value as CorrectionNotice.GoalMovementsRecorded).celebration)

        val replay = model(goal = celular)
        replay.line(withdrawal = false, "1000")
        commands.next = committed(change = null).copy(replayed = true)
        replay.onAction(GoalBulkAction.Submit)
        assertNull((corrections.notice.value as CorrectionNotice.GoalMovementsRecorded).celebration)
    }

    @Test
    fun afterProcessDeathTheLinesTheHighlightAndTheOpenNoteAreBack() = runTest {
        val saved = SavedStateHandle(mapOf("goalId" to viaje))
        val first = model(saved = saved)
        first.threeLines()
        first.onAction(GoalBulkAction.Select(1))
        first.onAction(GoalBulkAction.OpenComposer)
        first.onAction(GoalBulkAction.ComposerChanged("Regalo de mamá"))

        val restored = model(saved = saved).state.value

        assertEquals(
            listOf("100.000", "80.000", "50.000"),
            restored.lines.map {
                it.amount!!.digits
            }
        )
        assertEquals(listOf(false, false, true), restored.lines.map { it.withdrawal })
        assertEquals(
            listOf(12, 14, 16).map { LocalDate.of(2026, 10, it) },
            restored.lines.map { it.date }
        )
        assertEquals(1, restored.highlighted)
        assertTrue(restored.composerOpen)
        assertEquals("Regalo de mamá", restored.composerDraft)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }

    @Test
    fun afterProcessDeathAnOperationInFlightIsAskedAboutAndNeverSentAgain() = runTest {
        val saved = SavedStateHandle(mapOf("goalId" to viaje))
        val first = model(saved = saved)
        first.threeLines()
        commands.pause = true
        first.onAction(GoalBulkAction.Submit)
        val operation = commands.sent.single().operationId
        commands.pause = false
        commands.found = Outcome.Success(committed(null).value.copy(movementIds = null))

        val restored = model(saved = saved)

        assertEquals(listOf(operation), commands.asked)
        assertEquals(1, commands.sent.size)
        assertTrue(restored.state.value.closed)
        val notice = corrections.notice.value as CorrectionNotice.GoalMovementsRecorded
        assertEquals(3, notice.count)
        assertNull(notice.celebration)
    }

    @Test
    fun aStoredDraftThatIsNotWellFormedOpensAFreshReceipt() = runTest {
        val saved = SavedStateHandle(mapOf("goalId" to viaje))
        val first = model(saved = saved)
        first.threeLines()
        saved["goal.bulk.draft"] = arrayListOf<String?>("not", "a", "draft")

        val restored = model(saved = saved).state.value

        assertEquals(1, restored.lines.size)
        assertEquals(0, restored.highlighted)
    }

    @Test
    fun aFutureDateIsNotTaken() = runTest {
        val model = model()
        model.onAction(GoalBulkAction.DatePicked(LocalDate.of(2026, 10, 24)))
        assertEquals(today, model.state.value.line.date)
        assertFalse(model.state.value.datePickerOpen)
    }

    @Test
    fun aCompletedOrMissingGoalTakesNothing() = runTest {
        val done = model(goal = vacunas)
        assertEquals(GoalBulkError.Completed, done.state.value.error)
        assertFalse(done.state.value.editable)

        val missing = model(goal = "00000000-0000-4000-9000-000000000099")
        assertEquals(GoalBulkError.Gone, missing.state.value.error)
        missing.type("5")
        assertNull(missing.state.value.line.amount)
    }

    @Test
    fun aFailedFirstReadIsTheErrorAndRetryReadsAgain() = runTest {
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("x"), null)
        val model = model()
        assertTrue(model.state.value.loadFailed)
        assertFalse(model.state.value.editable)
        assertEquals(1, reads.started)

        model.onAction(GoalBulkAction.Retry)
        assertEquals(2, reads.started)

        reads.board.value = board()
        assertFalse(model.state.value.loadFailed)
    }

    @Test
    fun aFailedRefreshKeepsTheLastGoalRead() = runTest {
        val model = model()
        model.threeLines()
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("x"), board().value)

        assertFalse(model.state.value.loadFailed)
        assertEquals("Viaje a la costa", model.state.value.goalName)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun leavingAsksOnlyWhenSomethingWasTyped() = runTest {
        val clean = model()
        clean.onAction(GoalBulkAction.Back)
        assertTrue(clean.state.value.closed)

        val typed = model()
        typed.threeLines()
        typed.onAction(GoalBulkAction.Back)
        assertTrue(typed.state.value.confirmDiscard)
        assertEquals(3, typed.state.value.typed)
        typed.onAction(GoalBulkAction.KeepEditing)
        assertFalse(typed.state.value.closed)
        typed.onAction(GoalBulkAction.Back)
        typed.onAction(GoalBulkAction.DiscardConfirmed)
        assertTrue(typed.state.value.closed)
        assertEquals(emptyList<GoalCommand>(), commands.sent)
    }
}
