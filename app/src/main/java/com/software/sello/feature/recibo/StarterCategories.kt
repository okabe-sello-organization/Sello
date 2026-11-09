package com.software.sello.feature.recibo

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Outcome
import com.software.sello.presentation.category.CategoryIcons

/**
 * A category a new Recibo offers to start from: a name and one of the category icons,
 * never an amount (E38: Sello does not tell anyone how much to spend).
 */
enum class StarterCategory(@StringRes val label: Int, val iconKey: String) {
    Comida(R.string.recibo_starter_food, "restaurant"),
    Transporte(R.string.recibo_starter_transport, "directions_bus"),
    Casa(R.string.recibo_starter_home, "home"),
    Ocio(R.string.recibo_starter_leisure, "theaters"),
    Ahorro(R.string.recibo_starter_savings, "savings")
}

/** The test tag of the starter chips' group on the empty Recibo. */
const val RECIBO_STARTERS_TAG = "recibo:starters"

/**
 * "O empieza con una de estas:" and a chip for each [StarterCategory] whose name no
 * category uses yet ([takenNames], compared as the category form compares names). A chip
 * opens the category form with its name and icon; nothing is saved from here. With every
 * name taken, nothing is shown.
 */
@Composable
internal fun StarterCategories(
    takenNames: Set<String>,
    onStart: (name: String, iconKey: String) -> Unit
) {
    val offered = StarterCategory.entries
        .map { it to stringResource(it.label) }
        .filterNot { (_, name) -> isTaken(name, takenNames) }
    if (offered.isEmpty()) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = Modifier.fillMaxWidth().testTag(RECIBO_STARTERS_TAG)
    ) {
        Text(
            text = stringResource(R.string.recibo_starters_lead),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft,
            textAlign = TextAlign.Center
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                StarterGap,
                Alignment.CenterHorizontally
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            offered.forEach { (starter, name) ->
                StarterChip(starter, name) { onStart(name, starter.iconKey) }
            }
        }
    }
}

/** Whether a category already has [name], by the rule the category form refuses it with. */
internal fun isTaken(name: String, takenNames: Set<String>): Boolean =
    (CategoryName.fromInput(name) as? Outcome.Success)?.value?.uniquenessKey in takenNames

/**
 * A starter as a button, drawn as an unchosen chip: a choice chip would be read as one
 * of a set to pick, and this one opens a form. TalkBack says "Empezar con Comida".
 */
@Composable
private fun StarterChip(starter: StarterCategory, name: String, onClick: () -> Unit) {
    val colors = SelloTheme.colors
    val spoken = stringResource(R.string.recibo_starter_description, name)
    // Sizes, border and gap are SelloChip's (design-system Chips.kt), so it looks the same.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(SelloTheme.shapes.control)
            .background(colors.paper)
            .border(1.5.dp, colors.rule, SelloTheme.shapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .padding(horizontal = SelloTheme.spacing.md, vertical = SelloTheme.spacing.xs)
    ) {
        Icon(
            painter = CategoryIcons.iconFor(starter.iconKey).painter(),
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(18.dp)
        )
        Text(text = name, style = SelloTheme.type.chipLabel, color = colors.ink, maxLines = 1)
    }
}

private val StarterGap = 6.dp
