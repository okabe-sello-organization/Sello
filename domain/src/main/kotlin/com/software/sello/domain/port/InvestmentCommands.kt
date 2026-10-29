package com.software.sello.domain.port

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import java.time.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * A change to investments or the USD rate (docs/testing/investment-examples.md). Every
 * command names its attempt with [operationId] and the history it was prepared against
 * with [generation], as for expenses. No investment command reads or writes an expense,
 * an income, a category, a limit, a goal or a schedule (rule 15).
 */
sealed interface InvestmentCommand {
    val operationId: OperationId
    val generation: Long
}

/** A command about one existing investment, as the caller read it at [expectedVersion]. */
sealed interface InvestmentEdit : InvestmentCommand {
    val investmentId: InvestmentId
    val expectedVersion: Long
}

/** A rate entered together with another change: pesos per dollar on [date] (rule 11). */
data class DatedUsdRate(val rate: UsdRate, val date: LocalDate)

/**
 * A new investment with its first aporte and its value, both dated [date] (rule 1).
 * [firstValue] is the aporte itself for Lo mismo que aporté. A USD investment needs a
 * rate: [newRate] is entered in the same transaction, and is required when none exists
 * yet (rule 12). Amounts are in [currency].
 */
data class CreateInvestment(
    override val operationId: OperationId,
    override val generation: Long,
    val name: InvestmentName,
    val description: InvestmentDescription?,
    val icon: IconKey,
    val currency: Currency,
    val firstContribution: InvestmentAmount,
    val firstValue: InvestmentAmount,
    val date: LocalDate,
    val newRate: DatedUsdRate?
) : InvestmentCommand

/** Name, description and icon. The currency never changes (rule 1). */
data class EditInvestment(
    override val operationId: OperationId,
    override val generation: Long,
    override val investmentId: InvestmentId,
    override val expectedVersion: Long,
    val name: InvestmentName,
    val description: InvestmentDescription?,
    val icon: IconKey
) : InvestmentEdit

/** An aporte, retiro, dividend or recorded value, dated [date] (rules 2–5). */
data class RecordInvestmentMovement(
    override val operationId: OperationId,
    override val generation: Long,
    override val investmentId: InvestmentId,
    override val expectedVersion: Long,
    val kind: InvestmentMovementKind,
    val amount: InvestmentAmount,
    val date: LocalDate,
    val note: Note?
) : InvestmentEdit

/** A correction in place (rule 9): the movement keeps its kind and recording position. */
data class EditInvestmentMovement(
    override val operationId: OperationId,
    override val generation: Long,
    override val investmentId: InvestmentId,
    override val expectedVersion: Long,
    val movementId: InvestmentMovementId,
    val movementVersion: Long,
    val amount: InvestmentAmount,
    val date: LocalDate,
    val note: Note?
) : InvestmentEdit

/** Removes a movement, with the shared six-second undo (rule 9). */
data class DeleteInvestmentMovement(
    override val operationId: OperationId,
    override val generation: Long,
    override val investmentId: InvestmentId,
    override val expectedVersion: Long,
    val movementId: InvestmentMovementId,
    val movementVersion: Long
) : InvestmentEdit

/** Puts back the movement the deletion [deletionId] removed; a new operation of its own. */
data class RestoreInvestmentMovement(
    override val operationId: OperationId,
    override val generation: Long,
    val deletionId: OperationId
) : InvestmentCommand

/** Removes the investment and every movement, with no undo (rule 9). */
data class DeleteInvestment(
    override val operationId: OperationId,
    override val generation: Long,
    override val investmentId: InvestmentId,
    override val expectedVersion: Long
) : InvestmentEdit

/** Records a new dated rate; earlier ones stay in the history (rule 11). */
data class SetUsdRate(
    override val operationId: OperationId,
    override val generation: Long,
    val rate: UsdRate,
    val date: LocalDate
) : InvestmentCommand

/** Why an investment command wrote nothing, beyond the reasons every [CommandRejection] has. */
sealed interface InvestmentRejection {
    data object InvestmentMissing : InvestmentRejection

    data object MovementMissing : InvestmentRejection

    /** The change asks for exactly what is recorded. */
    data object Unchanged : InvestmentRejection

    data class FutureDate(val today: LocalDate) : InvestmentRejection

    /** A date the history cannot hold (before year 0000). */
    data object InvalidDate : InvestmentRejection

    /** An aporte, retiro or dividend of 0: only a recorded value may be 0 (rule 2). */
    data object InvalidAmount : InvestmentRejection

    /** An amount is not in the investment's currency. */
    data object CurrencyMismatch : InvestmentRejection

    /** A USD investment with no rate yet and none given (rule 12, IN33). */
    data object RateRequired : InvestmentRejection

    /** Dated before the earliest aporte, [firstContribution] (rule 3, IN25). */
    data class BeforeFirstContribution(val firstContribution: LocalDate) : InvestmentRejection

    /**
     * Replayed, [withdrawal] would take more than the [available] value on its date: a new
     * retiro above the value, or a correction that would leave less (rule 8, IN10, IN19).
     */
    data class AboveValue(val withdrawal: InvestmentMovementId, val available: Money) :
        InvestmentRejection

    /** Replayed, [withdrawal] would come with no value recorded (rule 7, IN18). */
    data class NoValue(val withdrawal: InvestmentMovementId) : InvestmentRejection

    /** The only aporte cannot be deleted; delete the investment instead (rule 6, IN22). */
    data object LastContribution : InvestmentRejection

    /** A total would not fit an exact amount. */
    data object Unrepresentable : InvestmentRejection

    /** The time to undo is over, or this is not the change on offer. */
    data object UndoExpired : InvestmentRejection

    /** Undoing would contradict what is recorded now. */
    data object UndoConflict : InvestmentRejection
}

/** A committed change and its receipt; [investmentId] is null for [SetUsdRate]. */
data class CommittedInvestment(val receipt: OperationReceipt, val investmentId: InvestmentId?)

typealias InvestmentCommandOutcome = CommandOutcome<CommittedInvestment, InvestmentRejection>

/** The deleted movement that can still be put back. */
data class InvestmentUndoOffer(
    val operationId: OperationId,
    val investmentId: InvestmentId,
    val until: Duration
)

/**
 * Changes to investments and the rate, with the guarantees of [ExpenseCommands]: one
 * operation writes once, with its revision and receipt, in one transaction, and the same
 * operation and input again returns the original result.
 */
interface InvestmentCommands {
    suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome

    /** The deleted movement that can be put back now, or null; shared with the other kinds. */
    fun movementUndoOffer(): InvestmentUndoOffer?

    suspend fun find(operationId: OperationId): Outcome<CommittedInvestment?, StorageFailure>
}

/** One investment as the screens show it. */
data class InvestmentSummary(val history: InvestmentHistory, val figures: InvestmentFigures)

/**
 * Every investment and the rates, read at one [revision] of one [generation]. [overview]
 * is the policy's overview of [investments] at the current rate, or why it cannot be
 * made; an investment whose history cannot be worked out fails the whole read instead.
 */
data class InvestmentBoard(
    val generation: Long,
    val revision: Long,
    val investments: List<InvestmentSummary>,
    val rates: UsdRateBook,
    val overview: Outcome<PortfolioOverview, PortfolioError>
)

/** The board as [InvestmentReads.observeBoard] sees it. A failed read keeps the last good board. */
sealed interface InvestmentBoardState {
    data object Loading : InvestmentBoardState

    data class Ready(val board: InvestmentBoard) : InvestmentBoardState

    data class Failed(val failure: StorageFailure, val lastGood: InvestmentBoard?) :
        InvestmentBoardState
}

interface InvestmentReads {
    /** One damaged investment or rate fails the read: it is never left out. */
    suspend fun board(): Outcome<InvestmentBoard, StorageFailure>

    /**
     * [board], read again after every committed change: Loading first, then each read.
     * A failure is a state with the last good board, never an empty one.
     */
    fun observeBoard(): Flow<InvestmentBoardState>

    suspend fun investment(id: InvestmentId): Outcome<InvestmentSummary?, StorageFailure>
}
