package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In the sandbox's real graph, goal writes go through the fence, faults and log like
 * every other financial write, and goal reads share the read-failure control.
 */
@RunWith(AndroidJUnit4::class)
class GoalSandboxGraphTest {
    private val sandbox = SandboxGraph(
        SandboxSession(
            active = true,
            date = LocalDate.of(2026, 10, 23),
            zone = ZoneId.of("America/Bogota")
        )
    )

    @After
    fun end() = sandbox.erase()

    @Test
    fun theSandboxGraphFencesGoalWritesAndCanFailGoalReads() {
        sandbox.erase()
        sandbox.open()

        val commands = sandbox.koin.get<GoalCommands>()
        val reads = sandbox.koin.get<GoalReads>()

        assertTrue("$commands", commands is FencedGoalCommands)
        assertTrue("$reads", reads is FaultyGoalReads)
    }
}
