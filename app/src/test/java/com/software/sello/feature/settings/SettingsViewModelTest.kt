package com.software.sello.feature.settings

import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.feature.backup.HandBackupRecords
import com.software.sello.feature.backup.g01Summary
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.navigation.HandFinancialClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val appearance = HandAppearancePreferences()
    private val ordering = HandOrderingPreferences()
    private val backups = HandBackupRecords()
    private val clock = HandFinancialClock("2026-10-22")
    private val bogota = ZoneId.of("America/Bogota")

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun changingEachControlUsesItsNarrowPortAndShowsThePersistedValues() = runTest {
        val model = SettingsViewModel(appearance, ordering, backups, clock, HandScheduleReads())
        runCurrent()

        model.onAction(SettingsAction.ThemeChosen(ThemePreference.Dark))
        model.onAction(SettingsAction.InkChosen(InkPreference.Violeta))
        model.onAction(SettingsAction.OrderChosen(CategoryOrder.LatestExpenseAdded))
        model.onAction(SettingsAction.DirectionChosen(CategoryOrderDirection.Ascending))
        runCurrent()

        assertEquals(
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
            model.state.value.appearance
        )
        assertEquals(
            CategoryOrderingPreference(
                CategoryOrder.LatestExpenseAdded,
                CategoryOrderDirection.Ascending
            ),
            model.state.value.categoryOrdering
        )
        assertFalse(model.state.value.busy)
    }

    @Test
    fun anUnreadablePreferenceStaysVisibleAndRetryKeepsKnownValues() = runTest {
        val model = SettingsViewModel(appearance, ordering, backups, clock, HandScheduleReads())
        runCurrent()
        appearance.values.value = PreferenceRead.Failed(
            PreferenceFailure.Unavailable,
            AppearancePreference(ThemePreference.Light, InkPreference.Cobalto)
        )
        runCurrent()

        assertEquals(ThemePreference.Light, model.state.value.appearance.theme)
        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)

        appearance.values.value = PreferenceRead.Ready(AppearancePreference(ThemePreference.Dark))
        model.onAction(SettingsAction.Retry)
        runCurrent()

        assertEquals(ThemePreference.Dark, model.state.value.appearance.theme)
        assertEquals(null, model.state.value.failure)
    }

    @Test
    fun aFailedNonfinancialWriteIsReportedWithoutChangingTheDisplayedFinancialOrder() = runTest {
        val model = SettingsViewModel(appearance, ordering, backups, clock, HandScheduleReads())
        runCurrent()
        ordering.nextWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

        model.onAction(SettingsAction.OrderChosen(CategoryOrder.AmountSpent))
        runCurrent()

        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)
        assertEquals(CategoryOrder.MostUsed, model.state.value.categoryOrdering.order)
        assertTrue(!model.state.value.busy)
    }

    @Test
    fun theBackupRowSaysNeverUntilABackupIsRecordedThenItsMoment() = runTest {
        val model = SettingsViewModel(appearance, ordering, backups, clock, HandScheduleReads())
        runCurrent()

        assertEquals(BackupLine.Read(null, bogota), model.state.value.backup)

        val at = Instant.parse("2026-10-22T19:30:00Z")
        backups.backupCompleted(LastBackup(at, "sello-respaldo.json", 4_096, g01Summary))
        runCurrent()

        assertEquals(BackupLine.Read(at, bogota), model.state.value.backup)
    }

    @Test
    fun anUnreadableBackupRecordIsSaidSoAndLeavesTheOtherControlsAlone() = runTest {
        val model = SettingsViewModel(appearance, ordering, backups, clock, HandScheduleReads())
        runCurrent()

        backups.failReads()
        runCurrent()

        assertEquals(BackupLine.Unreadable, model.state.value.backup)
        assertEquals(null, model.state.value.failure)
    }
}

private class HandAppearancePreferences : AppearancePreferences {
    val values = MutableStateFlow<PreferenceRead<AppearancePreference>>(
        PreferenceRead.Ready(AppearancePreference())
    )

    override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = values

    override suspend fun setTheme(theme: ThemePreference): PreferenceWrite {
        val current = (values.value as PreferenceRead.Ready).value
        values.value = PreferenceRead.Ready(current.copy(theme = theme))
        return PreferenceWrite.Saved
    }

    override suspend fun setInk(ink: InkPreference): PreferenceWrite {
        val current = (values.value as PreferenceRead.Ready).value
        values.value = PreferenceRead.Ready(current.copy(ink = ink))
        return PreferenceWrite.Saved
    }
}

private class HandOrderingPreferences : CategoryOrderingPreferences {
    val values = MutableStateFlow<PreferenceRead<CategoryOrderingPreference>>(
        PreferenceRead.Ready(CategoryOrderingPreference())
    )
    var nextWrite: PreferenceWrite = PreferenceWrite.Saved

    override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
        values

    override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite {
        val result = nextWrite.also { nextWrite = PreferenceWrite.Saved }
        if (result is PreferenceWrite.Saved) {
            val current = (values.value as PreferenceRead.Ready).value
            values.value = PreferenceRead.Ready(current.copy(order = order))
        }
        return result
    }

    override suspend fun setCategoryOrderDirection(
        direction: CategoryOrderDirection
    ): PreferenceWrite {
        val result = nextWrite.also { nextWrite = PreferenceWrite.Saved }
        if (result is PreferenceWrite.Saved) {
            val current = (values.value as PreferenceRead.Ready).value
            values.value = PreferenceRead.Ready(current.copy(direction = direction))
        }
        return result
    }
}
