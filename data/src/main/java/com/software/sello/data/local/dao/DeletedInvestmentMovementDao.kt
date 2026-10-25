package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.DeletedInvestmentMovementEntity

@Dao
interface DeletedInvestmentMovementDao {
    @Insert
    suspend fun insert(kept: DeletedInvestmentMovementEntity)

    @Query("SELECT * FROM deleted_investment_movement WHERE deletion_id = :deletionId")
    suspend fun find(deletionId: String): DeletedInvestmentMovementEntity?

    @Query("DELETE FROM deleted_investment_movement WHERE deletion_id = :deletionId")
    suspend fun remove(deletionId: String): Int

    @Query("DELETE FROM deleted_investment_movement")
    suspend fun clear()
}
