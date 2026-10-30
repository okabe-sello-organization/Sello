package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.carry.CarryProblem
import java.time.LocalDate
import java.time.YearMonth

/** One category in the selected month, and the expenses behind its figures. */
sealed interface CategoryDetailState {
    /** No category is being shown. */
    data object Closed : CategoryDetailState

    data object Loading : CategoryDetailState

    /**
     * [category] comes from the same snapshot Recibo shows, so the two agree. With
     * [stale] it is the last month that could be read, not the current one. [today]
     * is the day the figures are as of.
     */
    data class Ready(
        val category: ReciboCategory,
        val month: YearMonth,
        val today: LocalDate,
        val stale: Boolean,
        val canRecord: Boolean,
        val expenses: ExpenseListState,
        /** Why the last deletion asked for from this list did not happen, until the next one. */
        val deleteProblem: DeleteProblem? = null,
        val insights: DetailInsightsState = DetailInsightsState.Loading,
        /** Why the latest read failed, when a carry figure cannot be calculated (CP8). */
        val carryProblem: CarryProblem? = null
    ) : CategoryDetailState

    /** The month was read and this category has nothing in it. */
    data object Gone : CategoryDetailState

    data object Failed : CategoryDetailState

    /** A carry figure cannot be calculated and nothing earlier was read (CP8). */
    data class CarryFailed(val problem: CarryProblem) : CategoryDetailState
}

/** Why an expense was not deleted. In every case it is still recorded, or was already gone. */
enum class DeleteProblem {
    /** It had already been deleted. */
    Gone,

    /** It was changed since it was listed; the list shows it as it is now. */
    Changed,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** Storage failed and nothing was deleted. */
    Failed
}

/** The expenses listed. An empty list and a list that could not be read are different. */
sealed interface ExpenseListState {
    data object Loading : ExpenseListState

    /** Read successfully: there are none. */
    data object Empty : ExpenseListState

    /**
     * The newest [rows]; with [more] there are older ones not listed yet, so these
     * rows are never the source of a total. [moreFailed] says the last attempt to list
     * more did not work. With [refreshing] these are the rows listed before the figures
     * above them changed: they are being read again and may not match those figures yet.
     */
    data class Rows(
        val rows: List<ExpenseRow>,
        val more: Boolean,
        val loadingMore: Boolean = false,
        val moreFailed: Boolean = false,
        val refreshing: Boolean = false
    ) : ExpenseListState

    data object Failed : ExpenseListState
}

/** [notCountedYet] is an expense dated after today: listed, and not yet in the figures. */
data class ExpenseRow(
    val id: String,
    val date: LocalDate,
    val note: String?,
    val amount: MoneyTextValue,
    val notCountedYet: Boolean
)

sealed interface CategoryDetailAction {
    /** Show this category, or none. */
    data class Show(val categoryId: String?) : CategoryDetailAction

    data object Retry : CategoryDetailAction

    /** List the next older expenses. */
    data object ShowMore : CategoryDetailAction

    /** Delete one of the listed expenses, as it is listed. */
    data class Delete(val expenseId: String) : CategoryDetailAction
}
