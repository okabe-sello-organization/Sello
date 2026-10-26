package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.press
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ajustar presupuesto in the AJ fixture: October 80.000 over; Café 90.000 of 50.000. */
@RunWith(AndroidJUnit4::class)
class AdjustBudgetScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)
    private val actions = mutableListOf<AdjustBudgetAction>()
    private val sheetActions = mutableListOf<LimitSheetAction>()
    private val edited = mutableListOf<String>()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val summary = ReciboSummary(
        label = ReciboAnswerLabel.Remaining,
        amount = minus("80.000"),
        stamp = ReciboStamp.Over,
        adjustable = true
    )

    private val cafe = AdjustRow(
        id = "cafe",
        name = "Café",
        iconKey = "local_cafe",
        spent = plain("90.000"),
        limit = plain("50.000"),
        remaining = minus("40.000"),
        overBy = plain("40.000"),
        fraction = 1f,
        over = true,
        archived = false
    )

    private val viejo = cafe.copy(
        id = "viejo",
        name = "Viejo",
        spent = plain("0"),
        limit = plain("30.000"),
        remaining = plain("30.000"),
        overBy = null,
        fraction = 0f,
        over = false,
        archived = true
    )

    private val ready = AdjustBudgetState.Ready(
        month = october,
        summary = summary,
        limit = plain("1.000.000"),
        spent = plain("1.080.000"),
        limited = listOf(cafe, viejo),
        open = emptyList(),
        stale = false
    )

    private val openSheet = LimitSheetState(
        month = october,
        categoryId = "cafe",
        name = "Café",
        iconKey = "local_cafe",
        spent = plain("90.000"),
        current = plain("50.000"),
        amount = plain("150.000"),
        chips = listOf(
            LimitChip(ChipKind.Spent, plain("90.000"), 90_000),
            LimitChip(ChipKind.CoverMonth, plain("130.000"), 130_000)
        ),
        fact = LimitFact(plain("1.100.000"), plain("20.000"), FactOutcome.Left, false),
        canSave = true
    )

    private var sheet by mutableStateOf(LimitSheetState(october))

    private fun show(state: AdjustBudgetState, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    AdjustBudgetScreen(
                        state = state,
                        sheet = sheet,
                        onAction = { actions += it },
                        onSheet = { sheetActions += it },
                        onEditCategory = { edited += it },
                        contentPadding = PaddingValues(12.dp)
                    )
                }
            }
        }
    }

    @Test
    fun rowsReadAsOneSentence() {
        show(ready)

        rule.onNodeWithContentDescription(
            "Café. Te pasaste por 40.000 pesos. Gastaste 90.000 pesos de 50.000 pesos. " +
                "Cambiar límite"
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(
            "Viejo. Archivada. Desarchívala para cambiar su límite. Abrir categoría"
        ).performScrollTo().assertIsDisplayed()
        // Drawn under the name; the row itself is read as the sentence above.
        rule.onNodeWithText(
            "Archivada · desarchívala para cambiar su límite",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensTheSheetAndAnArchivedRowOpensTheEditor() {
        show(ready)

        rule.onNodeWithTag(adjustRowTag("cafe")).performScrollTo().press()
        rule.onNodeWithTag(adjustRowTag("viejo")).performScrollTo().press()

        assertEquals(listOf<AdjustBudgetAction>(AdjustBudgetAction.Open("cafe")), actions)
        assertEquals(listOf("viejo"), edited)
    }

    @Test
    fun theFactLineAndTheButtonFollowTheScope() {
        sheet = openSheet
        show(ready)

        rule.onNodeWithText("Tu límite total de octubre pasaría a 1.100.000. Te quedarían 20.000.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Guardar solo octubre").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Desde octubre").press()
        sheet = openSheet.copy(scope = LimitScope.FromThisMonth)

        rule.onNodeWithText("Guardar desde octubre").performScrollTo().assertIsDisplayed()
        assertEquals(
            listOf<LimitSheetAction>(LimitSheetAction.Scope(LimitScope.FromThisMonth)),
            sheetActions
        )
    }

    @Test
    fun failedReadShowsTheErrorNotZero() {
        show(AdjustBudgetState.Failed)

        rule.onNodeWithText("No pudimos leer tu presupuesto").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").press()

        assertEquals(listOf<AdjustBudgetAction>(AdjustBudgetAction.Retry), actions)
    }

    @Test
    fun largeTextKeepsTheButtonReachable() {
        sheet = openSheet
        show(ready, fontScale = 2f)

        rule.onNodeWithText("Guardar solo octubre").performScrollTo().assertIsDisplayed()
    }
}
