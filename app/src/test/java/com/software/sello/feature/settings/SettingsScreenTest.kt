package com.software.sello.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Configuración drawn from state given by hand. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tusDatosComesFirstThenLoQueSeRepiteAndThereIsNoAvisos() {
        rule.showSello {
            SettingsScreen(
                state = SettingsState(loading = false),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {}
            ) { Text("Tus datos") }
        }

        val tops = listOf(
            SETTINGS_DATA_TAG,
            SETTINGS_RECURRING_TAG,
            SETTINGS_APPEARANCE_TAG,
            SETTINGS_ORDERING_TAG,
            SETTINGS_INVESTMENTS_TAG
        ).map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(0, rule.onAllNodes(hasText("Avisos")).fetchSemanticsNodes().size)
    }
}
