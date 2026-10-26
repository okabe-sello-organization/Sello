package com.software.sello.feature.investments

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.feature.settings.SETTINGS_RATE_TAG
import com.software.sello.hideKeyboard
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The USD rate on real storage (investment rule 11): from Configuración, Dólar has no
 * rate; one typed with the comma pad is saved as exact hundredths and becomes current;
 * a second replaces it and the first stays below as an earlier rate.
 */
@RunWith(AndroidJUnit4::class)
class UsdRateJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun emptyHistory() = TestData.reset()

    @After
    fun close() {
        scenario?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    private fun type(keys: String) = keys.forEach { key ->
        if (key == ',') {
            rule.onNode(
                hasContentDescription("Coma decimal") and
                    hasAnyAncestor(hasTestTag(USD_RATE_ENTRY_KEYPAD_TAG))
            ).performClick()
        } else {
            rule.onNode(
                hasText(key.toString()) and hasAnyAncestor(hasTestTag(USD_RATE_ENTRY_KEYPAD_TAG))
            ).performClick()
        }
    }

    @Test
    fun aRateTypedWithTheCommaIsSavedExactlyAndANewOneReplacesIt() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.awaitTag(SETTINGS_ACTION_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(SETTINGS_RATE_TAG)
        rule.onNodeWithText("Sin tasa todavía", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(SETTINGS_RATE_TAG).performScrollTo().performClick()

        rule.awaitTag("usd.rate.empty")
        rule.onNodeWithText("Anotar la tasa").performScrollTo().press()
        rule.awaitTag(USD_RATE_ENTRY_KEYPAD_TAG)
        type("4150,25")
        rule.onNodeWithText("4.150,25", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(USD_RATE_ENTRY_SUBMIT_TAG).press()

        rule.awaitTag("usd.rate.current")
        assertEquals(
            listOf("415025|0"),
            TestData.rows("SELECT rate_hundredths, sequence FROM usd_rate")
        )
        rule.onNodeWithText("4.150,25", useUnmergedTree = true).assertExists()

        rule.onNodeWithTag(USD_RATE_CHANGE_TAG).performScrollTo().press()
        rule.awaitTag(USD_RATE_ENTRY_KEYPAD_TAG)
        rule.onNodeWithText("4.150,25", substring = true, useUnmergedTree = true).assertExists()
        type("4000")
        rule.onNodeWithText("4.000", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(USD_RATE_ENTRY_SUBMIT_TAG).press()

        rule.waitUntil(15_000) { TestData.count("usd_rate") == 2L }
        rule.awaitTag("usd.rate.history")
        assertEquals(
            listOf("400000", "415025"),
            TestData.rows("SELECT rate_hundredths FROM usd_rate ORDER BY sequence DESC")
        )
        rule.onNodeWithText("4.000,00", useUnmergedTree = true).assertExists()
        rule.onNode(
            hasText("4.150,25") and hasAnyAncestor(hasTestTag("usd.rate.history")),
            useUnmergedTree = true
        ).assertExists()
    }
}
