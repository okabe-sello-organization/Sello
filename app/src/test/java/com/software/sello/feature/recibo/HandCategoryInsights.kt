package com.software.sello.feature.recibo

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryInsightsState
import com.software.sello.domain.port.ReadState
import com.software.sello.testing.asNewObservation
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow

/** [newObservations]: every observation starts from Loading, as the real one does. */
internal class HandCategoryInsights(private val newObservations: Boolean = false) :
    CategoryInsightsSnapshots {
    val state = MutableStateFlow<CategoryInsightsState>(ReadState.Loading)
    override fun observe(categoryId: CategoryId, month: YearMonth) =
        if (newObservations) state.asNewObservation() else state
    override suspend fun read(
        categoryId: CategoryId,
        month: YearMonth
    ): Outcome<CategoryInsights, SnapshotFailure> = when (val value = state.value) {
        is ReadState.Ready -> Outcome.Success(value.value)
        is ReadState.Failed -> Outcome.Failure(value.failure)
        ReadState.Loading -> error("not prepared")
    }
}
