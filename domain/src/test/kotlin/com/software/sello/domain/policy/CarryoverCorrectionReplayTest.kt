package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SettledBalance
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.Carry.abr
import com.software.sello.domain.policy.Carry.chain
import com.software.sello.domain.policy.Carry.comida
import com.software.sello.domain.policy.Carry.cop
import com.software.sello.domain.policy.Carry.ene
import com.software.sello.domain.policy.Carry.expense
import com.software.sello.domain.policy.Carry.feb
import com.software.sello.domain.policy.Carry.hogar
import com.software.sello.domain.policy.Carry.mar
import com.software.sello.domain.policy.Carry.monthly
import com.software.sello.domain.policy.Carry.own
import com.software.sello.domain.policy.Carry.transporte
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Historical corrections (ADR 0020 CP20–CP22, CP18's catch-up notice) by SC25–SC33, SC37
 * and SC47. The fixture of SC25–SC34 is SC01's spending and chain; the current month is
 * abr 2027 with nothing spent in it. Every figure is copied from
 * docs/testing/signed-carryover-examples.md.
 */
class CarryoverCorrectionReplayTest {
    private val today = "2027-04-10"

    /** SC01's recorded expenses of Comida, as (id, date, pesos). */
    private val sc01 = listOf(
        Triple(1, "2027-01-10", 20_000L),
        Triple(2, "2027-02-10", 70_000L),
        Triple(3, "2027-03-10", 250_000L)
    )

    private fun comidaWith(
        spent: List<Triple<Int, String, Long>> = sc01,
        extra: List<Pair<String, Long>> = emptyList(),
        chains: List<CarryChain>? = null,
        overrides: List<com.software.sello.domain.model.MonthLimit> = emptyList(),
        of: CategoryId = comida,
        base: Long = 100_000,
        from: YearMonth = ene
    ): CategoryCarryInput {
        val expenses: List<Expense> =
            spent.map { expense(of, it.second, it.third) } +
                extra.map { expense(of, it.first, it.second) }
        return CategoryCarryInput(
            of,
            chains ?: listOf(chain(ene, of = of)),
            listOf(monthly(ene, base, of)),
            overrides,
            Carry.history(of, from, abr, today, *expenses.toTypedArray())
        )
    }

    private fun preview(vararg pairs: Pair<CategoryCarryInput, CategoryCarryInput>) =
        CarryoverCorrectionReplay.preview(
            abr,
            pairs.map {
                CategoryCorrection(it.first, it.second)
            }
        )
            .valueOrFail()

    /** (month, K before→after, C before→after, R before→after) of each listed month. */
    private fun CarryCorrection.listed() = months.map {
        listOf(
            it.month,
            it.before.carryIn.minorUnits to it.after.carryIn.minorUnits,
            it.before.capacity.minorUnits to it.after.capacity.minorUnits,
            it.before.remaining.minorUnits to it.after.remaining.minorUnits
        )
    }

    private fun CarryMonthChange.capacity() =
        before.capacity.minorUnits to after.capacity.minorUnits

    private fun CarryMonthChange.remaining() =
        before.remaining.minorUnits to after.remaining.minorUnits

    @Test
    fun sc25DeletingAnEarlyExpenseListsEveryLaterMonthWithItsCapacityBeforeAndAfter() {
        val result = preview(comidaWith() to comidaWith(spent = sc01.drop(1)))

        assertEquals(listOf(feb, mar, abr), result.months.map { it.month })
        assertEquals(
            listOf(180_000L to 200_000L, 210_000L to 230_000L, 60_000L to 80_000L),
            result.months.map { it.capacity() }
        )
        // "By how much" (CP20): each later month moves by the 20.000 taken out of ene.
        assertEquals(
            listOf(cop(20_000), cop(20_000), cop(20_000)),
            result.months.map { it.capacityChange }
        )
        // mar: R -40.000 -> -20.000, Excedido by 40.000 -> 20.000, still Excedido.
        val march = result.months[1]
        assertEquals(-40_000L to -20_000L, march.remaining())
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), march.before.verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), march.after.verdict)
        // Every base stays 100.000; the edited month shows its own remaining.
        assertTrue(result.months.all { it.after.base == cop(100_000) })
        assertEquals(listOf(ene), result.edited.map { it.month })
        assertEquals(80_000L to 100_000L, result.edited.single().remaining())
        assertEquals(ene, result.firstAffected)
        assertEquals(emptyList<SettledBalanceChange>(), result.settled)
        assertEquals(
            mapOf(feb to cop(20_000), mar to cop(20_000), abr to cop(20_000)),
            result.overallCapacityChange
        )
    }

    @Test
    fun sc26MovingAnExpenseBetweenCategoriesChangesBothChains() {
        val transporteBefore = comidaWith(
            spent = listOf(Triple(1, "2027-01-10", 10_000L)),
            of = transporte,
            base = 50_000
        )
        val transporteAfter = comidaWith(
            spent = listOf(Triple(1, "2027-01-10", 10_000L), Triple(2, "2027-02-10", 70_000L)),
            of = transporte,
            base = 50_000
        )

        val result = preview(
            comidaWith() to comidaWith(spent = listOf(sc01[0], sc01[2])),
            transporteBefore to transporteAfter
        )

        val comidaMonths = result.months.filter { it.categoryId == comida }
        val transporteMonths = result.months.filter { it.categoryId == transporte }
        assertEquals(listOf(mar, abr), comidaMonths.map { it.month })
        assertEquals(
            listOf(210_000L to 280_000L, 60_000L to 130_000L),
            comidaMonths.map {
                it.capacity()
            }
        )
        assertEquals(-40_000L to 30_000L, comidaMonths[0].remaining())
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), comidaMonths[0].before.verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), comidaMonths[0].after.verdict)
        assertEquals(listOf(mar, abr), transporteMonths.map { it.month })
        assertEquals(
            listOf(140_000L to 70_000L, 190_000L to 120_000L),
            transporteMonths.map {
                it.capacity()
            }
        )
        assertEquals(
            listOf(cop(70_000), cop(70_000), cop(-70_000), cop(-70_000)),
            (comidaMonths + transporteMonths).map { it.capacityChange }
        )
        // The edited month of each chain: feb R 110.000 -> 180.000 and 90.000 -> 20.000.
        assertEquals(
            listOf(comida to (110_000L to 180_000L), transporte to (90_000L to 20_000L)),
            result.edited.map { it.categoryId to it.remaining() }
        )
        // The overall capacity is unchanged: +70.000 and -70.000 in each month.
        assertEquals(emptyMap<YearMonth, Money>(), result.overallCapacityChange)
        assertEquals(feb, result.firstAffected)
    }

    @Test
    fun sc27MovingAnExpenseToALaterMonthDoesNotListAMonthWhoseCarryDoesNotChange() {
        // The 20.000 of ene moves to mar (same category): mar S 270.000, so its R stays -40.000.
        val moved =
            comidaWith(spent = listOf(sc01[1], sc01[2]), extra = listOf("2027-03-12" to 20_000L))

        val result = preview(comidaWith() to moved)

        assertEquals(listOf(feb, mar), result.months.map { it.month })
        assertEquals(
            listOf(180_000L to 200_000L, 210_000L to 230_000L),
            result.months.map {
                it.capacity()
            }
        )
        assertEquals(-40_000L to -40_000L, result.months[1].remaining())
        assertEquals(270_000L, result.months[1].after.spent.minorUnits)
        // abr stays 60.000 and is not listed.
        assertTrue(result.months.none { it.month == abr })
    }

    @Test
    fun sc28ACorrectedPastLimitShiftsEveryLaterMonthButNotTheirBases() {
        val corrected = comidaWith(overrides = listOf(own(ene, Carry.finite(120_000))))

        val result = preview(comidaWith() to corrected)

        assertEquals(listOf(feb, mar, abr), result.months.map { it.month })
        assertEquals(
            listOf(180_000L to 200_000L, 210_000L to 230_000L, 60_000L to 80_000L),
            result.months.map { it.capacity() }
        )
        assertEquals(-40_000L to -20_000L, result.months[1].remaining())
        assertEquals(80_000L to 100_000L, result.edited.single().remaining())
        assertEquals(ene, result.edited.single().month)
        // feb's and mar's limits stay 100.000.
        assertTrue(result.months.all { it.after.base == cop(100_000) })
    }

    @Test
    fun sc29DeletingAnExpenseThatFlipsAVerdictListsTheFlip() {
        // A 40.000 expense of feb goes: feb S 70.000 -> 30.000.
        val result = preview(
            comidaWith() to
                comidaWith(spent = listOf(sc01[0], Triple(2, "2027-02-10", 30_000L), sc01[2]))
        )

        assertEquals(listOf(mar, abr), result.months.map { it.month })
        assertEquals(
            listOf(210_000L to 250_000L, 60_000L to 100_000L),
            result.months.map {
                it.capacity()
            }
        )
        assertEquals(-40_000L to 0L, result.months[0].remaining())
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), result.months[0].before.verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), result.months[0].after.verdict)
        assertEquals(110_000L to 150_000L, result.edited.single().remaining())
    }

    @Test
    fun sc30AnExpenseAddedLateCanTurnEveryLaterMonthIntoADeficit() {
        val late = comidaWith(extra = listOf("2027-01-15" to 200_000L))

        val result = preview(comidaWith() to late)

        assertEquals(listOf(feb, mar, abr), result.months.map { it.month })
        assertEquals(
            listOf(180_000L to -20_000L, 210_000L to 10_000L, 60_000L to -140_000L),
            result.months.map { it.capacity() }
        )
        assertEquals(110_000L to -90_000L, result.months[0].remaining())
        assertEquals(CarryVerdict.Closed(PastVerdict.WithinLimit), result.months[0].before.verdict)
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), result.months[0].after.verdict)
        assertEquals(-40_000L to -240_000L, result.months[1].remaining())
        assertEquals(80_000L to -120_000L, result.edited.single().remaining())
        // abr's deficit is larger than its base.
        assertEquals(-140_000L, result.months[2].after.capacity.minorUnits)
    }

    @Test
    fun sc31AnEditInTheCurrentMonthListsNothing() {
        val result = preview(comidaWith() to comidaWith(extra = listOf("2027-04-10" to 15_000L)))

        assertEquals(emptyList<CarryMonthChange>(), result.months)
        assertTrue(result.isEmpty)
        assertEquals(abr, result.edited.single().month)
        assertEquals(60_000L to 45_000L, result.edited.single().remaining())
        assertEquals(CarryVerdict.InProgress(Verdict.OnTrack), result.edited.single().after.verdict)
    }

    @Test
    fun sc32ACorrectionAlsoNamesTheBalanceAChainSettledWhenItEnded() {
        // SC01; on 5 abr carry was turned off (chain ene-abr). On 20 abr the 20.000 of ene is deleted.
        val ended = listOf(chain(ene, abr))

        val result = preview(
            comidaWith(chains = ended) to comidaWith(spent = sc01.drop(1), chains = ended)
        )

        assertEquals(listOf(feb, mar), result.months.map { it.month })
        assertEquals(
            listOf(180_000L to 200_000L, 210_000L to 230_000L),
            result.months.map {
                it.capacity()
            }
        )
        assertEquals(-40_000L to -20_000L, result.months[1].remaining())
        assertEquals(
            listOf(
                SettledBalanceChange(
                    comida,
                    SettledBalance(ene, abr, cop(-40_000)),
                    SettledBalance(ene, abr, cop(-20_000)),
                    cop(20_000)
                )
            ),
            result.settled
        )
        // abr is outside the chain: base only, not listed.
        assertTrue(result.months.none { it.month == abr })
        assertTrue(!result.isEmpty)
    }

    @Test
    fun sc33AnExpenseBeforeTheChainOrOfACategoryWithCarryOffChangesNothing() {
        val december = Carry.history(
            comida,
            ene,
            abr,
            today,
            *(
                sc01.map { expense(comida, it.second, it.third) } +
                    expense(comida, "2026-12-05", 30_000)
                )
                .toTypedArray()
        )
        val before = comidaWith()
        val withDecember = CategoryCarryInput(
            comida,
            before.chains,
            before.defaults,
            before.overrides,
            december
        )
        val off = comidaWith(chains = emptyList())

        val beforeChain = preview(before to withDecember)
        val carryOff = preview(off to comidaWith(spent = sc01.drop(1), chains = emptyList()))

        assertTrue(beforeChain.isEmpty)
        assertEquals(emptyList<CarryMonthChange>(), beforeChain.edited)
        assertEquals(null, beforeChain.firstAffected)
        assertTrue(carryOff.isEmpty)
        assertEquals(emptyList<CarryMonthChange>(), carryOff.edited)
        assertEquals(emptyMap<YearMonth, Money>(), carryOff.overallCapacityChange)
    }

    @Test
    fun aCorrectionWhoseResultDoesNotFitIsRefusedNotClipped() {
        val huge = Carry.history(comida, ene, abr, today)
            .plus(LocalDate.parse("2027-01-10"), cop(Long.MAX_VALUE)).valueOrFail()
            .plus(LocalDate.parse("2027-02-10"), cop(Long.MAX_VALUE)).valueOrFail()
        val before = comidaWith()
        val overflow = CategoryCarryInput(
            comida,
            before.chains,
            before.defaults,
            before.overrides,
            huge
        )

        // ene: R = 100.000 - MAX fits; feb then spends MAX more, so R is about -2 x MAX.
        val result = CarryoverCorrectionReplay.preview(
            abr,
            listOf(CategoryCorrection(before, overflow))
        )

        assertEquals(CarryoverFailure.Unrepresentable, result.errorOrFail())
    }

    @Test
    fun aCorrectionThatNeedsAnUnreadMonthFailsInsteadOfListingFewer() {
        val unread = CategoryCarryInput(
            comida,
            listOf(chain(ene)),
            listOf(monthly(ene, 100_000)),
            emptyList(),
            Carry.history(comida, feb, abr, today)
        )

        assertEquals(
            CarryoverFailure.MissingHistory(ene),
            CarryoverCorrectionReplay.preview(
                abr,
                listOf(CategoryCorrection(unread, unread))
            ).errorOrFail()
        )
    }

    // --- Catch-up (CP18, SC37, SC47) ---

    private val rent = ScheduleId.of("00000000-0000-4000-8000-000000000777").valueOrFail()

    private fun due(date: String, pesos: Long, category: CategoryId = hogar) = ReservedPayment(
        rent,
        category,
        null,
        Occurrence(LocalDate.parse(date), 1),
        TransactionAmount.of(cop(pesos)).valueOrFail()
    )

    private fun hogarBefore(): CategoryCarryInput {
        val expenses = listOf(
            expense(hogar, "2027-01-10", 1_000_000),
            expense(hogar, "2027-02-10", 800_000),
            expense(hogar, "2027-03-05", 500_000)
        )
        return CategoryCarryInput(
            hogar,
            listOf(chain(ene, of = hogar)),
            listOf(monthly(ene, 1_000_000, hogar)),
            emptyList(),
            Carry.history(hogar, ene, mar, "2027-03-10", *expenses.toTypedArray())
        )
    }

    @Test
    fun sc37TheOpeningNoticeNamesTheMonthAndTheLaterCapacityAnOccurrenceChanges() {
        val result = CarryoverCorrectionReplay.catchUp(
            hogarBefore(),
            listOf(due("2027-02-05", 200_000)),
            mar
        )
            .valueOrFail()

        // hogar, feb: Restante 200.000 -> 0, still Cumplido; mar: Límite con arrastre 1.200.000 -> 1.000.000.
        assertEquals(feb, result.edited.single().month)
        assertEquals(200_000L to 0L, result.edited.single().remaining())
        assertEquals(
            CarryVerdict.Closed(PastVerdict.WithinLimit),
            result.edited.single().before.verdict
        )
        assertEquals(
            CarryVerdict.Closed(PastVerdict.WithinLimit),
            result.edited.single().after.verdict
        )
        assertEquals(listOf(mar), result.months.map { it.month })
        assertEquals(1_200_000L to 1_000_000L, result.months.single().capacity())
        assertEquals(200_000L, result.months.single().before.carryIn.minorUnits)
        assertEquals(0L, result.months.single().after.carryIn.minorUnits)
        assertEquals(mapOf(mar to cop(-200_000)), result.overallCapacityChange)
    }

    @Test
    fun sc47APaymentLargerThanTheMonthsRemainingFlipsItsVerdictInTheNotice() {
        val result = CarryoverCorrectionReplay.catchUp(
            hogarBefore(),
            listOf(due("2027-02-05", 300_000)),
            mar
        )
            .valueOrFail()

        assertEquals(200_000L, result.edited.single().before.remaining.minorUnits)
        assertEquals(
            CarryVerdict.Closed(PastVerdict.WithinLimit),
            result.edited.single().before.verdict
        )
        assertEquals(-100_000L, result.edited.single().after.remaining.minorUnits)
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), result.edited.single().after.verdict)
        assertEquals(1_200_000L to 900_000L, result.months.single().capacity())
        assertEquals(-100_000L, result.months.single().after.carryIn.minorUnits)
    }

    @Test
    fun sc47DeletingTheOccurrenceFromTheNoticeIsACorrectionWithTheListOfLaterMonths() {
        // Before: the 300.000 is recorded (feb S 1.100.000, mar K -100.000). After: deleted.
        val recorded = hogarBefore().let {
            CategoryCarryInput(
                it.categoryId,
                it.chains,
                it.defaults,
                it.overrides,
                it.spending.plus(LocalDate.parse("2027-02-05"), cop(300_000)).valueOrFail()
            )
        }

        val result = CarryoverCorrectionReplay.preview(
            mar,
            listOf(CategoryCorrection(recorded, hogarBefore()))
        )
            .valueOrFail()

        assertEquals(feb, result.edited.single().month)
        assertEquals(CarryVerdict.Closed(PastVerdict.Over), result.edited.single().before.verdict)
        assertEquals(
            CarryVerdict.Closed(PastVerdict.WithinLimit),
            result.edited.single().after.verdict
        )
        assertEquals(900_000L to 1_200_000L, result.months.single().capacity())
    }

    @Test
    fun catchUpIgnoresOccurrencesOutsideTheChainOrOfOtherCategoriesOrOfThisMonth() {
        val payments = listOf(
            due("2026-12-05", 200_000),
            due("2027-02-05", 200_000, comida),
            due("2027-03-05", 200_000)
        )

        val result = CarryoverCorrectionReplay.catchUp(hogarBefore(), payments, mar).valueOrFail()

        assertTrue(result.isEmpty)
        assertEquals(emptyList<CarryMonthChange>(), result.edited)
    }

    @Test
    fun monthsAreListedInMonthOrderThenCategoryAndOnlyOnceEach() {
        val result = preview(
            comidaWith(
                of = transporte,
                base = 50_000,
                spent = listOf(Triple(1, "2027-01-10", 10_000L))
            ) to
                comidaWith(of = transporte, base = 50_000, spent = emptyList()),
            comidaWith() to comidaWith(spent = sc01.drop(1))
        )

        assertEquals(
            listOf(
                feb to comida,
                feb to transporte,
                mar to comida,
                mar to transporte,
                abr to comida,
                abr to transporte
            ),
            result.months.map { it.month to it.categoryId }
        )
    }
}
