package com.atlassian.prosemirror.model.util

// Implementation of WeakMap for JS, which is a map with weak keys
interface WeakMap<K, V> {
    fun getOrPut(key: K, defaultValue: () -> V): V {
        val value = get(key)
        return if (value == null) {
            val answer = defaultValue()
            put(key, answer)
            answer
        } else {
            value
        }
    }
    fun get(key: K): V?
    fun put(key: K, value: V)
}

expect fun <K, V> mutableWeakMapOf(): WeakMap<K, V>
