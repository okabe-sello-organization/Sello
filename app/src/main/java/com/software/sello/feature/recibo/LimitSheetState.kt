package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneyTextValue
import java.time.YearMonth

/** Which months a new limit is for. "Solo" is the default. */
enum class LimitScope { ThisMonth, FromThisMonth }

/** What a shortcut covers: what the category spent, the month's overrun, or reserved payments. */
enum class ChipKind { Spent, CoverMonth, CoverPayments }

/** A limit offered in one tap: drawn as [amount], typed as [pesos]. */
data class LimitChip(val kind: ChipKind, val amount: MoneyTextValue, val pesos: Long)

/** How the month would end with the limit typed: something left, exactly nothing, or short. */
enum class FactOutcome { Left, Exactly, Short }

/**
 * "Tu límite total de octubre pasaría a [total]." and what would be [left], after reserved
 * payments when [afterPayments]. [left] carries no sign; [outcome] says which way it goes.
 */
data class LimitFact(
    val total: MoneyTextValue,
    val left: MoneyTextValue,
    val outcome: FactOutcome,
    val afterPayments: Boolean
)

/** Why the sheet cannot save, or did not. */
enum class LimitSheetError {
    /** Storage refused it and nothing was written. */
    NotSaved,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The category changed since the sheet read it. */
    Conflict,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    CategoryGone,

    /** The category was archived: its limit is changed from its editor once unarchived. */
    Archived,

    /** The month the screen was opened on is over; nothing was written. */
    MonthEnded
}

/**
 * The limit sheet for one category of [month], open while [categoryId] is set. [current]
 * is the month's limit now, null without one; [spent] is what it spent, null until the
 * month is read. [amount] is the draft, [fact] what it would leave.
 */
data class LimitSheetState(
    val month: YearMonth,
    val categoryId: String? = null,
    val name: String = "",
    val iconKey: String = "",
    val spent: MoneyTextValue? = null,
    val current: MoneyTextValue? = null,
    val scope: LimitScope = LimitScope.ThisMonth,
    val amount: MoneyTextValue? = null,
    val chips: List<LimitChip> = emptyList(),
    val chosen: ChipKind? = null,
    val fact: LimitFact? = null,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val canSave: Boolean = false,
    val error: LimitSheetError? = null
)

sealed interface LimitSheetAction {
    data class Open(val categoryId: String) : LimitSheetAction

    data object Close : LimitSheetAction

    data class Scope(val scope: LimitScope) : LimitSheetAction

    data class Digit(val digit: Int) : LimitSheetAction

    data object TripleZero : LimitSheetAction

    data object Backspace : LimitSheetAction

    data object ClearAll : LimitSheetAction

    data class Chip(val kind: ChipKind) : LimitSheetAction

    data object Save : LimitSheetAction

    /** Asks how a save whose outcome was not known ended. Nothing is sent again. */
    data object CheckSaved : LimitSheetAction

    /** Reads the category again, keeping the draft. */
    data object Retry : LimitSheetAction
}
