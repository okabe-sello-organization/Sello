package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What a deleted expense was, kept only so the deletion can be undone. The expense
 * itself is gone from `expense`, so no read of expenses can ever count it.
 *
 * The key is the deletion's operation identifier: undoing names the deletion, not the
 * expense. The columns after [deletedAt] are the expense's own, exactly as they were.
 * There is at most one row, and none that outlives the process that wrote it: a later
 * deletion replaces it and opening storage clears it. It has no foreign key, like a
 * receipt, and is not part of a portable backup.
 */
@Entity(tableName = "deleted_expense")
data class DeletedExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "deletion_id")
    val deletionId: String,
    @ColumnInfo(name = "generation")
    val generation: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,
    @ColumnInfo(name = "expense_id")
    val expenseId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
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
        const val TABLE = "deleted_expense"
    }
}
