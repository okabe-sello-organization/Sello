package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What a deleted income was, kept only so the deletion can be undone, as
 * [DeletedExpenseEntity] is for an expense. The income itself is gone from `income`,
 * so no read of income can ever count it. The columns after [deletedAt] are the
 * income's own, exactly as they were. Across this table and `deleted_expense` there is
 * at most one row, and none that outlives the process that wrote it.
 */
@Entity(tableName = "deleted_income")
data class DeletedIncomeEntity(
    @PrimaryKey
    @ColumnInfo(name = "deletion_id")
    val deletionId: String,
    @ColumnInfo(name = "generation")
    val generation: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,
    @ColumnInfo(name = "income_id")
    val incomeId: String,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "source_key")
    val sourceKey: String,
    @ColumnInfo(name = "source_name")
    val sourceName: String?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "origin_schedule_id")
    val originScheduleId: String?,
    @ColumnInfo(name = "origin_date")
    val originDate: String?,
    @ColumnInfo(name = "origin_slot")
    val originSlot: Int?
) {
    companion object {
        const val TABLE = "deleted_income"
    }
}
