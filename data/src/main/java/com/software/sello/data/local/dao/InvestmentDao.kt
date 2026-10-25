package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.InvestmentEntity
import com.software.sello.data.local.entity.InvestmentMovementEntity
import com.software.sello.data.local.entity.UsdRateEntity

@Dao
interface InvestmentDao {
    @Insert
    suspend fun insert(investment: InvestmentEntity)

    @Insert
    suspend fun insertMovement(movement: InvestmentMovementEntity)

    @Insert
    suspend fun insertRate(rate: UsdRateEntity)

    @Query("SELECT * FROM investment WHERE id = :id")
    suspend fun byId(id: String): InvestmentEntity?

    /** Every investment, the earliest created first. */
    @Query("SELECT * FROM investment ORDER BY created_at, id")
    suspend fun all(): List<InvestmentEntity>

    /** An investment's movements in replay order: date, then recording position. */
    @Query(
        "SELECT * FROM investment_movement WHERE investment_id = :investmentId " +
            "ORDER BY effective_date, sequence"
    )
    suspend fun movements(investmentId: String): List<InvestmentMovementEntity>

    /** Every investment's movements, grouped by investment and in replay order. */
    @Query("SELECT * FROM investment_movement ORDER BY investment_id, effective_date, sequence")
    suspend fun allMovements(): List<InvestmentMovementEntity>

    @Query("SELECT * FROM investment_movement WHERE id = :id")
    suspend fun movement(id: String): InvestmentMovementEntity?

    @Query(
        "SELECT COUNT(*) FROM investment_movement " +
            "WHERE investment_id = :investmentId AND sequence = :sequence"
    )
    suspend fun countAt(investmentId: String, sequence: Long): Int

    /** Every entered rate in the order it was entered. */
    @Query("SELECT * FROM usd_rate ORDER BY sequence")
    suspend fun rates(): List<UsdRateEntity>

    /** The highest entry position taken, or null before the first rate. */
    @Query("SELECT MAX(sequence) FROM usd_rate")
    suspend fun lastRateSequence(): Long?

    /**
     * Changes the investment only while it is still at [expectedVersion]. Returns the
     * rows changed: one, or none when it is gone or changed meanwhile.
     */
    @Query(
        "UPDATE investment SET name = :name, description = :description, " +
            "icon_key = :iconKey, next_sequence = :nextSequence, version = :newVersion, " +
            "updated_at = :updatedAt WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun update(
        id: String,
        expectedVersion: Long,
        name: String,
        description: String?,
        iconKey: String,
        nextSequence: Long,
        newVersion: Long,
        updatedAt: Long
    ): Int

    /** Corrects a movement in place while it is still at [expectedVersion]. */
    @Query(
        "UPDATE investment_movement SET amount_minor = :amountMinor, currency = :currency, " +
            "effective_date = :effectiveDate, note = :note, version = :newVersion, " +
            "updated_at = :updatedAt WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun updateMovement(
        id: String,
        expectedVersion: Long,
        amountMinor: Long,
        currency: String,
        effectiveDate: String,
        note: String?,
        newVersion: Long,
        updatedAt: Long
    ): Int

    @Query("DELETE FROM investment_movement WHERE id = :id AND version = :expectedVersion")
    suspend fun deleteMovement(id: String, expectedVersion: Long): Int

    @Query("DELETE FROM investment_movement WHERE investment_id = :investmentId")
    suspend fun deleteMovements(investmentId: String): Int

    @Query("DELETE FROM investment WHERE id = :id AND version = :expectedVersion")
    suspend fun delete(id: String, expectedVersion: Long): Int

    @Query("DELETE FROM investment_movement")
    suspend fun deleteAllMovements()

    @Query("DELETE FROM investment")
    suspend fun deleteAll()

    @Query("DELETE FROM usd_rate")
    suspend fun deleteAllRates()
}
