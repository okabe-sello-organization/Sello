package com.software.sello.presentation.read

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Says that what follows is older data, as last read, in [message], and offers to read
 * again. Shown above figures a refresh could not update (ARCHITECTURE §6).
 */
@Composable
fun StaleSlip(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Slip(modifier = modifier.fillMaxWidth(), pinked = false) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = SelloTheme.colors.semantic.loss,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        SelloButton(
            text = stringResource(R.string.recibo_stale_retry),
            onClick = onRetry,
            kind = ButtonKind.Text
        )
    }
}
