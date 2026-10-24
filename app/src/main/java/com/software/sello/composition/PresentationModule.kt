package com.software.sello.composition

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.software.sello.data.backup.BackupBounds
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.feature.backup.BackupSession
import com.software.sello.feature.backup.BackupViewModel
import com.software.sello.feature.backup.RestoreSession
import com.software.sello.feature.backup.RestoreViewModel
import com.software.sello.feature.category.CategoryEditorViewModel
import com.software.sello.feature.category.CategoryManagementViewModel
import com.software.sello.feature.expense.ExpenseEntryViewModel
import com.software.sello.feature.goals.GoalDetailViewModel
import com.software.sello.feature.goals.GoalEditorViewModel
import com.software.sello.feature.goals.GoalMovementViewModel
import com.software.sello.feature.goals.MetasViewModel
import com.software.sello.feature.income.IncomeFormViewModel
import com.software.sello.feature.income.IncomeHistoryViewModel
import com.software.sello.feature.recibo.CategoryDetailViewModel
import com.software.sello.feature.recibo.CategoryExpensesViewModel
import com.software.sello.feature.recibo.ReciboViewModel
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
import com.software.sello.presentation.corrections.CorrectionsViewModel
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.ResourceMoneyLabels
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module
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
        ExpenseCorrections(get(), get(), get(), get(), get(), scope)
    }
    // A save runs for as long as the process, not the screen that started it.
    single { BackupSession(get(), get(), get(), get(), get<CoroutineScope>()) }
    // Created with the graph, so a restore left with preferences pending resumes at start.
    single(createdAtStart = true) {
        RestoreSession(get(), get(), get<CoroutineScope>(), BackupBounds().maxBytes)
    }
    // Likewise an erase committed by an earlier process is finished at start.
    single(createdAtStart = true) { ResetSession(get(), get(), get<CoroutineScope>()) }
}

/**
 * How every view model is built. Screens ask for a view model by type and never see
 * the graph; each initializer names its collaborators here, so a missing one fails
 * when the screen opens instead of silently defaulting.
 */
fun viewModels(koin: Koin): ViewModelProvider.Factory = viewModelFactory {
    initializer { AppearanceViewModel(koin.get()) }
    initializer { SettingsViewModel(koin.get(), koin.get(), koin.get(), koin.get()) }
    initializer {
        BackupViewModel(createSavedStateHandle(), koin.get(), koin.get(), koin.get())
    }
    initializer { RestoreViewModel(koin.get(), koin.get(), koin.get()) }
    initializer { ResetViewModel(createSavedStateHandle(), koin.get(), koin.get()) }
    initializer {
        ResumenViewModel(createSavedStateHandle(), koin.get(), koin.get(), koin.get(), koin.get())
    }
    initializer {
        IncomeHistoryViewModel(koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
    }
    initializer {
        IncomeFormViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer { ShellViewModel(createSavedStateHandle(), koin.get(), koin.get()) }
    initializer { CorrectionsViewModel(koin.get()) }
    initializer { CorrectionAnnouncementsViewModel(koin.get()) }
    initializer {
        CategoryManagementViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer { ReciboViewModel(koin.get(), koin.get(), koin.get(), koin.get()) }
    initializer {
        MetasViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        GoalEditorViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        GoalMovementViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        GoalDetailViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        CategoryDetailViewModel(
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        CategoryExpensesViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        ExpenseEntryViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
    initializer {
        CategoryEditorViewModel(
            createSavedStateHandle(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get(),
            koin.get()
        )
    }
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
