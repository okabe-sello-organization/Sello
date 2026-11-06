package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.Money
import com.software.sello.domain.valueOrFail
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The drop question the category form asks before anything is sent (SELLO-188, CP10), from
 * a month's snapshot figures. Every figure is worked out by hand: `C = B + K`, `R = C − S`,
 * and after the drop `C = B`, `R = B − S`.
 */
class DropPreviewTest {
    private val mar = YearMonth.of(2027, 3)
    private val ene = YearMonth.of(2027, 1)

    @Test
    fun aDeficitShowsTheMonthBeforeAndAfterForgettingIt() {
        // B 100.000, K −20.000, C 80.000, S 30.000: R 50.000 before, 70.000 after.
        val preview = SignedCarryoverPolicy.dropPreview(
            mar,
            CarryBreakdown(Money.cop(100_000), Money.cop(-20_000), Money.cop(80_000), ene),
            Money.cop(30_000)
        ).valueOrFail()
        assertEquals(
            DropPreview(
                month = mar,
                carryIn = Money.cop(-20_000),
                base = Money.cop(100_000),
                capacityBefore = Money.cop(80_000),
                capacityAfter = Money.cop(100_000),
                remainingBefore = Money.cop(50_000),
                remainingAfter = Money.cop(70_000)
            ),
            preview
        )
    }

    @Test
    fun aSurplusOverspentThisMonthKeepsItsSignedRemaining() {
        // B 100.000, K +80.000, C 180.000, S 150.000: R 30.000 before, −50.000 after.
        val preview = SignedCarryoverPolicy.dropPreview(
            mar,
            CarryBreakdown(Money.cop(100_000), Money.cop(80_000), Money.cop(180_000), ene),
            Money.cop(150_000)
        ).valueOrFail()
        assertEquals(Money.cop(30_000), preview?.remainingBefore)
        assertEquals(Money.cop(-50_000), preview?.remainingAfter)
        assertEquals(Money.cop(100_000), preview?.capacityAfter)
    }

    @Test
    fun nothingCarriedInHasNothingToAskAbout() {
        val zero = CarryBreakdown(Money.cop(100_000), Money.cop(0), Money.cop(100_000), ene)
        assertNull(SignedCarryoverPolicy.dropPreview(mar, zero, Money.cop(5_000)).valueOrFail())
        val noChain = CarryBreakdown(Money.cop(100_000), Money.cop(0), Money.cop(100_000), null)
        assertNull(SignedCarryoverPolicy.dropPreview(mar, noChain, Money.cop(5_000)).valueOrFail())
    }

    @Test
    fun aRemainingThatDoesNotFitIsATypedFailureNeverClipped() {
        // C = Long.MIN_VALUE + 1 and S = 2: C − S is below Long.MIN_VALUE.
        val low = CarryBreakdown(
            Money.cop(1),
            Money.cop(Long.MIN_VALUE + 1),
            Money.cop(Long.MIN_VALUE + 1),
            ene
        )
        assertEquals(
            CarryoverFailure.Unrepresentable,
            SignedCarryoverPolicy.dropPreview(mar, low, Money.cop(2)).errorOrFail()
        )
    }
}
