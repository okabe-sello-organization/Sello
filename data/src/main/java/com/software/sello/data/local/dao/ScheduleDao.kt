package com.software.sello.data.local.dao

import androidx.room.Dao
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
}
