package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Words the frame and its controls show must stay whole at twice the text size in the
 * narrowest windows Sello supports: they may take another line, never an ellipsis.
 * One window dp is drawn as one pixel.
 */
@RunWith(AndroidJUnit4::class)
class LargeTextFitTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(width: Int, height: Int, content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { content() }
                }
            }
        }
    }

    private fun assertWhole(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        val shortened = layout.multiParagraph.didExceedMaxLines ||
            (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        assertFalse("\"$text\" is shortened in ${layout.lineCount} lines", shortened)
    }

    private val navigation = SelloNavigation(
        listOf(
            SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
            SelloDestination("resumen", "Resumen", SelloIcon.Insights)
        ),
        "recibo"
    ) {}

    @Test
    fun aPushedScreensTitleTakesASecondLineInsteadOfBeingShortened() {
        show(320, 640) {
            SelloScaffold(
                title = "Configuración · Octubre 2026",
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(SelloIcon.ArrowBack.painter(), "Volver") }
                },
                windowInsets = WindowInsets(0)
            ) {}
        }

        assertWhole("Configuración · Octubre 2026")
    }

    @Test
    fun aTitleWithNoRoomBesideItsActionMovesUnderItAndStaysWhole() {
        show(320, 640) {
            SelloScaffold(
                title = "Editar categoría",
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(SelloIcon.ArrowBack.painter(), "Volver") }
                },
                actions = {
                    SelloButton("Archivar", onClick = {}, kind = ButtonKind.Text)
                },
                windowInsets = WindowInsets(0)
            ) {}
        }

        assertWhole("Editar categoría")
        val title = rule.onNodeWithText("Editar categoría").getUnclippedBoundsInRoot()
        val action = rule.onNodeWithText("Archivar").getUnclippedBoundsInRoot()
        assertTrue("title $title, action $action", title.top >= action.bottom)
    }

    @Test
    fun aTitleThatFitsStaysBesideItsAction() {
        show(320, 640) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloScaffold(
                    title = "Editar categoría",
                    navigationIcon = {
                        IconButton(onClick = {}) { Icon(SelloIcon.ArrowBack.painter(), "Volver") }
                    },
                    actions = {
                        SelloButton("Archivar", onClick = {}, kind = ButtonKind.Text)
                    },
                    windowInsets = WindowInsets(0)
                ) {}
            }
        }

        val title = rule.onNodeWithText("Editar categoría").getUnclippedBoundsInRoot()
        val action = rule.onNodeWithText("Archivar").getUnclippedBoundsInRoot()
        assertTrue("title $title, action $action", title.right <= action.left)
        assertTrue("title $title, action $action", title.bottom > action.top)
    }

    @Test
    fun aMonthSwitcherTitleTakesASecondLineInsteadOfBeingShortened() {
        // Beside the back arrow on the narrowest phone.
        show(272, 640) { MonthSwitcher("Respaldo · Octubre 2026", onClick = {}) }

        assertWhole("Respaldo · Octubre 2026")
    }

    @Test
    fun theRailWidensSoEveryTabLabelIsWhole() {
        show(640, 360) { SelloNavigationRail(navigation) }

        assertWhole("Recibo")
        assertWhole("Resumen")
    }

    @Test
    fun aChipsLabelTakesASecondLineInsteadOfBeingShortened() {
        // Inside a slip on the narrowest phone: 320dp less the margins and the slip's padding.
        show(264, 640) {
            ChipFlow { SelloChip("Último gasto agregado", selected = false, onClick = {}) }
        }

        assertWhole("Último gasto agregado")
    }

    // The weeks of a month that reaches its 29th, in the narrow pane beside Recibo.
    private val fiveWeeks = listOf("sem 1", "sem 2", "sem 3", "sem 4", "esta")
        .mapIndexed { index, label ->
            ChartPoint(
                (index + 1).toString(),
                label,
                100_000L * (index + 1),
                "Semana ${index + 1}: ${index + 1}00.000 pesos anotados"
            )
        }

    private fun axisLayouts(): List<TextLayoutResult> = fiveWeeks.map { point ->
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(point.axisLabel, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.single()
    }

    @Test
    fun aChartsFiveWeekLabelsStayWholeInTheNarrowestPane() {
        // 640 x 360 at twice the text size leaves the chart 195dp: 39dp a week.
        show(195, 360) { ColumnsChart(fiveWeeks, "Gasto por semana") }

        axisLayouts().forEach { layout ->
            val shortened = layout.multiParagraph.didExceedMaxLines ||
                (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            assertFalse(
                "\"${layout.layoutInput.text.text}\" is shortened in ${layout.lineCount} lines",
                shortened
            )
        }
        fiveWeeks.forEach { point ->
            rule.onNodeWithContentDescription(point.spoken).assertExists()
        }
    }

    @Test
    fun aChartsWeekLabelsKeepTheirUsualSizeWhereTheyFit() {
        var usualLineHeight = 0
        show(360, 360) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.labelSmall
                usualLineHeight = measurer.measure("sem 1", style).size.height
                ColumnsChart(fiveWeeks, "Gasto por semana")
            }
        }

        axisLayouts().forEach { layout ->
            assertEquals(1, layout.lineCount)
            assertEquals(usualLineHeight, layout.multiParagraph.height.toInt())
        }
    }
}
