package com.software.sello.feature.settings

import androidx.compose.runtime.Immutable

/** Why the typed line cannot be saved: it is outside 1.000 to 100.000. */
enum class LineProblem { BelowMinimum, AboveMaximum }

/**
 * Gastos hormiga's form, the Tasa del dólar form without the date: whole pesos on the
 * Sello pad, the line in use under it, and Guardar. [amount] is the draft as the pad wrote
 * it ("15.000"), or null before any key. [current] is the line in use, null until read.
 * [notSaved]: the last save failed and nothing changed.
 */
@Immutable
data class SmallExpenseLineState(
    val loading: Boolean,
    val loadFailed: Boolean,
    val amount: String?,
    val problem: LineProblem?,
    val current: String?,
    val limitPulse: Int = 0,
    val atLimit: Boolean = false,
    val busy: Boolean = false,
    val notSaved: Boolean = false,
    val closed: Boolean = false
) {
    val editable: Boolean get() = !busy && !closed
    val canSubmit: Boolean
        get() = editable && !loading && !loadFailed && amount != null && problem == null
}

sealed interface SmallExpenseLineAction {
    data class Digit(val digit: Int) : SmallExpenseLineAction

    data object TripleZero : SmallExpenseLineAction

    data object Backspace : SmallExpenseLineAction

    data object Clear : SmallExpenseLineAction

    data object Submit : SmallExpenseLineAction

    /** Reintentar after the line in use could not be read. */
    data object Retry : SmallExpenseLineAction

    data object Back : SmallExpenseLineAction
}
