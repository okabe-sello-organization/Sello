package com.software.sello.data.repository

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryEdit
import com.software.sello.domain.port.ChangeCategoryIcon
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.DeleteSchedule
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditGoal
import com.software.sello.domain.port.EditGoalMovement
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalEdit
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreGoalMovement
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.ResumeSchedule
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleEdit
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import com.software.sello.domain.port.UndoGoalCompletion
import java.security.MessageDigest

/*
 * The identity of a command's input, used to tell a replay from a conflict. It is a
 * SHA-256 over the fields that carry meaning, each written as its UTF-8 byte length,
 * a colon and its bytes, so no two different inputs can produce the same sequence.
 * Every identity starts with the kind, a format version that changes if that kind's
 * field list ever does, and the generation. The operation identifier is not part of it.
 */

internal fun inputDigest(command: CreateExpense): String = digest(
    OperationKind.CreateExpense.key,
    "1",
    command.generation.toString(),
    command.categoryId.value,
    command.amount.money.minorUnits.toString(),
    command.amount.money.currency.code,
    command.date.toString(),
    if (command.note == null) "0" else "1",
    command.note?.value.orEmpty()
)

internal fun inputDigest(command: EditExpense): String = digest(
    OperationKind.EditExpense.key,
    "1",
    command.generation.toString(),
    command.expenseId.value,
    command.expectedVersion.toString(),
    command.categoryId.value,
    command.amount.money.minorUnits.toString(),
    command.amount.money.currency.code,
    command.date.toString(),
    if (command.note == null) "0" else "1",
    command.note?.value.orEmpty()
)

internal fun inputDigest(command: DeleteExpense): String = digest(
    OperationKind.DeleteExpense.key,
    "1",
    command.generation.toString(),
    command.expenseId.value,
    command.expectedVersion.toString()
)

internal fun inputDigest(command: RestoreExpense): String = digest(
    OperationKind.RestoreExpense.key,
    "1",
    command.generation.toString(),
    command.deletionId.value
)

/** Zero or one name: the key says which, so "other" named "salary" is not salary. */
private val IncomeSource.fields: List<String>
    get() = listOf(key, (this as? IncomeSource.Other)?.name?.value.orEmpty())

internal fun inputDigest(command: CreateIncome): String = digest(
    OperationKind.CreateIncome.key,
    "1",
    command.generation.toString(),
    command.amount.money.minorUnits.toString(),
    command.amount.money.currency.code,
    *command.source.fields.toTypedArray(),
    command.date.toString(),
    if (command.note == null) "0" else "1",
    command.note?.value.orEmpty()
)

internal fun inputDigest(command: EditIncome): String = digest(
    OperationKind.EditIncome.key,
    "1",
    command.generation.toString(),
    command.incomeId.value,
    command.expectedVersion.toString(),
    command.amount.money.minorUnits.toString(),
    command.amount.money.currency.code,
    *command.source.fields.toTypedArray(),
    command.date.toString(),
    if (command.note == null) "0" else "1",
    command.note?.value.orEmpty()
)

internal fun inputDigest(command: DeleteIncome): String = digest(
    OperationKind.DeleteIncome.key,
    "1",
    command.generation.toString(),
    command.incomeId.value,
    command.expectedVersion.toString()
)

internal fun inputDigest(command: RestoreIncome): String = digest(
    OperationKind.RestoreIncome.key,
    "1",
    command.generation.toString(),
    command.deletionId.value
)

internal val CategoryCommand.kind: OperationKind
    get() = when (this) {
        is CreateCategory -> OperationKind.CreateCategory
        is RenameCategory -> OperationKind.RenameCategory
        is ChangeCategoryIcon -> OperationKind.ChangeCategoryIcon
        is ArchiveCategory -> OperationKind.ArchiveCategory
        is UnarchiveCategory -> OperationKind.UnarchiveCategory
        is SetDefaultLimit -> OperationKind.SetDefaultLimit
        is SetMonthLimit -> OperationKind.SetMonthLimit
    }

internal fun inputDigest(command: CategoryCommand): String {
    val subject = when (command) {
        is CategoryEdit -> listOf(command.categoryId.value, command.expectedVersion.toString())
        is CreateCategory -> emptyList()
    }
    val change = when (command) {
        is CreateCategory -> listOf(command.name.value, command.icon.value) + command.limit.fields
        is RenameCategory -> listOf(command.name.value)
        is ChangeCategoryIcon -> listOf(command.icon.value)
        is ArchiveCategory, is UnarchiveCategory -> emptyList()
        is SetDefaultLimit -> command.limit.fields
        is SetMonthLimit -> listOf(command.month.toString()) + command.limit.fields
    }
    val head = listOf(command.kind.key, "1", command.generation.toString())
    return digest(*(head + subject + change).toTypedArray())
}

/** Zero and unlimited must not collide, so the kind is a field of its own. */
private val BudgetLimit.fields: List<String>
    get() = when (this) {
        is BudgetLimit.Finite ->
            listOf("finite", amount.minorUnits.toString(), amount.currency.code)

        BudgetLimit.Unlimited -> listOf("unlimited", "", "")
    }

internal val GoalCommand.kind: OperationKind
    get() = when (this) {
        is CreateGoal -> OperationKind.CreateGoal
        is EditGoal -> OperationKind.EditGoal
        is ChangeGoalTarget -> OperationKind.ChangeGoalTarget
        is RecordGoalMovement -> OperationKind.RecordGoalMovement
        is EditGoalMovement -> OperationKind.EditGoalMovement
        is DeleteGoalMovement -> OperationKind.DeleteGoalMovement
        is RestoreGoalMovement -> OperationKind.RestoreGoalMovement
        is CompleteGoal -> OperationKind.CompleteGoal
        is UndoGoalCompletion -> OperationKind.UndoGoalCompletion
        is DeleteGoal -> OperationKind.DeleteGoal
        is FeatureGoal -> OperationKind.FeatureGoal
    }

private val TransactionAmount.fields: List<String>
    get() = listOf(money.minorUnits.toString(), money.currency.code)

private val Note?.fields: List<String>
    get() = listOf(if (this == null) "0" else "1", this?.value.orEmpty())

/** [CompleteGoal.undoFor] is how long Deshacer is offered, not what was asked: it is left out. */
internal fun inputDigest(command: GoalCommand): String {
    val subject = when (command) {
        is GoalEdit -> listOf(command.goalId.value, command.expectedVersion.toString())
        is CreateGoal, is RestoreGoalMovement, is UndoGoalCompletion, is FeatureGoal -> emptyList()
    }
    val change = when (command) {
        is CreateGoal -> listOf(
            command.name.value,
            command.icon.value,
            command.targetMonth?.toString().orEmpty()
        ) + command.target.fields

        is EditGoal -> listOf(
            command.name.value,
            command.icon.value,
            command.targetMonth?.toString().orEmpty()
        )

        is ChangeGoalTarget -> command.target.fields

        is RecordGoalMovement -> listOf(command.kind.name, command.date.toString()) +
            command.amount.fields + command.note.fields

        is EditGoalMovement -> listOf(
            command.movementId.value,
            command.movementVersion.toString(),
            command.date.toString()
        ) + command.amount.fields + command.note.fields

        is DeleteGoalMovement ->
            listOf(command.movementId.value, command.movementVersion.toString())

        is RestoreGoalMovement -> listOf(command.deletionId.value)

        is CompleteGoal, is DeleteGoal -> emptyList()

        is UndoGoalCompletion -> listOf(command.completionId.value)

        is FeatureGoal -> listOf(command.goalId.value)
    }
    val head = listOf(command.kind.key, "1", command.generation.toString())
    return digest(*(head + subject + change).toTypedArray())
}

internal val ScheduleCommand.kind: OperationKind
    get() = when (this) {
        is CreateSchedule -> OperationKind.CreateSchedule
        is ChangeSchedule -> OperationKind.ChangeSchedule
        is PauseSchedule -> OperationKind.PauseSchedule
        is ResumeSchedule -> OperationKind.ResumeSchedule
        is DeleteSchedule -> OperationKind.DeleteSchedule
    }

private val ScheduledEntry.fields: List<String>
    get() = when (this) {
        is ScheduledEntry.Payment -> listOf("payment", categoryId.value)

        is ScheduledEntry.Income -> listOf(
            "income",
            source.key,
            (source as? IncomeSource.Other)?.name?.value.orEmpty()
        )
    } + amount.fields + note.fields

private val RepeatRule.fields: List<String>
    get() = when (this) {
        is RepeatRule.Monthly -> listOf("monthly", day.toString(), "")
        is RepeatRule.TwiceMonthly -> listOf("twice_monthly", first.toString(), second.toString())
        is RepeatRule.EveryDays -> listOf("every_days", days.toString(), "")
        is RepeatRule.EveryWeeks -> listOf("every_weeks", weeks.toString(), "")
    }

internal fun inputDigest(command: ScheduleCommand): String {
    val subject = when (command) {
        is ScheduleEdit -> listOf(command.scheduleId.value, command.expectedVersion.toString())
        is CreateSchedule -> emptyList()
    }
    val change = when (command) {
        is CreateSchedule ->
            command.entry.fields + command.rule.fields +
                listOf(command.start.toString(), command.end?.toString().orEmpty())

        is ChangeSchedule ->
            command.entry.fields + command.rule.fields +
                listOf(command.start.toString(), command.end?.toString().orEmpty())

        is PauseSchedule, is ResumeSchedule, is DeleteSchedule -> emptyList()
    }
    val head = listOf(command.kind.key, "1", command.generation.toString())
    return digest(*(head + subject + change).toTypedArray())
}

private fun digest(vararg fields: String): String {
    val sha256 = MessageDigest.getInstance("SHA-256")
    for (field in fields) {
        val bytes = field.toByteArray(Charsets.UTF_8)
        sha256.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
        sha256.update(bytes)
    }
    return sha256.digest().joinToString("") { "%02x".format(it) }
}
