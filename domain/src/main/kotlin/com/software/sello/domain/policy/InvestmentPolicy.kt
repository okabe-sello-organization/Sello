package com.software.sello.domain.policy

import com.software.sello.domain.model.CarriedValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.PortfolioItem
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.PortfolioTotal
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Investment rules (docs/testing/investment-examples.md, SELLO-106). Pure and exact:
 * checked `Long` money, `BigDecimal`/`BigInteger` only for the return and conversion,
 * never floating point.
 */
object InvestmentPolicy {
    private val replayOrder = compareBy<InvestmentMovement>({ it.date }, { it.sequence })

    /** What a replay has reached so far: the sums and the value carried forward (rule 5). */
    private class Replay(currency: Currency) {
        val contributed = Sum(currency)
        val withdrawn = Sum(currency)
        val dividends = Sum(currency)
        var value: BigInteger? = null
        var valuedOn: LocalDate? = null
    }

    /** An exact running sum; only [money] checks that the figure fits. */
    private class Sum(val currency: Currency) {
        var units: BigInteger = BigInteger.ZERO

        fun add(amount: Money) {
            units += BigInteger.valueOf(amount.minorUnits)
        }

        fun money(): Money? = units.toMoney(currency)
    }

    /**
     * Replays [movements] in order, refusing a retiro above the value on its date or with
     * no value, and a movement in another currency (rules 4, 5, 7, 8).
     */
    private fun replay(
        movements: List<InvestmentMovement>,
        currency: Currency
    ): Outcome<Replay, InvestmentHistoryError> {
        val state = Replay(currency)
        for (movement in movements.sortedWith(replayOrder)) {
            val amount = movement.amount.money
            if (amount.currency != currency) {
                return Outcome.Failure(InvestmentHistoryError.CurrencyMismatch(movement.id))
            }
            val units = BigInteger.valueOf(amount.minorUnits)
            when (movement.kind) {
                InvestmentMovementKind.Contribution -> {
                    state.contributed.add(amount)
                    state.value = state.value?.plus(units)
                }

                InvestmentMovementKind.Withdrawal -> {
                    val value = state.value ?: return Outcome.Failure(
                        InvestmentHistoryError.WithdrawalWithoutValue(movement.id)
                    )
                    if (units > value) {
                        val available = value.toMoney(currency)
                            ?: return Outcome.Failure(InvestmentHistoryError.Unrepresentable)
                        return Outcome.Failure(
                            InvestmentHistoryError.WithdrawalAboveValue(movement.id, available)
                        )
                    }
                    state.withdrawn.add(amount)
                    state.value = value - units
                }

                InvestmentMovementKind.Dividend -> state.dividends.add(amount)

                InvestmentMovementKind.Valuation -> {
                    state.value = units
                    state.valuedOn = movement.date
                }
            }
        }
        return Outcome.Success(state)
    }

    /**
     * Replays [history] by date, then sequence (rule 4) and works out its figures in its
     * own currency (rules 5–7). Refuses a history that breaks a rule: a retiro above the
     * value on its date or with no value (rules 7–8), a movement other than an aporte
     * before the earliest aporte (rule 3), no aporte (rule 6), a movement in another
     * currency, or a total that does not fit.
     */
    fun figures(history: InvestmentHistory): Outcome<InvestmentFigures, InvestmentHistoryError> {
        val currency = history.investment.currency
        val movements = history.movements
        val first = movements
            .filter { it.kind == InvestmentMovementKind.Contribution }
            .minOfOrNull { it.date }
            ?: return Outcome.Failure(InvestmentHistoryError.NoContribution)
        movements.sortedWith(replayOrder).firstOrNull {
            it.kind != InvestmentMovementKind.Contribution && it.date.isBefore(first)
        }?.let {
            return Outcome.Failure(InvestmentHistoryError.BeforeFirstContribution(it.id, first))
        }
        val state = when (val replayed = replay(movements, currency)) {
            is Outcome.Success -> replayed.value
            is Outcome.Failure -> return replayed
        }
        val unrepresentable = Outcome.Failure(InvestmentHistoryError.Unrepresentable)
        val contributed = state.contributed.money() ?: return unrepresentable
        val withdrawn = state.withdrawn.money() ?: return unrepresentable
        val dividends = state.dividends.money() ?: return unrepresentable
        val valueUnits = state.value
        val value = valueUnits?.let { units ->
            CarriedValue(units.toMoney(currency) ?: return unrepresentable, state.valuedOn!!)
        }
        val gain = valueUnits?.let { units ->
            (units + state.withdrawn.units + state.dividends.units - state.contributed.units)
                .toMoney(currency) ?: return unrepresentable
        }
        return Outcome.Success(
            InvestmentFigures(
                contributed = contributed,
                withdrawn = withdrawn,
                dividends = dividends,
                value = value,
                gain = gain,
                `return` = gain?.let { returnOf(it, contributed) },
                since = YearMonth.from(first),
                movements = movements.size
            )
        )
    }

    /**
     * The value a new retiro dated [date] may take: the value carried forward to the end of
     * [date] (after every movement already dated then), or null when no value is recorded
     * by then (rules 5, 7, 8; IN10, IN18).
     */
    fun available(
        history: InvestmentHistory,
        date: LocalDate
    ): Outcome<Money?, InvestmentHistoryError> {
        val currency = history.investment.currency
        val upTo = history.movements.filter { !it.date.isAfter(date) }
        return when (val replayed = replay(upTo, currency)) {
            is Outcome.Failure -> replayed

            is Outcome.Success -> {
                val units = replayed.value.value ?: return Outcome.Success(null)
                units.toMoney(currency)?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(InvestmentHistoryError.Unrepresentable)
            }
        }
    }

    /** [history] with [movement] added, or replacing the movement with the same id. */
    fun with(history: InvestmentHistory, movement: InvestmentMovement): InvestmentHistory {
        val replaced = history.movements.any { it.id == movement.id }
        val movements = if (replaced) {
            history.movements.map { if (it.id == movement.id) movement else it }
        } else {
            history.movements + movement
        }
        return history.copy(movements = movements)
    }

    /** [history] without the movement [movement]'s id. */
    fun without(history: InvestmentHistory, movement: InvestmentMovement): InvestmentHistory =
        history.copy(movements = history.movements.filterNot { it.id == movement.id })

    /**
     * The simple return `gain ÷ contributed`, one decimal, half up on the exact quotient,
     * signed by the gain (rule 6, IN03, IN16). Null when [contributed] is not positive.
     */
    fun returnOf(gain: Money, contributed: Money): InvestmentReturn? {
        if (contributed.minorUnits <= 0 || gain.currency != contributed.currency) return null
        val direction = when {
            gain.minorUnits > 0 -> GainDirection.Gain
            gain.minorUnits < 0 -> GainDirection.Loss
            else -> GainDirection.Even
        }
        val tenths = BigDecimal.valueOf(gain.minorUnits).abs()
            .multiply(BigDecimal.valueOf(1_000))
            .divide(BigDecimal.valueOf(contributed.minorUnits), 0, RoundingMode.HALF_UP)
        return InvestmentReturn(direction, tenths.longValueExact())
    }

    /** Today or earlier (rule 3, IN24). */
    fun acceptsDate(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today)

    /** The current rate: the latest date, then the last entered (rule 11, IN30, IN31). */
    fun currentRate(rates: List<UsdRateEntry>): UsdRateEntry? =
        rates.maxWithOrNull(compareBy({ it.date }, { it.sequence }))

    /** [cents] dollars at [rate], rounded half up to the peso, or null if it does not fit. */
    private fun pesos(cents: BigInteger, rate: UsdRate): Money? =
        BigDecimal(cents, Currency.USD.scale)
            .multiply(rate.pesos)
            .setScale(0, RoundingMode.HALF_UP)
            .toBigIntegerExact()
            .toMoney(Currency.COP)

    /**
     * The overview in pesos (rule 13, IN06, IN07, IN17, IN28, IN35): investments with a
     * value count; COP sums add up and each USD sum converts once at [rate]; the gain and
     * return come from the converted figures; items are largest value in pesos first, ties
     * by name then id, then those without a value in creation order. [rate] is the current
     * rate or null; it is required only when a USD investment has a value.
     */
    fun overview(
        investments: List<Pair<InvestmentHistory, InvestmentFigures>>,
        rate: UsdRateEntry?
    ): Outcome<PortfolioOverview, PortfolioError> {
        val unrepresentable = Outcome.Failure(PortfolioError.Unrepresentable)
        val (valued, unvalued) = investments.partition { it.second.value != null }
        val usesDollars = valued.any { it.first.investment.currency == Currency.USD }
        if (usesDollars && rate == null) return Outcome.Failure(PortfolioError.MissingRate)

        fun inPesos(currency: Currency, units: BigInteger): Money? = when (currency) {
            Currency.COP -> units.toMoney(Currency.COP)
            else -> pesos(units, rate!!.rate)
        }

        val items = valued.map { (history, figures) ->
            val value = figures.value!!.amount
            val pesos = inPesos(value.currency, BigInteger.valueOf(value.minorUnits))
                ?: return unrepresentable
            PortfolioItem(history, figures, pesos)
        }.sortedWith(
            compareByDescending<PortfolioItem> { it.valueInPesos!!.minorUnits }
                .thenBy { it.history.investment.name.value }
                .thenBy { it.history.investment.id.value }
        ) + unvalued.map { (history, figures) -> PortfolioItem(history, figures, null) }

        if (valued.isEmpty()) return Outcome.Success(PortfolioOverview(items, null, null))

        /** One figure's sum over the investments with a value, each currency converted once. */
        fun total(figure: (InvestmentFigures) -> Money): Money? {
            var pesos = BigInteger.ZERO
            for (currency in listOf(Currency.COP, Currency.USD)) {
                val units = valued
                    .filter { it.first.investment.currency == currency }
                    .fold(BigInteger.ZERO) { sum, it ->
                        sum +
                            BigInteger.valueOf(figure(it.second).minorUnits)
                    }
                if (units.signum() == 0) continue
                pesos += BigInteger.valueOf((inPesos(currency, units) ?: return null).minorUnits)
            }
            return pesos.toMoney(Currency.COP)
        }

        val value = total { it.value!!.amount } ?: return unrepresentable
        val contributed = total { it.contributed } ?: return unrepresentable
        val withdrawn = total { it.withdrawn } ?: return unrepresentable
        val dividends = total { it.dividends } ?: return unrepresentable
        val gain = (
            BigInteger.valueOf(value.minorUnits) + BigInteger.valueOf(withdrawn.minorUnits) +
                BigInteger.valueOf(dividends.minorUnits) -
                BigInteger.valueOf(contributed.minorUnits)
            ).toMoney(Currency.COP) ?: return unrepresentable
        val dates = valued.map { it.second.value!!.valuedOn }
        val total = PortfolioTotal(
            value = value,
            contributed = contributed,
            withdrawn = withdrawn,
            dividends = dividends,
            gain = gain,
            `return` = returnOf(gain, contributed) ?: return unrepresentable,
            since = valued.minOf { it.second.since },
            valuedFrom = dates.min(),
            valuedTo = dates.max()
        )
        return Outcome.Success(PortfolioOverview(items, total, rate.takeIf { usesDollars }))
    }
}

/** The figure in [currency], or null when it does not fit an exact amount. */
private fun BigInteger.toMoney(currency: Currency): Money? =
    if (bitLength() < Long.SIZE_BITS) Money(toLong(), currency) else null
