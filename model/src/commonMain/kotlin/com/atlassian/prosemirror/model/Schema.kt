@file:Suppress("ReturnCount", "ThrowsCount")

package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.util.ConcurrentMutableMap
import com.atlassian.prosemirror.util.slice
import com.atlassian.prosemirror.util.verbose
import kotlinx.serialization.json.JsonObject

// An object holding the attributes of a node.
typealias Attrs = Map<String, Any?>

val EmptyAttrs: Attrs = emptyMap()

// For node types where all attrs have a default value (or which don't have any attributes), build
// up a single reusable default attribute object, and use it for all nodes that don't specify
// specific attributes.
fun defaultAttrs(attrs: Map<String, Attribute>, includeNullValues: Boolean = false): Attrs {
    val res = mutableMapOf<String, Any?>()
    attrs.forEach { (key, value) ->
        if (includeNullValues || value.default != null) {
            res[key] = value.default
        }
    }
    return if (res.isEmpty()) EmptyAttrs else res.toMap()
}

fun checkAttrs(attrs: Map<String, Attribute>, values: Attrs, type: String, name: String) {
    values.keys.forEach { name ->
        if (name !in attrs) throw RangeError("Unsupported attribute $name for $type of type $name")
    }
    attrs.forEach { (name, attr) ->
        attr.validate?.invoke(values[name])
    }
}

// computeAttrs function not needed anymore - for correct round trip we don't combine attributes
// on creation
fun initAttrs(typeName: String, attrs: Map<String, AttributeSpec>?): Map<String, Attribute> {
    return attrs?.mapValues { (name, value) ->
        Attribute(typeName, name, value)
    } ?: emptyMap()
}

class RangeError(message: String) : IllegalArgumentException("Range Error: $message")

// Node types are objects allocated once per `Schema` and used to [tag](#model.Node.type) `Node`
// instances. They contain information about the node type, such as its name and what kind of nod
// it represents.
class NodeType internal constructor(
    // The name the node type has in this schema.
    val name: String,
    // A link back to the `Schema` the node type belongs to.
    val schema: Schema,
    // The spec that this type is based on
    val spec: NodeSpec
) {
    internal val groups: List<String>
    internal val attrs: Map<String, Attribute>
    internal val defaultAttrs: Attrs
    internal val defaultAttrsIncludingNullValues: Attrs

    // True if this node type has inline content.
    var inlineContent: Boolean = false

    // True if this is a block type
    val isBlock: Boolean

    // True if this is the text node type.
    val isText: Boolean

    // The starting match of the node type's content expression.
    lateinit var contentMatch: ContentMatch

    // The set of marks allowed in this node. `null` means all marks are allowed.
    var markSet: List<MarkType>? = null

    // True if this is an inline type.
    val isInline: Boolean
        get() = !this.isBlock

    // True if this is a textblock type, a block that contains inline content.
    val isTextblock: Boolean
        get() = this.isBlock && this.inlineContent

    val isLeaf: Boolean
        get() = this.contentMatch == ContentMatch.empty

    // True when this node is an atom, i.e. when it does not have directly editable content.
    val isAtom: Boolean
        get() = this.isLeaf || spec.atom

    // Return true when this node type is part of the given
    // [group](#model.NodeSpec.group).
    fun isInGroup(group: String) = this.groups.indexOf(group) > -1

    // If this is a node of the top level type (default: "doc")
    val isTopType: Boolean
        get() = this.name == (schema.spec.topNode ?: "doc")

    val whitespace: Whitespace
        get() = spec.whitespace ?: (if (this.spec.code) Whitespace.PRE else Whitespace.NORMAL)

    var creator: NodeCreator<out Node> = NodeCreator.DEFAULT

    init {
        this.groups = spec.group?.let { listOf(it) } ?: emptyList()
        this.attrs = initAttrs(name, spec.attrs)
        this.defaultAttrs = defaultAttrs(this.attrs)
        this.defaultAttrsIncludingNullValues = defaultAttrs(this.attrs, includeNullValues = true)

        this.isBlock = !(spec.inline || name == "text")
        this.isText = name == "text"
    }

    // Tells you whether this node type has any required attributes.
    fun hasRequiredAttrs(): Boolean {
        return attrs.values.firstOrNull { it.isRequired } != null
    }

    // Indicates whether this node allows some of the same content as the given node type.
    fun compatibleContent(other: NodeType): Boolean {
        return this == other || this.contentMatch.compatible(other.contentMatch)
    }

    private fun computeAttrs(attrs: Attrs?): Attrs {
        return attrs ?: emptyAttrs
    }

    // Create a `Node` of this type. The given attributes are checked and defaulted (you can pass
    // `null` to use the type's defaults entirely, if no required attributes exist). `content` may
    // be a `Fragment`, a node, an array of nodes, or `null`. Similarly `marks` may be `null` to
    // default to the empty set of marks.
    fun create(attrs: Attrs? = null, content: Fragment? = null, marks: List<Mark>? = null): Node {
        if (this.isText) throw IllegalStateException("NodeType.create can't construct text nodes")
        return creator.create(this, this.computeAttrs(attrs), Fragment.from(content), Mark.setFrom(marks))
    }

    fun create(attrs: Attrs? = null, content: Node?, marks: List<Mark>? = null): Node {
        if (this.isText) throw IllegalStateException("NodeType.create can't construct text nodes")
        return creator.create(this, this.computeAttrs(attrs), Fragment.from(content), Mark.setFrom(marks))
    }

    fun create(attrs: Attrs? = null, content: List<Node>?, marks: List<Mark>? = null): Node {
        if (this.isText) throw IllegalStateException("NodeType.create can't construct text nodes")
        return creator.create(this, this.computeAttrs(attrs), Fragment.from(content), Mark.setFrom(marks))
    }

    // Like [`create`](#model.NodeType.create), but check the given content against the node type's
    // content restrictions, and throw an error if it doesn't match.
    fun createChecked(attrs: Attrs? = null, content: Fragment? = null, marks: List<Mark>? = null): Node {
        val thisContent = Fragment.from(content)
        this.checkContent(thisContent)
        return creator.create(this, this.computeAttrs(attrs), thisContent, Mark.setFrom(marks))
    }

    fun createChecked(attrs: Attrs? = null, content: Node?, marks: List<Mark>? = null): Node {
        val thisContent = Fragment.from(content)
        if (!this.validContent(thisContent)) {
            throw RangeError(
                if (verbose) {
                    "Invalid content for node type $name: $thisContent"
                } else {
                    "Invalid content for node type $name"
                }
            )
        }
        return creator.create(this, computeAttrs(attrs), thisContent, Mark.setFrom(marks))
    }

    fun createChecked(attrs: Attrs? = null, content: List<Node>?, marks: List<Mark>? = null): Node {
        val thisContent = Fragment.from(content)
        if (!this.validContent(thisContent)) {
            throw RangeError(
                if (verbose) {
                    "Invalid content for node type $name: $thisContent"
                } else {
                    "Invalid content for node type $name"
                }
            )
        }
        return creator.create(this, this.computeAttrs(attrs), thisContent, Mark.setFrom(marks))
    }

    // Like [`create`](#model.NodeType.create), but see if it is necessary to add nodes to the start
    // or end of the given fragment to make it fit the node. If no fitting wrapping can be found,
    // return null. Note that, due to the fact that required nodes can always be created, this will
    // always succeed if you pass null or `Fragment.empty` as content.
    fun createAndFill(attrs: Attrs? = null, content: Fragment? = null, marks: List<Mark>? = null): Node? {
        val attrs = this.computeAttrs(attrs)
        var content = Fragment.from(content)
        if (content.size != 0) {
            val before = this.contentMatch.fillBefore(content) ?: return null
            content = before.append(content)
        }
        val matched = this.contentMatch.matchFragment(content)
        val after = matched?.fillBefore(Fragment.empty, true) ?: return null
        return creator.create(this, attrs, content.append(after), Mark.setFrom(marks))
    }

    fun createAndFill(attrs: Attrs? = null, content: Node?, marks: List<Mark>?): Node? {
        val attrs = this.computeAttrs(attrs)
        var content = Fragment.from(content)
        if (content.size != 0) {
            val before = this.contentMatch.fillBefore(content) ?: return null
            content = before.append(content)
        }
        val matched = this.contentMatch.matchFragment(content)
        val after = matched?.fillBefore(Fragment.empty, true) ?: return null
        return creator.create(this, attrs, content.append(after), Mark.setFrom(marks))
    }

    fun createAndFill(attrs: Attrs? = null, content: List<Node>?, marks: List<Mark>?): Node? {
        val attrs = this.computeAttrs(attrs)
        var content = Fragment.from(content)
        if (content.size != 0) {
            val before = this.contentMatch.fillBefore(content) ?: return null
            content = before.append(content)
        }
        val matched = this.contentMatch.matchFragment(content)
        val after = matched?.fillBefore(Fragment.empty, true) ?: return null
        return creator.create(this, attrs, content.append(after), Mark.setFrom(marks))
    }

    // Returns true if the given fragment is valid content for this node
    // type.
    fun validContent(content: Fragment): Boolean {
        val result = this.contentMatch.matchFragment(content)
        if (result == null || !result.validEnd) {
            return false
        }
        for (i in 0 until content.childCount) {
            if (!this.allowsMarks(content.child(i).marks)) {
                return false
            }
        }
        return true
    }

    // Throws a RangeError if the given fragment is not valid content for this
    // node type.
    internal fun checkContent(content: Fragment) {
        if (!this.validContent(content)) {
            throw RangeError(
                if (verbose) {
                    "Invalid content for node $name: ${content.toString().slice(0, 50)}"
                } else {
                    "Invalid content for node $name"
                }
            )
        }
    }

    internal fun checkAttrs(attrs: Attrs) {
        checkAttrs(this.attrs, attrs, "node", this.name)
    }

    // Check whether the given mark type is allowed in this node.
    fun allowsMarkType(markType: MarkType): Boolean {
        val markSet = this.markSet
        return markSet == null || markSet.indexOf(markType) > -1
    }

    // Test whether the given set of marks are allowed in this node.
    fun allowsMarks(marks: List<Mark>): Boolean {
        if (this.markSet == null) return true
        marks.forEach {
            if (!this.allowsMarkType(it.type)) return false
        }
        return true
    }

    // Removes the marks that are not allowed in this node from the given set.
    fun allowedMarks(marks: List<Mark>): List<Mark> {
        if (this.markSet == null) return marks
        var copy: MutableList<Mark>? = null
        marks.forEachIndexed { i, mark ->
            if (!this.allowsMarkType(mark.type)) {
                if (copy == null) copy = marks.slice(0..<i).toMutableList()
            } else {
                copy?.add(mark)
            }
        }
        val res = copy
        return when {
            res == null -> marks
            res.isEmpty() -> Mark.none
            else -> res
        }
    }

    override fun toString(): String {
        return "NodeType {name: '$name', schema: $schema, spec: $spec, markSet: $markSet, groups: $groups }"
    }

    companion object {
        internal fun compile(nodes: Map<String, NodeSpec>, schema: Schema): Map<String, NodeType> {
            val result = mutableMapOf<String, NodeType>()
            nodes.forEach { (name, spec) ->
                result[name] = NodeType(name, schema, spec)
            }

            val topType = schema.spec.topNode ?: "doc"
            if (!result.containsKey(topType)) throw RangeError("Schema is missing its top node type ('$topType')")
            if (!result.containsKey("text")) throw RangeError("Every schema needs a 'text' type")
            val textSpec = result["text"]!!
            if (textSpec.attrs.isNotEmpty()) throw RangeError("The text node type should not have attributes")

            return result
        }
    }
}

fun validateType(typeName: String, attrName: String, type: String): (Any?) -> Unit {
    val types = type.split("|")
    return { value ->
        val name = value?.let { value::class.simpleName } ?: "null"
        if (types.indexOf(name) < 0) {
            throw RangeError("Expected value of type $types for attribute $attrName on type $typeName, got $name")
        }
    }
}

// To be type safe every NodeType should be created through create method. Otherwise it would be
// of generic type Node
interface NodeCreator<T : Node> {
    fun create(type: NodeType, attrs: Attrs, content: Fragment? = null, marks: List<Mark> = Mark.none): T

    companion object {
        internal val DEFAULT = object : NodeCreator<Node> {
            override fun create(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>): Node {
                return Node(type, attrs, content, marks)
            }
        }
    }
}

interface MarkCreator<T : Mark> {
    fun create(type: MarkType, attrs: Attrs): T

    companion object {
        internal val DEFAULT = object : MarkCreator<Mark> {
            override fun create(type: MarkType, attrs: Attrs): Mark {
                return Mark(type, attrs)
            }
        }
    }
}

enum class Whitespace {
    PRE,
    NORMAL
}

// Attribute descriptors
class Attribute(
    typeName: String,
    attrName: String,
    options: AttributeSpec
) {
    val hasDefault: Boolean
    val default: Any?
    val validate: ((Any?) -> Unit)?

    val isRequired: Boolean
        get() = !this.hasDefault

    init {
        this.hasDefault = options.hasDefault // Object.prototype.hasOwnProperty.call(options, "default")
        this.default = options.default

        this.validate = options.validateString?.let { validateType(typeName, attrName, it) }
            ?: options.validateFunction
    }
}

// Marks

// Like nodes, marks (which are associated with nodes to signify things like emphasis or being part
// of a link) are [tagged](#model.Mark.type) with type objects, which are instantiated once per
// `Schema`.
class MarkType internal constructor(
    // The name of the mark type.
    val name: String,
    val rank: Int,
    // The schema that this mark type instance is part of.
    val schema: Schema,
    // The spec on which the type is based.
    val spec: MarkSpec
) {
    var creator: MarkCreator<out Mark> = MarkCreator.DEFAULT

    // @internal
    internal val attrs: Map<String, Attribute> = initAttrs(name, spec.attrs)

    // ;(this as any).excluded = null
    internal var excluded: List<MarkType>? = null
    internal val instance: Mark? by lazy {
        val defaults = defaultAttrs(this.attrs)
        creator.create(this, defaults)
    }

    // Create a mark of this type. `attrs` may be `null` or an object containing only some of the
    // mark's attributes. The others, if they have defaults, will be added.
    fun create(attrs: Attrs? = null): Mark {
        val instance = this.instance
        if (attrs == null && instance != null) return instance
        val adjustedAttrs = defaultAttrs(this.attrs) + (attrs ?: emptyAttrs)
        return creator.create(this, adjustedAttrs)
    }

    // When there is a mark of this type in the given set, a new set without it is returned.
    // Otherwise, the input set is returned.
    fun removeFromSet(set: List<Mark>): List<Mark> {
        return set.filter { it.type == this }
    }

    // Tests whether there is a mark of this type in the given set.
    fun isInSet(set: List<Mark>): Mark? {
        return set.firstOrNull { it.type == this }
    }

    internal fun checkAttrs(attrs: Attrs) {
        checkAttrs(this.attrs, attrs, "mark", this.name)
    }

    // Queries whether a given mark type is [excluded](#model.MarkSpec.excludes) by this one.
    fun excludes(other: MarkType): Boolean {
        val excluded = this.excluded
        return excluded != null && excluded.indexOf(other) > -1
    }

    override fun toString(): String {
        return "MarkType($name)"
    }

    companion object {
        internal fun compile(marks: Map<String, MarkSpec>, schema: Schema): Map<String, MarkType> {
            val result = mutableMapOf<String, MarkType>()
            var rank = 0
            marks.forEach { (name, spec) ->
                result[name] = MarkType(name, rank++, schema, spec)
            }
            return result
        }
    }
}

// An object describing a schema, as passed to the [`Schema`](#model.Schema) constructor.
data class SchemaSpec(
    // The node types in this schema. Maps names to [`NodeSpec`](#model.NodeSpec) objects that
    // describe the node type associated with that name. Their order is significant—it determines
    // which [parse rules](#model.NodeSpec.parseDOM) take precedence by default, and which nodes
    // come first in a given [group](#model.NodeSpec.group).
    val nodes: Map<String, NodeSpec> = emptyMap(),

    // The mark types that exist in this schema. The order in which they are provided determines the
    // order in which [mark sets](#model.Mark.addToSet) are sorted and in which
    // [parse rules](#model.MarkSpec.parseDOM) are tried.
    val marks: Map<String, MarkSpec> = emptyMap(),

    // The name of the default top-level node for the schema. Defaults to `"doc"`.
    val topNode: String? = null,

    // The name of the node for the schema to fall back whenever we encounter unknown node type.
    // The node will have original name saved into originalNodeName field if creator returned UnsupportedNode type
    val unsupportedNode: String = "unsupportedBlock",

    // The name of the inline node for the schema to fall back whenever we encounter unknown node type.
    // The node will have original name saved into originalNodeName field if creator returned UnsupportedNode type
    val unsupportedInlineNode: String = "unsupportedInline",

    // The name of the mark for the schema to fall back whenever we encounter unknown mark type.
    // The mark will have original name saved into originalMarkName field if creator returned UnsupportedMark type
    val unsupportedMark: String = "unsupportedMark"
)

// A description of a node type, used when defining a schema.
interface NodeSpec {
    // The content expression for this node, as described in the
    // [schema guide](/docs/guide/#schema.content_expressions). When not given, the node does not
    // allow any content.
    val content: String?

    // The marks that are allowed inside of this node. May be a space-separated string referring to
    // mark names or groups, `"_"` to explicitly allow all marks, or `""` to disallow marks. When
    // not given, nodes with inline content default to allowing all marks, other nodes default to
    // not allowing marks.
    val marks: String?

    // The group or space-separated groups to which this node belongs, which can be referred to in
    // the content expressions for the schema.
    val group: String?

    // Should be set to true for inline nodes. (Implied for text nodes.)
    val inline: Boolean

    // Can be set to true to indicate that, though this isn't a [leaf node](#model.NodeType.isLeaf),
    // it doesn't have directly editable content and should be treated as a single unit in the view.
    val atom: Boolean

    // The attributes that nodes of this type get.
    val attrs: Map<String, AttributeSpec>?

    // Controls whether nodes of this type can be selected as a
    // [node selection](#state.NodeSelection). Defaults to true for non-text nodes.
    val selectable: Boolean

    // Determines whether nodes of this type can be dragged without being selected.
    // Defaults to false.
    val draggable: Boolean

    // Can be used to indicate that this node contains code, which causes some commands to behave
    // differently.
    val code: Boolean

    // Controls way whitespace in this a node is parsed. The default is `"normal"`, which causes the
    // [DOM parser](#model.DOMParser) to collapse whitespace in normal mode, and normalize it
    // (replacing newlines and such with spaces) otherwise. `"pre"` causes the parser to preserve
    // spaces inside the node. When this option isn't given, but [`code`](#model.NodeSpec.code) is
    // true, `whitespace` will default to `"pre"`. Note that this option doesn't influence the way
    // the node is rendered—that should be handled by `toDOM` and/or styling.
    val whitespace: Whitespace?

    // Determines whether this node is considered an important parent node during replace operations
    // (such as paste). Non-defining (the default) nodes get dropped when their entire content is
    // replaced, whereas defining nodes persist and wrap the inserted content.
    val definingAsContext: Boolean?

    // In inserted content the defining parents of the content are preserved when possible.
    // Typically, non-default-paragraph textblock types, and possibly list items, are marked as
    // defining.
    val definingForContent: Boolean?

    // When enabled, enables both [`definingAsContext`](#model.NodeSpec.definingAsContext) and
    // [`definingForContent`](#model.NodeSpec.definingForContent).
    val defining: Boolean?

    // When enabled (default is false), the sides of nodes of this type count as boundaries that
    // regular editing operations, like backspacing or lifting, won't cross. An example of a node
    // that should probably have this enabled is a table cell.
    val isolating: Boolean?

    // Defines the default way a node of this type should be serialized to DOM/HTML (as used by
    // [`DOMSerializer.fromSchema`](#model.DOMSerializer^fromSchema)). Should return a DOM node or
    // an [array structure](#model.DOMOutputSpec) that describes one, with an optional number zero
    // (“hole”) in it to indicate where the node's content should be inserted.
    //
    // For text nodes, the default is to create a text DOM node. Though it is possible to create a
    // serializer where text is rendered differently, this is not supported inside the editor, so
    // you shouldn't override that in your text node spec.
    val toDOM: ((node: Node) -> DOMOutputSpec)?

    // Associates DOM parser information with this node, which can be used by
    // [`DOMParser.fromSchema`](#model.DOMParser^fromSchema) to automatically derive a parser. The
    // `node` field in the rules is implied (the name of this node will be filled in automatically).
    // If you supply your own parser, you do not need to also specify parsing rules in your schema.
    val parseDOM: List<TagParseRule>?

    // Defines the default way a node of this type should be serialized to a string representation
    // for debugging (e.g. in error messages).
    val toDebugString: ((node: Node) -> String)?

    // Defines the default way a [leaf node](#model.NodeType.isLeaf) of this type should be
    // serialized to a string (as used by [`Node.textBetween`](#model.Node^textBetween) and
    // [`Node.textContent`](#model.Node^textContent)).
    val leafText: ((node: Node) -> String)?

    // A single inline node in a schema can be set to be a linebreak
    // equivalent. When converting between block types that support the
    // node and block types that don't but have
    // [`whitespace`](#model.NodeSpec.whitespace) set to `"pre"`,
    // [`setBlockType`](#transform.Transform.setBlockType) will convert
    // between newline characters to or from linebreak nodes as
    // appropriate.
    val linebreakReplacement: Boolean?

    // Determines whether this node is automatically focused during navigation. Mainly used for navigation with arrow
    // key and backspace/delete key. Defaults to false.
    val autoFocusable: Boolean?

    // Node specs may include arbitrary properties that can be read by other code via
    // [`NodeType.spec`](#model.NodeType.spec).
    // [key: string]: any
}

// Used to define marks when creating a schema.
interface MarkSpec {
    // The attributes that marks of this type get.
    val attrs: Map<String, AttributeSpec>?

    // Whether this mark should be active when the cursor is positioned at its end (or at its start
    // when that is also the start of the parent node). Defaults to true.
    val inclusive: Boolean?

    // Determines which other marks this mark can coexist with. Should be a space-separated strings
    // naming other marks or groups of marks. When a mark is [added](#model.Mark.addToSet) to a set,
    // all marks that it excludes are removed in the process. If the set contains any mark that
    // excludes the new mark but is not, itself, excluded by the new mark, the mark can not be added
    // an the set. You can use the value `"_"` to indicate that the mark excludes all marks in the
    // schema.
    //
    // Defaults to only being exclusive with marks of the same type. You can set it to an empty
    // string (or any string not containing the mark's own name) to allow multiple marks of a given
    // type to coexist (as long as they have different attributes).
    val excludes: String?

    // The group or space-separated groups to which this mark belongs.
    val group: String?

    // Determines whether marks of this type can span multiple adjacent nodes when serialized to
    // DOM/HTML. Defaults to true.
    val spanning: Boolean?

    // Defines the default way marks of this type should be serialized to DOM/HTML. When the
    // resulting spec contains a hole, that is where the marked content is placed. Otherwise, it is
    // appended to the top node.
    val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)?

    // Associates DOM parser information with this mark (see the corresponding
    // [node spec field](#model.NodeSpec.parseDOM)). The `mark` field in the rules is implied.
    val parseDOM: List<ParseRule>?

    // Mark specs can include additional properties that can be
    // inspected through [`MarkType.spec`](#model.MarkType.spec) when
    // working with the mark.
    // [key: string]: any
}

// Used to [define](#model.NodeSpec.attrs) attributes on nodes or marks.
interface AttributeSpec {
    // The default value for this attribute, to use when no explicit value is provided. Attributes
    // that have no default must be provided whenever a node or mark of a type that has them is
    // created.
    val default: Any?
    val hasDefault: Boolean

    // A function or type name used to validate values of this
    // attribute. This will be used when deserializing the attribute
    // from JSON, and when running [`Node.check`](#model.Node.check).
    // When a function, it should raise an exception if the value isn't
    // of the expected type or shape. When a string, it should be a
    // `|`-separated string of primitive types (`"number"`, `"string"`,
    // `"boolean"`, `"null"`, and `"undefined"`), and the library will
    // raise an error when the value is not one of those types.
    val validateString: String?
    val validateFunction: ((value: Any?) -> Unit)?
}

// A document schema. Holds [node](#model.NodeType) and [mark type](#model.MarkType) objects for the
// nodes and marks that may occur in conforming documents, and provides functionality for creating
// and deserializing such documents.
//
// When given, the type parameters provide the names of the nodes and marks in this schema.
class Schema {
    // The [spec](#model.SchemaSpec) on which the schema is based, with the added guarantee that its
    // `nodes` and `marks` properties are [`OrderedMap`](https://github.com/marijnh/orderedmap)
    // instances (not raw objects).
    val spec: SchemaSpec

    // An object mapping the schema's node names to node type objects.
    val nodes: Map<String, NodeType> // {readonly [name in Nodes]: NodeType} & {readonly [key: string]: NodeType}

    // A map from mark names to mark type objects.
    val marks: Map<String, MarkType>

    // The [linebreak
    // replacement](#model.NodeSpec.linebreakReplacement) node defined
    // in this schema, if any.
    var linebreakReplacement: NodeType? = null

    /**
     * From some testing on mix-contents.json (will differ based on document etc.);
     * Initial Render: 3:1 iterating (reads) vs writing
     * Selection: ~10:1
     * Typing: ~5:1
     */
    val resolveCache = ConcurrentMutableMap<NodeId, ResolveCache>()

    // Construct a schema from a schema [specification](#model.SchemaSpec).
    constructor(spec: SchemaSpec) {
        this.spec = spec.copy(nodes = spec.nodes.toMap(), marks = spec.marks.toMap())
        this.nodes = NodeType.compile(this.spec.nodes, this)
        this.marks = MarkType.compile(this.spec.marks, this)

        val contentExprCache = mutableMapOf<String, ContentMatch>()
        for (prop in this.nodes.keys) {
            if (prop in this.marks) {
                throw RangeError("$prop can not be both a node and a mark")
            }
            val type = this.nodes[prop]!!
            val contentExpr = type.spec.content ?: ""
            val markExpr = type.spec.marks
            type.contentMatch = contentExprCache.getOrPut(contentExpr) {
                ContentMatch.parse(contentExpr, this.nodes)
            }
            type.inlineContent = type.contentMatch.inlineContent
            if (type.spec.linebreakReplacement == true) {
                if (linebreakReplacement != null) throw RangeError("Multiple linebreak nodes defined")
                if (!type.isInline || !type.isLeaf) {
                    throw RangeError("Linebreak replacement nodes must be inline leaf nodes")
                }
                linebreakReplacement = type
            }
            type.markSet = when {
                markExpr == "_" -> null
                !markExpr.isNullOrEmpty() -> gatherMarks(this, markExpr.split(" "))
                markExpr == "" || !type.inlineContent -> emptyList()
                else -> null
            }
        }
        this.marks.forEach { (prop, type) ->
            val excl = type.spec.excludes
            type.excluded = if (excl == null) {
                listOf(type)
            } else if (excl == "") {
                emptyList()
            } else {
                gatherMarks(this, excl.split(" "))
            }
        }

//        this.nodeFromJSON = this.nodeFromJSON.bind(this)
//        this.markFromJSON = this.markFromJSON.bind(this)
        this.topNodeType = this.nodes[this.spec.topNode ?: "doc"]!!
//        this.cached.wrappings = Object.create(null)
    }

    // The type of the [default top node](#model.SchemaSpec.topNode) for this schema.
    val topNodeType: NodeType

    // An object for storing whatever values modules may want to compute and cache per schema. (If
    // you want to store something in it, try to use property names unlikely to clash.)
    val cached = mutableMapOf<String, Any>()

    // Create a node in this schema. The `type` may be a string or a `NodeType` instance. Attributes
    // will be extended with defaults, `content` may be a `Fragment`, `null`, a `Node`, or an array
    // of nodes.
    fun node(type: NodeType, attrs: Attrs? = null, content: Fragment?, marks: List<Mark>?): Node {
        if (type.schema != this) {
            throw RangeError("Node type from different schema used (${type.name})")
        }

        return type.createChecked(attrs, content, marks)
    }

    fun node(type: NodeType, attrs: Attrs? = null, content: Node?, marks: List<Mark>?): Node {
        if (type.schema != this) {
            throw RangeError("Node type from different schema used (${type.name})")
        }

        return type.createChecked(attrs, content, marks)
    }

    fun node(type: NodeType, attrs: Attrs? = null, content: List<Node>?, marks: List<Mark>?): Node {
        if (type.schema != this) {
            throw RangeError("Node type from different schema used (${type.name})")
        }

        return type.create(attrs, content, marks)
    }

    fun node(type: String) = node(nodeType(type), null, null as Fragment?, null)

    fun node(type: String, attrs: Attrs? = null) = node(nodeType(type), attrs, null as Fragment?, null)

    fun node(type: String, attrs: Attrs? = null, content: Fragment?, marks: List<Mark>? = null) =
        node(nodeType(type), attrs, content, marks)

    fun node(type: String, attrs: Attrs? = null, content: Node?, marks: List<Mark>? = null) =
        node(nodeType(type), attrs, content, marks)

    fun node(type: String, attrs: Attrs? = null, content: List<Node>?, marks: List<Mark>? = null) =
        node(nodeType(type), attrs, content, marks)

    // Create a text node in the schema. Empty text nodes are not allowed.
    fun text(text: String, marks: List<Mark>? = null): Node {
        val type = this.nodes["text"]!!
        return TextNode(type, type.defaultAttrs, text, Mark.setFrom(marks))
    }

    // Create a mark with the given type and attributes.
    fun mark(type: String, attrs: Attrs? = null): Mark {
        return mark(marks[type]!!, attrs)
    }

    // Create a mark with the given type and attributes.
    fun mark(type: MarkType, attrs: Attrs? = null): Mark {
        return type.create(attrs)
    }

    // Deserialize a node from its JSON representation. This method is bound.
    fun nodeFromJSON(json: JsonObject?, withId: Boolean = false, check: Boolean = false): Node {
        return Node.fromJSON(this, json, withId, check)
    }

    // Deserialize a mark from its JSON representation. This method is bound.
    fun markFromJSON(json: JsonObject?, withId: Boolean = false, check: Boolean = false): Mark {
        return Mark.fromJSON(this, json, withId, check)
    }

    fun nodeType(name: String): NodeType {
        return this.nodes[name] ?: throw RangeError("Unknown node type: $name")
    }
}

@Suppress("NestedBlockDepth")
fun gatherMarks(schema: Schema, marks: List<String>): List<MarkType> {
    val found = mutableListOf<MarkType>()
    for (name in marks) {
        val mark = schema.marks[name]
        if (mark != null) {
            found.add(mark)
        } else {
            schema.marks.forEach { (prop, mark) ->
                if (name == "_" || (mark.spec.group?.let { it.split(" ").indexOf(name) > -1 } == true)) {
                    found.add(mark)
                }
            }
        }
    }
    return found
}

class SyntaxError(message: String) : Exception(message)
