package com.software.sello.feature.backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.LastBackup
import com.software.sello.showSello
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Último respaldo when its record could not be read again, given state by hand. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<BackupAction>()

    private val read = BackupState(
        loading = false,
        last = LastBackup(
            Instant.parse("2026-10-20T15:00:00Z"),
            "sello-respaldo.json",
            48_200,
            BackupSummary(8, 120, 6, 2, 3, 1)
        ),
        zone = ZoneId.of("America/Bogota"),
        today = LocalDate.of(2026, 10, 22)
    )

    private companion object {
        const val OLDER =
            "No pudimos leer tu último respaldo de nuevo. Te mostramos el de la última lectura."
    }

    private fun show(state: BackupState) {
        rule.showSello(Look()) { BackupScreen(state, { actions += it }, PaddingValues()) }
    }

    @Test
    fun aFailedReReadKeepsTheLastBackupMarkedOlderWithARetry() {
        show(read.copy(recordFailed = true))

        rule.onNodeWithText("sello-respaldo.json").assertIsDisplayed()
        rule.onNodeWithTag(BACKUP_OLDER_TAG).assertIsDisplayed()
        rule.onNodeWithText(OLDER).assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<BackupAction>(BackupAction.RetryRecord), actions)
    }

    @Test
    fun aRecordReadWellHasNoOlderNotice() {
        show(read)

        rule.onNodeWithTag(BACKUP_OLDER_TAG).assertDoesNotExist()
    }

    @Test
    fun aBackupJustSavedIsNotMarkedOlderWhileTheRecordStillFails() {
        // The figures are the save this process just made, not a past read.
        show(read.copy(recordFailed = true, justSaved = true))

        rule.onNodeWithTag(BACKUP_SAVED_TAG).assertIsDisplayed()
        rule.onNodeWithTag(BACKUP_OLDER_TAG).assertDoesNotExist()
    }

    @Test
    fun nothingReadBeforeStillSaysSoInsideTheSlipWithoutTheOlderNotice() {
        show(read.copy(last = null, recordFailed = true))

        rule.onNodeWithText(
            "No pudimos leer cuándo fue tu último respaldo. Puedes crear uno igual."
        ).assertIsDisplayed()
        rule.onNodeWithTag(BACKUP_OLDER_TAG).assertDoesNotExist()
    }
}
