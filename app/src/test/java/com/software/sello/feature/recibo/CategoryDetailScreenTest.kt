package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.selloColors
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<CategoryDetailAction>()
    private var recordHere = 0
    private val edited = mutableListOf<String>()
    private var editCategory = 0
    private var openedAll = 0
    private var pastLimitAnnouncementDismissed = 0

    private fun show(
        state: CategoryDetailState,
        showName: Boolean = false,
        showPastLimitAnnouncement: Boolean = false,
        ink: SelloInk = SelloInk.Cobalto,
        dark: Boolean = false
    ) {
        rule.showSello(Look(ink = ink, dark = dark)) {
            CategoryDetailScreen(
                state = state,
                months = monthNames(),
                dates = dateLabels(),
                showName = showName,
                onAction = { actions += it },
                onRecordHere = { recordHere++ },
                contentPadding = PaddingValues(12.dp),
                onEditExpense = { edited += it },
                onEditCategory = { editCategory++ },
                onShowAllExpenses = { openedAll++ },
                showPastLimitAnnouncement = showPastLimitAnnouncement,
                onDismissPastLimitAnnouncement = { pastLimitAnnouncementDismissed++ }
            )
        }
    }

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

    private fun ready(
        expenses: ExpenseListState,
        category: ReciboCategory = food,
        stale: Boolean = false,
        canRecord: Boolean = true
    ) = CategoryDetailState.Ready(
        category = category,
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 21),
        stale = stale,
        canRecord = canRecord,
        expenses = expenses
    )

    private fun inRow(id: String, text: String) =
        hasText(text) and hasAnyAncestor(hasTestTag(categoryExpenseTag(id)))

    private fun heroText(text: String) = rule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(CATEGORY_DETAIL_HEADER_TAG)),
        useUnmergedTree = true
    )

    private fun textStyle(text: String): androidx.compose.ui.text.TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        heroText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        return layouts.single().layoutInput.style
    }

    private fun footerFigures() = insightFigures().copy(
        allowance = ReciboPerDay(plain("31.950"), 9),
        comparisonMonth = YearMonth.of(2026, 9),
        comparison = "+6,7 %",
        comparisonDirection = DetailComparisonDirection.Higher
    )

    @Test
    fun heroFooterMatchesTheThreeEqualColumnsAndTypeSizesInTheReference() {
        show(
            ready(ExpenseListState.Rows(rows, more = false))
                .copy(insights = DetailInsightsState.Ready(footerFigures()))
        )
        heroText("9 días").assertIsDisplayed()
        heroText("9 días, incluido hoy").assertDoesNotExist()
        val labels = listOf("Para", "Al día", "Frente a sep")
            .map { heroText(it).getUnclippedBoundsInRoot() }
        assertEquals(labels[0].top, labels[1].top)
        assertEquals(labels[1].top, labels[2].top)
        // Centred in three equal columns, so the label centres are evenly spaced.
        val centres = labels.map { (it.left + it.right) / 2 }
        val firstGap = centres[1] - centres[0]
        val secondGap = centres[2] - centres[1]
        assertTrue(
            "The three columns must be equally spaced",
            kotlin.math.abs(firstGap.value - secondGap.value) <= 1f
        )
        listOf("9 días" to 0, "+6,7 %" to 2).forEach { (value, column) ->
            val bounds = heroText(value).getUnclippedBoundsInRoot()
            assertTrue(
                "$value must be centred under its label",
                kotlin.math.abs(((bounds.left + bounds.right) / 2 - centres[column]).value) <= 1f
            )
        }
        listOf("Para", "Al día", "Frente a sep").forEach {
            assertEquals(12.5.sp, textStyle(it).fontSize)
        }
        listOf("9 días", "+6,7 %").forEach {
            assertEquals(16.sp, textStyle(it).fontSize)
            assertEquals(FontWeight.W800, textStyle(it).fontWeight)
        }
    }

    @Test
    fun higherSpendingUsesTheReferencesAmberComparisonColor() {
        show(
            ready(ExpenseListState.Rows(rows, more = false))
                .copy(insights = DetailInsightsState.Ready(footerFigures()))
        )
        assertEquals(selloColors(SelloInk.Cobalto, false).semantic.warn, textStyle("+6,7 %").color)
    }

    @Test
    fun lowerSpendingUsesGreenInTheDarkVioletTheme() {
        val figures = footerFigures().copy(
            comparison = "−6,7 %",
            comparisonDirection = DetailComparisonDirection.Lower
        )
        show(
            ready(ExpenseListState.Rows(rows, more = false))
                .copy(insights = DetailInsightsState.Ready(figures)),
            ink = SelloInk.Violeta,
            dark = true
        )
        assertEquals(selloColors(SelloInk.Violeta, true).semantic.gain, textStyle("−6,7 %").color)
    }

    @Test
    fun anUnchangedComparisonUsesOrdinaryInkAndTheLastDayIsSingular() {
        val figures = footerFigures().copy(
            allowance = ReciboPerDay(plain("5.000"), 1),
            comparison = "0,0 %",
            comparisonDirection = DetailComparisonDirection.Equal
        )
        show(
            ready(ExpenseListState.Rows(rows, more = false))
                .copy(insights = DetailInsightsState.Ready(figures)),
            ink = SelloInk.Violeta
        )
        heroText("1 día").assertIsDisplayed()
        assertEquals(selloColors(SelloInk.Violeta, false).ink, textStyle("0,0 %").color)
    }

    @Test
    fun numbersUseTwoColumnsWithLabelsAboveValuesAndTheLargestNoteBelowItsAmount() {
        show(
            ready(ExpenseListState.Rows(rows, more = false))
                .copy(insights = DetailInsightsState.Ready(insightFigures()))
        )
        rule.onNodeWithTag("detail:numbers").performScrollTo()
        fun fact(text: String) = rule.onNode(
            hasText(text) and hasAnyAncestor(hasTestTag("detail:numbers")),
            useUnmergedTree = true
        )
        val count = fact("Gastos").getUnclippedBoundsInRoot()
        val largest = fact("El mayor").getUnclippedBoundsInRoot()
        val weekday = fact("Tu día más caro").getUnclippedBoundsInRoot()
        assertTrue(
            "The second row must contain two columns: $largest, $weekday",
            weekday.left > largest.right
        )
        assertEquals(largest.top, weekday.top)
        assertEquals(count.left, largest.left)
        val average = fact("En promedio").getUnclippedBoundsInRoot()
        assertEquals(count.top, average.top)
        assertEquals(average.left, weekday.left)
        val countValue = fact("13 en 22 días").getUnclippedBoundsInRoot()
        assertTrue("The count belongs under its label", countValue.top >= count.bottom)
        val amount = fact("20.600").getUnclippedBoundsInRoot()
        val caption = fact("Rappi, almuerzo · Hoy, mié 21 oct").getUnclippedBoundsInRoot()
        assertTrue(
            "The largest expense amount belongs under its label",
            amount.top >= largest.bottom
        )
        assertTrue("The note and date belong below the amount", caption.top >= amount.bottom)
        fact("Cada vez").assertDoesNotExist()
    }

    @Test
    fun sectionsFollowTheOwnersOrderAndChartsExposeExactRecordedAndAvailableAmounts() {
        val figures = insightFigures()
        show(
            ready(
                ExpenseListState.Rows(rows, more = false)
            ).copy(insights = DetailInsightsState.Ready(figures))
        )
        val positions = listOf(
            CATEGORY_DETAIL_HEADER_TAG,
            CATEGORY_DETAIL_LIST_TAG,
            "detail:numbers",
            "detail:weeks",
            "detail:history"
        )
            .map { rule.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.y }
        assertTrue(positions.toString(), positions.zipWithNext().all { (a, b) -> a < b })
        rule.onNodeWithTag("detail:weeks").performScrollTo()
        rule.onNodeWithContentDescription(
            "Del 22 al 28: gastado 5.000 pesos. Aún puedes gastar 70.000 pesos esta semana."
        )
            .assertExists().performClick()
        rule.onNodeWithText("Por gastar esta semana").assertExists()
        rule.onNodeWithTag("detail:history").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Un mes normal aquí").assertIsDisplayed()
    }

    private fun insightFigures() = DetailInsights(
        allowance = ReciboPerDay(plain("10.000"), 10), comparisonMonth = null, comparison = null,
        elapsedDays = 22,
        averageExpense = plain(
            "20.000"
        ),
        largest = rows.first(), weekday = java.time.DayOfWeek.THURSDAY,
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

    @Test
    fun latestExpensesAreACompactPreviewImmediatelyAfterTheHeader() {
        val five = (1..5).map { index ->
            ExpenseRow("$index", LocalDate.of(2026, 10, 21), "Compra $index", plain("1.000"), false)
        }
        show(ready(ExpenseListState.Rows(five, more = true)))

        rule.onNodeWithText("Últimos gastos").assertIsDisplayed()
        rule.onNodeWithText("Compra 4", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Compra 5", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Ver los 13").assertExists()
    }

    @Test
    fun aThousandExpensesReadWithTheirSeparator() {
        val five = (1..5).map { index ->
            ExpenseRow("$index", LocalDate.of(2026, 10, 21), "Compra $index", plain("1.000"), false)
        }
        show(ready(ExpenseListState.Rows(five, more = true), food.copy(expenseCount = 1_000)))

        rule.onNodeWithText("Ver los 1.000").assertExists()
    }

    @Test
    fun viewAllEndsFlushWithTheAmountsBelowIt() {
        // v1.1: "ver los 13" sits at the slip's content edge, the same edge as each amount.
        val counted = rows.filterNot { it.notCountedYet }
        show(ready(ExpenseListState.Rows(counted, more = true)))

        val label = rule.onNode(
            hasText("Ver los 13") and hasAnyAncestor(hasTestTag(CATEGORY_DETAIL_MORE_TAG)),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        val amount = rule.onNode(
            hasContentDescription("20.600 pesos") and
                hasAnyAncestor(hasTestTag(categoryExpenseTag("1"))),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        assertTrue(
            "label ends at ${label.right}, amounts at ${amount.right}",
            (label.right - amount.right).value in -1f..1f
        )
    }

    @Test
    fun theHeaderSaysWhatIsLeftSpentAndTheLimitExactlyAndInWords() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        rule.onNodeWithContentDescription("Te quedan aquí 287.600 pesos. Al día.")
            .assertIsDisplayed()
        // A line is read as one sentence, so its parts are looked for inside it.
        rule.onNode(
            hasContentDescription("612.400 pesos") and
                hasAnyAncestor(hasTestTag(CATEGORY_DETAIL_SPENT_TAG)),
            useUnmergedTree = true
        ).assertExists()
        rule.onNode(
            hasContentDescription("900.000 pesos") and
                hasAnyAncestor(hasTestTag(CATEGORY_DETAIL_LIMIT_TAG)),
            useUnmergedTree = true
        ).assertExists()
        // Its own screen is titled by the frame; beside Recibo it names itself.
        rule.onNodeWithText("Alimentación").assertDoesNotExist()
    }

    @Test
    fun besideReciboItNamesTheCategoryItself() {
        show(ready(ExpenseListState.Rows(rows, more = false)), showName = true)

        rule.onNodeWithText("Alimentación").assertIsDisplayed()
    }

    @Test
    fun eachExpenseShowsItsNoteDayAndExactAmountAndTheCountIsOfAllOfThem() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        rule.onNodeWithText("Ver todos").performScrollTo().assertIsDisplayed()
        rule.onNode(inRow("1", "Rappi, almuerzo"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("1", "Hoy, mié 21 oct"), useUnmergedTree = true).assertExists()
        rule.onNode(
            hasContentDescription("20.600 pesos") and
                hasAnyAncestor(hasTestTag(categoryExpenseTag("1"))),
            useUnmergedTree = true
        ).assertExists()
        rule.onNode(inRow("2", "Sin nota"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("2", "mar 20 oct"), useUnmergedTree = true).assertExists()
        rule.onNode(
            inRow("3", "mié 28 oct. Fecha posterior a hoy: todavía no cuenta"),
            useUnmergedTree = true
        ).assertExists()
        // Everything is listed, so there is nothing more to ask for.
        rule.onNodeWithTag(CATEGORY_DETAIL_MORE_TAG).assertExists()
    }

    @Test
    fun thePreviewOpensTheFullExpenseBrowserInsteadOfExpandingInPlace() {
        show(ready(ExpenseListState.Rows(rows, more = true)))
        rule.onNodeWithTag(CATEGORY_DETAIL_MORE_TAG).performScrollTo().performClick()
        assertEquals(1, openedAll)
        assertEquals(emptyList<CategoryDetailAction>(), actions)
    }

    @Test
    fun noExpensesIsSaidForThatMonthWithAWayToRecordOneHere() {
        show(ready(ExpenseListState.Empty))

        rule.onNodeWithTag(CATEGORY_DETAIL_EMPTY_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Sin gastos en octubre").assertIsDisplayed()
        rule.onNodeWithText("Anotar aquí").performScrollTo().performClick()

        assertEquals(1, recordHere)
        rule.onNodeWithTag(CATEGORY_DETAIL_LIST_FAILED_TAG).assertDoesNotExist()
    }

    @Test
    fun whereNothingCanBeRecordedTheEmptyListOffersNothing() {
        show(ready(ExpenseListState.Empty, canRecord = false))

        rule.onNodeWithText("Sin gastos en octubre").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Anotar aquí").assertDoesNotExist()
    }

    @Test
    fun aListThatCouldNotBeReadIsAnErrorWithTheFiguresStillShownAndNeverNoExpenses() {
        show(ready(ExpenseListState.Failed))

        rule.onNodeWithTag(CATEGORY_DETAIL_LIST_FAILED_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("No pudimos leer los gastos de esta categoría.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Te quedan aquí 287.600 pesos. Al día.").assertExists()
        rule.onNodeWithTag(CATEGORY_DETAIL_EMPTY_TAG).assertDoesNotExist()
        rule.onNodeWithText("Sin gastos en octubre").assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performScrollTo().performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Retry), actions)
    }

    @Test
    fun aCategoryWithoutALimitSaysWhatWasSpentAndWhyThereIsNothingToJudge() {
        val art = food.copy(
            name = "Arte",
            figure = BudgetFigure(ReciboHeadline.Spent, plain("80.000"), ReciboStamp.NoLimit),
            spent = plain("80.000"),
            limit = null,
            fraction = null
        )

        show(ready(ExpenseListState.Rows(rows, more = false), category = art))

        rule.onNodeWithContentDescription("Has gastado aquí 80.000 pesos. Sin límite.")
            .assertIsDisplayed()
        rule.onNode(
            hasText("Sin límite") and hasAnyAncestor(hasTestTag(CATEGORY_DETAIL_LIMIT_TAG)),
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun anArchivedCategoryIsStampedArchivadaWhereItsVerdictWouldBe() {
        val archived = food.copy(archived = true)

        show(ready(ExpenseListState.Rows(rows, more = false), category = archived))

        // The stamp replaces "Al día", in sight and in what is read aloud.
        rule.onNodeWithTag(CATEGORY_DETAIL_ARCHIVED_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Te quedan aquí 287.600 pesos. Archivada.")
            .assertIsDisplayed()
    }

    @Test
    fun anOverspentCategoryAndAFinishedMonthSayItInWords() {
        val over = food.copy(
            figure = BudgetFigure(ReciboHeadline.Overspent, plain("21.500"), ReciboStamp.OverLimit),
            over = true
        )

        show(ready(ExpenseListState.Rows(rows, more = false), category = over))

        rule.onNodeWithContentDescription("Te pasaste aquí por 21.500 pesos. Excedido.")
            .assertIsDisplayed()
    }

    @Test
    fun olderFiguresCarryANoticeAndFailedGoneAndLoadingAreTheirOwnStates() {
        show(ready(ExpenseListState.Rows(rows, more = false), stale = true))

        rule.onNodeWithTag(CATEGORY_DETAIL_STALE_TAG).assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Retry), actions)
    }

    @Test
    fun aCategoryThatCouldNotBeReadShowsNoFigureAndOffersRetry() {
        show(CategoryDetailState.Failed)

        rule.onNodeWithTag(CATEGORY_DETAIL_FAILED_TAG).assertIsDisplayed()
        rule.onNodeWithText("No pudimos leer esta categoría.").assertIsDisplayed()
        rule.onNodeWithTag(CATEGORY_DETAIL_HEADER_TAG).assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Retry), actions)
    }

    @Test
    fun aCategoryWithNothingInTheMonthSaysSoAndLoadingShowsNoFigure() {
        show(CategoryDetailState.Gone)

        rule.onNodeWithTag(CATEGORY_DETAIL_GONE_TAG).assertIsDisplayed()
        rule.onNodeWithText("Nada aquí este mes").assertIsDisplayed()
        rule.onNodeWithTag(CATEGORY_DETAIL_HEADER_TAG).assertDoesNotExist()
    }

    private fun numbered(numbers: List<Int>) = ExpenseListState.Rows(
        numbers.map {
            ExpenseRow(
                "n$it",
                LocalDate.of(2026, 10, 1 + it % 20),
                "Gasto $it",
                plain("${1000 + it}"),
                false
            )
        },
        more = false
    )

    /** Which piece of the screen is drawing each expense, by the expense's identifier. */
    private fun drawnBy(numbers: List<Int>): Map<Int, Int> = numbers.associateWith {
        rule.onNodeWithTag(categoryExpenseTag("n$it")).fetchSemanticsNode().id
    }

    private fun showChanging(first: ExpenseListState): (ExpenseListState) -> Unit {
        var state by mutableStateOf<CategoryDetailState>(ready(first))
        rule.showSello {
            CategoryDetailScreen(
                state = state,
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = { actions += it },
                onRecordHere = { recordHere++ },
                contentPadding = PaddingValues(12.dp)
            )
        }
        return { next ->
            state = ready(next)
            rule.waitForIdle()
        }
    }

    @Test
    fun aNewExpenseAtTheTopLeavesEveryRowThatWasThereBeingTheSameRow() {
        val before = (1..3).toList()
        val change = showChanging(numbered(before))
        val drawn = drawnBy(before)

        // Saved while the list is open: it goes first and the others move down one.
        change(numbered(listOf(99) + before))

        assertEquals(drawn, drawnBy(before))
        rule.onNodeWithTag(categoryExpenseTag("n99")).assertExists()
    }

    @Test
    fun aRowTakenOutOfTheMiddleLeavesTheOthersBeingTheSameRows() {
        val before = (1..3).toList()
        val change = showChanging(numbered(before))
        val after = before - 2
        val drawn = drawnBy(after)

        change(numbered(after))

        assertEquals(drawn, drawnBy(after))
        rule.onNodeWithTag(categoryExpenseTag("n2")).assertDoesNotExist()
    }

    @Test
    fun rowsThatChangePlacesAreStillThemselves() {
        val before = (1..4).toList()
        val change = showChanging(numbered(before))
        val drawn = drawnBy(before)

        change(numbered(before.reversed()))

        assertEquals(drawn, drawnBy(before))
        // And each still shows its own text, in its new place.
        rule.onNode(inRow("n3", "Gasto 3"), useUnmergedTree = true).assertExists()
    }

    // --- Correcting an expense from the list ---

    @Test
    fun pressingAnExpenseOpensItToBeCorrectedAndDeletesNothing() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        rule.onNodeWithTag(categoryExpenseTag("2")).performScrollTo().performClick()

        assertEquals(listOf("2"), edited)
        assertEquals(emptyList<CategoryDetailAction>(), actions)
    }

    @Test
    fun aLongPressOffersToEditOrDeleteThatExpense() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        rule.onNodeWithTag(categoryExpenseTag("1")).performScrollTo()
            .performTouchInput { longClick() }
        rule.onNode(hasText("Borrar") and hasAnyAncestor(hasTestTag(ACTION_LINE_MENU_TAG)))
            .performClick()
        rule.onNodeWithTag(categoryExpenseTag("2")).performTouchInput { longClick() }
        rule.onNode(hasText("Editar") and hasAnyAncestor(hasTestTag(ACTION_LINE_MENU_TAG)))
            .performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Delete("1")), actions)
        assertEquals(listOf("2"), edited)
    }

    @Test
    fun swipingAnExpenseAwayDeletesThatOneOnce() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        rule.onNodeWithTag(categoryExpenseTag("3")).performScrollTo()
            .performTouchInput { swipeLeft(startX = right - 4f, endX = left + 4f) }
        rule.waitForIdle()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Delete("3")), actions)
        assertEquals(emptyList<String>(), edited)
    }

    @Test
    fun aScreenReaderCanEditOrDeleteAnExpenseByNameAndHearsItWhole() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        val node = rule.onNodeWithTag(categoryExpenseTag("1")).fetchSemanticsNode()
        val offered = node.config[SemanticsActions.CustomActions]
        rule.runOnUiThread { offered.forEach { it.action() } }

        assertEquals(listOf("Editar", "Borrar"), offered.map { it.label })
        assertEquals(listOf("1"), edited)
        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.Delete("1")), actions)
        // Read as one line: what, when and exactly how much.
        rule.onNode(inRow("1", "Rappi, almuerzo"), useUnmergedTree = true).assertExists()
        rule.onNode(
            hasContentDescription("20.600 pesos") and
                hasAnyAncestor(hasTestTag(categoryExpenseTag("1"))),
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    fun theListSaysHowAnExpenseIsCorrectedOrDeleted() {
        show(ready(ExpenseListState.Rows(rows, more = false)))

        rule.onNodeWithTag(CATEGORY_DETAIL_HINT_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Toca un gasto para corregirlo. Para borrarlo, deslízalo o mantenlo presionado."
        ).assertExists()
    }

    @Test
    fun aDeletionThatDidNotHappenIsSaidAndTheExpenseStaysListed() {
        val texts = mapOf(
            DeleteProblem.Gone to "Ese gasto ya estaba borrado.",
            DeleteProblem.Changed to "Ese gasto cambió mientras tanto. No se borró; revísalo.",
            DeleteProblem.DataChanged to "Tus datos cambiaron mientras tanto. No se borró nada.",
            DeleteProblem.Failed to "No se pudo borrar. El gasto sigue anotado."
        )
        var problem by mutableStateOf(DeleteProblem.Gone)
        rule.showSello {
            CategoryDetailScreen(
                state = ready(
                    ExpenseListState.Rows(rows, more = false)
                ).copy(deleteProblem = problem),
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = { actions += it },
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp)
            )
        }

        texts.forEach { (which, text) ->
            problem = which
            rule.waitForIdle()
            rule.onNodeWithTag(
                CATEGORY_DETAIL_DELETE_PROBLEM_TAG
            ).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(text).assertExists()
            rule.onNodeWithTag(categoryExpenseTag("1")).assertExists()
        }
    }

    // --- Changing the category itself ---

    @Test
    fun theCategoryCanBeEditedFromItsHeader() {
        show(ready(ExpenseListState.Rows(rows, more = false)), showName = true)

        rule.onNodeWithTag(CATEGORY_DETAIL_EDIT_TAG).performScrollTo().assertIsDisplayed()
            .performClick()

        rule.onNodeWithContentDescription("Editar categoría").assertExists()
        assertEquals(1, editCategory)
        assertEquals(emptyList<CategoryDetailAction>(), actions)
    }

    @Test
    fun theMonthInProgressHasNoLineAboutCorrectingItsLimit() {
        // The state's month is October and its "today" is 21 October: the month in progress.
        show(ready(ExpenseListState.Empty))
        rule.onNodeWithTag(CATEGORY_DETAIL_PAST_HELP_TAG).assertDoesNotExist()
    }

    @Test
    fun aFinishedMonthSaysItsLimitCanStillBeCorrected() {
        show(ready(ExpenseListState.Empty).copy(today = LocalDate.of(2026, 11, 3)))

        rule.onNodeWithTag(CATEGORY_DETAIL_PAST_HELP_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Octubre ya terminó. Su límite aún se puede corregir en Editar categoría, " +
                "sin cambiar los demás meses."
        ).assertExists()
    }

    @Test
    fun firstTimePastLimitNoticeCanBeDismissedWithoutRemovingThePermanentGuidance() {
        show(
            state = ready(ExpenseListState.Empty).copy(today = LocalDate.of(2026, 11, 3)),
            showPastLimitAnnouncement = true
        )

        rule.onNodeWithTag(CATEGORY_DETAIL_PAST_HELP_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(CATEGORY_DETAIL_PAST_LIMIT_ANNOUNCEMENT_TAG)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Entendido").performScrollTo().performClick()

        assertEquals(1, pastLimitAnnouncementDismissed)
        rule.onNodeWithTag(CATEGORY_DETAIL_PAST_HELP_TAG).assertIsDisplayed()
    }
}
