package com.software.sello.feature.investments

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.inEveryScheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The investment rows keep their contracts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestmentContractsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun returnShowsItsPercentageAndAmountOnOneLine() {
        rule.inEveryScheme(content = {
            PerformanceLine("+5,6 %", "+2.066.000 de ganancia", false)
        }) {
            val percentage = rule.onNodeWithText(
                "+5,6 %",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            val amount = rule.onNodeWithText("+2.066.000 de ganancia", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val middle = (amount.top + amount.bottom) / 2
            assertTrue(middle > percentage.top && middle < percentage.bottom)
            assertTrue(amount.left > percentage.right)
        }
    }

    @Test
    fun investmentRowCarriesItsValueAndChangeOnTheRight() {
        rule.inEveryScheme(content = {
            InvestmentRow(
                "Parqueadero",
                "Finca raíz · valor del 12 jun",
                SelloIcon.Home,
                SelloTheme.colors.brand,
                MoneyTextValue(MoneySign.None, "", "19.000.000", "19.000.000 pesos"),
                "Sin valor",
                "+7,5 %",
                {}
            )
        }) {
            val name = rule.onNodeWithText(
                "Parqueadero",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            val change = rule.onNodeWithText(
                "+7,5 %",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            assertTrue(change.left > name.right)
            assertTrue(change.top < name.bottom + 24.dp)
        }
    }
}
