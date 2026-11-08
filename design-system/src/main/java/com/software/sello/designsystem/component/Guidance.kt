package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The **?** right after a card's title, which opens that card's [HelpSheet]: a solid soft
 * disc in `paperDim` with a dark glyph and no ring, quieter than anything brand-coloured.
 * The disc is 20 dp at every font size and takes only that room beside the title;
 * Compose widens its touch area to 48 dp around it. It sits `spacing.sm` after the title.
 * [contentDescription] names what it explains, such as "Cómo leer este resumen", and
 * TalkBack reads it as a button. A card title takes this and never a [HelpTerm].
 */
@Composable
fun CardHelp(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    // The glyph is part of the disc, like an icon: it keeps its size at every font size.
    val glyph = with(LocalDensity.current) { HelpGlyphSize.toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(HelpDiscSize)
            .clip(CircleShape)
            .background(colors.paperDim)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
            }
    ) {
        Text(
            "?",
            color = colors.ink,
            style = TextStyle(
                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = glyph,
                lineHeight = glyph,
                fontWeight = FontWeight.W800
            )
        )
    }
}

/**
 * A word whose meaning is one tap away, such as "sobrante" in a line of figures: the
 * [text] with a dotted underline in the outline grey, lighter than a [CardHelp]. A tap
 * calls [onClick], which opens its explanation. TalkBack reads the word, then
 * "Qué significa", as one button. [style] and [color] default to the text around it.
 *
 * It is its own piece of text, so it suits a short label in a row, such as "por día"
 * beside a figure, not a word in the middle of a sentence: it cannot flow with the words
 * around it. Compose widens its touch area to 48 dp.
 */
@Composable
fun HelpTerm(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val outline = SelloTheme.colors.outline
    val description = stringResource(R.string.sello_help_term, text)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
            }
            .padding(bottom = HelpUnderlineGap)
            .drawBehind {
                val result = layout ?: return@drawBehind
                val stroke = HelpUnderlineWidth.toPx()
                val dots = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 1.5f))
                // Each line of a wrapped term carries its own underline.
                for (line in 0 until result.lineCount) {
                    val y = result.getLineBottom(line) + HelpUnderlineGap.toPx() - stroke / 2
                    drawLine(
                        color = outline,
                        start = Offset(result.getLineLeft(line), y),
                        end = Offset(result.getLineRight(line), y),
                        strokeWidth = stroke,
                        pathEffect = dots
                    )
                }
            }
    )
}

/** One entry of a [HelpSheet]: a [term] in bold and its plain [explanation] under it. */
data class HelpEntry(val term: String, val explanation: String)

/**
 * The explanation a [CardHelp] or a [HelpTerm] opens: a [SelloSheet] with [title], each
 * of [entries] as a bold term over its plain explanation, an optional worked [example]
 * on `paperAlt`, and, with [onMore], the link "Ver más en la Guía de Sello". The scrim
 * calls [onDismiss]. As every Sello sheet, the screen shows it while it is open, keeps
 * that in its State so it survives a restart, and owns Back.
 */
@Composable
fun HelpSheet(
    title: String,
    entries: List<HelpEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    example: String? = null,
    onMore: (() -> Unit)? = null
) {
    val colors = SelloTheme.colors
    SelloSheet(title = title, onDismiss = onDismiss, modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            modifier = Modifier.padding(horizontal = SheetTextInset)
        ) {
            entries.forEach { entry ->
                // Term and explanation are read together.
                Column(
                    Modifier.clearAndSetSemantics {
                        contentDescription = "${entry.term}. ${entry.explanation}"
                    }
                ) {
                    Text(
                        entry.term,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = SelloTheme.type.weight.emphasis,
                        color = colors.ink
                    )
                    Text(
                        entry.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSoft
                    )
                }
            }
            if (example != null) {
                Text(
                    example,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SelloTheme.shapes.inset)
                        .background(colors.paperAlt)
                        .padding(SelloTheme.spacing.md)
                )
            }
        }
        if (onMore != null) {
            SelloButton(
                text = stringResource(R.string.sello_help_more),
                onClick = onMore,
                kind = ButtonKind.Text,
                trailingIcon = SelloIcon.ChevronRight,
                contentPadding = HelpLinkPadding,
                modifier = Modifier.padding(top = SelloTheme.spacing.xs)
            )
        }
    }
}

/** An action in a [NudgeRow]: its [label] and what a tap does. */
class NudgeAction(val label: String, val onClick: () -> Unit)

/**
 * A quiet line in the page that points at something useful, such as where the Guía de
 * Sello lives or that a backup is due: never floating, never covering anything. Plain
 * paper with an [icon] and one or two lines of [text] in `inkSoft` (parts in bold may
 * take `ink`). Any of: [onClose], a ✕ that closes it, named [closeLabel] for TalkBack;
 * [link], a quiet text link on the left, such as "No volver a mostrar"; and [pill], a
 * small tonal pill on the right that leads to the thing itself. With no link or pill it
 * is the "¿Dudas?" row; with them, the backup reminder. Whether it shows is the caller's.
 */
@Composable
fun NudgeRow(
    icon: SelloIcon,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    closeLabel: String = stringResource(R.string.sello_close),
    link: NudgeAction? = null,
    pill: NudgeAction? = null
) {
    val colors = SelloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(SelloTheme.shapes.inset)
            .background(colors.paper)
            .padding(
                start = SelloTheme.spacing.md,
                end = if (onClose == null) SelloTheme.spacing.md else 0.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        Icon(
            icon.painter(),
            contentDescription = null,
            tint = colors.inkSoft,
            modifier = Modifier.padding(top = NudgeTextTop).size(NudgeIconSize)
        )
        Column(Modifier.weight(1f).padding(vertical = NudgeTextTop)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            if (link != null || pill != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (link != null) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                                .clickable(role = Role.Button, onClick = link.onClick)
                        ) {
                            Text(
                                link.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.outline
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (pill != null) {
                        NudgePill(pill)
                    }
                }
            }
        }
        if (onClose != null) {
            SelloIconButton(
                icon = SelloIcon.Close,
                contentDescription = closeLabel,
                onClick = onClose,
                tint = colors.brand,
                iconSize = NudgeIconSize
            )
        }
    }
}

/**
 * A [NudgeRow]'s way to the thing itself: a small tonal pill, `brandSoft` with its label
 * in `onBrandSoft`, smaller than a [BandChip] so it stays quiet. Compose widens its touch
 * area to 48 dp.
 */
@Composable
private fun NudgePill(action: NudgeAction) {
    val colors = SelloTheme.colors
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.brandSoft)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = SelloTheme.spacing.md, vertical = NudgePillVertical)
    ) {
        Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = SelloTheme.type.weight.emphasis,
            color = colors.onBrandSoft
        )
    }
}

/** [NudgeRow] with plain [text]. */
@Composable
fun NudgeRow(
    icon: SelloIcon,
    text: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    closeLabel: String = stringResource(R.string.sello_close),
    link: NudgeAction? = null,
    pill: NudgeAction? = null
) {
    NudgeRow(icon, AnnotatedString(text), modifier, onClose, closeLabel, link, pill)
}

private val HelpDiscSize = 20.dp
private val HelpGlyphSize = 12.dp
private val HelpUnderlineWidth = 1.5.dp
private val HelpUnderlineGap = 1.dp
private val NudgeIconSize = 18.dp
private val NudgePillVertical = 6.dp

// Lines up with the sheet's title, which SelloSheet insets by as much.
private val SheetTextInset = 2.dp

// The text's first line sits level with the ✕ in its 48 dp target.
private val NudgeTextTop = 14.dp

// The link's words start where the entries' do.
private val HelpLinkPadding: PaddingValues
    @Composable @ReadOnlyComposable
    get() = PaddingValues(
        start = SheetTextInset,
        top = SelloTheme.spacing.sm,
        end = SelloTheme.spacing.sm,
        bottom = SelloTheme.spacing.sm
    )
