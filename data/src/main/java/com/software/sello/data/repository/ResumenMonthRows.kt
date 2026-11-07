package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.ResumenMonthInput
import com.software.sello.domain.port.MonthBudget
import java.time.LocalDate

/**
 * One month as Resumen and Tu año read it: its categories with their budget state, its
 * expenses, and the carry rows of each category a chain covers then (ADR 0020 CP16).
 */
internal class ResumenMonthRows(val input: ResumenMonthInput, val carry: List<CarryRows>)

/**
 * The month of [budget], with each month's carry from the chain covering it alone, read in
 * the caller's transaction (SELLO-038 consumer boundary, as RoomMonthlySnapshots).
 */
internal suspend fun SelloDatabase.resumenMonthRows(
    budget: MonthBudget,
    chains: Map<CategoryId, List<CarryChain>>
): Outcome<ResumenMonthRows, StorageFailure> {
    val period = budget.month
    val expenses = when (val read = monthExpenses(period)) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> return read
    }
    val covered = ArrayList<CarryRows>()
    for ((category, of) in chains) {
        when (val read = carryInRows(category, of, period)) {
            is Outcome.Success -> read.value?.let { covered += it }
            is Outcome.Failure -> return read
        }
    }
    return Outcome.Success(
        ResumenMonthRows(
            ResumenMonthInput(
                period,
                budget.categories.map { CategoryInMonth(it.category, it.state) },
                expenses
            ),
            covered
        )
    )
}

/** Each month's input with its carry worked out as of [today]; the first failure stops it. */
internal fun List<ResumenMonthRows>.inputs(
    today: LocalDate
): Outcome<List<ResumenMonthInput>, SnapshotFailure> {
    val months = ArrayList<ResumenMonthInput>(size)
    for (rows in this) {
        val carryover = ArrayList<CategoryCarryInput>(rows.carry.size)
        for (category in rows.carry) {
            when (val read = category.snapshotInput(today)) {
                is Outcome.Success -> carryover += read.value
                is Outcome.Failure -> return read
            }
        }
        months += rows.input.copy(carryover = carryover)
    }
    return Outcome.Success(months)
}
