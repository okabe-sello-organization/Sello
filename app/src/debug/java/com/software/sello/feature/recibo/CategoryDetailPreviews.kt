package com.software.sello.feature.recibo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Money
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.ResourceMoneyLabels
import com.software.sello.presentation.month.monthNames
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Synthetic rendering of the ADR 0015 sandbox scenario, using the actual feature. */
@Composable
private fun CategoryDetailPreview(ink: SelloInk = SelloInk.Cobalto, dark: Boolean = false) {
    val money = MoneyFormatter(ResourceMoneyLabels(LocalResources.current))
    fun cop(value: Long) = money.format(Money.cop(value))
    val month = YearMonth.of(2026, 10)
    val today = month.atDay(22)
    val rows = listOf(
        ExpenseRow("5", today, "Café", cop(5_000), false),
        ExpenseRow("4", month.atDay(21), "Fruta", cop(10_000), false),
        ExpenseRow("3", month.atDay(15), "Panadería", cop(15_000), false),
        ExpenseRow("2", month.atDay(8), "Almuerzo", cop(30_000), false)
    )
    val figures = DetailInsights(
        ReciboPerDay(cop(10_000), 10), null, null, 22, cop(20_000),
        ExpenseRow("1", month.atDay(1), "Mercado", cop(40_000), false), DayOfWeek.THURSDAY,
        cop(31_667),
        listOf(
            DetailWeek(1, month.atDay(1), month.atDay(7), cop(40_000), 40_000, false, null, 40_000),
            DetailWeek(
                2,
                month.atDay(8),
                month.atDay(14),
                cop(30_000),
                30_000,
                false,
                null,
                30_000
            ),
            DetailWeek(
                3,
                month.atDay(15),
                month.atDay(21),
                cop(25_000),
                25_000,
                false,
                null,
                25_000
            ),
            DetailWeek(4, today, month.atDay(28), cop(5_000), 5_000, true, cop(70_000), 75_000)
        ),
        cop(10_000),
        listOf(
            DetailHistoryMonth(YearMonth.of(2026, 8), cop(20_000), 20_000, cop(200_000), 200_000),
            DetailHistoryMonth(YearMonth.of(2026, 9), cop(0), 0, cop(200_000), 200_000),
            DetailHistoryMonth(month, cop(100_000), 100_000, cop(200_000), 200_000, current = true)
        )
    )
    val category = ReciboCategory(
        "food", "Alimentación", "restaurant", false,
        BudgetFigure(ReciboHeadline.Remaining, cop(100_000), ReciboStamp.OnTrack),
        cop(100_000), cop(100_000), cop(200_000), 0.5f, false, 5
    )
    SelloTheme(ink = ink, darkTheme = dark, reducedMotion = true) {
        CategoryDetailScreen(
            CategoryDetailState.Ready(
                category,
                month,
                today,
                false,
                true,
                ExpenseListState.Rows(rows, true),
                insights = DetailInsightsState.Ready(figures)
            ),
            monthNames(),
            dateLabels(),
            false,
            {},
            {},
            PaddingValues(12.dp),
            modifier = Modifier.background(SelloTheme.colors.desk)
        )
    }
}

@Preview(name = "Category detail · Cobalto light", locale = "es", heightDp = 1600)
@Preview(name = "Category detail · font 1.3", locale = "es", fontScale = 1.3f)
@Composable
private fun CategoryCobaltoLight() = CategoryDetailPreview()

@Preview(name = "Category detail · Cobalto dark", locale = "es")
@Composable
private fun CategoryCobaltoDark() = CategoryDetailPreview(dark = true)

@Preview(name = "Category detail · Violeta light", locale = "es")
@Composable
private fun CategoryVioletaLight() = CategoryDetailPreview(SelloInk.Violeta)

@Preview(name = "Category detail · Violeta dark", locale = "es")
@Composable
private fun CategoryVioletaDark() = CategoryDetailPreview(SelloInk.Violeta, dark = true)
