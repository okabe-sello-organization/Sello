package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ComparisonBar
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.ResourceMoneyLabels
import java.time.DayOfWeek

internal fun resumenExample(money: MoneyFormatter): ResumenState.Ready {
    fun point(
        id: String,
        label: String,
        pesos: Long,
        day: Long? = null,
        current: Boolean = false
    ): ResumenPoint {
        val amount = money.format(Money.cop(pesos))
        return ResumenPoint(
            ChartPoint(
                id,
                label,
                pesos,
                "$label, ${amount.spoken}",
                if (current) ChartPeriod.Current else ChartPeriod.Past
            ),
            label,
            amount,
            day?.let { money.format(Money.cop(it)) },
            current
        )
    }
    val weekdays = listOf(18_200L, 21_500L, 19_800L, 24_100L, 31_400L, 42_700L, 27_300L)
    val dayNames =
        listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos")
    return ResumenState.Ready(
        month = "2026-10",
        hero = ResumenHero(
            inProgress = true,
            monthName = "octubre",
            spent = money.format(Money.cop(30_000)),
            throughDay = 3,
            comparison = ResumenComparison(
                previousName = "septiembre",
                difference = money.format(Money.cop(5_000)),
                direction = SpendingDirection.Less,
                percent = "14,3",
                throughDay = 3,
                previousThroughDay = 3,
                bars = listOf(
                    ComparisonBar("Octubre", money.format(Money.cop(30_000)), 30f / 35f, true),
                    ComparisonBar("Septiembre", money.format(Money.cop(35_000)), 1f, false)
                )
            )
        ),
        income = money.format(Money.cop(150_000)),
        spent = money.format(Money.cop(30_000)), netFlow = money.format(Money.cop(120_000)),
        incomeSlip = ResumenIncome(
            count = 2,
            destinations = listOf(
                ResumenDestination(
                    "00000000-0000-4000-8000-000000000001",
                    "Comida",
                    "restaurant",
                    money.format(Money.cop(-30_000))
                )
            ),
            others = null,
            spentShare = 0.2f,
            overspent = false
        ),
        history = listOf(
            point("2026-09", "sep", 20_000, 80_000),
            point("2026-10", "oct", 30_000, 100_000, current = true)
        ),
        monthLimit = ResumenLimit(100_000, money.format(Money.cop(100_000))),
        normalMonth = ResumenNormalMonth(money.format(Money.cop(20_000)), 1),
        categories = listOf(
            ResumenCategory(
                "00000000-0000-4000-8000-000000000001",
                "Comida",
                "restaurant",
                money.format(Money.cop(30_000)),
                1f
            )
        ),
        weekdays = ResumenWeekdays(
            points = weekdays.mapIndexed { index, pesos ->
                val amount = money.format(Money.cop(pesos))
                ResumenPoint(
                    ChartPoint(
                        DayOfWeek.of(index + 1).name,
                        "LMXJVSD"[index].toString(),
                        pesos,
                        "${dayNames[index]}, ${amount.spoken}",
                        if (index == 5) ChartPeriod.Current else ChartPeriod.Past
                    ),
                    dayNames[index],
                    amount
                )
            },
            busiest = "sábados",
            busiestAverage = money.format(Money.cop(42_700)),
            from = "1 de julio",
            lastSixMonths = true
        ),
        suggestion = ResumenSuggestion(
            "00000000-0000-4000-8000-000000000001",
            "Comida",
            listOf("agosto", "septiembre"),
            money.format(Money.cop(74_000)),
            money.format(Money.cop(50_000))
        ),
        empty = false, insufficientHistory = false, stale = false, asOf = "3 de octubre"
    )
}

@Composable
private fun SummaryPreview(ink: SelloInk, dark: Boolean, stale: Boolean = false) {
    val money = MoneyFormatter(ResourceMoneyLabels(LocalResources.current))
    SelloTheme(ink = ink, darkTheme = dark, reducedMotion = true) {
        ResumenScreen(resumenExample(money).copy(stale = stale), {}, {}, {}, {}, PaddingValues())
    }
}

@Preview(name = "Cobalto claro", locale = "es", showBackground = true)
@Preview(name = "Fuente 2", locale = "es", fontScale = 2f)
@Composable
private fun CobaltoLightResumen() = SummaryPreview(SelloInk.Cobalto, false)

@Preview(name = "Cobalto oscuro", locale = "es")
@Composable
private fun CobaltoDarkResumen() = SummaryPreview(SelloInk.Cobalto, true)

@Preview(name = "Violeta claro ancho", locale = "es", widthDp = 840, heightDp = 900)
@Composable
private fun VioletaLightResumen() = SummaryPreview(SelloInk.Violeta, false)

@Preview(name = "Violeta oscuro, lectura anterior", locale = "es")
@Composable
private fun VioletaDarkResumen() = SummaryPreview(SelloInk.Violeta, true, stale = true)
