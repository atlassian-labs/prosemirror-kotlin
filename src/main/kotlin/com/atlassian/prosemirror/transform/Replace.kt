package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.ContentMatch
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.ResolvedPos
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.util.resolveAndLog
import kotlin.math.max
import kotlin.math.min

// import {Fragment, Slice, Node, ResolvedPos, NodeType, ContentMatch, Attrs} from "prosemirror-model"

// import {Step} from "./step"
// import {ReplaceStep, ReplaceAroundStep} from "./replace_step"
// import {Transform} from "./transform"
// import {insertPoint} from "./structure"

// /// ‘Fit’ a slice into a given position in the document, producing a
// /// [step](#transform.Step) that inserts it. Will return null if
// /// there's no meaningful way to insert the slice here, or inserting it
// /// would be a no-op (an empty slice over an empty range).
// export function replaceStep(doc: Node, from: number, to = from, slice = Slice.empty): Step | null {
//     if (from == to && !slice.size) return null
//
//     let $from = doc.resolve(from), $to = doc.resolve(to)
//     // Optimization -- avoid work if it's obvious that it's not needed.
//     if (fitsTrivially($from, $to, slice)) return new ReplaceStep(from, to, slice)
//     return new Fitter($from, $to, slice).fit()
// }
// ‘Fit’ a slice into a given position in the document, producing a [step](#transform.Step) that
// inserts it. Will return null if there's no meaningful way to insert the slice here, or inserting
// it would be a no-op (an empty slice over an empty range).
fun replaceStep(doc: Node, from: Int, to: Int = from, slice: Slice = Slice.empty): Step? {
    if (from == to && slice.size == 0) return null
    val (resolvedFrom, resolvedTo) = doc.resolveAndLog(from, to) ?: return null
    // Optimization -- avoid work if it's obvious that it's not needed.
    if (fitsTrivially(resolvedFrom, resolvedTo, slice)) return ReplaceStep(from, to, slice)
    return Fitter(resolvedFrom, resolvedTo, slice).fit()
}

fun fitsTrivially(_from: ResolvedPos, _to: ResolvedPos, slice: Slice): Boolean {
    return slice.openStart == 0 &&
        slice.openEnd == 0 &&
        _from.start() == _to.start() &&
        _from.parent.canReplace(_from.index(), _to.index(), slice.content)
}

// interface Fittable {
//     sliceDepth: number
//     frontierDepth: number
//     parent: Node | null
//     inject?: Fragment | null
//     wrap?: readonly NodeType[]
// }
interface Fittable {
    val sliceDepth: Int
    val frontierDepth: Int
    val parent: Node?
    val inject: Fragment?
    val wrap: List<NodeType>?
}

data class FittableImpl(
    override val sliceDepth: Int,
    override val frontierDepth: Int,
    override val parent: Node? = null,
    override val inject: Fragment? = null,
    override val wrap: List<NodeType>? = null
) : Fittable

// region Fitter
// // Algorithm for 'placing' the elements of a slice into a gap:
// //
// // We consider the content of each node that is open to the left to be
// // independently placeable. I.e. in <p("foo"), p("bar")>, when the
// // paragraph on the left is open, "foo" can be placed (somewhere on
// // the left side of the replacement gap) independently from p("bar").
// //
// // This class tracks the state of the placement progress in the
// // following properties:
// //
// //  - `frontier` holds a stack of `{type, match}` objects that
// //    represent the open side of the replacement. It starts at
// //    `$from`, then moves forward as content is placed, and is finally
// //    reconciled with `$to`.
// //
// //  - `unplaced` is a slice that represents the content that hasn't
// //    been placed yet.
// //
// //  - `placed` is a fragment of placed content. Its open-start value
// //    is implicit in `$from`, and its open-end value in `frontier`.
// class Fitter {
//     frontier: {type: NodeType, match: ContentMatch}[] = []
//     placed: Fragment = Fragment.empty
//
//     constructor(
//         readonly $from: ResolvedPos,
//         readonly $to: ResolvedPos,
//         public unplaced: Slice
//     ) {
//         for (let i = 0; i <= $from.depth; i++) {
//             let node = $from.node(i)
//             this.frontier.push({
//                 type: node.type,
//                 match: node.contentMatchAt($from.indexAfter(i))
//             })
//         }
//
//         for (let i = $from.depth; i > 0; i--)
//         this.placed = Fragment.from($from.node(i).copy(this.placed))
//     }
//
//     get depth() { return this.frontier.length - 1 }
//
//     fit() {
//         // As long as there's unplaced content, try to place some of it.
//         // If that fails, either increase the open score of the unplaced
//         // slice, or drop nodes from it, and then try again.
//         while (this.unplaced.size) {
//             let fit = this.findFittable()
//             if (fit) this.placeNodes(fit)
//             else this.openMore() || this.dropNode()
//         }
//         // When there's inline content directly after the frontier _and_
//         // directly after `this.$to`, we must generate a `ReplaceAround`
//         // step that pulls that content into the node after the frontier.
//         // That means the fitting must be done to the end of the textblock
//         // node after `this.$to`, not `this.$to` itself.
//         let moveInline = this.mustMoveInline(), placedSize = this.placed.size - this.depth - this.$from.depth
//         let $from = this.$from, $to = this.close(moveInline < 0 ? this.$to : $from.doc.resolve(moveInline))
//         if (!$to) return null
//
//         // If closing to `$to` succeeded, create a step
//         let content = this.placed, openStart = $from.depth, openEnd = $to.depth
//         while (openStart && openEnd && content.childCount == 1) { // Normalize by dropping open parent nodes
//             content = content.firstChild!.content
//             openStart--; openEnd--
//         }
//         let slice = new Slice(content, openStart, openEnd)
//         if (moveInline > -1)
//             return new ReplaceAroundStep($from.pos, moveInline, this.$to.pos, this.$to.end(), slice, placedSize)
//         if (slice.size || $from.pos != this.$to.pos) // Don't generate no-op steps
//             return new ReplaceStep($from.pos, $to.pos, slice)
//         return null
//     }
//
//     // Find a position on the start spine of `this.unplaced` that has
//     // content that can be moved somewhere on the frontier. Returns two
//     // depths, one for the slice and one for the frontier.
//     findFittable(): Fittable | undefined {
//         // Only try wrapping nodes (pass 2) after finding a place without
//         // wrapping failed.
//         for (let pass = 1; pass <= 2; pass++) {
//             for (let sliceDepth = this.unplaced.openStart; sliceDepth >= 0; sliceDepth--) {
//             let fragment, parent = null
//             if (sliceDepth) {
//                 parent = contentAt(this.unplaced.content, sliceDepth - 1).firstChild
//                 fragment = parent!.content
//             } else {
//                 fragment = this.unplaced.content
//             }
//             let first = fragment.firstChild
//                     for (let frontierDepth = this.depth; frontierDepth >= 0; frontierDepth--) {
//             let {type, match} = this.frontier[frontierDepth], wrap, inject: Fragment | null = null
//             // In pass 1, if the next node matches, or there is no next
//             // node but the parents look compatible, we've found a
//             // place.
//             if (pass == 1 && (first ? match.matchType(first.type) ||
//                  (inject = match.fillBefore(Fragment.from(first), false))
//             : parent && type.compatibleContent(parent.type)))
//             return {sliceDepth, frontierDepth, parent, inject}
//             // In pass 2, look for a set of wrapping nodes that make
//             // `first` fit here.
//             else if (pass == 2 && first && (wrap = match.findWrapping(first.type)))
//             return {sliceDepth, frontierDepth, parent, wrap}
//             // Don't continue looking further up if the parent node
//             // would fit here.
//             if (parent && match.matchType(parent.type)) break
//         }
//         }
//         }
//     }
//
//     openMore() {
//         let {content, openStart, openEnd} = this.unplaced
//         let inner = contentAt(content, openStart)
//         if (!inner.childCount || inner.firstChild!.isLeaf) return false
//         this.unplaced = new Slice(content, openStart + 1,
//         Math.max(openEnd, inner.size + openStart >= content.size - openEnd ? openStart + 1 : 0))
//         return true
//     }
//
//     dropNode() {
//         let {content, openStart, openEnd} = this.unplaced
//         let inner = contentAt(content, openStart)
//         if (inner.childCount <= 1 && openStart > 0) {
//             let openAtEnd = content.size - openStart <= openStart + inner.size
//             this.unplaced = new Slice(dropFromFragment(content, openStart - 1, 1), openStart - 1,
//             openAtEnd ? openStart - 1 : openEnd)
//         } else {
//             this.unplaced = new Slice(dropFromFragment(content, openStart, 1), openStart, openEnd)
//         }
//     }
//
//     // Move content from the unplaced slice at `sliceDepth` to the
//     // frontier node at `frontierDepth`. Close that frontier node when
//     // applicable.
//     placeNodes({sliceDepth, frontierDepth, parent, inject, wrap}: Fittable) {
//         while (this.depth > frontierDepth) this.closeFrontierNode()
//         if (wrap) for (let i = 0; i < wrap.length; i++) this.openFrontierNode(wrap[i])
//
//         let slice = this.unplaced, fragment = parent ? parent.content : slice.content
//         let openStart = slice.openStart - sliceDepth
//         let taken = 0, add = []
//         let {match, type} = this.frontier[frontierDepth]
//         if (inject) {
//             for (let i = 0; i < inject.childCount; i++) add.push(inject.child(i))
//             match = match.matchFragment(inject)!
//         }
//         // Computes the amount of (end) open nodes at the end of the
//         // fragment. When 0, the parent is open, but no more. When
//         // negative, nothing is open.
//         let openEndCount = (fragment.size + sliceDepth) - (slice.content.size - slice.openEnd)
//         // Scan over the fragment, fitting as many child nodes as
//         // possible.
//         while (taken < fragment.childCount) {
//             let next = fragment.child(taken), matches = match.matchType(next.type)
//             if (!matches) break
//             taken++
//             if (taken > 1 || openStart == 0 || next.content.size) { // Drop empty open nodes
//                 match = matches
//                 add.push(closeNodeStart(next.mark(type.allowedMarks(next.marks)), taken == 1 ? openStart : 0,
//                     taken == fragment.childCount ? openEndCount : -1))
//             }
//         }
//         let toEnd = taken == fragment.childCount
//                 if (!toEnd) openEndCount = -1
//
//         this.placed = addToFragment(this.placed, frontierDepth, Fragment.from(add))
//         this.frontier[frontierDepth].match = match
//
//         // If the parent types match, and the entire node was moved, and
//         // it's not open, close this frontier node right away.
//         if (toEnd && openEndCount < 0 && parent && parent.type == this.frontier[this.depth].type &&
//              this.frontier.length > 1)
//             this.closeFrontierNode()
//
//         // Add new frontier nodes for any open nodes at the end.
//         for (let i = 0, cur = fragment; i < openEndCount; i++) {
//             let node = cur.lastChild!
//             this.frontier.push({type: node.type, match: node.contentMatchAt(node.childCount)})
//             cur = node.content
//         }
//
//         // Update `this.unplaced`. Drop the entire node from which we
//         // placed it we got to its end, otherwise just drop the placed
//         // nodes.
//         this.unplaced = !toEnd ? new Slice(
//              dropFromFragment(slice.content, sliceDepth, taken), slice.openStart, slice.openEnd)
//         : sliceDepth == 0 ? Slice.empty
//         : new Slice(dropFromFragment(slice.content, sliceDepth - 1, 1),
//         sliceDepth - 1, openEndCount < 0 ? slice.openEnd : sliceDepth - 1)
//     }
//
//     mustMoveInline() {
//         if (!this.$to.parent.isTextblock) return -1
//         let top = this.frontier[this.depth], level
//         if (!top.type.isTextblock || !contentAfterFits(this.$to, this.$to.depth, top.type, top.match, false) ||
//             (this.$to.depth == this.depth && (level = this.findCloseLevel(this.$to)) && level.depth == this.depth))
//                  return -1
//
//         let {depth} = this.$to, after = this.$to.after(depth)
//         while (depth > 1 && after == this.$to.end(--depth)) ++after
//         return after
//     }
//
//     findCloseLevel($to: ResolvedPos) {
//         scan: for (let i = Math.min(this.depth, $to.depth); i >= 0; i--) {
//             let {match, type} = this.frontier[i]
//             let dropInner = i < $to.depth && $to.end(i + 1) == $to.pos + ($to.depth - (i + 1))
//             let fit = contentAfterFits($to, i, type, match, dropInner)
//             if (!fit) continue
//             for (let d = i - 1; d >= 0; d--) {
//             let {match, type} = this.frontier[d]
//             let matches = contentAfterFits($to, d, type, match, true)
//             if (!matches || matches.childCount) continue scan
//         }
//             return {depth: i, fit, move: dropInner ? $to.doc.resolve($to.after(i + 1)) : $to}
//         }
//     }
//
//     close($to: ResolvedPos) {
//         let close = this.findCloseLevel($to)
//         if (!close) return null
//
//         while (this.depth > close.depth) this.closeFrontierNode()
//         if (close.fit.childCount) this.placed = addToFragment(this.placed, close.depth, close.fit)
//         $to = close.move
//         for (let d = close.depth + 1; d <= $to.depth; d++) {
//             let node = $to.node(d), add = node.type.contentMatch.fillBefore(node.content, true, $to.index(d))!
//             this.openFrontierNode(node.type, node.attrs, add)
//         }
//         return $to
//     }
//
//     openFrontierNode(type: NodeType, attrs: Attrs | null = null, content?: Fragment) {
//         let top = this.frontier[this.depth]
//         top.match = top.match.matchType(type)!
//         this.placed = addToFragment(this.placed, this.depth, Fragment.from(type.create(attrs, content)))
//         this.frontier.push({type, match: type.contentMatch})
//     }
//
//     closeFrontierNode() {
//         let open = this.frontier.pop()!
//         let add = open.match.fillBefore(Fragment.empty, true)!
//         if (add.childCount) this.placed = addToFragment(this.placed, this.frontier.length, add)
//     }
// }
// endregion
// Algorithm for 'placing' the elements of a slice into a gap:
//
// We consider the content of each node that is open to the left to be
// independently placeable. I.e. in <p("foo"), p("bar")>, when the
// paragraph on the left is open, "foo" can be placed (somewhere on
// the left side of the replacement gap) independently from p("bar").
//
// This class tracks the state of the placement progress in the
// following properties:
//
//  - `frontier` holds a stack of `{type, match}` objects that
//    represent the open side of the replacement. It starts at
//    `$from`, then moves forward as content is placed, and is finally
//    reconciled with `$to`.
//
//  - `unplaced` is a slice that represents the content that hasn't
//    been placed yet.
//
//  - `placed` is a fragment of placed content. Its open-start value
//    is implicit in `$from`, and its open-end value in `frontier`.
class Fitter(
    val _from: ResolvedPos,
    val _to: ResolvedPos,
    var unplaced: Slice
) {
    val frontier: MutableList<Frontier> = mutableListOf()
    var placed: Fragment = Fragment.empty

    init {
        for (i in 0.._from.depth) {
            val node = _from.node(i)
            this.frontier.add(
                Frontier(
                    type = node.type,
                    match = node.contentMatchAt(_from.indexAfter(i))
                )
            )
        }

        for (i in _from.depth downTo 1) {
            this.placed = Fragment.from(_from.node(i).copy(this.placed))
        }
    }

    val depth: Int
        get() = this.frontier.size - 1

    fun fit(): Step? {
        // As long as there's unplaced content, try to place some of it.
        // If that fails, either increase the open score of the unplaced
        // slice, or drop nodes from it, and then try again.
        while (this.unplaced.size != 0) {
            val fit = this.findFittable()
            if (fit != null) {
                this.placeNodes(fit)
            } else if (!this.openMore()) this.dropNode()
        }
        // When there's inline content directly after the frontier _and_
        // directly after `this.$to`, we must generate a `ReplaceAround`
        // step that pulls that content into the node after the frontier.
        // That means the fitting must be done to the end of the textblock
        // node after `this.$to`, not `this.$to` itself.
        val moveInline = this.mustMoveInline()
        val placedSize = this.placed.size - this.depth - this._from.depth
        val _from = this._from
        val _to = this.close(if (moveInline < 0) this._to else _from.doc.resolve(moveInline)) ?: return null

        // If closing to `$to` succeeded, create a step
        var content = this.placed
        var openStart = _from.depth
        var openEnd = _to.depth
        while (openStart != 0 && openEnd != 0 && content.childCount == 1) { // Normalize by dropping open parent nodes
            content = content.firstChild!!.content
            openStart--
            openEnd--
        }
        val slice = Slice(content, openStart, openEnd)
        if (moveInline > -1) {
            return ReplaceAroundStep(_from.pos, moveInline, this._to.pos, this._to.end(), slice, placedSize)
        }
        if (slice.size != 0 || _from.pos != this._to.pos) { // Don't generate no-op steps
            return ReplaceStep(_from.pos, _to.pos, slice)
        }
        return null
    }

    // Find a position on the start spine of `this.unplaced` that has
    // content that can be moved somewhere on the frontier. Returns two
    // depths, one for the slice and one for the frontier.
    @Suppress("NestedBlockDepth", "ComplexMethod", "ComplexCondition")
    fun findFittable(): Fittable? {
        // Only try wrapping nodes (pass 2) after finding a place without
        // wrapping failed.
        for (pass in 1..2) {
            for (sliceDepth in this.unplaced.openStart downTo 0) {
                var parent: Node? = null
                val fragment = if (sliceDepth != 0) {
                    parent = contentAt(this.unplaced.content, sliceDepth - 1).firstChild!!
                    parent.content
                } else {
                    this.unplaced.content
                }
                val first = fragment.firstChild
                for (frontierDepth in this.depth downTo 0) {
                    val (type, match) = this.frontier[frontierDepth].run { type to match }
                    var wrap: List<NodeType>? = null
                    var inject: Fragment? = null
                    // In pass 1, if the next node matches, or there is no next
                    // node but the parents look compatible, we've found a
                    // place.
                    if (pass == 1 && (
                            if (first != null) {
                                match?.matchType(first.type) != null ||
                                    (match?.fillBefore(Fragment.from(first), false).also { inject = it } != null)
                            } else {
                                parent != null && type.compatibleContent(parent.type)
                            }
                            )
                    ) {
                        return FittableImpl(sliceDepth, frontierDepth, parent, inject)
                    }
                    // In pass 2, look for a set of wrapping nodes that make
                    // `first` fit here.
                    else if (pass == 2 && first != null && match?.findWrapping(first.type).also { wrap = it } != null) {
                        return FittableImpl(sliceDepth, frontierDepth, parent, wrap = wrap)
                    }
                    // Don't continue looking further up if the parent node
                    // would fit here.
                    if (parent != null && match?.matchType(parent.type) != null) break
                }
            }
        }
        return null
    }

    fun openMore(): Boolean {
        val content = unplaced.content
        val openStart = unplaced.openStart
        val openEnd = unplaced.openEnd
        val inner = contentAt(content, openStart)
        if (inner.childCount == 0 || inner.firstChild!!.isLeaf) return false
        this.unplaced = Slice(
            content,
            openStart + 1,
            max(openEnd, if (inner.size + openStart >= content.size - openEnd) openStart + 1 else 0)
        )
        return true
    }

    fun dropNode() {
        val content = unplaced.content
        val openStart = unplaced.openStart
        val openEnd = unplaced.openEnd
        val inner = contentAt(content, openStart)
        if (inner.childCount <= 1 && openStart > 0) {
            val openAtEnd = content.size - openStart <= openStart + inner.size
            this.unplaced = Slice(
                dropFromFragment(content, openStart - 1, 1),
                openStart - 1,
                if (openAtEnd) openStart - 1 else openEnd
            )
        } else {
            this.unplaced = Slice(dropFromFragment(content, openStart, 1), openStart, openEnd)
        }
    }

    // Move content from the unplaced slice at `sliceDepth` to the frontier node at `frontierDepth`.
    // Close that frontier node when applicable.
    @Suppress("LongMethod", "ComplexMethod", "ComplexCondition")
    fun placeNodes(f: Fittable) {
        val sliceDepth = f.sliceDepth
        val frontierDepth = f.frontierDepth
        val parent = f.parent
        val inject = f.inject
        val wrap = f.wrap
        while (this.depth > frontierDepth) this.closeFrontierNode()
        wrap?.forEach {
            this.openFrontierNode(it)
        }

        val slice = this.unplaced
        val fragment = parent?.content ?: slice.content
        val openStart = slice.openStart - sliceDepth
        var taken = 0
        val add = mutableListOf<Node>()
        val frontier = this.frontier[frontierDepth]
        var match = frontier.match
        val type = frontier.type
        if (inject != null) {
            for (i in 0 until inject.childCount) {
                add.add(inject.child(i))
            }
            match = match?.matchFragment(inject)
        }
        // Computes the amount of (end) open nodes at the end of the
        // fragment. When 0, the parent is open, but no more. When
        // negative, nothing is open.
        var openEndCount = (fragment.size + sliceDepth) - (slice.content.size - slice.openEnd)
        // Scan over the fragment, fitting as many child nodes as
        // possible.
        while (taken < fragment.childCount) {
            val next = fragment.child(taken)
            val matches = match?.matchType(next.type) ?: break
            taken++
            if (taken > 1 || openStart == 0 || next.content.size != 0) { // Drop empty open nodes
                match = matches
                add.add(
                    closeNodeStart(
                        next.mark(type.allowedMarks(next.marks)),
                        if (taken == 1) openStart else 0,
                        if (taken == fragment.childCount) openEndCount else -1
                    )
                )
            }
        }
        val toEnd = taken == fragment.childCount
        if (!toEnd) openEndCount = -1

        this.placed = addToFragment(this.placed, frontierDepth, Fragment.from(add))
        this.frontier[frontierDepth].match = match

        // If the parent types match, and the entire node was moved, and
        // it's not open, close this frontier node right away.
        if (
            toEnd &&
            openEndCount < 0 &&
            parent != null &&
            parent.type == this.frontier[this.depth].type &&
            this.frontier.size > 1
        ) {
            this.closeFrontierNode()
        }

        // Add new frontier nodes for any open nodes at the end.
        var cur = fragment
        for (i in 0 until openEndCount) {
            val node = cur.lastChild!!
            this.frontier.add(Frontier(type = node.type, match = node.contentMatchAt(node.childCount)))
            cur = node.content
        }

        // Update `this.unplaced`. Drop the entire node from which we
        // placed it we got to its end, otherwise just drop the placed
        // nodes.
        this.unplaced = if (!toEnd) {
            Slice(dropFromFragment(slice.content, sliceDepth, taken), slice.openStart, slice.openEnd)
        } else if (sliceDepth == 0) {
            Slice.empty
        } else {
            Slice(
                content = dropFromFragment(slice.content, sliceDepth - 1, 1),
                openStart = sliceDepth - 1,
                openEnd = if (openEndCount < 0) slice.openEnd else sliceDepth - 1
            )
        }
    }

    fun mustMoveInline(): Int {
        if (!this._to.parent.isTextblock) return -1
        val top = this.frontier[this.depth]
        if (!top.type.isTextblock ||
            contentAfterFits(this._to, this._to.depth, top.type, top.match, false) == null
        ) {
            return -1
        }
        if (this._to.depth == this.depth) {
            if (findCloseLevel(_to)?.depth == this.depth) {
                return -1
            }
        }

        var depth = this._to.depth
        var after = this._to.after(depth)
        while (depth > 1 && after == this._to.end(--depth)) ++after
        return after
    }

    fun findCloseLevel(_to: ResolvedPos): CloseLevelResult? {
        scan@ for (i in min(this.depth, _to.depth) downTo 0) {
            val (match, type) = this.frontier[i].run { match to type }
            val dropInner = i < _to.depth && _to.end(i + 1) == _to.pos + (_to.depth - (i + 1))
            val fit = contentAfterFits(_to, i, type, match, dropInner) ?: continue
            for (d in i - 1 downTo 0) {
                val (match, type) = this.frontier[d].run { this.match to this.type }
                val matches = contentAfterFits(_to, d, type, match, true)
                if (matches == null || matches.childCount != 0) continue@scan
            }
            return CloseLevelResult(
                depth = i,
                fit = fit,
                move = if (dropInner) _to.doc.resolve(_to.after(i + 1)) else _to
            )
        }
        return null
    }

    data class CloseLevelResult(val depth: Int, val fit: Fragment, val move: ResolvedPos)

    fun close(_to: ResolvedPos): ResolvedPos? {
        val close = this.findCloseLevel(_to) ?: return null

        while (this.depth > close.depth) this.closeFrontierNode()
        if (close.fit.childCount != 0) {
            this.placed = addToFragment(this.placed, close.depth, close.fit)
        }
        val _to = close.move
        for (d in close.depth + 1.._to.depth) {
            val node = _to.node(d)
            val add = node.type.contentMatch.fillBefore(node.content, true, _to.index(d))
            this.openFrontierNode(node.type, node.attrs, add)
        }
        return _to
    }

    fun openFrontierNode(type: NodeType, attrs: Attrs? = null, content: Fragment? = null) {
        val top = this.frontier[this.depth]
        top.match = top.match?.matchType(type)
        this.placed = addToFragment(this.placed, this.depth, Fragment.from(type.create(attrs, content)))
        this.frontier.add(Frontier(type, match = type.contentMatch))
    }

    fun closeFrontierNode() {
        val open = this.frontier.removeLast()
        val add = open.match?.fillBefore(Fragment.empty, true)
        add?.let {
            if (it.childCount != 0) this.placed = addToFragment(this.placed, this.frontier.size, it)
        }
    }
}

data class Frontier(val type: NodeType, var match: ContentMatch?)

// function dropFromFragment(fragment: Fragment, depth: number, count: number): Fragment {
//     if (depth == 0) return fragment.cutByIndex(count, fragment.childCount)
//     return fragment.replaceChild(0, fragment.firstChild!.copy(dropFromFragment(fragment.firstChild!.content,
//          depth - 1, count)))
// }
fun dropFromFragment(fragment: Fragment, depth: Int, count: Int): Fragment {
    if (depth == 0) return fragment.cutByIndex(count, fragment.childCount)
    return fragment.replaceChild(
        0,
        fragment.firstChild!!.copy(dropFromFragment(fragment.firstChild!!.content, depth - 1, count))
    )
}

// function addToFragment(fragment: Fragment, depth: number, content: Fragment): Fragment {
//     if (depth == 0) return fragment.append(content)
//     return fragment.replaceChild(fragment.childCount - 1,
//         fragment.lastChild!.copy(addToFragment(fragment.lastChild!.content, depth - 1, content)))
// }
fun addToFragment(fragment: Fragment, depth: Int, content: Fragment): Fragment {
    if (depth == 0) return fragment.append(content)
    return fragment.replaceChild(
        fragment.childCount - 1,
        fragment.lastChild!!.copy(addToFragment(fragment.lastChild!!.content, depth - 1, content))
    )
}

// function contentAt(fragment: Fragment, depth: number) {
//     for (let i = 0; i < depth; i++) fragment = fragment.firstChild!.content
//     return fragment
// }
fun contentAt(fragment: Fragment, depth: Int): Fragment {
    var fragment = fragment
    for (i in 0 until depth) {
        fragment = fragment.firstChild!!.content
    }
    return fragment
}

// function closeNodeStart(node: Node, openStart: number, openEnd: number) {
//     if (openStart <= 0) return node
//     let frag = node.content
//             if (openStart > 1)
//                 frag = frag.replaceChild(0, closeNodeStart(frag.firstChild!, openStart - 1, frag.childCount == 1 ?
//                      openEnd - 1 : 0))
//     if (openStart > 0) {
//         frag = node.type.contentMatch.fillBefore(frag)!.append(frag)
//         if (openEnd <= 0)
//              frag = frag.append(node.type.contentMatch.matchFragment(frag)!.fillBefore(Fragment.empty, true)!)
//     }
//     return node.copy(frag)
// }
fun closeNodeStart(node: Node, openStart: Int, openEnd: Int): Node {
    if (openStart <= 0) return node
    var frag = node.content
    if (openStart > 1) {
        frag = frag.replaceChild(
            0,
            closeNodeStart(frag.firstChild!!, openStart - 1, if (frag.childCount == 1) openEnd - 1 else 0)
        )
    }
    if (openStart > 0) {
        frag = node.type.contentMatch.fillBefore(frag)!!.append(frag)
        if (openEnd <= 0) {
            frag = frag.append(node.type.contentMatch.matchFragment(frag)!!.fillBefore(Fragment.empty, true)!!)
        }
    }
    return node.copy(frag)
}

// function contentAfterFits($to: ResolvedPos, depth: number, type: NodeType, match: ContentMatch, open: boolean) {
//     let node = $to.node(depth), index = open ? $to.indexAfter(depth) : $to.index(depth)
//     if (index == node.childCount && !type.compatibleContent(node.type)) return null
//     let fit = match.fillBefore(node.content, true, index)
//     return fit && !invalidMarks(type, node.content, index) ? fit : null
// }
fun contentAfterFits(_to: ResolvedPos, depth: Int, type: NodeType, match: ContentMatch?, open: Boolean): Fragment? {
    val node = _to.node(depth)
    val index = if (open) _to.indexAfter(depth) else _to.index(depth)
    if (index == node.childCount && !type.compatibleContent(node.type)) return null
    val fit = match?.fillBefore(node.content, true, index)
    return if (fit != null && !invalidMarks(type, node.content, index)) fit else null
}

// function invalidMarks(type: NodeType, fragment: Fragment, start: number) {
//     for (let i = start; i < fragment.childCount; i++)
//     if (!type.allowsMarks(fragment.child(i).marks)) return true
//     return false
// }
fun invalidMarks(type: NodeType, fragment: Fragment, start: Int): Boolean {
    for (i in start until fragment.childCount) {
        if (!type.allowsMarks(fragment.child(i).marks)) return true
    }
    return false
}

// function definesContent(type: NodeType) {
//     return type.spec.defining || type.spec.definingForContent
// }
fun definesContent(type: NodeType) = type.spec.defining == true || type.spec.definingForContent == true

// export function replaceRange(tr: Transform, from: number, to: number, slice: Slice) {
//     if (!slice.size) return tr.deleteRange(from, to)
//
//     let $from = tr.doc.resolve(from), $to = tr.doc.resolve(to)
//     if (fitsTrivially($from, $to, slice))
//     return tr.step(new ReplaceStep(from, to, slice))
//
//     let targetDepths = coveredDepths($from, tr.doc.resolve(to))
//     // Can't replace the whole document, so remove 0 if it's present
//     if (targetDepths[targetDepths.length - 1] == 0) targetDepths.pop()
//     // Negative numbers represent not expansion over the whole node at
//     // that depth, but replacing from $from.before(-D) to $to.pos.
//     let preferredTarget = -($from.depth + 1)
//     targetDepths.unshift(preferredTarget)
//     // This loop picks a preferred target depth, if one of the covering
//     // depths is not outside of a defining node, and adds negative
//     // depths for any depth that has $from at its start and does not
//     // cross a defining node.
//     for (let d = $from.depth, pos = $from.pos - 1; d > 0; d--, pos--) {
//         let spec = $from.node(d).type.spec
//         if (spec.defining || spec.definingAsContext || spec.isolating) break
//         if (targetDepths.indexOf(d) > -1) preferredTarget = d
//         else if ($from.before(d) == pos) targetDepths.splice(1, 0, -d)
//     }
//     // Try to fit each possible depth of the slice into each possible
//     // target depth, starting with the preferred depths.
//     let preferredTargetIndex = targetDepths.indexOf(preferredTarget)
//
//     let leftNodes = [], preferredDepth = slice.openStart
//     for (let content = slice.content, i = 0;; i++) {
//         let node = content.firstChild!
//         leftNodes.push(node)
//         if (i == slice.openStart) break
//         content = node.content
//     }
//
//     // Back up preferredDepth to cover defining textblocks directly
//     // above it, possibly skipping a non-defining textblock.
//     for (let d = preferredDepth - 1; d >= 0; d--) {
//         let type = leftNodes[d].type, def = definesContent(type)
//         if (def && $from.node(preferredTargetIndex).type != type) preferredDepth = d
//         else if (def || !type.isTextblock) break
//     }
//
//     for (let j = slice.openStart; j >= 0; j--) {
//         let openDepth = (j + preferredDepth + 1) % (slice.openStart + 1)
//         let insert = leftNodes[openDepth]
//         if (!insert) continue
//         for (let i = 0; i < targetDepths.length; i++) {
//         // Loop over possible expansion levels, starting with the
//         // preferred one
//         let targetDepth = targetDepths[(i + preferredTargetIndex) % targetDepths.length], expand = true
//         if (targetDepth < 0) { expand = false; targetDepth = -targetDepth }
//         let parent = $from.node(targetDepth - 1), index = $from.index(targetDepth - 1)
//         if (parent.canReplaceWith(index, index, insert.type, insert.marks))
//             return tr.replace($from.before(targetDepth), expand ? $to.after(targetDepth) : to,
//         new Slice(closeFragment(slice.content, 0, slice.openStart, openDepth),
//         openDepth, slice.openEnd))
//     }
//     }
//
//     let startSteps = tr.steps.length
//             for (let i = targetDepths.length - 1; i >= 0; i--) {
//         tr.replace(from, to, slice)
//         if (tr.steps.length > startSteps) break
//         let depth = targetDepths[i]
//         if (depth < 0) continue
//         from = $from.before(depth); to = $to.after(depth)
//     }
// }
@Suppress("LongMethod", "ComplexMethod")
fun replaceRange(tr: Transform, from: Int, to: Int, slice: Slice): Transform? {
    if (slice.size == 0) return tr.deleteRange(from, to)
    val (_from, _to) = tr.doc.resolveAndLog(from, to) ?: return null
    if (fitsTrivially(_from, _to, slice)) {
        return tr.step(ReplaceStep(from, to, slice))
    }

    val targetDepths = coveredDepths(_from, tr.doc.resolve(to)).toMutableList()
    // Can't replace the whole document, so remove 0 if it's present
    if (targetDepths.getOrNull(targetDepths.size - 1) == 0) targetDepths.removeLast()
    // Negative numbers represent not expansion over the whole node at
    // that depth, but replacing from $from.before(-D) to $to.pos.
    var preferredTarget = -(_from.depth + 1)
    targetDepths.add(0, preferredTarget)
    // This loop picks a preferred target depth, if one of the covering
    // depths is not outside of a defining node, and adds negative
    // depths for any depth that has $from at its start and does not
    // cross a defining node.
    var d = _from.depth
    var pos = _from.pos - 1
    while (d > 0) {
        val spec = _from.node(d).type.spec
        if (spec.defining == true || spec.definingAsContext == true || spec.isolating == true) break
        if (targetDepths.indexOf(d) > -1) {
            preferredTarget = d
        } else if (_from.before(d) == pos) {
            targetDepths.add(1, -d)
        }
        d--
        pos--
    }
    // Try to fit each possible depth of the slice into each possible
    // target depth, starting with the preferred depths.
    val preferredTargetIndex = targetDepths.indexOf(preferredTarget)

    val leftNodes = mutableListOf<Node>()
    var preferredDepth = slice.openStart
    var content = slice.content
    var i = 0
    while (true) {
        val node = content.firstChild!!
        leftNodes.add(node)
        if (i == slice.openStart) break
        content = node.content
        i++
    }

    // Back up preferredDepth to cover defining textblocks directly
    // above it, possibly skipping a non-defining textblock.
    for (d in preferredDepth - 1 downTo 0) {
        val type = leftNodes[d].type
        val def = definesContent(type)
        if (def && _from.node(preferredTargetIndex).type != type) {
            preferredDepth = d
        } else if (def || !type.isTextblock) {
            break
        }
    }

    for (j in slice.openStart downTo 0) {
        val openDepth = (j + preferredDepth + 1) % (slice.openStart + 1)
        val insert = leftNodes[openDepth] ?: continue
        for (i in targetDepths.indices) {
            // Loop over possible expansion levels, starting with the
            // preferred one
            var targetDepth = targetDepths[(i + preferredTargetIndex) % targetDepths.size]
            var expand = true
            if (targetDepth < 0) {
                expand = false
                targetDepth = -targetDepth
            }
            val parent = _from.node(targetDepth - 1)
            val index = _from.index(targetDepth - 1)
            if (parent.canReplaceWith(index, index, insert.type, insert.marks)) {
                return tr.replace(
                    _from.before(targetDepth),
                    if (expand) _to.after(targetDepth) else to,
                    Slice(
                        closeFragment(slice.content, 0, slice.openStart, openDepth),
                        openDepth,
                        slice.openEnd
                    )
                )
            }
        }
    }

    val startSteps = tr.steps.size
    var from = from
    var to = to
    @Suppress("LoopWithTooManyJumpStatements")
    for (i in targetDepths.size - 1 downTo 0) {
        tr.replace(from, to, slice)
        if (tr.steps.size > startSteps) break
        val depth = targetDepths[i]
        if (depth < 0) continue
        from = _from.before(depth)
        to = _to.after(depth)
    }
    return null
}

// function closeFragment(fragment: Fragment, depth: number, oldOpen: number, newOpen: number, parent?: Node) {
//     if (depth < oldOpen) {
//         let first = fragment.firstChild!
//         fragment = fragment.replaceChild(0, first.copy(
//              closeFragment(first.content, depth + 1, oldOpen, newOpen, first)
//         ))
//     }
//     if (depth > newOpen) {
//         let match = parent!.contentMatchAt(0)!
//         let start = match.fillBefore(fragment)!.append(fragment)
//         fragment = start.append(match.matchFragment(start)!.fillBefore(Fragment.empty, true)!)
//     }
//     return fragment
// }
fun closeFragment(fragment: Fragment, depth: Int, oldOpen: Int, newOpen: Int, parent: Node? = null): Fragment {
    var fragment = fragment
    if (depth < oldOpen) {
        val first = fragment.firstChild!!
        fragment = fragment.replaceChild(
            0,
            first.copy(closeFragment(first.content, depth + 1, oldOpen, newOpen, first))
        )
    }
    if (depth > newOpen) {
        val match = parent!!.contentMatchAt(0)
        val start = match.fillBefore(fragment)!!.append(fragment)
        fragment = start.append(match.matchFragment(start)!!.fillBefore(Fragment.empty, true)!!)
    }
    return fragment
}

// export function replaceRangeWith(tr: Transform, from: number, to: number, node: Node) {
//     if (!node.isInline && from == to && tr.doc.resolve(from).parent.content.size) {
//         let point = insertPoint(tr.doc, from, node.type)
//         if (point != null) from = to = point
//     }
//     tr.replaceRange(from, to, new Slice(Fragment.from(node), 0, 0))
// }
fun replaceRangeWith(tr: Transform, from: Int, to: Int, node: Node) {
    var from = from
    var to = to
    if (!node.isInline && from == to && tr.doc.resolve(from).parent.content.size != 0) {
        val point = insertPoint(tr.doc, from, node.type)
        if (point != null) {
            to = point
            from = to
        }
    }
    tr.replaceRange(from, to, Slice(Fragment.from(node), 0, 0))
}

// export function deleteRange(tr: Transform, from: number, to: number) {
//     let $from = tr.doc.resolve(from), $to = tr.doc.resolve(to)
//     let covered = coveredDepths($from, $to)
//     for (let i = 0; i < covered.length; i++) {
//         let depth = covered[i], last = i == covered.length - 1
//         if ((last && depth == 0) || $from.node(depth).type.contentMatch.validEnd)
//             return tr.delete($from.start(depth), $to.end(depth))
//         if (depth > 0 && (last || $from.node(depth - 1).canReplace($from.index(depth - 1),
//              $to.indexAfter(depth - 1))))
//         return tr.delete($from.before(depth), $to.after(depth))
//     }
//     for (let d = 1; d <= $from.depth && d <= $to.depth; d++) {
//         if (from - $from.start(d) == $from.depth - d && to > $from.end(d) && $to.end(d) - to != $to.depth - d)
//             return tr.delete($from.before(d), to)
//     }
//     tr.delete(from, to)
// }
fun deleteRange(tr: Transform, from: Int, to: Int): Transform {
    val (_from, _to) = tr.doc.resolveAndLog(from, to) ?: return tr
    val covered = coveredDepths(_from, _to)
    for (i in 0 until covered.size) {
        val depth = covered[i]
        val last = i == covered.size - 1
        if ((last && depth == 0) || _from.node(depth).type.contentMatch.validEnd) {
            return tr.delete(_from.start(depth), _to.end(depth))
        }
        if (depth > 0 &&
            (
                last || _from.node(depth - 1)
                    .canReplace(_from.index(depth - 1), _to.indexAfter(depth - 1))
                )
        ) {
            return tr.delete(_from.before(depth), _to.after(depth))
        }
    }
    var d = 1

    while (d <= _from.depth && d <= _to.depth) {
        if (from - _from.start(d) == _from.depth - d && to > _from.end(d) && _to.end(d) - to != _to.depth - d) {
            return tr.delete(_from.before(d), to)
        }
        d++
    }
    return tr.delete(from, to)
}

// // Returns an array of all depths for which $from - $to spans the
// // whole content of the nodes at that depth.
// function coveredDepths($from: ResolvedPos, $to: ResolvedPos) {
//     let result = [], minDepth = Math.min($from.depth, $to.depth)
//     for (let d = minDepth; d >= 0; d--) {
//         let start = $from.start(d)
//         if (start < $from.pos - ($from.depth - d) ||
//         $to.end(d) > $to.pos + ($to.depth - d) ||
//         $from.node(d).type.spec.isolating ||
//         $to.node(d).type.spec.isolating) break
//         if (start == $to.start(d) ||
//             (d == $from.depth && d == $to.depth && $from.parent.inlineContent && $to.parent.inlineContent &&
//                     d && $to.start(d - 1) == start - 1))
//             result.push(d)
//     }
//     return result
// }
// Returns an array of all depths for which _from - _to spans the whole content of the nodes at that depth.
@Suppress("ComplexCondition")
fun coveredDepths(_from: ResolvedPos, _to: ResolvedPos): List<Int> {
    val result = mutableListOf<Int>()
    val minDepth = min(_from.depth, _to.depth)
    for (d in minDepth downTo 0) {
        val start = _from.start(d)
        if (start < _from.pos - (_from.depth - d) ||
            _to.end(d) > _to.pos + (_to.depth - d) ||
            _from.node(d).type.spec.isolating == true ||
            _to.node(d).type.spec.isolating == true
        ) {
            break
        }
        if (start == _to.start(d) ||
            (
                d == _from.depth && d == _to.depth && _from.parent.inlineContent && _to.parent.inlineContent &&
                    d != 0 && _to.start(d - 1) == start - 1
                )
        ) {
            result.add(d)
        }
    }
    return result.toList()
}
