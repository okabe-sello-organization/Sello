package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** One choice in a [MoreMenu]. A [loss] choice, such as deleting, is drawn in red. */
data class MoreMenuItem(
    val label: String,
    val icon: SelloIcon,
    val onClick: () -> Unit,
    val loss: Boolean = false,
    val tag: String? = null
)

/**
 * The top bar's ⋮: what a screen rarely needs, such as deleting it, kept out of the
 * way of what it is for. Choosing an item closes the menu and then runs it.
 */
@Composable
fun MoreMenu(items: List<MoreMenuItem>, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(MORE_MENU_TAG)) {
            Icon(SelloIcon.MoreVert.painter(), stringResource(R.string.sello_more_options))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = SelloTheme.colors.paper
        ) {
            items.forEach { item ->
                val tint = if (item.loss) SelloTheme.colors.semantic.loss else SelloTheme.colors.ink
                DropdownMenuItem(
                    text = {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                    },
                    leadingIcon = { Icon(item.icon.painter(), null, tint = tint) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                    modifier = item.tag?.let { Modifier.testTag(it) } ?: Modifier
                )
            }
        }
    }
}

const val MORE_MENU_TAG = "sello.more"
