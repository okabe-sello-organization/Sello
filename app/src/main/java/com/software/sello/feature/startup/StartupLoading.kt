package com.software.sello.feature.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.software.sello.designsystem.R
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Shown while the data opens at start. It is the bare desk the app is drawn on, with no
 * figure or guess about what will open, and says it is loading to accessibility services.
 */
@Composable
fun StartupLoading(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.sello_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SelloTheme.colors.desk)
            .testTag(STARTUP_LOADING_TAG)
            .semantics {
                contentDescription = loading
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
    )
}

const val STARTUP_LOADING_TAG = "startup-loading"
