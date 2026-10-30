package com.software.sello.domain.port

/**
 * A read that is kept up to date, as a screen sees it. [E] says why a read failed. A failed
 * read keeps the last good value, to be shown clearly as older; it never becomes zero or
 * empty in its place.
 */
sealed interface ReadState<out T, out E> {
    /** Nothing has been read yet. */
    data object Loading : ReadState<Nothing, Nothing>

    /** Read successfully, which includes a read that found nothing recorded. */
    data class Ready<out T>(val value: T) : ReadState<T, Nothing>

    /** The latest read failed. [lastGood] is the previous successful read, if there was one. */
    data class Failed<out T, out E>(val failure: E, val lastGood: T?) : ReadState<T, E>
}
