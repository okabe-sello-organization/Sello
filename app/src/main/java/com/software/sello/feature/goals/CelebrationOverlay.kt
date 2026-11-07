package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.CelebrationAction
import com.software.sello.designsystem.component.CelebrationMoment
import com.software.sello.designsystem.component.CelebrationStamp
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import java.util.Locale

/** When the four stamps land, from the locked reference (docs/design/system/features/goals.html). */
private val LandAt = listOf(300, 650, 1000, 1400)

/**
 * Marcar como lograda's big moment, presentation only: the host opens this after a
 * confirmed completion, never during submission. Green floods out of the Meta stamp. With
 * reduced motion it opens on the final screen, without chads.
 */
@Composable
internal fun CelebrationOverlay(
    title: String,
    plateTitle: String,
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    detail: String,
    places: List<MilestoneSpec>,
    doneLabel: String,
    viewLabel: String,
    onDone: () -> Unit,
    onView: () -> Unit
) {
    val colors = SelloTheme.colors
    val onGain = colors.semantic.onGain
    CelebrationMoment(
        title = title,
        stamps = places.map { CelebrationStamp(it) },
        landAtMillis = LandAt,
        flood = colors.semantic.gain,
        onFlood = onGain,
        // The white chads are the band's own ink, white in every palette.
        chadColors = listOf(
            colors.onBand,
            colors.semantic.warn,
            colors.brandSoft,
            colors.onBand,
            colors.brand
        ),
        actionTone = ButtonTone.OnGain,
        primary = CelebrationAction(doneLabel, onDone),
        secondary = CelebrationAction(viewLabel, onView, SelloIcon.ChevronRight),
        onDismiss = onDone
    ) {
        Plate(plateTitle, name, icon, amount, detail, onGain)
    }
}

/** Inside the plate: the goal, its amount between dashed rules, the day and the aportes. */
@Composable
private fun Plate(
    plateTitle: String,
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    detail: String,
    onGain: Color
) {
    val stencil = SelloTheme.type.stampSmall
    val digits = amount.digits.count { it.isDigit() }
    val amountSize = when {
        digits > 9 -> AmountSizeLong
        digits > 6 -> AmountSizeMid
        else -> AmountSize
    }
    Text(
        plateTitle.uppercase(Locale.ROOT),
        color = onGain,
        style = stencil.copy(letterSpacing = PlateTitleTracking),
        textAlign = TextAlign.Center
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        Icon(icon.painter(), null, Modifier.size(24.dp), tint = onGain)
        Text(
            name,
            Modifier.weight(1f, fill = false),
            color = onGain,
            fontWeight = SelloTheme.type.weight.emphasis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = PlateNameSize,
                lineHeight = PlateNameLine
            )
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val line = 2.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                drawLine(
                    onGain,
                    Offset(0f, line / 2),
                    Offset(size.width, line / 2),
                    line,
                    pathEffect = dash
                )
                drawLine(
                    onGain,
                    Offset(0f, size.height - line / 2),
                    Offset(size.width, size.height - line / 2),
                    line,
                    pathEffect = dash
                )
            }
            .padding(top = SelloTheme.spacing.sm, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        MoneyText(
            amount,
            color = onGain,
            singleLine = true,
            textStyle = MaterialTheme.typography.displayMedium.copy(
                fontSize = amountSize,
                lineHeight = amountSize * 1.05f
            )
        )
    }
    Text(
        detail.uppercase(Locale.ROOT),
        color = onGain,
        style = stencil.copy(fontSize = PlateDetailSize, letterSpacing = PlateDetailTracking),
        textAlign = TextAlign.Center
    )
}

// The goal plate's one-off type: no token covers it.
private val AmountSize = 48.sp
private val AmountSizeMid = 37.sp
private val AmountSizeLong = 29.sp
private val PlateNameSize = 18.sp
private val PlateNameLine = 22.sp
private val PlateDetailSize = 13.sp
private val PlateTitleTracking = 0.14.em
private val PlateDetailTracking = 0.08.em
