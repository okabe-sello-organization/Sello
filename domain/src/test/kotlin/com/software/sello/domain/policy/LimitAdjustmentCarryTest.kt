package com.software.sello.domain.policy

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.Forecast
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.Reservation
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.Carry.comida
import com.software.sello.domain.policy.Carry.cop
import com.software.sello.domain.policy.Carry.ene
import com.software.sello.domain.policy.Carry.feb
import com.software.sello.domain.policy.Carry.hogar
import com.software.sello.domain.policy.Carry.mar
import com.software.sello.domain.policy.Carry.transporte
import com.software.sello.domain.valueOrFail
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The limit sheet once a category carries (ADR 0020 CP16, SELLO-038 consumer boundary,
 * SELLO-041 ruling): the sheet edits the **base**, so its shortcuts offer bases and its
 * preview gives the capacity `Σ base + Σ K`. Each month below is written out by hand; each
 * expected figure is worked out in the comment beside it, never by the code under test.
 */
class LimitAdjustmentCarryTest {
    private val standing = BudgetStanding.Current(
        Forecast(BigInteger.ZERO, 1, cop(0)),
        Verdict.OnTrack
    )

    private fun budget(
        base: Long,
        k: Long?,
        spent: Long,
        chainStart: YearMonth? = ene
    ): LimitedBudget {
        val capacity = base + (k ?: 0)
        return LimitedBudget(
            limit = cop(capacity),
            spent = cop(spent),
            remaining = cop(capacity - spent),
            standing = standing,
            carry = k?.let { CarryBreakdown(cop(base), cop(it), cop(capacity), chainStart) }
        )
    }

    private fun line(id: CategoryId, name: String, base: Long, k: Long?, spent: Long) =
        CategoryMonth(
            category = Carry.category(id, name),
            state = MonthBudgetState.Limited(Carry.finite(base), explicit = false),
            spent = cop(spent),
            budget = budget(base, k, spent),
            expenseCount = 1
        )

    private fun month(
        month: YearMonth,
        day: Int,
        lines: List<CategoryMonth>,
        overall: LimitedBudget,
        reservation: Reservation? = null
    ) = MonthlySnapshot(
        month = month,
        asOf = month.atDay(day),
        zone = ZoneId.of("America/Bogota"),
        generation = 1,
        revision = 1,
        categories = lines,
        spent = overall.spent,
        overall = OverallBudget.Limited(overall, allowance = null, reservation = reservation),
        futureDatedExpenses = 0
    )

    /**
     * SC11 for Comida (ene spent 250.000, so feb K −150.000, C −50.000, nothing spent), with
     * Hogar 400.000 (spent 380.000) and Transporte 50.000 (spent 40.000), both off; 22 feb.
     * Overall: Σ base 550.000, Σ K −150.000, C 400.000, S 420.000, R −20.000 (Excedido).
     */
    private val excedido = month(
        feb,
        22,
        listOf(
            line(comida, "Comida", 100_000, -150_000, 0),
            line(hogar, "Hogar", 400_000, null, 380_000),
            line(transporte, "Transporte", 50_000, null, 40_000)
        ),
        overall = budget(550_000, -150_000, 420_000, chainStart = null)
    )

    /**
     * SC36: Hogar base 1.000.000, K −200.000, C 800.000, mar spent 900.000 (R −100.000),
     * 150.000 reserved: margin −250.000, Revisar, short by 250.000; 15 mar.
     */
    private val revisar = month(
        mar,
        15,
        listOf(line(hogar, "Hogar", 1_000_000, -200_000, 900_000)),
        overall = budget(1_000_000, -200_000, 900_000, chainStart = null),
        reservation = Reservation(
            payments = emptyList(),
            total = cop(150_000),
            margin = cop(-250_000),
            standing = ReservedStanding.Short,
            shortfall = cop(250_000),
            overLimitBy = cop(100_000)
        )
    )

    @Test
    fun anEarlierDeficitIsCoveredByTheBaseThatReachesSpendingAndTheMonth() {
        // Lo gastado: the base that makes C = S is S − K = 0 − (−150.000) = 150.000
        // (150.000 − 150.000 = C 0 = S). Taking C instead would offer 0, as the base alone.
        // Cubre el mes: base − overall R = 100.000 − (−20.000) = 120.000
        // (overall C becomes 570.000 − 150.000 = 420.000 = S). Taking C would offer −30.000.
        assertEquals(
            LimitShortcuts(spent = cop(150_000), coverMonth = cop(120_000), coverPayments = null),
            LimitAdjustmentPolicy.shortcuts(excedido, comida).valueOrFail()
        )
    }

    @Test
    fun aSurplusSpentPastItsCapacityOffersTheBaseThatReachesSpending() {
        // Comida base 100.000 with K +80.000 (C 180.000) spent 200.000: S − K = 200.000 −
        // 80.000 = 120.000, so C = 120.000 + 80.000 = 200.000 = S. Offering S would give
        // C 280.000, more than was needed. Overall: base 100.000, K +80.000, R −20.000:
        // Cubre el mes = 100.000 − (−20.000) = 120.000.
        val surplus = month(
            feb,
            22,
            listOf(line(comida, "Comida", 100_000, 80_000, 200_000)),
            overall = budget(100_000, 80_000, 200_000, chainStart = null)
        )

        assertEquals(
            LimitShortcuts(spent = cop(120_000), coverMonth = cop(120_000), coverPayments = null),
            LimitAdjustmentPolicy.shortcuts(surplus, comida).valueOrFail()
        )
    }

    @Test
    fun paymentsAreCoveredFromTheBaseNotFromTheCapacity() {
        // Lo gastado: S 900.000 > C 800.000, so S − K = 900.000 − (−200.000) = 1.100.000.
        // Cubre tus pagos: base + shortfall = 1.000.000 + 250.000 = 1.250.000; then C is
        // 1.050.000, R 150.000 and M 150.000 − 150.000 = 0. From C it would be 1.050.000,
        // leaving M −200.000.
        assertEquals(
            LimitShortcuts(
                spent = cop(1_100_000),
                coverMonth = null,
                coverPayments = cop(1_250_000)
            ),
            LimitAdjustmentPolicy.shortcuts(revisar, hogar).valueOrFail()
        )
    }

    @Test
    fun thePreviewGivesTheCapacityWithTheCarryOfTheMonth() {
        // Comida's base 100.000 → 150.000: Σ base 550.000 − 100.000 + 150.000 = 600.000;
        // capacity 600.000 + (−150.000) = 450.000; left 450.000 − 420.000 = 30.000.
        // Without Σ K it would say 180.000 left.
        assertEquals(
            LimitPreview(
                total = cop(600_000),
                left = cop(30_000),
                afterPayments = false,
                carry = CarryBreakdown(cop(600_000), cop(-150_000), cop(450_000), chainStart = null)
            ),
            LimitAdjustmentPolicy.preview(excedido, comida, Carry.finite(150_000)).valueOrFail()
        )
    }

    @Test
    fun thePreviewAfterPaymentsTakesTheCarryToo() {
        // Hogar's base → 1.250.000: capacity 1.250.000 − 200.000 = 1.050.000; left after
        // payments 1.050.000 − 900.000 − 150.000 = 0.
        assertEquals(
            LimitPreview(
                total = cop(1_250_000),
                left = cop(0),
                afterPayments = true,
                carry = CarryBreakdown(
                    cop(1_250_000),
                    cop(-200_000),
                    cop(1_050_000),
                    chainStart = null
                )
            ),
            LimitAdjustmentPolicy.preview(
                revisar,
                hogar,
                BudgetLimit.finite(cop(1_250_000)).valueOrFail()
            )
                .valueOrFail()
        )
    }

    @Test
    fun withoutCarryThePreviewHasNoBreakdown() {
        // Hogar alone, base 400.000, spent 380.000, no chain; → 450.000: left 70.000.
        val plain = month(
            YearMonth.from(LocalDate.of(2027, 2, 1)),
            22,
            listOf(line(hogar, "Hogar", 400_000, null, 380_000)),
            overall = budget(400_000, null, 380_000)
        )

        assertEquals(
            LimitPreview(
                total = cop(450_000),
                left = cop(70_000),
                afterPayments = false,
                carry = null
            ),
            LimitAdjustmentPolicy.preview(plain, hogar, Carry.finite(450_000)).valueOrFail()
        )
    }
}
