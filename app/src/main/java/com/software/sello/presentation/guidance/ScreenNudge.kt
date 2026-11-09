package com.software.sello.presentation.guidance

import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.policy.NudgePolicy
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.PreferenceRead
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The nudge a screen shows and its day, and whether that day is already kept as its own. */
data class ScreenNudge(val shown: ShownNudge, val kept: Boolean) {
    val nudge: Nudge get() = shown.nudge

    companion object {
        /**
         * The one of [wanted] to show on [today], as [NudgePolicy.pick] says, or null. Only a
         * guidance read that worked counts: while it is loading or failed, no nudge shows.
         */
        fun pick(guidance: PreferenceRead<Guidance>, today: LocalDate, wanted: List<Nudge>) =
            (guidance as? PreferenceRead.Ready)?.value?.let { read ->
                NudgePolicy.pick(read, today, wanted)?.let {
                    ScreenNudge(ShownNudge(it, today), NudgePolicy.keptFor(read.memory, it, today))
                }
            }
    }
}

/**
 * Keeps the day a screen's nudge showed on, for the one-a-day rule (SELLO-211), once per
 * showing: a nudge already kept for its day is not written again. The same write keeps an
 * expired nudge as seen. A failed write is not retried until something changes; the nudge
 * still shows.
 */
class NudgeDays(private val guidance: GuidancePreferences, private val scope: CoroutineScope) {
    private var keeping: ShownNudge? = null

    /** [shown] is on screen now, or nothing is when null. */
    fun showing(shown: ScreenNudge?) {
        val nudge = shown?.takeUnless { it.kept }?.shown ?: return
        if (keeping == nudge) return
        keeping = nudge
        scope.launch { guidance.markShown(nudge.nudge, nudge.day) }
    }
}
