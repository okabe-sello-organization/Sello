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

    private fun capture(look: Look, file: String, state: CategoryExpensesState = ready) {
        rule.showSello(look) {
            CategoryExpensesScreen(
                state = state,
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

    /** A refresh that failed: the rows as last read, with the notice and Reintentar. */
    @Test
    fun olderClaro() = capture(Look(), "category-expenses-stale-claro", ready.copy(stale = true))

    /** A deletion whose outcome is not known, asked about again with its own operation (#331). */
    @Test
    fun deleteUnknownClaro() = capture(
        Look(),
        "category-expenses-delete-unknown-claro",
        ready.copy(problem = DeleteProblem.Unknown)
    )

    /** Nothing was ever read: the only case that shows the error. */
    @Test
    fun failedClaro() =
        capture(Look(), "category-expenses-failed-claro", CategoryExpensesState.Failed)

    @Test
    fun olderOscuro() =
        capture(Look(dark = true), "category-expenses-stale-oscuro", ready.copy(stale = true))

    @Test
    fun deleteUnknownOscuro() = capture(
        Look(dark = true),
        "category-expenses-delete-unknown-oscuro",
        ready.copy(problem = DeleteProblem.Unknown)
    )

    @Test
    fun failedOscuro() =
        capture(Look(dark = true), "category-expenses-failed-oscuro", CategoryExpensesState.Failed)

    private val empty = ready.copy(rows = emptyList(), more = false, totalCount = 0)

    @Test fun emptyClaro() = capture(Look(), "category-expenses-empty-claro", empty)

    @Test fun emptyOscuro() = capture(Look(dark = true), "category-expenses-empty-oscuro", empty)

    private val pageFailed = ready.copy(more = true, pageFailed = true)

    @Test
    fun pageFailedClaro() = capture(Look(), "category-expenses-page-failed-claro", pageFailed)

    @Test
    fun pageFailedOscuro() =
        capture(Look(dark = true), "category-expenses-page-failed-oscuro", pageFailed)

    private val busy = ready.copy(more = true, busy = true)

    @Test fun busyClaro() = capture(Look(), "category-expenses-busy-claro", busy)

    @Test fun busyOscuro() = capture(Look(dark = true), "category-expenses-busy-oscuro", busy)
}
