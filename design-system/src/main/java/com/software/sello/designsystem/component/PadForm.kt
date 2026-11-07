package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.software.sello.designsystem.theme.SelloTheme

/** What a [PadForm] shows: its first read, a first read that failed, or the form itself. */
sealed interface PadFormStage {
    data object Loading : PadFormStage

    /** Nothing was ever read: [title] and [message] say so, and Reintentar is [onRetry]. */
    data class Failed(val title: String, val message: String, val onRetry: () -> Unit) :
        PadFormStage

    data object Ready : PadFormStage
}

/**
 * A full-screen form for one value typed on the Sello pad, such as the dollar rate or Gastos
 * hormiga's line: [title] with [navigationIcon] (a close button), then, by [stage], a
 * skeleton slip, a failed read with Reintentar, or [EntryLayout] with the [amount] slip,
 * one [fact] under it, the [action] and the [keypad]. [modifier] is the scaffold's;
 * [loadingModifier] and
 * [failedModifier] are the skeleton's and the failed slip's.
 */
@Composable
fun PadForm(
    title: String,
    stage: PadFormStage,
    navigationIcon: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    fact: @Composable () -> Unit,
    action: @Composable () -> Unit,
    keypad: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    loadingModifier: Modifier = Modifier,
    failedModifier: Modifier = Modifier
) {
    SelloScaffold(title = title, modifier = modifier, navigationIcon = navigationIcon) { padding ->
        when (stage) {
            PadFormStage.Loading -> Column(
                Modifier.fillMaxSize().padding(padding).padding(SelloTheme.spacing.lg)
            ) {
                SlipSkeleton(loadingModifier)
            }

            is PadFormStage.Failed -> Column(
                Modifier.fillMaxSize().padding(padding).padding(SelloTheme.spacing.lg)
            ) {
                StatusSlip(
                    kind = StatusKind.LoadFailed(stage.title),
                    message = stage.message,
                    modifier = failedModifier,
                    action = StatusAction(StatusSlipDefaults.retryLabel, onClick = stage.onRetry)
                )
            }

            PadFormStage.Ready -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {},
                amount = amount,
                choices = fact,
                action = action,
                keypad = keypad
            )
        }
    }
}
