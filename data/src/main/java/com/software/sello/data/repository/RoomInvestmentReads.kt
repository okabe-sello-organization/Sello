package com.software.sello.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toHistory
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentSummary
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * Reads every investment and rate and works each investment out with [InvestmentPolicy],
 * in one transaction at one revision. An investment or rate that cannot be read or
 * worked out fails the read: it is never left out, and a failed read is never "no
 * investments" or a zero.
 */
class RoomInvestmentReads(private val database: SelloDatabase) : InvestmentReads {
    override suspend fun board(): Outcome<InvestmentBoard, StorageFailure> = reading {
        database.withTransaction {
            val profile = when (val stored = profileFrom(database.profileDao().rows())) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return@withTransaction stored
            }
            val movements = database.investmentDao().allMovements().groupBy { it.investmentId }
            val investments = mutableListOf<InvestmentSummary>()
            for (row in database.investmentDao().all()) {
                when (val summary = summary(row.toHistory(movements[row.id].orEmpty()))) {
                    is Outcome.Success -> investments += summary.value
                    is Outcome.Failure -> return@withTransaction summary
                }
            }
            val rates = when (
                val decoded = database.investmentDao().rates().decodeAll { it.toDomain() }
            ) {
                is Outcome.Success -> decoded.value
                is Outcome.Failure -> return@withTransaction decoded
            }
            val current = InvestmentPolicy.currentRate(rates)
            Outcome.Success(
                InvestmentBoard(
                    generation = profile.generation,
                    revision = profile.revision,
                    investments = investments,
                    rates = UsdRateBook(current, rates.sortedWith(newestFirst)),
                    overview = InvestmentPolicy.overview(
                        investments.map { it.history to it.figures },
                        current
                    )
                )
            )
        }
    }

    override fun observeBoard(): Flow<InvestmentBoardState> = flow {
        emit(InvestmentBoardState.Loading)
        var lastGood: InvestmentBoard? = null
        try {
            // Every command moves the profile's revision in its transaction.
            database.profileDao().changes().conflate().collect {
                when (val read = board()) {
                    is Outcome.Success -> {
                        lastGood = read.value
                        emit(InvestmentBoardState.Ready(read.value))
                    }

                    is Outcome.Failure -> emit(InvestmentBoardState.Failed(read.error, lastGood))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SQLException) {
            emit(InvestmentBoardState.Failed(unavailable(failure), lastGood))
        } catch (failure: IllegalStateException) {
            emit(InvestmentBoardState.Failed(unavailable(failure), lastGood))
        }
    }

    private fun unavailable(failure: Exception) =
        StorageFailure.Unavailable(failure.javaClass.simpleName)

    override suspend fun investment(id: InvestmentId): Outcome<InvestmentSummary?, StorageFailure> =
        reading {
            database.withTransaction {
                val row = database.investmentDao().byId(id.value)
                    ?: return@withTransaction Outcome.Success(null)
                summary(row.toHistory(database.investmentDao().movements(row.id)))
            }
        }

    private fun summary(
        read: Outcome<InvestmentHistory, StorageFailure.Integrity>
    ): Outcome<InvestmentSummary, StorageFailure> {
        val history = when (read) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        return when (val figures = InvestmentPolicy.figures(history)) {
            is Outcome.Success -> Outcome.Success(InvestmentSummary(history, figures.value))
            is Outcome.Failure -> Outcome.Failure(damage(history.investment.id, figures.error))
        }
    }

    private companion object {
        /** The rate history as shown: the latest date first, then the last entered. */
        val newestFirst: Comparator<UsdRateEntry> =
            compareByDescending<UsdRateEntry> { it.date }.thenByDescending { it.sequence }
    }
}
