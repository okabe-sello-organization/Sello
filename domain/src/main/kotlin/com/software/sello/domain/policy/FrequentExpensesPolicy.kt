package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.FrequentExpense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.UsualAmountError
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Gastos frecuentes (SELLO-199). Only expenses the person typed count: none a fixed payment
 * recorded, none without a note, none in a category that cannot take a new expense, and
 * only those dated from [since] through today. A group is a category and a note folded
 * by [SearchKey], as search folds it. Nothing here saves anything: a group only fills the
 * form.
 */
object FrequentExpensesPolicy {
    /** Expenses dated up to this many days before today count; one a day earlier doesn't. */
    const val WINDOW_DAYS = 90L

    /** A group recorded this many times or more in the window is frequent. */
    const val MIN_USES = 3

    /** The most frequent expenses the sheet offers. */
    const val SHOWN = 5

    /** The most notes the composer suggests. */
    const val SUGGESTED = 3

    /** The usual amount is the median of the group's last this many amounts. */
    const val USUAL_OF_LAST = 10

    private val newestFirst =
        compareByDescending<Expense> { it.date }.thenByDescending { it.sequence }

    /** The first day counted: [WINDOW_DAYS] before [today]. */
    fun since(today: LocalDate): LocalDate = today.minusDays(WINDOW_DAYS)

    /**
     * Every group in the window, recorded once or more: the most repeated first and, on a
     * tie, the one recorded most recently. [categories] are the ones that can take a new
     * expense now.
     */
    fun groups(
        expenses: List<Expense>,
        today: LocalDate,
        categories: Set<CategoryId>
    ): Outcome<List<FrequentExpense>, UsualAmountError> {
        val first = since(today)
        val counted = expenses.mapNotNull { expense ->
            val note = expense.note ?: return@mapNotNull null
            val key = SearchKey.of(note.value)
            val typed = expense.origin == null
            val inWindow = !expense.date.isBefore(first) && !expense.date.isAfter(today)
            if (typed && inWindow && key.isNotBlank() && expense.categoryId in categories) {
                (expense.categoryId to key) to expense
            } else {
                null
            }
        }
        val groups = mutableListOf<FrequentExpense>()
        for ((group, members) in counted.groupBy({ it.first }, { it.second })) {
            val recent = members.sortedWith(newestFirst)
            val latest = recent.first()
            val usual = when (
                val median = usual(recent.take(USUAL_OF_LAST).map { it.amount })
            ) {
                is Outcome.Failure -> return median
                is Outcome.Success -> median.value
            }
            groups += FrequentExpense(
                categoryId = group.first,
                note = checkNotNull(latest.note),
                key = group.second,
                count = members.size,
                usual = usual,
                latestDate = latest.date,
                latestSequence = latest.sequence
            )
        }
        return Outcome.Success(
            groups.sortedWith(
                compareByDescending<FrequentExpense> { it.count }
                    .thenByDescending { it.latestDate }
                    .thenByDescending { it.latestSequence }
            )
        )
    }

    /** What the sheet offers: up to [SHOWN] groups recorded [MIN_USES] times or more. */
    fun frequent(groups: List<FrequentExpense>): List<FrequentExpense> =
        groups.filter { it.count >= MIN_USES }.take(SHOWN)

    /**
     * What the composer suggests while [typed] is written: up to [SUGGESTED] groups whose
     * note starts with it, both folded, in the order of [groups]. Nothing for blank text.
     */
    fun suggestions(groups: List<FrequentExpense>, typed: String): List<FrequentExpense> {
        val start = SearchKey.of(typed.trimStart())
        if (start.isBlank()) return emptyList()
        return groups.filter { it.key.startsWith(start) }.take(SUGGESTED)
    }

    /**
     * The median of [amounts]: the middle one of an odd count; with an even count the mean
     * of the two middle ones, worked out exactly and rounded half up to the currency's
     * minor unit. It always lies between two recorded amounts.
     */
    fun usual(amounts: List<TransactionAmount>): Outcome<TransactionAmount, UsualAmountError> {
        if (amounts.isEmpty()) return Outcome.Failure(UsualAmountError.NoAmounts)
        val currency = amounts.first().money.currency
        amounts.firstOrNull { it.money.currency != currency }?.let {
            return Outcome.Failure(UsualAmountError.CurrencyMismatch(currency, it.money.currency))
        }
        val sorted = amounts.sortedBy { it.money.minorUnits }
        val middle = sorted.size / 2
        if (sorted.size % 2 == 1) return Outcome.Success(sorted[middle])
        val mean = BigDecimal.valueOf(sorted[middle - 1].money.minorUnits)
            .add(BigDecimal.valueOf(sorted[middle].money.minorUnits))
            .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
        return when (val amount = TransactionAmount.of(Money(mean.longValueExact(), currency))) {
            is Outcome.Failure -> Outcome.Failure(UsualAmountError.OutOfRange(amount.error))
            is Outcome.Success -> amount
        }
    }
}
