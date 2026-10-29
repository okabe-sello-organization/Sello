package com.software.sello.data

import android.content.Context
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.data.backup.RoomRestores
import com.software.sello.data.export.RoomDataExports
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.keptFiles
import com.software.sello.data.local.openSelloDatabase
import com.software.sello.data.local.setAside
import com.software.sello.data.preferences.PREFERENCES_FILE
import com.software.sello.data.repository.RoomCategoryCommands
import com.software.sello.data.repository.RoomCategoryExpenseSearch
import com.software.sello.data.repository.RoomCategoryInsightsSnapshots
import com.software.sello.data.repository.RoomCategoryReads
import com.software.sello.data.repository.RoomExpenseCommands
import com.software.sello.data.repository.RoomExpenseReads
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.data.repository.RoomGoalCommands
import com.software.sello.data.repository.RoomGoalReads
import com.software.sello.data.repository.RoomIncomeCommands
import com.software.sello.data.repository.RoomIncomeReads
import com.software.sello.data.repository.RoomInvestmentCommands
import com.software.sello.data.repository.RoomInvestmentReads
import com.software.sello.data.repository.RoomMonthlySnapshots
import com.software.sello.data.repository.RoomResumenSnapshots
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.data.repository.RoomScheduleCommands
import com.software.sello.data.repository.RoomScheduleReads
import com.software.sello.data.repository.RoomScheduleRecordReads
import com.software.sello.data.repository.UndoWindow
import com.software.sello.data.repository.discardKeptCopies
import com.software.sello.data.repository.reading
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryExpenseSearch
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.DataExports
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialProfileStore
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.Resets
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.ScheduleRecordReads
import java.io.File
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

/**
 * What the composition root gets from storage: domain ports backed by the one real
 * database, and nothing of Room. Each workflow that ships adds its port here.
 * Closing it closes the database.
 */
class FinancialStorage internal constructor(
    private val database: SelloDatabase,
    val profiles: FinancialProfileStore,
    private val restoreFolder: File,
    private val resetFolder: File,
    private val setAsideDatabase: File
) : AutoCloseable {
    /**
     * The expense workflow. It takes its clocks and identifier source here because the
     * financial clock can only exist once the stored zone has been read from this storage.
     */
    fun expenseCommands(
        audit: AuditClock,
        clock: FinancialClock,
        ids: RecordIdSource,
        monotonic: MonotonicClock
    ): ExpenseCommands = RoomExpenseCommands(database, audit, clock, ids, undoWindow(monotonic))

    /**
     * The income workflow. It shares the one offer to undo with the expense workflow:
     * a deletion of either kind makes an earlier one of either kind final.
     */
    fun incomeCommands(
        audit: AuditClock,
        clock: FinancialClock,
        ids: RecordIdSource,
        monotonic: MonotonicClock
    ): IncomeCommands = RoomIncomeCommands(database, audit, clock, ids, undoWindow(monotonic))

    val incomeReads: IncomeReads = RoomIncomeReads(database)

    /** Goals. A deleted movement shares the one offer to undo with expenses and income. */
    fun goalCommands(
        audit: AuditClock,
        clock: FinancialClock,
        ids: RecordIdSource,
        monotonic: MonotonicClock
    ): GoalCommands =
        RoomGoalCommands(database, audit, clock, ids, undoWindow(monotonic), monotonic)

    val goalReads: GoalReads = RoomGoalReads(database)

    /**
     * Investments and the USD rate. A deleted movement shares the one offer to undo with
     * expenses, income and goal movements.
     */
    fun investmentCommands(
        audit: AuditClock,
        clock: FinancialClock,
        ids: RecordIdSource,
        monotonic: MonotonicClock
    ): InvestmentCommands =
        RoomInvestmentCommands(database, audit, clock, ids, undoWindow(monotonic))

    val investmentReads: InvestmentReads = RoomInvestmentReads(database)

    /** Recurring payments and incomes: create, change, pause, resume and delete. */
    fun scheduleCommands(
        audit: AuditClock,
        clock: FinancialClock,
        ids: RecordIdSource
    ): ScheduleCommands = RoomScheduleCommands(database, audit, clock, ids)

    /** Records what schedules have due, one batch per transaction. */
    fun scheduleCatchUp(audit: AuditClock, ids: RecordIdSource): ScheduleCatchUpStore =
        RoomScheduleCatchUp(database, audit, ids)

    /** Recurring payments and incomes with their pauses and catch-up checkpoints. */
    val scheduleReads: ScheduleReads = RoomScheduleReads(database)

    val scheduleRecordReads: ScheduleRecordReads = RoomScheduleRecordReads(database)

    /** One offer to undo for the whole storage, whichever workflow asks for it first. */
    private var undo: UndoWindow? = null

    @Synchronized
    private fun undoWindow(monotonic: MonotonicClock): UndoWindow =
        undo ?: UndoWindow(monotonic).also { undo = it }

    fun categoryCommands(
        audit: AuditClock,
        clock: FinancialClock,
        ids: RecordIdSource
    ): CategoryCommands = RoomCategoryCommands(database, audit, clock, ids)

    val categoryReads: CategoryReads = RoomCategoryReads(database)

    val expenseReads: ExpenseReads = RoomExpenseReads(database)

    val categoryExpenseSearch: CategoryExpenseSearch = RoomCategoryExpenseSearch(database)

    fun categoryInsights(
        clock: FinancialClock,
        computation: CoroutineContext
    ): CategoryInsightsSnapshots = RoomCategoryInsightsSnapshots(database, clock, computation)

    /**
     * Restores into this storage, staged in its own private folder. The default [codec]
     * refuses a backup in which categories carry their balance, as the customer app does
     * until SELLO-041; a test or developer path passes one that accepts it.
     */
    fun restores(
        appearance: AppearancePreferences,
        ordering: CategoryOrderingPreferences,
        investments: InvestmentPreferences,
        notice: CatchUpNoticePreferences,
        ids: RecordIdSource,
        audit: AuditClock,
        dispatchers: DispatcherProvider,
        codec: BackupCodec = BackupCodec()
    ): Restores = RoomRestores(
        database,
        restoreFolder,
        appearance,
        ordering,
        investments,
        notice,
        ids,
        audit,
        dispatchers,
        codec
    )

    /**
     * Erasing this storage on request: its rows, its restore folder, the copy set aside
     * when it would not open, and the preferences [preferences] reset. A new history
     * takes [deviceZone].
     */
    fun resets(
        preferences: PreferenceReset,
        deviceZone: () -> ZoneId,
        audit: AuditClock,
        dispatchers: DispatcherProvider
    ): Resets = RoomResets(
        database,
        resetFolder,
        restoreFolder,
        keptFiles(setAsideDatabase),
        preferences,
        deviceZone,
        audit,
        dispatchers
    )

    /**
     * Backup files of this storage, with the portable preferences beside it. A [codec]
     * that refuses carry, the default, makes no file for a history in which a category
     * carries its balance, as it could not restore it either.
     */
    fun backupExports(
        appearance: AppearancePreferences,
        ordering: CategoryOrderingPreferences,
        investments: InvestmentPreferences,
        audit: AuditClock,
        dispatchers: DispatcherProvider,
        codec: BackupCodec = BackupCodec()
    ): BackupExports = BackupExport(
        RoomBackupSnapshots(database),
        appearance,
        ordering,
        investments,
        audit,
        dispatchers,
        codec
    )

    /** CSV and JSON exports of this storage, read from one revision (SELLO-121). */
    fun dataExports(audit: AuditClock, dispatchers: DispatcherProvider): DataExports =
        RoomDataExports(RoomBackupSnapshots(database), audit, dispatchers)

    /**
     * Takes the financial clock for the same reason the commands do. [computation] must
     * carry the dispatcher the month's figures are worked out on; it is never the caller's.
     */
    fun monthlySnapshots(clock: FinancialClock, computation: CoroutineContext): MonthlySnapshots =
        RoomMonthlySnapshots(database, clock, computation)

    fun resumenSnapshots(clock: FinancialClock, computation: CoroutineContext): ResumenSnapshots =
        RoomResumenSnapshots(database, clock, computation)

    /**
     * Makes every deletion final by removing what was kept to undo it. The time to undo
     * is counted in the process that deleted, so whatever a new process finds here can
     * no longer be undone. Call it once when storage is opened. It removes no expense
     * and changes no figure: a deleted expense was already gone.
     */
    suspend fun discardUndoCopies(): Outcome<Unit, StorageFailure> = reading {
        database.discardKeptCopies()
        Outcome.Success(Unit)
    }

    override fun close() = database.close()
}

/**
 * The private files one set of data lives in: its database and its preferences. The
 * app uses [Normal]; a debug sandbox keeps its own set beside it, never sharing a file.
 */
data class StorageNames(val database: String, val preferences: String) {
    init {
        require(database.endsWith(".db")) { "a database file name ends in .db" }
    }

    /** Where [database] is kept when it cannot be opened and the person starts again. */
    val setAsideDatabase: String get() = database.removeSuffix(".db") + "-unreadable.db"

    /** The private folder a restore into [database] is staged in. */
    val restoreFolder: String get() = database.removeSuffix(".db") + "-restore"

    /** The private folder an erase of [database] is tracked in until it is finished. */
    val resetFolder: String get() = database.removeSuffix(".db") + "-reset"

    companion object {
        val Normal = StorageNames(SelloDatabase.FILE_NAME, PREFERENCES_FILE)
    }
}

/**
 * Prepares the financial database named in [names] without touching the file yet; the
 * first read or write opens it, creating it on first use. [queries] must carry the
 * dispatcher database work runs on.
 */
fun openFinancialStorage(
    context: Context,
    queries: CoroutineContext,
    audit: AuditClock,
    names: StorageNames = StorageNames.Normal
): FinancialStorage {
    val database = openSelloDatabase(context, queries, names.database)
    return FinancialStorage(
        database,
        RoomFinancialProfileStore(database, audit),
        File(context.filesDir, names.restoreFolder),
        File(context.filesDir, names.resetFolder),
        context.getDatabasePath(names.setAsideDatabase)
    )
}

/**
 * For a database file that cannot be opened, once its storage is closed: moves it to
 * its set-aside name (`sello-unreadable.db` for the app's own) in the same private
 * folder so the next open starts an empty one. The file is kept, not deleted, because
 * a file this build cannot read may be intact: one written by a newer version reads
 * again once the app is updated. A restore staged for the file that moved belongs to
 * it, so its private folder is cleared; the next store starts with none.
 */
fun setAsideFinancialStorage(
    context: Context,
    names: StorageNames = StorageNames.Normal
): Outcome<Unit, StorageFailure> = setAside(
    context.getDatabasePath(names.database),
    context.getDatabasePath(names.setAsideDatabase)
).also { moved ->
    if (moved is Outcome.Success) File(context.filesDir, names.restoreFolder).deleteRecursively()
}
