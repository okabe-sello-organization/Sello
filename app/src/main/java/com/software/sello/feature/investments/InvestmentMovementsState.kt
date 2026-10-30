package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.presentation.movement.MovementNoteProblem
import java.time.LocalDate

/**
 * Why storage refused one line of a receipt (SELLO-129). Amounts are in the investment's
 * currency, as the app writes them.
 */
@Immutable
sealed interface MovementLineRefusal {
    /** A retiro above what the investment is worth on its date. */
    data class OverValue(val available: MoneyTextValue) : MovementLineRefusal

    /** A retiro with no value recorded by its date. */
    data object NoValue : MovementLineRefusal

    /** The line leaves the recorded retiro of [amount] on [date] above the value, or with none. */
    data class LeavesRetiroShort(val amount: MoneyTextValue, val date: LocalDate) :
        MovementLineRefusal

    /** Dated before the earliest aporte, [firstContribution]. */
    data class BeforeFirstContribution(val firstContribution: LocalDate) : MovementLineRefusal

    data object FutureDate : MovementLineRefusal

    /** An aporte, retiro or dividend of 0, or another amount the investment cannot hold. */
    data object InvalidAmount : MovementLineRefusal

    /** Any other reason storage gave for this line. */
    data object Other : MovementLineRefusal
}

/**
 * One line of Varios movimientos. [kind] is null until a type is chosen; [amount] is what
 * was typed, null while nothing is, and [valid] when the type may hold it.
 */
@Immutable
data class MovementLineModel(
    val key: String,
    val kind: InvestmentMovementKind?,
    val amount: MoneyTextValue?,
    val valid: Boolean,
    val date: LocalDate,
    val note: String,
    val refusal: MovementLineRefusal? = null
) {
    /** Lines without an amount are left out of the receipt. */
    val typed: Boolean get() = amount != null

    /** Typed with no type: it blocks the receipt. */
    val missingKind: Boolean get() = typed && kind == null

    /** Typed with a type that may not hold it, such as an aporte of 0. */
    val invalidAmount: Boolean get() = typed && kind != null && !valid
}

/** Why the receipt was not saved. None of these means anything was. */
enum class InvestmentMovementsError {
    NotSaved,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** The investment no longer exists. */
    Gone,

    /** The investment changed since it was read; it is read again and can be sent again. */
    Changed
}

/**
 * Varios movimientos (v2 *Anotar varios*): several movements of one investment on one
 * receipt, saved together or not at all. [highlighted] is always a line of [lines], which
 * hold 1 to 30. [removed] is the line just removed while it can still be put back.
 */
@Immutable
data class InvestmentMovementsState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The last read failed: the receipt works from the investment as it was read before. */
    val stale: Boolean = false,
    val name: String = "",
    val usd: Boolean = false,
    val lines: List<MovementLineModel>,
    val highlighted: Int = 0,
    /** Aportes − retiros of the lines, in the investment's currency. */
    val contributed: MoneyTextValue?,
    val today: LocalDate,
    val datePickerOpen: Boolean = false,
    val composerOpen: Boolean = false,
    val composerDraft: String = "",
    val composerProblem: MovementNoteProblem? = null,
    val composerLength: Int = 0,
    val removed: MovementLineModel? = null,
    val busy: Boolean = false,
    val error: InvestmentMovementsError? = null,
    val confirmDiscard: Boolean = false,
    /** The receipt is finished and should close. */
    val closed: Boolean = false
) {
    val current: MovementLineModel get() = lines[highlighted]

    /** Lines with an amount: the ones a save sends. */
    val counted: Int get() = lines.count { it.typed }

    val missingKinds: Int get() = lines.count { it.missingKind }

    val refused: Int get() = lines.count { it.refusal != null || it.invalidAmount }

    val full: Boolean get() = lines.size >= MAX_LINES

    /** While saving, or once nothing can be sent as it is, the receipt holds still. */
    val editable: Boolean
        get() = !loading && !loadFailed && !busy && !closed && error !in Blocking

    /** **Otro renglón**: below the most, once the highlighted line has an amount. */
    val canAdd: Boolean get() = editable && !full && current.typed

    val canSubmit: Boolean
        get() = editable && counted > 0 && missingKinds == 0 && refused == 0

    companion object {
        const val MAX_LINES = 30

        private val Blocking = setOf(
            InvestmentMovementsError.Unknown,
            InvestmentMovementsError.DataChanged,
            InvestmentMovementsError.Gone
        )
    }
}

sealed interface InvestmentMovementsAction {
    data class Select(val index: Int) : InvestmentMovementsAction

    data object AddLine : InvestmentMovementsAction

    data class Remove(val index: Int) : InvestmentMovementsAction

    /** Deshacer: the removed line goes back where it was. */
    data object UndoRemove : InvestmentMovementsAction

    /** The short time to put the removed line back is over. */
    data object RemovalShown : InvestmentMovementsAction

    data class Kind(val kind: InvestmentMovementKind) : InvestmentMovementsAction

    data class Digit(val digit: Int) : InvestmentMovementsAction

    data object TripleZero : InvestmentMovementsAction

    /** The decimal comma, for dollars only. */
    data object Comma : InvestmentMovementsAction

    data object Backspace : InvestmentMovementsAction

    data object ClearAmount : InvestmentMovementsAction

    data object OpenDatePicker : InvestmentMovementsAction

    data object CloseDatePicker : InvestmentMovementsAction

    data class DatePicked(val date: LocalDate) : InvestmentMovementsAction

    data object OpenComposer : InvestmentMovementsAction

    data class ComposerChanged(val text: String) : InvestmentMovementsAction

    data object ComposerDone : InvestmentMovementsAction

    data object ComposerCancelled : InvestmentMovementsAction

    data object Submit : InvestmentMovementsAction

    /** Comprobar si se guardó, or Reintentar after a failed first read. */
    data object Retry : InvestmentMovementsAction

    data object Back : InvestmentMovementsAction

    data object DiscardConfirmed : InvestmentMovementsAction

    data object KeepEditing : InvestmentMovementsAction
}
