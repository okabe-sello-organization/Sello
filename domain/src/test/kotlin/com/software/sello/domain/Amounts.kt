package com.software.sello.domain

import com.software.sello.domain.model.Money
import com.software.sello.domain.model.TransactionAmount

/** A whole-peso amount as a record carries it. */
fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()
