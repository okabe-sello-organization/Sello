package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.carry.CarryProblem
import java.time.LocalDate
import java.time.YearMonth

/** What Recibo shows. A failed read is its own state; it is never shown as zero. */
sealed interface ReciboState {
    data object Loading : ReciboState

    /** Read successfully and there are no categories yet. */
    data object FirstRun : ReciboState

    /**
     * A month that was read. With [stale] the latest read failed and this is the last
     * one that worked, shown as older data and never as current. [invitation] is the one
     * line inviting a look at the month just closed, when there is one to offer.
     */
    data class Month(
        val summary: ReciboSummary,
        val categories: List<ReciboCategory>,
        val stale: Boolean,
        /** Why the latest read failed, when it is a carry figure that cannot be calculated. */
        val carryProblem: CarryProblem? = null,
        val invitation: ReciboInvitation? = null
    ) : ReciboState

    /** The read failed and there is nothing earlier to show. */
    data object Failed : ReciboState

    /**
     * A carry figure the month needs cannot be calculated and there is nothing earlier to
     * show (ADR 0020 CP8): never zero or the base alone, with what repairs it.
     */
    data class CarryFailed(val problem: CarryProblem) : ReciboState
}

/** "Ver cómo cerraste [month] ›": the month just closed, which had expenses. */
data class ReciboInvitation(val month: YearMonth)

/** Which sentence an amount completes. */
enum class ReciboHeadline {
    /** "Te quedan": what is left of the limit in the month in progress. */
    Remaining,

    /** "Te pasaste por": how far over the limit, in any month. */
    Overspent,

    /** "Te sobraron": what was left of the limit in a finished month. */
    LeftOver,

    /** "Has gastado": there is no limit to compare with. */
    Spent
}

/** The verdict, in words on the screen and in what a screen reader says. */
enum class ReciboStamp {
    /** Spent below the limit. "Al día". */
    OnTrack,

    /** Spent exactly a positive limit. "Al límite". */
    AtLimit,

    /** Spent above the limit. "Excedido". */
    Over,

    /** A finished month that stayed within its limit. "Cumplido". */
    WithinLimit,

    /** A finished month that went over its limit. "Excedido". */
    OverLimit,
    NoLimit,

    /** Archived that month: no budget. */
    Paused,

    /** Nothing was configured for the category that month. */
    NotSet,

    /** Reserved payments take exactly what is left. "Sin margen". */
    NoMargin,

    /** Reserved payments take more than what is left. "Revisar". */
    Review
}

/** An amount with the sentence it completes and its verdict. [amount] carries no sign. */
data class BudgetFigure(
    val headline: ReciboHeadline,
    val amount: MoneyTextValue,
    val stamp: ReciboStamp
)

/** The label above the month's big number. */
enum class ReciboAnswerLabel {
    /** "Restante del presupuesto": the limit less what was spent, in any month. */
    Remaining,

    /** "Has gastado este mes": there is no finite limit to compare with. */
    Spent,

    /** "Falta para tus pagos": what reserved payments need beyond what is left. */
    ShortForPayments
}

/**
 * The month as a whole, as the answer slip shows it. [amount] carries its sign: a
 * month over its limit shows a minus. [perDay] exists only in the month in progress
 * with a limit; [closed] only in a finished month with one. [adjustable] offers
 * "Ajustar presupuesto" and is true only for a month in progress that is Excedido or
 * Revisar.
 */
data class ReciboSummary(
    val label: ReciboAnswerLabel,
    val amount: MoneyTextValue,
    val stamp: ReciboStamp,
    val perDay: ReciboPerDay? = null,
    val closed: ReciboClosedMonth? = null,
    val adjustable: Boolean = false,
    /** While payments are reserved: the footnote "Incluye … separado para … pagos previstos". */
    val reserved: ReciboReserved? = null,
    /** While reserved payments need more than is left: the two facts behind it. */
    val shortfall: ReciboShortfall? = null,
    /** While a category carries this month: the month's base, carry and capacity (CP16, CP27). */
    val carry: ReciboCarry? = null
)

/**
 * The month's carry: the sum of the carrying [categories]' carry-in on top of the bases,
 * read on [asOf]. [why] explains a verdict an earlier deficit caused.
 */
data class ReciboCarry(
    val figures: CarryFigures,
    val categories: List<CarriedCategory>,
    val asOf: LocalDate,
    val why: CarryWhy? = null
)

/** A category that carries this month, and since when. */
data class CarriedCategory(val name: String, val since: YearMonth)

/**
 * "Tu límite con arrastre es [capacity]: [category] trae [carry] de meses anteriores." With
 * no [category], several carry. With [base] and [month], the capacity is not above zero:
 * "Te pasaste … y tu límite base es [base]: [month] empezó en [capacity]."
 */
data class CarryWhy(
    val capacity: MoneyTextValue,
    val carry: MoneyTextValue,
    val carryMagnitude: MoneyTextValue,
    val category: String?,
    val base: MoneyTextValue? = null,
    val month: YearMonth? = null
)

/** What is reserved for the [count] payments still to come this month. */
data class ReciboReserved(val total: MoneyTextValue, val count: Int)

/**
 * "Quedan [left] y tus pagos previstos suman [reserved]." or, when the limit is already
 * passed ([over]), "Pasaste tu límite por [left] …". [left] carries no sign.
 */
data class ReciboShortfall(
    val left: MoneyTextValue,
    val over: Boolean,
    val reserved: MoneyTextValue
)

/** What can be spent each day from today to the end of the month, today included. */
data class ReciboPerDay(val amount: MoneyTextValue, val days: Int)

/**
 * What a finished month spent against its limit, both from the same snapshot. With
 * [carried] the limit is the month's limit with carry.
 */
data class ReciboClosedMonth(
    val spent: MoneyTextValue,
    val limit: MoneyTextValue,
    val carried: Boolean = false
)

/**
 * One category in the month. [cellAmount] is what its cell shows: what is left, with a
 * minus when overspent, or what was spent when it has no limit. [fraction] is how full
 * to draw its circle and nothing else; every amount here is exact.
 */
data class ReciboCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val archived: Boolean,
    val figure: BudgetFigure,
    val cellAmount: MoneyTextValue,
    val spent: MoneyTextValue,
    val limit: MoneyTextValue?,
    val fraction: Float?,
    val over: Boolean,
    val expenseCount: Int,
    /** While it carries: base, carry and capacity; [limit] is then the capacity (CP27). */
    val carry: CarryFigures? = null,
    /** Why its verdict is what it is, when an earlier deficit explains it. */
    val why: CarryWhy? = null
) {
    /** No limit on purpose, as its stamp says; never set or paused is something else. */
    val unlimited: Boolean get() = figure.stamp == ReciboStamp.NoLimit
}

sealed interface ReciboAction {
    data object Retry : ReciboAction

    /** Open the new-category form. Navigation is the root's, not the view model's. */
    data object CreateCategory : ReciboAction

    /** Show one category's detail. Which one is shown is the frame's to remember. */
    data class OpenCategory(val id: String) : ReciboAction

    /** Open the form on an existing category. Navigation is the root's. */
    data class EditCategory(val id: String) : ReciboAction

    /** Open the payments reserved this month. Navigation is the root's. */
    data object OpenReserved : ReciboAction

    /** Open Ajustar presupuesto for the month shown. Navigation is the root's. */
    data object AdjustBudget : ReciboAction

    /** Open [month] in Resumen. The invitation is remembered as handled; navigation is the root's. */
    data class OpenInvitation(val month: YearMonth) : ReciboAction

    /** The × on the invitation: not wanted for [month]. */
    data class DismissInvitation(val month: YearMonth) : ReciboAction

    /** Open the category's form on [month], to give that month a limit. Navigation is the root's. */
    data class SetMonthLimit(val categoryId: String, val month: YearMonth) : ReciboAction
}
