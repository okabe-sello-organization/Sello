package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Category detail with its fixed payments (SELLO-135, docs/design/v2/categoria-fijos.html),
 * kept as committed images (ADR 0022). Hogar on jue 22 oct 2026: 1.500.000 limit,
 * 950.000 spent, Internet and Servicios públicos (290.000) still to come.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryFixedScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val today = LocalDate.of(2026, 10, 22)

    private fun home(spent: String, reserved: CategoryReserved) = ReciboCategory(
        id = "h",
        name = "Hogar",
        iconKey = "home",
        archived = false,
        figure = BudgetFigure(ReciboHeadline.Remaining, plain("550.000"), ReciboStamp.OnTrack),
        cellAmount = plain("550.000"),
        spent = plain(spent),
        limit = plain("1.500.000"),
        fraction = 0.63f,
        over = false,
        expenseCount = 1,
        reserved = reserved
    )

    private val fixed = DetailFixedState.Ready(
        upcoming = listOf(
            DetailUpcomingPayment("2", "Internet", LocalDate.of(2026, 10, 23), plain("90.000")),
            DetailUpcomingPayment(
                "3",
                "Servicios públicos",
                LocalDate.of(2026, 10, 28),
                plain("200.000")
            )
        ),
        later = listOf(
            DetailSchedule(
                "1",
                "Arriendo",
                (RepeatRule.Monthly.of(5) as Outcome.Success).value,
                LocalDate.of(2026, 9, 5),
                next = LocalDate.of(2026, 11, 5),
                pausedFrom = null,
                amount = plain("950.000")
            ),
            DetailSchedule(
                "4",
                "Aseo",
                (RepeatRule.EveryWeeks.of(2) as Outcome.Success).value,
                LocalDate.of(2026, 9, 1),
                next = null,
                pausedFrom = LocalDate.of(2026, 10, 12),
                amount = plain("60.000")
            )
        )
    )

    private fun ready(category: ReciboCategory, perDay: String, fixed: DetailFixedState) =
        CategoryDetailState.Ready(
            category = category,
            month = YearMonth.of(2026, 10),
            today = today,
            stale = false,
            canRecord = true,
            expenses = ExpenseListState.Rows(
                listOf(
                    ExpenseRow("9", LocalDate.of(2026, 10, 5), "Arriendo", plain("950.000"), false)
                ),
                more = false
            ),
            insights = DetailInsightsState.Ready(
                DetailInsights(
                    allowance = ReciboPerDay(plain(perDay), 10),
                    comparisonMonth = null,
                    comparison = null,
                    elapsedDays = 22,
                    averageExpense = plain("950.000"),
                    largest = null,
                    weekday = null,
                    weeklyAverage = null,
                    weeks = emptyList(),
                    historicalAverage = null,
                    history = emptyList()
                )
            ),
            fixed = fixed
        )

    private val enough = ready(
        home(
            "950.000",
            CategoryReserved(
                BudgetFigure(ReciboHeadline.Remaining, plain("260.000"), ReciboStamp.OnTrack),
                ReciboReserved(plain("290.000"), 2),
                shortfall = null
            )
        ),
        "26.000",
        fixed
    )

    private fun capture(name: String, state: CategoryDetailState, look: Look = Look()) {
        rule.showSello(look) {
            CategoryDetailScreen(
                state = state,
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = {},
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/category-fixed-$name-${look.label}.png")
    }

    @Test
    fun paymentsStillToCome() = capture("coming", enough)

    @Test
    fun paymentsStillToComeDark() = capture("coming", enough, Look(dark = true))

    /** 200.000 left, 290.000 to come: Revisar, 90.000 short, nothing per day. */
    @Test
    fun revisar() = capture(
        "review",
        ready(
            home(
                "1.300.000",
                CategoryReserved(
                    BudgetFigure(
                        ReciboHeadline.ShortForPayments,
                        plain("90.000"),
                        ReciboStamp.Review
                    ),
                    ReciboReserved(plain("290.000"), 2),
                    ReciboShortfall(plain("200.000"), false, plain("290.000"))
                )
            ),
            "0",
            fixed
        )
    )

    @Test
    fun listFailed() = capture("failed", enough.copy(fixed = DetailFixedState.Failed))
}
