package com.software.sello.composition

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.software.sello.data.backup.BackupBounds
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.domain.usecase.CatchUpSchedules
import com.software.sello.feature.backup.BackupSession
import com.software.sello.feature.backup.BackupViewModel
import com.software.sello.feature.backup.ExportSession
import com.software.sello.feature.backup.ExportViewModel
import com.software.sello.feature.backup.RestoreSession
import com.software.sello.feature.backup.RestoreViewModel
import com.software.sello.feature.category.CategoryEditorViewModel
import com.software.sello.feature.category.CategoryManagementViewModel
import com.software.sello.feature.expense.ExpenseEntryViewModel
import com.software.sello.feature.goals.GoalDetailViewModel
import com.software.sello.feature.goals.GoalEditorViewModel
import com.software.sello.feature.goals.GoalMovementViewModel
import com.software.sello.feature.goals.MetasViewModel
import com.software.sello.feature.goals.ReciboGoalViewModel
import com.software.sello.feature.income.IncomeFormViewModel
import com.software.sello.feature.income.IncomeHistoryViewModel
import com.software.sello.feature.investments.InversionesViewModel
import com.software.sello.feature.investments.InvestmentDetailViewModel
import com.software.sello.feature.investments.InvestmentEditorViewModel
import com.software.sello.feature.investments.InvestmentMovementViewModel
import com.software.sello.feature.investments.UsdRateEntryViewModel
import com.software.sello.feature.investments.UsdRateViewModel
import com.software.sello.feature.recibo.AdjustBudgetViewModel
import com.software.sello.feature.recibo.CategoryDetailViewModel
import com.software.sello.feature.recibo.CategoryExpensesViewModel
import com.software.sello.feature.recibo.LimitSheetViewModel
import com.software.sello.feature.recibo.ReciboViewModel
import com.software.sello.feature.recibo.UpcomingPaymentsViewModel
import com.software.sello.feature.recurrence.CatchUpNoticeViewModel
import com.software.sello.feature.recurrence.CatchUpRunner
import com.software.sello.feature.recurrence.ScheduleEditorViewModel
import com.software.sello.feature.recurrence.SchedulesViewModel
import com.software.sello.feature.reset.ResetSession
import com.software.sello.feature.reset.ResetViewModel
import com.software.sello.feature.resumen.ResumenViewModel
import com.software.sello.feature.settings.AppearanceViewModel
import com.software.sello.feature.settings.SettingsViewModel
import com.software.sello.feature.startup.StartupFailureViewModel
import com.software.sello.feature.startup.StartupRecovery
import com.software.sello.navigation.MonthSession
import com.software.sello.navigation.ShellViewModel
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.CorrectionsViewModel
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.ResourceMoneyLabels
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** What screens share: the one month session and the money formatter. */
fun presentationModule(): Module = module {
    single { MonthSession(get(), get()) }
    single { MoneyFormatter(ResourceMoneyLabels(get<Context>().resources)) }
    // On the main thread, like the screens that call it, and for as long as the process.
    single {
        val scope = CoroutineScope(
            get<CoroutineScope>().coroutineContext + get<DispatcherProvider>().main
        )
        Corrections(get(), get(), get(), get(), get(), get(), scope, get(), get())
    }
    // A save runs for as long as the process, not the screen that started it.
    single { BackupSession(get(), get(), get(), get(), get<CoroutineScope>()) }
    single { ExportSession(get(), get(), get(), get<CoroutineScope>()) }
    // Created with the graph, so a restore left with preferences pending resumes at start.
    single(createdAtStart = true) {
        RestoreSession(get(), get(), get<CoroutineScope>(), BackupBounds().maxBytes)
    }
    // Likewise an erase committed by an earlier process is finished at start.
    single(createdAtStart = true) { ResetSession(get(), get(), get<CoroutineScope>()) }
    // Recurring payments and incomes are recorded from the moment the graph exists.
    single { CatchUpSchedules(get(), get()) }
    single(createdAtStart = true) { CatchUpRunner(get(), get(), get(), get<CoroutineScope>()) }
    single<CatchUpNow> { get<CatchUpRunner>() }
    viewModels()
}

/**
 * How every view model is built. Screens ask for a view model by type and never see
 * the graph; each definition names its collaborators by constructor, so a missing one
 * fails when the screen opens instead of silently defaulting. A [SavedStateHandle]
 * parameter is the one of the entry that asked, passed as a Koin parameter.
 */
private fun Module.viewModels() {
    factoryOf(::AppearanceViewModel)
    factoryOf(::SettingsViewModel)
    factoryOf(::ExportViewModel)
    factoryOf(::BackupViewModel)
    factoryOf(::RestoreViewModel)
    factoryOf(::ResetViewModel)
    factoryOf(::ResumenViewModel)
    factoryOf(::IncomeHistoryViewModel)
    factoryOf(::IncomeFormViewModel)
    factoryOf(::ShellViewModel)
    factoryOf(::CorrectionsViewModel)
    factoryOf(::CorrectionAnnouncementsViewModel)
    factoryOf(::CategoryManagementViewModel)
    factoryOf(::ReciboViewModel)
    factoryOf(::ReciboGoalViewModel)
    factoryOf(::InversionesViewModel)
    factoryOf(::UsdRateViewModel)
    factoryOf(::InvestmentDetailViewModel)
    factoryOf(::InvestmentEditorViewModel)
    factoryOf(::InvestmentMovementViewModel)
    factoryOf(::UsdRateEntryViewModel)
    factoryOf(::MetasViewModel)
    factoryOf(::GoalEditorViewModel)
    factoryOf(::GoalMovementViewModel)
    factoryOf(::GoalDetailViewModel)
    factoryOf(::CategoryDetailViewModel)
    factoryOf(::CategoryExpensesViewModel)
    factoryOf(::ExpenseEntryViewModel)
    factoryOf(::CategoryEditorViewModel)
    factoryOf(::UpcomingPaymentsViewModel)
    factoryOf(::AdjustBudgetViewModel)
    factoryOf(::LimitSheetViewModel)
    factoryOf(::CatchUpNoticeViewModel)
    factoryOf(::SchedulesViewModel)
    factoryOf(::ScheduleEditorViewModel)
}

/** Whether [type] takes the saved state of the entry that asked for it. */
private fun takesSavedState(type: KClass<*>): Boolean =
    type.java.declaredConstructors.any { SavedStateHandle::class.java in it.parameterTypes }

/**
 * A factory for a screen's owner that builds whichever view model it asks for from
 * [koin]. The saved state is made only for the view models that take it.
 */
fun viewModels(koin: Koin): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
        koin.get(modelClass, null) {
            if (takesSavedState(modelClass)) {
                parametersOf(extras.createSavedStateHandle())
            } else {
                parametersOf()
            }
        } as T
}

/**
 * The one view model built without the graph: its screen is shown when the data could
 * not be opened, which is exactly when there is no graph.
 */
fun startupFailureViewModels(
    recovery: StartupRecovery,
    restart: AppRestart
): ViewModelProvider.Factory = viewModelFactory {
    initializer { StartupFailureViewModel(createSavedStateHandle(), recovery, restart) }
}
