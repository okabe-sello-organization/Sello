package com.software.sello.feature.backup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyImportReport
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StagedRestore
import com.software.sello.feature.expense.uuid
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What Respaldo looks like in its frame, saving and restoring, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.of("America/Bogota")

    private val ready = BackupState(
        loading = false,
        last = LastBackup(
            Instant.parse("2026-10-20T15:00:00Z"),
            "sello-respaldo.json",
            48_200,
            BackupSummary(8, 120, 6, 2, 3, 1)
        ),
        zone = zone,
        today = LocalDate.of(2026, 10, 22)
    )

    private val operation = (OperationId.of(uuid(5_001)) as Outcome.Success).value

    private val staged = StagedRestore(
        operationId = operation,
        backupAt = Instant.parse("2026-10-18T14:30:00Z"),
        current = BackupSummary(8, 120, 6, 2, 3, 1),
        replacement = BackupSummary(7, 96, 5, 1, 2, 1),
        generation = 1,
        revision = 4
    )

    private val restored = RestoreOutcome.Restored(
        BackupSummary(7, 96, 5, 1, 2, 1),
        PendingPreferences(null, null)
    )

    private fun capture(
        name: String,
        look: Look,
        state: BackupState = ready,
        step: RestoreStep = RestoreStep.Idle
    ) {
        val restore = RestoreState(step, zone = zone)
        rule.showSello(look) {
            ShellScreen(
                state = ShellState(
                    currentMonth = YearMonth.of(2026, 10),
                    selectedMonth = YearMonth.of(2026, 10),
                    monthPickerOpen = false,
                    pickerYear = 2026,
                    pendingEntry = null
                ),
                names = monthNames(),
                onAction = {},
                onBack = {},
                screenTitle = "Respaldo",
                pastMonthRibbon = false,
                entryDock = false
            ) { padding ->
                BackupScreen(
                    state,
                    {},
                    padding,
                    restoreFirst = step != RestoreStep.Idle && step != RestoreStep.Checking,
                    restoreOnly = restore.replaced
                ) { RestoreSection(restore) {} }
            }
        }
        rule.captureStill("src/test/screenshots/backup-$name-${look.label}.png")
    }

    @Test fun readyLight() = capture("ready", Look())

    @Test fun readyDark() = capture("ready", Look(dark = true))

    @Test fun neverLight() = capture("never", Look(), ready.copy(last = null))

    @Test fun neverDark() = capture("never", Look(dark = true), ready.copy(last = null))

    @Test fun loadingLight() = capture("loading", Look(), BackupState())

    @Test fun loadingDark() = capture("loading", Look(dark = true), BackupState())

    @Test fun savingLight() = capture("saving", Look(), ready.copy(saving = true))

    @Test fun savingDark() = capture("saving", Look(dark = true), ready.copy(saving = true))

    @Test
    fun failedLight() = capture("failed", Look(), ready.copy(failure = BackupFailure.Storage))

    @Test
    fun failedDark() =
        capture("failed", Look(dark = true), ready.copy(failure = BackupFailure.Storage))

    @Test fun unknownLight() = capture("unknown", Look(), ready.copy(unknownOutcome = true))

    @Test
    fun unknownDark() = capture("unknown", Look(dark = true), ready.copy(unknownOutcome = true))

    // Restoring.

    @Test fun checkingLight() = capture("restore-checking", Look(), step = RestoreStep.Checking)

    @Test
    fun checkingDark() = capture("restore-checking", Look(dark = true), step = RestoreStep.Checking)

    private val rejected = RestoreStep.Rejected(RestoreRejection.NotABackup)

    @Test fun rejectedLight() = capture("restore-rejected", Look(), step = rejected)

    @Test fun rejectedDark() = capture("restore-rejected", Look(dark = true), step = rejected)

    @Test fun restoreFailedLight() = capture("restore-failed", Look(), step = RestoreStep.Failed)

    @Test
    fun restoreFailedDark() =
        capture("restore-failed", Look(dark = true), step = RestoreStep.Failed)

    private val refreshed = RestoreStep.Previewing(staged, refreshed = true)

    @Test fun refreshedLight() = capture("restore-refreshed", Look(), step = refreshed)

    @Test fun refreshedDark() = capture("restore-refreshed", Look(dark = true), step = refreshed)

    private val replacing = RestoreStep.Replacing(staged)

    @Test fun replacingLight() = capture("restore-replacing", Look(), step = replacing)

    @Test fun replacingDark() = capture("restore-replacing", Look(dark = true), step = replacing)

    /** A FinanceTracker backup, translated first: what changed and what was left out. */
    private val legacy = RestoreStep.Previewing(
        staged.copy(
            legacy = LegacyImportReport(
                listOf(
                    LegacyImportLine(LegacyRecord.Category, LegacyReason.Renamed, 2),
                    LegacyImportLine(LegacyRecord.Expense, LegacyReason.InvalidDate, 3)
                )
            )
        )
    )

    @Test fun legacyLight() = capture("restore-legacy", Look(), step = legacy)

    @Test fun legacyDark() = capture("restore-legacy", Look(dark = true), step = legacy)

    private val done = RestoreStep.Restored(restored)

    @Test fun restoredLight() = capture("restore-restored", Look(), step = done)

    @Test fun restoredDark() = capture("restore-restored", Look(dark = true), step = done)

    private val pending = RestoreStep.Restored(
        restored.copy(preferences = PendingPreferences(AppearancePreference(), null))
    )

    @Test fun pendingLight() = capture("restore-pending", Look(), step = pending)

    @Test fun pendingDark() = capture("restore-pending", Look(dark = true), step = pending)

    private val unreadable = RestoreStep.Restored(restored.copy(preferencesUnreadable = true))

    @Test fun unreadableLight() = capture("restore-unreadable", Look(), step = unreadable)

    @Test
    fun unreadableDark() = capture("restore-unreadable", Look(dark = true), step = unreadable)
}
