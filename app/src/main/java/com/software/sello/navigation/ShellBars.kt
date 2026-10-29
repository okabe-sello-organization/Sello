package com.software.sello.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The tab's top bar on the band: the month, its year and a chevron form one button that
 * opens the month picker; [actions] (settings, add) sit at the trailing edge as round
 * 48dp icon buttons. [description] says the month and year to a screen reader;
 * [buttonModifier] goes on the month button itself.
 */
@Composable
internal fun MonthBar(
    month: String,
    year: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "$month $year",
    buttonModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = SelloTheme.colors
    Row(
        modifier.fillMaxWidth().background(colors.band).heightIn(min = 56.dp)
            .padding(start = SelloTheme.spacing.lg, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            buttonModifier
                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onClick()
                        true
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                month,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = (-0.02).em
                ),
                color = colors.onBand
            )
            Text(
                year,
                Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600),
                color = colors.onBand.copy(alpha = 0.8f)
            )
            Icon(SelloIcon.KeyboardArrowDown.painter(), null, tint = colors.onBand)
        }
        Spacer(Modifier.weight(1f))
        CompositionLocalProvider(LocalContentColor provides colors.onBand) { actions() }
    }
}

/**
 * A tab's top bar on the band when it has a title instead of a month, as on Metas and
 * Inversiones: the [title] as a heading, then a [chip] such as **+ Nueva meta** (a
 * [BandChip]), told to be compact when its label would push the title off its line, and
 * any [actions] as round 48dp icon buttons at the trailing edge.
 */
@Composable
internal fun TitleBar(
    title: String,
    modifier: Modifier = Modifier,
    chip: (@Composable (compact: Boolean) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = SelloTheme.colors
    val width = LocalWindowInfo.current.containerSize.width
    // The chip keeps its label while the title fits on its line beside it; when it
    // would not, as on a narrow window, it is the round + alone.
    var compact by remember(title, width) { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().background(colors.band).heightIn(min = 56.dp)
            .padding(start = SelloTheme.spacing.lg, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-0.02).em
            ),
            color = colors.onBand,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (chip != null && it.hasVisualOverflow) compact = true }
        )
        if (chip != null) {
            Box(
                Modifier.padding(start = SelloTheme.spacing.sm, end = if (compact) 0.dp else 6.dp)
            ) {
                chip(compact)
            }
        }
        CompositionLocalProvider(LocalContentColor provides colors.onBand) { actions() }
    }
}

/**
 * Says which past month is shown and goes back in one tap. A translucent pill meant to
 * sit on the band, under the [MonthBar]. Bold spans in [message] name the month. Plain
 * presentation data only; no selected-month or clock logic.
 */
@Composable
internal fun PastMonthRibbon(
    message: AnnotatedString,
    returnLabel: String,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    Row(
        modifier.fillMaxWidth()
            .background(colors.onBand.copy(alpha = 0.16f), CircleShape)
            .padding(
                start = SelloTheme.spacing.md,
                top = SelloTheme.spacing.xs,
                end = SelloTheme.spacing.xs,
                bottom = SelloTheme.spacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        Icon(SelloIcon.Event.painter(), null, Modifier.size(20.dp), tint = colors.onBand)
        Text(
            message,
            color = colors.onBand,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            returnLabel,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 40.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) colors.paper else Color.White)
                .clickable(role = Role.Button, onClick = onReturn)
                .padding(horizontal = 14.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            color = if (colors.isDark) colors.ink else colors.band,
            fontWeight = FontWeight.W800,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

/** The month as a screen title that opens a month picker. It takes its ink from the bar. */
@Composable
internal fun MonthSwitcher(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .clip(SelloTheme.shapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = SelloTheme.spacing.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 24.sp),
            // A second line before an ellipsis, as for any long screen title.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Icon(SelloIcon.KeyboardArrowDown.painter(), null, Modifier.size(22.dp))
    }
}
