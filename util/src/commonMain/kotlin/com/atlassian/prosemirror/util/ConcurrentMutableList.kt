package com.atlassian.prosemirror.util

expect class ConcurrentMutableList<T : Any>() : MutableList<T> {
    override val size: Int
    override fun isEmpty(): Boolean
    override fun contains(element: T): Boolean
    override fun containsAll(elements: Collection<T>): Boolean
    override fun get(index: Int): T
    override fun indexOf(element: T): Int
    override fun lastIndexOf(element: T): Int
    override fun iterator(): MutableIterator<T>
    override fun listIterator(): MutableListIterator<T>
    override fun listIterator(index: Int): MutableListIterator<T>
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T>
    override fun add(element: T): Boolean
    override fun add(index: Int, element: T)
    override fun addAll(elements: Collection<T>): Boolean
    override fun addAll(index: Int, elements: Collection<T>): Boolean
    override fun remove(element: T): Boolean
    override fun removeAt(index: Int): T
    override fun removeAll(elements: Collection<T>): Boolean
    override fun retainAll(elements: Collection<T>): Boolean
    override fun clear()
    override fun set(index: Int, element: T): T
}
