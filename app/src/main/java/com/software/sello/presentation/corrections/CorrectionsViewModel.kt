package com.software.sello.presentation.corrections

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * What the frame shows about the last correction, and its one action. It holds
 * nothing itself: the notice and the time to undo live in [ExpenseCorrections], which
 * outlives every screen, so a screen drawn again shows the same offer with the same
 * time left.
 */
class CorrectionsViewModel(private val corrections: ExpenseCorrections) : ViewModel() {
    val notice: StateFlow<CorrectionNotice?> = corrections.notice

    fun undo() = corrections.undo()

    /** Whether a celebration's stamps come down now: once per save. */
    fun lands(celebration: GoalCelebration): Boolean = corrections.lands(celebration)

    /** The app is back in view: time passed that waiting alone did not count. */
    fun returned() = corrections.refresh()
}
