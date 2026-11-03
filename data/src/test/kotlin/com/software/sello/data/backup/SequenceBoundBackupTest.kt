package com.software.sello.data.backup

import com.software.sello.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A recording or creation position the app could not go past (SELLO-162). The app puts
 * the next goal event, movement, expense, income, schedule or rate after the highest
 * one, so a file holding the largest whole number would leave a goal it cannot read or
 * a list it can never add to. Such a file is refused; one below it is restored as
 * written. Each case breaks one position of a valid file; paths are written by hand.
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

    @Test
    fun `the largest whole number as a position is refused in every list`() {
        for (place in places) {
            assertEquals(
                place.path,
                BackupRejection.InvalidValue(place.path),
                rejection(variant(place.file, place.from, "9223372036854775807", place.nth))
            )
        }
    }

    @Test
    fun `one below the largest is restored as written in every list`() {
        val largest = 9_223_372_036_854_775_806L
        fun at(place: Place) =
            accepted(variant(place.file, place.from, "9223372036854775806", place.nth)).financial
        val (expense, income, goal, schedule, movement, rate) = places
        assertEquals(largest, at(expense).expenses[0].sequence)
        assertEquals(largest, at(income).incomes[0].sequence)
        assertEquals(largest, at(goal).goals[0].events[2].sequence)
        assertEquals(largest, at(schedule).schedules[2].sequence)
        assertEquals(largest, at(movement).investments[0].movements[4].sequence)
        assertEquals(largest, at(rate).usdRates[1].sequence)
    }

    @Test
    fun `a position beyond any whole number is refused, not wrapped`() {
        for (place in places) {
            assertEquals(
                place.path,
                BackupRejection.InvalidValue(place.path),
                rejection(variant(place.file, place.from, "9223372036854775808", place.nth))
            )
        }
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
