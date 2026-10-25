package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An investment (docs/testing/investment-examples.md), kept in one currency, `COP` or
 * `USD`, that never changes. Its movements are `investment_movement` rows; [nextSequence]
 * is the recording position the next movement takes.
 */
@Entity(tableName = "investment")
data class InvestmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "icon_key")
    val iconKey: String,
    @ColumnInfo(name = "currency")
    val currency: String,
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
        const val TABLE = "investment"
    }
}

/**
 * One aporte (`contribution`), retiro (`withdrawal`), dividend (`dividend`) or recorded
 * value (`valuation`), in its investment's currency. [sequence] is its recording position
 * within the investment and never changes. An investment with movements cannot be
 * deleted while they exist: there is no cascade.
 */
@Entity(
    tableName = "investment_movement",
    foreignKeys = [
        ForeignKey(
            entity = InvestmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["investment_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["investment_id", "sequence"], unique = true),
        Index("investment_id", "effective_date")
    ]
)
data class InvestmentMovementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "investment_id")
    val investmentId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String,
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
        const val TABLE = "investment_movement"
        const val CONTRIBUTION = "contribution"
        const val WITHDRAWAL = "withdrawal"
        const val DIVIDEND = "dividend"
        const val VALUATION = "valuation"
    }
}

/**
 * What a deleted movement was, kept only so its deletion can be undone, as
 * [DeletedGoalEventEntity] is for a goal. The columns after [deletedAt] are the
 * movement's own, exactly as they were.
 */
@Entity(tableName = "deleted_investment_movement")
data class DeletedInvestmentMovementEntity(
    @PrimaryKey
    @ColumnInfo(name = "deletion_id")
    val deletionId: String,
    @ColumnInfo(name = "generation")
    val generation: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,
    @ColumnInfo(name = "movement_id")
    val movementId: String,
    @ColumnInfo(name = "investment_id")
    val investmentId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String,
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
        const val TABLE = "deleted_investment_movement"
    }
}

/**
 * One entered USD rate: pesos per dollar in exact hundredths (4.150,25 is 415025), never
 * floating point, dated [effectiveDate]. [sequence] is the order rates were entered in
 * across the app. Rates are never changed or deleted by a command.
 */
@Entity(
    tableName = "usd_rate",
    indices = [Index(value = ["sequence"], unique = true)]
)
data class UsdRateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "rate_hundredths")
    val rateHundredths: Long,
    @ColumnInfo(name = "effective_date")
    val effectiveDate: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    companion object {
        const val TABLE = "usd_rate"
    }
}
