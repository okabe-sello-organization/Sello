package com.software.sello.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.IncomeEntity

/** Which table a [MovementKey] points into. */
internal object MovementKind {
    const val EXPENSE = 0
    const val INCOME = 1
}

/** Where one movement sits in Día a día's list: its table ([MovementKind]) and row. */
data class MovementKey(
    @ColumnInfo(name = "kind") val kind: Int,
    @ColumnInfo(name = "id") val id: String
)

/** One expense's day and amount, as stored, for adding up a day. */
data class DayAmountRow(
    @ColumnInfo(name = "effective_date") val effectiveDate: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency") val currency: String
)

/** Expenses and incomes read together, for the month's day-by-day list (SELLO-192). */
@Dao
interface MovementDao {
    /**
     * The movements dated from [from] to [through], both included, newest day first and,
     * within a day, the one created last first (expenses before incomes created at the same
     * moment, then the higher sequence first), skipping [offset] and at most [limit].
     * [expenses] and [incomes] say which tables are listed.
     */
    @Query(
        "SELECT kind, id FROM (" +
            "SELECT 0 AS kind, id, effective_date, created_at, sequence FROM expense " +
            "WHERE :expenses AND effective_date >= :from AND effective_date <= :through " +
            "UNION ALL " +
            "SELECT 1 AS kind, id, effective_date, created_at, sequence FROM income " +
            "WHERE :incomes AND effective_date >= :from AND effective_date <= :through" +
            ") ORDER BY effective_date DESC, created_at DESC, kind, sequence DESC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun page(
        from: String,
        through: String,
        expenses: Boolean,
        incomes: Boolean,
        offset: Int,
        limit: Int
    ): List<MovementKey>

    /** How many movements [page] can list for the same arguments, in all. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM expense WHERE :expenses " +
            "AND effective_date >= :from AND effective_date <= :through) + " +
            "(SELECT COUNT(*) FROM income WHERE :incomes " +
            "AND effective_date >= :from AND effective_date <= :through)"
    )
    suspend fun count(from: String, through: String, expenses: Boolean, incomes: Boolean): Long

    /** Every expense's day and amount from [from] to [through], both included. */
    @Query(
        "SELECT effective_date, amount_minor, currency FROM expense " +
            "WHERE effective_date >= :from AND effective_date <= :through"
    )
    suspend fun expenseAmounts(from: String, through: String): List<DayAmountRow>

    @Query("SELECT * FROM expense WHERE id IN (:ids)")
    suspend fun expenses(ids: List<String>): List<ExpenseEntity>

    @Query("SELECT * FROM income WHERE id IN (:ids)")
    suspend fun incomes(ids: List<String>): List<IncomeEntity>
}
