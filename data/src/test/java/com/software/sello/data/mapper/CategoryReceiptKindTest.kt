package com.software.sello.data.mapper

import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SELLO-188 removed the commands that turned carry on or off, renamed a category and
 * changed its icon on their own; the category form saves them all as one `EditCategory`.
 * Receipts those commands stored still read, under the keys they were stored with.
 */
class CategoryReceiptKindTest {
    private val id = "00000000-0000-4000-8000-000000000100"

    private fun stored(key: String) = OperationReceiptEntity(
        operationId = "00000000-0000-4000-8000-000000000009",
        kind = key,
        inputDigest = "a".repeat(64),
        generation = 1,
        revision = 3,
        subjectId = id,
        committedAt = 1_000
    ).toStored()

    @Test
    fun receiptsOfTheRemovedCommandsStillReadAsCategoryChanges() {
        val keys = listOf(
            "category.rename",
            "category.change_icon",
            "category.enable_carry",
            "category.disable_carry",
            "category.edit"
        )

        val read = keys.map { key ->
            val receipt = (stored(key) as Outcome.Success).value
            receipt.receipt.kind.key to receipt.categoryId
        }

        val category = (CategoryId.of(id) as Outcome.Success).value
        assertEquals(keys.map { it to category }, read)
        assertEquals(
            listOf(
                OperationKind.RenameCategory,
                OperationKind.ChangeCategoryIcon,
                OperationKind.EnableCarry,
                OperationKind.DisableCarry,
                OperationKind.EditCategory
            ).map { it.key },
            keys
        )
    }
}
