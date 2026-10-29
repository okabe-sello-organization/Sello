package com.software.sello.data.repository

import android.database.SQLException
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ReadState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * [read] again with each value of [changes], the latest only: Loading first, then each read.
 * A failed read is a state with the last good value, never an empty one. A database error
 * that ends [changes] is told the same way, as [unavailable], and ends the flow. A
 * cancelled collector gets the cancellation.
 */
internal fun <K, T, E> observeOn(
    changes: Flow<K>,
    unavailable: (StorageFailure) -> E,
    read: suspend (K) -> Outcome<T, E>
): Flow<ReadState<T, E>> = flow {
    emit(ReadState.Loading)
    var lastGood: T? = null
    try {
        changes.conflate().collect { key ->
            when (val result = read(key)) {
                is Outcome.Success -> {
                    lastGood = result.value
                    emit(ReadState.Ready(result.value))
                }

                is Outcome.Failure -> emit(ReadState.Failed(result.error, lastGood))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: SQLException) {
        emit(ReadState.Failed(unavailable(unavailableFrom(failure)), lastGood))
    } catch (failure: IllegalStateException) {
        emit(ReadState.Failed(unavailable(unavailableFrom(failure)), lastGood))
    }
}

/** [observeOn] the profile's revision, which every command moves in its transaction. */
internal fun <T> SelloDatabase.observeOnRevision(
    read: suspend () -> Outcome<T, StorageFailure>
): Flow<ReadState<T, StorageFailure>> = observeOn(profileDao().changes(), { it }) { read() }

private fun unavailableFrom(failure: Exception) =
    StorageFailure.Unavailable(failure.javaClass.simpleName)
