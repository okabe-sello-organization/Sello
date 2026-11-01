package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The full list draws each expense as the detail's latest expenses do: a dated stub. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryExpensesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun inRow(id: String) = hasAnyAncestor(hasTestTag(categoryExpenseTag(id)))

    @Test
    fun eachExpenseIsReadAsItsNoteWholeDayAndAmountAndAFutureOneSaysItDoesNotCountYet() {
        rule.showSello {
            CategoryExpensesScreen(
                state = CategoryExpensesState.Ready(
                    categoryName = "Alimentación",
                    month = YearMonth.of(2026, 10),
                    today = LocalDate.of(2026, 10, 21),
                    rows = listOf(
                        ExpenseRow(
                            "1",
                            LocalDate.of(2026, 10, 28),
                            "Adelantado",
                            plain("1.000"),
                            true
                        ),
                        ExpenseRow("2", LocalDate.of(2026, 10, 21), null, plain("20.600"), false)
                    ),
                    more = false,
                    totalCount = 2
                ),
                query = "",
                order = "Date",
                onAction = {},
                onEdit = {},
                padding = PaddingValues(12.dp)
            )
        }

        rule.onNode(hasText("Adelantado") and inRow("1"), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("mié 28 oct") and inRow("1"), useUnmergedTree = true)
            .assertExists()
        rule.onNode(hasText("Aún no cuenta") and inRow("1"), useUnmergedTree = true).assertExists()
        rule.onNode(hasContentDescription("1.000 pesos") and inRow("1"), useUnmergedTree = true)
            .assertExists()
        rule.onNode(hasText("Sin nota") and inRow("2"), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Hoy, mié 21 oct") and inRow("2"), useUnmergedTree = true)
            .assertExists()
        rule.onNode(hasText("Aún no cuenta") and inRow("2"), useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
