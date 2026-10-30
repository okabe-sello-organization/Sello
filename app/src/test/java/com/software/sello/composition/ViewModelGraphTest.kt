package com.software.sello.composition

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.StorageNames
import com.software.sello.domain.model.Outcome
import com.software.sello.feature.investments.InvestmentDetailViewModel
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication
import org.robolectric.Robolectric

/**
 * Every view model the graph registers is built through [viewModels], as a screen asks
 * for it, on the production modules and real Room. A binding that is missing, or a
 * constructor argument that cannot be resolved by type, fails here instead of when the
 * screen first opens on a device.
 */
@RunWith(AndroidJUnit4::class)
class ViewModelGraphTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @OptIn(KoinInternalApi::class)
    private fun registeredViewModels() = presentationModule().mappings.values
        .map { it.beanDefinition.primaryType }
        .filter { ViewModel::class.java.isAssignableFrom(it.java) }
        .map { it.java.asSubclass(ViewModel::class.java) }

    @Test
    fun everyRegisteredViewModelIsBuiltFromTheProductionGraph() {
        val names = StorageNames("view-model-graph.db", "view-model-graph")
        val opened = runBlocking {
            openStorage(
                context,
                ZoneId.of("America/Bogota"),
                SystemDispatcherProvider(),
                SystemAuditClock(),
                names
            )
        } as Outcome.Success
        val host = HostSubstitutes()
        val application = koinApplication {
            allowOverride(true)
            modules(
                productionModules(
                    context,
                    initializedZone(),
                    opened.value.storage,
                    names
                ) + host.module
            )
        }
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // What navigation puts in an entry's saved state: the one argument a detail needs.
        val extras = MutableCreationExtras(activity.defaultViewModelCreationExtras).apply {
            this[DEFAULT_ARGS_KEY] =
                bundleOf(InvestmentDetailViewModel.KEY_INVESTMENT to "investment")
        }
        val provider = ViewModelProvider.create(
            activity.viewModelStore,
            viewModels(application.koin),
            extras
        )
        try {
            val types = registeredViewModels()
            assertTrue("no view model is registered", types.isNotEmpty())
            types.forEach { type ->
                assertEquals(type, provider[type]::class.java)
            }
        } finally {
            activity.viewModelStore.clear()
            application.close()
        }
    }
}
