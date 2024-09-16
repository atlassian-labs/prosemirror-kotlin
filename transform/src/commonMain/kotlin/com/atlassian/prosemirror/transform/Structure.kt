package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.ContentMatch
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.NodeRange
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.model.util.resolveSafe
import com.atlassian.prosemirror.util.verbose

fun canCut(node: Node, start: Int, end: Int) =
    (start == 0 || node.canReplace(start, node.childCount)) && (end == node.childCount || node.canReplace(0, end))

// Try to find a target depth to which the content in the given range can be lifted. Will not go
// across [isolating](#model.NodeSpec.isolating) parent nodes.
fun liftTarget(range: NodeRange): Int? {
    val parent = range.parent
    val content = parent.content.cutByIndex(range.startIndex, range.endIndex)
    var depth = range.depth
    while (true) {
        val node = range.from.node(depth)
        val index = range.from.index(depth)
        val endIndex = range.to.indexAfter(depth)
        if (depth < range.depth && node.canReplace(index, endIndex, content)) {
            return depth
        }
        if (depth == 0 || node.type.spec.isolating == true || !canCut(node, index, endIndex)) break
        --depth
    }
    return null
}

fun lift(tr: Transform, range: NodeRange, target: Int) {
    val from = range.from
    val to = range.to
    val depth = range.depth

    val gapStart = from.before(depth + 1)
    val gapEnd = to.after(depth + 1)
    var start = gapStart
    var end = gapEnd

    var before = Fragment.empty
    var openStart = 0
    var splitting = false
    for (d in depth downTo target + 1) {
        if (splitting || from.index(d) > 0) {
            splitting = true
            before = Fragment.from(from.node(d).copy(before))
            openStart++
        } else {
            start--
        }
    }
    var after = Fragment.empty
    var openEnd = 0
    splitting = false
    for (d in depth downTo target + 1) {
        if (splitting || to.after(d + 1) < to.end(d)) {
            splitting = true
            after = Fragment.from(to.node(d).copy(after))
            openEnd++
        } else {
            end++
        }
    }

    tr.step(
        ReplaceAroundStep(
            from = start,
            to = end,
            gapFrom = gapStart,
            gapTo = gapEnd,
            slice = Slice(before.append(after), openStart, openEnd),
            insert = before.size - openStart,
            structure = true
        )
    )
}

// Try to find a valid way to wrap the content in the given range in a node of the given type. May
// introduce extra nodes around and inside the wrapper node, if necessary. Returns null if no valid
// wrapping could be found. When `innerRange` is given, that range's content is used as the content
// to fit into the wrapping, instead of the content of `range`.
fun findWrapping(
    range: NodeRange,
    nodeType: NodeType,
    attrs: Attrs? = null,
    innerRange: NodeRange = range
): List<NodeBase>? {
    val around = findWrappingOutside(range, nodeType) ?: return null
    val inner = findWrappingInside(innerRange, nodeType) ?: return null
    return around.map(::withAttrs) + NodeBase(type = nodeType, attrs) + inner.map(::withAttrs)
}

fun withAttrs(type: NodeType) = NodeBase(type, null)

fun findWrappingOutside(range: NodeRange, type: NodeType): List<NodeType>? {
    val parent = range.parent
    val startIndex = range.startIndex
    val endIndex = range.endIndex
    val around = parent.contentMatchAt(startIndex).findWrapping(type) ?: return null
    val outer = if (around.isNotEmpty()) around[0] else type
    return if (parent.canReplaceWith(startIndex, endIndex, outer)) around else null
}

fun findWrappingInside(range: NodeRange, type: NodeType): List<NodeType>? {
    val parent = range.parent
    val startIndex = range.startIndex
    val endIndex = range.endIndex
    val inner = parent.child(startIndex)
    val inside = type.contentMatch.findWrapping(inner.type) ?: return null
    val lastType = if (inside.isNotEmpty()) inside.last() else type
    var innerMatch: ContentMatch? = lastType.contentMatch
    var i = startIndex
    while (innerMatch != null && i < endIndex) {
        innerMatch = innerMatch.matchType(parent.child(i).type)
        i++
    }
    if (innerMatch == null || !innerMatch.validEnd) return null
    return inside
}

fun wrap(tr: Transform, range: NodeRange, wrappers: List<NodeBase>) {
    var content = Fragment.empty
    for (i in wrappers.size - 1 downTo 0) {
        if (content.size != 0) {
            val match = wrappers[i].type.contentMatch.matchFragment(content)
            if (match == null || !match.validEnd) {
                throw RangeError(
                    "Wrapper type given to Transform.wrap does not form valid content of its parent wrapper"
                )
            }
        }
        content = Fragment.from(wrappers[i].type.create(wrappers[i].attrs, content))
    }

    val start = range.start
    val end = range.end
    tr.step(ReplaceAroundStep(start, end, start, end, Slice(content, 0, 0), wrappers.size, true))
}

fun setBlockType(tr: Transform, from: Int, to: Int, type: NodeType, attrs: Attrs?) {
    if (!type.isTextblock) throw RangeError("Type given to setBlockType should be a textblock")
    val mapFrom = tr.steps.size
    tr.doc.nodesBetween(from, to, { node, pos, parent, index ->
        if (
            node.isTextblock &&
            !node.hasMarkup(type, attrs) &&
            canChangeType(tr.doc, tr.mapping.slice(mapFrom).map(pos), type)
        ) {
            // Ensure all markup that isn't allowed in the new node type is cleared
            tr.clearIncompatible(tr.mapping.slice(mapFrom).map(pos, 1), type)
            val mapping = tr.mapping.slice(mapFrom)
            val startM = mapping.map(pos, 1)
            val endM = mapping.map(pos + node.nodeSize, 1)
            tr.step(
                ReplaceAroundStep(
                    from = startM,
                    to = endM,
                    gapFrom = startM + 1,
                    gapTo = endM - 1,
                    slice = Slice(Fragment.from(type.create(attrs, null as Fragment?, node.marks)), 0, 0),
                    insert = 1,
                    structure = true
                )
            )
            false
        } else {
            true
        }
    })
}

fun canChangeType(doc: Node, pos: Int, type: NodeType): Boolean {
    val resolvedPos = doc.resolve(pos)
    val index = resolvedPos.index()
    return resolvedPos.parent.canReplaceWith(index, index + 1, type)
}

// Change the type, attributes, and/or marks of the node at `pos`. When `type` isn't given, the
// existing node type is preserved,
fun setNodeMarkup(tr: Transform, pos: Int, type: NodeType?, attrs: Attrs?, marks: List<Mark>?): Transform {
    val node = tr.doc.nodeAt(pos) ?: throw RangeError("No node at given position")
    val thisType = type ?: node.type
    val newNode = thisType.create(attrs, null as Fragment?, marks ?: node.marks)
    if (node.isLeaf) {
        return tr.replaceWith(pos, pos + node.nodeSize, newNode)
    }

    if (!thisType.validContent(node.content)) {
        throw RangeError(
            if (verbose) {
                "Invalid content for node type ${thisType.name}: ${node.content}"
            } else {
                "Invalid content for node type ${thisType.name}"
            }
        )
    }

    return tr.step(
        ReplaceAroundStep(
            pos,
            pos + node.nodeSize,
            pos + 1,
            pos + node.nodeSize - 1,
            Slice(Fragment.from(newNode), 0, 0),
            1,
            true
        )
    )
}

// Check whether splitting at the given position is allowed.
fun canSplit(doc: Node, pos: Int, depth: Int = 1, typesAfter: List<NodeBase?>? = null): Boolean {
    val resolvedPos = doc.resolve(pos)
    val base = resolvedPos.depth - depth
    val innerType = typesAfter?.lastOrNull() ?: resolvedPos.parent
    if (base < 0 || resolvedPos.parent.type.spec.isolating == true ||
        !resolvedPos.parent.canReplace(resolvedPos.index(), resolvedPos.parent.childCount) ||
        !innerType.type.validContent(
            resolvedPos.parent.content.cutByIndex(resolvedPos.index(), resolvedPos.parent.childCount)
        )
    ) {
        return false
    }
    var d = resolvedPos.depth - 1
    var i = depth - 2
    while (d > base) {
        val node = resolvedPos.node(d)
        val index = resolvedPos.index(d)
        if (node.type.spec.isolating == true) return false
        var rest = node.content.cutByIndex(index, node.childCount)
        val after = typesAfter?.get(i) ?: node
        if (after != node) rest = rest.replaceChild(0, after.type.create(after.attrs))
        if (!node.canReplace(index + 1, node.childCount) || !after.type.validContent(rest)) {
            return false
        }
        d--
        i--
    }
    val index = resolvedPos.indexAfter(base)
    val baseType = typesAfter?.firstOrNull()
    return resolvedPos.node(base).canReplaceWith(index, index, baseType?.type ?: resolvedPos.node(base + 1).type)
}

fun split(tr: Transform, pos: Int, depth: Int = 1, typesAfter: List<NodeBase?>?): Transform {
    val thisPos = tr.doc.resolveSafe(pos) ?: return tr
    var before = Fragment.empty
    var after = Fragment.empty
    var d = thisPos.depth
    val e = thisPos.depth - depth
    var i = depth - 1
    while (d > e) {
        before = Fragment.from(thisPos.node(d).copy(before))
        val typeAfter = typesAfter?.get(i)
        after = Fragment.from(typeAfter?.type?.create(typeAfter.attrs, after) ?: thisPos.node(d).copy(after))
        d--
        i--
    }
    return tr.step(ReplaceStep(pos, pos, Slice(before.append(after), depth, depth), true))
}

// Test whether the blocks before and after a given position can be joined.
fun canJoin(doc: Node, pos: Int): Boolean {
    val thisPos = doc.resolve(pos)
    val index = thisPos.index()
    return joinable(thisPos.nodeBefore, thisPos.nodeAfter) && thisPos.parent.canReplace(index, index + 1)
}

fun joinable(a: Node?, b: Node?) = a != null && b != null && !a.isLeaf && a.canAppend(b)

// Find an ancestor of the given position that can be joined to the block before (or after if `dir`
// is positive). Returns the joinable point, if any.
fun joinPoint(doc: Node, pos: Int, dir: Int = -1): Int? {
    var pos = pos
    val thisPos = doc.resolve(pos)
    var d = thisPos.depth
    while (true) {
        var before: Node?
        val after: Node?
        var index = thisPos.index(d)
        if (d == thisPos.depth) {
            before = thisPos.nodeBefore
            after = thisPos.nodeAfter
        } else if (dir > 0) {
            before = thisPos.node(d + 1)
            index++
            after = thisPos.node(d).maybeChild(index)
        } else {
            before = thisPos.node(d).maybeChild(index - 1)
            after = thisPos.node(d + 1)
        }
        if (before != null && !before.isTextblock && joinable(before, after) &&
            thisPos.node(d).canReplace(index, index + 1)
        ) {
            return pos
        }
        if (d == 0) break
        pos = if (dir < 0) thisPos.before(d) else thisPos.after(d)
        d--
    }
    return null
}

fun join(tr: Transform, pos: Int, depth: Int): Transform {
    val step = ReplaceStep(pos - depth, pos + depth, Slice.empty, true)
    return tr.step(step)
}

// Try to find a point where a node of the given type can be inserted near `pos`, by searching up
// the node hierarchy when `pos` itself isn't a valid place but is at the start or end of a node.
// Return null if no position was found.
fun insertPoint(doc: Node, pos: Int, nodeType: NodeType): Int? {
    val thisPos = doc.resolve(pos)
    if (thisPos.parent.canReplaceWith(thisPos.index(), thisPos.index(), nodeType)) return pos

    if (thisPos.parentOffset == 0) {
        for (d in thisPos.depth - 1 downTo 0) {
            val index = thisPos.index(d)
            if (thisPos.node(d).canReplaceWith(index, index, nodeType)) return thisPos.before(d + 1)
            if (index > 0) return null
        }
    }
    if (thisPos.parentOffset == thisPos.parent.content.size) {
        for (d in thisPos.depth - 1 downTo 0) {
            val index = thisPos.indexAfter(d)
            if (thisPos.node(d).canReplaceWith(index, index, nodeType)) return thisPos.after(d + 1)
            if (index < thisPos.node(d).childCount) return null
        }
    }
    return null
}

// Finds a position at or around the given position where the given slice can be inserted. Will look
// at parent nodes' nearest boundary and try there, even if the original position wasn't directly at
// the start or end of that node. Returns null when no position was found.
@Suppress("NestedBlockDepth", "ComplexMethod")
fun dropPoint(doc: Node, pos: Int, slice: Slice): Int? {
    val thisPos = doc.resolve(pos)
    if (slice.content.size == 0) return pos
    var content = slice.content
    for (i in 0 until slice.openStart) {
        content = content.firstChild!!.content
    }
    var pass = 1
    while (pass <= (if (slice.openStart == 0 && slice.size != 0) 2 else 1)) {
        for (d in thisPos.depth downTo 0) {
            val bias =
                if (d == thisPos.depth) {
                    0
                } else if (thisPos.pos <= (thisPos.start(d + 1) + thisPos.end(d + 1)) / 2) {
                    -1
                } else {
                    1
                }
            val insertPos = thisPos.index(d) + (if (bias > 0) 1 else 0)
            val parent = thisPos.node(d)
            val fits: Boolean? = if (pass == 1) {
                parent.canReplace(insertPos, insertPos, content)
            } else {
                val wrapping = parent.contentMatchAt(insertPos).findWrapping(content.firstChild!!.type)
                wrapping?.let { parent.canReplaceWith(insertPos, insertPos, it.first()) }
            }
            if (fits == true) {
                return if (bias == 0) thisPos.pos else if (bias < 0) thisPos.before(d + 1) else thisPos.after(d + 1)
            }
        }
        pass++
    }
    return null
}
