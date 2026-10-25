package com.software.sello.feature.goals

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.navigation.CORRECTION_NOTICE_TAG
import com.software.sello.navigation.MonthSession
import com.software.sello.press
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

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
    fun mt28ToMt30TheChoiceWinsUntilThatGoalIsCompletedThenTheBestOneIsBack() {
        openRecibo()
        // MT28: no choice, A at 57 % beats B at 37 %.
        rule.awaitDescription("Viaje a la costa, 57 por ciento. Ver meta")
        val before = answer()

        // MT29: B chosen on its page.
        rule.onNodeWithText("Metas").performClick()
        rule.awaitTag("metas.goal.$celular")
        rule.onNodeWithTag("metas.goal.$celular").performClick()
        rule.awaitTag("goal.pin")
        rule.onNodeWithTag("goal.pin").performClick()
        rule.awaitText("En tu Recibo")
        Espresso.pressBack()
        rule.awaitTag("metas.goal.$celular")
        rule.onNodeWithText("Recibo").performClick()
        rule.awaitDescription("Celular nuevo, 37 por ciento. Ver meta")

        // MT30: B completed, A again, with nothing for B on Recibo.
        TestCommands.completeGoal(celular)
        rule.awaitDescription("Viaje a la costa, 57 por ciento. Ver meta")
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasContentDescription("Celular nuevo", substring = true))
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals(before, answer())
    }

    @Test
    fun withNoCategoriesYetTheFirstUseScreenStillShowsTheGoal() {
        TestData.reset()
        val fondo = TestCommands.createGoal("Fondo", 1_000_000)
        TestCommands.recordGoalMovement(fondo, 250_000)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        rule.awaitText("Dale forma a tu mes")
        rule.awaitDescription("Fondo, 25 por ciento. Ver meta")
    }

    @Test
    fun onAPastMonthNoGoalIsShown() {
        openRecibo()
        rule.awaitTag(RECIBO_GOAL_TAG)

        GlobalContext.get().get<MonthSession>().select(
            YearMonth.from(TestCommands.today).minusMonths(1)
        )

        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag(RECIBO_GOAL_TAG)).fetchSemanticsNodes().isEmpty()
        }
        TestCommands.showCurrentMonth()
        rule.awaitTag(RECIBO_GOAL_TAG)
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

    @Test
    fun anotherGoalAt100PercentIsMarkedLogradaFromItsGreenRow() {
        // Viaje chosen for Recibo; Celular reaches 100 % beside it.
        TestCommands.featureGoal(viaje)
        TestCommands.recordGoalMovement(celular, 750_000)
        openRecibo()
        rule.awaitTag(RECIBO_GOAL_REACHED_TAG)
        rule.onNodeWithText("Llegó al 100 %").assertExists()
        rule.onNodeWithTag(RECIBO_GOAL_REACHED_TAG).performScrollTo()

        rule.onNode(
            hasText("Marcar como lograda") and hasAnyAncestor(hasTestTag(RECIBO_GOAL_REACHED_TAG))
        ).press()

        rule.awaitText("¡Lo lograste!")
        assertEquals(
            1L,
            TestData.long(
                "SELECT COUNT(*) FROM goal WHERE id = '$celular' AND completed_on IS NOT NULL"
            )
        )
        rule.onNodeWithText("Listo").press()
        rule.awaitText("Celular nuevo, meta lograda.")
        rule.onNodeWithTag(RECIBO_GOAL_REACHED_TAG).assertDoesNotExist()
        rule.awaitDescription("Viaje a la costa, 57 por ciento. Ver meta")
    }
}
