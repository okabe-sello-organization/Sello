package com.software.sello.data.repository

import com.software.sello.domain.policy.EffectiveDates
import java.time.YearMonth

/** The stored text of the month's first day: the inclusive lower bound of a month read. */
internal val YearMonth.firstDayText: String get() = EffectiveDates.storedText(atDay(1))

/**
 * The stored text of the month's last day: the inclusive upper bound of a month read. Not
 * the next month's first day: after December 9999 that is "+10000-01-01", which sorts
 * before every stored date and would leave the month empty.
 */
internal val YearMonth.lastDayText: String get() = EffectiveDates.storedText(atEndOfMonth())
