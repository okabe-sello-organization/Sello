package com.software.sello.feature.investments

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentLedgerLine
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.PortfolioItem
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.policy.InvestmentOrderPolicy
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.money.MoneyFormatter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Turns the investment board into what Inversiones draws. Every figure comes from
 * `InvestmentPolicy` through the board and the order from `InvestmentOrderPolicy`;
 * nothing is added, converted or compared here.
 */
class InvestmentsRenderModel(private val money: MoneyFormatter) {
    fun overview(
        board: InvestmentBoardState,
        today: LocalDate,
        order: InvestmentOrder = InvestmentOrder.LargestValue
    ): InversionesState = when (board) {
        ReadState.Loading -> InversionesState.Loading

        is ReadState.Ready -> overview(board.value, today, order, stale = false)

        is ReadState.Failed -> board.lastGood?.let {
            overview(it, today, order, stale = true)
        } ?: InversionesState.Failed
    }

    /** One investment's detail from the board, or why it cannot be shown. */
    fun detail(board: InvestmentBoardState, id: String, today: LocalDate): InvestmentDetailState =
        when (board) {
            ReadState.Loading -> InvestmentDetailState.Loading

            is ReadState.Ready -> detail(board.value, id, today, stale = false)

            is ReadState.Failed -> board.lastGood?.let {
                detail(it, id, today, stale = true)
            } ?: InvestmentDetailState.Failed
        }

    private fun detail(
        board: InvestmentBoard,
        id: String,
        today: LocalDate,
        stale: Boolean
    ): InvestmentDetailState {
        val summary = board.investments.firstOrNull { it.history.investment.id.value == id }
            ?: return InvestmentDetailState.Gone
        val investment = summary.history.investment
        val figures = summary.figures
        // Newest first with the figures under each amount (IN37–IN40). The board's figures
        // already worked out, so the ledger does too; without it the rows show no figure.
        val ledger = (InvestmentPolicy.ledger(summary.history) as? Outcome.Success)?.value
        val newestFirst = ledger?.map { it.movement } ?: summary.history.movements.sortedWith(
            compareByDescending<InvestmentMovement> { it.date }.thenByDescending { it.sequence }
        )
        val lines = ledger?.associateBy { it.movement.id }
        return InvestmentDetailState.Ready(
            id = investment.id.value,
            name = investment.name.value,
            description = investment.description?.value,
            iconKey = investment.icon.value,
            dollars = investment.currency == Currency.USD,
            value = figures.value?.let { money.format(it.amount) },
            valuedOn = figures.value?.valuedOn,
            gain = figures.gain?.let { money.format(it, signed = true) },
            `return` = figures.`return`,
            contributed = money.format(figures.contributed),
            withdrawn = money.format(figures.withdrawn),
            dividends = money.format(figures.dividends),
            since = figures.since,
            chart = ledger?.let { chart(it.asReversed(), investment.currency, today) },
            movements = newestFirst.map { movement ->
                val amount = movement.amount.money
                InvestmentMovementModel(
                    id = movement.id.value,
                    kind = movement.kind,
                    date = movement.date,
                    note = movement.note?.value,
                    amount = when (movement.kind) {
                        InvestmentMovementKind.Valuation -> money.format(amount)

                        InvestmentMovementKind.Withdrawal ->
                            money.format(amount).copy(sign = MoneySign.Minus)

                        else -> money.format(amount, signed = true)
                    },
                    figure = lines?.get(movement.id)?.let(::figure)
                )
            },
            today = today,
            stale = stale
        )
    }

    private fun figure(line: InvestmentLedgerLine): MovementFigure = when (line.movement.kind) {
        InvestmentMovementKind.Valuation -> {
            val gain = line.gain!!
            MovementFigure.Gain(
                direction = when {
                    gain.minorUnits > 0 -> GainDirection.Gain
                    gain.minorUnits < 0 -> GainDirection.Loss
                    else -> GainDirection.Even
                },
                amount = money.format(gain, signed = true)
            )
        }

        InvestmentMovementKind.Dividend -> MovementFigure.Dividends(money.format(line.dividends))

        else -> MovementFigure.PutIn(money.format(line.putIn))
    }

    /**
     * Recorded values as points, each with what was put in by then, and what was put in
     * (aportes − retiros) as steps, from the [replayed] ledger, oldest first. Time runs
     * from the first aporte to today; heights run between the round amounts of [grid]
     * around them. Coordinates are drawing positions, never money.
     */
    private fun chart(
        replayed: List<InvestmentLedgerLine>,
        currency: Currency,
        today: LocalDate
    ): ValuesChartModel? {
        val first = replayed.firstOrNull {
            it.movement.kind == InvestmentMovementKind.Contribution
        }?.movement?.date ?: return null
        val end = maxOf(today, replayed.last().movement.date)
        val span = (end.toEpochDay() - first.toEpochDay()).coerceAtLeast(1)
        val steps = replayed.filter {
            it.movement.kind == InvestmentMovementKind.Contribution ||
                it.movement.kind == InvestmentMovementKind.Withdrawal
        }
        val values = replayed.filter { it.movement.kind == InvestmentMovementKind.Valuation }
        val heights = values.map { it.movement.amount.money.minorUnits } +
            steps.map { it.putIn.minorUnits }
        val scale = grid(heights.min(), heights.max(), currency)
        fun x(date: LocalDate) = (date.toEpochDay() - first.toEpochDay()).toFloat() / span
        return ValuesChartModel(
            values = values.map {
                val units = it.movement.amount.money.minorUnits
                ChartPoint(
                    id = it.movement.id.value,
                    x = x(it.movement.date),
                    y = scale.y(units),
                    date = it.movement.date,
                    amount = money.format(it.movement.amount.money),
                    putIn = money.format(it.putIn)
                )
            },
            putIn = steps.map {
                ChartPoint(
                    id = it.movement.id.value,
                    x = x(it.movement.date),
                    y = scale.y(it.putIn.minorUnits),
                    date = it.movement.date,
                    amount = money.format(it.putIn)
                )
            },
            start = YearMonth.from(first),
            end = YearMonth.from(end),
            middle = YearMonth.from(first.plusDays(span / 2)),
            grid = scale.marks
        )
    }

    /** Round amounts from bottom to top and where each sits, for the chart's gridlines. */
    private class Grid(
        private val bottom: BigInteger,
        private val top: BigInteger,
        val marks: List<AxisMark>
    ) {
        fun y(units: Long): Float = y(BigInteger.valueOf(units))

        fun y(units: BigInteger): Float = BigDecimal(units - bottom)
            .divide(BigDecimal(top - bottom), 6, RoundingMode.HALF_UP)
            .toFloat()
    }

    /**
     * Gridlines a round step apart (1, 2, 2,5 or 5 times a power of ten, in whole pesos or
     * dollars): the smallest step whose marks, from the one at or below [low] to the one
     * at or above [high], are at most three steps apart, so at most four marks. Equal
     * heights get a step a third of their size and one mark either side. Exact: no figure
     * is wrapped or rounded.
     */
    private fun grid(low: Long, high: Long, currency: Currency): Grid {
        val unit = BigInteger.TEN.pow(currency.scale)
        val lowest = BigInteger.valueOf(low)
        val highest = BigInteger.valueOf(high)
        fun floor(units: BigInteger, step: BigInteger) = units.subtract(units.mod(step))
        fun ceil(units: BigInteger, step: BigInteger) =
            floor(units.add(step).subtract(BigInteger.ONE), step)
        val equal = lowest == highest
        val size = highest.abs().max(unit)
        val step = steps(unit).first { step ->
            if (equal) {
                step.multiply(MAX_STEPS) >= size
            } else {
                ceil(highest, step).subtract(floor(lowest, step)) <= step.multiply(MAX_STEPS)
            }
        }
        var bottom = floor(lowest, step)
        var top = ceil(highest, step)
        if (bottom == top) {
            bottom -= step
            top += step
        }
        val grid = Grid(bottom, top, emptyList())
        val marks = generateSequence(bottom) { it + step }.takeWhile { it <= top }
            .map { units -> axisMark(units, step, currency).copy(y = grid.y(units)) }
            .toList()
        return Grid(bottom, top, marks)
    }

    /** Round steps in minor units: 1, 2, 2,5 and 5 times each power of ten of a whole [unit]. */
    private fun steps(unit: BigInteger): Sequence<BigInteger> = generateSequence(unit) {
        it.multiply(BigInteger.TEN)
    }.flatMap { ten ->
        listOfNotNull(
            ten,
            ten.multiply(TWO),
            if (ten > unit) ten.multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN) else null,
            ten.multiply(FIVE)
        ).asSequence()
    }

    /**
     * [units], a multiple of [step], as an axis writes it, exactly: in millions ("2,5 M")
     * from a million while the step is at least 100.000, in thousands ("800 mil") from
     * ten thousand while the step is at least 100, otherwise whole ("1.020"). So two marks
     * never read the same, and a figure has at most two decimals.
     */
    private fun axisMark(units: BigInteger, step: BigInteger, currency: Currency): AxisMark {
        val whole = BigDecimal(units, currency.scale)
        val wholeStep = BigDecimal(step, currency.scale)
        val (amount, unit) = when {
            whole.abs() >= MILLION && wholeStep >= HUNDRED_THOUSAND ->
                whole.divide(MILLION) to AxisUnit.Millions

            whole.abs() >= TEN_THOUSAND && wholeStep >= HUNDRED ->
                whole.divide(THOUSAND) to AxisUnit.Thousands

            else -> whole to AxisUnit.One
        }
        val exact = amount.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        val plain = exact.abs().toPlainString()
        val integer = plain.substringBefore('.')
        val grouped = integer.reversed().chunked(3).joinToString(".").reversed()
        val decimals = plain.substringAfter('.', "").let { if (it.isEmpty()) "" else ",$it" }
        val sign = if (exact.signum() < 0) "−" else ""
        return AxisMark(0f, sign + grouped + decimals, unit)
    }

    /** The rate as the app writes it: "4.150,25". */
    fun rate(rate: UsdRate): String = money.decimal(rate.hundredths, Currency.USD.scale)

    private fun overview(
        board: InvestmentBoard,
        today: LocalDate,
        order: InvestmentOrder,
        stale: Boolean
    ): InversionesState {
        val overview = when (val worked = board.overview) {
            is Outcome.Success -> worked.value

            // Never guessed at another rate, never left out: the rows still show.
            is Outcome.Failure -> when (worked.error) {
                PortfolioError.MissingRate -> return InversionesState.Ready(
                    total = null,
                    rows = InvestmentOrderPolicy.order(
                        board.investments.map { PortfolioItem(it.history, it.figures, null) },
                        order
                    ).map { row(it, share = 0f) },
                    rate = null,
                    today = today,
                    missingRate = true,
                    stale = stale
                )

                PortfolioError.Unrepresentable -> return InversionesState.Failed
            }
        }
        val total = overview.total
        val pesos = total?.value?.minorUnits?.takeIf { it > 0 }
        return InversionesState.Ready(
            total = total?.let {
                PortfolioTotalModel(
                    value = money.format(it.value),
                    gain = money.format(it.gain, signed = true),
                    `return` = it.`return`,
                    contributed = money.format(it.contributed),
                    withdrawn = money.format(it.withdrawn),
                    dividends = money.format(it.dividends),
                    since = it.since,
                    valuedFrom = it.valuedFrom,
                    valuedTo = it.valuedTo
                )
            },
            rows = InvestmentOrderPolicy.order(overview.items, order).map { item ->
                val value = item.valueInPesos?.minorUnits
                val share = value?.let { units ->
                    pesos?.let { (units.toDouble() / it).toFloat() }
                } ?: 0f
                row(item, share, value?.let { units -> pesos?.let { percent(units, it) } })
            },
            rate = overview.rate?.let { RateLineModel(rate(it.rate), it.date) },
            today = today,
            stale = stale
        )
    }

    /** [part] of [whole] in whole percent, half up; a part above zero never reads 0 %. */
    private fun percent(part: Long, whole: Long): SharePercent {
        val value = BigDecimal.valueOf(part).multiply(HUNDRED)
            .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
            .toInt()
        return if (value == 0 && part > 0) SharePercent.UnderOne else SharePercent.Whole(value)
    }

    private fun row(
        item: PortfolioItem,
        share: Float,
        percent: SharePercent? = null
    ): InvestmentRowModel {
        val investment = item.history.investment
        val figures = item.figures
        return InvestmentRowModel(
            id = investment.id.value,
            name = investment.name.value,
            description = investment.description?.value,
            iconKey = investment.icon.value,
            dollars = investment.currency == Currency.USD,
            value = figures.value?.let { money.format(it.amount) },
            valuedOn = figures.value?.valuedOn,
            `return` = figures.`return`,
            contributed = money.format(figures.contributed),
            share = share,
            percent = percent
        )
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
        val THOUSAND: BigDecimal = BigDecimal.valueOf(1_000)
        val TEN_THOUSAND: BigDecimal = BigDecimal.valueOf(10_000)
        val HUNDRED_THOUSAND: BigDecimal = BigDecimal.valueOf(100_000)
        val MILLION: BigDecimal = BigDecimal.valueOf(1_000_000)
        val TWO: BigInteger = BigInteger.valueOf(2)
        val FIVE: BigInteger = BigInteger.valueOf(5)
        val MAX_STEPS: BigInteger = BigInteger.valueOf(3)
    }
}
