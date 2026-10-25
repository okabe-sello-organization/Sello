package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.OperationReceiptEntity

@Dao
interface OperationReceiptDao {
    /** Fails if the operation already has a receipt; the first one is never replaced. */
    @Insert
    suspend fun insert(receipt: OperationReceiptEntity)

    @Query("SELECT * FROM operation_receipt WHERE operation_id = :operationId")
    suspend fun find(operationId: String): OperationReceiptEntity?

    /** When the last operation of [kind] committed, in epoch milliseconds; null if none. */
    @Query("SELECT MAX(committed_at) FROM operation_receipt WHERE kind = :kind")
    suspend fun lastCommittedAt(kind: String): Long?

    /** Every receipt goes; only a reset does this, and it then stores its own. */
    @Query("DELETE FROM operation_receipt")
    suspend fun deleteAll(): Int
}
