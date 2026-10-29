package com.software.sello.data.mapper

import com.software.sello.data.local.entity.CarryChainEntity
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates

internal fun CarryChain.toEntity() = CarryChainEntity(
    categoryId = categoryId.value,
    startMonth = EffectiveDates.storedText(start),
    endMonth = end?.let { EffectiveDates.storedText(it) }
)

/** A chain that ends in or before its start month is a damaged row, never repaired. */
internal fun CarryChainEntity.toDomain(): Outcome<CarryChain, StorageFailure.Integrity> =
    decodeRow(CarryChainEntity.TABLE, "$categoryId/$startMonth") {
        val category = valid("category_id", CategoryId.of(categoryId))
        val start = valid("start_month", EffectiveDates.parseMonth(startMonth))
        val end = endMonth?.let { valid("end_month", EffectiveDates.parseMonth(it)) }
        valid("end_month", CarryChain.of(category, start, end))
    }
