package com.atlassian.prosemirror.transform

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// There are several things that positions can be mapped through. Such objects conform to this
// interface.
interface Mappable {
    // Map a position through this object. When given, `assoc` (should be -1 or 1, defaults to 1)
    // determines with which side the position is associated, which determines in which direction
    // to move when a chunk of content is inserted at the mapped position.
    fun map(pos: Int, assoc: Int = 1): Int

    // Map a position, and return an object containing additional information about the mapping. The
    // result's `deleted` field tells you whether the position was deleted (completely enclosed in a
    // replaced range) during the mapping. When content on only one side is deleted, the position
    // itself is only considered deleted when `assoc` points in the direction of the deleted
    // content.
    fun mapResult(pos: Int, assoc: Int = 1): MapResult
}

// Recovery values encode a range index and an offset. They are represented as numbers, because tons
// of them will be created when mapping, for example, a large number of decorations. The number's
// lower 16 bits provide the index, the remaining bits the offset.
//
// Note: We intentionally don't use bit shift operators to en- and decode these, since those clip to
// 32 bits, which we might in rare cases want to overflow. A 64-bit float can represent 48-bit
// integers precisely.

const val FACTOR_16 = 2 shl 15
const val LOWER_16 = 0xffff

fun makeRecover(index: Int, offset: Int): Int = index + offset * FACTOR_16
fun recoverIndex(value: Int): Int = value and LOWER_16
fun recoverOffset(value: Int): Int = (value - (value and LOWER_16)) / FACTOR_16

const val DEL_BEFORE = 1
const val DEL_AFTER = 2
const val DEL_ACROSS = 4
const val DEL_SIDE = 8

// An object representing a mapped position with extra information.
class MapResult internal constructor(
    // The mapped version of the position.
    val pos: Int,
    val delInfo: Int,
    val recover: Int?
) {

    // Tells you whether the position was deleted, that is, whether the step removed the token on
    // the side queried (via the `assoc`) argument from the document.
    val deleted: Boolean
        get() = (this.delInfo and DEL_SIDE) > 0

    // Tells you whether the token before the mapped position was deleted.
    val deletedBefore: Boolean
        get() = (this.delInfo and (DEL_BEFORE or DEL_ACROSS)) > 0

    // True when the token after the mapped position was deleted.
    val deletedAfter: Boolean
        get() = (this.delInfo and (DEL_AFTER or DEL_ACROSS)) > 0

    // Tells whether any of the steps mapped through deletes across the position (including both the
    // token before and after the position).
    val deletedAcross: Boolean
        get() = (this.delInfo and DEL_ACROSS) > 0
}

// A map describing the deletions and insertions made by a step, which can be used to find the
// correspondence between positions in the pre-step version of a document and the same position in
// the post-step version.
class StepMap(
    // Create a position map. The modifications to the document are represented as an array of
    // numbers, in which each group of three represents a modified chunk as
    // `[start, oldSize, newSize]`.
    internal val ranges: List<Int>,
    internal val inverted: Boolean = false
) : Mappable {
    internal fun recover(value: Int): Int {
        var diff = 0
        val index = recoverIndex(value)
        if (!this.inverted) {
            for (i in 0 until index) {
                diff += this.ranges[i * 3 + 2] - this.ranges[i * 3 + 1]
            }
        }
        return this.ranges[index * 3] + diff + recoverOffset(value)
    }

    override fun mapResult(pos: Int, assoc: Int): MapResult {
        return this._map(pos, assoc, false) as MapResult
    }

    override fun map(pos: Int, assoc: Int): Int {
        return _map(pos, assoc, true) as Int
    }

    @Suppress("NestedBlockDepth", "ComplexMethod", "FunctionNaming")
    internal fun _map(pos: Int, assoc: Int, simple: Boolean): Any {
        var diff = 0
        val oldIndex = if (this.inverted) 2 else 1
        val newIndex = if (this.inverted) 1 else 2
        for (i in 0 until this.ranges.size step 3) {
            val start = this.ranges[i] - (if (this.inverted) diff else 0)
            if (start > pos) break
            val oldSize = this.ranges[i + oldIndex]
            val newSize = this.ranges[i + newIndex]
            val end = start + oldSize
            if (pos <= end) {
                val side = when {
                    oldSize == 0 -> assoc
                    pos == start -> -1
                    pos == end -> 1
                    else -> assoc
                }
                val result = start + diff + (if (side < 0) 0 else newSize)
                if (simple) return result
                val recover = if (pos == (if (assoc < 0) start else end)) {
                    null
                } else {
                    makeRecover(i / 3, pos - start)
                }
                var del = if (pos == start) DEL_AFTER else if (pos == end) DEL_BEFORE else DEL_ACROSS
                if (if (assoc < 0) pos != start else pos != end) del = del or DEL_SIDE
                return MapResult(result, del, recover)
            }
            diff += newSize - oldSize
        }
        return if (simple) pos + diff else MapResult(pos + diff, 0, null)
    }

    internal fun touches(pos: Int, recover: Int): Boolean {
        var diff = 0
        val index = recoverIndex(recover)
        val oldIndex = if (this.inverted) 2 else 1
        val newIndex = if (this.inverted) 1 else 2
        for (i in ranges.indices step 3) {
            val start = this.ranges[i] - (if (this.inverted) diff else 0)
            if (start > pos) break
            val oldSize = this.ranges[i + oldIndex]
            val end = start + oldSize
            if (pos <= end && i == index * 3) return true
            diff += this.ranges[i + newIndex] - oldSize
        }
        return false
    }

    // Calls the given function on each of the changed ranges included in this map.
    fun forEach(f: (oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int) -> Unit) {
        val oldIndex = if (this.inverted) 2 else 1
        val newIndex = if (this.inverted) 1 else 2

        var diff = 0
        for (i in ranges.indices step 3) {
            val start = this.ranges[i]
            val oldStart = start - (if (this.inverted) diff else 0)
            val newStart = start + (if (this.inverted) 0 else diff)
            val oldSize = this.ranges[i + oldIndex]
            val newSize = this.ranges[i + newIndex]
            f(oldStart, oldStart + oldSize, newStart, newStart + newSize)
            diff += newSize - oldSize
        }
    }

    // Create an inverted version of this map. The result can be used to map positions in the post-step document to
    // the pre-step document.
    fun invert() = StepMap(this.ranges, !this.inverted)

    override fun toString(): String {
        return (if (this.inverted) "-" else "") + Json.encodeToString(this.ranges)
    }

    companion object {
        // A StepMap that contains no changed ranges.
        val empty = StepMap(emptyList())

        // Create a map that moves all positions by offset `n` (which may be negative). This can be
        // useful when applying steps meant for a sub-document to a larger document, or vice-versa.
        fun offset(n: Int) =
            if (n == 0) empty else StepMap(if (n < 0) listOf(0, -n, 0) else listOf(0, 0, n))
    }
}

// A mapping represents a pipeline of zero or more [step maps](#transform.StepMap). It has special
// provisions for losslessly handling mapping positions through a series of steps in which some
// steps are inverted versions of earlier steps. (This comes up
// when‘[rebasing](/docs/guide/#transform.rebasing)’ steps for collaboration or history management.)
class Mapping(
    // Create a new mapping with the given position maps. The step maps in this mapping.
    maps: List<StepMap> = emptyList(),
    internal var mirror: MutableList<Int>? = null,
    // The starting position in the `maps` array, used when `map` or `mapResult` is called.
    var from: Int = 0,
    // The end position in the `maps` array.
    var to: Int = maps.size
) : Mappable {
    private val stepMaps = maps.toMutableList()
    val maps: List<StepMap>
        get() = stepMaps

    // Create a mapping that maps only through a part of this one.
    fun slice(from: Int = 0, to: Int = this.stepMaps.size) = Mapping(this.stepMaps, this.mirror, from, to)

    internal fun copy() = Mapping(stepMaps.toMutableList(), this.mirror?.toMutableList(), this.from, this.to)

    // Add a step map to the end of this mapping. If `mirrors` is given, it should be the index of
    // the step map that is the mirror image of this one.
    fun appendMap(map: StepMap, mirrors: Int? = null) {
        this.stepMaps.add(map)
        this.to = this.stepMaps.size
        if (mirrors != null) this.setMirror(this.stepMaps.size - 1, mirrors)
    }

    // Add all the step maps in a given mapping to this one (preserving mirroring information).
    fun appendMapping(mapping: Mapping) {
        val startSize = stepMaps.size
        for (i in mapping.stepMaps.indices) {
            val mirr = mapping.getMirror(i)
            this.appendMap(mapping.stepMaps[i], if (mirr != null && mirr < i) startSize + mirr else null)
        }
    }

    // Finds the offset of the step map that mirrors the map at the given offset, in this mapping
    // (as per the second argument to `appendMap`).
    fun getMirror(n: Int): Int? {
        return this.mirror?.let {
            it.forEachIndexed { i, item ->
                if (item == n) return it[i + (if (i % 2 != 0) -1 else 1)]
            }
            return null
        }
    }

    fun setMirror(n: Int, m: Int) {
        if (this.mirror == null) this.mirror = mutableListOf()
        this.mirror?.add(n)
        this.mirror?.add(m)
    }

    // Append the inverse of the given mapping to this one.
    fun appendMappingInverted(mapping: Mapping) {
        val totalSize = this.maps.size + mapping.maps.size
        for (i in mapping.maps.size - 1 downTo 0) {
            val mirr = mapping.getMirror(i)
            this.appendMap(
                mapping.maps[i].invert(),
                if (mirr != null && mirr > i) totalSize - mirr - 1 else null
            )
        }
    }

    // Create an inverted version of this mapping.
    fun invert(): Mapping {
        val inverse = Mapping()
        inverse.appendMappingInverted(this)
        return inverse
    }

    // Map a position through this mapping.
    override fun map(pos: Int, assoc: Int): Int {
        var pos = pos
        if (this.mirror != null) return this._map(pos, assoc, true) as Int
        for (i in from until to) {
            pos = this.stepMaps[i].map(pos, assoc)
        }
        return pos
    }

    // Map a position through this mapping, returning a mapping result.
    override fun mapResult(pos: Int, assoc: Int): MapResult {
        return this._map(pos, assoc, false) as MapResult
    }

    @Suppress("FunctionNaming")
    internal fun _map(pos: Int, assoc: Int, simple: Boolean): Any {
        var delInfo = 0
        var pos = pos

        var i = this.from
        while (i < to) {
            val map = this.stepMaps[i]
            val result = map.mapResult(pos, assoc)
            if (result.recover != null) {
                val corr = this.getMirror(i)
                if (corr != null && corr > i && corr < this.to) {
                    i = corr
                    pos = this.stepMaps[corr].recover(result.recover)
                    i++
                    continue
                }
            }

            delInfo = delInfo or result.delInfo
            pos = result.pos
            i++
        }

        return if (simple) pos else MapResult(pos, delInfo, null)
    }
}
