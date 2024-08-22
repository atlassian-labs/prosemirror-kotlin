package com.atlassian.prosemirror.util

import kotlin.math.max
import kotlin.math.min

var safeMode = true

fun String.slice(from: Int, to: Int): String {
    return substring(max(0, from), min(to, length))
}
