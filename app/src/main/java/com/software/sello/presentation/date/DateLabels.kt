package com.software.sello.presentation.date

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How a day is written on a slip: "Hoy, vie 9 oct", "jue 8 oct", and with the year
 * when it is not this year's: "mié 31 dic 2025". Names come from resources.
 */
@Immutable
class DateLabels(
    private val weekdays: List<String>,
    private val months: List<String>,
    private val todayFormat: String
) {
    fun day(date: LocalDate, today: LocalDate): String {
        val weekday = weekdays[date.dayOfWeek.value - 1]
        val month = months[date.monthValue - 1]
        val plain = if (date.year == today.year) {
            "$weekday ${date.dayOfMonth} $month"
        } else {
            "$weekday ${date.dayOfMonth} $month ${date.year}"
        }
        return if (date == today) todayFormat.format(plain) else plain
    }

    /** "30 sep", and "31 dic 2025" when it is not this year's. */
    fun shortDay(date: LocalDate, today: LocalDate): String {
        val plain = "${date.dayOfMonth} ${months[date.monthValue - 1]}"
        return if (date.year == today.year) plain else "$plain ${date.year}"
    }

    /** "23 oct 2026", always with its year, as on a lograda plate. */
    fun dayMonthYear(date: LocalDate): String =
        "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"

    /** "dic 2026", as a goal's target month. */
    fun monthYear(month: YearMonth): String = "${months[month.monthValue - 1]} ${month.year}"

    /** "may 27", as under a stamp the plan projects. */
    fun monthShortYear(month: YearMonth): String =
        "${months[month.monthValue - 1]} ${(month.year % 100).toString().padStart(2, '0')}"

    /** A real moment in [zone], always with its year: "22 oct 2026, 2:30 p. m.". */
    fun moment(at: Instant, zone: ZoneId, format: String): String {
        val local = at.atZone(zone)
        val day = "${local.dayOfMonth} ${months[local.monthValue - 1]} ${local.year}"
        return format.format(day, clock.format(local))
    }

    private companion object {
        val clock: DateTimeFormatter =
            DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"))
    }
}

@Composable
fun dateLabels() = DateLabels(
    weekdays = stringArrayResource(R.array.weekday_names_short).toList(),
    months = stringArrayResource(R.array.month_names_abbreviated).toList(),
    todayFormat = stringResource(R.string.date_today)
)
