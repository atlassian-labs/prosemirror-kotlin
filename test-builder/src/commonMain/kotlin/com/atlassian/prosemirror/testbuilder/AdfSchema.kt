package com.atlassian.prosemirror.testbuilder

import com.atlassian.prosemirror.model.AttributeSpec
import com.atlassian.prosemirror.model.DOMOutputSpec
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkSpec
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeSpec
import com.atlassian.prosemirror.model.ParseRule
import com.atlassian.prosemirror.model.TagParseRule
import com.atlassian.prosemirror.model.Whitespace

data class NodeSpecImpl(
    override val content: String? = null,
    override val marks: String? = null,
    override val group: String? = null,
    override val inline: Boolean = false,
    override val atom: Boolean = false,
    override val attrs: Map<String, AttributeSpec>? = null,
    override val selectable: Boolean = true,
    override val draggable: Boolean = false,
    override val code: Boolean = false,
    override val whitespace: Whitespace? = null,
    override val definingAsContext: Boolean? = null,
    override val definingForContent: Boolean? = null,
    override val defining: Boolean? = null,
    override val isolating: Boolean? = null,
    override val toDebugString: ((node: Node) -> String)? = null,
    override val leafText: ((node: Node) -> String)? = null,
    override val toDOM: ((node: Node) -> DOMOutputSpec)? = null,
    override val parseDOM: List<TagParseRule>? = null,
    override val autoFocusable: Boolean? = null,
    override val linebreakReplacement: Boolean? = null
) : NodeSpec

data class MarkSpecImpl(
    override val attrs: Map<String, AttributeSpec>? = null,
    override val inclusive: Boolean? = null,
    override val excludes: String? = null,
    override val group: String? = null,
    override val spanning: Boolean? = null,
    override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = null,
    override val parseDOM: List<ParseRule>? = null
) : MarkSpec

data class AttributeSpecImpl(
    override val default: Any?,
    override val hasDefault: Boolean,
    override val validateString: String? = null,
    override val validateFunction: ((value: Any?) -> Unit)? = null
) : AttributeSpec {
    constructor(default: Any?, validateString: String? = null) : this(default, true, validateString)
    constructor() : this(null, false)
}
