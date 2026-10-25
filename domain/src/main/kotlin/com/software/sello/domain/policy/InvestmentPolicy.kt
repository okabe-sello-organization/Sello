package com.software.sello.domain.policy

import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import java.time.LocalDate

/**
 * Investment rules (docs/testing/investment-examples.md, SELLO-106). Pure and exact:
 * checked `Long` money, `BigDecimal`/`BigInteger` only for the return and conversion,
 * never floating point.
 */
object InvestmentPolicy {
    /**
     * Replays [history] by date, then sequence (rule 4) and works out its figures in its
     * own currency (rules 5–7). Refuses a history that breaks a rule: a retiro above the
     * value on its date or with no value (rules 7–8), a movement other than an aporte
     * before the earliest aporte (rule 3), no aporte (rule 6), a movement in another
     * currency, or a total that does not fit.
     */
    fun figures(history: InvestmentHistory): Outcome<InvestmentFigures, InvestmentHistoryError> =
        TODO("SELLO-107")

    /**
     * The value a new retiro dated [date] may take: the value carried forward to the end of
     * [date] (after every movement already dated then), or null when no value is recorded
     * by then (rules 5, 7, 8; IN10, IN18).
     */
    fun available(history: InvestmentHistory, date: LocalDate): Outcome<Money?, InvestmentHistoryError> =
        TODO("SELLO-107")

    /** [history] with [movement] added, or replacing the movement with the same id. */
    fun with(history: InvestmentHistory, movement: InvestmentMovement): InvestmentHistory =
        TODO("SELLO-107")

    /** [history] without the movement [movement]'s id. */
    fun without(history: InvestmentHistory, movement: InvestmentMovement): InvestmentHistory =
        TODO("SELLO-107")

    /**
     * The simple return `gain ÷ contributed`, one decimal, half up on the exact quotient,
     * signed by the gain (rule 6, IN03, IN16). Null when [contributed] is not positive.
     */
    fun returnOf(gain: Money, contributed: Money): InvestmentReturn? = TODO("SELLO-107")

    /** Today or earlier (rule 3, IN24). */
    fun acceptsDate(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today)

    /** The current rate: the latest date, then the last entered (rule 11, IN30, IN31). */
    fun currentRate(rates: List<UsdRateEntry>): UsdRateEntry? = TODO("SELLO-107")

    /** [usd] in pesos at [rate], rounded half up to the peso once (rule 13, IN28). */
    fun toPesos(usd: Money, rate: UsdRate): Outcome<Money, PortfolioError> = TODO("SELLO-107")

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
    ): Outcome<PortfolioOverview, PortfolioError> = TODO("SELLO-107")
}
