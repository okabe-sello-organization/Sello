package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What a category's detail looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryDetailScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val food = ReciboCategory(
        id = "a",
        name = "Alimentación",
        iconKey = "restaurant",
        archived = false,
        figure = BudgetFigure(ReciboHeadline.Remaining, plain("287.600"), ReciboStamp.OnTrack),
        cellAmount = plain("287.600"),
        spent = plain("612.400"),
        limit = plain("900.000"),
        fraction = 0.68f,
        over = false,
        expenseCount = 13
    )

    private val rows = listOf(
        ExpenseRow("1", LocalDate.of(2026, 10, 21), "Rappi, almuerzo", plain("20.600"), false),
        ExpenseRow("2", LocalDate.of(2026, 10, 20), null, plain("33.500"), false),
        ExpenseRow("3", LocalDate.of(2026, 10, 28), "Adelantado", plain("1.000"), true)
    )

    private val insights = DetailInsights(
        allowance = ReciboPerDay(plain("31.950"), 9),
        comparisonMonth = YearMonth.of(2026, 9),
        comparison = "+6,7 %",
        comparisonDirection = DetailComparisonDirection.Higher,
        elapsedDays = 22,
        averageExpense = plain("20.000"),
        largest = rows.first(),
        weekday = DayOfWeek.THURSDAY,
        weeklyAverage = plain("31.667"),
        weeks = listOf(
            DetailWeek(
                4,
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 28),
                plain("5.000"),
                5_000,
                true,
                plain("70.000"),
                75_000
            )
        ),
        historicalAverage = plain("10.000"),
        history = listOf(
            DetailHistoryMonth(
                YearMonth.of(2026, 8),
                plain("20.000"),
                20_000,
                plain("200.000"),
                200_000
            ),
            DetailHistoryMonth(YearMonth.of(2026, 9), plain("0"), 0, plain("200.000"), 200_000),
            DetailHistoryMonth(
                YearMonth.of(2026, 10),
                plain("100.000"),
                100_000,
                plain("500.000"),
                500_000,
                current = true
            )
        )
    )

    private val ready = CategoryDetailState.Ready(
        category = food,
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 21),
        stale = false,
        canRecord = true,
        expenses = ExpenseListState.Rows(rows, more = true),
        insights = DetailInsightsState.Ready(insights)
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            CategoryDetailScreen(
                state = ready,
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = {},
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/category-detail-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
