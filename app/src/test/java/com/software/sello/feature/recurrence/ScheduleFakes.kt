package com.software.sello.feature.recurrence

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommittedSchedule
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.StoredSchedule
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun scheduleId(number: Int) =
    (ScheduleId.of("00000000-0000-4000-8000-%012d".format(number)) as Outcome.Success).value

/**
 * Schedule commands a test answers by hand. By default a new schedule is committed as
 * schedule 700 and kept in [book], so a read back finds what was sent.
 */
class HandScheduleCommands(private val book: HandScheduleReads? = null) : ScheduleCommands {
    val submitted = mutableListOf<ScheduleCommand>()
    val looked = mutableListOf<OperationId>()
    var answer: (ScheduleCommand) -> ScheduleCommandOutcome = { command ->
        if (command is CreateSchedule) book?.keep(command, scheduleId(700))
        CommandOutcome.Committed(committed(command.operationId), replayed = false)
    }
    var found: (OperationId) -> Outcome<CommittedSchedule?, StorageFailure> =
        { Outcome.Success(null) }

    fun committed(operation: OperationId, schedule: ScheduleId = scheduleId(700)) =
        CommittedSchedule(
            OperationReceipt(operation, OperationKind.CreateSchedule, 1, 1, Instant.EPOCH),
            schedule
        )

    override suspend fun submit(command: ScheduleCommand): ScheduleCommandOutcome {
        submitted += command
        return answer(command)
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedSchedule?, StorageFailure> {
        looked += operationId
        return found(operationId)
    }
}

/** The schedules a test holds; [failing] makes every read fail. */
class HandScheduleReads : ScheduleReads {
    val schedules = mutableListOf<StoredSchedule>()
    var failing = false
    val state = MutableStateFlow<ScheduleBookState>(ReadState.Loading)

    fun keep(command: CreateSchedule, id: ScheduleId) {
        val recurrence =
            (
                Recurrence.of(
                    command.rule,
                    command.start,
                    command.end,
                    emptyList()
                ) as Outcome.Success
                )
                .value
        schedules += StoredSchedule(
            Schedule(id, command.entry, recurrence),
            sequence = schedules.size + 1L,
            handled = null,
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
    }

    override suspend fun book(): Outcome<ScheduleBook, StorageFailure> = if (failing) {
        Outcome.Failure(StorageFailure.Unavailable("hand"))
    } else {
        Outcome.Success(ScheduleBook(1, 1, schedules.toList()))
    }

    override fun observe(): Flow<ScheduleBookState> = state
}
