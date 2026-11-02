package com.software.sello.feature.category

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.carry.CarryCorrectionList
import java.time.YearMonth

/**
 * What a category holds, as the person is told before deleting it (ADR 0024): its
 * [expenses] and their [total], the months of the oldest and newest one, and its fixed
 * payments. Every figure comes from one preview; none is guessed.
 */
@Immutable
data class RemovalCounts(
    val expenses: Int,
    val total: MoneyTextValue,
    val firstMonth: YearMonth?,
    val lastMonth: YearMonth?,
    val schedules: Int
)

/** A limit as a removal slip says it: an amount, or Sin límite. */
sealed interface RemovalLimit {
    data class Finite(val amount: MoneyTextValue) : RemovalLimit

    data object Unlimited : RemovalLimit
}

/**
 * The exact facts of moving into [target] in [month]: what it has spent and will have
 * spent, the deleted category's own limit that goes ([sourceLimit], null when it had none
 * that month) and [target]'s, which stays ([targetLimit], null when it has none).
 */
@Immutable
data class MoveFacts(
    val target: String,
    val month: YearMonth,
    val targetSpent: MoneyTextValue,
    val targetSpentAfter: MoneyTextValue,
    val sourceLimit: RemovalLimit?,
    val targetLimit: RemovalLimit?
)

/** A category the expenses can go to, with what Recibo's cell shows for it this month. */
@Immutable
data class MoveTarget(
    val id: String,
    val name: String,
    val iconKey: String,
    val amount: MoneyTextValue
)

/** Where deleting the category stands: the slip or sheet on screen. */
sealed interface RemovalStep {
    /** Its contents are being counted; nothing is offered before they are. */
    data object Counting : RemovalStep

    /** Nothing but the category and its limits: one confirmation. */
    data object Empty : RemovalStep

    /** It holds expenses or fixed payments: move them, delete them all, or archive instead. */
    data class Choose(val counts: RemovalCounts, val canArchive: Boolean) : RemovalStep

    /**
     * The categories that can take the expenses, in [month]. [targets] is null while they
     * are read, and [failed] when they could not be.
     */
    data class Pick(
        val counts: RemovalCounts,
        val month: YearMonth,
        val targets: List<MoveTarget>?,
        val failed: Boolean = false
    ) : RemovalStep

    data class Move(val counts: RemovalCounts, val facts: MoveFacts) : RemovalStep

    /** Delete it all: [typed] is what was written to confirm, kept exactly. */
    data class All(val counts: RemovalCounts, val typed: String) : RemovalStep {
        /** As reset reads its word: the exact word, ignoring outer spaces. */
        val canDelete: Boolean get() = typed.trim() == CONFIRM_WORD
    }

    /** Moving changes [target]'s carry chain: the list of later months first (ADR 0020 CP20). */
    data class Correction(val target: String, val list: CarryCorrectionList, val changed: Boolean) :
        RemovalStep

    /**
     * Whether the deletion was written is not known: it is being checked with the same
     * operation. [failed] when the last check could not answer; the person asks again.
     */
    data class Checking(val failed: Boolean) : RemovalStep

    /** Nothing was deleted or moved, why, and the way forward. */
    data class Problem(val problem: RemovalProblem) : RemovalStep

    companion object {
        const val CONFIRM_WORD = "BORRAR"
    }
}

/** Why the deletion did not happen. */
sealed interface RemovalProblem {
    /** The category no longer holds what was shown: [now] is what it holds. */
    data class Changed(val before: RemovalCounts?, val now: RemovalCounts) : RemovalProblem

    /** The category chosen to receive the expenses was archived or deleted meanwhile. */
    data class TargetClosed(val target: String) : RemovalProblem

    /** Storage refused or failed; [counts], when known, are what the category still holds. */
    data class NotSaved(val counts: RemovalCounts?) : RemovalProblem

    /** Its contents could not be counted, so no deletion is offered. */
    data object CannotCount : RemovalProblem
}

/** Deleting the category [name], shown over the editor. Nothing can be pressed while [busy]. */
@Immutable
data class RemovalState(val name: String, val step: RemovalStep, val busy: Boolean = false)
