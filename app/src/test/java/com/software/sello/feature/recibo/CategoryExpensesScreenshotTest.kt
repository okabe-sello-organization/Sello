package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What a category's full expense list looks like, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryExpensesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val ready = CategoryExpensesState.Ready(
        categoryName = "Alimentación",
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 21),
        rows = listOf(
            ExpenseRow("1", LocalDate.of(2026, 10, 28), "Adelantado", plain("1.000"), true),
            ExpenseRow("2", LocalDate.of(2026, 10, 21), "Rappi, almuerzo", plain("20.600"), false),
            ExpenseRow("3", LocalDate.of(2026, 10, 20), null, plain("33.500"), false),
            ExpenseRow("4", LocalDate.of(2026, 10, 19), "Mercado D1", plain("48.900"), false)
        ),
        more = false,
        totalCount = 4
    )

    private fun capture(look: Look, file: String) {
        rule.showSello(look) {
            CategoryExpensesScreen(
                state = ready,
                query = "",
                order = "Date",
                onAction = {},
                onEdit = {},
                padding = PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/$file.png")
    }

    @Test
    fun claro() = capture(Look(), "category-expenses-claro")

    @Test
    fun oscuro() = capture(Look(dark = true), "category-expenses-oscuro")
}
