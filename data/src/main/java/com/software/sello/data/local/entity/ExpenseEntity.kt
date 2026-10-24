package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dates are ISO `yyyy-MM-dd` text, so text order is date order. A category with
 * expenses cannot be deleted or re-keyed: there is no cascade. The origin columns are
 * all present for an expense a schedule recorded and all absent otherwise; an origin
 * occurs at most once. They have no foreign key: the expense outlives its schedule.
 */
@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("effective_date", "sequence"),
        Index("category_id", "effective_date"),
        Index(value = ["sequence"], unique = true),
        Index(value = ["origin_schedule_id", "origin_date", "origin_slot"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
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
        const val TABLE = "expense"
    }
}
