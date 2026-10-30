package com.software.sello.composition

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.SelloTestRunner
import com.software.sello.TestCommands
import com.software.sello.data.StorageNames
import com.software.sello.devtools.endProcess
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import com.software.sello.platform.SystemFinancialClock
import com.software.sello.platform.TimeSignals
import java.time.ZoneId
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinApplication

/**
 * The installed app's graph runs under the device tests' held financial day (see
 * [com.software.sello.SelloTestRunner]); what release builds use is checked on a graph
 * built as a release build builds it, on its own data.
 */
@RunWith(AndroidJUnit4::class)
class ProductionGraphOnDeviceTest {
    private val koin get() = GlobalContext.get()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val names = StorageNames("production-graph.db", "production-graph-preferences")

    private var release: KoinApplication? = null

    @After
    fun eraseTheReleaseGraphsData() {
        release?.endProcess()
        eraseFinancialStorage(context, names)
    }

    /** The production modules and [ProductionComposition] on new data, as a release build starts. */
    private fun releaseGraph(): Koin {
        eraseFinancialStorage(context, names)
        val opened = runBlocking {
            openStorage(
                context,
                ZoneId.systemDefault(),
                SystemDispatcherProvider(),
                SystemAuditClock(),
                names
            )
        } as Outcome.Success
        val zone = ProcessFinancialZone().apply { initialize(opened.value.profile.zone) }
        return koinApplication {
            modules(
                productionModules(context, zone, opened.value.storage, names) +
                    ProductionComposition.modules(opened.value.profile)
            )
        }.also { release = it }.koin
    }

    @Test
    fun theAppIsSelloApplicationAndAReleaseGraphRunsOnRealFinancialTime() {
        assertTrue(context.applicationContext is SelloApplication)
        assertTrue(releaseGraph().get<FinancialClock>() is SystemFinancialClock)
    }

    @Test
    fun deviceTestsRunOnTheHeldFinancialDay() {
        assertEquals(SelloTestRunner.TEST_DAY, koin.get<FinancialClock>().today.value.date)
        assertEquals(SelloTestRunner.TEST_DAY, TestCommands.today)
    }

    @Test
    fun monotonicClockMovesForward() {
        val monotonic = koin.get<MonotonicClock>()
        val first = monotonic.elapsed()

        assertTrue(first > Duration.ZERO)
        assertTrue(monotonic.elapsed() >= first)
    }

    @Test
    fun bringingTheAppToTheForegroundEmitsATimeSignal() {
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking { withTimeout(10_000) { koin.get<TimeSignals>().changes().first() } }
        }
    }
}
