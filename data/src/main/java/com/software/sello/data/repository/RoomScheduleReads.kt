package com.software.sello.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.profileFrom
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * Reads every schedule with its pauses in one transaction at one revision. A schedule
 * that cannot be read fails the read: it is never left out, and a failed read is never
 * "no schedules".
 */
class RoomScheduleReads(private val database: SelloDatabase) : ScheduleReads {
    override suspend fun book(): Outcome<ScheduleBook, StorageFailure> = reading {
        database.withTransaction {
            val profile = when (val stored = profileFrom(database.profileDao().rows())) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return@withTransaction stored
            }
            when (val schedules = database.storedSchedules()) {
                is Outcome.Success ->
                    Outcome.Success(
                        ScheduleBook(profile.generation, profile.revision, schedules.value)
                    )

                is Outcome.Failure -> schedules
            }
        }
    }

    override fun observe(): Flow<ScheduleBookState> = database.observeOnRevision { book() }
}
