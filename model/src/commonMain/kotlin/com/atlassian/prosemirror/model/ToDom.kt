package com.atlassian.prosemirror.model

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node as DOMNode
import com.atlassian.prosemirror.model.util.mutableWeakMapOf
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.parser.ParseSettings
import com.fleeksoft.ksoup.parser.Tag

// A description of a DOM structure. Can be either a string, which is
// interpreted as a text node, a DOM node, which is interpreted as
// itself, a `{dom, contentDOM}` object, or an array.
//
// An array describes a DOM element. The first value in the array
// should be a string—the name of the DOM element, optionally prefixed
// by a namespace URL and a space. If the second element is plain
// object, it is interpreted as a set of attributes for the element.
// Any elements after that (including the 2nd if it's not an attribute
// object) are interpreted as children of the DOM elements, and must
// either be valid `DOMOutputSpec` values, or the number zero.
//
// The number zero (pronounced “hole”) is used to indicate the place
// where a node's child nodes should be inserted. If it occurs in an
// output spec, it should be the only child element in its parent
// node.
// export type DOMOutputSpec = string | DOMNode | {dom: DOMNode, contentDOM?: HTMLElement} | [string, ...any]
sealed interface DOMOutputSpec {
    data class TextNodeDOMOutputSpec(val content: String) : DOMOutputSpec
    data class DomNodeDOMOutputSpec(val domNode: DOMNode) : DOMOutputSpec
    data class ComplexNodeDOMOutputSpec(val domNode: DOMNode, val contentDOM: Element? = null) : DOMOutputSpec
    data class ArrayDOMOutputSpec(val content: List<Any>) : DOMOutputSpec
}

// A DOM serializer knows how to convert ProseMirror nodes and
// marks of various types to DOM nodes.

// Create a serializer. `nodes` should map node names to functions
// that take a node and return a description of the corresponding
// DOM. `marks` does the same for mark names, but also gets an
// argument that tells it whether the mark's content is block or
// inline content (for typical use, it'll always be inline). A mark
// serializer may be `null` to indicate that marks of that type
// should not be serialized.
open class DOMSerializer(
    // The node serialization functions.
    val nodes: Map<String, ((node: Node) -> DOMOutputSpec)>,
    // The mark serialization functions.
    val marks: Map<String, ((mark: Mark, inline: Boolean) -> DOMOutputSpec)>
) {
    fun serializeFragmentToHtml(fragment: Fragment, document: Document? = null): String {
        val doc = document ?: doc()
        serializeFragment(fragment, doc)
        doc.outputSettings().prettyPrint(false)
        return doc.body().html()
    }

    // Serialize the content of this fragment to a DOM fragment. When
    // not in the browser, the `document` option, containing a DOM
    // document, should be passed so that the serializer can create
    // nodes.
//     fun serializeFragment(fragment: Fragment, options: {document?: Document} = {},
    //     target?: HTMLElement | DocumentFragment) {
    @Suppress("ComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    open fun serializeFragment(fragment: Fragment, document: Document? = null, targetNode: Element? = null): DOMNode {
        val target: Element = targetNode ?: doc(document).body()
        var top = target
        val active = mutableListOf<Pair<Mark, Element>>() // : [Mark, HTMLElement | DocumentFragment][] = []
        fragment.forEach { node, _, _ ->
            if (active.size > 0 || node.marks.size > 0) {
                var keep = 0
                var rendered = 0
                while (keep < active.size && rendered < node.marks.size) {
                    val next = node.marks[rendered]
                    if (this.marks[next.type.name] == null) {
                        rendered++
                        continue
                    }
                    if (next != active[keep].first || next.type.spec.spanning == false) break
                    keep++
                    rendered++
                }
                while (keep < active.size) top = active.removeLast().second
                while (rendered < node.marks.size) {
                    val add = node.marks[rendered++]
                    val markDOM = this.serializeMark(add, node.isInline, document)
                    if (markDOM != null) {
                        active.add(add to top)
                        top.appendChild(markDOM.domNode)
                        top = markDOM.contentDOM ?: markDOM.domNode as Element
                    }
                }
            }
            top.appendChild(this.serializeNodeInner(node, document))
        }

        return target
    }

    internal fun serializeNodeInner(node: Node, document: Document?): DOMNode {
        val (dom, contentDOM) = renderSpec(
            doc(document),
            nodes[node.type.name]?.invoke(node) ?: DOMOutputSpec.TextNodeDOMOutputSpec(""),
            null,
            node.attrs
        )
        if (contentDOM != null) {
            if (node.isLeaf) {
                throw RangeError("Content hole not allowed in a leaf node spec")
            }
            this.serializeFragment(node.content, document, contentDOM)
        }
        return dom
    }

    // Serialize this node to a DOM node. This can be useful when you
    // need to serialize a part of a document, as opposed to the whole
    // document. To serialize a whole document, use
    // [`serializeFragment`](#model.DOMSerializer.serializeFragment) on
    // its [content](#model.Node.content).
    internal fun serializeMark(
        mark: Mark,
        inline: Boolean,
        document: Document?
    ): DOMOutputSpec.ComplexNodeDOMOutputSpec? {
        val toDOM = this.marks[mark.type.name] ?: return null
        return renderSpec(doc(document), toDOM(mark, inline), null, mark.attrs)
    }

    companion object {
        // Build a serializer using the [`toDOM`](#model.NodeSpec.toDOM)
        // properties in a schema's node and mark specs.
        fun fromSchema(schema: Schema): DOMSerializer {
            return schema.cached.getOrPut("domSerializer") {
                DOMSerializer(
                    this.nodesFromSchema(schema),
                    this.marksFromSchema(schema)
                )
            } as DOMSerializer
        }

        // Gather the serializers in a schema's node specs into an object.
        // This can be useful as a base to build a custom serializer from.
        fun nodesFromSchema(schema: Schema): Map<String, (node: Node) -> DOMOutputSpec> {
            val result = gatherNodesToDOM(schema.nodes).toMutableMap()
            if (result["text"] == null) {
                result["text"] = { node -> DOMOutputSpec.TextNodeDOMOutputSpec(node.text ?: "") }
            }
            return result
        }

        // Gather the serializers in a schema's mark specs into an object.
        fun marksFromSchema(schema: Schema): Map<String, (mark: Mark, isInline: Boolean) -> DOMOutputSpec> {
            return gatherMarksToDOM(schema.marks)
        }
    }
}

fun gatherNodesToDOM(obj: Map<String, NodeType>): Map<String, (node: Node) -> DOMOutputSpec> {
    return buildMap {
        obj.forEach { (key, value) ->
            value.spec.toDOM?.let {
                put(key, it)
            }
        }
    }
}

fun gatherMarksToDOM(obj: Map<String, MarkType>): Map<String, (mark: Mark, isInline: Boolean) -> DOMOutputSpec> {
    return buildMap {
        obj.forEach { (key, value) ->
            value.spec.toDOM?.let {
                put(key, it)
            }
        }
    }
}

fun doc(document: Document? = null) = document ?: Document("http://atlassian.net")

val suspiciousAttributeCache = mutableWeakMapOf<Any, List<Any>?>()

fun suspiciousAttributes(attrs: Map<String, Any?>): List<Any>? {
    return suspiciousAttributeCache.getOrPut(attrs) {
        suspiciousAttributesInner(attrs)
    }
}

fun suspiciousAttributesInner(attrs: Map<String, Any?>): List<Any>? {
    var result: MutableList<Any>? = null
    fun scan(value: Any) {
        if (value is List<*>) {
            if (value.isNotEmpty() && value[0] is String) {
                if (result == null) {
                    result = mutableListOf()
                }
                result?.add(value)
            } else {
                for (i in value.indices) {
                    scan(value[i]!!)
                }
            }
        } else if (value is Map<*, *>) {
            for (v in value.values) {
                v?.let { scan(it) }
            }
        }
    }
    scan(attrs)
    return result
}

fun renderSpec(
    doc: Document,
    structure: DOMOutputSpec,
    xmlNamespace: String? = null,
    blockArraysIn: Map<String, Any?>? = null
): DOMOutputSpec.ComplexNodeDOMOutputSpec {
    var xmlNS = xmlNamespace
    if (structure is DOMOutputSpec.TextNodeDOMOutputSpec) {
        return DOMOutputSpec.ComplexNodeDOMOutputSpec(domNode = TextNode(structure.content))
    }
    if (structure is DOMOutputSpec.DomNodeDOMOutputSpec) {
        return DOMOutputSpec.ComplexNodeDOMOutputSpec(domNode = structure.domNode)
    }
    if (structure is DOMOutputSpec.ComplexNodeDOMOutputSpec) {
        return structure
    }
    structure as DOMOutputSpec.ArrayDOMOutputSpec
    var tagName = structure.content.first() as? String ?: throw RangeError("Invalid array passed to renderSpec")
    val suspicious = blockArraysIn?.let { suspiciousAttributes(it) }
    if (blockArraysIn != null && suspicious != null && suspicious.contains(structure)) {
        throw RangeError("Using an array from an attribute object as a DOM spec. This may be an attempted cross site scripting attack.")
    }
    val space = tagName.indexOf(" ")
    if (space > 0) {
        xmlNS = tagName.substring(0, space)
        tagName = tagName.substring(space + 1)
    }
    var contentDOM: Element? = null
    val dom = if (xmlNS != null) doc.createElementNS(xmlNS, tagName) else doc.createElement(tagName)
    val attrs = structure.content.getOrNull(1)
    var start = 1
    if (attrs is Map<*, *>) {
        start = 2
        for (name in attrs.keys) {
            if (attrs[name] != null) {
                val name = name.toString()
                val space = name.indexOf(" ")
                if (space > 0) {
                    dom.attr(
//                        name.substring(0, space),
                        name.substring(space + 1),
                        attrs[name].toString()
                    )
                } else {
                    dom.attr(name, attrs[name].toString())
                }
            }
        }
    }
    for (i in start until structure.content.size) {
        val child = structure.content[i] as? DOMOutputSpec ?: 0
        if (child == 0) {
            if (i < structure.content.size - 1 || i > start) {
                throw RangeError("Content hole must be the only child of its parent node")
            }
            return DOMOutputSpec.ComplexNodeDOMOutputSpec(dom, dom)
        } else {
            val spec = renderSpec(doc, child as DOMOutputSpec, xmlNS, blockArraysIn)
            val inner = spec.domNode
            val innerContent = spec.contentDOM
            dom.appendChild(inner)
            if (innerContent != null) {
                if (contentDOM != null) {
                    throw RangeError("Multiple content holes")
                }
                contentDOM = innerContent
            }
        }
    }
    return DOMOutputSpec.ComplexNodeDOMOutputSpec(dom, contentDOM)
}

fun Document.createElementNS(namespace: String, tagName: String): Element {
    return Element(
        Tag.valueOf(
            tagName,
            namespace,
            ParseSettings.preserveCase,
        ),
        this.baseUri(),
    )
}
