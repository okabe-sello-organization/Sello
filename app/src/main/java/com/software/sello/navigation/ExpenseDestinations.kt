package com.software.sello.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.software.sello.feature.bulk.BulkEntryRoot
import com.software.sello.feature.expense.ExpenseEntryRoot
import com.software.sello.feature.expense.ExpenseSeveral
import com.software.sello.feature.income.IncomeFormRoot
import com.software.sello.presentation.corrections.CorrectionAnnouncementState
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/** Recording an expense and correcting one. */
internal fun NavGraphBuilder.expenseDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    announcementsState: State<CorrectionAnnouncementState>,
    announcements: CorrectionAnnouncementsViewModel
) {
    composable<ExpenseEntryRoute> { entry ->
        val announcementState by announcementsState
        EntryDestination(
            viewModels,
            startsWithIncome = false,
            result = entry.savedStateHandle,
            onCreateCategory = { navigation.navigate(CategoryEditorRoute()) },
            announcements = announcementState,
            onDismissPastExpense = if (announcementState.failure == null) {
                announcements::dismissPastExpense
            } else {
                announcements::retry
            },
            announcementFailed = announcementState.failure != null,
            onSeveral = { several ->
                navigation.navigate(several.route()) {
                    popUpTo<ExpenseEntryRoute> { inclusive = true }
                }
            }
        ) {
            val onEntry =
                navigation.currentBackStackEntry?.destination?.hasRoute<ExpenseEntryRoute>()
            if (onEntry == true) navigation.popBackStack()
        }
    }
    composable<ExpenseBulkRoute> {
        BulkEntryRoot(viewModels) {
            val onBulk = navigation.currentBackStackEntry?.destination?.hasRoute<ExpenseBulkRoute>()
            if (onBulk == true) navigation.popBackStack()
        }
    }
    // The same form: the route's identifier makes it correct that expense.
    composable<ExpenseEditRoute> {
        val announcementState by announcementsState
        ExpenseEntryRoot(
            viewModels = viewModels,
            announcements = announcementState,
            onDismissPastExpense = if (announcementState.failure == null) {
                announcements::dismissPastExpense
            } else {
                announcements::retry
            },
            announcementFailed = announcementState.failure != null
        ) {
            val onEdit =
                navigation.currentBackStackEntry?.destination?.hasRoute<ExpenseEditRoute>()
            if (onEdit == true) navigation.popBackStack()
        }
    }
}

@Composable
internal fun EntryDestination(
    viewModels: ViewModelProvider.Factory,
    startsWithIncome: Boolean,
    onCreateCategory: () -> Unit,
    announcements: CorrectionAnnouncementState,
    onDismissPastExpense: () -> Unit,
    announcementFailed: Boolean,
    result: SavedStateHandle? = null,
    onSeveral: ((ExpenseSeveral) -> Unit)? = null,
    onClosed: () -> Unit
) {
    var income by rememberSaveable { mutableStateOf(startsWithIncome) }
    // What the category editor, opened from Nueva, left for this entry.
    val created by (
        result?.getStateFlow<String?>(CREATED_CATEGORY_RESULT, null) ?: remember {
            MutableStateFlow<String?>(null)
        }
        ).collectAsStateWithLifecycle()
    if (income) {
        IncomeFormRoot(viewModels, onClosed, onExpense = { income = false })
    } else {
        ExpenseEntryRoot(
            viewModels,
            onIncome = { income = true },
            onCreateCategory = onCreateCategory,
            announcements = announcements,
            onDismissPastExpense = onDismissPastExpense,
            announcementFailed = announcementFailed,
            createdCategoryId = created,
            onCreatedCategoryTaken = { result?.set(CREATED_CATEGORY_RESULT, null) },
            onSeveral = onSeveral,
            onClosed = onClosed
        )
    }
}

/** Anotar varios with what was typed in Anotar as its first line. */
internal fun ExpenseSeveral.route() = ExpenseBulkRoute(categoryId, amount, note, date.toString())
