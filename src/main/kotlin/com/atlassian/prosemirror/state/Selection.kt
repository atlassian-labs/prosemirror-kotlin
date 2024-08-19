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

// import {Slice, Fragment, ResolvedPos, Node} from "prosemirror-model"
// import {ReplaceStep, ReplaceAroundStep, Mappable} from "prosemirror-transform"
// import {Transaction} from "./transaction"

// const classesById = Object.create(null)
val classesById = mutableMapOf<String, SelectionClass>()

interface SelectionClass {
    fun fromJSON(doc: Node, json: JsonObject): Selection
}

// region Selection
// /// Superclass for editor selections. Every selection type should
// /// extend this. Should not be instantiated directly.
// export abstract class Selection {
//     /// Initialize a selection with the head and anchor and ranges. If no
//     /// ranges are given, constructs a single range across `$anchor` and
//     /// `$head`.
//     constructor(
//         /// The resolved anchor of the selection (the side that stays in
//         /// place when the selection is modified).
//         readonly $anchor: ResolvedPos,
//         /// The resolved head of the selection (the side that moves when
//         /// the selection is modified).
//         readonly $head: ResolvedPos,
//         ranges?: readonly SelectionRange[]
//     ) {
//         this.ranges = ranges || [new SelectionRange($anchor.min($head), $anchor.max($head))]
//     }
//
//     /// The ranges covered by the selection.
//     ranges: readonly SelectionRange[]
//
//     /// The selection's anchor, as an unresolved position.
//     get anchor() { return this.$anchor.pos }
//
//     /// The selection's head.
//     get head() { return this.$head.pos }
//
//     /// The lower bound of the selection's main range.
//     get from() { return this.$from.pos }
//
//     /// The upper bound of the selection's main range.
//     get to() { return this.$to.pos }
//
//     /// The resolved lower  bound of the selection's main range.
//     get $from() {
//         return this.ranges[0].$from
//     }
//
//     /// The resolved upper bound of the selection's main range.
//     get $to() {
//         return this.ranges[0].$to
//     }
//
//     /// Indicates whether the selection contains any content.
//     get empty(): boolean {
//         let ranges = this.ranges
//         for (let i = 0; i < ranges.length; i++)
//         if (ranges[i].$from.pos != ranges[i].$to.pos) return false
//         return true
//     }
//
//     /// Test whether the selection is the same as another selection.
//     abstract eq(selection: Selection): boolean
//
//     /// Map this selection through a [mappable](#transform.Mappable)
//     /// thing. `doc` should be the new document to which we are mapping.
//     abstract map(doc: Node, mapping: Mappable): Selection
//
//     /// Get the content of this selection as a slice.
//     content() {
//         return this.$from.doc.slice(this.from, this.to, true)
//     }
//
//     /// Replace the selection with a slice or, if no slice is given,
//     /// delete the selection. Will append to the given transaction.
//     replace(tr: Transaction, content = Slice.empty) {
//         // Put the new selection at the position after the inserted
//         // content. When that ended in an inline node, search backwards,
//         // to get the position after that node. If not, search forward.
//         let lastNode = content.content.lastChild, lastParent = null
//         for (let i = 0; i < content.openEnd; i++) {
//             lastParent = lastNode!
//             lastNode = lastNode!.lastChild
//         }
//
//         let mapFrom = tr.steps.length, ranges = this.ranges
//         for (let i = 0; i < ranges.length; i++) {
//             let {$from, $to} = ranges[i], mapping = tr.mapping.slice(mapFrom)
//             tr.replaceRange(mapping.map($from.pos), mapping.map($to.pos), i ? Slice.empty : content)
//             if (i == 0)
//                 selectionToInsertionEnd(tr, mapFrom,
//                 (lastNode ? lastNode.isInline : lastParent && lastParent.isTextblock) ? -1 : 1)
//         }
//     }
//
//     /// Replace the selection with the given node, appending the changes
//     /// to the given transaction.
//     replaceWith(tr: Transaction, node: Node) {
//         let mapFrom = tr.steps.length, ranges = this.ranges
//         for (let i = 0; i < ranges.length; i++) {
//             let {$from, $to} = ranges[i], mapping = tr.mapping.slice(mapFrom)
//             let from = mapping.map($from.pos), to = mapping.map($to.pos)
//             if (i) {
//                 tr.deleteRange(from, to)
//             } else {
//                 tr.replaceRangeWith(from, to, node)
//                 selectionToInsertionEnd(tr, mapFrom, node.isInline ? -1 : 1)
//             }
//         }
//     }
//
//     /// Convert the selection to a JSON representation. When implementing
//     /// this for a custom selection class, make sure to give the object a
//     /// `type` property whose value matches the ID under which you
//     /// [registered](#state.Selection^jsonID) your class.
//     abstract toJSON(): any
//
//     /// Find a valid cursor or leaf node selection starting at the given
//     /// position and searching back if `dir` is negative, and forward if
//     /// positive. When `textOnly` is true, only consider cursor
//     /// selections. Will return null when no valid selection position is
//     /// found.
//     static findFrom($pos: ResolvedPos, dir: number, textOnly: boolean = false): Selection | null {
//         let inner = $pos.parent.inlineContent ? new TextSelection($pos)
//         : findSelectionIn($pos.node(0), $pos.parent, $pos.pos, $pos.index(), dir, textOnly)
//         if (inner) return inner
//
//         for (let depth = $pos.depth - 1; depth >= 0; depth--) {
//             let found = dir < 0
//             ? findSelectionIn($pos.node(0), $pos.node(depth), $pos.before(depth + 1),
//             $pos.index(depth), dir, textOnly)
//             : findSelectionIn($pos.node(0), $pos.node(depth), $pos.after(depth + 1),
//             $pos.index(depth) + 1, dir, textOnly)
//             if (found) return found
//         }
//         return null
//     }
//
//     /// Find a valid cursor or leaf node selection near the given
//     /// position. Searches forward first by default, but if `bias` is
//     /// negative, it will search backwards first.
//     static near($pos: ResolvedPos, bias = 1): Selection {
//         return this.findFrom($pos, bias) || this.findFrom($pos, -bias) || new AllSelection($pos.node(0))
//     }
//
//     /// Find the cursor or leaf node selection closest to the start of
//     /// the given document. Will return an
//     /// [`AllSelection`](#state.AllSelection) if no valid position
//     /// exists.
//     static atStart(doc: Node): Selection {
//         return findSelectionIn(doc, doc, 0, 0, 1) || new AllSelection(doc)
//     }
//
//     /// Find the cursor or leaf node selection closest to the end of the
//     /// given document.
//     static atEnd(doc: Node): Selection {
//         return findSelectionIn(doc, doc, doc.content.size, doc.childCount, -1) || new AllSelection(doc)
//     }
//
//     /// Deserialize the JSON representation of a selection. Must be
//     /// implemented for custom classes (as a static class method).
//     static fromJSON(doc: Node, json: any): Selection {
//         if (!json || !json.type) throw new RangeError("Invalid input for Selection.fromJSON")
//         let cls = classesById[json.type]
//         if (!cls) throw new RangeError(`No selection type ${json.type} defined`)
//         return cls.fromJSON(doc, json)
//     }
//
//     /// To be able to deserialize selections from JSON, custom selection
//     /// classes must register themselves with an ID string, so that they
//     /// can be disambiguated. Try to pick something that's unlikely to
//     /// clash with classes from other modules.
//     static jsonID(id: string, selectionClass: {fromJSON: (doc: Node, json: any) => Selection}) {
//         if (id in classesById) throw new RangeError("Duplicate use of selection JSON ID " + id)
//         classesById[id] = selectionClass
//         ;(selectionClass as any).prototype.jsonID = id
//         return selectionClass
//     }
//
//     /// Get a [bookmark](#state.SelectionBookmark) for this selection,
//     /// which is a value that can be mapped without having access to a
//     /// current document, and later resolved to a real selection for a
//     /// given document again. (This is used mostly by the history to
//     /// track and restore old selections.) The default implementation of
//     /// this method just converts the selection to a text selection and
//     /// returns the bookmark for that.
//     getBookmark(): SelectionBookmark {
//         return TextSelection.between(this.$anchor, this.$head).getBookmark()
//     }
//
//     /// Controls whether, when a selection of this type is active in the
//     /// browser, the selected range should be visible to the user.
//     /// Defaults to `true`.
//     visible!: boolean
// }
// endregion
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

// Selection.prototype.visible = true

// /// A lightweight, document-independent representation of a selection.
// /// You can define a custom bookmark type for a custom selection class
// /// to make the history handle it well.
// export interface SelectionBookmark {
//     /// Map the bookmark through a set of changes.
//     map: (mapping: Mappable) => SelectionBookmark
//
//     /// Resolve the bookmark to a real selection again. This may need to
//     /// do some error checking and may fall back to a default (usually
//     /// [`TextSelection.between`](#state.TextSelection^between)) if
//     /// mapping made the bookmark invalid.
//     resolve: (doc: Node) => Selection
// }
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

// /// Represents a selected range in a document.
// export class SelectionRange {
//     /// Create a range.
//     constructor(
//         /// The lower bound of the range.
//         readonly $from: ResolvedPos,
//         /// The upper bound of the range.
//         readonly $to: ResolvedPos
//     ) {}
// }
// Represents a selected range in a document.
class SelectionRange(
    // Create a range.
    // The lower bound of the range.
    val from: ResolvedPos,
    // The upper bound of the range.
    val to: ResolvedPos
)

// let warnedAboutTextSelection = false
// function checkTextSelection($pos: ResolvedPos) {
//     if (!warnedAboutTextSelection && !$pos.parent.inlineContent) {
//         warnedAboutTextSelection = true
//         console["warn"](
//             "TextSelection endpoint not pointing into a node with inline content (" + $pos.parent.type.name + ")"
//         )
//     }
// }
var warnedAboutTextSelection = false
fun checkTextSelection(_pos: ResolvedPos) {
    if (!warnedAboutTextSelection && !_pos.parent.inlineContent) {
        warnedAboutTextSelection = true
//        Log.w("TextSelection", "endpoint not pointing into a node with inline content (${_pos.parent.type.name})")
    }
}

// region TextSelection
// /// A text selection represents a classical editor selection, with a
// /// head (the moving side) and anchor (immobile side), both of which
// /// point into textblock nodes. It can be empty (a regular cursor
// /// position).
// export class TextSelection extends Selection {
//     /// Construct a text selection between the given points.
//     constructor($anchor: ResolvedPos, $head = $anchor) {
//         checkTextSelection($anchor)
//         checkTextSelection($head)
//         super($anchor, $head)
//     }
//
//     /// Returns a resolved position if this is a cursor selection (an
//     /// empty text selection), and null otherwise.
//     get $cursor() { return this.$anchor.pos == this.$head.pos ? this.$head : null }
//
//     map(doc: Node, mapping: Mappable): Selection {
//         let $head = doc.resolve(mapping.map(this.head))
//         if (!$head.parent.inlineContent) return Selection.near($head)
//         let $anchor = doc.resolve(mapping.map(this.anchor))
//         return new TextSelection($anchor.parent.inlineContent ? $anchor : $head, $head)
//     }
//
//     replace(tr: Transaction, content = Slice.empty) {
//         super.replace(tr, content)
//         if (content == Slice.empty) {
//             let marks = this.$from.marksAcross(this.$to)
//             if (marks) tr.ensureMarks(marks)
//         }
//     }
//
//     eq(other: Selection): boolean {
//         return other instanceof TextSelection && other.anchor == this.anchor && other.head == this.head
//     }
//
//     getBookmark() {
//         return new TextBookmark(this.anchor, this.head)
//     }
//
//     toJSON(): any {
//         return {type: "text", anchor: this.anchor, head: this.head}
//     }
//
//     /// @internal
//     static fromJSON(doc: Node, json: any) {
//         if (typeof json.anchor != "number" || typeof json.head != "number")
//         throw new RangeError("Invalid input for TextSelection.fromJSON")
//         return new TextSelection(doc.resolve(json.anchor), doc.resolve(json.head))
//     }
//
//     /// Create a text selection from non-resolved positions.
//     static create(doc: Node, anchor: number, head = anchor) {
//         let $anchor = doc.resolve(anchor)
//         return new this($anchor, head == anchor ? $anchor : doc.resolve(head))
//     }
//
//     /// Return a text selection that spans the given positions or, if
//     /// they aren't text positions, find a text selection near them.
//     /// `bias` determines whether the method searches forward (default)
//     /// or backwards (negative number) first. Will fall back to calling
//     /// [`Selection.near`](#state.Selection^near) when the document
//     /// doesn't contain a valid text position.
//     static between($anchor: ResolvedPos, $head: ResolvedPos, bias?: number): Selection {
//         let dPos = $anchor.pos - $head.pos
//         if (!bias || dPos) bias = dPos >= 0 ? 1 : -1
//         if (!$head.parent.inlineContent) {
//             let found = Selection.findFrom($head, bias, true) || Selection.findFrom($head, -bias, true)
//             if (found) $head = found.$head
//             else return Selection.near($head, bias)
//         }
//         if (!$anchor.parent.inlineContent) {
//             if (dPos == 0) {
//                 $anchor = $head
//             } else {
//                 $anchor = (Selection.findFrom($anchor, -bias, true) ||
//                      Selection.findFrom($anchor, bias, true))!.$anchor
//                 if (($anchor.pos < $head.pos) != (dPos < 0)) $anchor = $head
//             }
//         }
//         return new TextSelection($anchor, $head)
//     }
// }
// endregion
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

// Selection.jsonID("text", TextSelection)

// class TextBookmark {
//     constructor(readonly anchor: number, readonly head: number) {}
//
//     map(mapping: Mappable) {
//         return new TextBookmark(mapping.map(this.anchor), mapping.map(this.head))
//     }
//     resolve(doc: Node) {
//         return TextSelection.between(doc.resolve(this.anchor), doc.resolve(this.head))
//     }
// }
class TextBookmark(val anchor: Int, val head: Int) : SelectionBookmark {
    override fun map(mapping: Mappable) = TextBookmark(mapping.map(this.anchor), mapping.map(this.head))
    override fun resolve(doc: Node) = TextSelection.between(doc.resolve(this.anchor), doc.resolve(this.head))
}

// region NodeSelection
// /// A node selection is a selection that points at a single node. All
// /// nodes marked [selectable](#model.NodeSpec.selectable) can be the
// /// target of a node selection. In such a selection, `from` and `to`
// /// point directly before and after the selected node, `anchor` equals
// /// `from`, and `head` equals `to`..
// export class NodeSelection extends Selection {
//     /// Create a node selection. Does not verify the validity of its
//     /// argument.
//     constructor($pos: ResolvedPos) {
//         let node = $pos.nodeAfter!
//         let $end = $pos.node(0).resolve($pos.pos + node.nodeSize)
//         super($pos, $end)
//         this.node = node
//     }
//
//     /// The selected node.
//     node: Node
//
//     map(doc: Node, mapping: Mappable): Selection {
//         let {deleted, pos} = mapping.mapResult(this.anchor)
//         let $pos = doc.resolve(pos)
//         if (deleted) return Selection.near($pos)
//         return new NodeSelection($pos)
//     }
//
//     content() {
//         return new Slice(Fragment.from(this.node), 0, 0)
//     }
//
//     eq(other: Selection): boolean {
//         return other instanceof NodeSelection && other.anchor == this.anchor
//     }
//
//     toJSON(): any {
//         return {type: "node", anchor: this.anchor}
//     }
//
//     getBookmark() { return new NodeBookmark(this.anchor) }
//
//     /// @internal
//     static fromJSON(doc: Node, json: any) {
//         if (typeof json.anchor != "number")
//         throw new RangeError("Invalid input for NodeSelection.fromJSON")
//         return new NodeSelection(doc.resolve(json.anchor))
//     }
//
//     /// Create a node selection from non-resolved positions.
//     static create(doc: Node, from: number) {
//         return new NodeSelection(doc.resolve(from))
//     }
//
//     /// Determines whether the given node may be selected as a node
//     /// selection.
//     static isSelectable(node: Node) {
//         return !node.isText && node.type.spec.selectable !== false
//     }
// }
//
// NodeSelection.prototype.visible = false
// endregion
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

// Selection.jsonID("node", NodeSelection)
//
// class NodeBookmark {
//     constructor(readonly anchor: number) {}
//     map(mapping: Mappable) {
//         let {deleted, pos} = mapping.mapResult(this.anchor)
//         return deleted ? new TextBookmark(pos, pos) : new NodeBookmark(pos)
//     }
//     resolve(doc: Node) {
//         let $pos = doc.resolve(this.anchor), node = $pos.nodeAfter
//         if (node && NodeSelection.isSelectable(node)) return new NodeSelection($pos)
//         return Selection.near($pos)
//     }
// }
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

// region AllSelection
// /// A selection type that represents selecting the whole document
// /// (which can not necessarily be expressed with a text selection, when
// /// there are for example leaf block nodes at the start or end of the
// /// document).
// export class AllSelection extends Selection {
//     /// Create an all-selection over the given document.
//     constructor(doc: Node) {
//         super(doc.resolve(0), doc.resolve(doc.content.size))
//     }
//
//     replace(tr: Transaction, content = Slice.empty) {
//         if (content == Slice.empty) {
//             tr.delete(0, tr.doc.content.size)
//             let sel = Selection.atStart(tr.doc)
//             if (!sel.eq(tr.selection)) tr.setSelection(sel)
//         } else {
//             super.replace(tr, content)
//         }
//     }
//
//     toJSON(): any { return {type: "all"} }
//
//     /// @internal
//     static fromJSON(doc: Node) { return new AllSelection(doc) }
//
//     map(doc: Node) { return new AllSelection(doc) }
//
//     eq(other: Selection) { return other instanceof AllSelection }
//
//     getBookmark() { return AllBookmark }
// }
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
// endregion
//
// Selection.jsonID("all", AllSelection)

// const AllBookmark = {
//     map() { return this },
//     resolve(doc: Node) { return new AllSelection(doc) }
// }
object AllBookmark : SelectionBookmark {
    override fun map(mapping: Mappable) = this

    override fun resolve(doc: Node) = AllSelection(doc)
}

//
// // FIXME we'll need some awareness of text direction when scanning for selections
//
// // Try to find a selection inside the given node. `pos` points at the
// // position where the search starts. When `text` is true, only return
// // text selections.
// function findSelectionIn(
//     doc: Node, node: Node, pos: number, index: number, dir: number, text = false
//   ): Selection | null {
//     if (node.inlineContent) return TextSelection.create(doc, pos)
//     for (let i = index - (dir > 0 ? 0 : 1); dir > 0 ? i < node.childCount : i >= 0; i += dir) {
//         let child = node.child(i)
//         if (!child.isAtom) {
//             let inner = findSelectionIn(doc, child, pos + dir, dir < 0 ? child.childCount : 0, dir, text)
//             if (inner) return inner
//         } else if (!text && NodeSelection.isSelectable(child)) {
//             return NodeSelection.create(doc, pos - (dir < 0 ? child.nodeSize : 0))
//         }
//         pos += child.nodeSize * dir
//     }
//     return null
// }
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

// function selectionToInsertionEnd(tr: Transaction, startLen: number, bias: number) {
//     let last = tr.steps.length - 1
//     if (last < startLen) return
//     let step = tr.steps[last]
//     if (!(step instanceof ReplaceStep || step instanceof ReplaceAroundStep)) return
//     let map = tr.mapping.maps[last], end: number | undefined
//     map.forEach((_from, _to, _newFrom, newTo) => { if (end == null) end = newTo })
//     tr.setSelection(Selection.near(tr.doc.resolve(end!), bias))
// }
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
