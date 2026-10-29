package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One run of months in which a category carries its balance (ADR 0020 CP4): from
 * [startMonth] up to, not including, [endMonth], or on while [endMonth] is null. Only the
 * months are stored; carry, capacity and remaining are derived on every read (CP7, CP12).
 */
@Entity(
    tableName = "carry_chain",
    primaryKeys = ["category_id", "start_month"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ]
)
data class CarryChainEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "start_month")
    val startMonth: String,
    @ColumnInfo(name = "end_month")
    val endMonth: String?
) {
    companion object {
        const val TABLE = "carry_chain"
    }
}
