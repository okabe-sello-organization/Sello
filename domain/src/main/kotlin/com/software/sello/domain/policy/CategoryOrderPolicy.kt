package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection

/**
 * The order categories are shown in (decision D06). Only the default order exists so
 * far: "Más usadas", most expenses in the selected month first.
 *
 * Whatever the order, a category with a finite limit that month comes before one
 * without, and equal categories are ordered by name ignoring case and accents, then
 * by identifier, so the result never depends on the order rows were read in.
 */
object CategoryOrderPolicy {
    fun mostUsed(categories: List<CategoryMonth>): List<CategoryMonth> =
        order(categories, CategoryOrder.MostUsed, CategoryOrderDirection.Descending)

    /**
     * D06 in one comparator: finite categories before unlimited ones, categories without
     * selected-month expenses last inside either partition, then the selected metric.
     * Direction never reverses those partitions or the deterministic name/ID tie-break.
     */
    fun order(
        categories: List<CategoryMonth>,
        order: CategoryOrder,
        direction: CategoryOrderDirection
    ): List<CategoryMonth> = categories.sortedWith { left, right ->
        compareValues(left.budget == null, right.budget == null)
            .takeUnless { it == 0 }
            ?: compareValues(left.expenseCount == 0, right.expenseCount == 0)
                .takeUnless { it == 0 }
            ?: metric(left, right, order, direction).takeUnless { it == 0 }
            ?: left.category.name.uniquenessKey.compareTo(right.category.name.uniquenessKey)
                .takeUnless { it == 0 }
            ?: left.category.id.value.compareTo(right.category.id.value)
    }

    private fun metric(
        left: CategoryMonth,
        right: CategoryMonth,
        order: CategoryOrder,
        direction: CategoryOrderDirection
    ): Int {
        val compared = when (order) {
            CategoryOrder.MostUsed -> left.expenseCount.compareTo(right.expenseCount)

            CategoryOrder.Alphabetical ->
                left.category.name.uniquenessKey.compareTo(right.category.name.uniquenessKey)

            CategoryOrder.AmountSpent -> left.spent.minorUnits.compareTo(right.spent.minorUnits)

            // The base, not the capacity with carry (ADR 0020 CP19): ordering must not move with spending.
            CategoryOrder.Limit -> (left.budget?.base?.minorUnits ?: 0L)
                .compareTo(right.budget?.base?.minorUnits ?: 0L)

            CategoryOrder.LatestExpenseAdded -> compareValues(
                left.newestExpenseAddition,
                right.newestExpenseAddition
            )
        }
        return if (direction == CategoryOrderDirection.Descending) -compared else compared
    }
}
