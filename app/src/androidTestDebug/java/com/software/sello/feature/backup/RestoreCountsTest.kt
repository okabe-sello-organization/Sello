package com.software.sello.feature.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.BackupSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The preview's now-and-backup counts, given by hand (ADR 0020 CP26, SELLO-040 review R5). */
@RunWith(AndroidJUnit4::class)
class RestoreCountsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(current: BackupSummary, replacement: BackupSummary) {
        rule.setContent {
            SelloTheme(reducedMotion = true) { Counts(current, replacement) }
        }
    }

    @Test
    fun carryChainsGetTheirOwnRowOnlyWhenEitherSideHasAny() {
        show(BackupSummary(7, 10, 1, 0, 0), BackupSummary(8, 12, 1, 0, 0, carryChains = 4))

        rule.onNodeWithText("Arrastres").assertIsDisplayed()
        // Now 0, backup 4: both columns say theirs.
        rule.onNodeWithText("4").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTextCount("Inversiones"))
    }

    @Test
    fun withNoChainOnEitherSideThereIsNoRow() {
        show(BackupSummary(2, 10, 1, 0, 0), BackupSummary(3, 12, 1, 0, 0))

        assertEquals(0, rule.onAllNodesWithTextCount("Arrastres"))
    }

    private fun ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
