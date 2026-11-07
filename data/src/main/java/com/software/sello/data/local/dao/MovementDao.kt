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

/** What makes an expense one Día a día's search finds; see [MovementDao.searchExpenses]. */
private const val SEARCH_MATCH =
    "(:byWords AND instr(search_key, :key) > 0) " +
        "OR (:byName AND category_id IN " +
        "(SELECT id FROM category WHERE instr(name_key, :nameKey) > 0)) " +
        "OR (:byAmount AND amount_minor = :amount AND currency = :currency)"

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

    /**
     * The expenses Día a día's search finds (SELLO-193), in list order, after the cursor
     * when [hasCursor] and at most [limit]. One matches when [byWords] and its folded note
     * holds [key], when [byName] and its category's name key holds [nameKey], or when
     * [byAmount] and it is exactly [amount] of [currency]. The cursor is a movement's day,
     * creation time, [MovementKind] and sequence: an expense created at the same moment
     * comes after an expense cursor only with a lower sequence, never after an income's.
     */
    @Query(
        "SELECT 0 AS kind, id FROM expense WHERE (" + SEARCH_MATCH + ") " +
            "AND (NOT :hasCursor OR effective_date < :date " +
            "OR (effective_date = :date AND (created_at < :createdAt " +
            "OR (created_at = :createdAt AND :cursorKind = 0 AND sequence < :sequence)))) " +
            "ORDER BY effective_date DESC, created_at DESC, sequence DESC LIMIT :limit"
    )
    suspend fun searchExpenses(
        byWords: Boolean,
        key: String,
        byName: Boolean,
        nameKey: String,
        byAmount: Boolean,
        amount: Long,
        currency: String,
        hasCursor: Boolean,
        date: String,
        createdAt: Long,
        cursorKind: Int,
        sequence: Long,
        limit: Int
    ): List<MovementKey>

    /** The day and amount of every expense [searchExpenses] finds, for its totals. */
    @Query(
        "SELECT effective_date, amount_minor, currency FROM expense WHERE " + SEARCH_MATCH
    )
    suspend fun searchedAmounts(
        byWords: Boolean,
        key: String,
        byName: Boolean,
        nameKey: String,
        byAmount: Boolean,
        amount: Long,
        currency: String
    ): List<DayAmountRow>

    /** Every income: incomes have no stored search key, so search folds them as read. */
    @Query("SELECT * FROM income")
    suspend fun allIncomes(): List<IncomeEntity>

    @Query("SELECT * FROM expense WHERE id IN (:ids)")
    suspend fun expenses(ids: List<String>): List<ExpenseEntity>

    @Query("SELECT * FROM income WHERE id IN (:ids)")
    suspend fun incomes(ids: List<String>): List<IncomeEntity>
}
