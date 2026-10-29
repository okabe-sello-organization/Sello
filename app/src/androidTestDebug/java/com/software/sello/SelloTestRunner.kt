package com.software.sello

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner
import com.software.sello.devtools.TestFinancialTime
import java.time.LocalDate

/**
 * Runs the device tests with the app's financial day held on [TEST_DAY], set before the
 * app starts and opens its data. A test's "today" is then the same on every run: a date
 * bug can no longer show up only on the days of the month a run happens to fall on, and
 * a test about a month's edge says which day it is instead of waiting for one.
 */
class SelloTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        TestFinancialTime.fixedDate = TEST_DAY
        super.callApplicationOnCreate(app)
    }

    companion object {
        /**
         * The middle of a month, a Wednesday: a week either way stays in October, and
         * the month has days before and after it. Tests that need a month's first or
         * last day, or a fifth week, set their own day on the sandbox's clock.
         */
        val TEST_DAY: LocalDate = LocalDate.of(2026, 10, 14)
    }
}
