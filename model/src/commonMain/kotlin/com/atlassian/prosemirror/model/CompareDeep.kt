package com.atlassian.prosemirror.model

fun compareDeep(a: Any, b: Any): Boolean {
    return a == b // not sure if we need that if how to do that without reflection
}
