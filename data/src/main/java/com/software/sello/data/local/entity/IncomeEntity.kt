package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [sourceKey] is the stable income key; [sourceName] is present only for the "other"
 * source. The origin columns mean what they mean on `expense`.
 */
@Entity(
    tableName = "income",
    indices = [
        Index("effective_date", "sequence"),
        Index(value = ["sequence"], unique = true),
        Index(value = ["origin_schedule_id", "origin_date", "origin_slot"], unique = true)
    ]
)
data class IncomeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
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
        const val TABLE = "income"
    }
}
