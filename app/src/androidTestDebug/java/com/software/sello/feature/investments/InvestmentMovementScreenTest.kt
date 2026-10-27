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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.Window
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.looks
import com.software.sello.press
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Anotar movimiento as it is drawn from state given by hand: the type chips and the
 * investment chips each keep one row, each type draws its own sign, a retiro above the
 * value says so in the label's place and waits with Anotar el valor de hoy, the comma key
 * is there only for dollars, and the figures stay whole at 2.0 font scale on 320dp.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentMovementScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<InvestmentMovementAction>()
    private val today = LocalDate.of(2026, 10, 25)

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun dollars(digits: String) =
        MoneyTextValue(MoneySign.None, "USD", digits, "$digits dólares")

    private val investments = listOf(
        InvestmentChoice("p", "Parqueadero", "home", usd = false),
        InvestmentChoice("c", "CDT Banco", "account_balance", usd = false),
        InvestmentChoice("f", "Fondo de acciones", "insights", usd = false),
        InvestmentChoice("e", "Ecopetrol", "trending_up", usd = false),
        InvestmentChoice("u", "Acciones EE. UU.", "trending_up", usd = true)
    )

    private val aporte = InvestmentMovementState(
        loading = false,
        amount = pesos("500.000"),
        investments = investments,
        investmentId = "e",
        fact = InvestmentMovementFact.Contributed(pesos("3.000.000"), "Ecopetrol"),
        date = today,
        today = today
    )

    private fun show(state: InvestmentMovementState) {
        rule.setContent {
            SelloTheme(reducedMotion = true) { InvestmentMovementScreen(state) { actions += it } }
        }
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
        .getUnclippedBoundsInRoot()

    @Test
    fun theTypeChipsAndTheInvestmentChipsEachKeepOneRow() {
        show(aporte)

        val kinds = InvestmentMovementKind.entries.map {
            top("$INVESTMENT_MOVEMENT_KIND_TAG.${it.name}").top
        }
        assertEquals("type chips $kinds", 1, kinds.distinct().size)
        val chips = investments.map { top("$INVESTMENT_MOVEMENT_INVESTMENT_TAG.${it.id}").top }
        assertEquals("investment chips $chips", 1, chips.distinct().size)
        assertTrue(chips.first() > kinds.first())

        rule.onNodeWithText("Llevas 3.000.000 aportados en Ecopetrol.").assertExists()
        rule.onNodeWithText("Aporte en pesos", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Anotar aporte", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsEnabled()
        rule.onNodeWithText("Dividendo o renta", useUnmergedTree = true).performClick()
        assertEquals(
            listOf<InvestmentMovementAction>(
                InvestmentMovementAction.Kind(InvestmentMovementKind.Dividend)
            ),
            actions
        )
    }

    @Test
    fun eachTypeDrawsItsOwnSignLabelAndAction() {
        var state by mutableStateOf(aporte)
        rule.setContent {
            SelloTheme(reducedMotion = true) { InvestmentMovementScreen(state) { actions += it } }
        }
        val expected = listOf(
            Triple(InvestmentMovementKind.Contribution, "+", "Anotar aporte"),
            Triple(InvestmentMovementKind.Valuation, "", "Anotar valor"),
            Triple(InvestmentMovementKind.Withdrawal, "\u2212", "Anotar retiro"),
            Triple(InvestmentMovementKind.Dividend, "+", "Anotar dividendo")
        )
        val labels = mapOf(
            InvestmentMovementKind.Contribution to "Aporte en pesos",
            InvestmentMovementKind.Valuation to "Valor de hoy en pesos",
            InvestmentMovementKind.Withdrawal to "Retiro en pesos",
            InvestmentMovementKind.Dividend to "Dividendo o renta en pesos"
        )
        for ((kind, sign, action) in expected) {
            state = aporte.copy(kind = kind, fact = null)
            rule.waitForIdle()
            rule.onNodeWithText(labels.getValue(kind), useUnmergedTree = true).assertExists()
            rule.onNodeWithText(action, useUnmergedTree = true).assertExists()
            rule.onNodeWithText("${sign}500.000", useUnmergedTree = true).assertExists()
            if (sign.isEmpty()) {
                rule.onNodeWithText("+500.000", useUnmergedTree = true).assertDoesNotExist()
                rule.onNodeWithText("\u2212500.000", useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }

    @Test
    fun in10ARetiroAboveTheValueSaysSoInTheLabelsPlaceWaitsAndOffersTheValueFirst() {
        show(
            aporte.copy(
                kind = InvestmentMovementKind.Withdrawal,
                amount = pesos("6.000.000"),
                investmentId = "f",
                fact = InvestmentMovementFact.Worth(pesos("5.242.000"), LocalDate.of(2026, 10, 14)),
                overValue = pesos("5.242.000")
            )
        )

        rule.onNodeWithText("Más de lo que vale (5.242.000)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Retiro en pesos", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Vale 5.242.000 según el valor del 14 oct. ¿Vale más hoy?")
            .assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
        // Pressed through its action: on a short screen the pad covers where it is drawn.
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_VALUE_FIRST_TAG)
            .assert(hasText("Anotar el valor de hoy"))
            .press()
        assertEquals(
            listOf<InvestmentMovementAction>(InvestmentMovementAction.RecordValueFirst),
            actions
        )
    }

    @Test
    fun in18ARetiroWithNoValueAsksForTheValueFirst() {
        show(
            aporte.copy(
                kind = InvestmentMovementKind.Withdrawal,
                amount = null,
                fact = InvestmentMovementFact.NoValue,
                noValue = true
            )
        )

        rule.onNodeWithText("Anota el valor de hoy primero.").assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_VALUE_FIRST_TAG).assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun in25AMovementBeforeTheFirstAporteSaysTheDate() {
        show(aporte.copy(firstContribution = LocalDate.of(2025, 5, 3)))

        rule.onNodeWithText("No puede ser antes del primer aporte (3 may 2025).").assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun in19ACorrectionALaterRetiroNeedsNamesThatRetiro() {
        show(
            aporte.copy(
                editing = true,
                changed = true,
                blockedBy = BlockingRetiro(
                    pesos("6.000.000"),
                    LocalDate.of(2026, 10, 12),
                    pesos("5.000.000")
                )
            )
        )

        rule.onNodeWithText(
            "No se puede: el retiro de 6.000.000 del 12 oct sería más de lo que valdría " +
                "(5.000.000). Borra o corrige ese retiro primero.",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_DELETE_TAG).assertIsEnabled()
    }

    @Test
    fun theCommaKeyIsThereOnlyForDollars() {
        var state by mutableStateOf(aporte)
        rule.setContent {
            SelloTheme(reducedMotion = true) { InvestmentMovementScreen(state) { actions += it } }
        }
        rule.onNodeWithContentDescription("Coma decimal").assertDoesNotExist()
        rule.onNodeWithText("000", useUnmergedTree = true).assertExists()

        state = aporte.copy(investmentId = "u", amount = dollars("250,5"), fact = null)
        rule.waitForIdle()

        rule.onNodeWithText("Aporte en dólares", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("000", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Coma decimal").performClick()
        assertEquals(listOf<InvestmentMovementAction>(InvestmentMovementAction.Comma), actions)
    }

    @Test
    fun fromTheDockNothingIsChosenAndTheButtonWaits() {
        show(aporte.copy(investmentId = null, fact = null))

        rule.onNodeWithText("Elige en qué inversión va.", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aFailedReadSaysSoAndNeverNoInvestments() {
        show(aporte.copy(loadFailed = true))

        rule.onNodeWithTag(INVESTMENT_MOVEMENT_FAILED_TAG).assertExists()
        rule.onNodeWithText(
            "Todavía no tienes inversiones. Agrega una en Inversiones.",
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun theFiguresAndTheActionStayWholeAtLargeTextOnANarrowPhone() {
        val largest = aporte.copy(
            investmentId = "u",
            amount = dollars("9.999.999.999,99"),
            fact = InvestmentMovementFact.Contributed(dollars("1.000,00"), "Acciones EE. UU.")
        )
        var look by mutableStateOf(Look(SelloInk.Cobalto, false, 1f, Window(320, 640)))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(
                            look.window.width.dp,
                            look.window.height.dp
                        ).testTag("window")
                    ) {
                        InvestmentMovementScreen(largest) { actions += it }
                    }
                }
            }
        }
        for (each in looks(listOf(Window(320, 640), Window(600, 640)), scales = listOf(1f, 2f))) {
            look = each
            rule.waitForIdle()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            val submit = rule.onNodeWithTag(INVESTMENT_MOVEMENT_SUBMIT_TAG).assertIsEnabled()
                .getUnclippedBoundsInRoot()
            assertTrue("$each: action at $submit", submit.bottom <= window.bottom + 0.5.dp)
            assertTrue("$each: action at $submit", submit.right <= window.right + 0.5.dp)
            val amount = rule.onNodeWithTag(INVESTMENT_MOVEMENT_AMOUNT_TAG)
                .getUnclippedBoundsInRoot()
            assertTrue("$each: amount at $amount", amount.right <= window.right + 0.5.dp)
        }
    }
}
