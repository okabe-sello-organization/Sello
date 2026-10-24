package com.software.sello.feature.goals

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aportar, Retirar and corrections in the installed app over its real database: an
 * aporte from the goal raises it at once, a retiro above what is saved waits, and a
 * movement is corrected, deleted and put back with Deshacer. No budget or income row is
 * ever written.
 */
@RunWith(AndroidJUnit4::class)
class GoalMovementJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var viaje: String

    @Before
    fun oneGoal() {
        TestData.reset()
        viaje = TestCommands.createGoal("Viaje a la costa", 3_000_000)
        TestCommands.recordGoalMovement(viaje, 1_710_000)
    }

    @After
    fun close() {
        scenario?.close()
        TestData.reset()
    }

    private fun openGoal() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Metas")
        rule.onNodeWithText("Metas").performClick()
        rule.awaitTag("metas.goal.$viaje")
        rule.onNodeWithTag("metas.goal.$viaje").performClick()
        rule.awaitTag("goal.hero")
    }

    private fun keys(vararg labels: String) = labels.forEach { label ->
        val key = if (label == "<") hasContentDescription("Borrar") else hasText(label)
        rule.onNode(key and hasAnyAncestor(hasTestTag(GOAL_MOVEMENT_KEYPAD_TAG))).press()
    }

    private fun amounts() =
        TestData.rows("SELECT kind, amount_minor FROM goal_event ORDER BY sequence").drop(1)

    @Test
    fun anAporteFromTheGoalSurvivesRecreationAndRaisesTheGoalAtOnce() {
        openGoal()
        rule.onNodeWithContentDescription("Aportar a esta meta").performClick()
        rule.awaitTag(GOAL_MOVEMENT_KEYPAD_TAG)
        rule.onNodeWithText("Llevas 1.710.000 guardados en esta meta.").assertExists()
        keys("3", "5", "0", "000")

        scenario?.recreate()
        rule.awaitTag(GOAL_MOVEMENT_KEYPAD_TAG)
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).press()

        // 1.710.000 + 350.000 = 2.060.000 of 3.000.000: 68 %.
        rule.awaitText("Aporte anotado: 350.000")
        rule.awaitText("68 %")
        assertEquals(listOf("contribution|1710000", "contribution|350000"), amounts())
        assertEquals(0L, TestData.count("expense") + TestData.count("income"))
        assertEquals(0L, TestData.count("month_limit") + TestData.count("default_limit"))
    }

    @Test
    fun mt08ARetiroAboveWhatIsSavedWaitsAndAllThatIsSavedCanBeWithdrawn() {
        openGoal()
        rule.onNodeWithTag("goal.withdraw").performScrollTo().performClick()
        rule.awaitTag(GOAL_MOVEMENT_KEYPAD_TAG)
        keys("1", "7", "1", "0", "0", "0", "1")

        rule.awaitText("Más de lo guardado (1.710.000)")
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
        keys("<", "0")
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).press()

        rule.awaitText("Retiro anotado: 1.710.000")
        rule.awaitText("0 %")
        assertEquals(listOf("contribution|1710000", "withdrawal|1710000"), amounts())
    }

    @Test
    fun aMovementIsCorrectedThenDeletedAndPutBackWithDeshacer() {
        val aporte = TestData.rows("SELECT id FROM goal_event WHERE kind = 'contribution'").single()
        openGoal()
        rule.onNodeWithTag("goal.movement.$aporte").performScrollTo().performClick()
        rule.awaitText("Corregir aporte")
        keys("<", "<", "<", "<", "<", "<", "<", "1", "5", "0", "0", "000")
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).press()

        rule.awaitText("Movimiento actualizado")
        rule.awaitDescription("1.500.000 pesos")
        assertEquals(listOf("contribution|1500000"), amounts())

        rule.onNodeWithTag("goal.movement.$aporte").performScrollTo().performClick()
        rule.awaitText("Corregir aporte")
        rule.onNodeWithTag(GOAL_MOVEMENT_DELETE_TAG).press()
        rule.awaitText("Movimiento borrado")
        assertEquals(emptyList<String>(), amounts())

        rule.onNodeWithText("Deshacer").press()
        rule.awaitText("Movimiento restaurado")
        assertEquals(listOf("contribution|1500000"), amounts())
        rule.awaitDescription("1.500.000 pesos")
    }
}
