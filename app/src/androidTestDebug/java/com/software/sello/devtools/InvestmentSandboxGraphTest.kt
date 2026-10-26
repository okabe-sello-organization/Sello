package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In the sandbox's real graph, investment and rate writes go through the fence, faults
 * and log like every other financial write, investment reads share the read-failure
 * control, and the diagnostic finds an investment write's receipt among the investments.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentSandboxGraphTest {
    private val sandbox = SandboxGraph(
        SandboxSession(
            active = true,
            date = LocalDate.of(2026, 10, 20),
            zone = ZoneId.of("America/Bogota"),
            scenario = Scenarios.NoValue.key
        )
    )

    @After
    fun end() = sandbox.erase()

    @Test
    fun theSandboxGraphFencesInvestmentWritesAndCanFailInvestmentReads() {
        sandbox.erase()
        sandbox.open()

        val commands = sandbox.koin.get<InvestmentCommands>()
        val reads = sandbox.koin.get<InvestmentReads>()

        assertTrue("$commands", commands is FencedInvestmentCommands)
        assertTrue("$reads", reads is FaultyInvestmentReads)
    }

    @Test
    fun aScenarioInvestmentWriteIsLoggedAndItsReceiptIsFound() {
        sandbox.erase()
        sandbox.open()
        val tools = checkNotNull(sandbox.tools)
        val runner = checkNotNull(tools.scenarios)
        val diagnostics = checkNotNull(tools.diagnostics)

        assertTrue(runBlocking { runner.step() })

        val logged = diagnostics.log.state.value.single()
        assertEquals("Inversión: CreateInvestment" to "Guardado", logged.kind to logged.told)
        val stored = runBlocking { diagnostics.inspector.stored(logged) }
        assertTrue("$stored", stored is StoredOutcome.Saved)
        val board = runBlocking { sandbox.koin.get<InvestmentReads>().board() }
        assertEquals(1, (board as Outcome.Success).value.investments.size)
    }
}
