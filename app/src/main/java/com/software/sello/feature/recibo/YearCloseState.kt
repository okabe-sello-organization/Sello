package com.software.sello.feature.recibo

import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.port.GoalBoard
import java.time.YearMonth

/**
 * The big moment of the year that closed (SELLO-200), presentation only: it reads the year
 * and the goals when it opens and writes nothing.
 */
sealed interface YearCloseState {
    /** Not open. */
    data object Closed : YearCloseState

    /** Open on [year], its figures still being read: nothing is drawn yet. */
    data class Loading(val year: Int) : YearCloseState

    /**
     * Twelve [months], January first, and the plate's counts, never money. [limitedMonths]
     * of 0 leaves the months line out; [goalsReached] of 0 leaves the goals line out.
     */
    data class Ready(
        val year: Int,
        val months: List<YearCloseMonth>,
        val expenses: Int,
        val recordedDays: Int,
        val withinMonths: Int,
        val limitedMonths: Int,
        val goalsReached: Int
    ) : YearCloseState

    /** The year or the goals could not be read: the moment says so instead of any count. */
    data class Failed(val year: Int) : YearCloseState
}

/** A month's own verdict: [Within] lands in full ink, the others faded. */
enum class YearCloseMark { Within, Over, NoLimit }

data class YearCloseMonth(val month: YearMonth, val mark: YearCloseMark)

sealed interface YearCloseAction {
    data class Open(val year: Int) : YearCloseAction

    /** Listo, Back, or Ver tu año once Tu año opens. */
    data object Done : YearCloseAction
}

/**
 * The plate's counts from the closed year and the goal board: the year's expenses, its days
 * with anything recorded, the months within their limit out of those with one, and the goals
 * marked as reached on a day of that year.
 */
internal fun yearClose(year: YearSnapshot, goals: GoalBoard): YearCloseState.Ready {
    val months = year.months.map { line ->
        val verdict = (line.status as? YearMonthStatus.Finished)?.verdict
        YearCloseMonth(
            line.month,
            when (verdict) {
                PastVerdict.WithinLimit -> YearCloseMark.Within
                PastVerdict.Over -> YearCloseMark.Over
                null -> YearCloseMark.NoLimit
            }
        )
    }
    return YearCloseState.Ready(
        year = year.year,
        months = months,
        expenses = year.expenseCount,
        recordedDays = year.recordedDays,
        withinMonths = months.count { it.mark == YearCloseMark.Within },
        limitedMonths = months.count { it.mark != YearCloseMark.NoLimit },
        goalsReached = goals.goals.count { it.history.goal.completedOn?.year == year.year }
    )
}
