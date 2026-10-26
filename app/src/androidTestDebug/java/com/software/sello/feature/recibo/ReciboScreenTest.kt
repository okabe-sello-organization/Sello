package com.software.sello.feature.recibo

import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SCAFFOLD_SECONDARY_TAG
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.navigation.ShellDetail
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReciboScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<ReciboAction>()

    private fun show(state: ReciboState, fontScale: Float = 1f, selected: String? = null) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    ReciboScreen(
                        state,
                        { actions += it },
                        PaddingValues(12.dp),
                        selectedCategoryId = selected
                    )
                }
            }
        }
    }

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun limited(
        id: String,
        name: String,
        headline: ReciboHeadline,
        amount: String,
        stamp: ReciboStamp,
        limit: String,
        over: Boolean = false
    ) = ReciboCategory(
        id = id,
        name = name,
        iconKey = "restaurant",
        archived = false,
        figure = BudgetFigure(headline, plain(amount), stamp),
        cellAmount = if (over) {
            MoneyTextValue(MoneySign.Minus, "", amount, "menos $amount pesos")
        } else {
            plain(amount)
        },
        spent = plain("1"),
        limit = plain(limit),
        fraction = 0.5f,
        over = over,
        expenseCount = 1
    )

    private fun open(id: String, name: String, stamp: ReciboStamp, archived: Boolean = false) =
        ReciboCategory(
            id = id,
            name = name,
            iconKey = "una_clave_nueva",
            archived = archived,
            figure = BudgetFigure(ReciboHeadline.Spent, plain("80.000"), stamp),
            cellAmount = plain("80.000"),
            spent = plain("80.000"),
            limit = null,
            fraction = null,
            over = false,
            expenseCount = 8
        )

    private val categories = listOf(
        limited(
            "a",
            "Alimentación",
            ReciboHeadline.Remaining,
            "287.600",
            ReciboStamp.OnTrack,
            "900.000"
        ),
        limited(
            "b",
            "Transporte",
            ReciboHeadline.Remaining,
            "55.000",
            ReciboStamp.AtLimit,
            "200.000"
        ),
        limited(
            "c",
            "Entretenimiento",
            ReciboHeadline.Overspent,
            "21.500",
            ReciboStamp.Over,
            "100.000",
            over = true
        ),
        open("d", "Arte", ReciboStamp.NoLimit),
        open("e", "Vieja", ReciboStamp.Paused, archived = true),
        open("f", "Nueva", ReciboStamp.NotSet)
    )

    private fun inTheAnswer(words: String) = hasContentDescription(words, substring = true) and
        hasAnyAncestor(hasTestTag(RECIBO_SUMMARY_TAG))

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private fun month(
        amount: MoneyTextValue,
        stamp: ReciboStamp,
        label: ReciboAnswerLabel = ReciboAnswerLabel.Remaining,
        days: Int? = null,
        perDay: String? = null,
        closed: Pair<String, String>? = null,
        stale: Boolean = false,
        adjustable: Boolean = false,
        shownCategories: List<ReciboCategory> = categories
    ) = ReciboState.Month(
        ReciboSummary(
            label,
            amount,
            stamp,
            perDay = perDay?.let { ReciboPerDay(plain(it), days!!) },
            closed = closed?.let { (spent, limit) ->
                ReciboClosedMonth(plain(spent), plain(limit))
            },
            adjustable = adjustable
        ),
        shownCategories,
        stale
    )

    private val adjust get() =
        rule.onNodeWithContentDescription("Ajustar presupuesto, abre tus categorías y sus límites")

    /** Enough categories that the card can be brought to the top of any phone's list. */
    private val many = (1..24).map {
        limited(
            "m$it",
            "Categoría $it",
            ReciboHeadline.Remaining,
            "1.000",
            ReciboStamp.OnTrack,
            "2.000"
        )
    }

    private fun overspent(stale: Boolean = false, shown: List<ReciboCategory> = categories) = month(
        minus("43.700"),
        ReciboStamp.Over,
        days = 9,
        perDay = "0",
        stale = stale,
        adjustable = true,
        shownCategories = shown
    )

    @Test
    fun loadingShowsSkeletonsAndNoFigure() {
        show(ReciboState.Loading)

        rule.onNodeWithTag(RECIBO_LOADING_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_SUMMARY_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_CATEGORIES_TAG).assertDoesNotExist()
    }

    @Test
    fun theAppIsReportedFullyDrawnOnceRecibosFiguresAreReadAndNotWhileLoading() {
        var reported = 0
        val owner = object : FullyDrawnReporterOwner {
            override val fullyDrawnReporter = FullyDrawnReporter({ it.run() }) { reported++ }
        }
        var state by mutableStateOf<ReciboState>(ReciboState.Loading)
        rule.setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                SelloTheme(reducedMotion = true) {
                    ReciboScreen(state, { actions += it }, PaddingValues(12.dp))
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, reported)

        state = overspent()
        rule.waitForIdle()

        assertEquals(1, reported)
    }

    @Test
    fun theFirstRunStillShowsTheGoalUnderItsCard() {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                ReciboScreen(
                    ReciboState.FirstRun,
                    { actions += it },
                    PaddingValues(12.dp),
                    goal = { Text("Meta en Recibo", Modifier.testTag("goal")) }
                )
            }
        }

        rule.onNodeWithText("Dale forma a tu mes").assertIsDisplayed()
        val card = rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).getUnclippedBoundsInRoot()
        val goal = rule.onNodeWithTag("goal").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("$goal under $card", goal.top >= card.bottom)
    }

    @Test
    fun theFirstRunOffersToCreateCategoriesAndNothingThatDoesNotExistYet() {
        show(ReciboState.FirstRun)

        rule.onNodeWithText("Dale forma a tu mes").assertIsDisplayed()
        rule.onNodeWithText(
            "Crea una categoría para lo que importa: la casa, la comida, el ocio o tu ahorro."
        ).assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_FAILED_TAG).assertDoesNotExist()
        rule.onNodeWithText("Crear categoría").performClick()

        assertEquals(listOf<ReciboAction>(ReciboAction.CreateCategory), actions)
        // No backup or entry action before those exist.
        rule.onNodeWithText("Traer mis datos de un respaldo").assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_SUMMARY_TAG).assertDoesNotExist()
    }

    @Test
    fun theMonthInProgressSaysWhatRemainsThenWhatCanBeSpentEachDayBelowATearLine() {
        show(month(plain("937.200"), ReciboStamp.OnTrack, days = 9, perDay = "99.100"))

        val answer = rule.onNodeWithContentDescription(
            "Restante del presupuesto: 937.200 pesos. Al día."
        ).assertIsDisplayed().getUnclippedBoundsInRoot()
        val perDay = rule.onNodeWithTag(RECIBO_PER_DAY_TAG)
            .assertContentDescriptionEquals("99.100 pesos por día. 9 días, incluido hoy.")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("$perDay must be under $answer", perDay.top >= answer.bottom)
        rule.onNodeWithTag(RECIBO_CLOSED_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_STALE_TAG).assertDoesNotExist()
        // The v1 wording is gone from the answer; the categories keep theirs.
        rule.onNodeWithText("Puedes gastar al día").assertDoesNotExist()
        rule.onAllNodes(inTheAnswer("Te quedan")).assertCountEquals(0)
    }

    @Test
    fun anOverrunIsSignedAndOnTheLastDayZeroPerDayIsStillShown() {
        show(month(minus("43.700"), ReciboStamp.Over, days = 1, perDay = "0"))

        rule.onNodeWithContentDescription(
            "Restante del presupuesto: menos 43.700 pesos. Excedido."
        ).assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG)
            .assertContentDescriptionEquals("0 pesos por día. 1 día, incluido hoy.")
            .assertIsDisplayed()
        rule.onAllNodes(inTheAnswer("Te pasaste")).assertCountEquals(0)
    }

    @Test
    fun atTheLimitNothingIsLeftAndZeroPerDayIsShownForEveryDayLeft() {
        show(month(plain("0"), ReciboStamp.AtLimit, days = 16, perDay = "0"))

        rule.onNodeWithContentDescription("Restante del presupuesto: 0 pesos. Al límite.")
            .assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG)
            .assertContentDescriptionEquals("0 pesos por día. 16 días, incluido hoy.")
    }

    @Test
    fun aClosedMonthSaysWhatWasLeftAndWhatItSpentOfItsLimitWithNoDailyAmount() {
        show(
            month(
                plain("80.000"),
                ReciboStamp.WithinLimit,
                closed = "920.000" to "1.000.000"
            )
        )

        val answer = rule.onNodeWithContentDescription(
            "Restante del presupuesto: 80.000 pesos. Cumplido."
        ).assertIsDisplayed().getUnclippedBoundsInRoot()
        val closed = rule.onNodeWithText("Mes cerrado · gastaste 920.000 de 1.000.000.")
            .assertIsDisplayed()
            .assertContentDescriptionEquals(
                "Mes cerrado. Gastaste 920.000 pesos de 1.000.000 pesos."
            )
            .getUnclippedBoundsInRoot()
        assertTrue("$closed must be under $answer", closed.top >= answer.bottom)
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertDoesNotExist()
    }

    @Test
    fun aClosedMonthOverItsLimitIsSignedAndSaysExcedido() {
        show(
            month(
                minus("80.000"),
                ReciboStamp.OverLimit,
                closed = "1.080.000" to "1.000.000"
            )
        )

        rule.onNodeWithContentDescription(
            "Restante del presupuesto: menos 80.000 pesos. Excedido."
        ).assertIsDisplayed()
        rule.onNodeWithText("Mes cerrado · gastaste 1.080.000 de 1.000.000.").assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertDoesNotExist()
    }

    @Test
    fun withNoLimitItSaysWhatWasSpentThisMonthAndNothingPerDay() {
        show(month(plain("80.000"), ReciboStamp.NoLimit, label = ReciboAnswerLabel.Spent))

        rule.onNodeWithContentDescription("Has gastado este mes: 80.000 pesos. Sin límite.")
            .assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_CLOSED_TAG).assertDoesNotExist()
    }

    @Test
    fun everyCategoryIsOneButtonThatSaysItsExactAmountsAndItsVerdictInWords() {
        show(month(plain("937.200"), ReciboStamp.OnTrack, days = 9, perDay = "99.100"))

        listOf(
            "Alimentación. Te quedan 287.600 pesos de 900.000 pesos. Al día.",
            "Transporte. Te quedan 55.000 pesos de 200.000 pesos. Al límite.",
            "Entretenimiento. Te pasaste por 21.500 pesos de 100.000 pesos. Excedido.",
            "Arte. Has gastado 80.000 pesos. Sin límite.",
            "Vieja. Has gastado 80.000 pesos. En pausa. Archivada.",
            "Nueva. Has gastado 80.000 pesos. Sin configurar."
        ).forEach { rule.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithTag(reciboCategoryTag("c")).performScrollTo().performClick()
        rule.onNodeWithTag(RECIBO_ADD_CATEGORY_TAG).performScrollTo().performClick()

        assertEquals(
            listOf(ReciboAction.OpenCategory("c"), ReciboAction.CreateCategory),
            actions
        )
    }

    @Test
    fun theCategoriesCardHeaderCarriesTheOnlyAddButton() {
        show(month(plain("937.200"), ReciboStamp.OnTrack, days = 9, perDay = "99.100"))

        val add = rule.onNodeWithContentDescription("Agregar categoría")
        val heading = rule.onNodeWithText("Tus categorías").getUnclippedBoundsInRoot()
        val firstCell = rule.onNodeWithTag(reciboCategoryTag("a")).getUnclippedBoundsInRoot()
        val button = add.getUnclippedBoundsInRoot()
        // On the heading's line, above the grid.
        assertTrue(button.bottom <= firstCell.top)
        assertTrue(button.top < heading.bottom && button.bottom > heading.top)
        val node = add.fetchSemanticsNode()
        assertTrue(
            with(node.layoutInfo.density) {
                node.touchBoundsInRoot.height.toDp()
            } >= 47.9.dp
        )
        // No caption and no second add action under the grid.
        rule.onNodeWithText("queda en cada una").assertDoesNotExist()
        rule.onAllNodesWithText("Agregar categoría").assertCountEquals(0)
        add.performClick()

        assertEquals(listOf<ReciboAction>(ReciboAction.CreateCategory), actions)
    }

    @Test
    fun olderFiguresCarryANoticeAndAWayToReadAgain() {
        show(
            month(plain("937.200"), ReciboStamp.OnTrack, days = 9, perDay = "99.100", stale = true)
        )

        rule.onNodeWithTag(RECIBO_STALE_TAG).assertIsDisplayed()
        rule.onNodeWithText(
            "No pudimos actualizar. Esto es lo último que se leyó y puede no estar al día."
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription("Restante del presupuesto: 937.200 pesos. Al día.")
            .assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<ReciboAction>(ReciboAction.Retry), actions)
    }

    @Test
    fun theCategoryShownBesideReciboIsMarkedAsSelectedAndNoOtherIs() {
        show(month(plain("937.200"), ReciboStamp.OnTrack), selected = "b")

        rule.onNodeWithTag(reciboCategoryTag("b")).assertIsSelected()
        rule.onNodeWithTag(reciboCategoryTag("a")).assertIsNotSelected()
    }

    @Test
    fun atTwiceTheFontSizeWithVeryLargeAmountsEverythingCanStillBeReached() {
        val huge = "8.998.000.000.000.001"
        val state = month(minus(huge), ReciboStamp.Over, days = 31, perDay = huge).copy(
            categories = listOf(
                limited(
                    "a",
                    "Un nombre de categoría largo",
                    ReciboHeadline.Overspent,
                    huge,
                    ReciboStamp.Over,
                    huge,
                    over = true
                )
            )
        )

        show(state, fontScale = 2f)

        rule.onNodeWithContentDescription("Restante del presupuesto: menos $huge pesos. Excedido.")
            .assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertContentDescriptionEquals("$huge pesos por día. 31 días, incluido hoy.")
        rule.onNodeWithContentDescription(
            "Un nombre de categoría largo. Te pasaste por $huge pesos de $huge pesos. Excedido."
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_ADD_CATEGORY_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFailureOffersRetryAndShowsNoAmount() {
        show(ReciboState.Failed)

        rule.onNodeWithTag(RECIBO_FAILED_TAG).assertIsDisplayed()
        rule.onNodeWithText("No pudimos leer tu mes").assertIsDisplayed()
        rule.onNodeWithText("Tus registros siguen guardados. Vuelve a intentarlo.")
            .assertIsDisplayed()
        // A failed read is never shown as first use.
        rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<ReciboAction>(ReciboAction.Retry), actions)
        rule.onNodeWithTag(RECIBO_SUMMARY_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_CATEGORIES_TAG).assertDoesNotExist()
    }

    @Test
    fun aLongPressOrAScreenReaderActionOnACategoryOffersToEditItAndPressingStillOpensIt() {
        show(month(plain("937.200"), ReciboStamp.OnTrack, days = 9, perDay = "99.100"))

        rule.onNodeWithTag(reciboCategoryTag("a")).performScrollTo().performClick()
        rule.onNodeWithTag(reciboCategoryTag("a")).performTouchInput { longClick() }
        rule.onNodeWithText("Editar categoría").assertIsDisplayed().performClick()
        val offered = rule.onNodeWithTag(reciboCategoryTag("b")).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        rule.runOnUiThread { offered.single().action() }

        assertEquals(listOf("Editar categoría"), offered.map { it.label })
        assertEquals(
            listOf(
                ReciboAction.OpenCategory("a"),
                ReciboAction.EditCategory("a"),
                ReciboAction.EditCategory("b")
            ),
            actions
        )
    }

    @Test
    fun anExcedidoMonthInProgressOffersAjustarPresupuestoAsOneButtonUnderItsAnswer() {
        show(overspent())

        val link = adjust.assertIsDisplayed().assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val node = link.fetchSemanticsNode()
        assertTrue(
            with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() } >= 47.9.dp
        )
        val answer = rule.onNodeWithTag(RECIBO_SUMMARY_TAG).getUnclippedBoundsInRoot()
        val shown = link.getUnclippedBoundsInRoot()
        assertTrue(
            "$shown inside $answer",
            shown.top >= answer.top && shown.bottom <= answer.bottom
        )
        rule.onNodeWithTag(RECIBO_PER_DAY_TAG).assertExists()
    }

    @Test
    fun noOtherAnswerOffersIt() {
        val others = listOf(
            month(plain("937.200"), ReciboStamp.OnTrack, days = 9, perDay = "99.100"),
            month(plain("0"), ReciboStamp.AtLimit, days = 9, perDay = "0"),
            month(minus("80.000"), ReciboStamp.OverLimit, closed = "1.080.000" to "1.000.000"),
            month(plain("80.000"), ReciboStamp.NoLimit, label = ReciboAnswerLabel.Spent)
        )
        var state by mutableStateOf<ReciboState>(others.first())
        rule.setContent {
            SelloTheme(reducedMotion = true) { ReciboScreen(state, {}, PaddingValues(12.dp)) }
        }

        others.forEach { other ->
            state = other
            rule.waitForIdle()
            rule.onNodeWithTag(RECIBO_SUMMARY_TAG).assertExists()
            rule.onNodeWithTag(RECIBO_ADJUST_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun theLinkAsksToAdjust() {
        show(overspent(shown = categories.take(1)))

        adjust.performClick()

        // The root opens Ajustar presupuesto; the list itself does not move.
        assertEquals(listOf<ReciboAction>(ReciboAction.AdjustBudget), actions)
    }

    @Test
    fun withOlderFiguresAndAtTwiceTheFontSizeItStillAsks() {
        show(overspent(stale = true, shown = many), fontScale = 2f)

        adjust.performScrollTo().assertIsDisplayed().performClick()

        rule.onNodeWithTag(RECIBO_STALE_TAG).assertExists()
        assertEquals(listOf<ReciboAction>(ReciboAction.AdjustBudget), actions)
    }

    // --- Payments reserved (SELLO-101) ---

    private fun reservedMonth(summary: ReciboSummary) =
        ReciboState.Month(summary, categories, false)

    @Test
    fun revisarSaysWhatIsMissingAndItsTwoFactsThenOpensThePayments() {
        show(
            reservedMonth(
                ReciboSummary(
                    ReciboAnswerLabel.ShortForPayments,
                    plain("50.000"),
                    ReciboStamp.Review,
                    ReciboPerDay(plain("0"), 10),
                    adjustable = true,
                    reserved = ReciboReserved(plain("150.000"), 1),
                    shortfall = ReciboShortfall(
                        plain("100.000"),
                        over = false,
                        reserved = plain("150.000")
                    )
                )
            )
        )

        rule.onNodeWithContentDescription("Falta para tus pagos: 50.000 pesos. Revisar.")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(
            "Quedan 100.000 pesos y tus pagos previstos suman 150.000 pesos."
        ).assertIsDisplayed()
        adjust.assertExists()
        rule.onNodeWithTag(RECIBO_RESERVED_TAG).performScrollTo().performClick()

        assertEquals(listOf<ReciboAction>(ReciboAction.OpenReserved), actions)
    }

    @Test
    fun alreadyPastTheLimitTheSentenceSaysBy() {
        show(
            reservedMonth(
                ReciboSummary(
                    ReciboAnswerLabel.ShortForPayments,
                    plain("280.000"),
                    ReciboStamp.Review,
                    ReciboPerDay(plain("0"), 10),
                    adjustable = true,
                    reserved = ReciboReserved(plain("200.000"), 2),
                    shortfall = ReciboShortfall(
                        plain("80.000"),
                        over = true,
                        reserved = plain("200.000")
                    )
                )
            )
        )

        rule.onNodeWithContentDescription("Falta para tus pagos: 280.000 pesos. Revisar.")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(
            "Pasaste tu límite por 80.000 pesos y tus pagos previstos suman 200.000 pesos."
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription("Incluye 200.000 pesos separado para 2 pagos previstos")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sinMargenKeepsTheFootnoteAndNothingIsReservedWithoutOne() {
        show(
            reservedMonth(
                ReciboSummary(
                    ReciboAnswerLabel.Remaining,
                    plain("0"),
                    ReciboStamp.NoMargin,
                    ReciboPerDay(plain("0"), 10),
                    reserved = ReciboReserved(plain("100.000"), 1)
                )
            )
        )

        rule.onNodeWithContentDescription("Restante del presupuesto: 0 pesos. Sin margen.")
            .assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_RESERVED_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_SHORTFALL_TAG).assertDoesNotExist()
        adjust.assertDoesNotExist()
    }
}
