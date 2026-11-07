package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.FrequentExpense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.UsualAmountError
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every expected amount below is worked out by hand from the ticket's rule. */
class FrequentExpensesPolicyTest {
    private val today = LocalDate.parse("2026-11-07")
    private val stamp = Instant.parse("2026-11-07T12:00:00Z")
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val food = CategoryId.of(uuid(1)).valueOrFail()
    private val bus = CategoryId.of(uuid(2)).valueOrFail()
    private val archived = CategoryId.of(uuid(3)).valueOrFail()
    private val open = setOf(food, bus)

    private var sequence = 0L

    private fun cop(pesos: Long) = TransactionAmount.of(Money.cop(pesos)).valueOrFail()

    private fun expense(
        note: String?,
        pesos: Long,
        day: String = "2026-11-01",
        category: CategoryId = food,
        scheduled: Boolean = false
    ): Expense {
        sequence++
        return Expense(
            ExpenseId.of(uuid(100 + sequence.toInt())).valueOrFail(),
            category,
            LocalDate.parse(day),
            sequence,
            cop(pesos),
            Note.of(note).valueOrFail(),
            1,
            stamp,
            stamp,
            if (scheduled) {
                EntryOrigin(
                    ScheduleId.of(uuid(900)).valueOrFail(),
                    Occurrence(LocalDate.parse(day), 0)
                )
            } else {
                null
            }
        )
    }

    private fun groups(expenses: List<Expense>) =
        FrequentExpensesPolicy.groups(expenses, today, open).valueOrFail()

    private fun usual(vararg pesos: Long) =
        FrequentExpensesPolicy.usual(pesos.map(::cop)).valueOrFail().money

    private fun List<FrequentExpense>.shown() =
        map { "${it.note.value} ${it.count} ${it.usual.money.minorUnits}" }

    @Test
    fun anOddCountTakesTheMiddleAmount() {
        assertEquals(Money.cop(18_000), usual(17_500, 21_000, 18_000))
    }

    @Test
    fun anEvenCountTakesTheMeanOfTheTwoMiddleAmounts() {
        assertEquals(Money.cop(17_750), usual(17_500, 18_000))
    }

    @Test
    fun aHalfPesoRoundsUp() {
        assertEquals(Money.cop(18_002), usual(18_001, 18_002))
    }

    @Test
    fun oneUnusualDayDoesNotMoveTheUsualAmount() {
        assertEquals(
            Money.cop(18_000),
            usual(18_000, 18_000, 18_000, 18_000, 45_000, 18_000, 18_000, 18_000, 18_000, 18_000)
        )
    }

    @Test
    fun theLargestAmountsStayExact() {
        assertEquals(
            Money.cop(999_999_999_999),
            usual(999_999_999_998, 999_999_999_999)
        )
    }

    @Test
    fun noAmountsIsRefusedNotTurnedIntoZero() {
        assertEquals(
            UsualAmountError.NoAmounts,
            FrequentExpensesPolicy.usual(emptyList()).errorOrFail()
        )
    }

    @Test
    fun onlyTheLastTenAmountsCount() {
        // Ten recent lunches, five at 10.000 and five at 20.000: (10.000 + 20.000) / 2.
        // An eleventh, older one at 20.000 would make it 20.000 if it counted.
        val older = expense("Almuerzo", 20_000, day = "2026-10-01")
        val recent = (1..10).map {
            val pesos = if (it % 2 == 0) 10_000L else 20_000L
            expense("Almuerzo", pesos, day = "2026-10-%02d".format(10 + it))
        }

        assertEquals(listOf("Almuerzo 11 15000"), groups(listOf(older) + recent).shown())
    }

    @Test
    fun day90CountsAndDay91DoesNot() {
        // 2026-08-09 is 90 days before 2026-11-07; 2026-08-08 is 91.
        val expenses = listOf(
            expense("Bus", 2_950, day = "2026-08-09", category = bus),
            expense("Bus", 2_950, day = "2026-11-01", category = bus),
            expense("Bus", 2_950, day = "2026-11-02", category = bus),
            expense("Tinto", 3_500, day = "2026-08-08"),
            expense("Tinto", 3_500, day = "2026-11-01"),
            expense("Tinto", 3_500, day = "2026-11-02")
        )

        val all = groups(expenses)

        assertEquals(listOf("Bus 3 2950", "Tinto 2 3500"), all.shown())
        assertEquals(listOf("Bus 3 2950"), FrequentExpensesPolicy.frequent(all).shown())
    }

    @Test
    fun twoUsesAreNotEnough() {
        val expenses = listOf(expense("Recarga", 20_000), expense("Recarga", 20_000))

        assertEquals(
            emptyList<FrequentExpense>(),
            FrequentExpensesPolicy.frequent(groups(expenses))
        )
    }

    @Test
    fun aGroupIsACategoryAndANoteFoldedWhateverTheAmounts() {
        val expenses = listOf(
            expense("Almuerzo", 17_500, day = "2026-11-01"),
            expense("ALMUERZO", 21_000, day = "2026-11-02"),
            expense("almuérzo", 18_000, day = "2026-11-03"),
            // The same note in another category is another group.
            expense("Almuerzo", 9_000, day = "2026-11-04", category = bus)
        )

        val all = groups(expenses)

        // The note as it was last written, three times, usually 18.000.
        assertEquals(listOf("almuérzo 3 18000", "Almuerzo 1 9000"), all.shown())
        assertEquals("almuerzo", all.first().key)
        assertEquals(food, all.first().categoryId)
    }

    @Test
    fun theMostRepeatedComeFirstAndATieGoesToTheMostRecent() {
        val expenses = listOf(
            expense("Tinto", 3_500, day = "2026-11-01"),
            expense("Tinto", 3_500, day = "2026-11-02"),
            expense("Tinto", 3_500, day = "2026-11-03"),
            expense("Bus", 2_950, day = "2026-10-01", category = bus),
            expense("Bus", 2_950, day = "2026-10-02", category = bus),
            expense("Bus", 2_950, day = "2026-11-05", category = bus),
            expense("Mercado", 96_400, day = "2026-10-01"),
            expense("Mercado", 96_400, day = "2026-10-02"),
            expense("Mercado", 96_400, day = "2026-10-03"),
            expense("Mercado", 96_400, day = "2026-10-04"),
            // Same latest day as Bus, added later.
            expense("Recarga", 20_000, day = "2026-10-01"),
            expense("Recarga", 20_000, day = "2026-10-02"),
            expense("Recarga", 20_000, day = "2026-11-05")
        )

        assertEquals(
            listOf("Mercado 4 96400", "Recarga 3 20000", "Bus 3 2950", "Tinto 3 3500"),
            FrequentExpensesPolicy.frequent(groups(expenses)).shown()
        )
    }

    @Test
    fun theSheetOffersFiveAtMost() {
        val expenses = listOf("A", "B", "C", "D", "E", "F").flatMap { note ->
            (1..3).map { expense(note, 1_000) }
        }

        assertEquals(5, FrequentExpensesPolicy.frequent(groups(expenses)).size)
    }

    @Test
    fun fixedPaymentsNotesLeftEmptyArchivedCategoriesAndFutureDaysDoNotCount() {
        val expenses = listOf(
            expense("Arriendo", 900_000, scheduled = true),
            expense("Arriendo", 900_000, scheduled = true),
            expense("Arriendo", 900_000, scheduled = true),
            expense(null, 5_000),
            expense(null, 5_000),
            expense(null, 5_000),
            expense("Gym", 80_000, category = archived),
            expense("Gym", 80_000, category = archived),
            expense("Gym", 80_000, category = archived),
            expense("Cine", 20_000, day = "2026-11-08"),
            expense("Cine", 20_000, day = "2026-11-08"),
            expense("Cine", 20_000, day = "2026-11-08")
        )

        assertEquals(emptyList<String>(), groups(expenses).shown())
    }

    @Test
    fun theComposerSuggestsUpToThreeNotesThatStartWithWhatIsTyped() {
        val expenses = listOf(
            expense("Almuerzo", 18_000),
            expense("Almuerzo", 18_000),
            expense("Almuerzo con Ana", 42_000),
            expense("Almohada", 60_000),
            expense("Almendras", 9_000),
            expense("Café almuerzo", 4_000)
        )
        val all = groups(expenses)

        assertEquals(
            listOf("Almuerzo 2 18000", "Almendras 1 9000", "Almohada 1 60000"),
            FrequentExpensesPolicy.suggestions(all, "ALM").shown()
        )
        assertEquals(
            listOf("Almuerzo con Ana 1 42000"),
            FrequentExpensesPolicy.suggestions(all, "  almuerzo c").shown()
        )
        assertEquals(
            emptyList<String>(),
            FrequentExpensesPolicy.suggestions(all, "  ").shown()
        )
        assertEquals(
            emptyList<String>(),
            FrequentExpensesPolicy.suggestions(all, "pan").shown()
        )
    }
}
