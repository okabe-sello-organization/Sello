package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A goal (docs/testing/goal-examples.md). Its target and movements are `goal_event`
 * rows; [nextSequence] is the recording position the next event takes, so targets and
 * movements share one order. Months are `yyyy-MM`, days `yyyy-MM-dd`.
 */
@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon_key")
    val iconKey: String,
    @ColumnInfo(name = "target_month")
    val targetMonth: String?,
    @ColumnInfo(name = "completed_on")
    val completedOn: String?,
    @ColumnInfo(name = "next_sequence")
    val nextSequence: Long,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val TABLE = "goal"
    }
}

/**
 * One step of a goal's history: a target (`kind` `target`, no [effectiveDate] or
 * [note]) or a movement (`contribution`, `withdrawal`). [sequence] is its recording
 * position within the goal and never changes. A goal with events cannot be deleted
 * while they exist: there is no cascade.
 */
@Entity(
    tableName = "goal_event",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["goal_id", "sequence"], unique = true),
        Index("goal_id", "effective_date")
    ]
)
data class GoalEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "goal_id")
    val goalId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String?,
    @ColumnInfo(name = "recorded_on")
    val recordedOn: String,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val TABLE = "goal_event"
        const val TARGET = "target"
        const val CONTRIBUTION = "contribution"
        const val WITHDRAWAL = "withdrawal"
    }
}

/**
 * What a deleted movement was, kept only so its deletion can be undone, as
 * [DeletedExpenseEntity] is for an expense. The columns after [deletedAt] are the
 * event's own, exactly as they were.
 */
@Entity(tableName = "deleted_goal_event")
data class DeletedGoalEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "deletion_id")
    val deletionId: String,
    @ColumnInfo(name = "generation")
    val generation: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "goal_id")
    val goalId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String?,
    @ColumnInfo(name = "recorded_on")
    val recordedOn: String,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val TABLE = "deleted_goal_event"
    }
}

/**
 * A goal the person starred for Recibo (rule 12): at most
 * [com.software.sello.domain.policy.GoalPolicy.MAX_STARRED] rows, never one for a
 * completed goal. The key makes a goal starred at most once.
 */
@Entity(
    tableName = "starred_goal",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ]
)
data class StarredGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "goal_id")
    val goalId: String
) {
    companion object {
        const val TABLE = "starred_goal"
    }
}
