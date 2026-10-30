package com.software.sello.presentation.corrections

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * What the frame shows about the last correction, and its one action. It holds
 * nothing itself: the notice and the time to undo live in [Corrections], which
 * outlives every screen, so a screen drawn again shows the same offer with the same
 * time left.
 */
class CorrectionsViewModel(private val corrections: Corrections) : ViewModel() {
    val notice: StateFlow<CorrectionNotice?> = corrections.notice

    fun undo() = corrections.undo()

    /** A list of later months waiting to be confirmed before a deletion, or its undoing. */
    val question: StateFlow<CorrectionQuestion?> = corrections.question

    fun confirmQuestion() = corrections.confirmQuestion()

    fun dismissQuestion() = corrections.dismissQuestion()

    /** Whether a celebration's stamps come down now: once per save. */
    fun lands(celebration: GoalCelebration): Boolean = corrections.lands(celebration)

    /** The app is back in view: time passed that waiting alone did not count. */
    fun returned() = corrections.refresh()
}
