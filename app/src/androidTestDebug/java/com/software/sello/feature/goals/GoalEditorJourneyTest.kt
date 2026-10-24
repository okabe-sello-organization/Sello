package com.software.sello.feature.goals

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.GOAL_EDIT_TAG
import com.software.sello.navigation.METAS_CREATE_TAG
import com.software.sello.press
import java.time.YearMonth
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nueva meta and Editar meta in the installed app over its real database: a goal typed
 * on the Sello pad with a target month appears in Metas with its plan, is edited and
 * deleted after the confirm slip, and a draft survives the activity being recreated.
 */
@RunWith(AndroidJUnit4::class)
class GoalEditorJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun emptyHistory() = TestData.reset()

    @After
    fun close() {
        scenario?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    private fun openMetas() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitText("Metas")
        rule.onNodeWithText("Metas").performClick()
        rule.awaitTag(METAS_LIST_TAG)
    }

    private fun name(text: String) {
        val field = rule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(GOAL_EDITOR_NAME_TAG))
        )
        field.performTextClearance()
        field.performTextInput(text)
        scenario?.hideKeyboard()
    }

    private fun keys(vararg labels: String) {
        rule.onNodeWithTag(GOAL_EDITOR_AMOUNT_TAG).performScrollTo().press()
        rule.awaitText("Listo")
        labels.forEach { label ->
            val key = if (label == BACKSPACE) hasContentDescription("Borrar") else hasText(label)
            rule.onNode(key and hasAnyAncestor(hasTestTag(GOAL_EDITOR_PAD_TAG))).press()
        }
    }

    private val spanish = Locale.forLanguageTag("es-CO")

    private companion object {
        const val BACKSPACE = "<"
    }

    /** "Diciembre 2026", as the month sheet says a month. */
    private fun spoken(month: YearMonth) =
        month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, spanish)
            .replaceFirstChar { it.titlecase(spanish) } + " " + month.year

    @Test
    fun aGoalTypedOnThePadWithAMonthAppearsInMetasWithItsPlan() {
        openMetas()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Crear una meta"))
        rule.onNodeWithTag(METAS_CREATE_TAG).performClick()
        rule.awaitTag(GOAL_EDITOR_TAG)

        name("Celular nuevo")
        rule.onNodeWithContentDescription("Tecnología").performClick()
        keys("1", "2", "000", "0", "0")
        rule.onNodeWithText("Listo").press()
        rule.onNodeWithContentDescription("Cuánto necesitas: 1.200.000 pesos", substring = true)
            .assertExists()

        // Two months from now: 1.200.000 ÷ 2 = 600.000 a month, whatever today is.
        val month = YearMonth.from(TestCommands.today).plusMonths(2)
        rule.onNodeWithTag("goal.editor.month").performScrollTo().press()
        rule.awaitTag(GOAL_EDITOR_MONTHS_TAG)
        if (month.year > YearMonth.from(TestCommands.today).plusMonths(1).year) {
            rule.onNodeWithContentDescription("Año siguiente").performClick()
        }
        rule.onNodeWithContentDescription(spoken(month)).performClick()
        rule.awaitText("Son 600.000 al mes durante 2 meses.")
        rule.onNodeWithText("Si aportas 600.000 cada mes").assertExists()

        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).press()
        rule.awaitTag(METAS_LIST_TAG)
        rule.awaitText("Celular nuevo")
        assertEquals(
            listOf("Celular nuevo|phone_iphone|$month|1"),
            TestData.rows("SELECT name, icon_key, target_month, version FROM goal")
        )
        assertEquals(
            listOf("1200000"),
            TestData.rows("SELECT amount_minor FROM goal_event")
        )
    }

    @Test
    fun aGoalIsEditedThenDeletedOnlyAfterTheConfirmSlip() {
        val viaje = TestCommands.createGoal("Viaje a la costa", 3_000_000)
        TestCommands.recordGoalMovement(viaje, 1_710_000)
        openMetas()
        rule.awaitTag("metas.goal.$viaje")
        rule.onNodeWithTag("metas.goal.$viaje").performClick()
        rule.awaitTag(GOAL_EDIT_TAG)
        rule.onNodeWithTag(GOAL_EDIT_TAG).performClick()
        rule.awaitTag(GOAL_EDITOR_TAG)
        rule.awaitText("Editar meta")

        name("Viaje a Cartagena")
        keys(*Array(7) { BACKSPACE }, "2", "000", "000")
        rule.onNodeWithText("Listo").press()
        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).press()

        // 1.710.000 of 2.000.000 is 85 %.
        rule.awaitText("Viaje a Cartagena")
        rule.awaitText("85 %")
        assertEquals(
            // Created at 1, the aporte 2, the name 3 and the target 4.
            listOf("Viaje a Cartagena|4"),
            TestData.rows("SELECT name, version FROM goal")
        )
        assertEquals(
            listOf("3000000", "1710000", "2000000"),
            TestData.rows("SELECT amount_minor FROM goal_event ORDER BY sequence")
        )

        rule.onNodeWithTag(GOAL_EDIT_TAG).performClick()
        rule.awaitTag(GOAL_EDITOR_DELETE_TAG)
        rule.onNodeWithText("Borrar meta").performScrollTo().performClick()
        rule.awaitText("¿Borrar Viaje a Cartagena?")
        assertEquals(1L, TestData.count("goal"))
        rule.onNodeWithText("Borrar").press()

        rule.awaitTag("metas.empty")
        assertEquals(0L, TestData.count("goal"))
        assertEquals(0L, TestData.count("goal_event"))
    }

    @Test
    fun aDraftSurvivesRecreationAndLeavingAsksBeforeDiscardingIt() {
        openMetas()
        rule.onNodeWithTag(METAS_CREATE_TAG).performClick()
        rule.awaitTag(GOAL_EDITOR_TAG)
        name("Casa")
        keys("5", "000")

        scenario?.recreate()
        rule.awaitText("Listo")
        rule.onNodeWithContentDescription("Cuánto necesitas: 5.000 pesos", substring = true)
            .assertExists()
        rule.onNodeWithText("Casa").assertExists()

        rule.onNodeWithTag(GOAL_EDITOR_CLOSE_TAG).performClick()
        rule.awaitText("¿Descartar los cambios?")
        rule.onNodeWithText("Descartar").press()
        rule.awaitTag("metas.empty")
        assertEquals(0L, TestData.count("goal"))
    }
}
