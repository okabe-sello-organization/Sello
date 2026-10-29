package com.software.sello.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutRulesTest {
    private fun layout(width: Int, height: Int) = selloWindowLayout(width.dp, height.dp)

    @Test
    fun aPhoneUprightIsOneColumnWithTabBarAndDock() {
        for ((w, h) in listOf(360 to 760, 411 to 891, 599 to 900, 320 to 480)) {
            assertEquals(
                "$w x $h",
                SelloWindowLayout(useRail = false, twoPane = false),
                layout(w, h)
            )
        }
    }

    @Test
    fun aShortWindowUsesARailAndTwoEvenColumnsHoweverWideItIs() {
        for ((w, h) in listOf(760 to 360, 891 to 411, 640 to 479, 1000 to 400, 500 to 300)) {
            assertEquals(
                "$w x $h",
                SelloWindowLayout(useRail = true, twoPane = true),
                layout(w, h)
            )
        }
    }

    @Test
    fun mediumWidthKeepsTheTabBarAndCapsOneColumnAt560() {
        for ((w, h) in listOf(600 to 900, 700 to 1000, 839 to 480)) {
            assertEquals(
                "$w x $h",
                SelloWindowLayout(
                    useRail = false,
                    twoPane = false,
                    contentMaxWidth = 560.dp
                ),
                layout(w, h)
            )
        }
    }

    @Test
    fun expandedWidthUsesARailA400ListPaneAnd420SidePanels() {
        for ((w, h) in listOf(840 to 600, 1280 to 800, 840 to 480)) {
            assertEquals(
                "$w x $h",
                SelloWindowLayout(
                    useRail = true,
                    twoPane = true,
                    listPaneWidth = 400.dp,
                    sidePanelWidth = 420.dp
                ),
                layout(w, h)
            )
        }
    }

    @Test
    fun categoryCellsUseTwoColumnsThreeWhenWideAndOneAtLargeType() {
        assertEquals(2, categoryColumns(336.dp, 1.0f))
        assertEquals(2, categoryColumns(336.dp, 1.29f))
        assertEquals(1, categoryColumns(336.dp, 1.3f))
        assertEquals(1, categoryColumns(336.dp, 2.0f))
        assertEquals(3, categoryColumns(560.dp, 1.0f))
        assertEquals(3, categoryColumns(600.dp, 1.0f))
        assertEquals(2, categoryColumns(559.dp, 1.0f))
        // Large type wins over width: names need the room.
        assertEquals(1, categoryColumns(600.dp, 1.3f))
    }

    @Test
    fun chartHeightsAreSharesOfTheLargestValueAndNeverOutsideTheChart() {
        assertEquals(0.5f, chartFraction(50, 100), 1e-6f)
        assertEquals(1f, chartFraction(100, 100), 1e-6f)
        assertEquals(0f, chartFraction(0, 100), 1e-6f)
        assertEquals(0f, chartFraction(-20, 100), 1e-6f)
        assertEquals(1f, chartFraction(150, 100), 1e-6f)
        // Nothing to compare against: draw nothing, do not divide by zero.
        assertEquals(0f, chartFraction(0, 0), 1e-6f)
        assertEquals(0f, chartFraction(5, 0), 1e-6f)
        assertEquals(0f, chartFraction(5, -1), 1e-6f)
        // Amounts beyond floating-point precision still land inside the chart.
        val huge = chartFraction(Long.MAX_VALUE - 1, Long.MAX_VALUE)
        assertTrue("$huge", huge in 0.99f..1f)
        assertEquals(1f, chartFraction(Long.MAX_VALUE, Long.MAX_VALUE), 1e-6f)
    }

    @Test
    fun circleFillIsClampedAndOverLimitIsFull() {
        assertEquals(0f, fillLevel(null, over = false), 1e-6f) // no limit: outline only
        assertEquals(0f, fillLevel(0f, over = false), 1e-6f)
        assertEquals(0.68f, fillLevel(0.68f, over = false), 1e-6f)
        assertEquals(1f, fillLevel(1f, over = false), 1e-6f)
        assertEquals(1f, fillLevel(1.7f, over = false), 1e-6f)
        assertEquals(0f, fillLevel(-0.2f, over = false), 1e-6f)
        assertEquals(0f, fillLevel(Float.NaN, over = false), 1e-6f)
        assertEquals(1f, fillLevel(0.4f, over = true), 1e-6f)
        assertEquals(1f, fillLevel(null, over = true), 1e-6f)
    }

    @Test
    fun iconsFillUpToSevenColumnsAndNeverShrinkBelowTheTouchSize() {
        // Cells of 48dp with 6dp between them: seven need 372dp.
        assertEquals(7, iconColumns(372.dp, 48.dp, 7))
        assertEquals(6, iconColumns(371.dp, 48.dp, 7))
        assertEquals(7, iconColumns(900.dp, 48.dp, 7))
        assertEquals(5, iconColumns(288.dp, 48.dp, 7))
        assertEquals(1, iconColumns(48.dp, 48.dp, 7))
        assertEquals(1, iconColumns(10.dp, 48.dp, 7))
        assertEquals(3, iconColumns(372.dp, 48.dp, 3))
    }
}
