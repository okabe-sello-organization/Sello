package com.software.sello.feature.bulk

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.BULK_RECEIPT_LINES_TAG
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Anotar varios for expenses, drawn from State given by hand on CI's phone size, and kept
 * as committed images in Claro and Oscuro (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class BulkEntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val claro = Look()
    private val oscuro = Look(dark = true)

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val home = BulkCategory("h", "Hogar", "home")
    private val transport = BulkCategory("t", "Transporte", "directions_bus")
    private val health = BulkCategory("s", "Salud", "medical_services")
    private val clothes = BulkCategory("r", "Ropa", "checkroom")
    private val food = BulkCategory("c", "Comida", "restaurant")
    private val coffee = BulkCategory("f", "Café", "local_cafe")
    private val leisure = BulkCategory("o", "Ocio", "theaters")
    private val pets = BulkCategory("m", "Mascotas", "pets")
    private val categories = listOf(home, transport, health, clothes, food, coffee, leisure, pets)

    /** The mock's receipt: 18.400 + 75.100 + 32.500 = 126.000. */
    private val three = BulkEntryState(
        lines = listOf(
            BulkLineUi("1", transport, pesos("18.400"), "Uber"),
            BulkLineUi("2", health, pesos("75.100"), "Farmacia Pasteur"),
            BulkLineUi("3", food, pesos("32.500"), "Carulla")
        ),
        highlighted = 2,
        date = today,
        today = today,
        loading = false,
        categories = categories,
        total = pesos("126.000"),
        count = 3
    )

    private fun show(state: BulkEntryState, onAction: (BulkEntryAction) -> Unit = {}) =
        rule.showSello(claro) { BulkEntryScreen(state, dateLabels(), onAction) }

    private fun capture(name: String, state: BulkEntryState, look: Look) {
        rule.showSello(look) { BulkEntryScreen(state, dateLabels(), {}) }
        val label = if (look.dark) "oscuro" else "claro"
        rule.captureStill("src/test/screenshots/bulk-expenses-$name-$label.png")
    }

    @Test
    fun threeLinesCountTheirTotalAndTheButton() {
        val sent = mutableListOf<BulkEntryAction>()
        show(three) { sent += it }

        rule.onNodeWithText("Anotar varios").assertExists()
        rule.onNodeWithText("3 renglones").assertExists()
        rule.onNodeWithText("CATEGORÍA · RENGLÓN 3").assertExists()
        rule.onNodeWithTag(BULK_SUBMIT_TAG).assertIsEnabled().performClick()
        rule.onNode(hasText("Salud") and hasAnyAncestor(hasTestTag(BULK_CATEGORIES_TAG)))
            .performClick()

        assertEquals(listOf(BulkEntryAction.Submit, BulkEntryAction.CategoryPicked("s")), sent)
        rule.onNodeWithText("Anotar 3 gastos").assertExists()
    }

    @Test
    fun aLineWithoutACategorySaysWhyAndTheButtonWaits() {
        show(missing)

        rule.onNodeWithText("Falta 1 categoría").assertExists()
        // A line is read as one item, its reason included.
        rule.onNode(hasContentDescription("Elige una categoría", substring = true)).assertExists()
        rule.onNodeWithTag(BULK_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun anUnknownOutcomeOffersOnlyChecking() {
        val sent = mutableListOf<BulkEntryAction>()
        show(three.copy(error = BulkError.Unknown)) { sent += it }

        rule.onNodeWithText("Aún no sabemos si se guardó. Compruébalo antes de volver a anotar.")
            .assertExists()
        rule.onNodeWithText("Comprobar si se guardó").performClick()
        rule.onNodeWithText("Anotar 3 gastos").assertDoesNotExist()
        assertEquals(listOf(BulkEntryAction.Retry), sent)
    }

    /** CI's phone, 412 × 914 dp: more lines than fit scroll inside the slip; the total stays. */
    @Test
    fun theReceiptScrollsAfterFiveLinesOnThePhone() {
        val lines = (1..6).map { BulkLineUi("$it", coffee, pesos("4.500"), "Tinto") }
        show(three.copy(lines = lines, highlighted = 0, total = pesos("27.000"), count = 6))

        val range = rule.onNodeWithTag(BULK_RECEIPT_LINES_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("six lines overflow the receipt", range.maxValue() > 0f)
        rule.onNodeWithText("Total").assertExists()
    }

    private val missing = three.copy(
        lines = three.lines.toMutableList().also {
            it[1] =
                BulkLineUi("2", null, pesos("75.100"), "Farmacia Pasteur", LineProblem.NoCategory)
        },
        highlighted = 1,
        missingCategories = 1
    )

    private val refused = three.copy(
        lines = three.lines.toMutableList().also {
            it[1] = it[1].copy(problem = LineProblem.CategoryArchived)
        },
        highlighted = 1,
        refusedLines = 1,
        error = BulkError.NotSaved
    )

    /** Opened from Anotar with Transporte 18.400 typed: line one, and an empty line two. */
    private val fromAnotar = three.copy(
        lines = listOf(
            BulkLineUi("0", transport, pesos("18.400"), "Uber"),
            BulkLineUi("1", transport, null, "")
        ),
        highlighted = 1,
        total = pesos("18.400"),
        count = 1
    )

    /** 30 × 4.500 = 135.000 */
    private val thirty = three.copy(
        lines = (1..30).map { BulkLineUi("$it", listOf(food, coffee)[it % 2], pesos("4.500"), "") },
        highlighted = 29,
        total = pesos("135.000"),
        count = 30
    )

    @Test
    fun threeLinesClaro() = capture("three-lines", three, claro)

    @Test
    fun threeLinesOscuro() = capture("three-lines", three, oscuro)

    @Test
    fun missingCategoryClaro() = capture("missing-category", missing, claro)

    @Test
    fun missingCategoryOscuro() = capture("missing-category", missing, oscuro)

    @Test
    fun refusedClaro() = capture("refused", refused, claro)

    @Test
    fun refusedOscuro() = capture("refused", refused, oscuro)

    @Test
    fun unknownClaro() = capture("unknown", three.copy(error = BulkError.Unknown), claro)

    @Test
    fun unknownOscuro() = capture("unknown", three.copy(error = BulkError.Unknown), oscuro)

    @Test
    fun savingClaro() = capture("saving", three.copy(busy = true), claro)

    @Test
    fun savingOscuro() = capture("saving", three.copy(busy = true), oscuro)

    @Test
    fun fromAnotarClaro() = capture("from-anotar", fromAnotar, claro)

    @Test
    fun fromAnotarOscuro() = capture("from-anotar", fromAnotar, oscuro)

    @Test
    fun thirtyLinesClaro() = capture("thirty-lines", thirty, claro)

    @Test
    fun thirtyLinesOscuro() = capture("thirty-lines", thirty, oscuro)

    @Test
    fun failedReadClaro() = capture("failed-read", three.copy(readFailed = true), claro)

    @Test
    fun failedReadOscuro() = capture("failed-read", three.copy(readFailed = true), oscuro)
}
