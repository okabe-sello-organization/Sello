package com.software.sello.devtools

import java.time.LocalDate

/**
 * For the debug build's device tests only. When [fixedDate] is set before the app's
 * data opens, the app's own profile runs on a [ControlledFinancialClock] held on that
 * date instead of real financial time, so a test's "today" is the same whatever day it
 * runs. Only the test runner sets it; the debug app used by hand never does and keeps
 * real financial time. The sandbox keeps its own clock either way, and audit and
 * elapsed time stay real.
 */
object TestFinancialTime {
    @Volatile
    var fixedDate: LocalDate? = null
}
