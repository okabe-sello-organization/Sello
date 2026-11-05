package com.software.sello.testing

import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A hand-driven read as a real one is seen: every new subscription starts from Loading and
 * knows only the good values it read itself, as `ObservedReads.observeOn` does. A failure
 * set by hand loses any last good value the subscription did not see.
 */
fun <T, E> Flow<ReadState<T, E>>.asNewObservation(): Flow<ReadState<T, E>> {
    val source = this
    return flow {
        emit(ReadState.Loading)
        var lastGood: T? = null
        source.collect { read ->
            when (read) {
                ReadState.Loading -> Unit

                is ReadState.Ready -> {
                    lastGood = read.value
                    emit(read)
                }

                is ReadState.Failed -> emit(ReadState.Failed(read.failure, lastGood))
            }
        }
    }
}

/** [HandSnapshots] whose every observation is new, as the real snapshots are. */
open class NewObservationSnapshots : HandSnapshots() {
    override fun observe(month: YearMonth): Flow<SnapshotState> =
        super.observe(month).asNewObservation()
}

/**
 * A read whose subscriptions answer [answers] in turn: Loading, then the next answer, and the
 * flow ends, as `ObservedReads.observeOn` ends after a database error. Only a new
 * subscription reads again; [subscriptions] counts them.
 */
class AnswersInTurn<T, E>(vararg answers: ReadState<T, E>) {
    private val left = ArrayDeque(answers.toList())
    var subscriptions = 0
        private set

    fun observe(): Flow<ReadState<T, E>> = flow {
        subscriptions++
        emit(ReadState.Loading)
        emit(left.removeFirst())
    }
}
