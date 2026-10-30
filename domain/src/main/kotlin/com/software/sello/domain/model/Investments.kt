package com.software.sello.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Rules and examples: docs/testing/investment-examples.md (SELLO-106). An investment is
 * kept in one currency, COP or USD, chosen when it is created and never changed (rule 1).
 */
data class Investment(
    val id: InvestmentId,
    val name: InvestmentName,
    val description: InvestmentDescription?,
    val icon: IconKey,
    val currency: Currency,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

sealed interface InvestmentAmountError {
    /** Below the smallest amount: one minor unit, or zero for a recorded value. */
    data object BelowMinimum : InvestmentAmountError

    data object AboveMaximum : InvestmentAmountError

    /** Investments are kept in COP or USD only (rule 1). */
    data class UnsupportedCurrency(val currency: Currency) : InvestmentAmountError
}

/**
 * One movement's amount in its investment's currency (rule 2): 1 to 999.999.999.999 minor
 * units, so 1 to 999.999.999.999 pesos or USD 0,01 to 9.999.999.999,99. A recorded value
 * may also be 0 ([ofValue]). The same bounds hold for input, commands, rows and backups.
 */
@ConsistentCopyVisibility
data class InvestmentAmount private constructor(val money: Money) {
    companion object {
        const val MAX_UNITS = 999_999_999_999L

        val currencies = setOf(Currency.COP, Currency.USD)

        /** An aporte, retiro or dividend. */
        fun of(money: Money): Outcome<InvestmentAmount, InvestmentAmountError> =
            bounded(money, minimum = 1)

        /** A recorded value, which may be 0 (rule 2). */
        fun ofValue(money: Money): Outcome<InvestmentAmount, InvestmentAmountError> =
            bounded(money, minimum = 0)

        private fun bounded(money: Money, minimum: Long) = when {
            money.currency !in currencies ->
                Outcome.Failure(InvestmentAmountError.UnsupportedCurrency(money.currency))

            money.minorUnits < minimum -> Outcome.Failure(InvestmentAmountError.BelowMinimum)

            money.minorUnits > MAX_UNITS -> Outcome.Failure(InvestmentAmountError.AboveMaximum)

            else -> Outcome.Success(InvestmentAmount(money))
        }
    }
}

enum class InvestmentMovementKind {
    /** Aporte: money put in. */
    Contribution,

    /** Retiro: money taken out, never above the value on its date (rule 8). */
    Withdrawal,

    /** Dividendo o renta: counts in the gain, never in the value (rule 5). */
    Dividend,

    /** Valor de hoy: replaces the value from its place in the replay on (rule 5). */
    Valuation
}

/**
 * One step of an investment's history. Replay is by [date], then [sequence], the order it
 * was recorded in, never reassigned (rule 4). A correction replaces it in place, keeping
 * its [sequence]. [amount] is in the investment's currency.
 */
data class InvestmentMovement(
    val id: InvestmentMovementId,
    val kind: InvestmentMovementKind,
    val amount: InvestmentAmount,
    val date: LocalDate,
    val sequence: Long,
    val note: Note?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** An investment with every movement as it now stands, corrections applied. */
data class InvestmentHistory(val investment: Investment, val movements: List<InvestmentMovement>)

/** The direction of a gain: its sign, which the rounded return never changes (rule 6). */
enum class GainDirection { Gain, Loss, Even }

/**
 * The simple return `G ÷ C`: [tenths] is its magnitude in tenths of a percent, rounded
 * half up on the exact quotient (7,46 % is 75); [direction] is the sign of the gain, so a
 * gain of −1 on 3.000.000 is Loss with 0 tenths ("−0,0 %", IN16).
 */
data class InvestmentReturn(val direction: GainDirection, val tenths: Long)

/** The value carried forward (rule 5) and the date of the recorded value it comes from. */
data class CarriedValue(val amount: Money, val valuedOn: LocalDate)

/**
 * One investment worked out from its history, in its own currency. [value], [gain] and
 * [return] are null when no value is recorded (rule 7). [since] is the month of the
 * earliest aporte.
 */
data class InvestmentFigures(
    val contributed: Money,
    val withdrawn: Money,
    val dividends: Money,
    val value: CarriedValue?,
    val gain: Money?,
    val `return`: InvestmentReturn?,
    val since: YearMonth,
    val movements: Int
)

sealed interface InvestmentHistoryError {
    /** Replayed, [withdrawal] takes more than the [available] value on its date (rule 8). */
    data class WithdrawalAboveValue(val withdrawal: InvestmentMovementId, val available: Money) :
        InvestmentHistoryError

    /** Replayed, [withdrawal] comes when no value is recorded yet (rule 7). */
    data class WithdrawalWithoutValue(val withdrawal: InvestmentMovementId) : InvestmentHistoryError

    /** A movement other than an aporte is dated before the earliest aporte (rule 3). */
    data class BeforeFirstContribution(
        val movement: InvestmentMovementId,
        val firstContribution: LocalDate
    ) : InvestmentHistoryError

    /** The history has no aporte (rule 6). */
    data object NoContribution : InvestmentHistoryError

    /** A movement's currency is not the investment's. */
    data class CurrencyMismatch(val movement: InvestmentMovementId) : InvestmentHistoryError

    /** A total does not fit an exact amount. Reported, never clipped. */
    data object Unrepresentable : InvestmentHistoryError
}

/**
 * Why a history with new lines added does not work out: [error], and the 0-based
 * [position] of the line at fault, or null when no single line is (a total that does not
 * fit). [error] may name a recorded movement that a line left short.
 */
data class InvestmentLineError(val position: Int?, val error: InvestmentHistoryError)

sealed interface UsdRateError {
    /** Below 1,00 or above 100.000,00 pesos per dollar (rule 11). */
    data object OutOfBounds : UsdRateError

    /** More than two decimals. Never rounded. */
    data object TooPrecise : UsdRateError
}

/**
 * Pesos per 1 USD, exact to the centavo (rule 11): [hundredths] is 100 (1,00) to
 * 10.000.000 (100.000,00). Never floating point.
 */
@ConsistentCopyVisibility
data class UsdRate private constructor(val hundredths: Long) {
    val pesos: BigDecimal get() = BigDecimal.valueOf(hundredths, 2)

    companion object {
        const val MIN_HUNDREDTHS = 100L
        const val MAX_HUNDREDTHS = 10_000_000L

        fun ofHundredths(hundredths: Long): Outcome<UsdRate, UsdRateError> =
            if (hundredths in MIN_HUNDREDTHS..MAX_HUNDREDTHS) {
                Outcome.Success(UsdRate(hundredths))
            } else {
                Outcome.Failure(UsdRateError.OutOfBounds)
            }

        /** An exact decimal, as typed or decoded; three decimals are refused, not rounded. */
        fun of(pesos: BigDecimal): Outcome<UsdRate, UsdRateError> {
            val scaled = pesos.stripTrailingZeros()
            if (scaled.scale() > 2) return Outcome.Failure(UsdRateError.TooPrecise)
            val hundredths = try {
                scaled.movePointRight(2).longValueExact()
            } catch (_: ArithmeticException) {
                return Outcome.Failure(UsdRateError.OutOfBounds)
            }
            return ofHundredths(hundredths)
        }
    }
}

/**
 * One entered rate, dated [date] (today or earlier). [sequence] is the order it was
 * entered in; the current rate is the latest [date], then the highest [sequence] (rule 11).
 */
data class UsdRateEntry(
    val id: UsdRateId,
    val rate: UsdRate,
    val date: LocalDate,
    val sequence: Long,
    val createdAt: Instant
)

/** Every entered rate and the current one, or none yet. */
data class UsdRateBook(val current: UsdRateEntry?, val history: List<UsdRateEntry>)

/** One investment as the overview places it: its figures and its value's share in pesos. */
data class PortfolioItem(
    val history: InvestmentHistory,
    val figures: InvestmentFigures,
    /** The value in pesos used for the order and the bar; null without a value. */
    val valueInPesos: Money?
)

/**
 * The overview (rule 13), in pesos, over the investments with a value. COP figures add up;
 * USD sums convert once each at [rate]. [items] are largest value first, then those
 * without a value. [total] is null when no investment has a value (IN35). [rate] is null
 * when no USD investment with a value is in the total.
 */
data class PortfolioOverview(
    val items: List<PortfolioItem>,
    val total: PortfolioTotal?,
    val rate: UsdRateEntry?
)

data class PortfolioTotal(
    val value: Money,
    val contributed: Money,
    val withdrawn: Money,
    val dividends: Money,
    val gain: Money,
    val `return`: InvestmentReturn,
    val since: YearMonth,
    /** The oldest and newest value dates in the total (rule 14). */
    val valuedFrom: LocalDate,
    val valuedTo: LocalDate
)

sealed interface PortfolioError {
    /** A USD investment has a value and no rate exists. Never converted at a guess. */
    data object MissingRate : PortfolioError

    data object Unrepresentable : PortfolioError
}
