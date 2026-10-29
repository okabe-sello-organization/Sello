package com.software.sello.feature.goals

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.await
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.press
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Metas in the installed app, over its real database: goals written through the real
 * commands appear in the tab, best completion first, and open their detail; the star
 * chooses Recibo's goal; a completed goal is deleted after the confirm slip.
 */
@RunWith(AndroidJUnit4::class)
class MetasJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun emptyHistory() = TestData.reset()

    @After
    fun close() {
        scenario?.close()
        TestData.reset()
    }

    private fun openMetas() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Metas")
        rule.onNodeWithText("Metas").performClick()
        rule.awaitTag(METAS_LIST_TAG)
    }

    @Test
    fun goalsAppearBestFirstWithTheBandAndOpenTheirDetail() {
        val today = TestCommands.today
        val celular = TestCommands.createGoal("Celular nuevo", 1_200_000, "phone_iphone")
        TestCommands.recordGoalMovement(celular, 450_000, today)
        val viaje = TestCommands.createGoal(
            "Viaje a la costa",
            3_000_000,
            targetMonth = YearMonth.from(today).plusMonths(2)
        )
        TestCommands.recordGoalMovement(viaje, 750_000, today)
        TestCommands.recordGoalMovement(viaje, 960_000, today)
        val vacunas = TestCommands.createGoal("Vacunas de Luna", 400_000, "pets")
        TestCommands.recordGoalMovement(vacunas, 400_000, today)
        TestCommands.completeGoal(vacunas)

        openMetas()

        // 1.710.000 + 450.000, active goals only; all of it came in this month.
        rule.awaitTag("metas.goal.$viaje")
        rule.onNodeWithContentDescription("2.160.000 pesos", useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText("2.160.000 en ", substring = true, useUnmergedTree = true)
            .assertExists()
        val first = rule.onNodeWithTag("metas.goal.$viaje").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("metas.goal.$celular").getUnclippedBoundsInRoot()
        assertTrue("57 % before 37 %", first.bottom <= second.top)
        // Completed goals come last, under the active ones.
        rule.onNodeWithTag(
            METAS_LIST_TAG
        ).performScrollToNode(hasTestTag("metas.completed.$vacunas"))
        rule.onNodeWithContentDescription("Lograda", useUnmergedTree = true).assertExists()

        rule.onNodeWithTag(METAS_LIST_TAG).performScrollToNode(hasTestTag("metas.goal.$viaje"))
        rule.onNodeWithTag("metas.goal.$viaje").performClick()
        rule.awaitTag("goal.hero")
        rule.onNodeWithText("Viaje a la costa").assertExists()
        rule.onNodeWithText("57 %").assertExists()
        rule.onNodeWithText("2 de 4").assertExists()
        rule.onNodeWithText("faltan 1.290.000").assertExists()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertDoesNotExist()
        // Without a choice the best goal is Recibo's.
        rule.onNodeWithText("En tu Recibo").assertExists()

        Espresso.pressBack()
        rule.awaitTag(METAS_LIST_TAG)
        rule.onNodeWithTag(METAS_LIST_TAG).performScrollToNode(hasTestTag("metas.goal.$celular"))
        rule.onNodeWithTag("metas.goal.$celular").performClick()
        rule.awaitTag("goal.pin")
        rule.onNodeWithText("Mostrar en Recibo").press()
        rule.awaitText("En tu Recibo")
        assertEquals(listOf(celular), TestData.rows("SELECT goal_id FROM featured_goal"))

        // Choosing it survives recreation and is not sent again.
        scenario?.recreate()
        rule.awaitText("En tu Recibo")
        assertEquals(
            1L,
            TestData.long("SELECT COUNT(*) FROM operation_receipt WHERE kind = 'goal.feature'")
        )
        Espresso.pressBack()
        rule.awaitTag(METAS_LIST_TAG)

        // Back from the Metas tab returns to Recibo, from the real navigation stack.
        Espresso.pressBack()
        rule.await(hasText("Recibo"))
        rule.awaitTag(MONTH_SWITCHER_TAG)
        rule.onNodeWithText("Recibo").assertIsSelected()
    }

    @Test
    fun aCompletedGoalIsReadOnlyAndDeletedOnlyAfterTheConfirmSlip() {
        val vacunas = TestCommands.createGoal("Vacunas de Luna", 400_000, "pets")
        TestCommands.recordGoalMovement(vacunas, 400_000)
        TestCommands.completeGoal(vacunas)
        val other = TestCommands.createGoal("Fondo", 1_000_000)

        openMetas()
        rule.awaitTag("metas.goal.$other")
        rule.onNodeWithTag(
            METAS_LIST_TAG
        ).performScrollToNode(hasTestTag("metas.completed.$vacunas"))
        rule.onNodeWithTag("metas.completed.$vacunas").performClick()
        rule.awaitTag("goal.delete")
        rule.onNodeWithTag("goal.pin").assertDoesNotExist()
        rule.onNodeWithTag("goal.facts").assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assertDoesNotExist()

        rule.onNodeWithText("Borrar meta").performScrollTo().performClick()
        rule.awaitText("¿Borrar Vacunas de Luna?")
        // The question survives recreation; nothing is deleted yet.
        scenario?.recreate()
        rule.awaitText("¿Borrar Vacunas de Luna?")
        assertEquals(2L, TestData.count("goal"))
        rule.onNodeWithText("Borrar").press()

        rule.awaitTag("metas.goal.$other")
        rule.onNodeWithTag("metas.completed.$vacunas").assertDoesNotExist()
        assertEquals(listOf(other), TestData.rows("SELECT id FROM goal"))
        assertEquals(
            0L,
            TestData.long("SELECT COUNT(*) FROM goal_event WHERE goal_id = '$vacunas'")
        )
    }
}
