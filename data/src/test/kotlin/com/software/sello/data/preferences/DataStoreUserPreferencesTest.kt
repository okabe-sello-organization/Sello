package com.software.sello.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.CorrectionAnnouncementPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DismissedSuggestion
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import java.io.IOException
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreUserPreferencesTest {
    private class HandDataStore(initial: Preferences = emptyPreferences()) :
        DataStore<Preferences> {
        private val values = MutableStateFlow(initial)
        override val data: Flow<Preferences> = values

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = transform(values.value).also { values.value = it }
    }

    private class UnavailableDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("not readable") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw IOException("not writable")
    }

    private class OnceUnavailableDataStore : DataStore<Preferences> {
        private val values = MutableStateFlow<Preferences>(emptyPreferences())
        private var reads = 0

        override val data: Flow<Preferences> = flow {
            if (reads++ == 0) throw IOException("temporarily unreadable")
            emitAll(values)
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = transform(values.value).also { values.value = it }
    }

    private val lastBackup = LastBackup(
        at = Instant.parse("2026-10-22T19:30:00.250Z"),
        name = "sello-respaldo-2026-10-22-1430.json",
        bytes = 4_096,
        summary = BackupSummary(categories = 2, expenses = 3, incomes = 1, 0, 0)
    )

    @Test
    fun noBackupIsRecordedUntilOneCompletes() = runTest {
        val preferences = DataStoreUserPreferences(HandDataStore())

        assertEquals(PreferenceRead.Ready(BackupRecord()), preferences.observeBackups().first())

        preferences.backupStarted()
        assertEquals(
            PreferenceRead.Ready(BackupRecord(last = null, unfinished = true)),
            preferences.observeBackups().first()
        )

        assertEquals(PreferenceWrite.Saved, preferences.backupCompleted(lastBackup))
        assertEquals(
            PreferenceRead.Ready(BackupRecord(last = lastBackup, unfinished = false)),
            preferences.observeBackups().first()
        )
    }

    @Test
    fun anAbandonedSaveKeepsTheLastGoodBackup() = runTest {
        val preferences = DataStoreUserPreferences(HandDataStore())
        preferences.backupCompleted(lastBackup)

        preferences.backupStarted()
        preferences.backupAbandoned()

        assertEquals(
            PreferenceRead.Ready(BackupRecord(last = lastBackup, unfinished = false)),
            preferences.observeBackups().first()
        )
    }

    @Test
    fun aPartlyStoredBackupRecordIsDamageNotADifferentBackup() = runTest {
        val partial = mutablePreferencesOf(
            stringPreferencesKey("backup_last_name_v1") to "sello-respaldo.json"
        )
        val preferences = DataStoreUserPreferences(HandDataStore(partial))

        assertEquals(
            PreferenceRead.Failed<BackupRecord>(PreferenceFailure.InvalidValue, null),
            preferences.observeBackups().first()
        )
    }

    @Test
    fun theBackupRecordIsNeverPartOfTheAppearanceOrOrder() = runTest {
        val preferences = DataStoreUserPreferences(HandDataStore())
        preferences.backupCompleted(lastBackup)

        assertEquals(
            PreferenceRead.Ready(AppearancePreference()),
            preferences.observeAppearance().first()
        )
        assertEquals(
            PreferenceRead.Ready(CategoryOrderingPreference()),
            preferences.observeCategoryOrdering().first()
        )
    }

    @Test
    fun missingValuesHaveTheApprovedDefaults() = runTest {
        val preferences = DataStoreUserPreferences(HandDataStore())

        assertEquals(
            PreferenceRead.Ready(AppearancePreference()),
            preferences.observeAppearance().first()
        )
        assertEquals(
            PreferenceRead.Ready(CategoryOrderingPreference()),
            preferences.observeCategoryOrdering().first()
        )
    }

    @Test
    fun stableCodesSurviveANewAdapterReadingTheSameStore() = runTest {
        val data = HandDataStore()
        val first = DataStoreUserPreferences(data)

        assertEquals(PreferenceWrite.Saved, first.setTheme(ThemePreference.Dark))
        assertEquals(PreferenceWrite.Saved, first.setInk(InkPreference.Violeta))
        assertEquals(
            PreferenceWrite.Saved,
            first.setCategoryOrder(CategoryOrder.LatestExpenseAdded)
        )
        assertEquals(
            PreferenceWrite.Saved,
            first.setCategoryOrderDirection(CategoryOrderDirection.Ascending)
        )

        val reopened = DataStoreUserPreferences(data)
        assertEquals(
            PreferenceRead.Ready(AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)),
            reopened.observeAppearance().first()
        )
        assertEquals(
            PreferenceRead.Ready(
                CategoryOrderingPreference(
                    CategoryOrder.LatestExpenseAdded,
                    CategoryOrderDirection.Ascending
                )
            ),
            reopened.observeCategoryOrdering().first()
        )
    }

    @Test
    fun correctionDismissalsSurviveANewAdapterIndependently() = runTest {
        val data = HandDataStore()
        val first = DataStoreUserPreferences(data)

        assertEquals(PreferenceWrite.Saved, first.dismissPastExpenseAnnouncement())

        val reopened = DataStoreUserPreferences(data)
        assertEquals(
            PreferenceRead.Ready(CorrectionAnnouncementPreference(pastExpenseSeen = true)),
            reopened.observeCorrectionAnnouncements().first()
        )
        assertEquals(PreferenceWrite.Saved, reopened.dismissPastLimitAnnouncement())
        assertEquals(
            PreferenceRead.Ready(CorrectionAnnouncementPreference(true, true)),
            DataStoreUserPreferences(data).observeCorrectionAnnouncements().first()
        )
    }

    @Test
    fun aDismissedSuggestionSurvivesANewAdapterAndALaterOneReplacesIt() = runTest {
        val data = HandDataStore()
        val october = DismissedSuggestion(YearMonth.of(2026, 10), cafe)
        val november = DismissedSuggestion(YearMonth.of(2026, 11), cafe)

        assertEquals(
            PreferenceRead.Ready(null),
            DataStoreUserPreferences(data).observeDismissedSuggestion().first()
        )
        assertEquals(
            PreferenceWrite.Saved,
            DataStoreUserPreferences(data).dismissSuggestion(october)
        )
        assertEquals(
            PreferenceRead.Ready(october),
            DataStoreUserPreferences(data).observeDismissedSuggestion().first()
        )
        DataStoreUserPreferences(data).dismissSuggestion(november)
        assertEquals(
            PreferenceRead.Ready(november),
            DataStoreUserPreferences(data).observeDismissedSuggestion().first()
        )
    }

    @Test
    fun theHandledClosedMonthInviteSurvivesANewAdapterAndALaterOneReplacesIt() = runTest {
        val data = HandDataStore()
        val september = YearMonth.of(2026, 9)
        val october = YearMonth.of(2026, 10)

        assertEquals(
            PreferenceRead.Ready(null),
            DataStoreUserPreferences(data).observeHandled().first()
        )
        assertEquals(PreferenceWrite.Saved, DataStoreUserPreferences(data).markHandled(september))
        assertEquals(
            PreferenceRead.Ready(september),
            DataStoreUserPreferences(data).observeHandled().first()
        )
        DataStoreUserPreferences(data).markHandled(october)
        assertEquals(
            PreferenceRead.Ready(october),
            DataStoreUserPreferences(data).observeHandled().first()
        )
    }

    @Test
    fun resetForgetsTheHandledClosedMonthInviteAndNothingElseWasNeeded() = runTest {
        val data = HandDataStore()
        val preferences = DataStoreUserPreferences(data)
        preferences.markHandled(YearMonth.of(2026, 9))

        assertEquals(PreferenceWrite.Saved, preferences.resetAll())

        assertEquals(PreferenceRead.Ready(null), preferences.observeHandled().first())
    }

    @Test
    fun aDamagedHandledClosedMonthIsVisibleAsInvalid() = runTest {
        val raw = mutablePreferencesOf(
            stringPreferencesKey("recibo_closed_month_invite_handled_v1") to "septiembre"
        )
        val preferences = DataStoreUserPreferences(HandDataStore(raw))

        assertEquals(
            PreferenceRead.Failed<YearMonth?>(PreferenceFailure.InvalidValue, null),
            preferences.observeHandled().first()
        )
    }

    @Test
    fun whenTheCatchUpNoticeWasSeenSurvivesANewAdapterAndALaterTimeReplacesIt() = runTest {
        val data = HandDataStore()
        val first = Instant.parse("2026-10-22T14:00:00.250Z")
        val later = Instant.parse("2026-10-29T08:30:00Z")

        assertEquals(
            PreferenceRead.Ready(null),
            DataStoreUserPreferences(data).observeCatchUpNoticeSeen().first()
        )
        assertEquals(
            PreferenceWrite.Saved,
            DataStoreUserPreferences(data).markCatchUpNoticeSeen(first)
        )
        assertEquals(
            PreferenceRead.Ready(first),
            DataStoreUserPreferences(data).observeCatchUpNoticeSeen().first()
        )
        DataStoreUserPreferences(data).markCatchUpNoticeSeen(later)
        assertEquals(
            PreferenceRead.Ready(later),
            DataStoreUserPreferences(data).observeCatchUpNoticeSeen().first()
        )
        // Never part of what a reset of appearance or order touches.
        assertEquals(
            PreferenceRead.Ready(AppearancePreference()),
            DataStoreUserPreferences(data).observeAppearance().first()
        )
    }

    @Test
    fun aCatchUpNoticeThatCannotBeReadOrKeptFailsVisibly() = runTest {
        val preferences = DataStoreUserPreferences(UnavailableDataStore())

        assertEquals(
            PreferenceRead.Failed<Instant?>(PreferenceFailure.Unavailable, null),
            preferences.observeCatchUpNoticeSeen().first()
        )
        assertEquals(
            PreferenceWrite.Failed(PreferenceFailure.Unavailable),
            preferences.markCatchUpNoticeSeen(Instant.EPOCH)
        )
    }

    @Test
    fun aDamagedDismissalIsVisibleAsInvalid() = runTest {
        val raw =
            mutablePreferencesOf(stringPreferencesKey("resumen_suggestion_dismissed_v1") to "x")
        val preferences = DataStoreUserPreferences(HandDataStore(raw))

        assertEquals(
            PreferenceRead.Failed<DismissedSuggestion?>(PreferenceFailure.InvalidValue, null),
            preferences.observeDismissedSuggestion().first()
        )
    }

    private val cafe = (
        CategoryId.of("00000000-0000-4000-8000-000000000001") as Outcome.Success
        ).value

    @Test
    fun investmentsStartInPesosLargestValueFirstAndKeepTheirStoredCodes() = runTest {
        val data = HandDataStore()
        val first = DataStoreUserPreferences(data)
        assertEquals(
            PreferenceRead.Ready(InvestmentPreference(Currency.COP, InvestmentOrder.LargestValue)),
            first.observeInvestments().first()
        )

        assertEquals(PreferenceWrite.Saved, first.setNewInvestmentCurrency(Currency.USD))
        assertEquals(PreferenceWrite.Saved, first.setInvestmentOrder(InvestmentOrder.LatestUpdate))

        val reopened = DataStoreUserPreferences(data)
        assertEquals(
            PreferenceRead.Ready(InvestmentPreference(Currency.USD, InvestmentOrder.LatestUpdate)),
            reopened.observeInvestments().first()
        )
        // The codes are the stored contract, not enum names.
        val stored = data.data.first()
        assertEquals("usd", stored[stringPreferencesKey("investment_new_currency_v1")])
        assertEquals("latest_update", stored[stringPreferencesKey("investment_order_v1")])
    }

    @Test
    fun aCurrencyNoInvestmentMayHoldIsRefusedAndNothingIsStored() = runTest {
        val preferences = DataStoreUserPreferences(HandDataStore())

        assertEquals(
            PreferenceWrite.Failed(PreferenceFailure.InvalidValue),
            preferences.setNewInvestmentCurrency(Currency.EUR)
        )
        assertEquals(
            PreferenceRead.Ready(InvestmentPreference()),
            preferences.observeInvestments().first()
        )
    }

    @Test
    fun anUnknownInvestmentCodeIsVisibleAndResetReturnsTheDefaults() = runTest {
        val raw = mutablePreferencesOf(stringPreferencesKey("investment_order_v1") to "random")
        val preferences = DataStoreUserPreferences(HandDataStore(raw))
        assertEquals(
            PreferenceRead.Failed<InvestmentPreference>(PreferenceFailure.InvalidValue, null),
            preferences.observeInvestments().first()
        )

        assertEquals(PreferenceWrite.Saved, preferences.resetAll())

        assertEquals(
            PreferenceRead.Ready(InvestmentPreference()),
            preferences.observeInvestments().first()
        )
    }

    @Test
    fun everyInkIsStoredAsTheIdItHasAlwaysHad() = runTest {
        val data = HandDataStore()
        val preferences = DataStoreUserPreferences(data)
        for ((ink, id) in listOf(
            InkPreference.Violeta to "violeta",
            InkPreference.Cobalto to "cobalto"
        )) {
            assertEquals(PreferenceWrite.Saved, preferences.setInk(ink))
            assertEquals(id, data.data.first()[stringPreferencesKey("appearance_ink_v1")])
            val written = mutablePreferencesOf(stringPreferencesKey("appearance_ink_v1") to id)
            assertEquals(
                PreferenceRead.Ready(AppearancePreference(ink = ink)),
                DataStoreUserPreferences(HandDataStore(written)).observeAppearance().first()
            )
        }
    }

    @Test
    fun anUnknownStoredInkIsVisibleAndIsNotSilentlyRewritten() = runTest {
        val raw = mutablePreferencesOf(stringPreferencesKey("appearance_ink_v1") to "purpura")
        val data = HandDataStore(raw)
        val preferences = DataStoreUserPreferences(data)

        assertEquals(
            PreferenceRead.Failed<AppearancePreference>(PreferenceFailure.InvalidValue, null),
            preferences.observeAppearance().first()
        )
        assertEquals("purpura", data.data.first()[stringPreferencesKey("appearance_ink_v1")])
    }

    @Test
    fun anUnknownStoredCodeIsVisibleAndIsNotSilentlyRewritten() = runTest {
        val raw = mutablePreferencesOf(stringPreferencesKey("appearance_theme_v1") to "neon")
        val preferences = DataStoreUserPreferences(HandDataStore(raw))

        assertEquals(
            PreferenceRead.Failed<AppearancePreference>(PreferenceFailure.InvalidValue, null),
            preferences.observeAppearance().first()
        )
    }

    @Test
    fun readAndWriteFailuresStayNonfinancialAndObservable() = runTest {
        val preferences = DataStoreUserPreferences(UnavailableDataStore())

        assertEquals(
            PreferenceRead.Failed<AppearancePreference>(PreferenceFailure.Unavailable, null),
            preferences.observeAppearance().first()
        )
        assertEquals(
            PreferenceWrite.Failed(PreferenceFailure.Unavailable),
            preferences.setTheme(ThemePreference.Light)
        )
    }

    @Test
    fun aSuccessfulWriteReconnectsAConsumerAfterATemporaryReadFailure() = runTest {
        val preferences = DataStoreUserPreferences(OnceUnavailableDataStore())
        val observed = mutableListOf<PreferenceRead<AppearancePreference>>()
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            preferences.observeAppearance().take(2).toList(observed)
        }
        runCurrent()

        assertEquals(
            listOf(
                PreferenceRead.Failed<AppearancePreference>(
                    PreferenceFailure.Unavailable,
                    null
                )
            ),
            observed
        )

        assertEquals(PreferenceWrite.Saved, preferences.setTheme(ThemePreference.Dark))
        runCurrent()

        assertEquals(
            listOf(
                PreferenceRead.Failed<AppearancePreference>(
                    PreferenceFailure.Unavailable,
                    null
                ),
                PreferenceRead.Ready(AppearancePreference(theme = ThemePreference.Dark))
            ),
            observed
        )
        collecting.join()
    }

    @Test
    fun aRetryingObserverReconnectsTheConsumerThatAppliesTheAppTheme() = runTest {
        val preferences = DataStoreUserPreferences(OnceUnavailableDataStore())
        val appThemeReads = mutableListOf<PreferenceRead<AppearancePreference>>()
        val appTheme = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            preferences.observeAppearance().take(2).toList(appThemeReads)
        }
        runCurrent()

        assertEquals(
            listOf(
                PreferenceRead.Failed<AppearancePreference>(
                    PreferenceFailure.Unavailable,
                    null
                )
            ),
            appThemeReads
        )

        assertEquals(
            PreferenceRead.Ready(AppearancePreference()),
            preferences.observeAppearance().first()
        )
        runCurrent()

        assertEquals(
            listOf(
                PreferenceRead.Failed<AppearancePreference>(
                    PreferenceFailure.Unavailable,
                    null
                ),
                PreferenceRead.Ready(AppearancePreference())
            ),
            appThemeReads
        )
        appTheme.join()
    }

    @Test
    fun aDismissedIncomeSuggestionSurvivesANewAdapterAndALaterMonthReplacesIt() = runTest {
        val data = HandDataStore()
        val october = YearMonth.of(2026, 10)
        val november = YearMonth.of(2026, 11)

        assertEquals(
            PreferenceRead.Ready<YearMonth?>(null),
            DataStoreUserPreferences(data).observeDismissedIncomeSuggestion().first()
        )
        assertEquals(
            PreferenceWrite.Saved,
            DataStoreUserPreferences(data).dismissIncomeSuggestion(october)
        )
        assertEquals(
            PreferenceRead.Ready<YearMonth?>(october),
            DataStoreUserPreferences(data).observeDismissedIncomeSuggestion().first()
        )
        // The stored form is the month alone, and Resumen's own dismissal is untouched.
        val key = stringPreferencesKey("income_suggestion_dismissed_v1")
        assertEquals("2026-10", data.data.first()[key])
        assertEquals(
            PreferenceRead.Ready<DismissedSuggestion?>(null),
            DataStoreUserPreferences(data).observeDismissedSuggestion().first()
        )
        DataStoreUserPreferences(data).dismissIncomeSuggestion(november)
        assertEquals(
            PreferenceRead.Ready<YearMonth?>(november),
            DataStoreUserPreferences(data).observeDismissedIncomeSuggestion().first()
        )
    }

    @Test
    fun resetForgetsADismissedIncomeSuggestion() = runTest {
        val preferences = DataStoreUserPreferences(HandDataStore())
        preferences.dismissIncomeSuggestion(YearMonth.of(2026, 10))

        assertEquals(PreferenceWrite.Saved, preferences.resetAll())

        assertEquals(
            PreferenceRead.Ready<YearMonth?>(null),
            preferences.observeDismissedIncomeSuggestion().first()
        )
    }

    @Test
    fun anIncomeSuggestionDismissalThatCannotBeKeptOrIsDamagedFailsVisibly() = runTest {
        assertEquals(
            PreferenceWrite.Failed(PreferenceFailure.Unavailable),
            DataStoreUserPreferences(UnavailableDataStore())
                .dismissIncomeSuggestion(YearMonth.of(2026, 10))
        )
        val damaged = DataStoreUserPreferences(
            HandDataStore(
                mutablePreferencesOf(
                    stringPreferencesKey("income_suggestion_dismissed_v1") to "octubre"
                )
            )
        )
        assertEquals(
            PreferenceRead.Failed<YearMonth?>(PreferenceFailure.InvalidValue, null),
            damaged.observeDismissedIncomeSuggestion().first()
        )
    }
}
