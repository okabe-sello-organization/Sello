package com.software.sello.feature.recibo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.composition.viewModels
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.devtools.SandboxGraph
import com.software.sello.devtools.SandboxSession
import com.software.sello.devtools.ScenarioStep
import com.software.sello.devtools.Scenarios
import com.software.sello.navigation.SelloAppRoot
import com.software.sello.press
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ajustar presupuesto through the real app on the `ajustar-presupuesto` scenario, stopped
 * where October is 80.000 over (AJ04): Café gets 150.000 for October only (AJ01).
 */
@RunWith(AndroidJUnit4::class)
class AdjustBudgetJourneyTest {
    @get:Rule val rule = createComposeRule()
    private var sandbox: SandboxGraph? = null

    @After fun close() {
        sandbox?.erase()
    }

    /** The scenario up to its first check: four categories and October's expenses. */
    private suspend fun overspentOctober(): SandboxGraph {
        val scenario = Scenarios.AdjustBudget
        val graph = SandboxGraph(
            SandboxSession(
                active = true,
                date = scenario.start,
                zone = scenario.zone,
                scenario = scenario.key
            )
        )
        sandbox = graph
        graph.erase()
        graph.open()
        val runner = checkNotNull(graph.tools?.scenarios)
        val firstCheck = scenario.steps.indexOfFirst { it is ScenarioStep.Verify }
        repeat(firstCheck + 1) { runner.step() }
        return graph
    }

    private fun press(vararg keys: String) = keys.forEach { key ->
        rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag(LIMIT_KEYPAD_TAG))).press()
    }

    @Test
    fun aLimitForOctoberOnlyClosesTheGapAndRecordsOnlyOctober() = runBlocking {
        val graph = overspentOctober()
        rule.setContent {
            SelloTheme(reducedMotion = true) { SelloAppRoot(viewModels(graph.koin)) }
        }

        rule.awaitTag(RECIBO_ADJUST_TAG)
        rule.onNodeWithTag(RECIBO_ADJUST_TAG).performScrollTo().press()
        rule.awaitTag(ADJUST_TAG)
        rule.onNodeWithContentDescription("Restante del presupuesto: menos 80.000 pesos. Excedido.")
            .assertIsDisplayed()
        capture("screen")
        rule.onNode(
            hasText("Café") and hasAnyAncestor(hasTestTag(ADJUST_TAG)),
            useUnmergedTree = true
        )
            .assertExists()
        val cafe = graph.rows("SELECT id FROM category WHERE name = 'Café'").single()
        rule.onNodeWithTag(adjustRowTag(cafe)).performScrollTo().press()
        rule.awaitTag(LIMIT_SHEET_TAG)
        press("1", "5", "0", "000")
        rule.awaitText("Tu límite total de octubre pasaría a 750.000. Te quedarían 20.000.")
        capture("sheet")
        rule.onNodeWithText("Guardar solo octubre").performScrollTo().press()

        rule.awaitText("Límite de Café para octubre: 150.000")
        rule.onNodeWithContentDescription("Restante del presupuesto: 20.000 pesos. Al día.")
            .assertIsDisplayed()
        capture("saved")
        assertEquals(
            listOf("2026-10|150000"),
            graph.rows("SELECT month || '|' || limit_minor FROM month_limit")
        )
        Espresso.pressBack()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(RECIBO_ADJUST_TAG).assertDoesNotExist()
        capture("recibo")
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "adjust-$name.png")
        file.outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }
    }
}
