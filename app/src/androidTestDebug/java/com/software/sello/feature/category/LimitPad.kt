package com.software.sello.feature.category

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import com.software.sello.press

/**
 * Types [limit], such as "300.000" or "300000", on the category form's Sello pad in place
 * of what was there: the backspace's "Borrar todo" first, then one key per digit.
 */
fun ComposeTestRule.typeLimit(limit: String) {
    val pad = hasAnyAncestor(hasTestTag(EDITOR_PAD_TAG))
    onNode(hasContentDescription("Borrar") and pad)
        .performSemanticsAction(SemanticsActions.OnLongClick)
    limit.filter { it.isDigit() }.forEach { digit ->
        onNode(hasText(digit.toString()) and pad).press()
    }
}
