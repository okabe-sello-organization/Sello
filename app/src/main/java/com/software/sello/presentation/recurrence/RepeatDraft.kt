package com.software.sello.presentation.recurrence

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What Se repite's row and sheet do. */
sealed interface RepeatAction {
    data object Open : RepeatAction

    data class KindPicked(val kind: RepeatKind) : RepeatAction

    data class DayToggled(val day: Int) : RepeatAction

    data object Decrease : RepeatAction

    data object Increase : RepeatAction

    data object NoEnd : RepeatAction

    data object OpenEndPicker : RepeatAction

    data object CloseEndPicker : RepeatAction

    data class EndPicked(val date: LocalDate) : RepeatAction

    /** Keep the sheet's choice: the entry becomes a schedule. */
    data object Done : RepeatAction

    /** Se repite off: the entry is recorded once. */
    data object Remove : RepeatAction

    /** Close the sheet; the choice stays as it was. */
    data object Dismiss : RepeatAction
}

/**
 * Se repite as an entry form shows it. [choice] is the kept choice, or null while the entry
 * is recorded once; [next] is the first date it records from the entry's date. [sheet] is
 * the choice being made in the open sheet, with its own [sheetNext]; it can be kept only
 * when [sheetComplete].
 */
data class RepeatUi(
    val choice: RepeatChoice? = null,
    val next: LocalDate? = null,
    val sheet: RepeatChoice? = null,
    val sheetNext: LocalDate? = null,
    val endPickerOpen: Boolean = false
) {
    val sheetComplete: Boolean get() = sheetNext != null
}

/**
 * Se repite's choice and its open sheet for one entry form, kept in [saved] under [prefix]
 * so rotation or the process being stopped loses neither. The form owns the date: a
 * schedule starts today or later and a single entry is never dated after today, so after
 * [Done][RepeatAction.Done] or [Remove][RepeatAction.Remove] it moves its date to today
 * when the date no longer fits.
 */
class RepeatDraft(private val saved: SavedStateHandle, prefix: String) {
    private val choiceKey = "$prefix.repeat"
    private val sheetKey = "$prefix.repeat.sheet"
    private val endPickerKey = "$prefix.repeat.endPicker"

    /** The kept choice and the sheet's, read back from saved state. */
    val changes: Flow<Pair<RepeatChoice?, RepeatChoice?>> = combine(
        saved.getStateFlow<String?>(choiceKey, null),
        saved.getStateFlow<String?>(sheetKey, null),
        saved.getStateFlow(endPickerKey, false)
    ) { choice, sheet, _ -> choice?.let(RepeatChoice::decode) to sheet?.let(RepeatChoice::decode) }

    val choice: RepeatChoice? get() = saved.get<String>(choiceKey)?.let(RepeatChoice::decode)

    private val sheet: RepeatChoice? get() = saved.get<String>(sheetKey)?.let(RepeatChoice::decode)

    /** Starts from [choice], as a stored schedule has it. */
    fun keep(choice: RepeatChoice) {
        saved[choiceKey] = choice.encode()
    }

    fun ui(date: LocalDate): RepeatUi {
        val choice = choice
        val sheet = sheet
        return RepeatUi(
            choice = choice,
            next = choice?.next(date),
            sheet = sheet,
            sheetNext = sheet?.next(date),
            endPickerOpen = sheet != null && saved.get<Boolean>(endPickerKey) == true
        )
    }

    /** Applies [action] for an entry dated [date]. */
    fun onAction(action: RepeatAction, date: LocalDate) {
        val sheet = sheet
        when (action) {
            RepeatAction.Open -> setSheet(choice ?: RepeatChoice.startingOn(date))

            is RepeatAction.KindPicked -> sheet?.let { setSheet(it.withKind(action.kind)) }

            is RepeatAction.DayToggled -> sheet?.let { setSheet(it.toggleDay(action.day)) }

            RepeatAction.Decrease -> sheet?.let { setSheet(it.decrease()) }

            RepeatAction.Increase -> sheet?.let { setSheet(it.increase()) }

            RepeatAction.NoEnd -> sheet?.let { setSheet(it.withEnd(null)) }

            RepeatAction.OpenEndPicker -> if (sheet != null) saved[endPickerKey] = true

            RepeatAction.CloseEndPicker -> saved[endPickerKey] = false

            is RepeatAction.EndPicked -> if (sheet != null) {
                // An end before the start would leave nothing to record; it is not taken.
                if (!action.date.isBefore(date)) setSheet(sheet.withEnd(action.date))
                saved[endPickerKey] = false
            }

            RepeatAction.Done -> if (sheet != null && sheet.next(date) != null) {
                saved[choiceKey] = sheet.encode()
                close()
            }

            RepeatAction.Remove -> {
                saved[choiceKey] = null
                close()
            }

            RepeatAction.Dismiss -> close()
        }
    }

    private fun setSheet(choice: RepeatChoice) {
        saved[sheetKey] = choice.encode()
    }

    private fun close() {
        saved[sheetKey] = null
        saved[endPickerKey] = false
    }
}
