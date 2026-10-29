package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.presentation.movement.MovementNoteProblem
import java.time.LocalDate

/** An investment offered to anotar a movement in, in the overview's order. */
@Immutable
data class InvestmentChoice(
    val id: String,
    val name: String,
    val iconKey: String,
    /** Its amounts are typed in dollars, with the comma key (rule 10). */
    val usd: Boolean
)

/**
 * The one plain fact under the chips (v2 `MOVE`), about the chosen investment as it is
 * recorded now. Amounts are as the app writes them, in the investment's currency.
 */
@Immutable
sealed interface InvestmentMovementFact {
    /** "Llevas 3.000.000 aportados en Ecopetrol." */
    data class Contributed(val amount: MoneyTextValue, val name: String) : InvestmentMovementFact

    /** "Último valor: 3.050.000, anotado el 9 oct. El nuevo lo reemplaza; no se suma." */
    data class LastValue(val amount: MoneyTextValue, val valuedOn: LocalDate) :
        InvestmentMovementFact

    /** Valor de hoy for an investment that has none yet. */
    data object FirstValue : InvestmentMovementFact

    /** "Vale 5.242.000 según el valor del 14 oct.", the value a retiro on its date may take. */
    data class Worth(val amount: MoneyTextValue, val valuedOn: LocalDate) : InvestmentMovementFact

    /** A retiro with no value recorded by its date (rule 7, IN18). */
    data object NoValue : InvestmentMovementFact

    /** "Llevas 178.000 en dividendos y rentas de Ecopetrol." */
    data class Dividends(val amount: MoneyTextValue, val name: String) : InvestmentMovementFact
}

/**
 * The later retiro a movement or a correction would leave short (rule 8, IN19): above the
 * [available] value it would have on its date, or with no value at all when that is null.
 */
@Immutable
data class BlockingRetiro(
    val amount: MoneyTextValue,
    val date: LocalDate,
    val available: MoneyTextValue?
)

/** Why the movement was not saved or deleted. None of these means it was. */
enum class InvestmentMovementError {
    NotSaved,
    FutureDate,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The investment or the movement no longer exists. */
    Gone,

    /** The investment changed since it was read; it is read again and can be sent again. */
    Changed,

    /** The amount is not in the investment's currency. */
    CurrencyMismatch,

    /** The correction asks for exactly what is recorded. */
    Unchanged,

    /** The only aporte cannot be deleted (rule 6, IN22). */
    LastContribution,
    NotDeleted
}

/**
 * Anotar movimiento (v2 Inversiones): an Aporte, Valor de hoy, Retiro or Dividendo o
 * renta, or the correction of one already recorded. [amount] is present only for a valid
 * amount; [amountText] is what was pasted when it could not be used. [overValue] is what
 * the investment is worth on the retiro's date when the retiro asks for more;
 * [noValue] when a retiro has no value to come from; [blockedBy] the later retiro the
 * movement would leave short; [firstContribution] the earliest aporte a movement is
 * dated before (rule 3).
 */
@Immutable
data class InvestmentMovementState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val editing: Boolean = false,
    val kind: InvestmentMovementKind = InvestmentMovementKind.Contribution,
    val amount: MoneyTextValue? = null,
    val amountText: String? = null,
    val limitPulse: Int = 0,
    val atLimit: Boolean = false,
    val investments: List<InvestmentChoice> = emptyList(),
    val investmentId: String? = null,
    val fact: InvestmentMovementFact? = null,
    val overValue: MoneyTextValue? = null,
    val noValue: Boolean = false,
    val blockedBy: BlockingRetiro? = null,
    val firstContribution: LocalDate? = null,
    val date: LocalDate,
    val today: LocalDate,
    val datePickerOpen: Boolean = false,
    val note: String = "",
    val composerOpen: Boolean = false,
    val composerDraft: String = "",
    val composerProblem: MovementNoteProblem? = null,
    val composerLength: Int = 0,
    /** Editing: something differs from what is stored. */
    val changed: Boolean = false,
    val busy: Boolean = false,
    val error: InvestmentMovementError? = null,
    val confirmDiscard: Boolean = false,
    /** The form is finished and should close. */
    val closed: Boolean = false
) {
    val investment: InvestmentChoice? get() = investments.firstOrNull { it.id == investmentId }

    /** Dollars are typed with the comma key; pesos keep 000. */
    val usd: Boolean get() = investment?.usd == true

    /** While saving, or once nothing can be sent as it is, the draft holds still. */
    val editable: Boolean
        get() = !busy && !closed && error !in Blocking

    val canSubmit: Boolean
        get() = !loading && !loadFailed && editable && amount != null && investment != null &&
            overValue == null && !noValue && blockedBy == null && firstContribution == null &&
            (!editing || changed)

    val canDelete: Boolean get() = editing && !loading && !loadFailed && editable

    /** "Anotar el valor de hoy" is offered where a retiro waits for a value. */
    val offersValue: Boolean
        get() = !editing && kind == InvestmentMovementKind.Withdrawal &&
            (overValue != null || noValue)

    private companion object {
        val Blocking = setOf(
            InvestmentMovementError.Unknown,
            InvestmentMovementError.DataChanged,
            InvestmentMovementError.Gone
        )
    }
}

sealed interface InvestmentMovementAction {
    data class Kind(val kind: InvestmentMovementKind) : InvestmentMovementAction

    /** "Anotar el valor de hoy": Valor de hoy for the same investment. */
    data object RecordValueFirst : InvestmentMovementAction

    data class Digit(val digit: Int) : InvestmentMovementAction

    data object TripleZero : InvestmentMovementAction

    /** The decimal comma, for dollars only. */
    data object Comma : InvestmentMovementAction

    data object Backspace : InvestmentMovementAction

    data object ClearAmount : InvestmentMovementAction

    /** Text from the clipboard, exactly as it was. Pesos only. */
    data class Paste(val text: String) : InvestmentMovementAction

    data class InvestmentPicked(val id: String) : InvestmentMovementAction

    data object OpenDatePicker : InvestmentMovementAction

    data object CloseDatePicker : InvestmentMovementAction

    data class DatePicked(val date: LocalDate) : InvestmentMovementAction

    data object OpenComposer : InvestmentMovementAction

    data class ComposerChanged(val text: String) : InvestmentMovementAction

    data object ComposerDone : InvestmentMovementAction

    data object ComposerCancelled : InvestmentMovementAction

    data object Submit : InvestmentMovementAction

    data object Retry : InvestmentMovementAction

    data object Delete : InvestmentMovementAction

    data object Back : InvestmentMovementAction

    data object DiscardConfirmed : InvestmentMovementAction

    data object KeepEditing : InvestmentMovementAction
}
