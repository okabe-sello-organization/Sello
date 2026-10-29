package com.software.sello.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconChoiceGridTest {
    @get:Rule
    val rule = createComposeRule()

    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val choices = listOf(
        IconChoice("restaurant", SelloIcon.Restaurant, "Comida"),
        IconChoice("home", SelloIcon.Home, "Hogar"),
        IconChoice("pets", SelloIcon.Pets, "Mascotas"),
        IconChoice("flight", SelloIcon.Flight, "Viajes"),
        IconChoice("school", SelloIcon.School, "Educación"),
        IconChoice("checkroom", SelloIcon.Checkroom, "Ropa"),
        IconChoice("shopping_cart", SelloIcon.ShoppingCart, "Mercado"),
        IconChoice("child_care", SelloIcon.ChildCare, "Hijos"),
        IconChoice("phone_iphone", SelloIcon.PhoneIphone, "Celular")
    )

    @Test
    fun eachIconIsANamedChoiceOneIsSelectedAndAPickIsOnlyReported() {
        val picked = mutableListOf<String>()
        rule.inEveryScheme(
            reset = { picked.clear() },
            content = { IconChoiceGrid(choices, "home", { picked += it }) },
            check = { scheme ->
                for (choice in choices) {
                    val node = rule.onNodeWithContentDescription(choice.label).assert(isRadio)
                    if (choice.id == "home") node.assertIsSelected() else node.assertIsNotSelected()
                }

                rule.onNodeWithContentDescription("Mascotas").performClick()

                assertEquals(scheme, listOf("pets"), picked)
                // The grid keeps no selection of its own: the app has not changed it.
                rule.onNodeWithContentDescription("Hogar").assertIsSelected()
            }
        )
    }

    private fun everyCellIsBigEnoughAndInside(width: Dp, fontScale: Float) {
        rule.inEveryScheme(
            fontScale = fontScale,
            width = width,
            content = { IconChoiceGrid(choices, null, {}, Modifier.testTag("grid")) },
            check = { scheme ->
                for (choice in choices) {
                    val bounds = rule.onNodeWithContentDescription(choice.label)
                        .getUnclippedBoundsInRoot()
                    val what = "$scheme ${choice.label} at $width × $fontScale: $bounds"
                    assertTrue(what, bounds.width >= 47.9.dp && bounds.height >= 47.9.dp)
                    // Half a dp of slack for rounding to whole pixels.
                    assertTrue(what, bounds.right <= width + 0.5.dp)
                }
            }
        )
    }

    @Test
    fun everyCellIsAtLeastTheMinimumTouchSize() = everyCellIsBigEnoughAndInside(360.dp, 1f)

    @Test
    fun cellsKeepTheirSizeAtTwiceTheFontSize() = everyCellIsBigEnoughAndInside(360.dp, 2f)

    @Test
    fun inANarrowSpaceThereAreFewerColumnsNotSmallerCells() =
        everyCellIsBigEnoughAndInside(150.dp, 1f)

    @Test
    fun aDisabledGridShowsItsChoiceAndIgnoresTaps() {
        val picked = mutableListOf<String>()
        rule.inEveryScheme(
            content = { IconChoiceGrid(choices, "home", { picked += it }, enabled = false) },
            check = { scheme ->
                rule.onNodeWithContentDescription("Mascotas").assertIsNotEnabled().performClick()

                assertEquals(scheme, emptyList<String>(), picked)
                rule.onNodeWithContentDescription("Hogar").assertIsSelected()
            }
        )
    }
}
