package com.software.sello.feature.recibo

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.Footnote
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons
import kotlinx.coroutines.launch

/**
 * Recibo: what is left this month and where it is going. [selectedCategoryId] marks
 * the category whose detail is shown beside it, when there is room for both. [goal] is
 * drawn under the month's answer, or under the first-use card; it is read apart, so the
 * answer never waits on it.
 */
@Composable
fun ReciboScreen(
    state: ReciboState,
    onAction: (ReciboAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    selectedCategoryId: String? = null,
    goal: @Composable () -> Unit = {}
) {
    // The start is complete once the month was read: its figures, a first run or a failure.
    ReportDrawnWhen { state !is ReciboState.Loading }
    val scroll = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding)
    ) {
        when (state) {
            ReciboState.Loading -> Column(
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
                modifier = Modifier.testTag(RECIBO_LOADING_TAG)
            ) {
                SlipSkeleton()
                SlipSkeleton()
            }

            ReciboState.FirstRun -> {
                EmptySlip(
                    title = stringResource(R.string.recibo_first_run_title),
                    body = stringResource(R.string.recibo_first_run_body),
                    modifier = Modifier.testTag(RECIBO_FIRST_RUN_TAG),
                    actionLabel = stringResource(R.string.recibo_create_category),
                    onAction = { onAction(ReciboAction.CreateCategory) },
                    icon = SelloIcon.ReceiptLong
                )
                // A goal needs no category: Recibo shows it here too (rule 12).
                goal()
            }

            is ReciboState.Month -> {
                if (state.stale) {
                    StaleSlip({ onAction(ReciboAction.Retry) }, Modifier.testTag(RECIBO_STALE_TAG))
                }
                SummarySlip(
                    state.summary,
                    onAdjust = { onAction(ReciboAction.AdjustBudget) },
                    onReserved = { onAction(ReciboAction.OpenReserved) }
                )
                goal()
                CategoriesSlip(
                    state.categories,
                    selectedCategoryId,
                    onAction
                )
            }

            ReciboState.Failed -> ErrorSlip(
                message = stringResource(R.string.recibo_failed),
                onRetry = { onAction(ReciboAction.Retry) },
                modifier = Modifier.testTag(RECIBO_FAILED_TAG),
                title = stringResource(R.string.recibo_failed_title),
                icon = SelloIcon.Error
            )
        }
    }
}

/** Says that what follows is older data, and offers to read again. */
@Composable
internal fun StaleSlip(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Slip(modifier = modifier.fillMaxWidth(), pinked = false) {
        Text(
            text = stringResource(R.string.recibo_stale),
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

/**
 * The month's answer: the label and stamp, the signed remainder, then either what can be
 * spent per day or how the closed month ended. "Restante del presupuesto: menos 43.700
 * pesos. Excedido." The minus is a character on the screen and a word when read.
 */
@Composable
private fun SummarySlip(summary: ReciboSummary, onAdjust: () -> Unit, onReserved: () -> Unit) {
    val label = answerLabel(summary.label)
    val stamp = stampText(summary.stamp)
    AnswerSlip(
        label = label,
        amount = summary.amount,
        stamp = stamp,
        ink = stampInk(summary.stamp),
        modifier = Modifier.testTag(RECIBO_SUMMARY_TAG),
        description = stringResource(
            R.string.recibo_answer_description,
            label,
            summary.amount.spoken,
            stamp
        )
    ) {
        summary.closed?.let { ClosedMonthNote(it) }
        // Revisar's two facts under the big number, before the tear line (v2 Recibo).
        summary.shortfall?.let { ShortfallNote(it) }
        summary.perDay?.let { PerDayFigure(it, over = summary.stamp == ReciboStamp.Over) }
        summary.reserved?.let { ReservedFootnote(it, onReserved) }
        if (summary.adjustable) AdjustBudgetLink(onAdjust)
    }
}

/** The words above the month's big number. */
@Composable
internal fun answerLabel(label: ReciboAnswerLabel): String = stringResource(
    when (label) {
        ReciboAnswerLabel.Remaining -> R.string.recibo_answer_remaining
        ReciboAnswerLabel.Spent -> R.string.recibo_answer_spent
        ReciboAnswerLabel.ShortForPayments -> R.string.recibo_answer_short
    }
)

/** "Quedan 100.000 y tus pagos previstos suman 150.000." or "Pasaste tu límite por …". */
@Composable
internal fun ShortfallNote(shortfall: ReciboShortfall) {
    val sentence = if (shortfall.over) R.string.recibo_shortfall_over else R.string.recibo_shortfall
    // On screen the figures as written; read aloud with their currency.
    val spoken = stringResource(sentence, shortfall.left.spoken, shortfall.reserved.spoken)
    Text(
        text = stringResource(sentence, shortfall.left.digits, shortfall.reserved.digits),
        style = MaterialTheme.typography.bodyMedium,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier
            .padding(top = SelloTheme.spacing.sm)
            .testTag(RECIBO_SHORTFALL_TAG)
            .clearAndSetSemantics { contentDescription = spoken }
    )
}

/** "Incluye 200.000 separado para 1 pago previsto ›": opens the payments reserved. */
@Composable
internal fun ReservedFootnote(reserved: ReciboReserved, onClick: () -> Unit) {
    val payments =
        pluralStringResource(R.plurals.recibo_reserved_payments, reserved.count, reserved.count)
    val template = stringResource(R.string.recibo_reserved, AMOUNT_MARK, payments)
    val text = buildAnnotatedString {
        val at = template.indexOf(AMOUNT_MARK)
        append(template.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.W800, color = SelloTheme.colors.ink)) {
            append(reserved.total.digits)
        }
        append(template.substring(at + AMOUNT_MARK.length))
    }
    val spoken = stringResource(R.string.recibo_reserved, reserved.total.spoken, payments)
    Footnote(
        text = text,
        onClick = onClick,
        modifier = Modifier.testTag(RECIBO_RESERVED_TAG).semantics { contentDescription = spoken }
    )
}

/** "Ajustar presupuesto ›", for a month in progress that is Excedido or Revisar. */
@Composable
private fun AdjustBudgetLink(onClick: () -> Unit) {
    val spoken = stringResource(R.string.recibo_adjust_budget_description)
    val ink = SelloTheme.colors.brand
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = SelloTheme.spacing.xs)
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            // Before the clear: a tag after it would be cleared with everything else.
            .testTag(RECIBO_ADJUST_TAG)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = null) {
                    onClick()
                    true
                }
            }
    ) {
        Text(
            text = stringResource(R.string.recibo_adjust_budget),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.W800,
            color = ink
        )
        Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(20.dp), tint = ink)
    }
}

/** Over the limit nothing is left per day: the 0 is in loss ink, and "Excedido" says why. */
@Composable
private fun PerDayFigure(perDay: ReciboPerDay, over: Boolean) {
    val days = pluralStringResource(
        R.plurals.recibo_days_including_today,
        perDay.days,
        perDay.days
    )
    val spoken = stringResource(R.string.recibo_per_day_description, perDay.amount.spoken, days)
    TearLine()
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(RECIBO_PER_DAY_TAG)
            .clearAndSetSemantics { contentDescription = spoken }
    ) {
        // The unit moves under a figure too wide to share its line.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.Bottom
        ) {
            MoneyText(
                perDay.amount,
                style = MoneyStyle.Card,
                color = if (over) SelloTheme.colors.semantic.loss else SelloTheme.colors.ink
            )
            Text(
                text = stringResource(R.string.recibo_per_day_unit),
                style = MaterialTheme.typography.bodyLarge,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = SelloTheme.spacing.xs)
            )
        }
        Text(
            text = days,
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft
        )
    }
}

/**
 * Spent and limit are never negative, so no sign is drawn. Read with "pesos" in place of
 * the middle dot.
 */
@Composable
private fun ClosedMonthNote(closed: ReciboClosedMonth) {
    val spoken = stringResource(
        R.string.recibo_month_closed_description,
        closed.spent.spoken,
        closed.limit.spoken
    )
    Text(
        text = stringResource(
            R.string.recibo_month_closed,
            closed.spent.digits,
            closed.limit.digits
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier
            .padding(top = SelloTheme.spacing.sm)
            .testTag(RECIBO_CLOSED_TAG)
            .semantics { contentDescription = spoken }
    )
}

@Composable
private fun CategoriesSlip(
    categories: List<ReciboCategory>,
    selectedCategoryId: String?,
    onAction: (ReciboAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Only the answer slip above keeps the pinked edge; this card is plain paper.
    Slip(modifier.testTag(RECIBO_CATEGORIES_TAG), pinked = false) {
        val title: @Composable (Modifier) -> Unit = { titleModifier ->
            Text(
                text = stringResource(R.string.recibo_categories_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = titleModifier
                    .semantics { heading() }
                    .testTag(RECIBO_CATEGORIES_HEADING_TAG)
            )
        }
        // "+ Nueva categoría", as Metas' "+ Nueva meta" on its band.
        val add: @Composable () -> Unit = {
            BandChip(
                label = stringResource(R.string.recibo_new_category),
                onClick = { onAction(ReciboAction.CreateCategory) },
                onPaper = true,
                modifier = Modifier.testTag(RECIBO_ADD_CATEGORY_TAG)
            )
        }
        // At large text the pill goes under the title, so neither is squeezed.
        if (LocalDensity.current.fontScale >= LARGE_HEADER_SCALE) {
            Column {
                title(Modifier)
                add()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                add()
            }
        }
        val editLabel = stringResource(R.string.recibo_category_edit)
        // Archived ones last and apart, each part in the order it came in.
        val (archived, active) = categories.partition { it.archived }
        val cell: @Composable (Int, ReciboCategory) -> Unit = { index, category ->
            val isSelected = category.id == selectedCategoryId
            CategoryCell(
                name = category.name,
                icon = CategoryIcons.iconFor(category.iconKey),
                amount = category.cellAmount,
                fraction = category.fraction,
                description = categoryDescription(category),
                onClick = { onAction(ReciboAction.OpenCategory(category.id)) },
                actions = listOf(
                    LineAction(editLabel) { onAction(ReciboAction.EditCategory(category.id)) }
                ),
                modifier = Modifier
                    .testTag(reciboCategoryTag(category.id))
                    .fillMaxWidth()
                    .background(
                        if (isSelected) SelloTheme.colors.brandSoft else SelloTheme.colors.paper,
                        MaterialTheme.shapes.small
                    )
                    .semantics { selected = isSelected },
                over = category.over,
                amountCaption = when {
                    category.unlimited -> stringResource(R.string.recibo_cell_unlimited_caption)
                    category.limit == null -> stringResource(R.string.recibo_cell_spent_caption)
                    else -> null
                },
                staggerIndex = index,
                unlimited = category.unlimited,
                archived = category.archived
            )
        }
        if (active.isNotEmpty()) CategoryGrid(active, cell = cell)
        if (archived.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recibo_archived_section),
                // As Ajustar presupuesto's group headings.
                style = MaterialTheme.typography.titleSmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier
                    .padding(top = SelloTheme.spacing.md, bottom = SelloTheme.spacing.xs)
                    .semantics { heading() }
            )
            CategoryGrid(archived, cell = cell)
        }
    }
}

/** The whole cell in words, with both exact amounts and the verdict. */
@Composable
internal fun categoryDescription(category: ReciboCategory): String {
    val headline = reciboHeadline(category.figure.headline)
    val stamp = stampText(category.figure.stamp)
    val amount = category.figure.amount.spoken
    val sentence = if (category.limit != null) {
        stringResource(
            R.string.recibo_cell_limited,
            category.name,
            headline,
            amount,
            category.limit.spoken,
            stamp
        )
    } else {
        stringResource(R.string.recibo_cell_open, category.name, headline, amount, stamp)
    }
    return if (category.archived) {
        stringResource(R.string.recibo_cell_archived, sentence)
    } else {
        sentence
    }
}

@Composable
internal fun reciboHeadline(headline: ReciboHeadline): String = stringResource(
    when (headline) {
        ReciboHeadline.Remaining -> R.string.recibo_headline_remaining
        ReciboHeadline.Overspent -> R.string.recibo_headline_overspent
        ReciboHeadline.LeftOver -> R.string.recibo_headline_left_over
        ReciboHeadline.Spent -> R.string.recibo_headline_spent
    }
)

@Composable
internal fun stampText(stamp: ReciboStamp): String = stringResource(
    when (stamp) {
        ReciboStamp.OnTrack -> R.string.recibo_stamp_on_track
        ReciboStamp.AtLimit -> R.string.recibo_stamp_at_limit
        ReciboStamp.Over -> R.string.recibo_stamp_over
        ReciboStamp.WithinLimit -> R.string.recibo_stamp_within_limit
        ReciboStamp.OverLimit -> R.string.recibo_stamp_over
        ReciboStamp.NoLimit -> R.string.recibo_stamp_no_limit
        ReciboStamp.Paused -> R.string.recibo_stamp_paused
        ReciboStamp.NotSet -> R.string.recibo_stamp_not_set
        ReciboStamp.NoMargin -> R.string.recibo_stamp_no_margin
        ReciboStamp.Review -> R.string.recibo_stamp_review
    }
)

internal fun stampInk(stamp: ReciboStamp): StampInk = when (stamp) {
    ReciboStamp.OnTrack, ReciboStamp.WithinLimit -> StampInk.Good

    ReciboStamp.Over, ReciboStamp.OverLimit -> StampInk.Over

    ReciboStamp.AtLimit, ReciboStamp.NoLimit, ReciboStamp.Paused, ReciboStamp.NotSet,
    ReciboStamp.NoMargin -> StampInk.Neutral

    ReciboStamp.Review -> StampInk.Warn
}

fun reciboCategoryTag(id: String) = "recibo:category:$id"

/** From this font scale the categories card stacks its title and its add pill. */
private const val LARGE_HEADER_SCALE = 1.3f

const val RECIBO_SUMMARY_TAG = "recibo:summary"
const val RECIBO_SHORTFALL_TAG = "recibo:shortfall"
const val RECIBO_RESERVED_TAG = "recibo:reserved"
private const val AMOUNT_MARK = "\u0000"
const val RECIBO_PER_DAY_TAG = "recibo:per-day"
const val RECIBO_CLOSED_TAG = "recibo:closed"
const val RECIBO_CATEGORIES_TAG = "recibo:categories"
const val RECIBO_CATEGORIES_HEADING_TAG = "recibo:categories:heading"
const val RECIBO_ADJUST_TAG = "recibo:adjust-budget"
const val RECIBO_ADD_CATEGORY_TAG = "recibo:add-category"
const val RECIBO_LOADING_TAG = "recibo:loading"
const val RECIBO_FIRST_RUN_TAG = "recibo:first-run"
const val RECIBO_STALE_TAG = "recibo:stale"
const val RECIBO_FAILED_TAG = "recibo:failed"
