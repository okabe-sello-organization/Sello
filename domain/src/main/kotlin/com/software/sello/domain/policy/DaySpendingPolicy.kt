package com.software.sello.domain.policy

import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.model.Outcome
import java.time.LocalDate

/** What was spent on each day, as Día a día heads its days (SELLO-192). */
object DaySpendingPolicy {
    /**
     * Every expense amount of a month by its day, added up exactly per day. Only days with
     * an expense have a sum: incomes never come in here, so they are never netted into a
     * day. A sum that does not fit [Money] fails the whole answer; it is never wrapped.
     */
    fun byDay(expenses: List<Pair<LocalDate, Money>>): Outcome<Map<LocalDate, Money>, MoneyError> {
        val days = LinkedHashMap<LocalDate, ExactTotal>()
        for ((date, amount) in expenses) {
            days[date] = (days[date] ?: ExactTotal.zero(amount.currency)) + amount
        }
        val sums = LinkedHashMap<LocalDate, Money>(days.size)
        for ((date, total) in days) {
            when (val sum = total.toMoney()) {
                is Outcome.Success -> sums[date] = sum.value
                is Outcome.Failure -> return sum
            }
        }
        return Outcome.Success(sums)
    }
}
