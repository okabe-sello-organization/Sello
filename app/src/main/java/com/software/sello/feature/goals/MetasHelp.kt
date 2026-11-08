package com.software.sello.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.HelpEntry
import com.software.sello.designsystem.component.HelpSheet

/**
 * What "Guardado en metas" means (SELLO-209), as a sheet over the whole of Metas. Back and
 * the scrim call [onClose]; "Ver más en la Guía de Sello" calls [onGuide], which opens
 * the Guía's Metas page.
 */
@Composable
fun MetasHelpSheet(onClose: () -> Unit, onGuide: () -> Unit) {
    BackHandler(onBack = onClose)
    HelpSheet(
        title = stringResource(R.string.metas_help_title),
        entries = listOf(
            HelpEntry(
                stringResource(R.string.metas_help_saved),
                stringResource(R.string.metas_help_saved_body)
            ),
            HelpEntry(
                stringResource(R.string.metas_help_month),
                stringResource(R.string.metas_help_month_body)
            ),
            HelpEntry(
                stringResource(R.string.metas_help_apart),
                stringResource(R.string.metas_help_apart_body)
            )
        ),
        onDismiss = onClose,
        modifier = Modifier.testTag(METAS_HELP_TAG),
        example = stringResource(R.string.metas_help_example),
        onMore = onGuide
    )
}

const val METAS_HELP_TAG = "metas.help"
const val METAS_SAVED_HELP_TAG = "metas.help.saved"
