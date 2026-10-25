package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/** One entered rate as Dólar lists it: "4.150,25" and its date. */
@Immutable
data class RateModel(val rate: String, val date: LocalDate)

/** Dólar (investment rule 11; v2 *Dólares y la tasa*): the current rate and the earlier ones. */
sealed interface UsdRateState {
    data object Loading : UsdRateState

    /** The rates could not be read. Never shown as "no rate". */
    data object Failed : UsdRateState

    /** [current] is null when no rate was ever entered; [history] is newest date first. */
    @Immutable
    data class Ready(
        val current: RateModel?,
        val history: List<RateModel>,
        val today: LocalDate,
        val stale: Boolean = false
    ) : UsdRateState
}

sealed interface UsdRateAction {
    data object Retry : UsdRateAction

    data object Change : UsdRateAction
}

/** Why a rate was not saved. Certain refusals; [Unknown] is asked about again. */
enum class RateEntryError { Unknown, NotSaved, FutureDate, DataChanged }

/** Why the typed rate cannot be saved yet (rule 11, IN32). */
enum class RateProblem { BelowMinimum, AboveMaximum }

/**
 * Cambiar la tasa: pesos per dollar typed with the comma pad and a date, today or
 * earlier. [rate] is the draft as the pad wrote it, or null before any key.
 */
@Immutable
data class UsdRateEntryState(
    val loading: Boolean,
    val loadFailed: Boolean,
    val rate: String?,
    val problem: RateProblem?,
    val current: RateModel?,
    val date: LocalDate,
    val today: LocalDate,
    val datePickerOpen: Boolean = false,
    val limitPulse: Int = 0,
    val atLimit: Boolean = false,
    val busy: Boolean = false,
    val error: RateEntryError? = null,
    val closed: Boolean = false
) {
    val editable: Boolean get() = !busy && !closed
    val canSubmit: Boolean
        get() = editable && !loading && !loadFailed && rate != null && problem == null &&
            error != RateEntryError.Unknown
}

sealed interface UsdRateEntryAction {
    data class Digit(val digit: Int) : UsdRateEntryAction

    data object Comma : UsdRateEntryAction

    data object Backspace : UsdRateEntryAction

    data object Clear : UsdRateEntryAction

    data object OpenDatePicker : UsdRateEntryAction

    data object CloseDatePicker : UsdRateEntryAction

    data class DatePicked(val date: LocalDate) : UsdRateEntryAction

    data object Submit : UsdRateEntryAction

    data object Retry : UsdRateEntryAction

    data object Back : UsdRateEntryAction
}
