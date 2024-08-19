@file:Suppress("ReturnCount")

package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.model.parser.JSON
import com.atlassian.prosemirror.util.slice
import com.atlassian.prosemirror.state.Selection
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.transform.Transform
import java.io.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID

// import {Fragment} from "./fragment"
// import {Mark} from "./mark"
// import {Schema, NodeType, Attrs, MarkType} from "./schema"
// import {Slice, replace} from "./replace"
// import {ResolvedPos} from "./resolvedpos"
// import {compareDeep} from "./comparedeep"

// const emptyAttrs: Attrs = Object.create(null)
val emptyAttrs: Attrs = emptyMap()

// region Node
// /// This class represents a node in the tree that makes up a
// /// ProseMirror document. So a document is an instance of `Node`, with
// /// children that are also instances of `Node`.
// ///
// /// Nodes are persistent data structures. Instead of changing them, you
// /// create new ones with the content you want. Old ones keep pointing
// /// at the old document shape. This is made cheaper by sharing
// /// structure between the old and new data as much as possible, which a
// /// tree shape like this (without back pointers) makes easy.
// ///
// /// **Do not** directly mutate the properties of a `Node` object. See
// /// [the guide](/docs/guide/#doc) for more information.
// export class Node {
//     /// @internal
//     constructor(
//         /// The type of node that this is.
//         readonly type: NodeType,
//         /// An object mapping attribute names to values. The kind of
//         /// attributes allowed and required are
//         /// [determined](#model.NodeSpec.attrs) by the node type.
//         readonly attrs: Attrs,
//         // A fragment holding the node's children.
//         content?: Fragment | null,
//     /// The marks (things like whether it is emphasized or part of a
//     /// link) applied to this node.
//     readonly marks = Mark.none
//     ) {
//         this.content = content || Fragment.empty
//     }
//
//     /// A container holding the node's children.
//     readonly content: Fragment
//
//     /// For text nodes, this contains the node's text content.
//     readonly text: string | undefined
//
//     /// The size of this node, as defined by the integer-based [indexing
//     /// scheme](/docs/guide/#doc.indexing). For text nodes, this is the
//     /// amount of characters. For other leaf nodes, it is one. For
//     /// non-leaf nodes, it is the size of the content plus two (the
//     /// start and end token).
//     get nodeSize(): number { return this.isLeaf ? 1 : 2 + this.content.size }
//
//     /// The number of children that the node has.
//     get childCount() { return this.content.childCount }
//
//     /// Get the child node at the given index. Raises an error when the
//     /// index is out of range.
//     child(index: number) { return this.content.child(index) }
//
//     /// Get the child node at the given index, if it exists.
//     maybeChild(index: number) { return this.content.maybeChild(index) }
//
//     /// Call `f` for every child node, passing the node, its offset
//     /// into this parent node, and its index.
//     forEach(f: (node: Node, offset: number, index: number) => void) { this.content.forEach(f) }
//
//     /// Invoke a callback for all descendant nodes recursively between
//     /// the given two positions that are relative to start of this
//     /// node's content. The callback is invoked with the node, its
//     /// parent-relative position, its parent node, and its child index.
//     /// When the callback returns false for a given node, that node's
//     /// children will not be recursed over. The last parameter can be
//     /// used to specify a starting position to count from.
//     nodesBetween(from: number, to: number,
//     f: (node: Node, pos: number, parent: Node | null, index: number) => void | boolean,
//     startPos = 0) {
//         this.content.nodesBetween(from, to, f, startPos, this)
//     }
//
//     /// Call the given callback for every descendant node. Doesn't
//     /// descend into a node when the callback returns `false`.
//     descendants(f: (node: Node, pos: number, parent: Node | null, index: number) => void | boolean) {
//         this.nodesBetween(0, this.content.size, f)
//     }
//
//     /// Concatenates all the text nodes found in this fragment and its
//     /// children.
//     get textContent() {
//         return (this.isLeaf && this.type.spec.leafText)
//         ? this.type.spec.leafText(this)
//         : this.textBetween(0, this.content.size, "")
//     }
//
//     /// Get all text between positions `from` and `to`. When
//     /// `blockSeparator` is given, it will be inserted to separate text
//     /// from different block nodes. If `leafText` is given, it'll be
//     /// inserted for every non-text leaf node encountered, otherwise
//     /// [`leafText`](#model.NodeSpec^leafText) will be used.
//     textBetween(from: number, to: number, blockSeparator?: string | null,
//     leafText?: null | string | ((leafNode: Node) => string)) {
//         return this.content.textBetween(from, to, blockSeparator, leafText)
//     }
//
//     /// Returns this node's first child, or `null` if there are no
//     /// children.
//     get firstChild(): Node | null { return this.content.firstChild }
//
//     /// Returns this node's last child, or `null` if there are no
//     /// children.
//     get lastChild(): Node | null { return this.content.lastChild }
//
//     /// Test whether two nodes represent the same piece of document.
//     eq(other: Node) {
//         return this == other || (this.sameMarkup(other) && this.content.eq(other.content))
//     }
//
//     /// Compare the markup (type, attributes, and marks) of this node to
//     /// those of another. Returns `true` if both have the same markup.
//     sameMarkup(other: Node) {
//         return this.hasMarkup(other.type, other.attrs, other.marks)
//     }
//
//     /// Check whether this node's markup correspond to the given type,
//     /// attributes, and marks.
//     hasMarkup(type: NodeType, attrs?: Attrs | null, marks?: readonly Mark[]): boolean {
//         return this.type == type &&
//                 compareDeep(this.attrs, attrs || type.defaultAttrs || emptyAttrs) &&
//                 Mark.sameSet(this.marks, marks || Mark.none)
//     }
//
//     /// Create a new node with the same markup as this node, containing
//     /// the given content (or empty, if no content is given).
//     copy(content: Fragment | null = null): Node {
//         if (content == this.content) return this
//         return new Node(this.type, this.attrs, content, this.marks)
//     }
//
//     /// Create a copy of this node, with the given set of marks instead
//     /// of the node's own marks.
//     mark(marks: readonly Mark[]): Node {
//         return marks == this.marks ? this : new Node(this.type, this.attrs, this.content, marks)
//     }
//
//     /// Create a copy of this node with only the content between the
//     /// given positions. If `to` is not given, it defaults to the end of
//     /// the node.
//     cut(from: number, to: number = this.content.size): Node {
//         if (from == 0 && to == this.content.size) return this
//         return this.copy(this.content.cut(from, to))
//     }
//
//     /// Cut out the part of the document between the given positions, and
//     /// return it as a `Slice` object.
//     slice(from: number, to: number = this.content.size, includeParents = false) {
//         if (from == to) return Slice.empty
//
//         let $from = this.resolve(from), $to = this.resolve(to)
//         let depth = includeParents ? 0 : $from.sharedDepth(to)
//         let start = $from.start(depth), node = $from.node(depth)
//         let content = node.content.cut($from.pos - start, $to.pos - start)
//         return new Slice(content, $from.depth - depth, $to.depth - depth)
//     }
//
//     /// Replace the part of the document between the given positions with
//     /// the given slice. The slice must 'fit', meaning its open sides
//     /// must be able to connect to the surrounding content, and its
//     /// content nodes must be valid children for the node they are placed
//     /// into. If any of this is violated, an error of type
//     /// [`ReplaceError`](#model.ReplaceError) is thrown.
//     replace(from: number, to: number, slice: Slice) {
//         return replace(this.resolve(from), this.resolve(to), slice)
//     }
//
//     /// Find the node directly after the given position.
//     nodeAt(pos: number): Node | null {
//         for (let node: Node | null = this;;) {
//             let {index, offset} = node.content.findIndex(pos)
//             node = node.maybeChild(index)
//             if (!node) return null
//             if (offset == pos || node.isText) return node
//             pos -= offset + 1
//         }
//     }
//
//     /// Find the (direct) child node after the given offset, if any,
//     /// and return it along with its index and offset relative to this
//     /// node.
//     childAfter(pos: number): {node: Node | null, index: number, offset: number} {
//         let {index, offset} = this.content.findIndex(pos)
//         return {node: this.content.maybeChild(index), index, offset}
//     }
//
//     /// Find the (direct) child node before the given offset, if any,
//     /// and return it along with its index and offset relative to this
//     /// node.
//     childBefore(pos: number): {node: Node | null, index: number, offset: number} {
//         if (pos == 0) return {node: null, index: 0, offset: 0}
//         let {index, offset} = this.content.findIndex(pos)
//         if (offset < pos) return {node: this.content.child(index), index, offset}
//         let node = this.content.child(index - 1)
//         return {node, index: index - 1, offset: offset - node.nodeSize}
//     }
//
//     /// Resolve the given position in the document, returning an
//     /// [object](#model.ResolvedPos) with information about its context.
//     resolve(pos: number) { return ResolvedPos.resolveCached(this, pos) }
//
//     /// @internal
//     resolveNoCache(pos: number) { return ResolvedPos.resolve(this, pos) }
//
//     /// Test whether a given mark or mark type occurs in this document
//     /// between the two given positions.
//     rangeHasMark(from: number, to: number, type: Mark | MarkType): boolean {
//         let found = false
//         if (to > from) this.nodesBetween(from, to, node => {
//             if (type.isInSet(node.marks)) found = true
//             return !found
//         })
//         return found
//     }
//
//     /// True when this is a block (non-inline node)
//     get isBlock() { return this.type.isBlock }
//
//     /// True when this is a textblock node, a block node with inline
//     /// content.
//     get isTextblock() { return this.type.isTextblock }
//
//     /// True when this node allows inline content.
//     get inlineContent() { return this.type.inlineContent }
//
//     /// True when this is an inline node (a text node or a node that can
//     /// appear among text).
//     get isInline() { return this.type.isInline }
//
//     /// True when this is a text node.
//     get isText() { return this.type.isText }
//
//     /// True when this is a leaf node.
//     get isLeaf() { return this.type.isLeaf }
//
//     /// True when this is an atom, i.e. when it does not have directly
//     /// editable content. This is usually the same as `isLeaf`, but can
//     /// be configured with the [`atom` property](#model.NodeSpec.atom)
//     /// on a node's spec (typically used when the node is displayed as
//     /// an uneditable [node view](#view.NodeView)).
//     get isAtom() { return this.type.isAtom }
//
//     /// Return a string representation of this node for debugging
//     /// purposes.
//     toString(): string {
//         if (this.type.spec.toDebugString) return this.type.spec.toDebugString(this)
//         let name = this.type.name
//         if (this.content.size)
//             name += "(" + this.content.toStringInner() + ")"
//         return wrapMarks(this.marks, name)
//     }
//
//     /// Get the content match in this node at the given index.
//     contentMatchAt(index: number) {
//         let match = this.type.contentMatch.matchFragment(this.content, 0, index)
//         if (!match) throw new Error("Called contentMatchAt on a node with invalid content")
//         return match
//     }
//
//     /// Test whether replacing the range between `from` and `to` (by
//     /// child index) with the given replacement fragment (which defaults
//     /// to the empty fragment) would leave the node's content valid. You
//     /// can optionally pass `start` and `end` indices into the
//     /// replacement fragment.
//     canReplace(from: number, to: number, replacement = Fragment.empty, start = 0, end = replacement.childCount) {
//         let one = this.contentMatchAt(from).matchFragment(replacement, start, end)
//         let two = one && one.matchFragment(this.content, to)
//         if (!two || !two.validEnd) return false
//         for (let i = start; i < end; i++) if (!this.type.allowsMarks(replacement.child(i).marks)) return false
//         return true
//     }
//
//     /// Test whether replacing the range `from` to `to` (by index) with
//     /// a node of the given type would leave the node's content valid.
//     canReplaceWith(from: number, to: number, type: NodeType, marks?: readonly Mark[]) {
//         if (marks && !this.type.allowsMarks(marks)) return false
//         let start = this.contentMatchAt(from).matchType(type)
//         let end = start && start.matchFragment(this.content, to)
//         return end ? end.validEnd : false
//     }
//
//     /// Test whether the given node's content could be appended to this
//     /// node. If that node is empty, this will only return true if there
//     /// is at least one node type that can appear in both nodes (to avoid
//     /// merging completely incompatible nodes).
//     canAppend(other: Node) {
//         if (other.content.size) return this.canReplace(this.childCount, this.childCount, other.content)
//         else return this.type.compatibleContent(other.type)
//     }
//
//     /// Check whether this node and its descendants conform to the
//     /// schema, and raise error when they do not.
//     check() {
//         if (!this.type.validContent(this.content))
//             throw
//              new RangeError(`Invalid content for node ${this.type.name}: ${this.content.toString().slice(0, 50)}`)
//         let copy = Mark.none
//                 for (let i = 0; i < this.marks.length; i++) copy = this.marks[i].addToSet(copy)
//         if (!Mark.sameSet(copy, this.marks))
//             throw new RangeError(`Invalid collection of marks for node ${this.type.name}:
//                  ${this.marks.map(m => m.type.name)}`)
//         this.content.forEach(node => node.check())
//     }
//
//     /// Return a JSON-serializeable representation of this node.
//     toJSON(): any {
//         let obj: any = {type: this.type.name}
//         for (let _ in this.attrs) {
//             obj.attrs = this.attrs
//             break
//         }
//         if (this.content.size)
//             obj.content = this.content.toJSON()
//         if (this.marks.length)
//             obj.marks = this.marks.map(n => n.toJSON())
//         return obj
//     }
//
//     /// Deserialize a node from its JSON representation.
//     static fromJSON(schema: Schema, json: any): Node {
//         if (!json) throw new RangeError("Invalid input for Node.fromJSON")
//         let marks = null
//         if (json.marks) {
//             if (!Array.isArray(json.marks)) throw new RangeError("Invalid mark data for Node.fromJSON")
//             marks = json.marks.map(schema.markFromJSON)
//         }
//         if (json.type == "text") {
//             if (typeof json.text != "string") throw new RangeError("Invalid text node in JSON")
//             return schema.text(json.text, marks)
//         }
//         let content = Fragment.fromJSON(schema, json.content)
//         return schema.nodeType(json.type).create(json.attrs, content, marks)
//     }
// }
// endregion

open class NodeBase(open val type: NodeType, open val attrs: Attrs? = null) {
  override fun toString(): String {
    return "NodeBase(type=${type.name}, attrs=$attrs)"
  }
}

interface UnsupportedNode {
  var originalNodeName: String?
}

// Indicates that the Node should not be serialised to Json when
// sanitised = true parameter is provided
interface JsonOmitNode

@JvmInline
value class NodeId(val id: String) : Serializable

/**
 * This class represents a node in the tree that makes up a ProseMirror document. So a document is
 * an instance of `Node`, with children that are also instances of `Node`.
 * Nodes are persistent data structures. Instead of changing them, you create new ones with the
 * content you want. Old ones keep pointing at the old document shape. This is made cheaper by
 * sharing structure between the old and new data as much as possible, which a tree shape like this
 * (without back pointers) makes easy.
 *
 * **Do not** directly mutate the properties of a `Node` object. See [the guide](/docs/guide/#doc)
 * for more information.
 */
@Suppress("EqualsWithHashCodeExist")
open class Node constructor(
  // The type of node that this is.
  override val type: NodeType,
  // An object mapping attribute names to values. The kind of attributes allowed and required are
  // [determined](#model.NodeSpec.attrs) by the node type.
  override val attrs: Attrs,
  // A fragment holding the node's children.
  content: Fragment? = null,
  // The marks (things like whether it is emphasized or part of a link) applied to this node.
  val marks: List<Mark> = Mark.none
) : NodeBase(type, attrs) {

  private var unknownFields: Map<String, JsonElement>? = null

  // A container holding the node's children.
  val content: Fragment

  // For text nodes, this contains the node's text content.
  open val text: String? = null

  var nodeId: NodeId = NodeId("${type.name}&${UUID.randomUUID()}")

  init {
    this.content = content ?: Fragment.empty
  }

  // The size of this node, as defined by the integer-based [indexing scheme]
  // (/docs/guide/#doc.indexing). For text nodes, this is the amount of characters. For other leaf
  // nodes, it is one. For non-leaf nodes, it is the size of the content plus two (the start and
  // end token).
  open val nodeSize: Int
    get() = if (this.isLeaf) 1 else 2 + this.content.size

  // The number of children that the node has.
  val childCount: Int
    get() = this.content.childCount

  // True when this is a block (non-inline node)
  val isBlock: Boolean
    get() = this.type.isBlock

  // True when this is a textblock node, a block node with inline content.
  val isTextblock: Boolean
    get() = this.type.isTextblock

  // True when this node allows inline content.
  val inlineContent: Boolean
    get() = this.type.inlineContent

  // True when this is an inline node (a text node or a node that can appear among text).
  val isInline: Boolean
    get() = this.type.isInline

  // True when this is a text node.
  val isText: Boolean
    get() = this.type.isText

  // True when this is a leaf node.
  val isLeaf: Boolean
    get() = this.type.isLeaf

  // True when this is an empty top level node (default: "doc") with no children
  val isEmptyTop: Boolean
    get() = this.type.isTopType && this.childCount == 0

  // True when this is an atom, i.e. when it does not have directly editable content. This is
  // usually the same as `isLeaf`, but can be configured with the
  // [`atom` property](#model.NodeSpec.atom) on a node's spec (typically used when the node is
  // displayed as an uneditable [node view](#view.NodeView)).
  val isAtom: Boolean
    get() = this.type.isAtom

  // True if node is visually blank.
  open val isBlank: Boolean
    get() = if (isInline && !isText) {
      false
    } else {
      text?.isBlank() ?: true
    }

  // Concatenates all the text nodes found in this fragment and its children.
  open val textContent: String
    get() {
      val func = this.type.spec.leafText
      return if (this.isLeaf && func != null) {
        func.invoke(this)
      } else {
        this.textBetween(0, this.content.size, "", null)
      }
    }

  fun toKeyboardText(): String {
    val sb = StringBuilder()
    content.forEach { node, _, _ ->
      if (node is TextNode) {
        sb.append(node.text)
      } else {
        sb.append("�")
      }
    }
    return sb.toString()
  }

  fun computeAttr(name: String): Any? {
    return if (attrs.containsKey(name)) attrs[name] else type.defaultAttrs[name]
  }

  inline fun <reified T : Any?> attr(name: String, default: T? = null): T {
    return (computeAttr(name) as T? ?: default) as T
  }

  // Get the child node at the given index. Raises an error when the index is out of range.
  fun child(index: Int): Node {
    return this.content.child(index)
  }

  // Get the child node at the given index, if it exists.
  fun maybeChild(index: Int): Node? {
    return this.content.maybeChild(index)
  }

  // Call `f` for every child node, passing the node, its offset into this parent node, and its index.
  fun forEach(f: (node: Node, offset: Int, index: Int) -> Unit) {
    this.content.forEach(f)
  }

  // Invoke a callback for all descendant nodes recursively between the given two positions that
  // are relative to start of this node's content. The callback is invoked with the node, its
  // parent-relative position, its parent node, and its child index.
  // When the callback returns false for a given node, that node's children will not be recursed
  // over. The last parameter can be used to specify a starting position to count from.
  fun nodesBetween(
    from: Int,
    to: Int,
    f: (node: Node, pos: Int, parent: Node?, index: Int) -> Boolean,
    startPos: Int = 0,
    terminate: () -> Boolean = { false }
  ) {
    this.content.nodesBetween(from, to, f, startPos, this, terminate)
  }

  // Call the given callback for every descendant node. Doesn't descend into a node when the
  // callback returns `false`.
  fun descendants(
    f: (node: Node, pos: Int, parent: Node?, index: Int) -> Boolean
  ) {
    this.nodesBetween(0, this.content.size, f)
  }

  /**
   * Traverse descendants of the current node and looks for a children node.
   */
  fun deepContains(nodeToHighlight: Node?): Boolean {
    val highlightNode = nodeToHighlight ?: return false
    var found = false
    this.descendants { node, pos, _, _ ->
      if (node.nodeId == highlightNode.nodeId) {
        found = true
      }
      !found
    }
    return found
  }

  /**
   * Checks if a node is an immediate child of the current node
   */
  fun shallowContains(nodeToFind: Node): Boolean = content.content.contains(nodeToFind)

  fun resolve(nodeToFind: Node, charIndex: Int): ResolvedPos? {
    var result: ResolvedPos? = null
    this.descendants { node, nodePos, _, _ ->
      if (node.nodeId == nodeToFind.nodeId) {
        if (node.isInline) {
          val endIndex = node.text?.length ?: 1
          if (result == null && endIndex >= charIndex) {
            result = resolve(nodePos + charIndex)
          }
        } else {
          var index = 0
          if (node.childCount == 0) {
            if (node.isLeaf) {
              // BlockCard and EmbedCard
              result = resolve(nodePos + charIndex)
            } else {
              // Empty codeblock
              result = resolve(nodePos + charIndex + 1)
            }
          } else {
            node.descendants { childNode, childNodePos, _, _ ->
              val endIndex = index + (childNode.text?.length ?: (if (childNode.isInline) 1 else 0))
              if (result == null && endIndex >= charIndex) {
                result = resolve(nodePos + childNodePos + charIndex - index + 1)
              }
              index = endIndex
              index < charIndex
            }
          }
        }
      }
      result == null
    }
    return result
  }

  fun resolve(nodeToFind: Node): ResolvedPos? {
    var result: ResolvedPos? = null
    this.descendants { node, nodePos, _, _ ->
      if (node.nodeId == nodeToFind.nodeId) {
        result = resolve(nodePos)
      }
      result == null
    }
    return result
  }

  fun toCharInd(pos: Int): Int {
    val size = this.content.size
    if (pos == 0 || pos > size + 1) {
      return -1
    }
    var res: Int? = null
    var textLen = 0
    this.nodesBetween(
      from = 0,
      to = size,
      f = { node, nodePos, _, _ ->
        val currentTextLen = node.text?.length ?: (if (node.isInline) 1 else 0)
        val endIndex = nodePos + currentTextLen
        if (endIndex >= pos) {
          res = textLen + pos - nodePos - 1
          false
        } else {
          textLen += currentTextLen
          true
        }
      },
      terminate = { res != null }
    )
    return res ?: textLen
  }

  // Get all text between positions `from` and `to`. When `blockSeparator` is given, it will be
  // inserted to separate text from different block nodes. If `leafText` is given, it'll be
  // inserted for every non-text leaf node encountered, otherwise
  // [`leafText`](#model.NodeSpec^leafText) will be used.
  fun textBetween(
    from: Int,
    to: Int,
    blockSeparator: String? = null,
    leafText: (leafNode: Node) -> String
  ): String {
    return this.content.textBetween(from, to, blockSeparator, leafText)
  }

  fun textBetween(
    from: Int,
    to: Int,
    blockSeparator: String? = null,
    leafText: String? = null
  ): String {
    return this.content.textBetween(from, to, blockSeparator, leafText)
  }

  // Returns this node's first child, or `null` if there are no children.
  val firstChild: Node?
    get() = this.content.firstChild

  // Returns this node's last child, or `null` if there are no children.
  val lastChild: Node?
    get() = this.content.lastChild

  // Test whether two nodes represent the same piece of document.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Node) return false

    return this.sameMarkup(other) && this.content == other.content
  }

  // Compare the markup (type, attributes, and marks) of this node to those of another. Returns
  // `true` if both have the same markup.
  fun sameMarkup(other: Node): Boolean {
    return this.hasMarkup(other.type, other.attrs, other.marks)
  }

  // Check whether this node's markup correspond to the given type, attributes, and marks.
  fun hasMarkup(type: NodeType, attrs: Attrs?, marks: List<Mark>? = null): Boolean {
    return this.type == type &&
      compareDeep(
        type.defaultAttrsIncludingNullValues + this.attrs,
        type.defaultAttrsIncludingNullValues + (attrs ?: emptyMap())
      ) &&
      Mark.sameSet(this.marks, marks ?: Mark.none)
  }

  // Create a new node with the same markup as this node, containing the given content
  // (or empty, if no content is given).
  fun copy(content: Fragment? = null): Node {
    if (content == this.content) return this
    return this.type.creator.create(this.type, this.attrs, content, this.marks).also {
      it.unknownFields = this.unknownFields
    }
  }

  // Always create a new node with the same markup and attributes as this node, containing the given content
  // (or empty, if no content is given).
  fun copy(content: Fragment? = null, attrs: Attrs? = null): Node {
    return this.type.creator.create(this.type, attrs ?: this.attrs, content, this.marks).also {
      it.unknownFields = this.unknownFields
    }
  }

  // Create a copy of this node, with the given set of marks instead of the node's own marks.
  open fun mark(marks: List<Mark>): Node {
    return if (marks == this.marks) {
      this
    } else {
      this.type.creator.create(this.type, this.attrs, content, marks)
    }
  }

  // Create a copy of this node with only the content between the given positions. If `to` is not
  // given, it defaults to the end of the node.
  open fun cut(from: Int, to: Int? = null): Node {
    val end = to ?: this.content.size
    if (from == 0 && end == this.content.size) return this
    return this.copy(this.content.cut(from, end))
  }

  // Cut out the part of the document between the given positions, and return it as a `Slice` object.
  fun slice(from: Int, to: Int = this.content.size, includeParents: Boolean = false): Slice {
    if (from == to) return Slice.empty

    val resolvedFrom = this.resolve(from)
    val resolvedTo = this.resolve(to)
    val depth = if (includeParents) 0 else resolvedFrom.sharedDepth(to)
    val start = resolvedFrom.start(depth)
    val node = resolvedFrom.node(depth)
    val content = node.content.cut(resolvedFrom.pos - start, resolvedTo.pos - start)
    return Slice(content, resolvedFrom.depth - depth, resolvedTo.depth - depth)
  }

  // Replace the part of the document between the given positions with the given slice. The slice
  // must 'fit', meaning its open sides must be able to connect to the surrounding content, and
  // its content nodes must be valid children for the node they are placed into. If any of this is
  // violated, an error of type [`ReplaceError`](#model.ReplaceError) is thrown.
  fun replace(from: Int, to: Int, slice: Slice): Node {
    return replace(this.resolve(from), this.resolve(to), slice)
  }

  // Find the node directly after the given position.
  fun nodeAt(pos: Int): Node? {
    var pos = pos
    var node: Node? = this
    while (node != null) {
      val ind = node.content.findIndex(pos)
      val (index, offset) = ind.run { index to offset }
      node = node.maybeChild(index) ?: return null
      if (offset == pos || node.isText) return node
      pos -= offset + 1
    }
    return null
  }

  // Find the (direct) child node after the given offset, if any, and return it along with its
  // index and offset relative to this node.
  fun childAfter(pos: Int): ChildPosition {
    val (index, offset) = this.content.findIndex(pos).run { index to offset }
    return ChildPosition(node = this.content.maybeChild(index), index, offset)
  }

  // Find the (direct) child node before the given offset, if any, and return it along with its
  // index and offset relative to this node.
  fun childBefore(pos: Int): ChildPosition {
    if (pos == 0) return ChildPosition(null, 0, 0)
    val (index, offset) = this.content.findIndex(pos).run { index to offset }
    if (offset < pos) return ChildPosition(node = this.content.child(index), index, offset)
    val node = this.content.child(index - 1)
    return ChildPosition(node, index = index - 1, offset = offset - node.nodeSize)
  }

  data class ChildPosition(val node: Node?, val index: Int, val offset: Int)

  // Resolve the given position in the document, returning an [object](#model.ResolvedPos) with
  // information about its context. Threadsafe.
  fun resolve(pos: Int) = ResolvedPos.resolveCached(this, pos)

  // Resolve safely - if pos cannot be resolved, return null instead
  fun resolveOrNull(pos: Int) = try {
    resolve(pos)
  } catch (e: RangeError) {
    null
  }

  fun resolveOrNull(nodeToFind: Node, charIndex: Int) = try {
    resolve(nodeToFind, charIndex)
  } catch (e: RangeError) {
    null
  }

  internal fun resolveNoCache(pos: Int) = ResolvedPos.resolve(this, pos)

  // Test whether a given mark or mark type occurs in this document between the two given positions.
  fun rangeHasMark(from: Int, to: Int, type: Mark): Boolean {
    var found = false
    if (to > from) {
      this.nodesBetween(from, to, { node: Node, pos: Int, parent: Node?, index: Int ->
        if (type.isInSet(node.marks)) found = true
        return@nodesBetween !found
      })
    }
    return found
  }

  fun rangeHasMark(from: Int, to: Int, type: MarkType): Boolean {
    var found = false
    if (to > from) {
      this.nodesBetween(from, to, { node: Node, pos: Int, parent: Node?, index: Int ->
        if (type.isInSet(node.marks) != null) found = true
        return@nodesBetween !found
      })
    }
    return found
  }

  // Return a string representation of this node for debugging purposes.
  override fun toString(): String {
    this.type.spec.toDebugString?.let { return it.invoke(this) }
    var name = this.type.name
    if (this.attrs.isNotEmpty()) {
      name += "(${this.attrs.entries.joinToString()})"
    }
    if (this.content.size != 0) {
      name += "{" + this.content.toStringInner() + "}"
    }
    return wrapMarks(this.marks, name)
  }

  // Get the content match in this node at the given index.
  fun contentMatchAt(index: Int): ContentMatch {
    return type.contentMatch.matchFragment(content, 0, index)
      ?: throw IllegalStateException("Called contentMatchAt on a node with invalid content")
  }

  // Test whether replacing the range between `from` and `to` (by child index) with the given
  // replacement fragment (which defaults to the empty fragment) would leave the node's content
  // valid. You can optionally pass `start` and `end` indices into the replacement fragment.
  fun canReplace(
    from: Int,
    to: Int,
    replacement: Fragment = Fragment.empty,
    start: Int = 0,
    end: Int = replacement.childCount
  ): Boolean {
    val one = this.contentMatchAt(from).matchFragment(replacement, start, end)
    val two = one?.matchFragment(this.content, to)
    if (two == null || !two.validEnd) return false
    for (i in start until end) {
      if (!this.type.allowsMarks(replacement.child(i).marks)) return false
    }
    return true
  }

  // Test whether replacing the range `from` to `to` (by index) with a node of the given type
  // would leave the node's content valid.
  fun canReplaceWith(from: Int, to: Int, type: NodeType, marks: List<Mark>? = null): Boolean {
    if (!marks.isNullOrEmpty() && !this.type.allowsMarks(marks)) return false
    val start = this.contentMatchAt(from).matchType(type)
    val end = start?.matchFragment(this.content, to)
    return end?.validEnd == true
  }

  // Test whether the given node's content could be appended to this node. If that node is empty,
  // this will only return true if there is at least one node type that can appear in both nodes
  // (to avoid merging completely incompatible nodes).
  fun canAppend(other: Node): Boolean {
    return if (other.content.size != 0) {
      this.canReplace(this.childCount, this.childCount, other.content)
    } else {
      this.type.compatibleContent(other.type)
    }
  }

  // Check whether this node and its descendants conform to the schema, and raise error when they do not.
  @Suppress("MagicNumber")
  fun check() {
    if (!this.type.validContent(this.content)) {
      throw RangeError("Invalid content for node ${this.type.name}: ${this.content.toString().slice(0, 50)}")
    }
    var copy = Mark.none
    for (mark in marks) {
      copy = mark.addToSet(copy)
    }
    if (!Mark.sameSet(copy, this.marks)) {
      throw RangeError(
        "Invalid collection of marks for node ${this.type.name}: ${this.marks.map { m -> m.type.name }}"
      )
    }
    this.content.forEach { node, _, _ -> node.check() }
  }

  // Return a JSON-serializeable representation of this node.
  open fun toJSON() = JsonObject(
    buildJsonObject {
      toJson(this)
    }.toMutableMap().apply {
      // not sure what I am missing here but toJson() is adding the
      // the id when the content is restored but it shouldn't
      remove("id")
    }
  )

  open fun toJSONWithId() = buildJsonObject {
    toJson(this, true)
  }

  fun toJSON(sanitised: Boolean): JsonObject {
    if (sanitised) {
      val tr = Transform(this)
      var from = -1
      var to = -1
      tr.doc.nodesBetween(
        0,
        tr.doc.content.size,
        f = { node: Node, pos: Int, _: Node?, _: Int ->
          val shouldOmit = node is JsonOmitNode || node.marks.firstOrNull { it is JsonOmitMark } != null
          if (shouldOmit) {
            from = pos
            to = pos + node.nodeSize
          }
          !shouldOmit
        },
        terminate = {
          from != -1
        }
      )
      if (from != -1) {
        tr.delete(from, to)
      }
      return tr.doc.toJSON()
    } else {
      return toJSON()
    }
  }

  protected open fun toJson(builder: JsonObjectBuilder) = builder.apply {
    toJson(builder, false)
  }

  protected open fun toJson(builder: JsonObjectBuilder, withId: Boolean = false) = builder.apply {
    put("type", type.name)
    if (withId) {
      put("id", nodeId.id)
    }
    if (attrs !== EmptyAttrs) {
      put("attrs", JSON.encodeToJsonElement(attrs))
    }
    if (content !== Fragment.empty) {
      put("content", content.toJSON(withId))
    }
    if (marks.isNotEmpty()) {
      putJsonArray("marks") {
        for (mark in marks) {
          add(mark.toJSON(withId))
        }
      }
    }
    unknownFields?.let {
      it.forEach { (key, value) -> put(key, value) }
    }
  }

  open fun hasToolbarItems(): Boolean {
    return type.spec.selectable && (isBlock || isText) // text can have LinkMark
  }

  /**
   * If this node's spec has selectable = false, but is tapped anyway, what should we do? Return null to not select
   * anything (ignore the tap), or return an appropriate text selection to select instead
   *
   * By default it will return the next valid text selection in the node.
   */
  open fun selectionPassthrough(doc: Node): TextSelection? {
    return doc.resolve(this)?.let { Selection.findFrom(it, 1, true) } as? TextSelection
  }

  companion object {

    // Deserialize a node from its JSON representation.
    @Suppress("ThrowsCount", "SwallowedException", "ComplexMethod")
    fun fromJSON(schema: Schema, json: JsonObject?, withId: Boolean = false): Node {
      if (json == null) throw RangeError("Invalid input for Node.fromJSON")
      var marks: List<Mark>? = null
      if (json.containsKey("marks")) {
        val marksArray = json["marks"]!!.jsonArray
        marks = marksArray.map { schema.markFromJSON(it.jsonObject, withId) }
      }
      val type = json["type"]?.jsonPrimitive?.contentOrNull
      if (type == "text") {
        val text = json["text"]?.jsonPrimitive
        if (text?.isString != true) throw RangeError("Invalid text node in JSON")
        return schema.text(text.content, marks)
      }
      val content = Fragment.fromJSON(schema, json["content"]?.jsonArray, withId)
      val attrs = json["attrs"]?.jsonObject?.mapValues {
        if (it.value is JsonNull) null else JSON.decodeFromJsonElement<Any>(it.value)
      }
      val id = json["id"]?.jsonPrimitive?.contentOrNull
      return try {
        schema.nodeType(type!!).create(attrs, content, marks).also {
          if (withId && id != null) {
            it.nodeId = NodeId(id)
          }
        }
      } catch (ex: RangeError) {
        val unsupportedNodeType = if (json["content"] == null) {
          schema.spec.unsupportedInlineNode
        } else {
          schema.spec.unsupportedNode
        }
        schema.nodeType(unsupportedNodeType).create(attrs, content, marks).also {
          if (withId && id != null) {
            it.nodeId = NodeId(id)
          }
          (it as? UnsupportedNode)?.let { unsupportedNode ->
            unsupportedNode.originalNodeName = type
          }
        }
      }.also { node ->
        // for round tripping
        node.unknownFields = json.fieldsExcept("marks", "type", "content", "attrs")
      }
    }
  }
}

private fun JsonObject.fieldsExcept(vararg fieldNames: String): Map<String, JsonElement>? {
  return this.filter { !fieldNames.contains(it.key) }.takeIf { it.isNotEmpty() }
}

// ;(Node.prototype as any).text = undefined

// region TextNode
// export class TextNode extends Node {
//     readonly text: string
//
//             /// @internal
//             constructor(type: NodeType, attrs: Attrs, content: string, marks?: readonly Mark[]) {
//                 super(type, attrs, null, marks)
//                 if (!content) throw new RangeError("Empty text nodes are not allowed")
//                 this.text = content
//             }
//
//     toString() {
//         if (this.type.spec.toDebugString) return this.type.spec.toDebugString(this)
//         return wrapMarks(this.marks, JSON.stringify(this.text))
//     }
//
//     get textContent() { return this.text }
//
//     textBetween(from: number, to: number) { return this.text.slice(from, to) }
//
//     get nodeSize() { return this.text.length }
//
//     mark(marks: readonly Mark[]) {
//         return marks == this.marks ? this : new TextNode(this.type, this.attrs, this.text, marks)
//     }
//
//     withText(text: string) {
//         if (text == this.text) return this
//         return new TextNode(this.type, this.attrs, text, this.marks)
//     }
//
//     cut(from = 0, to = this.text.length) {
//         if (from == 0 && to == this.text.length) return this
//         return this.withText(this.text.slice(from, to))
//     }
//
//     eq(other: Node) {
//         return this.sameMarkup(other) && this.text == other.text
//     }
//
//     toJSON() {
//         let base = super.toJSON()
//         base.text = this.text
//         return base
//     }
// }
//endregion
class TextNode : Node {
  override val text: String

  override val textContent: String
    get() = this.text

  override val nodeSize: Int
    get() = this.text.length

  constructor(type: NodeType, attrs: Attrs, content: String, marks: List<Mark>?) :
    super(type, attrs, null, marks ?: Mark.none) {
    if (content.isEmpty()) throw RangeError("Empty text nodes are not allowed")
    this.text = content
  }

  override fun toString(): String {
    if (this.type.spec.toDebugString != null) return this.type.spec.toDebugString!!(this)
    return wrapMarks(this.marks, Json.encodeToString(this.text))
  }

  fun textBetween(from: Int, to: Int): String {
    return this.text.slice(from, to)
  }

  override fun mark(marks: List<Mark>): Node {
    return if (marks == this.marks) {
      this
    } else {
      TextNode(this.type, this.attrs, this.text, marks)
    }
  }

  fun withText(text: String): TextNode {
    if (text == this.text) return this
    return TextNode(this.type, this.attrs, text, this.marks)
  }

  override fun cut(from: Int, to: Int?): Node {
    val end = to ?: this.text.length
    if (from == 0 && end == this.text.length) return this
    return this.withText(this.text.slice(from, end))
  }

  override fun toJSON(): JsonObject {
    val base = super.toJSON()
    return JsonObject(
      base.toMutableMap().apply {
        put("text", JsonPrimitive(text))
      }
    )
  }

  override fun toJSONWithId(): JsonObject {
    val base = super.toJSONWithId()
    return JsonObject(
      base.toMutableMap().apply {
        put("text", JsonPrimitive(text))
      }
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextNode) return false
    if (!super.equals(other)) return false

    return this.sameMarkup(other) && this.text == other.text
  }

  override fun hashCode(): Int {
    return text.hashCode()
  }
}

// function wrapMarks(marks: readonly Mark[], str: string) {
//     for (let i = marks.length - 1; i >= 0; i--)
//     str = marks[i].type.name + "(" + str + ")"
//     return str
// }
fun wrapMarks(marks: List<Mark>, str: String): String {
  val res = StringBuilder(str)
  marks.asReversed().forEach { mark ->
    res.insert(0, "${mark.type.name}(")
    res.append(")")
  }
  return res.toString()
}
