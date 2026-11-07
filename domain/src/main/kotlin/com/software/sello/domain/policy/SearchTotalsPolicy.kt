package com.software.sello.domain.policy

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Outcome
import java.time.LocalDate
import java.time.YearMonth

/** How many movements matched and what they add up to, exactly. */
data class Matches(val count: Long, val total: Money)

/**
 * One month of a search's results: how many movements it found there and what the
 * matching expenses add up to ([spent]), null when it found no expense in that month.
 */
data class MonthMatches(val month: YearMonth, val count: Long, val spent: Money?)

/**
 * What a whole search found (SELLO-193): each month with a match, newest first, and the
 * matching [expenses] and [incomes], each null when there is none.
 */
data class SearchTotals(
    val months: List<MonthMatches>,
    val expenses: Matches?,
    val incomes: Matches?
)

/** Adds up what Día a día's search found, by month and in all. */
object SearchTotalsPolicy {
    /**
     * [expenses] and [incomes] are every match's day and amount. A month's sum counts its
     * expenses only: incomes are counted, never netted in. A sum that does not fit [Money]
     * or mixes currencies fails the whole answer; it is never wrapped or left out.
     */
    fun of(
        expenses: List<Pair<LocalDate, Money>>,
        incomes: List<Pair<LocalDate, Money>>
    ): Outcome<SearchTotals, MoneyError> {
        val counts = HashMap<YearMonth, Long>()
        val spent = HashMap<YearMonth, ExactTotal>()
        for ((date, amount) in expenses) {
            val month = YearMonth.from(date)
            counts[month] = (counts[month] ?: 0L) + 1
            spent[month] = (spent[month] ?: ExactTotal.zero(amount.currency)) + amount
        }
        for ((date, _) in incomes) {
            val month = YearMonth.from(date)
            counts[month] = (counts[month] ?: 0L) + 1
        }
        val months = ArrayList<MonthMatches>(counts.size)
        for (month in counts.keys.sortedDescending()) {
            val sum = spent[month]?.let { total ->
                when (val money = total.toMoney()) {
                    is Outcome.Failure -> return money
                    is Outcome.Success -> money.value
                }
            }
            months += MonthMatches(month, counts.getValue(month), sum)
        }
        val expenseMatches = when (val all = matches(expenses)) {
            is Outcome.Failure -> return all
            is Outcome.Success -> all.value
        }
        val incomeMatches = when (val all = matches(incomes)) {
            is Outcome.Failure -> return all
            is Outcome.Success -> all.value
        }
        return Outcome.Success(SearchTotals(months, expenseMatches, incomeMatches))
    }

    private fun matches(found: List<Pair<LocalDate, Money>>): Outcome<Matches?, MoneyError> {
        if (found.isEmpty()) return Outcome.Success(null)
        val currency: Currency = found.first().second.currency
        return when (val total = ExactTotal.of(currency, found.map { it.second }).toMoney()) {
            is Outcome.Failure -> total
            is Outcome.Success -> Outcome.Success(Matches(found.size.toLong(), total.value))
        }
    }
}
