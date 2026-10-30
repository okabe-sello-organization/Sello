package com.software.sello.domain.policy

import java.time.LocalDate
import java.time.YearMonth

/** ADR 0017 (E16): Recibo invites a look at the month just closed during the first week. */
object ClosedMonthInvitePolicy {
    const val LAST_DAY = 7

    /**
     * The month to invite a look at, or null: only on financial days 1 to [LAST_DAY] of
     * the current month, and only while that current month is the one on screen.
     */
    fun monthToReview(today: LocalDate, shown: YearMonth): YearMonth? {
        val current = YearMonth.from(today)
        return current.minusMonths(1).takeIf {
            shown == current && today.dayOfMonth <= LAST_DAY
        }
    }
}
