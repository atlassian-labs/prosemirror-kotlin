package com.atlassian.prosemirror.util

import kotlin.math.max
import kotlin.math.min

fun String.slice(from: Int, to: Int): String {
    return substring(max(0, from), min(to, length))
}

/**
 * Removes elements from an array and, if necessary, inserts new elements in their place, returning the deleted
 * elements.
 * @param start The zero-based location in the array from which to start removing elements.
 * @param deleteCount The number of elements to remove.
 * @param items Elements to insert into the array in place of the deleted elements.
 * @returns An array containing the elements that were deleted.
 */
fun <E : Any> MutableList<E>.splice(start: Int, deleteCount: Int = 1, vararg items: E): List<E> {
    val result = subList(start, start + deleteCount)
    repeat(deleteCount) {
        removeAt(start)
    }
    addAll(items)
    return result
}
