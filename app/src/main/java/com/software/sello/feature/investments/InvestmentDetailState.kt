package com.software.sello.feature.investments

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentReturn
import java.time.LocalDate
import java.time.YearMonth

/** One investment (docs/design/system/features/investments.html#screen-detail). */
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
        /** The investments nudge under the answer, once (SELLO-213). */
        val nudge: Boolean = false,
        val allMovements: Boolean = false,
        /** The value the line above the chart reads; null reads the newest. */
        val selectedValue: String? = null,
        val stale: Boolean = false,
        /** The help sheet open over the page, if any (SELLO-209). */
        val help: InvestmentHelp? = null
    ) : InvestmentDetailState
}

/**
 * A recorded movement as the list shows it: kind, day, note, the signed amount and the
 * [figure] under it (IN37–IN40), null when the history could not be worked out.
 */
@Immutable
data class InvestmentMovementModel(
    val id: String,
    val kind: InvestmentMovementKind,
    val date: LocalDate,
    val note: String?,
    val amount: MoneyTextValue,
    val figure: MovementFigure? = null
)

/** What a movement's row says under its amount. */
@Immutable
sealed interface MovementFigure {
    /** A recorded value: the gain at it, "ganancia +228.000", "pérdida −100.000" or "sin ganancia". */
    data class Gain(val direction: GainDirection, val amount: MoneyTextValue) : MovementFigure

    /** An aporte or retiro: what was put in so far, "pusiste 3.000.000". */
    data class PutIn(val amount: MoneyTextValue) : MovementFigure

    /** A dividend: the dividends so far, "dividendos 178.000". */
    data class Dividends(val amount: MoneyTextValue) : MovementFigure
}

/**
 * A point of the chart, placed in 0..1 on both axes, and what it stands for. A recorded
 * value also carries what was put in by then, [putIn], for the line above the chart.
 */
@Immutable
data class ChartPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val date: LocalDate,
    val amount: MoneyTextValue,
    val putIn: MoneyTextValue? = null
)

/** A gridline at [y] in 0..1, labelled short: "2,5 M", "800 mil", "500". */
@Immutable
data class AxisMark(val y: Float, val figure: String, val unit: AxisUnit)

enum class AxisUnit { One, Thousands, Millions }

/**
 * Recorded values only, as dots ([values]); what was put in (aportes − retiros) as steps
 * ([putIn]); [grid] marks round amounts across them; from the month of the first aporte,
 * [start], through [middle] to [end].
 */
@Immutable
data class ValuesChartModel(
    val values: List<ChartPoint>,
    val putIn: List<ChartPoint>,
    val start: YearMonth,
    val end: YearMonth,
    val middle: YearMonth = start,
    val grid: List<AxisMark> = emptyList()
)

sealed interface InvestmentDetailAction {
    data object Retry : InvestmentDetailAction

    /** The investments nudge's ✕: closes it for good. */
    data object CloseNudge : InvestmentDetailAction

    data object ShowAllMovements : InvestmentDetailAction

    data class SelectValue(val id: String) : InvestmentDetailAction

    /** Handled by the root, which owns navigation. */
    data object Edit : InvestmentDetailAction

    data object RecordValue : InvestmentDetailAction

    data class OpenMovement(val id: String) : InvestmentDetailAction

    /** A **?** or an underlined word was tapped: its sheet opens. */
    data class ShowHelp(val help: InvestmentHelp) : InvestmentDetailAction

    /** Back or the scrim closed the sheet. */
    data object CloseHelp : InvestmentDetailAction

    /** "Ver más en la Guía de Sello": the sheet closes and the root opens the Guía. */
    data object OpenGuide : InvestmentDetailAction
}
