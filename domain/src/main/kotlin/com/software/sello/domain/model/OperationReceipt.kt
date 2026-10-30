package com.software.sello.domain.model

import java.time.Instant

/** What an operation did. [key] is the stable stored spelling. */
enum class OperationKind(val key: String) {
    CreateExpense("expense.create"),
    EditExpense("expense.edit"),
    DeleteExpense("expense.delete"),
    RestoreExpense("expense.restore"),
    CreateIncome("income.create"),
    EditIncome("income.edit"),
    DeleteIncome("income.delete"),
    RestoreIncome("income.restore"),
    CreateCategory("category.create"),
    RenameCategory("category.rename"),
    ChangeCategoryIcon("category.change_icon"),
    ArchiveCategory("category.archive"),
    UnarchiveCategory("category.unarchive"),
    SetDefaultLimit("category.set_default_limit"),
    SetMonthLimit("category.set_month_limit"),
    EnableCarry("category.enable_carry"),
    DisableCarry("category.disable_carry"),
    CreateGoal("goal.create"),
    EditGoal("goal.edit"),
    ChangeGoalTarget("goal.change_target"),
    RecordGoalMovement("goal.record_movement"),
    EditGoalMovement("goal.edit_movement"),
    DeleteGoalMovement("goal.delete_movement"),
    RestoreGoalMovement("goal.restore_movement"),
    CompleteGoal("goal.complete"),
    UndoGoalCompletion("goal.undo_completion"),
    DeleteGoal("goal.delete"),
    FeatureGoal("goal.feature"),
    CreateInvestment("investment.create"),
    EditInvestment("investment.edit"),
    RecordInvestmentMovement("investment.record_movement"),

    /** Several movements of one investment, saved together or not at all. */
    RecordInvestmentMovements("investment.record_movements"),
    EditInvestmentMovement("investment.edit_movement"),
    DeleteInvestmentMovement("investment.delete_movement"),
    RestoreInvestmentMovement("investment.restore_movement"),
    DeleteInvestment("investment.delete"),
    SetUsdRate("investment.set_usd_rate"),
    CreateSchedule("schedule.create"),
    ChangeSchedule("schedule.change"),
    PauseSchedule("schedule.pause"),
    ResumeSchedule("schedule.resume"),
    DeleteSchedule("schedule.delete"),

    /** A batch catch-up recorded across schedules. It has no single subject. */
    CatchUpSchedules("schedule.catch_up"),

    /** The whole financial history replaced by a backup. It has no single subject. */
    RestoreHistory("history.restore"),

    /** The whole financial history erased on request. It has no single subject. */
    ResetHistory("history.reset")
}

/**
 * Durable proof that an operation committed. [generation] is the history it was
 * committed into and [revision] the revision it produced. [committedAt] is real audit
 * time, never simulated financial time.
 */
data class OperationReceipt(
    val operationId: OperationId,
    val kind: OperationKind,
    val generation: Long,
    val revision: Long,
    val committedAt: Instant
)
