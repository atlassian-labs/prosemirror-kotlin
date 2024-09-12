package com.atlassian.prosemirror.util

import kotlin.math.max
import kotlin.math.min

/**
 * When enabled, it may overlook certain exceptions and disregard the changes that lead to the error.
 */
var safeMode = true

/**
 * When enabled, the toString methods and exception messages may include user-generated content.
 */
var verbose = false

fun String.slice(
    from: Int,
    to: Int,
): String {
    val start = max(0, from)
    return substring(start, max(start, min(to, length)))
}
