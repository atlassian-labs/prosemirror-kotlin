package com.atlassian.prosemirror.history.ropesequence

import kotlin.math.max
import kotlin.math.min

const val GOOD_LEAF_SIZE = 200

// :: class<T> A rope sequence is a persistent sequence data structure
// that supports appending, prepending, and slicing without doing a
// full copy. It is represented as a mostly-balanced tree.
abstract class RopeSequence<T> {
    // length:: number
    // The length of the rope.
    abstract val length: Int

    abstract val depth: Int

    abstract fun forEachInner(f: (T, Int) -> Boolean, from: Int, to: Int, start: Int): Boolean

    abstract fun forEachInvertedInner(f: (T, Int) -> Boolean, from: Int, to: Int, start: Int): Boolean

    abstract fun leafAppend(other: RopeSequence<T>): RopeSequence<T>?

    abstract fun leafPrepend(other: RopeSequence<T>): RopeSequence<T>?

    abstract fun sliceInner(from: Int, to: Int): RopeSequence<T>

    // flatten:: () → [T]
    // Return the content of this rope as an array.
    abstract fun flatten(): List<T>

    abstract fun getInner(i: Int): T?

    // :: (union<[T], RopeSequence<T>>) → RopeSequence<T>
    // Append an array or other rope to this one, returning a new rope.
    @Suppress("ReturnCount")
    open fun append(other: RopeSequence<T>): RopeSequence<T> {
        if (other.length == 0) return this
        if (this.length == 0) return other
        if (other.length < GOOD_LEAF_SIZE) {
            val res = this.leafAppend(other)
            if (res != null) {
                return res
            }
        }
        if (this.length < GOOD_LEAF_SIZE) {
            val res = other.leafPrepend(this)
            if (res != null) {
                return res
            }
        }
        return this.appendInner(other)
    }

    open fun append(other: List<T>): RopeSequence<T> {
        if (other.isEmpty()) return this
        return append(from(other))
    }

    // :: (union<[T], RopeSequence<T>>) → RopeSequence<T>
    // Prepend an array or other rope to this one, returning a new rope.
    fun prepend(other: RopeSequence<T>): RopeSequence<T> {
        if (other.length == 0) return this
        return RopeSequence.from(other).append(this)
    }

    open fun appendInner(other: RopeSequence<T>): Append<T> {
        return Append(this, other)
    }

    // :: (?number, ?number) → RopeSequence<T>
    // Create a rope repesenting a sub-sequence of this rope.
    fun slice(from: Int = 0, to: Int = this.length): RopeSequence<T> {
        if (from >= to) return empty()
        return this.sliceInner(Math.max(0, from), Math.min(this.length, to))
    }

    // :: (number) → T
    // Retrieve the element at the given position from this rope.
    fun get(i: Int): T? {
        if (i < 0 || i >= this.length) return null
        return this.getInner(i)
    }

    // :: ((element: T, index: number) → ?bool, ?number, ?number)
    // Call the given function for each element between the given
    // indices. This tends to be more efficient than looping over the
    // indices and calling `get`, because it doesn't have to descend the
    // tree for every element.
    fun forEach(f: (T, Int) -> Boolean, from: Int = 0, to: Int = this.length) {
        if (from <= to) {
            this.forEachInner(f, from, to, 0)
        } else {
            this.forEachInvertedInner(f, from, to, 0)
        }
    }

    // :: ((element: T, index: number) → U, ?number, ?number) → [U]
    // Map the given functions over the elements of the rope, producing
    // a flat array.
    fun <U> map(f: (T, Int) -> U, from: Int = 0, to: Int = this.length): List<U> {
        return buildList {
            forEach(
                { elt, i ->
                    add(f(elt, i))
                },
                from,
                to
            )
        }
    }

    companion object {
        // :: (?union<[T], RopeSequence<T>>) → RopeSequence<T>
        // Create a rope representing the given array, or return the rope
        // itself if a rope was given.
        fun <T> from(values: List<T>?): RopeSequence<T> {
            return if (values.isNullOrEmpty()) empty() else Leaf(values)
        }

        fun <T> from(values: RopeSequence<T>): RopeSequence<T> {
            return values
        }

        // :: RopeSequence
        // The empty rope sequence.
        private val empty = Leaf(emptyList<Unit>())

        @Suppress("UNCHECKED_CAST")
        fun <T> empty(): RopeSequence<T> = empty as RopeSequence<T>
    }
}

class Leaf<T>(val values: List<T>) : RopeSequence<T>() {

    override val length: Int
        get() = values.size

    override val depth: Int
        get() = 0

    override fun flatten(): List<T> {
        return this.values
    }

    override fun sliceInner(from: Int, to: Int): Leaf<T> {
        if (from == 0 && to == this.length) return this
        return Leaf(this.values.slice(from until to))
    }

    override fun getInner(i: Int): T {
        return this.values[i]
    }

    override fun forEachInner(f: (T, Int) -> Boolean, from: Int, to: Int, start: Int): Boolean {
        for (i in from until to) {
            if (!f(values[i], start + i)) return false
        }
        return true
    }

    override fun forEachInvertedInner(f: (T, Int) -> Boolean, from: Int, to: Int, start: Int): Boolean {
        for (i in from - 1 downTo to) {
            if (!f(values[i], start + i)) return false
        }
        return true
    }

    override fun leafAppend(other: RopeSequence<T>): Leaf<T>? {
        return if (this.length + other.length <= GOOD_LEAF_SIZE) {
            Leaf(this.values + other.flatten())
        } else {
            null
        }
    }

    override fun leafPrepend(other: RopeSequence<T>): Leaf<T>? {
        return if (this.length + other.length <= GOOD_LEAF_SIZE) {
            Leaf(other.flatten() + this.values)
        } else {
            null
        }
    }

    override fun toString(): String {
        return "Leaf {values: $values, lenght: $length, depth: $depth}"
    }
}

class Append<T> (
    val left: RopeSequence<T>,
    val right: RopeSequence<T>
) : RopeSequence<T>() {
    override val length = left.length + right.length
    override val depth = Math.max(left.depth, right.depth) + 1

    override fun flatten(): List<T> {
        return this.left.flatten() + this.right.flatten()
    }

    override fun getInner(i: Int): T? {
        return if (i < this.left.length) this.left.get(i) else this.right.get(i - this.left.length)
    }

    @Suppress("ReturnCount")
    override fun forEachInner(f: (T, Int) -> Boolean, from: Int, to: Int, start: Int): Boolean {
        val leftLen = this.left.length
        if (from < leftLen && !this.left.forEachInner(f, from, min(to, leftLen), start)) {
            return false
        }
        if (to > leftLen &&
            !this.right.forEachInner(
                f,
                max(from - leftLen, 0),
                min(this.length, to) - leftLen,
                start + leftLen
            )
        ) {
            return false
        }
        return true
    }

    @Suppress("ReturnCount")
    override fun forEachInvertedInner(f: (T, Int) -> Boolean, from: Int, to: Int, start: Int): Boolean {
        val leftLen = this.left.length
        if (from > leftLen &&
            !this.right.forEachInvertedInner(f, from - leftLen, max(to, leftLen) - leftLen, start + leftLen)
        ) {
            return false
        }
        if (to < leftLen && !this.left.forEachInvertedInner(f, min(from, leftLen), to, start)) {
            return false
        }
        return true
    }

    @Suppress("ReturnCount")
    override fun sliceInner(from: Int, to: Int): RopeSequence<T> {
        if (from == 0 && to == this.length) return this
        val leftLen = this.left.length
        if (to <= leftLen) return this.left.slice(from, to)
        if (from >= leftLen) return this.right.slice(from - leftLen, to - leftLen)
        return this.left.slice(from, leftLen).append(this.right.slice(0, to - leftLen))
    }

    override fun leafAppend(other: RopeSequence<T>): Append<T>? {
        val inner = this.right.leafAppend(other)
        return if (inner != null) {
            Append(this.left, inner)
        } else {
            null
        }
    }

    override fun leafPrepend(other: RopeSequence<T>): Append<T>? {
        val inner = this.left.leafPrepend(other)
        return if (inner != null) {
            Append(inner, this.right)
        } else {
            null
        }
    }

    override fun appendInner(other: RopeSequence<T>): Append<T> {
        if (this.left.depth >= max(this.right.depth, other.depth) + 1) {
            return Append(this.left, Append(this.right, other))
        }
        return Append(this, other)
    }

    override fun toString(): String {
        return "Append {left: ${left.javaClass.simpleName}, right: ${right.javaClass.simpleName}, " +
            "lenght: $length, depth: $depth}"
    }
}
