package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.util.slice
import com.atlassian.prosemirror.util.verbose
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject

// A fragment represents a node's collection of child nodes.
// Like nodes, fragments are persistent data structures, and  should not mutate them or their
// content. Rather, you create  instances whenever needed. The API tries to make this easy.
@Suppress("EqualsWithHashCodeExist")
class Fragment {
    // The size of the fragment, which is the total of the size of its content nodes.
    val size: Int
    internal val content: List<Node>

    // The first child of the fragment, or `null` if it is empty.
    val firstChild: Node?
        get() = content.firstOrNull()

    // The last child of the fragment, or `null` if it is empty.
    val lastChild: Node?
        get() = content.lastOrNull()

    // The number of child nodes in this fragment.
    val childCount: Int
        get() = content.size

    internal constructor(
        content: List<Node>,
        size: Int? = null
    ) {
        this.content = content
        this.size = size ?: content.sumOf { it.nodeSize }
    }

    // Invoke a callback for all descendant nodes between the given two positions (relative to start
    // of this fragment). Doesn't descend into a node when the callback returns `false`.
    // Terminate evaluating to true will immediately stop searching; useful if you want first result only
    fun nodesBetween(
        from: Int,
        to: Int,
        f: (node: Node, start: Int, parent: Node?, index: Int) -> Boolean,
        nodeStart: Int = 0,
        parent: Node?,
        terminate: () -> Boolean = { false }
    ) {
        var i = 0
        var pos = 0
        while (pos < to && i < this.content.size) {
            val child = this.content[i]
            val end = pos + child.nodeSize
            if (end > from && f(child, nodeStart + pos, parent, i) && child.content.size > 0) {
                val start = pos + 1
                child.nodesBetween(
                    max(0, from - start),
                    min(child.content.size, to - start),
                    f,
                    nodeStart + start,
                    terminate
                )
            }
            if (terminate()) return
            pos = end
            i++
        }
    }

    // Call the given callback for every descendant node. `pos` will be relative to the start of the
    // fragment. The callback may return `false` to prevent traversal of a given node's children.
    fun descendants(f: (node: Node, pos: Int, parent: Node?, index: Int) -> Boolean) {
        this.nodesBetween(0, this.size, f, parent = null)
    }

    fun descendants(f: (node: Node, pos: Int, parent: Node?, index: Int) -> Boolean, terminate: () -> Boolean) {
        this.nodesBetween(0, this.size, f, parent = null, terminate = terminate)
    }

    // Extract the text between `from` and `to`. See the same method on [`Node`](#model.Node.textBetween).
    fun textBetween(
        from: Int,
        to: Int,
        blockSeparator: String?,
        leafText: String?
    ): String {
        return if (leafText != null) {
            textBetween(from, to, blockSeparator) { leafText }
        } else {
            textBetween(from, to, blockSeparator, null as ((leafNode: Node) -> String?)?)
        }
    }

    fun textBetween(
        from: Int,
        to: Int,
        blockSeparator: String?,
        leafText: ((leafNode: Node) -> String?)?
    ): String {
        var text = ""
        var first = true
        val func: (node: Node, start: Int, parent: Node?, index: Int) -> Boolean = { node, pos, parent, index ->
            val nodeText = when {
                node.isText -> node.text?.slice(max(from, pos) - pos, to - pos) ?: ""
                !node.isLeaf -> ""
                leafText != null -> leafText(node)
                node.type.spec.leafText != null -> node.type.spec.leafText!!.invoke(node)
                else -> ""
            }
            if (node.isBlock && (node.isLeaf && nodeText?.isNotEmpty() == true || node.isTextblock) && blockSeparator != null) {
                if (first) {
                    first = false
                } else {
                    text += blockSeparator
                }
            }
            text += nodeText
            true
        }
        this.nodesBetween(
            from,
            to,
            func,
            0,
            null
        )
        return text
    }

    // Create a new fragment containing the combined content of this fragment and the other.
    fun append(other: Fragment): Fragment {
        if (other.size == 0) return this
        if (this.size == 0) return other
        val last = this.lastChild!!
        val first = other.firstChild!!
        val content = this.content.toMutableList()
        var i = 0
        if (last.isText && last.sameMarkup(first)) {
            content[content.size - 1] = (last as TextNode).withText(last.text + first.text)
            i = 1
        }
        for (j in i until other.content.size) {
            content.add(other.content[j])
        }
        return Fragment(content, this.size + other.size)
    }

    // Cut out the sub-fragment between the two given positions.
    @Suppress("NestedBlockDepth")
    fun cut(from: Int, to: Int = this.size): Fragment {
        if (from == 0 && to == this.size) return this
        val result = mutableListOf<Node>()
        var size = 0
        if (to > from) {
            var i = 0
            var pos = 0
            while (pos < to) {
                var child = this.content[i]
                val end = pos + child.nodeSize
                if (end > from) {
                    if (pos < from || end > to) {
                        child = if (child.isText) {
                            child.cut(max(0, from - pos), min(child.text!!.length, to - pos))
                        } else {
                            child.cut(max(0, from - pos - 1), min(child.content.size, to - pos - 1))
                        }
                    }
                    result.add(child)
                    size += child.nodeSize
                }
                pos = end
                i++
            }
        }
        return Fragment(result, size)
    }

    fun cutByIndex(from: Int, to: Int): Fragment {
        if (from == to) return empty
        if (from == 0 && to == this.content.size) return this
        return Fragment(this.content.slice(from until to), null)
    }

    // Create a new fragment in which the node at the given index is replaced by the given node.
    fun replaceChild(index: Int, node: Node): Fragment {
        val current = this.content[index]
        if (current == node) return this
        val copy = this.content.toMutableList()
        val size = this.size + node.nodeSize - current.nodeSize
        copy[index] = node
        return Fragment(copy, size)
    }

    // Create a new fragment by prepending the given node to this fragment.
    fun addToStart(node: Node): Fragment {
        return Fragment(listOf(node) + this.content, this.size + node.nodeSize)
    }

    // Create a new fragment by appending the given node to this fragment.
    fun addToEnd(node: Node): Fragment {
        return Fragment(this.content + node, this.size + node.nodeSize)
    }

    // Compare this fragment to another one.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fragment) return false

        if (size != other.size) return false
        if (content != other.content) return false

        return true
    }

    // Get the child node at the given index. Raise an error when the index is out of range.
    fun child(index: Int): Node {
        return content.getOrNull(index) ?: throw RangeError("Index $index out of range for $this")
    }

    // Get the child node at the given index, if it exists.
    fun maybeChild(index: Int): Node? {
        return content.getOrNull(index)
    }

    // Call `f` for every child node, passing the node, its offset into this parent node, and its
    // index.
    fun forEach(f: (node: Node, offset: Int, index: Int) -> Unit) {
        var p = 0
        this.content.forEachIndexed { i, child ->
            f(child, p, i)
            p += child.nodeSize
        }
    }

    // Sequence of child nodes
    fun asSequence(): Sequence<Node> = this.content.asSequence()

    // List of child nodes
    fun toList(): List<Node> = this.content.toList()

    // Find the first position at which this fragment and another fragment differ, or `null` if they
    // are the same.
    fun findDiffStart(other: Fragment, pos: Int = 0): Int? {
        return findDiffStart(this, other, pos)
    }

    // Find the first position, searching from the end, at which this fragment and the given
    // fragment differ, or `null` if they are the same. Since this position will not be the same in
    // both nodes, an object with two separate positions is returned.
    fun findDiffEnd(other: Fragment, pos: Int = this.size, otherPos: Int = other.size): Pair<Int, Int>? {
        return findDiffEnd(this, other, pos, otherPos)
    }

    // Find the index and inner offset corresponding to a given relative position in this fragment.
    // The result object will be reused (overwritten) the next time the function is called. @internal
    internal fun findIndex(pos: Int, round: Int = -1): Index {
        if (pos == 0) return retIndex(0, pos)
        if (pos == this.size) return retIndex(this.content.size, pos)
        if (pos > this.size || pos < 0) throw RangeError("Position $pos outside of fragment ($this)")
        var i = 0
        var curPos = 0
        while (true) {
            val cur = this.child(i)
            val end = curPos + cur.nodeSize
            if (end >= pos) {
                if (end == pos || round > 0) return retIndex(i + 1, end)
                return retIndex(i, curPos)
            }
            curPos = end
            i++
        }
    }

    // Return a debugging string that describes this fragment.
    override fun toString(): String {
        return if (verbose) {
            "<${toStringInner()}>"
        } else {
            "Fragment#${super.toString()} content size: ${content.size}"
        }
    }

    internal fun toStringInner(): String = this.content.joinToString(", ")

    // Create a JSON-serializeable representation of this fragment.
    fun toJSON(withId: Boolean = false): JsonArray {
        return if (this.content.isNotEmpty()) {
            buildJsonArray {
                content.forEach { node ->
                    add(
                        if (withId) {
                            node.toJSONWithId()
                        } else {
                            node.toJSON()
                        }
                    )
                }
            }
        } else {
            JsonArray(emptyList())
        }
    }

    companion object {
        // Deserialize a fragment from its JSON representation.
        fun fromJSON(schema: Schema, value: JsonArray?, withId: Boolean = false, check: Boolean = false): Fragment {
            if (value == null) return empty
//            if (!Array.isArray(value)) throw RangeError("Invalid input for Fragment.fromJSON")
            return Fragment(value.map { el -> schema.nodeFromJSON(el.jsonObject, withId, check) })
        }

        // Build a fragment from an array of nodes. Ensures that adjacent text nodes with the same
        // marks are joined together.
        fun fromArray(array: List<Node>): Fragment {
            if (array.isEmpty()) return empty
            var joined: MutableList<Node>? = null
            var size = 0
            array.forEachIndexed { i, node ->
                size += node.nodeSize
                if (i != 0 && node.isText && array[i - 1].sameMarkup(node)) {
                    if (joined == null) joined = array.slice(0 until i).toMutableList()
                    val joined = joined!!
                    joined[joined.size - 1] = (node as TextNode)
                        .withText((joined[joined.size - 1] as TextNode).text + node.text)
                } else {
                    joined?.add(node)
                }
            }
            return Fragment(joined ?: array, size)
        }

        // Create a fragment from something that can be interpreted as a set of nodes. For `null`,
        // it returns the empty fragment. For a fragment, the fragment itself. For a node or array
        // of nodes, a fragment containing those nodes.
        fun from(nodes: Fragment?): Fragment {
            return nodes ?: empty
        }

        fun from(nodes: Node?): Fragment {
            if (nodes == null) return empty
            return Fragment(listOf(nodes), nodes.nodeSize)
        }

        fun from(nodes: List<Node>?): Fragment {
            return nodes?.let { fromArray(it) } ?: empty
        }

        // An empty fragment. Intended to be reused whenever a node doesn't contain anything (rather
        // than allocating a new empty fragment for each leaf node).
        val empty = Fragment(emptyList(), 0)
    }
}

@Suppress("MemberNameEqualsClassName")
object Index {
    var index = 0
    var offset = 0
}

fun retIndex(index: Int, offset: Int): Index {
    Index.index = index
    Index.offset = offset
    return Index
}
