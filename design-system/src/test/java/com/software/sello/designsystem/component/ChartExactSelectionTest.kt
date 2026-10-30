package com.software.sello.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartExactSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    /** Neighbours that no floating-point type can tell apart: 2^53 and 2^53 + 1. */
    private val first =
        ChartPoint("jul", "jul", 9_007_199_254_740_992, "julio, 9.007.199.254.740.992 pesos")
    private val second =
        ChartPoint("ago", "ago", 9_007_199_254_740_993, "agosto, 9.007.199.254.740.993 pesos")
    private val small = ChartPoint("sep", "sep", 1, "septiembre, 1 peso", ChartPeriod.Current, "1")
    private val points = listOf(first, second, small)
    private val summary = "Gasto por mes. El más alto es agosto."

    private val selectable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    @Test
    fun theseAmountsReallyAreIndistinguishableAsFloatingPoint() {
        assertEquals(first.value.toDouble(), second.value.toDouble(), 0.0)
        assertTrue(first.value != second.value)
    }

    @Test
    fun columnsSelectTheOriginalPointNotAPixel() {
        var selected by mutableStateOf<String?>(null)
        val picks = mutableListOf<String>()
        rule.inEveryScheme(
            reset = {
                selected = null
                picks.clear()
            },
            content = {
                val onSelect: (String) -> Unit = {
                    selected = it
                    picks += it
                }
                ColumnsChart(points, summary, Modifier.testTag("chart"), selected, onSelect)
            }
        ) {
            rule.onNodeWithTag("chart").assertContentDescriptionEquals(summary)
            // Each mark announces its own exact amount, however alike they are drawn.
            rule.onNodeWithContentDescription(first.spoken).assertIsNotSelected()
            rule.onNodeWithContentDescription(second.spoken).assertIsNotSelected().performClick()
            rule.onNodeWithContentDescription(second.spoken).assertIsSelected()
            rule.onNodeWithContentDescription(first.spoken).assertIsNotSelected().performClick()
            rule.onNodeWithContentDescription(first.spoken).assertIsSelected()
            rule.onNodeWithContentDescription(small.spoken).performClick()
            assertEquals(it, listOf("ago", "jul", "sep"), picks)
            assertEquals(it, 3, rule.onAllNodes(selectable).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun aPartlyStripedColumnKeepsRecordedAndAvailableAmountsDistinctWhenSelected() {
        val current = ChartPoint(
            "week",
            "esta",
            75_000,
            "Semana actual: 5.000 pesos anotados y 70.000 pesos por gastar",
            ChartPeriod.Current,
            stripedFromValue = 5_000
        )
        var picked: String? = null
        rule.inEveryScheme(content = {
            ColumnsChart(listOf(current), "Gastos y margen", onSelect = { picked = it })
        }) {
            rule.onNodeWithContentDescription(current.spoken).assertIsDisplayed().performClick()
            assertEquals("week", picked)
        }
    }

    @Test
    fun aChartIsNeverShorterThan130dpAndCarriesOneLabelledReference() {
        rule.inEveryScheme(
            content = {
                ColumnsChart(
                    points = points,
                    summary = summary,
                    modifier = Modifier.testTag("chart"),
                    reference = ChartReference(
                        9_000_000_000_000_000,
                        "Límite 9.000 B",
                        "Límite 9.000.000.000.000.000 pesos"
                    ),
                    height = 40.dp
                )
            }
        ) {
            val height = rule.onNodeWithTag("chart").getUnclippedBoundsInRoot().height
            assertTrue("$it chart is $height", height >= 130.dp)
            rule.onNodeWithText("Límite 9.000 B").assertIsDisplayed()
            // The line's label is shortened like an axis; it is read in full.
            rule.onNodeWithContentDescription("Límite 9.000.000.000.000.000 pesos").assertExists()
            // Without a selection callback the marks still speak but cannot be picked.
            assertEquals(it, 0, rule.onAllNodes(selectable).fetchSemanticsNodes().size)
            rule.onNodeWithContentDescription(second.spoken).assertExists()
        }
    }

    @Test
    fun anEmptyChartSaysSoAtFullSizeAndOffersNothingToSelect() {
        rule.setContent {
            SelloTheme {
                ColumnsChart(
                    points = emptyList(),
                    summary = "Sin gastos este mes.",
                    modifier = Modifier.testTag("chart"),
                    onSelect = {},
                    emptyText = "Aún no hay gastos."
                )
            }
        }
        rule.onNodeWithText("Aún no hay gastos.").assertIsDisplayed()
        assertTrue(rule.onNodeWithTag("chart").getUnclippedBoundsInRoot().height >= 130.dp)
        assertEquals(0, rule.onAllNodes(selectable).fetchSemanticsNodes().size)
    }
}
