package com.atlassian.prosemirror.util

import java.util.concurrent.ConcurrentHashMap

actual class ConcurrentMutableMap<Key : Any, Value : Any> : MutableMap<Key, Value> {
    private val map = ConcurrentHashMap<Key, Value>()
    actual override val size: Int
        get() = map.size
    actual override val keys: MutableSet<Key>
        get() = map.keys
    actual override val values: MutableCollection<Value>
        get() = map.values

    actual override fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    actual override fun containsKey(key: Key): Boolean {
        return map.containsKey(key)
    }

    actual override fun containsValue(value: Value): Boolean {
        return map.containsValue(value)
    }

    actual override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>
        get() = map.entries

    actual override operator fun get(key: Key): Value? {
        return map[key]
    }

    actual override fun put(key: Key, value: Value): Value? {
        return map.put(key, value)
    }

    actual override fun remove(key: Key): Value? {
        return map.remove(key)
    }

    actual override fun putAll(from: Map<out Key, Value>) {
        map.putAll(from)
    }

    actual override fun clear() {
        map.clear()
    }
}
