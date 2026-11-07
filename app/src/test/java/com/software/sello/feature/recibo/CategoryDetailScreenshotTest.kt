package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.navigation.CategoryEditAction
import com.software.sello.navigation.ShellDetail
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.archived
import com.software.sello.navigation.detailTitle
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.carry.CarryProblem
import com.software.sello.presentation.carry.CarryProblemKind
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

    private fun capture(
        look: Look,
        file: String = "category-detail-${look.label}",
        several: Boolean = false,
        state: CategoryDetailState.Ready = ready
    ) {
        rule.showSello(look) {
            CategoryDetailScreen(
                state = state,
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = {},
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp),
                onRecordSeveral = if (several) ({}) else null
            )
        }
        rule.captureStill("src/test/screenshots/$file.png")
    }

    /** Anotar varios at the foot of the latest expenses (SELLO-131). */
    @Test
    fun variosClaro() = capture(Look(), "category-detail-varios-claro", several = true)

    @Test
    fun variosOscuro() = capture(Look(dark = true), "category-detail-varios-oscuro", several = true)

    /** A deletion whose outcome is not known, asked about again with its own operation (#331). */
    @Test
    fun deleteUnknownClaro() = capture(
        Look(),
        "category-detail-delete-unknown-claro",
        state = ready.copy(deleteProblem = DeleteProblem.Unknown)
    )

    @Test
    fun deleteUnknownOscuro() = capture(
        Look(dark = true),
        "category-detail-delete-unknown-oscuro",
        state = ready.copy(deleteProblem = DeleteProblem.Unknown)
    )

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** Recibo's month as already read, listing [food]: what names the detail meanwhile. */
    private val recibo = ReciboState.Month(
        ReciboSummary(ReciboAnswerLabel.Remaining, plain("287.600"), ReciboStamp.Over),
        listOf(food),
        stale = false
    )

    private fun shot(
        name: String,
        state: CategoryDetailState,
        dark: Boolean,
        month: YearMonth = YearMonth.of(2026, 10),
        announcement: Boolean = false,
        listed: ReciboState = recibo
    ) {
        rule.showSello(Look(dark = dark)) {
            // Opened in Recibo, on a phone: ReciboDestinations.kt hands it to the frame as
            // its ShellDetail, which draws its own title row (ShellScreen.kt): the name
            // Recibo already lists while the detail is read (DetailIdentity.kt), and the
            // edit pencil, disabled until the detail is ready.
            ShellScreen(
                octoberShell.copy(selectedMonth = month, detailCategoryId = food.id),
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Recibo),
                detail = ShellDetail(
                    detailTitle(food.id, state, listed)
                        ?: stringResource(R.string.category_detail_title_fallback),
                    archived = archived(state),
                    actions = { CategoryEditAction(state is CategoryDetailState.Ready) {} }
                ) { padding ->
                    CategoryDetailScreen(
                        state = state,
                        months = monthNames(),
                        dates = dateLabels(),
                        showName = false,
                        onAction = {},
                        onRecordHere = {},
                        contentPadding = padding,
                        showPastLimitAnnouncement = announcement
                    )
                }
            ) {}
        }
        rule.captureStill(
            "src/test/screenshots/category-detail-$name-${if (dark) "oscuro" else "claro"}.png"
        )
    }

    @Test fun loadingClaro() = shot("loading", CategoryDetailState.Loading, dark = false)

    @Test fun loadingOscuro() = shot("loading", CategoryDetailState.Loading, dark = true)

    @Test fun failedClaro() = shot("failed", CategoryDetailState.Failed, dark = false)

    @Test fun failedOscuro() = shot("failed", CategoryDetailState.Failed, dark = true)

    @Test fun goneClaro() = shot("gone", CategoryDetailState.Gone, dark = false)

    @Test fun goneOscuro() = shot("gone", CategoryDetailState.Gone, dark = true)

    @Test fun staleClaro() = shot("stale", ready.copy(stale = true), dark = false)

    @Test fun staleOscuro() = shot("stale", ready.copy(stale = true), dark = true)

    private val listsLoading = ready.copy(
        expenses = ExpenseListState.Loading,
        insights = DetailInsightsState.Loading
    )

    @Test fun listsLoadingClaro() = shot("lists-loading", listsLoading, dark = false)

    @Test fun listsLoadingOscuro() = shot("lists-loading", listsLoading, dark = true)

    private val listsFailed = ready.copy(
        expenses = ExpenseListState.Failed,
        insights = DetailInsightsState.Failed
    )

    @Test fun listsFailedClaro() = shot("lists-failed", listsFailed, dark = false)

    @Test fun listsFailedOscuro() = shot("lists-failed", listsFailed, dark = true)

    private val empty = ready.copy(expenses = ExpenseListState.Empty)

    @Test fun emptyClaro() = shot("empty", empty, dark = false)

    @Test fun emptyOscuro() = shot("empty", empty, dark = true)

    /** Alimentación's carry from September could not be read, and nothing was read before. */
    private val problem =
        CarryProblem("a", "Alimentación", CarryProblemKind.Missing, YearMonth.of(2026, 9))

    @Test
    fun carryFailedClaro() = shot("carry-failed", CategoryDetailState.CarryFailed(problem), false)

    @Test
    fun carryFailedOscuro() = shot("carry-failed", CategoryDetailState.CarryFailed(problem), true)

    private val carryProblem = ready.copy(stale = true, carryProblem = problem)

    @Test fun carryProblemClaro() = shot("carry-problem", carryProblem, dark = false)

    @Test fun carryProblemOscuro() = shot("carry-problem", carryProblem, dark = true)

    private val archivedFood = ready.copy(category = food.copy(archived = true), canRecord = false)

    @Test fun archivedClaro() = shot("archived", archivedFood, dark = false)

    @Test fun archivedOscuro() = shot("archived", archivedFood, dark = true)

    /** The largest expense of the finished September. */
    private val mercado =
        ExpenseRow("4", LocalDate.of(2026, 9, 28), "Mercado", plain("112.000"), false)

    private fun week(number: Int, from: Int, until: Int, digits: String, value: Long) = DetailWeek(
        number,
        LocalDate.of(2026, 9, from),
        LocalDate.of(2026, 9, until),
        plain(digits),
        value,
        false,
        null,
        value
    )

    private fun month(month: Int, digits: String, value: Long) = DetailHistoryMonth(
        YearMonth.of(2026, month),
        plain(digits),
        value,
        plain("900.000"),
        900_000
    )

    /**
     * September, finished, seen on 21 October: 13 expenses of 806.000 against 900.000, so
     * 94.000 left over. A finished month has no allowance (CategoryInsightsPolicy). Against
     * August's 775.000: 31.000 / 775.000 = +4,0 %. 806.000 / 13 = 62.000 per expense; five
     * weeks 150.000 + 185.300 + 160.000 + 230.700 + 80.000 = 806.000, 806.000 / 5 = 161.200
     * a week; July to September (760.000 + 775.000 + 806.000) / 3 = 780.333.
     */
    private val september = ready.copy(
        category = food.copy(
            figure = BudgetFigure(
                ReciboHeadline.LeftOver,
                plain("94.000"),
                ReciboStamp.WithinLimit
            ),
            cellAmount = plain("94.000"),
            spent = plain("806.000"),
            fraction = 0.9f
        ),
        month = YearMonth.of(2026, 9),
        expenses = ExpenseListState.Rows(
            listOf(
                mercado,
                ExpenseRow("5", LocalDate.of(2026, 9, 14), null, plain("45.300"), false)
            ),
            more = true
        ),
        insights = DetailInsightsState.Ready(
            DetailInsights(
                allowance = null,
                comparisonMonth = YearMonth.of(2026, 8),
                comparison = "+4,0 %",
                comparisonDirection = DetailComparisonDirection.Higher,
                elapsedDays = 30,
                averageExpense = plain("62.000"),
                largest = mercado,
                weekday = DayOfWeek.MONDAY,
                weeklyAverage = plain("161.200"),
                weeks = listOf(
                    week(1, 1, 7, "150.000", 150_000),
                    week(2, 8, 14, "185.300", 185_300),
                    week(3, 15, 21, "160.000", 160_000),
                    week(4, 22, 28, "230.700", 230_700),
                    week(5, 29, 30, "80.000", 80_000)
                ),
                historicalAverage = plain("780.333"),
                history = listOf(
                    month(7, "760.000", 760_000),
                    month(8, "775.000", 775_000),
                    month(9, "806.000", 806_000)
                )
            )
        )
    )

    @Test
    fun pastMonthClaro() = shot(
        "past-month",
        september,
        dark = false,
        month = YearMonth.of(2026, 9),
        announcement = true
    )

    @Test
    fun pastMonthOscuro() = shot(
        "past-month",
        september,
        dark = true,
        month = YearMonth.of(2026, 9),
        announcement = true
    )

    /** Lo que más se repite (SELLO-195), the mock's three rows, right after two latest expenses. */
    private val repeating = ready.copy(
        expenses = ExpenseListState.Rows(rows.take(2), more = false),
        insights = DetailInsightsState.Ready(
            insights.copy(
                repeats = listOf(
                    DetailRepeat("Mercado D1", 4, plain("385.600")),
                    DetailRepeat("Almuerzo", 9, plain("162.000")),
                    DetailRepeat("Rappi, almuerzo", 6, plain("123.600"))
                )
            )
        )
    )

    @Test fun repeatsClaro() = shot("repeats", repeating, dark = false)

    @Test fun repeatsOscuro() = shot("repeats", repeating, dark = true)

    private val checking = ready.copy(deleteProblem = DeleteProblem.Unknown, checkingDelete = true)

    @Test fun deleteCheckingClaro() = shot("delete-checking", checking, dark = false)

    @Test fun deleteCheckingOscuro() = shot("delete-checking", checking, dark = true)

    private val refused = ready.copy(deleteProblem = DeleteProblem.Failed)

    @Test fun deleteRefusedClaro() = shot("delete-refused", refused, dark = false)

    @Test fun deleteRefusedOscuro() = shot("delete-refused", refused, dark = true)

    /** Opened before Recibo's month was read: the fallback title. */
    @Test
    fun untitledClaro() = shot(
        "untitled",
        CategoryDetailState.Loading,
        dark = false,
        listed = ReciboState.Loading
    )

    @Test
    fun untitledOscuro() = shot(
        "untitled",
        CategoryDetailState.Loading,
        dark = true,
        listed = ReciboState.Loading
    )
}
