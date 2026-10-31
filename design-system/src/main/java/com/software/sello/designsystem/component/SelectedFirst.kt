package com.software.sello.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The order of a row of choice chips: the chosen one first, so a choice made before the
 * person touches the row (asked for by the caller, a restored draft, the entry being
 * corrected) is always in view. Once they pick in the row the order stays put, so chips
 * never jump under their finger. Call [picked] from every chip's click.
 */
class SelectedFirstOrder<T> internal constructor(
    val items: List<T>,
    private val onPicked: () -> Unit
) {
    fun picked() = onPicked()
}

/** [items] with the one whose [key] is [selectedKey] moved to the front; see [SelectedFirstOrder]. */
@Composable
fun <T> rememberSelectedFirst(
    items: List<T>,
    selectedKey: Any?,
    key: (T) -> Any
): SelectedFirstOrder<T> {
    var settled by remember { mutableStateOf<Settled?>(null) }
    val front = settled.let { if (it != null) it.front else selectedKey }
    val ordered = remember(items, front) { selectedFirst(items, front, key) }
    return SelectedFirstOrder(ordered) { if (settled == null) settled = Settled(front) }
}

/** The front choice, kept from the person's first pick in the row on. */
private class Settled(val front: Any?)

/** [items] with the one whose [key] is [front] moved to the front, the rest in order. */
fun <T> selectedFirst(items: List<T>, front: Any?, key: (T) -> Any): List<T> {
    val index = if (front == null) -1 else items.indexOfFirst { key(it) == front }
    if (index <= 0) return items
    return listOf(items[index]) + items.subList(0, index) + items.subList(index + 1, items.size)
}
