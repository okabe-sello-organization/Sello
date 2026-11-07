package com.software.sello.domain.policy

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatedNote

/** Lo que más se repite (SELLO-195): notes grouped as search folds them; never the note-less. */
object RepeatedNotesPolicy {
    const val MIN_USES = 2
    const val SHOWN = 3

    private val newest = compareBy<Expense>({ it.date }, { it.sequence })

    fun top(expenses: List<Expense>): Outcome<List<RepeatedNote>, MoneyError> {
        val groups = expenses.filter { it.note != null }
            .groupBy { SearchKey.of(it.note?.value) }
            .values.filter { it.size >= MIN_USES }
        val ranked = mutableListOf<Pair<Expense, RepeatedNote>>()
        for (group in groups) {
            val latest = group.maxWith(newest)
            val total = when (
                val sum = ExactTotal.of(Currency.COP, group.map { it.amount.money }).toMoney()
            ) {
                is Outcome.Failure -> return sum
                is Outcome.Success -> sum.value
            }
            ranked += latest to RepeatedNote(checkNotNull(latest.note), group.size, total)
        }
        return Outcome.Success(
            ranked.sortedWith(
                compareByDescending<Pair<Expense, RepeatedNote>> { it.second.total.minorUnits }
                    .thenByDescending { it.second.count }
                    .thenByDescending { it.first.date }
                    .thenByDescending { it.first.sequence }
            ).take(SHOWN).map { it.second }
        )
    }
}
