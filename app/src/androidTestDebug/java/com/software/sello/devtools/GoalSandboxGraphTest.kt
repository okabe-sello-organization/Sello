package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.port.GoalCommands
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** In the sandbox's real graph, goal writes go through the fence like every other financial write. */
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
    fun theSandboxGraphFencesGoalWrites() {
        sandbox.erase()
        sandbox.open()

        val commands = sandbox.koin.get<GoalCommands>()

        assertTrue("$commands", commands is FencedGoalCommands)
    }
}
