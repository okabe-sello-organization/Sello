package com.software.sello.domain.model

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * SELLO-164: a newly typed name can't use a character that draws nothing or turns the
 * text around to look like another name. Every such character is written as an escape
 * and checked as UTF-8 bytes, because a literal one can be stripped without a trace.
 */
class InvisibleNameCharactersTest {
    private fun hex(text: String) =
        text.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }

    private fun of(vararg codePoints: Int) = String(codePoints, 0, codePoints.size)

    private val cafe = "Caf\u00E9"

    /** The ticket's three look-alikes of "Caf\u00E9". */
    private val zeroWidthSpace = "Caf\u00E9\u200B"
    private val softHyphen = "Ca\u00ADf\u00E9"
    private val rightToLeftOverride = "\u202ECaf\u00E9"

    /** Every character the rule refuses, one by one. */
    private val refused = listOf(
        0x00AD, // soft hyphen
        0x061C, // Arabic letter mark
        0x180E, // Mongolian vowel separator (another Cf)
        0x200B, // zero-width space
        0x200E, // left-to-right mark
        0x200F, // right-to-left mark
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E, // bidi embeddings and overrides
        0x2060, 0x2061, 0x2062, 0x2063, 0x2064, // word joiner, invisible operators
        0x2066, 0x2067, 0x2068, 0x2069, // bidi isolates
        0xFEFF, // zero-width no-break space (BOM)
        0xE0001 // language tag (another Cf, outside the BMP)
    )

    @Test
    fun theLookAlikesHoldTheCharactersTheyClaim() {
        assertEquals("43 61 66 C3 A9", hex(cafe))
        assertEquals("43 61 66 C3 A9 E2 80 8B", hex(zeroWidthSpace))
        assertEquals("43 61 C2 AD 66 C3 A9", hex(softHyphen))
        assertEquals("E2 80 AE 43 61 66 C3 A9", hex(rightToLeftOverride))
        assertEquals("F3 A0 80 81", hex(of(0xE0001)))
    }

    @Test
    fun aTypedNameWithAnInvisibleOrDirectionCharacterIsRefused() {
        for (codePoint in refused) {
            val typed = "Caf" + of(codePoint) + "\u00E9"
            val label = "U+%04X".format(codePoint)
            assertEquals(
                label,
                TextError.ControlCharacter,
                CategoryName.fromInput(typed).errorOrFail()
            )
            assertEquals(label, TextError.ControlCharacter, GoalName.fromInput(typed).errorOrFail())
            assertEquals(
                label,
                TextError.ControlCharacter,
                InvestmentName.fromInput(typed).errorOrFail()
            )
            assertEquals(
                label,
                TextError.ControlCharacter,
                SourceName.fromInput(typed).errorOrFail()
            )
            assertEquals(
                label,
                IncomeSourceError.InvalidOtherName(TextError.ControlCharacter),
                IncomeSource.fromInput(IncomeSource.Other.KEY, typed).errorOrFail()
            )
        }
    }

    @Test
    fun theTicketsThreeLookAlikesOfCafeAreRefused() {
        for (typed in listOf(zeroWidthSpace, softHyphen, rightToLeftOverride)) {
            assertEquals(
                hex(typed),
                TextError.ControlCharacter,
                CategoryName.fromInput(typed).errorOrFail()
            )
        }
        assertEquals(cafe, CategoryName.fromInput(cafe).valueOrFail().value)
    }

    @Test
    fun anInvisibleCharacterAtEitherEndIsNotTrimmedAway() {
        // trim() removes whitespace only; these are not whitespace and stay to be refused.
        for (typed in listOf("\u200B$cafe", "$cafe\uFEFF", " \u200F$cafe ")) {
            assertEquals(
                hex(typed),
                TextError.ControlCharacter,
                CategoryName.fromInput(typed).errorOrFail()
            )
        }
    }

    @Test
    fun emojiSequencesKeepTheirJoinersAndVariationSelectors() {
        val technologist = "\uD83D\uDC69\u200D\uD83D\uDCBB" // woman, ZWJ, laptop
        val redHeart = "\u2764\uFE0F" // heart, VS16
        val nonJoiner = "Caf\u00E9\u200C"
        val supplementarySelector = "\u845B\uDB40\uDD00" // ideograph, VS17 (U+E0100)
        assertEquals("F0 9F 91 A9 E2 80 8D F0 9F 92 BB", hex(technologist))
        assertEquals("E2 9D A4 EF B8 8F", hex(redHeart))
        assertEquals("43 61 66 C3 A9 E2 80 8C", hex(nonJoiner))
        assertEquals("E8 91 9B F3 A0 84 80", hex(supplementarySelector))
        for (typed in listOf(technologist, redHeart, nonJoiner, supplementarySelector)) {
            assertEquals(hex(typed), hex(CategoryName.fromInput(typed).valueOrFail().value))
            assertEquals(hex(typed), hex(GoalName.fromInput(typed).valueOrFail().value))
            assertEquals(hex(typed), hex(InvestmentName.fromInput(typed).valueOrFail().value))
            assertEquals(hex(typed), hex(SourceName.fromInput(typed).valueOrFail().value))
        }
    }

    @Test
    fun theStoredNameKeepsPassingSoAnOlderNameCanBeKept() {
        // Saved before the rule: editing the rest keeps the name as it is.
        assertEquals(
            "43 61 66 C3 A9 E2 80 8B",
            hex(CategoryName.fromInput(zeroWidthSpace, stored = zeroWidthSpace).valueOrFail().value)
        )
        assertEquals(
            hex(softHyphen),
            hex(GoalName.fromInput(" $softHyphen ", stored = softHyphen).valueOrFail().value)
        )
        assertEquals(
            hex(rightToLeftOverride),
            hex(
                InvestmentName.fromInput(rightToLeftOverride, stored = rightToLeftOverride)
                    .valueOrFail().value
            )
        )
        assertEquals(
            hex(zeroWidthSpace),
            hex(SourceName.fromInput(zeroWidthSpace, stored = zeroWidthSpace).valueOrFail().value)
        )
        val source = IncomeSource.fromInput(
            IncomeSource.Other.KEY,
            zeroWidthSpace,
            stored = zeroWidthSpace
        ).valueOrFail() as IncomeSource.Other
        assertEquals(hex(zeroWidthSpace), hex(source.name.value))

        // A different name is new input, and the rule applies to it.
        assertEquals(
            TextError.ControlCharacter,
            CategoryName.fromInput(zeroWidthSpace, stored = cafe).errorOrFail()
        )
        assertEquals(
            TextError.ControlCharacter,
            CategoryName.fromInput("$zeroWidthSpace\u200B", stored = zeroWidthSpace).errorOrFail()
        )
    }

    @Test
    fun theOtherRulesStayAsTheyWere() {
        assertEquals(TextError.Required, CategoryName.fromInput("  ").errorOrFail())
        assertEquals(
            TextError.TooLong(24, 25),
            CategoryName.fromInput("a".repeat(25)).errorOrFail()
        )
        assertEquals(TextError.ControlCharacter, CategoryName.fromInput("Ri\u0000fa").errorOrFail())
        assertEquals(TextError.TooLong(40, 41), GoalName.fromInput("a".repeat(41)).errorOrFail())
        assertEquals(
            IncomeSourceError.UnexpectedName("salary"),
            IncomeSource.fromInput("salary", "N\u00F3mina").errorOrFail()
        )
        assertEquals(IncomeSource.Salary, IncomeSource.fromInput("salary", null).valueOrFail())
    }

    @Test
    fun storedAndRestoredNamesStillReadAsTheyWere() {
        // of() reads storage and backups: unchanged, so old data still decodes and the
        // fold, which Room stores as name_key, is the same as before.
        for (stored in listOf(zeroWidthSpace, softHyphen, rightToLeftOverride)) {
            assertEquals(hex(stored), hex(CategoryName.of(stored).valueOrFail().value))
            assertEquals(hex(stored), hex(GoalName.of(stored).valueOrFail().value))
            assertEquals(hex(stored), hex(InvestmentName.of(stored).valueOrFail().value))
            assertEquals(hex(stored), hex(SourceName.of(stored).valueOrFail().value))
            assertNotEquals(
                CategoryName.of(cafe).valueOrFail().uniquenessKey,
                CategoryName.of(stored).valueOrFail().uniquenessKey
            )
        }
        assertEquals(
            "63 61 66 65 E2 80 8B",
            hex(CategoryName.of(zeroWidthSpace).valueOrFail().uniquenessKey)
        )
    }
}
