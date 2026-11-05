package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.IncomeEntity

@Dao
interface IncomeDao {
    @Insert
    suspend fun insert(income: IncomeEntity)

    /**
     * The first [limit] incomes a schedule recorded that were created after [since], in
     * the order they were created.
     */
    @Query(
        "SELECT * FROM income WHERE created_at > :since AND origin_schedule_id IS NOT NULL " +
            "ORDER BY created_at, sequence LIMIT :limit"
    )
    suspend fun recordedAfter(since: Long, limit: Int): List<IncomeEntity>

    /** When the newest income a schedule recorded was created; null when there is none. */
    @Query("SELECT MAX(created_at) FROM income WHERE origin_schedule_id IS NOT NULL")
    suspend fun newestRecordedAt(): Long?

    /** Every income a schedule recorded that was created at [at]. */
    @Query(
        "SELECT * FROM income WHERE created_at = :at AND origin_schedule_id IS NOT NULL " +
            "ORDER BY sequence"
    )
    suspend fun recordedAt(at: Long): List<IncomeEntity>

    /** Every income a schedule recorded that is dated from [from] to [until], both included. */
    @Query(
        "SELECT * FROM income WHERE origin_schedule_id IS NOT NULL " +
            "AND effective_date BETWEEN :from AND :until ORDER BY effective_date, sequence"
    )
    suspend fun recordedFor(from: String, until: String): List<IncomeEntity>

    @Query("SELECT * FROM income WHERE id = :id")
    suspend fun byId(id: String): IncomeEntity?

    /** The highest sequence in use, or null when there is no income. */
    @Query("SELECT MAX(sequence) FROM income")
    suspend fun highestSequence(): Long?

    /** How many incomes hold [sequence]: one or none. */
    @Query("SELECT COUNT(*) FROM income WHERE sequence = :sequence")
    suspend fun countAtSequence(sequence: Long): Int

    /** How many income rows record this schedule occurrence: one or none. */
    @Query(
        "SELECT COUNT(*) FROM income WHERE origin_schedule_id = :scheduleId " +
            "AND origin_date = :date AND origin_slot = :slot"
    )
    suspend fun countAtOrigin(scheduleId: String, date: String, slot: Int): Int

    /**
     * Changes the income only while it is still at [expectedVersion]. Returns how many
     * rows changed: one, or none when the income is gone or was changed meanwhile.
     * When it was first added and its sequence are not among what can change.
     */
    @Query(
        "UPDATE income SET effective_date = :effectiveDate, amount_minor = :amountMinor, " +
            "currency = :currency, source_key = :sourceKey, source_name = :sourceName, " +
            "note = :note, version = :newVersion, updated_at = :updatedAt " +
            "WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun update(
        id: String,
        expectedVersion: Long,
        effectiveDate: String,
        amountMinor: Long,
        currency: String,
        sourceKey: String,
        sourceName: String?,
        note: String?,
        newVersion: Long,
        updatedAt: Long
    ): Int

    /** Removes the income only while it is still at [expectedVersion]. Returns the rows removed. */
    @Query("DELETE FROM income WHERE id = :id AND version = :expectedVersion")
    suspend fun delete(id: String, expectedVersion: Long): Int

    /**
     * Incomes dated from [from] up to and including [through], newest day first and
     * within a day the highest sequence first, starting before the position
     * ([beforeDate], [beforeSequence]), or with the newest when [beforeDate] is null, and
     * returning at most [limit].
     */
    @Query(
        "SELECT * FROM income WHERE effective_date >= :from AND effective_date <= :through " +
            "AND (:beforeDate IS NULL OR effective_date < :beforeDate " +
            "OR (effective_date = :beforeDate AND sequence < :beforeSequence)) " +
            "ORDER BY effective_date DESC, sequence DESC LIMIT :limit"
    )
    suspend fun newest(
        from: String,
        through: String,
        beforeDate: String?,
        beforeSequence: Long,
        limit: Int
    ): List<IncomeEntity>

    /** Same paging contract as [ExpenseDao.page]. */
    @Query(
        "SELECT * FROM income WHERE effective_date >= :from AND effective_date <= :through " +
            "AND (effective_date > :afterDate " +
            "OR (effective_date = :afterDate AND sequence > :afterSequence)) " +
            "ORDER BY effective_date, sequence LIMIT :limit"
    )
    suspend fun page(
        from: String,
        through: String,
        afterDate: String,
        afterSequence: Long,
        limit: Int
    ): List<IncomeEntity>

    /** Every income after ([afterDate], [afterSequence]) in date order, for a backup. */
    @Query(
        "SELECT * FROM income WHERE effective_date > :afterDate " +
            "OR (effective_date = :afterDate AND sequence > :afterSequence) " +
            "ORDER BY effective_date, sequence LIMIT :limit"
    )
    suspend fun all(afterDate: String, afterSequence: Long, limit: Int): List<IncomeEntity>

    /** For a restore only. */
    @Query("DELETE FROM income")
    suspend fun deleteAll(): Int

    @Insert
    suspend fun insertAll(incomes: List<IncomeEntity>)

    @Query("SELECT COUNT(*) FROM income")
    suspend fun count(): Int
}
