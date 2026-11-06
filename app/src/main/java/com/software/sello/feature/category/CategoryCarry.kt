package com.software.sello.feature.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ChangeRow
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.signedText
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.carry.CarryCorrectionContent
import com.software.sello.presentation.carry.CarryCorrectionList
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth

/**
 * The carry setting of the month in progress (ADR 0020 CP1–CP4, CP9, CP28): [Off] with the
 * month it would start in, or [On] since a month with this month's carry and capacity.
 * [Unreadable] when the month could not be read: the setting is never guessed.
 */
sealed interface CarryCard {
    data class Off(val month: YearMonth) : CarryCard

    data class On(
        val since: YearMonth,
        val base: MoneyTextValue,
        val carry: MoneyTextValue,
        val carryMagnitude: MoneyTextValue,
        val direction: CarryDirection,
        val capacity: MoneyTextValue
    ) : CarryCard

    data object Unreadable : CarryCard
}

/** Why carry did not change, said in the card with what to do (CP3, CP13, CP23, CP25). */
enum class CarryProblemText {
    /** The default after this month is not finite. */
    DefaultAfterNotFinite,

    UnlimitedThisMonth,

    /** Paused by archiving, or never configured, this month. */
    NoLimitThisMonth,

    Archived,

    /** The base is above 999.999.999.999 (CP25). */
    BaseAboveLimit,

    /** A limit above 999.999.999.999 while the category carries (CP25). */
    LimitAboveCap,

    /** A past month inside a chain cannot become unlimited (CP13). */
    MonthUnlimited,

    /** The change would leave a month whose carry cannot be calculated (CP23). */
    Unavailable,

    /** A balance nobody can calculate cannot be dropped (CP9). */
    DropUnavailable
}

/** What turning carry off, archiving or Sin límite does to this month's balance (CP10). */
enum class DropReason { Disable, Archive, Unlimited }

/**
 * A balance to drop, as the month was read or the save answered it: [carry] signed, the base, and this
 * month's capacity and remaining before and after. [changedFrom] is the balance the
 * person saw before, when it moved meanwhile.
 */
data class DropFigures(
    val month: YearMonth,
    val carry: MoneyTextValue,
    val base: MoneyTextValue,
    val capacityBefore: MoneyTextValue,
    val capacityAfter: MoneyTextValue,
    val remainingBefore: MoneyTextValue,
    val remainingAfter: MoneyTextValue,
    val changedFrom: MoneyTextValue? = null
)

/** A question about carry that waits for an answer. */
sealed interface CarryQuestion {
    /** Turning carry on from [month]; [spent] is what the month already has, if anything. */
    data class Enable(val month: YearMonth, val spent: MoneyTextValue?) : CarryQuestion

    data class Drop(val reason: DropReason, val figures: DropFigures) : CarryQuestion

    /** A past month's new limit changes later months (CP20); [changed] after SC34. */
    data class Correction(
        val month: YearMonth,
        val before: MoneyTextValue?,
        val after: MoneyTextValue?,
        val list: CarryCorrectionList,
        val changed: Boolean
    ) : CarryQuestion
}

/**
 * The carry switch at the foot of the Límite mensual slip, under a tear line: the setting,
 * what it carries this month and what it said last. Flipping it asks first when there is
 * something to confirm, then only changes the draft: the switch shows the draft and says
 * that Guardar saves it (SELLO-188).
 */
@Composable
internal fun CarrySwitch(state: CategoryEditorState, onAction: (CategoryEditorAction) -> Unit) {
    val card = state.carry ?: return
    val dates = dateLabels()
    Column(Modifier.fillMaxWidth().testTag(EDITOR_CARRY_TAG)) {
        TearLine(verticalMargin = SelloTheme.spacing.md)
        when (card) {
            CarryCard.Unreadable -> {
                Text(
                    stringResource(R.string.carry_setting),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(R.string.carry_read_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = SelloTheme.colors.semantic.loss
                )
                // A change still in the draft is never saved unseen.
                state.carryPending?.let { pending ->
                    Text(
                        pendingText(pending),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag(EDITOR_CARRY_PENDING_TAG)
                    )
                }
                // A save sent from here can still be refused for carry: said here too.
                CarryMessages(state)
            }

            else -> {
                val pending = state.carryPending
                val on = pending?.on ?: (card is CarryCard.On)
                val setting = if (pending != null) {
                    pendingText(pending)
                } else if (card is CarryCard.On) {
                    stringResource(
                        R.string.carry_on_summary,
                        dates.monthYear(card.since),
                        card.carry.signedText(),
                        card.capacity.digits
                    )
                } else {
                    stringResource(R.string.carry_setting_help)
                }
                val spoken = if (pending == null && card is CarryCard.On) {
                    stringResource(
                        R.string.carry_on_summary_spoken,
                        dates.monthYear(card.since),
                        card.carry.spoken,
                        card.capacity.spoken
                    )
                } else {
                    setting
                }
                SwitchRow(
                    title = stringResource(R.string.carry_setting),
                    checked = on,
                    onCheckedChange = {
                        onAction(
                            if (on) {
                                CategoryEditorAction.DisableCarry
                            } else {
                                CategoryEditorAction.EnableCarry
                            }
                        )
                    },
                    modifier = Modifier.testTag(EDITOR_CARRY_SWITCH_TAG),
                    subtitle = setting,
                    enabled = state.canChangeCarry,
                    spokenSubtitle = spoken
                )
                CarryMessages(state)
            }
        }
    }
}

/** What Guardar will do to carry: on from its month, or off and what that forgets. */
@Composable
private fun pendingText(pending: CarryPending): String {
    val forgets = pending.forgets
    return when {
        pending.on -> stringResource(
            R.string.carry_pending_on,
            monthNames().inSentence(pending.month)
        )

        forgets != null -> stringResource(R.string.carry_pending_off_forgets, forgets.signedText())

        else -> stringResource(R.string.carry_pending_off)
    }
}

/** The last refusal about carry, read out as it appears. */
@Composable
private fun CarryMessages(state: CategoryEditorState) {
    val name = state.name
    val month = state.targetMonth ?: return
    state.carryProblem?.let { problem ->
        val text = carryProblemText(problem, name, month)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.semantic.loss,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .testTag(EDITOR_CARRY_PROBLEM_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
internal fun carryProblemText(problem: CarryProblemText, name: String, month: YearMonth): String {
    val months = monthNames()
    return when (problem) {
        CarryProblemText.DefaultAfterNotFinite -> stringResource(
            R.string.carry_refused_default,
            name,
            months.name(month),
            months.inSentence(month.plusMonths(1))
        )

        CarryProblemText.UnlimitedThisMonth -> stringResource(
            R.string.carry_refused_unlimited,
            name
        )

        CarryProblemText.NoLimitThisMonth ->
            stringResource(R.string.carry_refused_no_limit, name, months.inSentence(month))

        CarryProblemText.Archived -> stringResource(R.string.carry_refused_archived, name)

        CarryProblemText.BaseAboveLimit -> stringResource(R.string.carry_refused_cap)

        CarryProblemText.LimitAboveCap -> stringResource(R.string.carry_limit_cap, name)

        CarryProblemText.MonthUnlimited ->
            stringResource(R.string.carry_month_unlimited, months.name(month), name)

        CarryProblemText.Unavailable -> stringResource(R.string.carry_unavailable_save, name)

        CarryProblemText.DropUnavailable -> stringResource(R.string.carry_unavailable_drop, name)
    }
}

/** The confirmation a carry question needs, answered with the action it names. */
@Composable
internal fun CarryQuestionSlip(
    state: CategoryEditorState,
    question: CarryQuestion,
    onAction: (CategoryEditorAction) -> Unit
) {
    val months = monthNames()
    val dates = dateLabels()
    val confirm = { onAction(CategoryEditorAction.CarryConfirmed) }
    val dismiss = { onAction(CategoryEditorAction.CarryDismissed) }
    when (question) {
        is CarryQuestion.Enable -> ConfirmSlip(
            title = stringResource(
                R.string.carry_enable_title,
                state.name,
                months.inSentence(question.month)
            ),
            confirmLabel = stringResource(R.string.carry_enable_confirm),
            onConfirm = confirm,
            onDismiss = dismiss,
            modifier = Modifier.testTag(EDITOR_CARRY_QUESTION_TAG),
            destructive = false,
            busy = state.busy
        ) {
            val month = months.inSentence(question.month)
            Text(
                question.spent?.let { stringResource(R.string.carry_enable_body, month, it.digits) }
                    ?: stringResource(R.string.carry_enable_body_none, month),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(
                    R.string.carry_enable_after,
                    months.inSentence(question.month.plusMonths(1))
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = SelloTheme.spacing.sm)
            )
        }

        is CarryQuestion.Drop -> {
            val figures = question.figures
            ConfirmSlip(
                title = stringResource(
                    if (figures.carry.sign == MoneySign.Minus) {
                        R.string.carry_drop_title_deficit
                    } else {
                        R.string.carry_drop_title_surplus
                    },
                    figures.carry.digits
                ),
                confirmLabel = stringResource(
                    when (question.reason) {
                        DropReason.Disable -> R.string.carry_drop_confirm
                        DropReason.Archive -> R.string.carry_drop_confirm_archive
                        DropReason.Unlimited -> R.string.carry_drop_confirm_unlimited
                    }
                ),
                onConfirm = confirm,
                onDismiss = dismiss,
                modifier = Modifier.testTag(EDITOR_CARRY_QUESTION_TAG),
                // Forgetting a carried balance is not undone by turning carry on (M03-3).
                destructive = true,
                busy = state.busy
            ) {
                figures.changedFrom?.let {
                    Text(
                        stringResource(R.string.carry_drop_changed, it.signedText()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SelloTheme.colors.semantic.warn,
                        modifier = Modifier.padding(
                            bottom = SelloTheme.spacing.sm
                        ).testTag(EDITOR_CARRY_CHANGED_TAG)
                    )
                }
                Text(
                    when (question.reason) {
                        DropReason.Disable -> stringResource(R.string.carry_drop_body)

                        DropReason.Archive -> stringResource(
                            R.string.carry_drop_body_archive,
                            state.name
                        )

                        DropReason.Unlimited -> stringResource(
                            R.string.carry_drop_body_unlimited,
                            state.name,
                            months.inSentence(figures.month)
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Column(
                    Modifier.padding(top = SelloTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val unlimited = question.reason == DropReason.Unlimited
                    val none = stringResource(R.string.carry_drop_unlimited_after)
                    BeforeAfter(
                        stringResource(R.string.carry_capacity),
                        figures.capacityBefore,
                        figures.capacityAfter.takeUnless { unlimited },
                        none
                    )
                    BeforeAfter(
                        stringResource(R.string.carry_drop_remaining),
                        figures.remainingBefore,
                        figures.remainingAfter.takeUnless { unlimited },
                        none
                    )
                    if (!unlimited) {
                        val base = stringResource(R.string.carry_base)
                        val same =
                            stringResource(R.string.carry_drop_base_same, figures.base.signedText())
                        ChangeRow(base, same, "$base: ${figures.base.spoken}")
                    }
                }
            }
        }

        is CarryQuestion.Correction -> ConfirmSlip(
            title = stringResource(
                R.string.carry_correct_limit_title,
                months.inSentence(question.month)
            ),
            confirmLabel = stringResource(R.string.carry_correct_save),
            onConfirm = confirm,
            onDismiss = dismiss,
            modifier = Modifier.testTag(EDITOR_CARRY_QUESTION_TAG),
            destructive = false,
            busy = state.busy
        ) {
            val what = if (question.before != null && question.after != null) {
                stringResource(
                    R.string.carry_correct_limit_what,
                    dates.monthYear(question.month),
                    question.before.signedText(),
                    question.after.signedText()
                )
            } else {
                null
            }
            CarryCorrectionContent(question.list, question.changed, what)
        }
    }
}

@Composable
private fun BeforeAfter(
    label: String,
    before: MoneyTextValue,
    after: MoneyTextValue?,
    none: String
) {
    val value = stringResource(
        R.string.carry_list_change,
        before.signedText(),
        after?.signedText() ?: none
    )
    ChangeRow(
        label,
        value,
        stringResource(R.string.carry_drop_row_spoken, label, before.spoken, after?.spoken ?: none)
    )
}

const val EDITOR_CARRY_TAG = "editor:carry"
const val EDITOR_CARRY_SWITCH_TAG = "editor:carry.switch"
const val EDITOR_CARRY_PROBLEM_TAG = "editor:carry.problem"
const val EDITOR_CARRY_PENDING_TAG = "editor:carry.pending"
const val EDITOR_CARRY_QUESTION_TAG = "editor:carry.question"
const val EDITOR_CARRY_CHANGED_TAG = "editor:carry.changed"
