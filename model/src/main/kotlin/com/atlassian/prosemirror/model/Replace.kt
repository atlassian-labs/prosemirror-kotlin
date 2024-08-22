package com.atlassian.prosemirror.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Error type raised by [`Node.replace`](#model.Node.replace) when given an invalid replacement.
class ReplaceError(override val message: String, cause: Throwable? = null) : Error(message, cause)

// A slice represents a piece cut out of a larger document. It stores not only a fragment, but also
// the depth up to which nodes on both side are ‘open’ (cut through).
@Suppress("EqualsWithHashCodeExist")
data class Slice(
    // Create a slice. When specifying a non-zero open depth, you must make sure that there are
    // nodes of at least that depth at the appropriate side of the fragment—i.e. if the fragment is
    // an empty paragraph node, `openStart` and `openEnd` can't be greater than 1.
    //
    // It is not necessary for the content of open nodes to conform to the schema's content
    // constraints, though it should be a valid start/end/middle for such a node, depending on which
    // sides are open.

    // The slice's content.
    val content: Fragment,
    // The open depth at the start of the fragment.
    val openStart: Int,
    // The open depth at the end.
    val openEnd: Int
) {

    // The size this slice would add when inserted into a document.
    val size: Int
        get() = this.content.size - this.openStart - this.openEnd

    fun insertAt(pos: Int, fragment: Fragment): Slice? {
        val content = insertInto(this.content, pos + this.openStart, fragment) ?: return null
        return Slice(content, this.openStart, this.openEnd)
    }

    fun removeBetween(from: Int, to: Int): Slice {
        return Slice(
            removeRange(this.content, from + this.openStart, to + this.openStart),
            this.openStart,
            this.openEnd
        )
    }

    // Tests whether this slice is equal to another slice.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Slice) return false

        if (content != other.content) return false
        if (openStart != other.openStart) return false
        if (openEnd != other.openEnd) return false

        return true
    }

    override fun toString(): String {
        return "$content($openStart,$openEnd)"
    }

    // Convert a slice to a JSON-serializable representation.
    fun toJSON(): JsonObject? {
        if (this.content.size == 0) return null
        return buildJsonObject {
            put("content", content.toJSON())
            if (openStart > 0) put("openStart", openStart)
            if (openEnd > 0) put("openEnd", openEnd)
        }
    }

    companion object {
        // The empty slice.
        val empty = Slice(Fragment.empty, 0, 0)

        // Deserialize a slice from its JSON representation.
        fun fromJSON(schema: Schema, json: JsonObject?): Slice {
            if (json == null) return empty
            val openStart: Int = json["openStart"]?.jsonPrimitive?.int ?: 0
            val openEnd: Int = json["openEnd"]?.jsonPrimitive?.int ?: 0
            return Slice(Fragment.fromJSON(schema, json["content"]!!.jsonArray), openStart, openEnd)
        }

        // Create a slice from a fragment by taking the maximum possible open value on both side of
        // the fragment.
        fun maxOpen(fragment: Fragment, openIsolating: Boolean = true): Slice {
            var openStart = 0
            var openEnd = 0
            var n = fragment.firstChild
            while (n != null && !n.isLeaf && (openIsolating || n.type.spec.isolating == false)) {
                openStart++
                n = n.firstChild
            }
            n = fragment.lastChild
            while (n != null && !n.isLeaf && (openIsolating || n.type.spec.isolating == false)) {
                openEnd++
                n = n.lastChild
            }
            return Slice(fragment, openStart, openEnd)
        }
    }
}

fun removeRange(content: Fragment, from: Int, to: Int): Fragment {
    val (index, offset) = content.findIndex(from).run { index to offset }
    val child = content.maybeChild(index)
    val (indexTo, offsetTo) = content.findIndex(to).run { this.index to this.offset }
    if (offset == from || child!!.isText) {
        if (offsetTo != to && !content.child(indexTo).isText) throw RangeError("Removing non-flat range")
        return content.cut(0, from).append(content.cut(to))
    }
    if (index != indexTo) throw RangeError("Removing non-flat range")
    return content.replaceChild(index, child.copy(removeRange(child.content, from - offset - 1, to - offset - 1)))
}

@Suppress("ReturnCount")
fun insertInto(content: Fragment, dist: Int, insert: Fragment, parent: Node? = null): Fragment? {
    val (index, offset) = content.findIndex(dist).run { index to offset }
    val child = content.maybeChild(index)
    if (offset == dist || child!!.isText) {
        if (parent != null && !parent.canReplace(index, index, insert)) return null
        return content.cut(0, dist).append(insert).append(content.cut(dist))
    }
    val inner = insertInto(child.content, dist - offset - 1, insert, null) ?: return null
    return content.replaceChild(index, child.copy(inner))
}

fun replace(_from: ResolvedPos, _to: ResolvedPos, slice: Slice): Node {
    if (slice.openStart > _from.depth) {
        throw ReplaceError("Inserted content deeper than insertion position")
    }
    if (_from.depth - slice.openStart != _to.depth - slice.openEnd) {
        throw ReplaceError("Inconsistent open depths")
    }
    return replaceOuter(_from, _to, slice, 0)
}

fun replaceOuter(_from: ResolvedPos, _to: ResolvedPos, slice: Slice, depth: Int): Node {
    val index = _from.index(depth)
    val node = _from.node(depth)
    return if (index == _to.index(depth) && depth < _from.depth - slice.openStart) {
        val inner = replaceOuter(_from, _to, slice, depth + 1)
        node.copy(node.content.replaceChild(index, inner))
    } else if (slice.content.size == 0) {
        close(node, replaceTwoWay(_from, _to, depth))
    } else if (slice.openStart == 0 && slice.openEnd == 0 && _from.depth == depth && _to.depth == depth) {
        // Simple, flat case
        val parent = _from.parent
        val content = parent.content
        val modified = content.cut(0, _from.parentOffset).append(slice.content).append(content.cut(_to.parentOffset))
        close(parent, modified)
    } else {
        val (start, end) = prepareSliceForReplace(slice, _from)
        close(node, replaceThreeWay(_from, start, end, _to, depth))
    }
}

fun checkJoin(main: Node, sub: Node) {
    if (!sub.type.compatibleContent(main.type)) {
        throw ReplaceError("Cannot join ${sub.type.name} onto ${main.type.name}")
    }
}

fun joinable(_before: ResolvedPos, _after: ResolvedPos, depth: Int): Node {
    val node = _before.node(depth)
    checkJoin(node, _after.node(depth))
    return node
}

fun addNode(child: Node, target: MutableList<Node>) {
    val last = target.size - 1
    if (last >= 0 && child.isText && child.sameMarkup(target[last])) {
        target[last] = (child as TextNode).withText(target[last].text + child.text)
    } else {
        target.add(child)
    }
}

fun addRange(_start: ResolvedPos?, _end: ResolvedPos?, depth: Int, target: MutableList<Node>) {
    val node = (_end ?: _start)!!.node(depth)
    var startIndex = 0
    val endIndex = _end?.index(depth) ?: node.childCount
    if (_start != null) {
        startIndex = _start.index(depth)
        if (_start.depth > depth) {
            startIndex++
        } else if (_start.textOffset != 0) {
            addNode(_start.nodeAfter!!, target)
            startIndex++
        }
    }
    for (i in startIndex until endIndex) addNode(node.child(i), target)
    if (_end != null && _end.depth == depth && _end.textOffset != 0) {
        addNode(_end.nodeBefore!!, target)
    }
}

fun close(node: Node, content: Fragment): Node {
    if (!node.type.validContent(content)) {
        throw ReplaceError("Invalid content for node ${node.type.name} content: ${content.toJSON()}")
    }
    return node.copy(content)
}

fun replaceThreeWay(
    _from: ResolvedPos,
    _start: ResolvedPos,
    _end: ResolvedPos,
    _to: ResolvedPos,
    depth: Int
): Fragment {
    val openStart = if (_from.depth > depth) joinable(_from, _start, depth + 1) else null
    val openEnd = if (_to.depth > depth) joinable(_end, _to, depth + 1) else null

    val content = mutableListOf<Node>()
    addRange(null, _from, depth, content)
    if (openStart != null && openEnd != null && _start.index(depth) == _end.index(depth)) {
        checkJoin(openStart, openEnd)
        addNode(close(openStart, replaceThreeWay(_from, _start, _end, _to, depth + 1)), content)
    } else {
        if (openStart != null) {
            addNode(close(openStart, replaceTwoWay(_from, _start, depth + 1)), content)
        }
        addRange(_start, _end, depth, content)
        if (openEnd != null) {
            addNode(close(openEnd, replaceTwoWay(_end, _to, depth + 1)), content)
        }
    }
    addRange(_to, null, depth, content)
    return Fragment(content)
}

fun replaceTwoWay(_from: ResolvedPos, _to: ResolvedPos, depth: Int): Fragment {
    val content = mutableListOf<Node>()
    addRange(null, _from, depth, content)
    if (_from.depth > depth) {
        val type = joinable(_from, _to, depth + 1)
        addNode(close(type, replaceTwoWay(_from, _to, depth + 1)), content)
    }
    addRange(_to, null, depth, content)
    return Fragment(content)
}

fun prepareSliceForReplace(slice: Slice, _along: ResolvedPos): Pair<ResolvedPos, ResolvedPos> {
    val extra = _along.depth - slice.openStart
    val parent = _along.node(extra)
    var node = parent.copy(slice.content)
    for (i in extra - 1 downTo 0) {
        node = _along.node(i).copy(Fragment.from(node))
    }
    return node.resolveNoCache(slice.openStart + extra) to
        node.resolveNoCache(node.content.size - slice.openEnd - extra)
}
