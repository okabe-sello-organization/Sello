package com.software.sello.feature.category

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryRemovalPreviews
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.RemovalPreview
import com.software.sello.domain.port.RemovalPreviewFailure

/**
 * Previews a test scripts: [answer] says what each request gets, and [asked] keeps every
 * request in order. Unless told otherwise, asking fails the test: a form that never deletes
 * must never count.
 */
class HandRemovalPreviews : CategoryRemovalPreviews {
    val asked = mutableListOf<Pair<CategoryId, Disposal>>()
    var answer: (CategoryId, Disposal) -> Outcome<RemovalPreview, RemovalPreviewFailure> =
        { _, _ -> error("No preview was expected") }

    override suspend fun preview(
        categoryId: CategoryId,
        disposal: Disposal
    ): Outcome<RemovalPreview, RemovalPreviewFailure> {
        asked += categoryId to disposal
        return answer(categoryId, disposal)
    }
}
