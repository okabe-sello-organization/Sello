package com.software.sello.data.mapper

import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.LimitKind
import com.software.sello.data.local.entity.MonthLimitEntity
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** Limits are capped only when a person sets one (ADR 0003): stored ones keep reading. */
class LimitMapperTest {
    private val category = "00000000-0000-4000-8000-000000000001"

    private val thirteenDigits =
        (BudgetLimit.finite(Money.cop(1_000_000_000_000)) as Outcome.Success).value

    @Test
    fun aStoredMonthLimitAboveTheMaximumOneCanSetStillReadsExactly() {
        val row = MonthLimitEntity(category, "2026-10", LimitKind.FINITE, 1_000_000_000_000, "COP")

        val read = (row.toDomain() as Outcome.Success).value

        assertEquals(thirteenDigits, read.limit)
    }

    @Test
    fun aStoredDefaultLimitAboveTheMaximumOneCanSetStillReadsExactly() {
        val row =
            DefaultLimitEntity(category, "2026-10", LimitKind.FINITE, 1_000_000_000_000, "COP")

        val read = (row.toDomain() as Outcome.Success).value

        assertEquals(AutomaticBudget.Limit(thirteenDigits), read.budget)
    }
}
