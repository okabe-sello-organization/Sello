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
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.composition.viewModels
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.devtools.JourneySetups
import com.software.sello.devtools.SandboxGraph
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

    /** Four categories and October's expenses, 80.000 over. */
    private suspend fun overspentOctober(): SandboxGraph {
        val graph = JourneySetups.graph(JourneySetups.OverspentOctober)
        sandbox = graph
        graph.erase()
        graph.open()
        val runner = checkNotNull(graph.tools?.scenarios)
        while (runner.step()) Unit
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
        // The screen shows while the month is still being read off the main thread, which
        // the test framework does not wait for: wait for the answer itself.
        rule.awaitDescription(OVER)
        rule.onNodeWithContentDescription(OVER).assertIsDisplayed()
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
        // The month's answer is read again after the save, apart from the category's line.
        rule.awaitDescription(ON_TRACK)
        rule.onNodeWithContentDescription(ON_TRACK).assertIsDisplayed()
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

private const val OVER = "Restante del presupuesto: menos 80.000 pesos. Excedido."
private const val ON_TRACK = "Restante del presupuesto: 20.000 pesos. Al día."
