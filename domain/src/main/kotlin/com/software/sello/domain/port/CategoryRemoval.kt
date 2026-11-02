package com.software.sello.domain.port

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import java.time.YearMonth

/** What happens to a deleted category's expenses and fixed payments (ADR 0024). */
sealed interface Disposal {
    /**
     * Its expenses and fixed payments go to [target], each on its own date. The deleted
     * category's limits and carry go with it; [target] keeps its own.
     */
    data class MoveTo(val target: CategoryId) : Disposal

    /** Its expenses and fixed payments are deleted with it, from every month. */
    data object DeleteAll : Disposal
}

/**
 * Exactly what deleting [categoryId] would remove or move, read at one revision. The person
 * confirms this; [DeleteCategory] carries it back and is refused when the category no
 * longer holds exactly this.
 *
 * [total] sums every expense of the category, in COP; zero when it has none. [firstMonth]
 * and [lastMonth] are the months of its oldest and newest expense, both null when it has
 * none. [schedules] counts its fixed payments, paused ones included. [month] is the current
 * financial month, [spentThisMonth] the category's spending in it and [limitThisMonth] its
 * own limit in it, null when no finite or unlimited limit is in force there (paused or never
 * set). [target] is present exactly when the preview is for [Disposal.MoveTo].
 */
data class RemovalPreview(
    val categoryId: CategoryId,
    val expenses: Int,
    val total: Money,
    val firstMonth: YearMonth?,
    val lastMonth: YearMonth?,
    val schedules: Int,
    val month: YearMonth,
    val spentThisMonth: Money,
    val limitThisMonth: BudgetLimit?,
    val target: TargetPreview?
) {
    /** Nothing to move or delete but the category and its limits. */
    val isEmpty: Boolean get() = expenses == 0 && schedules == 0
}

/** The category that would receive the expenses, as it is in [RemovalPreview.month] today. */
data class TargetPreview(
    val categoryId: CategoryId,
    val spentThisMonth: Money,
    val limitThisMonth: BudgetLimit?
)

/** Why no preview could be given. A preview is never guessed or shown partly. */
sealed interface RemovalPreviewFailure {
    data object CategoryMissing : RemovalPreviewFailure

    data object TargetMissing : RemovalPreviewFailure

    /** An archived category takes no expenses. */
    data object TargetArchived : RemovalPreviewFailure

    /** A category cannot receive its own expenses. */
    data object SameCategory : RemovalPreviewFailure

    /** The sum of its expenses does not fit in `Money`; nothing is shown as a total. */
    data object TotalOverflow : RemovalPreviewFailure

    data class Storage(val failure: StorageFailure) : RemovalPreviewFailure
}

interface CategoryRemovalPreviews {
    /** What deleting [categoryId] with [disposal] would do, read at one revision. */
    suspend fun preview(
        categoryId: CategoryId,
        disposal: Disposal
    ): Outcome<RemovalPreview, RemovalPreviewFailure>
}
