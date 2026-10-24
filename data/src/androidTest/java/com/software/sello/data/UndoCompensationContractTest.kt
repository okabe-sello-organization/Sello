package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommandOutcome.Committed
import com.software.sello.domain.port.ExpenseCommandOutcome.Rejected
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.RestoreExpense
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Undoing a deletion, against a real database file: approved cases U01 to U05. The
 * ledger starts with category "Mercado" (number 100) and an expense of 48.700 with a
 * note on 3 October (number 101), edited once, so it is at version 2. It is deleted
 * at elapsed time 7 h, which is revision 4.
 */
@RunWith(AndroidJUnit4::class)
class UndoCompensationContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val recorded = Instant.parse("2026-10-09T15:04:05.678Z").toEpochMilli()
    private val edited = Instant.parse("2026-10-10T09:30:00.125Z")
    private val pan = uuid(101)
    private val deletedAt = 7.hours
    private lateinit var deletion: OperationId

    private fun expenses() = ledger.rows(
        "expense", "sequence", "id", "category_id", "effective_date", "amount_minor",
        "currency", "note", "version", "created_at", "updated_at"
    )

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private fun kept() = ledger.long("SELECT COUNT(*) FROM deleted_expense")

    /** The expense exactly as it was when it was deleted. */
    private val original =
        "1|$pan|${uuid(100)}|2026-10-03|48700|COP|Pan|2|$recorded|${edited.toEpochMilli()}"

    private fun deleted() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 10_000))
        ledger.audit = edited
        val change = ledger.expenses.edit(
            EditExpense(
                ledger.operation(),
                1,
                ExpenseId.of(pan).valueOrFail(),
                1,
                ledger.categoryId(100),
                TransactionAmount.of(Money.cop(48_700)).valueOrFail(),
                LocalDate.parse("2026-10-03"),
                Note.of("Pan").valueOrFail()
            )
        )
        assertTrue("$change", change is Committed)
        assertEquals(listOf(original), expenses())
        ledger.elapsed = deletedAt
        deletion = ledger.operation()
        val removed = ledger.expenses.delete(
            DeleteExpense(deletion, 1, ExpenseId.of(pan).valueOrFail(), 2)
        )
        assertTrue("$removed", removed is Committed)
        assertEquals(emptyList<String>(), expenses())
        assertEquals(4L, ledger.revision())
    }

    private fun undo(
        operation: OperationId = ledger.operation(),
        of: OperationId = deletion,
        generation: Long = 1
    ) = RestoreExpense(operation, generation, of)

    private fun raw(sql: String) = ledger.database.openHelper.writableDatabase.execSQL(sql)

    private fun assertNothingRestored(revision: Long = 4) {
        assertEquals(emptyList<String>(), expenses())
        assertEquals(revision, ledger.revision())
        assertEquals(0, receipts().count { it.contains("expense.restore") })
    }

    // ---- U01: the six-second boundary ----

    @Test
    fun atFiveSecondsNineNineNineUndoPutsBackExactlyTheExpenseThatWasDeleted() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 5999.milliseconds
        ledger.audit = Instant.parse("2026-10-12T08:00:00Z")

        val outcome = ledger.expenses.restore(undo())

        val restored = (outcome as Committed).expense
        assertEquals(false, outcome.replayed)
        assertEquals(
            OperationKind.RestoreExpense to pan,
            restored.receipt.kind to restored.expenseId.value
        )
        // Every column as it was: its place in the order, its version, both timestamps.
        assertEquals(listOf(original), expenses())
        assertEquals(0L, kept())
        assertEquals(5L, ledger.revision())
        assertEquals("5|expense.restore|$pan", receipts().last())
        assertNull(ledger.expenses.undoOffer())
    }

    @Test
    fun atSixSecondsExactlyUndoIsRefusedAndNothingComesBack() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 6.seconds

        val outcome = ledger.expenses.restore(undo())

        assertEquals(Rejected(ExpenseRejection.UndoExpired), outcome)
        assertNothingRestored()
    }

    // ---- U02: time passing in the same process ----

    @Test
    fun timePassingNeverMovesTheDeadlineAndUndoStillWorksBeforeIt() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 4.seconds
        val offer = ledger.expenses.undoOffer()

        val outcome = ledger.expenses.restore(undo())

        assertEquals(deletedAt + 6.seconds, offer!!.until)
        assertTrue("$outcome", outcome is Committed)
        assertEquals(listOf(original), expenses())
    }

    // ---- U03: the process ends before undo ----

    @Test
    fun afterTheProcessEndsThereIsNoOfferAndTheDeletionStands() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 2.seconds

        ledger.reopen()
        val offer = ledger.expenses.undoOffer()
        val outcome = ledger.expenses.restore(undo())

        assertNull(offer)
        assertEquals(Rejected(ExpenseRejection.UndoExpired), outcome)
        assertNothingRestored()
        // What happened is still on record: the deletion committed.
        val found = (ledger.expenses.find(deletion) as Outcome.Success).value
        assertEquals(
            OperationKind.DeleteExpense to pan,
            found!!.receipt.kind to found.expenseId.value
        )
    }

    @Test
    fun openingStorageAgainDiscardsWhatWasKeptForAnUndoThatCanNoLongerHappen() = runBlocking {
        deleted()
        assertEquals(1L, kept())
        ledger.reopen()

        val discarded = ledger.storage().discardUndoCopies()

        assertEquals(Outcome.Success(Unit), discarded)
        assertEquals(0L, kept())
        assertNothingRestored()
    }

    // ---- U04: undo committed, then the process ends ----

    @Test
    fun anUndoThatCommittedBeforeTheProcessEndedIsFoundAndIsNotDoneTwice() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 3.seconds
        val operation = ledger.operation()
        val first = (ledger.expenses.restore(undo(operation)) as Committed).expense

        ledger.reopen()
        ledger.elapsed = deletedAt + 10.hours
        val found = ledger.expenses.find(operation)
        val again = ledger.expenses.restore(undo(operation))

        assertEquals(Outcome.Success(first), found)
        // The same undo asked again is answered with the original, however late.
        assertEquals(Committed(first, replayed = true), again)
        assertEquals(listOf(original), expenses())
        assertEquals(5L, ledger.revision())
        assertEquals(1, receipts().count { it.contains("expense.restore") })
    }

    @Test
    fun aSecondUndoOfTheSameDeletionUnderAnotherNameRestoresNothingMore() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 1.seconds
        ledger.expenses.restore(undo())

        val another = ledger.expenses.restore(undo())

        assertEquals(Rejected(ExpenseRejection.UndoExpired), another)
        assertEquals(listOf(original), expenses())
        assertEquals(5L, ledger.revision())
    }

    // ---- U05: financial time, and what blocks an undo ----

    @Test
    fun movingTheFinancialDayAYearDoesNotChangeTheSixSeconds() = runBlocking {
        deleted()
        ledger.goTo("2027-10-09")
        ledger.elapsed = deletedAt + 5.seconds
        val inTime = ledger.expenses.undoOffer()
        ledger.elapsed = deletedAt + 6.seconds

        val late = ledger.expenses.restore(undo())

        assertEquals(deletedAt + 6.seconds, inTime!!.until)
        assertEquals(Rejected(ExpenseRejection.UndoExpired), late)
        assertNothingRestored()
    }

    @Test
    fun anUndoPreparedBeforeTheHistoryWasReplacedIsRefused() = runBlocking {
        deleted()
        raw("UPDATE profile SET generation = 2")
        ledger.elapsed = deletedAt + 1.seconds

        val outcome = ledger.expenses.restore(undo(generation = 1))

        assertEquals(Rejected(ExpenseRejection.StaleGeneration(current = 2)), outcome)
        assertNothingRestored()
    }

    @Test
    fun anExpenseDeletedFromAHistoryThatWasReplacedIsNotPutIntoTheNewOne() = runBlocking {
        deleted()
        raw("UPDATE profile SET generation = 2")
        ledger.elapsed = deletedAt + 1.seconds

        // Even naming the new history: what was kept belongs to the old one.
        val outcome = ledger.expenses.restore(undo(generation = 2))

        assertEquals(Rejected(ExpenseRejection.UndoConflict), outcome)
        assertNothingRestored()
    }

    @Test
    fun anExpenseWhoseIdentifierIsInUseAgainIsNotOverwritten() = runBlocking {
        deleted()
        raw(
            "INSERT INTO expense VALUES ('$pan', '${uuid(100)}', '2026-10-08', 9, 700, 'COP', " +
                "NULL, 1, 0, 0, NULL, NULL, NULL)"
        )
        ledger.elapsed = deletedAt + 1.seconds
        val before = expenses()

        val outcome = ledger.expenses.restore(undo())

        assertEquals(Rejected(ExpenseRejection.UndoConflict), outcome)
        assertEquals(before, expenses())
        assertEquals(4L, ledger.revision())
    }

    @Test
    fun anExpenseWhosePlaceInTheOrderWasTakenIsNotPutBack() = runBlocking {
        deleted()
        raw(
            "INSERT INTO expense VALUES ('${uuid(555)}', '${uuid(100)}', '2026-10-08', 1, 700, " +
                "'COP', NULL, 1, 0, 0, NULL, NULL, NULL)"
        )
        ledger.elapsed = deletedAt + 1.seconds
        val before = expenses()

        val outcome = ledger.expenses.restore(undo())

        assertEquals(Rejected(ExpenseRejection.UndoConflict), outcome)
        assertEquals(before, expenses())
        assertEquals(4L, ledger.revision())
    }

    @Test
    fun aDeletionThatALaterOneReplacedCanNoLongerBeUndone() = runBlocking {
        deleted()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-04", 5_000))
        ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, ExpenseId.of(uuid(102)).valueOrFail(), 1)
        )
        ledger.elapsed = deletedAt + 1.seconds

        val outcome = ledger.expenses.restore(undo())

        assertEquals(Rejected(ExpenseRejection.UndoExpired), outcome)
        assertEquals(emptyList<String>(), expenses())
    }

    @Test
    fun undoNamingSomethingThatIsNotThePendingDeletionRestoresNothing() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 1.seconds

        val outcome = ledger.expenses.restore(undo(of = ledger.operation(4242)))

        assertEquals(Rejected(ExpenseRejection.UndoExpired), outcome)
        assertNothingRestored()
    }

    @Test
    fun theSameUndoNamedAgainWithADifferentDeletionIsAConflict() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 1.seconds
        val operation = ledger.operation()
        ledger.expenses.restore(undo(operation))

        val other = ledger.expenses.restore(undo(operation, of = ledger.operation(4242)))

        assertEquals(Rejected(ExpenseRejection.OperationConflict), other)
        assertEquals(5L, ledger.revision())
    }

    @Test
    fun anExpenseComesBackIntoItsCategoryEvenIfThatWasArchivedMeanwhile() = runBlocking {
        deleted()
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(100), 1))
        ledger.elapsed = deletedAt + 1.seconds

        val outcome = ledger.expenses.restore(undo())

        assertTrue("$outcome", outcome is Committed)
        assertEquals(listOf(original), expenses())
    }

    @Test
    fun anUndoThatCannotFinishLeavesTheDeletionStandingAndCanBeTriedAgainInTime() = runBlocking {
        deleted()
        raw("UPDATE profile SET revision = ${Long.MAX_VALUE}")
        ledger.elapsed = deletedAt + 1.seconds

        val failed = ledger.expenses.restore(undo())
        raw("UPDATE profile SET revision = 4")
        ledger.elapsed = deletedAt + 2.seconds
        val retried = ledger.expenses.restore(undo())

        assertTrue("$failed", (failed as Rejected).reason is ExpenseRejection.StorageFailed)
        assertTrue("$retried", retried is Committed)
        assertEquals(listOf(original), expenses())
        assertEquals(5L, ledger.revision())
    }

    @Test
    fun aCallerCancelledWhileAnUndoIsSavingIsToldSoAndTheExpenseIsBackOnce() = runBlocking {
        deleted()
        ledger.elapsed = deletedAt + 1.seconds
        val operation = ledger.operation()
        lateinit var caller: Job
        var returned: ExpenseCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.expenses.restore(undo(operation))
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(listOf(original), expenses())
        assertNull(ledger.expenses.undoOffer())
        val found = (ledger.expenses.find(operation) as Outcome.Success).value
        assertEquals(Committed(found!!, replayed = true), ledger.expenses.restore(undo(operation)))
        assertEquals(5L, ledger.revision())
    }
}
