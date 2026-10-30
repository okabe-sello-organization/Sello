package com.software.sello.presentation.corrections

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.InvestmentMovementKind
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

    /** A deletion confirmed after its list: whether it was saved could not be found out. */
    data object DeleteUnknown : CorrectionNotice

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
     * changed, when it was one: that month's figures now follow the new limit.
     */
    data class CategorySaved(val limitOf: YearMonth?) : CorrectionNotice

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
     */
    data class GoalMovementsRecorded(
        val count: Int,
        val net: MoneyTextValue?,
        val celebration: GoalCelebration? = null
    ) : CorrectionNotice

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
        sendDelete(expenseId, expectedVersion, generation, confirmation = null, months = null)
    }.await()

    /**
     * Sends the deletion, with the [confirmation] that came with its list when one was
     * shown. A list that is needed, or needed again, is asked and nothing is deleted.
     */
    private suspend fun sendDelete(
        expenseId: ExpenseId,
        expectedVersion: Long,
        generation: Long,
        confirmation: CorrectionConfirmation?,
        months: Int?
    ): DeleteResult {
        val operation = operation()
        val command = DeleteExpense(operation, generation, expenseId, expectedVersion, confirmation)
        val committed = when (val outcome = commands.delete(command)) {
            is CommandOutcome.Committed -> true

            // Asked, never sent again: only a receipt says it was deleted, and a question
            // that cannot be answered says nothing either way.
            is CommandOutcome.OutcomeUnknown -> when (val found = commands.find(operation)) {
                is Outcome.Success -> found.value != null
                is Outcome.Failure -> return DeleteResult.Unknown
            }

            is CommandOutcome.Rejected -> return when (val reason = outcome.reason) {
                Specific(ExpenseRejection.ExpenseMissing) -> DeleteResult.Gone

                is CommandRejection.VersionConflict -> DeleteResult.Changed

                is CommandRejection.StaleGeneration -> DeleteResult.DataChanged

                is Specific -> when (val asks = reason.reason) {
                    is ExpenseRejection.CorrectionUnconfirmed -> {
                        val list = list(asks.correction)
                        asked = Asked(
                            expenseId,
                            expectedVersion,
                            generation,
                            asks.confirmation,
                            asks.correction.months.size
                        )
                        asking.value = CorrectionQuestion(
                            CorrectionKind.Delete,
                            list,
                            changed = confirmation != null
                        )
                        DeleteResult.AwaitsConfirmation
                    }

                    else -> DeleteResult.Failed
                }

                else -> DeleteResult.Failed
            }
        }
        if (!committed) return DeleteResult.Failed
        asked = null
        asking.value = null
        if (confirmation != null) {
            confirmedDeleted.value = ConfirmedDeletion(expenseId, ++confirmedCount)
        }
        val until = commands.undoOffer()?.takeIf { it.deletionId == operation }?.until
        return deleted(Kind.Expense, operation, generation, until, carryMonths = months)
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
                scope.launch {
                    val result = sendDelete(
                        wanted.expenseId,
                        wanted.expectedVersion,
                        wanted.generation,
                        wanted.confirmation,
                        wanted.months
                    )
                    if (result != DeleteResult.AwaitsConfirmation &&
                        result != DeleteResult.Deleted
                    ) {
                        // The list's screen may be gone; what happened is said here.
                        asking.value = null
                        asked = null
                        said(
                            if (result == DeleteResult.Unknown) {
                                CorrectionNotice.DeleteUnknown
                            } else {
                                CorrectionNotice.NotDeleted
                            }
                        )
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
        if (until == null) {
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
            outcome == Undone.Retry && monotonic.elapsed() < offered.until ->
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
     * Marks a goal lograda and offers Deshacer, in its name, for the time [command] asked.
     * It runs to its end even if the caller is cancelled meanwhile.
     */
    suspend fun completeGoal(command: CompleteGoal, goalName: String): GoalCommandOutcome =
        scope.async {
            val outcome = goals.submit(command)
            if (outcome is CommandOutcome.Committed) {
                goalCompleted(outcome.value.receipt, goalName)
            }
            outcome
        }.await()

    /** A completion committed, as found again after its outcome was not known. */
    fun goalCompleted(receipt: OperationReceipt, goalName: String) {
        val until = goals.completionUndoOffer()
            ?.takeIf { it.operationId == receipt.operationId }?.until
        deleted(Kind.GoalCompletion, receipt.operationId, receipt.generation, until, goalName)
    }

    /** A screen reports that a correction of a goal's movement was saved. */
    fun goalMovementUpdated() = said(CorrectionNotice.GoalMovementUpdated)

    /** A screen reports a new aporte or retiro its receipt confirmed, read back from storage. */
    fun goalMovementRecorded(notice: CorrectionNotice.GoalMovementRecorded) = said(notice)

    /** A screen reports a receipt of several aportes and retiros its receipt confirmed. */
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

    /** A screen reports a new income its receipt confirmed, read back from storage. */
    fun incomeRecorded(notice: CorrectionNotice.IncomeRecorded) = said(notice)

    /** A screen reports a new schedule its receipt confirmed, read back from storage. */
    fun scheduleSaved(notice: CorrectionNotice.ScheduleSaved) = said(notice)

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
        said(notice)
    }

    fun dismiss() {
        if (offerShown()?.second == true) return
        ending?.cancel()
        pending = null
        shown.value = null
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
        pending = null
        shown.value = null
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
