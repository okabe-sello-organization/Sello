package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.data.backup.RoomRestores
import com.software.sello.data.backup.checkFile
import com.software.sello.data.preferences.DataStoreUserPreferences
import com.software.sello.data.repository.RoomCategoryExpenseSearch
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedLookup
import com.software.sello.domain.port.StagedRestore
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
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
    private val dispatchers = realDispatchers

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

        /** Tuya's hue each restore wrote beside its ink, which [written] holds. */
        val tuyaHues = mutableListOf<InkHue?>()

        override suspend fun restoreInk(ink: InkPreference, tuyaHue: InkHue?) =
            write(ink, failAppearance).also {
                if (it is PreferenceWrite.Saved) tuyaHues += tuyaHue
            }

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            emptyFlow()

        override suspend fun setCategoryOrder(order: CategoryOrder) = write(order, failOrdering)

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            write(direction, failOrdering)
    }

    private val investments = HandInvestmentPreferences()
    private val smallExpenses = HandSmallExpensePreferences()
    private val guidance = HandGuidancePreferences()

    private val preferences = Preferences()
    private val notice = NoticeSeen()
    private var now = Instant.parse("2026-10-22T19:30:00Z")
    private var whenStamped: () -> Unit = {}
    private var nextId = 5_000

    private fun restores(
        dispatchers: DispatcherProvider = this.dispatchers,
        guidance: GuidancePreferences = this.guidance
    ) = RoomRestores(
        ledger.database,
        files.restoreFolder,
        preferences,
        preferences,
        investments,
        smallExpenses,
        guidance,
        notice,
        { uuid(nextId++) },
        {
            whenStamped()
            now
        },
        dispatchers,
        Mutex(),
        { true }
    )

    private val lima = ZoneId.of("America/Lima")
    private val wanted = PortablePreferences(
        AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
        CategoryOrderingPreference(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending)
    )

    /** [used]: false for a file with categories and limits but no expense or income. */
    private fun g01(
        preferences: PortablePreferences? = wanted,
        plaza: String = "Plaza",
        used: Boolean = true
    ) = BackupCodec().encode(
        BackupContents(
            exportedAt = Instant.parse("2026-10-22T14:30:00Z"),
            financialMonth = YearMonth.of(2026, 10),
            financial = FinancialSnapshot(
                zone = lima,
                revision = 9,
                categories = listOf(
                    category(1, "Comida"),
                    category(2, "Café", archived = true)
                ),
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
                    expense(102, 1, "2026-10-02", 2, 15_000, note = plaza),
                    expense(103, 1, "2026-10-03", 3, 5_000)
                ).takeIf { used }.orEmpty(),
                incomes = listOf(income(201, "2026-10-01", 1, 200_000, IncomeSource.Salary))
                    .takeIf { used }.orEmpty(),
                goals = emptyList(),
                starredGoals = emptySet(),
                schedules = emptyList(),
                investments = emptyList(),
                usdRates = emptyList(),
                carryChains = emptyList()
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
        // An investment with its movements and a USD rate, as SELLO-107 stores them. The
        // backup holds none, so a restore leaves none.
        ledger.database.insertInvestmentRows()
    }

    private fun everyRow(): List<List<String>> = listOf(
        ledger.rows("category", "id", "name", "archived"),
        ledger.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor"),
        ledger.rows("month_limit", "category_id", "month"),
        ledger.rows("expense", "id", "category_id", "effective_date", "sequence", "amount_minor"),
        ledger.rows("income", "id", "effective_date", "sequence", "amount_minor", "source_key"),
        ledger.rows("schedule", "id", "category_id", "handled_date"),
        ledger.rows("schedule_pause", "schedule_id", "from_date"),
        ledger.rows("investment", "id", "name"),
        ledger.rows("investment_movement", "id", "investment_id", "amount_minor"),
        ledger.rows("usd_rate", "id", "rate_hundredths"),
        ledger.rows("profile", "financial_zone", "generation", "revision")
    )

    private suspend fun stage(bytes: ByteArray = g01()): StagedRestore =
        restores().stage(bytes).valueOrFail()

    private fun stagingFiles(): List<String> = files.restoreFolder.list()?.sorted().orEmpty()

    /**
     * A backup holds no search key (SELLO-165): the format is the one every earlier build
     * wrote, and the restore builds the key from each note, so "\u00c1REA \u00d1u\u00f1oa"
     * is found as "area nunoa".
     */
    @Test
    fun aRestoreBuildsTheSearchKeyOfEveryNote() = runBlocking {
        currentHistory()

        restores().replace(stage(g01(plaza = "\u00c1REA \u00d1u\u00f1oa")))

        assertEquals(
            listOf("${uuid(101)}|", "${uuid(102)}|area nunoa", "${uuid(103)}|"),
            ledger.rows("expense", "id", "search_key")
        )
        val found = RoomCategoryExpenseSearch(ledger.database).read(
            ledger.categoryId(1),
            YearMonth.of(2026, 10),
            ExpenseOrder.Date,
            "area nunoa",
            0,
            20
        ).valueOrFail()
        assertEquals(listOf(ExpenseId.of(uuid(102)).valueOrFail()), found.expenses.map { it.id })
    }

    @Test
    fun aConfirmedRestoreReplacesTheWholeHistoryNotMergesIt() = runBlocking {
        currentHistory()
        val revision = ledger.revision()

        val staged = stage()
        val outcome = restores().replace(staged)

        assertEquals(
            BackupSummary(categories = 1, expenses = 2, incomes = 0, 0, 1, investments = 1),
            staged.current
        )
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
                emptyList(),
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

    /** [ink] and Tuya's kept [tuyaHue] restored from a file that holds them (SELLO-202). */
    private suspend fun restoreAppearance(ink: InkPreference, tuyaHue: InkHue?) {
        currentHistory()
        val brought = wanted.copy(
            appearance = AppearancePreference(ThemePreference.Dark, ink, tuyaHue)
        )

        val outcome = restores().replace(stage(g01(brought)))

        assertEquals(
            PendingPreferences(null, null),
            (outcome as RestoreOutcome.Restored).preferences
        )
        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                ink,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
        assertEquals(listOf(tuyaHue), preferences.tuyaHues)
    }

    @Test
    fun aTuyaBackupRestoresTuyaWithItsHue() = runBlocking {
        val red = InkHue.of(22)!!
        restoreAppearance(InkPreference.Tuya(red), red)
    }

    @Test
    fun aCobaltoBackupRestoresTuyasKeptHueBesideIt() = runBlocking {
        restoreAppearance(InkPreference.Cobalto, InkHue.of(147)!!)
    }

    @Test
    fun aBackupWithoutAHueForgetsTheOneOnThisPhone() = runBlocking {
        restoreAppearance(InkPreference.Violeta, null)
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
        val text = String(bytes)
        check(text.contains("\"version\": 11,"))
        val newer = restores().stage(
            text.replaceFirst("\"version\": 11,", "\"version\": 12,").toByteArray()
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
        assertEquals(StagedLookup.None, restores().staged())
    }

    @Test
    fun aStagedFileSurvivesARestartWithItsOwnOperation() = runBlocking {
        currentHistory()
        val staged = stage()

        ledger.reopen()
        val again = restores().staged()

        assertEquals(StagedLookup.Waiting(staged), again)
    }

    /**
     * As if the process died in the middle of the replacement: the transaction never
     * commits, its writes roll back, and the database is opened again.
     */
    private suspend fun diedWhileReplacing(staged: StagedRestore) {
        whenStamped = { throw IllegalStateException("The process died") }
        assertEquals(RestoreOutcome.Failed, restores().replace(staged))
        whenStamped = {}
        ledger.reopen()
    }

    private fun restoreReceipts() =
        ledger.rows("operation_receipt", "operation_id", "kind").filter {
            it.endsWith("|history.restore")
        }

    @Test
    fun aConfirmedFileThatDidNotCommitIsFoundAttemptedAndReplacedUnderItsOperation() = runBlocking {
        currentHistory()
        val before = everyRow()
        val staged = stage()

        diedWhileReplacing(staged)

        assertEquals(StagedLookup.Waiting(staged, attempted = true), restores().staged())
        assertEquals(before, everyRow())
        assertEquals(emptyList<String>(), restoreReceipts())

        // Confirming again replaces once, under the operation staged with the file.
        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
        assertEquals(
            listOf("${staged.operationId.value}|history.restore"),
            restoreReceipts()
        )
        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aFileOnlyPreviewedIsNotFoundAttemptedAfterARestart() = runBlocking {
        currentHistory()
        val staged = stage()

        ledger.reopen()

        // Staging writes the manifest as before the key existed: no `attempted` in it.
        assertFalse(File(files.restoreFolder, "staged.properties").readText().contains("attempted"))
        assertEquals(StagedLookup.Waiting(staged, attempted = false), restores().staged())
    }

    @Test
    fun anAttemptedFileExpiresOnlyOnceItsReceiptReads() = runBlocking {
        currentHistory()
        val staged = stage()
        diedWhileReplacing(staged)
        val kept = stagingFiles()
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt RENAME TO operation_receipt_away")
        now = now.plusSeconds(24 * 60 * 60 + 1)
        ledger.reopen()

        assertEquals(StagedLookup.Unknown(staged.operationId), restores().staged())
        assertEquals(kept, stagingFiles())

        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt_away RENAME TO operation_receipt")
        ledger.reopen()

        assertEquals(
            StagedLookup.NotReplaced(staged.operationId, attempted = true),
            restores().staged()
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun anAttemptedFileIsLeftBehindWhenTheHistoryMovesToANewGeneration() = runBlocking {
        currentHistory()
        val staged = stage()
        diedWhileReplacing(staged)

        ledger.database.openHelper.writableDatabase
            .execSQL("UPDATE profile SET generation = generation + 1")
        ledger.reopen()

        assertEquals(
            StagedLookup.NotReplaced(staged.operationId, attempted = true),
            restores().staged()
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    private val operationKey get() = File(files.restoreFolder, "staged.properties")

    /** A directory where the manifest's temporary copy goes: writing the manifest fails. */
    private fun blockManifestWrites() =
        File(files.restoreFolder, "staged.properties.tmp").apply { mkdirs() }

    @Test
    fun aStageCutShortOffersNothingRatherThanAnEarlierConfirmation() = runBlocking {
        currentHistory()
        val first = stage()
        diedWhileReplacing(first)
        assertEquals(StagedLookup.Waiting(first, attempted = true), restores().staged())

        val blocker = blockManifestWrites()
        assertEquals(Outcome.Failure(StageFailure.Storage), restores().stage(g01()))

        assertEquals(StagedLookup.None, restores().staged())
        blocker.deleteRecursively()
        val second = stage()
        assertEquals(StagedLookup.Waiting(second, attempted = false), restores().staged())
    }

    @Test
    fun aConfirmationThatCannotBeRecordedStillReplaces() = runBlocking {
        currentHistory()
        val staged = stage()
        val blocker = blockManifestWrites()

        // The replacement still reaches its transaction, which stamps the receipt.
        diedWhileReplacing(staged)

        assertEquals(StagedLookup.Waiting(staged, attempted = false), restores().staged())
        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
        blocker.deleteRecursively()
        assertEquals(listOf("${staged.operationId.value}|history.restore"), restoreReceipts())
    }

    @Test
    fun anotherOperationLeavesTheStagedFileUnmarked() = runBlocking {
        currentHistory()
        val staged = stage()
        val other = (OperationId.of(uuid(99_999)) as Outcome.Success).value

        diedWhileReplacing(staged.copy(operationId = other))

        assertFalse(operationKey.readText().contains("attempted"))
        assertEquals(StagedLookup.Waiting(staged, attempted = false), restores().staged())
    }

    /**
     * Runs the caller on a thread of its own and cancels it as it is about to go on
     * after the confirmation was recorded, before the replacement's transaction.
     */
    private class CancelAfterTheKey(
        private val manifest: File,
        private val delegate: CoroutineDispatcher
    ) : CoroutineDispatcher() {
        @Volatile
        var cancelled = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!cancelled && manifest.exists() && manifest.readText().contains("attempted=true")) {
                cancelled = true
                context[Job]?.cancel()
            }
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun aCallerCancelledAfterTheConfirmationIsRecordedCommitsNothing() = runBlocking {
        currentHistory()
        val before = everyRow()
        val staged = stage()
        val thread = Executors.newSingleThreadExecutor()
        val caller = CancelAfterTheKey(operationKey, thread.asCoroutineDispatcher())

        val replacing = async(caller) { restores().replace(staged) }
        val cancelled = runCatching { replacing.await() }.exceptionOrNull()
        thread.shutdown()

        assertTrue(caller.cancelled)
        assertTrue("$cancelled", cancelled is CancellationException)
        assertEquals(before, everyRow())
        assertEquals(emptyList<String>(), restoreReceipts())
        ledger.reopen()
        assertEquals(StagedLookup.Waiting(staged, attempted = true), restores().staged())
    }

    @Test
    fun aReplacementRefusedBecauseTheHistoryMovedIsNoLongerFoundAttempted() = runBlocking {
        currentHistory()
        val staged = stage()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-07", 9_000))

        assertEquals(RestoreOutcome.Stale, restores().replace(staged))
        ledger.reopen()

        val again = restores().staged() as StagedLookup.Waiting
        assertEquals(staged.operationId, again.staged.operationId)
        assertFalse(again.attempted)
    }

    @Test
    fun aChangeAfterThePreviewRefusesTheReplacementUntilPreviewedAgain() = runBlocking {
        currentHistory()
        val staged = stage()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-07", 9_000))
        val before = everyRow()

        assertEquals(RestoreOutcome.Stale, restores().replace(staged))
        assertEquals(before, everyRow())

        val fresh = (restores().staged() as StagedLookup.Waiting).staged
        assertEquals(BackupSummary(1, 3, 0, 0, 1, investments = 1), fresh.current)
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
            CommandOutcome.Rejected(CommandRejection.StaleGeneration(2)),
            draft
        )
        assertEquals(CommandOutcome.Rejected(CommandRejection.StaleGeneration(2)), undo)
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM deleted_expense"))
        assertEquals(3L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    /** CP26 and SC45: the restore replaces the carry chains too; this backup has none. */
    @Test
    fun aRestoreLeavesEveryCategoryOffAndFencesCarryCommandsPreparedBefore() = runBlocking {
        currentHistory()
        val enabled = ledger.categories.submit(
            ledger.carryOn(ledger.operation(), 1, ledger.categoryId(100), 1)
        )
        assertTrue("$enabled", enabled is CommandOutcome.Committed)
        assertEquals(listOf("${uuid(100)}|2026-10|null"), ledger.chainRows())

        restores().replace(stage())
        val prepared = ledger.categories.submit(
            ledger.carryOn(ledger.operation(), 1, ledger.categoryId(1), 1)
        )

        assertEquals(emptyList<String>(), ledger.chainRows())
        assertEquals(
            CommandOutcome.Rejected(CommandRejection.StaleGeneration(2)),
            prepared
        )
        assertEquals(emptyList<String>(), ledger.chainRows())
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
        assertEquals(StagedLookup.None, restores().staged())
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
    fun investmentSettingsAreAppliedAndOnesThatFailStayPendingAlone() = runBlocking {
        currentHistory()
        val dollars = InvestmentPreference(Currency.USD, InvestmentOrder.Return)
        investments.fail = true

        val outcome = restores().replace(stage(g01(wanted.copy(investments = dollars))))

        // Appearance and order were applied; only Inversiones' settings wait.
        assertEquals(
            PendingPreferences(null, null, dollars),
            (outcome as RestoreOutcome.Restored).preferences
        )
        assertEquals(InvestmentPreference(), investments.value)

        investments.fail = false
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(dollars, investments.value)
        assertNull(restores().pending())
    }

    @Test
    fun gastosHormigasLineIsAppliedAndOneThatFailsStaysPendingAlone() = runBlocking {
        currentHistory()
        val line = SmallExpenseLine.of(8_000)!!
        smallExpenses.fail = true

        val outcome = restores().replace(stage(g01(wanted.copy(smallExpenseLine = line))))

        // Appearance, order and Inversiones were applied; only the line waits.
        assertEquals(
            PendingPreferences(null, null, null, line),
            (outcome as RestoreOutcome.Restored).preferences
        )
        assertEquals(SmallExpenseLine.DEFAULT, smallExpenses.value)

        // After a restart it is still pending, then applied once it can be saved.
        ledger.reopen()
        assertEquals(PendingPreferences(null, null, null, line), restores().pending()?.preferences)
        smallExpenses.fail = false
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(line, smallExpenses.value)
        assertNull(restores().pending())
    }

    /** What the phone had seen before the restore (SELLO-205). */
    private val phoneGuidance = Guidance(
        GuidanceMemory(
            seen = setOf(Nudge.GuideHint),
            lastShown = ShownNudge(Nudge.Deleting, LocalDate.of(2026, 11, 3))
        ),
        existingUseChecked = true
    )

    /** What the backup says was seen. */
    private val backupGuidance = GuidanceMemory(
        seen = setOf(Nudge.Carry),
        lastShown = ShownNudge(Nudge.Carry, LocalDate.of(2026, 11, 2)),
        backupReminderShown = LocalDate.of(2026, 10, 30),
        backupReminderOff = true
    )

    @Test
    fun aRestoreMergesTheBackupsGuidanceIntoThePhonesOnce() = runBlocking {
        currentHistory()
        guidance.value = phoneGuidance

        val outcome = restores().replace(stage(g01(wanted.copy(guidance = backupGuidance))))

        assertEquals(
            PendingPreferences(null, null),
            (outcome as RestoreOutcome.Restored).preferences
        )
        assertEquals(
            Guidance(
                GuidanceMemory(
                    seen = setOf(Nudge.GuideHint, Nudge.Carry),
                    lastShown = ShownNudge(Nudge.Deleting, LocalDate.of(2026, 11, 3)),
                    backupReminderShown = LocalDate.of(2026, 10, 30),
                    backupReminderOff = true
                ),
                existingUseChecked = true
            ),
            guidance.value
        )
        assertEquals(listOf(backupGuidance), guidance.merges)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** A merge that cannot be saved stays pending alone, like any other group, until it lands. */
    @Test
    fun aGuidanceMergeThatFailsStaysPendingAloneAndIsAppliedLater() = runBlocking {
        currentHistory()
        guidance.value = phoneGuidance
        guidance.fail = true
        val staged = stage(g01(wanted.copy(guidance = backupGuidance)))

        val outcome = restores().replace(staged)

        assertEquals(
            RestoreOutcome.Restored(
                BackupSummary(2, 3, 1, 0, 0),
                PendingPreferences(null, null, guidance = backupGuidance)
            ),
            outcome
        )
        assertEquals(phoneGuidance, guidance.value)

        // After a restart it is still pending, and the history is not replaced again.
        ledger.reopen()
        val history = everyRow()
        assertEquals(outcome, restores().pending())
        guidance.fail = false
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(setOf(Nudge.GuideHint, Nudge.Carry), guidance.value.memory.seen)
        assertEquals(history, everyRow())
        assertNull(restores().pending())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /**
     * The phone's own guidance cannot be read, so no merge will ever land: the group is done
     * and the restore finishes with nothing pending.
     */
    @Test
    fun guidanceThePhoneCannotReadEndsTheGroupInsteadOfLeavingItPendingForGood() = runBlocking {
        currentHistory()
        guidance.fail = true
        guidance.failure = PreferenceFailure.InvalidValue

        val outcome = restores().replace(stage(g01(wanted.copy(guidance = backupGuidance))))

        assertEquals(
            RestoreOutcome.Restored(BackupSummary(2, 3, 1, 0, 0), PendingPreferences(null, null)),
            outcome
        )
        assertEquals(listOf(backupGuidance), guidance.merges)
        ledger.reopen()
        assertNull(restores().pending())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** While another group waits, guidance applied once is never merged again. */
    @Test
    fun aGuidanceMergeAppliedBesideAPendingGroupIsNeverRepeated() = runBlocking {
        currentHistory()
        guidance.value = phoneGuidance
        guidance.fail = true
        smallExpenses.fail = true
        val line = SmallExpenseLine.of(8_000)!!

        restores().replace(
            stage(g01(wanted.copy(smallExpenseLine = line, guidance = backupGuidance)))
        )

        ledger.reopen()
        guidance.fail = false
        val partly = restores().applyPending()
        assertEquals(PendingPreferences(null, null, null, line), partly?.preferences)
        assertEquals(setOf(Nudge.GuideHint, Nudge.Carry), guidance.value.memory.seen)

        smallExpenses.fail = false
        restores().applyPending()

        // Asked when it failed and when it landed; not again once applied.
        assertEquals(listOf(backupGuidance, backupGuidance), guidance.merges)
        assertNull(restores().pending())
    }

    private val newUserNudges = setOf(
        Nudge.BulkEntry,
        Nudge.FixedPayment,
        Nudge.Carry,
        Nudge.Deleting,
        Nudge.Investments,
        Nudge.ReciboGoal
    )

    /**
     * A file without guidance (older than version 11) whose history was in use: its person
     * was already using Sello, so the six new-user nudges are merged as seen; the Guía hint
     * and the backup reminder are left as the phone has them.
     */
    @Test
    fun aBackupWithoutGuidanceButInUseMarksTheNewUserNudgesSeen() = runBlocking {
        currentHistory()
        guidance.value = phoneGuidance

        restores().replace(stage(g01(wanted)))

        assertEquals(listOf(GuidanceMemory(seen = newUserNudges)), guidance.merges)
        assertEquals(
            phoneGuidance.copy(
                memory = phoneGuidance.memory.copy(seen = newUserNudges + Nudge.GuideHint)
            ),
            guidance.value
        )
    }

    /** The same with a backup made without preferences at all. */
    @Test
    fun aBackupWithoutPreferencesButInUseMarksTheNewUserNudgesSeen() = runBlocking {
        currentHistory()

        restores().replace(stage(g01(preferences = null)))

        assertEquals(newUserNudges, guidance.value.memory.seen)
    }

    /** A file without guidance and no expense, income, goal or investment keeps the phone's. */
    @Test
    fun aBackupWithoutGuidanceOrUseKeepsThePhones() = runBlocking {
        currentHistory()
        guidance.value = phoneGuidance

        val outcome = restores().replace(stage(g01(wanted, used = false)))

        assertEquals(
            PendingPreferences(null, null),
            (outcome as RestoreOutcome.Restored).preferences
        )
        assertEquals(phoneGuidance, guidance.value)
        assertTrue(guidance.merges.isEmpty())
    }

    /**
     * On real Room and a real preferences file: a restore of an older file in use marks the
     * six new-user nudges seen in one write, read back whole after its process ends.
     */
    @Test
    fun anOlderFileInUseMarksTheNewUserNudgesInOneWriteOnARealFile() = runBlocking {
        val name = "guidance-${files.name.removeSuffix(".db")}"
        val context = files.context
        fun open(job: Job) =
            DataStoreUserPreferences(context, CoroutineScope(Dispatchers.IO + job), name)
        val first = Job()
        val next = Job()
        try {
            val real = open(first)
            real.markSeen(Nudge.GuideHint)
            currentHistory()

            restores(guidance = real).replace(stage(g01(wanted)))
            first.cancelAndJoin()

            assertEquals(
                PreferenceRead.Ready(
                    Guidance(GuidanceMemory(seen = newUserNudges + Nudge.GuideHint))
                ),
                open(next).observeGuidance().first()
            )
        } finally {
            first.cancelAndJoin()
            next.cancelAndJoin()
            File(context.filesDir, "datastore/$name.preferences_pb").delete()
        }
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
        assertEquals(InvestmentPreference(), investments.value)
        assertEquals(SmallExpenseLine.DEFAULT, smallExpenses.value)
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
        // The staged file now belongs to a committed replacement: it is found replaced,
        // never offered again, and asking with the same staging replays, not replaces twice.
        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().pending() is RestoreOutcome.Restored)
        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
        assertEquals(1, ledger.rows("operation_receipt", "kind").count { it == "history.restore" })
    }

    @Test
    fun aCommittedReplacementWhoseFileCannotBeMovedStaysUnknownUntilItCan() = runBlocking {
        currentHistory()
        val staged = stage()
        val replacing = async(Dispatchers.Default) { restores().replace(staged) }
        whenStamped = { replacing.cancel() }
        runCatching { replacing.await() }
        whenStamped = {}
        // Something already sits where the file would move to, so the rename fails. The
        // blocker also makes `restored.exists()` true, which stops `discard()` from deleting
        // anything: this test cannot tell whether a failed rename discards the staging.
        val blocker = File(files.restoreFolder, "restored.json").apply { mkdirs() }
        File(blocker, "in-the-way").writeText("x")
        val kept = stagingFiles()

        assertEquals(StagedLookup.Unknown(staged.operationId), restores().staged())
        assertEquals(kept, stagingFiles())

        blocker.deleteRecursively()

        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().pending() is RestoreOutcome.Restored)
    }

    /**
     * Every preference G01 brings, none applied: with no guidance of its own and a history in
     * use, it brings the new-user nudges as seen.
     */
    private val everyPreference = RestoreOutcome.Restored(
        BackupSummary(2, 3, 1, 0, 0),
        PendingPreferences(
            wanted.appearance,
            wanted.ordering,
            InvestmentPreference(),
            SmallExpenseLine.DEFAULT,
            GuidanceMemory(seen = newUserNudges)
        )
    )

    @Test
    fun aFileThatCannotMoveAfterTheCommitIsNeverReportedComplete() = runBlocking {
        currentHistory()
        val staged = stage()
        // Something already sits where the file moves to, so the rename after the commit
        // fails, and reading what sits there fails too.
        val blocker = File(files.restoreFolder, "restored.json").apply { mkdirs() }
        File(blocker, "in-the-way").writeText("x")

        val outcome = restores().replace(staged)

        assertEquals(everyPreference, outcome)
        assertEquals("America/Lima|2|${ledger.revision()}", everyRow().last().single())
        assertTrue(preferences.written.isEmpty())
        assertEquals(everyPreference, restores().pending())
        assertEquals(everyPreference, restores().applyPending())
        assertTrue(preferences.written.isEmpty())

        // After a restart with the way clear, the file moves and every preference applies.
        blocker.deleteRecursively()
        ledger.reopen()
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                InkPreference.Violeta,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
        assertNull(restores().pending())
        assertEquals(emptyList<String>(), stagingFiles())
        assertEquals(1, ledger.rows("operation_receipt", "kind").count { it == "history.restore" })
    }

    @Test
    fun aStagedFileThatCannotBeReadFailsTheReplacementInsteadOfCrashing() = runBlocking {
        currentHistory()
        val staged = stage()
        val before = everyRow()
        val file = File(files.restoreFolder, "staged.json")
        assertTrue(file.setReadable(false, false))

        val outcome = restores().replace(staged)

        assertEquals(RestoreOutcome.Failed, outcome)
        assertEquals(before, everyRow())
        assertNull(restores().pending())

        assertTrue(file.setReadable(true, false))
        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
    }

    @Test
    fun anAppliedListThatCannotBeReadAppliesNothingAgainAndStaysPending() = runBlocking {
        currentHistory()
        preferences.failOrdering = true
        restores().replace(stage())
        val written = preferences.written.toList()
        val applied = File(files.restoreFolder, "applied")
        assertTrue(applied.setReadable(false, false))
        preferences.failOrdering = false

        val retried = restores().applyPending()

        assertEquals(everyPreference, retried)
        assertEquals(everyPreference, restores().pending())
        assertEquals(written, preferences.written)

        assertTrue(applied.setReadable(true, false))
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(
            written + listOf<Any>(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending),
            preferences.written
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /**
     * The computation dispatcher of a restore: it runs on [Dispatchers.Default] and counts
     * what it is handed; while [hold] is set it keeps that until [release].
     */
    private class Computation : CoroutineDispatcher() {
        val handed = AtomicInteger()
        private val arrived = Semaphore(0)
        private val held = ConcurrentLinkedQueue<Runnable>()

        @Volatile
        var hold = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            handed.incrementAndGet()
            if (hold) held += block else Dispatchers.Default.dispatch(context, block)
            arrived.release()
        }

        fun waitForWork(): Boolean = arrived.tryAcquire(10, TimeUnit.SECONDS)

        fun release() {
            hold = false
            while (true) Dispatchers.Default.dispatch(EmptyCoroutineContext, held.poll() ?: return)
        }
    }

    private fun on(computation: Computation) = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default: CoroutineDispatcher = computation
        override val io = Dispatchers.IO
    }

    @Test
    fun aRestoreAfterARestartParsesItsFileOnceAndNotOnTheCaller() = runBlocking {
        currentHistory()
        val staged = stage()
        ledger.reopen()
        val computation = Computation().apply { hold = true }
        val restores = restores(on(computation))

        val replacing = async(Dispatchers.IO) { restores.replace(staged) }

        assertTrue("nothing was handed to the computation dispatcher", computation.waitForWork())
        assertFalse(replacing.isCompleted)
        computation.release()
        assertEquals(
            RestoreOutcome.Restored(BackupSummary(2, 3, 1, 0, 0), PendingPreferences(null, null)),
            replacing.await()
        )
        assertEquals(1, computation.handed.get())
    }

    @Test
    fun aRestoreConfirmedAfterItsStageParsesTheFileNoMore() = runBlocking {
        currentHistory()
        val computation = Computation()
        val restores = restores(on(computation))
        val staged = restores.stage(g01()).valueOrFail()
        computation.handed.set(0)

        val outcome = restores.replace(staged)

        assertEquals(
            RestoreOutcome.Restored(BackupSummary(2, 3, 1, 0, 0), PendingPreferences(null, null)),
            outcome
        )
        assertEquals(0, computation.handed.get())
    }

    /** G01 replaced the history and its order is still to apply. */
    private suspend fun orderPending(): StagedRestore {
        currentHistory()
        preferences.failOrdering = true
        val staged = stage()
        restores().replace(staged)
        preferences.failOrdering = false
        assertEquals(listOf("applied", "restored.json", "staged.properties"), stagingFiles())
        return staged
    }

    @Test
    fun aRestoredFileThatCannotBeReadIsUnknownNeverNothing() = runBlocking {
        val staged = orderPending()
        val restored = File(files.restoreFolder, "restored.json")
        assertTrue(restored.setReadable(false, false))
        val kept = stagingFiles()

        assertEquals(StagedLookup.Unknown(staged.operationId), restores().staged())
        assertNull(restores().applyPending())
        assertEquals(Outcome.Failure(StageFailure.Storage), restores().stage(g01(other)))
        assertEquals(kept, stagingFiles())

        assertTrue(restored.setReadable(true, false))
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** Whose `restored.json` is comes from the receipts, so its manifest is not needed. */
    @Test
    fun aManifestThatCannotBeReadAfterTheMoveDoesNotHoldTheRestoreUp() = runBlocking {
        orderPending()
        assertTrue(File(files.restoreFolder, "staged.properties").setReadable(false, false))

        assertEquals(
            PendingPreferences(null, wanted.ordering),
            restores().pending()?.preferences
        )
        assertEquals(PendingPreferences(null, null), restores().applyPending()?.preferences)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aDamagedRestoredFileIsClearedSaidOnceAndBackupsGoOn() = runBlocking {
        orderPending()
        File(files.restoreFolder, "restored.json").writeText("{\"version\":")
        val history = everyRow()

        val said = restores().applyPending()

        assertEquals(
            RestoreOutcome.Restored(
                BackupSummary(2, 3, 1, 0, 0),
                PendingPreferences(null, null),
                preferencesUnreadable = true
            ),
            said
        )
        assertEquals(emptyList<String>(), stagingFiles())
        // Said once: after that there is nothing pending and nothing unknown.
        assertNull(restores().applyPending())
        assertNull(restores().pending())
        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(history, everyRow())
        assertTrue(preferences.written.none { it == CategoryOrder.Alphabetical })
        // Another file can be restored, as a backup can be made.
        assertTrue(restores().replace(stage(g01(other))) is RestoreOutcome.Restored)
    }

    @Test
    fun aRestoredFileLeftByAnErasedHistoryWithoutItsManifestIsNeverApplied() = runBlocking {
        orderPending()
        // An erase whose clean-up took the manifest but not the restored file.
        RoomResets(
            ledger.database,
            files.resetFolder,
            File(files.restoreFolder, "elsewhere"),
            emptyList(),
            PreferenceReset { PreferenceWrite.Saved },
            { lima },
            { now },
            dispatchers,
            Mutex()
        ).let { it.reset(it.preview().valueOrFail(), ledger.operation()) }
        File(files.restoreFolder, "staged.properties").delete()
        val written = preferences.written.toList()

        assertNull(restores().applyPending())
        assertEquals(written, preferences.written)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aLaterRestoreCommittedWhileTheClockWasBackStillAppliesItsPreferences() = runBlocking {
        currentHistory()
        restores().replace(stage())
        // The clock goes back an hour: the second restore commits "before" the first.
        now = now.minusSeconds(60 * 60)
        preferences.failAppearance = true
        val second = restores().replace(stage(g01(other)))
        assertEquals(
            PendingPreferences(other.appearance, null),
            (second as RestoreOutcome.Restored).preferences
        )
        preferences.failAppearance = false

        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(
            listOf<Any>(ThemePreference.Light, InkPreference.Cobalto),
            preferences.written.takeLast(2)
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun theSameFileStagedAgainAfterLaterChangesIsNeverTakenForTheEarlierRestore() = runBlocking {
        currentHistory()
        val bytes = g01()
        restores().replace(stage(bytes))
        val restoredAt = ledger.revision()
        ledger.categories.submit(ledger.createCategory("Nueva", finite(10_000), generation = 2))
        assertEquals(restoredAt + 1, ledger.revision())
        val written = preferences.written.toList()
        stage(bytes)
        File(files.restoreFolder, "staged.properties").writeText("operation=unreadable\n")

        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
        assertNull(restores().pending())
        assertEquals(written, preferences.written)
    }

    @Test
    fun aCommittedRestoresStagedFileDamagedWithItsManifestIsSaidOnceNotForgotten() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        File(files.restoreFolder, "staged.json").writeText("{\"version\":")
        File(files.restoreFolder, "staged.properties").writeText("operation=unreadable\n")

        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertEquals(true, restores().applyPending()?.preferencesUnreadable)
        assertNull(restores().pending())
        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aDamagedRestoredFileBesideAStagedOneIsStillClearedAndSaidOnce() = runBlocking {
        orderPending()
        File(files.restoreFolder, "restored.json").writeText("{\"version\":")
        File(files.restoreFolder, "staged.json").writeBytes(g01(other))

        val said = restores().applyPending()

        assertEquals(true, said?.preferencesUnreadable)
        assertEquals(listOf("staged.json", "staged.properties"), stagingFiles())
        assertFalse(restores().staged() is StagedLookup.Unknown)
    }

    @Test
    fun aFileTheStartUpCheckReadIsStagedAndRestoredWithoutParsingAgain() = runBlocking {
        currentHistory()
        val file = BackupCodec().checkFile(g01()).valueOrFail()
        val computation = Computation()
        val restores = restores(on(computation))

        val staged = restores.stage(file).valueOrFail()
        val outcome = restores.replace(staged)

        assertEquals(
            RestoreOutcome.Restored(BackupSummary(2, 3, 1, 0, 0), PendingPreferences(null, null)),
            outcome
        )
        assertEquals(0, computation.handed.get())
    }

    @Test
    fun anExpiredOrMovedStagingIsKeptWhileItsReceiptCannotBeRead() = runBlocking {
        currentHistory()
        val staged = stage()
        val kept = stagingFiles()
        val database = ledger.database.openHelper.writableDatabase
        database.execSQL("UPDATE profile SET generation = generation + 1")
        database.execSQL("ALTER TABLE operation_receipt RENAME TO operation_receipt_away")
        now = now.plusSeconds(24 * 60 * 60 + 1)

        ledger.reopen()

        assertEquals(StagedLookup.Unknown(staged.operationId), restores().staged())
        assertEquals(kept, stagingFiles())

        // Once the receipts read again it is known not replaced, and only then forgotten.
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt_away RENAME TO operation_receipt")
        ledger.reopen()

        assertEquals(StagedLookup.NotReplaced(staged.operationId), restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aReceiptThatCannotBeReadLeavesTheOutcomeUnknownAndTheFileInPlace() = runBlocking {
        currentHistory()
        val staged = stage()
        val before = everyRow()
        val kept = stagingFiles()
        val receipts = ledger.database.openHelper.writableDatabase
        receipts.execSQL("ALTER TABLE operation_receipt RENAME TO operation_receipt_away")

        ledger.reopen()
        val unknown = restores().staged()

        assertEquals(StagedLookup.Unknown(staged.operationId), unknown)
        assertEquals(kept, stagingFiles())
        assertNull(restores().pending())

        // Once the receipts read again, it is found not replaced and offered as it was.
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt_away RENAME TO operation_receipt")
        ledger.reopen()

        assertEquals(StagedLookup.Waiting(staged), restores().staged())
        assertEquals(before, everyRow())
    }

    @Test
    fun anUnconfirmedFileExpiresAfterADay() = runBlocking {
        currentHistory()
        val staged = stage()

        now = now.plusSeconds(24 * 60 * 60 + 1)

        assertEquals(StagedLookup.NotReplaced(staged.operationId), restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aPreviewADayOldIsForgottenAtStartWithoutBeingLookedAt() = runBlocking {
        currentHistory()
        val staged = stage()

        now = now.plusSeconds(24 * 60 * 60 + 1)

        assertEquals(StagedLookup.NotReplaced(staged.operationId), restores().expireStaged())
        assertEquals(emptyList<String>(), stagingFiles())
        assertEquals(StagedLookup.None, restores().staged())
    }

    @Test
    fun aPreviewNotADayOldIsKeptAtStart() = runBlocking {
        currentHistory()
        val staged = stage()
        val kept = stagingFiles()

        now = now.plusSeconds(24 * 60 * 60)

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
        assertEquals(StagedLookup.Waiting(staged), restores().staged())
    }

    @Test
    fun aConfirmedFileADayOldIsForgottenAtStartAndSaidConfirmed() = runBlocking {
        currentHistory()
        val staged = stage()
        val before = everyRow()
        diedWhileReplacing(staged)

        now = now.plusSeconds(24 * 60 * 60 + 1)

        assertEquals(
            StagedLookup.NotReplaced(staged.operationId, attempted = true),
            restores().expireStaged()
        )
        assertEquals(emptyList<String>(), stagingFiles())
        assertEquals(before, everyRow())
    }

    @Test
    fun aConfirmedFileADayOldIsKeptAtStartWhileItsReceiptCannotBeRead() = runBlocking {
        currentHistory()
        val staged = stage()
        diedWhileReplacing(staged)
        val kept = stagingFiles()
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt RENAME TO operation_receipt_away")
        now = now.plusSeconds(24 * 60 * 60 + 1)
        ledger.reopen()

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
    }

    @Test
    fun aMovedReplacementWithPreferencesPendingIsLeftAloneAtStart() = runBlocking {
        currentHistory()
        preferences.failAppearance = true
        restores().replace(stage())
        val kept = stagingFiles()

        now = now.plusSeconds(2 * 24 * 60 * 60)

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
        assertFalse(restores().pending()!!.preferences.done)
    }

    @Test
    fun aFileNeverReplacedBesideAReplacementWithPreferencesPendingIsKeptAtStart() = runBlocking {
        currentHistory()
        preferences.failAppearance = true
        restores().replace(stage())
        val folder = files.restoreFolder
        File(folder, "restored.json").copyTo(File(folder, "staged.json"))
        File(folder, "staged.properties").writeText(
            "operation=00000000-0000-4000-8000-00000000f172\n" +
                "generation=1\nstagedAt=${now.toEpochMilli()}\n"
        )
        val kept = stagingFiles()
        val bytes = File(folder, "staged.json").readBytes().toList()

        now = now.plusSeconds(2 * 24 * 60 * 60)

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
        assertEquals(bytes, File(folder, "staged.json").readBytes().toList())
        assertFalse(restores().pending()!!.preferences.done)
    }

    @Test
    fun aCommittedFileADayOldIsNotExpiredAtStartButFinishedByTheLook() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        val kept = stagingFiles()

        now = now.plusSeconds(24 * 60 * 60 + 1)

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().applyPending() is RestoreOutcome.Restored)
        assertEquals(1, restoreReceipts().size)
    }

    /** A manifest that names no operation, beside a staged file last written two days ago. */
    private fun unreadableManifestBesideATwoDayOldFile() {
        File(files.restoreFolder, "staged.properties").writeText("operation=unreadable\n")
        assertTrue(
            File(files.restoreFolder, "staged.json")
                .setLastModified(now.minusSeconds(2 * 24 * 60 * 60).toEpochMilli())
        )
    }

    @Test
    fun aFileBesideAnUnreadableManifestIsKeptAtStartWhateverItsAge() = runBlocking {
        currentHistory()
        stage()
        unreadableManifestBesideATwoDayOldFile()
        val kept = stagingFiles()

        now = now.plusSeconds(2 * 24 * 60 * 60)

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
    }

    @Test
    fun aCommittedFileBesideAnUnreadableManifestIsKeptAtStartAndFinishedAfter() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        unreadableManifestBesideATwoDayOldFile()
        val kept = stagingFiles()

        now = now.plusSeconds(24 * 60 * 60 + 1)

        assertNull(restores().expireStaged())
        assertEquals(kept, stagingFiles())
        // Respaldo's look settles it by its receipt; its preferences are then applied.
        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().applyPending() is RestoreOutcome.Restored)
        assertEquals(1, restoreReceipts().size)
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

    private val other = PortablePreferences(
        AppearancePreference(ThemePreference.Light, InkPreference.Cobalto),
        CategoryOrderingPreference(CategoryOrder.Limit, CategoryOrderDirection.Descending)
    )

    /**
     * G01 replaced the history and every preference was applied: the folder as the
     * clean-up found it. Appearance failed at first and the retry that cleans up applied
     * it, so `applied` lists it last.
     */
    private suspend fun cleanUpDue() {
        currentHistory()
        preferences.failAppearance = true
        restores().replace(stage())
        preferences.failAppearance = false
        File(files.restoreFolder, "applied").appendText("\nappearance")
        assertEquals(listOf("applied", "restored.json", "staged.properties"), stagingFiles())
    }

    /** As if the process died in the clean-up with only [left] still on disk. */
    private fun diedInTheCleanUp(vararg left: String) {
        files.restoreFolder.listFiles()!!.filter { it.name !in left }.forEach { it.delete() }
        assertEquals(left.sorted(), stagingFiles())
        ledger.reopen()
    }

    /**
     * The start-up apply, then the look Respaldo makes: a replacement finished quietly or
     * shown restored, never unknown, with nothing applied twice and nothing left behind.
     */
    private suspend fun assertFinishedQuietly() {
        val written = preferences.written.toList()
        val startUp = restores().applyPending()

        assertTrue("$startUp", startUp == null || startUp.preferences.done)
        assertNull(restores().pending())
        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(written, preferences.written)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** Then the next restore, of a file with other preferences, applies every one of them. */
    private suspend fun assertTheNextRestoreAppliesItsOwnPreferences() {
        val marks = notice.marks.size
        val outcome = restores().replace(stage(g01(other)))

        assertEquals(
            RestoreOutcome.Restored(BackupSummary(2, 3, 1, 0, 0), PendingPreferences(null, null)),
            outcome
        )
        assertEquals(
            listOf<Any>(
                ThemePreference.Light,
                InkPreference.Cobalto,
                CategoryOrder.Limit,
                CategoryOrderDirection.Descending
            ),
            preferences.written.takeLast(4)
        )
        assertEquals(marks + 1, notice.marks.size)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aDeathBeforeTheCleanUpFinishesTheRestoreQuietly() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied", "restored.json", "staged.properties")

        assertFinishedQuietly()
        assertTheNextRestoreAppliesItsOwnPreferences()
    }

    @Test
    fun aDeathAfterTheManifestWentFinishesTheRestoreQuietly() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied", "restored.json")

        assertFinishedQuietly()
        assertTheNextRestoreAppliesItsOwnPreferences()
    }

    @Test
    fun aDeathAfterTheRestoredFileWentLeavesNoAppliedListForTheNextRestore() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied")

        assertTheNextRestoreAppliesItsOwnPreferences()
    }

    @Test
    fun aDeathAfterTheRestoredFileWentIsClearedByTheNextLook() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied")

        assertFinishedQuietly()
    }

    /** What the clean-up's earlier order (restored, applied, manifest) left on a death. */
    @Test
    fun whatTheEarlierCleanUpOrderLeftNeverReachesTheNextRestore() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied", "staged.properties")

        assertTheNextRestoreAppliesItsOwnPreferences()
    }

    @Test
    fun whatTheEarlierCleanUpOrderLeftIsClearedByTheNextLook() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied", "staged.properties")

        assertFinishedQuietly()
    }

    @Test
    fun aManifestLeftAloneIsClearedByTheNextLook() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("staged.properties")

        assertFinishedQuietly()
    }

    /** The temporary copy a write of [name] makes, as a death before its rename leaves it. */
    private fun copyLeftOf(name: String, text: String) =
        File(files.restoreFolder, "$name.tmp").writeText(text)

    @Test
    fun aStageCutShortBeforeItsFileWasInPlaceLeavesNoCopyBehind() = runBlocking {
        currentHistory()
        stage()
        // The next stage deleted the manifest and died before renaming its file's copy.
        File(files.restoreFolder, "staged.properties").delete()
        copyLeftOf("staged.json", String(g01(other)))
        ledger.reopen()

        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /**
     * Confirmed once already, then refused as stale, and the process died taking the mark
     * back before its rename: confirming again rewrites nothing, so no write replaces it.
     */
    @Test
    fun aConfirmationCutShortLeavesNoCopyOfTheManifestOnceReplaced() = runBlocking {
        currentHistory()
        val staged = stage()
        diedWhileReplacing(staged)
        copyLeftOf("staged.properties", "operation=${staged.operationId.value}\n")

        assertEquals(StagedLookup.Waiting(staged, attempted = true), restores().staged())
        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aConfirmationCutShortLeavesNoCopyOfTheManifestOnceCancelled() = runBlocking {
        currentHistory()
        val staged = stage()
        copyLeftOf("staged.properties", "operation=${staged.operationId.value}\nattempted=true\n")
        ledger.reopen()

        restores().discard()

        assertEquals(emptyList<String>(), stagingFiles())
    }

    /**
     * Every preference was applied and the process died marking the notice before its
     * rename. After the restart the notice cannot be marked, so no write replaces the copy.
     */
    @Test
    fun anAppliedGroupCutShortLeavesNoCopyOnceEveryPreferenceIsApplied() = runBlocking {
        currentHistory()
        preferences.failOrdering = true
        notice.fail = true
        restores().replace(stage())
        // Then ordering was applied, and the death came marking the notice.
        val applied = File(files.restoreFolder, "applied")
        applied.appendText("\nordering")
        copyLeftOf("applied", "${applied.readText()}\nnotice")
        ledger.reopen()

        preferences.failOrdering = false
        val done = restores().applyPending()

        assertEquals(PendingPreferences(null, null), done?.preferences)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aFileThatCannotTakeItsPlaceLeavesNoCopyOfTheBackupBehind() = runBlocking {
        currentHistory()
        // Something sits where the staged file goes, so the copy cannot be renamed over it.
        val blocker = File(files.restoreFolder, "staged.json").apply { mkdirs() }
        File(blocker, "in-the-way").writeText("x")

        assertEquals(Outcome.Failure(StageFailure.Storage), restores().stage(g01()))
        assertFalse(File(files.restoreFolder, "staged.json.tmp").exists())
    }

    /** The manifest without its `stagedAt`: its operation still reads, the rest does not. */
    private fun manifestCutShort() {
        val key = File(files.restoreFolder, "staged.properties")
        key.writeText(key.readLines().filterNot { it.startsWith("stagedAt=") }.joinToString("\n"))
    }

    /** It will not read better later: once its replacement is known committed, it moves on. */
    @Test
    fun aManifestCutShortWhoseReplacementCommittedIsFinishedNotLeftUnknown() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        manifestCutShort()

        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().applyPending() is RestoreOutcome.Restored)
        assertNull(restores().pending())
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aManifestNamingNoOperationBesideAReplacedFileIsFinishedByItsReceipt() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        File(files.restoreFolder, "staged.properties").writeText("operation=unreadable\n")

        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertEquals(
            PendingPreferences(null, null),
            restores().applyPending()?.preferences
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aManifestNamingNoOperationBesideAFileNeverReplacedIsCleared() = runBlocking {
        currentHistory()
        stage()
        File(files.restoreFolder, "staged.properties").writeText("operation=unreadable\n")

        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
        assertTrue(restores().stage(g01(other)) is Outcome.Success)
    }

    @Test
    fun aFileChosenBesideAManifestCutShortWhoseReplacementCommittedIsRefused() = runBlocking {
        currentHistory()
        committedBeforeTheRename()
        manifestCutShort()
        val kept = stagingFiles()

        assertEquals(Outcome.Failure(StageFailure.Unsettled), restores().stage(g01(other)))
        assertEquals(kept, stagingFiles())
    }

    @Test
    fun aFileChosenBesideAManifestNamingNoOperationIsRefused() = runBlocking {
        currentHistory()
        stage()
        File(files.restoreFolder, "staged.properties").writeText("operation=unreadable\n")
        val kept = stagingFiles()

        assertEquals(Outcome.Failure(StageFailure.Storage), restores().stage(g01(other)))
        assertEquals(kept, stagingFiles())
    }

    /** A `\u` escape that is not one: the manifest does not load at all. */
    private fun manifestWithAMalformedEscape() =
        File(files.restoreFolder, "staged.properties").writeText("operation=\\uZZZZ\n")

    @Test
    fun aManifestWithAMalformedEscapeBesideAReplacedFileIsFinishedByItsReceipt() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        manifestWithAMalformedEscape()

        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertEquals(
            PendingPreferences(null, null),
            restores().applyPending()?.preferences
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    @Test
    fun aManifestWithAMalformedEscapeBesideAFileNeverReplacedIsCleared() = runBlocking {
        currentHistory()
        stage()
        manifestWithAMalformedEscape()

        assertEquals(StagedLookup.None, restores().staged())
        assertEquals(emptyList<String>(), stagingFiles())
        assertTrue(restores().stage(g01(other)) is Outcome.Success)
    }

    @Test
    fun aFileChosenBesideAManifestWithAMalformedEscapeIsRefused() = runBlocking {
        currentHistory()
        stage()
        manifestWithAMalformedEscape()
        val kept = stagingFiles()

        assertEquals(Outcome.Failure(StageFailure.Storage), restores().stage(g01(other)))
        assertEquals(kept, stagingFiles())
    }

    /** The confirmation cannot be recorded in it; the replacement still goes ahead. */
    @Test
    fun aConfirmationBesideAManifestWithAMalformedEscapeStillReplaces() = runBlocking {
        currentHistory()
        val staged = stage()
        manifestWithAMalformedEscape()

        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)
        assertEquals(listOf("${staged.operationId.value}|history.restore"), restoreReceipts())
    }

    @Test
    fun aFileChosenBesideAManifestCutShortThatDidNotCommitTakesItsPlace() = runBlocking {
        currentHistory()
        stage()
        manifestCutShort()

        val next = restores().stage(g01(other)).valueOrFail()

        assertEquals(StagedLookup.Waiting(next), restores().staged())
    }

    @Test
    fun aManifestThatCannotBeReadWholeIsKeptWhileItsReceiptCannotBeRead() = runBlocking {
        currentHistory()
        val staged = stage()
        manifestCutShort()
        val kept = stagingFiles()
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt RENAME TO operation_receipt_away")
        ledger.reopen()

        assertEquals(StagedLookup.Unknown(staged.operationId), restores().staged())
        assertEquals(kept, stagingFiles())
    }

    @Test
    fun aManifestThatCannotBeReadWholeIsClearedOnceItsReplacementIsKnownNotCommitted() =
        runBlocking {
            currentHistory()
            stage()
            manifestCutShort()

            assertEquals(StagedLookup.None, restores().staged())
            assertEquals(emptyList<String>(), stagingFiles())
        }

    /** A clean-up cut short by an earlier version, then a file staged by that version too. */
    @Test
    fun anAppliedListBesideAFileAnEarlierVersionStagedIsNotThisRestores() = runBlocking {
        cleanUpDue()
        diedInTheCleanUp("applied", "staged.properties")
        val stale = File(files.restoreFolder, "applied").readText()
        val staged = stage(g01(other))
        File(files.restoreFolder, "applied").writeText(stale)
        ledger.reopen()
        val marks = notice.marks.size

        assertTrue(restores().replace(staged) is RestoreOutcome.Restored)

        assertEquals(
            listOf<Any>(
                ThemePreference.Light,
                InkPreference.Cobalto,
                CategoryOrder.Limit,
                CategoryOrderDirection.Descending
            ),
            preferences.written.takeLast(4)
        )
        assertEquals(marks + 1, notice.marks.size)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** Committed before the rename, beside an earlier replacement's `applied` list. */
    @Test
    fun aReplacementFoundCommittedAppliesItsOwnPreferencesBesideAnOldAppliedList() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        File(files.restoreFolder, "applied").writeText("appearance\nordering\nnotice")

        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().applyPending()!!.preferences.done)

        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                InkPreference.Violeta,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
        assertEquals(1, notice.marks.size)
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** As if the process died after the replacement committed, before its file was renamed. */
    private suspend fun committedBeforeTheRename(): StagedRestore = coroutineScope {
        val staged = stage()
        val replacing = async(Dispatchers.Default) { restores().replace(staged) }
        whenStamped = { replacing.cancel() }
        runCatching { replacing.await() }
        whenStamped = {}
        ledger.reopen()
        staged
    }

    @Test
    fun aFileChosenBeforeACommittedReplacementWasFoundIsRefusedUntilItIs() = runBlocking {
        currentHistory()
        val staged = committedBeforeTheRename()
        val kept = stagingFiles()

        assertEquals(Outcome.Failure(StageFailure.Unsettled), restores().stage(g01(other)))
        assertEquals(Outcome.Failure(StageFailure.Unsettled), restores().stage(g01(other)))
        assertEquals(kept, stagingFiles())

        // The look then finds it replaced under its own operation.
        assertEquals(StagedLookup.Replaced(staged.operationId), restores().staged())
        assertTrue(restores().applyPending()!!.preferences.done)
        assertEquals(1, restoreReceipts().size)
        assertTrue(restores().stage(g01(other)) is Outcome.Success)
    }

    @Test
    fun aFileChosenWhileAnEarlierOutcomeCannotBeReadIsRefusedAndKeepsEverything() = runBlocking {
        currentHistory()
        val staged = stage()
        copyLeftOf("staged.properties", "operation=${staged.operationId.value}\nattempted=true\n")
        val kept = stagingFiles()
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt RENAME TO operation_receipt_away")
        ledger.reopen()

        assertEquals(Outcome.Failure(StageFailure.Unsettled), restores().stage(g01(other)))
        assertEquals(StagedLookup.Unknown(staged.operationId), restores().staged())
        assertEquals(kept, stagingFiles())

        // Once the receipts read again it is not replaced, and another file can be chosen.
        ledger.database.openHelper.writableDatabase
            .execSQL("ALTER TABLE operation_receipt_away RENAME TO operation_receipt")
        ledger.reopen()
        val next = restores().stage(g01(other)).valueOrFail()

        assertEquals(StagedLookup.Waiting(next), restores().staged())
        assertEquals(emptyList<String>(), restoreReceipts())
    }

    @Test
    fun aFileChosenAfterAConfirmationThatDidNotCommitTakesItsPlace() = runBlocking {
        currentHistory()
        val first = stage()
        diedWhileReplacing(first)

        val second = restores().stage(g01(other)).valueOrFail()

        assertEquals(StagedLookup.Waiting(second, attempted = false), restores().staged())
        assertEquals(emptyList<String>(), restoreReceipts())
    }
}
