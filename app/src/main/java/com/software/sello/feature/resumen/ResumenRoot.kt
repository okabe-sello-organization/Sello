package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ResumenRoot(
    factory: ViewModelProvider.Factory,
    onIncome: () -> Unit,
    onCategory: (String) -> Unit,
    onExpense: () -> Unit,
    padding: PaddingValues,
    onSchedules: () -> Unit,
    onAdjustLimit: (String) -> Unit,
    onDayByDay: () -> Unit,
    onYear: (Int) -> Unit,
    onOpenGuide: () -> Unit
) {
    val model: ResumenViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    ResumenScreen(
        state,
        model::onAction,
        onIncome,
        onCategory,
        onExpense,
        padding,
        onAdjustLimit,
        onSchedules,
        onDayByDay,
        onYear,
        onOpenGuide
    )
}

/** Tu año inside the shell's [padding]. */
@Composable
fun YearRoot(factory: ViewModelProvider.Factory, padding: PaddingValues) {
    val model: YearViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    YearScreen(state, model::onAction, padding)
}
