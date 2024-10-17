package com.atlassian.prosemirror.model.util

import platform.Foundation.NSMapTable

class IOSWeakMap<K, V>: WeakMap<K, V> {
    private val map = NSMapTable.weakToStrongObjectsMapTable()

    override fun get(key: K): V? = map.objectForKey(key) as? V

    override fun put(key: K, value: V) {
        map.setObject(value, key)
    }

}

actual fun <K, V> mutableWeakMapOf(): WeakMap<K, V> = IOSWeakMap()
