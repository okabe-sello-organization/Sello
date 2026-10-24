package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.repository.RoomExpenseCommands
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.data.repository.UndoWindow
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommandOutcome.Committed
import com.software.sello.domain.port.ExpenseCommandOutcome.Rejected
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.RecordIdSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The create-expense workflow against a real database file. Every case checks the
 * stored rows, the receipt and the revision themselves, not only what was returned.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val bogota = ZoneId.of("America/Bogota")

    // Real time has microseconds; what is stored and returned is whole milliseconds.
    private val audit = Instant.parse("2026-10-09T15:04:05.678912Z")
    private val auditMillis = Instant.parse("2026-10-09T15:04:05.678Z")
    private val financialToday = MutableStateFlow(FinancialDay(LocalDate.of(2026, 10, 9), bogota))
    private val clock = object : FinancialClock {
        override val today = financialToday
    }

    private var nextExpense = 100
    private var whenAnIdIsTaken: () -> Unit = {}
    private val ids = RecordIdSource {
        whenAnIdIsTaken()
        uuid(nextExpense++)
    }

    private lateinit var database: SelloDatabase
    private lateinit var commands: ExpenseCommands

    private fun open() {
        database = files.open()
        commands = RoomExpenseCommands(database, { audit }, clock, ids, UndoWindow { 5.hours })
    }

    /** A new installation with one category, "Mercado", number 1. */
    private fun start() = runBlocking {
        open()
        RoomFinancialProfileStore(database) { audit }.establish(bogota)
        database.categoryDao().insert(category(1, "Mercado").toEntity())
    }

    private fun command(
        operation: Int,
        pesos: Long = 10_000,
        date: String = "2026-10-03",
        note: String? = "Pan",
        categoryNumber: Int = 1,
        generation: Long = 1
    ) = CreateExpense(
        operationId = OperationId.of(uuid(operation)).valueOrFail(),
        generation = generation,
        categoryId = CategoryId.of(uuid(categoryNumber)).valueOrFail(),
        amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
        date = LocalDate.parse(date),
        note = Note.of(note).valueOrFail()
    )

    private fun committed(operation: Int, expense: Int, revision: Long) = CommittedExpense(
        OperationReceipt(
            operationId = OperationId.of(uuid(operation)).valueOrFail(),
            kind = OperationKind.CreateExpense,
            generation = 1,
            revision = revision,
            committedAt = auditMillis
        ),
        ExpenseId.of(uuid(expense)).valueOrFail()
    )

    private suspend fun expenses(): List<ExpenseEntity> =
        database.expenseDao().page("0000-01-01", "9999-12-32", "", 0, 1000)

    private fun revision(): Long =
        database.openHelper.readableDatabase.query("SELECT revision FROM profile").use {
            it.moveToFirst()
            it.getLong(0)
        }

    private suspend fun assertNothingWritten() {
        assertEquals(emptyList<ExpenseEntity>(), expenses())
        assertEquals(0, database.count("operation_receipt"))
        assertEquals(0, revision())
    }

    private fun storedPan(expense: Int = 100, sequence: Long = 1) = ExpenseEntity(
        id = uuid(expense),
        categoryId = uuid(1),
        effectiveDate = "2026-10-03",
        sequence = sequence,
        amountMinor = 10_000,
        currency = "COP",
        note = "Pan",
        version = 1,
        createdAt = auditMillis.toEpochMilli(),
        updatedAt = auditMillis.toEpochMilli(),
        originScheduleId = null,
        originDate = null,
        originSlot = null
    )

    @Test
    fun anExpenseIsStoredWithItsReceiptAndOneRevisionInOneStep() = runBlocking {
        start()

        val outcome = commands.create(command(50))

        assertEquals(Committed(committed(50, 100, 1), replayed = false), outcome)
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, revision())
        assertEquals(
            OperationReceiptEntity(
                operationId = uuid(50),
                kind = "expense.create",
                // SHA-256 of the canonical input, worked out separately from the app.
                inputDigest = "465545f44c6201d2afd33700f8f5b81f6465f70a727363cf1a25055f160148fd",
                generation = 1,
                revision = 1,
                subjectId = uuid(100),
                committedAt = auditMillis.toEpochMilli()
            ),
            database.operationReceiptDao().find(uuid(50))
        )
        assertEquals(Outcome.Success(committed(50, 100, 1)), commands.find(command(50).operationId))
    }

    @Test
    fun anExpenseWithoutANoteHasItsOwnInputIdentity() = runBlocking {
        start()

        commands.create(command(50, note = null))

        assertEquals(
            "24bb1ce4cb90c66045e923d14de5b9fc43b0c4039306dd030cac1309f596dc92",
            database.operationReceiptDao().find(uuid(50))!!.inputDigest
        )
        assertNull(expenses().single().note)
    }

    @Test
    fun submittingTheSameOperationAndInputAgainReturnsTheOriginalAndWritesNothing() = runBlocking {
        start()
        val first = commands.create(command(50))

        val again = commands.create(command(50))

        assertEquals(Committed(committed(50, 100, 1), replayed = false), first)
        assertEquals(Committed(committed(50, 100, 1), replayed = true), again)
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(1, revision())
    }

    @Test
    fun reusingAnOperationWithAnyDifferentInputIsAConflictAndChangesNothing() = runBlocking {
        start()
        database.categoryDao().insert(category(2, "Transporte").toEntity())
        commands.create(command(50))
        val changed = listOf(
            command(50, pesos = 12_000),
            command(50, date = "2026-10-04"),
            command(50, note = "Pan y leche"),
            command(50, note = null),
            command(50, categoryNumber = 2)
        )

        for (different in changed) {
            assertEquals(
                "$different",
                Rejected(ExpenseRejection.OperationConflict),
                commands.create(different)
            )
        }

        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(1, revision())
        assertEquals(Outcome.Success(committed(50, 100, 1)), commands.find(command(50).operationId))
    }

    @Test
    fun aSecondOperationGetsTheNextSequenceAndRevision() = runBlocking {
        start()
        commands.create(command(50))

        val second = commands.create(command(51, pesos = 15_000, date = "2026-10-02", note = null))

        assertEquals(Committed(committed(51, 101, 2), replayed = false), second)
        assertEquals(
            listOf(uuid(101) to 2L, uuid(100) to 1L),
            expenses().map { it.id to it.sequence }
        )
        assertEquals(2, revision())
    }

    @Test
    fun aCommandPreparedAgainstAReplacedHistoryIsRejected() = runBlocking {
        start()
        commands.create(command(50))
        database.openHelper.writableDatabase.execSQL("UPDATE profile SET generation = 2")

        val stale = commands.create(command(51))
        val staleReplay = commands.create(command(50))
        val ahead = commands.create(command(52, generation = 3))

        assertEquals(Rejected(ExpenseRejection.StaleGeneration(current = 2)), stale)
        assertEquals(Rejected(ExpenseRejection.StaleGeneration(current = 2)), staleReplay)
        assertEquals(Rejected(ExpenseRejection.StaleGeneration(current = 2)), ahead)
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(1, revision())
    }

    @Test
    fun aMissingCategoryIsRejectedAndNothingIsWritten() = runBlocking {
        start()

        val outcome = commands.create(command(50, categoryNumber = 9))

        assertEquals(Rejected(ExpenseRejection.CategoryMissing), outcome)
        assertNothingWritten()
        assertEquals(Outcome.Success(null), commands.find(command(50).operationId))
    }

    @Test
    fun aCategoryArchivedAfterItWasChosenIsRejectedInsideTheTransaction() = runBlocking {
        start()
        commands.create(command(49))
        database.openHelper.writableDatabase.execSQL("UPDATE category SET archived = 1")

        val outcome = commands.create(command(50))
        val earlier = commands.create(command(49))

        assertEquals(Rejected(ExpenseRejection.CategoryArchived), outcome)
        // An operation that committed before the archive still answers with its receipt.
        assertEquals(Committed(committed(49, 100, 1), replayed = true), earlier)
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, revision())
    }

    @Test
    fun theDateLimitIsTheFinancialDayNotTheRealClock() = runBlocking {
        start()
        // The real clock says 9 October; simulated financial time has moved to December.
        financialToday.value = FinancialDay(LocalDate.of(2026, 12, 31), bogota)

        val onTheDay = commands.create(command(50, date = "2026-12-31"))
        val dayAfter = commands.create(command(51, date = "2027-01-01"))

        assertEquals(Committed(committed(50, 100, 1), replayed = false), onTheDay)
        assertEquals(
            Rejected(ExpenseRejection.FutureDate(today = LocalDate.of(2026, 12, 31))),
            dayAfter
        )
        assertEquals(listOf("2026-12-31"), expenses().map { it.effectiveDate })
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(1, revision())
    }

    @Test
    fun aDateTheStoredTextCannotHoldIsRefusedBeforeAnythingIsWritten() = runBlocking {
        start()

        // Year -1 would be stored as "-0001-01-01" and never read back.
        val before = commands.create(command(50, date = "-0001-01-01"))
        val longBefore = commands.create(command(51, date = "-2026-10-03"))

        assertEquals(Rejected(ExpenseRejection.InvalidDate), before)
        assertEquals(Rejected(ExpenseRejection.InvalidDate), longBefore)
        assertNothingWritten()
        assertNull(commands.find(command(50).operationId).valueOrFail())
    }

    @Test
    fun onADayPastYear9999TodayItselfCannotBeStoredAndIsRefused() = runBlocking {
        start()
        financialToday.value = FinancialDay(LocalDate.of(10000, 1, 1), bogota)

        val outcome = commands.create(command(50, date = "+10000-01-01"))

        assertEquals(Rejected(ExpenseRejection.InvalidDate), outcome)
        assertNothingWritten()
    }

    @Test
    fun theOldestAndOrdinaryOldDatesAreStillSavedAndReadBackAsThemselves() = runBlocking {
        start()

        val first = commands.create(command(50, date = "0000-01-01"))
        val leap = commands.create(command(51, date = "1904-02-29"))

        assertEquals(Committed(committed(50, 100, 1), replayed = false), first)
        assertEquals(Committed(committed(51, 101, 2), replayed = false), leap)
        assertEquals(
            listOf(LocalDate.of(0, 1, 1), LocalDate.of(1904, 2, 29)),
            expenses().map { it.toDomain().valueOrFail().date }
        )
    }

    private fun raw(sql: String) = database.openHelper.writableDatabase.execSQL(sql)

    private fun integrity(record: String, field: String) =
        Rejected(ExpenseRejection.StorageFailed(StorageFailure.Integrity(record, null, field)))

    @Test
    fun aRevisionThatCannotAdvanceRefusesTheExpenseAndWritesNothing() = runBlocking {
        start()
        raw("UPDATE profile SET revision = ${Long.MAX_VALUE}")

        val outcome = commands.create(command(50))

        assertEquals(integrity("profile", "revision"), outcome)
        assertEquals(emptyList<ExpenseEntity>(), expenses())
        assertEquals(0, database.count("operation_receipt"))
        assertEquals(Long.MAX_VALUE, revision())
        assertNull(commands.find(command(50).operationId).valueOrFail())
    }

    @Test
    fun theLastRevisionCanStillBeReachedAndOnlyTheChangeAfterItIsRefused() = runBlocking {
        start()
        raw("UPDATE profile SET revision = ${Long.MAX_VALUE - 1}")

        val last = commands.create(command(50))
        val beyond = commands.create(command(51, pesos = 2_000))

        assertEquals(
            Long.MAX_VALUE,
            ((last as Committed).expense.receipt).revision
        )
        assertEquals(integrity("profile", "revision"), beyond)
        assertEquals(listOf(10_000L), expenses().map { it.amountMinor })
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(Long.MAX_VALUE, revision())
    }

    @Test
    fun anOrderingSequenceThatCannotAdvanceRefusesTheExpenseAndWritesNothing() = runBlocking {
        start()
        commands.create(command(50))
        raw("UPDATE expense SET sequence = ${Long.MAX_VALUE - 1}")

        val last = commands.create(command(51, pesos = 2_000))
        val beyond = commands.create(command(52, pesos = 3_000))

        assertTrue("$last", last is Committed)
        assertEquals(integrity("expense", "sequence"), beyond)
        assertEquals(
            listOf(Long.MAX_VALUE - 1 to 10_000L, Long.MAX_VALUE to 2_000L),
            expenses().map { it.sequence to it.amountMinor }
        )
        // The refused one left no receipt and did not move the revision: both are 2.
        assertEquals(2, database.count("operation_receipt"))
        assertEquals(2, revision())
        assertNull(commands.find(command(52).operationId).valueOrFail())
    }

    @Test
    fun manyIdenticalSubmissionsAtOnceSaveOneExpense() = runBlocking {
        start()
        val go = CompletableDeferred<Unit>()

        val outcomes = List(32) {
            async(Dispatchers.Default) {
                go.await()
                commands.create(command(50))
            }
        }.also { go.complete(Unit) }.awaitAll()

        assertEquals(
            listOf(Committed(committed(50, 100, 1), replayed = false)) +
                List(31) { Committed(committed(50, 100, 1), replayed = true) },
            outcomes.sortedBy { (it as Committed).replayed }
        )
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(1, revision())
    }

    @Test
    fun differentOperationsAtOnceEachSaveOnceInTheirOwnRevision() = runBlocking {
        start()
        val go = CompletableDeferred<Unit>()

        val outcomes = List(16) { index ->
            async(Dispatchers.Default) {
                go.await()
                commands.create(command(50 + index))
            }
        }.also { go.complete(Unit) }.awaitAll()

        val saved = outcomes.map { (it as Committed).expense }
        assertEquals((1L..16L).toList(), saved.map { it.receipt.revision }.sorted())
        assertEquals(16, saved.map { it.expenseId }.toSet().size)
        assertEquals((1L..16L).toList(), expenses().map { it.sequence }.sorted())
        assertEquals(16, database.count("operation_receipt"))
        assertEquals(16, revision())
    }

    @Test
    fun aRealConstraintFailureLeavesNeitherExpenseNorReceiptNorRevision() = runBlocking {
        start()
        commands.create(command(50))
        // The identifier source repeats itself, so the second insert breaks the primary key.
        nextExpense = 100

        val outcome = commands.create(command(51, pesos = 77_000))

        assertEquals(
            Rejected(
                ExpenseRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, database.count("operation_receipt"))
        assertEquals(1, revision())
        assertEquals(Outcome.Success(null), commands.find(command(51).operationId))
    }

    @Test
    fun aCallerCancelledBeforeSubmittingWritesNothing() = runBlocking {
        start()
        var cancellation: CancellationException? = null

        launch(Dispatchers.Default) {
            coroutineContext.cancel()
            try {
                commands.create(command(50))
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }.join()

        assertTrue(cancellation != null)
        assertNothingWritten()
        assertEquals(Outcome.Success(null), commands.find(command(50).operationId))
    }

    @Test
    fun aCallerCancelledWhileSavingGetsTheCancellationAndCanRecoverTheSavedExpense() = runBlocking {
        start()
        lateinit var caller: Job
        var returned: ExpenseCommandOutcome? = null
        var cancellation: CancellationException? = null
        whenAnIdIsTaken = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                returned = commands.create(command(50))
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        whenAnIdIsTaken = {}

        // The caller is told it was cancelled, never "saved" or "not saved".
        assertNull(returned)
        assertTrue(cancellation != null)
        // The truth is in storage, and the same operation recovers it without a second row.
        assertEquals(
            Outcome.Success(committed(50, 100, 1)),
            commands.find(command(50).operationId)
        )
        assertEquals(
            Committed(committed(50, 100, 1), replayed = true),
            commands.create(command(50))
        )
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, revision())
    }

    @Test
    fun aFailureAtTheCommitItselfIsReportedAsUnknownAndRecoveredByLookup() = runBlocking {
        start()
        // Deferred checks make the database refuse the transaction only when it commits:
        // by then every statement of the command has run without error.
        whenAnIdIsTaken = {
            database.openHelper.writableDatabase.apply {
                execSQL("PRAGMA defer_foreign_keys = ON")
                execSQL("DELETE FROM category")
            }
        }

        val outcome = commands.create(command(50))
        whenAnIdIsTaken = {}

        assertEquals(ExpenseCommandOutcome.OutcomeUnknown(command(50).operationId), outcome)
        assertEquals(Outcome.Success(null), commands.find(command(50).operationId))
        assertNothingWritten()
        assertEquals(1, database.count("category"))
    }

    @Test
    fun theReceiptAndReplaySurviveClosingAndReopeningTheFile() = runBlocking {
        start()
        commands.create(command(50))
        database.close()

        open()

        assertEquals(Outcome.Success(committed(50, 100, 1)), commands.find(command(50).operationId))
        assertEquals(
            Committed(committed(50, 100, 1), replayed = true),
            commands.create(command(50))
        )
        assertEquals(Rejected(ExpenseRejection.OperationConflict), commands.create(command(50, 1)))
        assertEquals(listOf(storedPan()), expenses())
        assertEquals(1, revision())
    }

    @Test
    fun aDamagedReceiptIsAFailureForBothSubmissionAndLookup() = runBlocking {
        start()
        val cases = listOf<Pair<Pair<String, Any?>, String>>(
            ("kind" to "expense.teleport") to "kind",
            ("kind" to "") to "kind",
            ("subject_id" to null) to "subject_id",
            ("subject_id" to "100") to "subject_id",
            ("generation" to 0L) to "generation",
            ("revision" to 0L) to "revision",
            ("input_digest" to "") to "input_digest",
            ("input_digest" to "Pan 10000") to "input_digest"
        )
        val row = listOf<Pair<String, Any?>>(
            "operation_id" to uuid(50),
            "kind" to "expense.create",
            "input_digest" to "465545f44c6201d2afd33700f8f5b81f6465f70a727363cf1a25055f160148fd",
            "generation" to 1L,
            "revision" to 1L,
            "subject_id" to uuid(100),
            "committed_at" to auditMillis.toEpochMilli()
        )

        for ((change, field) in cases) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM operation_receipt")
            database.insertRaw(
                "operation_receipt",
                *row.map { if (it.first == change.first) change else it }.toTypedArray()
            )
            val damaged = StorageFailure.Integrity("operation_receipt", uuid(50), field)

            assertEquals(
                "$change",
                Rejected(ExpenseRejection.StorageFailed(damaged)),
                commands.create(command(50))
            )
            assertEquals(
                "$change",
                Outcome.Failure(damaged),
                commands.find(command(50).operationId)
            )
        }
        assertEquals(emptyList<ExpenseEntity>(), expenses())
        assertEquals(0, revision())
    }

    @Test
    fun aDamagedProfileOrCategoryRejectsTheCommandWithoutWriting() = runBlocking {
        start()
        database.openHelper.writableDatabase.execSQL("UPDATE category SET icon = 'Home'")

        val badCategory = commands.create(command(50))
        database.openHelper.writableDatabase.apply {
            execSQL("UPDATE category SET icon = 'home'")
            execSQL("UPDATE profile SET financial_zone = 'Mars/Olympus'")
        }
        val badProfile = commands.create(command(51))

        assertEquals(
            Rejected(
                ExpenseRejection.StorageFailed(
                    StorageFailure.Integrity("category", uuid(1), "icon")
                )
            ),
            badCategory
        )
        assertEquals(
            Rejected(
                ExpenseRejection.StorageFailed(
                    StorageFailure.Integrity("profile", null, "financial_zone")
                )
            ),
            badProfile
        )
        assertNothingWritten()
    }
}
