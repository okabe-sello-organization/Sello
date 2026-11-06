package com.software.sello.feature.investments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MORE_MENU_TAG
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Currency
import com.software.sello.revealed
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Nueva inversión looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestmentEditorScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)

    /** A name, an icon and a first contribution given: the form ready to save. */
    private val creating = InvestmentEditorState(
        loading = false,
        name = "Fondo de acciones",
        nameLength = 17,
        description = "Fondo colectivo",
        descriptionLength = 15,
        iconKey = "account_balance",
        firstContribution = MoneyTextValue(MoneySign.None, "", "6.000.000", "6.000.000 pesos"),
        firstReady = true,
        date = today,
        today = today,
        rateDate = today
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            InvestmentEditorScreen(creating) {}
        }
        rule.captureStill("src/test/screenshots/investment-editor-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** Editing: deleting waits in the top bar's ⋮, shown open, not on the form. */
    private fun editingWithTheMenuOpen(look: Look) {
        rule.showSello(look) {
            InvestmentEditorScreen(
                creating.copy(
                    editing = true,
                    name = "Ecopetrol",
                    nameLength = 9,
                    description = "Acciones",
                    descriptionLength = 8,
                    iconKey = "trending_up",
                    movementCount = 9
                )
            ) {}
        }
        rule.onNodeWithTag(MORE_MENU_TAG).performClick()
        rule.captureStill("src/test/screenshots/investment-editor-editing-menu-${look.label}.png")
    }

    @Test
    fun editingWithTheMenuOpenLight() = editingWithTheMenuOpen(Look())

    @Test
    fun editingWithTheMenuOpenDark() = editingWithTheMenuOpen(Look(dark = true))

    /** Editar inversión with a save whose outcome is unknown: only checking again is offered. */
    private fun unresolvedSave(look: Look) {
        rule.showSello(look) {
            InvestmentEditorScreen(
                creating.copy(
                    editing = true,
                    name = "Ecopetrol",
                    nameLength = 9,
                    description = "Acciones",
                    descriptionLength = 8,
                    iconKey = "trending_up",
                    movementCount = 9,
                    saveError = InvestmentSaveError.Unknown
                )
            ) {}
        }
        rule.captureStill("src/test/screenshots/investment-editor-unresolved-${look.label}.png")
    }

    @Test
    fun unresolvedSaveLight() = unresolvedSave(Look())

    @Test
    fun unresolvedSaveDark() = unresolvedSave(Look(dark = true))

    private fun shot(name: String, state: InvestmentEditorState, look: Look, tap: String? = null) {
        rule.showSello(look) { InvestmentEditorScreen(state) {} }
        if (tap != null) rule.onNodeWithTag(tap).revealed().performClick()
        rule.captureStill("src/test/screenshots/investment-editor-$name-${look.label}.png")
    }

    private val loading = InvestmentEditorState(today = today, date = today)

    @Test fun loadingLight() = shot("loading", loading, Look())

    @Test fun loadingDark() = shot("loading", loading, Look(dark = true))

    private val failed = loading.copy(loading = false, loadFailed = true, editing = true)

    @Test fun failedLight() = shot("failed", failed, Look())

    @Test fun failedDark() = shot("failed", failed, Look(dark = true))

    private val empty = loading.copy(loading = false, rateDate = today)

    @Test fun emptyLight() = shot("empty", empty, Look())

    @Test fun emptyDark() = shot("empty", empty, Look(dark = true))

    /** In dollars with no rate for the day: the rate slip asks for it. */
    private val dollars = creating.copy(
        currency = Currency.USD,
        firstContribution = MoneyTextValue(MoneySign.None, "US$", "1.500,00", "1.500 dólares"),
        rateNeeded = true,
        rate = "4.150,25",
        rateReady = true
    )

    @Test fun dollarsLight() = shot("dollars", dollars, Look())

    @Test fun dollarsDark() = shot("dollars", dollars, Look(dark = true))

    /** Worth something else today than what was put in. */
    private val otherValue = creating.copy(
        sameValue = false,
        otherValue = MoneyTextValue(MoneySign.None, "", "6.300.000", "6.300.000 pesos"),
        valueReady = true
    )

    @Test fun otherValueLight() = shot("other-value", otherValue, Look())

    @Test fun otherValueDark() = shot("other-value", otherValue, Look(dark = true))

    @Test fun busyLight() = shot("busy", creating.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", creating.copy(busy = true), Look(dark = true))

    private val refused = creating.copy(saveError = InvestmentSaveError.NotSaved)

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    @Test fun iconSheetLight() = shot("icon-sheet", creating, Look(), INVESTMENT_EDITOR_ICON_TAG)

    @Test
    fun iconSheetDark() =
        shot("icon-sheet", creating, Look(dark = true), INVESTMENT_EDITOR_ICON_TAG)

    private val datePicker = creating.copy(datePicker = InvestmentField.First)

    @Test fun datePickerLight() = shot("date-picker", datePicker, Look())

    @Test fun datePickerDark() = shot("date-picker", datePicker, Look(dark = true))

    private val discard = creating.copy(changed = true, confirmDiscard = true)

    @Test fun discardLight() = shot("discard", discard, Look())

    @Test fun discardDark() = shot("discard", discard, Look(dark = true))

    private val delete = creating.copy(editing = true, movementCount = 9, confirmDelete = true)

    @Test fun deleteLight() = shot("delete", delete, Look())

    @Test fun deleteDark() = shot("delete", delete, Look(dark = true))
}
