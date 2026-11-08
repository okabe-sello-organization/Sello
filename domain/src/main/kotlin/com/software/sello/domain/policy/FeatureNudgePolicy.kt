package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryHistoryMonth
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.FinancialDay
import java.time.YearMonth

/**
 * When the feature nudges' triggers hold (SELLO-212). Each answers only from readings that
 * worked and were complete; [NudgePolicy] then says which nudge, if any, shows.
 */
object FeatureNudgePolicy {
    /** Expenses added by hand on one day before Anotar varios is pointed at. */
    const val BULK_ENTRY_AFTER = 3

    /**
     * Anotar varios: at least [BULK_ENTRY_AFTER] of [expenses] are dated [today] and were
     * added today, in [today]'s zone, by hand. One a pago fijo recorded does not count.
     * Storage does not keep whether an expense was saved alone or with Anotar varios, so
     * both count. [expenses] must hold every expense dated [today].
     */
    fun bulkEntryDue(expenses: List<Expense>, today: FinancialDay): Boolean =
        expenses.count {
            it.origin == null && it.date == today.date &&
                it.createdAt.atZone(today.zone).toLocalDate() == today.date
        } >= BULK_ENTRY_AFTER

    /**
     * Pagos fijos: [categoryId] had, in each of the two months before [month], an expense
     * added by hand with the same amount and the same note, notes compared as search folds
     * them ([SearchKey]), and none of [schedules] is a payment of that category with that
     * amount and note. An expense without a note never counts. [expenses] must hold every
     * expense of those two months; other months and categories in it are ignored.
     */
    fun fixedPaymentDue(
        expenses: List<Expense>,
        categoryId: CategoryId,
        month: YearMonth,
        schedules: List<Schedule>
    ): Boolean {
        fun repeats(of: YearMonth) = expenses.filter {
            it.categoryId == categoryId && it.origin == null && it.note != null &&
                YearMonth.from(it.date) == of
        }.map { Repeat(it.amount, SearchKey.of(it.note?.value)) }.toSet()

        val covered = schedules.mapNotNull { schedule ->
            (schedule.entry as? ScheduledEntry.Payment)
                ?.takeIf { it.categoryId == categoryId }
                ?.let { Repeat(it.amount, SearchKey.of(it.note?.value)) }
        }.toSet()
        val both = repeats(month.minusMonths(1)) intersect repeats(month.minusMonths(2))
        return (both - covered).isNotEmpty()
    }

    /**
     * Arrastre: what [previous], the category's month before as its history has it, ended
     * with left (positive) or missing (negative), when [current], the same category in the
     * month in progress, has a limit, is not archived and does not carry, and [previous] had
     * a limit and did not end exactly on it. Null otherwise.
     */
    fun carryDue(current: CategoryMonth, previous: CategoryHistoryMonth?): Money? {
        val budget = current.budget ?: return null
        if (current.category.archived || budget.carry != null) return null
        val limit = previous?.limit ?: return null
        val left = (limit - previous.spent) as? Outcome.Success ?: return null
        return left.value.takeIf { it.minorUnits != 0L }
    }

    private data class Repeat(val amount: TransactionAmount, val note: String)
}
