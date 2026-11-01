package com.software.sello.feature.recibo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.software.sello.R
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.DateLabels
import java.time.LocalDate
import java.util.Locale

/**
 * One expense as a receipt stub: the day torn off at the start (weekday over the day of
 * the month, "hoy" for today), then the note and the amount. Read as one line: the note,
 * the whole day and the amount; one dated after [today] also says it does not count yet.
 */
@Composable
internal fun ExpenseStubLine(
    row: ExpenseRow,
    dates: DateLabels,
    today: LocalDate,
    modifier: Modifier
) {
    val colors = SelloTheme.colors
    val future = row.notCountedYet
    val day = dates.day(row.date, today)
    val weekday = if (row.date == today) {
        stringResource(R.string.category_detail_stub_today)
    } else {
        dates.weekday(row.date)
    }
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(STUB_WIDTH)
                // Read with the note, as the whole day, below; "HOY" and "21" alone say less.
                .clearAndSetSemantics {}
        ) {
            Text(
                weekday.uppercase(Locale.forLanguageTag("es-CO")),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.6.sp
                ),
                color = colors.inkSoft,
                maxLines = 1
            )
            Text(
                row.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.W900,
                    fontFeatureSettings = "tnum"
                ),
                color = if (future) colors.inkSoft else colors.ink
            )
        }
        // Where the stub tears off: the line of a ticket's perforation.
        val perforation = colors.rule
        Canvas(
            Modifier
                .padding(start = SelloTheme.spacing.sm)
                .width(2.dp)
                .fillMaxHeight()
        ) {
            drawLine(
                color = perforation,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            )
        }
        val note = row.note ?: stringResource(R.string.category_detail_no_note)
        val notCounted = stringResource(R.string.category_detail_not_counted_short)
        // One label on a node with a size: a size-less one never reaches a screen reader.
        val spoken = listOfNotNull(note, day, notCounted.takeIf { future }).joinToString(", ")
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = SelloTheme.spacing.md, vertical = SelloTheme.spacing.sm)
                .clearAndSetSemantics { text = AnnotatedString(spoken) }
        ) {
            Text(
                note,
                style = if (row.note != null) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
                },
                color = if (row.note != null) colors.ink else colors.inkSoft
            )
            if (future) {
                Text(
                    notCounted,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                    color = colors.semantic.warn,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(colors.semantic.warnSoft, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        MoneyText(row.amount, color = if (future) colors.inkSoft else Color.Unspecified)
    }
}

private val STUB_WIDTH = 38.dp
