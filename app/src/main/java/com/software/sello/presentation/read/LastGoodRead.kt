package com.software.sello.presentation.read

import com.software.sello.domain.port.ReadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What a screen last read, kept by its ViewModel across new observations of the same thing.
 *
 * A read port starts every new observation from Loading with no last good value: a Retry,
 * or coming back to a screen after its sharing stopped, starts one. Through [keep], such an
 * observation repeats what was last shown instead of Loading, and a failed read carries the
 * last good value, so figures already shown are never replaced by a skeleton or an error
 * screen: they stay, marked as older. Only what was never read is Loading or an error.
 *
 * [of] says what is read (a month, a category): another one starts afresh, so the figures
 * of one are never shown for another. One instance per read, used from the main thread.
 */
class LastGoodRead<T, E> {
    private var of: Any? = NOTHING
    private var shown: ReadState<T, E>? = null

    fun keep(of: Any?, reads: Flow<ReadState<T, E>>): Flow<ReadState<T, E>> =
        // What was shown, then the same read again, is told once.
        reads.map { read -> remember(of, read) }.distinctUntilChanged()

    private fun remember(of: Any?, read: ReadState<T, E>): ReadState<T, E> {
        if (of != this.of) {
            this.of = of
            shown = null
        }
        return when (read) {
            ReadState.Loading -> shown ?: read

            is ReadState.Ready -> read.also { shown = it }

            is ReadState.Failed -> {
                val good = read.lastGood ?: shown?.let(::goodIn)
                ReadState.Failed(read.failure, good).also { if (good != null) shown = it }
            }
        }
    }

    private fun goodIn(state: ReadState<T, E>): T? = when (state) {
        ReadState.Loading -> null
        is ReadState.Ready -> state.value
        is ReadState.Failed -> state.lastGood
    }

    private object NOTHING
}
