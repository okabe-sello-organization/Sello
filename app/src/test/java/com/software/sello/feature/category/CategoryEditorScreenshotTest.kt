package com.software.sello.feature.category

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the form to change a category looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryEditorScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /** "Café", limit 100.000, opened from October with a name typed over it. */
    private val editing = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "local_cafe",
        suggestions = listOf(
            LimitSuggestion(200_000, "200.000", "200.000 pesos"),
            LimitSuggestion(300_000, "300.000", "300.000 pesos"),
            LimitSuggestion(500_000, "500.000", "500.000 pesos")
        ),
        editing = true,
        name = "Cafetería",
        changed = true,
        limitText = "100000",
        limitAmount = MoneyTextValue(MoneySign.None, "", "100.000", "100.000 pesos"),
        targetMonth = YearMonth.of(2026, 10)
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            CategoryEditorScreen(editing, {})
        }
        rule.captureStill("src/test/screenshots/category-editor-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** A 13-digit limit pasted over it: kept as pasted, with why it cannot be set. */
    @Test
    fun aLimitAboveTheMaximum() {
        val pasted = editing.copy(
            limitText = "1.000.000.000.000",
            limitAmount = MoneyTextValue(
                MoneySign.None,
                "",
                "1.000.000.000.000",
                "1.000.000.000.000 pesos"
            ),
            limitError = LimitError.TooLarge
        )
        val look = Look()
        rule.showSello(look) {
            CategoryEditorScreen(pasted, {})
        }
        rule.captureStill("src/test/screenshots/category-editor-too-large-${look.label}.png")
    }
}
