package com.software.sello.presentation.recurrence

import com.software.sello.domain.model.RepeatRule
import java.time.LocalDate

/**
 * The schedule that recorded the entry being corrected, as the choice names it: its note
 * or category, or for an income its note, Otro's name or [sourceKey].
 */
data class FollowingSchedule(
    val name: String?,
    val rule: RepeatRule,
    val start: LocalDate,
    val sourceKey: String? = null
)
