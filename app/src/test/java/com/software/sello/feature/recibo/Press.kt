package com.software.sello.feature.recibo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction

/** Presses a control through its accessibility action instead of by screen position. */
fun SemanticsNodeInteraction.press(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
