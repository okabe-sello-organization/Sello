package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Pagos fijos on category detail (SELLO-135): what each part opens. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryFixedScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var all = 0
    private var adjusted = 0
    private val actions = mutableListOf<CategoryDetailAction>()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val reserved = CategoryReserved(
        BudgetFigure(ReciboHeadline.ShortForPayments, plain("90.000"), ReciboStamp.Review),
        ReciboReserved(plain("290.000"), 2),
        ReciboShortfall(plain("200.000"), false, plain("290.000"))
    )

    private fun state(fixed: DetailFixedState) = CategoryDetailState.Ready(
        category = ReciboCategory(
            id = "h",
            name = "Hogar",
            iconKey = "home",
            archived = false,
            figure = BudgetFigure(ReciboHeadline.Remaining, plain("200.000"), ReciboStamp.OnTrack),
            cellAmount = plain("200.000"),
            spent = plain("1.300.000"),
            limit = plain("1.500.000"),
            fraction = 0.87f,
            over = false,
            expenseCount = 1,
            reserved = reserved
        ),
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 22),
        stale = false,
        canRecord = true,
        expenses = ExpenseListState.Empty,
        insights = DetailInsightsState.Loading,
        fixed = fixed
    )

    private val ready = DetailFixedState.Ready(
        upcoming = listOf(
            DetailUpcomingPayment("2", "Internet", LocalDate.of(2026, 10, 23), plain("90.000"))
        ),
        later = listOf(
            DetailSchedule(
                "1",
                "Arriendo",
                (RepeatRule.Monthly.of(5) as Outcome.Success).value,
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 11, 5),
                null,
                plain("950.000")
            )
        )
    )

    private fun show(fixed: DetailFixedState) = rule.showSello(Look()) {
        CategoryDetailScreen(
            state = state(fixed),
            months = monthNames(),
            dates = dateLabels(),
            showName = false,
            onAction = { actions += it },
            onRecordHere = {},
            contentPadding = PaddingValues(12.dp),
            onOpenSchedule = { opened += it },
            onAllSchedules = { all++ },
            onAdjustBudget = { adjusted++ }
        )
    }

    @Test
    fun eachPaymentOpensItsScheduleAndTheLinkOpensThemAll() {
        show(ready)

        rule.onNodeWithTag("$CATEGORY_DETAIL_FIXED_TAG:coming:2.2026-10-23").performScrollTo()
            .performClick()
        rule.onNodeWithTag("$CATEGORY_DETAIL_FIXED_TAG:later:1").performScrollTo().performClick()
        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_ALL_TAG).performScrollTo().performClick()

        assertEquals(listOf("2", "1"), opened)
        assertEquals(1, all)
    }

    @Test
    fun revisarOffersAjustarPresupuestoAndSaysWhatIsReserved() {
        show(ready)

        rule.onNodeWithTag(RECIBO_SHORTFALL_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_RESERVED_TAG).assertIsDisplayed().performClick()
        rule.onNodeWithTag(RECIBO_ADJUST_TAG).performClick()

        assertEquals(1, adjusted)
    }

    @Test
    fun tomorrowsPaymentSaysSo() {
        show(ready)

        rule.onNodeWithText("Mañana, vie 23 oct", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aListReadBeforeAFailedReadSaysItIsOlderAndOffersToReadAgain() {
        show(ready.copy(stale = true))

        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_STALE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performScrollTo().performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Retry), actions)
    }

    @Test
    fun aFailedListOffersToReadAgain() {
        show(DetailFixedState.Failed)

        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_FAILED_TAG).assertIsDisplayed()
        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_TAG).assertDoesNotExist()
    }

    @Test
    fun aHiddenSlipDrawsNothing() {
        show(DetailFixedState.Hidden)

        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_TAG).assertDoesNotExist()
        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_LOADING_TAG).assertDoesNotExist()
    }
}
