package com.software.sello.feature.recibo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.CelebrationAction
import com.software.sello.designsystem.component.CelebrationMoment
import com.software.sello.designsystem.component.CelebrationStamp
import com.software.sello.designsystem.component.DigitGrouping
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloColors
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.month.monthNames
import java.util.Locale

/**
 * "¡Cerraste el año!" in the month invitation's place, 1 to 7 January: the ink's band with
 * the year's stamp and a few paper chads. It opens the year's big moment; the × puts it away
 * for that year.
 */
@Composable
internal fun YearCard(
    invitation: ReciboInvitation,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    val gold = celebrationGold(colors)
    val year = invitation.month.year.toString()
    val spoken = stringResource(R.string.recibo_year_card_description, year)
    val shape = SelloTheme.shapes.panel
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag(RECIBO_YEAR_CARD_TAG)
            // The gold ring sits flush around the band, as the mock's.
            .border(RING, gold, RoundedCornerShape(CARD_RADIUS + RING))
            .padding(RING)
            .clip(shape)
            .background(colors.band, shape)
            .drawBehind { cardChads(colors.onBand, gold) }
            .heightIn(min = 64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = SelloTheme.spacing.lg, end = SelloTheme.spacing.xs)
                .padding(vertical = 14.dp)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    onClick(label = null) {
                        onOpen()
                        true
                    }
                }
        ) {
            YearStamp(year, colors.onBand)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recibo_year_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = SelloTheme.type.weight.emphasis,
                    color = colors.onBand
                )
                Text(
                    stringResource(R.string.recibo_year_card_body, year),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBand.copy(alpha = SOFT)
                )
            }
            Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(24.dp), colors.onBand)
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(RECIBO_YEAR_CARD_DISMISS_TAG)
        ) {
            Icon(
                SelloIcon.Close.painter(),
                stringResource(R.string.recibo_invitation_dismiss),
                Modifier.size(24.dp),
                colors.onBand.copy(alpha = SOFT)
            )
        }
    }
}

/** The year as a white stamp on the band: outlined, tilted, in the stamp face. */
@Composable
private fun YearStamp(year: String, ink: Color) {
    Text(
        year,
        style = SelloTheme.type.stampSmall.copy(fontSize = 18.sp, letterSpacing = 0.05.em),
        color = ink,
        modifier = Modifier
            .rotate(-5f)
            .border(2.5.dp, ink, RoundedCornerShape(8.dp))
            .padding(start = 8.dp, end = 8.dp, top = 3.dp, bottom = 1.dp)
    )
}

/** Three chads resting on the card, as the mock's: two holes and a gold strip. */
private fun DrawScope.cardChads(white: Color, gold: Color) {
    drawCircle(white.copy(alpha = 0.85f), 4.5.dp.toPx(), Offset(size.width * 0.62f, 12.5.dp.toPx()))
    val strip = Offset(size.width * 0.74f, 30.dp.toPx())
    rotate(30f, strip) {
        drawRect(gold, strip, Size(7.dp.toPx(), 12.dp.toPx()))
    }
    drawCircle(white.copy(alpha = 0.85f), 3.dp.toPx(), Offset(size.width * 0.83f, 55.dp.toPx()))
}

/**
 * The year's big moment, built as a goal's ¡Lo lograste! in the ink's band: the twelve months
 * land as stamps (faded for a month over its limit or without one), "¡Un año con Sello!", the
 * stamped plate with the year's counts, paper chads, then Ver tu año and Listo. Nothing shows
 * while it is closed or still reading.
 */
@Composable
fun YearCloseMoment(state: YearCloseState, onDone: () -> Unit, onViewYear: (Int) -> Unit) {
    val (year, months) = when (state) {
        YearCloseState.Closed, is YearCloseState.Loading -> return
        is YearCloseState.Ready -> state.year to state.months
        is YearCloseState.Failed -> state.year to emptyList()
    }
    val colors = SelloTheme.colors
    val gold = celebrationGold(colors)
    val names = monthNames()
    val stamps = months.map { month ->
        val name = names.name(month.month)
        CelebrationStamp(
            MilestoneSpec(
                label = name.take(1).uppercase(Locale.ROOT),
                description = stringResource(
                    when (month.mark) {
                        YearCloseMark.Within -> R.string.year_close_month_within
                        YearCloseMark.Over -> R.string.year_close_month_over
                        YearCloseMark.NoLimit -> R.string.year_close_month_no_limit
                    },
                    name
                ),
                state = MilestoneState.Earned
            ),
            faded = month.mark != YearCloseMark.Within
        )
    }
    CelebrationMoment(
        title = stringResource(R.string.year_close_title),
        stamps = stamps,
        // The twelve land a tenth of a second apart, ending where a goal's last stamp does.
        landAtMillis = List(stamps.size) { FIRST_LANDING + it * LANDING_STEP },
        flood = colors.band,
        onFlood = colors.onBand,
        chadColors = listOf(colors.onBand, gold, colors.onBand, gold, colors.onBand),
        actionTone = ButtonTone.OnBand,
        primary = CelebrationAction(stringResource(R.string.year_close_view), { onViewYear(year) }),
        secondary = CelebrationAction(stringResource(R.string.year_close_done), onDone),
        onDismiss = onDone,
        stampDiameter = 46.dp,
        stampsPerRow = MONTHS_PER_ROW,
        stampRowWidth = 336.dp
    ) {
        YearPlate(state, year.toString(), colors.onBand)
    }
}

/** AÑO CERRADO, the year, a dashed rule, then its counts, never money, two to a row. */
@Composable
private fun YearPlate(state: YearCloseState, year: String, ink: Color) {
    Text(
        stringResource(R.string.year_close_plate).uppercase(Locale.ROOT),
        color = ink,
        style = SelloTheme.type.stampSmall.copy(letterSpacing = 0.08.em),
        textAlign = TextAlign.Center
    )
    Text(
        year,
        color = ink,
        style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp),
        fontWeight = SelloTheme.type.weight.emphasis,
        modifier = Modifier.testTag(YEAR_CLOSE_PLATE_TAG)
    )
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawLine(
                    ink.copy(alpha = 0.7f),
                    Offset(0f, size.height / 2),
                    Offset(size.width, size.height / 2),
                    size.height,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                    )
                )
            }
    )
    when (state) {
        is YearCloseState.Ready -> YearFacts(state, ink)

        is YearCloseState.Failed -> Text(
            stringResource(R.string.year_close_failed, year),
            color = ink,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(YEAR_CLOSE_FAILED_TAG)
        )

        else -> Unit
    }
}

@Composable
private fun YearFacts(state: YearCloseState.Ready, ink: Color) {
    val facts = buildList {
        add(count(state.expenses) to pluralStringResource(R.plurals.year_close_expenses, state.expenses))
        add(
            count(state.recordedDays) to
                pluralStringResource(R.plurals.year_close_days, state.recordedDays)
        )
        if (state.limitedMonths > 0) {
            add(
                stringResource(
                    R.string.year_close_months_count,
                    count(state.withinMonths),
                    count(state.limitedMonths)
                ) to pluralStringResource(R.plurals.year_close_months, state.limitedMonths)
            )
        }
        if (state.goalsReached > 0) {
            add(
                count(state.goalsReached) to
                    pluralStringResource(R.plurals.year_close_goals, state.goalsReached)
            )
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = Modifier.fillMaxWidth().testTag(YEAR_CLOSE_FACTS_TAG)
    ) {
        facts.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (figure, label) ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(
                            figure,
                            color = ink,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            fontWeight = SelloTheme.type.weight.emphasis
                        )
                        Text(label, color = ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A count as Sello writes numbers: 1.240. */
private fun count(value: Int): String = DigitGrouping().shown(value.toString())

/**
 * The celebration's gold, for the card's ring and the chads on the band: light enough to
 * read on the band in either theme (no gold token).
 */
private fun celebrationGold(colors: SelloColors): Color =
    if (colors.isDark) colors.semantic.warn else colors.semantic.warnSoft

private const val SOFT = 0.85f
private const val FIRST_LANDING = 300
private const val LANDING_STEP = 100
private const val MONTHS_PER_ROW = 6
private val RING = 3.dp
private val CARD_RADIUS = 14.dp

const val RECIBO_YEAR_CARD_TAG = "recibo:year-card"
const val RECIBO_YEAR_CARD_DISMISS_TAG = "recibo:year-card:dismiss"
const val YEAR_CLOSE_PLATE_TAG = "year-close:plate"
const val YEAR_CLOSE_FACTS_TAG = "year-close:facts"
const val YEAR_CLOSE_FAILED_TAG = "year-close:failed"
