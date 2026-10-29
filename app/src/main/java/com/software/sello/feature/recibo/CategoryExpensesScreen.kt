package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.dateLabels

@Composable
fun CategoryExpensesScreen(
    state: CategoryExpensesState,
    query: String,
    order: String,
    onAction: (CategoryExpensesAction) -> Unit,
    onEdit: (String) -> Unit,
    padding: PaddingValues
) {
    val dates = dateLabels()
    val loadedLabel = (state as? CategoryExpensesState.Ready)?.let {
        if (it.rows.size.toLong() < it.totalCount) {
            stringResource(
                R.string.category_detail_listed,
                countText(it.rows.size),
                countText(it.totalCount)
            )
        } else {
            pluralStringResource(
                R.plurals.category_detail_expense_count,
                it.rows.size,
                countText(it.rows.size)
            )
        }
    }
    LazyColumn(
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = Modifier.testTag("category.expenses").semantics {
            if (loadedLabel != null) stateDescription = loadedLabel
        }
    ) {
        item(key = "filters") {
            Slip(Modifier.fillMaxWidth(), pinked = false) {
                FormField(
                    label = stringResource(R.string.category_expenses_search),
                    value = query,
                    onValueChange = {
                        onAction(CategoryExpensesAction.Search(it))
                    },
                    modifier = Modifier.testTag("category.expenses.search")
                )
                ChipFlow {
                    ExpenseOrder.entries.forEach { sort ->
                        val label = stringResource(
                            when (sort) {
                                ExpenseOrder.Date -> R.string.category_expenses_sort_date
                                ExpenseOrder.Amount -> R.string.category_expenses_sort_amount
                                ExpenseOrder.Name -> R.string.category_expenses_sort_name
                            }
                        )
                        SelloChip(
                            label,
                            selected = order == sort.name,
                            onClick = { onAction(CategoryExpensesAction.Sort(sort)) }
                        )
                    }
                }
            }
        }
        when (state) {
            CategoryExpensesState.Loading -> item { SlipSkeleton() }

            CategoryExpensesState.Failed -> item {
                ErrorSlip(stringResource(R.string.category_detail_list_failed), {
                    onAction(CategoryExpensesAction.Retry)
                })
            }

            is CategoryExpensesState.Ready -> {
                if (state.rows.isEmpty()) {
                    item {
                        EmptySlip(
                            stringResource(R.string.category_expenses_empty),
                            stringResource(R.string.category_expenses_empty_body)
                        )
                    }
                }
                state.problem?.let { problem ->
                    item {
                        ErrorSlip(stringResource(problem.text()), {
                            onAction(CategoryExpensesAction.Retry)
                        })
                    }
                }
                items(state.rows, key = { it.id }) { row ->
                    Slip(
                        Modifier.fillMaxWidth(),
                        pinked = false,
                        contentPadding = PaddingValues(horizontal = SelloTheme.spacing.md)
                    ) {
                        val delete =
                            LineAction(stringResource(R.string.category_detail_expense_delete)) {
                                onAction(CategoryExpensesAction.Delete(row.id))
                            }
                        ActionLine(
                            row.note ?: stringResource(R.string.category_detail_no_note),
                            onClick = {
                                onEdit(row.id)
                            },
                            caption = dates.day(row.date, state.today) + if (row.notCountedYet) {
                                ". " + stringResource(R.string.category_detail_not_counted)
                            } else {
                                ""
                            },
                            actions = listOf(
                                LineAction(stringResource(R.string.category_detail_expense_edit)) {
                                    onEdit(row.id)
                                },
                                delete
                            ),
                            swipeAway = delete,
                            modifier = Modifier.testTag(categoryExpenseTag(row.id))
                        ) {
                            MoneyText(row.amount)
                        }
                    }
                }
                item(key = "count") {
                    loadedLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SelloTheme.colors.inkSoft,
                            modifier = Modifier.testTag("category.expenses.count")
                        )
                    }
                }
                if (state.pageFailed) {
                    item(key = "page-error") {
                        ErrorSlip(stringResource(R.string.category_detail_more_failed), {
                            onAction(CategoryExpensesAction.Retry)
                        })
                    }
                }
                if (state.more) {
                    item(key = "more") {
                        SelloButton(
                            stringResource(R.string.category_detail_more),
                            {
                                onAction(CategoryExpensesAction.More)
                            },
                            kind = ButtonKind.Text,
                            loading = state.busy,
                            modifier = Modifier.testTag("category.expenses.more")
                        )
                    }
                }
            }
        }
    }
}
