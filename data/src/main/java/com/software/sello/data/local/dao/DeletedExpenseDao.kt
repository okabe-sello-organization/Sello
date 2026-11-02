package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.DeletedExpenseEntity

@Dao
interface DeletedExpenseDao {
    @Insert
    suspend fun insert(deleted: DeletedExpenseEntity)

    @Query("SELECT * FROM deleted_expense WHERE deletion_id = :deletionId")
    suspend fun find(deletionId: String): DeletedExpenseEntity?

    /** Returns how many rows were removed. */
    @Query("DELETE FROM deleted_expense WHERE deletion_id = :deletionId")
    suspend fun remove(deletionId: String): Int

    /** Makes every earlier deletion final. Returns how many kept copies were removed. */
    @Query("DELETE FROM deleted_expense")
    suspend fun clear(): Int

    /** Kept copies of [fromCategoryId]'s expenses now belong to [toCategoryId]. Returns the rows changed. */
    @Query(
        "UPDATE deleted_expense SET category_id = :toCategoryId " +
            "WHERE category_id = :fromCategoryId"
    )
    suspend fun retarget(fromCategoryId: String, toCategoryId: String): Int

    /** Drops the kept copies of a category's expenses. Returns how many were removed. */
    @Query("DELETE FROM deleted_expense WHERE category_id = :categoryId")
    suspend fun removeOfCategory(categoryId: String): Int

    /** The highest sequence a kept copy holds, or null when there is none. */
    @Query("SELECT MAX(sequence) FROM deleted_expense")
    suspend fun highestSequence(): Long?
}
