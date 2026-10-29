package com.software.sello.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.recibo.text
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The month switcher's title takes a second line at twice the font size instead of being shortened. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthSwitcherFitTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(width: Int, height: Int, content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { content() }
                }
            }
        }
    }

    private fun assertWhole(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        val shortened = layout.multiParagraph.didExceedMaxLines ||
            (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        assertFalse("\"$text\" is shortened in ${layout.lineCount} lines", shortened)
    }

    @Test
    fun aMonthSwitcherTitleTakesASecondLineInsteadOfBeingShortened() {
        // Beside the back arrow on the narrowest phone.
        show(272, 640) { MonthSwitcher("Respaldo · Octubre 2026", onClick = {}) }

        assertWhole("Respaldo · Octubre 2026")
    }
}
