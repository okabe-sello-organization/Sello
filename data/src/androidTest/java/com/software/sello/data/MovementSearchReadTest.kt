package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomMovementSearch
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.Matches
import com.software.sello.domain.policy.MonthMatches
import com.software.sello.domain.policy.SearchTotals
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementQuery
import com.software.sello.domain.port.SearchPage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Día a día's search (SELLO-193): every month's expenses and incomes, on a real file. */
@RunWith(AndroidJUnit4::class)
class MovementSearchReadTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val search by lazy { RoomMovementSearch(ledger.database) }
    private val names = mapOf("salary" to "Salario", "freelance" to "Freelance")
    private var second = 0L

    /** Each write a second after the one before, so "added last" is told apart. */
    private fun nextMoment() {
        ledger.audit = Instant.parse("2026-11-01T12:00:00Z").plusSeconds(second++)
    }

    private fun spend(category: Int, date: String, pesos: Long, note: String? = null) =
        runBlocking {
            nextMoment()
            ledger.expenses.create(
                ledger.createExpense(category, date, pesos).copy(note = Note.of(note).valueOrFail())
            )
        }

    private fun receive(
        date: String,
        pesos: Long,
        note: String? = null,
        source: IncomeSource = IncomeSource.Salary
    ) = runBlocking {
        nextMoment()
        ledger.incomes.create(
            CreateIncome(
                operationId = ledger.operation(),
                generation = 1,
                amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
                source = source,
                date = LocalDate.parse(date),
                note = Note.of(note).valueOrFail()
            )
        )
    }

    /**
     * Alimentación (100), Rappí (101) and Hogar (102), with the mock's «rappi» expenses over
     * three months, others that do not match, and incomes.
     */
    private fun history() = runBlocking {
        ledger.goTo("2026-11-30")
        ledger.categories.submit(ledger.createCategory("Alimentación"))
        ledger.categories.submit(ledger.createCategory("Rappí"))
        ledger.categories.submit(ledger.createCategory("Hogar"))
        spend(100, "2026-08-12", 27_900, "Rappi, almuerzo")
        // Found by its category's name, without a note.
        spend(101, "2026-08-05", 27_400)
        spend(100, "2026-09-27", 22_100, "Rappi, almuerzo")
        spend(100, "2026-09-19", 41_200, "Rappí mercado")
        spend(102, "2026-09-13", 18_000, "Plomero")
        spend(100, "2026-10-03", 20_600, "Rappi, almuerzo")
        spend(100, "2026-10-01", 15_000, "rappi, café")
        spend(100, "2026-10-01", 25_600, "RAPPI cena")
        spend(100, "2026-10-04", 18_000, "Almuerzo")
        spend(100, "2026-10-05", 96_400, "Mercado D1")
        receive("2026-10-03", 2_500_000, "Quincena")
        receive("2026-09-20", 18_000, "Reembolso Rappi")
        receive(
            "2026-07-15",
            650_000,
            source = IncomeSource.Other(SourceName.of("Proyecto web").valueOrFail())
        )
    }

    private fun read(
        typed: String,
        filter: MovementFilter = MovementFilter.All,
        after: Movement? = null,
        limit: Int = 50
    ) = runBlocking {
        search.read(MovementQuery.of(typed)!!, filter, names, after, limit).valueOrFail()
    }

    private fun SearchPage.shown() = movements.map {
        when (it) {
            is Movement.Spent -> "${it.date} ${it.expense.note?.value} ${it.category.name.value} " +
                "${it.expense.amount.money.minorUnits}"

            is Movement.Received -> "${it.date} + ${it.income.amount.money.minorUnits}"
        }
    }

    @Test
    fun wordsFindNotesCategoriesAndIncomesInEveryMonthNewestFirst() {
        history()

        val found = read("rappí")

        assertEquals(
            listOf(
                "2026-10-03 Rappi, almuerzo Alimentación 20600",
                "2026-10-01 RAPPI cena Alimentación 25600",
                "2026-10-01 rappi, café Alimentación 15000",
                "2026-09-27 Rappi, almuerzo Alimentación 22100",
                "2026-09-20 + 18000",
                "2026-09-19 Rappí mercado Alimentación 41200",
                "2026-08-12 Rappi, almuerzo Alimentación 27900",
                "2026-08-05 null Rappí 27400"
            ),
            found.shown()
        )
        assertFalse(found.more)
        assertEquals(ledger.revision(), found.revision)
        // Worked by hand: 20.600 + 25.600 + 15.000 = 61.200; 22.100 + 41.200 = 63.300;
        // 27.900 + 27.400 = 55.300; in all 61.200 + 63.300 + 55.300 = 179.800.
        assertEquals(
            SearchTotals(
                listOf(
                    MonthMatches(YearMonth.of(2026, 10), 3, Money.cop(61_200)),
                    MonthMatches(YearMonth.of(2026, 9), 3, Money.cop(63_300)),
                    MonthMatches(YearMonth.of(2026, 8), 2, Money.cop(55_300))
                ),
                Matches(7, Money.cop(179_800)),
                Matches(1, Money.cop(18_000))
            ),
            found.totals
        )
    }

    @Test
    fun capitalsAndAccentsNeverKeepAMatchOut() {
        history()

        assertEquals(read("rappí").shown(), read("RAPPI").shown())
        assertEquals(read("rappí").shown(), read("Rappi").shown())
    }

    @Test
    fun anIncomeIsFoundByItsSourceAsTheScreenNamesIt() {
        history()

        assertEquals(
            listOf("2026-10-03 + 2500000", "2026-09-20 + 18000"),
            read("SALAR").shown()
        )
        assertEquals(listOf("2026-07-15 + 650000"), read("proyecto").shown())
    }

    @Test
    fun anAmountAlsoFindsExpensesAndIncomesOfExactlyThatAmount() {
        history()

        val found = read("18.000")

        assertEquals(
            listOf(
                "2026-10-04 Almuerzo Alimentación 18000",
                "2026-09-20 + 18000",
                "2026-09-13 Plomero Hogar 18000"
            ),
            found.shown()
        )
        assertEquals(read("18000").shown(), found.shown())
        // 18.000 + 18.000 = 36.000 of expenses; the income is counted apart.
        assertEquals(Matches(2, Money.cop(36_000)), found.totals.expenses)
        assertEquals(Matches(1, Money.cop(18_000)), found.totals.incomes)
    }

    @Test
    fun aBadlyGroupedNumberIsOnlyWords() {
        history()
        spend(102, "2026-10-02", 5_000, "Recibo 1.80.00")

        assertEquals(listOf("2026-10-02 Recibo 1.80.00 Hogar 5000"), read("1.80.00").shown())
    }

    @Test
    fun theChipsListOnlyExpensesOrOnlyIncomesWithTheirOwnTotals() {
        history()

        val expenses = read("rappi", MovementFilter.Expenses)
        val incomes = read("rappi", MovementFilter.Incomes)

        assertEquals(7, expenses.movements.size)
        assertEquals(Matches(7, Money.cop(179_800)), expenses.totals.expenses)
        assertNull(expenses.totals.incomes)
        assertEquals(listOf("2026-09-20 + 18000"), incomes.shown())
        assertNull(incomes.totals.expenses)
        assertEquals(
            listOf(MonthMatches(YearMonth.of(2026, 9), 1, null)),
            incomes.totals.months
        )
    }

    @Test
    fun pagesFollowTheLastRowListedWithoutRepeatingOrSkipping() {
        history()
        val all = read("rappi")
        val pages = mutableListOf<SearchPage>()
        var after: Movement? = null
        do {
            val page = read("rappi", after = after, limit = 3)
            pages += page
            after = page.movements.lastOrNull()
        } while (page.more)

        assertEquals(listOf(3, 3, 2), pages.map { it.movements.size })
        assertEquals(all.shown(), pages.flatMap { it.shown() })
        // Every page carries the totals of the whole search.
        pages.forEach { assertEquals(all.totals, it.totals) }
    }

    @Test
    fun rowsCreatedAtOneInstantPageExpensesFirstThenTheHigherSequence() {
        runBlocking {
            ledger.goTo("2026-11-30")
            ledger.categories.submit(ledger.createCategory("Mercado"))
        }
        // One instant for every write, as a fixed-payment catch-up records several rows.
        ledger.audit = Instant.parse("2026-11-02T08:00:00Z")
        runBlocking {
            ledger.expenses.create(
                ledger.createExpense(100, "2026-10-20", 1_000)
                    .copy(note = Note.of("Pago fijo").valueOrFail())
            )
            ledger.expenses.create(
                ledger.createExpense(100, "2026-10-20", 2_000)
                    .copy(note = Note.of("Pago fijo").valueOrFail())
            )
            ledger.incomes.create(
                CreateIncome(
                    ledger.operation(),
                    1,
                    TransactionAmount.of(Money.cop(900_000)).valueOrFail(),
                    IncomeSource.Salary,
                    LocalDate.parse("2026-10-20"),
                    Note.of("Pago fijo").valueOrFail()
                )
            )
            ledger.incomes.create(
                CreateIncome(
                    ledger.operation(),
                    1,
                    TransactionAmount.of(Money.cop(800_000)).valueOrFail(),
                    IncomeSource.Salary,
                    LocalDate.parse("2026-10-20"),
                    Note.of("Pago fijo").valueOrFail()
                )
            )
        }

        val shown = mutableListOf<String>()
        var after: Movement? = null
        do {
            val page = read("pago fijo", after = after, limit = 1)
            shown += page.shown()
            after = page.movements.lastOrNull()
        } while (page.more)

        assertEquals(
            listOf(
                "2026-10-20 Pago fijo Mercado 2000",
                "2026-10-20 Pago fijo Mercado 1000",
                "2026-10-20 + 800000",
                "2026-10-20 + 900000"
            ),
            shown
        )
        assertEquals(read("pago fijo").shown(), shown)
    }

    @Test
    fun nothingFoundIsAnEmptyAnswerNotAFailure() {
        history()

        val found = read("plomería")

        assertEquals(emptyList<String>(), found.shown())
        assertFalse(found.more)
        assertEquals(SearchTotals(emptyList(), null, null), found.totals)
    }

    @Test
    fun accentMarksAloneFindNothingRatherThanEverything() {
        history()

        assertEquals(emptyList<String>(), read("́").shown())
    }
}
