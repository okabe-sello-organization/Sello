package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/*
 * The parts restoring a backup, starting after a failure and erasing everything share:
 * the slips' heading and paragraphs, and the slip that says a history was restored.
 */

/** A restore or reset slip's heading, read as a heading. */
@Composable
fun RestoreHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * A paragraph of a restore or reset slip, in soft ink under what comes before it; not
 * [soft], in full ink, for the warning of a step that deletes data.
 */
@Composable
fun RestoreBody(text: String, soft: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (soft) SelloTheme.colors.inkSoft else SelloTheme.colors.ink,
        modifier = Modifier.padding(top = SelloTheme.spacing.xs)
    )
}

/**
 * The preferences a restore could not apply: [items], one bullet each, and [label] for the
 * button that applies them, busy while [applying]; [note] under it says what applying does.
 */
@Immutable
data class RestorePending(
    val items: List<String>,
    val label: String,
    val note: String,
    val applying: Boolean
)

/**
 * A replaced history: [title] and [body], with the landing [stamp] beside the title when
 * everything was applied; with [pending], the preferences still to apply and the button
 * that applies them ([onApply]). [note], when given, is one more line before the buttons.
 * [leaveLabel] leaves for the recibo, filled unless there is something to apply.
 */
@Composable
fun RestoredSlip(
    title: String,
    body: String,
    leaveLabel: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    stamp: String? = null,
    note: String? = null,
    pending: RestorePending? = null,
    onApply: () -> Unit = {}
) {
    Slip(modifier.fillMaxWidth(), pinked = false) {
        if (stamp != null) {
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RestoreHeading(title)
                Stamp(
                    text = stamp,
                    ink = StampInk.Good,
                    land = true,
                    modifier = Modifier.padding(vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        } else {
            RestoreHeading(title)
        }
        RestoreBody(body)
        pending?.let {
            BulletList(
                it.items.map { item -> Bullet(null, item) },
                Modifier.padding(top = SelloTheme.spacing.sm)
            )
        }
        note?.let { RestoreBody(it) }
        pending?.let {
            SelloButton(
                text = it.label,
                onClick = onApply,
                loading = it.applying,
                enabled = !it.applying,
                modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                    .testTag(RESTORE_APPLY_TAG)
            )
            Text(
                text = it.note,
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(top = SelloTheme.spacing.sm)
            )
        }
        SelloButton(
            text = leaveLabel,
            onClick = onLeave,
            kind = if (pending == null) ButtonKind.Filled else ButtonKind.Text,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                .testTag(RESTORE_LEAVE_TAG)
        )
    }
}

const val RESTORE_APPLY_TAG = "restore:apply"
const val RESTORE_LEAVE_TAG = "restore:leave"
