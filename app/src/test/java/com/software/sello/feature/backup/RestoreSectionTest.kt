package com.software.sello.feature.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.StagedRestore
import com.software.sello.showSello
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The restore part of Respaldo after an unknown outcome, given state by hand. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class RestoreSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<RestoreAction>()
    private val operation = OperationId.of("00000000-0000-4000-8000-000000005000")
        .let { (it as Outcome.Success).value }

    private companion object {
        const val SET_ASIDE =
            "No revisamos el archivo que elegiste: primero había que resolver esta restauración."
    }

    private fun show(step: RestoreStep) {
        rule.showSello(Look()) { RestoreSection(RestoreState(step = step), { actions += it }) }
    }

    @Test
    fun anUnknownOutcomeIsNamedAndComprobarChecksWithoutConfirming() {
        show(RestoreStep.Unknown(operation))

        rule.onNodeWithText("No sabemos si se restauró").assertIsDisplayed()
        rule.onNodeWithText("Comprobar").performClick()

        assertEquals(listOf<RestoreAction>(RestoreAction.Check), actions)
    }

    @Test
    fun anUnknownOutcomeShownForAnEarlierRestoreSaysTheChosenFileWasNotChecked() {
        show(RestoreStep.Unknown(operation, chosenSetAside = true))

        rule.onNodeWithText("No sabemos si se restauró").assertIsDisplayed()
        rule.onNodeWithText(SET_ASIDE, substring = true).assertIsDisplayed()
    }

    @Test
    fun anEarlierRestoreShownRestoredSaysTheChosenFileWasNotChecked() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(null, null)
                ),
                chosenSetAside = true
            )
        )

        rule.onNodeWithText("Ir al recibo").assertIsDisplayed()
        rule.onNodeWithText(SET_ASIDE).assertIsDisplayed()
    }

    @Test
    fun anUnknownOutcomeWithoutASetAsideSaysNothingAboutAChosenFile() {
        show(RestoreStep.Unknown(operation))

        rule.onNodeWithText(SET_ASIDE, substring = true).assertDoesNotExist()
    }

    @Test
    fun aCheckUnderWayCannotBePressedAgain() {
        show(RestoreStep.Unknown(operation, checking = true))

        rule.onNodeWithText("No sabemos si se restauró").assertIsDisplayed()
        rule.onNodeWithText("Comprobando…").assertIsNotEnabled()
    }

    @Test
    fun aFileFoundNotReplacedSaysSoAboveItsPreview() {
        val staged = StagedRestore(
            operationId = operation,
            backupAt = Instant.parse("2026-10-22T14:30:00Z"),
            current = BackupSummary(1, 2, 0, 0, 0),
            replacement = BackupSummary(2, 3, 1, 0, 0),
            generation = 1,
            revision = 4
        )

        show(RestoreStep.Previewing(staged, notReplaced = true))

        rule.onNodeWithText(
            "No se restauró: tus datos no cambiaron. Si aún quieres, confírmalo de nuevo."
        ).assertIsDisplayed()
    }
}
