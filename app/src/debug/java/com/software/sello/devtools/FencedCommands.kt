package com.software.sello.devtools

import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome

/*
 * The real commands, run through the sandbox's [WriteFence]. They add no rule and
 * change no result. [saved] hears of each change written now, not of a replay, so the
 * sandbox knows its history has moved on. Lookups by operation pass straight through.
 */

class FencedExpenseCommands(
    private val commands: ExpenseCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : ExpenseCommands by commands {
    override suspend fun create(command: CreateExpense) = run { commands.create(command) }

    override suspend fun edit(command: EditExpense) = run { commands.edit(command) }

    override suspend fun delete(command: DeleteExpense) = run { commands.delete(command) }

    override suspend fun restore(command: RestoreExpense) = run { commands.restore(command) }

    private suspend fun run(block: suspend () -> ExpenseCommandOutcome) = fence.write(block).also {
        if (it is ExpenseCommandOutcome.Committed && !it.replayed) saved()
    }
}

class FencedIncomeCommands(
    private val commands: IncomeCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : IncomeCommands by commands {
    override suspend fun create(command: CreateIncome) = run { commands.create(command) }

    override suspend fun edit(command: EditIncome) = run { commands.edit(command) }

    override suspend fun delete(command: DeleteIncome) = run { commands.delete(command) }

    override suspend fun restore(command: RestoreIncome) = run { commands.restore(command) }

    private suspend fun run(block: suspend () -> IncomeCommandOutcome) = fence.write(block).also {
        if (it is IncomeCommandOutcome.Committed && !it.replayed) saved()
    }
}

class FencedCategoryCommands(
    private val commands: CategoryCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : CategoryCommands by commands {
    override suspend fun submit(command: CategoryCommand) = fence.write {
        commands.submit(command)
    }.also { if (it is CategoryCommandOutcome.Committed && !it.replayed) saved() }
}

class FencedGoalCommands(
    private val commands: GoalCommands,
    private val fence: WriteFence,
    private val saved: () -> Unit
) : GoalCommands by commands {
    override suspend fun submit(command: GoalCommand): GoalCommandOutcome = fence.write {
        commands.submit(command)
    }.also { if (it is GoalCommandOutcome.Committed && !it.replayed) saved() }
}
