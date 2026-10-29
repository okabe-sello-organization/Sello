package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.SnapshotState
import com.software.sello.domain.port.UndoOffer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Changing and removing a recorded expense, against a real database file. Every case
 * reads the stored rows, the receipts and the revision itself, not only what the
 * command answered. The ledger starts with category "Mercado" (number 100) and one
 * expense of 10.000 on 3 October (number 101), which is revision 2.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseEditDeleteContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val recorded = Instant.parse("2026-10-09T15:04:05.678Z").toEpochMilli()
    private val later = Instant.parse("2026-10-12T08:00:00.250Z")
    private val pan = uuid(101)

    private fun expenses() = ledger.rows(
        "expense", "sequence", "id", "category_id", "effective_date", "amount_minor",
        "currency", "note", "version", "created_at", "updated_at"
    )

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private fun kept() = ledger.rows("deleted_expense", "sequence", "deletion_id", "expense_id")

    private fun start() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        val created = ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 10_000))
        assertTrue("$created", created is Committed)
        assertEquals(
            listOf("1|$pan|${uuid(100)}|2026-10-03|10000|COP|null|1|$recorded|$recorded"),
            expenses()
        )
        assertEquals(2L, ledger.revision())
    }

    private fun edit(
        version: Long,
        pesos: Long = 10_000,
        date: String = "2026-10-03",
        note: String? = null,
        category: Int = 100,
        expense: String = pan,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = EditExpense(
        operationId = operation,
        generation = generation,
        expenseId = ExpenseId.of(expense).valueOrFail(),
        expectedVersion = version,
        categoryId = ledger.categoryId(category),
        amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
        date = LocalDate.parse(date),
        note = Note.of(note).valueOrFail()
    )

    private fun delete(
        version: Long,
        expense: String = pan,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = DeleteExpense(operation, generation, ExpenseId.of(expense).valueOrFail(), version)

    private fun committed(outcome: ExpenseCommandOutcome) = (outcome as Committed).value

    private suspend fun assertUnchangedSince(start: List<String>, revision: Long, receipts: Int) {
        assertEquals(start, expenses())
        assertEquals(revision, ledger.revision())
        assertEquals(receipts, receipts().size)
    }

    // ---- Editing ----

    @Test
    fun changingTenThousandToTwelveThousandIsOneRecordOneVersionOnAndOneReceipt() = runBlocking {
        start()
        ledger.audit = later

        val outcome = ledger.expenses.edit(edit(version = 1, pesos = 12_000))

        val saved = committed(outcome)
        assertEquals(false, (outcome as Committed).replayed)
        assertEquals(OperationKind.EditExpense to pan, saved.receipt.kind to saved.expenseId.value)
        assertEquals(3L to later, saved.receipt.revision to saved.receipt.committedAt)
        // Same record and place in the order, first recorded when it was; only the
        // amount, the version and when it was last changed are different.
        assertEquals(
            listOf(
                "1|$pan|${uuid(100)}|2026-10-03|12000|COP|null|2|$recorded|${later.toEpochMilli()}"
            ),
            expenses()
        )
        assertEquals(3L, ledger.revision())
        assertEquals(
            listOf(
                "1|category.create|${uuid(100)}",
                "2|expense.create|$pan",
                "3|expense.edit|$pan"
            ),
            receipts()
        )
    }

    @Test
    fun everyFieldThatCanChangeIsSavedExactlyIncludingMovingToAnotherMonth() = runBlocking {
        start()
        ledger.categories.submit(ledger.createCategory("Café"))
        ledger.audit = later

        val outcome = ledger.expenses.edit(
            edit(version = 1, pesos = 48_700, date = "2026-09-30", note = "Tostado", category = 102)
        )

        assertTrue("$outcome", outcome is Committed)
        assertEquals(
            listOf(
                "1|$pan|${uuid(
                    102
                )}|2026-09-30|48700|COP|Tostado|2|$recorded|${later.toEpochMilli()}"
            ),
            expenses()
        )
    }

    @Test
    fun anEditorHoldingTheOldVersionCannotOverwriteTheNewerAmount() = runBlocking {
        start()
        ledger.expenses.edit(edit(version = 1, pesos = 12_000))
        val after = expenses()

        val stale = ledger.expenses.edit(edit(version = 1, pesos = 99_000))

        assertEquals(Rejected(CommandRejection.VersionConflict(current = 2)), stale)
        assertUnchangedSince(after, revision = 3, receipts = 3)
    }

    @Test
    fun anExpenseThatDoesNotExistOrWasDeletedCannotBeEdited() = runBlocking {
        start()
        val before = expenses()

        val never = ledger.expenses.edit(edit(version = 1, expense = uuid(999), pesos = 5))
        ledger.expenses.delete(delete(version = 1))
        val deleted = ledger.expenses.edit(edit(version = 1, pesos = 12_000))

        assertEquals(Rejected(Specific(ExpenseRejection.ExpenseMissing)), never)
        assertEquals(Rejected(Specific(ExpenseRejection.ExpenseMissing)), deleted)
        assertEquals(1, before.size)
        assertEquals(emptyList<String>(), expenses())
        // Only the deletion moved the revision: 2 to 3.
        assertEquals(3L, ledger.revision())
    }

    @Test
    fun theSameEditAgainAnswersWithTheOriginalAndADifferentOneUnderItsNameIsAConflict() =
        runBlocking {
            start()
            val operation = ledger.operation()
            val first = ledger.expenses.edit(
                edit(version = 1, pesos = 12_000, operation = operation)
            )
            val after = expenses()

            val again = ledger.expenses.edit(
                edit(version = 1, pesos = 12_000, operation = operation)
            )
            val other = ledger.expenses.edit(
                edit(version = 1, pesos = 13_000, operation = operation)
            )

            assertEquals(Committed(committed(first), replayed = true), again)
            assertEquals(Rejected(CommandRejection.OperationConflict), other)
            assertUnchangedSince(after, revision = 3, receipts = 3)
            assertEquals(Outcome.Success(committed(first)), ledger.expenses.find(operation))
        }

    @Test
    fun anEditThatAsksForWhatIsAlreadyRecordedSavesNothing() = runBlocking {
        start()
        val before = expenses()

        val outcome = ledger.expenses.edit(edit(version = 1))

        assertEquals(Rejected(Specific(ExpenseRejection.Unchanged)), outcome)
        assertUnchangedSince(before, revision = 2, receipts = 2)
    }

    @Test
    fun anExpenseStaysEditableInItsArchivedCategoryButCannotBeMovedIntoOne() = runBlocking {
        start()
        ledger.categories.submit(ledger.createCategory("Café"))
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(100), 1))
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(102), 1))
        val revision = ledger.revision()

        val kept = ledger.expenses.edit(edit(version = 1, pesos = 12_000))
        val intoArchived = ledger.expenses.edit(edit(version = 2, pesos = 12_000, category = 102))
        val intoNothing = ledger.expenses.edit(edit(version = 2, pesos = 12_000, category = 777))

        assertTrue("$kept", kept is Committed)
        assertEquals(Rejected(Specific(ExpenseRejection.CategoryArchived)), intoArchived)
        assertEquals(Rejected(Specific(ExpenseRejection.CategoryMissing)), intoNothing)
        assertEquals(
            listOf("1|$pan|${uuid(100)}|2026-10-03|12000|COP|null|2|$recorded|$recorded"),
            expenses()
        )
        assertEquals(revision + 1, ledger.revision())
    }

    @Test
    fun anEditCannotDateAnExpenseAfterTodayOrOnADateThatCannotBeStored() = runBlocking {
        start()
        val before = expenses()

        val tomorrow = ledger.expenses.edit(edit(version = 1, date = "2026-10-10"))
        val unreadable = ledger.expenses.edit(edit(version = 1, date = "-0001-01-01"))
        val today = ledger.expenses.edit(edit(version = 1, date = "2026-10-09"))

        assertEquals(
            Rejected(Specific(ExpenseRejection.FutureDate(today = LocalDate.of(2026, 10, 9)))),
            tomorrow
        )
        assertEquals(Rejected(Specific(ExpenseRejection.InvalidDate)), unreadable)
        assertTrue("$today", today is Committed)
        assertEquals(1, before.size)
        assertEquals(listOf("2026-10-09"), ledger.rows("expense", "effective_date"))
    }

    @Test
    fun anExpenseAlreadyDatedAfterTodayCanBeCorrectedWithoutTouchingItsDate() = runBlocking {
        start()
        // The financial day moved back, so the expense of 3 October is now two days ahead.
        ledger.goTo("2026-10-01")

        val amountOnly = ledger.expenses.edit(edit(version = 1, pesos = 12_000))
        val toAnotherFutureDay = ledger.expenses.edit(
            edit(version = 2, pesos = 12_000, date = "2026-10-02")
        )
        val afterRefusal = expenses()
        val toToday = ledger.expenses.edit(edit(version = 2, pesos = 12_000, date = "2026-10-01"))

        assertTrue("$amountOnly", amountOnly is Committed)
        assertEquals(
            Rejected(Specific(ExpenseRejection.FutureDate(today = LocalDate.of(2026, 10, 1)))),
            toAnotherFutureDay
        )
        assertEquals(
            listOf("1|$pan|${uuid(100)}|2026-10-03|12000|COP|null|2|$recorded|$recorded"),
            afterRefusal
        )
        assertTrue("$toToday", toToday is Committed)
        assertEquals(
            listOf("2026-10-01|12000|3"),
            ledger.rows("expense", "effective_date", "amount_minor", "version")
        )
    }

    @Test
    fun anEditPreparedAgainstAHistoryThatWasReplacedIsRefused() = runBlocking {
        start()
        val before = expenses()
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE profile SET generation = 2")

        val outcome = ledger.expenses.edit(edit(version = 1, pesos = 12_000))

        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 2)), outcome)
        assertUnchangedSince(before, revision = 2, receipts = 2)
    }

    @Test
    fun anEditThatCannotFinishLeavesTheExpenseAsItWas() = runBlocking {
        start()
        // The revision cannot advance, which is found only after the expense was changed.
        ledger.database.openHelper.writableDatabase
            .execSQL("UPDATE profile SET revision = ${Long.MAX_VALUE}")
        val before = expenses()

        val outcome = ledger.expenses.edit(edit(version = 1, pesos = 12_000))

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("profile", null, "revision")
                )
            ),
            outcome
        )
        assertEquals(before, expenses())
        assertEquals(2, receipts().size)
    }

    // ---- Deleting ----

    @Test
    fun deletingRemovesTheExpenseKeepsWhatItWasAndOffersSixSecondsToUndo() = runBlocking {
        start()
        ledger.audit = later
        ledger.elapsed = 7.hours
        val operation = ledger.operation()

        val outcome = ledger.expenses.delete(delete(version = 1, operation = operation))

        val saved = committed(outcome)
        assertEquals(
            OperationKind.DeleteExpense to pan,
            saved.receipt.kind to saved.expenseId.value
        )
        assertEquals(emptyList<String>(), expenses())
        assertEquals(listOf("1|${operation.value}|$pan"), kept())
        assertEquals(3L, ledger.revision())
        assertEquals("3|expense.delete|$pan", receipts().last())
        assertEquals(
            UndoOffer(operation, saved.expenseId, until = 7.hours + 6.seconds),
            ledger.expenses.undoOffer()
        )
        assertEquals(Outcome.Success(saved), ledger.expenses.find(operation))
    }

    @Test
    fun onlyTheVersionThatWasReadCanBeDeletedAndAMissingExpenseCannot() = runBlocking {
        start()
        ledger.expenses.edit(edit(version = 1, pesos = 12_000))
        val after = expenses()

        val stale = ledger.expenses.delete(delete(version = 1))
        val never = ledger.expenses.delete(delete(version = 1, expense = uuid(999)))

        assertEquals(Rejected(CommandRejection.VersionConflict(current = 2)), stale)
        assertEquals(Rejected(Specific(ExpenseRejection.ExpenseMissing)), never)
        assertUnchangedSince(after, revision = 3, receipts = 3)
        assertEquals(emptyList<String>(), kept())
        assertNull(ledger.expenses.undoOffer())
    }

    @Test
    fun theSameDeletionAgainAnswersWithTheOriginalAndNeverGivesMoreTime() = runBlocking {
        start()
        ledger.elapsed = 7.hours
        val operation = ledger.operation()
        val first = ledger.expenses.delete(delete(version = 1, operation = operation))

        ledger.elapsed = 7.hours + 4.seconds
        val again = ledger.expenses.delete(delete(version = 1, operation = operation))

        assertEquals(Committed(committed(first), replayed = true), again)
        assertEquals(7.hours + 6.seconds, ledger.expenses.undoOffer()!!.until)
        assertEquals(3L, ledger.revision())
        assertEquals(1, kept().size)
    }

    @Test
    fun aSecondDeletionMakesTheFirstOneFinal() = runBlocking {
        start()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-04", 5_000))
        val first = ledger.operation()
        val second = ledger.operation()
        ledger.expenses.delete(delete(version = 1, operation = first))

        ledger.elapsed += 2.seconds
        ledger.expenses.delete(delete(version = 1, expense = uuid(102), operation = second))

        assertEquals(emptyList<String>(), expenses())
        // One kept copy, the second deletion's; the first can no longer be put back.
        assertEquals(listOf("2|${second.value}|${uuid(102)}"), kept())
        assertEquals(second, ledger.expenses.undoOffer()!!.deletionId)
    }

    @Test
    fun anExpenseAddedAfterTheNewestWasDeletedDoesNotTakeItsPlaceInTheOrder() = runBlocking {
        start()
        ledger.expenses.delete(delete(version = 1))

        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 7_000))

        // The deleted one was 1 and can still come back, so the new one is 2.
        assertEquals(
            listOf("2|${uuid(102)}|7000"),
            ledger.rows("expense", "sequence", "id", "amount_minor")
        )
    }

    @Test
    fun theOfferEndsBySixSecondsWithoutAnythingBeingAsked() = runBlocking {
        start()
        ledger.elapsed = 7.hours
        ledger.expenses.delete(delete(version = 1))

        ledger.elapsed = 7.hours + 5.seconds
        val stillThere = ledger.expenses.undoOffer()
        ledger.elapsed = 7.hours + 6.seconds

        assertEquals(7.hours + 6.seconds, stillThere!!.until)
        assertNull(ledger.expenses.undoOffer())
    }

    // ---- At the same time, and cancelled ----

    @Test
    fun ofSeveralEditsMadeAtOnceFromTheSameVersionExactlyOneIsSaved() = runBlocking {
        start()
        val edits = (1..8).map { edit(version = 1, pesos = 20_000L + it) }

        val outcomes = edits.map {
            async(Dispatchers.Default) { ledger.expenses.edit(it) }
        }.awaitAll()

        val saved = outcomes.filterIsInstance<Committed<CommittedExpense>>()
        assertEquals(1, saved.size)
        assertEquals(
            7,
            outcomes.count {
                it ==
                    Rejected(CommandRejection.VersionConflict(current = 2))
            }
        )
        // The amount stored is the winner's, at version 2, with one receipt for it.
        val winner = edits.single { it.operationId == saved.single().value.receipt.operationId }
        assertEquals(
            listOf("${winner.amount.money.minorUnits}|2"),
            ledger.rows("expense", "amount_minor", "version")
        )
        assertEquals(3L, ledger.revision())
        assertEquals(3, receipts().size)
    }

    @Test
    fun anEditAndADeletionMadeAtOnceFromTheSameVersionNeverBothHappen() = runBlocking {
        start()
        val change = edit(version = 1, pesos = 12_000)
        val removal = delete(version = 1)

        val edited = async(Dispatchers.Default) { ledger.expenses.edit(change) }
        val deleted = async(Dispatchers.Default) { ledger.expenses.delete(removal) }
        val outcomes = edited.await() to deleted.await()

        val expected = if (outcomes.first is Committed) {
            // The edit was first: the deletion held a version that no longer exists.
            Rejected(CommandRejection.VersionConflict(current = 2)) to
                listOf("1|$pan|${uuid(100)}|2026-10-03|12000|COP|null|2|$recorded|$recorded")
        } else {
            Rejected(Specific(ExpenseRejection.ExpenseMissing)) to emptyList()
        }
        val loser = if (outcomes.first is Committed) outcomes.second else outcomes.first
        assertEquals(1, listOf(outcomes.first, outcomes.second).count { it is Committed })
        assertEquals(expected, loser to expenses())
        assertEquals(3L, ledger.revision())
        assertEquals(3, receipts().size)
    }

    /** Runs [command] in a caller that is cancelled while the command is saving. */
    private suspend fun cancelledWhileSaving(
        command: suspend () -> ExpenseCommandOutcome
    ): Pair<ExpenseCommandOutcome?, CancellationException?> = coroutineScope {
        lateinit var caller: Job
        var returned: ExpenseCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }
        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = command()
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}
        returned to cancellation
    }

    @Test
    fun aCallerCancelledWhileAnEditIsSavingIsToldSoAndCanRecoverTheSavedEdit() = runBlocking {
        start()
        val change = edit(version = 1, pesos = 12_000)

        val (returned, cancellation) = cancelledWhileSaving { ledger.expenses.edit(change) }

        // The caller is told it was cancelled, never "saved" or "not saved".
        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(listOf("12000|2"), ledger.rows("expense", "amount_minor", "version"))
        val found = (ledger.expenses.find(change.operationId) as Outcome.Success).value
        assertEquals(Committed(found!!, replayed = true), ledger.expenses.edit(change))
        assertEquals(3L, ledger.revision())
    }

    @Test
    fun aCallerCancelledWhileADeletionIsSavingStillLeavesItRecoverableAndUndoable() = runBlocking {
        start()
        ledger.elapsed = 7.hours
        val removal = delete(version = 1)

        val (returned, cancellation) = cancelledWhileSaving { ledger.expenses.delete(removal) }

        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(emptyList<String>(), expenses())
        val found = (ledger.expenses.find(removal.operationId) as Outcome.Success).value
        assertEquals(OperationKind.DeleteExpense, found!!.receipt.kind)
        // The deletion happened, so its six seconds are running even though nobody was told.
        assertEquals(
            UndoOffer(removal.operationId, removal.expenseId, until = 7.hours + 6.seconds),
            ledger.expenses.undoOffer()
        )
        val undone = ledger.expenses.restore(
            RestoreExpense(ledger.operation(), 1, removal.operationId)
        )
        assertTrue("$undone", undone is Committed)
        assertEquals(
            listOf("1|$pan|${uuid(100)}|2026-10-03|10000|COP|null|1|$recorded|$recorded"),
            expenses()
        )
    }

    // ---- What the month shows ----

    @Test
    fun theMonthsTotalFollowsAnEditADeletionAndAnUndoWithoutBeingToldTo() = runBlocking {
        start()
        val october = YearMonth.of(2026, 10)
        val states = Channel<SnapshotState>(Channel.UNLIMITED)
        val watching = launch(Dispatchers.Default) {
            ledger.snapshots.observe(october).collect { states.send(it) }
        }
        suspend fun spentAt(revision: Long): Money = withTimeout(15_000) {
            var ready = states.receive() as? SnapshotState.Ready
            while (ready?.snapshot?.revision != revision) {
                ready = states.receive() as? SnapshotState.Ready
            }
            ready.snapshot.spent
        }
        val recordedTotal = spentAt(2)

        ledger.expenses.edit(edit(version = 1, pesos = 12_000))
        val edited = spentAt(3)
        val deletion = ledger.operation()
        ledger.expenses.delete(delete(version = 2, operation = deletion))
        val deleted = spentAt(4)
        ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))
        val restored = spentAt(5)
        watching.cancel()

        // 10.000, then 2.000 more, then nothing, then the 12.000 again.
        assertEquals(
            listOf(Money.cop(10_000), Money.cop(12_000), Money.cop(0), Money.cop(12_000)),
            listOf(recordedTotal, edited, deleted, restored)
        )
    }
}
