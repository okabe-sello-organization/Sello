package com.software.sello.presentation

import com.software.sello.presentation.date.DateLabels
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateLabelsTest {
    private val labels = DateLabels(
        weekdays = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom"),
        months = listOf(
            "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
        ),
        todayFormat = "Hoy, %1\$s"
    )
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun todayIsNamedAsSuchWithItsWeekday() {
        // 9 October 2026 is a Friday.
        assertEquals("Hoy, vie 9 oct", labels.day(today, today))
    }

    @Test
    fun anotherDayThisYearHasNoYear() {
        assertEquals("jue 8 oct", labels.day(LocalDate.of(2026, 10, 8), today))
        assertEquals("jue 1 ene", labels.day(LocalDate.of(2026, 1, 1), today))
        // 29 February 2024 was a Thursday; from 2026 it carries its year.
        assertEquals("jue 29 feb 2024", labels.day(LocalDate.of(2024, 2, 29), today))
    }

    @Test
    fun aDayInAnotherYearSaysWhichYear() {
        // 31 December 2025 was a Wednesday.
        assertEquals("mié 31 dic 2025", labels.day(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun everyWeekdayAndMonthUsesItsOwnName() {
        // The week of Monday 5 October 2026.
        assertEquals(
            listOf("lun 5 oct", "mar 6 oct", "mié 7 oct", "jue 8 oct", "sáb 10 oct", "dom 11 oct"),
            listOf(5, 6, 7, 8, 10, 11).map { labels.day(LocalDate.of(2026, 10, it), today) }
        )
        assertEquals("dom 15 mar", labels.day(LocalDate.of(2026, 3, 15), today))
    }

    @Test
    fun aShortDayIsDayAndMonthWithTheYearOnlyWhenItIsAnotherYears() {
        assertEquals("30 sep", labels.shortDay(LocalDate.of(2026, 9, 30), today))
        assertEquals("9 oct", labels.shortDay(today, today))
        assertEquals("31 dic 2025", labels.shortDay(LocalDate.of(2025, 12, 31), today))
    }

    @Test
    fun aMonthOfAYearIsItsShortNameAndTheYear() {
        assertEquals("dic 2026", labels.monthYear(java.time.YearMonth.of(2026, 12)))
        assertEquals("ene 2027", labels.monthYear(java.time.YearMonth.of(2027, 1)))
    }

    @Test
    fun aProjectedMonthIsItsShortNameAndTwoDigitYear() {
        assertEquals("may 27", labels.monthShortYear(java.time.YearMonth.of(2027, 5)))
        assertEquals("dic 28", labels.monthShortYear(java.time.YearMonth.of(2028, 12)))
        assertEquals("ene 07", labels.monthShortYear(java.time.YearMonth.of(2107, 1)))
    }
}
