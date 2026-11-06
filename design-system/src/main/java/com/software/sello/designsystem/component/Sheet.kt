package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Sello's bottom sheet, over the screen it belongs to, such as how a payment repeats, an
 * icon to choose or a category's month limit: a scrim that closes it when tapped, and
 * paper docked at the bottom with [title] as its heading and [content] under it, scrolling
 * when it is taller than the screen. It keeps clear of the system bars. Back is the
 * screen's to handle, as is what lies behind.
 */
@Composable
fun SelloSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SelloTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = SelloTheme.alpha.scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .clearAndSetSemantics {}
        )
        Surface(
            color = colors.paper,
            shape = SelloTheme.shapes.sheet,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = SheetTopGap)
                .semantics { paneTitle = title }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(
                        start = SelloTheme.spacing.md,
                        end = SelloTheme.spacing.md,
                        top = SelloTheme.spacing.md,
                        bottom = 10.dp
                    )
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp, bottom = 10.dp)
                        .semantics { heading() }
                )
                content()
            }
        }
    }
}

private val SheetTopGap = 24.dp
