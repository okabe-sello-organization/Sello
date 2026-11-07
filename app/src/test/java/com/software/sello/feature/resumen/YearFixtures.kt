package com.software.sello.feature.resumen

import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.YearCategory
import com.software.sello.domain.model.YearComparison
import com.software.sello.domain.model.YearMonthLine
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.feature.expense.categoryIn
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/** The mock's 2026 as of 6 November: the figures are given, not worked out here. */
internal fun yearSnapshot(
    year: Int = 2026,
    asOf: String = "2026-11-06",
    firstYear: Int = 2025,
    comparison: Boolean = true,
    empty: Boolean = false
): YearSnapshot {
    val today = LocalDate.parse(asOf)
    // January to November 2026, then December of a finished year; March and July went over.
    val spent = listOf(
        1_300_000L, 1_250_000L, 1_840_000L, 1_290_000L, 1_400_000L, 1_300_000L,
        1_620_000L, 1_020_000L, 1_270_000L, 1_470_000L, 470_000L, 1_200_000L
    )
    val over = setOf(3, 7)
    val categories = listOf(
        Triple("Hogar", 6_600_000L, 46),
        Triple("Alimentación", 3_910_000L, 27),
        Triple("Transporte", 1_420_000L, 10),
        Triple("Entretenimiento", 1_150_000L, 8),
        Triple("Café", 620_000L, 4),
        Triple("Salud", 200_000L, 1),
        Triple("Regalos", 150_000L, 1),
        Triple("Mascotas", 100_000L, 1),
        Triple("Viejo", 80_000L, 1)
    )
    val icons = listOf(
        "home", "restaurant", "directions_bus", "theaters", "local_cafe", "home", "home",
        "home", "home"
    )
    return YearSnapshot(
        year = year,
        asOf = today,
        generation = 1,
        revision = 7,
        firstYear = firstYear,
        spent = Money.cop(if (empty) 0 else 14_230_000),
        income = Money.cop(if (empty) 0 else 31_500_000),
        netFlow = Money.cop(if (empty) 0 else 17_270_000),
        incomeCount = if (empty) 0 else 12,
        expenseCount = if (empty) 0 else 180,
        months = (1..12).map { number ->
            val month = YearMonth.of(year, number)
            // A finished year has no month in progress.
            val current = if (year == today.year) today.monthValue else 13
            when {
                number > current -> YearMonthLine(month, null, YearMonthStatus.Upcoming)

                number == current -> YearMonthLine(
                    month,
                    Money.cop(if (empty) 0 else spent[number - 1]),
                    YearMonthStatus.InProgress
                )

                empty -> YearMonthLine(month, Money.cop(0), YearMonthStatus.Finished(null))

                else -> YearMonthLine(
                    month,
                    Money.cop(spent[number - 1]),
                    YearMonthStatus.Finished(
                        if (number in over) PastVerdict.Over else PastVerdict.WithinLimit
                    )
                )
            }
        },
        comparison = if (comparison && !empty) {
            YearComparison(
                previousYear = year - 1,
                spent = Money.cop(14_230_000),
                previousSpent = Money.cop(15_350_000),
                through = today,
                previousThrough = today.minusYears(1),
                wholeYears = false,
                difference = Money.cop(1_120_000),
                direction = SpendingDirection.Less,
                percentTenths = BigInteger.valueOf(73)
            )
        } else {
            null
        },
        categories = if (empty) {
            emptyList()
        } else {
            categories.mapIndexed { index, (name, amount, percent) ->
                YearCategory(
                    categoryIn(index + 1, name, null, icon = icons[index]).category,
                    Money.cop(amount),
                    percent
                )
            }
        }
    )
}
