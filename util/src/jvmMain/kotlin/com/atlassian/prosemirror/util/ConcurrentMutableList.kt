package com.atlassian.prosemirror.util

import java.util.Collections

actual class ConcurrentMutableList<T : Any> : MutableList<T> {
    private val list = Collections.synchronizedList(mutableListOf<T>())

    actual override val size: Int
        get() = list.size

    actual override fun isEmpty(): Boolean {
        return list.isEmpty()
    }

    actual override fun contains(element: T): Boolean {
        return list.contains(element)
    }

    actual override fun containsAll(elements: Collection<T>): Boolean {
        return list.containsAll(elements)
    }

    actual override fun get(index: Int): T {
        return list[index]
    }

    actual override fun indexOf(element: T): Int {
        return list.indexOf(element)
    }

    actual override fun lastIndexOf(element: T): Int {
        return list.lastIndexOf(element)
    }

    actual override fun iterator(): MutableIterator<T> {
        return list.iterator()
    }

    actual override fun listIterator(): MutableListIterator<T> {
        return list.listIterator()
    }

    actual override fun listIterator(index: Int): MutableListIterator<T> {
        return list.listIterator(index)
    }

    actual override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return list.subList(fromIndex, toIndex)
    }

    actual override fun add(element: T): Boolean {
        return list.add(element)
    }

    actual override fun add(index: Int, element: T) {
        list.add(index, element)
    }

    actual override fun addAll(elements: Collection<T>): Boolean {
        return list.addAll(elements)
    }

    actual override fun addAll(index: Int, elements: Collection<T>): Boolean {
        return list.addAll(index, elements)
    }

    actual override fun remove(element: T): Boolean {
        return list.remove(element)
    }

    actual override fun removeAt(index: Int): T {
        return list.removeAt(index)
    }

    actual override fun removeAll(elements: Collection<T>): Boolean {
        return list.removeAll(elements)
    }

    actual override fun retainAll(elements: Collection<T>): Boolean {
        return list.retainAll(elements)
    }

    actual override fun clear() {
        list.clear()
    }

    actual override fun set(index: Int, element: T): T {
        return list.set(index, element)
    }
}
