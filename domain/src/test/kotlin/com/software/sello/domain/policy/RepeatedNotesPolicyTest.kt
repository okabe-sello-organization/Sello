package com.software.sello.domain.policy

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.RepeatedNote
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que más se repite (SELLO-195): hand-worked sums, grouped as search folds notes. */
class RepeatedNotesPolicyTest {
    private val stamp = Instant.parse("2026-10-22T12:00:00Z")
    private var next = 0
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val category = CategoryId.of(uuid(1)).valueOrFail()
    private fun cop(pesos: Long) = Money.cop(pesos)
    private fun note(text: String) = Note.of(text).valueOrFail()!!

    private fun expense(day: String, pesos: Long, note: String?): Expense {
        next += 1
        return Expense(
            ExpenseId.of(uuid(100 + next)).valueOrFail(),
            category,
            LocalDate.parse("2026-10-$day"),
            next.toLong(),
            TransactionAmount.of(cop(pesos)).valueOrFail(),
            Note.of(note).valueOrFail(),
            1,
            stamp,
            stamp
        )
    }

    private fun top(vararg expenses: Expense) =
        RepeatedNotesPolicy.top(expenses.toList()).valueOrFail()

    @Test
    fun sixRappiLunchesAddUpTo123600() {
        val lunches = listOf("01", "03", "06", "09", "14", "20").map {
            expense(it, 20_600, "Rappi, almuerzo")
        }
        assertEquals(
            listOf(RepeatedNote(note("Rappi, almuerzo"), 6, cop(123_600))),
            top(*lunches.toTypedArray())
        )
    }

    @Test
    fun capitalsAccentsAndEndSpacesFormOneGroupUnderTheLatestSpelling() {
        val shown = top(
            expense("05", 50_000, "mercado d1 "),
            expense("12", 30_000, "MERCADO D1"),
            expense("26", 96_400, "Mercado D1"),
            expense("02", 8_000, "Panadería"),
            expense("03", 7_000, "panaderia")
        )
        assertEquals(
            listOf(
                RepeatedNote(note("Mercado D1"), 3, cop(176_400)),
                RepeatedNote(note("panaderia"), 2, cop(15_000))
            ),
            shown
        )
    }

    @Test
    fun aNoteUsedOnceIsNotShown() {
        val shown = top(
            expense("04", 50_000, "Café"),
            expense("05", 3_000, "Pan"),
            expense("06", 3_000, "Pan")
        )
        assertEquals(listOf(RepeatedNote(note("Pan"), 2, cop(6_000))), shown)
    }

    @Test
    fun expensesWithoutANoteOrWithOnlySpacesFormNoGroup() {
        val shown = top(
            expense("01", 10_000, null),
            expense("02", 10_000, null),
            expense("03", 10_000, null),
            expense("04", 10_000, null),
            expense("05", 10_000, "   "),
            expense("06", 10_000, "   ")
        )
        assertEquals(emptyList<RepeatedNote>(), shown)
    }

    @Test
    fun aTieOnSumGoesToTheHigherCountThenToTheMostRecent() {
        val shown = top(
            expense("02", 30_000, "A"),
            expense("03", 30_000, "A"),
            expense("04", 20_000, "B"),
            expense("05", 20_000, "B"),
            expense("06", 20_000, "B"),
            expense("07", 30_000, "C"),
            expense("08", 30_000, "C")
        )
        assertEquals(
            listOf(
                RepeatedNote(note("B"), 3, cop(60_000)),
                RepeatedNote(note("C"), 2, cop(60_000)),
                RepeatedNote(note("A"), 2, cop(60_000))
            ),
            shown
        )
    }

    @Test
    fun onlyTheTopThreeBySumShow() {
        val shown = top(
            expense("01", 1_000, "W"),
            expense("02", 1_000, "W"),
            expense("03", 5_000, "X"),
            expense("04", 5_000, "X"),
            expense("05", 4_000, "Y"),
            expense("06", 4_000, "Y"),
            expense("07", 3_000, "Z"),
            expense("08", 3_000, "Z")
        )
        assertEquals(
            listOf(
                RepeatedNote(note("X"), 2, cop(10_000)),
                RepeatedNote(note("Y"), 2, cop(8_000)),
                RepeatedNote(note("Z"), 2, cop(6_000))
            ),
            shown
        )
    }
}
