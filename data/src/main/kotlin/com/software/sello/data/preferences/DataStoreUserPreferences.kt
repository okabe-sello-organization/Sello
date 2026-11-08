package com.software.sello.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
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
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DismissedSuggestion
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.ClosedMonthInvitePreferences
import com.software.sello.domain.port.CorrectionAnnouncementPreferences
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.IncomeSuggestionPreferences
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.SmallExpensePreferences
import com.software.sello.domain.port.SuggestionPreferences
import com.software.sello.domain.policy.EffectiveDates
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
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
    InvestmentPreferences,
    SmallExpensePreferences,
    CorrectionAnnouncementPreferences,
    SuggestionPreferences,
    IncomeSuggestionPreferences,
    ClosedMonthInvitePreferences,
    CatchUpNoticePreferences,
    GuidancePreferences,
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

    /**
     * Tuya and its hue go in one edit, so Tuya is never stored without one. A hand-made ink
     * or Sistema, which stores no hue of its own, leaves Tuya's hue where it is, unless the
     * stored one is damaged (not a whole degree from 0 to 359): then it goes in the same edit,
     * so the pick reads back.
     */
    override suspend fun setInk(ink: InkPreference): PreferenceWrite = write {
        this[INK] = ink.id
        when (ink) {
            is InkPreference.Tuya -> this[INK_HUE] = ink.hue.degrees

            InkPreference.Cobalto, InkPreference.Violeta, InkPreference.Sistema -> {
                val kept = asMap()[INK_HUE]
                if (kept != null && (kept as? Int)?.toLong()?.let(InkHue::of) == null) {
                    remove(INK_HUE)
                }
            }
        }
    }

    override suspend fun restoreInk(ink: InkPreference, tuyaHue: InkHue?): PreferenceWrite {
        if (ink is InkPreference.Tuya && ink.hue != tuyaHue) {
            return PreferenceWrite.Failed(PreferenceFailure.InvalidValue)
        }
        return write {
            this[INK] = ink.id
            if (tuyaHue == null) remove(INK_HUE) else this[INK_HUE] = tuyaHue.degrees
        }
    }

    override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
        observe(::categoryOrdering)

    override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite =
        write { this[CATEGORY_ORDER] = order.stored }

    override suspend fun setCategoryOrderDirection(
        direction: CategoryOrderDirection
    ): PreferenceWrite = write { this[CATEGORY_DIRECTION] = direction.stored }

    override fun observeInvestments(): Flow<PreferenceRead<InvestmentPreference>> =
        observe(::investments)

    override suspend fun setNewInvestmentCurrency(currency: Currency): PreferenceWrite {
        val stored =
            currency.stored ?: return PreferenceWrite.Failed(PreferenceFailure.InvalidValue)
        return write { this[INVESTMENT_CURRENCY] = stored }
    }

    override suspend fun setInvestmentOrder(order: InvestmentOrder): PreferenceWrite =
        write { this[INVESTMENT_ORDER] = order.stored }

    override fun observeSmallExpenseLine(): Flow<PreferenceRead<SmallExpenseLine>> =
        observe(::smallExpenseLine)

    override suspend fun setSmallExpenseLine(line: SmallExpenseLine): PreferenceWrite =
        write { this[SMALL_EXPENSE_LINE] = line.pesos }

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

    override fun observeDismissedIncomeSuggestion(): Flow<PreferenceRead<YearMonth?>> =
        observe(::dismissedIncomeSuggestion)

    override suspend fun dismissIncomeSuggestion(month: YearMonth): PreferenceWrite =
        write { this[INCOME_SUGGESTION_DISMISSED] = month.toString() }

    override fun observeHandled(): Flow<PreferenceRead<YearMonth?>> = observe(::closedMonthInvite)

    override suspend fun markHandled(month: YearMonth): PreferenceWrite =
        write { this[CLOSED_MONTH_INVITE_HANDLED] = month.toString() }

    override fun observeCatchUpNoticeSeen(): Flow<PreferenceRead<Instant?>> = observe { stored ->
        Decoded.Value(stored[CATCH_UP_NOTICE_SEEN]?.let(Instant::ofEpochMilli))
    }

    override suspend fun markCatchUpNoticeSeen(at: Instant): PreferenceWrite =
        write { this[CATCH_UP_NOTICE_SEEN] = at.toEpochMilli() }

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
        this[BACKUP_INVESTMENTS] = last.summary.investments
        remove(BACKUP_UNFINISHED)
    }

    override suspend fun backupAbandoned(): PreferenceWrite = write { remove(BACKUP_UNFINISHED) }

    override fun observeGuidance(): Flow<PreferenceRead<Guidance>> = observe(::guidance)

    override suspend fun markSeen(nudge: Nudge): PreferenceWrite =
        write { this[seenKey(nudge)] = true }

    override suspend fun markShown(nudge: Nudge, day: LocalDate): PreferenceWrite = write {
        this[GUIDANCE_LAST_SHOWN] = "${EffectiveDates.storedText(day)}|${nudge.stored}"
        if (nudge == Nudge.BackupReminder) {
            this[GUIDANCE_BACKUP_REMINDER_SHOWN] = EffectiveDates.storedText(day)
        }
    }

    override suspend fun turnOffBackupReminder(): PreferenceWrite =
        write { this[GUIDANCE_BACKUP_REMINDER_OFF] = true }

    /** Read and written in one edit, so two starts never both decide. */
    override suspend fun rememberExistingUse(wasUsingSello: Boolean): PreferenceWrite =
        writeReading { current ->
            if (current.existingUseChecked) return@writeReading
            if (wasUsingSello) {
                Nudge.entries.filter { it.forNewUsers }.forEach { this[seenKey(it)] = true }
            }
            this[GUIDANCE_EXISTING_USE_CHECKED] = true
        }

    /** Read and written in one edit, so a nudge seen meanwhile is never lost. */
    override suspend fun mergeRestored(memory: GuidanceMemory): PreferenceWrite =
        writeReading { current ->
            val merged = current.memory.mergedWith(memory)
            merged.seen.forEach { this[seenKey(it)] = true }
            merged.lastShown?.let {
                this[GUIDANCE_LAST_SHOWN] =
                    "${EffectiveDates.storedText(it.day)}|${it.nudge.stored}"
            }
            merged.backupReminderShown?.let {
                this[GUIDANCE_BACKUP_REMINDER_SHOWN] = EffectiveDates.storedText(it)
            }
            if (merged.backupReminderOff) this[GUIDANCE_BACKUP_REMINDER_OFF] = true
        }

    /**
     * Every key but the guidance's goes in one edit, so each reads its first-run default
     * again; what guidance was seen stays (E38: Restablecer does not clear it).
     */
    override suspend fun resetAll(): PreferenceWrite = write {
        asMap().keys.filterNot { it in GUIDANCE_KEYS }.forEach { remove(it) }
    }

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

    /**
     * Like [write], with the guidance as stored passed to [change] inside the same edit.
     * Guidance that cannot be read refuses the write and changes nothing.
     */
    private suspend fun writeReading(change: MutablePreferences.(Guidance) -> Unit) = try {
        write {
            when (val current = guidance(this)) {
                is Decoded.Value -> change(current.value)
                Decoded.Invalid -> throw UnreadableGuidance()
            }
        }
    } catch (_: UnreadableGuidance) {
        PreferenceWrite.Failed(PreferenceFailure.InvalidValue)
    }

    /** Thrown inside an edit only, so DataStore keeps what it had. */
    private class UnreadableGuidance : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    /**
     * Each key is checked for its own type here, since a write reads them too: a value of
     * another type, a day that is not `yyyy-MM-dd` or an unknown nudge is damage.
     */
    private fun guidance(stored: Preferences): Decoded<Guidance> {
        val values = stored.asMap()
        fun flag(key: Preferences.Key<Boolean>): Boolean? = when (val value = values[key]) {
            null -> false
            is Boolean -> value
            else -> null
        }
        fun day(text: String): LocalDate? = (EffectiveDates.parseDate(text) as? Outcome.Success)?.value
        val seen = Nudge.entries.filter { nudge ->
            flag(seenKey(nudge)) ?: return Decoded.Invalid
        }.toSet()
        val lastShown = when (val raw = values[GUIDANCE_LAST_SHOWN]) {
            null -> null

            is String -> {
                val parts = raw.split('|')
                if (parts.size != 2) return Decoded.Invalid
                ShownNudge(
                    nudge = Nudge.entries.firstOrNull { it.stored == parts[1] }
                        ?: return Decoded.Invalid,
                    day = day(parts[0]) ?: return Decoded.Invalid
                )
            }

            else -> return Decoded.Invalid
        }
        val reminderShown = when (val raw = values[GUIDANCE_BACKUP_REMINDER_SHOWN]) {
            null -> null
            is String -> day(raw) ?: return Decoded.Invalid
            else -> return Decoded.Invalid
        }
        return Decoded.Value(
            Guidance(
                memory = GuidanceMemory(
                    seen = seen,
                    lastShown = lastShown,
                    backupReminderShown = reminderShown,
                    backupReminderOff = flag(GUIDANCE_BACKUP_REMINDER_OFF)
                        ?: return Decoded.Invalid
                ),
                existingUseChecked = flag(GUIDANCE_EXISTING_USE_CHECKED)
                    ?: return Decoded.Invalid
            )
        )
    }

    private fun appearance(stored: Preferences): Decoded<AppearancePreference> {
        val theme = when (stored[THEME] ?: ThemePreference.System.stored) {
            "system" -> ThemePreference.System
            "light" -> ThemePreference.Light
            "dark" -> ThemePreference.Dark
            else -> return Decoded.Invalid
        }
        // A hue outside 0 to 359, beside any ink, or Tuya without one is damage: refused,
        // never clipped or dropped.
        val hue = stored[INK_HUE]?.let { InkHue.of(it.toLong()) ?: return Decoded.Invalid }
        val ink = InkPreference.of(stored[INK] ?: InkPreference.Cobalto.id, hue)
            ?: return Decoded.Invalid
        return Decoded.Value(AppearancePreference(theme, ink, hue))
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

    private fun investments(stored: Preferences): Decoded<InvestmentPreference> {
        val currency = when (stored[INVESTMENT_CURRENCY] ?: "cop") {
            "cop" -> Currency.COP
            "usd" -> Currency.USD
            else -> return Decoded.Invalid
        }
        val order = when (stored[INVESTMENT_ORDER] ?: InvestmentOrder.LargestValue.stored) {
            "largest_value" -> InvestmentOrder.LargestValue
            "return" -> InvestmentOrder.Return
            "alphabetical" -> InvestmentOrder.Alphabetical
            "newest" -> InvestmentOrder.Newest
            "latest_update" -> InvestmentOrder.LatestUpdate
            else -> return Decoded.Invalid
        }
        return Decoded.Value(InvestmentPreference(currency, order))
    }

    /** Whole pesos; absent is the default, and outside the line's range is damage. */
    private fun smallExpenseLine(stored: Preferences): Decoded<SmallExpenseLine> {
        val pesos = stored[SMALL_EXPENSE_LINE] ?: return Decoded.Value(SmallExpenseLine.DEFAULT)
        return SmallExpenseLine.of(pesos)?.let { Decoded.Value(it) } ?: Decoded.Invalid
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

    /** Stored as `2026-10`; anything else is damage, not "nothing dismissed". */
    private fun dismissedIncomeSuggestion(stored: Preferences): Decoded<YearMonth?> {
        val raw = stored[INCOME_SUGGESTION_DISMISSED] ?: return Decoded.Value(null)
        val month = runCatching { YearMonth.parse(raw) }.getOrNull() ?: return Decoded.Invalid
        return Decoded.Value(month)
    }

    /** Stored as `2026-10`; anything else is damage, not "nothing handled". */
    private fun closedMonthInvite(stored: Preferences): Decoded<YearMonth?> {
        val raw = stored[CLOSED_MONTH_INVITE_HANDLED] ?: return Decoded.Value(null)
        val month = runCatching { YearMonth.parse(raw) }.getOrNull() ?: return Decoded.Invalid
        return Decoded.Value(month)
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
        // And in one kept before investments existed.
        val investments = stored[BACKUP_INVESTMENTS] ?: 0
        if (goals < 0 || schedules < 0 || investments < 0) return Decoded.Invalid
        val last = LastBackup(
            at = Instant.ofEpochMilli(stored[BACKUP_AT]!!),
            name = stored[BACKUP_NAME]!!,
            bytes = bytes,
            summary = BackupSummary(counts[0], counts[1], counts[2], goals, schedules, investments)
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

        /** Tuya's hue, a whole degree: always there with Tuya, and kept when another ink is. */
        val INK_HUE = intPreferencesKey("appearance_ink_hue_v1")
        val CATEGORY_ORDER = stringPreferencesKey("recibo_category_order_v1")
        val CATEGORY_DIRECTION = stringPreferencesKey("recibo_category_direction_v1")
        val INVESTMENT_CURRENCY = stringPreferencesKey("investment_new_currency_v1")
        val INVESTMENT_ORDER = stringPreferencesKey("investment_order_v1")
        val SMALL_EXPENSE_LINE = longPreferencesKey("resumen_small_expense_line_v1")
        val PAST_EXPENSE_SEEN = booleanPreferencesKey("past_expense_correction_seen_v1")
        val PAST_LIMIT_SEEN = booleanPreferencesKey("past_limit_correction_seen_v1")
        val SUGGESTION_DISMISSED = stringPreferencesKey("resumen_suggestion_dismissed_v1")
        val INCOME_SUGGESTION_DISMISSED =
            stringPreferencesKey("income_suggestion_dismissed_v1")
        val CLOSED_MONTH_INVITE_HANDLED =
            stringPreferencesKey("recibo_closed_month_invite_handled_v1")
        val CATCH_UP_NOTICE_SEEN = longPreferencesKey("catch_up_notice_seen_v1")
        val BACKUP_UNFINISHED = booleanPreferencesKey("backup_unfinished_v1")
        val BACKUP_AT = longPreferencesKey("backup_last_at_v1")
        val BACKUP_NAME = stringPreferencesKey("backup_last_name_v1")
        val BACKUP_BYTES = longPreferencesKey("backup_last_bytes_v1")
        val BACKUP_CATEGORIES = intPreferencesKey("backup_last_categories_v1")
        val BACKUP_EXPENSES = intPreferencesKey("backup_last_expenses_v1")
        val BACKUP_INCOMES = intPreferencesKey("backup_last_incomes_v1")
        val BACKUP_GOALS = intPreferencesKey("backup_last_goals_v1")
        val BACKUP_SCHEDULES = intPreferencesKey("backup_last_schedules_v1")
        val BACKUP_INVESTMENTS = intPreferencesKey("backup_last_investments_v1")

        /** `2026-11-08|guide_hint`: the day the last nudge showed, and which one. */
        val GUIDANCE_LAST_SHOWN = stringPreferencesKey("guidance_last_shown_v1")
        val GUIDANCE_BACKUP_REMINDER_SHOWN =
            stringPreferencesKey("guidance_backup_reminder_shown_v1")
        val GUIDANCE_BACKUP_REMINDER_OFF = booleanPreferencesKey("guidance_backup_reminder_off_v1")
        val GUIDANCE_EXISTING_USE_CHECKED =
            booleanPreferencesKey("guidance_existing_use_checked_v1")

        /** A stable name per nudge, never its ordinal or class name. */
        val Nudge.stored: String
            get() = when (this) {
                Nudge.GuideHint -> "guide_hint"
                Nudge.BackupReminder -> "backup_reminder"
                Nudge.BulkEntry -> "bulk_entry"
                Nudge.FixedPayment -> "fixed_payment"
                Nudge.Carry -> "carry"
                Nudge.Deleting -> "deleting"
                Nudge.Investments -> "investments"
                Nudge.ReciboGoal -> "recibo_goal"
            }

        /** True once [nudge] was seen and closed for good. */
        fun seenKey(nudge: Nudge) = booleanPreferencesKey("guidance_seen_${nudge.stored}_v1")

        /** What Restablecer keeps. */
        val GUIDANCE_KEYS: Set<Preferences.Key<*>> = Nudge.entries.map(::seenKey).toSet() +
            setOf(
                GUIDANCE_LAST_SHOWN,
                GUIDANCE_BACKUP_REMINDER_SHOWN,
                GUIDANCE_BACKUP_REMINDER_OFF,
                GUIDANCE_EXISTING_USE_CHECKED
            )

        val ThemePreference.stored: String
            get() = when (this) {
                ThemePreference.System -> "system"
                ThemePreference.Light -> "light"
                ThemePreference.Dark -> "dark"
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

        /** Only the currencies an investment may hold have a stored value. */
        val Currency.stored: String?
            get() = when (this) {
                Currency.COP -> "cop"
                Currency.USD -> "usd"
                Currency.EUR, Currency.GBP -> null
            }

        val InvestmentOrder.stored: String
            get() = when (this) {
                InvestmentOrder.LargestValue -> "largest_value"
                InvestmentOrder.Return -> "return"
                InvestmentOrder.Alphabetical -> "alphabetical"
                InvestmentOrder.Newest -> "newest"
                InvestmentOrder.LatestUpdate -> "latest_update"
            }
    }
}
