@file:Suppress("MagicNumber")

package com.atlassian.prosemirror.testbuilder

import com.atlassian.prosemirror.model.AttributeSpec
import com.atlassian.prosemirror.model.DOMOutputSpec
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkSpec
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeSpec
import com.atlassian.prosemirror.model.ParseRuleImpl
import com.atlassian.prosemirror.model.ParseRuleMatch
import com.atlassian.prosemirror.model.PreserveWhitespace
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec
import com.fleeksoft.ksoup.nodes.Element

val pDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("p", 0))
val blockquoteDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("blockquote", 0))
val hrDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("hr"))
val preDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(
    listOf("pre", DOMOutputSpec.ArrayDOMOutputSpec(listOf("code", 0)))
)
val brDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("br"))

val emDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("em", 0))
val strongDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("strong", 0))
val codeDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("code", 0))
val strikeDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("strike", 0))
val underlineDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("underline", 0))

// [Specs](#model.NodeSpec) for the nodes defined in this schema.
val nodes = mapOf<String, NodeSpec>(
    // NodeSpec The top level document node.
    "doc" to NodeSpecImpl(
        content = "block+"
    ),

    // A plain paragraph textblock. Represented in the DOM as a `<p>` element.
    "paragraph" to NodeSpecImpl(
        content = "inline*",
        group = "block",
        parseDOM = listOf(ParseRuleImpl(tag = "p")),
        toDOM = { _ -> pDOM }
    ),

    // A blockquote (`<blockquote>`) wrapping one or more blocks.
    "blockquote" to NodeSpecImpl(
        content = "block+",
        group = "block",
        defining = true,
        parseDOM = listOf(ParseRuleImpl(tag = "blockquote")),
        toDOM = { _ -> blockquoteDOM }
    ),

    // A horizontal rule (`<hr>`).
    "horizontal_rule" to NodeSpecImpl(
        group = "block",
        parseDOM = listOf(ParseRuleImpl(tag = "hr")),
        toDOM = { _ -> hrDOM }
    ),

    // A heading textblock, with a `level` attribute that should hold the number 1 to 6. Parsed and
    // serialized as `<h1>` to `<h6>` elements.
    "heading" to NodeSpecImpl(
        attrs = mutableMapOf("level" to AttributeSpecImpl(default = 1)),
        content = "inline*",
        group = "block",
        defining = true,
        parseDOM = listOf(
            ParseRuleImpl(tag = "h1", attrs = mapOf("level" to 1)),
            ParseRuleImpl(tag = "h2", attrs = mapOf("level" to 2)),
            ParseRuleImpl(tag = "h3", attrs = mapOf("level" to 3)),
            ParseRuleImpl(tag = "h4", attrs = mapOf("level" to 4)),
            ParseRuleImpl(tag = "h5", attrs = mapOf("level" to 5)),
            ParseRuleImpl(tag = "h6", attrs = mapOf("level" to 6))
        ),
        toDOM = { node: Node ->
            DOMOutputSpec.ArrayDOMOutputSpec(listOf("h" + node.attrs["level"], 0))
        }
    ),

    // A code listing. Disallows marks or non-text inline nodes by default. Represented as a `<pre>`
    // element with a `<code>` element inside of it.
    "code_block" to NodeSpecImpl(
        content = "text*",
        marks = "",
        group = "block",
        code = true,
        defining = true,
        parseDOM = listOf(ParseRuleImpl(tag = "pre", preserveWhitespace = PreserveWhitespace.FULL)),
        toDOM = { _ -> preDOM }
    ),

    // The text node.
    "text" to NodeSpecImpl(
        group = "inline"
    ),

    // An inline image (`<img>`) node. Supports `src`, `alt`, and `href` attributes. The latter two
    // default to the empty string.
    "image" to NodeSpecImpl(
        inline = true,
        attrs = mutableMapOf(
            "src" to AttributeSpecImpl(),
            "alt" to AttributeSpecImpl(default = null),
            "title" to AttributeSpecImpl(default = null)
        ),
        group = "inline",
        draggable = true,
        parseDOM = listOf(
            ParseRuleImpl(tag = "img[src]", getNodeAttrs = { dom ->
                ParseRuleMatch(
                    mapOf(
                        "src" to dom.attribute("src")?.value,
                        "title" to dom.attribute("title")?.value,
                        "alt" to dom.attribute("alt")?.value
                    ),
                    matches = true
                )
            })
        ),
        toDOM = { node ->
            val src = node.attrs["src"]
            val alt = node.attrs["alt"]
            val title = node.attrs["title"]
            DOMOutputSpec.ArrayDOMOutputSpec(
                listOf(
                    "img",
                    mapOf(
                        "src" to src,
                        "title" to title,
                        "alt" to alt
                    )
                )
            )
        }
    ),

    // A hard line break, represented in the DOM as `<br>`.
    "hard_break" to NodeSpecImpl(
        inline = true,
        group = "inline",
        selectable = false,
        leafText = { "\n" },
        parseDOM = listOf(ParseRuleImpl(tag = "br")),
        toDOM = { _ -> brDOM }
    )
)

// [Specs](#model.MarkSpec) for the marks in the schema.
val marks = mapOf<String, MarkSpec>(
    // A link. Has `href` and `title` attributes. `title` defaults to the empty string. Rendered and
    // parsed as an `<a>` element.
    "link" to MarkSpecImpl(
        attrs = mutableMapOf<String, AttributeSpec>(
            "href" to AttributeSpecImpl(),
            "title" to AttributeSpecImpl(default = null)
        ),
        inclusive = false,
        parseDOM = listOf(
            ParseRuleImpl(tag = "a[href]", getNodeAttrs = { dom ->
                ParseRuleMatch(
                    mapOf(
                        "href" to dom.attribute("href")?.value,
                        "title" to dom.attribute("title")?.value
                    )
                )
            })
        ),
        toDOM = { mark: Mark, inline: Boolean ->
            DOMOutputSpec.ArrayDOMOutputSpec(
                listOf(
                    "a",
                    mapOf(
                        "href" to mark.attrs["href"],
                        "title" to mark.attrs["title"]
                    ),
                    0
                )
            )
        }
    ),

    // An emphasis mark. Rendered as an `<em>` element. Has parse rules that also match `<i>` and
    // `font-style: italic`.
    "em" to MarkSpecImpl(
        parseDOM = listOf(
            ParseRuleImpl(tag = "i"),
            ParseRuleImpl(tag = "em"),
            ParseRuleImpl(style = "font-style=italic")
        ),
        toDOM = { _, _ -> emDOM }
    ),

    // A strong mark. Rendered as `<strong>`, parse rules also match `<b>` and `font-weight: bold`.
    "strong" to MarkSpecImpl(
        parseDOM = listOf(
            ParseRuleImpl(tag = "strong"),
            // This works around a Google Docs misbehavior where
            // pasted content will be inexplicably wrapped in `<b>`
            // tags with a font-weight normal.
            ParseRuleImpl(tag = "b", getNodeAttrs = { node ->
                ParseRuleMatch(null, node.styles()["font-weight"] != "normal")
            }),
            ParseRuleImpl(style = "font-weight", getStyleAttrs = { value ->
                val regex = "^bold(er)?|[5-9]\\d{2,}".toRegex()
                ParseRuleMatch(null, regex.matches(value))
            })
        ),
        toDOM = { _, _ -> strongDOM }
    ),

    // Code font mark. Represented as a `<code>` element.
    "code" to MarkSpecImpl(
        parseDOM = listOf(ParseRuleImpl(tag = "code")),
        toDOM = { _, _ -> codeDOM }
    ),

    "strike" to MarkSpecImpl(
        parseDOM = listOf(ParseRuleImpl(tag = "strike")),
        toDOM = { _, _ -> strikeDOM }
    ),
    "underline" to MarkSpecImpl(
        parseDOM = listOf(ParseRuleImpl(tag = "underline")),
        toDOM = { _, _ -> underlineDOM }
    )
)

// This schema roughly corresponds to the document schema used by
// [CommonMark](http://commonmark.org/), minus the list elements, which are defined in the
// [`prosemirror-schema-list`](#schema-list) module.
//
// To reuse elements from this schema, extend or read from its `spec.nodes` and `spec.marks`
// [properties](#model.Schema.spec).
val schemaBasic = Schema(SchemaSpec(nodes = nodes, marks = marks))

fun Element.styles(): Map<String, String> {
    val style = attribute("style")?.value ?: return emptyMap()
    return style.split(";").associate {
        val (key, value) = it.split(":")
        key.trim() to value.trim()
    }
}
