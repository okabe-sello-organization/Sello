package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.CarryChainEntity

@Dao
interface CarryChainDao {
    /** A category's chains, oldest first, at most [limit]. */
    @Query(
        "SELECT * FROM carry_chain WHERE category_id = :categoryId " +
            "ORDER BY start_month LIMIT :limit"
    )
    suspend fun ofCategory(categoryId: String, limit: Int): List<CarryChainEntity>

    /** Every chain after ([afterCategoryId], [afterStart]), by category then start. */
    @Query(
        "SELECT * FROM carry_chain WHERE category_id > :afterCategoryId " +
            "OR (category_id = :afterCategoryId AND start_month > :afterStart) " +
            "ORDER BY category_id, start_month LIMIT :limit"
    )
    suspend fun all(afterCategoryId: String, afterStart: String, limit: Int): List<CarryChainEntity>

    @Query("SELECT COUNT(*) FROM carry_chain")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(chains: List<CarryChainEntity>)

    @Query("DELETE FROM carry_chain WHERE category_id = :categoryId")
    suspend fun deleteOfCategory(categoryId: String): Int

    /** For a restore or a reset only. */
    @Query("DELETE FROM carry_chain")
    suspend fun deleteAll(): Int
}
