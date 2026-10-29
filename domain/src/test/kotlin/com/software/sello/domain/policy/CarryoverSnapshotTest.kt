package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.Carry.abr
import com.software.sello.domain.policy.Carry.chain
import com.software.sello.domain.policy.Carry.comida
import com.software.sello.domain.policy.Carry.comidaInput
import com.software.sello.domain.policy.Carry.cop
import com.software.sello.domain.policy.Carry.ene
import com.software.sello.domain.policy.Carry.expense
import com.software.sello.domain.policy.Carry.feb
import com.software.sello.domain.policy.Carry.hogar
import com.software.sello.domain.policy.Carry.m
import com.software.sello.domain.policy.Carry.mar
import com.software.sello.domain.policy.Carry.monthly
import com.software.sello.domain.policy.Carry.transporte
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Signed carryover in the monthly snapshot: verdicts, remaining and the daily allowance on
 * base + carry, the overall budget, Por asignar on the base only, reservations and the
 * forecast (SC09–SC16, SC35–SC39, SC41, CP14–CP19). Every figure is copied from
 * docs/testing/signed-carryover-examples.md.
 */
class CarryoverSnapshotTest {
    private val bogota = ZoneId.of("America/Bogota")

    private fun line(id: CategoryId, name: String, base: Long?) = CategoryInMonth(
        Carry.category(id, name),
        MonthBudgetState.Limited(
            if (base == null) BudgetLimit.Unlimited else Carry.finite(base),
            explicit = false
        )
    )

    private fun read(
        today: String,
        categories: List<CategoryInMonth>,
        expenses: List<Expense>,
        carryover: List<CategoryCarryInput>,
        reserved: List<com.software.sello.domain.model.ReservedPayment> = emptyList()
    ) = MonthlyBudgetPolicy.snapshot(
        month = YearMonth.from(LocalDate.parse(today)),
        today = FinancialDay(LocalDate.parse(today), bogota),
        generation = 3,
        revision = 41,
        categories = categories,
        expenses = expenses,
        reserved = reserved,
        carryover = carryover
    )

    /** Comida, base 100.000 on from ene, chain from ene, with spending as (date, pesos). */
    private fun comidaMonth(
        today: String,
        spent: List<Pair<String, Long>>,
        base: Long = 100_000,
        chainStart: YearMonth = ene
    ): Result {
        val expenses = spent.map { expense(comida, it.first, it.second) }
        val current = YearMonth.from(LocalDate.parse(today))
        val input = CategoryCarryInput(
            comida,
            listOf(chain(chainStart)),
            listOf(monthly(ene, base)),
            emptyList(),
            Carry.history(comida, chainStart, current, today, *expenses.toTypedArray())
        )
        return Result(
            read(today, listOf(line(comida, "Comida", base)), expenses, listOf(input)).valueOrFail()
        )
    }

    private class Result(val snapshot: MonthlySnapshot) {
        val category: LimitedBudget get() = snapshot.categories.single().budget!!
        val overall get() = snapshot.overall as OverallBudget.Limited
        val verdict get() = (category.standing as BudgetStanding.Current).verdict
    }

    private fun breakdown(base: Long, k: Long, c: Long, chainStart: YearMonth?) =
        CarryBreakdown(cop(base), cop(k), cop(c), chainStart)

    @Test
    fun sc09TheMonthIsJudgedOnBasePlusCarryAndTheAllowanceFollowsIt() {
        // 22 feb, D = 7: ene spent 20.000, feb 30.000.
        val month = comidaMonth(
            "2027-02-22",
            listOf(
                "2027-01-10" to 20_000L,
                "2027-02-05" to 30_000L
            )
        )

        assertEquals(cop(180_000), month.category.limit)
        assertEquals(cop(150_000), month.category.remaining)
        assertEquals(breakdown(100_000, 80_000, 180_000, ene), month.category.carry)
        assertEquals(Verdict.OnTrack, month.verdict)
        assertEquals(cop(21_400), month.overall.allowance?.amount)
        assertEquals(7, month.overall.allowance?.availableDays)
        assertEquals(cop(180_000), month.overall.budget.limit)
        assertEquals(cop(150_000), month.overall.budget.remaining)
    }

    @Test
    fun sc10SpendingExactlyTheCapacityIsAlLimiteAndOneMoreIsExcedido() {
        val exact = comidaMonth(
            "2027-02-22",
            listOf(
                "2027-01-10" to 20_000L,
                "2027-02-05" to 180_000L
            )
        )
        val over = comidaMonth(
            "2027-02-22",
            listOf(
                "2027-01-10" to 20_000L,
                "2027-02-05" to 180_001L
            )
        )

        assertEquals(cop(0), exact.category.remaining)
        assertEquals(Verdict.AtLimit, exact.verdict)
        assertEquals(cop(0), exact.overall.allowance?.amount)
        assertEquals(cop(-1), over.category.remaining)
        assertEquals(Verdict.Over, over.verdict)
    }

    @Test
    fun sc11AnEarlierDeficitIsExcedidoEvenWithNothingSpentThisMonth() {
        val nothing = comidaMonth("2027-02-22", listOf("2027-01-10" to 250_000L))
        val thirty = comidaMonth(
            "2027-02-22",
            listOf(
                "2027-01-10" to 250_000L,
                "2027-02-05" to 30_000L
            )
        )

        assertEquals(cop(-50_000), nothing.category.limit)
        assertEquals(cop(-50_000), nothing.category.remaining)
        assertEquals(Verdict.Over, nothing.verdict)
        assertEquals(cop(0), nothing.overall.allowance?.amount)
        assertEquals(cop(-80_000), thirty.category.remaining)
    }

    @Test
    fun sc12TheAllowanceIsRoundedAndClampedButTheRemainingIsNeverErased() {
        // Turned on in feb; 15 mar 2027 has 31 days, D = 17.
        fun march(febSpent: Long, marSpent: Long?) = comidaMonth(
            "2027-03-15",
            listOfNotNull(
                "2027-02-10" to febSpent,
                marSpent?.let { "2027-03-05" to it }
            ),
            chainStart = feb
        )

        val under = march(199_151, null)
        val atFifty = march(199_150, null)
        val deficit = march(199_151, 969)

        assertEquals(cop(849), under.category.limit)
        assertEquals(cop(849), under.category.remaining)
        assertEquals(Verdict.OnTrack, under.verdict)
        assertEquals(cop(0), under.overall.allowance?.amount)
        assertEquals(17, under.overall.allowance?.availableDays)
        assertEquals(cop(850), atFifty.category.limit)
        assertEquals(cop(50), atFifty.overall.allowance?.amount)
        // Only the allowance is clamped: the remaining shows -120, never -50.
        assertEquals(cop(-120), deficit.category.remaining)
        assertEquals(Verdict.Over, deficit.verdict)
        assertEquals(cop(0), deficit.overall.allowance?.amount)
        assertEquals(cop(-120), deficit.overall.budget.remaining)
    }

    // SC13: 22 feb 2027, D = 7. Comida base 100.000 on from ene, Transporte 50.000 off, Ocio unlimited.
    private val sc13Categories = listOf(
        line(comida, "Comida", 100_000),
        line(transporte, "Transporte", 50_000),
        line(Carry.id(4), "Ocio", null)
    )

    private fun sc13(extra: List<Expense> = emptyList()): MonthlySnapshot {
        val expenses = listOf(
            expense(comida, "2027-01-10", 20_000),
            expense(comida, "2027-02-05", 30_000),
            expense(transporte, "2027-02-06", 60_000),
            expense(Carry.id(4), "2027-02-07", 10_000)
        ) + extra
        val input = CategoryCarryInput(
            comida,
            listOf(chain(ene)),
            listOf(monthly(ene, 100_000)),
            emptyList(),
            Carry.history(comida, ene, feb, "2027-02-22", *expenses.toTypedArray())
        )
        return read("2027-02-22", sc13Categories, expenses, listOf(input)).valueOrFail()
    }

    @Test
    fun sc13TheOverallBudgetIsOneSumAndNoCategoryBorrowsFromAnother() {
        val snapshot = sc13()
        val overall = snapshot.overall as OverallBudget.Limited
        val byName = snapshot.categories.associateBy { it.category.name.value }

        assertEquals(breakdown(150_000, 80_000, 230_000, null), overall.budget.carry)
        assertEquals(cop(230_000), overall.budget.limit)
        assertEquals(cop(100_000), overall.budget.spent)
        assertEquals(cop(130_000), overall.budget.remaining)
        assertEquals(Verdict.OnTrack, (overall.budget.standing as BudgetStanding.Current).verdict)
        assertEquals(cop(18_550), overall.allowance?.amount)
        assertEquals(cop(180_000), byName["Comida"]!!.budget!!.limit)
        assertEquals(cop(150_000), byName["Comida"]!!.budget!!.remaining)
        val transporte = byName["Transporte"]!!.budget!!
        assertEquals(cop(50_000), transporte.limit)
        assertEquals(cop(-10_000), transporte.remaining)
        assertNull(transporte.carry)
        assertEquals(
            Verdict.Over,
            (transporte.standing as BudgetStanding.Current).verdict
        )
        assertNull(byName["Ocio"]!!.budget)
    }

    @Test
    fun sc14PorAsignarUsesTheBaseOnlyAndCarryIsNeverIncomeOrNetFlow() {
        val expenses = listOf(
            expense(comida, "2027-01-10", 20_000),
            expense(comida, "2027-02-05", 30_000),
            expense(transporte, "2027-02-06", 60_000),
            expense(Carry.id(4), "2027-02-07", 10_000)
        )
        val input = CategoryCarryInput(
            comida,
            listOf(chain(ene)),
            listOf(monthly(ene, 100_000)),
            emptyList(),
            Carry.history(comida, ene, feb, "2027-02-22", *expenses.toTypedArray())
        )
        val income = Income(
            IncomeId.of("00000000-0000-4000-8000-000000009001").valueOrFail(),
            LocalDate.parse("2027-02-01"),
            1,
            TransactionAmount.of(cop(200_000)).valueOrFail(),
            IncomeSource.of("salary", null).valueOrFail(),
            null,
            1,
            Instant.EPOCH,
            Instant.EPOCH
        )

        fun resumen(carryover: List<CategoryCarryInput>) = ResumenPolicy.snapshot(
            feb,
            FinancialDay(LocalDate.parse("2027-02-22"), bogota),
            3,
            41,
            ResumenPolicy.monthsEndingIn(feb).map {
                ResumenMonthInput(it, sc13Categories, expenses, carryover)
            },
            listOf(income)
        ).valueOrFail()

        val withCarry = resumen(listOf(input))
        val without = resumen(emptyList())

        assertEquals(cop(150_000), withCarry.allocated)
        assertEquals(cop(50_000), withCarry.unallocated)
        assertEquals(cop(100_000), withCarry.netFlow)
        assertEquals(cop(200_000), withCarry.income)
        // The carry is on the month the screen shows, and turning it on changes neither figure.
        assertEquals(cop(230_000), (withCarry.budget.overall as OverallBudget.Limited).budget.limit)
        assertEquals(without.allocated, withCarry.allocated)
        assertEquals(without.unallocated, withCarry.unallocated)
        assertEquals(without.netFlow, withCarry.netFlow)
    }

    @Test
    fun sc15ASurplusInOneCategoryNeverCoversAnotherCategorysOverrun() {
        fun feb(comidaSpent: Long): MonthlySnapshot {
            val expenses = listOf(
                expense(comida, "2027-01-10", 160_000),
                expense(hogar, "2027-01-11", 40_000),
                expense(comida, "2027-02-05", comidaSpent)
            )
            val inputs = listOf(comida, hogar).map {
                CategoryCarryInput(
                    it,
                    listOf(chain(ene, of = it)),
                    listOf(monthly(ene, 100_000, it)),
                    emptyList(),
                    Carry.history(it, ene, Carry.feb, "2027-02-22", *expenses.toTypedArray())
                )
            }
            return read(
                "2027-02-22",
                listOf(line(comida, "Comida", 100_000), line(hogar, "Hogar", 100_000)),
                expenses,
                inputs
            ).valueOrFail()
        }

        val at = feb(40_000)
        val over = feb(41_000)
        val comidaAt = at.categories.first { it.category.id == comida }.budget!!
        val hogarAt = at.categories.first { it.category.id == hogar }.budget!!
        val comidaOver = over.categories.first { it.category.id == comida }.budget!!

        assertEquals(cop(40_000), comidaAt.limit)
        assertEquals(cop(160_000), hogarAt.limit)
        assertEquals(Verdict.AtLimit, (comidaAt.standing as BudgetStanding.Current).verdict)
        assertEquals(cop(200_000), (at.overall as OverallBudget.Limited).budget.limit)
        assertEquals(cop(40_000), (at.overall as OverallBudget.Limited).budget.spent)
        assertEquals(cop(160_000), (at.overall as OverallBudget.Limited).budget.remaining)
        val overallAt = (at.overall as OverallBudget.Limited).budget
        assertEquals(Verdict.OnTrack, (overallAt.standing as BudgetStanding.Current).verdict)
        assertEquals(Verdict.Over, (comidaOver.standing as BudgetStanding.Current).verdict)
        assertEquals(cop(-1_000), comidaOver.remaining)
        assertEquals(cop(159_000), (over.overall as OverallBudget.Limited).budget.remaining)
    }

    @Test
    fun sc16ADeficitLowersTheOverallCapacityByExactlyItself() {
        fun feb(transporteSpent: Long?): MonthlySnapshot {
            val expenses = listOfNotNull(
                expense(comida, "2027-01-10", 250_000),
                transporteSpent?.let { expense(transporte, "2027-02-05", it) }
            )
            val input = CategoryCarryInput(
                comida,
                listOf(chain(ene)),
                listOf(monthly(ene, 100_000)),
                emptyList(),
                Carry.history(comida, ene, Carry.feb, "2027-02-22", *expenses.toTypedArray())
            )
            return read(
                "2027-02-22",
                listOf(line(comida, "Comida", 100_000), line(transporte, "Transporte", 50_000)),
                expenses,
                listOf(input)
            ).valueOrFail()
        }

        val nothing = feb(null)
        val onePeso = feb(1)
        val comidaLine = nothing.categories.first { it.category.id == comida }.budget!!

        assertEquals(cop(-50_000), comidaLine.limit)
        assertEquals(Verdict.Over, (comidaLine.standing as BudgetStanding.Current).verdict)
        val overall = nothing.overall as OverallBudget.Limited
        assertEquals(cop(0), overall.budget.limit)
        assertEquals(Verdict.OnTrack, (overall.budget.standing as BudgetStanding.Current).verdict)
        val overOverall = onePeso.overall as OverallBudget.Limited
        assertEquals(cop(-1), overOverall.budget.remaining)
        assertEquals(Verdict.Over, (overOverall.budget.standing as BudgetStanding.Current).verdict)
        val transporteLine = onePeso.categories.first { it.category.id == transporte }.budget!!
        assertEquals(cop(49_999), transporteLine.remaining)
        assertEquals(Verdict.OnTrack, (transporteLine.standing as BudgetStanding.Current).verdict)
    }

    // --- Reservations, catch-up and the forecast (SC35–SC38, SC47 headline) ---

    private val rent = ScheduleId.of("00000000-0000-4000-8000-000000000777").valueOrFail()

    private fun payment(date: String, pesos: Long) = StoredSchedule(
        Schedule(
            rent,
            ScheduledEntry.Payment(hogar, TransactionAmount.of(cop(pesos)).valueOrFail(), null),
            Recurrence.of(
                RepeatRule.Monthly.of(LocalDate.parse(date).dayOfMonth).valueOrFail(),
                LocalDate.parse(date),
                LocalDate.parse(date)
            ).valueOrFail()
        ),
        sequence = 1,
        handled = null,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    /** Hogar, base 1.000.000, chain from ene, read on [today] with [spent] as (date, pesos). */
    private fun hogarMonth(
        today: String,
        spent: List<Pair<String, Long>>,
        schedules: List<StoredSchedule> = emptyList(),
        chains: List<CarryChain> = listOf(chain(ene, of = hogar))
    ): Result {
        val expenses = spent.map { expense(hogar, it.first, it.second) }
        val current = YearMonth.from(LocalDate.parse(today))
        val input = CategoryCarryInput(
            hogar,
            chains,
            listOf(monthly(ene, 1_000_000, hogar)),
            emptyList(),
            Carry.history(hogar, ene, current, today, *expenses.toTypedArray())
        )
        return Result(
            read(
                today,
                listOf(line(hogar, "Hogar", 1_000_000)),
                expenses,
                listOf(input),
                ReservationPolicy.reserved(schedules, current, chains)
            ).valueOrFail()
        )
    }

    @Test
    fun sc35AReservedPaymentIsSeparatedFromTheCapacityWithCarry() {
        // 15 mar, D = 17. The rent of 150.000 is due later this month.
        val month = hogarMonth(
            "2027-03-15",
            listOf("2027-01-10" to 800_000L, "2027-02-10" to 1_000_000L, "2027-03-05" to 900_000L),
            listOf(payment("2027-03-28", 150_000))
        )

        val reservation = month.overall.reservation!!
        assertEquals(cop(1_200_000), month.category.limit)
        assertEquals(breakdown(1_000_000, 200_000, 1_200_000, ene), month.category.carry)
        assertEquals(cop(300_000), month.category.remaining)
        assertEquals(cop(150_000), reservation.margin)
        assertEquals(ReservedStanding.Enough, reservation.standing)
        assertEquals(cop(8_800), month.overall.allowance?.amount)
        assertEquals(Verdict.OnTrack, month.verdict)
    }

    @Test
    fun sc36ADeficitWithAReservedPaymentIsShortByTheWholeAmountMissing() {
        val before = hogarMonth(
            "2027-03-15",
            listOf(
                "2027-01-10" to 1_200_000L,
                "2027-02-10" to 1_000_000L,
                "2027-03-05" to 900_000L
            ),
            listOf(payment("2027-03-28", 150_000))
        )
        val recorded = hogarMonth(
            "2027-03-15",
            listOf(
                "2027-01-10" to 1_200_000L,
                "2027-02-10" to 1_000_000L,
                "2027-03-05" to 900_000L,
                "2027-03-06" to 150_000L
            )
        )

        val reservation = before.overall.reservation!!
        assertEquals(cop(800_000), before.category.limit)
        assertEquals(cop(-100_000), before.category.remaining)
        assertEquals(cop(-250_000), reservation.margin)
        assertEquals(ReservedStanding.Short, reservation.standing)
        assertEquals(cop(250_000), reservation.shortfall)
        assertEquals(cop(100_000), reservation.overLimitBy)
        assertEquals(cop(0), before.overall.allowance?.amount)
        // Once recorded: spent 1.050.000, nothing reserved, Excedido by 250.000.
        assertNull(recorded.overall.reservation)
        assertEquals(cop(1_050_000), recorded.category.spent)
        assertEquals(cop(-250_000), recorded.category.remaining)
        assertEquals(Verdict.Over, recorded.verdict)
    }

    @Test
    fun sc37AnUnrecordedPaymentOfAnEarlierMonthOfTheChainIsReservedSoTheHeadlineNeverMoves() {
        // 10 mar, D = 22. ene spent 1.000.000; feb recorded 800.000 and a 200.000 payment due
        // 5 feb that catch-up has not recorded; mar spent 500.000.
        val before = hogarMonth(
            "2027-03-10",
            listOf("2027-01-10" to 1_000_000L, "2027-02-10" to 800_000L, "2027-03-05" to 500_000L),
            listOf(payment("2027-02-05", 200_000))
        )
        val after = hogarMonth(
            "2027-03-10",
            listOf(
                "2027-01-10" to 1_000_000L,
                "2027-02-10" to 800_000L,
                "2027-02-05" to 200_000L,
                "2027-03-05" to 500_000L
            ),
            emptyList()
        )

        assertEquals(cop(1_200_000), before.category.limit)
        assertEquals(cop(700_000), before.category.remaining)
        assertEquals(cop(200_000), before.overall.reservation?.total)
        assertEquals(cop(500_000), before.overall.reservation?.margin)
        assertEquals(cop(22_700), before.overall.allowance?.amount)
        assertEquals(22, before.overall.allowance?.availableDays)
        assertEquals(cop(1_000_000), after.category.limit)
        assertEquals(breakdown(1_000_000, 0, 1_000_000, ene), after.category.carry)
        assertEquals(cop(500_000), after.category.remaining)
        assertNull(after.overall.reservation)
        assertEquals(cop(22_700), after.overall.allowance?.amount)
    }

    @Test
    fun theReservationOfAnEarlierMonthNeedsACarryChainThatCoversBothMonths() {
        val schedules = listOf(payment("2027-02-05", 200_000))
        fun reserved(chains: List<CarryChain>) =
            ReservationPolicy.reserved(schedules, mar, chains).map { it.amount.money }

        // No chain: the MVP rule, an earlier month is not reserved.
        assertEquals(emptyList<Money>(), reserved(emptyList()))
        assertEquals(listOf(cop(200_000)), reserved(listOf(chain(ene, of = hogar))))
        // The chain began after the payment's month, ended before this one, or is another category's.
        assertEquals(emptyList<Money>(), reserved(listOf(chain(mar, of = hogar))))
        assertEquals(emptyList<Money>(), reserved(listOf(chain(ene, mar, of = hogar))))
        assertEquals(emptyList<Money>(), reserved(listOf(chain(ene, of = comida))))
        // A payment of this very month is reserved exactly as before.
        assertEquals(
            listOf(cop(150_000)),
            ReservationPolicy.reserved(listOf(payment("2027-03-28", 150_000)), mar, emptyList())
                .map { it.amount.money }
        )
    }

    @Test
    fun sc38TheForecastIsSpendingAloneAndIsDrawnAgainstTheCapacity() {
        // 10 mar, S 500.000, N 31, d 10: 500.000 x 31 / 10.
        val month = hogarMonth(
            "2027-03-10",
            listOf("2027-01-10" to 1_000_000L, "2027-02-10" to 800_000L, "2027-03-05" to 500_000L),
            listOf(payment("2027-02-05", 200_000))
        )

        val standing = month.category.standing as BudgetStanding.Current
        assertEquals(cop(1_550_000), standing.forecast.rounded)
        assertEquals(cop(1_200_000), month.category.limit)
        assertEquals(Verdict.OnTrack, standing.verdict)
    }

    // --- Ordering, the year boundary and the month closed (SC39, SC41, CP14) ---

    @Test
    fun sc39OrderingByLimitUsesTheBaseNotTheCapacity() {
        // A: base 100.000, K +200.000 (C 300.000). B: base 200.000, K -50.000 (C 150.000).
        val a = Carry.id(10)
        val b = Carry.id(11)
        val expenses = listOf(
            expense(b, "2027-01-10", 250_000),
            expense(a, "2027-02-05", 1_000),
            expense(b, "2027-02-05", 1_000)
        )
        val inputs = listOf(
            CategoryCarryInput(
                a,
                listOf(chain(Carry.m(2026, 12), of = a)),
                listOf(monthly(Carry.m(2026, 12), 100_000, a)),
                emptyList(),
                Carry.history(a, Carry.m(2026, 12), feb, "2027-02-22", *expenses.toTypedArray())
            ),
            CategoryCarryInput(
                b,
                listOf(chain(ene, of = b)),
                listOf(monthly(ene, 200_000, b)),
                emptyList(),
                Carry.history(b, ene, feb, "2027-02-22", *expenses.toTypedArray())
            )
        )
        val snapshot = read(
            "2027-02-22",
            listOf(line(a, "A", 100_000), line(b, "B", 200_000)),
            expenses,
            inputs
        ).valueOrFail()

        val ordered = CategoryOrderPolicy.order(
            snapshot.categories,
            CategoryOrder.Limit,
            CategoryOrderDirection.Descending
        )

        assertEquals(cop(300_000), snapshot.categories.first { it.category.id == a }.budget!!.limit)
        assertEquals(cop(150_000), snapshot.categories.first { it.category.id == b }.budget!!.limit)
        assertEquals(listOf(b, a), ordered.map { it.category.id })
    }

    @Test
    fun sc41TheAllowanceOfFebruaryInALeapYearCountsItsTwentyNineDays() {
        // 22 feb 2028: D = 8; jan spent 40.000 of 100.000 so feb C is 160.000.
        val month =
            comidaMonth("2028-02-22", listOf("2028-01-10" to 40_000L), chainStart = m(2028, 1))

        assertEquals(cop(160_000), month.category.remaining)
        assertEquals(cop(20_000), month.overall.allowance?.amount)
        assertEquals(8, month.overall.allowance?.availableDays)
    }

    @Test
    fun aClosedMonthWithCarryIsJudgedOnItsCapacity() {
        // mar 2027 read from abr 10: S 250.000 > C 210.000 is Excedido (SC01).
        val input = comidaInput(
            listOf(chain(ene)),
            ene,
            mar,
            "2027-04-10",
            listOf("2027-01-10" to 20_000L, "2027-02-10" to 70_000L, "2027-03-10" to 250_000L)
        )
        val expenses = listOf(
            expense(comida, "2027-03-10", 250_000)
        )

        val snapshot = MonthlyBudgetPolicy.snapshot(
            mar,
            FinancialDay(LocalDate.parse("2027-04-10"), bogota),
            1,
            1,
            listOf(line(comida, "Comida", 100_000)),
            expenses,
            carryover = listOf(input)
        ).valueOrFail()

        val budget = snapshot.categories.single().budget!!
        assertEquals(cop(210_000), budget.limit)
        assertEquals(cop(-40_000), budget.remaining)
        assertEquals(BudgetStanding.Past(PastVerdict.Over), budget.standing)
        assertNull((snapshot.overall as OverallBudget.Limited).allowance)
    }

    // --- Failures and the unchanged MVP (CP8, CP23) ---

    @Test
    fun sc06AFigureThatNeedsAnUnreadMonthFailsTheSnapshotInsteadOfShowingTheBase() {
        val unread =
            comidaInput(listOf(chain(ene)), ene, ene, "2027-03-10", listOf("2027-01-10" to 20_000L))

        val failure =
            read("2027-03-10", listOf(line(comida, "Comida", 100_000)), emptyList(), listOf(unread))

        assertEquals(
            SnapshotFailure.Carryover(CarryoverFailure.MissingHistory(feb)),
            failure.errorOrFail()
        )
    }

    @Test
    fun aCapacityThatDoesNotFitFailsTheSnapshotAsUnrepresentable() {
        val big = CategoryCarryInput(
            comida,
            listOf(chain(ene)),
            listOf(monthly(ene, Long.MAX_VALUE)),
            emptyList(),
            Carry.history(comida, ene, feb, "2027-02-10")
        )

        val failure = read(
            "2027-02-10",
            listOf(line(comida, "Comida", Long.MAX_VALUE)),
            emptyList(),
            listOf(big)
        )

        assertEquals(SnapshotFailure.Unrepresentable, failure.errorOrFail())
    }

    @Test
    fun aCarryingCategoryWithoutAFiniteBaseThisMonthFailsTheSnapshot() {
        val input = comidaInput(listOf(chain(ene)), ene, feb, "2027-02-10", emptyList())

        val failure =
            read("2027-02-10", listOf(line(comida, "Comida", null)), emptyList(), listOf(input))

        assertEquals(
            SnapshotFailure.Carryover(CarryoverFailure.NoFiniteBase(feb)),
            failure.errorOrFail()
        )
    }

    @Test
    fun withoutAChainTheSnapshotIsExactlyTheMvpOne() {
        val expenses =
            listOf(expense(comida, "2027-01-10", 250_000), expense(comida, "2027-02-05", 30_000))
        val plain = MonthlyBudgetPolicy.snapshot(
            feb,
            FinancialDay(LocalDate.parse("2027-02-22"), bogota),
            3,
            41,
            listOf(line(comida, "Comida", 100_000)),
            expenses
        ).valueOrFail()
        val off = read(
            "2027-02-22",
            listOf(line(comida, "Comida", 100_000)),
            expenses,
            listOf(comidaInput(emptyList(), ene, feb, "2027-02-22", emptyList()))
        ).valueOrFail()

        assertEquals(plain, off)
        // SC42: every category is off, so the deficit of ene is not guessed.
        assertEquals(cop(100_000), (off.overall as OverallBudget.Limited).budget.limit)
        assertEquals(cop(70_000), (off.overall as OverallBudget.Limited).budget.remaining)
        assertNull(off.categories.single().budget!!.carry)
    }

    @Test
    fun r2TheSuggestionOffersAndRanksByTheBaseNotTheCapacity() {
        // Both categories carry from dic and are over their capacity in dic and ene.
        // X: base 100.000, C 70.000 in ene, C feb 95.000. Y: base 120.000, C feb 60.000.
        val x = Carry.id(10)
        val y = Carry.id(11)
        val dic = m(2026, 12)
        val expenses = listOf(
            expense(x, "2026-12-10", 130_000),
            expense(x, "2027-01-10", 75_000),
            expense(y, "2026-12-10", 200_000),
            expense(y, "2027-01-10", 100_000)
        )
        val categories = listOf(line(x, "X", 100_000), line(y, "Y", 120_000))
        val inputs = listOf(x to 100_000L, y to 120_000L).map { (id, base) ->
            CategoryCarryInput(
                id,
                listOf(chain(dic, of = id)),
                listOf(monthly(dic, base, id)),
                emptyList(),
                Carry.history(id, dic, feb, "2027-02-22", *expenses.toTypedArray())
            )
        }

        val resumen = ResumenPolicy.snapshot(
            feb,
            FinancialDay(LocalDate.parse("2027-02-22"), bogota),
            3,
            41,
            ResumenPolicy.monthsEndingIn(feb).map {
                ResumenMonthInput(it, categories, expenses, inputs)
            },
            emptyList()
        ).valueOrFail()

        // Over C twice (S 200.000 > C 120.000 and S 100.000 > C 40.000 for Y): Y; its limit is
        // the base 120.000, not the capacity 60.000, and the base ranks it above X's 100.000.
        assertEquals(
            com.software.sello.domain.model.LimitAdjustment(
                y,
                Carry.category(y, "Y").name,
                listOf(dic, ene),
                cop(150_000),
                cop(120_000)
            ),
            resumen.suggestion
        )
    }
}
