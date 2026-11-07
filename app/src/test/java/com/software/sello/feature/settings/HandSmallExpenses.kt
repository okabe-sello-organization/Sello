package com.software.sello.feature.settings

import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.SmallExpensePreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Gastos hormiga's line as a test holds it. [read] is what observers see, null before the
 * first read; [fail] refuses writes; [hold] keeps a write waiting until it is completed.
 * [observations] counts how often the line was asked for, as a retry asks again.
 */
class HandSmallExpenses(
    initial: PreferenceRead<SmallExpenseLine>? = PreferenceRead.Ready(SmallExpenseLine.DEFAULT)
) : SmallExpensePreferences {
    val read = MutableStateFlow(initial)
    var fail = false
    var hold: CompletableDeferred<Unit>? = null
    val written = mutableListOf<SmallExpenseLine>()
    var observations = 0
        private set

    override fun observeSmallExpenseLine(): Flow<PreferenceRead<SmallExpenseLine>> {
        observations++
        return read.filterNotNull()
    }

    override suspend fun setSmallExpenseLine(line: SmallExpenseLine): PreferenceWrite {
        hold?.await()
        if (fail) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        written += line
        read.value = PreferenceRead.Ready(line)
        return PreferenceWrite.Saved
    }
}
