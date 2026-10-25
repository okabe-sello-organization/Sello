package com.software.sello.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategorySemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun money(digits: String, minus: Boolean = false) = MoneyTextValue(
        if (minus) MoneySign.Minus else MoneySign.None,
        "",
        digits,
        "$digits pesos"
    )

    @Test
    fun aCellIsOneButtonThatSaysItsWholeMeaningAndOpensOnce() {
        val opened = mutableListOf<String>()
        val left = "Alimentación. Quedan 287.600 pesos de 900.000. 68 por ciento usado."
        // The circle can only look full; the words keep the exact overspend.
        val over = "Entretenimiento. Pasado por 999.999.999.999 pesos de 150.000."
        val free = "Transporte. Gastado 86.000 pesos. Sin límite."
        rule.inEveryScheme(
            reset = { opened.clear() },
            content = {
                CategoryGrid(listOf("left", "over", "free")) { _, id ->
                    when (id) {
                        "left" -> CategoryCell(
                            "Alimentación",
                            SelloIcon.Restaurant,
                            money("287.600"),
                            0.68f,
                            left,
                            { opened += id },
                            Modifier.testTag(id)
                        )

                        "over" -> CategoryCell(
                            "Entretenimiento",
                            SelloIcon.Theaters,
                            money("999.999.999.999", true),
                            6_666_667f,
                            over,
                            { opened += id },
                            Modifier.testTag(id),
                            over = true
                        )

                        else -> CategoryCell(
                            "Transporte",
                            SelloIcon.DirectionsBus,
                            money("86.000"),
                            null,
                            free,
                            { opened += id },
                            Modifier.testTag(id),
                            amountCaption = "gastado"
                        )
                    }
                }
            }
        ) {
            rule.onNodeWithTag("left").assert(isButton).assertContentDescriptionEquals(left)
            rule.onNodeWithTag("over").assert(isButton).assertContentDescriptionEquals(over)
            rule.onNodeWithTag("free").assert(isButton).assertContentDescriptionEquals(free)
            // Nothing inside a cell is announced on its own: not the circle, not the amount.
            assertEquals(
                it,
                3,
                rule.onAllNodes(
                    hasContentDescription("", substring = true)
                ).fetchSemanticsNodes().size
            )
            rule.onNodeWithTag("over").performClick()
            rule.onNodeWithTag("free").performClick()
            assertEquals(it, listOf("over", "free"), opened)
            for (tag in listOf("left", "over", "free")) {
                val height = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().height
                assertTrue("$it $tag is $height", height >= 47.9.dp)
            }
        }
    }

    @Test
    fun aCellOffersItsActionsByLongPressAndByNameAndPressingStillOnlyOpens() {
        val done = mutableListOf<String>()
        rule.setContent {
            SelloTheme {
                CategoryCell(
                    "Alimentación",
                    SelloIcon.Restaurant,
                    money("287.600"),
                    0.68f,
                    "Alimentación. Quedan 287.600 pesos.",
                    { done += "open" },
                    Modifier.testTag("cell"),
                    actions = listOf(LineAction("Editar categoría") { done += "edit" })
                )
            }
        }

        rule.onNodeWithTag("cell").performClick()
        rule.onNodeWithTag("cell").performTouchInput { longClick() }
        rule.onNodeWithText("Editar categoría").assertIsDisplayed().performClick()
        val offered = rule.onNodeWithTag("cell").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        rule.runOnUiThread { offered.single().action() }

        assertEquals(listOf("open", "edit", "edit"), done)
        assertEquals(listOf("Editar categoría"), offered.map { it.label })
        rule.onNodeWithTag(
            "cell"
        ).assertContentDescriptionEquals("Alimentación. Quedan 287.600 pesos.")
    }

    private fun rowsOf(count: Int): List<Dp> = (0 until count).map {
        rule.onNodeWithTag("cell$it").getUnclippedBoundsInRoot().top
    }

    private fun showGrid(width: Dp, fontScale: Float) {
        rule.inEveryScheme(
            width = width,
            densityScale = 0.5f,
            fontScale = fontScale,
            content = {
                CategoryGrid(List(4) { it }) { index, _ ->
                    CategoryCell(
                        "Categoría $index",
                        SelloIcon.Home,
                        money("1.000"),
                        0.5f,
                        "Categoría $index",
                        {},
                        Modifier.testTag("cell$index")
                    )
                }
            }
        ) {
            if (it.startsWith("Cobalto dark=false")) tops = rowsOf(4)
        }
    }

    private var tops: List<Dp> = emptyList()

    @Test
    fun twoCellsShareARowOnAPhone() {
        showGrid(336.dp, 1f)
        assertEquals(tops[0], tops[1])
        assertEquals(tops[2], tops[3])
        assertTrue(tops[2] > tops[0])
    }

    @Test
    fun atLargeTypeEachCellGetsItsOwnRow() {
        showGrid(336.dp, 1.3f)
        assertEquals(4, tops.distinct().size)
    }

    @Test
    fun aWideColumnFitsThreeCellsPerRow() {
        showGrid(560.dp, 1f)
        assertEquals(tops[0], tops[1])
        assertEquals(tops[1], tops[2])
        assertTrue(tops[3] > tops[0])
    }
}
