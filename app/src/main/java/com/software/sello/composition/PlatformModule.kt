package com.software.sello.composition

import android.content.Context
import com.software.sello.data.FinancialStorage
import com.software.sello.data.StorageNames
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.DocumentReader
import com.software.sello.domain.port.DocumentWriter
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.platform.AndroidDocumentReader
import com.software.sello.platform.AndroidDocumentWriter
import com.software.sello.platform.AndroidScreenReader
import com.software.sello.platform.AndroidTimeSignals
import com.software.sello.platform.ApplicationScope
import com.software.sello.platform.ProcessAppRestart
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.RandomRecordIds
import com.software.sello.platform.ScreenReader
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import com.software.sello.platform.SystemFinancialClock
import com.software.sello.platform.SystemMonotonicClock
import com.software.sello.platform.TimeSignals
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Time, dispatchers and lifetime. Every collaborator is a required constructor
 * parameter and the financial clock is created with the graph, so a missing
 * binding fails at startup, not at first use.
 *
 * [financialZone] must already be initialized by the caller with the zone stored in
 * the database (see [openStorage]). [TimeSignals] comes from
 * [androidModule] in the app and from a substitute in host tests.
 */
fun platformModule(financialZone: ProcessFinancialZone): Module = module {
    single { financialZone }
    singleOf(::SystemDispatcherProvider) { bind<DispatcherProvider>() }
    singleOf(::ApplicationScope) {
        bind<CoroutineScope>()
        onClose { it?.close() }
    }
    singleOf(::SystemAuditClock) { bind<AuditClock>() }
    singleOf(::SystemMonotonicClock) { bind<MonotonicClock>() }
    singleOf(::RandomRecordIds) { bind<RecordIdSource>() }
    // Bound by its contract only, so a debug override replaces the whole definition
    // and the production observer is never started alongside a simulated clock.
    single<FinancialClock>(createdAtStart = true) {
        SystemFinancialClock(get(), get(), get(), get())
    }
}

/** Adapters that need the Android application context. */
fun androidModule(context: Context): Module = module {
    single<Context> { context }
    singleOf(::AndroidTimeSignals) { bind<TimeSignals>() }
    single<DocumentWriter> { AndroidDocumentWriter(context.contentResolver, get()) }
    single<DocumentReader> { AndroidDocumentReader(context.contentResolver, get()) }
    single<AppRestart> { ProcessAppRestart(context) }
    single<ScreenReader> { AndroidScreenReader(context) }
}

fun productionModules(
    context: Context,
    financialZone: ProcessFinancialZone,
    storage: FinancialStorage,
    names: StorageNames = StorageNames.Normal
): List<Module> = listOf(
    androidModule(context),
    platformModule(financialZone),
    storageModule(storage),
    preferencesModule(names.preferences),
    presentationModule()
)
