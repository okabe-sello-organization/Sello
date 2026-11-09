package com.software.sello.presentation.corrections

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreGoalMovement
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.domain.port.UndoGoalCompletion
import com.software.sello.presentation.carry.CarryCorrectionList
import com.software.sello.presentation.carry.CarryCorrectionText
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is said, for a moment, about the last correction of an expense. */
sealed interface CorrectionNotice {
    /**
     * An expense was deleted. With [canUndo] it can still be put back; [undoing] while
     * that has been asked, and [undoFailed] when the last attempt did not work and
     * another can be made.
     */
    data class Deleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false,
        /** With carry: how many later months the deletion changed (CP21). */
        val carryMonths: Int? = null
    ) : CorrectionNotice

    /** The deleted expense is back. */
    data object Restored : CorrectionNotice

    /**
     * A deletion confirmed after its list: whether it was saved could not be found out. It
     * stays until it is asked about again with its own operation ([Corrections.checkDeletion]),
     * [checking] meanwhile, or until a later correction takes its place.
     */
    data class DeleteUnknown(val checking: Boolean = false) : CorrectionNotice

    /** A deletion confirmed after its list was not made: the data changed, or storage failed. */
    data object NotDeleted : CorrectionNotice

    /** The time to undo was over when undo was asked. The deletion stands. */
    data object UndoTooLate : CorrectionNotice

    /** Putting the expense back would contradict what is recorded now. The deletion stands. */
    data object UndoBlocked : CorrectionNotice

    /** An edit was saved. */
    data object Updated : CorrectionNotice

    /**
     * A category's changes were saved. [limitOf] is the finished month whose limit was
     * changed, when it was one: that month's figures now follow the new limit. [carry] is
     * carry turned on or off by the same save.
     */
    data class CategorySaved(val limitOf: YearMonth?, val carry: SavedCarry? = null) :
        CorrectionNotice

    /**
     * A limit set from Ajustar presupuesto, as its receipt confirmed: "Límite de Café para
     * octubre: 150.000", or "desde octubre" when [fromThisMonth].
     */
    data class LimitAdjusted(
        val categoryName: String,
        val limit: MoneyTextValue,
        val month: YearMonth,
        val fromThisMonth: Boolean
    ) : CorrectionNotice

    data object CategoryArchived : CorrectionNotice

    data object CategoryUnarchived : CorrectionNotice

    /**
     * A category was deleted, as its receipt confirmed (ADR 0024): [name], how many
     * [expenses] it had, and the category they moved to, or null when they went with it.
     */
    data class CategoryDeleted(val name: String, val expenses: Int, val movedTo: String?) :
        CorrectionNotice

    /** The same as [Deleted], [Restored], [UndoTooLate], [UndoBlocked] and [Updated], for an income. */
    data class IncomeDeleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    data object IncomeRestored : CorrectionNotice

    data object IncomeUndoTooLate : CorrectionNotice

    data object IncomeUndoBlocked : CorrectionNotice

    data object IncomeUpdated : CorrectionNotice

    /**
     * A save that storage confirmed, said with the confirmation look: a check before
     * [message]. Whoever saved writes the words from the record read back, so nothing is
     * said before the save is confirmed. [spoken] can carry the full amount.
     */
    data class Confirmed(val message: String, val spoken: String = message) : CorrectionNotice

    /**
     * A new expense storage confirmed, as it was read back: "Gasto anotado: 48.700 en
     * Comida". [amount] is null when it is saved but could not be read back, and
     * [categoryName] when its category could not be named; it is saved all the same.
     */
    data class ExpenseRecorded(val amount: MoneyTextValue?, val categoryName: String?) :
        CorrectionNotice

    /** The same for a new income: "Ingreso anotado: 2.000.000". */
    data class IncomeRecorded(val amount: MoneyTextValue?) : CorrectionNotice

    /**
     * A receipt of several new expenses storage confirmed: "3 gastos anotados: 126.000",
     * the [count] of lines and their [total].
     */
    data class ExpensesRecorded(val count: Int, val total: MoneyTextValue) : CorrectionNotice

    /** The same for a receipt of several new incomes: "3 ingresos anotados: 1.420.000". */
    data class IncomesRecorded(val count: Int, val total: MoneyTextValue) : CorrectionNotice

    /**
     * A new schedule storage confirmed, as it was read back: "Pago fijo guardado: Arriendo,
     * el 5 de cada mes". [name] is its note, else its category or the name of an Otro
     * source; an income without either is named by [sourceKey]. [rule] and [start] are
     * null when it is saved but could not be read back.
     */
    data class ScheduleSaved(
        val name: String?,
        val rule: RepeatRule?,
        val start: LocalDate?,
        val income: Boolean,
        val sourceKey: String? = null
    ) : CorrectionNotice

    /** A schedule changed, paused, resumed or deleted, as its receipt confirmed. */
    data class ScheduleChanged(val change: ScheduleChange, val income: Boolean) : CorrectionNotice

    /** The same as [Deleted], [Restored], [UndoTooLate], [UndoBlocked] and [Updated], for a goal's movement. */
    data class GoalMovementDeleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    data object GoalMovementRestored : CorrectionNotice

    data object GoalMovementUndoTooLate : CorrectionNotice

    data object GoalMovementUndoBlocked : CorrectionNotice

    data object GoalMovementUpdated : CorrectionNotice

    /** A goal marked lograda; with [canUndo] Deshacer returns it to 100 %, waiting. */
    data class GoalCompleted(
        val goalName: String,
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : CorrectionNotice

    data class GoalCompletionUndone(val goalName: String) : CorrectionNotice

    data object GoalCompletionUndoTooLate : CorrectionNotice

    /**
     * A new aporte, or with [withdrawal] a retiro, storage confirmed: "Aporte anotado:
     * 350.000". [amount] is null when it is saved but could not be read back.
     */
    data class GoalMovementRecorded(
        val withdrawal: Boolean,
        val amount: MoneyTextValue?,
        val celebration: GoalCelebration? = null
    ) : CorrectionNotice

    /**
     * A receipt of [count] aportes and retiros storage confirmed: "3 movimientos anotados:
     * +130.000", [net] being aportes minus retiros. [net] is null when it could not be worked out.
     * [celebrations] has one per goal whose stamps the receipt changed, in line order; they are
     * said one after the other, [turn] being the one said now.
     */
    data class GoalMovementsRecorded(
        val count: Int,
        val net: MoneyTextValue?,
        val celebrations: List<GoalCelebration> = emptyList(),
        val turn: Int = 0
    ) : CorrectionNotice {
        val celebration: GoalCelebration? get() = celebrations.getOrNull(turn)
    }

    /** A new target, storage confirmed, that earned or relit a stamp (rule 7, MT18). */
    data class GoalTargetChanged(val celebration: GoalCelebration) : CorrectionNotice

    /** The same as [GoalMovementDeleted] and the notices after it, for an investment's movement. */
    data class InvestmentMovementDeleted(
        val canUndo: Boolean,
        val undoing: Boolean = false,
        val undoFailed: Boolean = false
    ) : InvestmentMovementNotice

    data object InvestmentMovementRestored : InvestmentMovementNotice

    data object InvestmentMovementUndoTooLate : InvestmentMovementNotice

    data object InvestmentMovementUndoBlocked : InvestmentMovementNotice

    data object InvestmentMovementUpdated : InvestmentMovementNotice

    /**
     * A new movement of [kind] storage confirmed: "Aporte anotado: 500.000". [amount] is
     * null when it is saved but could not be read back.
     */
    data class InvestmentMovementRecorded(
        val kind: InvestmentMovementKind,
        val amount: MoneyTextValue?
    ) : InvestmentMovementNotice

    /**
     * A receipt of [count] movements storage confirmed (SELLO-134): "3 movimientos
     * anotados en Ecopetrol". [investmentName] is null when it could not be read back.
     */
    data class InvestmentMovementsRecorded(val count: Int, val investmentName: String?) :
        InvestmentMovementNotice
}

/** What is said about an investment's movement (SELLO-110), drawn in its own words. */
sealed interface InvestmentMovementNotice : CorrectionNotice

/** The stamps a confirmed save earned, if this notice says so. */
val CorrectionNotice.celebration: GoalCelebration?
    get() = when (this) {
        is CorrectionNotice.GoalMovementRecorded -> celebration
        is CorrectionNotice.GoalMovementsRecorded -> celebration
        is CorrectionNotice.GoalTargetChanged -> celebration
        else -> null
    }

/**
 * What one confirmed save did to a goal's stamps, to be celebrated once: places
 * [earned] for the first time, places [relit] after a withdrawal or a target change, and
 * whether the Meta was [reachedMeta], waiting to be marked. [missing] is what is still
 * left for the target. Never made for a replay or a save found after an unknown outcome.
 */
data class GoalCelebration(
    val goalId: String,
    val goalName: String,
    val earned: List<GoalPlace>,
    val relit: List<GoalPlace>,
    val reachedMeta: Boolean,
    val missing: MoneyTextValue? = null
)

/** An expense deleted once its list was confirmed; [number] tells one deletion from the next. */
data class ConfirmedDeletion(val expenseId: ExpenseId, val number: Long)

/** How a deletion ended. Only [Deleted] means the expense is gone. */
enum class DeleteResult {
    Deleted,
    Gone,
    Changed,
    DataChanged,
    Failed,

    /**
     * It reaches a closed month of a carry chain: nothing was deleted, and [Corrections.question]
     * shows the later months it changes before it is confirmed (ADR 0020 CP20, CP21).
     */
    AwaitsConfirmation,

    /** It may or may not have been deleted: its outcome was not known and asking failed. */
    Unknown
}

/** Which correction a list waits to confirm. */
enum class CorrectionKind { Delete, Undo }

/**
 * The later months a deletion, or undoing one, changes, shown before it is saved. With
 * [changed] the list was read again because the data moved meanwhile (SC34); while [busy]
 * the confirmed change is being sent and nothing can be pressed.
 */
data class CorrectionQuestion(
    val kind: CorrectionKind,
    val list: CarryCorrectionList,
    val changed: Boolean = false,
    val busy: Boolean = false
)

/**
 * Deleting an expense, an income, a goal's movement or an investment's movement, undoing that, and what is said
 * about the last correction. One instance serves the whole process, so the offer to undo is the same
 * whatever screen is showing and however often it is redrawn, and a deletion is
 * finished and offered even if the screen that asked for it is gone. There is one
 * offer for every kind, as in storage: a later deletion takes its place.
 *
 * The time to undo belongs to the commands; this only stops showing the offer when
 * that time is over, by the same [monotonic] clock. [scope] must outlive screens and
 * run everything on the one thread these functions are called from.
 */
class Corrections(
    private val commands: ExpenseCommands,
    private val incomes: IncomeCommands,
    private val goals: GoalCommands,
    private val investments: InvestmentCommands,
    private val monotonic: MonotonicClock,
    private val ids: RecordIdSource,
    private val scope: CoroutineScope,
    private val categories: CategoryReads,
    money: MoneyFormatter
) {
    private val correctionText = CarryCorrectionText(money)

    /** A deletion waiting for its list to be confirmed, with what came with the list. */
    private class Asked(
        val expenseId: ExpenseId,
        val expectedVersion: Long,
        val generation: Long,
        val confirmation: CorrectionConfirmation,
        val months: Int
    )

    private var asked: Asked? = null

    /**
     * A deletion confirmed after its list whose outcome is not known: asked about, never
     * resent. Kept apart from what the bar shows, so a later notice never loses it: once that
     * notice is over, the bar asks about it again. In memory only (#331 ruling).
     */
    private class Unresolved(val command: DeleteExpense, val months: Int) {
        var checking = false
    }

    private var unresolved: Unresolved? = null

    /** What a check answered while another notice held the bar: said once the bar is free. */
    private var owed: CorrectionNotice? = null
    private var askedUndo: CorrectionConfirmation? = null
    private val asking = MutableStateFlow<CorrectionQuestion?>(null)

    /** A list waiting to be confirmed before a deletion, or its undoing, is sent. */
    val question: StateFlow<CorrectionQuestion?> = asking

    private val confirmedDeleted = MutableStateFlow<ConfirmedDeletion?>(null)
    private var confirmedCount = 0L

    /**
     * The last expense deleted once its list was confirmed, kept as state: a form open on it
     * when it happened closes (the deletion was not its own answer). Each deletion has its
     * own [ConfirmedDeletion.number], so a form compares with what it saw when it opened and
     * never closes for one that came before it; undoing the deletion clears it.
     */
    val confirmedDeletion: StateFlow<ConfirmedDeletion?> = confirmedDeleted

    private val done = MutableStateFlow<Set<Nudge>>(emptySet())

    /**
     * The nudges whose action storage confirmed in this process, wherever it was done
     * (SELLO-212): an expense deleted, a pago fijo created, carry turned on. It only grows,
     * so a reader never misses one; each closes its nudge for good.
     */
    val nudgesDone: StateFlow<Set<Nudge>> = done

    /**
     * The deletion on offer. [undoOperation] names every attempt to undo it, so an
     * attempt whose outcome was not known is asked again and never done twice.
     */
    private class Pending(
        val kind: Kind,
        val deletionId: OperationId,
        val generation: Long,
        val until: Duration,
        var undoOperation: OperationId,
        val goalName: String = "",
        val carryMonths: Int? = null
    )

    /** What the deletion on offer removed. */
    private enum class Kind { Expense, Income, GoalMovement, GoalCompletion, InvestmentMovement }

    /** How an attempt to undo ended. [Retry]: nothing was restored and nothing forbids it. */
    private enum class Undone { Restored, TooLate, Blocked, Retry, Asked }

    /** What became of a restore command, in the terms every kind has in common. */
    private sealed interface Sent {
        data object Committed : Sent

        data object Unknown : Sent

        data object Expired : Sent

        data object Conflict : Sent

        data object Other : Sent

        /** An expense's restore changes later months of a chain: its list is shown first. */
        data class Asks(val question: ExpenseRejection.CorrectionUnconfirmed) : Sent
    }

    private val shown = MutableStateFlow<CorrectionNotice?>(null)
    val notice: StateFlow<CorrectionNotice?> = shown

    private var pending: Pending? = null

    /** The last completion whose undo was answered: restored, blocked or too late. */
    private var answered: OperationId? = null
    private var ending: Job? = null
    private var landed: GoalCelebration? = null

    /** The offer in the words of what was deleted. */
    private fun offer(
        kind: Kind,
        canUndo: Boolean,
        undoing: Boolean = false,
        failed: Boolean = false,
        goalName: String = "",
        carryMonths: Int? = null
    ) = when (kind) {
        Kind.Expense -> CorrectionNotice.Deleted(canUndo, undoing, failed, carryMonths)

        Kind.Income -> CorrectionNotice.IncomeDeleted(canUndo, undoing, failed)

        Kind.GoalMovement -> CorrectionNotice.GoalMovementDeleted(canUndo, undoing, failed)

        Kind.GoalCompletion -> CorrectionNotice.GoalCompleted(goalName, canUndo, undoing, failed)

        Kind.InvestmentMovement ->
            CorrectionNotice.InvestmentMovementDeleted(canUndo, undoing, failed)
    }

    /** Whether an offer is on screen, and if so whether it can be taken and is being taken. */
    private fun offerShown(): Pair<Boolean, Boolean>? = when (val now = shown.value) {
        is CorrectionNotice.Deleted -> now.canUndo to now.undoing
        is CorrectionNotice.IncomeDeleted -> now.canUndo to now.undoing
        is CorrectionNotice.GoalMovementDeleted -> now.canUndo to now.undoing
        is CorrectionNotice.GoalCompleted -> now.canUndo to now.undoing
        is CorrectionNotice.InvestmentMovementDeleted -> now.canUndo to now.undoing
        else -> null
    }

    /**
     * Deletes the expense as the caller read it. It runs to its end even if the caller
     * is cancelled meanwhile; the caller then gets the cancellation, not a result.
     */
    suspend fun delete(
        expenseId: ExpenseId,
        expectedVersion: Long,
        generation: Long
    ): DeleteResult = scope.async {
        asked = null
        asking.value = null
        val command = DeleteExpense(operation(), generation, expenseId, expectedVersion)
        deleteResult(command, months = null)
    }.await()

    /**
     * Sends [command] as the caller made it, so a caller that keeps its operation can ask
     * about it after an unknown outcome or a restart (then [expenseDeleted]). A deletion
     * that commits is offered to undo; one that needs its list shows it ([question]).
     */
    suspend fun deleteExpense(command: DeleteExpense): ExpenseCommandOutcome = scope.async {
        asked = null
        asking.value = null
        sendDelete(command, months = null)
    }.await()

    /**
     * A deletion the caller found committed by asking about its operation. With no time left
     * to undo it, it is only said, and never takes the place of another deletion on offer.
     */
    fun expenseDeleted(receipt: OperationReceipt) {
        val until = foundOffer(receipt.operationId) ?: return
        deleted(Kind.Expense, receipt.operationId, receipt.generation, until.value)
    }

    /**
     * How long a deletion found after the fact can still be undone, wrapped so that null
     * inside means it is only said; null itself when another deletion's offer is to stay.
     */
    private fun foundOffer(operation: OperationId): Found? {
        val until = commands.undoOffer()?.takeIf { it.deletionId == operation }?.until
            ?.takeIf { it > monotonic.elapsed() }
        if (until == null && pending.let { it != null && it.deletionId != operation }) return null
        return Found(until)
    }

    private class Found(val value: Duration?)

    /**
     * Whether a deletion of [expense] confirmed after its list is still unresolved. Then
     * nothing new may be sent for it: the bar asks about that one again instead, unless
     * another deletion's Deshacer is on it (it does once that is over).
     */
    fun holdsDeletionOf(expense: ExpenseId): Boolean {
        val held = unresolved?.takeIf { it.command.expenseId == expense } ?: return false
        if (pending == null) {
            ending?.cancel()
            shown.value = CorrectionNotice.DeleteUnknown(held.checking)
        }
        return true
    }

    /**
     * Sends the deletion and says what it means: only a receipt says it was deleted, and
     * an unknown outcome is asked about, never sent again.
     */
    private suspend fun deleteResult(command: DeleteExpense, months: Int?): DeleteResult =
        when (val outcome = sendDelete(command, months)) {
            is CommandOutcome.Committed -> DeleteResult.Deleted

            // A question that cannot be answered says nothing either way.
            is CommandOutcome.OutcomeUnknown ->
                when (val found = commands.find(command.operationId)) {
                    is Outcome.Success -> when (found.value) {
                        null -> DeleteResult.Failed
                        else -> expenseGone(command, months)
                    }

                    is Outcome.Failure -> DeleteResult.Unknown
                }

            is CommandOutcome.Rejected -> when (val reason = outcome.reason) {
                Specific(ExpenseRejection.ExpenseMissing) -> DeleteResult.Gone

                is CommandRejection.VersionConflict -> DeleteResult.Changed

                is CommandRejection.StaleGeneration -> DeleteResult.DataChanged

                is Specific -> when (reason.reason) {
                    is ExpenseRejection.CorrectionUnconfirmed -> DeleteResult.AwaitsConfirmation
                    else -> DeleteResult.Failed
                }

                else -> DeleteResult.Failed
            }
        }

    /**
     * Sends the deletion, with the confirmation that came with its list when one was
     * shown. A list that is needed, or needed again, is asked and nothing is deleted.
     */
    private suspend fun sendDelete(command: DeleteExpense, months: Int?): ExpenseCommandOutcome {
        val outcome = commands.delete(command)
        when (outcome) {
            is CommandOutcome.Committed -> expenseGone(command, months)

            is CommandOutcome.Rejected -> {
                val asks = (outcome.reason as? Specific)?.reason
                if (asks is ExpenseRejection.CorrectionUnconfirmed) {
                    val list = list(asks.correction)
                    asked = Asked(
                        command.expenseId,
                        command.expectedVersion,
                        command.generation,
                        asks.confirmation,
                        asks.correction.months.size
                    )
                    asking.value = CorrectionQuestion(
                        CorrectionKind.Delete,
                        list,
                        changed = command.confirmation != null
                    )
                }
            }

            is CommandOutcome.OutcomeUnknown -> Unit
        }
        return outcome
    }

    /**
     * The deletion [command] committed: it is offered to undo while that can be done. One
     * [found] after the fact never takes the place of another deletion still on offer.
     */
    private fun expenseGone(
        command: DeleteExpense,
        months: Int?,
        found: Boolean = false
    ): DeleteResult {
        done.update { it + Nudge.Deleting }
        asked = null
        asking.value = null
        if (command.confirmation != null) {
            confirmedDeleted.value = ConfirmedDeletion(command.expenseId, ++confirmedCount)
        }
        val until = if (found) {
            // Another deletion's Deshacer stays; this one is not said over it.
            (foundOffer(command.operationId) ?: return DeleteResult.Deleted).value
        } else {
            commands.undoOffer()?.takeIf { it.deletionId == command.operationId }?.until
        }
        return deleted(
            Kind.Expense,
            command.operationId,
            command.generation,
            until,
            carryMonths = months
        )
    }

    /** The list of later months for [correction], with the categories' names. */
    private suspend fun list(correction: CarryCorrection): CarryCorrectionList {
        val month = correction.firstAffected ?: correction.edited.firstOrNull()?.month
        val names = month?.let { categories.monthBudget(it) as? Outcome.Success }?.value
            ?.categories.orEmpty().associate { it.category.id to it.category }
        return correctionText.list(correction, names)
    }

    /**
     * The list shown is confirmed: the deletion, or its undoing, is sent with what came
     * with the list. Asked again while that is being done, it does nothing.
     */
    fun confirmQuestion() {
        val shown = asking.value ?: return
        if (shown.busy) return
        asking.value = shown.copy(busy = true)
        when (shown.kind) {
            CorrectionKind.Delete -> {
                val wanted = asked ?: return run { asking.value = null }
                // One confirmed before is still unresolved: it is asked about first, never
                // replaced, and this one is not sent.
                val held = unresolved
                if (held != null) {
                    asking.value = null
                    asked = null
                    ending?.cancel()
                    pending = null
                    this.shown.value = CorrectionNotice.DeleteUnknown(held.checking)
                    return
                }
                scope.launch {
                    val command = DeleteExpense(
                        operation(),
                        wanted.generation,
                        wanted.expenseId,
                        wanted.expectedVersion,
                        wanted.confirmation
                    )
                    val result = deleteResult(command, wanted.months)
                    if (result != DeleteResult.AwaitsConfirmation &&
                        result != DeleteResult.Deleted
                    ) {
                        // The list's screen may be gone; what happened is said here.
                        asking.value = null
                        asked = null
                        if (result == DeleteResult.Unknown) {
                            unknown(Unresolved(command, wanted.months))
                        } else {
                            said(CorrectionNotice.NotDeleted)
                        }
                    }
                }
            }

            CorrectionKind.Undo -> {
                val offered = pending ?: return undoListExpired()
                // Once confirmed, the answer decides what is said, not the time running out,
                // and the bar's Deshacer is held: the same operation is never sent twice.
                ending?.cancel()
                this.shown.value = offer(
                    offered.kind,
                    canUndo = true,
                    undoing = true,
                    goalName = offered.goalName,
                    carryMonths = offered.carryMonths
                )
                scope.launch { settle(offered, restore(offered, askedUndo)) }
            }
        }
    }

    /** Says [deletion] may or may not have happened, with no end: it waits to be checked. */
    private fun unknown(deletion: Unresolved) {
        ending?.cancel()
        pending = null
        unresolved = deletion
        shown.value = CorrectionNotice.DeleteUnknown()
    }

    /**
     * Asks how the confirmed deletion said to be unknown ended, with its own operation, never
     * by sending it again. Found committed, it is offered to undo while its time lasts; found
     * not, it is said that nothing was deleted; still not known, it waits to be asked again.
     */
    fun checkDeletion() {
        val wanted = unresolved ?: return
        if (wanted.checking || shown.value !is CorrectionNotice.DeleteUnknown) return
        wanted.checking = true
        shown.value = CorrectionNotice.DeleteUnknown(checking = true)
        scope.launch {
            val found = try {
                commands.find(wanted.command.operationId)
            } finally {
                wanted.checking = false
            }
            // A later notice may be on the bar meanwhile: it is not said over.
            val onBar = shown.value is CorrectionNotice.DeleteUnknown
            when (found) {
                is Outcome.Failure -> if (onBar) shown.value = CorrectionNotice.DeleteUnknown()

                is Outcome.Success -> {
                    unresolved = null
                    when {
                        found.value != null -> expenseGone(wanted.command, wanted.months, true)

                        onBar -> said(CorrectionNotice.NotDeleted)

                        // Kept for when the notice holding the bar is over.
                        else -> owed = CorrectionNotice.NotDeleted
                    }
                }
            }
        }
    }

    /**
     * The time to undo ran out while its list was open: the list goes and the person is told
     * the deletion stands, never left with a button that does nothing.
     */
    private fun undoListExpired() {
        asking.value = null
        askedUndo = null
        said(CorrectionNotice.UndoTooLate)
    }

    /** The list shown is not confirmed: nothing is sent, and an offer to undo stays. */
    fun dismissQuestion() {
        val shown = asking.value ?: return
        if (shown.busy) return
        asking.value = null
        asked = null
        askedUndo = null
        val offered = pending
        if (shown.kind == CorrectionKind.Undo && offered != null) {
            show(offer(offered.kind, canUndo = true, goalName = offered.goalName), offered.until)
        }
    }

    /** The same for an income. */
    suspend fun deleteIncome(command: DeleteIncome): IncomeCommandOutcome = scope.async {
        val outcome = incomes.delete(command)
        if (outcome is CommandOutcome.Committed) incomeDeleted(outcome.value.receipt)
        outcome
    }.await()

    fun incomeDeleted(receipt: OperationReceipt) {
        val until = incomes.undoOffer()?.takeIf { it.deletionId == receipt.operationId }?.until
        deleted(Kind.Income, receipt.operationId, receipt.generation, until)
    }

    suspend fun deleteIncome(
        incomeId: IncomeId,
        expectedVersion: Long,
        generation: Long
    ): DeleteResult = scope.async {
        val operation = operation()
        val command = DeleteIncome(operation, generation, incomeId, expectedVersion)
        val committed = when (val outcome = incomes.delete(command)) {
            is CommandOutcome.Committed -> true

            is CommandOutcome.OutcomeUnknown ->
                (incomes.find(operation) as? Outcome.Success)?.value != null

            is CommandOutcome.Rejected -> return@async when (outcome.reason) {
                Specific(IncomeRejection.IncomeMissing) -> DeleteResult.Gone
                is CommandRejection.VersionConflict -> DeleteResult.Changed
                is CommandRejection.StaleGeneration -> DeleteResult.DataChanged
                else -> DeleteResult.Failed
            }
        }
        if (!committed) return@async DeleteResult.Failed
        val until = incomes.undoOffer()?.takeIf { it.deletionId == operation }?.until
        deleted(Kind.Income, operation, generation, until)
    }.await()

    /** A deletion committed. With [until] it can be undone until then; without, it is only said. */
    private fun deleted(
        kind: Kind,
        operation: OperationId,
        generation: Long,
        until: Duration?,
        goalName: String = "",
        carryMonths: Int? = null
    ): DeleteResult {
        // An offer whose time is already over, as for one found late, is only said.
        if (until == null || until <= monotonic.elapsed()) {
            pending = null
            show(
                offer(kind, canUndo = false, goalName = goalName, carryMonths = carryMonths),
                monotonic.elapsed() + NOTICE
            )
        } else {
            pending =
                Pending(kind, operation, generation, until, operation(), goalName, carryMonths)
            show(offer(kind, canUndo = true, goalName = goalName, carryMonths = carryMonths), until)
        }
        return DeleteResult.Deleted
    }

    /** Puts back the deletion on offer. Asked again while that is being done, it does nothing. */
    fun undo() {
        val offered = pending ?: return
        val (canUndo, undoing) = offerShown() ?: return
        if (!canUndo || undoing) return
        // Its list is open: the answer is given there, not by a second Deshacer.
        if (asking.value?.kind == CorrectionKind.Undo) return
        // Once asked, the answer decides what is said next, not the time running out.
        ending?.cancel()
        shown.value =
            offer(offered.kind, canUndo = true, undoing = true, goalName = offered.goalName)
        scope.launch { settle(offered, restore(offered, confirmation = null)) }
    }

    /**
     * How a kind sends its restore, and asks about one whose outcome was not known. Only an
     * expense's restore can need a list confirmed first (ADR 0020 CP21); the others ignore it.
     */
    private class Restorer(
        val send: suspend (OperationId, Long, OperationId, CorrectionConfirmation?) -> Sent,
        val found: suspend (OperationId) -> Boolean
    )

    private fun restorer(kind: Kind) = when (kind) {
        Kind.Expense -> Restorer(
            { operation, generation, deletion, confirmation ->
                commands.restore(RestoreExpense(operation, generation, deletion, confirmation))
                    .sent(ExpenseRejection.UndoExpired, ExpenseRejection.UndoConflict)
            },
            { (commands.find(it) as? Outcome.Success)?.value != null }
        )

        Kind.Income -> Restorer(
            { operation, generation, deletion, _ ->
                incomes.restore(RestoreIncome(operation, generation, deletion))
                    .sent(IncomeRejection.UndoExpired, IncomeRejection.UndoConflict)
            },
            { (incomes.find(it) as? Outcome.Success)?.value != null }
        )

        Kind.GoalMovement -> Restorer(
            { operation, generation, deletion, _ ->
                goals.submit(RestoreGoalMovement(operation, generation, deletion))
                    .sent(GoalRejection.UndoExpired, GoalRejection.UndoConflict)
            },
            { (goals.find(it) as? Outcome.Success)?.value != null }
        )

        Kind.GoalCompletion -> Restorer(
            { operation, generation, deletion, _ ->
                goals.submit(UndoGoalCompletion(operation, generation, deletion))
                    .sent(GoalRejection.UndoExpired, GoalRejection.UndoConflict)
            },
            { (goals.find(it) as? Outcome.Success)?.value != null }
        )

        Kind.InvestmentMovement -> Restorer(
            { operation, generation, deletion, _ ->
                investments.submit(RestoreInvestmentMovement(operation, generation, deletion))
                    .sent(InvestmentRejection.UndoExpired, InvestmentRejection.UndoConflict)
            },
            { (investments.find(it) as? Outcome.Success)?.value != null }
        )
    }

    /**
     * One attempt to put back what [offered] deleted, whatever its kind, with the
     * [confirmation] that came with its list when one was shown. An outcome that is not
     * known is asked about with the same operation, never sent as a new one.
     */
    private suspend fun restore(offered: Pending, confirmation: CorrectionConfirmation?): Undone {
        val restorer = restorer(offered.kind)
        val sent = restorer.send(
            offered.undoOperation,
            offered.generation,
            offered.deletionId,
            confirmation
        )
        return when (sent) {
            Sent.Committed -> Undone.Restored

            Sent.Unknown ->
                if (restorer.found(offered.undoOperation)) Undone.Restored else Undone.Retry

            Sent.Expired -> Undone.TooLate

            Sent.Conflict -> Undone.Blocked

            Sent.Other -> Undone.Retry

            // Putting it back reaches a closed month of a chain (CP21): the list first.
            // The refused attempt wrote nothing; the confirmed one is a new operation.
            is Sent.Asks -> {
                offered.undoOperation = operation()
                askedUndo = sent.question.confirmation
                asking.value = CorrectionQuestion(
                    CorrectionKind.Undo,
                    list(sent.question.correction),
                    changed = confirmation != null
                )
                Undone.Asked
            }
        }.also {
            if (it != Undone.Asked) {
                askedUndo = null
                asking.value = null
            }
        }
    }

    /**
     * How a restore command ended, the same for every kind: [expired] and [conflict] are
     * that kind's own reasons for "too late" and "blocked"; a replaced history is blocked too.
     */
    private fun <F> CommandOutcome<*, F>.sent(expired: F, conflict: F): Sent = when (this) {
        is CommandOutcome.Committed -> Sent.Committed

        is CommandOutcome.OutcomeUnknown -> Sent.Unknown

        is CommandOutcome.Rejected -> when (val why = reason) {
            Specific(expired) -> Sent.Expired

            Specific(conflict), is CommandRejection.StaleGeneration -> Sent.Conflict

            is Specific -> (why.reason as? ExpenseRejection.CorrectionUnconfirmed)
                ?.let(Sent::Asks) ?: Sent.Other

            else -> Sent.Other
        }
    }

    private fun settle(offered: Pending, outcome: Undone) {
        // A later deletion took this one's place while it was being undone.
        if (pending !== offered) return
        val kind = offered.kind
        val retried = outcome == Undone.Retry && monotonic.elapsed() < offered.until
        if (kind == Kind.GoalCompletion && outcome != Undone.Asked && !retried) {
            answered = offered.deletionId
        }
        when {
            // The list is shown; the offer waits under it, as it was.
            outcome == Undone.Asked -> show(
                offer(
                    kind,
                    canUndo = true,
                    goalName = offered.goalName,
                    carryMonths = offered.carryMonths
                ),
                offered.until
            )

            outcome == Undone.Restored -> {
                // The expense is back: no form should close for its earlier deletion.
                if (kind == Kind.Expense) confirmedDeleted.value = null
                said(
                    when (kind) {
                        Kind.Expense -> CorrectionNotice.Restored

                        Kind.Income -> CorrectionNotice.IncomeRestored

                        Kind.GoalMovement -> CorrectionNotice.GoalMovementRestored

                        Kind.GoalCompletion -> CorrectionNotice.GoalCompletionUndone(
                            offered.goalName
                        )

                        Kind.InvestmentMovement -> CorrectionNotice.InvestmentMovementRestored
                    }
                )
            }

            outcome == Undone.Blocked -> said(
                when (kind) {
                    Kind.Expense -> CorrectionNotice.UndoBlocked
                    Kind.Income -> CorrectionNotice.IncomeUndoBlocked
                    Kind.GoalMovement -> CorrectionNotice.GoalMovementUndoBlocked
                    Kind.GoalCompletion -> CorrectionNotice.GoalCompletionUndoTooLate
                    Kind.InvestmentMovement -> CorrectionNotice.InvestmentMovementUndoBlocked
                }
            )

            // Nothing was restored and nothing forbids it: it can be asked again while
            // the time lasts.
            retried ->
                show(
                    offer(kind, canUndo = true, failed = true, goalName = offered.goalName),
                    offered.until
                )

            else -> said(
                when (kind) {
                    Kind.Expense -> CorrectionNotice.UndoTooLate
                    Kind.Income -> CorrectionNotice.IncomeUndoTooLate
                    Kind.GoalMovement -> CorrectionNotice.GoalMovementUndoTooLate
                    Kind.GoalCompletion -> CorrectionNotice.GoalCompletionUndoTooLate
                    Kind.InvestmentMovement -> CorrectionNotice.InvestmentMovementUndoTooLate
                }
            )
        }
    }

    /**
     * Deletes a goal's movement and offers to undo it, in the movement's own words. It runs
     * to its end even if the caller is cancelled meanwhile.
     */
    suspend fun deleteGoalMovement(command: DeleteGoalMovement): GoalCommandOutcome = scope.async {
        val outcome = goals.submit(command)
        if (outcome is CommandOutcome.Committed) goalMovementDeleted(outcome.value.receipt)
        outcome
    }.await()

    /** A movement's deletion committed, as found again after its outcome was not known. */
    fun goalMovementDeleted(receipt: OperationReceipt) {
        val until = goals.movementUndoOffer()
            ?.takeIf { it.operationId == receipt.operationId }?.until
        deleted(Kind.GoalMovement, receipt.operationId, receipt.generation, until)
    }

    /**
     * Deletes an investment's movement and offers to undo it, as for a goal's movement. It
     * runs to its end even if the caller is cancelled meanwhile.
     */
    suspend fun deleteInvestmentMovement(
        command: DeleteInvestmentMovement
    ): InvestmentCommandOutcome = scope.async {
        val outcome = investments.submit(command)
        if (outcome is CommandOutcome.Committed) {
            investmentMovementDeleted(outcome.value.receipt)
        }
        outcome
    }.await()

    /** An investment movement's deletion committed, as found again after its outcome was not known. */
    fun investmentMovementDeleted(receipt: OperationReceipt) {
        val until = investments.movementUndoOffer()
            ?.takeIf { it.operationId == receipt.operationId }?.until
        deleted(Kind.InvestmentMovement, receipt.operationId, receipt.generation, until)
    }

    /** A screen reports that a correction of an investment's movement was saved. */
    fun investmentMovementUpdated() = said(CorrectionNotice.InvestmentMovementUpdated)

    /** A screen reports a new investment movement its receipt confirmed. */
    fun investmentMovementRecorded(notice: CorrectionNotice.InvestmentMovementRecorded) =
        said(notice)

    /** A screen reports a receipt of several investment movements its receipt confirmed. */
    fun investmentMovementsRecorded(count: Int, investmentName: String?) =
        said(CorrectionNotice.InvestmentMovementsRecorded(count, investmentName))

    /**
     * Marks a goal lograda and offers Deshacer, in its name, for [shownFor] from the save,
     * never longer than storage keeps it possible, the time [command] asked. It runs to its
     * end even if the caller is cancelled meanwhile.
     */
    suspend fun completeGoal(
        command: CompleteGoal,
        goalName: String,
        shownFor: Duration = command.undoFor
    ): GoalCommandOutcome = scope.async {
        val outcome = goals.submit(command)
        if (outcome is CommandOutcome.Committed) {
            goalCompleted(outcome.value.receipt, goalName, shownFor, command.undoFor)
        }
        outcome
    }.await()

    /**
     * A completion committed, as found again after its outcome was not known: Deshacer for
     * [shownFor] counted from the save, not from the find. Storage keeps it for [askedFor]
     * from the save, which places the save on the clock.
     */
    fun goalCompleted(
        receipt: OperationReceipt,
        goalName: String,
        shownFor: Duration,
        askedFor: Duration
    ) {
        val until = completionOffer(receipt)?.let { it - askedFor + shownFor }
            ?.takeIf { it > monotonic.elapsed() }
        // Only said, it never takes the place of another deletion still on offer.
        if (until == null && pending.let { it != null && it.deletionId != receipt.operationId }) {
            return
        }
        deleted(Kind.GoalCompletion, receipt.operationId, receipt.generation, until, goalName)
    }

    /**
     * The big moment after the completion [receipt] closed: Deshacer is offered again, in
     * [goalName], for [shownFor] from now (SELLO-092), within the time storage keeps it
     * possible; past that, the completion is only said. Nothing changes while another
     * deletion is on offer or once an undo of this completion was answered.
     */
    fun goalCompletionMomentClosed(
        receipt: OperationReceipt,
        goalName: String,
        shownFor: Duration
    ) {
        val offered = pending
        if (offered != null && offered.deletionId != receipt.operationId) return
        if (receipt.operationId == answered) return
        // An undo already asked is answered by its own outcome, not offered again.
        if (offered != null && offerShown()?.second == true) return
        val until = completionOffer(receipt)?.let { minOf(it, monotonic.elapsed() + shownFor) }
        deleted(Kind.GoalCompletion, receipt.operationId, receipt.generation, until, goalName)
    }

    /** Until when storage keeps [receipt] for Deshacer; null when it no longer does. */
    private fun completionOffer(receipt: OperationReceipt): Duration? = goals.completionUndoOffer()
        ?.takeIf { it.operationId == receipt.operationId && monotonic.elapsed() < it.until }
        ?.until

    /** A screen reports that a correction of a goal's movement was saved. */
    fun goalMovementUpdated() = said(CorrectionNotice.GoalMovementUpdated)

    /** A screen reports a new aporte or retiro its receipt confirmed, read back from storage. */
    fun goalMovementRecorded(notice: CorrectionNotice.GoalMovementRecorded) = said(notice)

    /**
     * A screen reports a receipt of several aportes and retiros its receipt confirmed. Each
     * goal it celebrates gets the notice's time in turn.
     */
    fun goalMovementsRecorded(notice: CorrectionNotice.GoalMovementsRecorded) = said(notice)

    /**
     * Whether [celebration]'s stamps come down now: only the first time a screen asks, so
     * reopening the goal or redrawing it while the notice is said does not land them again.
     * Each save makes its own celebration, so a later save lands again.
     */
    fun lands(celebration: GoalCelebration): Boolean {
        if (landed === celebration) return false
        landed = celebration
        return true
    }

    /** A screen reports a new target whose receipt earned or relit a stamp. */
    fun goalTargetChanged(celebration: GoalCelebration) =
        said(CorrectionNotice.GoalTargetChanged(celebration))

    /** A screen reports that an edit of an expense was saved. */
    fun updated() = said(CorrectionNotice.Updated)

    /** A screen reports that an edit of an income was saved. */
    fun incomeUpdated() = said(CorrectionNotice.IncomeUpdated)

    /**
     * A screen reports a save that storage confirmed. Like every statement it takes the
     * place of whatever is shown, an offer to undo included.
     */
    fun confirmed(notice: CorrectionNotice.Confirmed) = said(notice)

    /** A screen reports a new expense its receipt confirmed, read back from storage. */
    fun recorded(notice: CorrectionNotice.ExpenseRecorded) = said(notice)

    /** A screen reports a receipt of several expenses storage confirmed. */
    fun expensesRecorded(notice: CorrectionNotice.ExpensesRecorded) = said(notice)

    /** A screen reports a new income its receipt confirmed, read back from storage. */
    fun incomeRecorded(notice: CorrectionNotice.IncomeRecorded) = said(notice)

    /** A screen reports a receipt of several incomes storage confirmed. */
    fun incomesRecorded(notice: CorrectionNotice.IncomesRecorded) = said(notice)

    /** A screen reports a new schedule its receipt confirmed, read back from storage. */
    fun scheduleSaved(notice: CorrectionNotice.ScheduleSaved) {
        if (!notice.income) done.update { it + Nudge.FixedPayment }
        said(notice)
    }

    /** A screen reports a schedule change its receipt confirmed. */
    fun scheduleChanged(notice: CorrectionNotice.ScheduleChanged) = said(notice)

    /** A screen reports a category change its receipt confirmed. */
    fun categoryChanged(notice: CorrectionNotice) {
        require(
            notice !is CorrectionNotice.Deleted && notice !is CorrectionNotice.IncomeDeleted &&
                notice !is CorrectionNotice.GoalMovementDeleted &&
                notice !is CorrectionNotice.InvestmentMovementDeleted
        ) {
            "An offer to undo is not a statement"
        }
        if ((notice as? CorrectionNotice.CategorySaved)?.carry?.on == true) {
            done.update { it + Nudge.Carry }
        }
        said(notice)
    }

    fun dismiss() {
        if (offerShown()?.second == true) return
        ending?.cancel()
        pending = null
        // An unresolved deletion is not dismissed: it is still to be asked about.
        freed()
    }

    /**
     * Call when the app is back in view. Waiting does not advance while a device
     * sleeps but the time to undo does, so the offer is checked against the clock again.
     */
    fun refresh() {
        val offered = pending ?: return
        val (canUndo, undoing) = offerShown() ?: return
        if (canUndo && !undoing) shown.value?.let { show(it, offered.until) }
    }

    /** Something that is over: said for a moment, with nothing left to undo. */
    private fun said(notice: CorrectionNotice) {
        pending = null
        show(notice, monotonic.elapsed() + NOTICE)
    }

    /** Shows [notice] until the clock reads [until]. */
    private fun show(notice: CorrectionNotice, until: Duration) {
        ending?.cancel()
        if (monotonic.elapsed() >= until) return over()
        shown.value = notice
        ending = scope.launch {
            var left = until - monotonic.elapsed()
            while (left > Duration.ZERO) {
                delay(left)
                left = until - monotonic.elapsed()
            }
            over()
        }
    }

    /**
     * What is shown is over. An undo whose list is still open, and not yet confirmed, can no
     * longer be done: the list goes with it and that is said (AC4).
     */
    private fun over() {
        val list = asking.value
        if (list?.kind == CorrectionKind.Undo && !list.busy) return undoListExpired()
        val receipt = shown.value as? CorrectionNotice.GoalMovementsRecorded
        if (receipt != null && receipt.turn + 1 < receipt.celebrations.size) {
            return said(receipt.copy(turn = receipt.turn + 1))
        }
        pending = null
        freed()
    }

    /**
     * The bar is free: an unresolved deletion comes back, still to be asked about, or what a
     * check answered meanwhile is said; otherwise nothing is shown.
     */
    private fun freed() {
        val held = unresolved
        val answer = owed
        when {
            held != null -> shown.value = CorrectionNotice.DeleteUnknown(held.checking)

            answer != null -> {
                owed = null
                said(answer)
            }

            else -> shown.value = null
        }
    }

    private fun operation(): OperationId = when (val id = OperationId.of(ids.next())) {
        is Outcome.Success -> id.value
        is Outcome.Failure -> error("The identifier source returned a malformed identifier")
    }

    private companion object {
        /** How long something that needs no answer stays on screen. */
        val NOTICE = 4.seconds
    }
}

/** What a confirmed command did to a schedule. */
enum class ScheduleChange { Changed, Paused, Resumed, Deleted }

/** Carry of [category] turned [on] from [month], or off, by a saved category change. */
data class SavedCarry(val category: String, val on: Boolean, val month: YearMonth)
