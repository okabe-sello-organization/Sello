package com.software.sello.feature.bulk

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.IncomeSource
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Anotar varios switched to Ingreso, drawn from State given by hand on CI's phone size, and
 * kept as committed images in Claro and Oscuro (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class BulkIncomesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val claro = Look()
    private val oscuro = Look(dark = true)

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val freelance = BulkSource("freelance", null)
    private val transfer = BulkSource("transfer", null)
    private val bicycle = BulkSource("other", "Venta de la bicicleta")

    /** The mock's receipt: 450.000 + 120.000 + 850.000 = 1.420.000, Otro highlighted. */
    private val three = BulkEntryState(
        kind = BulkKind.Income,
        lines = listOf(
            BulkLineUi("1", null, pesos("450.000"), "Página web", source = freelance),
            BulkLineUi("2", null, pesos("120.000"), "Devolución de Laura", source = transfer),
            BulkLineUi("3", null, pesos("850.000"), "", source = bicycle)
        ),
        highlighted = 2,
        date = today,
        today = today,
        loading = false,
        sources = IncomeSource.keys,
        total = pesos("1.420.000"),
        count = 3
    )

    /** Transferencia's line has lost its source. */
    private val missing = three.copy(
        lines = three.lines.toMutableList().also {
            it[1] =
                BulkLineUi("2", null, pesos("120.000"), "Devolución de Laura", LineProblem.NoSource)
        },
        highlighted = 1,
        missingCategories = 1
    )

    /** The expense receipt just switched: 18.400 + 75.100 + 32.500 = 126.000, no sources. */
    private val switched = three.copy(
        lines = listOf(
            BulkLineUi("1", null, pesos("18.400"), "Uber", LineProblem.NoSource),
            BulkLineUi("2", null, pesos("75.100"), "Farmacia Pasteur", LineProblem.NoSource),
            BulkLineUi("3", null, pesos("32.500"), "Carulla", LineProblem.NoSource)
        ),
        highlighted = 0,
        total = pesos("126.000"),
        missingCategories = 3
    )

    private val transport = BulkCategory("t", "Transporte", "directions_bus")
    private val health = BulkCategory("s", "Salud", "medical_services")
    private val food = BulkCategory("c", "Comida", "restaurant")

    /** The expense receipt asking before it becomes a receipt of incomes. */
    private val switching = BulkEntryState(
        lines = listOf(
            BulkLineUi("1", transport, pesos("18.400"), "Uber"),
            BulkLineUi("2", health, pesos("75.100"), "Farmacia Pasteur"),
            BulkLineUi("3", food, pesos("32.500"), "Carulla")
        ),
        highlighted = 2,
        date = today,
        today = today,
        loading = false,
        categories = listOf(transport, health, food),
        total = pesos("126.000"),
        count = 3,
        confirmSwitch = true
    )

    /** Otro chosen on line 3: its name being written. */
    private val naming = three.copy(
        lines = three.lines.toMutableList().also {
            it[2] =
                it[2].copy(source = BulkSource("other", null))
        },
        noteDraft = "Venta de la bicicleta",
        composing = BulkComposer.SourceName,
        noteDraftLength = 21
    )

    private fun show(state: BulkEntryState, onAction: (BulkEntryAction) -> Unit = {}) =
        rule.showSello(claro) { BulkEntryScreen(state, dateLabels(), onAction) }

    private fun capture(name: String, state: BulkEntryState, look: Look) {
        rule.showSello(look) { BulkEntryScreen(state, dateLabels(), {}) }
        val label = if (look.dark) "oscuro" else "claro"
        rule.captureStill("src/test/screenshots/bulk-incomes-$name-$label.png")
    }

    @Test
    fun incomesShowInGainGreenWithAPlusAndTheButtonCountsThem() {
        val sent = mutableListOf<BulkEntryAction>()
        show(three) { sent += it }

        rule.onNodeWithText("ORIGEN · RENGLÓN 3").assertExists()
        rule.onNodeWithText("Anotar 3 ingresos").assertExists()
        // Otro's name is the line's title and has its own row under the chips.
        rule.onAllNodesWithText("Venta de la bicicleta", useUnmergedTree = true)
            .assertCountEquals(2)
        rule.onNode(hasText("Freelance") and hasAnyAncestor(hasTestTag(BULK_SOURCES_TAG)))
            .performClick()
        rule.onNodeWithTag(BULK_SOURCE_NAME_TAG).performClick()
        rule.onNodeWithText("Gasto").performClick()

        assertEquals(
            listOf(
                BulkEntryAction.SourcePicked("freelance"),
                BulkEntryAction.OpenSourceName,
                BulkEntryAction.KindPicked(BulkKind.Expense)
            ),
            sent
        )
    }

    @Test
    fun oneIncomeReadsAnotarIngreso() {
        show(three.copy(lines = three.lines.take(1), highlighted = 0, count = 1))

        rule.onNodeWithText("Anotar ingreso").assertExists()
        rule.onNodeWithText("ORIGEN").assertExists()
    }

    @Test
    fun aLineWithoutASourceSaysWhyAndTheButtonWaits() {
        show(missing)

        rule.onNodeWithText("Falta 1 origen").assertExists()
        rule.onNode(hasContentDescription("Elige un origen", substring = true)).assertExists()
        rule.onNodeWithTag(BULK_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun switchingAsksFirstAndSaysWhatStays() {
        val sent = mutableListOf<BulkEntryAction>()
        show(switching) { sent += it }

        rule.onNodeWithText("¿Pasar a ingresos?").assertExists()
        rule.onNodeWithText(
            "Tus 3 renglones conservan monto y nota. Tendrás que elegir el origen de cada uno."
        ).assertExists()
        rule.onNodeWithText("Seguir con gastos").performClick()
        rule.onNodeWithText("Pasar a ingreso").performClick()

        assertEquals(listOf(BulkEntryAction.SwitchDismissed, BulkEntryAction.SwitchConfirmed), sent)
    }

    @Test
    fun threeLinesClaro() = capture("three-lines", three, claro)

    @Test
    fun threeLinesOscuro() = capture("three-lines", three, oscuro)

    @Test
    fun missingSourceClaro() = capture("missing-source", missing, claro)

    @Test
    fun missingSourceOscuro() = capture("missing-source", missing, oscuro)

    @Test
    fun switchingClaro() = capture("switching", switching, claro)

    @Test
    fun switchingOscuro() = capture("switching", switching, oscuro)

    @Test
    fun afterSwitchingClaro() = capture("after-switching", switched, claro)

    @Test
    fun afterSwitchingOscuro() = capture("after-switching", switched, oscuro)

    @Test
    fun otroNameClaro() = capture("otro-name", naming, claro)

    @Test
    fun otroNameOscuro() = capture("otro-name", naming, oscuro)

    @Test
    fun unknownClaro() = capture("unknown", three.copy(error = BulkError.Unknown), claro)

    @Test
    fun unknownOscuro() = capture("unknown", three.copy(error = BulkError.Unknown), oscuro)
}
