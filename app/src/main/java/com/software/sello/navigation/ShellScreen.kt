package com.software.sello.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.Blotter
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.selloWindowLayout
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.month.MonthNames

/**
 * Whether a detail fits beside what it is the detail of: a window laid out in two
 * columns that is also wide enough for both to be read. A window that is short and
 * narrow, such as half a phone's screen, shows one at a time like any phone.
 */
fun showsDetailBeside(width: Dp, height: Dp): Boolean =
    selloWindowLayout(width, height).twoPane && width >= SideBySideWidth

private val SideBySideWidth = 600.dp

/**
 * The detail of what the content lists: its [title] and how to draw it. An [archived]
 * category takes no new expense, so its dock says so instead of starting one.
 */
class ShellDetail(
    val title: String,
    val archived: Boolean = false,
    val actions: @Composable RowScope.() -> Unit = {},
    val content: @Composable (PaddingValues) -> Unit
)

/**
 * The dock of a category's own screen: it starts an expense in that category, or, for an
 * archived one, stays in place disabled and reads "Archivada" beside a prohibited sign.
 */
@Composable
internal fun categoryDock(archived: Boolean, onClick: () -> Unit): SelloDock = if (archived) {
    SelloDock(
        label = stringResource(R.string.dock_entry_archived_label),
        actionLabel = stringResource(R.string.recibo_category_archived),
        onClick = {},
        icon = SelloIcon.Block,
        enabled = false
    )
} else {
    SelloDock(
        label = stringResource(R.string.dock_entry_label),
        actionLabel = stringResource(R.string.dock_entry_action),
        onClick = onClick
    )
}

/**
 * "Estás viendo septiembre" and "Volver a octubre". The year is added to the month
 * viewed only when it is not the current one's: "septiembre de 2025".
 */
@Composable
internal fun PastMonthNotice(
    state: ShellState,
    names: MonthNames,
    onAction: (ShellAction) -> Unit
) {
    val month = state.selectedMonth
    val viewed = if (month.year == state.currentMonth.year) {
        names.inSentence(month)
    } else {
        stringResource(R.string.month_ribbon_month_of_year, names.inSentence(month), month.year)
    }
    val sentence = stringResource(R.string.month_ribbon_viewing, viewed)
    val start = sentence.lastIndexOf(viewed)
    PastMonthRibbon(
        message = buildAnnotatedString {
            append(sentence)
            if (start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + viewed.length)
            }
        },
        returnLabel = stringResource(
            R.string.month_ribbon_return,
            names.inSentence(state.currentMonth)
        ),
        onReturn = { onAction(ShellAction.ReturnToCurrentMonth) },
        modifier = Modifier.testTag(PAST_MONTH_RIBBON_TAG)
    )
}

/**
 * The frame: the month in the title, the content, and the month picker. With a
 * [detail], a window with room for two columns shows it beside the content; any other
 * window shows it instead of the content once it has been opened, under its own title
 * and with a way back. The picker is
 * a bottom sheet, or a side panel beside the content in a window wide enough for one.
 * There is no tab bar while there is one tab. The dock asks to record an expense, unless [entryDock] is false.
 *
 * A screen not tied to a month, as Metas and a goal are, passes [titleOnly]: its whole
 * title, with no month bar, month switcher or past-month ribbon. Its [band], if any,
 * sits on the band under that title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    state: ShellState,
    names: MonthNames,
    onAction: (ShellAction) -> Unit,
    modifier: Modifier = Modifier,
    detail: ShellDetail? = null,
    notice: (@Composable () -> Unit)? = null,
    navigation: SelloNavigation? = null,
    onBack: (() -> Unit)? = null,
    screenTitle: String? = null,
    showMonthAsSubtitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    pastMonthRibbon: Boolean = true,
    titleLabel: String? = null,
    dock: SelloDock? = null,
    entryDock: Boolean = true,
    titleOnly: String? = null,
    band: (@Composable () -> Unit)? = null,
    titleChip: (@Composable (compact: Boolean) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val title = names.title(state.selectedMonth)
    // Over a past month, every screen that shows it says so and offers the way back.
    val ribbon: (@Composable () -> Unit)? = when {
        titleOnly != null -> band

        pastMonthRibbon && state.selectedMonth != state.currentMonth -> {
            { PastMonthNotice(state, names, onAction) }
        }

        else -> null
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layout = selloWindowLayout(maxWidth, maxHeight)
        val sidePanel = layout.sidePanelWidth != null
        val beside = showsDetailBeside(maxWidth, maxHeight)
        val openedId = state.detailCategoryId.takeIf { detail != null && !beside }
        if (openedId != null && detail != null) {
            SelloScaffold(
                title = detail.title,
                subtitle = title,
                actions = detail.actions,
                blotter = Blotter.Short,
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(ShellAction.CloseCategory) },
                        modifier = Modifier.testTag(DETAIL_BACK_TAG)
                    ) {
                        Icon(
                            SelloIcon.ArrowBack.painter(),
                            stringResource(R.string.category_detail_back)
                        )
                    }
                },
                // From a category's own screen, an expense starts in that category.
                dock = categoryDock(detail.archived) {
                    onAction(ShellAction.StartEntryIn(openedId))
                },
                notice = notice,
                ribbon = ribbon,
                content = detail.content
            )
        } else {
            // A tab gets the v2 month bar; a pushed screen keeps its titled bar and back arrow.
            val tab = screenTitle == null && onBack == null
            val bar = monthBarLabel(state, names)
            SelloScaffold(
                title = titleOnly ?: screenTitle ?: title,
                subtitle = if (showMonthAsSubtitle && titleOnly == null) title else null,
                topBar = if (tab && titleOnly != null) {
                    { TitleBar(titleOnly, chip = titleChip, actions = actions) }
                } else if (tab) {
                    {
                        MonthBar(
                            month = bar.month,
                            year = bar.year,
                            onClick = { onAction(ShellAction.OpenMonthPicker) },
                            description = bar.spoken,
                            buttonModifier = Modifier.testTag(MONTH_SWITCHER_TAG),
                            actions = actions
                        )
                    }
                } else {
                    null
                },
                ribbon = ribbon,
                navigation = navigation,
                navigationIcon = onBack?.let { back ->
                    {
                        IconButton(onClick = back) {
                            Icon(
                                SelloIcon.ArrowBack.painter(),
                                stringResource(R.string.navigation_back)
                            )
                        }
                    }
                },
                actions = actions,
                titleContent = if (showMonthAsSubtitle || titleOnly != null) {
                    null
                } else {
                    {
                        val label = when {
                            titleLabel != null -> titleLabel
                            screenTitle == null -> title
                            else -> stringResource(R.string.screen_title_month, screenTitle, title)
                        }
                        MonthSwitcher(
                            label = label,
                            onClick = { onAction(ShellAction.OpenMonthPicker) },
                            modifier = Modifier
                                .testTag(MONTH_SWITCHER_TAG)
                                .semantics { contentDescription = bar.spoken }
                        )
                    }
                },
                blotter = Blotter.Short,
                // A screen can offer its own entry, as income history does, or none
                // when its own action is the only one, as Respaldo's is.
                dock = dock ?: if (entryDock) {
                    SelloDock(
                        label = stringResource(R.string.dock_entry_label),
                        actionLabel = stringResource(R.string.dock_entry_action),
                        onClick = { onAction(ShellAction.StartEntry) }
                    )
                } else {
                    null
                },
                notice = notice,
                secondaryPane = if (state.monthPickerOpen && sidePanel) {
                    { padding ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(
                                rememberScrollState()
                            ).padding(padding)
                        ) {
                            Slip(pinked = false) { MonthPicker(state, names, onAction) }
                        }
                    }
                } else {
                    detail?.content.takeIf { beside }
                },
                content = content
            )
        }
        if (state.monthPickerOpen && !sidePanel) {
            ModalBottomSheet(
                onDismissRequest = { onAction(ShellAction.CloseMonthPicker) },
                // Opened whole: a half-open sheet would hide the later months.
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = SelloTheme.colors.paper,
                contentColor = SelloTheme.colors.ink
            ) {
                // Scrolls, so a large font or a short window cannot cut months off.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MonthPicker(state, names, onAction)
                }
            }
        }
    }
}

/** What the month bar shows and says, made from one month so they cannot disagree. */
@Immutable
internal data class MonthBarLabel(val month: String, val year: String, val spoken: String)

/** "Mes actual" is said only of the month the financial day is in. */
@Composable
internal fun monthBarLabel(state: ShellState, names: MonthNames): MonthBarLabel = MonthBarLabel(
    month = names.name(state.selectedMonth),
    year = state.selectedMonth.year.toString(),
    spoken = stringResource(
        if (state.selectedMonth == state.currentMonth) {
            R.string.month_picker_open_current
        } else {
            R.string.month_picker_open_selected
        },
        names.title(state.selectedMonth)
    )
)

const val MONTH_SWITCHER_TAG = "shell:month-switcher"
const val PAST_MONTH_RIBBON_TAG = "shell:past-month-ribbon"
const val DETAIL_BACK_TAG = "shell:detail-back"
