package com.software.sello.feature.goals

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.Corrections
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The goal to mark lograda, as it was read, with what its plate will say. */
@Immutable
data class CompletionTarget(
    val goalId: String,
    val version: Long,
    val generation: Long,
    val name: String,
    val iconKey: String,
    val saved: MoneyTextValue,
    val contributions: Int
)

/** The goal just marked lograda, for the big moment's plate. */
@Immutable
data class LogradaPlate(
    val name: String,
    val iconKey: String,
    val saved: MoneyTextValue,
    val completedOn: LocalDate,
    val contributions: Int,
    /** For Ver mi meta. */
    val goalId: String = ""
)

sealed interface CompletionState {
    data object Idle : CompletionState

    data object Busy : CompletionState

    data class Celebrating(val plate: LogradaPlate) : CompletionState

    data class Failed(val error: GoalDetailError) : CompletionState
}

/**
 * Marcar como lograda, from whichever screen offers it. The completion's identifier and
 * the goal's name are saved before it leaves, so an outcome that is not known is asked
 * about with that identifier, after a restart too, and never sent twice. The big moment
 * follows only a confirmed completion made here; one found again is said, not celebrated.
 * Deshacer is offered by [corrections] for about ten seconds after the moment, longer
 * while a screen reader is on.
 */
class GoalCompleter(
    private val saved: SavedStateHandle,
    private val commands: GoalCommands,
    private val corrections: Corrections,
    private val ids: RecordIdSource,
    private val clock: FinancialClock,
    private val screenReader: ScreenReader,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow<CompletionState>(CompletionState.Idle)
    val state: StateFlow<CompletionState> = mutable

    init {
        if (saved.get<String>(OPERATION) != null) scope.launch { recover() }
    }

    fun complete(target: CompletionTarget) {
        // Only from rest or after a failure: never while sending or during the moment.
        val now = mutable.value
        if (now != CompletionState.Idle && now !is CompletionState.Failed) return
        if (saved.get<String>(OPERATION) != null) return
        val goalId = (GoalId.of(target.goalId) as? Outcome.Success)?.value ?: return
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[NAME] = target.name
        mutable.value = CompletionState.Busy
        val undoFor = (if (screenReader.active()) READING_UNDO else UNDO) + MOMENT
        scope.launch {
            val command =
                CompleteGoal(operation, target.generation, goalId, target.version, undoFor)
            when (val outcome = corrections.completeGoal(command, target.name)) {
                is CommandOutcome.Committed -> {
                    saved[OPERATION] = null
                    mutable.value = CompletionState.Celebrating(
                        LogradaPlate(
                            target.name,
                            target.iconKey,
                            target.saved,
                            clock.today.value.date,
                            target.contributions,
                            target.goalId
                        )
                    )
                }

                is CommandOutcome.OutcomeUnknown -> recover()

                // Certain: nothing was written, so this attempt is over.
                is CommandOutcome.Rejected -> {
                    saved[OPERATION] = null
                    mutable.value = CompletionState.Failed(GoalDetailError.NotSaved)
                }
            }
        }
    }

    /** The big moment is closed: Listo or Ver mi meta. */
    fun done() {
        if (mutable.value is CompletionState.Celebrating) mutable.value = CompletionState.Idle
    }

    fun retry() {
        when {
            saved.get<String>(OPERATION) != null -> scope.launch { recover() }
            mutable.value is CompletionState.Failed -> mutable.value = CompletionState.Idle
        }
    }

    private suspend fun recover() {
        val operation = (saved.get<String>(OPERATION)?.let(OperationId::of) as? Outcome.Success)
            ?.value ?: return
        mutable.value = CompletionState.Busy
        when (val found = commands.find(operation)) {
            is Outcome.Failure -> mutable.value = CompletionState.Failed(GoalDetailError.Unknown)

            is Outcome.Success -> {
                saved[OPERATION] = null
                val committed = found.value
                if (committed != null) {
                    corrections.goalCompleted(committed.receipt, saved[NAME] ?: "")
                    mutable.value = CompletionState.Idle
                } else {
                    mutable.value = CompletionState.Failed(GoalDetailError.NotSaved)
                }
            }
        }
    }

    private companion object {
        /** About ten seconds of Deshacer after the moment, longer while a screen reader is on. */
        val UNDO = 10.seconds
        val READING_UNDO = 30.seconds

        /** The big moment plays before Deshacer is seen. */
        val MOMENT = 4.seconds

        const val OPERATION = "goal.completion.operation"
        const val NAME = "goal.completion.name"
    }
}
