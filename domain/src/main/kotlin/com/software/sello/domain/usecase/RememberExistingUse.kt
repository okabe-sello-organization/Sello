package com.software.sello.domain.usecase

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.Resets
import kotlinx.coroutines.flow.first

/** What one [RememberExistingUse.run] did. */
enum class ExistingUseCheck {
    /** It was checked before: nothing was read or written. */
    AlreadyChecked,

    /** Checked now, and remembered. */
    Checked,

    /**
     * The guidance or the history could not be read, or the answer could not be saved:
     * nothing changed, and the next start asks again. New-user nudges stay hidden until then.
     */
    NotNow
}

/**
 * The first time a version with guidance starts (E38): someone with any expense, income,
 * goal or investment was already using Sello, so the new-user nudges are marked seen. It
 * counts with [Resets.preview], the read that counts the whole history in one
 * transaction. Run at every start; it does its work once. A death at any point leaves
 * either nothing written, so the next start checks again, or the whole answer.
 */
class RememberExistingUse(
    private val resets: Resets,
    private val guidance: GuidancePreferences
) {
    suspend fun run(): ExistingUseCheck {
        val read = guidance.observeGuidance().first()
        if (read !is PreferenceRead.Ready) return ExistingUseCheck.NotNow
        if (read.value.existingUseChecked) return ExistingUseCheck.AlreadyChecked
        val counts = when (val preview = resets.preview()) {
            is Outcome.Success -> preview.value.current
            is Outcome.Failure -> return ExistingUseCheck.NotNow
        }
        val wasUsingSello =
            counts.expenses > 0 || counts.incomes > 0 || counts.goals > 0 || counts.investments > 0
        return when (guidance.rememberExistingUse(wasUsingSello)) {
            PreferenceWrite.Saved -> ExistingUseCheck.Checked
            is PreferenceWrite.Failed -> ExistingUseCheck.NotNow
        }
    }
}
