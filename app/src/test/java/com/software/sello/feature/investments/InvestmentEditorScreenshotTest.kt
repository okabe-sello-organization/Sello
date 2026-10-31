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
    @Test
    fun editingWithTheMenuOpen() {
        rule.showSello {
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
        rule.captureStill("src/test/screenshots/investment-editor-editing-menu.png")
    }
}
