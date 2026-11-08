package com.software.sello.domain.port

import com.software.sello.domain.model.AppearancePreference
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
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.ThemePreference
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** A preference could not be read or written; it is never a financial-storage failure. */
sealed interface PreferenceFailure {
    data object Unavailable : PreferenceFailure
    data object InvalidValue : PreferenceFailure
}

sealed interface PreferenceRead<out T> {
    data class Ready<T>(val value: T) : PreferenceRead<T>
    data class Failed<T>(val failure: PreferenceFailure, val lastGood: T?) : PreferenceRead<T>
}

sealed interface PreferenceWrite {
    data object Saved : PreferenceWrite
    data class Failed(val failure: PreferenceFailure) : PreferenceWrite
}

/** Appearance only: consumers cannot use it as a general settings manager. */
interface AppearancePreferences {
    fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>>
    suspend fun setTheme(theme: ThemePreference): PreferenceWrite

    /** The ink picked in Configuración. Tuya's hue is kept when another ink is chosen. */
    suspend fun setInk(ink: InkPreference): PreferenceWrite

    /**
     * A restore's ink and Tuya's kept hue, exactly as the backup has them, in one write: a
     * null [tuyaHue] forgets it. Refused with [PreferenceFailure.InvalidValue] when Tuya is
     * [ink] and [tuyaHue] is not its hue.
     */
    suspend fun restoreInk(ink: InkPreference, tuyaHue: InkHue?): PreferenceWrite
}

/** The two persisted controls that affect Recibo's presentation order. */
interface CategoryOrderingPreferences {
    fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>>
    suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite
    suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection): PreferenceWrite
}

/** Inversiones' two settings; a new investment may still be created in either currency. */
interface InvestmentPreferences {
    fun observeInvestments(): Flow<PreferenceRead<InvestmentPreference>>

    /** Refuses with [PreferenceFailure.InvalidValue] a currency no investment may hold. */
    suspend fun setNewInvestmentCurrency(currency: Currency): PreferenceWrite
    suspend fun setInvestmentOrder(order: InvestmentOrder): PreferenceWrite
}

/**
 * Gastos hormiga's line, a portable preference (backup version 10): [SmallExpenseLine.DEFAULT]
 * until the person sets one. A stored value outside the line's range is damage, read as
 * [PreferenceFailure.InvalidValue], never clipped.
 */
interface SmallExpensePreferences {
    fun observeSmallExpenseLine(): Flow<PreferenceRead<SmallExpenseLine>>
    suspend fun setSmallExpenseLine(line: SmallExpenseLine): PreferenceWrite
}

/** Remembers only the two one-time correction announcements handed off to this ticket. */
interface CorrectionAnnouncementPreferences {
    fun observeCorrectionAnnouncements(): Flow<PreferenceRead<CorrectionAnnouncementPreference>>
    suspend fun dismissPastExpenseAnnouncement(): PreferenceWrite
    suspend fun dismissPastLimitAnnouncement(): PreferenceWrite
}

/** The one Resumen suggestion dismissed most recently; a new dismissal replaces it. */
interface SuggestionPreferences {
    fun observeDismissedSuggestion(): Flow<PreferenceRead<DismissedSuggestion?>>
    suspend fun dismissSuggestion(dismissed: DismissedSuggestion): PreferenceWrite
}

/**
 * The month whose "assign income to a category" suggestion on the income screen was
 * dismissed; a new dismissal replaces it. The suggestion may come back in a later month
 * while income is still unassigned. Nonfinancial: it hides advice, never a figure.
 */
interface IncomeSuggestionPreferences {
    fun observeDismissedIncomeSuggestion(): Flow<PreferenceRead<YearMonth?>>
    suspend fun dismissIncomeSuggestion(month: YearMonth): PreferenceWrite
}

/**
 * The latest closed month whose Recibo invitation was opened or dismissed; null before the
 * first time. A later month's invitation is a new one, so only the latest is kept. The year
 * card of January's first week stands in December's place, so handling it keeps December.
 */
interface ClosedMonthInvitePreferences {
    fun observeHandled(): Flow<PreferenceRead<YearMonth?>>
    suspend fun markHandled(month: YearMonth): PreferenceWrite
}

/**
 * When the catch-up notice was last dismissed with Listo, on the audit clock that also
 * stamps what catch-up records; null before the first time. Catch-up stamps what it
 * records after it, even with the clock set back since (#340).
 */
interface CatchUpNoticePreferences {
    fun observeCatchUpNoticeSeen(): Flow<PreferenceRead<Instant?>>

    suspend fun markCatchUpNoticeSeen(at: Instant): PreferenceWrite
}

/**
 * What guidance a person has seen (E38). Restablecer keeps all of it, and backups carry its
 * [GuidanceMemory] from version 11 on. A stored value that cannot be read is
 * [PreferenceFailure.InvalidValue], never taken as seen or not seen: while the guidance
 * cannot be read, no nudge shows. A write that fails leaves the nudge to show again later.
 */
interface GuidancePreferences {
    fun observeGuidance(): Flow<PreferenceRead<Guidance>>

    /** [nudge] was seen and closed for good: its action was done or its ✕ tapped. */
    suspend fun markSeen(nudge: Nudge): PreferenceWrite

    /**
     * [nudge] showed on [day], for the one-a-day rule. The backup reminder also records
     * [day] as the day it last showed, in the same write.
     */
    suspend fun markShown(nudge: Nudge, day: LocalDate): PreferenceWrite

    /** The backup reminder's "No volver a mostrar". */
    suspend fun turnOffBackupReminder(): PreferenceWrite

    /**
     * Once per phone: if the person [wasUsingSello] (had any expense, income, goal or
     * investment), every new-user nudge is marked seen; the Guía hint and the backup
     * reminder are not. Then [Guidance.existingUseChecked] is set, in the same write, and
     * asking again changes nothing.
     */
    suspend fun rememberExistingUse(wasUsingSello: Boolean): PreferenceWrite

    /**
     * Merges a restored backup's [memory] into the phone's, in one write, as
     * [GuidanceMemory.mergedWith] says. Refused, writing nothing, while the phone's own
     * guidance cannot be read.
     */
    suspend fun mergeRestored(memory: GuidanceMemory): PreferenceWrite
}
