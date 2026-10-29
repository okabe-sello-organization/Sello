package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * A category with no limit says what was spent: the amount on its own line and a small
 * "gastado" under it, as in the v2 Recibo grid. In a grid column of a 320dp screen the
 * amount stays whole, so the cell is as tall as it is with room to spare; "gastado"
 * before the amount once squeezed it down to one character per line.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryCellAmountTest {
    @get:Rule
    val rule = createComposeRule()

    private val spent = MoneyTextValue(MoneySign.None, "", "120.000", "120.000 pesos")

    @Test
    fun aSpentAmountStaysWholeInANarrowGridColumn() {
        rule.inEveryScheme(content = {
            Column {
                for ((tag, width) in listOf("narrow" to 120.dp, "roomy" to 320.dp)) {
                    CategoryCell(
                        "Comida",
                        SelloIcon.Restaurant,
                        spent,
                        null,
                        "Comida. Gastado 120.000 pesos, sin límite.",
                        {},
                        Modifier.width(width).testTag(tag),
                        amountCaption = "gastado"
                    )
                }
            }
        }) { scheme ->
            val narrow = rule.onNodeWithTag("narrow").getUnclippedBoundsInRoot().height
            val roomy = rule.onNodeWithTag("roomy").getUnclippedBoundsInRoot().height
            assertEquals(scheme, roomy.value, narrow.value, 0.5f)
        }
    }
}
