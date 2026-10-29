package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryReplay
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryoverCorrectionReplay
import com.software.sello.domain.policy.SignedCarryoverPolicy
import com.software.sello.domain.port.CarryCatchUpNotice
import com.software.sello.domain.port.CarryHistory
import com.software.sello.domain.port.CarryReadFailure
import com.software.sello.domain.port.CarryoverReads
import com.software.sello.domain.port.FinancialClock
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/**
 * Carry read from Room and worked out by the domain calculation (ADR 0020 CP7). The rows
 * come out of the database in one transaction, so every figure is of one revision; the
 * replay runs on [computation], never on the caller's thread. Nothing is stored or cached:
 * every read reads again, so an older revision is never shown as the current one.
 */
class RoomCarryoverReads(
    private val database: SelloDatabase,
    private val clock: FinancialClock,
    private val computation: CoroutineContext
) : CarryoverReads {
    private class HistoryRows(
        val profile: FinancialProfile,
        val chains: List<CarryChain>,
        val rows: CarryRows?
    )

    override suspend fun history(
        categoryId: CategoryId,
        through: YearMonth
    ): Outcome<CarryHistory, CarryReadFailure> {
        val today = clock.today.value.date
        val month = YearMonth.from(today)
        if (through > month) {
            return Outcome.Failure(CarryReadFailure.Carryover(CarryoverFailure.FutureMonth(month)))
        }
        val read = when (
            val stored = reading { database.withTransaction { historyRows(categoryId, through) } }
        ) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return Outcome.Failure(CarryReadFailure.Storage(stored.error))
        } ?: return Outcome.Failure(CarryReadFailure.CategoryMissing)
        return withContext(computation) {
            val replay = when (val rows = read.rows) {
                null -> Outcome.Success(CarryReplay(emptyList(), emptyList()))

                else -> when (val input = rows.input(today)) {
                    is Outcome.Success -> SignedCarryoverPolicy.replay(input.value, month, through)
                    is Outcome.Failure -> input
                }
            }
            when (replay) {
                is Outcome.Failure -> Outcome.Failure(CarryReadFailure.Carryover(replay.error))

                is Outcome.Success -> Outcome.Success(
                    CarryHistory(
                        categoryId,
                        read.profile.generation,
                        read.profile.revision,
                        today,
                        read.chains,
                        replay.value
                    )
                )
            }
        }
    }

    /** Null when the category does not exist. */
    private suspend fun historyRows(
        categoryId: CategoryId,
        through: YearMonth
    ): Outcome<HistoryRows?, StorageFailure> {
        val profile = when (val stored = profileFrom(database.profileDao().rows())) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return stored
        }
        database.categoryDao().byId(categoryId.value) ?: return Outcome.Success(null)
        val chains = when (val stored = database.carryChains(categoryId.value)) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return stored
        }
        // The replay lists every chain up to [through]: read from the earliest one's start.
        val from = chains.filter { it.start <= through }.minOfOrNull { it.start }
            ?: return Outcome.Success(HistoryRows(profile, chains, null))
        return when (val rows = database.carryRows(categoryId, chains, from, through)) {
            is Outcome.Success -> Outcome.Success(HistoryRows(profile, chains, rows.value))
            is Outcome.Failure -> rows
        }
    }

    /** One category the listed payments reach, with those payments as recorded now. */
    private class Reached(val rows: CarryRows, val recorded: List<Expense>)

    override suspend fun catchUpNotice(
        recorded: List<ExpenseId>
    ): Outcome<CarryCatchUpNotice, CarryReadFailure> {
        val today = clock.today.value.date
        val month = YearMonth.from(today)
        val read = when (
            val stored = reading { database.withTransaction { noticeRows(recorded, month) } }
        ) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return Outcome.Failure(CarryReadFailure.Storage(stored.error))
        }
        val (profile, reached) = read
        return withContext(computation) {
            val categories = ArrayList<CarryCorrection>(reached.size)
            for (category in reached) {
                val listed = category.recorded.map { it.id }.toSet()
                // The month before catch-up: the listed payments not yet recorded.
                val before = when (
                    val input = category.rows.input(
                        today,
                        expenses = category.rows.expenses.filterNot { it.id in listed }
                    )
                ) {
                    is Outcome.Success -> input.value

                    is Outcome.Failure ->
                        return@withContext Outcome.Failure(CarryReadFailure.Carryover(input.error))
                }
                val payments = category.recorded.mapNotNull { expense ->
                    val origin = expense.origin ?: return@mapNotNull null
                    ReservedPayment(
                        origin.schedule,
                        expense.categoryId,
                        expense.note,
                        Occurrence(expense.date, origin.occurrence.slot),
                        expense.amount
                    )
                }
                when (
                    val change = CarryoverCorrectionReplay.catchUp(before, payments, month)
                ) {
                    is Outcome.Success -> if (!change.value.isEmpty) categories += change.value

                    is Outcome.Failure ->
                        return@withContext Outcome.Failure(CarryReadFailure.Carryover(change.error))
                }
            }
            Outcome.Success(
                CarryCatchUpNotice(profile.generation, profile.revision, today, categories)
            )
        }
    }

    /**
     * The listed payments still recorded, grouped by the categories whose chains they can
     * reach. One deleted since is simply not read: it leaves the notice (CP18).
     */
    private suspend fun noticeRows(
        recorded: List<ExpenseId>,
        month: YearMonth
    ): Outcome<Pair<FinancialProfile, List<Reached>>, StorageFailure> {
        val profile = when (val stored = profileFrom(database.profileDao().rows())) {
            is Outcome.Success -> stored.value
            is Outcome.Failure -> return stored
        }
        val expenses = ArrayList<Expense>(recorded.size)
        for (id in recorded.distinct()) {
            val row = database.expenseDao().byId(id.value) ?: continue
            when (val decoded = row.toDomain()) {
                is Outcome.Success -> expenses += decoded.value
                is Outcome.Failure -> return decoded
            }
        }
        val reached = ArrayList<Reached>()
        for ((categoryId, of) in expenses.groupBy { it.categoryId }) {
            val chains = when (val stored = database.carryChains(categoryId.value)) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return stored
            }
            // Only the chains a payment of an earlier month lands in change (CP18); the
            // notice replays those, from the earliest one's start.
            val landed = chains.filter { chain ->
                of.any { YearMonth.from(it.date) < month && chain.covers(YearMonth.from(it.date)) }
            }
            val from = landed.minOfOrNull { it.start } ?: continue
            when (val rows = database.carryRows(categoryId, landed, from, month)) {
                is Outcome.Success -> reached += Reached(rows.value, of)
                is Outcome.Failure -> return rows
            }
        }
        return Outcome.Success(profile to reached)
    }
}
