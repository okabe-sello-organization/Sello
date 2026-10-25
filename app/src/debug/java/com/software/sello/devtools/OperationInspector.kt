package com.software.sello.devtools

import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.IncomeCommands

/**
 * Reads what storage holds for a logged operation through the same receipt lookups
 * the screens use to recover. It only reads: a receipt is never written or changed
 * here, so a simulated fault can never make a committed write look failed.
 */
class OperationInspector(
    private val expenses: ExpenseCommands,
    private val incomes: IncomeCommands,
    private val categories: CategoryCommands,
    private val goals: GoalCommands
) {
    suspend fun stored(entry: LoggedOperation): StoredOutcome {
        val found: Outcome<OperationReceipt?, *> = when {
            entry.kind.startsWith("Gasto") -> expenses.find(entry.operation).map { it?.receipt }
            entry.kind.startsWith("Ingreso") -> incomes.find(entry.operation).map { it?.receipt }
            entry.kind.startsWith("Meta") -> goals.find(entry.operation).map { it?.receipt }
            else -> categories.find(entry.operation).map { it?.receipt }
        }
        return when (found) {
            is Outcome.Failure -> StoredOutcome.Unreadable

            is Outcome.Success ->
                found.value?.let { StoredOutcome.Saved(it.revision) } ?: StoredOutcome.Nothing
        }
    }

    private inline fun <T, R, E> Outcome<T, E>.map(change: (T) -> R): Outcome<R, E> = when (this) {
        is Outcome.Success -> Outcome.Success(change(value))
        is Outcome.Failure -> this
    }
}

/**
 * The diagnostic a developer chooses to share: the sandbox session, the faults armed
 * and each logged operation with what storage holds. It carries identifiers, kinds,
 * outcomes, revisions, dates and counts only; no category name, note or amount.
 */
fun diagnosticText(
    session: SandboxSession,
    faults: Map<Fault, Int>,
    operations: List<Pair<LoggedOperation, StoredOutcome>>
): String = buildString {
    appendLine("Sello sandbox diagnostic · synthetic data only")
    appendLine(
        "session: date=${session.date} zone=${session.zone?.id ?: "device"} " +
            "scenario=${session.scenario ?: "none"} nextStep=${session.nextStep} changed=${session.changed}"
    )
    appendLine(
        "faults armed: " +
            faults.entries.joinToString { "${it.key.name}×${it.value}" }.ifEmpty { "none" }
    )
    appendLine("operations (${operations.size}):")
    operations.forEach { (entry, stored) ->
        val storage = when (stored) {
            is StoredOutcome.Saved -> "saved rev ${stored.revision}"
            StoredOutcome.Nothing -> "nothing written"
            StoredOutcome.Unreadable -> "lookup failed"
        }
        appendLine(
            "- ${entry.operation.value} · ${entry.kind} · told: ${entry.told}" +
                (entry.fault?.let { " · simulated: ${it.name}" } ?: "") + " · storage: $storage"
        )
    }
}
