package com.software.sello.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.CorrectionAnnouncementPreference
import com.software.sello.domain.model.DismissedSuggestion
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CorrectionAnnouncementPreferences
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.SuggestionPreferences
import java.io.IOException
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** The app's own preferences file; a sandbox names its own in `StorageNames`. */
internal const val PREFERENCES_FILE = "sello-preferences"

/**
 * The sole owner of Sello's nonfinancial preferences. Financial values deliberately
 * have no key here: they remain Room data with the command/recovery guarantees it has.
 */
class DataStoreUserPreferences internal constructor(private val data: DataStore<Preferences>) :
    AppearancePreferences,
    CategoryOrderingPreferences,
    CorrectionAnnouncementPreferences,
    SuggestionPreferences,
    BackupRecords,
    PreferenceReset {
    private val readAttempt = MutableStateFlow(0L)

    /** [fileName] is the profile's preferences file; see `StorageNames`. */
    constructor(
        context: Context,
        scope: CoroutineScope,
        fileName: String = PREFERENCES_FILE
    ) : this(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.preferencesDataStoreFile(fileName) }
        )
    )

    override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> =
        observe(::appearance)

    override suspend fun setTheme(theme: ThemePreference): PreferenceWrite =
        write { this[THEME] = theme.stored }

    override suspend fun setInk(ink: InkPreference): PreferenceWrite =
        write { this[INK] = ink.stored }

    override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
        observe(::categoryOrdering)

    override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite =
        write { this[CATEGORY_ORDER] = order.stored }

    override suspend fun setCategoryOrderDirection(
        direction: CategoryOrderDirection
    ): PreferenceWrite = write { this[CATEGORY_DIRECTION] = direction.stored }

    override fun observeCorrectionAnnouncements():
        Flow<PreferenceRead<CorrectionAnnouncementPreference>> =
        observe(::correctionAnnouncements)

    override suspend fun dismissPastExpenseAnnouncement(): PreferenceWrite =
        write { this[PAST_EXPENSE_SEEN] = true }

    override suspend fun dismissPastLimitAnnouncement(): PreferenceWrite =
        write { this[PAST_LIMIT_SEEN] = true }

    override fun observeDismissedSuggestion(): Flow<PreferenceRead<DismissedSuggestion?>> =
        observe(::dismissedSuggestion)

    override suspend fun dismissSuggestion(dismissed: DismissedSuggestion): PreferenceWrite =
        write { this[SUGGESTION_DISMISSED] = "${dismissed.month}|${dismissed.categoryId.value}" }

    override fun observeBackups(): Flow<PreferenceRead<BackupRecord>> = observe(::backups)

    override suspend fun backupStarted(): PreferenceWrite = write { this[BACKUP_UNFINISHED] = true }

    override suspend fun backupCompleted(last: LastBackup): PreferenceWrite = write {
        this[BACKUP_AT] = last.at.toEpochMilli()
        this[BACKUP_NAME] = last.name
        this[BACKUP_BYTES] = last.bytes
        this[BACKUP_CATEGORIES] = last.summary.categories
        this[BACKUP_EXPENSES] = last.summary.expenses
        this[BACKUP_INCOMES] = last.summary.incomes
        this[BACKUP_GOALS] = last.summary.goals
        this[BACKUP_SCHEDULES] = last.summary.schedules
        remove(BACKUP_UNFINISHED)
    }

    override suspend fun backupAbandoned(): PreferenceWrite = write { remove(BACKUP_UNFINISHED) }

    /** Every key goes in one edit, so each reads its first-run default again. */
    override suspend fun resetAll(): PreferenceWrite = write { clear() }

    /**
     * A failed DataStore collection ends, so keep the public flow alive around each attempt.
     * A new observer (including an explicit screen retry) or a successful write reconnects every
     * active consumer, including the app-wide theme and Recibo ordering collectors.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observe(decoder: (Preferences) -> Decoded<T>): Flow<PreferenceRead<T>> =
        readAttempt
            .onStart { advanceReadAttempt() }
            .flatMapLatest { observeOnce(decoder) }
            .distinctUntilChanged()

    private fun <T> observeOnce(decoder: (Preferences) -> Decoded<T>): Flow<PreferenceRead<T>> =
        flow {
            var lastGood: T? = null
            try {
                data.data.collect { stored ->
                    when (val decoded = decoder(stored)) {
                        is Decoded.Value -> {
                            lastGood = decoded.value
                            emit(PreferenceRead.Ready(decoded.value))
                        }

                        Decoded.Invalid -> emit(
                            PreferenceRead.Failed(PreferenceFailure.InvalidValue, lastGood)
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                emit(PreferenceRead.Failed(PreferenceFailure.Unavailable, lastGood))
            } catch (_: ClassCastException) {
                emit(PreferenceRead.Failed(PreferenceFailure.InvalidValue, lastGood))
            }
        }

    private fun advanceReadAttempt() {
        readAttempt.update { current -> if (current == Long.MAX_VALUE) 0L else current + 1L }
    }

    private suspend fun write(
        change: androidx.datastore.preferences.core.MutablePreferences.() -> Unit
    ): PreferenceWrite = try {
        data.edit(change)
        advanceReadAttempt()
        PreferenceWrite.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        PreferenceWrite.Failed(PreferenceFailure.Unavailable)
    }

    private fun appearance(stored: Preferences): Decoded<AppearancePreference> {
        val theme = when (stored[THEME] ?: ThemePreference.System.stored) {
            "system" -> ThemePreference.System
            "light" -> ThemePreference.Light
            "dark" -> ThemePreference.Dark
            else -> return Decoded.Invalid
        }
        val ink = when (stored[INK] ?: InkPreference.Cobalto.stored) {
            "cobalto" -> InkPreference.Cobalto
            "violeta" -> InkPreference.Violeta
            else -> return Decoded.Invalid
        }
        return Decoded.Value(AppearancePreference(theme, ink))
    }

    private fun categoryOrdering(stored: Preferences): Decoded<CategoryOrderingPreference> {
        val order = when (stored[CATEGORY_ORDER] ?: CategoryOrder.MostUsed.stored) {
            "most_used" -> CategoryOrder.MostUsed
            "alphabetical" -> CategoryOrder.Alphabetical
            "amount_spent" -> CategoryOrder.AmountSpent
            "limit" -> CategoryOrder.Limit
            "latest_expense_added" -> CategoryOrder.LatestExpenseAdded
            else -> return Decoded.Invalid
        }
        val defaultDirection = CategoryOrderDirection.Descending.stored
        val direction = when (stored[CATEGORY_DIRECTION] ?: defaultDirection) {
            "descending" -> CategoryOrderDirection.Descending
            "ascending" -> CategoryOrderDirection.Ascending
            else -> return Decoded.Invalid
        }
        return Decoded.Value(CategoryOrderingPreference(order, direction))
    }

    private fun correctionAnnouncements(stored: Preferences) = Decoded.Value(
        CorrectionAnnouncementPreference(
            pastExpenseSeen = stored[PAST_EXPENSE_SEEN] ?: false,
            pastLimitSeen = stored[PAST_LIMIT_SEEN] ?: false
        )
    )

    /** Stored as `2026-10|<category id>`; anything else is damage, not "nothing dismissed". */
    private fun dismissedSuggestion(stored: Preferences): Decoded<DismissedSuggestion?> {
        val raw = stored[SUGGESTION_DISMISSED] ?: return Decoded.Value(null)
        val parts = raw.split('|')
        if (parts.size != 2) return Decoded.Invalid
        val month = runCatching { YearMonth.parse(parts[0]) }.getOrNull() ?: return Decoded.Invalid
        val id = (CategoryId.of(parts[1]) as? Outcome.Success)?.value ?: return Decoded.Invalid
        return Decoded.Value(DismissedSuggestion(month, id))
    }

    /** The last backup is all of its keys or none of them; anything between is damage. */
    private fun backups(stored: Preferences): Decoded<BackupRecord> {
        val unfinished = stored[BACKUP_UNFINISHED] ?: false
        val keys = listOf(
            BACKUP_AT,
            BACKUP_NAME,
            BACKUP_BYTES,
            BACKUP_CATEGORIES,
            BACKUP_EXPENSES,
            BACKUP_INCOMES
        )
        val present = keys.count { it in stored }
        if (present == 0) return Decoded.Value(BackupRecord(null, unfinished))
        if (present != keys.size) return Decoded.Invalid
        val bytes = stored[BACKUP_BYTES]!!
        val counts =
            listOf(stored[BACKUP_CATEGORIES]!!, stored[BACKUP_EXPENSES]!!, stored[BACKUP_INCOMES]!!)
        if (bytes < 0 || counts.any { it < 0 }) return Decoded.Invalid
        // Absent in a record kept before goals existed (0.1.0), when there were none.
        val goals = stored[BACKUP_GOALS] ?: 0
        // Likewise in one kept before schedules existed.
        val schedules = stored[BACKUP_SCHEDULES] ?: 0
        if (goals < 0 || schedules < 0) return Decoded.Invalid
        val last = LastBackup(
            at = Instant.ofEpochMilli(stored[BACKUP_AT]!!),
            name = stored[BACKUP_NAME]!!,
            bytes = bytes,
            summary = BackupSummary(counts[0], counts[1], counts[2], goals, schedules)
        )
        return Decoded.Value(BackupRecord(last, unfinished))
    }

    private sealed interface Decoded<out T> {
        data class Value<T>(val value: T) : Decoded<T>
        data object Invalid : Decoded<Nothing>
    }

    private companion object {
        val THEME = stringPreferencesKey("appearance_theme_v1")
        val INK = stringPreferencesKey("appearance_ink_v1")
        val CATEGORY_ORDER = stringPreferencesKey("recibo_category_order_v1")
        val CATEGORY_DIRECTION = stringPreferencesKey("recibo_category_direction_v1")
        val PAST_EXPENSE_SEEN = booleanPreferencesKey("past_expense_correction_seen_v1")
        val PAST_LIMIT_SEEN = booleanPreferencesKey("past_limit_correction_seen_v1")
        val SUGGESTION_DISMISSED = stringPreferencesKey("resumen_suggestion_dismissed_v1")
        val BACKUP_UNFINISHED = booleanPreferencesKey("backup_unfinished_v1")
        val BACKUP_AT = longPreferencesKey("backup_last_at_v1")
        val BACKUP_NAME = stringPreferencesKey("backup_last_name_v1")
        val BACKUP_BYTES = longPreferencesKey("backup_last_bytes_v1")
        val BACKUP_CATEGORIES = intPreferencesKey("backup_last_categories_v1")
        val BACKUP_EXPENSES = intPreferencesKey("backup_last_expenses_v1")
        val BACKUP_INCOMES = intPreferencesKey("backup_last_incomes_v1")
        val BACKUP_GOALS = intPreferencesKey("backup_last_goals_v1")
        val BACKUP_SCHEDULES = intPreferencesKey("backup_last_schedules_v1")

        val ThemePreference.stored: String
            get() = when (this) {
                ThemePreference.System -> "system"
                ThemePreference.Light -> "light"
                ThemePreference.Dark -> "dark"
            }

        val InkPreference.stored: String
            get() = when (this) {
                InkPreference.Cobalto -> "cobalto"
                InkPreference.Violeta -> "violeta"
            }

        val CategoryOrder.stored: String
            get() = when (this) {
                CategoryOrder.MostUsed -> "most_used"
                CategoryOrder.Alphabetical -> "alphabetical"
                CategoryOrder.AmountSpent -> "amount_spent"
                CategoryOrder.Limit -> "limit"
                CategoryOrder.LatestExpenseAdded -> "latest_expense_added"
            }

        val CategoryOrderDirection.stored: String
            get() = when (this) {
                CategoryOrderDirection.Descending -> "descending"
                CategoryOrderDirection.Ascending -> "ascending"
            }
    }
}
