package com.software.sello.domain.port

import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.CorrectionAnnouncementPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DismissedSuggestion
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.ThemePreference
import java.time.Instant
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
    suspend fun setInk(ink: InkPreference): PreferenceWrite
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
 * first time. A later month's invitation is a new one, so only the latest is kept.
 */
interface ClosedMonthInvitePreferences {
    fun observeHandled(): Flow<PreferenceRead<YearMonth?>>
    suspend fun markHandled(month: YearMonth): PreferenceWrite
}

/**
 * When the catch-up notice was last dismissed with Listo, on the audit clock that also
 * stamps what catch-up records; null before the first time.
 */
interface CatchUpNoticePreferences {
    fun observeCatchUpNoticeSeen(): Flow<PreferenceRead<Instant?>>

    suspend fun markCatchUpNoticeSeen(at: Instant): PreferenceWrite
}
