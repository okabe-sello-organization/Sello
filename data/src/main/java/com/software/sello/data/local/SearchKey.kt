package com.software.sello.data.local

import java.text.Normalizer
import java.util.Locale

/**
 * What expense search compares (SELLO-165): the text with every accent and other mark
 * taken off and in lower case, so "\u00d1AME \u00c1REA" and "name area" read the same.
 * A note and a query are folded the same way. Derived only: never backed up, rebuilt
 * whenever an expense is written.
 */
internal object SearchKey {
    private val marks = Regex("\\p{M}+")

    fun of(text: String?): String = if (text.isNullOrEmpty()) {
        ""
    } else {
        Normalizer.normalize(text, Normalizer.Form.NFKD).replace(marks, "").lowercase(Locale.ROOT)
    }
}
