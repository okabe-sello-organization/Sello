package com.software.sello.domain.model

sealed interface RecordIdError {
    /** Not a canonical identifier. [original] is what was received. */
    data class Malformed(val original: String) : RecordIdError
}

private val canonicalUuid =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/**
 * The one accepted spelling of an identifier: a lower-case hyphenated UUID. A stored
 * or imported identifier in any other form is rejected, never normalized, so the same
 * record cannot exist under two spellings.
 */
private fun canonical(raw: String): Outcome<String, RecordIdError> =
    if (canonicalUuid.matches(raw)) {
        Outcome.Success(raw)
    } else {
        Outcome.Failure(RecordIdError.Malformed(raw))
    }

private inline fun <T> canonical(raw: String, wrap: (String) -> T): Outcome<T, RecordIdError> =
    when (val id = canonical(raw)) {
        is Outcome.Success -> Outcome.Success(wrap(id.value))
        is Outcome.Failure -> id
    }

/** A category's identity. It survives renaming and archiving. */
@ConsistentCopyVisibility
data class CategoryId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<CategoryId, RecordIdError> = canonical(raw, ::CategoryId)
    }
}

@ConsistentCopyVisibility
data class ExpenseId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<ExpenseId, RecordIdError> = canonical(raw, ::ExpenseId)
    }
}

@ConsistentCopyVisibility
data class IncomeId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<IncomeId, RecordIdError> = canonical(raw, ::IncomeId)
    }
}

/** A goal's identity. It survives edits, completion and target changes. */
@ConsistentCopyVisibility
data class GoalId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<GoalId, RecordIdError> = canonical(raw, ::GoalId)
    }
}

/** One contribution or withdrawal of a goal. */
@ConsistentCopyVisibility
data class GoalMovementId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<GoalMovementId, RecordIdError> =
            canonical(raw, ::GoalMovementId)
    }
}

/** An investment's identity. It survives edits and every movement. */
@ConsistentCopyVisibility
data class InvestmentId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<InvestmentId, RecordIdError> = canonical(raw, ::InvestmentId)
    }
}

/** One aporte, retiro, dividend or recorded value of an investment. */
@ConsistentCopyVisibility
data class InvestmentMovementId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<InvestmentMovementId, RecordIdError> =
            canonical(raw, ::InvestmentMovementId)
    }
}

/** One dated USD rate the person entered. Rates are never deleted. */
@ConsistentCopyVisibility
data class UsdRateId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<UsdRateId, RecordIdError> = canonical(raw, ::UsdRateId)
    }
}

/** A recurring payment's or income's identity. It survives changes, pauses and its end. */
@ConsistentCopyVisibility
data class ScheduleId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<ScheduleId, RecordIdError> = canonical(raw, ::ScheduleId)
    }
}

/**
 * The identity of one attempt to change something. The caller chooses it before
 * submitting and keeps it, so the same attempt can be recognized, replayed or looked
 * up afterwards instead of being made twice.
 */
@ConsistentCopyVisibility
data class OperationId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Outcome<OperationId, RecordIdError> = canonical(raw, ::OperationId)
    }
}
