package com.atlassian.prosemirror.util

import kotlin.math.max
import kotlin.math.min

var safeMode = true

fun String.slice(from: Int, to: Int): String {
    val start = max(0, from)
    return substring(start, max(start, min(to, length)))
}
