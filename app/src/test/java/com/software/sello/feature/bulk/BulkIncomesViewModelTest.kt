package com.software.sello.feature.bulk

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EntryBatchPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedIncomes
import com.software.sello.domain.port.CreateIncomes
import com.software.sello.domain.port.IncomeBatchCommands
import com.software.sello.domain.port.IncomeBatchOutcome
import com.software.sello.domain.port.IncomeBatchRejection
import com.software.sello.domain.port.LineRefusal
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RefusedLine
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.income.incomeId
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
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

/** Receipts of incomes a test scripts: what each one answers, and what it was sent. */
class HandIncomeBatchCommands : IncomeBatchCommands {
    val created = mutableListOf<CreateIncomes>()
    val looked = mutableListOf<OperationId>()
    var answer: (CreateIncomes) -> IncomeBatchOutcome =
        { CommandOutcome.Committed(committed(it.operationId, it.lines.size), replayed = false) }
    var found: (OperationId) -> Outcome<CommittedIncomes?, StorageFailure> =
        { Outcome.Success(null) }

    /** When set, every receipt waits here: the save is in flight. */
    var hold: CompletableDeferred<Unit>? = null

    fun committed(operation: OperationId, lines: Int) = CommittedIncomes(
        OperationReceipt(operation, OperationKind.CreateIncomes, 1, 2, Instant.EPOCH),
        (1..lines).map { incomeId(900 + it) }
    )

    override suspend fun create(command: CreateIncomes): IncomeBatchOutcome {
        created += command
        hold?.await()
        return answer(command)
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedIncomes?, StorageFailure> {
        looked += operationId
        return found(operationId)
    }
}

/** Anotar varios switched to Ingreso: sources, Otro's name, the switch and every save outcome. */
@OptIn(ExperimentalCoroutinesApi::class)
class BulkIncomesViewModelTest {
    private val expenses = HandExpenseBatchCommands()
    private val incomes = HandIncomeBatchCommands()
    private val snapshots = HandSnapshots()
    private val clock = HandFinancialClock("2026-10-22")
    private val october = YearMonth.of(2026, 10)
    private var nextId = 500

    private val transport = categoryIn(1, "Transporte", 200_000, icon = "directions_bus")
    private val health = categoryIn(2, "Salud", 200_000, icon = "medical_services")
    private val food = categoryIn(3, "Comida", 400_000, icon = "restaurant")

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private val corrections by lazy {
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

    private val said get() = corrections.notice.value

    private fun sheet(saved: SavedStateHandle) = BulkEntryViewModel(
        saved,
        expenses,
        incomes,
        snapshots,
        clock,
        { uuid(nextId++) },
        testMoney(),
        corrections
    )

    /** October with Transporte, Salud and Comida, read at [generation]. */
    private fun readMonth(generation: Long = 7) = snapshots.ready(
        monthOf("2026-10-22", listOf(transport, health, food), generation = generation)
    )

    /** A receipt of incomes, as the income list opens it. */
    private fun incomeSheet(
        saved: SavedStateHandle = SavedStateHandle(mapOf("income" to true))
    ): BulkEntryViewModel {
        readMonth()
        return sheet(saved)
    }

    /** A receipt of expenses, as Anotar's Gasto opens it. */
    private fun expenseSheet(): BulkEntryViewModel {
        readMonth()
        return sheet(SavedStateHandle())
    }

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private val BulkEntryViewModel.now get() = state.value
    private val BulkEntryViewModel.digits get() = now.lines.map { it.amount?.digits }

    private fun BulkEntryViewModel.type(digits: String) =
        digits.forEach { onAction(BulkEntryAction.Digit(it.digitToInt())) }

    private fun BulkEntryViewModel.source(key: String) = onAction(BulkEntryAction.SourcePicked(key))

    private fun BulkEntryViewModel.name(text: String) {
        onAction(BulkEntryAction.NoteDraftChanged(text))
        onAction(BulkEntryAction.NoteDone)
    }

    private fun BulkEntryViewModel.note(text: String) {
        onAction(BulkEntryAction.OpenNote)
        onAction(BulkEntryAction.NoteDraftChanged(text))
        onAction(BulkEntryAction.NoteDone)
    }

    private fun BulkEntryViewModel.add() = onAction(BulkEntryAction.AddLine)

    private fun BulkEntryViewModel.submit() = onAction(BulkEntryAction.Submit)

    /**
     * The mock's receipt: Freelance 450.000 Página web, Transferencia 120.000 Devolución de
     * Laura, Otro "Venta de la bicicleta" 850.000.
     */
    private fun BulkEntryViewModel.threeIncomes() {
        type("450000")
        source("freelance")
        note("Página web")
        add()
        type("120000")
        source("transfer")
        note("Devolución de Laura")
        add()
        type("850000")
        source("other")
        name("Venta de la bicicleta")
    }

    // Sources

    @Test
    fun opensFromTheIncomeListWithOneEmptyLineNoSourceChosenAndTheSourcesOffered() {
        val bulk = incomeSheet()

        assertEquals(BulkKind.Income, bulk.now.kind)
        assertEquals(1, bulk.now.lines.size)
        assertNull(bulk.now.lines.single().source)
        assertEquals(IncomeSource.keys, bulk.now.sources)
        assertTrue(bulk.now.categories.isEmpty())
        assertFalse(bulk.now.canSubmit)
        assertFalse(bulk.now.canAdd)
    }

    @Test
    fun threeIncomesEachCopyingTheSourceAboveAddUpToTheirTotal() {
        val bulk = incomeSheet()
        bulk.type("450000")
        bulk.source("freelance")
        bulk.add()

        // The new line copies Freelance.
        assertEquals("freelance", bulk.now.lines[1].source?.key)

        bulk.onAction(BulkEntryAction.Select(0))
        bulk.note("Página web")
        bulk.onAction(BulkEntryAction.Select(1))
        bulk.type("120000")
        bulk.source("transfer")
        bulk.note("Devolución de Laura")
        bulk.add()
        bulk.type("850000")
        bulk.source("other")
        bulk.name("Venta de la bicicleta")

        assertEquals(listOf("450.000", "120.000", "850.000"), bulk.digits)
        assertEquals(
            listOf("freelance", "transfer", "other"),
            bulk.now.lines.map {
                it.source?.key
            }
        )
        assertEquals("Venta de la bicicleta", bulk.now.lines[2].source?.name)
        assertEquals(3, bulk.now.count)
        // 450.000 + 120.000 + 850.000 = 1.420.000
        assertEquals("1.420.000", bulk.now.total.digits)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun aLineWithAnAmountAndNoSourceBlocksSavingAndSaysWhy() {
        val bulk = incomeSheet()
        bulk.type("120000")

        assertEquals(LineProblem.NoSource, bulk.now.lines.single().problem)
        assertEquals(1, bulk.now.missingCategories)
        assertFalse(bulk.now.canSubmit)

        bulk.source("salary")
        assertNull(bulk.now.lines.single().problem)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun anUnknownSourceIsNotTaken() {
        val bulk = incomeSheet()
        bulk.source("lottery")

        assertNull(bulk.now.lines.single().source)
    }

    @Test
    fun categoriesAreNotTakenOnAReceiptOfIncomes() {
        val bulk = incomeSheet()
        bulk.onAction(BulkEntryAction.CategoryPicked(uuid(1)))

        assertNull(bulk.now.lines.single().category)
    }

    // Otro

    @Test
    fun otroAsksForItsNameThroughTheComposerPerLine() {
        val bulk = incomeSheet()
        bulk.type("850000")
        bulk.source("other")

        assertEquals("", bulk.now.noteDraft)
        assertEquals(BulkComposer.SourceName, bulk.now.composing)

        bulk.name("Venta de la bicicleta")

        assertNull(bulk.now.noteDraft)
        assertEquals("Venta de la bicicleta", bulk.now.lines.single().source?.name)
        // The name is not the note.
        assertEquals("", bulk.now.lines.single().note)

        // A second Otro line asks for a name of its own; Cambiar reopens it as written.
        bulk.add()
        bulk.onAction(BulkEntryAction.OpenSourceName)
        assertEquals("Venta de la bicicleta", bulk.now.noteDraft)
        bulk.onAction(BulkEntryAction.NoteDraftChanged("Arriendo del garaje"))
        bulk.onAction(BulkEntryAction.NoteDone)
        assertEquals(
            listOf("Venta de la bicicleta", "Arriendo del garaje"),
            bulk.now.lines.map { it.source?.name }
        )
    }

    @Test
    fun otroWithoutANameBlocksSavingAndSaysWhy() {
        val bulk = incomeSheet()
        bulk.type("850000")
        bulk.source("other")
        bulk.onAction(BulkEntryAction.NoteCancelled)

        assertEquals("other", bulk.now.lines.single().source?.key)
        assertNull(bulk.now.lines.single().source?.name)
        assertEquals(LineProblem.NoSourceName, bulk.now.lines.single().problem)
        assertEquals(1, bulk.now.missingCategories)
        assertFalse(bulk.now.canSubmit)
    }

    @Test
    fun otroNameOverItsLimitOrEmptyIsNotTakenAndTheComposerStaysOpen() {
        val bulk = incomeSheet()
        bulk.source("other")
        bulk.name("x".repeat(25))

        assertTrue(bulk.now.noteDraftTooLong)
        assertEquals("x".repeat(25), bulk.now.noteDraft)
        assertNull(bulk.now.lines.single().source?.name)

        bulk.name("   ")
        assertTrue(bulk.now.noteDraftRequired)
        assertEquals("   ", bulk.now.noteDraft)

        // 24 is the most it takes.
        bulk.name("x".repeat(24))
        assertNull(bulk.now.noteDraft)
        assertEquals("x".repeat(24), bulk.now.lines.single().source?.name)
    }

    @Test
    fun anotherSourceAfterOtroIsSentWithoutTheName() {
        val bulk = incomeSheet()
        bulk.type("850000")
        bulk.source("other")
        bulk.name("Venta de la bicicleta")
        bulk.source("salary")

        assertNull(bulk.now.lines.single().source?.name)
        bulk.submit()

        assertEquals(IncomeSource.Salary, incomes.created.single().lines.single().source)
    }

    // The kind

    @Test
    fun theReceiptKeepsTheKindItWasOpenedWithEvenAfterARestart() {
        assertEquals(BulkKind.Expense, expenseSheet().now.kind)
        val saved = SavedStateHandle(mapOf("income" to true))
        val bulk = incomeSheet(saved)
        bulk.threeIncomes()

        val restored = incomeSheet(saved.afterProcessDeath())

        assertEquals(BulkKind.Income, restored.now.kind)
        assertEquals(listOf("450.000", "120.000", "850.000"), restored.digits)
    }

    // Saving

    @Test
    fun savingSendsEveryIncomeInOrderOnceAndTheOriginSaysWhatWasSaved() {
        val bulk = incomeSheet()
        bulk.threeIncomes()

        bulk.submit()

        val sent = incomes.created.single()
        assertEquals(7L, sent.generation)
        assertEquals(LocalDate.parse("2026-10-22"), sent.date)
        assertEquals(
            listOf(450_000L, 120_000L, 850_000L),
            sent.lines.map { it.amount.money.minorUnits }
        )
        assertEquals(
            listOf("freelance", "transfer", "other"),
            sent.lines.map { it.source.key }
        )
        assertEquals(
            "Venta de la bicicleta",
            (sent.lines[2].source as IncomeSource.Other).name.value
        )
        assertEquals(
            listOf("Página web", "Devolución de Laura", null),
            sent.lines.map { it.note?.value }
        )
        assertTrue(expenses.created.isEmpty())
        val notice = said as CorrectionNotice.IncomesRecorded
        assertEquals(3, notice.count)
        assertEquals("1.420.000", notice.total.digits)
        assertTrue(bulk.now.recorded)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun savingLocksTheSheetAndASecondPressSendsNothing() {
        val gate = CompletableDeferred<Unit>()
        incomes.hold = gate
        val bulk = incomeSheet()
        bulk.threeIncomes()

        bulk.submit()
        bulk.submit()
        bulk.source("salary")
        bulk.type("1")

        assertTrue(bulk.now.busy)
        assertEquals("other", bulk.now.lines[2].source?.key)
        assertEquals(listOf("450.000", "120.000", "850.000"), bulk.digits)
        gate.complete(Unit)

        assertEquals(1, incomes.created.size)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun aMonthThatCannotHoldALineMarksItByItsPlaceAmongTheLinesSent() {
        val bulk = incomeSheet()
        bulk.type("450000")
        bulk.source("freelance")
        bulk.add()
        bulk.type("5")
        bulk.add()
        bulk.type("999")
        // The middle line is emptied: it is not sent, so the last is the second line sent.
        bulk.onAction(BulkEntryAction.Select(1))
        bulk.onAction(BulkEntryAction.ClearAmount)
        assertEquals(listOf("450.000", null, "999"), bulk.digits)
        incomes.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    IncomeBatchRejection.Lines(listOf(RefusedLine(1, LineRefusal.MonthTotalFull)))
                )
            )
        }

        bulk.submit()

        assertEquals(2, incomes.created.single().lines.size)
        assertEquals(BulkError.NotSaved, bulk.now.error)
        assertEquals(listOf(null, null, LineProblem.MonthFull), bulk.now.lines.map { it.problem })
        assertFalse(bulk.now.canSubmit)
        // Changing it lifts the refusal.
        bulk.onAction(BulkEntryAction.Select(2))
        bulk.onAction(BulkEntryAction.Backspace)
        assertNull(bulk.now.lines[2].problem)
    }

    @Test
    fun aFutureOrInvalidDateRefusedSaysSoAndKeepsTheDraft() {
        val bulk = incomeSheet()
        bulk.threeIncomes()
        incomes.answer = { command ->
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    IncomeBatchRejection.Lines(
                        command.lines.indices.map {
                            RefusedLine(it, LineRefusal.FutureDate(LocalDate.parse("2026-10-21")))
                        }
                    )
                )
            )
        }

        bulk.submit()
        assertEquals(BulkError.FutureDate, bulk.now.error)
        assertEquals(3, bulk.now.count)

        incomes.answer = { command ->
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    IncomeBatchRejection.Lines(
                        command.lines.indices.map { RefusedLine(it, LineRefusal.InvalidDate) }
                    )
                )
            )
        }
        bulk.submit()
        assertEquals(BulkError.InvalidDate, bulk.now.error)
        assertEquals(listOf("450.000", "120.000", "850.000"), bulk.digits)
        assertFalse(bulk.now.recorded)
    }

    @Test
    fun aLineCountReplacedDataAndStorageFailuresSayNothingWasSaved() {
        val bulk = incomeSheet()
        bulk.threeIncomes()

        incomes.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    IncomeBatchRejection.LineCount(EntryBatchPolicy.MAX_LINES + 1)
                )
            )
        }
        bulk.submit()
        assertEquals(BulkError.NotSaved, bulk.now.error)

        incomes.answer = { CommandOutcome.Rejected(CommandRejection.StaleGeneration(8)) }
        bulk.submit()
        assertEquals(BulkError.DataChanged, bulk.now.error)

        incomes.answer = {
            CommandOutcome.Rejected(
                CommandRejection.StorageFailed(StorageFailure.Unavailable("disk"))
            )
        }
        bulk.submit()
        assertEquals(BulkError.NotSaved, bulk.now.error)
        assertEquals(3, incomes.created.size)
        assertNull(said)
        assertEquals(listOf("450.000", "120.000", "850.000"), bulk.digits)
    }

    @Test
    fun anUnknownOutcomeOffersOnlyCheckingTheSameOperation() {
        val bulk = incomeSheet()
        bulk.threeIncomes()
        incomes.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        incomes.found = { Outcome.Failure(StorageFailure.Unavailable("still unknown")) }

        bulk.submit()
        val operation = incomes.created.single().operationId

        assertEquals(BulkError.Unknown, bulk.now.error)
        assertFalse(bulk.now.editable)
        bulk.submit()
        assertEquals(1, incomes.created.size)

        incomes.found = { Outcome.Success(incomes.committed(it, 3)) }
        bulk.onAction(BulkEntryAction.Retry)

        assertEquals(listOf(operation, operation), incomes.looked)
        assertTrue(expenses.looked.isEmpty())
        assertEquals(1, incomes.created.size)
        assertEquals("1.420.000", (said as CorrectionNotice.IncomesRecorded).total.digits)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun anUnknownOutcomeFoundNotSavedLetsTheReceiptBeSentAgainAsANewOperation() {
        val bulk = incomeSheet()
        bulk.threeIncomes()
        incomes.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        incomes.found = { Outcome.Failure(StorageFailure.Unavailable("still unknown")) }
        bulk.submit()
        val first = incomes.created.single().operationId
        assertEquals(BulkError.Unknown, bulk.now.error)

        incomes.found = { Outcome.Success(null) }
        bulk.onAction(BulkEntryAction.Retry)
        assertNull(bulk.now.error)
        assertTrue(bulk.now.editable)

        incomes.answer = { CommandOutcome.Committed(incomes.committed(it.operationId, 3), false) }
        bulk.submit()

        assertEquals(2, incomes.created.size)
        assertTrue(first != incomes.created[1].operationId)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun aFailedReadKeepsTheLinesAndCannotSaveUntilItIsReadAgain() {
        readMonth()
        val bulk = sheet(SavedStateHandle(mapOf("income" to true)))
        bulk.threeIncomes()
        snapshots.of(october).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("disk")),
            lastGood = null
        )

        assertTrue(bulk.now.readFailed)
        assertEquals(listOf("450.000", "120.000", "850.000"), bulk.digits)
        assertFalse(bulk.now.canSubmit)
        bulk.submit()
        assertTrue(incomes.created.isEmpty())

        readMonth()
        assertFalse(bulk.now.readFailed)
        assertTrue(bulk.now.canSubmit)
    }

    // Restoring

    @Test
    fun aRestoredReceiptOfIncomesComesBackExactlyAsItWasLeft() {
        val saved = SavedStateHandle(mapOf("income" to true))
        val bulk = incomeSheet(saved)
        bulk.threeIncomes()
        bulk.onAction(BulkEntryAction.Select(1))

        val restored = incomeSheet(saved.afterProcessDeath())

        assertEquals(BulkKind.Income, restored.now.kind)
        assertEquals(listOf("450.000", "120.000", "850.000"), restored.digits)
        assertEquals(
            listOf("freelance", "transfer", "other"),
            restored.now.lines.map { it.source?.key }
        )
        assertEquals("Venta de la bicicleta", restored.now.lines[2].source?.name)
        assertEquals(1, restored.now.highlighted)
    }

    @Test
    fun otroNameBeingWrittenComesBackInItsComposerAfterARestart() {
        val saved = SavedStateHandle(mapOf("income" to true))
        val bulk = incomeSheet(saved)
        bulk.source("other")
        bulk.onAction(BulkEntryAction.NoteDraftChanged("Venta de"))

        val restored = incomeSheet(saved.afterProcessDeath())

        assertEquals("Venta de", restored.now.noteDraft)
        assertEquals(BulkComposer.SourceName, restored.now.composing)
    }

    @Test
    fun anIncomeSaveInFlightWhenTheProcessDiedIsAskedAboutNeverSentAgain() {
        val saved = SavedStateHandle(mapOf("income" to true))
        incomes.hold = CompletableDeferred()
        val bulk = incomeSheet(saved)
        bulk.threeIncomes()
        bulk.submit()
        val operation = incomes.created.single().operationId

        incomes.hold = null
        incomes.found = { Outcome.Success(incomes.committed(it, 3)) }
        // After the restart the month has not been read: the confirmation needs none of it.
        snapshots.of(october).value = ReadState.Loading
        val restored = sheet(saved.afterProcessDeath())

        assertEquals(1, incomes.created.size)
        assertEquals(listOf(operation), incomes.looked)
        assertTrue(expenses.looked.isEmpty())
        val notice = said as CorrectionNotice.IncomesRecorded
        assertEquals(3, notice.count)
        assertEquals("1.420.000", notice.total.digits)
        assertTrue(restored.now.closed)
    }

    @Test
    fun anIncomeSaveCancelledMidwayStaysPendingAndAfterARestartIsCheckedNotSentAgain() {
        val saved = SavedStateHandle(mapOf("income" to true))
        val gate = CompletableDeferred<Unit>()
        incomes.hold = gate
        val bulk = incomeSheet(saved)
        bulk.threeIncomes()
        bulk.submit()
        val operation = incomes.created.single().operationId
        gate.completeExceptionally(CancellationException("process ending"))

        // Cancelled is not an answer: the sheet stays busy, with no error and nothing looked up.
        assertTrue(bulk.now.busy)
        assertNull(bulk.now.error)
        assertTrue(incomes.looked.isEmpty())

        incomes.hold = null
        incomes.found = { Outcome.Success(incomes.committed(it, 3)) }
        val restored = sheet(saved.afterProcessDeath())

        assertEquals(1, incomes.created.size)
        assertEquals(listOf(operation), incomes.looked)
        assertEquals(3, (said as CorrectionNotice.IncomesRecorded).count)
        assertTrue(restored.now.closed)
    }

    @Test
    fun anIncomeSaveStillUnknownAfterARestartWaitsForCheckingAgain() {
        val saved = SavedStateHandle(mapOf("income" to true))
        incomes.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        incomes.found = { Outcome.Failure(StorageFailure.Unavailable("still unknown")) }
        val bulk = incomeSheet(saved)
        bulk.threeIncomes()
        bulk.submit()

        val restored = incomeSheet(saved.afterProcessDeath())

        assertEquals(BulkError.Unknown, restored.now.error)
        restored.submit()
        assertEquals(1, incomes.created.size)
        assertEquals(BulkKind.Income, restored.now.kind)
    }

    @Test
    fun aReceiptOfIncomesSavedBeforeTheProcessDiedIsAnnouncedNotSentAgain() {
        val saved = SavedStateHandle(mapOf("income" to true))
        val bulk = incomeSheet(saved)
        bulk.threeIncomes()
        bulk.submit()
        corrections.dismiss()

        val restored = incomeSheet(saved.afterProcessDeath())

        assertEquals(1, incomes.created.size)
        assertEquals(3, (said as CorrectionNotice.IncomesRecorded).count)
        assertTrue(restored.now.closed)
    }

    // Hints

    @Test
    fun fromAnotarIngresoWhatWasTypedIsLineOneAndASecondLineWaitsInItsSource() {
        val bulk = incomeSheet(
            SavedStateHandle(
                mapOf(
                    "income" to true,
                    "amount" to "850000",
                    "source" to "other",
                    "sourceName" to "Venta de la bicicleta",
                    "note" to "Usada",
                    "date" to "2026-10-20"
                )
            )
        )

        assertEquals(listOf("850.000", null), bulk.digits)
        assertEquals(listOf("other", "other"), bulk.now.lines.map { it.source?.key })
        assertEquals(
            listOf("Venta de la bicicleta", "Venta de la bicicleta"),
            bulk.now.lines.map { it.source?.name }
        )
        assertEquals(listOf("Usada", ""), bulk.now.lines.map { it.note })
        assertEquals(1, bulk.now.highlighted)
        assertEquals(LocalDate.parse("2026-10-20"), bulk.now.date)
    }

    @Test
    fun incomeHintsThatCannotBeUsedAreDroppedNotRepaired() {
        val unknown = incomeSheet(
            SavedStateHandle(mapOf("income" to true, "source" to "lottery", "amount" to "5000"))
        )
        assertEquals(listOf(null, null), unknown.now.lines.map { it.source })

        val longName = incomeSheet(
            SavedStateHandle(
                mapOf("income" to true, "source" to "other", "sourceName" to "x".repeat(25))
            )
        )
        assertEquals("other", longName.now.lines.single().source?.key)
        assertNull(longName.now.lines.single().source?.name)

        // A category means nothing to a receipt of incomes.
        val category = incomeSheet(
            SavedStateHandle(mapOf("income" to true, "categoryId" to uuid(1)))
        )
        assertNull(category.now.lines.single().category)
    }
}
