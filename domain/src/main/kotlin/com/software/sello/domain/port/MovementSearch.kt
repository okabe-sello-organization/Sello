package com.software.sello.domain.port

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.CopAmountInput
import com.software.sello.domain.policy.SearchTotals

/**
 * What Día a día's search looks for (SELLO-193): [words], found anywhere in a note, a
 * category name or an income's source, ignoring case and accents; and, when the words
 * are a valid COP amount under the D05 input grammar (`18000`, `18.000`), movements of
 * exactly [amount] too. A badly grouped number (`1.80.00`) is only words.
 */
@ConsistentCopyVisibility
data class MovementQuery private constructor(val words: String, val amount: TransactionAmount?) {
    companion object {
        /** The longest search typed, in characters, as category detail's search. */
        const val MAX_LENGTH = 60

        /**
         * The query typed, without surrounding spaces; null when nothing is left to look
         * for. Text longer than [MAX_LENGTH] is refused, never cut here.
         */
        fun of(typed: String): MovementQuery? {
            val words = typed.trim()
            if (words.isEmpty() || words.length > MAX_LENGTH) return null
            val amount = when (val money = CopAmountInput.parse(words)) {
                is Outcome.Failure -> null

                // Zero and amounts past the record cap parse, but no movement holds them.
                is Outcome.Success -> (TransactionAmount.of(money.value) as? Outcome.Success)?.value
            }
            return MovementQuery(words, amount)
        }
    }
}

/**
 * Some of the movements a search finds across every month, newest first in Día a día's
 * own order ([Movement.listOrder]), with whether [more] follow and the [totals] of every
 * match, not only of this page. Read at one [revision] of one [generation].
 */
data class SearchPage(
    val movements: List<Movement>,
    val more: Boolean,
    val totals: SearchTotals,
    val generation: Long,
    val revision: Long
)

interface MovementSearch {
    /**
     * Up to [limit] (1 to [MAX_PAGE]) of the movements [query] finds that [filter] lets
     * through, those listed after [after] (null for the first page). [sourceNames] is how
     * each fixed income source is called on screen, by its key, so a word finds it as
     * shown; an "Otro" source is found by its own name.
     */
    suspend fun read(
        query: MovementQuery,
        filter: MovementFilter,
        sourceNames: Map<String, String>,
        after: Movement?,
        limit: Int
    ): Outcome<SearchPage, MovementReadFailure>

    companion object {
        const val MAX_PAGE = 200
    }
}
