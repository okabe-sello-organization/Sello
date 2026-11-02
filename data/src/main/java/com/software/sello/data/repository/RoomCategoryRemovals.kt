package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryRemovalPreviews
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RemovalPreview
import com.software.sello.domain.port.RemovalPreviewFailure

/** SELLO-138 stub: replaced by the data work. */
class RoomCategoryRemovals(
    private val database: SelloDatabase,
    private val clock: FinancialClock
) : CategoryRemovalPreviews {
    override suspend fun preview(
        categoryId: CategoryId,
        disposal: Disposal
    ): Outcome<RemovalPreview, RemovalPreviewFailure> = error("SELLO-138 stub")
}
