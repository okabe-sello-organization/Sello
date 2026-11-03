package com.software.sello.data.backup

import com.software.sello.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A recording or creation position the app could not add after (SELLO-162). An expense,
 * income, schedule or rate takes the position after the highest, so the highest may be
 * one below the largest whole number. A goal event or investment movement takes its
 * goal's or investment's stored next position, set one past the last on restore and
 * raised by each add, so the last may be two below. A file past that is refused; one at
 * it is accepted as written. Each case breaks one position of a valid file; paths and
 * numbers are written by hand.
 */
class SequenceBoundBackupTest {
    private val codec = BackupCodec()

    /** [file] with the [nth] (from 1) `"sequence": [from],` replaced by [to]. */
    private fun variant(file: String, from: Long, to: String, nth: Int): String {
        val text = String(resource(file))
        val needle = "\"sequence\": $from,"
        var at = -1
        repeat(nth) {
            at = text.indexOf(needle, at + 1)
            check(at >= 0) { "'$needle' occurs fewer than $nth times in $file" }
        }
        return text.replaceRange(at, at + needle.length, "\"sequence\": $to,")
    }

    private fun rejection(text: String): BackupRejection =
        when (val read = codec.read(text.toByteArray())) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    private fun accepted(text: String): BackupContents =
        codec.read(text.toByteArray()).value().contents

    private class Place(val file: String, val from: Long, val nth: Int, val path: String)

    private val places = listOf(
        Place("valid-g01-v7.json", from = 1, nth = 1, path = "financial.expenses[0].sequence"),
        Place("valid-g01-v7.json", from = 1, nth = 2, path = "financial.incomes[0].sequence"),
        Place(
            "valid-goals-v3.json",
            from = 3,
            nth = 2,
            path = "financial.goals[0].history[2].sequence"
        ),
        Place(
            "valid-schedules-v3.json",
            from = 2,
            nth = 2,
            path = "financial.schedules[2].sequence"
        ),
        Place(
            "valid-investments-v4.json",
            from = 5,
            nth = 1,
            path = "financial.investments[0].movements[4].sequence"
        ),
        Place(
            "valid-investments-v4.json",
            from = 1,
            nth = 4,
            path = "financial.usdRates[1].sequence"
        )
    )

    private val expense get() = places[0]
    private val income get() = places[1]
    private val goal get() = places[2]
    private val schedule get() = places[3]
    private val movement get() = places[4]
    private val rate get() = places[5]

    private fun assertRefused(places: List<Place>, sequence: String) {
        for (place in places) {
            assertEquals(
                place.path,
                BackupRejection.InvalidValue(place.path),
                rejection(variant(place.file, place.from, sequence, place.nth))
            )
        }
    }

    private fun accepted(place: Place, sequence: String) =
        accepted(variant(place.file, place.from, sequence, place.nth)).financial

    @Test
    fun `the largest whole number as a position is refused in every list`() {
        assertRefused(places, "9223372036854775807")
    }

    @Test
    fun `one below the largest is refused for a goal event or an investment movement`() {
        assertRefused(listOf(goal, movement), "9223372036854775806")
    }

    @Test
    fun `the last position each list allows is accepted as written`() {
        val oneBelow = 9_223_372_036_854_775_806L
        val twoBelow = 9_223_372_036_854_775_805L
        assertEquals(oneBelow, accepted(expense, "9223372036854775806").expenses[0].sequence)
        assertEquals(oneBelow, accepted(income, "9223372036854775806").incomes[0].sequence)
        assertEquals(
            oneBelow,
            accepted(schedule, "9223372036854775806").schedules[2].sequence
        )
        assertEquals(oneBelow, accepted(rate, "9223372036854775806").usdRates[1].sequence)
        assertEquals(
            twoBelow,
            accepted(goal, "9223372036854775805").goals[0].events[2].sequence
        )
        assertEquals(
            twoBelow,
            accepted(movement, "9223372036854775805").investments[0].movements[4].sequence
        )
    }

    @Test
    fun `a position beyond any whole number is refused, not wrapped`() {
        assertRefused(places, "9223372036854775808")
    }
}
