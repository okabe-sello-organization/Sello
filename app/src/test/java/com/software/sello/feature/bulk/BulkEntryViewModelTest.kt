package com.software.sello.feature.bulk

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryMonthChange
import com.software.sello.domain.policy.EntryBatchPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedExpenses
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.ExpenseBatchCommands
import com.software.sello.domain.port.ExpenseBatchOutcome
import com.software.sello.domain.port.ExpenseBatchRejection
import com.software.sello.domain.port.LineRefusal
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RefusedLine
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.expenseId
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
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

/** Receipts a test scripts: what each one answers, and what it was sent. */
class HandExpenseBatchCommands : ExpenseBatchCommands {
    val created = mutableListOf<CreateExpenses>()
    val looked = mutableListOf<OperationId>()
    var answer: (CreateExpenses) -> ExpenseBatchOutcome =
        { CommandOutcome.Committed(committed(it), replayed = false) }
    var found: (OperationId) -> Outcome<CommittedExpenses?, StorageFailure> =
        { Outcome.Success(null) }

    /** When set, every receipt waits here: the save is in flight. */
    var hold: CompletableDeferred<Unit>? = null

    fun committed(command: CreateExpenses) = committed(command.operationId, command.lines.size)

    fun committed(operation: OperationId, lines: Int) = CommittedExpenses(
        OperationReceipt(operation, OperationKind.CreateExpenses, 1, 2, Instant.EPOCH),
        (1..lines).map { expenseId(900 + it) }
    )

    override suspend fun create(command: CreateExpenses): ExpenseBatchOutcome {
        created += command
        hold?.await()
        return answer(command)
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedExpenses?, StorageFailure> {
        looked += operationId
        return found(operationId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BulkEntryViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val commands = HandExpenseBatchCommands()
    private val snapshots = HandSnapshots()
    private val clock = HandFinancialClock("2026-10-22")
    private var nextId = 500

    private val transport = categoryIn(1, "Transporte", 200_000, icon = "directions_bus")
    private val health = categoryIn(2, "Salud", 200_000, icon = "medical_services")
    private val food = categoryIn(3, "Comida", 400_000, icon = "restaurant")
    private val old = categoryIn(5, "Vieja", 100_000, archived = true)

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

    private fun sheet(saved: SavedStateHandle = SavedStateHandle()) = BulkEntryViewModel(
        saved,
        commands,
        HandIncomeBatchCommands(),
        snapshots,
        clock,
        { uuid(nextId++) },
        testMoney(),
        corrections
    )

    /** A sheet over October with Transporte, Salud and Comida, already read. */
    private fun ready(
        saved: SavedStateHandle = SavedStateHandle(),
        generation: Long = 7
    ): BulkEntryViewModel {
        snapshots.ready(
            monthOf("2026-10-22", listOf(transport, health, food, old), generation = generation)
        )
        return sheet(saved)
    }

    private fun hints(vararg pairs: Pair<String, String>) = SavedStateHandle(pairs.toMap())

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private val BulkEntryViewModel.now get() = state.value

    private fun BulkEntryViewModel.type(digits: String) =
        digits.forEach { onAction(BulkEntryAction.Digit(it.digitToInt())) }

    private fun BulkEntryViewModel.pick(number: Int) =
        onAction(BulkEntryAction.CategoryPicked(uuid(number)))

    private fun BulkEntryViewModel.add() = onAction(BulkEntryAction.AddLine)

    private fun BulkEntryViewModel.note(text: String) {
        onAction(BulkEntryAction.OpenNote)
        onAction(BulkEntryAction.NoteDraftChanged(text))
        onAction(BulkEntryAction.NoteDone)
    }

    private fun BulkEntryViewModel.submit() = onAction(BulkEntryAction.Submit)

    /** The mock's receipt: Transporte 18.400 Uber, Salud 75.100, Comida 32.500 Carulla. */
    private fun BulkEntryViewModel.threeLines() {
        type("18400")
        pick(1)
        note("Uber")
        add()
        type("75100")
        pick(2)
        add()
        type("32500")
        pick(3)
        note("Carulla")
    }

    private val BulkEntryViewModel.digits get() = now.lines.map { it.amount?.digits }

    // The loop

    @Test
    fun opensWithOneEmptyHighlightedLineAndNothingToSave() {
        val bulk = ready()

        assertEquals(1, bulk.now.lines.size)
        assertEquals(0, bulk.now.highlighted)
        assertNull(bulk.now.lines.single().amount)
        assertNull(bulk.now.lines.single().category)
        assertEquals(0, bulk.now.count)
        assertEquals("0", bulk.now.total.digits)
        assertFalse(bulk.now.canSubmit)
        // Otro renglón waits for an amount.
        assertFalse(bulk.now.canAdd)
        assertEquals(LocalDate.parse("2026-10-22"), bulk.now.date)
        // Archived categories are not offered.
        assertEquals(listOf("Transporte", "Salud", "Comida"), bulk.now.categories.map { it.name })
    }

    @Test
    fun typesThreeLinesEachCopyingTheCategoryAboveAndCountsThemWithTheirTotal() {
        val bulk = ready()
        bulk.type("18400")
        bulk.pick(1)
        bulk.add()

        // The new line copies Transporte and is the one the pad types into.
        assertEquals(1, bulk.now.highlighted)
        assertEquals("Transporte", bulk.now.lines[1].category?.name)
        assertNull(bulk.now.lines[1].amount)

        bulk.type("75100")
        bulk.pick(2)
        bulk.add()
        bulk.type("32500")
        bulk.pick(3)

        assertEquals(listOf("18.400", "75.100", "32.500"), bulk.digits)
        assertEquals(
            listOf("Transporte", "Salud", "Comida"),
            bulk.now.lines.map {
                it.category?.name
            }
        )
        assertEquals(3, bulk.now.count)
        // 18.400 + 75.100 + 32.500 = 126.000
        assertEquals("126.000", bulk.now.total.digits)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun tappingALineMakesThePadChipsAndNoteApplyToIt() {
        val bulk = ready()
        bulk.threeLines()

        bulk.onAction(BulkEntryAction.Select(0))
        bulk.onAction(BulkEntryAction.Backspace)
        bulk.pick(3)
        bulk.note("Taxi")

        assertEquals(listOf("1.840", "75.100", "32.500"), bulk.digits)
        assertEquals("Comida", bulk.now.lines[0].category?.name)
        assertEquals("Taxi", bulk.now.lines[0].note)
        // 1.840 + 75.100 + 32.500 = 109.440
        assertEquals("109.440", bulk.now.total.digits)
    }

    /** Transporte 18.400, an empty line, then Salud 75.100. */
    private fun BulkEntryViewModel.gapInTheMiddle() {
        type("18400")
        pick(1)
        add()
        type("5")
        add()
        type("75100")
        pick(2)
        onAction(BulkEntryAction.Select(1))
        onAction(BulkEntryAction.ClearAmount)
    }

    @Test
    fun linesWithoutAnAmountAreIgnoredAndNotSent() {
        val bulk = ready()
        bulk.gapInTheMiddle()

        assertEquals(listOf("18.400", null, "75.100"), bulk.digits)
        assertEquals(2, bulk.now.count)
        // 18.400 + 75.100 = 93.500
        assertEquals("93.500", bulk.now.total.digits)
        // An empty line needs no category either.
        assertNull(bulk.now.lines[1].problem)
        bulk.submit()

        val sent = commands.created.single()
        assertEquals(listOf(18_400L, 75_100L), sent.lines.map { it.amount.money.minorUnits })
    }

    @Test
    fun aLineWithAnAmountAndNoCategoryBlocksSavingAndSaysWhy() {
        val bulk = ready()
        bulk.type("75100")
        bulk.note("Farmacia Pasteur")

        assertEquals(LineProblem.NoCategory, bulk.now.lines.single().problem)
        assertEquals(1, bulk.now.missingCategories)
        assertFalse(bulk.now.canSubmit)
        bulk.submit()
        assertTrue(commands.created.isEmpty())

        bulk.pick(2)
        assertNull(bulk.now.lines.single().problem)
        assertEquals(0, bulk.now.missingCategories)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun thirtyLinesIsTheMostAndOtroRenglonThenWaits() {
        val bulk = ready()
        repeat(EntryBatchPolicy.MAX_LINES) {
            bulk.type("4500")
            bulk.pick(3)
            bulk.add()
        }

        assertEquals(30, bulk.now.lines.size)
        assertEquals(29, bulk.now.highlighted)
        assertTrue(bulk.now.atLineLimit)
        assertFalse(bulk.now.canAdd)
        // 30 × 4.500 = 135.000
        assertEquals("135.000", bulk.now.total.digits)
        assertEquals(30, bulk.now.count)
        bulk.submit()
        assertEquals(30, commands.created.single().lines.size)
    }

    @Test
    fun aRestoredFullReceiptKeepsAsManyLinesAsStorageTakes() {
        val saved = SavedStateHandle()
        val bulk = ready(saved)
        repeat(EntryBatchPolicy.MAX_LINES) {
            bulk.type("4500")
            bulk.pick(3)
            bulk.add()
        }
        assertEquals(30, bulk.now.lines.size)

        val restored = ready(saved.afterProcessDeath())

        assertEquals(30, restored.now.lines.size)
        assertTrue(restored.now.atLineLimit)
        restored.submit()
        assertTrue(EntryBatchPolicy.holds(commands.created.single().lines.size))
    }

    @Test
    fun otroRenglonWaitsForTheLastLineNotTheHighlightedOne() {
        val bulk = ready()
        bulk.type("18400")
        bulk.pick(1)
        bulk.add()
        assertEquals(2, bulk.now.lines.size)

        // Line one has an amount, but the new last line is still empty.
        bulk.onAction(BulkEntryAction.Select(0))

        assertFalse(bulk.now.canAdd)
        bulk.add()
        assertEquals(2, bulk.now.lines.size)
        assertEquals(0, bulk.now.highlighted)
    }

    @Test
    fun twelveDigitsIsTheMostALineTakes() {
        val bulk = ready()
        bulk.type("999999999999")
        bulk.type("9")

        assertTrue(bulk.now.atLimit)
        assertEquals("999.999.999.999", bulk.now.lines.single().amount?.digits)
    }

    @Test
    fun thirtyLinesOfTheLargestAmountStillAddUpExactly() {
        val bulk = ready()
        repeat(EntryBatchPolicy.MAX_LINES) {
            bulk.type("999999999999")
            bulk.pick(1)
            bulk.add()
        }

        // 30 × 999.999.999.999 = 29.999.999.999.970
        assertEquals("29.999.999.999.970", bulk.now.total.digits)
    }

    // Removing

    @Test
    fun removingALineKeepsAValidHighlightAndDeshacerPutsItBack() {
        val bulk = ready()
        bulk.threeLines()
        assertEquals(2, bulk.now.highlighted)

        bulk.onAction(BulkEntryAction.Remove(1))

        assertEquals(listOf("18.400", "32.500"), bulk.digits)
        assertEquals(1, bulk.now.highlighted)
        assertEquals("Salud", bulk.now.removed?.categoryName)
        // 18.400 + 32.500 = 50.900
        assertEquals("50.900", bulk.now.total.digits)

        bulk.onAction(BulkEntryAction.UndoRemove)

        assertEquals(listOf("18.400", "75.100", "32.500"), bulk.digits)
        assertEquals(1, bulk.now.highlighted)
        assertNull(bulk.now.removed)
    }

    @Test
    fun fillingTheReceiptAgainEndsTheDeshacerOffer() {
        val bulk = ready()
        repeat(EntryBatchPolicy.MAX_LINES) {
            bulk.type("4500")
            bulk.pick(3)
            bulk.add()
        }
        bulk.onAction(BulkEntryAction.Remove(0))
        assertEquals(29, bulk.now.lines.size)
        assertEquals("Comida", bulk.now.removed?.categoryName)

        bulk.add()

        assertEquals(30, bulk.now.lines.size)
        // Deshacer could no longer put the line back, so it is no longer offered.
        assertNull(bulk.now.removed)
    }

    @Test
    fun removingTheHighlightedLastLineHighlightsTheNewLast() {
        val bulk = ready()
        bulk.threeLines()

        bulk.onAction(BulkEntryAction.Remove(2))
        assertEquals(1, bulk.now.highlighted)
        bulk.onAction(BulkEntryAction.Select(0))
        bulk.onAction(BulkEntryAction.Remove(0))
        assertEquals(0, bulk.now.highlighted)
        assertEquals(listOf("75.100"), bulk.digits)
    }

    @Test
    fun theLastLineCannotBeRemovedNorALineThatIsNotThere() {
        val bulk = ready()
        bulk.type("18400")

        bulk.onAction(BulkEntryAction.Remove(0))
        bulk.onAction(BulkEntryAction.Remove(3))
        bulk.onAction(BulkEntryAction.Select(4))

        assertEquals(listOf("18.400"), bulk.digits)
        assertEquals(0, bulk.now.highlighted)
        assertNull(bulk.now.removed)
    }

    @Test
    fun theDeshacerNoticeGoesAwayAndTheRemovalStays() {
        val bulk = ready()
        bulk.threeLines()
        bulk.onAction(BulkEntryAction.Remove(0))

        bulk.onAction(BulkEntryAction.RemoveSeen)
        bulk.onAction(BulkEntryAction.UndoRemove)

        assertEquals(listOf("75.100", "32.500"), bulk.digits)
    }

    // Opening

    @Test
    fun fromACategoryTheFirstLineAndEveryNewOneStartInIt() {
        val bulk = ready(hints("categoryId" to uuid(2)))

        assertEquals("Salud", bulk.now.lines.single().category?.name)
        bulk.type("10000")
        bulk.add()
        assertEquals("Salud", bulk.now.lines[1].category?.name)
    }

    @Test
    fun fromAnotarWhatWasTypedIsLineOneAndASecondLineWaitsInItsCategory() {
        val bulk = ready(
            hints(
                "amount" to "18400",
                "categoryId" to uuid(1),
                "note" to "Uber",
                "date" to "2026-10-20"
            )
        )

        assertEquals(listOf("18.400", null), bulk.digits)
        assertEquals("Uber", bulk.now.lines[0].note)
        assertEquals(listOf("Transporte", "Transporte"), bulk.now.lines.map { it.category?.name })
        assertEquals(1, bulk.now.highlighted)
        assertEquals(LocalDate.parse("2026-10-20"), bulk.now.date)
        assertEquals("18.400", bulk.now.total.digits)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun hintsThatCannotBeUsedAreDroppedNotRepaired() {
        val bulk = ready(
            hints(
                "amount" to "1e3",
                "categoryId" to uuid(5),
                "note" to "dos\nlíneas",
                "date" to "2026-10-23"
            )
        )

        assertEquals(listOf<String?>(null), bulk.digits)
        // An archived category is not offered, so the line has none.
        assertNull(bulk.now.lines.single().category)
        assertEquals("", bulk.now.lines.single().note)
        assertEquals(LocalDate.parse("2026-10-22"), bulk.now.date)
    }

    // The date

    @Test
    fun oneDateForTheReceiptNeverInTheFuture() {
        val bulk = ready()
        bulk.onAction(BulkEntryAction.OpenDatePicker)
        bulk.onAction(BulkEntryAction.DatePicked(LocalDate.parse("2026-10-23")))
        assertEquals(LocalDate.parse("2026-10-22"), bulk.now.date)
        assertFalse(bulk.now.datePickerOpen)

        bulk.onAction(BulkEntryAction.DatePicked(LocalDate.parse("2026-10-03")))
        bulk.type("18400")
        bulk.pick(1)
        bulk.submit()

        assertEquals(LocalDate.parse("2026-10-03"), commands.created.single().date)
    }

    // Saving

    @Test
    fun savingSendsEveryLineInOrderOnceAndTheOriginSaysWhatWasSaved() {
        val bulk = ready(generation = 7)
        bulk.threeLines()

        bulk.submit()

        val sent = commands.created.single()
        assertEquals(7L, sent.generation)
        assertEquals(LocalDate.parse("2026-10-22"), sent.date)
        assertEquals(
            listOf(18_400L, 75_100L, 32_500L),
            sent.lines.map {
                it.amount.money.minorUnits
            }
        )
        assertEquals(listOf(uuid(1), uuid(2), uuid(3)), sent.lines.map { it.categoryId.value })
        assertEquals(listOf("Uber", null, "Carulla"), sent.lines.map { it.note?.value })
        assertNull(sent.confirmation)
        val notice = said as CorrectionNotice.ExpensesRecorded
        assertEquals(3, notice.count)
        assertEquals("126.000", notice.total.digits)
        assertTrue(bulk.now.recorded)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun savingLocksTheSheetAndASecondPressSendsNothing() {
        val gate = CompletableDeferred<Unit>()
        commands.hold = gate
        val bulk = ready()
        bulk.threeLines()

        bulk.submit()
        bulk.submit()
        bulk.type("1")
        bulk.pick(1)
        bulk.add()
        bulk.onAction(BulkEntryAction.Remove(0))
        bulk.onAction(BulkEntryAction.Back)

        assertTrue(bulk.now.busy)
        assertFalse(bulk.now.editable)
        assertFalse(bulk.now.canSubmit)
        assertEquals(listOf("18.400", "75.100", "32.500"), bulk.digits)
        assertFalse(bulk.now.closed || bulk.now.confirmDiscard)
        gate.complete(Unit)

        assertEquals(1, commands.created.size)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun aRefusedLineIsMarkedByItsPlaceAmongTheLinesSentAndTheDraftStays() {
        val bulk = ready()
        // The empty line is not sent, so Salud is the second line sent: position 1.
        bulk.gapInTheMiddle()
        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.Lines(
                        listOf(RefusedLine(1, LineRefusal.CategoryArchived))
                    )
                )
            )
        }

        bulk.submit()

        assertEquals(listOf("18.400", null, "75.100"), bulk.digits)
        assertEquals(
            listOf(null, null, LineProblem.CategoryArchived),
            bulk.now.lines.map {
                it.problem
            }
        )
        assertEquals(1, bulk.now.refusedLines)
        assertEquals(BulkError.NotSaved, bulk.now.error)
        assertFalse(bulk.now.busy || bulk.now.closed)
        assertNull(said)
        // It waits until the refused line changes.
        assertFalse(bulk.now.canSubmit)
        bulk.onAction(BulkEntryAction.Select(2))
        bulk.pick(3)
        assertNull(bulk.now.lines[2].problem)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun aMonthThatCannotHoldALineMarksItUntilItsAmountChanges() {
        val bulk = ready()
        bulk.threeLines()
        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.Lines(listOf(RefusedLine(2, LineRefusal.MonthTotalFull)))
                )
            )
        }

        bulk.submit()

        assertEquals(LineProblem.MonthFull, bulk.now.lines[2].problem)
        bulk.onAction(BulkEntryAction.Backspace)
        assertNull(bulk.now.lines[2].problem)
    }

    @Test
    fun aFutureDateRefusedSaysSoAndKeepsTheDraft() {
        val bulk = ready()
        bulk.threeLines()
        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.Lines(
                        (0..2).map {
                            RefusedLine(it, LineRefusal.FutureDate(LocalDate.parse("2026-10-21")))
                        }
                    )
                )
            )
        }

        bulk.submit()

        assertEquals(BulkError.FutureDate, bulk.now.error)
        assertEquals(0, bulk.now.refusedLines)
        assertEquals(3, bulk.now.count)
    }

    @Test
    fun replacedDataAndStorageFailuresSayNothingWasSaved() {
        val bulk = ready()
        bulk.threeLines()
        commands.answer = { CommandOutcome.Rejected(CommandRejection.StaleGeneration(8)) }
        bulk.submit()
        assertEquals(BulkError.DataChanged, bulk.now.error)

        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.StorageFailed(StorageFailure.Unavailable("disk"))
            )
        }
        bulk.submit()
        assertEquals(BulkError.NotSaved, bulk.now.error)

        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.CarryUnavailable(
                        CarryoverFailure.Unrepresentable
                    )
                )
            )
        }
        bulk.submit()
        assertEquals(BulkError.CarryUnavailable, bulk.now.error)
        assertEquals(3, commands.created.size)
        assertTrue(commands.created.map { it.operationId }.distinct().size == 3)
    }

    @Test
    fun anUnknownOutcomeOffersOnlyCheckingTheSameOperation() {
        val bulk = ready()
        bulk.threeLines()
        commands.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("still unknown")) }

        bulk.submit()

        val operation = commands.created.single().operationId
        assertEquals(BulkError.Unknown, bulk.now.error)
        assertFalse(bulk.now.editable)
        assertFalse(bulk.now.canSubmit)
        bulk.submit()
        bulk.type("5")
        assertEquals(1, commands.created.size)
        assertEquals(listOf("18.400", "75.100", "32.500"), bulk.digits)

        commands.found = { Outcome.Success(commands.committed(it, 3)) }
        bulk.onAction(BulkEntryAction.Retry)

        assertEquals(listOf(operation, operation), commands.looked)
        assertEquals(1, commands.created.size)
        assertEquals(3, (said as CorrectionNotice.ExpensesRecorded).count)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun anUnknownOutcomeFoundNotSavedLetsTheReceiptBeSentAgainAsANewOperation() {
        val bulk = ready()
        bulk.threeLines()
        commands.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("still unknown")) }
        bulk.submit()

        commands.found = { Outcome.Success(null) }
        bulk.onAction(BulkEntryAction.Retry)

        assertNull(bulk.now.error)
        assertTrue(bulk.now.canSubmit)
        commands.answer = { CommandOutcome.Committed(commands.committed(it), replayed = false) }
        bulk.submit()
        assertEquals(2, commands.created.size)
        assertTrue(commands.created[0].operationId != commands.created[1].operationId)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun aPastDateThatChangesLaterMonthsAsksOnceForTheWholeReceipt() {
        val confirmation = CorrectionConfirmation(41, "batch-1")
        val bulk = ready()
        bulk.onAction(BulkEntryAction.DatePicked(LocalDate.parse("2026-09-30")))
        snapshots.ready(
            monthOf(
                "2026-10-22",
                listOf(transport, health, food),
                month = YearMonth.of(2026, 9),
                generation = 7
            )
        )
        bulk.threeLines()
        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.CorrectionUnconfirmed(confirmation, carryList())
                )
            )
        }

        bulk.submit()

        val correction = bulk.now.correction!!
        assertEquals("Transporte", correction.list.groups.single().name)
        assertFalse(correction.changed)
        assertFalse(bulk.now.canSubmit)
        assertNull(said)

        commands.answer = { CommandOutcome.Committed(commands.committed(it), replayed = false) }
        bulk.onAction(BulkEntryAction.CorrectionConfirmed)

        assertEquals(2, commands.created.size)
        assertEquals(confirmation, commands.created[1].confirmation)
        assertEquals(commands.created[0].lines, commands.created[1].lines)
        assertEquals(LocalDate.parse("2026-09-30"), commands.created[1].date)
        assertTrue(bulk.now.closed)
    }

    @Test
    fun notConfirmingTheListSavesNothingAndForgetsIt() {
        val bulk = ready()
        bulk.threeLines()
        commands.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.CorrectionUnconfirmed(
                        CorrectionConfirmation(41, "batch-1"),
                        carryList()
                    )
                )
            )
        }
        bulk.submit()

        bulk.onAction(BulkEntryAction.CorrectionDismissed)
        commands.answer = { CommandOutcome.Committed(commands.committed(it), replayed = false) }
        bulk.submit()

        assertNull(commands.created[1].confirmation)
    }

    // Reads

    @Test
    fun aFailedReadKeepsTheLinesAndCannotSaveUntilItIsReadAgain() {
        snapshots.of(october).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("disk")),
            lastGood = null
        )
        val bulk = sheet()
        bulk.type("18400")

        assertTrue(bulk.now.readFailed)
        assertFalse(bulk.now.loading)
        assertEquals(listOf("18.400"), bulk.digits)
        assertFalse(bulk.now.canSubmit)

        snapshots.ready(monthOf("2026-10-22", listOf(transport)))
        bulk.onAction(BulkEntryAction.Retry)
        bulk.pick(1)

        assertFalse(bulk.now.readFailed)
        assertTrue(bulk.now.canSubmit)
    }

    @Test
    fun whileLoadingNoLineIsBlamedForItsCategory() {
        val bulk = sheet()
        bulk.type("18400")

        assertTrue(bulk.now.loading)
        assertNull(bulk.now.lines.single().problem)
        assertFalse(bulk.now.canSubmit)
    }

    // Restoring

    @Test
    fun aRestoredDraftComesBackExactlyAsItWasLeft() {
        val saved = SavedStateHandle()
        val bulk = ready(saved)
        bulk.threeLines()
        bulk.onAction(BulkEntryAction.Select(1))
        bulk.onAction(BulkEntryAction.DatePicked(LocalDate.parse("2026-10-20")))

        val restored = ready(saved.afterProcessDeath())

        assertEquals(listOf("18.400", "75.100", "32.500"), restored.digits)
        assertEquals(listOf("Uber", "", "Carulla"), restored.now.lines.map { it.note })
        assertEquals(
            listOf("Transporte", "Salud", "Comida"),
            restored.now.lines.map {
                it.category?.name
            }
        )
        assertEquals(1, restored.now.highlighted)
        assertEquals(LocalDate.parse("2026-10-20"), restored.now.date)
        assertTrue(commands.created.isEmpty())
        // New lines still get keys of their own.
        restored.onAction(BulkEntryAction.Select(2))
        restored.add()
        assertEquals(4, restored.now.lines.map { it.key }.distinct().size)
    }

    @Test
    fun aSaveInFlightWhenTheProcessDiedIsAskedAboutNeverSentAgain() {
        val saved = SavedStateHandle()
        commands.hold = CompletableDeferred()
        val bulk = ready(saved)
        bulk.threeLines()
        bulk.submit()
        val operation = commands.created.single().operationId

        commands.hold = null
        commands.found = { Outcome.Success(commands.committed(it, 3)) }
        val restored = ready(saved.afterProcessDeath())

        assertEquals(1, commands.created.size)
        assertEquals(listOf(operation), commands.looked)
        assertEquals("126.000", (said as CorrectionNotice.ExpensesRecorded).total.digits)
        assertTrue(restored.now.closed)
    }

    @Test
    fun aSaveStillUnknownAfterARestartWaitsForCheckingAgain() {
        val saved = SavedStateHandle()
        commands.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("still unknown")) }
        val bulk = ready(saved)
        bulk.threeLines()
        bulk.submit()

        val restored = ready(saved.afterProcessDeath())

        assertEquals(BulkError.Unknown, restored.now.error)
        assertEquals(1, commands.created.size)
        restored.submit()
        assertEquals(1, commands.created.size)
    }

    @Test
    fun aReceiptSavedBeforeTheProcessDiedIsAnnouncedNotSentAgain() {
        val saved = SavedStateHandle()
        val bulk = ready(saved)
        bulk.threeLines()
        bulk.submit()
        corrections.dismiss()

        val restored = ready(saved.afterProcessDeath())

        assertEquals(1, commands.created.size)
        assertTrue(restored.now.recorded)
        assertTrue(restored.now.closed)
    }

    @Test
    fun aRestoredDraftThatCannotBeUsedStartsOver() {
        val bulk =
            ready(SavedStateHandle(mapOf("bulk.expense.draft" to arrayListOf<String?>("garbage"))))

        assertEquals(1, bulk.now.lines.size)
        assertEquals(0, bulk.now.highlighted)
    }

    // Leaving and the note

    @Test
    fun leavingWithTypedLinesAsksFirstAndWithNothingTypedCloses() {
        val empty = ready()
        empty.onAction(BulkEntryAction.Back)
        assertTrue(empty.now.closed)

        val bulk = ready()
        bulk.threeLines()
        bulk.onAction(BulkEntryAction.Back)
        assertTrue(bulk.now.confirmDiscard)
        assertEquals(3, bulk.now.typedLines)
        bulk.onAction(BulkEntryAction.KeepEditing)
        assertFalse(bulk.now.confirmDiscard || bulk.now.closed)
        bulk.onAction(BulkEntryAction.Back)
        bulk.onAction(BulkEntryAction.DiscardConfirmed)
        assertTrue(bulk.now.closed)
        assertTrue(commands.created.isEmpty())
    }

    @Test
    fun aNoteOverTheLimitIsNotTakenAndTheComposerStaysOpen() {
        val bulk = ready()
        bulk.onAction(BulkEntryAction.OpenNote)
        bulk.onAction(BulkEntryAction.NoteDraftChanged("x".repeat(61)))
        bulk.onAction(BulkEntryAction.NoteDone)

        assertTrue(bulk.now.noteDraftTooLong)
        assertEquals("x".repeat(61), bulk.now.noteDraft)
        assertEquals("", bulk.now.lines.single().note)
        bulk.onAction(BulkEntryAction.NoteCancelled)
        assertNull(bulk.now.noteDraft)
    }

    /** One later month, Transporte's November: 200.000 → 181.600, as a list shows it. */
    private fun carryList(): CarryCorrection {
        val nov = YearMonth.of(2026, 11)
        fun month(c: Long) = CarryMonth(
            nov,
            YearMonth.of(2026, 9),
            Money.cop(200_000),
            Money.cop(c - 200_000),
            Money.cop(c),
            Money.cop(0),
            Money.cop(c),
            CarryVerdict.InProgress(Verdict.OnTrack)
        )
        return CarryCorrection(
            listOf(
                CarryMonthChange(
                    transport.category.id,
                    nov,
                    month(200_000),
                    month(181_600),
                    Money.cop(-18_400)
                )
            ),
            emptyList(),
            emptyList(),
            nov,
            mapOf(nov to Money.cop(-18_400))
        )
    }
}
