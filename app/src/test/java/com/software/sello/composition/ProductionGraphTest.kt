package com.software.sello.composition

import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.platform.ApplicationScope
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.RecordingTimeSignals
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemFinancialClock
import com.software.sello.platform.SystemMonotonicClock
import com.software.sello.platform.TimeSignals
import com.software.sello.testing.TestDispatcherProvider
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

/** Replaces only what needs a device: Android broadcasts and real dispatchers. */
@OptIn(ExperimentalCoroutinesApi::class)
class HostSubstitutes {
    val scheduler = TestCoroutineScheduler()
    val signals = RecordingTimeSignals()
    val module: Module = module {
        single<TimeSignals> { signals }
        single<DispatcherProvider> { TestDispatcherProvider(scheduler) }
    }
}

fun initializedZone(): ProcessFinancialZone = ProcessFinancialZone().apply {
    initialize(ZoneId.of("America/Bogota"))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionGraphTest {
    private val host = HostSubstitutes()

    private fun start(vararg modules: Module): KoinApplication = koinApplication {
        allowOverride(true)
        modules(*modules)
    }

    @Test
    fun everyProductionDefinitionHasItsCollaborators() {
        platformModule(initializedZone()).verify(extraTypes = listOf(TimeSignals::class))
    }

    @Test
    fun productionGraphBuildsWithRealClocks() {
        val application = start(platformModule(initializedZone()), host.module)
        host.scheduler.runCurrent()
        val koin = application.koin

        assertTrue(koin.get<FinancialClock>() is SystemFinancialClock)
        assertTrue(koin.get<AuditClock>() is SystemAuditClock)
        assertTrue(koin.get<MonotonicClock>() is SystemMonotonicClock)
        assertEquals(ZoneId.of("America/Bogota"), koin.get<FinancialClock>().today.value.zone)
        application.close()
    }

    @Test
    fun financialClockIsCreatedWithTheGraphAndIsASingleInstance() {
        val application = start(platformModule(initializedZone()), host.module)
        host.scheduler.runCurrent()

        assertEquals(1, host.signals.subscriptions)
        assertSame(application.koin.get<FinancialClock>(), application.koin.get<FinancialClock>())
        application.close()
    }

    @Test
    fun closingTheGraphCancelsTheApplicationScopeAndItsObservers() {
        val application = start(platformModule(initializedZone()), host.module)
        host.scheduler.runCurrent()
        val scope = application.koin.get<ApplicationScope>()

        application.close()
        host.scheduler.runCurrent()

        assertFalse(scope.isActive)
        assertEquals(1, host.signals.cancellations)
    }

    @Test
    fun graphWithoutAnInitializedFinancialZoneFailsAtConstruction() {
        val failure = assertThrows(Exception::class.java) {
            start(platformModule(ProcessFinancialZone()), host.module)
        }

        assertTrue(
            generateSequence<Throwable>(failure) {
                it.cause
            }.any { it is IllegalStateException }
        )
    }

    @Test
    fun missingRequiredBindingFailsGraphConstruction() {
        val withoutAuditClock = module {
            single { initializedZone() }
            single<FinancialClock>(createdAtStart = true) {
                SystemFinancialClock(get(), get(), get(), get())
            }
        }

        val failure = assertThrows(Exception::class.java) { start(withoutAuditClock, host.module) }

        assertTrue(
            generateSequence<Throwable>(failure) { it.cause }.any {
                "AuditClock" in
                    it.message.orEmpty()
            }
        )
    }
}
