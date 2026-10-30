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
import org.robolectric.annotation.GraphicsMode

/**
 * What the frame and the charts show in the narrowest windows Sello supports. One window
 * dp is drawn as one pixel.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NarrowFitTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(width: Int, height: Int, content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun aTitleThatFitsStaysBesideItsAction() {
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

        val title = rule.onNodeWithText("Editar categoría").getUnclippedBoundsInRoot()
        val action = rule.onNodeWithText("Archivar").getUnclippedBoundsInRoot()
        assertTrue("title $title, action $action", title.right <= action.left)
        assertTrue("title $title, action $action", title.bottom > action.top)
    }

    // The weeks of a month that reaches its 29th: four past ones and the current one.
    private val fiveWeeks = listOf("sem 1", "sem 2", "sem 3", "sem 4", "esta")
        .mapIndexed { index, label -> week(index + 1, label, current = label == "esta") }

    private fun week(number: Int, label: String, current: Boolean) = ChartPoint(
        number.toString(),
        label,
        100_000L * number,
        "Semana $number: ${number}00.000 pesos anotados",
        if (current) ChartPeriod.Current else ChartPeriod.Past,
        shortAxisLabel = number.toString()
    )

    private var labelSmallSize = 0.sp

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

    // Guards: these hold on the chart as it was, and must keep holding.

    @Test
    fun guardWeekLabelsKeepTheirWordsAndSizeWhereTheyFit() {
        var usualLineHeight = 0
        show(360, 360) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelSmall
            labelSmallSize = style.fontSize
            usualLineHeight = measurer.measure("sem 1", style).size.height
            ColumnsChart(fiveWeeks, "Gasto por semana")
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
}
