package com.software.sello.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.software.sello.data.local.entity.GoalEntity
import com.software.sello.data.local.entity.GoalEventEntity
import com.software.sello.data.local.entity.StarredGoalEntity

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: GoalEntity)

    @Insert
    suspend fun insertEvent(event: GoalEventEntity)

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun byId(id: String): GoalEntity?

    @Query("SELECT COUNT(*) FROM goal")
    suspend fun count(): Int

    @Query("DELETE FROM goal_event")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM starred_goal")
    suspend fun deleteAllStars()

    @Query("DELETE FROM goal")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(goals: List<GoalEntity>)

    @Insert
    suspend fun insertAllEvents(events: List<GoalEventEntity>)

    /** Every goal, the earliest created first. */
    @Query("SELECT * FROM goal ORDER BY created_at, id")
    suspend fun all(): List<GoalEntity>

    /** A goal's history in recording order. */
    @Query("SELECT * FROM goal_event WHERE goal_id = :goalId ORDER BY sequence")
    suspend fun events(goalId: String): List<GoalEventEntity>

    /** Every goal's history, grouped by goal and in recording order. */
    @Query("SELECT * FROM goal_event ORDER BY goal_id, sequence")
    suspend fun allEvents(): List<GoalEventEntity>

    @Query("SELECT * FROM goal_event WHERE id = :id")
    suspend fun event(id: String): GoalEventEntity?

    @Query("SELECT COUNT(*) FROM goal_event WHERE goal_id = :goalId AND sequence = :sequence")
    suspend fun countAt(goalId: String, sequence: Long): Int

    /**
     * Changes the goal only while it is still at [expectedVersion]. Returns the rows
     * changed: one, or none when it is gone or changed meanwhile.
     */
    @Query(
        "UPDATE goal SET name = :name, icon_key = :iconKey, target_month = :targetMonth, " +
            "completed_on = :completedOn, next_sequence = :nextSequence, " +
            "version = :newVersion, updated_at = :updatedAt " +
            "WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun update(
        id: String,
        expectedVersion: Long,
        name: String,
        iconKey: String,
        targetMonth: String?,
        completedOn: String?,
        nextSequence: Long,
        newVersion: Long,
        updatedAt: Long
    ): Int

    /** Changes a movement in place while it is still at [expectedVersion]. */
    @Query(
        "UPDATE goal_event SET amount_minor = :amountMinor, currency = :currency, " +
            "effective_date = :effectiveDate, note = :note, version = :newVersion, " +
            "updated_at = :updatedAt WHERE id = :id AND version = :expectedVersion"
    )
    suspend fun updateEvent(
        id: String,
        expectedVersion: Long,
        amountMinor: Long,
        currency: String,
        effectiveDate: String,
        note: String?,
        newVersion: Long,
        updatedAt: Long
    ): Int

    @Query("DELETE FROM goal_event WHERE id = :id AND version = :expectedVersion")
    suspend fun deleteEvent(id: String, expectedVersion: Long): Int

    @Query("DELETE FROM goal_event WHERE goal_id = :goalId")
    suspend fun deleteEvents(goalId: String): Int

    @Query("DELETE FROM goal WHERE id = :id AND version = :expectedVersion")
    suspend fun delete(id: String, expectedVersion: Long): Int

    /** The starred goals' identifiers, in identifier order. */
    @Query("SELECT goal_id FROM starred_goal ORDER BY goal_id")
    suspend fun starred(): List<String>

    @Query("SELECT COUNT(*) FROM starred_goal")
    suspend fun starredCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM starred_goal WHERE goal_id = :goalId)")
    suspend fun isStarred(goalId: String): Boolean

    /** Fails on a goal already starred: the caller checks first. */
    @Insert
    suspend fun star(star: StarredGoalEntity)

    @Insert
    suspend fun starAll(stars: List<StarredGoalEntity>)

    /** Returns the rows removed: one, or none when the goal was not starred. */
    @Query("DELETE FROM starred_goal WHERE goal_id = :goalId")
    suspend fun unstar(goalId: String): Int
}
