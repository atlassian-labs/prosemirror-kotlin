package com.atlassian.prosemirror.state

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeId
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.ResolvedPos
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.transform.Mappable
import com.atlassian.prosemirror.transform.ReplaceAroundStep
import com.atlassian.prosemirror.transform.ReplaceStep
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

val classesById = mutableMapOf<String, SelectionClass>()

interface SelectionClass {
    fun fromJSON(doc: Node, json: JsonObject): Selection
}

// Superclass for editor selections. Every selection type should extend this. Should not be
// instantiated directly.
abstract class Selection(
    // Initialize a selection with the head and anchor and ranges. If no ranges are given,
    // constructs a single range across `$anchor` and `$head`.

    // The resolved anchor of the selection (the side that stays in place when the selection is
    // modified).
    val _anchor: ResolvedPos,
    // The resolved head of the selection (the side that moves when the selection is modified).
    val _head: ResolvedPos,
    // Actions (i.e. open links) will only trigger if enabled. You likely don't want this unless the user is actively
    // tapping to set this selection
    val allowActions: Boolean = false,
    ranges: List<SelectionRange>? = null
) {
    // The ranges covered by the selection.
    val ranges: List<SelectionRange>

    init {
        this.ranges = ranges ?: listOf(SelectionRange(_anchor.min(_head), _anchor.max(_head)))
    }

    // The selection's anchor, as an unresolved position.
    val anchor: Int
        get() = _anchor.pos

    // The selection's head.
    val head: Int
        get() = _head.pos

    // The lower bound of the selection's main range.
    val from: Int
        get() = _from.pos

    // The upper bound of the selection's main range.
    val to: Int
        get() = _to.pos

    // The resolved lower  bound of the selection's main range.
    val _from: ResolvedPos
        get() = this.ranges[0].from

    // The resolved upper bound of the selection's main range.
    val _to: ResolvedPos
        get() = this.ranges[0].to

    // Indicates whether the selection contains any content.
    val empty: Boolean
        get() = this.ranges.firstOrNull { it.from.pos != it.to.pos } == null

    // Get a [bookmark](#state.SelectionBookmark) for this selection, which is a value that can be
    // mapped without having access to a current document, and later resolved to a real selection
    // for a given document again. (This is used mostly by the history to track and restore old
    // selections.) The default implementation of this method just converts the selection to a text
    // selection and returns the bookmark for that.
    open fun getBookmark(): SelectionBookmark {
        return TextSelection.between(this._anchor, this._head).getBookmark()
    }

//    /// Test whether the selection is the same as another selection.
//    abstract eq(selection: Selection): boolean

    // Map this selection through a [mappable](#transform.Mappable)
    // thing. `doc` should be the new document to which we are mapping.
    abstract fun map(doc: Node, mapping: Mappable): Selection

    // Get the content of this selection as a slice.
    open fun content(): Slice {
        return this._from.doc.slice(this.from, this.to, true)
    }

    // Replace the selection with a slice or, if no slice is given, delete the selection. Will
    // append to the given transaction.
    open fun replace(tr: Transaction, content: Slice = Slice.empty) {
        // Put the new selection at the position after the inserted
        // content. When that ended in an inline node, search backwards,
        // to get the position after that node. If not, search forward.
        var lastNode: Node? = content.content.lastChild
        var lastParent: Node? = null
        for (i in 0 until content.openEnd) {
            lastParent = lastNode!!
            lastNode = lastNode.lastChild
        }

        val mapFrom = tr.steps.size
        val ranges = this.ranges
        for (i in 0 until ranges.size) {
            val (_from, _to) = ranges[i].run { _from to _to }
            val mapping = tr.mapping.slice(mapFrom)
            tr.replaceRange(mapping.map(_from.pos), mapping.map(_to.pos), if (i != 0) Slice.empty else content)
            if (i == 0) {
                selectionToInsertionEnd(
                    tr,
                    mapFrom,
                    if (lastNode?.isInline ?: (lastParent?.isTextblock != null)) -1 else 1
                )
            }
        }
    }

    // Replace the selection with the given node, appending the changes to the given transaction.
    fun replaceWith(tr: Transaction, node: Node) {
        val mapFrom = tr.steps.size
        val ranges = this.ranges
        for (i in ranges.indices) {
            val (_from, _to) = ranges[i].run { from to to }
            val mapping = tr.mapping.slice(mapFrom)
            val from = mapping.map(_from.pos)
            val to = mapping.map(_to.pos)
            if (i != 0) {
                tr.deleteRange(from, to)
            } else {
                tr.replaceRangeWith(from, to, node)
                selectionToInsertionEnd(tr, mapFrom, if (node.isInline) -1 else 1)
            }
        }
    }

    // Convert the selection to a JSON representation. When implementing this for a custom selection
    // class, make sure to give the object a `type` property whose value matches the ID under which
    // you [registered](#state.Selection^jsonID) your class.
    abstract fun toJSON(): JsonObject

    // Controls whether, when a selection of this type is active in the browser, the selected range
    // should be visible to the user. Defaults to `true`.
    val visible: Boolean = true

    companion object {
        // Find a valid cursor or leaf node selection starting at the given position and searching
        // back if `dir` is negative, and forward if positive. When `textOnly` is true, only
        // consider cursor selections. Will return null when no valid selection position is found.
        fun findFrom(_pos: ResolvedPos, dir: Int, textOnly: Boolean = false): Selection? {
            val inner = if (_pos.parent.inlineContent) {
                TextSelection(_pos)
            } else {
                findSelectionIn(_pos.node(0), _pos.parent, _pos.pos, _pos.index(), dir, textOnly)
            }
            if (inner != null) return inner

            for (depth in _pos.depth - 1 downTo 0) {
                val found =
                    if (dir < 0) {
                        findSelectionIn(
                            _pos.node(0),
                            _pos.node(depth),
                            _pos.before(depth + 1),
                            _pos.index(depth),
                            dir,
                            textOnly
                        )
                    } else {
                        findSelectionIn(
                            _pos.node(0),
                            _pos.node(depth),
                            _pos.after(depth + 1),
                            _pos.index(depth) + 1,
                            dir,
                            textOnly
                        )
                    }
                if (found != null) return found
            }
            return null
        }

        // Find a valid cursor or leaf node selection near the given position. Searches forward
        // first by default, but if `bias` is negative, it will search backwards first.
        fun near(_pos: ResolvedPos, bias: Int = 1): Selection {
            return this.findFrom(_pos, bias) ?: this.findFrom(_pos, -bias) ?: AllSelection(_pos.node(0))
        }

        // Find the cursor or leaf node selection closest to the start of the given document. Will
        // return an [`AllSelection`](#state.AllSelection) if no valid position exists.
        fun atStart(doc: Node): Selection {
            return findSelectionIn(doc, doc, 0, 0, 1) ?: AllSelection(doc)
        }

        // Find the cursor or leaf node selection closest to the end of the given document.
        fun atEnd(doc: Node): Selection {
            return findSelectionIn(doc, doc, doc.content.size, doc.childCount, -1) ?: AllSelection(doc)
        }

        // Deserialize the JSON representation of a selection. Must be implemented for custom
        // classes (as a static class method).
        fun fromJSON(doc: Node, json: JsonObject?): Selection {
            if (json == null || json["type"] == null) throw RangeError("Invalid input for Selection.fromJSON")
            val type = json["type"]!!.jsonPrimitive.content
            val cls = classesById[type] ?: throw RangeError("No selection type $type defined")
            return cls.fromJSON(doc, json)
        }

        // To be able to deserialize selections from JSON, custom selection classes must register
        // themselves with an ID string, so that they can be disambiguated. Try to pick something
        // that's unlikely to clash with classes from other modules.
        fun jsonID(id: String, selectionClass: SelectionClass): SelectionClass {
            if (id in classesById) throw RangeError("Duplicate use of selection JSON ID $id")
            classesById[id] = selectionClass
            return selectionClass
        }
    }
}

// A lightweight, document-independent representation of a selection. You can define a custom
// bookmark type for a custom selection class to make the history handle it well.
interface SelectionBookmark {
    // Map the bookmark through a set of changes.
    fun map(mapping: Mappable): SelectionBookmark

    // Resolve the bookmark to a real selection again. This may need to do some error checking and
    // may fall back to a default (usually [`TextSelection.between`](#state.TextSelection^between))
    // if mapping made the bookmark invalid.
    fun resolve(doc: Node): Selection
}

// Represents a selected range in a document.
class SelectionRange(
    // Create a range.
    // The lower bound of the range.
    val from: ResolvedPos,
    // The upper bound of the range.
    val to: ResolvedPos
)

var warnedAboutTextSelection = false
fun checkTextSelection(_pos: ResolvedPos) {
    if (!warnedAboutTextSelection && !_pos.parent.inlineContent) {
        warnedAboutTextSelection = true
//        Log.w("TextSelection", "endpoint not pointing into a node with inline content (${_pos.parent.type.name})")
    }
}

// A text selection represents a classical editor selection, with a head (the moving side) and
// anchor (immobile side), both of which point into textblock nodes. It can be empty (a regular
// cursor position).
class TextSelection(
    // Construct a text selection between the given points.
    _anchor: ResolvedPos,
    _head: ResolvedPos = _anchor,
    allowActions: Boolean = false
) : Selection(_anchor, _head, allowActions) {
    init {
        checkTextSelection(_anchor)
        checkTextSelection(_head)
    }

    // Returns a resolved position if this is a cursor selection (an empty text selection), and null
    // otherwise.
    val _cursor: ResolvedPos?
        get() = if (this._anchor.pos == this._head.pos) this._head else null

    private val containedNodes: Set<NodeId> by lazy {
        buildSet {
            _to.doc.nodesBetween(from, to, f = { node, nodePos, _, _ ->
                if (nodePos + node.nodeSize <= to) {
                    add(node.nodeId)
                }
                true
            })
        }
    }

    fun contains(node: Node): Boolean {
        return containedNodes.contains(node.nodeId)
    }

    override fun map(doc: Node, mapping: Mappable): Selection {
        val _head = doc.resolve(mapping.map(this.head))
        if (!_head.parent.inlineContent) return near(_head)
        val _anchor = doc.resolve(mapping.map(this.anchor))
        return TextSelection(if (_anchor.parent.inlineContent) _anchor else _head, _head)
    }

    override fun replace(tr: Transaction, content: Slice) {
        super.replace(tr, content)
        if (content == Slice.empty) {
            val marks = this._from.marksAcross(this._to)
            if (marks != null) tr.ensureMarks(marks)
        }
    }

    override fun getBookmark() = TextBookmark(this.anchor, this.head)

    override fun toJSON() = buildJsonObject {
        put("type", "text")
        put("anchor", anchor)
        put("head", head)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextSelection) return false
        return other.anchor == this.anchor && other.head == this.head
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }

    override fun toString(): String {
        return "TextSelection($anchor, $head)"
    }

    companion object : SelectionClass {
        init {
            jsonID("text", this)
        }

        override fun fromJSON(doc: Node, json: JsonObject): TextSelection {
            val anchor = json["anchor"]?.jsonPrimitive?.intOrNull
            val head = json["head"]?.jsonPrimitive?.intOrNull
            if (anchor == null || head == null) {
                throw RangeError("Invalid input for TextSelection.fromJSON")
            }
            return TextSelection(doc.resolve(anchor), doc.resolve(head))
        }

        // Create a text selection from non-resolved positions.
        fun create(doc: Node, anchor: Int, head: Int? = anchor, allowActions: Boolean = false): TextSelection {
            val head = head ?: anchor
            val _anchor = doc.resolve(anchor)
            return TextSelection(_anchor, if (head == anchor) _anchor else doc.resolve(head), allowActions)
        }

        // Return a text selection that spans the given positions or, if they aren't text positions,
        // find a text selection near them. `bias` determines whether the method searches forward
        // (default) or backwards (negative number) first. Will fall back to calling
        // [`Selection.near`](#state.Selection^near) when the document doesn't contain a valid text
        // position.
        fun between(_anchor: ResolvedPos, _head: ResolvedPos, bias: Int? = null): Selection {
            var bias = bias
            var _head = _head
            var _anchor = _anchor
            val dPos = _anchor.pos - _head.pos
            if (bias == null || bias == 0 || dPos != 0) {
                bias = if (dPos >= 0) 1 else -1
            }
            if (!_head.parent.inlineContent) {
                val found = findFrom(_head, bias, true) ?: findFrom(_head, -bias, true)
                if (found != null) {
                    _head = found._head
                } else {
                    return near(_head, bias)
                }
            }
            if (!_anchor.parent.inlineContent) {
                if (dPos == 0) {
                    _anchor = _head
                } else {
                    _anchor = (findFrom(_anchor, -bias, true) ?: findFrom(_anchor, bias, true))!!._anchor
                    if ((_anchor.pos < _head.pos) != (dPos < 0)) _anchor = _head
                }
            }
            return TextSelection(_anchor, _head)
        }

        // Find a valid cursor or leaf node selection near the given
        // position. Searches forward first by default, but if `bias` is
        // negative, it will search backwards first.
        // static near($pos: ResolvedPos, bias = 1): Selection {
        //     return this.findFrom($pos, bias) || this.findFrom($pos, -bias) || new AllSelection($pos.node(0))
        // }
        fun near(pos: ResolvedPos, bias: Int = 1): Selection {
            return findFrom(pos, bias) ?: findFrom(pos, -bias) ?: AllSelection(pos.node(0))
        }
    }
}

class TextBookmark(val anchor: Int, val head: Int) : SelectionBookmark {
    override fun map(mapping: Mappable) = TextBookmark(mapping.map(this.anchor), mapping.map(this.head))
    override fun resolve(doc: Node) = TextSelection.between(doc.resolve(this.anchor), doc.resolve(this.head))
}

// A node selection is a selection that points at a single node. All nodes marked
// [selectable](#model.NodeSpec.selectable) can be the target of a node selection. In such a selection, `from` and `to`
// point directly before and after the selected node, `anchor` equals `from`, and `head` equals `to`..
@Suppress("EqualsWithHashCodeExist")
class NodeSelection private constructor(
    // Create a node selection. Does not verify the validity of its argument.
    _pos: ResolvedPos,
    // The selected node.
    val node: Node,
    val _end: ResolvedPos,
    allowActions: Boolean = false
) : Selection(_pos, _end, allowActions) {

    private constructor(_pos: ResolvedPos, node: Node, allowActions: Boolean) : this(
        _pos,
        node = _pos.nodeAfter!!,
        _end = _pos.node(0).resolve(_pos.pos + node.nodeSize),
        allowActions = allowActions
    )

    constructor(_pos: ResolvedPos, allowActions: Boolean = false) : this(_pos, _pos.nodeAfter!!, allowActions)

    override fun map(doc: Node, mapping: Mappable): Selection {
        val (deleted, pos) = mapping.mapResult(this.anchor).run { deleted to pos }
        val _pos = doc.resolve(pos)
        if (deleted) return near(_pos)
        return NodeSelection(_pos)
    }

    override fun content() = Slice(Fragment.from(this.node), 0, 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NodeSelection) return false
        if (anchor != other.anchor) return false
        return true
    }

    override fun toJSON() = buildJsonObject {
        put("type", "node")
        put("anchor", anchor)
    }

    override fun getBookmark() = NodeBookmark(this.anchor)

    override fun toString(): String {
        return "NodeSelection($node)"
    }

    companion object : SelectionClass {
        init {
            jsonID("node", this)
        }

        override fun fromJSON(doc: Node, json: JsonObject): NodeSelection {
            val anchor = json["anchor"]?.jsonPrimitive?.intOrNull
                ?: throw RangeError("Invalid input for NodeSelection.fromJSON")
            return NodeSelection(doc.resolve(anchor))
        }

        // Create a node selection from non-resolved positions.
        fun create(doc: Node, from: Int) = NodeSelection(doc.resolve(from))

        // Determines whether the given node may be selected as a node selection.
        fun isSelectable(node: Node) = !node.isText && node.type.spec.selectable != false
    }
}

class NodeBookmark(val anchor: Int) : SelectionBookmark {
    override fun map(mapping: Mappable): SelectionBookmark {
        val (deleted, pos) = mapping.mapResult(this.anchor).run { deleted to pos }
        return if (deleted) TextBookmark(pos, pos) else NodeBookmark(pos)
    }

    override fun resolve(doc: Node): Selection {
        val _pos = doc.resolve(this.anchor)
        val node = _pos.nodeAfter
        if (node != null && NodeSelection.isSelectable(node)) return NodeSelection(_pos)
        return Selection.near(_pos)
    }
}

// A selection type that represents selecting the whole document (which can not necessarily be
// expressed with a text selection, when there are for example leaf block nodes at the start or end
// of the document).
class AllSelection(
    // Create an all-selection over the given document.
    doc: Node
) : Selection(doc.resolve(0), doc.resolve(doc.content.size)) {

    override fun replace(tr: Transaction, content: Slice) {
        if (content == Slice.empty) {
            tr.delete(0, tr.doc.content.size)
            val sel = Selection.atStart(tr.doc)
            if (sel != tr.selection) tr.setSelection(sel)
        } else {
            super.replace(tr, content)
        }
    }

    override fun toJSON() = buildJsonObject {
        put("type", "all")
    }

    override fun map(doc: Node, mapping: Mappable) = AllSelection(doc)

    fun eq(other: Selection) = other is AllSelection

    override fun getBookmark() = AllBookmark

    companion object : SelectionClass {

        init {
            jsonID("all", this)
        }

        override fun fromJSON(doc: Node, json: JsonObject): Selection = AllSelection(doc)
    }
}

object AllBookmark : SelectionBookmark {
    override fun map(mapping: Mappable) = this

    override fun resolve(doc: Node) = AllSelection(doc)
}

// Try to find a selection inside the given node. `pos` points at the position where the search
// starts. When `text` is true, only return text selections.
fun findSelectionIn(
    doc: Node,
    node: Node,
    pos: Int,
    index: Int,
    dir: Int,
    text: Boolean = false
): Selection? {
    var pos = pos
    if (node.inlineContent) return TextSelection.create(doc, pos)
    var i = index - (if (dir > 0) 0 else 1)
    while (if (dir > 0) i < node.childCount else i >= 0) {
        val child = node.child(i)
        if (!child.isAtom) {
            val inner = findSelectionIn(doc, child, pos + dir, if (dir < 0) child.childCount else 0, dir, text)
            if (inner != null) return inner
        } else if (!text && NodeSelection.isSelectable(child)) {
            return NodeSelection.create(doc, pos - (if (dir < 0) child.nodeSize else 0))
        }
        pos += child.nodeSize * dir
        i += dir
    }
    return null
}

fun selectionToInsertionEnd(tr: Transaction, startLen: Int, bias: Int) {
    val last = tr.steps.size - 1
    if (last < startLen) return
    val step = tr.steps[last]
    if (!(step is ReplaceStep || step is ReplaceAroundStep)) return
    val map = tr.mapping.maps[last]
    var end: Int? = null
    map.forEach { _from, _to, _newFrom, newTo -> if (end == null) end = newTo }
    tr.setSelection(Selection.near(tr.doc.resolve(end!!), bias))
}
