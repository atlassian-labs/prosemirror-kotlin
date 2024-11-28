package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.ContentMatch
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.ResolvedPos
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.model.util.resolveSafe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ‘Fit’ a slice into a given position in the document, producing a [step](#transform.Step) that
// inserts it. Will return null if there's no meaningful way to insert the slice here, or inserting
// it would be a no-op (an empty slice over an empty range).
fun replaceStep(doc: Node, from: Int, to: Int = from, slice: Slice = Slice.empty): Step? {
    if (from == to && slice.size == 0) return null
    val (resolvedFrom, resolvedTo) = doc.resolveSafe(from, to) ?: return null
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

    @Suppress("ktlint:standard:property-naming")
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
        var startDepth = this.unplaced.openStart
        var cur = this.unplaced.content
        var d = 0
        var openEnd = this.unplaced.openEnd
        while (d < startDepth) {
            val node = cur.firstChild!!
            if (cur.childCount > 1) openEnd = 0
            if (node.type.spec.isolating == true && openEnd <= d) {
                startDepth = d
                break
            }
            cur = node.content
            d++
        }

        // Only try wrapping nodes (pass 2) after finding a place without
        // wrapping failed.
        for (pass in 1..2) {
            val start = if (pass == 1) startDepth else this.unplaced.openStart
            for (sliceDepth in start downTo 0) {
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
                    } else if (
                        pass == 2 && first != null && match?.findWrapping(first.type).also { wrap = it } != null
                    ) {
                        // In pass 2, look for a set of wrapping nodes that make
                        // `first` fit here.
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

    @Suppress("ktlint:standard:property-naming")
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

fun dropFromFragment(fragment: Fragment, depth: Int, count: Int): Fragment {
    if (depth == 0) return fragment.cutByIndex(count, fragment.childCount)
    return fragment.replaceChild(
        0,
        fragment.firstChild!!.copy(dropFromFragment(fragment.firstChild!!.content, depth - 1, count))
    )
}

fun addToFragment(fragment: Fragment, depth: Int, content: Fragment): Fragment {
    if (depth == 0) return fragment.append(content)
    return fragment.replaceChild(
        fragment.childCount - 1,
        fragment.lastChild!!.copy(addToFragment(fragment.lastChild!!.content, depth - 1, content))
    )
}

fun contentAt(fragment: Fragment, depth: Int): Fragment {
    var fragment = fragment
    for (i in 0 until depth) {
        fragment = fragment.firstChild!!.content
    }
    return fragment
}

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

fun contentAfterFits(_to: ResolvedPos, depth: Int, type: NodeType, match: ContentMatch?, open: Boolean): Fragment? {
    val node = _to.node(depth)
    val index = if (open) _to.indexAfter(depth) else _to.index(depth)
    if (index == node.childCount && !type.compatibleContent(node.type)) return null
    val fit = match?.fillBefore(node.content, true, index)
    return if (fit != null && !invalidMarks(type, node.content, index)) fit else null
}

fun invalidMarks(type: NodeType, fragment: Fragment, start: Int): Boolean {
    for (i in start until fragment.childCount) {
        if (!type.allowsMarks(fragment.child(i).marks)) return true
    }
    return false
}

fun definesContent(type: NodeType) = type.spec.defining == true || type.spec.definingForContent == true

@Suppress("LongMethod", "ComplexMethod")
fun replaceRange(tr: Transform, from: Int, to: Int, slice: Slice): Transform? {
    if (slice.size == 0) return tr.deleteRange(from, to)
    val (_from, _to) = tr.doc.resolveSafe(from, to) ?: return null
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

    val leftNodes = mutableListOf<Node?>()
    var preferredDepth = slice.openStart
    var content = slice.content
    var i = 0
    while (true) {
        val node = content.firstChild
        leftNodes.add(node)
        if (i == slice.openStart) break
        content = node!!.content
        i++
    }

    // Back up preferredDepth to cover defining textblocks directly
    // above it, possibly skipping a non-defining textblock.
    for (d in preferredDepth - 1 downTo 0) {
        val leftNode = leftNodes[d] ?: continue
        val def = definesContent(leftNode.type)
        if (def && !leftNode.sameMarkup(_from.node(abs(preferredTarget) - 1))) {
            preferredDepth = d
        } else if (def || !leftNode.type.isTextblock) {
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

fun deleteRange(tr: Transform, from: Int, to: Int): Transform {
    val (_from, _to) = tr.doc.resolveSafe(from, to) ?: return tr
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
