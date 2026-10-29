package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.DropDecision
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SettledBalance
import com.software.sello.domain.model.SpendingHistory
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.Carry.abr
import com.software.sello.domain.policy.Carry.chain
import com.software.sello.domain.policy.Carry.comida
import com.software.sello.domain.policy.Carry.comidaInput
import com.software.sello.domain.policy.Carry.cop
import com.software.sello.domain.policy.Carry.ene
import com.software.sello.domain.policy.Carry.expense
import com.software.sello.domain.policy.Carry.feb
import com.software.sello.domain.policy.Carry.figures
import com.software.sello.domain.policy.Carry.m
import com.software.sello.domain.policy.Carry.mar
import com.software.sello.domain.policy.Carry.monthly
import com.software.sello.domain.policy.Carry.own
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signed carryover calculation by ADR 0020 and docs/testing/signed-carryover-examples.md
 * (SC01–SC07, SC23, SC24, SC40, SC41, SC46 and the refusals of SC08). Every expected figure
 * is copied from there, where it was worked out by hand: `B`, `K`, `C`, `S`, `R` in pesos.
 */
class SignedCarryoverPolicyTest {
    /** Comida on from ene 2027, base 100.000, replayed to [through] with [current] in progress. */
    private fun replay(
        spent: List<Pair<String, Long>>,
        current: YearMonth,
        through: YearMonth = current,
        chains: List<CarryChain> = listOf(chain(ene)),
        from: YearMonth = ene,
        asOf: String = "$current-28"
    ) = SignedCarryoverPolicy.replay(
        comidaInput(chains, from, current, asOf, spent),
        current,
        through
    ).valueOrFail()

    private fun assertMonth(
        replay: com.software.sello.domain.model.CarryReplay,
        month: YearMonth,
        k: Long,
        c: Long,
        s: Long,
        r: Long
    ) {
        val found = replay.months.single { it.month == month }
        assertEquals("B K C S R of $month", listOf(100_000L, k, c, s, r), found.figures())
    }

    @Test
    fun sc01SurplusAndDeficitBothCarryMonthAfterMonth() {
        val read = replay(
            listOf("2027-01-10" to 20_000L, "2027-02-10" to 70_000L, "2027-03-10" to 250_000L),
            current = abr
        )

        assertMonth(read, ene, k = 0, c = 100_000, s = 20_000, r = 80_000)
        assertMonth(read, feb, k = 80_000, c = 180_000, s = 70_000, r = 110_000)
        assertMonth(read, mar, k = 110_000, c = 210_000, s = 250_000, r = -40_000)
        assertMonth(read, abr, k = -40_000, c = 60_000, s = 0, r = 60_000)
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), read.months[0].verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), read.months[1].verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), read.months[2].verdict)
        assertEquals(CarryVerdict.InProgress(Verdict.OnTrack), read.months[3].verdict)
        assertEquals(listOf(ene, ene, ene, ene), read.months.map { it.chainStart })
        assertEquals(emptyList<SettledBalance>(), read.settled)
    }

    @Test
    fun sc02ALaterSurplusDoesNotForgetAnEarlierDeficit() {
        val read = replay(
            listOf("2027-01-10" to 120_000L, "2027-02-10" to 70_000L),
            current = mar
        )

        assertMonth(read, feb, k = -20_000, c = 80_000, s = 70_000, r = 10_000)
        // Not 130.000, which is 100.000 + (100.000 - 70.000) and forgets the deficit.
        assertMonth(read, mar, k = 10_000, c = 110_000, s = 0, r = 110_000)
    }

    @Test
    fun sc03ADeficitLargerThanTheNextBaseLeavesANegativeCapacity() {
        val read = replay(
            listOf("2027-01-10" to 250_000L, "2027-03-10" to 20_000L),
            current = abr
        )

        assertMonth(read, feb, k = -150_000, c = -50_000, s = 0, r = -50_000)
        // Past, S 0 > C -50.000.
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), read.months[1].verdict)
        assertMonth(read, mar, k = -50_000, c = 50_000, s = 20_000, r = 30_000)
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), read.months[2].verdict)
    }

    @Test
    fun sc04SkippedMonthsAreZeroSpendingAndTheirWholeBaseCarries() {
        val read =
            replay(listOf("2027-01-10" to 30_000L), current = m(2027, 5), through = m(2027, 5))

        assertMonth(read, feb, k = 70_000, c = 170_000, s = 0, r = 170_000)
        assertMonth(read, mar, k = 170_000, c = 270_000, s = 0, r = 270_000)
        assertMonth(read, abr, k = 270_000, c = 370_000, s = 0, r = 370_000)
    }

    @Test
    fun sc05ADeficitIsRepaidOnlyByLaterBases() {
        val read = replay(listOf("2027-01-10" to 400_000L), current = m(2027, 5))

        assertMonth(read, ene, k = 0, c = 100_000, s = 400_000, r = -300_000)
        assertMonth(read, feb, k = -300_000, c = -200_000, s = 0, r = -200_000)
        assertMonth(read, mar, k = -200_000, c = -100_000, s = 0, r = -100_000)
        // abr is a closed month with C 0 and nothing spent: Cumplido.
        assertMonth(read, abr, k = -100_000, c = 0, s = 0, r = 0)
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), read.months[3].verdict)
        // may is the month in progress, C 100.000 - 100.000 = 0 and nothing spent: Al día.
        assertMonth(read, m(2027, 5), k = 0, c = 100_000, s = 0, r = 100_000)
    }

    @Test
    fun sc05ZeroCapacityWithNothingSpentIsAlDiaAndOnePesoIsExcedido() {
        // abr is the month in progress with K -100.000, C 0.
        val nothing = replay(listOf("2027-01-10" to 400_000L), current = abr)
        val onePeso = replay(
            listOf("2027-01-10" to 400_000L, "2027-04-02" to 1L),
            current = abr
        )

        assertMonth(nothing, abr, k = -100_000, c = 0, s = 0, r = 0)
        assertEquals(CarryVerdict.InProgress(Verdict.OnTrack), nothing.months.last().verdict)
        assertMonth(onePeso, abr, k = -100_000, c = 0, s = 1, r = -1)
        assertEquals(CarryVerdict.InProgress(Verdict.Over), onePeso.months.last().verdict)
    }

    @Test
    fun sc06AMonthThatCannotBeReadIsAFailureNeverZeroCarry() {
        // History read for ene only; feb's spending is unknown, so mar's carry cannot be derived.
        val unreadFeb = comidaInput(
            listOf(chain(ene)),
            ene,
            ene,
            "2027-03-10",
            listOf("2027-01-10" to 20_000L)
        )

        assertEquals(
            CarryoverFailure.MissingHistory(feb),
            SignedCarryoverPolicy.carryIn(unreadFeb, mar, mar).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.MissingHistory(feb),
            SignedCarryoverPolicy.replay(unreadFeb, mar, mar).errorOrFail()
        )
    }

    @Test
    fun aChainStartingBeforeTheHistoryWasReadIsMissingHistoryNotAStartOfZero() {
        val late = comidaInput(listOf(chain(ene)), feb, feb, "2027-02-10", emptyList())

        assertEquals(
            CarryoverFailure.MissingHistory(ene),
            SignedCarryoverPolicy.replay(late, feb, feb).errorOrFail()
        )
    }

    @Test
    fun sc07TheStartMonthCountsFromDayOneAndOlderMonthsNeverCount() {
        // Turned on 17 feb: ene overran by 40.000 and is not read; feb spent 30.000 + 20.000.
        val input = comidaInput(
            listOf(chain(feb)),
            feb,
            mar,
            "2027-03-15",
            listOf(
                "2027-01-20" to 140_000L,
                "2027-02-05" to 30_000L,
                "2027-02-20" to 20_000L
            )
        )

        val read = SignedCarryoverPolicy.replay(input, mar, mar).valueOrFail()

        assertEquals(listOf(feb, mar), read.months.map { it.month })
        assertEquals(listOf(100_000L, 0L, 100_000L, 50_000L, 50_000L), read.months[0].figures())
        // Not 180.000 (only the 20.000 after the 17th) and not 110.000 (carrying ene's overrun).
        assertEquals(listOf(100_000L, 50_000L, 150_000L, 0L, 150_000L), read.months[1].figures())
        assertNull(SignedCarryoverPolicy.carryIn(input, ene, mar).valueOrFail())
    }

    @Test
    fun sc23ABaseOfZeroCarriesNothingUntilSomethingIsSpent() {
        val zero = listOf(monthly(ene, 0))

        val none = SignedCarryoverPolicy.replay(
            comidaInput(listOf(chain(ene)), ene, feb, "2027-02-10", emptyList(), zero),
            feb,
            feb
        ).valueOrFail()
        val onePeso = SignedCarryoverPolicy.replay(
            comidaInput(
                listOf(chain(ene)),
                ene,
                feb,
                "2027-02-10",
                listOf("2027-02-02" to 1L),
                zero
            ),
            feb,
            feb
        ).valueOrFail()
        val thirty = SignedCarryoverPolicy.replay(
            comidaInput(
                listOf(chain(ene)),
                ene,
                feb,
                "2027-02-10",
                listOf("2027-01-10" to 30_000L),
                zero
            ),
            feb,
            feb
        ).valueOrFail()

        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), none.months[1].figures())
        assertEquals(CarryVerdict.InProgress(Verdict.OnTrack), none.months[1].verdict)
        assertEquals(listOf(0L, 0L, 0L, 1L, -1L), onePeso.months[1].figures())
        assertEquals(CarryVerdict.InProgress(Verdict.Over), onePeso.months[1].verdict)
        assertEquals(listOf(0L, -30_000L, -30_000L, 0L, -30_000L), thirty.months[1].figures())
        assertEquals(CarryVerdict.InProgress(Verdict.Over), thirty.months[1].verdict)
    }

    @Test
    fun sc23ABaseSetToZeroInFebStillSpendsTheCarriedSurplus() {
        // ene spent 20.000 so K feb is 80.000; feb's own base is 0.
        val zeroFeb = listOf(own(feb, BudgetLimit.finite(Money.cop(0)).valueOrFail()))
        fun feb(spent: Long) = SignedCarryoverPolicy.replay(
            comidaInput(
                listOf(chain(ene)),
                ene,
                feb,
                "2027-02-20",
                listOf("2027-01-10" to 20_000L, "2027-02-05" to spent),
                overrides = zeroFeb
            ),
            feb,
            feb
        ).valueOrFail().months[1]

        assertEquals(listOf(0L, 80_000L, 80_000L, 80_000L, 0L), feb(80_000).figures())
        assertEquals(CarryVerdict.InProgress(Verdict.AtLimit), feb(80_000).verdict)
        assertEquals(CarryVerdict.InProgress(Verdict.Over), feb(80_001).verdict)
    }

    @Test
    fun sc24OnlyMonthsFromTheNewDefaultUseTheNewBase() {
        val raised = listOf(monthly(ene, 100_000), monthly(mar, 120_000))
        val lowered = listOf(monthly(ene, 100_000), monthly(mar, 60_000))
        fun read(defaults: List<DefaultLimit>) = SignedCarryoverPolicy.replay(
            comidaInput(
                listOf(chain(ene)),
                ene,
                mar,
                "2027-03-03",
                listOf("2027-01-10" to 20_000L, "2027-02-10" to 70_000L),
                defaults
            ),
            mar,
            mar
        ).valueOrFail().months

        assertEquals(listOf(100_000L, 0L, 100_000L, 20_000L, 80_000L), read(raised)[0].figures())
        assertEquals(
            listOf(100_000L, 80_000L, 180_000L, 70_000L, 110_000L),
            read(raised)[1].figures()
        )
        assertEquals(listOf(120_000L, 110_000L, 230_000L, 0L, 230_000L), read(raised)[2].figures())
        assertEquals(listOf(60_000L, 110_000L, 170_000L, 0L, 170_000L), read(lowered)[2].figures())
    }

    @Test
    fun sc41TheYearBoundaryIsJustTheNextMonth() {
        val input = comidaInput(
            listOf(chain(m(2026, 12))),
            m(2026, 12),
            ene,
            "2027-01-05",
            listOf("2026-12-10" to 90_000L),
            listOf(monthly(m(2026, 12), 100_000))
        )

        val january = SignedCarryoverPolicy.replay(input, ene, ene).valueOrFail().months.last()

        assertEquals(listOf(100_000L, 10_000L, 110_000L, 0L, 110_000L), january.figures())
    }

    @Test
    fun anExpenseDatedAfterTheFinancialDayIsNotSpendingYet() {
        val read = replay(
            listOf("2027-02-05" to 10_000L, "2027-02-25" to 50_000L),
            current = feb,
            asOf = "2027-02-10"
        )

        assertMonth(read, feb, k = 100_000, c = 200_000, s = 10_000, r = 190_000)
    }

    @Test
    fun aChainEndedByADropShowsTheBalanceItSettledAndDoesNotCarryOn() {
        // SC17 shape: ene spent 120.000, so -20.000 arrived in feb where the chain ended.
        val dropped = replay(
            listOf("2027-01-10" to 120_000L, "2027-02-10" to 50_000L),
            current = mar,
            chains = listOf(chain(ene, feb))
        )

        assertEquals(listOf(ene), dropped.months.map { it.month })
        assertEquals(listOf(SettledBalance(ene, feb, cop(-20_000))), dropped.settled)
        assertNull(
            SignedCarryoverPolicy.carryIn(
                comidaInput(listOf(chain(ene, feb)), ene, mar, "2027-03-10", emptyList()),
                mar,
                mar
            ).valueOrFail()
        )
    }

    @Test
    fun sc19TurnedOffAndOnAgainInTheSameMonthStartsFromZeroAgain() {
        val input = comidaInput(
            listOf(chain(ene, feb), chain(feb)),
            ene,
            feb,
            "2027-02-10",
            listOf("2027-01-10" to 120_000L)
        )

        val entry = SignedCarryoverPolicy.carryIn(input, feb, feb).valueOrFail()!!
        val read = SignedCarryoverPolicy.replay(input, feb, feb).valueOrFail()

        assertEquals(cop(0), entry.carryIn)
        assertEquals(feb, entry.chainStart)
        // The first chain keeps its figures and its settled balance.
        assertEquals(listOf(SettledBalance(ene, feb, cop(-20_000))), read.settled)
        assertEquals(listOf(ene, feb), read.months.map { it.month })
    }

    @Test
    fun aMonthOutsideEveryChainHasNoCarryEntryAndIsNotAnError() {
        val input = comidaInput(listOf(chain(feb)), ene, mar, "2027-03-10", emptyList())

        assertNull(SignedCarryoverPolicy.carryIn(input, ene, mar).valueOrFail())
        assertEquals(cop(0), SignedCarryoverPolicy.carryIn(input, feb, mar).valueOrFail()!!.carryIn)
        assertEquals(
            cop(100_000),
            SignedCarryoverPolicy.carryIn(input, mar, mar).valueOrFail()!!.carryIn
        )
    }

    @Test
    fun aMonthWithNoFiniteBaseInsideAChainIsATypedFailure() {
        val unlimitedMarch = comidaInput(
            listOf(chain(ene)),
            ene,
            mar,
            "2027-03-10",
            emptyList(),
            overrides = listOf(own(mar, BudgetLimit.Unlimited))
        )
        val paused = comidaInput(
            listOf(chain(ene)),
            ene,
            mar,
            "2027-03-10",
            emptyList(),
            defaults = listOf(monthly(ene, 100_000), Carry.pausedFrom(feb))
        )
        val unconfigured = comidaInput(
            listOf(chain(ene)),
            ene,
            feb,
            "2027-02-10",
            emptyList(),
            defaults = listOf(monthly(feb, 100_000))
        )

        assertEquals(
            CarryoverFailure.NoFiniteBase(mar),
            SignedCarryoverPolicy.replay(unlimitedMarch, mar, mar).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.NoFiniteBase(feb),
            SignedCarryoverPolicy.replay(paused, mar, mar).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.NoFiniteBase(ene),
            SignedCarryoverPolicy.replay(unconfigured, feb, feb).errorOrFail()
        )
    }

    @Test
    fun damagedChainsAreRefusedNotRepaired() {
        fun withChains(chains: List<CarryChain>) =
            comidaInput(chains, ene, mar, "2027-03-10", emptyList())

        assertEquals(
            CarryoverFailure.OverlappingChains(comida, feb),
            SignedCarryoverPolicy.replay(
                withChains(listOf(chain(ene, mar), chain(feb))),
                mar,
                mar
            ).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.OverlappingChains(comida, mar),
            SignedCarryoverPolicy.replay(
                withChains(listOf(chain(ene), chain(mar))),
                mar,
                mar
            ).errorOrFail()
        )
        // A chain of another category does not belong in this category's input.
        assertEquals(
            CarryoverFailure.InvalidChain(Carry.transporte, ene, feb),
            SignedCarryoverPolicy.replay(
                withChains(listOf(chain(ene, feb, Carry.transporte))),
                mar,
                mar
            ).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.InvalidChain(comida, feb, feb),
            CarryChain.of(comida, feb, feb).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.InvalidChain(comida, feb, ene),
            CarryChain.of(comida, feb, ene).errorOrFail()
        )
    }

    @Test
    fun aMonthAfterTheCurrentOneCannotBeCalculated() {
        val input = comidaInput(listOf(chain(ene)), ene, feb, "2027-02-10", emptyList())

        assertEquals(
            CarryoverFailure.FutureMonth(feb),
            SignedCarryoverPolicy.replay(input, feb, mar).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.FutureMonth(feb),
            SignedCarryoverPolicy.carryIn(input, mar, feb).errorOrFail()
        )
    }

    // --- Bounds (CP23, SC40) ---

    private val max = Long.MAX_VALUE

    private fun huge(
        base: Long,
        spent: Map<LocalDate, Long> = emptyMap(),
        through: YearMonth = feb
    ): CategoryCarryInput {
        var history = Carry.history(comida, ene, through, "2027-02-28")
        for ((date, pesos) in spent) history = history.plus(date, cop(pesos)).valueOrFail()
        return CategoryCarryInput(
            comida,
            listOf(chain(ene)),
            listOf(monthly(ene, base)),
            emptyList(),
            history
        )
    }

    @Test
    fun sc40ACapacityThatDoesNotFitALongFailsInsteadOfClipping() {
        // ene C 9.223.372.036.854.775.807 fits; feb K is the same and C would be 18.446.744.073.709.551.614.
        val input = huge(max)

        val january = SignedCarryoverPolicy.replay(input, feb, ene).valueOrFail().months.single()

        assertEquals(listOf(max, 0L, max, 0L, max), january.figures())
        assertEquals(
            CarryoverFailure.Unrepresentable,
            SignedCarryoverPolicy.replay(input, feb, feb).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.Unrepresentable,
            SignedCarryoverPolicy.carryIn(input, mar, mar).errorOrFail()
        )
    }

    @Test
    fun anIntermediateCapacityThatOverflowsFailsEvenWhenTheRemainingWouldFit() {
        // feb: B max + K max overflows, although spending max would bring R back to max.
        val input = huge(max, mapOf(LocalDate.parse("2027-02-10") to max))

        assertEquals(
            CarryoverFailure.Unrepresentable,
            SignedCarryoverPolicy.replay(input, feb, feb).errorOrFail()
        )
    }

    @Test
    fun sc40ADeficitPastTheSmallestLongFailsTheSameWayAndTheSmallestItselfFits() {
        val onTheEdge = huge(
            0,
            mapOf(
                LocalDate.parse("2027-01-10") to max,
                LocalDate.parse("2027-02-10") to 1L
            )
        )
        val pastIt = huge(
            0,
            mapOf(
                LocalDate.parse("2027-01-10") to max,
                LocalDate.parse("2027-02-10") to 2L
            )
        )

        val feb = SignedCarryoverPolicy.replay(onTheEdge, feb, feb).valueOrFail().months.last()

        assertEquals(listOf(0L, -max, -max, 1L, Long.MIN_VALUE), feb.figures())
        assertEquals(
            CarryoverFailure.Unrepresentable,
            SignedCarryoverPolicy.replay(pastIt, Carry.feb, Carry.feb).errorOrFail()
        )
    }

    @Test
    fun spendingThatDoesNotFitALongIsUnrepresentableNotWrapped() {
        val history = Carry.history(comida, ene, feb, "2027-02-10")
            .plus(LocalDate.parse("2027-01-10"), cop(max)).valueOrFail()

        assertEquals(
            CarryoverFailure.Unrepresentable,
            history.plus(LocalDate.parse("2027-01-11"), cop(1)).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.MissingHistory(abr),
            history.plus(LocalDate.parse("2027-04-11"), cop(1)).errorOrFail()
        )
        assertEquals(
            CarryoverFailure.InvalidHistoryRange(feb, ene),
            SpendingHistory.of(
                comida,
                feb,
                ene,
                LocalDate.parse("2027-02-10"),
                emptyList()
            ).errorOrFail()
        )
    }

    // --- Turning on (CP3, SC08, SC46) ---

    private fun enable(
        month: YearMonth = feb,
        archived: Boolean = false,
        chains: List<CarryChain> = emptyList(),
        defaults: List<DefaultLimit> = listOf(monthly(ene, 100_000)),
        overrides: List<com.software.sello.domain.model.MonthLimit> = emptyList()
    ) = SignedCarryoverPolicy.enable(comida, archived, month, chains, defaults, overrides)

    @Test
    fun turningOnStartsAnOpenChainAtZeroInTheCurrentMonth() {
        val earlier = chain(ene, feb)

        val chains = enable(chains = listOf(earlier)).valueOrFail()

        assertEquals(listOf(earlier, chain(feb)), chains)
        assertEquals(listOf(chain(feb)), enable().valueOrFail())
    }

    @Test
    fun sc08ABaseOfZeroAndTheLargestBaseAreAccepted() {
        assertEquals(listOf(chain(feb)), enable(defaults = listOf(monthly(ene, 0))).valueOrFail())
        assertEquals(
            listOf(chain(feb)),
            enable(defaults = listOf(monthly(ene, 999_999_999_999))).valueOrFail()
        )
    }

    @Test
    fun sc08EachRefusalHasItsOwnReasonAndChangesNothing() {
        assertEquals(
            CarryEnableRefusal.UnlimitedThisMonth,
            enable(defaults = listOf(Carry.unlimitedFrom(ene))).errorOrFail()
        )
        assertEquals(CarryEnableRefusal.Archived, enable(archived = true).errorOrFail())
        assertEquals(
            CarryEnableRefusal.NoLimitThisMonth,
            enable(defaults = listOf(monthly(ene, 100_000), Carry.pausedFrom(feb))).errorOrFail()
        )
        assertEquals(
            CarryEnableRefusal.NoLimitThisMonth,
            enable(defaults = listOf(monthly(mar, 100_000))).errorOrFail()
        )
        assertEquals(
            CarryEnableRefusal.AlreadyOn,
            enable(chains = listOf(chain(ene))).errorOrFail()
        )
        assertEquals(
            CarryEnableRefusal.BaseAboveLimit(cop(1_000_000_000_000)),
            enable(defaults = listOf(monthly(ene, 1_000_000_000_000))).errorOrFail()
        )
    }

    @Test
    fun sc46ACategoryWhoseDefaultAfterThisMonthIsNotFiniteCannotTurnCarryOn() {
        // Default unlimited, feb's own limit 100.000: mar would take an unlimited base.
        val ownOnly = enable(
            defaults = listOf(Carry.unlimitedFrom(ene)),
            overrides = listOf(own(feb, Carry.finite(100_000)))
        )
        val pausedAfter = enable(
            defaults = listOf(monthly(ene, 100_000), Carry.pausedFrom(mar))
        )
        // The default becomes 100.000 from feb: accepted.
        val finiteDefault =
            enable(defaults = listOf(Carry.unlimitedFrom(ene), monthly(feb, 100_000)))
        val hugeDefault = enable(
            defaults = listOf(monthly(ene, 100_000), monthly(mar, 1_000_000_000_000))
        )

        assertEquals(CarryEnableRefusal.DefaultAfterNotFinite, ownOnly.errorOrFail())
        assertEquals(CarryEnableRefusal.DefaultAfterNotFinite, pausedAfter.errorOrFail())
        assertEquals(listOf(chain(feb)), finiteDefault.valueOrFail())
        assertEquals(
            CarryEnableRefusal.BaseAboveLimit(cop(1_000_000_000_000)),
            hugeDefault.errorOrFail()
        )
    }

    // --- Dropping the balance (CP9, CP10, SC17, SC18) ---

    /** Fixture of SC17: ene spent 120.000, so feb K is -20.000; 10 feb, spent so far 30.000. */
    private fun dropInput(
        spentJanuary: Long = 120_000,
        chains: List<CarryChain> = listOf(chain(ene))
    ) = comidaInput(
        chains,
        ene,
        feb,
        "2027-02-10",
        listOf("2027-01-10" to spentJanuary, "2027-02-05" to 30_000L)
    )

    @Test
    fun sc17TheBalanceMustBeConfirmedAndTheChainEndsInTheCurrentMonth() {
        val unconfirmed = SignedCarryoverPolicy.drop(dropInput(), feb, null).valueOrFail()
        val wrong = SignedCarryoverPolicy.drop(dropInput(), feb, cop(-10_000)).valueOrFail()
        val confirmed = SignedCarryoverPolicy.drop(dropInput(), feb, cop(-20_000)).valueOrFail()

        val preview = DropPreview(
            month = feb,
            carryIn = cop(-20_000),
            base = cop(100_000),
            capacityBefore = cop(80_000),
            capacityAfter = cop(100_000),
            remainingBefore = cop(50_000),
            remainingAfter = cop(70_000)
        )
        assertEquals(DropDecision.ConfirmationRequired(preview), unconfirmed)
        // A confirmation of another balance changes nothing and shows the real one.
        assertEquals(DropDecision.ConfirmationRequired(preview), wrong)
        assertEquals(DropDecision.Dropped(listOf(chain(ene, feb)), cop(-20_000)), confirmed)
    }

    @Test
    fun sc18ASurplusIsConfirmedTheSameWay() {
        val input = dropInput(spentJanuary = 20_000)

        val unconfirmed = SignedCarryoverPolicy.drop(input, feb, null).valueOrFail()

        assertEquals(
            DropDecision.ConfirmationRequired(
                DropPreview(
                    feb,
                    cop(80_000),
                    cop(100_000),
                    cop(180_000),
                    cop(100_000),
                    cop(150_000),
                    cop(70_000)
                )
            ),
            unconfirmed
        )
        assertEquals(
            DropDecision.Dropped(listOf(chain(ene, feb)), cop(80_000)),
            SignedCarryoverPolicy.drop(input, feb, cop(80_000)).valueOrFail()
        )
    }

    @Test
    fun sc18WithNothingCarriedInThereIsNothingToConfirmAndNoChainRemains() {
        val startedThisMonth = dropInput(chains = listOf(chain(feb)))
        val zeroBalance = dropInput(spentJanuary = 100_000)

        assertEquals(
            DropDecision.Dropped(emptyList(), cop(0)),
            SignedCarryoverPolicy.drop(startedThisMonth, feb, null).valueOrFail()
        )
        assertEquals(
            DropDecision.Dropped(listOf(chain(ene, feb)), cop(0)),
            SignedCarryoverPolicy.drop(zeroBalance, feb, null).valueOrFail()
        )
    }

    @Test
    fun droppingKeepsEarlierChainsAndSayingNothingWhenNotCarrying() {
        val earlier = chain(ene, feb)
        val input = comidaInput(
            listOf(earlier, chain(mar)),
            ene,
            abr,
            "2027-04-10",
            listOf("2027-03-10" to 20_000L)
        )

        val dropped = SignedCarryoverPolicy.drop(input, abr, cop(80_000)).valueOrFail()

        assertEquals(DropDecision.Dropped(listOf(earlier, chain(mar, abr)), cop(80_000)), dropped)
        assertEquals(
            DropDecision.NotCarrying,
            SignedCarryoverPolicy.drop(dropInput(chains = emptyList()), feb, null).valueOrFail()
        )
    }

    @Test
    fun droppingNeverZeroesAFailedRead() {
        val unread = comidaInput(listOf(chain(ene)), feb, feb, "2027-02-10", emptyList())

        assertEquals(
            CarryoverFailure.MissingHistory(ene),
            SignedCarryoverPolicy.drop(unread, feb, null).errorOrFail()
        )
    }

    @Test
    fun sc17AfterTheDropTheNextMonthStartsFromItsBaseAndTheBalanceIsSettled() {
        // feb closes with 50.000 spent: mar's C is 100.000, not 130.000.
        val afterwards = comidaInput(
            listOf(chain(ene, feb)),
            ene,
            mar,
            "2027-03-10",
            listOf("2027-01-10" to 120_000L, "2027-02-10" to 50_000L)
        )

        assertNull(SignedCarryoverPolicy.carryIn(afterwards, mar, mar).valueOrFail())
        assertTrue(
            SignedCarryoverPolicy.replay(afterwards, mar, mar).valueOrFail().months.none {
                it.month >=
                    feb
            }
        )
    }
}
