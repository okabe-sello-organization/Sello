package com.software.sello.domain.model

import java.text.Normalizer
import java.util.Locale

sealed interface TextError {
    /** Nothing but whitespace where a name is required. */
    data object Required : TextError

    data class TooLong(val maxCodePoints: Int, val actualCodePoints: Int) : TextError

    data object ControlCharacter : TextError
}

/**
 * The shared rule for user-written text: trim the ends, store accents composed (NFC)
 * so the same word always counts and compares the same, and count Unicode code points.
 * Over-long or control-character text is rejected, never cut or cleaned.
 */
private fun boundedText(raw: String, maxCodePoints: Int): Outcome<String, TextError> {
    val text = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
    val codePoints = text.codePointCount(0, text.length)
    return when {
        text.isEmpty() -> Outcome.Failure(TextError.Required)
        text.codePoints().anyMatch(::isControl) -> Outcome.Failure(TextError.ControlCharacter)
        codePoints > maxCodePoints -> Outcome.Failure(TextError.TooLong(maxCodePoints, codePoints))
        else -> Outcome.Success(text)
    }
}

private fun isControl(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
    Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
    else -> false
}

private val combiningMarks = Regex("\\p{M}+")
private val whitespaceRuns = Regex("[\\s\\p{Z}]+")

/** [text] with case, accents, compatibility forms and repeated spaces folded away. */
private fun foldedKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
    .replace(combiningMarks, "")
    .uppercase(Locale.ROOT)
    .lowercase(Locale.ROOT)
    .replace(whitespaceRuns, " ")

/** A category's displayed name: trimmed, 1–24 Unicode code points, accents preserved. */
@ConsistentCopyVisibility
data class CategoryName private constructor(val value: String) {
    /**
     * What two names are compared by: case, accents, compatibility forms and repeated
     * spaces are ignored. Storage, commands and backup validation must compare this
     * key, including against archived categories. It is never shown.
     */
    val uniquenessKey: String
        get() = foldedKey(value)

    companion object {
        const val MAX_CODE_POINTS = 24

        fun of(raw: String): Outcome<CategoryName, TextError> =
            when (val text = boundedText(raw, MAX_CODE_POINTS)) {
                is Outcome.Success -> Outcome.Success(CategoryName(text.value))
                is Outcome.Failure -> text
            }
    }
}

/** An optional note of up to 60 code points. Blank input means "no note". */
@ConsistentCopyVisibility
data class Note private constructor(val value: String) {
    companion object {
        const val MAX_CODE_POINTS = 60

        fun of(raw: String?): Outcome<Note?, TextError> {
            if (raw.isNullOrBlank()) return Outcome.Success(null)
            return when (val text = boundedText(raw, MAX_CODE_POINTS)) {
                is Outcome.Success -> Outcome.Success(Note(text.value))
                is Outcome.Failure -> text
            }
        }
    }
}

/** The name the user gives an "Otro" income source: trimmed, 1–24 code points. */
@ConsistentCopyVisibility
data class SourceName private constructor(val value: String) {
    companion object {
        const val MAX_CODE_POINTS = 24

        fun of(raw: String): Outcome<SourceName, TextError> =
            when (val text = boundedText(raw, MAX_CODE_POINTS)) {
                is Outcome.Success -> Outcome.Success(SourceName(text.value))
                is Outcome.Failure -> text
            }
    }
}

/** A goal's displayed name: trimmed, 1–40 code points. Two goals may share a name. */
@ConsistentCopyVisibility
data class GoalName private constructor(val value: String) {
    companion object {
        const val MAX_CODE_POINTS = 40

        fun of(raw: String): Outcome<GoalName, TextError> =
            when (val text = boundedText(raw, MAX_CODE_POINTS)) {
                is Outcome.Success -> Outcome.Success(GoalName(text.value))
                is Outcome.Failure -> text
            }
    }
}

/** An investment's displayed name: trimmed, 1–40 code points, as a goal's (investment rule 1). */
@ConsistentCopyVisibility
data class InvestmentName private constructor(val value: String) {
    /** What the alphabetical order compares: case and accents ignored, as category names. */
    val sortKey: String
        get() = foldedKey(value)

    companion object {
        const val MAX_CODE_POINTS = 40

        fun of(raw: String): Outcome<InvestmentName, TextError> =
            when (val text = boundedText(raw, MAX_CODE_POINTS)) {
                is Outcome.Success -> Outcome.Success(InvestmentName(text.value))
                is Outcome.Failure -> text
            }
    }
}

/**
 * An investment's optional description, shown under its name: trimmed, at most 40 code
 * points; blank is no description (investment rule 1).
 */
@ConsistentCopyVisibility
data class InvestmentDescription private constructor(val value: String) {
    companion object {
        const val MAX_CODE_POINTS = 40

        fun of(raw: String?): Outcome<InvestmentDescription?, TextError> {
            if (raw.isNullOrBlank()) return Outcome.Success(null)
            return when (val text = boundedText(raw, MAX_CODE_POINTS)) {
                is Outcome.Success -> Outcome.Success(InvestmentDescription(text.value))
                is Outcome.Failure -> text
            }
        }
    }
}
