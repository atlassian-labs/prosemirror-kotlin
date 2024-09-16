package com.atlassian.prosemirror.model.util

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.ResolvedPos
import com.atlassian.prosemirror.util.safeMode

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
