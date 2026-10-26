package com.software.sello.feature.investments

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.PortfolioItem
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.policy.InvestmentOrderPolicy
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.presentation.money.MoneyFormatter
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
        InvestmentBoardState.Loading -> InversionesState.Loading

        is InvestmentBoardState.Ready -> overview(board.board, today, order, stale = false)

        is InvestmentBoardState.Failed -> board.lastGood?.let {
            overview(it, today, order, stale = true)
        } ?: InversionesState.Failed
    }

    /** One investment's detail from the board, or why it cannot be shown. */
    fun detail(board: InvestmentBoardState, id: String, today: LocalDate): InvestmentDetailState =
        when (board) {
            InvestmentBoardState.Loading -> InvestmentDetailState.Loading

            is InvestmentBoardState.Ready -> detail(board.board, id, today, stale = false)

            is InvestmentBoardState.Failed -> board.lastGood?.let {
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
        val newestFirst = summary.history.movements.sortedWith(
            compareByDescending<InvestmentMovement> { it.date }.thenByDescending { it.sequence }
        )
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
            chart = chart(summary.history, today),
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
                    }
                )
            },
            today = today,
            stale = stale
        )
    }

    /**
     * Recorded values as points and what was put in (aportes − retiros, in replay order)
     * as steps, both scaled to the largest of them; time runs from the first aporte to
     * today. Coordinates are drawing positions, never money.
     */
    private fun chart(history: InvestmentHistory, today: LocalDate): ValuesChartModel? {
        val ordered = history.movements.sortedWith(
            compareBy<InvestmentMovement> { it.date }.thenBy { it.sequence }
        )
        val first = ordered.firstOrNull { it.kind == InvestmentMovementKind.Contribution }?.date
            ?: return null
        val end = maxOf(today, ordered.last().date)
        val span = (end.toEpochDay() - first.toEpochDay()).coerceAtLeast(1).toFloat()
        var put = 0L
        val steps = mutableListOf<Pair<InvestmentMovement, Long>>()
        ordered.forEach { movement ->
            val units = movement.amount.money.minorUnits
            when (movement.kind) {
                InvestmentMovementKind.Contribution -> put += units
                InvestmentMovementKind.Withdrawal -> put -= units
                else -> return@forEach
            }
            steps += movement to put
        }
        val values = ordered.filter { it.kind == InvestmentMovementKind.Valuation }
        val top = (values.map { it.amount.money.minorUnits } + steps.map { it.second })
            .maxOrNull()?.coerceAtLeast(1)?.toFloat() ?: 1f
        val currency = history.investment.currency
        fun point(id: String, date: LocalDate, units: Long) = ChartPoint(
            id = id,
            x = (date.toEpochDay() - first.toEpochDay()) / span,
            y = units / top,
            date = date,
            amount = money.format(Money(units, currency))
        )
        return ValuesChartModel(
            values = values.map { point(it.id.value, it.date, it.amount.money.minorUnits) },
            putIn = steps.map { (movement, level) ->
                point(movement.id.value, movement.date, level)
            },
            start = YearMonth.from(first),
            end = YearMonth.from(end)
        )
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
                val share = item.valueInPesos?.minorUnits?.let { value ->
                    pesos?.let { (value.toDouble() / it).toFloat() }
                } ?: 0f
                row(item, share)
            },
            rate = overview.rate?.let { RateLineModel(rate(it.rate), it.date) },
            today = today,
            stale = stale
        )
    }

    private fun row(item: PortfolioItem, share: Float): InvestmentRowModel {
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
            share = share
        )
    }
}
