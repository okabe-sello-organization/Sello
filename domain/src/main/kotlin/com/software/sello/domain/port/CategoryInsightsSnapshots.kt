package com.software.sello.domain.port

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

typealias CategoryInsightsState = ReadState<CategoryInsights, SnapshotFailure>

interface CategoryInsightsSnapshots {
    suspend fun read(
        categoryId: CategoryId,
        month: YearMonth
    ): Outcome<CategoryInsights, SnapshotFailure>
    fun observe(categoryId: CategoryId, month: YearMonth): Flow<CategoryInsightsState>
}
