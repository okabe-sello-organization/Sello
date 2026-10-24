package com.software.sello.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A recorded expense. [date] is the effective day in the financial zone. [sequence]
 * orders records that share a day and never changes once assigned. [createdAt] is the
 * real moment it was first added and is kept through edits; [version] starts at 1.
 * [origin] is present only when a schedule recorded it, and edits never change it.
 */
data class Expense(
    val id: ExpenseId,
    val categoryId: CategoryId,
    val date: LocalDate,
    val sequence: Long,
    val amount: TransactionAmount,
    val note: Note?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val origin: EntryOrigin? = null
)

/**
 * A declared inflow. Ordering, audit, version and [origin] mean the same as on [Expense].
 */
data class Income(
    val id: IncomeId,
    val date: LocalDate,
    val sequence: Long,
    val amount: TransactionAmount,
    val source: IncomeSource,
    val note: Note?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val origin: EntryOrigin? = null
)
