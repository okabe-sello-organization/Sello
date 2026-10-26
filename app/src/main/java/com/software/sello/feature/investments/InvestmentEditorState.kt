package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import java.time.LocalDate

/** Why a name or a description cannot be used yet. Nothing is said before something is typed. */
enum class InvestmentTextError { TooLong, NotAllowed }

/** Which figure the Sello pad is typing, or which date pill is open. */
enum class InvestmentField {
    /** Primer aporte. */
    First,

    /** Vale hoy, Otro valor. */
    Value,

    /** ¿A cuánto está el dólar? */
    Rate
}

/** Why saving did not finish. Every one but [Unknown] is certain: nothing was written. */
enum class InvestmentSaveError {
    NotSaved,

    /** Not known whether it was saved: asked about again before anything else is sent. */
    Unknown,

    /** The investment or the history changed since the form read it; it is read again. */
    Changed,

    /** A date after today. */
    FutureDate,

    /** An amount or a date the investment cannot hold. */
    Invalid,

    /** A dollar investment and no rate any more: the rate slip asks for one (IN33). */
    RateRequired,

    /** Editing asked for exactly what is saved. */
    Unchanged
}

/** How the form ended. Kept in state so a rotation cannot lose it. */
sealed interface InvestmentEditorResult {
    data class Created(val investmentId: String) : InvestmentEditorResult

    data object Updated : InvestmentEditorResult

    data object Deleted : InvestmentEditorResult

    data object Discarded : InvestmentEditorResult
}

/**
 * Nueva inversión and Editar inversión (v2 *Agregar / editar inversión*, *Dólares y la
 * tasa*). [name] and [description] are exactly what was typed; [firstContribution],
 * [otherValue] and [rate] are what the pad built, null before any key (empty is not
 * zero). [firstReady], [valueReady] and [rateReady] say whether each figure is one that
 * can be saved. [rateNeeded] is a dollar investment while no rate exists (rule 12).
 */
@Immutable
data class InvestmentEditorState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val editing: Boolean = false,
    val name: String = "",
    val nameLength: Int = 0,
    val nameError: InvestmentTextError? = null,
    val description: String = "",
    val descriptionLength: Int = 0,
    val descriptionError: InvestmentTextError? = null,
    val iconKey: String? = null,
    val currency: Currency = Currency.COP,
    val firstContribution: MoneyTextValue? = null,
    val firstReady: Boolean = false,
    val date: LocalDate = LocalDate.of(2026, 1, 1),
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    /** Vale hoy: Lo mismo que aporté (true, the default) or Otro valor. */
    val sameValue: Boolean = true,
    val otherValue: MoneyTextValue? = null,
    val valueReady: Boolean = false,
    val rateNeeded: Boolean = false,
    /** The typed rate as the pad wrote it: "4.180,7". */
    val rate: String? = null,
    val rateProblem: RateProblem? = null,
    val rateReady: Boolean = false,
    val rateDate: LocalDate = LocalDate.of(2026, 1, 1),
    /** The current rate, named in one quiet line when a dollar investment has one. */
    val currentRate: RateModel? = null,
    /** The figure the open pad types into; null when the pad is closed. */
    val padField: InvestmentField? = null,
    /** Bumped when a key was refused for length, so the figure can shake. */
    val pulse: Int = 0,
    /** The figure whose last key was refused: two decimals at most, or too many digits. */
    val atLimit: InvestmentField? = null,
    /** The date pill whose picker is open: [InvestmentField.First] or [InvestmentField.Rate]. */
    val datePicker: InvestmentField? = null,
    val movementCount: Int = 0,
    val busy: Boolean = false,
    val saveError: InvestmentSaveError? = null,
    val confirmDelete: Boolean = false,
    val confirmDiscard: Boolean = false,
    /** Editing: something differs from what is stored. */
    val changed: Boolean = false,
    val result: InvestmentEditorResult? = null
) {
    val padOpen: Boolean get() = padField != null

    /** The new investment's id once it is created, for its detail to open. */
    val created: String? get() = (result as? InvestmentEditorResult.Created)?.investmentId

    /** Whether the form can still be changed. */
    val editable: Boolean get() = result == null && !loading && !loadFailed && !busy

    /**
     * Crear inversión waits for a name, an icon and a first contribution, a value when
     * Otro valor is chosen, and the rate when a dollar investment needs one. Guardar
     * cambios waits for a valid name and icon. Once the form has ended, nothing more is sent.
     */
    val canSubmit: Boolean
        get() = editable && nameLength in 1..MAX_NAME && nameError == null &&
            descriptionError == null && iconKey != null &&
            saveError != InvestmentSaveError.Unknown &&
            (editing || (firstReady && (sameValue || valueReady) && (!rateNeeded || rateReady)))

    companion object {
        const val MAX_NAME = 40
        const val MAX_DESCRIPTION = 40
    }
}

sealed interface InvestmentEditorAction {
    data class NameChanged(val text: String) : InvestmentEditorAction

    data class DescriptionChanged(val text: String) : InvestmentEditorAction

    data class IconPicked(val key: String) : InvestmentEditorAction

    data class CurrencyPicked(val currency: Currency) : InvestmentEditorAction

    /** Vale hoy: true for Lo mismo que aporté, false for Otro valor. */
    data class SameValuePicked(val same: Boolean) : InvestmentEditorAction

    /** The Sello pad, always under a new investment, types into [field] from now on. */
    data class TypeInto(val field: InvestmentField) : InvestmentEditorAction

    data class Digit(val digit: Int) : InvestmentEditorAction

    data object TripleZero : InvestmentEditorAction

    data object Comma : InvestmentEditorAction

    data object Backspace : InvestmentEditorAction

    data object Clear : InvestmentEditorAction

    data class OpenDatePicker(val field: InvestmentField) : InvestmentEditorAction

    data object CloseDatePicker : InvestmentEditorAction

    data class DatePicked(val date: LocalDate) : InvestmentEditorAction

    data object Submit : InvestmentEditorAction

    data object Retry : InvestmentEditorAction

    data object Back : InvestmentEditorAction

    data object DiscardConfirmed : InvestmentEditorAction

    data object KeepEditing : InvestmentEditorAction

    data object AskToDelete : InvestmentEditorAction

    data object CancelDelete : InvestmentEditorAction

    data object Delete : InvestmentEditorAction
}
