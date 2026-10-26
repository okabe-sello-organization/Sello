package com.software.sello.feature.goals

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
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.navigation.GOAL_EDIT_TAG
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stamps and Marcar como lograda in the installed app over its real database: an aporte
 * that crosses 75 % is celebrated on the goal and, from Metas, on a paper ticket; a new
 * target that reaches a place is celebrated too; at 100 % the goal is marked lograda,
 * Deshacer puts it back, and once the window is over the goal stays lograda.
 */
@RunWith(AndroidJUnit4::class)
class GoalCelebrationJourneyTest {
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

    private fun openMetas() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Metas")
        rule.onNodeWithText("Metas").performClick()
        rule.awaitTag("metas.goal.$viaje")
    }

    private fun openGoal() {
        openMetas()
        rule.onNodeWithTag("metas.goal.$viaje").performClick()
        rule.awaitTag("goal.hero")
    }

    private fun keys(vararg labels: String) = labels.forEach { label ->
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(GOAL_MOVEMENT_KEYPAD_TAG)))
            .press()
    }

    private fun completed() =
        TestData.long("SELECT COUNT(*) FROM goal WHERE completed_on IS NOT NULL")

    @Test
    fun mt02AnAporteThatCrossesSeventyFivePercentIsCelebratedOnTheGoal() {
        openGoal()
        rule.onNodeWithContentDescription("Aportar a esta meta").performClick()
        rule.awaitTag(GOAL_MOVEMENT_KEYPAD_TAG)
        keys("6", "0", "0", "000")
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).press()

        // 1.710.000 + 600.000 = 2.310.000 of 3.000.000: 77 %, past the 75 % place.
        rule.awaitText("Aporte anotado: 600.000. Ganaste el sello del 75 %.")
        rule.awaitText("77 %")
        rule.onNode(
            hasContentDescription("75 por ciento, logrado") and
                hasAnyAncestor(hasTestTag("goal.hero"))
        ).assertExists()
    }

    @Test
    fun fromMetasTheStampLandsOnAPaperTicket() {
        openMetas()
        rule.onNodeWithText("Aportar a una meta").performClick()
        rule.awaitTag(GOAL_MOVEMENT_KEYPAD_TAG)
        rule.onNodeWithTag("goal.movement.goal.$viaje").performScrollTo().press()
        keys("6", "0", "0", "000")
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).press()

        rule.awaitTag(CELEBRATION_TICKET_TAG)
        rule.onNode(hasTestTag(CORRECTION_NOTICE_TAG)).assertExists()
        rule.awaitText("Ganaste el sello del 75 %")
    }

    @Test
    fun mt18ANewTargetThatReachesSeventyFivePercentIsCelebrated() {
        openGoal()
        rule.onNodeWithTag(GOAL_EDIT_TAG).performClick()
        rule.awaitTag(GOAL_EDITOR_TAG)
        repeat(7) {
            rule.onNode(
                hasContentDescription("Borrar") and hasAnyAncestor(hasTestTag(GOAL_EDITOR_PAD_TAG))
            ).press()
        }
        listOf("2", "000", "000").forEach {
            rule.onNode(hasText(it) and hasAnyAncestor(hasTestTag(GOAL_EDITOR_PAD_TAG))).press()
        }
        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).press()

        // 1.710.000 of 2.000.000 is 85 %.
        rule.awaitText("Meta actualizada. Ganaste el sello del 75 %.")
        rule.awaitText("85 %")
    }

    @Test
    fun mt23AndMt24MarcarComoLogradaCelebratesAndDeshacerPutsItBackAt100() {
        TestCommands.recordGoalMovement(viaje, 1_350_000)
        openGoal()
        rule.onNodeWithTag(GOAL_MARK_COMPLETE_TAG).performScrollTo().press()

        rule.awaitText("¡Lo lograste!")
        // The plate: 1.710.000 + 1.350.000 = 3.060.000 saved in two aportes.
        rule.onNodeWithText("META LOGRADA").assertExists()
        rule.onNodeWithText("· 2 APORTES", substring = true).assertExists()
        assertEquals(1L, completed())
        rule.onNodeWithText("Listo").press()

        rule.awaitText("Viaje a la costa, meta lograda.")
        rule.onNodeWithText("Deshacer").press()
        rule.awaitText("Viaje a la costa vuelve a estar al 100 %, lista para marcar.")
        rule.awaitTag(GOAL_MARK_COMPLETE_TAG)
        assertEquals(0L, completed())
    }

    @Test
    fun mt26AGoalReachedOutsideSelloIsMarkedFromEditAt57Percent() {
        openGoal()
        rule.onNodeWithTag(GOAL_EDIT_TAG).performClick()
        rule.awaitTag(GOAL_EDITOR_TAG)
        rule.onNodeWithText("¿Ya la lograste?").performScrollTo()
        rule.onNodeWithTag(GOAL_EDITOR_MARK_COMPLETE_TAG).performScrollTo().press()

        rule.awaitText("¡Lo lograste!")
        rule.onNodeWithText("· 1 APORTE", substring = true).assertExists()
        assertEquals(1L, completed())
        rule.onNodeWithText("Listo").press()

        // The form ends; the goal is lograda with what was saved, 1.710.000.
        rule.awaitText("Viaje a la costa, meta lograda.")
        rule.onNodeWithTag(GOAL_EDITOR_TAG).assertDoesNotExist()
        assertEquals(
            listOf("1710000"),
            TestData.rows("SELECT amount_minor FROM goal_event WHERE kind = 'contribution'")
        )
    }

    @Test
    fun afterTheDeshacerWindowTheGoalStaysLogradaAndReadOnly() {
        TestCommands.recordGoalMovement(viaje, 1_350_000)
        openGoal()
        rule.onNodeWithTag(GOAL_MARK_COMPLETE_TAG).performScrollTo().press()
        rule.awaitText("¡Lo lograste!")
        rule.onNodeWithText("Listo").press()
        rule.awaitText("Viaje a la costa, meta lograda.")

        // The offer ends with the window the command gave, about fourteen seconds.
        rule.waitUntil(30_000) {
            rule.onAllNodes(hasText("Deshacer"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("goal.withdraw").assertDoesNotExist()
        rule.onNodeWithTag(GOAL_MARK_COMPLETE_TAG).assertDoesNotExist()
        assertEquals(1L, TestData.count("goal"))
        assertEquals(1L, completed())
    }
}
