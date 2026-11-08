package com.software.sello.navigation

import com.software.sello.feature.goals.ReciboGoalPill
import com.software.sello.feature.goals.ReciboGoalState
import com.software.sello.feature.goals.ReciboReachedGoal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recibo's goal nudge may show only while its card takes turns among current goals (SELLO-213). */
class GoalCardTurnsTest {
    private val viaje = ReciboGoalPill("viaje", "Viaje a la costa", "flight", 57)
    private val fondo = ReciboGoalPill("fondo", "Fondo de emergencia", "home", 42)

    @Test
    fun twoGoalsTakingTurnsCount() {
        assertTrue(takesTurns(ReciboGoalState.Shown(listOf(viaje, fondo), reached = null)))
    }

    @Test
    fun aHiddenOrFailedCardDoesNot() {
        assertFalse(takesTurns(ReciboGoalState.Hidden))
        assertFalse(takesTurns(ReciboGoalState.Failed))
    }

    @Test
    fun oneGoalOrOnlyAReachedRowDoesNot() {
        assertFalse(takesTurns(ReciboGoalState.Shown(listOf(viaje), reached = null)))
        assertFalse(
            takesTurns(
                ReciboGoalState.Shown(
                    emptyList(),
                    reached = ReciboReachedGoal("fondo", "Fondo de emergencia", "home")
                )
            )
        )
    }

    @Test
    fun olderGoalsAfterAFailedReadDoNot() {
        assertFalse(
            takesTurns(ReciboGoalState.Shown(listOf(viaje, fondo), reached = null, stale = true))
        )
    }
}
