package com.atlassian.prosemirror.util

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.ResolvedPos

fun getMilliseconds() = System.currentTimeMillis()

fun Node.resolveAndLog(from: Int, to: Int): Pair<ResolvedPos, ResolvedPos>? {
  try {
    val resolvedFrom = resolve(from)
    val resolvedTo = resolve(to)
    return resolvedFrom to resolvedTo
  } catch (e: RangeError) {
    // It's safe to log Node.toString as UGC is not returned in Debug mode
    // TODO: add library to log
    return null
  }
}

fun Node.resolveAndLog(pos: Int): ResolvedPos? {
  try {
    return resolve(pos)
  } catch (e: RangeError) {
    // It's safe to log Node.toString as UGC is not returned in Debug mode
    // TODO: add library to log
    return null
  }
}
