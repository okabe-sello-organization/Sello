package com.software.sello.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The durable proof that an operation committed. The operation's identifier is the
 * primary key, so a second commit under the same identifier is refused by the database.
 *
 * A receipt has no foreign key to the record it describes and holds no amount, name or
 * note: it must outlive that record, including a replacement of the whole history.
 * [inputDigest] identifies the submitted input without storing it.
 */
@Entity(
    tableName = "operation_receipt",
    indices = [Index("generation"), Index("kind", "committed_at")]
)
data class OperationReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "input_digest")
    val inputDigest: String,
    @ColumnInfo(name = "generation")
    val generation: Long,
    @ColumnInfo(name = "revision")
    val revision: Long,
    @ColumnInfo(name = "subject_id")
    val subjectId: String?,
    @ColumnInfo(name = "committed_at")
    val committedAt: Long
) {
    companion object {
        const val TABLE = "operation_receipt"
    }
}
