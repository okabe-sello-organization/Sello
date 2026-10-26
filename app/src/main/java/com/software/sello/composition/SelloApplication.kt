package com.software.sello.composition

import android.app.Application
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ExportShares
import com.software.sello.platform.AndroidDocumentReader
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import com.software.sello.platform.deviceZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

class SelloApplication : Application() {
    /** Whether the data opened, and with it how every screen's view model is built. */
    lateinit var startup: Startup
        private set

    /** Production in a release build; the debug build may choose a sandbox here. */
    private lateinit var variant: VariantComposition

    override fun onCreate() {
        super.onCreate()
        variant = variantComposition(this)
        val dispatchers = SystemDispatcherProvider()
        val documents = AndroidDocumentReader(contentResolver, dispatchers)
        startup = Startup(
            start = ::openAndBuildGraph,
            moveUnreadable = { setAsideStorage(applicationContext, variant.names) },
            readBackup = { readStartupBackup(documents, it, dispatchers) }
        )
    }

    /** The graph is started only on data that opened, so it is started at most once. */
    private fun openAndBuildGraph(): Outcome<Started, StorageFailure> {
        // The financial zone comes from the database: the device zone is stored on first
        // use and never read again, so a later trip does not move recorded days.
        val opened = openStorage(
            applicationContext,
            variant.newDataZone ?: deviceZone(),
            SystemDispatcherProvider(),
            SystemAuditClock(),
            variant.names
        )
        return when (opened) {
            is Outcome.Failure -> opened

            is Outcome.Success -> {
                val financialZone = ProcessFinancialZone().apply {
                    initialize(opened.value.profile.zone)
                }
                val application = startKoin {
                    modules(
                        productionModules(
                            applicationContext,
                            financialZone,
                            opened.value.storage,
                            variant.names
                        ) + variant.modules(opened.value.profile)
                    )
                }
                // Exports left for sharing by an earlier run go now (SELLO-121).
                val koin = application.koin
                koin.get<CoroutineScope>().launch { koin.get<ExportShares>().clear() }
                Outcome.Success(Started(viewModels(koin), koin.get()))
            }
        }
    }
}
