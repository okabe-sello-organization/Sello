package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Money
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.ResourceMoneyLabels

/** The answer slip in each state of the v2 Recibo, with one synthetic category. */
private enum class Answer { Today, Over, NoLimit, Closed }

private fun reciboExample(money: MoneyFormatter, answer: Answer): ReciboState.Month {
    fun cop(pesos: Long) = money.format(Money.cop(pesos))
    val summary = when (answer) {
        // 1.000.000 less 594.400 over 16 days, down to COP 50.
        Answer.Today -> ReciboSummary(
            ReciboAnswerLabel.Remaining,
            cop(405_600),
            ReciboStamp.OnTrack,
            perDay = ReciboPerDay(cop(25_350), 16)
        )

        Answer.Over -> ReciboSummary(
            ReciboAnswerLabel.Remaining,
            cop(-43_700),
            ReciboStamp.Over,
            perDay = ReciboPerDay(cop(0), 1)
        )

        Answer.NoLimit -> ReciboSummary(ReciboAnswerLabel.Spent, cop(600_000), ReciboStamp.NoLimit)

        Answer.Closed -> ReciboSummary(
            ReciboAnswerLabel.Remaining,
            cop(80_000),
            ReciboStamp.WithinLimit,
            closed = ReciboClosedMonth(cop(920_000), cop(1_000_000))
        )
    }
    val food = ReciboCategory(
        id = "00000000-0000-4000-8000-000000000001",
        name = "Comida",
        iconKey = "restaurant",
        archived = false,
        figure = BudgetFigure(ReciboHeadline.Remaining, cop(120_000), ReciboStamp.OnTrack),
        cellAmount = cop(120_000),
        spent = cop(180_000),
        limit = cop(300_000),
        fraction = 0.6f,
        over = false,
        expenseCount = 6
    )
    return ReciboState.Month(summary, listOf(food), stale = false)
}

@Composable
private fun AnswerPreview(answer: Answer, ink: SelloInk = SelloInk.Cobalto, dark: Boolean = false) {
    val money = MoneyFormatter(ResourceMoneyLabels(LocalResources.current))
    SelloTheme(ink = ink, darkTheme = dark, reducedMotion = true) {
        ReciboScreen(reciboExample(money, answer), {}, PaddingValues(12.dp))
    }
}

@Preview(name = "Hoy", locale = "es", showBackground = true)
@Composable
private fun TodayRecibo() = AnswerPreview(Answer.Today)

@Preview(name = "Excedido, último día", locale = "es", showBackground = true)
@Composable
private fun OverRecibo() = AnswerPreview(Answer.Over)

@Preview(name = "Sin límite, oscuro", locale = "es")
@Composable
private fun NoLimitRecibo() = AnswerPreview(Answer.NoLimit, SelloInk.Violeta, dark = true)

@Preview(name = "Mes cerrado", locale = "es", showBackground = true)
@Composable
private fun ClosedRecibo() = AnswerPreview(Answer.Closed)
