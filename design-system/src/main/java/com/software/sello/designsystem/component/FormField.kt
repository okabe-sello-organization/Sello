package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A labelled text input. It shows exactly what was typed: it does not trim, cut at a
 * limit or remove characters. The caller validates and passes back a [message]; an
 * error message also marks the field invalid for screen readers. [counter] is a
 * ready-made count such as `25/24`. [visualTransformation] changes only how the
 * value is drawn, never the value: see [DigitGrouping].
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: FieldMessage? = null,
    counter: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = SelloTheme.colors
    var focused by remember { mutableStateOf(false) }
    val isError = message?.isError == true
    val line = when {
        isError -> colors.semantic.loss
        focused -> colors.brand
        else -> colors.rule
    }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.inkSoft)
        // Text given to the field, such as a name being edited, puts the cursor after it,
        // so what is typed next is added to the end. Typing keeps its own cursor.
        val edited = rememberEditedText(value)
        BasicTextField(
            value = edited.field,
            onValueChange = { edited.edit(it)?.let(onValueChange) },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    if (isError) error(message.text)
                },
            decorationBox = { field ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = SelloTheme.spacing.minTouchTarget)
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            val y = size.height - stroke / 2
                            drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
                        }
                ) {
                    Box(modifier = Modifier.weight(1f)) { field() }
                    if (trailing != null) trailing()
                }
            }
        )
        if (message != null || counter != null) {
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    if (message != null) FieldMessageText(message)
                }
                if (counter != null) {
                    Text(
                        text = counter,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isError) colors.semantic.loss else colors.inkSoft
                    )
                }
            }
        }
    }
}
