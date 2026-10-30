package com.software.sello.devtools

import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommands
import java.time.LocalDate

/*
 * The real commands, run through the sandbox's [WriteFence]. They add no rule and
 * change no result. [saved] hears of each change written now, not of a replay, so the
 * sandbox knows its history has moved on. Lookups by operation pass straight through.
 */

/** [block] through the fence; [saved] hears of a change it wrote now, never of a replay. */
private suspend fun <O : CommandOutcome<*, *>> WriteFence.command(
    saved: () -> Unit,
    block: suspend () -> O
): O = write(block).also { if (it is CommandOutcome.Committed<*> && !it.replayed) saved() }

class FencedExpenseCommands(
    private val commands: ExpenseCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : ExpenseCommands by commands {
    override suspend fun create(command: CreateExpense) =
        fence.command(saved) { commands.create(command) }

    override suspend fun edit(command: EditExpense) =
        fence.command(saved) { commands.edit(command) }

    override suspend fun delete(command: DeleteExpense) =
        fence.command(saved) { commands.delete(command) }

    override suspend fun restore(command: RestoreExpense) =
        fence.command(saved) { commands.restore(command) }
}

class FencedIncomeCommands(
    private val commands: IncomeCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : IncomeCommands by commands {
    override suspend fun create(command: CreateIncome) =
        fence.command(saved) { commands.create(command) }

    override suspend fun edit(command: EditIncome) = fence.command(saved) { commands.edit(command) }

    override suspend fun delete(command: DeleteIncome) =
        fence.command(saved) { commands.delete(command) }

    override suspend fun restore(command: RestoreIncome) =
        fence.command(saved) { commands.restore(command) }
}

class FencedCategoryCommands(
    private val commands: CategoryCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : CategoryCommands by commands {
    override suspend fun submit(command: CategoryCommand) =
        fence.command(saved) { commands.submit(command) }
}

class FencedGoalCommands(
    private val commands: GoalCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : GoalCommands by commands {
    override suspend fun submit(command: GoalCommand) =
        fence.command(saved) { commands.submit(command) }
}

class FencedInvestmentCommands(
    private val commands: InvestmentCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : InvestmentCommands by commands {
    override suspend fun submit(command: InvestmentCommand) =
        fence.command(saved) { commands.submit(command) }
}

class FencedScheduleCommands(
    private val commands: ScheduleCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : ScheduleCommands by commands {
    override suspend fun submit(command: ScheduleCommand) =
        fence.command(saved) { commands.submit(command) }
}

/** Catch-up on opening, through the fence; a batch that recorded anything is a change. */
class FencedScheduleCatchUp(
    private val store: ScheduleCatchUpStore,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : ScheduleCatchUpStore {
    override suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch = fence.write {
        store.recordDue(today, limit)
    }.also { if (it is CatchUpBatch.Recorded && it.occurrences.isNotEmpty()) saved() }
}
