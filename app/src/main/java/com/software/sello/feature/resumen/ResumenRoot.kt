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
    onAdjustLimit: (String) -> Unit
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
        onSchedules
    )
}
