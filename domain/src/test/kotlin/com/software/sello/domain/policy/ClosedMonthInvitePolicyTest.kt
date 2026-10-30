package com.software.sello.domain.policy

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ADR 0017 (E16): days 1 to 7 of the current month, never a past month. */
class ClosedMonthInvitePolicyTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun theFirstWeekInvitesToThePreviousMonth() {
        listOf(1, 2, 7).forEach { day ->
            assertEquals(
                YearMonth.of(2026, 9),
                ClosedMonthInvitePolicy.monthToReview(LocalDate.of(2026, 10, day), october)
            )
        }
    }

    @Test
    fun fromDayEightThereIsNone() {
        listOf(8, 9, 31).forEach { day ->
            assertNull(
                ClosedMonthInvitePolicy.monthToReview(LocalDate.of(2026, 10, day), october)
            )
        }
    }

    @Test
    fun aPastMonthOnScreenHasNone() {
        assertNull(
            ClosedMonthInvitePolicy.monthToReview(
                LocalDate.of(2026, 10, 2),
                YearMonth.of(2026, 9)
            )
        )
    }

    @Test
    fun januaryInvitesToDecemberOfTheYearBefore() {
        assertEquals(
            YearMonth.of(2025, 12),
            ClosedMonthInvitePolicy.monthToReview(
                LocalDate.of(2026, 1, 3),
                YearMonth.of(2026, 1)
            )
        )
    }
}
