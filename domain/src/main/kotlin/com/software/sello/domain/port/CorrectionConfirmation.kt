package com.software.sello.domain.port

/**
 * What a person confirmed when they saw the list of later months a correction changes (ADR
 * 0020 CP20–CP22): that list was read at [revision] for exactly one change, which [change]
 * names, an identity of the command without its confirmation that storage gives out with the
 * list. It is accepted only by that same change at that same revision: any other change, or
 * anything committed in between, is refused and shown its own list.
 */
data class CorrectionConfirmation(val revision: Long, val change: String)
