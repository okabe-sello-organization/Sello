package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommandOutcome.Committed
import com.software.sello.domain.port.IncomeCommandOutcome.Rejected
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonth
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.IncomeUndoOffer
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
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
 * Recording, changing, removing and putting back income, against a real database file.
 * Every case reads the stored rows, the receipts and the revision itself, not only what
 * the command answered. Records are numbered from 100 and operations from 1000; today
 * is 9 October 2026.
 */
@RunWith(AndroidJUnit4::class)
class IncomeCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val recorded = Instant.parse("2026-10-09T15:04:05.678Z").toEpochMilli()
    private val later = Instant.parse("2026-10-12T08:00:00.250Z")
    private val october = YearMonth.of(2026, 10)

    private fun incomes() = ledger.rows(
        "income", "sequence", "id", "effective_date", "amount_minor", "currency",
        "source_key", "source_name", "note", "version", "created_at", "updated_at"
    )

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private fun keptIncome() = ledger.rows("deleted_income", "sequence", "deletion_id", "income_id")

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private fun source(key: String, name: String? = null) = IncomeSource.of(key, name).valueOrFail()

    private fun incomeId(number: Int) = IncomeId.of(uuid(number)).valueOrFail()

    private fun create(
        pesos: Long = 150_000,
        key: String = "salary",
        name: String? = null,
        date: String = "2026-10-09",
        note: String? = null,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = CreateIncome(
        operation,
        generation,
        pesos(pesos),
        source(key, name),
        LocalDate.parse(date),
        Note.of(note).valueOrFail()
    )

    private fun edit(
        version: Long,
        pesos: Long = 150_000,
        key: String = "salary",
        name: String? = null,
        date: String = "2026-10-09",
        note: String? = null,
        income: Int = 100,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = EditIncome(
        operation,
        generation,
        incomeId(income),
        version,
        pesos(pesos),
        source(key, name),
        LocalDate.parse(date),
        Note.of(note).valueOrFail()
    )

    private fun delete(
        version: Long,
        income: Int = 100,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = DeleteIncome(operation, generation, incomeId(income), version)

    private fun committed(outcome: IncomeCommandOutcome) = (outcome as Committed).income

    private fun raw(sql: String) = ledger.database.openHelper.writableDatabase.execSQL(sql)

    /** One salary of 150.000 today: income 100, sequence 1, revision 1. */
    private val salary = "1|${uuid(
        100
    )}|2026-10-09|150000|COP|salary|null|null|1|$recorded|$recorded"

    private suspend fun start() {
        val created = ledger.incomes.create(create())
        assertTrue("$created", created is Committed)
        assertEquals(listOf(salary), incomes())
    }

    // ---- Recording ----

    @Test
    fun anIncomeIsOneRowWithExactlyWhatWasGivenOneRevisionAndOneReceipt() = runBlocking {
        val operation = ledger.operation()

        val outcome = ledger.incomes.create(
            create(850_000, "other", "Diseño", "2026-10-08", "Logo", operation)
        )

        val saved = committed(outcome)
        assertEquals(false, (outcome as Committed).replayed)
        assertEquals(
            OperationKind.CreateIncome to uuid(100),
            saved.receipt.kind to saved.incomeId.value
        )
        assertEquals(
            listOf("1|${uuid(100)}|2026-10-08|850000|COP|other|Diseño|Logo|1|$recorded|$recorded"),
            incomes()
        )
        assertEquals(1L, ledger.revision())
        assertEquals(listOf("1|income.create|${uuid(100)}"), receipts())
        assertEquals(Outcome.Success(saved), ledger.incomes.find(operation))
    }

    @Test
    fun theSameIncomeSentTwiceIsOneRowAndADifferentOneUnderItsNameIsAConflict() = runBlocking {
        val operation = ledger.operation()
        val first = ledger.incomes.create(create(operation = operation))

        val again = ledger.incomes.create(create(operation = operation))
        val otherAmount = ledger.incomes.create(create(pesos = 160_000, operation = operation))
        val otherSource = ledger.incomes.create(create(key = "freelance", operation = operation))

        assertEquals(Committed(committed(first), replayed = true), again)
        assertEquals(Rejected(IncomeRejection.OperationConflict), otherAmount)
        assertEquals(Rejected(IncomeRejection.OperationConflict), otherSource)
        assertEquals(listOf(salary), incomes())
        assertEquals(1L, ledger.revision())
    }

    @Test
    fun anIncomeCannotBeDatedAfterTodayOrOnADateThatCannotBeStored() = runBlocking {
        val tomorrow = ledger.incomes.create(create(date = "2026-10-10"))
        val unreadable = ledger.incomes.create(create(date = "-0001-01-01"))
        val stale = ledger.incomes.create(create(generation = 2))

        assertEquals(Rejected(IncomeRejection.FutureDate(LocalDate.of(2026, 10, 9))), tomorrow)
        assertEquals(Rejected(IncomeRejection.InvalidDate), unreadable)
        assertEquals(Rejected(IncomeRejection.StaleGeneration(current = 1)), stale)
        assertEquals(emptyList<String>(), incomes())
        assertEquals(0L, ledger.revision())
    }

    @Test
    fun anIncomeThatCannotFinishLeavesNothingBehind() = runBlocking {
        raw("UPDATE profile SET revision = ${Long.MAX_VALUE}")

        val outcome = ledger.incomes.create(create())

        assertEquals(
            Rejected(
                IncomeRejection.StorageFailed(StorageFailure.Integrity("profile", null, "revision"))
            ),
            outcome
        )
        assertEquals(emptyList<String>(), incomes())
        assertEquals(emptyList<String>(), receipts())
    }

    @Test
    fun aCallerCancelledWhileAnIncomeIsSavingIsToldSoAndCanRecoverIt() = runBlocking {
        val command = create()
        lateinit var caller: Job
        var returned: IncomeCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.incomes.create(command)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(listOf(salary), incomes())
        val found = (ledger.incomes.find(command.operationId) as Outcome.Success).value
        assertEquals(Committed(found!!, replayed = true), ledger.incomes.create(command))
    }

    // ---- Income and expenses do not touch each other ----

    @Test
    fun incomeChangesNoExpenseNoBudgetAndNoMonthFigureAndExpensesChangeNoIncome() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 10_000))
        val expensesBefore = ledger.rows("expense", "id", "amount_minor", "version")
        val limitsBefore = ledger.rows("default_limit", "category_id", "limit_minor")
        val spentBefore = (ledger.snapshots.read(october) as Outcome.Success).value.spent

        ledger.incomes.create(create(pesos = 5_000_000))
        ledger.incomes.edit(edit(version = 1, pesos = 6_000_000, income = 102))
        val incomesMidway = incomes()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-04", 7_000))

        assertEquals(
            expensesBefore,
            ledger.rows("expense", "id", "amount_minor", "version").take(1)
        )
        assertEquals(limitsBefore, ledger.rows("default_limit", "category_id", "limit_minor"))
        assertEquals(Money.cop(10_000), spentBefore)
        // 10.000 and 7.000 spent; six million of income changes neither.
        assertEquals(
            Money.cop(17_000),
            (ledger.snapshots.read(october) as Outcome.Success).value.spent
        )
        assertEquals(incomesMidway, incomes())
        // Each kind keeps its own order: the second expense is 2, the income is 1.
        assertEquals(listOf("1"), ledger.rows("income", "sequence"))
        assertEquals(listOf("1", "2"), ledger.rows("expense", "sequence"))
        // An operation that recorded an expense is not found as an income, nor the reverse.
        assertEquals(Outcome.Success(null), ledger.incomes.find(ledger.operation(1001)))
    }

    // ---- Editing ----

    @Test
    fun everyFieldThatCanChangeIsSavedOnTheSameRecordOneVersionOn() = runBlocking {
        start()
        ledger.audit = later

        val outcome = ledger.incomes.edit(
            edit(
                version = 1,
                pesos = 160_000,
                key = "other",
                name = "Bono",
                date = "2026-09-30",
                note = "Extra"
            )
        )

        val saved = committed(outcome)
        assertEquals(
            OperationKind.EditIncome to uuid(100),
            saved.receipt.kind to saved.incomeId.value
        )
        assertEquals(
            listOf(
                "1|${uuid(
                    100
                )}|2026-09-30|160000|COP|other|Bono|Extra|2|$recorded|${later.toEpochMilli()}"
            ),
            incomes()
        )
        assertEquals(2L, ledger.revision())
        assertEquals("2|income.edit|${uuid(100)}", receipts().last())
    }

    @Test
    fun anEditOfAnOldVersionAMissingIncomeOrNothingAtAllSavesNothing() = runBlocking {
        start()
        ledger.incomes.edit(edit(version = 1, pesos = 160_000))
        val after = incomes()

        val stale = ledger.incomes.edit(edit(version = 1, pesos = 999))
        val never = ledger.incomes.edit(edit(version = 1, income = 999))
        val same = ledger.incomes.edit(edit(version = 2, pesos = 160_000))
        val replaced = ledger.incomes.edit(edit(version = 2, pesos = 5, generation = 2))

        assertEquals(Rejected(IncomeRejection.VersionConflict(current = 2)), stale)
        assertEquals(Rejected(IncomeRejection.IncomeMissing), never)
        assertEquals(Rejected(IncomeRejection.Unchanged), same)
        assertEquals(Rejected(IncomeRejection.StaleGeneration(current = 1)), replaced)
        assertEquals(after, incomes())
        assertEquals(2L, ledger.revision())
    }

    @Test
    fun theDateIsCheckedOnlyWhenAnEditChangesIt() = runBlocking {
        start()
        // The financial day moved back: the income of the 9th is now ahead of today.
        ledger.goTo("2026-10-01")

        val amountOnly = ledger.incomes.edit(edit(version = 1, pesos = 160_000))
        val toAnotherFutureDay = ledger.incomes.edit(
            edit(version = 2, pesos = 160_000, date = "2026-10-02")
        )

        assertTrue("$amountOnly", amountOnly is Committed)
        assertEquals(
            Rejected(IncomeRejection.FutureDate(LocalDate.of(2026, 10, 1))),
            toAnotherFutureDay
        )
        assertEquals(
            listOf("2026-10-09|160000|2"),
            ledger.rows("income", "effective_date", "amount_minor", "version")
        )
    }

    @Test
    fun ofSeveralEditsMadeAtOnceFromTheSameVersionExactlyOneIsSaved() = runBlocking {
        start()
        val edits = (1..8).map { edit(version = 1, pesos = 200_000L + it) }

        val outcomes = edits.map {
            async(Dispatchers.Default) { ledger.incomes.edit(it) }
        }.awaitAll()

        assertEquals(1, outcomes.count { it is Committed })
        assertEquals(
            7,
            outcomes.count {
                it ==
                    Rejected(IncomeRejection.VersionConflict(current = 2))
            }
        )
        assertEquals(listOf("2"), ledger.rows("income", "version"))
        assertEquals(2L, ledger.revision())
    }

    // ---- Deleting and the six seconds (U01 to U05) ----

    @Test
    fun deletingRemovesTheIncomeKeepsWhatItWasAndOffersSixSeconds() = runBlocking {
        start()
        ledger.elapsed = 7.hours
        val operation = ledger.operation()

        val outcome = ledger.incomes.delete(delete(version = 1, operation = operation))

        val saved = committed(outcome)
        assertEquals(
            OperationKind.DeleteIncome to uuid(100),
            saved.receipt.kind to saved.incomeId.value
        )
        assertEquals(emptyList<String>(), incomes())
        assertEquals(listOf("1|${operation.value}|${uuid(100)}"), keptIncome())
        assertEquals("2|income.delete|${uuid(100)}", receipts().last())
        assertEquals(
            IncomeUndoOffer(operation, incomeId(100), until = 7.hours + 6.seconds),
            ledger.incomes.undoOffer()
        )
        // The offer is an income's: the expense workflow has nothing to undo.
        assertNull(ledger.expenses.undoOffer())
    }

    @Test
    fun onlyTheVersionThatWasReadCanBeDeletedAndAMissingIncomeCannot() = runBlocking {
        start()
        ledger.incomes.edit(edit(version = 1, pesos = 160_000))

        val stale = ledger.incomes.delete(delete(version = 1))
        val never = ledger.incomes.delete(delete(version = 1, income = 999))

        assertEquals(Rejected(IncomeRejection.VersionConflict(current = 2)), stale)
        assertEquals(Rejected(IncomeRejection.IncomeMissing), never)
        assertEquals(1, incomes().size)
        assertEquals(emptyList<String>(), keptIncome())
        assertNull(ledger.incomes.undoOffer())
    }

    /** The salary deleted at elapsed time 7 h. Returns the deletion's operation. */
    private suspend fun deleted(): OperationId {
        start()
        ledger.elapsed = 7.hours
        val deletion = ledger.operation()
        val removed = ledger.incomes.delete(delete(version = 1, operation = deletion))
        assertTrue("$removed", removed is Committed)
        return deletion
    }

    private fun undo(
        deletion: OperationId,
        operation: OperationId = ledger.operation(),
        generation: Long = 1
    ) = RestoreIncome(operation, generation, deletion)

    @Test
    fun atFiveSecondsNineNineNineUndoPutsBackExactlyTheIncomeThatWasDeleted() = runBlocking {
        val deletion = deleted()
        ledger.elapsed = 7.hours + 5999.milliseconds
        ledger.audit = later

        val outcome = ledger.incomes.restore(undo(deletion))

        val restored = committed(outcome)
        assertEquals(
            OperationKind.RestoreIncome to uuid(100),
            restored.receipt.kind to restored.incomeId.value
        )
        // Every column as it was: its place in the order, its version, both timestamps.
        assertEquals(listOf(salary), incomes())
        assertEquals(emptyList<String>(), keptIncome())
        assertEquals("3|income.restore|${uuid(100)}", receipts().last())
        assertNull(ledger.incomes.undoOffer())
    }

    @Test
    fun atSixSecondsExactlyUndoIsRefusedAndTheOfferEndedByItself() = runBlocking {
        val deletion = deleted()
        ledger.elapsed = 7.hours + 5.seconds
        val stillThere = ledger.incomes.undoOffer()
        ledger.elapsed = 7.hours + 6.seconds

        val gone = ledger.incomes.undoOffer()
        val outcome = ledger.incomes.restore(undo(deletion))

        assertEquals(7.hours + 6.seconds, stillThere!!.until)
        assertNull(gone)
        assertEquals(Rejected(IncomeRejection.UndoExpired), outcome)
        assertEquals(emptyList<String>(), incomes())
        assertEquals(2L, ledger.revision())
    }

    @Test
    fun afterTheProcessEndsThereIsNoOfferTheDeletionStandsAndItsReceiptIsFound() = runBlocking {
        val deletion = deleted()
        ledger.elapsed = 7.hours + 2.seconds

        ledger.reopen()
        val offer = ledger.incomes.undoOffer()
        val outcome = ledger.incomes.restore(undo(deletion))
        val discarded = ledger.storage().discardUndoCopies()

        assertNull(offer)
        assertEquals(Rejected(IncomeRejection.UndoExpired), outcome)
        assertEquals(emptyList<String>(), incomes())
        val found = (ledger.incomes.find(deletion) as Outcome.Success).value
        assertEquals(OperationKind.DeleteIncome, found!!.receipt.kind)
        assertEquals(Outcome.Success(Unit), discarded)
        assertEquals(emptyList<String>(), keptIncome())
    }

    @Test
    fun anUndoThatCommittedBeforeTheProcessEndedIsFoundAndIsNotDoneTwice() = runBlocking {
        val deletion = deleted()
        ledger.elapsed = 7.hours + 3.seconds
        val operation = ledger.operation()
        val first = committed(ledger.incomes.restore(undo(deletion, operation)))

        ledger.reopen()
        ledger.elapsed = 17.hours
        val again = ledger.incomes.restore(undo(deletion, operation))

        assertEquals(Outcome.Success(first), ledger.incomes.find(operation))
        assertEquals(Committed(first, replayed = true), again)
        assertEquals(listOf(salary), incomes())
        assertEquals(3L, ledger.revision())
    }

    @Test
    fun movingTheFinancialDayAYearDoesNotChangeTheSixSeconds() = runBlocking {
        val deletion = deleted()
        ledger.goTo("2027-10-09")
        ledger.elapsed = 7.hours + 5.seconds
        val inTime = ledger.incomes.undoOffer()
        ledger.elapsed = 7.hours + 6.seconds

        val late = ledger.incomes.restore(undo(deletion))

        assertEquals(7.hours + 6.seconds, inTime!!.until)
        assertEquals(Rejected(IncomeRejection.UndoExpired), late)
    }

    @Test
    fun undoIsRefusedWhenTheHistoryWasReplacedOrTheIncomesPlaceWasTaken() = runBlocking {
        val deletion = deleted()
        ledger.elapsed = 7.hours + 1.seconds
        raw(
            "INSERT INTO income VALUES ('${uuid(555)}', '2026-10-08', 1, 700, 'COP', 'salary', " +
                "NULL, NULL, 1, 0, 0, NULL, NULL, NULL)"
        )
        val sequenceTaken = ledger.incomes.restore(undo(deletion))
        raw("DELETE FROM income")
        raw(
            "INSERT INTO income VALUES ('${uuid(100)}', '2026-10-08', 9, 700, 'COP', 'salary', " +
                "NULL, NULL, 1, 0, 0, NULL, NULL, NULL)"
        )
        val identifierTaken = ledger.incomes.restore(undo(deletion))
        raw("DELETE FROM income")
        raw("UPDATE profile SET generation = 2")
        val prepared = ledger.incomes.restore(undo(deletion, generation = 1))
        val otherHistory = ledger.incomes.restore(undo(deletion, generation = 2))

        assertEquals(Rejected(IncomeRejection.UndoConflict), sequenceTaken)
        assertEquals(Rejected(IncomeRejection.UndoConflict), identifierTaken)
        assertEquals(Rejected(IncomeRejection.StaleGeneration(current = 2)), prepared)
        assertEquals(Rejected(IncomeRejection.UndoConflict), otherHistory)
        assertEquals(emptyList<String>(), incomes())
        assertEquals(0, receipts().count { it.contains("income.restore") })
    }

    @Test
    fun aNewIncomeDoesNotTakeThePlaceOfOneThatCanStillComeBack() = runBlocking {
        deleted()

        ledger.incomes.create(create(pesos = 7_000))

        assertEquals(listOf("2|7000"), ledger.rows("income", "sequence", "amount_minor"))
    }

    // ---- One offer for expenses and income ----

    @Test
    fun deletingAnIncomeMakesAnEarlierExpenseDeletionFinal() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 10_000))
        ledger.incomes.create(create())
        val expenseDeletion = ledger.operation()
        ledger.expenses.delete(
            DeleteExpense(expenseDeletion, 1, ExpenseId.of(uuid(101)).valueOrFail(), 1)
        )
        val offered = ledger.expenses.undoOffer()

        ledger.incomes.delete(delete(version = 1, income = 102))
        val expenseUndo = ledger.expenses.restore(
            RestoreExpense(ledger.operation(), 1, expenseDeletion)
        )

        assertEquals(expenseDeletion, offered!!.deletionId)
        assertNull(ledger.expenses.undoOffer())
        assertEquals(ExpenseCommandOutcome.Rejected(ExpenseRejection.UndoExpired), expenseUndo)
        // One kept copy across both kinds, the income's.
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM deleted_expense"))
        assertEquals(1, keptIncome().size)
        assertEquals(emptyList<String>(), ledger.rows("expense", "id"))
    }

    @Test
    fun deletingAnExpenseMakesAnEarlierIncomeDeletionFinal() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 10_000))
        ledger.incomes.create(create())
        val incomeDeletion = ledger.operation()
        ledger.incomes.delete(delete(version = 1, income = 102, operation = incomeDeletion))

        ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, ExpenseId.of(uuid(101)).valueOrFail(), 1)
        )
        val incomeUndo = ledger.incomes.restore(undo(incomeDeletion))

        assertNull(ledger.incomes.undoOffer())
        assertEquals(Rejected(IncomeRejection.UndoExpired), incomeUndo)
        assertEquals(emptyList<String>(), keptIncome())
        assertEquals(1L, ledger.long("SELECT COUNT(*) FROM deleted_expense"))
        assertEquals(emptyList<String>(), incomes())
    }

    // ---- Reading ----

    @Test
    fun aMonthsIncomeIsListedNewestFirstInPagesAndReadBackExactly() = runBlocking {
        ledger.incomes.create(create(pesos = 1_000, date = "2026-10-01"))
        ledger.incomes.create(
            create(pesos = 2_000, date = "2026-10-05", key = "other", name = "Bono", note = "Extra")
        )
        ledger.incomes.create(create(pesos = 3_000, date = "2026-10-05"))
        ledger.incomes.create(create(pesos = 4_000, date = "2026-09-30"))

        val first = ledger.incomeReads.inMonth(october, null, 2).valueOrFail()
        val second = ledger.incomeReads.inMonth(october, first.next, 2).valueOrFail()
        val one = ledger.incomeReads.byId(incomeId(101)).valueOrFail()!!

        // Within a day the one added last comes first; September's is not October's.
        assertEquals(listOf(3_000L, 2_000L), first.incomes.map { it.amount.money.minorUnits })
        assertEquals(IncomeCursor(LocalDate.of(2026, 10, 5), 2), first.next)
        assertEquals(listOf(1_000L), second.incomes.map { it.amount.money.minorUnits })
        assertNull(second.next)
        assertEquals(
            listOf("other", "Bono", "Extra", 1L),
            listOf(
                one.source.key,
                (one.source as IncomeSource.Other).name.value,
                one.note?.value,
                one.version
            )
        )
        assertEquals(Outcome.Success(null), ledger.incomeReads.byId(incomeId(999)))
    }

    @Test
    fun aMonthsTotalIsExactAndAMonthWithoutIncomeIsZeroNotAFailure() = runBlocking {
        val empty = ledger.incomeReads.month(october).valueOrFail()
        ledger.incomes.create(create(pesos = 5_200_000))
        ledger.incomes.create(create(pesos = 850_000, date = "2026-10-08"))
        ledger.incomes.create(create(pesos = 999, date = "2026-09-30"))

        val read = ledger.incomeReads.month(october).valueOrFail()

        assertEquals(IncomeMonth(october, 1, 0, Money.cop(0), 0), empty)
        // 5.200.000 and 850.000; September's 999 is not in it.
        assertEquals(IncomeMonth(october, 1, 3, Money.cop(6_050_000), 2), read)
    }

    @Test
    fun anIncomeThatCannotBeReadFailsTheMonthAndIsNeverCountedAsZero() = runBlocking {
        ledger.incomes.create(create())
        raw("UPDATE income SET amount_minor = 0")

        val read = ledger.incomeReads.month(october)
        val list = ledger.incomeReads.inMonth(october, null, 10)

        val failure = StorageFailure.Integrity("income", uuid(100), "amount_minor")
        assertEquals(Outcome.Failure(failure), read)
        assertEquals(Outcome.Failure(failure), list)
    }

    @Test
    fun whoeverWatchesAMonthSeesItsTotalFollowEveryChangeWithoutAsking() = runBlocking {
        val states = Channel<IncomeMonthState>(Channel.UNLIMITED)
        val watching = launch(Dispatchers.Default) {
            ledger.incomeReads.observeMonth(october).collect { states.send(it) }
        }
        suspend fun totalAt(revision: Long): Money = withTimeout(15_000) {
            var ready = states.receive() as? IncomeMonthState.Ready
            while (ready?.month?.revision != revision) {
                ready = states.receive() as? IncomeMonthState.Ready
            }
            ready.month.total
        }
        val atFirst = totalAt(0)

        ledger.incomes.create(create(pesos = 150_000))
        val recordedTotal = totalAt(1)
        ledger.incomes.edit(edit(version = 1, pesos = 160_000))
        val edited = totalAt(2)
        val deletion = ledger.operation()
        ledger.incomes.delete(delete(version = 2, operation = deletion))
        val deletedTotal = totalAt(3)
        ledger.incomes.restore(undo(deletion))
        val restored = totalAt(4)
        watching.cancel()

        assertEquals(
            listOf(0L, 150_000L, 160_000L, 0L, 160_000L),
            listOf(atFirst, recordedTotal, edited, deletedTotal, restored).map { it.minorUnits }
        )
    }
}
