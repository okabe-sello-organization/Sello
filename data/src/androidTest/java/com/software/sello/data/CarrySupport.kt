package com.software.sello.data

import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.SettledBalance
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryMonthChange
import com.software.sello.domain.policy.SettledBalanceChange
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection

fun operationIdOf(number: Int): OperationId = OperationId.of(uuid(number)).valueOrFail()

fun categoryIdOf(number: Int): CategoryId = CategoryId.of(uuid(number)).valueOrFail()

fun expenseIdOf(number: Int): ExpenseId = ExpenseId.of(uuid(number)).valueOrFail()

private fun CarryVerdict.text(): String = when (this) {
    is CarryVerdict.Closed -> verdict.name
    is CarryVerdict.InProgress -> verdict.name
}

/** One month of a chain as its month, `K`, `C`, `S`, `R` and verdict. */
fun CarryMonth.text(): String = "$month|K ${carryIn.minorUnits}|C ${capacity.minorUnits}|" +
    "S ${spent.minorUnits}|R ${remaining.minorUnits}|${verdict.text()}"

/** One listed change: the category's number, the month, `C`, `R` and verdict before → after. */
fun CarryMonthChange.text(): String = "${categoryId.value.takeLast(3)}|$month|" +
    "C ${before.capacity.minorUnits}→${after.capacity.minorUnits}|" +
    "R ${before.remaining.minorUnits}→${after.remaining.minorUnits}|" +
    "${before.verdict.text()}→${after.verdict.text()}"

fun SettledBalance.text(): String = "$chainStart|$endMonth|${balance.minorUnits}"

fun SettledBalanceChange.text(): String = "${categoryId.value.takeLast(3)}|${before.endMonth}|" +
    "${before.balance.minorUnits}→${after.balance.minorUnits}"

/** The refusal of an expense correction, with its list and what confirms it (CP20). */
fun ExpenseCommandOutcome.refusedCorrection(): ExpenseRejection.CorrectionUnconfirmed =
    (this as ExpenseCommandOutcome.Rejected).reason as ExpenseRejection.CorrectionUnconfirmed

/** The list an expense correction was refused with, to be confirmed (CP20). */
fun ExpenseCommandOutcome.listedCorrection(): CarryCorrection = refusedCorrection().correction

/** The carry chains as stored, one row per chain. */
fun TestLedger.chainRows(): List<String> =
    rows("carry_chain", "category_id", "start_month", "end_month")

/**
 * Real commands that build the histories of the carryover examples
 * (docs/testing/signed-carryover-examples.md). Each expense is recorded on its own day,
 * in the month it belongs to, as a person would have; numbers follow [TestLedger]'s.
 */
class CarryHistories(private val ledger: TestLedger) {
    /** Creates [name] on [date] with [pesos] as its limit; returns its number. */
    suspend fun category(name: String, pesos: Long, date: String): Int {
        ledger.goTo(date)
        val number = ledger.nextRecord
        val outcome = ledger.categories.submit(ledger.createCategory(name, finite(pesos)))
        check(outcome is CommandOutcome.Committed) { "$name: $outcome" }
        return number
    }

    /** Turns carry on for [category], read at [version], in the current month. */
    suspend fun enable(category: Int, version: Long) {
        val outcome = ledger.categories.submit(
            EnableCarry(ledger.operation(), 1, ledger.categoryId(category), version)
        )
        check(outcome is CommandOutcome.Committed) { "enable $category: $outcome" }
    }

    /** Records [pesos] in [category] on [date], which becomes the financial day; returns its number. */
    suspend fun spend(category: Int, date: String, pesos: Long): Int {
        ledger.goTo(date)
        val number = ledger.nextRecord
        val outcome = ledger.expenses.create(ledger.createExpense(category, date, pesos))
        check(outcome is ExpenseCommandOutcome.Committed) { "spend $pesos on $date: $outcome" }
        return number
    }

    /**
     * SC01: Comida (100), base 100.000, on from ene 2027; spent ene 20.000 (101), feb
     * 70.000 (102), mar 250.000 (103). The financial day is then 5 abr 2027. Comida is at
     * version 2 and the revision is 5.
     */
    suspend fun sc01() {
        val comida = category("Comida", 100_000, "2027-01-05")
        enable(comida, version = 1)
        spend(comida, "2027-01-10", 20_000)
        spend(comida, "2027-02-10", 70_000)
        spend(comida, "2027-03-10", 250_000)
        ledger.goTo("2027-04-05")
    }
}
