package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleCommands
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** In the sandbox's real graph, schedule commands and catch-up go through the fence. */
@RunWith(AndroidJUnit4::class)
class ScheduleSandboxGraphTest {
    private val sandbox = SandboxGraph(
        SandboxSession(
            active = true,
            date = LocalDate.of(2026, 10, 25),
            zone = ZoneId.of("America/Bogota")
        )
    )

    @After
    fun end() = sandbox.erase()

    @Test
    fun theSandboxGraphFencesScheduleCommandsAndCatchUp() {
        sandbox.erase()
        sandbox.open()

        val commands = sandbox.koin.get<ScheduleCommands>()
        val catchUp = sandbox.koin.get<ScheduleCatchUpStore>()

        assertTrue("$commands", commands is FencedScheduleCommands)
        assertTrue("$catchUp", catchUp is FencedScheduleCatchUp)
    }
}
