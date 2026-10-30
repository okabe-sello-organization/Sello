package com.software.sello.feature.resumen

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.presentation.carry.CarryFigures
import java.time.LocalDate

sealed interface ResumenState {
    data object Loading : ResumenState

    data object Failed : ResumenState

    @Immutable
    data class Ready(
        val month: String,
        val hero: ResumenHero,
        val income: MoneyTextValue,
        val spent: MoneyTextValue,
        val netFlow: MoneyTextValue,
        /** Where [income] went: "¿A dónde fue lo que entró?", with [categories] as its rows. */
        val incomeSlip: ResumenIncome,
        val history: List<ResumenPoint>,
        /** The selected month's limit, drawn once as a dashed line over the columns. */
        val monthLimit: ResumenLimit?,
        /** The selected month's carry: its base, carry and capacity (CP19, CP27). */
        val carry: CarryFigures? = null,
        /** Shown above the columns once there is enough history to average. */
        val normalMonth: ResumenNormalMonth?,
        /** Ranked by what was spent, most first; their amounts add up to [spent]. */
        val categories: List<ResumenCategory>,
        val weekdays: ResumenWeekdays?,
        /** Null when there is none, or it was dismissed this month. */
        val suggestion: ResumenSuggestion?,
        val empty: Boolean,
        val insufficientHistory: Boolean,
        val stale: Boolean,
        /** The day the figures run to, as written in a sentence: "22 de octubre". */
        val asOf: String,
        val selectedHistory: String? = null,
        val selectedWeekday: String? = null,
        /** The fact on the stub's Fijos half. */
        val nextFixed: ResumenNextFixed = ResumenNextFixed.Unknown
    ) : ResumenState
}

/**
 * The income slip's own figures; its rows are [ResumenState.Ready.categories]. [count] is
 * how many incomes make up the total. [spentShare] is how much of the bar is spent:
 * geometry only, never shown or turned back into money. [overspent] says spending went
 * above the income.
 */
@Immutable
data class ResumenIncome(val count: Int, val spentShare: Float, val overspent: Boolean)

/** The nearest fixed payment or income still to come, or why there is none to say. */
sealed interface ResumenNextFixed {
    /** Loading or a failed read: nothing is said, never "none". */
    data object Unknown : ResumenNextFixed

    data object None : ResumenNextFixed

    data class On(val date: LocalDate, val today: LocalDate) : ResumenNextFixed
}

/**
 * How the selected month is going, or how it closed. [monthName] is as written in a
 * sentence ("octubre"). Without [comparison], [spent] is shown alone, from day 1 to
 * [throughDay].
 */
@Immutable
data class ResumenHero(
    val inProgress: Boolean,
    val monthName: String,
    val spent: MoneyTextValue,
    val throughDay: Int,
    val comparison: ResumenComparison?
)

/**
 * The difference with [previousName], never negative: [direction] says which way.
 * [percent] is written "11,1", or null when the month before had nothing on those days.
 * [throughDay] and [previousThroughDay] are null when whole months are compared.
 */
@Immutable
data class ResumenComparison(
    val previousName: String,
    val difference: MoneyTextValue,
    val direction: SpendingDirection,
    val percent: String?,
    val throughDay: Int?,
    val previousThroughDay: Int?,
    val bars: List<ComparisonBar>
)

@Immutable
data class ResumenNormalMonth(val amount: MoneyTextValue, val months: Int)

/**
 * Average spending per weekday, Monday first. [busiest] is the plural day name ("sábados")
 * when one stands out. With [lastSixMonths] the averages cover the last six months;
 * otherwise they start [from], written as in a sentence.
 */
@Immutable
data class ResumenWeekdays(
    val points: List<ResumenPoint>,
    val busiest: String?,
    val busiestAverage: MoneyTextValue?,
    val from: String,
    val lastSixMonths: Boolean
)

/** A category over its limit in [months] (names as in a sentence) that could be adjusted. */
@Immutable
data class ResumenSuggestion(
    val categoryId: String,
    val name: String,
    val months: List<String>,
    val averageSpent: MoneyTextValue,
    val limit: MoneyTextValue
)

@Immutable
data class ResumenPoint(
    val chart: ChartPoint,
    val label: String,
    val amount: MoneyTextValue,
    val detail: MoneyTextValue? = null,
    /** The month in progress: drawn striped and said to be up to today, never as a whole month. */
    val current: Boolean = false
)

@Immutable
data class ResumenLimit(val value: Long, val amount: MoneyTextValue, val carried: Boolean = false)

/**
 * A category row in the income slip, ranked by what was spent. [share] is how much of the
 * bar to fill against the month's spending: geometry only,
 * never shown as a number or turned back into money.
 */
@Immutable
data class ResumenCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val amount: MoneyTextValue,
    val share: Float
)

sealed interface ResumenAction {
    data object Retry : ResumenAction

    data class SelectHistory(val id: String) : ResumenAction

    data class SelectWeekday(val id: String) : ResumenAction

    data object DismissSuggestion : ResumenAction
}
