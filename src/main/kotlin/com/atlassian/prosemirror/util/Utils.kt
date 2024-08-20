package com.atlassian.prosemirror.util

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.ResolvedPos

fun getMilliseconds() = System.currentTimeMillis()

fun Node.resolveSafe(from: Int, to: Int): Pair<ResolvedPos, ResolvedPos>? {
    try {
        val resolvedFrom = resolve(from)
        val resolvedTo = resolve(to)
        return resolvedFrom to resolvedTo
    } catch (e: RangeError) {
        if (!safeMode) throw e
        return null
    }
}

fun Node.resolveSafe(pos: Int): ResolvedPos? {
    try {
        return resolve(pos)
    } catch (e: RangeError) {
        if (!safeMode) throw e
        return null
    }
}
