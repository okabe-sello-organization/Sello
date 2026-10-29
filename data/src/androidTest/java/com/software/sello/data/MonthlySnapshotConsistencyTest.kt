package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.mapper.toEntity
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.DailyAllowance
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.SnapshotState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The monthly snapshot over a real database file, fed by the real commands. The
 * financial day starts on 9 October 2026. Expected figures are the approved examples'.
 */
@RunWith(AndroidJUnit4::class)
class MonthlySnapshotConsistencyTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private suspend fun read(month: YearMonth): MonthlySnapshot =
        when (val read = ledger.snapshots.read(month)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> throw AssertionError("expected a snapshot, got ${read.error}")
        }

    private fun MonthlySnapshot.limited() = overall as OverallBudget.Limited

    private suspend fun category(name: String, limit: BudgetLimit = finite(100_000)) {
        ledger.categories.submit(ledger.createCategory(name, limit))
    }

    private suspend fun spend(category: Int, date: String, pesos: Long) {
        val outcome = ledger.expenses.create(ledger.createExpense(category, date, pesos))
        assertTrue(
            "$outcome",
            outcome is com.software.sello.domain.port.CommandOutcome.Committed
        )
    }

    private fun raw(sql: String) = ledger.database.openHelper.writableDatabase.execSQL(sql)

    /** Collects [month]'s states so a test can wait for the one it expects. */
    private class Watch(val states: Channel<SnapshotState>, val job: Job) {
        suspend fun next(what: String, matches: (SnapshotState) -> Boolean): SnapshotState =
            withTimeout(15_000) {
                var state = states.receive()
                while (!matches(state)) state = states.receive()
                state
            }.also { check(matches(it)) { what } }
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.watch(month: YearMonth): Watch {
        val states = Channel<SnapshotState>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            ledger.snapshots.observe(month).collect { states.send(it) }
        }
        return Watch(states, job)
    }

    private fun ready(state: SnapshotState) = (state as SnapshotState.Ready).snapshot

    @Test
    fun aNewInstallationReadsAsAnEmptyMonthNotAFailure() = runBlocking {
        val read = read(october)

        assertEquals(emptyList<Any>(), read.categories)
        assertEquals(Money.cop(0), read.spent)
        assertEquals(OverallBudget.NoLimit, read.overall)
        assertEquals(1L to 0L, read.generation to read.revision)
        assertEquals(LocalDate.of(2026, 10, 9), read.asOf)
        assertEquals(ledger.zone, read.zone)
    }

    @Test
    fun spendingAccumulatesDayByDayAgainstTheLimit() = runBlocking {
        // G01: 10.000 on the 1st, 15.000 on the 2nd, 5.000 on the 3rd, limit 100.000.
        ledger.goTo("2026-10-01")
        category("Mercado")
        spend(100, "2026-10-01", 10_000)
        val first = read(october)
        ledger.goTo("2026-10-02")
        spend(100, "2026-10-02", 15_000)
        val second = read(october)
        ledger.goTo("2026-10-03")
        spend(100, "2026-10-03", 5_000)
        val third = read(october)

        assertEquals(
            listOf(Money.cop(10_000), Money.cop(25_000), Money.cop(30_000)),
            listOf(first.spent, second.spent, third.spent)
        )
        assertEquals(Money.cop(70_000), third.limited().budget.remaining)
        assertEquals(Money.cop(30_000), third.categories.single().spent)
        assertEquals(listOf(2L, 3L, 4L), listOf(first.revision, second.revision, third.revision))
    }

    @Test
    fun unlimitedSpendingCountsAndNoFiniteLimitMeansNoBudget() = runBlocking {
        // B01 then B02.
        category("Mercado")
        category("Libre", BudgetLimit.Unlimited)
        spend(100, "2026-10-02", 30_000)
        spend(101, "2026-10-03", 20_000)
        val withLimit = read(october)
        ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, ledger.categoryId(100), 1, BudgetLimit.Unlimited)
        )
        val withoutLimit = read(october)

        assertEquals(Money.cop(50_000), withLimit.spent)
        assertEquals(Money.cop(50_000), withLimit.limited().budget.remaining)
        assertEquals(OverallBudget.NoLimit, withoutLimit.overall)
        assertEquals(Money.cop(50_000), withoutLimit.spent)
    }

    @Test
    fun aLimitWithNoExpensesIsZeroSpentAndOnTrack() = runBlocking {
        // B06: a successful empty read is a real zero.
        category("Mercado")

        val read = read(october)

        val standing = read.limited().budget.standing as BudgetStanding.Current
        assertEquals(Money.cop(0), read.spent)
        assertEquals(Money.cop(0), standing.forecast.rounded)
        assertEquals(Verdict.OnTrack, standing.verdict)
        // 9 October: 23 days left including today; 100.000 / 23 = 4.347, shown as 4.300.
        assertEquals(DailyAllowance(Money.cop(4_300), availableDays = 23), read.limited().allowance)
    }

    @Test
    fun pastMonthsKeepTheirOwnLimitsAndShowActualsOnly() = runBlocking {
        // H01 and H04 end to end.
        ledger.goTo("2026-09-10")
        category("Mercado")
        spend(100, "2026-09-08", 20_000)
        ledger.goTo("2026-12-03")
        ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, ledger.categoryId(100), 1, finite(120_000))
        )
        spend(100, "2026-11-15", 10_000)

        val past = listOf(read(september), read(november))

        assertEquals(
            listOf(Money.cop(80_000), Money.cop(90_000)),
            past.map { it.limited().budget.remaining }
        )
        assertEquals(
            List(2) { BudgetStanding.Past(PastVerdict.WithinLimit) },
            past.map { it.limited().budget.standing }
        )
        assertEquals(listOf<DailyAllowance?>(null, null), past.map { it.limited().allowance })
        assertEquals(Money.cop(120_000), read(YearMonth.of(2026, 12)).limited().budget.limit)
    }

    @Test
    fun anArchivedCategoryStaysInMonthsItExplainsAndLeavesTheOthers() = runBlocking {
        category("Mercado")
        category("Transporte")
        spend(101, "2026-10-03", 5_000)
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(101), 1))
        ledger.goTo("2026-12-05")
        spend(100, "2026-12-01", 1_000)

        val names = listOf(october, november, YearMonth.of(2026, 12))
            .map { month -> read(month).categories.map { it.category.name.value } }

        // October: it had a limit and spending. November and December: paused, nothing spent.
        assertEquals(
            listOf(listOf("Mercado", "Transporte"), listOf("Mercado"), listOf("Mercado")),
            names
        )
        assertEquals(Money.cop(200_000), read(october).limited().budget.limit)
        assertEquals(Money.cop(100_000), read(november).limited().budget.limit)
    }

    @Test
    fun largeAndManyAmountsAreSummedExactly() = runBlocking {
        category("Mercado")
        repeat(3) { spend(100, "2026-10-0${it + 1}", 999_999_999_999) }
        // More rows than two pages of the read, written directly for speed.
        for (number in 2_000 until 3_200) {
            ledger.database.expenseDao().insert(
                expense(number, 100, "2026-10-05", number.toLong(), 1_000).toEntity()
            )
        }

        val read = read(october)

        // 3 × 999.999.999.999 + 1.200 × 1.000
        assertEquals(Money.cop(3_000_001_199_997), read.spent)
        assertEquals(Money.cop(100_000 - 3_000_001_199_997), read.limited().budget.remaining)
    }

    @Test
    fun expensesDatedAfterTheFinancialDayAreLeftOutAndCounted() = runBlocking {
        category("Mercado")
        spend(100, "2026-10-03", 30_000)
        spend(100, "2026-10-09", 4_000)
        // The financial day moves back, as a restored or simulated clock can.
        ledger.goTo("2026-10-05")

        val read = read(october)

        assertEquals(Money.cop(30_000), read.spent)
        assertEquals(1, read.futureDatedExpenses)
        assertEquals(LocalDate.of(2026, 10, 5), read.asOf)
    }

    @Test
    fun aMonthAfterTheCurrentOneIsRefused() = runBlocking {
        category("Mercado")

        assertEquals(
            Outcome.Failure(SnapshotFailure.FutureMonth(currentMonth = october)),
            ledger.snapshots.read(november)
        )
    }

    @Test
    fun aDamagedRowIsAFailedReadNeverAnEmptyOrZeroMonth() = runBlocking {
        category("Mercado")
        spend(100, "2026-10-03", 30_000)
        raw("UPDATE expense SET currency = 'BTC'")

        val badExpense = ledger.snapshots.read(october)
        raw("UPDATE expense SET currency = 'COP'")
        raw("UPDATE default_limit SET limit_minor = -5")
        val badLimit = ledger.snapshots.read(october)

        assertEquals(
            Outcome.Failure(
                SnapshotFailure.Storage(StorageFailure.Integrity("expense", uuid(101), "currency"))
            ),
            badExpense
        )
        assertEquals(
            Outcome.Failure(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("default_limit", "${uuid(100)}/2026-10", "limit_minor")
                )
            ),
            badLimit
        )
    }

    @Test
    fun observersGetAFreshSnapshotAfterEveryCommitAndEveryChangeOfDay() = runBlocking {
        category("Mercado")
        val watch = watch(october)

        val loading = watch.states.receive()
        val initial = ready(watch.next("first read") { it is SnapshotState.Ready })
        spend(100, "2026-10-03", 30_000)
        val afterExpense =
            ready(watch.next("expense") { it is SnapshotState.Ready && it.snapshot.revision == 2L })
        ledger.goTo("2026-10-31")
        val lastDay = ready(
            watch.next("last day") {
                it is SnapshotState.Ready && it.snapshot.asOf == LocalDate.of(2026, 10, 31)
            }
        )
        ledger.goTo("2026-11-01")
        val nextMonth = ready(
            watch.next("rollover") {
                it is SnapshotState.Ready && it.snapshot.asOf == LocalDate.of(2026, 11, 1)
            }
        )
        watch.job.cancel()

        assertEquals(SnapshotState.Loading, loading)
        assertEquals(Money.cop(0) to 1L, initial.spent to initial.revision)
        assertEquals(Money.cop(30_000), afterExpense.spent)
        // On the 31st one day is left: 70.000 for today.
        assertEquals(DailyAllowance(Money.cop(70_000), 1), lastDay.limited().allowance)
        // On 1 November, October is a finished month: no allowance, no forecast.
        assertNull(nextMonth.limited().allowance)
        assertEquals(
            BudgetStanding.Past(PastVerdict.WithinLimit),
            nextMonth.limited().budget.standing
        )
    }

    @Test
    fun aFailedRefreshKeepsTheLastGoodSnapshotAndRecoversWhenTheDataReadsAgain() = runBlocking {
        category("Mercado")
        spend(100, "2026-10-03", 30_000)
        val watch = watch(october)
        val good = ready(watch.next("first read") { it is SnapshotState.Ready })
        raw("UPDATE expense SET amount_minor = 0")

        ledger.goTo("2026-10-10")
        val failed = watch.next("failed refresh") { it is SnapshotState.Failed }
        raw("UPDATE expense SET amount_minor = 30000")
        ledger.goTo("2026-10-11")
        val recovered = ready(watch.next("recovery") { it is SnapshotState.Ready })
        watch.job.cancel()

        assertEquals(
            SnapshotState.Failed(
                SnapshotFailure.Storage(
                    StorageFailure.Integrity("expense", uuid(101), "amount_minor")
                ),
                lastGood = good
            ),
            failed
        )
        // The kept snapshot still says it is from the 9th at revision 2.
        assertEquals(LocalDate.of(2026, 10, 9) to 2L, good.asOf to good.revision)
        assertEquals(Money.cop(30_000), recovered.spent)
        assertEquals(LocalDate.of(2026, 10, 11), recovered.asOf)
    }

    @Test
    fun aFailureOnTheVeryFirstReadHasNoLastGoodData() = runBlocking {
        category("Mercado")
        raw("UPDATE category SET icon = 'Home'")
        val watch = watch(october)

        val loading = watch.states.receive()
        val failed = watch.next("first read fails") { it !is SnapshotState.Loading }
        watch.job.cancel()

        assertEquals(SnapshotState.Loading, loading)
        assertEquals(
            SnapshotState.Failed(
                SnapshotFailure.Storage(StorageFailure.Integrity("category", uuid(100), "icon")),
                lastGood = null
            ),
            failed
        )
    }

    @Test
    fun aSnapshotNeverMixesTheSpendingOfOneRevisionWithTheLimitOfAnother() = runBlocking {
        category("Mercado")
        val rounds = 40
        val seen = mutableListOf<MonthlySnapshot>()

        // Each round commits an expense of 1.000, then raises October's limit by 1.000.
        // So revision 1 + 2k means k expenses and limit 100.000 + 1.000 × k, and
        // revision 2k means k expenses and the limit of round k − 1.
        coroutineScope {
            val writer = launch(Dispatchers.Default) {
                for (round in 1..rounds) {
                    spend(100, "2026-10-03", 1_000)
                    ledger.categories.submit(
                        SetMonthLimit(
                            ledger.operation(),
                            1,
                            ledger.categoryId(100),
                            round.toLong(),
                            october,
                            finite(100_000 + 1_000L * round)
                        )
                    )
                }
            }
            launch(Dispatchers.Default) {
                while (writer.isActive) seen += read(october)
                seen += read(october)
            }
        }

        for (snapshot in seen) {
            val changes = snapshot.revision - 1
            val expenses = (changes + 1) / 2
            val limitRound = changes / 2
            assertEquals(
                "revision ${snapshot.revision}",
                Money.cop(1_000 * expenses) to Money.cop(100_000 + 1_000 * limitRound),
                snapshot.spent to snapshot.limited().budget.limit
            )
        }
        assertEquals(1L + 2 * rounds, seen.last().revision)
        assertTrue("only ${seen.map { it.revision }.toSet().size} revisions seen", seen.size > 3)
    }
}
