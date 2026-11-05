package com.software.sello.feature.resumen

import org.junit.Assert.assertEquals
import org.junit.Test

/** The short labels printed on chart columns and the limit line. */
class ChartLabelTest {
    @Test
    fun underAThousandIsWrittenAsItIs() {
        assertEquals("999", chartLabel(999))
        assertEquals("0", chartLabel(0))
    }

    @Test
    fun thousandsAreRoundedHalfUpToWholeThousands() {
        // 920.000 → 920 mil; 36.353 → 36 mil; 42.700 → 43 mil; 1.500 → 2 mil.
        assertEquals("920 mil", chartLabel(920_000))
        assertEquals("36 mil", chartLabel(36_353))
        assertEquals("43 mil", chartLabel(42_700))
        assertEquals("2 mil", chartLabel(1_500))
    }

    @Test
    fun millionsKeepUpToTwoDecimalsWithAComma() {
        // 4.310.000 → 4,31 M; 1.250.000 → 1,25 M; 1.000.000 → 1 M; 3.680.000 → 3,68 M.
        assertEquals("4,31 M", chartLabel(4_310_000))
        assertEquals("1,25 M", chartLabel(1_250_000))
        assertEquals("1 M", chartLabel(1_000_000))
        assertEquals("1,5 M", chartLabel(1_500_000))
        // 4.305.000 → 4,305 M, half up 4,31 M.
        assertEquals("4,31 M", chartLabel(4_305_000))
    }

    @Test
    fun aThousandThousandsIsAMillion() {
        // 999.600 rounds to 1.000 mil, which is written 1 M.
        assertEquals("1 M", chartLabel(999_600))
    }
}
