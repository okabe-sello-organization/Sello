package com.software.sello.feature.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SelloSheet
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons

/*
 * Gastos frecuentes (SELLO-199), as the approved mock draws them: ⚡ Frecuentes at the end
 * of the note's line, the sheet it opens and the suggestions under the note being written.
 * Each only fills the form; Anotar gasto saves it.
 */

/**
 * ⚡ Frecuentes: a small brand pill inside the note's line, its own button with its own
 * spoken name and a touch target of at least 48dp.
 */
@Composable
internal fun FrequentButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = SelloTheme.colors
    val description = stringResource(R.string.expense_frequent_open)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(end = SelloTheme.spacing.sm)
            .minimumInteractiveComponentSize()
            .height(FrequentPillHeight)
            .testTag(ENTRY_FREQUENT_TAG)
            .background(colors.brandSoft, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 10.dp, end = SelloTheme.spacing.md)
    ) {
        Icon(
            SelloIcon.Bolt.painter(),
            null,
            tint = colors.onBrandSoft,
            modifier = Modifier.size(18.dp)
        )
        Text(
            stringResource(R.string.expense_frequent),
            color = colors.onBrandSoft,
            fontWeight = SelloTheme.type.weight.emphasis,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

/**
 * Lo que más anotas: each frequent expense with its note, category, how many times it was
 * recorded and its usual amount. A tap fills the form and the sheet closes.
 */
@Composable
internal fun FrequentSheet(state: ExpenseEntryState, onAction: (ExpenseEntryAction) -> Unit) {
    SelloSheet(
        title = stringResource(R.string.expense_frequent_title),
        onDismiss = { onAction(ExpenseEntryAction.CloseFrequent) },
        modifier = Modifier.testTag(ENTRY_FREQUENT_SHEET_TAG)
    ) {
        Text(
            stringResource(R.string.expense_frequent_lede),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = SelloTheme.spacing.sm)
        )
        state.frequent.forEachIndexed { index, frequent ->
            ScheduleRow(
                name = frequent.note,
                caption = pluralStringResource(
                    R.plurals.expense_frequent_caption,
                    frequent.count,
                    frequent.categoryName,
                    frequent.count
                ),
                amount = frequent.usual,
                icon = CategoryIcons.iconFor(frequent.iconKey),
                onClick = {
                    onAction(ExpenseEntryAction.FrequentPicked(frequent.categoryId, frequent.key))
                },
                spoken = pluralStringResource(
                    R.plurals.expense_frequent_spoken,
                    frequent.count,
                    frequent.note,
                    frequent.categoryName,
                    frequent.count,
                    frequent.usual.spoken
                ),
                modifier = Modifier.testTag(ENTRY_FREQUENT_ROW_TAG),
                last = index == state.frequent.lastIndex
            )
        }
    }
}

/**
 * Under the note being written: past notes that start with what is typed, each with its
 * usual amount, read with its category. Picking one fills the note, the category and the
 * amount, and the composer closes.
 */
@Composable
internal fun NoteSuggestions(
    suggestions: List<EntryFrequent>,
    onAction: (ExpenseEntryAction) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .testTag(ENTRY_NOTE_SUGGESTIONS_TAG)
    ) {
        suggestions.forEach { suggestion ->
            val spoken = stringResource(
                R.string.expense_note_suggestion_spoken,
                suggestion.note,
                suggestion.categoryName,
                suggestion.usual.spoken
            )
            FieldPill(
                text = stringResource(
                    R.string.expense_note_suggestion,
                    suggestion.note,
                    suggestion.usual.digits
                ),
                icon = CategoryIcons.iconFor(suggestion.iconKey),
                onClick = {
                    onAction(
                        ExpenseEntryAction.FrequentPicked(suggestion.categoryId, suggestion.key)
                    )
                },
                modifier = Modifier.semantics { contentDescription = spoken }
            )
        }
    }
}

private val FrequentPillHeight = 32.dp

const val ENTRY_FREQUENT_TAG = "expense-entry:frequent"
const val ENTRY_FREQUENT_SHEET_TAG = "expense-entry:frequent-sheet"
const val ENTRY_FREQUENT_ROW_TAG = "expense-entry:frequent-row"
const val ENTRY_NOTE_SUGGESTIONS_TAG = "expense-entry:note-suggestions"
