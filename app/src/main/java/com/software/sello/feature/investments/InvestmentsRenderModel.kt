package com.software.sello.feature.investments

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.model.PortfolioItem
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate

/**
 * Turns the investment board into what Inversiones draws. Every figure comes from
 * `InvestmentPolicy` through the board; nothing is added, converted or compared here.
 */
class InvestmentsRenderModel(private val money: MoneyFormatter) {
    fun overview(board: InvestmentBoardState, today: LocalDate): InversionesState = when (board) {
        InvestmentBoardState.Loading -> InversionesState.Loading

        is InvestmentBoardState.Ready -> overview(board.board, today, stale = false)

        is InvestmentBoardState.Failed ->
            board.lastGood?.let { overview(it, today, stale = true) } ?: InversionesState.Failed
    }

    /** The rate as the app writes it: "4.150,25". */
    fun rate(rate: UsdRate): String = money.decimal(rate.hundredths, Currency.USD.scale)

    private fun overview(
        board: InvestmentBoard,
        today: LocalDate,
        stale: Boolean
    ): InversionesState {
        val overview = when (val worked = board.overview) {
            is Outcome.Success -> worked.value

            // Never guessed at another rate, never left out: the rows still show.
            is Outcome.Failure -> when (worked.error) {
                PortfolioError.MissingRate -> return InversionesState.Ready(
                    total = null,
                    rows = board.investments.map { summary ->
                        row(PortfolioItem(summary.history, summary.figures, null), share = 0f)
                    },
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
            rows = overview.items.map { item ->
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
