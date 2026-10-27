package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/** How far the band colour reaches below the status bar: 0, 100dp or 132dp. */
enum class Blotter(val height: Dp) { None(0.dp), Short(100.dp), Tall(132.dp) }

/**
 * The frame of a screen: top bar, content on the desk, and the app's navigation and
 * entry action arranged for the window it is given.
 *
 * In an upright phone or a medium window the [dock] and tab bar sit under the content,
 * never over it. In a short or expanded window a rail at the leading edge carries the
 * [navigation] and the dock's "+", and [secondaryPane], if given, sits beside [content];
 * each pane scrolls on its own. Both bottom controls step aside for the system keyboard.
 * A [notice] is drawn over the bottom of the content, above the dock, and moves nothing.
 * A [topBar] takes the place of the title row ([title], [subtitle], [titleContent],
 * [navigationIcon] and [actions] then go unused). A [ribbon], if given, sits on the band
 * under the top bar or the title row; the band grows with it, so the content still
 * starts on the band.
 * The scaffold holds no state: selection, the month and what a pane shows are the app's.
 *
 * @param blotterScroll how far, in pixels, the content has scrolled. The band shrinks
 * by that much; the top bar keeps the band colour until the blotter is gone.
 * @param windowInsets the system areas to keep clear. The paddings passed to [content]
 * and [secondaryPane] already include what they must avoid.
 */
@Composable
fun SelloScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    blotter: Blotter = Blotter.None,
    blotterScroll: () -> Int = { 0 },
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    navigation: SelloNavigation? = null,
    dock: SelloDock? = null,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    notice: (@Composable () -> Unit)? = null,
    secondaryPane: (@Composable (PaddingValues) -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    ribbon: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = SelloTheme.colors
    val density = LocalDensity.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.desk)) {
        val layout = selloWindowLayout(maxWidth, maxHeight)
        val rail = layout.useRail && navigation != null
        val bottomControls = !rail && !keyboardOpen
        val showBar = bottomControls && navigation != null
        val showDock = bottomControls && dock != null

        val scrolled = with(density) { blotterScroll().toDp() }
        val bandLeft = blotter.height - scrolled
        val onBand = blotter != Blotter.None && bandLeft > 0.dp
        val statusBar = windowInsets.asPaddingValues().calculateTopPadding()
        // The status bar, the bar and any ribbon, as last measured.
        var topArea by remember { mutableStateOf(statusBar + TopBarHeight) }
        val measured = topBar != null || ribbon != null
        if (measured) {
            // A v2 bar or a ribbon always sits on the band; the band then reaches as far
            // under the content as it does under the title row alone.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topArea + (bandLeft - TopBarHeight).coerceAtLeast(0.dp))
                    .background(colors.band)
                    .testTag(SCAFFOLD_BAND_TAG)
            )
        } else if (onBand) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBar + maxOf(bandLeft, TopBarHeight))
                    .background(colors.band)
                    .testTag(SCAFFOLD_BAND_TAG)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (rail) {
                SelloNavigationRail(
                    navigation = navigation,
                    dock = dock,
                    modifier = Modifier
                        .testTag(SCAFFOLD_RAIL_TAG)
                        .background(colors.paper)
                        .windowInsetsPadding(
                            windowInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                        )
                )
            }
            // A system bar or cutout at the side covers nothing: not the top bar, the
            // panes or the dock. Beside a rail, the rail already keeps the leading edge clear.
            val sides = if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(windowInsets.only(sides))
            ) {
                if (measured) {
                    CompositionLocalProvider(LocalContentColor provides colors.onBand) {
                        Column(
                            Modifier
                                .onSizeChanged { topArea = with(density) { it.height.toDp() } }
                                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top))
                        ) {
                            if (topBar != null) {
                                topBar()
                            } else {
                                TopBar(
                                    title = title,
                                    subtitle = subtitle,
                                    titleContent = titleContent,
                                    navigationIcon = navigationIcon,
                                    actions = actions,
                                    modifier = Modifier
                                )
                            }
                            if (ribbon != null) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(colors.band)
                                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                ) { ribbon() }
                            }
                        }
                    }
                } else {
                    CompositionLocalProvider(
                        LocalContentColor provides if (onBand) colors.onBand else colors.ink
                    ) {
                        TopBar(
                            title = title,
                            subtitle = subtitle,
                            titleContent = titleContent,
                            navigationIcon = navigationIcon,
                            actions = actions,
                            modifier = Modifier
                                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top))
                        )
                    }
                }
                // Without bottom controls the content itself must clear the system bar.
                val bottomInset = if (showBar || showDock) {
                    0.dp
                } else {
                    windowInsets.asPaddingValues().calculateBottomPadding()
                }
                val padding = PaddingValues(
                    start = ContentMargin,
                    end = ContentMargin,
                    top = 0.dp,
                    bottom = ContentMargin + bottomInset
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (layout.twoPane && secondaryPane != null) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            val first = layout.listPaneWidth
                                ?.let { Modifier.width(it) } ?: Modifier.weight(1f)
                            Box(first.fillMaxHeight().testTag(SCAFFOLD_PRIMARY_TAG)) {
                                content(padding)
                            }
                            Box(
                                Modifier.weight(1f).fillMaxHeight().testTag(SCAFFOLD_SECONDARY_TAG)
                            ) {
                                secondaryPane(padding)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = layout.contentMaxWidth ?: Dp.Unspecified)
                                .fillMaxSize()
                                .testTag(SCAFFOLD_PRIMARY_TAG)
                        ) { content(padding) }
                    }
                    if (notice != null) {
                        // Over the content, so what it says never moves what is under it.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    start = ContentMargin,
                                    end = ContentMargin,
                                    bottom = NoticeGap + bottomInset
                                )
                                .testTag(SCAFFOLD_NOTICE_TAG)
                        ) { notice() }
                    }
                }
                if (showDock) {
                    // One full-width "+ Anotar", read as what it records: "Anotar un gasto".
                    EntryDock(
                        label = dock.actionLabel,
                        onClick = dock.onClick,
                        tone = dock.tone,
                        icon = dock.icon,
                        enabled = dock.enabled,
                        modifier = Modifier
                            .padding(horizontal = ContentMargin, vertical = 8.dp)
                            .then(
                                if (showBar) {
                                    Modifier
                                } else {
                                    Modifier.windowInsetsPadding(
                                        windowInsets.only(WindowInsetsSides.Bottom)
                                    )
                                }
                            )
                            .testTag(SCAFFOLD_DOCK_TAG)
                            .semantics { contentDescription = dock.label }
                    )
                }
                if (showBar) {
                    Box(
                        Modifier
                            .testTag(SCAFFOLD_BAR_TAG)
                            .background(colors.paper)
                            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom))
                    ) { SelloNavigationBar(navigation) }
                }
            }
        }
    }
}

/**
 * The title row: navigation icon, title and actions on one line. When large text leaves
 * the title no room beside the actions, even on two lines, the title moves under them
 * and takes the whole width, so it is never cut short.
 */
@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    titleContent: (@Composable () -> Unit)?,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier
) {
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleLarge
    val margin = if (navigationIcon == null) ContentMargin else 0.dp
    SubcomposeLayout(modifier.fillMaxWidth().heightIn(min = TopBarHeight)) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val start = margin.roundToPx()
        val nav = subcompose(TopBarSlot.Navigation) { navigationIcon?.invoke() }
            .map { it.measure(loose) }
        val navWidth = nav.maxOfOrNull { it.width } ?: 0
        val acts = subcompose(TopBarSlot.Actions) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }.map {
            it.measure(loose.copy(maxWidth = (constraints.maxWidth - navWidth).coerceAtLeast(0)))
        }
        val actionsWidth = acts.maxOfOrNull { it.width } ?: 0
        val beside = (constraints.maxWidth - start * 2 - navWidth - actionsWidth).coerceAtLeast(0)
        val stacked = titleContent == null && actionsWidth > 0 &&
            measurer.measure(
                title,
                titleStyle,
                maxLines = TITLE_LINES,
                constraints = Constraints(maxWidth = beside)
            ).multiParagraph.didExceedMaxLines
        val titleStart = if (stacked) ContentMargin.roundToPx() else start + navWidth
        val titleWidth = if (stacked) constraints.maxWidth - titleStart * 2 else beside
        val heading = subcompose(TopBarSlot.Title) {
            Column {
                if (titleContent != null) {
                    titleContent()
                } else {
                    // A second line before an ellipsis: large text must not cut a title short.
                    Text(
                        text = title,
                        style = titleStyle,
                        maxLines = TITLE_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }.map { it.measure(Constraints(maxWidth = titleWidth.coerceAtLeast(0))) }
        val headingHeight = heading.maxOfOrNull { it.height } ?: 0
        val iconsHeight = maxOf(
            nav.maxOfOrNull { it.height } ?: 0,
            acts.maxOfOrNull { it.height } ?: 0
        )
        val minHeight = TopBarHeight.roundToPx()
        val rowHeight = if (stacked) {
            maxOf(iconsHeight, minHeight)
        } else {
            maxOf(iconsHeight, headingHeight, minHeight)
        }
        val height = if (stacked) rowHeight + headingHeight + TitleGap.roundToPx() else rowHeight
        layout(constraints.maxWidth, height) {
            nav.forEach { it.placeRelative(start, (rowHeight - it.height) / 2) }
            acts.forEach {
                it.placeRelative(
                    constraints.maxWidth - start - it.width,
                    (rowHeight - it.height) / 2
                )
            }
            heading.forEach {
                val y = if (stacked) rowHeight else (rowHeight - it.height) / 2
                it.placeRelative(titleStart, y)
            }
        }
    }
}

private enum class TopBarSlot { Navigation, Actions, Title }

private const val TITLE_LINES = 2
private val TitleGap = 8.dp
private val TopBarHeight = 50.dp
private val ContentMargin = 12.dp
private val NoticeGap = 8.dp

const val SCAFFOLD_BAND_TAG = "sello:scaffold:band"
const val SCAFFOLD_RAIL_TAG = "sello:scaffold:rail"
const val SCAFFOLD_BAR_TAG = "sello:scaffold:bar"
const val SCAFFOLD_DOCK_TAG = "sello:scaffold:dock"
const val SCAFFOLD_NOTICE_TAG = "sello:scaffold:notice"
const val SCAFFOLD_PRIMARY_TAG = "sello:scaffold:primary"
const val SCAFFOLD_SECONDARY_TAG = "sello:scaffold:secondary"
