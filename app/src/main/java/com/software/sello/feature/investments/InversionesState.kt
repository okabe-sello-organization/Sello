package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.InvestmentReturn
import java.time.LocalDate
import java.time.YearMonth

/** Inversiones (docs/design/v2/inversiones.html; docs/testing/investment-examples.md). */
sealed interface InversionesState {
    data object Loading : InversionesState

    /** The investments could not be read or worked out. Never shown as none or as zero. */
    data object Failed : InversionesState

    /**
     * [rows] largest value in pesos first, then those without a value. With no investment
     * at all [rows] is empty: the true empty state. [total] is null when none has a value
     * (IN35) or when a USD value has no rate ([missingRate]). [stale] says the last read
     * failed and this is the board as last read.
     */
    @Immutable
    data class Ready(
        val total: PortfolioTotalModel?,
        val rows: List<InvestmentRowModel>,
        val rate: RateLineModel?,
        val today: LocalDate,
        val missingRate: Boolean = false,
        val stale: Boolean = false
    ) : InversionesState {
        val empty: Boolean get() = rows.isEmpty()
    }
}

/** The answer slip: value, Gana / Pierde, the signed return, the breakdown and the dates. */
@Immutable
data class PortfolioTotalModel(
    val value: MoneyTextValue,
    val gain: MoneyTextValue,
    val `return`: InvestmentReturn,
    val contributed: MoneyTextValue,
    val withdrawn: MoneyTextValue,
    val dividends: MoneyTextValue,
    val since: YearMonth,
    val valuedFrom: LocalDate,
    val valuedTo: LocalDate
)

/**
 * One row. [value] and [valuedOn] are null without a recorded value, and then
 * [contributed] says what was put in. [share] is its part of the bar, 0 without a value,
 * and [percent] the same part as written, null without a value or a total. Amounts are in
 * the investment's own currency.
 */
@Immutable
data class InvestmentRowModel(
    val id: String,
    val name: String,
    val description: String?,
    val iconKey: String,
    val dollars: Boolean,
    val value: MoneyTextValue?,
    val valuedOn: LocalDate?,
    val `return`: InvestmentReturn?,
    val contributed: MoneyTextValue,
    val share: Float,
    val percent: SharePercent? = null
)

/** A row's part of the total in whole percent, half up; above zero but under 1 % is "<1 %". */
@Immutable
sealed interface SharePercent {
    data class Whole(val value: Int) : SharePercent

    data object UnderOne : SharePercent
}

/** "Dólar a 4.150,25 · tasa del 20 oct": the rate the total used. */
@Immutable
data class RateLineModel(val rate: String, val date: LocalDate)

sealed interface InversionesAction {
    data object Retry : InversionesAction

    data class Open(val id: String) : InversionesAction
}
