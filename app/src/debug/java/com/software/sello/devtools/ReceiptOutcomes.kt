package com.software.sello.devtools

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.ExpenseBatchCommands
import com.software.sello.domain.port.ExpenseBatchOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Sandbox only: lets the owner reach an unknown outcome on an Anotar varios receipt by hand.
 * Once [arm]ed, the next receipt is really saved, but the sheet is told its outcome is
 * unknown and the first question about it fails too, as if storage stopped answering. The
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
        val unknown = outcomes?.take() == true
        val outcome = fence.write { commands.create(command) }
        if (outcome is CommandOutcome.Committed && !outcome.replayed) saved()
        if (unknown && outcome is CommandOutcome.Committed) {
            outcomes?.hide(command.operationId)
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
