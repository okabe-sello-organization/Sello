package com.software.sello.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonth
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomePage
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.ReadState
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * Reads income. A list is a bounded page; a month's total is every income of the
 * month added exactly, in the same transaction as the revision it is reported at.
 * One row that cannot be read fails the read: it is never left out or counted as zero.
 */
class RoomIncomeReads(private val database: SelloDatabase) : IncomeReads {
    override suspend fun byId(id: IncomeId): Outcome<Income?, StorageFailure> = reading {
        when (val row = database.incomeDao().byId(id.value)) {
            null -> Outcome.Success(null)
            else -> row.toDomain()
        }
    }

    override suspend fun inMonth(
        month: YearMonth,
        after: IncomeCursor?,
        limit: Int
    ): Outcome<IncomePage, StorageFailure> = reading {
        require(limit in 1..IncomeReads.MAX_PAGE) {
            "a page holds 1 to ${IncomeReads.MAX_PAGE} incomes"
        }
        val until = month.plusMonths(1).atDay(1).toString()
        // One more than asked for, only to learn whether another page exists.
        val rows = database.incomeDao().newest(
            from = month.atDay(1).toString(),
            until = until,
            beforeDate = after?.date?.toString() ?: until,
            beforeSequence = after?.sequence ?: Long.MAX_VALUE,
            limit = limit + 1
        )
        when (val incomes = rows.take(limit).decodeAll { it.toDomain() }) {
            is Outcome.Failure -> incomes

            is Outcome.Success -> {
                val last = incomes.value.lastOrNull()
                val next = last?.takeIf { rows.size > limit }
                    ?.let { IncomeCursor(it.date, it.sequence) }
                Outcome.Success(IncomePage(incomes.value, next))
            }
        }
    }

    override suspend fun month(month: YearMonth): Outcome<IncomeMonth, StorageFailure> =
        reading { database.withTransaction { monthIn(month) } }

    private suspend fun monthIn(month: YearMonth): Outcome<IncomeMonth, StorageFailure> {
        val profile = when (val stored = profileFrom(database.profileDao().rows())) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return stored
        }
        val incomes = when (val rows = database.monthIncomes(month)) {
            is Outcome.Success -> rows.value
            is Outcome.Failure -> return rows
        }
        val total = ExactTotal.of(Currency.COP, incomes.map { it.amount.money }).toMoney()
        return when (total) {
            is Outcome.Success -> Outcome.Success(
                IncomeMonth(month, profile.generation, profile.revision, total.value, incomes.size)
            )

            // Rows that each read correctly but cannot be added: the month has no total.
            is Outcome.Failure ->
                Outcome.Failure(StorageFailure.Integrity(IncomeEntity.TABLE, null, "amount_minor"))
        }
    }

    /**
     * Every command advances the revision in the profile row, so watching that row is
     * watching every committed change. Bursts are conflated.
     */
    override fun observeMonth(month: YearMonth): Flow<IncomeMonthState> =
        database.observeOnRevision { month(month) }
}
