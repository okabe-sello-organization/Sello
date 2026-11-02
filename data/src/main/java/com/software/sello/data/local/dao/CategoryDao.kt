package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.software.sello.data.local.entity.CategoryEntity

@Dao
interface CategoryDao {
    /** Fails on a duplicate identifier or name key; nothing is replaced. */
    @Insert
    suspend fun insert(category: CategoryEntity)

    /** Writes every column of the row with this identifier. Returns the rows changed: 1 or 0. */
    @Update
    suspend fun update(category: CategoryEntity): Int

    /** The category holding this uniqueness key, archived or not. */
    @Query("SELECT * FROM category WHERE name_key = :nameKey")
    suspend fun byNameKey(nameKey: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    /**
     * Deletes the category while it is still at [expectedVersion], once nothing refers to
     * it any more: the foreign keys refuse it otherwise. Returns the rows removed.
     */
    @Query("DELETE FROM category WHERE id = :id AND version = :expectedVersion")
    suspend fun delete(id: String, expectedVersion: Long): Int

    /** Categories after [afterId] in identifier order, at most [limit]. */
    @Query("SELECT * FROM category WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: String, limit: Int): List<CategoryEntity>

    /** For a restore only, after every row that refers to a category is gone. */
    @Query("DELETE FROM category")
    suspend fun deleteAll(): Int

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int
}
