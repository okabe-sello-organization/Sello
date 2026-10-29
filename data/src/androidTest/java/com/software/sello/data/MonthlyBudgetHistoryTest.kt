package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.mapper.toEntity
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.policy.MonthBudgetState.Limited
import com.software.sello.domain.policy.MonthBudgetState.Paused
import com.software.sello.domain.policy.MonthBudgetState.Unconfigured
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The approved budget-history examples (H01 to H04, AR01 to AR06), played through the
 * real commands on a real database file while the financial day moves, then read back
 * through the real month read. Expected limits are the figures in the examples.
 */
@RunWith(AndroidJUnit4::class)
class MonthlyBudgetHistoryTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)
    private val mercado get() = ledger.categoryId(100)

    private fun derived(pesos: Long) = Limited(finite(pesos), explicit = false)

    private fun explicit(pesos: Long) = Limited(finite(pesos), explicit = true)

    /** Each category's name, archived flag and budget state in [month]. */
    private suspend fun budget(month: YearMonth): List<Triple<String, Boolean, MonthBudgetState>> =
        ledger.reads.monthBudget(month).valueOrFail().categories
            .map { Triple(it.category.name.value, it.category.archived, it.state) }

    private suspend fun stateIn(month: YearMonth): MonthBudgetState = budget(month).single().third

    private suspend fun version(): Long =
        ledger.reads.monthBudget(october).valueOrFail().categories.first().category.version

    private suspend fun committed(outcome: Any) = assertTrue("$outcome", outcome is Committed<*>)

    private suspend fun setDefault(pesos: Long) = committed(
        ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, mercado, version(), finite(pesos))
        )
    )

    private suspend fun setMonth(month: YearMonth, pesos: Long) = committed(
        ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, mercado, version(), month, finite(pesos))
        )
    )

    private suspend fun archive() = committed(
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, mercado, version()))
    )

    private suspend fun unarchive() = committed(
        ledger.categories.submit(UnarchiveCategory(ledger.operation(), 1, mercado, version()))
    )

    private fun limitRows() = ledger.long("SELECT COUNT(*) FROM default_limit") to
        ledger.long("SELECT COUNT(*) FROM month_limit")

    @Test
    fun changingThisMonthsDefaultDoesNotRewriteLastMonth() = runBlocking {
        // H01: September 100.000 with 20.000 spent; in October the default becomes 120.000.
        ledger.goTo("2026-09-10")
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.expenses.create(ledger.createExpense(100, "2026-09-08", 20_000))
        ledger.goTo("2026-10-02")

        setDefault(120_000)

        assertEquals(derived(100_000), stateIn(september))
        assertEquals(derived(120_000), stateIn(october))
        assertEquals(
            listOf("2026-09-08|20000"),
            ledger.rows("expense", "effective_date", "amount_minor")
        )
    }

    @Test
    fun correctingAPastMonthChangesThatMonthOnly() = runBlocking {
        // H02: September corrected to 90.000 from October; October stays 120.000.
        ledger.goTo("2026-09-10")
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.goTo("2026-10-02")
        setDefault(120_000)
        val defaults = ledger.rows("default_limit", "effective_month", "limit_minor")

        setMonth(september, 90_000)

        assertEquals(explicit(90_000), stateIn(september))
        assertEquals(derived(120_000), stateIn(october))
        assertEquals(defaults, ledger.rows("default_limit", "effective_month", "limit_minor"))
        assertEquals(listOf("2026-09|90000"), ledger.rows("month_limit", "month", "limit_minor"))
    }

    @Test
    fun aMonthOnlyLimitChangesThisMonthAndLeavesTheDefault() = runBlocking {
        // AJ01: October's own 150.000; November keeps the default 50.000.
        ledger.goTo("2026-10-02")
        ledger.categories.submit(ledger.createCategory("Mercado", finite(50_000)))

        setMonth(october, 150_000)
        val inOctober = stateIn(october)
        ledger.goTo("2026-11-02")

        assertEquals(explicit(150_000), inOctober)
        assertEquals(derived(50_000), stateIn(november))
    }

    @Test
    fun aNewDefaultReplacesOnlyThisMonthsOwnLimit() = runBlocking {
        // AJ03: September's own 90.000 stays; October's own 150.000 gives way to 60.000.
        ledger.goTo("2026-09-10")
        ledger.categories.submit(ledger.createCategory("Mercado", finite(50_000)))
        ledger.goTo("2026-10-02")
        setMonth(september, 90_000)
        setMonth(october, 150_000)

        setDefault(60_000)
        val inOctober = stateIn(october)
        ledger.goTo("2026-11-02")

        assertEquals(derived(60_000), inOctober)
        assertEquals(explicit(90_000), stateIn(september))
        assertEquals(listOf("2026-09|90000"), ledger.rows("month_limit", "month", "limit_minor"))
        assertEquals(derived(60_000), stateIn(november))
    }

    @Test
    fun aMonthNobodyOpenedUsesTheDefaultOfThatTimeHoweverLateItIsRead() = runBlocking {
        // H03, H04: 100.000 from October, the app closed all November, 120.000 from December.
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.goTo("2026-12-03")
        setDefault(120_000)
        ledger.expenses.create(ledger.createExpense(100, "2026-11-15", 10_000))
        val rowsBeforeReading = limitRows()

        val firstRead = listOf(october, november, december).map { stateIn(it) }
        ledger.reopen()
        ledger.goTo("2027-03-01")
        val laterRead = listOf(october, november, december).map { stateIn(it) }

        assertEquals(listOf(derived(100_000), derived(100_000), derived(120_000)), firstRead)
        assertEquals(firstRead, laterRead)
        assertEquals(derived(120_000), stateIn(YearMonth.of(2027, 3)))
        // The backdated expense belongs to November and reading created no rows.
        assertEquals(
            listOf("2026-11-15|10000"),
            ledger.rows("expense", "effective_date", "amount_minor")
        )
        assertEquals(rowsBeforeReading, limitRows())
        assertEquals(2L to 0L, rowsBeforeReading)
    }

    @Test
    fun monthsBeforeTheCategoryWasConfiguredAreUnconfiguredNotBackfilled() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))

        assertEquals(Unconfigured, stateIn(september))
        assertEquals(Unconfigured, stateIn(YearMonth.of(2020, 1)))
        assertEquals(derived(100_000), stateIn(october))
    }

    @Test
    fun zeroUnlimitedAndUnconfiguredStayDistinctThroughStorage() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Cero", finite(0)))
        ledger.categories.submit(ledger.createCategory("Libre", BudgetLimit.Unlimited))

        assertEquals(
            listOf(
                Triple("Cero", false, derived(0)),
                Triple("Libre", false, Limited(BudgetLimit.Unlimited, explicit = false))
            ),
            budget(october)
        )
        assertEquals(
            listOf(Triple("Cero", false, Unconfigured), Triple("Libre", false, Unconfigured)),
            budget(september)
        )
    }

    @Test
    fun anArchivedCategoryKeepsItsMonthAndGetsNoBudgetWhileArchived() = runBlocking {
        // AR01, AR02: 100.000 and 20.000 spent in October, archived in October.
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 20_000))

        archive()
        ledger.goTo("2026-11-20")

        assertEquals(listOf(Triple("Mercado", true, derived(100_000))), budget(october))
        assertEquals(listOf(Triple("Mercado", true, Paused)), budget(november))
        assertEquals(
            listOf("2026-10-03|20000"),
            ledger.rows("expense", "effective_date", "amount_minor")
        )
    }

    @Test
    fun unarchivingInTheSameMonthLeavesTheLimitAndTheFutureAsTheyWere() = runBlocking {
        // AR03.
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        val before = ledger.rows("default_limit", "effective_month", "kind", "limit_minor")
        archive()

        unarchive()
        ledger.goTo("2026-11-20")

        assertEquals(before, ledger.rows("default_limit", "effective_month", "kind", "limit_minor"))
        assertEquals(listOf(Triple("Mercado", false, derived(100_000))), budget(october))
        assertEquals(listOf(Triple("Mercado", false, derived(100_000))), budget(november))
        assertEquals(1, ledger.long("SELECT COUNT(*) FROM category"))
    }

    @Test
    fun unarchivingMonthsLaterRestoresTheLastDefaultWithoutABudgetForTheGap() = runBlocking {
        // AR04: archived in October, unarchived in December.
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        archive()
        ledger.goTo("2026-12-05")

        unarchive()

        assertEquals(derived(100_000), stateIn(october))
        assertEquals(Paused, stateIn(november))
        assertEquals(derived(100_000), stateIn(december))
        assertEquals(derived(100_000), stateIn(YearMonth.of(2027, 1)))
        assertEquals(
            listOf("2026-10|finite|100000", "2026-11|paused|null", "2026-12|finite|100000"),
            ledger.rows("default_limit", "effective_month", "kind", "limit_minor")
        )
    }

    @Test
    fun unarchivingKeepsALimitAlreadySetForThatMonth() = runBlocking {
        // AR05: December already has its own 90.000.
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        archive()
        ledger.goTo("2026-12-05")
        setMonth(december, 90_000)

        unarchive()

        assertEquals(explicit(90_000), stateIn(december))
        assertEquals(Paused, stateIn(november))
        // From January the last default applies again.
        assertEquals(derived(100_000), stateIn(YearMonth.of(2027, 1)))
    }

    @Test
    fun unarchivingInTheFirstPausedMonthRestoresThatMonth() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        archive()
        ledger.goTo("2026-11-01")

        unarchive()

        assertEquals(derived(100_000), stateIn(november))
        assertEquals(
            listOf("2026-10|finite|100000", "2026-11|finite|100000"),
            ledger.rows("default_limit", "effective_month", "kind", "limit_minor")
        )
    }

    @Test
    fun renamingAnArchivedCategoryChangesItsLabelInEveryMonthAndNothingElse() = runBlocking {
        // AR06: a saved limit, no expenses, archived, then renamed.
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        archive()
        val limits = ledger.rows("default_limit", "effective_month", "kind", "limit_minor")

        committed(
            ledger.categories.submit(
                RenameCategory(ledger.operation(), 1, mercado, version(), ledger.name("Plaza"))
            )
        )

        assertEquals(listOf(Triple("Plaza", true, derived(100_000))), budget(october))
        assertEquals(listOf(Triple("Plaza", true, Paused)), budget(november))
        assertEquals(limits, ledger.rows("default_limit", "effective_month", "kind", "limit_minor"))
        assertEquals(listOf(uuid(100)), ledger.rows("category", "id"))
    }

    @Test
    fun aSecondDefaultInTheSameMonthReplacesTheFirstAndAffectsNoEarlierMonth() = runBlocking {
        ledger.goTo("2026-09-10")
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.goTo("2026-10-02")
        setDefault(120_000)

        setDefault(150_000)

        assertEquals(derived(100_000), stateIn(september))
        assertEquals(derived(150_000), stateIn(october))
        assertEquals(
            listOf("2026-09|100000", "2026-10|150000"),
            ledger.rows("default_limit", "effective_month", "limit_minor")
        )
    }

    @Test
    fun theMonthReadCarriesTheRevisionItWasReadAtAndEveryCategoryInOrder() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        // More categories than two pages of the read, written directly for speed.
        for (number in 200 until 1300) {
            ledger.database.categoryDao().insert(category(number, "Categoría $number").toEntity())
        }

        val read = ledger.reads.monthBudget(october).valueOrFail()

        assertEquals(october, read.month)
        assertEquals(1L to 1L, read.generation to read.revision)
        assertEquals(
            listOf(uuid(100)) + (200 until 1300).map { uuid(it) },
            read.categories.map { it.category.id.value }
        )
        assertEquals(derived(100_000), read.categories.first().state)
        assertEquals(List(1100) { Unconfigured }, read.categories.drop(1).map { it.state })
    }

    @Test
    fun aDamagedLimitOrCategoryFailsTheWholeMonthRead() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.categories.submit(ledger.createCategory("Transporte", finite(50_000)))
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE default_limit SET kind = 'soft' WHERE category_id = '${uuid(101)}'"
        )

        val badLimit = ledger.reads.monthBudget(october)
        ledger.database.openHelper.writableDatabase.apply {
            execSQL("UPDATE default_limit SET kind = 'finite'")
            execSQL("UPDATE category SET archived = 7 WHERE id = '${uuid(100)}'")
        }
        val badCategory = ledger.reads.monthBudget(october)

        assertEquals(
            Outcome.Failure(
                StorageFailure.Integrity("default_limit", "${uuid(101)}/2026-10", "kind")
            ),
            badLimit
        )
        assertEquals(
            Outcome.Failure(StorageFailure.Integrity("category", uuid(100), "archived")),
            badCategory
        )
    }
}
