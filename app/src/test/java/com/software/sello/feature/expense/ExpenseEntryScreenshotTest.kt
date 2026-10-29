package com.software.sello.feature.expense

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the form looks like, kept as committed images (ADR 0022). The host gate compares
 * each capture with its baseline in `src/test/screenshots/`; a visual change re-records
 * them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpenseEntryScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 9)

    /** An amount typed and a category chosen: the form ready to record. */
    private val ready = ExpenseEntryState(
        loading = false,
        categories = listOf(
            EntryCategory("a", "Alimentación", "restaurant"),
            EntryCategory("b", "Café", "local_cafe")
        ),
        categoryId = "a",
        amount = MoneyTextValue(MoneySign.None, "", "48.700", "48.700 pesos"),
        missing = null,
        date = today,
        today = today
    )

    private fun capture(look: Look) {
        rule.showSello(look) { ExpenseEntryScreen(ready, dateLabels(), {}) }
        rule.captureStill("src/test/screenshots/expense-entry-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
