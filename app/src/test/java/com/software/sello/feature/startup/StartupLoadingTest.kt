package com.software.sello.feature.startup

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.showSello
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the app shows while its data opens at start. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class StartupLoadingTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun itFillsTheScreenAndTellsAScreenReaderItIsLoading() {
        // It holds no text, so a larger font changes nothing; 2x is shown to prove it.
        rule.showSello(Look(fontScale = 2f)) { StartupLoading() }

        rule.onNodeWithTag(STARTUP_LOADING_TAG).assertIsDisplayed()
        val loading = rule.onNodeWithContentDescription("Cargando")
        assertTrue(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
                .matches(loading.fetchSemanticsNode())
        )
    }
}
