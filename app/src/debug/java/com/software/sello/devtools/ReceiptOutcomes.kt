package com.software.sello.devtools

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.CreateIncomes
import com.software.sello.domain.port.ExpenseBatchCommands
import com.software.sello.domain.port.ExpenseBatchOutcome
import com.software.sello.domain.port.IncomeBatchCommands
import com.software.sello.domain.port.IncomeBatchOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Sandbox only: lets the owner reach an unknown outcome on an Anotar varios receipt by hand.
 * Once [arm]ed, the next receipt that is really saved is reported to the sheet as unknown,
 * and the first question about it fails too, as if storage stopped answering. The
 * next **Comprobar si se guardó** then finds the receipt with the same operation.
 */
class ReceiptOutcomes {
    private val state = MutableStateFlow(false)
    private val hidden = MutableStateFlow<OperationId?>(null)

    /** The next receipt will come back unknown. */
    val armed: StateFlow<Boolean> = state.asStateFlow()

    fun arm() {
        state.value = true
    }

    internal fun take(): Boolean = state.getAndUpdate { false }

    internal fun hide(operation: OperationId) {
        hidden.value = operation
    }

    /** The first lookup of a hidden operation fails; later ones answer. */
    internal fun unhide(operation: OperationId): Boolean {
        val was = hidden.getAndUpdate { if (it == operation) null else it }
        return was == operation
    }
}

/** Receipts of expenses through the fence, with [outcomes] able to hide one's outcome. */
class FencedExpenseBatchCommands(
    private val commands: ExpenseBatchCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit,
    private val outcomes: ReceiptOutcomes?
) : ExpenseBatchCommands {
    override suspend fun create(command: CreateExpenses): ExpenseBatchOutcome {
        val outcome = fence.write { commands.create(command) }
        if (outcome is CommandOutcome.Committed && !outcome.replayed) saved()
        // Only a saved receipt uses the switch up: a refused one, such as one asking to
        // confirm later months first, leaves it for the receipt that is then saved.
        if (outcome is CommandOutcome.Committed && outcomes?.take() == true) {
            outcomes.hide(command.operationId)
            return CommandOutcome.OutcomeUnknown(command.operationId)
        }
        return outcome
    }

    override suspend fun find(operationId: OperationId) =
        if (outcomes?.unhide(operationId) == true) {
            Outcome.Failure(StorageFailure.Unavailable("sandbox: outcome held back once"))
        } else {
            commands.find(operationId)
        }
}

/** Receipts of incomes through the fence, as [FencedExpenseBatchCommands] has expenses. */
class FencedIncomeBatchCommands(
    private val commands: IncomeBatchCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit,
    private val outcomes: ReceiptOutcomes?
) : IncomeBatchCommands {
    override suspend fun create(command: CreateIncomes): IncomeBatchOutcome {
        val outcome = fence.write { commands.create(command) }
        if (outcome is CommandOutcome.Committed && !outcome.replayed) saved()
        if (outcome is CommandOutcome.Committed && outcomes?.take() == true) {
            outcomes.hide(command.operationId)
            return CommandOutcome.OutcomeUnknown(command.operationId)
        }
        return outcome
    }

    override suspend fun find(operationId: OperationId) =
        if (outcomes?.unhide(operationId) == true) {
            Outcome.Failure(StorageFailure.Unavailable("sandbox: outcome held back once"))
        } else {
            commands.find(operationId)
        }
}
