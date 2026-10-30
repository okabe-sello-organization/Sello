package com.software.sello.domain.policy

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import java.time.YearMonth

/** What a category's budget is in one month. */
sealed interface MonthBudgetState {
    /**
     * A limit applies. [explicit] is true when it was set for this month in
     * particular, false when it comes from the default in force that month.
     */
    data class Limited(val limit: BudgetLimit, val explicit: Boolean) : MonthBudgetState

    /** The category was archived for this month: no automatic budget. */
    data object Paused : MonthBudgetState

    /** Earlier than anything configured for the category. Nothing is assumed. */
    data object Unconfigured : MonthBudgetState
}

/** What unarchiving changes in a category's default history. */
data class UnarchiveChange(val remove: List<YearMonth>, val put: DefaultLimit?)

/**
 * How a category's monthly budget follows from its history. A month's answer depends
 * only on what was configured up to that month, never on today's default or on
 * whether the app was opened then. There is no carryover: each month starts from its
 * own limit.
 */
object CategoryBudgetPolicy {
    /** The default in force in [month]: the latest entry effective that month or earlier. */
    fun inForce(month: YearMonth, defaults: List<DefaultLimit>): DefaultLimit? =
        defaults.filter { it.effectiveMonth <= month }.maxByOrNull { it.effectiveMonth }

    /**
     * [defaults] is one category's whole history and [override] the limit set for
     * [month] itself, if any. The month's own limit wins over any default, including
     * a pause, because someone set it for that month on purpose.
     */
    fun stateFor(
        month: YearMonth,
        defaults: List<DefaultLimit>,
        override: MonthLimit?
    ): MonthBudgetState {
        if (override != null) return MonthBudgetState.Limited(override.limit, explicit = true)
        return when (val budget = inForce(month, defaults)?.budget) {
            null -> MonthBudgetState.Unconfigured
            AutomaticBudget.Paused -> MonthBudgetState.Paused
            is AutomaticBudget.Limit -> MonthBudgetState.Limited(budget.limit, explicit = false)
        }
    }

    /**
     * The [count] limits offered as shortcuts, smallest first: the non-zero limits one
     * could set ([BudgetLimit.entered]) that the categories in use have most often, the
     * smaller first when equally common, topped up from [fallback] while there are fewer
     * than [count].
     */
    fun suggestedLimits(
        categories: List<CategoryInMonth>,
        fallback: List<Money>,
        count: Int = 3
    ): List<Money> {
        val own = categories.asSequence()
            .filter { !it.category.archived }
            .mapNotNull { ((it.state as? MonthBudgetState.Limited)?.limit as? BudgetLimit.Finite) }
            .map { it.amount }
            .filter { it.minorUnits > 0 && BudgetLimit.entered(it) is Outcome.Success }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(MostUsedFirst)
            .map { it.key }
            .take(count)
        return (own + fallback.filter { it !in own }.take(count - own.size))
            .sortedBy { it.minorUnits }
    }

    /** Archiving keeps the current month as it is and pauses from the month after. */
    fun pausedFrom(archivedIn: YearMonth): YearMonth = archivedIn.plusMonths(1)

    /**
     * Unarchiving in [month]. A pause that has not started yet is removed, so the
     * month and its default carry on untouched. If the pause is already in force, the
     * last limit the category was given applies again from [month]; the paused months
     * in between stay paused.
     */
    fun unarchive(month: YearMonth, defaults: List<DefaultLimit>): UnarchiveChange {
        val notStarted = defaults
            .filter { it.budget == AutomaticBudget.Paused && it.effectiveMonth > month }
            .map { it.effectiveMonth }
        val current = inForce(month, defaults)
        if (current?.budget != AutomaticBudget.Paused) return UnarchiveChange(notStarted, null)
        val lastConfigured = defaults
            .filter { it.budget is AutomaticBudget.Limit && it.effectiveMonth <= month }
            .maxByOrNull { it.effectiveMonth }
        return UnarchiveChange(
            notStarted,
            lastConfigured?.copy(effectiveMonth = month)
        )
    }
}

/** More categories first; between equally common limits, the smaller first. */
private val MostUsedFirst =
    compareByDescending<Map.Entry<Money, Int>> { it.value }.thenBy { it.key.minorUnits }
