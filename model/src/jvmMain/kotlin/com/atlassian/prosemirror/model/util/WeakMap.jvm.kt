package com.atlassian.prosemirror.model.util

import java.util.WeakHashMap

class AndroidWeakMap<K, V> : WeakMap<K, V> {
    private val map = WeakHashMap<K, V>()

    override fun get(key: K): V? = map[key]

    override fun put(key: K, value: V) {
        map[key] = value
    }
}

actual fun <K, V> mutableWeakMapOf(): WeakMap<K, V> = AndroidWeakMap()
