package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.data.backup.RoomRestores
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedRestore
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Restoring a backup over a real database file whose history the real commands wrote.
 * The current history is two expenses in Mercado (100); the backup is G01 in Lima:
 * two categories, three expenses and one income, with dark violet alphabetical
 * preferences. Expected rows are written out by hand.
 */
@RunWith(AndroidJUnit4::class)
class RestoreCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }

    /** Preferences that record what was applied and can be told to fail. */
    private class Preferences :
        AppearancePreferences,
        CategoryOrderingPreferences {
        var failAppearance = false
        var failOrdering = false
        val written = mutableListOf<Any>()

        private fun write(value: Any, fail: Boolean): PreferenceWrite = if (fail) {
            PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        } else {
            written += value
            PreferenceWrite.Saved
        }

        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = emptyFlow()

        override suspend fun setTheme(theme: ThemePreference) = write(theme, failAppearance)

        override suspend fun setInk(ink: InkPreference) = write(ink, failAppearance)

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            emptyFlow()

        override suspend fun setCategoryOrder(order: CategoryOrder) = write(order, failOrdering)

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            write(direction, failOrdering)
    }

    private val preferences = Preferences()
    private val notice = NoticeSeen()
    private var now = Instant.parse("2026-10-22T19:30:00Z")
    private var whenStamped: () -> Unit = {}
    private var nextId = 5_000

    private fun restores() = RoomRestores(
        ledger.database,
        files.restoreFolder,
        preferences,
        preferences,
        notice,
        { uuid(nextId++) },
        {
            whenStamped()
            now
        },
        dispatchers
    )

    private val lima = ZoneId.of("America/Lima")
    private val wanted = PortablePreferences(
        AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
        CategoryOrderingPreference(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending)
    )

    private fun g01(preferences: PortablePreferences? = wanted) = BackupCodec().encode(
        BackupContents(
            exportedAt = Instant.parse("2026-10-22T14:30:00Z"),
            financial = FinancialSnapshot(
                zone = lima,
                revision = 9,
                categories = listOf(category(1, "Comida"), category(2, "Café", archived = true)),
                defaultLimits = listOf(
                    DefaultLimit(
                        ledger.categoryId(1),
                        YearMonth.of(2026, 10),
                        AutomaticBudget.Limit(finite(100_000))
                    )
                ),
                monthLimits = emptyList(),
                expenses = listOf(
                    expense(101, 1, "2026-10-01", 1, 10_000),
                    expense(102, 1, "2026-10-02", 2, 15_000, note = "Plaza"),
                    expense(103, 1, "2026-10-03", 3, 5_000)
                ),
                incomes = listOf(income(201, "2026-10-01", 1, 200_000, IncomeSource.Salary)),
                goals = emptyList(),
                featuredGoal = null,
                schedules = emptyList()
            ),
            preferences = preferences
        )
    ).valueOrFail()

    /** Two expenses in Mercado, through the real commands. */
    private suspend fun currentHistory() {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(50_000)))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 7_000))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-06", 8_000))
        // A paused recurring payment in Mercado, as SELLO-098 stores one. The backup
        // holds no schedules, so a restore leaves none.
        ledger.database.insertRaw(
            "schedule",
            "id" to uuid(300),
            "kind" to "payment",
            "category_id" to uuid(100),
            "source_key" to null,
            "source_name" to null,
            "amount_minor" to 7_000L,
            "currency" to "COP",
            "note" to null,
            "rule" to "monthly",
            "rule_first" to 5,
            "rule_second" to null,
            "start_date" to "2026-10-05",
            "end_date" to null,
            "sequence" to 0L,
            "handled_date" to "2026-10-05",
            "handled_slot" to 1,
            "version" to 1L,
            "created_at" to 0L,
            "updated_at" to 0L
        )
        ledger.database.insertRaw(
            "schedule_pause",
            "schedule_id" to uuid(300),
            "from_date" to "2026-10-07",
            "until_date" to null
        )
    }

    private fun everyRow(): List<List<String>> = listOf(
        ledger.rows("category", "id", "name", "archived"),
        ledger.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor"),
        ledger.rows("month_limit", "category_id", "month"),
        ledger.rows("expense", "id", "category_id", "effective_date", "sequence", "amount_minor"),
        ledger.rows("income", "id", "effective_date", "sequence", "amount_minor", "source_key"),
        ledger.rows("schedule", "id", "category_id", "handled_date"),
        ledger.rows("schedule_pause", "schedule_id", "from_date"),
        ledger.rows("profile", "financial_zone", "generation", "revision")
    )

    private suspend fun stage(bytes: ByteArray = g01()): StagedRestore =
        restores().stage(bytes).valueOrFail()

    private fun stagingFiles(): List<String> = files.restoreFolder.list()?.sorted().orEmpty()

    @Test
    fun aConfirmedRestoreReplacesTheWholeHistoryNotMergesIt() = runBlocking {
        currentHistory()
        val revision = ledger.revision()

        val staged = stage()
        val outcome = restores().replace(staged)

        assertEquals(BackupSummary(categories = 1, expenses = 2, incomes = 0, 0, 1), staged.current)
        assertEquals(
            BackupSummary(categories = 2, expenses = 3, incomes = 1, 0, 0),
            staged.replacement
        )
        assertEquals(
            RestoreOutcome.Restored(
                BackupSummary(2, 3, 1, 0, 0),
                PendingPreferences(null, null)
            ),
            outcome
        )
        assertEquals(
            listOf(
                listOf("${uuid(1)}|Comida|0", "${uuid(2)}|Café|1"),
                listOf("${uuid(1)}|2026-10|finite|100000"),
                emptyList(),
                listOf(
                    "${uuid(101)}|${uuid(1)}|2026-10-01|1|10000",
                    "${uuid(102)}|${uuid(1)}|2026-10-02|2|15000",
                    "${uuid(103)}|${uuid(1)}|2026-10-03|3|5000"
                ),
                listOf("${uuid(201)}|2026-10-01|1|200000|salary"),
                emptyList(),
                emptyList(),
                // The backup's zone, a new generation and the next revision.
                listOf("America/Lima|2|${revision + 1}")
            ),
            everyRow()
        )
        assertEquals(
            listOf("history.restore|2|${revision + 1}"),
            ledger.rows("operation_receipt", "kind", "generation", "revision")
                .filter { it.startsWith("history.") }
        )
        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                InkPreference.Violeta,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /**
     * What the backup brings counts as reviewed: the notice is marked seen at the
     * restore's commit, once. A later retry of the preferences never moves it back past
     * a Listo pressed meanwhile.
     */
    @Test
    fun aRestoreMarksTheNoticeSeenAtItsCommitOnlyOnce() = runBlocking {
        currentHistory()
        preferences.failAppearance = true

        restores().replace(stage())
        val listo = Instant.parse("2026-10-22T21:00:00Z")
        notice.seen = listo
        preferences.failAppearance = false
        now = Instant.parse("2026-10-23T08:00:00Z")
        val retried = restores().applyPending()

        assertEquals(listOf(Instant.parse("2026-10-22T19:30:00Z")), notice.marks)
        assertEquals(listo, notice.seen)
        assertTrue(retried!!.preferences.done)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** A notice that cannot be marked never holds the restore or its preferences up. */
    @Test
    fun aNoticeThatCannotBeMarkedLeavesTheRestoreDone() = runBlocking {
        currentHistory()
        notice.fail = true

        val outcome = restores().replace(stage())

        assertEquals(
            RestoreOutcome.Restored(BackupSummary(2, 3, 1, 0, 0), PendingPreferences(null, null)),
            outcome
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aFileThatCannotBeRestoredChangesNothingAndIsNotKept() = runBlocking {
        currentHistory()
        val before = everyRow()
        val bytes = g01()

        val truncated = restores().stage(bytes.copyOf(bytes.size / 2))
        val newer = restores().stage(
            String(bytes).replaceFirst("\"version\": 3", "\"version\": 4").toByteArray()
        )

        assertEquals(Outcome.Failure(StageFailure.Rejected(RestoreRejection.Damaged)), truncated)
        assertEquals(Outcome.Failure(StageFailure.Rejected(RestoreRejection.NewerVersion)), newer)
        assertEquals(before, everyRow())
        assertEquals(emptyList<String>(), stagingFiles())
        assertTrue(preferences.written.isEmpty())
    }

    @Test
    fun aCanceledPreviewChangesNothingAndForgetsTheFile() = runBlocking {
        currentHistory()
        val before = everyRow()
        stage()

        restores().discard()

        assertEquals(before, everyRow())
        assertEquals(emptyList<String>(), stagingFiles())
        assertNull(restores().staged())
    }

    @Test
    fun aStagedFileSurvivesARestartWithItsOwnOperation() = runBlocking {
        currentHistory()
        val staged = stage()

        ledger.reopen()
        val again = restores().staged()

        assertEquals(staged, again)
    }

    @Test
    fun aChangeAfterThePreviewRefusesTheReplacementUntilPreviewedAgain() = runBlocking {
        currentHistory()
        val staged = stage()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-07", 9_000))
        val before = everyRow()

        assertEquals(RestoreOutcome.Stale, restores().replace(staged))
        assertEquals(before, everyRow())

        val fresh = restores().staged()!!
        assertEquals(BackupSummary(1, 3, 0, 0, 1), fresh.current)
        assertTrue(restores().replace(fresh) is RestoreOutcome.Restored)
    }

    @Test
    fun aFailureInsideTheReplacementRollsEverythingBack() = runBlocking {
        currentHistory()
        val staged = stage()
        // The revision cannot advance past its largest value, so the replacement fails
        // after its deletes and inserts have run, inside the transaction.
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE profile SET revision = ${Long.MAX_VALUE}"
        )
        val before = everyRow()

        val outcome = restores().replace(staged.copy(revision = Long.MAX_VALUE))

        assertEquals(RestoreOutcome.Failed, outcome)
        assertEquals(before, everyRow())
        assertTrue(preferences.written.isEmpty())
    }

    @Test
    fun oldDraftsAndUndoCannotCrossTheReplacement() = runBlocking {
        currentHistory()
        val deletion = ledger.operation()
        ledger.expenses.delete(DeleteExpense(deletion, 1, ExpenseId.of(uuid(101)).valueOrFail(), 1))
        val staged = stage()
        restores().replace(staged)

        // A draft and an undo prepared against generation 1, even naming an expense
        // whose identifier the backup reuses.
        val draft = ledger.expenses.create(ledger.createExpense(1, "2026-10-08", 1_000))
        val undo = ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))

        assertEquals(
            ExpenseCommandOutcome.Rejected(ExpenseRejection.StaleGeneration(2)),
            draft
        )
        assertEquals(ExpenseCommandOutcome.Rejected(ExpenseRejection.StaleGeneration(2)), undo)
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM deleted_expense"))
        assertEquals(3L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    @Test
    fun preferencesThatFailAreNamedAndRetriedWithoutASecondReplacement() = runBlocking {
        currentHistory()
        preferences.failOrdering = true
        val staged = stage()

        val outcome = restores().replace(staged)
        val history = everyRow()

        assertEquals(
            RestoreOutcome.Restored(
                BackupSummary(2, 3, 1, 0, 0),
                PendingPreferences(appearance = null, ordering = wanted.ordering)
            ),
            outcome
        )

        // After a restart the history is not replaced again; only the order is pending.
        ledger.reopen()
        assertNull(restores().staged())
        assertEquals(outcome, restores().pending())
        assertEquals(outcome, restores().replace(staged))
        assertEquals(history, everyRow())

        preferences.failOrdering = false
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(history, everyRow())
        assertNull(restores().pending())
        assertEquals(emptyList<String>(), stagingFiles())
        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                InkPreference.Violeta,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
    }

    @Test
    fun aBackupWithoutPreferencesLeavesTheDevicesAlone() = runBlocking {
        currentHistory()

        val outcome = restores().replace(stage(g01(preferences = null)))

        assertEquals(
            PendingPreferences(null, null),
            (outcome as RestoreOutcome.Restored).preferences
        )
        assertTrue(preferences.written.isEmpty())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aCallerCancelledAroundTheCommitStillFindsTheReplacementDone() = runBlocking {
        currentHistory()
        val staged = stage()
        val replacing = async(Dispatchers.Default) { restores().replace(staged) }
        // The receipt is stamped inside the transaction: cancel the caller right there.
        whenStamped = { replacing.cancel() }

        val cancelled = runCatching { replacing.await() }.exceptionOrNull()
        whenStamped = {}

        assertTrue("$cancelled", cancelled is CancellationException)
        assertEquals("America/Lima|2|${ledger.revision()}", everyRow().last().single())
        // The staged file now belongs to a committed replacement: it is never offered
        // again, and asking with the same staging replays instead of replacing twice.
        assertNull(restores().staged())
        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
        assertEquals(1, ledger.rows("operation_receipt", "kind").count { it == "history.restore" })
    }

    @Test
    fun anUnconfirmedFileExpiresAfterADay() = runBlocking {
        currentHistory()
        stage()

        now = now.plusSeconds(24 * 60 * 60 + 1)

        assertNull(restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aPendingReplacementBlocksStagingAnotherFile() = runBlocking {
        currentHistory()
        preferences.failAppearance = true
        restores().replace(stage())

        assertEquals(Outcome.Failure(StageFailure.Storage), restores().stage(g01()))
        assertFalse(restores().pending()!!.preferences.done)
    }

    @Test
    fun askingAgainAfterACrashBeforeTheFileMovedReplaysTheSameReplacement() = runBlocking {
        currentHistory()
        // A preference that fails keeps the files, as a crash right after commit would.
        preferences.failAppearance = true
        val staged = stage()
        restores().replace(staged)
        val history = everyRow()
        // As if the process died after the commit, before the staged file was moved:
        // the receipt exists and the file is still where staging left it.
        val folder = files.restoreFolder
        File(folder, "restored.json").renameTo(File(folder, "staged.json"))

        val again = restores().replace(staged)

        assertTrue("$again", again is RestoreOutcome.Restored)
        assertEquals(history, everyRow())
        assertEquals(1, ledger.rows("operation_receipt", "kind").count { it == "history.restore" })
    }
}
