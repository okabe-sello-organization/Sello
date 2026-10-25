package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.local.entity.SchedulePauseEntity

@Dao
interface ScheduleDao {
    /** Every schedule in creation order. */
    @Query("SELECT * FROM schedule ORDER BY sequence")
    suspend fun all(): List<ScheduleEntity>

    /** Every pause, by schedule and then by its first day. */
    @Query("SELECT * FROM schedule_pause ORDER BY schedule_id, from_date")
    suspend fun allPauses(): List<SchedulePauseEntity>

    /** Pauses first: a schedule with pauses cannot be deleted. */
    @Query("DELETE FROM schedule_pause")
    suspend fun deleteAllPauses()

    /** Before categories: a category with a schedule cannot be deleted. */
    @Query("DELETE FROM schedule")
    suspend fun deleteAll()

    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun byId(id: String): ScheduleEntity?

    @Query("SELECT * FROM schedule_pause WHERE schedule_id = :scheduleId ORDER BY from_date")
    suspend fun pauses(scheduleId: String): List<SchedulePauseEntity>

    /** The schedules that record into [categoryId], in creation order. */
    @Query("SELECT * FROM schedule WHERE category_id = :categoryId ORDER BY sequence")
    suspend fun ofCategory(categoryId: String): List<ScheduleEntity>

    /** The highest creation order given so far, or null before the first schedule. */
    @Query("SELECT MAX(sequence) FROM schedule")
    suspend fun highestSequence(): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPause(pause: SchedulePauseEntity)

    /** Replaces the terms while the schedule is still at [expectedVersion]. */
    @Query(
        "UPDATE schedule SET category_id = :categoryId, source_key = :sourceKey, " +
            "source_name = :sourceName, amount_minor = :amountMinor, currency = :currency, " +
            "note = :note, rule = :rule, rule_first = :ruleFirst, rule_second = :ruleSecond, " +
            "start_date = :startDate, end_date = :endDate, version = :newVersion, " +
            "updated_at = :updatedAt WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun update(
        id: String,
        expectedVersion: Long,
        categoryId: String?,
        sourceKey: String?,
        sourceName: String?,
        amountMinor: Long,
        currency: String,
        note: String?,
        rule: String,
        ruleFirst: Int,
        ruleSecond: Int?,
        startDate: String,
        endDate: String?,
        newVersion: Long,
        updatedAt: Long
    ): Int

    /** Raises the version alone, for a change that writes only pauses. */
    @Query(
        "UPDATE schedule SET version = :newVersion, updated_at = :updatedAt " +
            "WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun touch(id: String, expectedVersion: Long, newVersion: Long, updatedAt: Long): Int

    @Query(
        "UPDATE schedule_pause SET until_date = :untilDate " +
            "WHERE schedule_id = :scheduleId AND from_date = :fromDate"
    )
    suspend fun endPause(scheduleId: String, fromDate: String, untilDate: String): Int

    @Query("DELETE FROM schedule_pause WHERE schedule_id = :scheduleId AND from_date = :fromDate")
    suspend fun removePause(scheduleId: String, fromDate: String): Int

    @Query("DELETE FROM schedule_pause WHERE schedule_id = :scheduleId")
    suspend fun removePauses(scheduleId: String): Int

    @Query("DELETE FROM schedule WHERE id = :id AND version = :expectedVersion")
    suspend fun delete(id: String, expectedVersion: Long): Int

    /** Catch-up's checkpoint: [date] and [slot] are the last occurrence it dealt with. */
    @Query("UPDATE schedule SET handled_date = :date, handled_slot = :slot WHERE id = :id")
    suspend fun setHandled(id: String, date: String, slot: Int): Int
}
