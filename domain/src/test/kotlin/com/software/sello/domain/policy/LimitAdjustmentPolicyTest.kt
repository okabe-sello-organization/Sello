package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Forecast
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.Reservation
import com.software.sello.domain.model.ReservedStanding
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.valueOrFail
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The AJ examples in `docs/testing/mvp-financial-examples.md`. Each month below is the
 * snapshot those examples describe, written out by hand; every expected figure is the
 * one the example states.
 */
class LimitAdjustmentPolicyTest {
    private val recorded = Instant.parse("2026-10-22T15:00:00Z")
    private val names = mutableMapOf<String, Int>()

    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(name: String) =
        CategoryId.of(uuid(names.getOrPut(name) { 100 + names.size })).valueOrFail()

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun finite(pesos: Long) = BudgetLimit.finite(cop(pesos)).valueOrFail()

    private val current = BudgetStanding.Current(
        Forecast(BigInteger.ZERO, 1, cop(0)),
        Verdict.OnTrack
    )

    /** A category with [limit] (none when null) that spent [spent] in October. */
    private fun line(name: String, limit: Long?, spent: Long) = CategoryMonth(
        category = Category(
            id(name),
            CategoryName.of(name).valueOrFail(),
            IconKey.of("shopping_cart").valueOrFail(),
            archived = false,
            version = 1,
            createdAt = recorded,
            updatedAt = recorded
        ),
        state = MonthBudgetState.Limited(
            limit?.let(::finite) ?: BudgetLimit.Unlimited,
            explicit = false
        ),
        spent = cop(spent),
        budget = limit?.let { LimitedBudget(cop(it), cop(spent), cop(it - spent), current) },
        expenseCount = 1
    )

    private fun month(
        lines: List<CategoryMonth>,
        limit: Long,
        spent: Long,
        reservation: Reservation? = null
    ) = MonthlySnapshot(
        month = YearMonth.of(2026, 10),
        asOf = LocalDate.of(2026, 10, 22),
        zone = ZoneId.of("America/Bogota"),
        generation = 1,
        revision = 1,
        categories = lines,
        spent = cop(spent),
        overall = OverallBudget.Limited(
            LimitedBudget(cop(limit), cop(spent), cop(limit - spent), current),
            allowance = null,
            reservation = reservation
        ),
        futureDatedExpenses = 0
    )

    /** Excedido: L 1.000.000, E 1.080.000; Café 50.000 with 90.000 spent. */
    private val excedido = month(
        listOf(
            line("Café", 50_000, 90_000),
            line("Comida", 200_000, 230_000),
            line("Transporte", 100_000, 120_000),
            line("Ocio", 100_000, 110_000),
            line("Hogar", 400_000, 400_000),
            line("Salud", 80_000, 60_000),
            line("Ropa", 70_000, 70_000)
        ),
        limit = 1_000_000,
        spent = 1_080_000
    )

    /** Revisar: L 1.000.000, E 900.000 (R 100.000), Q 150.000 reserved. */
    private val revisar = month(
        listOf(
            line("Café", 50_000, 90_000),
            line("Comida", 200_000, 200_000),
            line("Transporte", 100_000, 100_000),
            line("Ocio", 100_000, 100_000),
            line("Hogar", 400_000, 300_000),
            line("Salud", 80_000, 60_000),
            line("Ropa", 70_000, 50_000)
        ),
        limit = 1_000_000,
        spent = 900_000,
        reservation = Reservation(
            payments = emptyList(),
            total = cop(150_000),
            margin = cop(-50_000),
            standing = ReservedStanding.Short,
            shortfall = cop(50_000)
        )
    )

    @Test
    fun excedidoOffersWhatWasSpentAndWhatCoversTheMonth() {
        // AJ04
        assertEquals(
            LimitShortcuts(spent = cop(90_000), coverMonth = cop(130_000), coverPayments = null),
            LimitAdjustmentPolicy.shortcuts(excedido, id("Café")).valueOrFail()
        )
    }

    @Test
    fun revisarOffersWhatCoversThePayments() {
        // AJ05
        assertEquals(
            LimitShortcuts(spent = cop(90_000), coverMonth = null, coverPayments = cop(100_000)),
            LimitAdjustmentPolicy.shortcuts(revisar, id("Café")).valueOrFail()
        )
    }

    @Test
    fun aCategoryWithinItsLimitIsNotOfferedWhatItSpent() {
        // AJ04: Salud spent 60.000 of its 80.000.
        assertEquals(
            LimitShortcuts(spent = null, coverMonth = cop(160_000), coverPayments = null),
            LimitAdjustmentPolicy.shortcuts(excedido, id("Salud")).valueOrFail()
        )
    }

    @Test
    fun previewWithoutPaymentsSaysWhatTheMonthWouldHaveLeft() {
        // AJ06
        fun preview(pesos: Long) =
            LimitAdjustmentPolicy.preview(excedido, id("Café"), finite(pesos)).valueOrFail()

        assertEquals(
            LimitPreview(cop(1_100_000), cop(20_000), afterPayments = false),
            preview(150_000)
        )
        assertEquals(LimitPreview(cop(1_080_000), cop(0), afterPayments = false), preview(130_000))
        assertEquals(
            LimitPreview(cop(1_040_000), cop(-40_000), afterPayments = false),
            preview(90_000)
        )
    }

    @Test
    fun previewWithPaymentsSubtractsThem() {
        // AJ07
        fun preview(pesos: Long) =
            LimitAdjustmentPolicy.preview(revisar, id("Café"), finite(pesos)).valueOrFail()

        assertEquals(
            LimitPreview(cop(1_070_000), cop(20_000), afterPayments = true),
            preview(120_000)
        )
        assertEquals(LimitPreview(cop(1_050_000), cop(0), afterPayments = true), preview(100_000))
        assertEquals(
            LimitPreview(cop(1_010_000), cop(-40_000), afterPayments = true),
            preview(60_000)
        )
    }

    @Test
    fun aCategoryWithoutALimitIsCoveredFromZero() {
        // AJ08: Hogar holds 480.000 so the limits still add up to 1.000.000.
        val month = month(
            listOf(
                line("Café", 50_000, 90_000),
                line("Comida", 200_000, 230_000),
                line("Transporte", 100_000, 120_000),
                line("Ocio", 100_000, 110_000),
                line("Hogar", 480_000, 400_000),
                line("Ropa", 70_000, 70_000),
                line("Salud", null, 60_000)
            ),
            limit = 1_000_000,
            spent = 1_080_000
        )

        assertEquals(
            LimitShortcuts(spent = cop(60_000), coverMonth = cop(80_000), coverPayments = null),
            LimitAdjustmentPolicy.shortcuts(month, id("Salud")).valueOrFail()
        )
        assertEquals(
            LimitPreview(cop(1_080_000), cop(0), afterPayments = false),
            LimitAdjustmentPolicy.preview(month, id("Salud"), finite(80_000)).valueOrFail()
        )
    }

    @Test
    fun orderPutsOverspentFirstThenOpenCategoriesApart() {
        // AJ09
        val order = LimitAdjustmentPolicy.order(
            listOf(
                line("Salud", 80_000, 60_000),
                line("Ropa", 70_000, 70_000),
                line("Regalos", null, 10_000),
                line("Comida", 200_000, 230_000),
                line("Libros", null, 0),
                line("Hogar", 400_000, 400_000),
                line("Café", 50_000, 90_000)
            )
        )

        assertEquals(
            listOf("Café", "Comida", "Hogar", "Ropa", "Salud"),
            order.limited.map { it.category.name.value }
        )
        assertEquals(listOf("Regalos"), order.open.map { it.category.name.value })
    }

    @Test
    fun aTotalThatDoesNotFitIsAFailure() {
        val huge = Long.MAX_VALUE - 1
        val month = month(
            listOf(line("Café", huge, Long.MAX_VALUE), line("Comida", 200_000, 0)),
            limit = 1_000_000,
            spent = 1_080_000
        )

        assertEquals(
            SnapshotFailure.Unrepresentable,
            LimitAdjustmentPolicy.shortcuts(month, id("Café")).errorOrFail()
        )
        assertEquals(
            SnapshotFailure.Unrepresentable,
            LimitAdjustmentPolicy.preview(month, id("Comida"), finite(huge)).errorOrFail()
        )
    }
}
