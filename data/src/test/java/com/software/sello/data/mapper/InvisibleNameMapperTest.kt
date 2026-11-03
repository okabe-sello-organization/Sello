package com.software.sello.data.mapper

import com.software.sello.data.local.entity.CategoryEntity
import com.software.sello.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SELLO-164 applies to names typed from now on. A category stored before it may hold a
 * zero-width space, and its stored key was folded the same way it is now, so the row
 * still reads and the unique index still guards the same key.
 */
class InvisibleNameMapperTest {
    private fun hex(text: String) =
        text.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }

    @Test
    fun aStoredCategoryNameWithAZeroWidthSpaceStillReadsWithItsKey() {
        val row = CategoryEntity(
            id = "00000000-0000-4000-8000-000000000001",
            name = "Caf\u00E9\u200B",
            // Folded by hand: accents and case away, the zero-width space kept.
            nameKey = "cafe\u200B",
            icon = "local_cafe",
            archived = 0,
            version = 1,
            createdAt = 0,
            updatedAt = 0
        )

        val read = (row.toDomain() as Outcome.Success).value

        assertEquals("43 61 66 C3 A9 E2 80 8B", hex(read.name.value))
        assertEquals("63 61 66 65 E2 80 8B", hex(read.name.uniquenessKey))
        assertEquals(hex(row.nameKey), hex(read.toEntity().nameKey))
    }
}
