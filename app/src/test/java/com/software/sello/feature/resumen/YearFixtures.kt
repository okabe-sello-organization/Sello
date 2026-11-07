package com.software.sello.feature.resumen

import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.YearCategory
import com.software.sello.domain.model.YearComparison
import com.software.sello.domain.model.YearExpenseRecord
import com.software.sello.domain.model.YearMonthLine
import com.software.sello.domain.model.YearMonthRecord
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearOverRecord
import com.software.sello.domain.model.YearRecords
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/** The mock's 2026 as of 6 November: the figures are given, not worked out here. */
internal fun yearSnapshot(
    year: Int = 2026,
    asOf: String = "2026-11-06",
    firstYear: Int = 2025,
    comparison: Boolean = true,
    empty: Boolean = false,
    records: YearRecords? = null
): YearSnapshot {
    val today = LocalDate.parse(asOf)
    // Both add up to the year's 14.230.000: to 6 November, or a whole finished year.
    // March and July went over.
    val finished = year != today.year
    val spent = listOf(
        1_300_000L, 1_250_000L, 1_840_000L, 1_290_000L, 1_400_000L, 1_300_000L,
        1_620_000L, 1_020_000L, 1_270_000L, 1_470_000L
    ) + if (finished) listOf(270_000L, 200_000L) else listOf(470_000L)
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
            val current = if (finished) 13 else today.monthValue
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
        records = records ?: if (empty) YearRecords.TooFew else mockRecords(year),
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

/**
 * The mock's records: March 1.840.000 the dearest, August 1.020.000 the calmest, the
 * 1.200.000 Matrícula of 3 February the biggest, Entretenimiento over in four months.
 */
internal fun mockRecords(year: Int = 2026, mostOver: Boolean = true, note: String? = "Matrícula") =
    YearRecords.Found(
        dearest = YearMonthRecord(YearMonth.of(year, 3), Money.cop(1_840_000)),
        calmest = YearMonthRecord(YearMonth.of(year, 8), Money.cop(1_020_000)),
        biggest = YearExpenseRecord(
            storedExpense(50, 1, "$year-02-03", 1_200_000, note),
            categoryIn(1, "Hogar", null).category
        ),
        mostOver = if (mostOver) {
            YearOverRecord(categoryIn(4, "Entretenimiento", null).category, 4)
        } else {
            null
        }
    )
