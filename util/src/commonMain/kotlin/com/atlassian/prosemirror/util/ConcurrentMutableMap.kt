package com.atlassian.prosemirror.util

expect class ConcurrentMutableMap<Key : Any, Value : Any>() : MutableMap<Key, Value> {
    override val size: Int
    override val keys: MutableSet<Key>
    override val values: MutableCollection<Value>
    override fun isEmpty(): Boolean
    override fun containsKey(key: Key): Boolean
    override fun containsValue(value: Value): Boolean
    override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>
    override operator fun get(key: Key): Value?
    override fun put(key: Key, value: Value): Value?
    override fun remove(key: Key): Value?
    override fun putAll(from: Map<out Key, Value>)
    override fun clear()
}
