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
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // The weeks of a month that reaches its 29th: four past ones and the current one.
    private val fiveWeeks = listOf("sem 1", "sem 2", "sem 3", "sem 4", "esta")
        .mapIndexed { index, label -> week(index + 1, label, current = label == "esta") }

    // A past month with five weeks, none of them current.
    private val pastMonth = (1..5).map { week(it, "sem $it", current = false) }

    private fun week(number: Int, label: String, current: Boolean) = ChartPoint(
        number.toString(),
        label,
        100_000L * number,
        "Semana $number: ${number}00.000 pesos anotados",
        if (current) ChartPeriod.Current else ChartPeriod.Past,
        shortAxisLabel = number.toString()
    )

    private var labelSmallSize = 0.sp

    private fun showChart(points: List<ChartPoint>, width: Int, fontScale: Float) {
        show(width, 360) {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                labelSmallSize = MaterialTheme.typography.labelSmall.fontSize
                ColumnsChart(points, "Gasto por semana")
            }
        }
    }

    private fun axisLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true)
            .assertExists("the axis does not show \"$text\"")
            .fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    /** A line ends inside a word: the column is narrower than the word it holds. */
    private fun TextLayoutResult.breaksAWord(): Boolean {
        val text = layoutInput.text.text
        return (0 until lineCount - 1).any {
            val end = getLineEnd(it)
            text[end - 1] != ' ' && text[end] != ' '
        }
    }

    private fun assertAxisShows(
        shown: List<String>,
        hidden: List<String>,
        wholeOnly: Boolean = true
    ) {
        shown.forEach { text ->
            val layout = axisLayout(text)
            // The text size is the label style's own, never reduced to make it fit.
            assertEquals("\"$text\"", labelSmallSize, layout.layoutInput.style.fontSize)
            if (wholeOnly) {
                assertFalse("\"$text\" is cut off", layout.multiParagraph.didExceedMaxLines)
                assertFalse("\"$text\" breaks a word", layout.breaksAWord())
            }
        }
        hidden.forEach {
            rule.onNodeWithText(it, useUnmergedTree = true)
                .assertDoesNotExist()
        }
    }

    @Test
    fun aChartsFiveWeekLabelsShowTheNumberAloneWhereTheWordsDoNotFit() {
        // 640 x 360 at twice the text size leaves the chart 195dp: 39dp a week, and "sem"
        // alone is wider than that.
        showChart(fiveWeeks, 195, 2f)

        assertAxisShows(
            shown = listOf("1", "2", "3", "4", "5"),
            hidden = listOf("sem 1", "sem 2", "sem 3", "sem 4", "esta")
        )
    }

    @Test
    fun aPastMonthsFiveWeekLabelsShowTheNumberAlone() {
        showChart(pastMonth, 195, 2f)

        assertAxisShows(
            shown = listOf("1", "2", "3", "4", "5"),
            hidden = listOf("sem 1", "sem 2", "sem 3", "sem 4", "sem 5")
        )
    }

    // Guards: these hold on the chart as it was, and must keep holding.

    @Test
    fun guardTheShortenedAxisChangesNothingTheChartSays() {
        showChart(fiveWeeks, 195, 2f)

        fiveWeeks.forEach { point ->
            rule.onNode(hasContentDescription(point.spoken)).assertContentDescriptionEquals(
                point.spoken
            )
        }
    }

    @Test
    fun guardWeekLabelsKeepTheirWordsAndSizeWhereTheyFit() {
        var usualLineHeight = 0
        show(360, 360) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.labelSmall
                labelSmallSize = style.fontSize
                usualLineHeight = measurer.measure("sem 1", style).size.height
                ColumnsChart(fiveWeeks, "Gasto por semana")
            }
        }

        assertAxisShows(
            shown = listOf("sem 1", "sem 2", "sem 3", "sem 4", "esta"),
            hidden = listOf("1", "2", "3", "4", "5")
        )
        fiveWeeks.forEach {
            val layout = axisLayout(it.axisLabel)
            assertEquals(1, layout.lineCount)
            assertEquals(usualLineHeight, layout.multiParagraph.height.toInt())
        }
    }

    @Test
    fun guardALabelWithNoShortFormIsLeftAsItIs() {
        showChart(fiveWeeks.map { it.copy(shortAxisLabel = null) }, 195, 2f)

        assertAxisShows(
            shown = listOf("sem 1", "sem 2", "sem 3", "sem 4", "esta"),
            hidden = listOf("1", "2", "3", "4", "5"),
            // As before, a label with no shorter form is cut when it cannot fit.
            wholeOnly = false
        )
    }
}
