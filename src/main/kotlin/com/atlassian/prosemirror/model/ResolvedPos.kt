package com.atlassian.prosemirror.model

// import java.lang.StringBuilder

// import {Mark} from "./mark"
// import {Node} from "./node"

// region ResolvedPos
// /// You can [_resolve_](#model.Node.resolve) a position to get more
// /// information about it. Objects of this class represent such a
// /// resolved position, providing various pieces of context
// /// information, and some helper methods.
// ///
// /// Throughout this interface, methods that take an optional `depth`
// /// parameter will interpret undefined as `this.depth` and negative
// /// numbers as `this.depth + value`.
// export class ResolvedPos {
//     /// The number of levels the parent node is from the root. If this
//     /// position points directly into the root node, it is 0. If it
//     /// points into a top-level paragraph, 1, and so on.
//     depth: number
//
//     /// @internal
//     constructor(
//         /// The position that was resolved.
//         readonly pos: number,
//         /// @internal
//         readonly path: any[],
//         /// The offset this position has into its parent node.
//         readonly parentOffset: number
//     ) {
//         this.depth = path.length / 3 - 1
//     }
//
//     /// @internal
//     resolveDepth(val: number | undefined | null) {
//         if (val == null) return this.depth
//         if (val < 0) return this.depth + val
//         return val
//     }
//
//     /// The parent node that the position points into. Note that even if
//     /// a position points into a text node, that node is not considered
//     /// the parent—text nodes are ‘flat’ in this model, and have no content.
//     get parent() { return this.node(this.depth) }
//
//     /// The root node in which the position was resolved.
//     get doc() { return this.node(0) }
//
//     /// The ancestor node at the given level. `p.node(p.depth)` is the
//     /// same as `p.parent`.
//     node(depth?: number | null): Node { return this.path[this.resolveDepth(depth) * 3] }
//
//     /// The index into the ancestor at the given level. If this points
//     /// at the 3rd node in the 2nd paragraph on the top level, for
//     /// example, `p.index(0)` is 1 and `p.index(1)` is 2.
//     index(depth?: number | null): number { return this.path[this.resolveDepth(depth) * 3 + 1] }
//
//     /// The index pointing after this position into the ancestor at the
//     /// given level.
//     indexAfter(depth?: number | null): number {
//         depth = this.resolveDepth(depth)
//         return this.index(depth) + (depth == this.depth && !this.textOffset ? 0 : 1)
//     }
//
//     /// The (absolute) position at the start of the node at the given
//     /// level.
//     start(depth?: number | null): number {
//         depth = this.resolveDepth(depth)
//         return depth == 0 ? 0 : this.path[depth * 3 - 1] + 1
//     }
//
//     /// The (absolute) position at the end of the node at the given
//     /// level.
//     end(depth?: number | null): number {
//         depth = this.resolveDepth(depth)
//         return this.start(depth) + this.node(depth).content.size
//     }
//
//     /// The (absolute) position directly before the wrapping node at the
//     /// given level, or, when `depth` is `this.depth + 1`, the original
//     /// position.
//     before(depth?: number | null): number {
//         depth = this.resolveDepth(depth)
//         if (!depth) throw new RangeError("There is no position before the top-level node")
//         return depth == this.depth + 1 ? this.pos : this.path[depth * 3 - 1]
//     }
//
//     /// The (absolute) position directly after the wrapping node at the
//     /// given level, or the original position when `depth` is `this.depth + 1`.
//     after(depth?: number | null): number {
//         depth = this.resolveDepth(depth)
//         if (!depth) throw new RangeError("There is no position after the top-level node")
//         return depth == this.depth + 1 ? this.pos : this.path[depth * 3 - 1] + this.path[depth * 3].nodeSize
//     }
//
//     /// When this position points into a text node, this returns the
//     /// distance between the position and the start of the text node.
//     /// Will be zero for positions that point between nodes.
//     get textOffset(): number { return this.pos - this.path[this.path.length - 1] }
//
//     /// Get the node directly after the position, if any. If the position
//     /// points into a text node, only the part of that node after the
//     /// position is returned.
//     get nodeAfter(): Node | null {
//         let parent = this.parent, index = this.index(this.depth)
//         if (index == parent.childCount) return null
//         let dOff = this.pos - this.path[this.path.length - 1], child = parent.child(index)
//         return dOff ? parent.child(index).cut(dOff) : child
//     }
//
//     /// Get the node directly before the position, if any. If the
//     /// position points into a text node, only the part of that node
//     /// before the position is returned.
//     get nodeBefore(): Node | null {
//         let index = this.index(this.depth)
//         let dOff = this.pos - this.path[this.path.length - 1]
//         if (dOff) return this.parent.child(index).cut(0, dOff)
//         return index == 0 ? null : this.parent.child(index - 1)
//     }
//
//     /// Get the position at the given index in the parent node at the
//     /// given depth (which defaults to `this.depth`).
//     posAtIndex(index: number, depth?: number | null): number {
//         depth = this.resolveDepth(depth)
//         let node = this.path[depth * 3], pos = depth == 0 ? 0 : this.path[depth * 3 - 1] + 1
//         for (let i = 0; i < index; i++) pos += node.child(i).nodeSize
//         return pos
//     }
//
//     /// Get the marks at this position, factoring in the surrounding
//     /// marks' [`inclusive`](#model.MarkSpec.inclusive) property. If the
//     /// position is at the start of a non-empty node, the marks of the
//     /// node after it (if any) are returned.
//     marks(): readonly Mark[] {
//         let parent = this.parent, index = this.index()
//
//         // In an empty parent, return the empty array
//         if (parent.content.size == 0) return Mark.none
//
//         // When inside a text node, just return the text node's marks
//         if (this.textOffset) return parent.child(index).marks
//
//         let main = parent.maybeChild(index - 1), other = parent.maybeChild(index)
//         // If the `after` flag is true of there is no node before, make
//         // the node after this position the main reference.
//         if (!main) { let tmp = main; main = other; other = tmp }
//
//         // Use all marks in the main node, except those that have
//         // `inclusive` set to false and are not present in the other node.
//         let marks = main!.marks
//                 for (var i = 0; i < marks.length; i++)
//         if (marks[i].type.spec.inclusive === false && (!other || !marks[i].isInSet(other.marks)))
//             marks = marks[i--].removeFromSet(marks)
//
//         return marks
//     }
//
//     /// Get the marks after the current position, if any, except those
//     /// that are non-inclusive and not present at position `$end`. This
//     /// is mostly useful for getting the set of marks to preserve after a
//     /// deletion. Will return `null` if this position is at the end of
//     /// its parent node or its parent node isn't a textblock (in which
//     /// case no marks should be preserved).
//     marksAcross($end: ResolvedPos): readonly Mark[] | null {
//         let after = this.parent.maybeChild(this.index())
//         if (!after || !after.isInline) return null
//
//         let marks = after.marks, next = $end.parent.maybeChild($end.index())
//         for (var i = 0; i < marks.length; i++)
//         if (marks[i].type.spec.inclusive === false && (!next || !marks[i].isInSet(next.marks)))
//             marks = marks[i--].removeFromSet(marks)
//         return marks
//     }
//
//     /// The depth up to which this position and the given (non-resolved)
//     /// position share the same parent nodes.
//     sharedDepth(pos: number): number {
//         for (let depth = this.depth; depth > 0; depth--)
//         if (this.start(depth) <= pos && this.end(depth) >= pos) return depth
//         return 0
//     }
//
//     /// Returns a range based on the place where this position and the
//     /// given position diverge around block content. If both point into
//     /// the same textblock, for example, a range around that textblock
//     /// will be returned. If they point into different blocks, the range
//     /// around those blocks in their shared ancestor is returned. You can
//     /// pass in an optional predicate that will be called with a parent
//     /// node to see if a range into that parent is acceptable.
//     blockRange(other: ResolvedPos = this, pred?: (node: Node) => boolean): NodeRange | null {
//         if (other.pos < this.pos) return other.blockRange(this)
//         for (let d = this.depth - (this.parent.inlineContent || this.pos == other.pos ? 1 : 0); d >= 0; d--)
//         if (other.pos <= this.end(d) && (!pred || pred(this.node(d))))
//             return new NodeRange(this, other, d)
//         return null
//     }
//
//     /// Query whether the given position shares the same parent node.
//     sameParent(other: ResolvedPos): boolean {
//         return this.pos - this.parentOffset == other.pos - other.parentOffset
//     }
//
//     /// Return the greater of this and the given position.
//     max(other: ResolvedPos): ResolvedPos {
//         return other.pos > this.pos ? other : this
//     }
//
//     /// Return the smaller of this and the given position.
//     min(other: ResolvedPos): ResolvedPos {
//         return other.pos < this.pos ? other : this
//     }
//
//     /// @internal
//     toString() {
//         let str = ""
//         for (let i = 1; i <= this.depth; i++)
//         str += (str ? "/" : "") + this.node(i).type.name + "_" + this.index(i - 1)
//         return str + ":" + this.parentOffset
//     }
//
//     /// @internal
//     static resolve(doc: Node, pos: number): ResolvedPos {
//         if (!(pos >= 0 && pos <= doc.content.size)) throw new RangeError("Position " + pos + " out of range")
//         let path = []
//         let start = 0, parentOffset = pos
//         for (let node = doc;;) {
//             let {index, offset} = node.content.findIndex(parentOffset)
//             let rem = parentOffset - offset
//             path.push(node, index, start + offset)
//             if (!rem) break
//             node = node.child(index)
//             if (node.isText) break
//             parentOffset = rem - 1
//             start += offset + 1
//         }
//         return new ResolvedPos(pos, path, parentOffset)
//     }
//
//     /// @internal
//     static resolveCached(doc: Node, pos: number): ResolvedPos {
//         for (let i = 0; i < resolveCache.length; i++) {
//             let cached = resolveCache[i]
//             if (cached.pos == pos && cached.doc == doc) return cached
//         }
//         let result = resolveCache[resolveCachePos] = ResolvedPos.resolve(doc, pos)
//         resolveCachePos = (resolveCachePos + 1) % resolveCacheSize
//         return result
//     }
// }
// endregion

// You can [_resolve_](#model.Node.resolve) a position to get more information about it. Objects of
// this class represent such a resolved position, providing various pieces of context information,
// and some helper methods.
//
// Throughout this interface, methods that take an optional `depth` parameter will interpret
// undefined as `this.depth` and negative numbers as `this.depth + value`.
class ResolvedPos(
    // The position that was resolved.
    val pos: Int,
    internal val path: List<Any>,
    // The offset this position has into its parent node.
    val parentOffset: Int
) {
    // The number of levels the parent node is from the root. If this position points directly into
    // the root node, it is 0. If it points into a top-level paragraph, 1, and so on.
    val depth: Int

    init {
        this.depth = path.size / 3 - 1
    }

    // The parent node that the position points into. Note that even if a position points into a
    // text node, that node is not considered the parent—text nodes are ‘flat’ in this model, and
    // have no content.
    val parent: Node
        get() = this.node(this.depth)

    // The root node in which the position was resolved.
    val doc: Node
        get() = this.node(0)

    // When this position points into a text node, this returns the distance between the position
    // and the start of the text node. Will be zero for positions that point between nodes.
    val textOffset: Int
        get() = this.pos - this.path[this.path.size - 1] as Int

    // Get the node directly after the position, if any. If the position points into a text node,
    // only the part of that node after the position is returned.
    val nodeAfter: Node?
        get() {
            val parent = this.parent
            val index = this.index(this.depth)
            if (index == parent.childCount) {
                return null
            }
            val dOff = this.pos - this.path[this.path.size - 1] as Int
            val child = parent.child(index)
            return if (dOff > 0) parent.child(index).cut(dOff) else child
        }

    // Get the node directly before the position, if any. If the position points into a text node,
    // only the part of that node before the position is returned.
    val nodeBefore: Node?
        get() {
            val index = this.index(this.depth)
            val dOff = this.pos - this.path[this.path.size - 1] as Int
            if (dOff > 0) return this.parent.child(index).cut(0, dOff)
            return if (index == 0) null else this.parent.child(index - 1)
        }

    internal fun resolveDepth(value: Int?): Int {
        if (value == null) return this.depth
        if (value < 0) return this.depth + value
        return value
    }

    // The ancestor node at the given level. `p.node(p.depth)` is the same as `p.parent`.
    fun node(depth: Int? = null): Node {
        return this.path[this.resolveDepth(depth) * 3] as Node
    }

    fun nodeOrNull(depth: Int?): Node? {
        return this.path.getOrNull(this.resolveDepth(depth) * 3) as Node?
    }

    // The index into the ancestor at the given level. If this points at the 3rd node in the 2nd
    // paragraph on the top level, for example, `p.index(0)` is 1 and `p.index(1)` is 2.
    fun index(depth: Int? = null): Int {
        return this.path[this.resolveDepth(depth) * 3 + 1] as Int
    }

    // The index pointing after this position into the ancestor at the given level.
    fun indexAfter(depth: Int?): Int {
        val depth = this.resolveDepth(depth)
        return this.index(depth) + (if (depth == this.depth && this.textOffset == 0) 0 else 1)
    }

    // The (absolute) position at the start of the node at the given level.
    fun start(depth: Int? = null): Int {
        val depth = this.resolveDepth(depth)
        return if (depth == 0) {
            0
        } else {
            this.path[depth * 3 - 1] as Int + 1
        }
    }

    // The (absolute) position at the end of the node at the given level.
    fun end(depth: Int? = null): Int {
        val depth = this.resolveDepth(depth)
        return this.start(depth) + this.node(depth).content.size
    }

    // The (absolute) position directly before the wrapping node at the given level, or, when
    // `depth` is `this.depth + 1`, the original position.
    fun before(depth: Int? = null): Int {
        val depth = this.resolveDepth(depth)
        if (depth == 0) throw RangeError("There is no position before the top-level node")
        return if (depth == this.depth + 1) this.pos else this.path[depth * 3 - 1] as Int
    }

    // The (absolute) position directly after the wrapping node at the given level, or the original
    // position when `depth` is `this.depth + 1`.
    fun after(depth: Int? = null): Int {
        val depth = this.resolveDepth(depth)
        if (depth == 0) throw RangeError("There is no position after the top-level node")
        return if (depth == this.depth + 1) {
            this.pos
        } else {
            this.path[depth * 3 - 1] as Int + (this.path[depth * 3] as Node).nodeSize
        }
    }

    // Get the position at the given index in the parent node at the given depth (which defaults to `this.depth`).
    fun posAtIndex(index: Int, depth: Int? = null): Int {
        val depth = this.resolveDepth(depth)
        val node = nodeOrNull(depth)
        var pos = if (depth == 0) 0 else (this.path[depth * 3 - 1] as Int + 1)
        node ?: return pos
        for (i in 0..<index) {
            pos += node.child(i).nodeSize
        }
        return pos
    }

    // Get the marks at this position, factoring in the surrounding marks'
    // [`inclusive`](#model.MarkSpec.inclusive) property. If the position is at the start of a
    // non-empty node, the marks of the node after it (if any) are returned.
    fun marks(): List<Mark> {
        val parent = this.parent
        val index = this.index()

        // In an empty parent, return the empty array
        if (parent.content.size == 0) return Mark.none

        // When inside a text node, just return the text node's marks
        if (this.textOffset != 0) return parent.child(index).marks

        var main = parent.maybeChild(index - 1)
        var other = parent.maybeChild(index)
        // If the `after` flag is true of there is no node before, make
        // the node after this position the main reference.
        if (main == null) {
            // swap
            main = other.also { other = main }
        }

        // Use all marks in the main node, except those that have
        // `inclusive` set to false and are not present in the other node.
        val marks = main!!.marks.toMutableList()

        val marksIterator = marks.iterator()
        while (marksIterator.hasNext()) {
            val mark = marksIterator.next()
            if (mark.type.spec.inclusive == false && (other == null || !mark.isInSet(other!!.marks))) {
                marksIterator.remove()
            }
        }

        return marks.toList()
    }

    // Get the marks after the current position, if any, except those that are non-inclusive and not
    // present at position `$end`. This is mostly useful for getting the set of marks to preserve
    // after a deletion. Will return `null` if this position is at the end of its parent node or its
    // parent node isn't a textblock (in which case no marks should be preserved).
    fun marksAcross(end: ResolvedPos): List<Mark>? {
        val after = this.parent.maybeChild(this.index())
        if (after == null || !after.isInline) return null

        val marks = after.marks.toMutableList()
        val next = end.parent.maybeChild(end.index())
        val marksIterator = marks.iterator()
        while (marksIterator.hasNext()) {
            val mark = marksIterator.next()
            if (mark.type.spec.inclusive == false && (next == null || !mark.isInSet(next.marks))) {
                marksIterator.remove()
            }
        }
        return marks.toList()
    }

    // The depth up to which this position and the given (non-resolved) position share the same
    // parent nodes.
    fun sharedDepth(pos: Int): Int {
        for (depth in this.depth downTo 1) {
            if (this.start(depth) <= pos && this.end(depth) >= pos) return depth
        }
        return 0
    }

    // Returns a range based on the place where this position and the given position diverge around
    // block content. If both point into the same textblock, for example, a range around that
    // textblock will be returned. If they point into different blocks, the range around those
    // blocks in their shared ancestor is returned. You can pass in an optional predicate that will
    // be called with a parent node to see if a range into that parent is acceptable.
    fun blockRange(other: ResolvedPos = this, pred: ((node: Node) -> Boolean)? = null): NodeRange? {
        if (other.pos < this.pos) return other.blockRange(this)
        for (d in this.depth - (if (this.parent.inlineContent || this.pos == other.pos) 1 else 0) downTo 0) {
            if (other.pos <= this.end(d) && (pred == null || pred(this.node(d)))) {
                return NodeRange(this, other, d)
            }
        }
        return null
    }

    // Query whether the given position shares the same parent node.
    fun sameParent(other: ResolvedPos): Boolean {
        return this.pos - this.parentOffset == other.pos - other.parentOffset
    }

    // Return the greater of this and the given position.
    fun max(other: ResolvedPos): ResolvedPos {
        return if (other.pos > this.pos) other else this
    }

    // Return the smaller of this and the given position.
    fun min(other: ResolvedPos): ResolvedPos {
        return if (other.pos < this.pos) other else this
    }

    override fun toString(): String {
        val str = StringBuilder()
        for (i in 1..this.depth) {
            str.append(if (i == 0) "/" else "")
                .append(this.node(i).type.name)
                .append("_")
                .append(this.index(i - 1))
        }
        str.append(":").append(this.parentOffset)
        return str.toString()
    }

    companion object {
        @Suppress("LoopWithTooManyJumpStatements")
        internal fun resolve(doc: Node, pos: Int): ResolvedPos {
            if (!(pos >= 0 && pos <= doc.content.size)) throw RangeError("Position $pos out of range")
            val path = mutableListOf<Any>()
            var start = 0
            var parentOffset = pos
            var node = doc
            while (true) {
                val ind = node.content.findIndex(parentOffset)
                val index = Index.index
                val offset = Index.offset
                val rem = parentOffset - offset
                path.add(node)
                path.add(index)
                path.add(start + offset)
                if (rem == 0) break
                node = node.child(index)
                if (node.isText) break
                parentOffset = rem - 1
                start += offset + 1
            }
            return ResolvedPos(pos, path, parentOffset)
        }

        internal fun resolveCached(doc: Node, pos: Int): ResolvedPos {
            val resolveCache = doc.type.schema.resolveCache
            val resolveCachePos = doc.type.schema.resolveCachePos

            resolveCache.forEach { cached ->
                if (cached.pos == pos && cached.doc === doc) return cached
            }
            val result = resolve(doc, pos)
            if (resolveCachePos.get() >= resolveCache.size) {
                resolveCache.add(result)
            } else {
                resolveCache[resolveCachePos.get()] = result
            }
            resolveCachePos.set((resolveCachePos.get() + 1) % resolveCacheSize)
            return result
        }
    }
}

@Suppress("MayBeConst")
val resolveCacheSize = 12

// region NodeRange
// /// Represents a flat range of content, i.e. one that starts and
// /// ends in the same node.
// export class NodeRange {
//     /// Construct a node range. `$from` and `$to` should point into the
//     /// same node until at least the given `depth`, since a node range
//     /// denotes an adjacent set of nodes in a single parent node.
//     constructor(
//         /// A resolved position along the start of the content. May have a
//         /// `depth` greater than this object's `depth` property, since
//         /// these are the positions that were used to compute the range,
//         /// not re-resolved positions directly at its boundaries.
//         readonly $from: ResolvedPos,
//         /// A position along the end of the content. See
//         /// caveat for [`$from`](#model.NodeRange.$from).
//         readonly $to: ResolvedPos,
//         /// The depth of the node that this range points into.
//         readonly depth: number
//     ) {}
//
//     /// The position at the start of the range.
//     get start() { return this.$from.before(this.depth + 1) }
//     /// The position at the end of the range.
//     get end() { return this.$to.after(this.depth + 1) }
//
//     /// The parent node that the range points into.
//     get parent() { return this.$from.node(this.depth) }
//     /// The start index of the range in the parent node.
//     get startIndex() { return this.$from.index(this.depth) }
//     /// The end index of the range in the parent node.
//     get endIndex() { return this.$to.indexAfter(this.depth) }
// }
// endregion

// Represents a flat range of content, i.e. one that starts and ends in the same node.
data class NodeRange(
    // Construct a node range. `$from` and `$to` should point into the same node until at least the
    // given `depth`, since a node range denotes an adjacent set of nodes in a single parent node.

    // A resolved position along the start of the content. May have a `depth` greater than this
    // object's `depth` property, since these are the positions that were used to compute the range,
    // not re-resolved positions directly at its boundaries.
    val from: ResolvedPos,
    // A position along the end of the content. See caveat for [`$from`](#model.NodeRange.$from).
    val to: ResolvedPos,
    // The depth of the node that this range points into.
    val depth: Int
) {
    // The position at the start of the range.
    val start: Int
        get() = this.from.before(this.depth + 1)

    // The position at the end of the range.
    val end: Int
        get() = this.to.after(this.depth + 1)

    // The parent node that the range points into.
    val parent: Node
        get() = this.from.node(this.depth)

    // The start index of the range in the parent node.
    val startIndex: Int
        get() = this.from.index(this.depth)

    // The end index of the range in the parent node.
    val endIndex: Int
        get() = this.to.indexAfter(this.depth)
}
