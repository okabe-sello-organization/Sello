package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentReturn
import java.time.LocalDate
import java.time.YearMonth

/** One investment (v2 page, *Investment detail*). */
sealed interface InvestmentDetailState {
    data object Loading : InvestmentDetailState

    /** It could not be read. Never shown as gone or as zero. */
    data object Failed : InvestmentDetailState

    /** It is not there any more (deleted): the screen leaves. */
    data object Gone : InvestmentDetailState

    /**
     * Every figure is in the investment's own currency (rule 10). [value], [gain] and
     * [return] are null when no value is recorded (rule 7). [movements] are newest first;
     * [allMovements] shows them all instead of the first few.
     */
    @Immutable
    data class Ready(
        val id: String,
        val name: String,
        val description: String?,
        val iconKey: String,
        val dollars: Boolean,
        val value: MoneyTextValue?,
        val valuedOn: LocalDate?,
        val gain: MoneyTextValue?,
        val `return`: InvestmentReturn?,
        val contributed: MoneyTextValue,
        val withdrawn: MoneyTextValue,
        val dividends: MoneyTextValue,
        val since: YearMonth,
        val chart: ValuesChartModel?,
        val movements: List<InvestmentMovementModel>,
        val today: LocalDate,
        val allMovements: Boolean = false,
        val selectedValue: String? = null,
        val stale: Boolean = false
    ) : InvestmentDetailState
}

/** A recorded movement as the list shows it: kind, day, note and the signed amount. */
@Immutable
data class InvestmentMovementModel(
    val id: String,
    val kind: InvestmentMovementKind,
    val date: LocalDate,
    val note: String?,
    val amount: MoneyTextValue
)

/** A point of the chart, placed in 0..1 on both axes, and what it stands for. */
@Immutable
data class ChartPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val date: LocalDate,
    val amount: MoneyTextValue
)

/**
 * Recorded values only, as dots ([values]); what was put in (aportes − retiros) as steps
 * ([putIn]); from the month of the first aporte, [start], to [end].
 */
@Immutable
data class ValuesChartModel(
    val values: List<ChartPoint>,
    val putIn: List<ChartPoint>,
    val start: YearMonth,
    val end: YearMonth
)

sealed interface InvestmentDetailAction {
    data object Retry : InvestmentDetailAction

    data object ShowAllMovements : InvestmentDetailAction

    data class SelectValue(val id: String) : InvestmentDetailAction

    /** Handled by the root, which owns navigation. */
    data object Edit : InvestmentDetailAction

    data object RecordValue : InvestmentDetailAction

    data class OpenMovement(val id: String) : InvestmentDetailAction

    /** Varios movimientos: handled by the root. */
    data object RecordSeveral : InvestmentDetailAction
}
