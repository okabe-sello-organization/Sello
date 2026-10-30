package com.software.sello.navigation

import com.software.sello.feature.expense.ExpenseEntryViewModel
import kotlinx.serialization.Serializable

/*
 * Routes are identifiers only. A route never carries an amount, a record or a result:
 * a screen that needs data gets an identifier and reads the data itself.
 */

/** The start destination and the place Back returns to before leaving the app. */
@Serializable
data object ReciboRoute

@Serializable
data object ResumenRoute

/** Goals: not tied to a month, so the month bar does not appear there. */
@Serializable
data object MetasRoute

/** Investments: not tied to a month either. */
@Serializable
data object InversionesRoute

/** One investment. The screen reads it itself. */
@Serializable
data class InvestmentRoute(val investmentId: String)

/** Nueva inversión, or with [investmentId] Editar inversión. */
@Serializable
data class InvestmentEditorRoute(val investmentId: String? = null)

/**
 * Anotar movimiento: [investmentId] when chosen already, [kind] one of contribution,
 * valuation, withdrawal or dividend; with [movementId] the correction of that movement.
 */
@Serializable
data class InvestmentMovementRoute(
    val investmentId: String? = null,
    val kind: String? = null,
    val movementId: String? = null
)

/** Dólar: the current USD rate and the earlier ones, from Configuración or Inversiones. */
@Serializable
data object UsdRateRoute

/** Cambiar la tasa: a new dated USD rate. */
@Serializable
data object UsdRateEntryRoute

/** One goal's detail. The screen reads the goal itself. */
@Serializable
data class GoalRoute(val goalId: String)

/** Nueva meta, or with [goalId] Editar meta. The form reads the goal itself. */
@Serializable
data class GoalEditorRoute(val goalId: String? = null)

/**
 * Aportar, or with [withdrawal] Retirar, to [goalId] when it is chosen already; with
 * [movementId] the correction of that recorded movement. The form reads both itself.
 */
@Serializable
data class GoalMovementRoute(
    val goalId: String? = null,
    val withdrawal: Boolean = false,
    val movementId: String? = null
)

/**
 * Aportar varios: several aportes and retiros of [goalId] on one receipt. The screen reads
 * the goal itself.
 */
@Serializable
data class GoalBulkRoute(val goalId: String)

/** Nonfinancial controls reachable from Recibo's settings action. */
@Serializable
data object SettingsRoute

/** Making a backup file, reached from Configuración. */
@Serializable
data object BackupRoute

/** Borrar todos los datos, reached from Configuración. */
@Serializable
data object ResetRoute

@Serializable
data class ResumenCategoryRoute(val categoryId: String)

@Serializable
data class CategoryExpensesRoute(val categoryId: String)

@Serializable
data object IncomeHistoryRoute

/** The entry form for one income; with [repeat] it opens with Se repite's sheet. */
@Serializable
data class IncomeEntryRoute(val repeat: Boolean = false)

/** Pagos e ingresos fijos, on Ingresos when [income]. */
@Serializable
data class SchedulesRoute(val income: Boolean = false)

/** Lo que Sello anotó: what catch-up recorded since the notice was dismissed. */
@Serializable
data object CatchUpRoute

/**
 * Ajustar presupuesto for [month], opened from Recibo's answer slip. The month is the one
 * Recibo showed, as `YYYY-MM`; the screen keeps it even if the month changes meanwhile.
 */
@Serializable
data class AdjustBudgetRoute(val month: String)

/** The payments reserved this month, opened from Recibo's footnote. */
@Serializable
data object UpcomingPaymentsRoute

/** Changing one schedule. The screen reads the schedule itself. */
@Serializable
data class ScheduleRoute(val scheduleId: String)

@Serializable
data class IncomeEditRoute(val incomeId: String)

/**
 * The new-category form. [forEntry] says it was opened because recording an expense
 * needs a category first, so the form explains that. [limit] is a limit in pesos to start
 * the form with, such as the income not yet assigned; the form ignores it unless it is a
 * limit it can hold.
 */
@Serializable
data class CategoryEditorRoute(val forEntry: Boolean = false, val limit: String? = null)

/**
 * The entry form for one expense. The arguments are hints for the draft that came
 * from outside (a link, a category just created, or + on Pagos e ingresos fijos, which
 * asks for [repeat]): the form checks them like any other input and may ignore them.
 * None is a record or a result.
 */
@Serializable
data class ExpenseEntryRoute(
    val categoryId: String? = null,
    val amount: String? = null,
    val repeat: Boolean = false
)

/**
 * The same form, correcting the recorded expense with this identifier. The form reads
 * the expense itself; if there is none with that identifier it says so.
 */
@Serializable
data class ExpenseEditRoute(val expenseId: String)

/**
 * The same form on an existing category. [month] is the month it was opened from, as
 * `yyyy-MM`: the month whose limit the form shows and changes. Without it, or with
 * one that has not started, that is the current month.
 */
@Serializable
data class CategoryEditRoute(val categoryId: String, val month: String? = null)

/** Where the editor leaves the new category's identifier for the screen that opened it. */
const val CREATED_CATEGORY_RESULT = "result.createdCategoryId"

/**
 * The tabs that exist. A tab is added here when its screen works; there is no
 * disabled or placeholder tab. With a single tab the tab bar is not shown.
 */
enum class ShellTab(val id: String) {
    Recibo("recibo"),
    Resumen("resumen"),
    Metas("metas"),
    Inversiones("inversiones")
}
