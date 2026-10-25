package com.software.sello.feature.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Currency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nueva inversión and Editar inversión as they are drawn (v2 *Agregar / editar
 * inversión*, *Dólares y la tasa*): the currency only when creating, the rate slip only
 * for dollars with no rate, Crear waiting for it, deletion counting the movements, and
 * the longest name and largest amounts whole at 1.0, 1.3 and 2.0 on narrow and wider
 * windows in all four themes.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<InvestmentEditorAction>()
    private val today = LocalDate.of(2026, 10, 25)

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun dollars(digits: String) =
        MoneyTextValue(MoneySign.None, "USD", digits, "$digits dólares")

    private val creating = InvestmentEditorState(
        loading = false,
        name = "a".repeat(40),
        nameLength = 40,
        description = "d".repeat(40),
        descriptionLength = 40,
        iconKey = "account_balance",
        firstContribution = pesos("999.999.999.999"),
        firstReady = true,
        date = today,
        today = today,
        rateDate = today
    )

    private val dollarsNoRate = creating.copy(
        name = "Acciones EE. UU.",
        nameLength = 16,
        description = "ETF",
        descriptionLength = 3,
        iconKey = "trending_up",
        currency = Currency.USD,
        firstContribution = dollars("1.000,00"),
        rateNeeded = true
    )

    private val ecopetrol = InvestmentEditorState(
        loading = false,
        editing = true,
        name = "Ecopetrol",
        nameLength = 9,
        description = "Acciones",
        descriptionLength = 8,
        iconKey = "trending_up",
        movementCount = 9,
        today = today
    )

    private fun show(state: InvestmentEditorState) {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                InvestmentEditorScreen(state) { actions += it }
            }
        }
    }

    @Test
    fun theCurrencyIsChosenOnlyWhenCreating() {
        var state by mutableStateOf(creating)
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                InvestmentEditorScreen(state) { actions += it }
            }
        }
        rule.onNodeWithText("Nueva inversión").assertExists()
        rule.onNodeWithText("Pesos").performScrollTo()
        rule.onNodeWithText("Dólares").performScrollTo().performClick()
        assertEquals(listOf(InvestmentEditorAction.CurrencyPicked(Currency.USD)), actions)
        rule.onNodeWithText("Primer aporte").performScrollTo()
        rule.onNodeWithText("Crear inversión").assertExists()

        state = ecopetrol
        rule.waitForIdle()
        rule.onNodeWithText("Editar inversión").assertExists()
        rule.onNodeWithTag(INVESTMENT_EDITOR_CURRENCY_TAG).assertDoesNotExist()
        rule.onNodeWithText("Dólares").assertDoesNotExist()
        rule.onNodeWithTag(INVESTMENT_EDITOR_FIRST_TAG).assertDoesNotExist()
        rule.onNodeWithText("Guardar cambios").assertExists()
    }

    @Test
    fun dollarsSayTheyStayInDollars() {
        show(dollarsNoRate)
        rule.onNodeWithText(
            "Lo que anotes de esta inversión queda en dólares. No se puede cambiar después."
        ).performScrollTo()
        rule.onNodeWithText("Primer aporte en dólares").performScrollTo()
    }

    @Test
    fun dollarsWithNoRateAskForItAndCrearWaits() {
        show(dollarsNoRate)
        rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_SLIP_TAG).performScrollTo()
        rule.onNodeWithText("¿A cuánto está el dólar?").assertExists()
        rule.onNodeWithText(
            "Pesos por 1 dólar. Sello no la busca: la anotas tú, y sirve para sumar tus dólares al total."
        ).assertExists()
        rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).assertIsNotEnabled()

        rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_TAG).performScrollTo().performClick()
        assertEquals(listOf(InvestmentEditorAction.OpenPad(InvestmentField.Rate)), actions)
    }

    @Test
    fun aTypedRateLetsCrearGo() {
        show(dollarsNoRate.copy(rate = "4.150,25", rateReady = true))
        rule.onNodeWithContentDescription("4.150,25 pesos por dólar", substring = true)
            .performScrollTo()
        rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).assertIsEnabled()
    }

    @Test
    fun withARateOneQuietLineNamesItAndNoSlipAsks() {
        show(
            dollarsNoRate.copy(
                rateNeeded = false,
                currentRate = RateModel("4.150,25", LocalDate.of(2026, 10, 20))
            )
        )
        rule.onNodeWithText("Dólar a 4.150,25, tasa del 20 oct: solo para el total en pesos.")
            .performScrollTo()
        rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_SLIP_TAG).assertDoesNotExist()
        rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).assertIsEnabled()
    }

    @Test
    fun pesosNeverAskForTheRate() {
        show(creating)
        rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_SLIP_TAG).assertDoesNotExist()
        rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_LINE_TAG).assertDoesNotExist()
    }

    @Test
    fun otroValorShowsASecondAmount() {
        show(creating.copy(sameValue = false, otherValue = pesos("0"), valueReady = true))
        rule.onNodeWithTag(INVESTMENT_EDITOR_VALUE_TAG).performScrollTo()
        rule.onNodeWithContentDescription("Vale hoy: 0 pesos", substring = true).assertExists()
        rule.onNodeWithTag(INVESTMENT_EDITOR_SAME_TAG).performScrollTo().performClick()
        assertEquals(listOf(InvestmentEditorAction.SameValuePicked(true)), actions)
    }

    @Test
    fun deletingCountsTheMovementsAndAsksOnceMore() {
        var state by mutableStateOf(ecopetrol)
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                InvestmentEditorScreen(state) { actions += it }
            }
        }
        val consequences = "Se borran la inversión y sus 9 movimientos. Esto no se puede deshacer."
        rule.onNodeWithText("Borrar esta inversión").performScrollTo()
        rule.onNodeWithText(consequences).assertExists()
        rule.onNodeWithText("Borrar inversión").performScrollTo().performClick()
        assertEquals(listOf(InvestmentEditorAction.AskToDelete), actions)

        state = ecopetrol.copy(confirmDelete = true)
        rule.waitForIdle()
        rule.onNodeWithText("¿Borrar Ecopetrol?").assertExists()
        rule.onAllNodesWithText(consequences).assertCountEquals(2)
        rule.onNodeWithText("Borrar").performClick()
        assertEquals(InvestmentEditorAction.Delete, actions.last())
    }

    private data class Look(val ink: SelloInk, val dark: Boolean, val scale: Float, val width: Int)

    @Test
    fun theFormStaysWholeAndTheActionReachableInEveryThemeSizeAndWindow() {
        var look by mutableStateOf(Look(SelloInk.Cobalto, false, 1f, 320))
        var state by mutableStateOf(creating)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(Modifier.requiredSize(look.width.dp, 640.dp).testTag("window")) {
                        InvestmentEditorScreen(state) { actions += it }
                    }
                }
            }
        }
        val looks = SelloInk.entries.flatMap { ink ->
            listOf(false, true).flatMap { dark ->
                listOf(1f, 1.3f, 2f).flatMap { scale ->
                    listOf(320, 600).map { width -> Look(ink, dark, scale, width) }
                }
            }
        }
        for (each in looks) {
            look = each
            for (shown in listOf(
                creating,
                dollarsNoRate.copy(rate = "100.000,00", rateReady = true)
            )) {
                state = shown
                rule.waitForIdle()
                val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
                val submit = rule.onNodeWithTag(INVESTMENT_EDITOR_SUBMIT_TAG).assertIsEnabled()
                    .getUnclippedBoundsInRoot()
                assertTrue("$each: action at $submit", submit.bottom <= window.bottom + 0.5.dp)
                val first = rule.onNodeWithTag(INVESTMENT_EDITOR_FIRST_TAG).performScrollTo()
                    .getUnclippedBoundsInRoot()
                assertTrue("$each: amount at $first", first.right <= window.right + 0.5.dp)
                val date = rule.onNodeWithTag(INVESTMENT_EDITOR_DATE_TAG).performScrollTo()
                    .getUnclippedBoundsInRoot()
                assertTrue("$each: date at $date", date.right <= window.right + 0.5.dp)
            }
            rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_SLIP_TAG).performScrollTo()
            val rate = rule.onNodeWithTag(INVESTMENT_EDITOR_RATE_DATE_TAG).performScrollTo()
                .getUnclippedBoundsInRoot()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            assertTrue("$each: rate date at $rate", rate.right <= window.right + 0.5.dp)
            state = creating
            rule.waitForIdle()
            rule.onNodeWithTag(INVESTMENT_EDITOR_NAME_TAG).performScrollTo()
            rule.onAllNodesWithText("40/40", useUnmergedTree = true).assertCountEquals(2)
        }
    }
}
