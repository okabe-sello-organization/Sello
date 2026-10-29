package com.software.sello.domain.port

import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CarryReplay
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CarryCorrection
import java.time.LocalDate
import java.time.YearMonth

/**
 * One category's chains and every month they carry, all read at one [revision] as of the
 * financial day [asOf] (ADR 0020 CP27).
 */
data class CarryHistory(
    val categoryId: CategoryId,
    val generation: Long,
    val revision: Long,
    val asOf: LocalDate,
    val chains: List<CarryChain>,
    val replay: CarryReplay
)

/**
 * What recording catch-up's payments changed in carry chains (ADR 0020 CP18), one entry per
 * category a listed payment reaches, read at one [revision] as of the financial day [asOf].
 * Derived from the listed payments that are still recorded: one deleted since is no longer
 * in it.
 */
data class CarryCatchUpNotice(
    val generation: Long,
    val revision: Long,
    val asOf: LocalDate,
    val categories: List<CarryCorrection>
)

/** Why a carry read has no answer. Never shown as zero carry or as the base alone (CP8). */
sealed interface CarryReadFailure {
    data class Storage(val failure: StorageFailure) : CarryReadFailure

    data class Carryover(val failure: CarryoverFailure) : CarryReadFailure

    data object CategoryMissing : CarryReadFailure
}

/**
 * Carry derived on every read from the recorded expenses, the base history and the chains
 * (ADR 0020 CP7), at the current financial day. Nothing here is stored or cached.
 */
interface CarryoverReads {
    /** [categoryId]'s chains and their months up to [through], which may not be after this month. */
    suspend fun history(
        categoryId: CategoryId,
        through: YearMonth
    ): Outcome<CarryHistory, CarryReadFailure>

    /** The CP18 notice of the payments catch-up recorded as [recorded]. */
    suspend fun catchUpNotice(
        recorded: List<ExpenseId>
    ): Outcome<CarryCatchUpNotice, CarryReadFailure>
}
