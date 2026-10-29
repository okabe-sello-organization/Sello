package com.software.sello.feature.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.SweepLook
import com.software.sello.SweepWindow
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.navigation.INVERSIONES_ADD_TAG
import com.software.sello.navigation.InversionesDestination
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.showSello
import com.software.sello.sweepLooks
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Inversiones as it is drawn (docs/design/v2/inversiones.html; investment examples):
 * no month, no Anotar and no dock until its screens exist; the answer, its return and
 * dates; dollars in dollars with the rate named; and every figure whole on a narrow
 * phone, a tablet and an expanded window, at 1.0, 1.3 and 2.0 font scales, in all four
 * themes. Loading and failure are never "no investments".
 */
@RunWith(AndroidJUnit4::class)
class InversionesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) =
            if (currency == Currency.COP) "$digits pesos" else "$digits dólares"

        override fun negative(amount: String) = "menos $amount"

        override fun positive(amount: String) = "más $amount"
    })
    private val at = Instant.parse("2026-10-25T12:00:00Z")
    private var ids = 0

    private fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun investment(
        name: String,
        description: String?,
        movements: List<Triple<InvestmentMovementKind, Long, String>>,
        currency: Currency = Currency.COP
    ): InvestmentSummary {
        val investment = Investment(
            InvestmentId.of(id()).value(),
            InvestmentName.of(name).value(),
            InvestmentDescription.of(description).value(),
            IconKey.of("home").value(),
            currency,
            1,
            at,
            at
        )
        val recorded = movements.mapIndexed { index, (kind, units, date) ->
            val amount = Money(units, currency)
            InvestmentMovement(
                InvestmentMovementId.of(id()).value(),
                kind,
                if (kind == InvestmentMovementKind.Valuation) {
                    InvestmentAmount.ofValue(amount).value()
                } else {
                    InvestmentAmount.of(amount).value()
                },
                LocalDate.parse(date),
                index + 1L,
                null,
                1,
                at,
                at
            )
        }
        val history = InvestmentHistory(investment, recorded)
        return InvestmentSummary(history, InvestmentPolicy.figures(history).value())
    }

    private val aporte = InvestmentMovementKind.Contribution
    private val retiro = InvestmentMovementKind.Withdrawal
    private val dividendo = InvestmentMovementKind.Dividend
    private val valor = InvestmentMovementKind.Valuation

    private val rate = UsdRateEntry(
        UsdRateId.of("00000000-0000-4000-8000-999999999999").value(),
        UsdRate.of(BigDecimal("4150.25")).value(),
        LocalDate.of(2026, 10, 20),
        1,
        at
    )

    private fun board(vararg investments: InvestmentSummary, rate: UsdRateEntry? = null) =
        InvestmentBoard(
            1,
            1,
            investments.toList(),
            UsdRateBook(rate, listOfNotNull(rate)),
            InvestmentPolicy.overview(investments.map { it.history to it.figures }, rate)
        )

    /** The page's examples (IN02, IN04, IN05, IN17, IN27) with a rate of 4.150,25. */
    private fun page() = board(
        investment(
            "Parqueadero",
            "Finca raíz",
            listOf(
                Triple(aporte, 18_000_000, "2025-03-10"),
                Triple(valor, 18_000_000, "2025-03-10"),
                Triple(dividendo, 350_000, "2026-06-01"),
                Triple(valor, 19_000_000, "2026-06-12")
            )
        ),
        investment(
            "Fondo de acciones",
            "Fondo colectivo",
            listOf(
                Triple(aporte, 6_000_000, "2025-05-03"),
                Triple(valor, 6_000_000, "2025-05-03"),
                Triple(retiro, 500_000, "2026-08-03"),
                Triple(valor, 5_242_000, "2026-10-14")
            )
        ),
        investment(
            "Acciones EE. UU.",
            "ETF",
            listOf(Triple(aporte, 100_000, "2026-10-16"), Triple(valor, 105_050, "2026-10-16")),
            Currency.USD
        ),
        investment("Cripto", "Criptomoneda", listOf(Triple(aporte, 2_000_000, "2026-08-01"))),
        rate = rate
    )

    /** The largest amounts an investment can hold, to test what fits. */
    private fun largest() = board(
        investment(
            "Fondo de inversión con nombre muy largo!",
            "Una descripción de cuarenta caracteres",
            listOf(
                Triple(aporte, 999_999_999_999, "2025-01-01"),
                Triple(valor, 999_999_999_999, "2025-01-01"),
                Triple(valor, 999_999_999_998, "2026-10-01")
            )
        ),
        investment(
            "Dólares",
            null,
            listOf(
                Triple(aporte, 999_999_999_999, "2025-01-01"),
                Triple(valor, 999_999_999_999, "2025-01-01")
            ),
            Currency.USD
        ),
        rate = rate
    )

    private val clock = object : FinancialClock {
        override val today = MutableStateFlow(
            FinancialDay(this@InversionesScreenTest.today, ZoneId.of("America/Bogota"))
        )
    }

    private class Reads(private val state: InvestmentBoardState) : InvestmentReads {
        override suspend fun board(): Outcome<InvestmentBoard, StorageFailure> =
            Outcome.Failure(StorageFailure.Unavailable("not used"))

        override fun observeBoard(): Flow<InvestmentBoardState> = flowOf(state)

        override suspend fun investment(
            id: InvestmentId
        ): Outcome<InvestmentSummary?, StorageFailure> =
            Outcome.Failure(StorageFailure.Unavailable("not used"))
    }

    /** Inversiones' settings at their defaults: largest value first. */
    private class Order : InvestmentPreferences {
        override fun observeInvestments(): Flow<PreferenceRead<InvestmentPreference>> =
            flowOf(PreferenceRead.Ready(InvestmentPreference()))

        override suspend fun setNewInvestmentCurrency(currency: Currency) = PreferenceWrite.Saved

        override suspend fun setInvestmentOrder(order: InvestmentOrder) = PreferenceWrite.Saved
    }

    private val shell =
        ShellState(YearMonth.of(2026, 10), YearMonth.of(2026, 10), false, 2026, null)

    private fun destination(state: InvestmentBoardState, onAdd: (() -> Unit)? = null) {
        val factory = viewModelFactory {
            initializer { InversionesViewModel(Reads(state), Order(), clock, money) }
        }
        rule.showSello(Look(density = 1f)) {
            Box(Modifier.requiredSize(360.dp, 2000.dp)) {
                InversionesDestination(
                    state = shell,
                    viewModels = factory,
                    onShell = {},
                    tabs = SelloNavigation(
                        listOf(
                            SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                            SelloDestination("resumen", "Resumen", SelloIcon.Insights),
                            SelloDestination("metas", "Metas", SelloIcon.Flag),
                            SelloDestination(
                                "inversiones",
                                "Inversiones",
                                SelloIcon.AccountBalance
                            )
                        ),
                        "inversiones"
                    ) {},
                    onAdd = onAdd
                )
            }
        }
    }

    @Test
    fun withInvestmentsNuevaInversionSitsBesideTheTitleAndThereIsNoGear() {
        var added = 0
        destination(InvestmentBoardState.Ready(page()), onAdd = { added++ })

        // Settings is on Recibo only (owner, 2026-10-26).
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).assertDoesNotExist()
        rule.onNodeWithTag(INVERSIONES_ADD_TAG).assert(hasText("Nueva inversión")).performClick()
        assertEquals(1, added)
    }

    @Test
    fun withNoInvestmentsOnlyTheDockAddsOne() {
        destination(InvestmentBoardState.Ready(board()), onAdd = {})

        rule.onNodeWithTag(INVERSIONES_ADD_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Agregar inversión"))
    }

    @Test
    fun theTabIsTitledInversionesWithNoMonthNoAnotarAndNoDockYet() {
        destination(InvestmentBoardState.Ready(page()))

        rule.onNode(hasText("Inversiones") and isHeading(), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Anotar un gasto").assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assertDoesNotExist()
    }

    @Test
    fun theAnswerLeadsWithTheValueTheReturnTheDatesAndTheRate() {
        destination(InvestmentBoardState.Ready(page()))

        // 19.000.000 + 5.242.000 + USD 1.050,50 at 4.150,25 (4.359.838); Cripto has no value.
        rule.onNodeWithContentDescription(
            "Tus inversiones valen, 28.601.838 pesos, Gana",
            useUnmergedTree = true
        ).assertExists()
        // Gain 28.601.838 + 500.000 + 350.000 − (18.000.000 + 6.000.000 + 4.150.250).
        rule.onNodeWithText("+1.301.588 de ganancia", useUnmergedTree = true).assertExists()
        // 1.301.588 ÷ 28.150.250 = 4,62 %.
        rule.onNodeWithText("+4,6 %", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("En total, desde mar 2025", useUnmergedTree = true).assertExists()
        rule.onNodeWithText(
            "Valores anotados entre el 12 jun y el 16 oct",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithText("Dólar a 4.150,25 · tasa del 20 oct", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun rowsShowDollarsInDollarsAndAMissingValuePlainlyButOpenNothingYet() {
        destination(InvestmentBoardState.Ready(page()))
        val list = rule.onNodeWithTag(INVERSIONES_LIST_TAG)
        list.performScrollToNode(hasTestTag("inversiones.rows"))

        rule.onNodeWithText("Finca raíz · valor del 12 jun", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("1.050,50", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("+5,1 %", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Criptomoneda · sin valor anotado", useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText("aportaste 2.000.000", useUnmergedTree = true).assertExists()
        val row = rule.onNode(hasTestTag("inversiones.row.00000000-0000-4000-8000-000000000001"))
            .fetchSemanticsNode()
        assertNull(
            "rows open nothing until SELLO-110",
            row.config.getOrNull(SemanticsActions.OnClick)
        )
    }

    @Test
    fun loadingFailureEmptyAndNoValuesAreEachTheirOwnState() {
        val render = InvestmentsRenderModel(money)
        var shown by mutableStateOf<InversionesState>(InversionesState.Loading)
        rule.showSello {
            InversionesScreen(shown, {}, dateLabels(), PaddingValues(16.dp))
        }
        rule.onNodeWithTag("inversiones.loading").assertExists()

        shown =
            render.overview(
                InvestmentBoardState.Failed(StorageFailure.Unavailable("x"), null),
                today
            )
        rule.onNodeWithText(
            "No pudimos leer tus inversiones",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithTag("inversiones.empty").assertDoesNotExist()

        shown = render.overview(InvestmentBoardState.Ready(board()), today)
        rule.onNodeWithText("Anota lo que tienes invertido", useUnmergedTree = true).assertExists()

        val cripto =
            investment("Cripto", null, listOf(Triple(aporte, 2_000_000, "2026-08-01")))
        shown = render.overview(InvestmentBoardState.Ready(board(cripto)), today)
        rule.onNodeWithTag("inversiones.noTotal").assertExists()
        rule.onNodeWithText(
            "Todavía no hay valores anotados. Anota cuánto vale cada una para ver el total.",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithTag("inversiones.bar").assertDoesNotExist()
    }

    @Test
    // A screen larger than every window below, so each window is laid out whole.
    @Config(qualifiers = "w900dp-h900dp-mdpi")
    fun everyFigureStaysWholeInEveryThemeSizeAndWindow() {
        val shown = InvestmentsRenderModel(
            money
        ).overview(InvestmentBoardState.Ready(largest()), today)
        var look by mutableStateOf(SweepLook(SelloInk.Cobalto, false, 1f, SweepWindow(320, 640)))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(
                            look.window.width.dp,
                            look.window.height.dp
                        ).testTag("window")
                    ) {
                        InversionesScreen(shown, {}, dateLabels(), PaddingValues(16.dp))
                    }
                }
            }
        }
        val windows = listOf(SweepWindow(320, 640), SweepWindow(600, 900), SweepWindow(840, 600))
        for (each in sweepLooks(windows)) {
            look = each
            rule.waitForIdle()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            fun whole(text: String, tag: String) {
                rule.onNodeWithTag(INVERSIONES_LIST_TAG).performScrollToNode(hasTestTag(tag))
                val bounds = rule.onNodeWithText(text, useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                assertTrue("$each: $text at $bounds", bounds.right <= window.right + 0.5.dp)
            }
            // 999.999.999.998 + USD 9.999.999.999,99 × 4.150,25 (41.502.499.999.958,4975 → …958).
            whole("42.502.499.999.956", "inversiones.total")
            whole("9.999.999.999,99", "inversiones.rows")
            whole("999.999.999.998", "inversiones.rows")
        }
    }
}
