package com.software.sello.domain.model

import com.software.sello.domain.policy.MonthBudgetState
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Estimated spending for the whole month at the pace recorded so far, kept as the
 * exact fraction [numerator] / [denominator] pesos. Resumen shows it as an estimate;
 * no verdict uses it (ADR 0012 BG1). [rounded] is only for showing it.
 */
data class Forecast(val numerator: BigInteger, val denominator: Int, val rounded: Money)

/** The current month, judged on what was actually spent, never on pace (ADR 0012 BG1). */
enum class Verdict {
    /** Spent below the limit, or nothing spent against a zero limit. "Al día". */
    OnTrack,

    /** Spent exactly a positive limit. "Al límite". */
    AtLimit,

    /** Spent above the limit. "Excedido". */
    Over
}

/** A finished month, judged on what was actually spent. */
enum class PastVerdict {
    /** Spending exactly the limit is within it. "Cumplido" on Recibo. */
    WithinLimit,

    /** "Excedido" on Recibo. */
    Over
}

sealed interface BudgetStanding {
    /** The month in progress. [forecast] is an estimate, to be labelled as one. */
    data class Current(val forecast: Forecast, val verdict: Verdict) : BudgetStanding

    /** A finished month: actuals only, no forecast and no allowance. */
    data class Past(val verdict: PastVerdict) : BudgetStanding
}

/**
 * A finite limit against spending. [remaining] is negative when overspent; it is never clamped.
 * With signed carryover ([carry] present, ADR 0020) [limit] is the capacity `C = B + K` that
 * the verdict, remaining and allowance are judged on; [base] is the configured limit, which
 * ordering and Por asignar still use (CP17, CP19). Without carry the two are the same.
 */
data class LimitedBudget(
    val limit: Money,
    val spent: Money,
    val remaining: Money,
    val standing: BudgetStanding,
    val carry: CarryBreakdown? = null
) {
    /** The configured limit, without any carry. */
    val base: Money get() = carry?.base ?: limit
}

/**
 * What can be spent per day from today to the end of the month, today included.
 * [amount] is rounded down to a multiple of 50 pesos and is zero when nothing remains.
 * It is a planning figure: it is not recorded money and nothing else is rounded like it.
 */
data class DailyAllowance(val amount: Money, val availableDays: Int)

/** One payment a schedule still has to record this month (recurrence-examples rule 11). */
data class ReservedPayment(
    val schedule: ScheduleId,
    val categoryId: CategoryId,
    val note: Note?,
    val occurrence: Occurrence,
    val amount: TransactionAmount
)

/** How the reserved payments compare with what is left (ADR 0012 BG1, rule 12). */
enum class ReservedStanding {
    /** Something is left after them: the recorded verdict stands. */
    Enough,

    /** They take exactly what is left: "Sin margen". */
    NoMargin,

    /** They take more than what is left, even past the limit: "Revisar". */
    Short
}

/**
 * The payments reserved in the month in progress: their [total] `Q`, the [margin]
 * `M = R − Q` the headline shows and its [standing]. While [ReservedStanding.Short],
 * [shortfall] is `−M`, what is missing for them ("Falta para tus pagos"), and
 * [overLimitBy] is `−R` once spending is past the limit. Never recorded money.
 */
data class Reservation(
    val payments: List<ReservedPayment>,
    val total: Money,
    val margin: Money,
    val standing: ReservedStanding,
    val shortfall: Money? = null,
    val overLimitBy: Money? = null
)

/** The month as a whole. */
sealed interface OverallBudget {
    /** No category has a finite limit this month: "Has gastado / Sin límite". Not a zero budget. */
    data object NoLimit : OverallBudget

    /**
     * [budget] is the sum of the finite limits against everything spent, including
     * spending in categories without a limit. [allowance] exists only in the current month.
     */
    data class Limited(
        val budget: LimitedBudget,
        val allowance: DailyAllowance?,
        /** Only in the month in progress, while a payment is reserved; [allowance] is from its margin. */
        val reservation: Reservation? = null
    ) : OverallBudget
}

/**
 * One category in the month. [budget] is present only when the category has a finite
 * limit that month; its spending counts towards the month's total either way.
 * [expenseCount] is how many expenses make up [spent].
 */
data class CategoryMonth(
    val category: Category,
    val state: MonthBudgetState,
    val spent: Money,
    val budget: LimitedBudget?,
    val expenseCount: Int,
    /** Newest original addition in the selected month, never an edit/update time. */
    val newestExpenseAddition: Instant? = null
)

/**
 * Everything the screens show about one month, worked out once from rows read
 * together at [revision] of [generation], as of the financial day [asOf] in [zone].
 *
 * [spent] and every figure here count expenses dated up to [asOf] only;
 * [futureDatedExpenses] says how many later ones in the month were left out. A
 * snapshot kept after a failed refresh still carries its own [asOf] and [revision],
 * so it can be shown as what it is.
 */
data class MonthlySnapshot(
    val month: YearMonth,
    val asOf: LocalDate,
    val zone: ZoneId,
    val generation: Long,
    val revision: Long,
    val categories: List<CategoryMonth>,
    val spent: Money,
    val overall: OverallBudget,
    val futureDatedExpenses: Int
)

sealed interface SnapshotFailure {
    data class Storage(val failure: StorageFailure) : SnapshotFailure

    /** A total does not fit an exact amount. It is reported, never clipped or approximated. */
    data object Unrepresentable : SnapshotFailure

    /** Months after the current financial month cannot be viewed. */
    data class FutureMonth(val currentMonth: YearMonth) : SnapshotFailure

    /**
     * A carry figure the month needs could not be calculated (ADR 0020 CP8): missing history,
     * a chain month without a finite base, damaged chains. Never shown as zero carry.
     */
    data class Carryover(val failure: CarryoverFailure) : SnapshotFailure
}
