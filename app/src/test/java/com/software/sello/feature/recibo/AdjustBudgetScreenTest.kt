package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.CATEGORY_ARCHIVED_TAG
import com.software.sello.designsystem.component.CATEGORY_UNLIMITED_TAG
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val regalos = cafe.copy(
        id = "regalos",
        name = "Regalos",
        spent = plain("10.000"),
        limit = null,
        remaining = null,
        overBy = null,
        fraction = null,
        over = false,
        unlimited = true
    )

    private val ready = AdjustBudgetState.Ready(
        month = october,
        summary = summary,
        limit = plain("1.000.000"),
        spent = plain("1.080.000"),
        limited = listOf(cafe),
        open = listOf(regalos),
        stale = false,
        archived = listOf(viejo)
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
        rule.showSello(Look(fontScale = fontScale)) {
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
    fun aCategoryWithoutALimitGetsTheBadgeAndSaysSinLimite() {
        show(ready)

        val inRow = hasAnyAncestor(hasTestTag(adjustRowTag("regalos")))
        rule.onNodeWithTag(adjustRowTag("regalos")).performScrollTo()
        rule.onNode(hasTestTag(CATEGORY_UNLIMITED_TAG) and inRow, useUnmergedTree = true)
            .assertIsDisplayed()
        rule.onNode(hasText("sin límite") and inRow, useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(hasText("gastado") and inRow, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun archivedCategoriesComeLastUnderTheirOwnHeadingInGrey() {
        show(ready)

        val heading = rule.onNodeWithText("Archivadas").performScrollTo().assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val row = rule.onNodeWithTag(adjustRowTag("viejo")).getUnclippedBoundsInRoot()
        assertTrue(row.top >= heading.bottom)
        rule.onNode(
            hasTestTag(CATEGORY_ARCHIVED_TAG) and hasAnyAncestor(hasTestTag(adjustRowTag("viejo"))),
            useUnmergedTree = true
        ).assertExists()
        rule.onNode(
            hasTestTag(CATEGORY_ARCHIVED_TAG) and hasAnyAncestor(hasTestTag(adjustRowTag("cafe"))),
            useUnmergedTree = true
        ).assertDoesNotExist()
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
