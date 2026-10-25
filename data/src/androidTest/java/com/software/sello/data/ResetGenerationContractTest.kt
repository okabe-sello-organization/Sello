package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.ResetPreview
import com.software.sello.domain.port.RestoreExpense
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Erasing the whole history of a real database file the real commands wrote: one
 * category Mercado (100) with a limit, two expenses (one deleted and kept for undo),
 * one income, and private files an erase must not leave behind. The device is in Lima;
 * the history was made in Bogotá. Expected rows are written out by hand.
 */
@RunWith(AndroidJUnit4::class)
class ResetGenerationContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }
    private val lima = ZoneId.of("America/Lima")
    private val setAside: List<File> by lazy {
        val kept = files.context.getDatabasePath(
            StorageNames(files.name, "unused").setAsideDatabase
        )
        listOf(kept, File(kept.path + "-wal"))
    }

    /** Preference resets that are counted and can be told to fail. */
    private var preferencesFail = false
    private var preferenceResets = 0
    private val preferences = PreferenceReset {
        preferenceResets++
        if (preferencesFail) {
            PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        } else {
            PreferenceWrite.Saved
        }
    }

    private var whenStamped: () -> Unit = {}

    @After
    fun removePrivateFiles() {
        setAside.forEach { it.delete() }
    }

    private fun resets() = RoomResets(
        ledger.database,
        files.resetFolder,
        files.restoreFolder,
        setAside,
        preferences,
        { lima },
        {
            whenStamped()
            ledger.audit
        },
        dispatchers
    )

    /** The history, through the real commands, and files that hold older data. */
    private suspend fun currentHistory() {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(50_000)))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 7_000))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-06", 8_000))
        ledger.incomes.create(
            CreateIncome(
                ledger.operation(),
                1,
                TransactionAmount.of(Money.cop(200_000)).valueOrFail(),
                IncomeSource.Salary,
                LocalDate.parse("2026-10-01"),
                Note.of("Quincena").valueOrFail()
            )
        )
        // A recurring payment in Mercado, paused, as SELLO-098 stores one.
        ledger.database.insertRaw(
            "schedule",
            "id" to uuid(200),
            "kind" to "payment",
            "category_id" to uuid(100),
            "source_key" to null,
            "source_name" to null,
            "amount_minor" to 7_000L,
            "currency" to "COP",
            "note" to "Mercado semanal",
            "rule" to "every_weeks",
            "rule_first" to 1,
            "rule_second" to null,
            "start_date" to "2026-10-05",
            "end_date" to null,
            "sequence" to 0L,
            "handled_date" to null,
            "handled_slot" to null,
            "version" to 1L,
            "created_at" to 0L,
            "updated_at" to 0L
        )
        ledger.database.insertRaw(
            "schedule_pause",
            "schedule_id" to uuid(200),
            "from_date" to "2026-10-07",
            "until_date" to null
        )
        // An investment with its movements and a USD rate, as SELLO-107 stores them.
        ledger.database.insertInvestmentRows()
        files.restoreFolder.mkdirs()
        File(files.restoreFolder, "restored.json").writeText("{\"Mercado\": 7000}")
        setAside.forEach { it.writeBytes(ByteArray(64) { 7 }) }
    }

    private fun counts() = listOf(
        "category",
        "default_limit",
        "month_limit",
        "expense",
        "income",
        "deleted_expense",
        "deleted_income",
        "schedule",
        "schedule_pause",
        "investment",
        "investment_movement",
        "deleted_investment_movement",
        "usd_rate"
    ).associateWith { ledger.long("SELECT COUNT(*) FROM $it") }

    private fun everyRow(): List<List<String>> = listOf(
        ledger.rows("category", "id", "name"),
        ledger.rows("expense", "id", "amount_minor"),
        ledger.rows("income", "id", "amount_minor"),
        ledger.rows("profile", "financial_zone", "generation", "revision")
    )

    private suspend fun preview(): ResetPreview = resets().preview().valueOrFail()

    private fun markerExists() = File(files.resetFolder, "pending.properties").exists()

    @Test
    fun theCountsAreWhatWouldBeErasedAndReadingThemChangesNothing() = runBlocking {
        currentHistory()
        val before = everyRow()

        val preview = preview()

        assertEquals(
            BackupSummary(categories = 1, expenses = 2, incomes = 1, 0, 1),
            preview.current
        )
        assertEquals(1L, preview.generation)
        assertEquals(before, everyRow())
        assertFalse(markerExists())
    }

    @Test
    fun anEraseRemovesEveryRowAndKeepsOnlyItsOwnReceiptInTheDevicesZone() = runBlocking {
        currentHistory()
        val revision = ledger.revision()
        val operation = ledger.operation()

        val outcome = resets().reset(preview(), operation)

        assertEquals(ResetOutcome.Erased(preferencesPending = false), outcome)
        assertTrue(counts().values.all { it == 0L })
        assertEquals(
            listOf("America/Lima|2|${revision + 1}"),
            ledger.rows("profile", "financial_zone", "generation", "revision")
        )
        // One receipt: this erase, with no subject, and nothing else that was recorded.
        assertEquals(
            listOf("${operation.value}|history.reset|2|${revision + 1}|null"),
            ledger.rows(
                "operation_receipt",
                "operation_id",
                "kind",
                "generation",
                "revision",
                "subject_id"
            )
        )
        assertEquals(1, preferenceResets)
        assertFalse(files.restoreFolder.exists())
        assertTrue(setAside.none { it.exists() })
        assertFalse(markerExists())
    }

    @Test
    fun nothingLeftInTheFilesHoldsAnOldNameOrNote() = runBlocking {
        currentHistory()
        ledger.categories.submit(ledger.createCategory("Farmacia Pasteur", finite(20_000)))

        resets().reset(preview(), ledger.operation())

        // SQLite keeps deleted rows in free pages; the erase compacts the file, so the
        // bytes of the database and its companions no longer hold them either.
        val folder = files.file.parentFile!!
        val bytes = folder.listFiles { file -> file.name.startsWith(files.name) }!!
            .joinToString("") { it.readBytes().toString(Charsets.ISO_8859_1) }
        for (erased in listOf("Mercado", "Farmacia Pasteur", "Quincena")) {
            assertFalse("$erased survives in the files", erased in bytes)
        }
        assertEquals(64, ledger.rows("operation_receipt", "input_digest").single().length)
        val private = listOf(files.resetFolder, files.restoreFolder)
            .flatMap { it.walk().filter(File::isFile).toList() }
        assertEquals(emptyList<File>(), private)
    }

    @Test
    fun aChangeAfterTheCountsRefusesTheEraseAndForgetsIt() = runBlocking {
        currentHistory()
        val preview = preview()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-07", 9_000))
        val before = everyRow()

        val outcome = resets().reset(preview, ledger.operation())

        assertEquals(ResetOutcome.Stale, outcome)
        assertEquals(before, everyRow())
        assertEquals(0, preferenceResets)
        assertFalse(markerExists())
        assertTrue(files.restoreFolder.exists())
        assertEquals(BackupSummary(1, 3, 1, 0, 1), preview().current)
    }

    @Test
    fun aFailureInsideTheEraseRollsEverythingBack() = runBlocking {
        currentHistory()
        // The revision cannot advance past its largest value, so the erase fails after
        // its deletes have run, inside the transaction.
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE profile SET revision = ${Long.MAX_VALUE}"
        )
        val before = everyRow()
        val receipts = ledger.long("SELECT COUNT(*) FROM operation_receipt")

        val outcome = resets().reset(preview(), ledger.operation())

        assertEquals(ResetOutcome.Failed, outcome)
        assertEquals(before, everyRow())
        assertEquals(receipts, ledger.long("SELECT COUNT(*) FROM operation_receipt"))
        assertEquals(0, preferenceResets)
        assertFalse(markerExists())
        assertTrue(setAside.all { it.exists() })
    }

    @Test
    fun oldDraftsAndUndoCannotCrossTheErase() = runBlocking {
        currentHistory()
        val deletion = ledger.operation()
        val deleted = ledger.long("SELECT COUNT(*) FROM expense")
        ledger.expenses.delete(
            DeleteExpense(deletion, 1, ExpenseId.of(firstExpense()).valueOrFail(), 1)
        )
        assertEquals(deleted - 1, ledger.long("SELECT COUNT(*) FROM expense"))
        resets().reset(preview(), ledger.operation())

        val draft = ledger.expenses.create(ledger.createExpense(100, "2026-10-08", 1_000))
        val undo = ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))

        assertEquals(ExpenseCommandOutcome.Rejected(ExpenseRejection.StaleGeneration(2)), draft)
        assertEquals(ExpenseCommandOutcome.Rejected(ExpenseRejection.StaleGeneration(2)), undo)
        assertTrue(counts().values.all { it == 0L })
    }

    @Test
    fun aCallerCancelledAroundTheCommitStillFindsTheEraseDoneAndNeverErasesTwice() = runBlocking {
        currentHistory()
        val preview = preview()
        val operation = ledger.operation()
        val erasing = async(Dispatchers.Default) { resets().reset(preview, operation) }
        // The receipt is stamped inside the transaction: cancel the caller right there.
        whenStamped = { erasing.cancel() }

        val cancelled = runCatching { erasing.await() }.exceptionOrNull()
        whenStamped = {}

        assertTrue("$cancelled", cancelled is CancellationException)
        assertEquals(2L, ledger.long("SELECT generation FROM profile"))
        // A new expense in the new history, then the same erase asked again.
        ledger.categories.submit(ledger.createCategory("Nueva", finite(10_000), generation = 2))
        val again = resets().reset(preview, operation)

        assertTrue(again is ResetOutcome.Erased)
        assertEquals(listOf("Nueva"), ledger.rows("category", "name"))
        assertEquals(2L, ledger.long("SELECT generation FROM profile"))
    }

    @Test
    fun preferencesThatFailStayPendingAndAreFinishedLaterWithoutASecondErase() = runBlocking {
        currentHistory()
        preferencesFail = true

        val outcome = resets().reset(preview(), ledger.operation())

        assertEquals(ResetOutcome.Erased(preferencesPending = true), outcome)
        assertTrue(markerExists())
        // After a restart, a new expense, and a working preference store.
        ledger.reopen()
        ledger.categories.submit(ledger.createCategory("Nueva", finite(10_000), generation = 2))
        preferencesFail = false
        assertTrue(resets().pending())

        assertEquals(ResetOutcome.Erased(preferencesPending = false), resets().finishPending())
        assertEquals(listOf("Nueva"), ledger.rows("category", "name"))
        assertEquals(2, preferenceResets)
        assertFalse(markerExists())
        assertFalse(resets().pending())
    }

    @Test
    fun anEraseThatNeverCommittedIsForgottenAndChangesNothing() = runBlocking {
        currentHistory()
        val before = everyRow()
        // A process died after writing the pending file and before the transaction.
        files.resetFolder.mkdirs()
        File(files.resetFolder, "pending.properties")
            .writeText("operation=${ledger.operation().value}\n")

        assertFalse(resets().pending())
        assertEquals(null, resets().finishPending())
        assertEquals(before, everyRow())
        assertFalse(markerExists())
        assertEquals(0, preferenceResets)
        assertTrue(setAside.all { it.exists() })
    }

    @Test
    fun theNewHistoryCanBeUsedFromEmpty() = runBlocking {
        currentHistory()
        resets().reset(preview(), ledger.operation())

        val comida = ledger.nextRecord
        ledger.categories.submit(ledger.createCategory("Comida", finite(80_000), generation = 2))
        val created = ledger.expenses.create(
            ledger.createExpense(comida, "2026-10-09", 12_000).copy(generation = 2)
        )

        assertTrue("$created", created is ExpenseCommandOutcome.Committed)
        assertEquals(listOf("12000"), ledger.rows("expense", "amount_minor"))
    }

    private fun firstExpense(): String = ledger.rows("expense", "id").first()
}
