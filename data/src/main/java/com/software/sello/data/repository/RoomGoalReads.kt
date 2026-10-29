package com.software.sello.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.GoalEventEntity
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toHistory
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * Reads goals and works each one out with [GoalPolicy], in one transaction at one
 * revision. A goal that cannot be read or worked out fails the read: it is never left
 * out, and a failed read is never "no goals".
 */
class RoomGoalReads(private val database: SelloDatabase) : GoalReads {
    override suspend fun board(): Outcome<GoalBoard, StorageFailure> = reading {
        database.withTransaction {
            val profile = when (val stored = profileFrom(database.profileDao().rows())) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return@withTransaction stored
            }
            val events = database.goalDao().allEvents().groupBy { it.goalId }
            val goals = mutableListOf<GoalSummary>()
            for (row in database.goalDao().all()) {
                when (val summary = summary(row.toHistory(events[row.id].orEmpty()))) {
                    is Outcome.Success -> goals += summary.value
                    is Outcome.Failure -> return@withTransaction summary
                }
            }
            val chosen = database.goalDao().featured()?.goalId?.let { GoalId.of(it) }
            val chosenId = when (chosen) {
                null -> null

                is Outcome.Success -> chosen.value

                is Outcome.Failure -> return@withTransaction Outcome.Failure(
                    StorageFailure.Integrity("featured_goal", null, "goal_id")
                )
            }
            Outcome.Success(
                GoalBoard(
                    generation = profile.generation,
                    revision = profile.revision,
                    goals = goals,
                    featured = GoalPolicy.featured(
                        goals.map { it.history.goal to it.progress },
                        chosenId
                    )
                )
            )
        }
    }

    override fun observeBoard(): Flow<GoalBoardState> = database.observeOnRevision { board() }

    override suspend fun goal(id: GoalId): Outcome<GoalSummary?, StorageFailure> = reading {
        database.withTransaction {
            val row =
                database.goalDao().byId(id.value) ?: return@withTransaction Outcome.Success(null)
            summary(row.toHistory(database.goalDao().events(row.id)))
        }
    }

    private fun summary(
        read: Outcome<GoalHistory, StorageFailure.Integrity>
    ): Outcome<GoalSummary, StorageFailure> {
        val history = when (read) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        return when (val progress = GoalPolicy.progress(history)) {
            is Outcome.Success -> Outcome.Success(GoalSummary(history, progress.value))

            is Outcome.Failure -> Outcome.Failure(
                StorageFailure.Integrity(
                    GoalEventEntity.TABLE,
                    history.goal.id.value,
                    "amount_minor"
                )
            )
        }
    }
}
