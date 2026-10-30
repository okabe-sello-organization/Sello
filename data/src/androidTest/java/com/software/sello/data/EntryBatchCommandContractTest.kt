package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedExpenses
import com.software.sello.domain.port.CommittedIncomes
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.CreateIncomes
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseBatchOutcome
import com.software.sello.domain.port.ExpenseBatchRejection
import com.software.sello.domain.port.ExpenseLine
import com.software.sello.domain.port.IncomeBatchRejection
import com.software.sello.domain.port.IncomeLine
import com.software.sello.domain.port.LineRefusal
import com.software.sello.domain.port.RefusedLine
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A receipt of several expenses or incomes against a real database file: every line with one
 * receipt and one revision, or nothing. Every case checks the stored rows, the receipts and
 * the revision themselves, not only what was returned.
 */
@RunWith(AndroidJUnit4::class)
class EntryBatchCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger = TestLedger(files)

    private val receiptOp = operationIdOf(50)
    private val october3 = LocalDate.parse("2026-10-03")

    /** A new installation with Mercado (100) and Salud (101), at revision 2. */
    private fun startWithCategories() = runBlocking {
        ledger.start()
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.categories.submit(ledger.createCategory("Salud"))
        Unit
    }

    private fun line(category: Int, pesos: Long, note: String? = null) =
        ExpenseLine(categoryIdOf(category), transactionPesos(pesos), Note.of(note).valueOrFail())

    private val mercadoPan = line(100, 8_000, "Pan")
    private val saludDroga = line(101, 30_000)
    private val mercadoLeche = line(100, 12_500, "Leche")
    private val threeLines = listOf(mercadoPan, saludDroga, mercadoLeche)

    private fun expenses(
        lines: List<ExpenseLine> = threeLines,
        date: LocalDate = october3,
        operation: OperationId = receiptOp,
        generation: Long = 1
    ) = CreateExpenses(operation, generation, date, lines)

    private fun committedExpenses(revision: Long, vararg records: Int) = CommittedExpenses(
        OperationReceipt(receiptOp, OperationKind.CreateExpenses, 1, revision, ledger.auditMillis),
        records.map(::expenseIdOf)
    )

    private fun refusedExpenses(vararg refused: Pair<Int, LineRefusal>) = Rejected(
        Specific(ExpenseBatchRejection.Lines(refused.map { RefusedLine(it.first, it.second) }))
    )

    private fun expenseRows() = ledger.rows(
        "expense",
        "id",
        "category_id",
        "effective_date",
        "sequence",
        "amount_minor",
        "currency",
        "note",
        "version"
    )

    private fun incomeRows() = ledger.rows(
        "income",
        "id",
        "effective_date",
        "sequence",
        "amount_minor",
        "currency",
        "source_key",
        "source_name",
        "note"
    )

    private fun receiptCount() = ledger.long("SELECT COUNT(*) FROM operation_receipt")

    private fun receiptSubject(operation: OperationId) = ledger.database.openHelper
        .readableDatabase
        .query("SELECT subject_id FROM operation_receipt WHERE operation_id = '${operation.value}'")
        .use {
            it.moveToFirst()
            it.getString(0)
        }

    private fun raw(sql: String) = ledger.database.openHelper.writableDatabase.execSQL(sql)

    /** Every insert into [table] fails once it already holds [rows] rows, as a fault would. */
    private fun faultAfter(table: String, rows: Int) = raw(
        "CREATE TRIGGER fault_after BEFORE INSERT ON $table " +
            "WHEN (SELECT COUNT(*) FROM $table) >= $rows " +
            "BEGIN SELECT RAISE(ABORT, 'fault'); END"
    )

    private val constraintFailure = Rejected(
        CommandRejection.StorageFailed(StorageFailure.Unavailable("SQLiteConstraintException"))
    )

    /** What a stored expense row reads as here: id|category|date|sequence|amount|COP|note|1. */
    private fun storedExpense(
        record: Int,
        category: Int,
        sequence: Int,
        pesos: Long,
        note: String?
    ) = "${uuid(record)}|${uuid(category)}|2026-10-03|$sequence|$pesos|COP|$note|1"

    // --- Expenses ---

    @Test
    fun everyLineIsStoredWithOneReceiptAndOneRevisionAndTheIdsComeBackInLineOrder() = runBlocking {
        startWithCategories()

        val outcome = ledger.expenseBatches.create(expenses())

        assertEquals(Committed(committedExpenses(3, 102, 103, 104), replayed = false), outcome)
        assertEquals(
            listOf(
                storedExpense(102, 100, 1, 8_000, "Pan"),
                storedExpense(103, 101, 2, 30_000, null),
                storedExpense(104, 100, 3, 12_500, "Leche")
            ),
            expenseRows()
        )
        assertEquals(3, receiptCount())
        assertEquals(3, ledger.revision())
        assertEquals("${uuid(102)},${uuid(103)},${uuid(104)}", receiptSubject(receiptOp))
        assertEquals(
            Outcome.Success(committedExpenses(3, 102, 103, 104)),
            ledger.expenseBatches.find(receiptOp)
        )
    }

    @Test
    fun theLinesAreOrdinaryExpensesInTotalsEditsAndDeletions() = runBlocking {
        startWithCategories()
        ledger.expenseBatches.create(expenses())

        val month = ledger.snapshots.read(YearMonth.of(2026, 10)).valueOrFail().categories
            .associate { it.category.id to (it.spent.minorUnits to it.expenseCount) }
        val edited = ledger.expenses.edit(
            EditExpense(
                ledger.operation(),
                1,
                expenseIdOf(103),
                1,
                categoryIdOf(101),
                transactionPesos(31_000),
                october3,
                null
            )
        )
        val deleted = ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, expenseIdOf(102), 1)
        )

        // Mercado: 8.000 + 12.500 = 20.500 in two expenses; Salud: 30.000 in one.
        assertEquals(
            mapOf(categoryIdOf(100) to (20_500L to 2), categoryIdOf(101) to (30_000L to 1)),
            month
        )
        assertTrue("$edited", edited is Committed)
        assertTrue("$deleted", deleted is Committed)
        assertEquals(
            listOf(
                "${uuid(103)}|${uuid(101)}|2026-10-03|2|31000|COP|null|2",
                storedExpense(104, 100, 3, 12_500, "Leche")
            ),
            expenseRows()
        )
        // A replay still names the receipt's own expenses, whatever happened to them since.
        assertEquals(
            Committed(committedExpenses(3, 102, 103, 104), replayed = true),
            ledger.expenseBatches.create(expenses())
        )
        assertEquals(5, ledger.revision())
    }

    @Test
    fun theSameReceiptAgainReturnsTheOriginalAndWritesNothing() = runBlocking {
        startWithCategories()
        ledger.expenseBatches.create(expenses())
        val before = expenseRows()

        val again = ledger.expenseBatches.create(expenses())

        assertEquals(Committed(committedExpenses(3, 102, 103, 104), replayed = true), again)
        assertEquals(before, expenseRows())
        assertEquals(3, receiptCount())
        assertEquals(3, ledger.revision())
    }

    @Test
    fun theSameOperationWithAnyOtherInputIsAConflictAndChangesNothing() = runBlocking {
        startWithCategories()
        ledger.expenseBatches.create(expenses())
        val before = expenseRows()

        val conflicts = listOf(
            ledger.expenseBatches.create(expenses(lines = threeLines.reversed())),
            ledger.expenseBatches.create(expenses(lines = threeLines.take(2))),
            ledger.expenseBatches.create(expenses(date = october3.minusDays(1))),
            ledger.incomeBatches.create(
                CreateIncomes(receiptOp, 1, october3, listOf(salary(3_000_000)))
            ),
            ledger.expenses.create(
                CreateExpense(
                    receiptOp,
                    1,
                    categoryIdOf(100),
                    transactionPesos(8_000),
                    october3,
                    Note.of("Pan").valueOrFail()
                )
            )
        )

        conflicts.forEach { assertEquals(Rejected(CommandRejection.OperationConflict), it) }
        assertEquals(before, expenseRows())
        assertEquals(3, ledger.revision())
    }

    @Test
    fun manyIdenticalSubmissionsAtOnceSaveOneReceipt() = runBlocking {
        startWithCategories()
        val go = CompletableDeferred<Unit>()

        val outcomes = List(16) {
            async(Dispatchers.Default) {
                go.await()
                ledger.expenseBatches.create(expenses())
            }
        }.also { go.complete(Unit) }.awaitAll()

        assertEquals(
            listOf(Committed(committedExpenses(3, 102, 103, 104), replayed = false)) +
                List(15) { Committed(committedExpenses(3, 102, 103, 104), replayed = true) },
            outcomes.sortedBy { (it as Committed).replayed }
        )
        assertEquals(3, expenseRows().size)
        assertEquals(3, ledger.revision())
    }

    @Test
    fun aReceiptPreparedAgainstAReplacedHistoryIsRefused() = runBlocking {
        startWithCategories()

        val stale = ledger.expenseBatches.create(expenses(generation = 2))

        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), stale)
        assertEquals(emptyList<String>(), expenseRows())
        assertEquals(2, ledger.revision())
    }

    @Test
    fun noLinesOrMoreThanThirtyAreRefused() = runBlocking {
        startWithCategories()

        val none = ledger.expenseBatches.create(expenses(lines = emptyList()))
        val thirtyOne = ledger.expenseBatches.create(expenses(lines = List(31) { mercadoPan }))
        val thirty = ledger.expenseBatches.create(
            expenses(lines = List(30) { mercadoPan }, operation = operationIdOf(51))
        )

        assertEquals(Rejected(Specific(ExpenseBatchRejection.LineCount(0))), none)
        assertEquals(Rejected(Specific(ExpenseBatchRejection.LineCount(31))), thirtyOne)
        assertEquals(30, (thirty as Committed).value.expenseIds.size)
        assertEquals(30, expenseRows().size)
        assertEquals(3, ledger.revision())
    }

    @Test
    fun everyRefusedLineIsNamedByPositionAndNothingIsWritten() = runBlocking {
        startWithCategories()
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, categoryIdOf(101), 1))

        val outcome = ledger.expenseBatches.create(
            expenses(
                lines = listOf(
                    mercadoPan,
                    line(999, 1_000),
                    saludDroga,
                    mercadoLeche,
                    line(999, 2_000)
                )
            )
        )

        assertEquals(
            refusedExpenses(
                1 to LineRefusal.CategoryMissing,
                2 to LineRefusal.CategoryArchived,
                4 to LineRefusal.CategoryMissing
            ),
            outcome
        )
        assertEquals(emptyList<String>(), expenseRows())
        assertEquals(3, receiptCount())
        assertEquals(3, ledger.revision())
        assertNull(ledger.expenseBatches.find(receiptOp).valueOrFail())
    }

    @Test
    fun aDateNoLineCanHaveRefusesEveryLine() = runBlocking {
        startWithCategories()
        val lines = listOf(mercadoPan, line(999, 1_000))
        val today = LocalDate.parse("2026-10-09")

        val tomorrow = ledger.expenseBatches.create(expenses(lines, LocalDate.parse("2026-10-10")))
        val unstorable = ledger.expenseBatches.create(
            expenses(lines, LocalDate.parse("-0001-01-01"))
        )
        val onTheDay = ledger.expenseBatches.create(
            expenses(lines.take(1), today, operationIdOf(51))
        )

        assertEquals(
            refusedExpenses(0 to LineRefusal.FutureDate(today), 1 to LineRefusal.FutureDate(today)),
            tomorrow
        )
        assertEquals(
            refusedExpenses(0 to LineRefusal.InvalidDate, 1 to LineRefusal.InvalidDate),
            unstorable
        )
        assertTrue("$onTheDay", onTheDay is Committed)
        assertEquals(listOf("2026-10-09"), ledger.rows("expense", "effective_date"))
        assertEquals(3, ledger.revision())
    }

    @Test
    fun aFaultAfterSomeLinesLeavesNoLineNoReceiptAndNoRevision() = runBlocking {
        startWithCategories()
        faultAfter("expense", rows = 2)

        val failed = ledger.expenseBatches.create(expenses(threeLines + mercadoPan))

        assertEquals(constraintFailure, failed)
        assertEquals(emptyList<String>(), expenseRows())
        assertEquals(2, receiptCount())
        assertEquals(2, ledger.revision())
        assertNull(ledger.expenseBatches.find(receiptOp).valueOrFail())

        // Nothing of the failed attempt is left in the way of the same receipt.
        raw("DROP TRIGGER fault_after")
        val saved = ledger.expenseBatches.create(expenses(threeLines + mercadoPan))

        assertEquals(
            listOf(106, 107, 108, 109).map(::expenseIdOf),
            (saved as Committed).value.expenseIds
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), expenseRows().map { it.split("|")[3].toLong() })
    }

    @Test
    fun aFailureAtTheCommitItselfIsUnknownAndTheSameOperationFindsNothingSaved() = runBlocking {
        startWithCategories()
        // Deferred checks make the database refuse the transaction only when it commits:
        // by then every statement of the receipt has run without error.
        var once = true
        ledger.whenAnIdIsTaken = {
            if (once) {
                once = false
                raw("PRAGMA defer_foreign_keys = ON")
                raw("DELETE FROM category")
            }
        }

        val outcome = ledger.expenseBatches.create(expenses())
        ledger.whenAnIdIsTaken = {}

        assertEquals(CommandOutcome.OutcomeUnknown(receiptOp), outcome)
        assertEquals(Outcome.Success(null), ledger.expenseBatches.find(receiptOp))
        assertEquals(emptyList<String>(), expenseRows())
        assertEquals(2L, ledger.long("SELECT COUNT(*) FROM category"))
        assertEquals(2, ledger.revision())
    }

    @Test
    fun aCallerCancelledWhileSavingGetsTheCancellationAndRecoversTheReceipt() = runBlocking {
        startWithCategories()
        lateinit var caller: Job
        var returned: ExpenseBatchOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAnIdIsTaken = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.expenseBatches.create(expenses())
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAnIdIsTaken = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(
            Outcome.Success(committedExpenses(3, 102, 103, 104)),
            ledger.expenseBatches.find(receiptOp)
        )
        assertEquals(
            Committed(committedExpenses(3, 102, 103, 104), replayed = true),
            ledger.expenseBatches.create(expenses())
        )
        assertEquals(3, expenseRows().size)
    }

    @Test
    fun aCallerCancelledBeforeSubmittingWritesNothing() = runBlocking {
        startWithCategories()
        var cancellation: CancellationException? = null

        launch(Dispatchers.Default) {
            coroutineContext.cancel()
            try {
                ledger.expenseBatches.create(expenses())
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }.join()

        assertTrue(cancellation != null)
        assertEquals(emptyList<String>(), expenseRows())
        assertEquals(2, ledger.revision())
    }

    @Test
    fun theReceiptItsIdsAndItsReplaySurviveARestart() = runBlocking {
        startWithCategories()
        ledger.expenseBatches.create(expenses())

        ledger.reopen()

        assertEquals(
            Outcome.Success(committedExpenses(3, 102, 103, 104)),
            ledger.expenseBatches.find(receiptOp)
        )
        assertEquals(
            Committed(committedExpenses(3, 102, 103, 104), replayed = true),
            ledger.expenseBatches.create(expenses())
        )
        assertEquals(
            Rejected(CommandRejection.OperationConflict),
            ledger.expenseBatches.create(expenses(lines = threeLines.take(1)))
        )
        assertEquals(3, expenseRows().size)
        assertEquals(3, ledger.revision())
    }

    @Test
    fun eachLookupAnswersOnlyForItsOwnKindOfOperation() = runBlocking {
        startWithCategories()
        ledger.expenseBatches.create(expenses())
        val single = ledger.operation()
        ledger.expenses.create(
            CreateExpense(single, 1, categoryIdOf(100), transactionPesos(1_000), october3, null)
        )

        assertEquals(Outcome.Success(null), ledger.expenses.find(receiptOp))
        assertEquals(Outcome.Success(null), ledger.incomeBatches.find(receiptOp))
        assertEquals(Outcome.Success(null), ledger.expenseBatches.find(single))
        assertEquals(Outcome.Success(null), ledger.categories.find(receiptOp))
    }

    /**
     * SC01, then a receipt of two Comida lines, 10.000 and 5.000, dated 15 feb 2027: feb
     * spends 85.000 instead of 70.000, so feb ends at 95.000 instead of 110.000 and every
     * later month carries 15.000 less. One confirmation covers the whole receipt.
     */
    @Test
    fun aReceiptThatChangesLaterMonthsOfAChainAsksOneConfirmationForTheWholeReceipt() =
        runBlocking {
            ledger.start()
            CarryHistories(ledger).sc01()
            val revision = ledger.revision()
            val receipt = CreateExpenses(
                receiptOp,
                1,
                LocalDate.parse("2027-02-15"),
                listOf(line(100, 10_000, "Pan"), line(100, 5_000))
            )
            val before = expenseRowsOf()

            val asked = ledger.expenseBatches.create(receipt)
            val refused = ((asked as Rejected).reason as Specific).reason
                as ExpenseBatchRejection.CorrectionUnconfirmed
            val other = ledger.expenseBatches.create(
                receipt.copy(
                    operationId = operationIdOf(51),
                    lines = listOf(line(100, 10_000, "Pan")),
                    confirmation = refused.confirmation
                )
            )
            val unchanged = expenseRowsOf()
            val saved = ledger.expenseBatches.create(
                receipt.copy(confirmation = refused.confirmation)
            )

            assertEquals(revision, refused.confirmation.revision)
            assertEquals(
                listOf(
                    "100|2027-03|C 210000→195000|R -40000→-55000|Over→Over",
                    "100|2027-04|C 60000→45000|R 60000→45000|OnTrack→OnTrack"
                ),
                refused.correction.months.map { it.text() }
            )
            assertEquals(
                listOf("100|2027-02|C 180000→180000|R 110000→95000|WithinLimit→WithinLimit"),
                refused.correction.edited.map { it.text() }
            )
            assertEquals(
                mapOf(
                    YearMonth.of(2027, 3) to Money.cop(-15_000),
                    YearMonth.of(2027, 4) to Money.cop(-15_000)
                ),
                refused.correction.overallCapacityChange
            )
            // A confirmation names one receipt: another one is refused with its own list.
            assertTrue(
                "$other",
                ((other as Rejected).reason as Specific).reason
                    is ExpenseBatchRejection.CorrectionUnconfirmed
            )
            assertEquals(before, unchanged)
            assertEquals(revision, ledger.revision() - 1)
            // Each refused attempt took its new identifiers (104–106) and wrote none of them.
            assertEquals(
                listOf(expenseIdOf(107), expenseIdOf(108)),
                (saved as Committed).value.expenseIds
            )
            assertEquals(
                listOf(
                    "2027-01|K 0|C 100000|S 20000|R 80000|WithinLimit",
                    "2027-02|K 80000|C 180000|S 85000|R 95000|WithinLimit",
                    "2027-03|K 95000|C 195000|S 250000|R -55000|Over",
                    "2027-04|K -55000|C 45000|S 0|R 45000|OnTrack"
                ),
                ledger.carry.history(categoryIdOf(100), YearMonth.of(2027, 4)).valueOrFail()
                    .replay.months.map { it.text() }
            )
        }

    private fun expenseRowsOf() = ledger.rows("expense", "id", "amount_minor")

    // --- Incomes ---

    private val incomeOp = operationIdOf(60)

    private fun salary(pesos: Long) = IncomeLine(IncomeSource.Salary, transactionPesos(pesos), null)

    private val venta = IncomeLine(
        IncomeSource.Other(SourceName.of("Venta").valueOrFail()),
        transactionPesos(50_000),
        Note.of("Bici").valueOrFail()
    )

    private fun incomes(
        lines: List<IncomeLine> = listOf(salary(3_000_000), venta),
        date: LocalDate = LocalDate.parse("2026-10-01"),
        generation: Long = 1
    ) = CreateIncomes(incomeOp, generation, date, lines)

    private fun committedIncomes(revision: Long, vararg records: Int) = CommittedIncomes(
        OperationReceipt(incomeOp, OperationKind.CreateIncomes, 1, revision, ledger.auditMillis),
        records.map { IncomeId.of(uuid(it)).valueOrFail() }
    )

    @Test
    fun everyIncomeIsStoredWithOneReceiptAndReplaysWithItsIds() = runBlocking {
        ledger.start()

        val outcome = ledger.incomeBatches.create(incomes())
        val month = ledger.incomeReads.month(YearMonth.of(2026, 10)).valueOrFail()
        val deleted = ledger.incomes.delete(
            DeleteIncome(ledger.operation(), 1, IncomeId.of(uuid(100)).valueOrFail(), 1)
        )
        val again = ledger.incomeBatches.create(incomes())

        assertEquals(Committed(committedIncomes(1, 100, 101), replayed = false), outcome)
        // 3.000.000 + 50.000 = 3.050.000 in two incomes.
        assertEquals(Money.cop(3_050_000) to 2, month.total to month.count)
        assertTrue("$deleted", deleted is Committed)
        assertEquals(Committed(committedIncomes(1, 100, 101), replayed = true), again)
        assertEquals(
            listOf("${uuid(101)}|2026-10-01|2|50000|COP|other|Venta|Bici"),
            incomeRows()
        )
        assertEquals("${uuid(100)},${uuid(101)}", receiptSubject(incomeOp))
        assertEquals(2, ledger.revision())
    }

    @Test
    fun aReceiptOfIncomesIsRefusedWholeForItsInputOrItsHistory() = runBlocking {
        ledger.start()
        val today = LocalDate.parse("2026-10-09")

        val none = ledger.incomeBatches.create(incomes(lines = emptyList()))
        val thirtyOne = ledger.incomeBatches.create(incomes(lines = List(31) { venta }))
        val tomorrow = ledger.incomeBatches.create(incomes(date = today.plusDays(1)))
        val unstorable = ledger.incomeBatches.create(incomes(date = LocalDate.parse("-0001-01-01")))
        val stale = ledger.incomeBatches.create(incomes(generation = 2))

        assertEquals(Rejected(Specific(IncomeBatchRejection.LineCount(0))), none)
        assertEquals(Rejected(Specific(IncomeBatchRejection.LineCount(31))), thirtyOne)
        assertEquals(
            Rejected(
                Specific(
                    IncomeBatchRejection.Lines(
                        listOf(
                            RefusedLine(0, LineRefusal.FutureDate(today)),
                            RefusedLine(1, LineRefusal.FutureDate(today))
                        )
                    )
                )
            ),
            tomorrow
        )
        assertEquals(
            Rejected(
                Specific(
                    IncomeBatchRejection.Lines(
                        listOf(
                            RefusedLine(0, LineRefusal.InvalidDate),
                            RefusedLine(1, LineRefusal.InvalidDate)
                        )
                    )
                )
            ),
            unstorable
        )
        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), stale)
        assertEquals(emptyList<String>(), incomeRows())
        assertEquals(0, receiptCount())
        assertEquals(0, ledger.revision())
    }

    @Test
    fun aFaultAfterSomeIncomesLeavesNothingAndARestartFindsNothingSaved() = runBlocking {
        ledger.start()
        faultAfter("income", rows = 1)

        val failed = ledger.incomeBatches.create(incomes())
        ledger.reopen()

        assertEquals(constraintFailure, failed)
        assertEquals(emptyList<String>(), incomeRows())
        assertEquals(0, receiptCount())
        assertEquals(0, ledger.revision())
        assertEquals(Outcome.Success(null), ledger.incomeBatches.find(incomeOp))
    }

    @Test
    fun aReceiptOfIncomesSurvivesARestartAndItsIncomesAreEditedAndDeletedLikeAnyOther() =
        runBlocking {
            ledger.start()
            ledger.incomeBatches.create(incomes())

            ledger.reopen()

            assertEquals(
                Outcome.Success(committedIncomes(1, 100, 101)),
                ledger.incomeBatches.find(incomeOp)
            )
            val edited = ledger.incomes.edit(
                EditIncome(
                    ledger.operation(),
                    1,
                    IncomeId.of(uuid(100)).valueOrFail(),
                    1,
                    transactionPesos(2_900_000),
                    IncomeSource.Salary,
                    LocalDate.parse("2026-10-01"),
                    Note.of("Prima").valueOrFail()
                )
            )
            val deleted = ledger.incomes.delete(
                DeleteIncome(ledger.operation(), 1, IncomeId.of(uuid(101)).valueOrFail(), 1)
            )
            val month = ledger.incomeReads.month(YearMonth.of(2026, 10)).valueOrFail()

            assertTrue("$edited", edited is Committed)
            assertTrue("$deleted", deleted is Committed)
            assertEquals(
                listOf("${uuid(100)}|2026-10-01|1|2900000|COP|salary|null|Prima"),
                incomeRows()
            )
            // 2.900.000 in one income: the edit counts, the deleted 50.000 does not.
            assertEquals(Money.cop(2_900_000) to 1, month.total to month.count)
            // A replay still names the receipt's own incomes, whatever happened to them since.
            assertEquals(
                Committed(committedIncomes(1, 100, 101), replayed = true),
                ledger.incomeBatches.create(incomes())
            )
            assertEquals(
                Rejected(CommandRejection.OperationConflict),
                ledger.incomeBatches.create(incomes(lines = listOf(venta, salary(3_000_000))))
            )
            assertEquals(1, incomeRows().size)
            assertEquals(3, ledger.revision())
        }
}
