package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.ExpenseEntity

@Dao
interface ExpenseDao {
    @Query(
        "SELECT COUNT(*) FROM expense WHERE category_id = :categoryId " +
            "AND effective_date >= :from AND effective_date < :until " +
            "AND instr(lower(COALESCE(note, '')), lower(:query)) > 0"
    )
    suspend fun countSearchInCategory(
        categoryId: String,
        from: String,
        until: String,
        query: String
    ): Long

    @Query(
        "SELECT * FROM expense WHERE category_id = :categoryId " +
            "AND effective_date >= :from AND effective_date < :until " +
            "AND instr(lower(COALESCE(note, '')), lower(:query)) > 0 " +
            "ORDER BY CASE WHEN :sort = 'amount' THEN amount_minor END DESC, " +
            "CASE WHEN :sort = 'name' THEN COALESCE(note, '') END COLLATE NOCASE ASC, " +
            "effective_date DESC, sequence DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun searchInCategory(
        categoryId: String,
        from: String,
        until: String,
        sort: String,
        query: String,
        offset: Int,
        limit: Int
    ): List<ExpenseEntity>

    @Insert
    suspend fun insert(expense: ExpenseEntity)

    /** The highest sequence in use, or null when there is no expense. */
    @Query("SELECT MAX(sequence) FROM expense")
    suspend fun highestSequence(): Long?

    /**
     * A category's expenses dated from [from] up to but not including [until], newest
     * day first and within a day the highest sequence first, starting before the
     * position ([beforeDate], [beforeSequence]) and returning at most [limit].
     */
    @Query(
        "SELECT * FROM expense WHERE category_id = :categoryId " +
            "AND effective_date >= :from AND effective_date < :until " +
            "AND (effective_date < :beforeDate " +
            "OR (effective_date = :beforeDate AND sequence < :beforeSequence)) " +
            "ORDER BY effective_date DESC, sequence DESC LIMIT :limit"
    )
    suspend fun newestInCategory(
        categoryId: String,
        from: String,
        until: String,
        beforeDate: String,
        beforeSequence: Long,
        limit: Int
    ): List<ExpenseEntity>

    /** The category of the expense added last, or null when there is no expense. */
    @Query("SELECT category_id FROM expense ORDER BY sequence DESC LIMIT 1")
    suspend fun lastAddedCategory(): String?

    /** Every expense a schedule recorded that was created after [since], by date. */
    @Query(
        "SELECT * FROM expense WHERE origin_schedule_id IS NOT NULL AND created_at > :since " +
            "ORDER BY effective_date, sequence"
    )
    suspend fun recordedAfter(since: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun byId(id: String): ExpenseEntity?

    /** How many expenses hold [sequence]: one or none. */
    @Query("SELECT COUNT(*) FROM expense WHERE sequence = :sequence")
    suspend fun countAtSequence(sequence: Long): Int

    /** How many expense rows record this schedule occurrence: one or none. */
    @Query(
        "SELECT COUNT(*) FROM expense WHERE origin_schedule_id = :scheduleId " +
            "AND origin_date = :date AND origin_slot = :slot"
    )
    suspend fun countAtOrigin(scheduleId: String, date: String, slot: Int): Int

    /**
     * Changes the expense only while it is still at [expectedVersion]. Returns how many
     * rows changed: one, or none when the expense is gone or was changed meanwhile.
     * When it was first added and its sequence are not among what can change.
     */
    @Query(
        "UPDATE expense SET category_id = :categoryId, effective_date = :effectiveDate, " +
            "amount_minor = :amountMinor, currency = :currency, note = :note, " +
            "version = :newVersion, updated_at = :updatedAt " +
            "WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun update(
        id: String,
        expectedVersion: Long,
        categoryId: String,
        effectiveDate: String,
        amountMinor: Long,
        currency: String,
        note: String?,
        newVersion: Long,
        updatedAt: Long
    ): Int

    /** Removes the expense only while it is still at [expectedVersion]. Returns the rows removed. */
    @Query("DELETE FROM expense WHERE id = :id AND version = :expectedVersion")
    suspend fun delete(id: String, expectedVersion: Long): Int

    /**
     * Expenses dated from [from] up to but not including [until], in date then
     * sequence order, starting after the position ([afterDate], [afterSequence]) and
     * returning at most [limit]. Pass the last row of one page to get the next.
     */
    @Query(
        "SELECT * FROM expense WHERE effective_date >= :from AND effective_date < :until " +
            "AND (effective_date > :afterDate " +
            "OR (effective_date = :afterDate AND sequence > :afterSequence)) " +
            "ORDER BY effective_date, sequence LIMIT :limit"
    )
    suspend fun page(
        from: String,
        until: String,
        afterDate: String,
        afterSequence: Long,
        limit: Int
    ): List<ExpenseEntity>

    /** Every expense after ([afterDate], [afterSequence]) in date order, for a backup. */
    @Query(
        "SELECT * FROM expense WHERE effective_date > :afterDate " +
            "OR (effective_date = :afterDate AND sequence > :afterSequence) " +
            "ORDER BY effective_date, sequence LIMIT :limit"
    )
    suspend fun all(afterDate: String, afterSequence: Long, limit: Int): List<ExpenseEntity>

    /** For a restore only. */
    @Query("DELETE FROM expense")
    suspend fun deleteAll(): Int

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT COUNT(*) FROM expense")
    suspend fun count(): Int
}
