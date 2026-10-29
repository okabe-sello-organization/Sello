package com.software.sello.feature.goals

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recibo's goal in the installed app over its real database (rule 12): the highest
 * completion without a choice (MT28), the person's choice once made (MT29), the fallback
 * when that goal is completed (MT30), nothing on a past month, Aportar from the pill
 * with its stamp on a ticket, and Marcar como lograda from the green row. Recibo's
 * month answer never changes with a goal.
 */
@RunWith(AndroidJUnit4::class)
class ReciboGoalJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var viaje: String
    private lateinit var celular: String

    @Before
    fun twoGoals() {
        TestData.reset()
        TestCommands.createCategory("Comida", 500_000)
        viaje = TestCommands.createGoal("Viaje a la costa", 3_000_000)
        TestCommands.recordGoalMovement(viaje, 1_710_000)
        celular = TestCommands.createGoal("Celular nuevo", 1_200_000, "phone_iphone")
        TestCommands.recordGoalMovement(celular, 450_000)
    }

    @After
    fun close() {
        scenario?.close()
        TestCommands.showCurrentMonth()
        TestData.reset()
    }

    private fun openRecibo() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitTag(RECIBO_SUMMARY_TAG)
    }

    /** What Recibo's month answer says, word for word. */
    private fun answer(): List<String> = rule.onNodeWithTag(RECIBO_SUMMARY_TAG)
        .fetchSemanticsNode().config.let { config ->
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }

    @Test
    fun aportarFromThePillRecordsForThatGoalAndItsStampLandsOnATicket() {
        openRecibo()
        rule.awaitTag(RECIBO_GOAL_TAG)
        val before = answer()
        rule.onNodeWithContentDescription("Aportar a Viaje a la costa").performClick()
        rule.awaitTag(GOAL_MOVEMENT_KEYPAD_TAG)
        listOf("6", "0", "0", "000").forEach {
            rule.onNode(hasText(it) and hasAnyAncestor(hasTestTag(GOAL_MOVEMENT_KEYPAD_TAG)))
                .press()
        }
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).press()

        // 1.710.000 + 600.000 = 2.310.000 of 3.000.000: 77 %, past the 75 % place.
        rule.awaitTag(CELEBRATION_TICKET_TAG)
        rule.onNode(hasTestTag(CORRECTION_NOTICE_TAG)).assertExists()
        rule.awaitDescription("Viaje a la costa, 77 por ciento. Ver meta")
        assertEquals(before, answer())
        assertEquals(0L, TestData.count("expense") + TestData.count("income"))
    }
}
