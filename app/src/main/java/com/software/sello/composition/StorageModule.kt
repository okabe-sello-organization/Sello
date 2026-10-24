package com.software.sello.composition

import android.content.Context
import com.software.sello.data.FinancialStorage
import com.software.sello.data.StorageNames
import com.software.sello.data.backup.BackupBounds
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.check
import com.software.sello.data.openFinancialStorage
import com.software.sello.data.setAsideFinancialStorage
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryExpenseSearch
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.DocumentRead
import com.software.sello.domain.port.DocumentReader
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.FinancialProfileStore
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.Resets
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.platform.deviceZone
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Open storage and the profile it holds, established before the graph is built. */
class OpenedStorage(val storage: FinancialStorage, val profile: FinancialProfile)

/**
 * Opens the database and establishes the profile: stored on first use with
 * [deviceZone], read back on every later start. It waits for that one small read
 * because the financial zone must be known before anything computes a date.
 *
 * A database that cannot be read is a failure with the storage closed again. It is
 * never replaced by an empty one and the device zone is never used in its place.
 */
fun openStorage(
    context: Context,
    deviceZone: ZoneId,
    dispatchers: DispatcherProvider,
    audit: AuditClock,
    names: StorageNames = StorageNames.Normal
): Outcome<OpenedStorage, StorageFailure> {
    val storage = openFinancialStorage(context, dispatchers.io, audit, names)
    val opened = runBlocking {
        when (val profile = storage.profiles.establish(deviceZone)) {
            is Outcome.Failure -> profile

            // Whatever an earlier process kept to undo a deletion can no longer be undone.
            is Outcome.Success -> when (val discarded = storage.discardUndoCopies()) {
                is Outcome.Failure -> discarded
                is Outcome.Success -> Outcome.Success(OpenedStorage(storage, profile.value))
            }
        }
    }
    if (opened is Outcome.Failure) storage.close()
    return opened
}

/** Keeps the file that could not be opened under another name; see `:data`. */
fun setAsideStorage(
    context: Context,
    names: StorageNames = StorageNames.Normal
): Outcome<Unit, StorageFailure> = setAsideFinancialStorage(context, names)

/** Ports backed by the database. Closing the graph closes the database. */
fun storageModule(storage: FinancialStorage): Module = module {
    single { storage } onClose { it?.close() }
    single<FinancialProfileStore> { storage.profiles }
    single<ExpenseCommands> { storage.expenseCommands(get(), get(), get(), get()) }
    single<CategoryCommands> { storage.categoryCommands(get(), get(), get()) }
    single<CategoryReads> { storage.categoryReads }
    single<CategoryInsightsSnapshots> {
        storage.categoryInsights(get(), get<DispatcherProvider>().default)
    }
    single<CategoryExpenseSearch> { storage.categoryExpenseSearch }
    single<MonthlySnapshots> {
        storage.monthlySnapshots(get(), get<DispatcherProvider>().default)
    }
    single<ResumenSnapshots> {
        storage.resumenSnapshots(get(), get<DispatcherProvider>().default)
    }
    single<ExpenseReads> { storage.expenseReads }
    single<IncomeCommands> { storage.incomeCommands(get(), get(), get(), get()) }
    single<IncomeReads> { storage.incomeReads }
    single<GoalCommands> { storage.goalCommands(get(), get(), get(), get()) }
    single<GoalReads> { storage.goalReads }
    single<ScheduleCommands> { storage.scheduleCommands(get(), get(), get()) }
    single<ScheduleReads> { storage.scheduleReads }
    single<ScheduleCatchUpStore> { storage.scheduleCatchUp(get(), get()) }
    single<BackupExports> { storage.backupExports(get(), get(), get(), get()) }
    single<Restores> { storage.restores(get(), get(), get(), get(), get()) }
    // A new history after an erase takes the device's zone, read when the erase commits.
    single<Resets> { storage.resets(get(), ::deviceZone, get(), get()) }
}

/**
 * Reads a chosen document and checks it whole as a backup, before any storage is open:
 * how the start-up failure screen learns what a file would restore. At most
 * [maxBytes] plus one are read, and the check runs off the main thread.
 */
suspend fun readStartupBackup(
    reader: DocumentReader,
    source: DocumentSource,
    dispatchers: DispatcherProvider,
    maxBytes: Int = BackupBounds().maxBytes
): Outcome<ChosenBackup, RestoreRejection> = when (val read = reader.read(source, maxBytes)) {
    DocumentRead.Failed -> Outcome.Failure(RestoreRejection.Unreadable)

    is DocumentRead.TooLarge -> Outcome.Failure(RestoreRejection.TooLarge)

    is DocumentRead.Read -> when (
        val checked = withContext(dispatchers.default) { BackupCodec().check(read.bytes) }
    ) {
        is Outcome.Success -> Outcome.Success(ChosenBackup(read.bytes, checked.value))
        is Outcome.Failure -> checked
    }
}
