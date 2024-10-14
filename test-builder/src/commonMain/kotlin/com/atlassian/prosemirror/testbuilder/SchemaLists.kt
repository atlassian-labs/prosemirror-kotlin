@file:Suppress("MaxLineLength")

package com.atlassian.prosemirror.testbuilder

import com.atlassian.prosemirror.model.DOMOutputSpec
import com.atlassian.prosemirror.model.NodeSpec
import com.atlassian.prosemirror.model.ParseRuleMatch
import com.atlassian.prosemirror.model.TagParseRuleImpl

val olDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("ol", 0))
val ulDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("ul", 0))
val liDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("li", 0))

// An ordered list [node spec](#model.NodeSpec). Has a single attribute, `order`, which determines
// the number at which the list starts counting, and defaults to 1. Represented as an `<ol>`
// element.
val orderedList = NodeSpecImpl(
    attrs = mapOf("order" to AttributeSpecImpl(default = 1, validateString = "Int|null")),
    parseDOM = listOf(
        TagParseRuleImpl(tag = "ol", getNodeAttrs = { dom ->
            val start = if (dom.hasAttr("start")) {
                dom.attr("start").toInt()
            } else {
                1
            }
            ParseRuleMatch(mapOf("order" to start))
        })
    ),
    toDOM = { node ->
        if (node.attrs["order"] == 1) {
            olDOM
        } else {
            DOMOutputSpec.ArrayDOMOutputSpec(listOf("ol", mapOf("start" to node.attrs["order"]), 0))
        }
    }
)

// A bullet list node spec, represented in the DOM as `<ul>`.
val bulletList = NodeSpecImpl(
    parseDOM = listOf(TagParseRuleImpl(tag = "ul")),
    toDOM = { _ -> ulDOM }
)

// A list item (`<li>`) spec.
val listItem = NodeSpecImpl(
    parseDOM = listOf(TagParseRuleImpl(tag = "li")),
    toDOM = { _ -> liDOM },
    defining = true
)

@Suppress("ComplexMethod")
fun add(obj: NodeSpec, props: NodeSpec): NodeSpec {
    return NodeSpecImpl(
        content = props.content ?: obj.content,
        marks = props.marks ?: obj.marks,
        group = props.group ?: obj.group,
        inline = props.inline ?: obj.inline,
        atom = props.atom ?: obj.atom,
        attrs = props.attrs ?: obj.attrs,
        selectable = props.selectable ?: obj.selectable,
        draggable = props.draggable ?: obj.draggable,
        code = props.code ?: obj.code,
        whitespace = props.whitespace ?: obj.whitespace,
        definingAsContext = props.definingAsContext ?: obj.definingAsContext,
        definingForContent = props.definingForContent ?: obj.definingForContent,
        defining = props.defining ?: obj.defining,
        isolating = props.isolating ?: obj.isolating,
        toDebugString = props.toDebugString ?: obj.toDebugString,
        leafText = props.leafText ?: obj.leafText,
        toDOM = props.toDOM ?: obj.toDOM,
        parseDOM = props.parseDOM ?: obj.parseDOM
    )
}

// Convenience function for adding list-related node types to a map specifying the nodes for a
//  schema. Adds [`orderedList`](#schema-list.orderedList) as `"ordered_list"`,
//  [`bulletList`](#schema-list.bulletList) as `"bullet_list"`, and
//  [`listItem`](#schema-list.listItem) as `"list_item"`.
//
//  `itemContent` determines the content expression for the list items. If you want the commands
//  defined in this module to apply to your list structure, it should have a shape like `"paragraph
//  block*"` or `"paragraph (ordered_list | bullet_list)*"`. `listGroup` can be given to assign a
//  group name to the list node types, for example `"block"`.
fun addListNodes(nodes: Map<String, NodeSpec>, itemContent: String, listGroup: String?): Map<String, NodeSpec> {
    return buildMap {
        putAll(nodes)
        put("ordered_list", add(orderedList, NodeSpecImpl(content = "list_item+", group = listGroup)))
        put("bullet_list", add(bulletList, NodeSpecImpl(content = "list_item+", group = listGroup)))
        put("list_item", add(listItem, NodeSpecImpl(content = itemContent)))
    }
}
