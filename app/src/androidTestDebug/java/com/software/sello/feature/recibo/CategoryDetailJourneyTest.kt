package com.software.sello.feature.recibo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.await
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.composition.viewModels
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.devtools.SandboxGraph
import com.software.sello.devtools.SandboxSession
import com.software.sello.devtools.Scenarios
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryReads
import com.software.sello.feature.category.EDITOR_SUBMIT_TAG
import com.software.sello.feature.category.typeLimit
import com.software.sello.navigation.SelloAppRoot
import com.software.sello.press
import com.software.sello.pressBackPastTheKeyboard
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDetailJourneyTest {
    @get:Rule val rule = createComposeRule()
    private var sandbox: SandboxGraph? = null

    @After fun close() {
        sandbox?.erase()
    }

    private suspend fun seededScenario(): SandboxGraph {
        val scenario = Scenarios.CategoryDetail
        val graph =
            SandboxGraph(
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
        while (runner.step()) {
            // Each step persists its checkpoint before the next one is requested.
        }
        assertTrue(runner.state.value.finished)
        return graph
    }

    @Test
    fun realCategoryInsightsAndTheSearchableExpenseBrowserSurviveReopening() = runBlocking {
        val graph = seededScenario()
        val category = (
            graph.koin.get<CategoryReads>().monthBudget(
                YearMonth.of(2026, 10)
            ) as Outcome.Success
            ).value.categories.single().category
        val snapshots = graph.koin.get<CategoryInsightsSnapshots>()
        val first = (snapshots.read(category.id, YearMonth.of(2026, 10)) as Outcome.Success).value
        assertEquals(70_000L, first.weeks.last().remainingAllowance!!.minorUnits)
        assertEquals(10_000L, first.historicalAverage!!.minorUnits)
        graph.open()
        val reopened = (
            graph.koin.get<CategoryInsightsSnapshots>().read(
                category.id,
                YearMonth.of(2026, 10)
            ) as Outcome.Success
            ).value
        assertEquals(first, reopened)
        rule.setContent {
            SelloTheme(reducedMotion = true) { SelloAppRoot(viewModels(graph.koin)) }
        }
        rule.awaitTag(reciboCategoryTag(category.id.value))
        rule.onNodeWithTag(reciboCategoryTag(category.id.value)).performScrollTo().performClick()
        rule.awaitTag(CATEGORY_DETAIL_LIST_TAG)
        rule.awaitTag("detail:history")
        rule.onNodeWithTag(CATEGORY_DETAIL_HEADER_TAG).performScrollTo()
        capture("top")
        rule.onNodeWithText("Últimos gastos").assertIsDisplayed()
        rule.onNodeWithTag("detail:numbers").performScrollTo().assertIsDisplayed()
        capture("numbers")
        rule.onNodeWithTag("detail:weeks").performScrollTo().assertIsDisplayed()
        capture("weeks")
        rule.onNodeWithTag("detail:history").performScrollTo().assertIsDisplayed()
        capture("history")
        rule.onNodeWithText("Ver los 5").performScrollTo().performClick()
        rule.awaitTag("category.expenses")
        rule.onNodeWithText("Valor").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("Mercado")
        rule.awaitText("Mercado")
        rule.onNodeWithText("Café").assertDoesNotExist()
        // Back below exercises navigation, rather than dismissing the search keyboard.
        Espresso.closeSoftKeyboard()
        rule.onNode(
            hasText("Mercado") and
                androidx.compose.ui.test.hasAnyAncestor(hasTestTag("category.expenses")) and
                !hasSetTextAction()
        )
            .performScrollTo().performClick()
        rule.awaitText("Guardar cambios")
        Espresso.pressBack()
        rule.awaitTag("category.expenses")
        rule.onNodeWithText("Nombre").performClick()
        rule.pressBackPastTheKeyboard()
        rule.awaitTag(CATEGORY_DETAIL_TAG)
        rule.onNodeWithTag(CATEGORY_DETAIL_EDIT_TAG).performClick()
        rule.awaitText("Editar categoría")
        assertEquals(6, graph.rows("SELECT id FROM expense").size)
    }

    @Test
    fun editingTheCurrentLimitRefreshesItsChartPointBeforeRollover() = runBlocking<Unit> {
        val graph = seededScenario()
        val category = (
            graph.koin.get<CategoryReads>().monthBudget(YearMonth.of(2026, 10)) as Outcome.Success
            ).value.categories.single().category
        rule.setContent {
            SelloTheme(reducedMotion = true) { SelloAppRoot(viewModels(graph.koin)) }
        }
        rule.awaitTag(reciboCategoryTag(category.id.value))
        rule.onNodeWithTag(reciboCategoryTag(category.id.value)).performScrollTo().performClick()
        rule.awaitTag("detail:history")
        fun currentPoint(limit: String) = hasContentDescription("hasta hoy", substring = true) and
            hasContentDescription("Límite: $limit pesos.", substring = true)
        fun completedPoint(month: String, spent: String, limit: String) =
            hasContentDescription(month, substring = true) and
                hasContentDescription("2026: $spent pesos. Límite: $limit pesos.", substring = true)
        rule.onNodeWithTag("detail:history").performScrollTo()
        rule.onNode(currentPoint("200.000")).assertExists()
        rule.onNodeWithText("Promedio de 2 meses completos").assertIsDisplayed()
        rule.onNodeWithTag(CATEGORY_DETAIL_EDIT_TAG).performClick()
        rule.awaitText("Editar categoría")
        rule.typeLimit("500000")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()
        rule.awaitTag(CATEGORY_DETAIL_TAG)
        rule.await(currentPoint("500.000"))
        rule.onNodeWithTag("detail:history").performScrollTo()
        rule.onNode(currentPoint("200.000")).assertDoesNotExist()
        rule.onNode(currentPoint("500.000")).performClick()
        rule.onNodeWithText("Promedio de 2 meses completos").assertIsDisplayed()
        rule.onNode(completedPoint("ago", "20.000", "200.000")).assertExists()
        rule.onNode(completedPoint("sep", "0", "200.000")).assertExists()
        capture("history-updated-limit")
        checkNotNull(graph.tools?.time).jumpTo(LocalDate.of(2026, 11, 1))
        rule.await(completedPoint("oct", "100.000", "500.000"))
        rule.await(currentPoint("500.000"))
        rule.onNodeWithTag("detail:history").performScrollTo()
        rule.onNodeWithText("Promedio de 3 meses completos").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file =
            File(instrumentation.targetContext.filesDir, "category-$name.png")
        file.outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }
    }
}
