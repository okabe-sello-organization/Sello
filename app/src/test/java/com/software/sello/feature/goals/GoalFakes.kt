package com.software.sello.feature.goals

import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.GoalUndoOffer
import com.software.sello.domain.port.ReadState
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

/** The board a test sets by hand; [started] counts observations. */
class HandGoalReads : GoalReads {
    val board = MutableStateFlow<GoalBoardState>(ReadState.Loading)
    var started = 0

    /** What a one-off read returns; [reads] counts them. */
    var read: Outcome<GoalBoard, StorageFailure> =
        Outcome.Failure(StorageFailure.Unavailable("not used"))
    var reads = 0

    override suspend fun board(): Outcome<GoalBoard, StorageFailure> {
        reads++
        return read
    }

    override fun observeBoard(): Flow<GoalBoardState> = board.onStart { started++ }

    override suspend fun goal(id: GoalId): Outcome<GoalSummary?, StorageFailure> =
        Outcome.Failure(StorageFailure.Unavailable("not used"))
}

/**
 * Commands a test answers by hand: [next] once, or committed. With [pause] a command
 * waits for [release]. [found] answers [find], and [asked] records what was asked.
 */
class HandGoalCommands : GoalCommands {
    val sent = mutableListOf<GoalCommand>()
    val asked = mutableListOf<OperationId>()
    var next: GoalCommandOutcome? = null

    /** Answers in order, before [next]. */
    val queue = ArrayDeque<GoalCommandOutcome>()
    var found: Outcome<CommittedGoal?, StorageFailure> = Outcome.Success(null)
    var pause = false
    private var waiting: CompletableDeferred<GoalCommandOutcome>? = null

    fun release(outcome: GoalCommandOutcome) {
        waiting?.complete(outcome)
    }

    override suspend fun submit(command: GoalCommand): GoalCommandOutcome {
        sent += command
        if (pause) {
            val deferred = CompletableDeferred<GoalCommandOutcome>()
            waiting = deferred
            return deferred.await()
        }
        queue.removeFirstOrNull()?.let { return it }
        return next?.also { next = null } ?: CommandOutcome.Committed(
            CommittedGoal(
                OperationReceipt(
                    command.operationId,
                    OperationKind.DeleteGoal,
                    command.generation,
                    10,
                    Instant.EPOCH
                ),
                (GoalId.of("00000000-0000-4000-9000-000000000001") as Outcome.Success).value,
                null
            ),
            replayed = false
        )
    }

    /** What [movementUndoOffer] says is on offer. */
    var undoOffer: () -> GoalUndoOffer? = { null }

    override fun movementUndoOffer(): GoalUndoOffer? = undoOffer()

    /** What [completionUndoOffer] says is on offer. */
    var completionOffer: () -> GoalUndoOffer? = { null }

    override fun completionUndoOffer(): GoalUndoOffer? = completionOffer()

    override suspend fun find(operationId: OperationId): Outcome<CommittedGoal?, StorageFailure> {
        asked += operationId
        return found
    }
}
