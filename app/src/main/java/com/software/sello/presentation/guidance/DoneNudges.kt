package com.software.sello.presentation.guidance

import com.software.sello.domain.model.Nudge
import com.software.sello.domain.port.GuidancePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Keeps as seen, for good, each nudge whose action [done] says was done anywhere in the
 * app (SELLO-212): an expense deleted, a pago fijo created, carry turned on. It runs for as
 * long as the process. A write that fails leaves the nudge to show again later, as for any
 * nudge; the screens showing it hide it in this process all the same.
 */
class DoneNudges(done: Flow<Set<Nudge>>, guidance: GuidancePreferences, scope: CoroutineScope) {
    init {
        scope.launch {
            var kept = emptySet<Nudge>()
            done.collect { now ->
                (now - kept).forEach { guidance.markSeen(it) }
                kept = now
            }
        }
    }
}
