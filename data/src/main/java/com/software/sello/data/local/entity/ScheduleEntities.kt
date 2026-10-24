package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recurring payment (`kind` `payment`, with [categoryId]) or income (`income`, with
 * [sourceKey] and, for `other`, [sourceName]) (docs/testing/recurrence-examples.md).
 * [rule] is `monthly` ([ruleFirst] the day), `twice_monthly` ([ruleFirst] and
 * [ruleSecond] the days), `every_days` or `every_weeks` ([ruleFirst] the interval).
 * [sequence] is the creation order. [handledDate] and [handledSlot] are the last
 * occurrence catch-up dealt with, both absent before the first. A category with a
 * schedule cannot be deleted or re-keyed: there is no cascade.
 */
@Entity(
    tableName = "schedule",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("category_id"), Index(value = ["sequence"], unique = true)]
)
data class ScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    @ColumnInfo(name = "source_key")
    val sourceKey: String?,
    @ColumnInfo(name = "source_name")
    val sourceName: String?,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "rule")
    val rule: String,
    @ColumnInfo(name = "rule_first")
    val ruleFirst: Int,
    @ColumnInfo(name = "rule_second")
    val ruleSecond: Int?,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "end_date")
    val endDate: String?,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "handled_date")
    val handledDate: String?,
    @ColumnInfo(name = "handled_slot")
    val handledSlot: Int?,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val TABLE = "schedule"
        const val PAYMENT = "payment"
        const val INCOME = "income"
        const val MONTHLY = "monthly"
        const val TWICE_MONTHLY = "twice_monthly"
        const val EVERY_DAYS = "every_days"
        const val EVERY_WEEKS = "every_weeks"
    }
}

/**
 * One pause of a schedule: nothing from [fromDate] up to the day before [untilDate], or
 * from [fromDate] on while [untilDate] is absent (rule 8). A schedule with pauses
 * cannot be deleted while they exist: there is no cascade.
 */
@Entity(
    tableName = "schedule_pause",
    primaryKeys = ["schedule_id", "from_date"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ]
)
data class SchedulePauseEntity(
    @ColumnInfo(name = "schedule_id")
    val scheduleId: String,
    @ColumnInfo(name = "from_date")
    val fromDate: String,
    @ColumnInfo(name = "until_date")
    val untilDate: String?
) {
    companion object {
        const val TABLE = "schedule_pause"
    }
}
