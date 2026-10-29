package com.software.sello

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.navigation.MonthSession
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/**
 * The running app's own commands, for a test that needs more history than is worth
 * tapping in. Everything goes through the same workflows the screens use, so the app
 * hears of each change as it would of its own; nothing is written around them.
 */
object TestCommands {
    private var graph: Koin? = null

    /** The graph the commands below run on, and whose database [TestData] reads and writes. */
    val koin: Koin get() = graph ?: GlobalContext.get()

    /**
     * Runs the commands below on [graph], a test's own graph, instead of the installed
     * app's. A test that does this ends it again with [useInstalledApp].
     */
    fun use(graph: Koin) {
        this.graph = graph
    }

    fun useInstalledApp() {
        graph = null
    }

    val today: LocalDate get() = koin.get<FinancialClock>().today.value.date

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun operation(): OperationId = OperationId.of(UUID.randomUUID().toString()).value()

    private fun finite(pesos: Long): BudgetLimit = BudgetLimit.finite(Money.cop(pesos)).value()

    /**
     * The month selection lives as long as the process, which every test in a run
     * shares. A test that depends on the month, or changed it, puts it back.
     */
    fun showCurrentMonth() {
        koin.get<MonthSession>().select(YearMonth.from(today))
    }

    /** The identifier of the category called [name]. */
    fun categoryId(name: String): String =
        TestData.rows("SELECT id FROM category WHERE name = '$name'").single()

    /** [limit] in pesos, or null for no limit. */
    fun createCategory(name: String, limit: Long?) = runBlocking {
        val outcome = koin.get<CategoryCommands>().submit(
            CreateCategory(
                operation(),
                1,
                CategoryName.of(name).value(),
                IconKey.of("home").value(),
                limit?.let(::finite) ?: BudgetLimit.Unlimited
            )
        )
        assertTrue("$outcome", outcome is CategoryCommandOutcome.Committed)
    }

    /** A peso investment named [name], its first aporte [pesos] today, valued the same. */
    fun createInvestment(name: String, pesos: Long) = runBlocking {
        val amount = InvestmentAmount.of(Money.cop(pesos)).value()
        val outcome = koin.get<InvestmentCommands>().submit(
            CreateInvestment(
                operation(),
                generation(),
                InvestmentName.of(name).value(),
                null,
                IconKey.of("account_balance").value(),
                Currency.COP,
                amount,
                InvestmentAmount.ofValue(Money.cop(pesos)).value(),
                today,
                null
            )
        )
        assertTrue("$outcome", outcome is InvestmentCommandOutcome.Committed)
    }

    fun setMonthLimit(category: String, month: YearMonth, pesos: Long) = runBlocking {
        val id = categoryId(category)
        val outcome = koin.get<CategoryCommands>().submit(
            SetMonthLimit(
                operation(),
                1,
                CategoryId.of(id).value(),
                TestData.long("SELECT version FROM category WHERE id = '$id'"),
                month,
                finite(pesos)
            )
        )
        assertTrue("$outcome", outcome is CategoryCommandOutcome.Committed)
    }

    fun createExpense(category: String, date: LocalDate, pesos: Long, note: String? = null) =
        runBlocking {
            val outcome = koin.get<ExpenseCommands>().create(
                CreateExpense(
                    operation(),
                    1,
                    CategoryId.of(categoryId(category)).value(),
                    TransactionAmount.of(Money.cop(pesos)).value(),
                    date,
                    Note.of(note).value()
                )
            )
            assertTrue("$outcome", outcome is ExpenseCommandOutcome.Committed)
        }

    private fun goalVersion(id: String): Long =
        TestData.long("SELECT version FROM goal WHERE id = '$id'")

    /** A goal called [name], with its target; returns its identifier. */
    fun createGoal(
        name: String,
        target: Long,
        icon: String = "flight",
        targetMonth: YearMonth? = null
    ): String = runBlocking {
        val outcome = koin.get<GoalCommands>().submit(
            CreateGoal(
                operation(),
                generation(),
                GoalName.of(name).value(),
                IconKey.of(icon).value(),
                TransactionAmount.of(Money.cop(target)).value(),
                targetMonth
            )
        )
        assertTrue("$outcome", outcome is CommandOutcome.Committed)
        (outcome as CommandOutcome.Committed).value.goalId.value
    }

    /** An aporte, or with [withdrawal] a retiro, of [pesos] dated [date]. */
    fun recordGoalMovement(
        goal: String,
        pesos: Long,
        date: LocalDate = today,
        withdrawal: Boolean = false
    ) = runBlocking {
        val outcome = koin.get<GoalCommands>().submit(
            RecordGoalMovement(
                operation(),
                generation(),
                GoalId.of(goal).value(),
                goalVersion(goal),
                if (withdrawal) GoalMovementKind.Withdrawal else GoalMovementKind.Contribution,
                TransactionAmount.of(Money.cop(pesos)).value(),
                date,
                null
            )
        )
        assertTrue("$outcome", outcome is CommandOutcome.Committed)
    }

    /** Marcar como lograda, through the real command. */
    fun completeGoal(goal: String) = runBlocking {
        val outcome = koin.get<GoalCommands>().submit(
            CompleteGoal(
                operation(),
                generation(),
                GoalId.of(goal).value(),
                goalVersion(goal),
                1.seconds
            )
        )
        assertTrue("$outcome", outcome is CommandOutcome.Committed)
    }

    /** Chooses [goal] for Recibo, as "Mostrar en Recibo" does, through the real command. */
    fun featureGoal(goal: String) = runBlocking {
        val outcome = koin.get<GoalCommands>().submit(
            FeatureGoal(operation(), generation(), GoalId.of(goal).value())
        )
        assertTrue("$outcome", outcome is CommandOutcome.Committed)
    }

    private fun generation(): Long = TestData.long("SELECT generation FROM profile")

    /** A salary income of [pesos] on [date]. */
    fun createIncome(date: LocalDate, pesos: Long) = runBlocking {
        val outcome = koin.get<IncomeCommands>().create(
            CreateIncome(
                operation(),
                1,
                TransactionAmount.of(Money.cop(pesos)).value(),
                IncomeSource.Salary,
                date,
                Note.of(null).value()
            )
        )
        assertTrue("$outcome", outcome is CommandOutcome.Committed)
    }
}
