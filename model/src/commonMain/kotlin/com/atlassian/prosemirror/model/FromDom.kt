package com.atlassian.prosemirror.model

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import java.util.Locale
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.math.max
import com.fleeksoft.ksoup.nodes.Node as DOMNode

// TODO move all regex patterns here to avoid parsing multiple times
object RegexPatterns {
    // val re = /\s*([\w-]+)\s*:\s*([^;]+)/g
    val STYLE_REGEX = "\\s*([\\w-]+)\\s*:\\s*([^;]+)".toRegex()
}

data class ParseOptionPosition(val node: DOMNode, val offset: Int, var pos: Int?)
enum class PreserveWhitespace {
    YES, NO, FULL
}

// These are the options recognized by the
// [`parse`](#model.DOMParser.parse) and
// [`parseSlice`](#model.DOMParser.parseSlice) methods.
interface ParseOptions {
    // By default, whitespace is collapsed as per HTML's rules. Pass
    // `true` to preserve whitespace, but normalize newlines to
    // spaces, and `"full"` to preserve whitespace entirely.
    val preserveWhitespace: PreserveWhitespace?

    // When given, the parser will, beside parsing the content,
    // record the document positions of the given DOM positions. It
    // will do so by writing to the objects, adding a `pos` property
    // that holds the document position. DOM positions that are not
    // in the parsed content will not be written to.
    val findPositions: List<ParseOptionPosition>?

    // The child node index to start parsing from.
    val from: Int?

    // The child node index to stop parsing at.
    val to: Int?

    // By default, the content is parsed into the schema's default
    // [top node type](#model.Schema.topNodeType). You can pass this
    // option to use the type and attributes from a different node
    // as the top container.
    val topNode: Node?

    // Provide the starting content match that content parsed into the
    // top node is matched against.
    val topMatch: ContentMatch?

    // A set of additional nodes to count as
    // [context](#model.ParseRule.context) when parsing, above the
    // given [top node](#model.ParseOptions.topNode).
    val context: ResolvedPos?

    val ruleFromNode: ((node: DOMNode) -> ParseRule?)?

    val topOpen: Boolean?
}

data class ParseOptionsImpl(
    override val preserveWhitespace: PreserveWhitespace? = null,
    override val findPositions: List<ParseOptionPosition>? = null,
    override val from: Int? = null,
    override val to: Int? = null,
    override val topNode: Node? = null,
    override val topMatch: ContentMatch? = null,
    override val context: ResolvedPos? = null,
    override val ruleFromNode: ((node: DOMNode) -> ParseRule?)? = null,
    override val topOpen: Boolean? = null
) : ParseOptions

// A value that describes how to parse a given DOM node or inline
// style as a ProseMirror node or mark.
interface ParseRule {
    // A CSS selector describing the kind of DOM elements to match. A
    // single rule should have _either_ a `tag` or a `style` property.
    val tag: String?

    // The namespace to match. This should be used with `tag`.
    // Nodes are only matched when the namespace matches or this property
    // is null.
    val namespace: String?

    // A CSS property name to match. When given, this rule matches
    // inline styles that list that property. May also have the form
    // `"property=value"`, in which case the rule only matches if the
    // property's value exactly matches the given value. (For more
    // complicated filters, use [`getAttrs`](#model.ParseRule.getAttrs)
    // and return false to indicate that the match failed.) Rules
    // matching styles may only produce [marks](#model.ParseRule.mark),
    // not nodes.
    val style: String?

    // Can be used to change the order in which the parse rules in a
    // schema are tried. Those with higher priority come first. Rules
    // without a priority are counted as having priority 50. This
    // property is only meaningful in a schema—when directly
    // constructing a parser, the order of the rule array is used.
    val priority: Int?

    // By default, when a rule matches an element or style, no further
    // rules get a chance to match it. By setting this to `false`, you
    // indicate that even when this rule matches, other rules that come
    // after it should also run.
    val consuming: Boolean?

    // When given, restricts this rule to only match when the current
    // context—the parent nodes into which the content is being
    // parsed—matches this expression. Should contain one or more node
    // names or node group names followed by single or double slashes.
    // For example `"paragraph/"` means the rule only matches when the
    // parent node is a paragraph, `"blockquote/paragraph/"` restricts
    // it to be in a paragraph that is inside a blockquote, and
    // `"section//"` matches any position inside a section—a double
    // slash matches any sequence of ancestor nodes. To allow multiple
    // different contexts, they can be separated by a pipe (`|`)
    // character, as in `"blockquote/|list_item/"`.
    val context: String?

    // The name of the node type to create when this rule matches. Only
    // valid for rules with a `tag` property, not for style rules. Each
    // rule should have one of a `node`, `mark`, `clearMark`, or
    // `ignore` property (except when it appears in a
    // [node](#model.NodeSpec.parseDOM) or [mark
    // spec](#model.MarkSpec.parseDOM), in which case the `node` or
    // `mark` property will be derived from its position).
    var node: String?

    // The name of the mark type to wrap the matched content in.
    var mark: String?

    // [Style](#model.ParseRule.style) rules can remove marks from the
    // set of active marks.
    var clearMark: ((mark: Mark) -> Boolean)?

    // When true, ignore content that matches this rule.
    val ignore: Boolean?

    // When true, finding an element that matches this rule will close
    // the current node.
    val closeParent: Boolean?

    // When true, ignore the node that matches this rule, but do parse
    // its content.
    val skip: Boolean?

    // Attributes for the node or mark created by this rule. When
    // `getAttrs` is provided, it takes precedence.
    var attrs: Attrs?

    // A function used to compute the attributes for the node or mark
    // created by this rule. Can also be used to describe further
    // conditions the DOM element or style must match. When it returns
    // `false`, the rule won't match. When it returns null or undefined,
    // that is interpreted as an empty/default set of attributes.
    //
    // Called with a DOM Element for `tag` rules, and with a string (the
    // style's value) for `style` rules.
    val getStyleAttrs: ((style: String) -> ParseRuleMatch)?
    val getNodeAttrs: ((node: Element) -> ParseRuleMatch)?

    // For `tag` rules that produce non-leaf nodes or marks, by default
    // the content of the DOM element is parsed as content of the mark
    // or node. If the child nodes are in a descendent node, this may be
    // a CSS selector string that the parser must use to find the actual
    // content element, or a function that returns the actual content
    // element to the parser.
    val contentElement: ContentElement?

    // Can be used to override the content of a matched node. When
    // present, instead of parsing the node's child nodes, the result of
    // this function is used.
    val getContent: ((node: DOMNode, schema: Schema) -> Fragment?)?

    // Controls whether whitespace should be preserved when parsing the
    // content inside the matched element. `false` means whitespace may
    // be collapsed, `true` means that whitespace should be preserved
    // but newlines normalized to spaces, and `"full"` means that
    // newlines should also be preserved.
    val preserveWhitespace: PreserveWhitespace?

    fun copyRule(): ParseRule
}

data class ParseRuleImpl(
    override val tag: String? = null,
    override val namespace: String? = null,
    override val style: String? = null,
    override val priority: Int? = null,
    override val consuming: Boolean? = null,
    override val context: String? = null,
    override var node: String? = null,
    override var mark: String? = null,
    override var clearMark: ((mark: Mark) -> Boolean)? = null,
    override val ignore: Boolean? = null,
    override val closeParent: Boolean? = null,
    override val skip: Boolean? = null,
    override var attrs: Attrs? = null,
    override val getStyleAttrs: ((style: String) -> ParseRuleMatch)? = null,
    override val getNodeAttrs: ((node: Element) -> ParseRuleMatch)? = null,
    override val contentElement: ContentElement? = null,
    override val getContent: ((node: DOMNode, schema: Schema) -> Fragment?)? = null,
    override val preserveWhitespace: PreserveWhitespace? = null
) : ParseRule {
    override fun copyRule(): ParseRule = this.copy()
}

data class ParseRuleMatch(val attrs: Attrs?, val matches: Boolean = true) {
    companion object {
        val FALSE = ParseRuleMatch(null, false)
    }
}

sealed interface ContentElement {
    @JvmInline
    value class StringContentElement(val s: String) : ContentElement

    @JvmInline
    value class ElementContentElement(val element: Element) : ContentElement

    @JvmInline
    value class FunctionContentElement(val func: ((node: Element) -> Element)) : ContentElement
}

// A DOM parser represents a strategy for parsing DOM content into a
// ProseMirror document conforming to a given schema. Its behavior is
// defined by an array of [rules](#model.ParseRule).
class DOMParser(
    // The schema into which the parser parses.
    val schema: Schema,
    // The set of [parse rules](#model.ParseRule) that the parser
    // uses, in order of precedence.
    val rules: List<ParseRule>
) {
    internal val tags = mutableListOf<ParseRule>()
    internal val styles = mutableListOf<ParseRule>()
    internal val normalizeLists: Boolean

    // Create a parser that targets the given schema, using the given
    // parsing rules.
    init {
        rules.forEach { rule ->
            if (rule.tag != null) {
                this.tags.add(rule)
            } else if (rule.style != null) {
                this.styles.add(rule)
            }
        }

        // Only normalize list elements when lists in the schema can't directly contain themselves
        this.normalizeLists = this.tags.firstOrNull { r ->
            val regex = "^(ul|ol)\\b".toRegex()
            if (!regex.containsMatchIn(r.tag!!) || r.node == null) {
                false
            } else {
                val node = schema.nodes[r.node]!!
                node.contentMatch.matchType(node) != null
            }
        } == null
    }

    @Suppress("UnusedPrivateMember")
    fun parseHtml(html: String, options: ParseOptions = ParseOptionsImpl()): Node {
        val derivedDOM = Ksoup.parse(html).body()
        return parse(derivedDOM, options)
    }

    // Parse a document from the content of a DOM node.
    fun parse(dom: DOMNode, options: ParseOptions = ParseOptionsImpl()): Node {
        val context = ParseContext(this, options, false)
        context.addAll(dom, options.from, options.to)
        return context.finish() as Node
    }

    // Parses the content of the given DOM node, like
    // [`parse`](#model.DOMParser.parse), and takes the same set of
    // options. But unlike that method, which produces a whole node,
    // this one returns a slice that is open at the sides, meaning that
    // the schema constraints aren't applied to the start of nodes to
    // the left of the input and the end of nodes at the end.
    fun parseSlice(dom: DOMNode, options: ParseOptions = ParseOptionsImpl()): Slice {
        val context = ParseContext(this, options, true)
        context.addAll(dom, options.from, options.to)
        return Slice.maxOpen(context.finish() as Fragment)
    }

    @Suppress("NestedBlockDepth", "ComplexCondition")
    internal fun matchTag(dom: DOMNode, context: ParseContext, after: ParseRule?): ParseRule? {
        val start = if (after != null) this.tags.indexOf(after) + 1 else 0
        for (i in start until this.tags.size) {
            val rule = this.tags[i]
            if (matches(dom, rule.tag!!) &&
                (rule.namespace == null || dom.baseUri() == rule.namespace) &&
                (rule.context == null || context.matchesContext(rule.context!!))
            ) {
                val getNodeAttrs = rule.getNodeAttrs
                if (getNodeAttrs != null) {
                    val result = getNodeAttrs(dom as Element)
                    if (!result.matches) continue
                    rule.attrs = result.attrs
                }
                return rule
            }
        }
        return null
    }

    @Suppress("ComplexCondition", "LoopWithTooManyJumpStatements")
    internal fun matchStyle(prop: String, value: String, context: ParseContext, after: ParseRule?): ParseRule? {
        val start = if (after != null) {
            this.styles.indexOf(after) + 1
        } else {
            0
        }
        for (i in start until this.styles.size) {
            val rule = this.styles[i]
            val style = rule.style!!
            if (style.indexOf(prop) != 0 ||
                rule.context != null && !context.matchesContext(rule.context!!) ||
                // Test that the style string either precisely matches the prop,
                // or has an '=' sign after the prop, followed by the given
                // value.
                style.length > prop.length &&
                (style[prop.length] != 61.toChar() || style.slice(prop.length + 1 until style.length) != value)
            ) {
                continue
            }
            val getStyleAttrs = rule.getStyleAttrs
            if (getStyleAttrs != null) {
                val result = getStyleAttrs(value)
                if (!result.matches) continue
                rule.attrs = result.attrs
            }
            return rule
        }
        return null
    }

    companion object {

        @Suppress("MagicNumber", "ComplexMethod")
        internal fun schemaRules(schema: Schema): List<ParseRule> {
            val result = mutableListOf<ParseRule>()
            fun insert(rule: ParseRule) {
                val priority = rule.priority ?: 50
                var i = 0
                while (i < result.size) {
                    val next = result[i]
                    val nextPriority = next.priority ?: 50
                    if (nextPriority < priority) break
                    i++
                }
                result.add(i, rule)
            }

            schema.marks.forEach { (key, value) ->
                value.spec.parseDOM?.forEach { rule ->
                    val ruleToInsert = rule.copyRule()
                    insert(rule = ruleToInsert)
                    if (!(rule.mark != null || rule.ignore != null || rule.clearMark != null)) {
                        ruleToInsert.mark = key
                    }
                }
            }
            schema.nodes.forEach { (key, value) ->
                value.spec.parseDOM?.forEach { rule ->
                    val ruleToInsert = rule.copyRule()
                    insert(ruleToInsert)
                    if (!(rule.node != null || rule.ignore != null || rule.mark != null)) {
                        ruleToInsert.node = key
                    }
                }
            }
            return result
        }

        // Construct a DOM parser using the parsing rules listed in a
        // schema's [node specs](#model.NodeSpec.parseDOM), reordered by
        // [priority](#model.ParseRule.priority).
        fun fromSchema(schema: Schema): DOMParser {
            return schema.cached.getOrPut("domParser") {
                DOMParser(schema, schemaRules(schema))
            } as DOMParser
        }
    }
}

val blockTags = setOf(
    "address", "article", "aside", "blockquote", "canvas", "dd", "div", "dl", "fieldset", "figcaption", "figure",
    "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "li", "noscript", "ol", "output",
    "p", "pre", "section", "table", "tfoot", "ul"
)

val ignoreTags = setOf("head", "noscript", "object", "script", "style", "title")

val listTags = setOf("ol", "ul")

// Using a bitfield for node context options
const val OPT_PRESERVE_WS = 1
const val OPT_PRESERVE_WS_FULL = 2
const val OPT_OPEN_LEFT = 4

fun wsOptionsFor(type: NodeType?, preserveWhitespace: PreserveWhitespace?, base: Int): Int {
    if (preserveWhitespace != null) {
        return (if (preserveWhitespace != PreserveWhitespace.NO) OPT_PRESERVE_WS else 0) or
            (if (preserveWhitespace == PreserveWhitespace.FULL) OPT_PRESERVE_WS_FULL else 0)
    }
    return if (type?.whitespace == Whitespace.PRE) {
        OPT_PRESERVE_WS or OPT_PRESERVE_WS_FULL
    } else {
        base and (OPT_OPEN_LEFT.inv())
    }
}

class NodeContext(
    val type: NodeType?,
    val attrs: Attrs?,
    // Marks applied to this node itself
    val marks: List<Mark>,
    // Marks that can't apply here, but will be used in children if possible
    var pendingMarks: List<Mark>,
    val solid: Boolean,
    match: ContentMatch?,
    val options: Int
) {
    var match: ContentMatch? = match ?: if ((options and OPT_OPEN_LEFT) != 0) null else type!!.contentMatch
    val content = mutableListOf<Node>()

    // Marks applied to the node's children
    var activeMarks: List<Mark> = Mark.none

    // Nested Marks with same type
    var stashMarks = mutableListOf<Mark>()

    @Suppress("ReturnCount")
    fun findWrapping(node: Node): List<NodeType>? {
        if (this.match == null) {
            if (this.type == null) return emptyList()
            val fill = this.type.contentMatch.fillBefore(Fragment.from(node))
            if (fill != null) {
                this.match = this.type.contentMatch.matchFragment(fill)
            } else {
                val start = this.type.contentMatch
                val wrap = start.findWrapping(node.type)
                return if (wrap != null) {
                    this.match = start
                    wrap
                } else {
                    null
                }
            }
        }
        return this.match?.findWrapping(node.type)
    }

    @Suppress("NestedBlockDepth")
    fun finish(openEnd: Boolean?): Any { // Node | Fragment
        if ((this.options and OPT_PRESERVE_WS) == 0) { // Strip trailing whitespace
            val last = this.content.lastOrNull()
            val regex = "[ \\t\\r\\n\\u000c]+\$".toRegex()
            if (last != null && last.isText) {
                val m = regex.find(last.text!!)
                if (m != null) {
                    val text = last as TextNode
                    if (last.text.length == m.groupValues.first().length) {
                        this.content.removeLast()
                    } else {
                        this.content[this.content.size - 1] =
                            text.withText(
                                text.text.slice(0 until text.text.length - m.groupValues.first().length)
                            )
                    }
                }
            }
        }
        var content = Fragment.Companion.from(this.content)
        if (openEnd != true) {
            this.match?.let {
                content = content.append(it.fillBefore(Fragment.empty, true)!!)
            }
        }
        return this.type?.create(this.attrs, content, this.marks) ?: content
    }

    fun popFromStashMark(mark: Mark): Mark? {
        var ind = -1
        for (i in this.stashMarks.size - 1 downTo 0) {
            if (mark == this.stashMarks[i]) {
                ind = i
                break
            }
        }
        if (ind >= 0) {
            return this.stashMarks.removeAt(ind)
        }
        return null
    }

    fun applyPending(nextType: NodeType) {
        val pending = this.pendingMarks
        for (i in 0 until pending.size) {
            val mark = pending[i]
            if ((this.type?.allowsMarkType(mark.type) ?: markMayApply(mark.type, nextType)) &&
                !mark.isInSet(this.activeMarks)
            ) {
                this.activeMarks = mark.addToSet(this.activeMarks)
                this.pendingMarks = mark.removeFromSet(this.pendingMarks)
            }
        }
    }

    @Suppress("ReturnCount")
    fun inlineContext(node: DOMNode): Boolean {
        if (this.type != null) return this.type.inlineContent
        if (this.content.isNotEmpty()) return this.content[0].isInline
        val name = node.parentNode()?.nodeName()?.lowercase(Locale.getDefault()) ?: false
        return blockTags.contains(name)
    }
}

class ParseContext(
    // The parser we are using.
    val parser: DOMParser,
    // The options passed to this parse.
    val options: ParseOptions,
    val isOpen: Boolean
) {
    var open = 0
    val find: List<ParseOptionPosition>?
    var needsBlock: Boolean
    var nodes: MutableList<NodeContext>

    val top: NodeContext
        get() = this.nodes[this.open]

    init {
        val topNode = options.topNode
        val topContext: NodeContext
        val topOptions = wsOptionsFor(null, options.preserveWhitespace, 0) or
            (if (isOpen) OPT_OPEN_LEFT else 0)
        if (topNode != null) {
            topContext = NodeContext(
                topNode.type, topNode.attrs, Mark.none, Mark.none, true,
                options.topMatch ?: topNode.type.contentMatch, topOptions
            )
        } else if (isOpen) {
            topContext = NodeContext(null, null, Mark.none, Mark.none, true, null, topOptions)
        } else {
            topContext = NodeContext(parser.schema.topNodeType, null, Mark.none, Mark.none, true, null, topOptions)
        }
        this.nodes = mutableListOf(topContext)
        this.find = options.findPositions
        this.needsBlock = false
    }

    // Add a DOM node to the content. Text is inserted as text node,
    // otherwise, the node is passed to `addElement` or, if it has a
    // `style` attribute, `addElementWithStyles`.
    fun addDOM(dom: DOMNode) {
        if (dom is com.fleeksoft.ksoup.nodes.TextNode) {
            this.addTextNode(dom)
        } else if (dom is Element) {
            this.addElement(dom)
        }
    }

    fun withStyleRules(dom: Element, f: () -> Unit) {
        val style = dom.attribute("style")?.value ?: return f()
        val marks = this.readStyles(parseStyles(style)) ?: return // A style with ignore: true
        val (addMarks, removeMarks) = marks
        val top = this.top

        removeMarks.forEach {
            this.removePendingMark(it, top)
        }
        addMarks.forEach {
            this.addPendingMark(it)
        }
        f()
        addMarks.forEach {
            this.removePendingMark(it, top)
        }
        removeMarks.forEach {
            this.addPendingMark(it)
        }
    }

    @Suppress("NestedBlockDepth", "ComplexCondition")
    fun addTextNode(dom: com.fleeksoft.ksoup.nodes.TextNode) {
        var value = dom.getWholeText()
        val top = this.top
        if (
            (top.options and OPT_PRESERVE_WS_FULL) != 0 ||
            top.inlineContext(dom) ||
            // /[^ \t\r\n\u000c]/.test(value)
            "[^ \\t\\r\\n\\u000c]".toRegex().containsMatchIn(value)
        ) {
            if ((top.options and OPT_PRESERVE_WS) == 0) {
                // value = value.replace(/[ \t\r\n\u000c]+/g, " ")
                value = value.replace("[ \\t\\r\\n\\u000c]+".toRegex(), " ")
                // If this starts with whitespace, and there is no node before it, or
                // a hard break, or a text node that ends with whitespace, strip the
                // leading space.
                // /^[ \t\r\n\u000c]/.test(value)
                if ("^[ \\t\\r\\n\\u000c]".toRegex().containsMatchIn(value) && this.open == this.nodes.size - 1) {
                    val nodeBefore = top.content.lastOrNull()
                    val domNodeBefore = dom.previousSibling()
                    // /[ \t\r\n\u000c]$/.test(nodeBefore.text!)
                    if (nodeBefore == null || domNodeBefore?.nodeName().equals("br", true) ||
                        (nodeBefore.isText && "[ \\t\\r\\n\\u000c]$".toRegex().containsMatchIn(nodeBefore.text!!))
                    ) {
                        value = value.substring(1)
                    }
                }
            } else if ((top.options and OPT_PRESERVE_WS_FULL) == 0) {
                // value = value.replace(/\r?\n|\r/g, " ")
                value = value.replace("\\r?\\n|\\r".toRegex(), " ")
            } else {
                // value = value.replace(/\r\n?/g, "\n")
                value = value.replace("\\r\\n?".toRegex(), "\n")
            }
            if (value.isNotEmpty()) {
                this.insertNode(this.parser.schema.text(value))
            }
            this.findInText(dom)
        } else {
            this.findInside(dom)
        }
    }

    // Try to find a handler for the given tag and use that to parse. If
    // none is found, the element's content nodes are added directly.
    @Suppress("ComplexMethod")
    fun addElement(dom: Element, matchAfter: ParseRule? = null) {
        val name = dom.nodeName().lowercase(Locale.getDefault())
        var ruleID: ParseRule? = null
        if (listTags.contains(name) && this.parser.normalizeLists) normalizeList(dom)
        val ruleFromNode = this.options.ruleFromNode?.invoke(dom)
        val rule = ruleFromNode ?: this.parser.matchTag(dom, this, matchAfter).also { ruleID = it }
        if (rule?.ignore ?: ignoreTags.contains(name)) {
            this.findInside(dom)
            this.ignoreFallback(dom)
        } else if (rule == null || rule.skip == true || rule.closeParent == true) {
            if (rule?.closeParent == true) {
                this.open = max(0, this.open - 1)
            }
            // TODO block below does not make sense since rule.skip is defined as Boolean so it can't have nodeType
            // ever
//             else if (rule && (rule.skip as any).nodeType) {
//                 dom = rule.skip as any as Element
//             }
            var sync = false
            var top = this.top
            val oldNeedsBlock = this.needsBlock
            if (blockTags.contains(name)) {
                if (top.content.size > 0 && top.content[0].isInline && this.open != 0) {
                    this.open--
                    top = this.top
                }
                sync = true
                if (top.type == null) {
                    this.needsBlock = true
                }
            } else if (dom.firstChild() == null) {
                this.leafFallback(dom)
                return
            }
            if (rule?.skip == true) {
                this.addAll(dom)
            } else {
                this.withStyleRules(dom) {
                    addAll(dom)
                }
            }
            if (sync) {
                this.sync(top)
            }
            this.needsBlock = oldNeedsBlock
        } else {
            this.withStyleRules(dom) {
                this.addElementByRule(dom, rule, ruleID?.takeIf { rule.consuming == false })
            }
        }
    }

    // Called for leaf DOM nodes that would otherwise be ignored
    fun leafFallback(dom: DOMNode) {
        if (dom.nodeName().equals("br", true) && this.top.type?.inlineContent == true) {
            this.addTextNode(com.fleeksoft.ksoup.nodes.TextNode("\n"))
        }
    }

    // Called for ignored nodes
    fun ignoreFallback(dom: DOMNode) {
        // Ignored BR nodes should at least create an inline context
        if (dom.nodeName().equals("br", true) && (this.top.type?.inlineContent == false)) {
            this.findPlace(this.parser.schema.text("-"))
        }
    }

    // Run any style parser associated with the node's styles. Either
    // return an array of marks, or null to indicate some of the styles
    // had a rule with `ignore` set.
    @Suppress("LoopWithTooManyJumpStatements", "NestedBlockDepth")
    fun readStyles(styles: List<String>): Pair<List<Mark>, List<Mark>>? {
        var add = Mark.none
        var remove = Mark.none
        style@ for (i in styles.indices step 2) {
            var after: ParseRule? = null
            while (true) {
                val rule = this.parser.matchStyle(styles[i], styles[i + 1], this, after) ?: break
                if (rule.ignore == true) {
                    return null
                }
                if (rule.clearMark != null) {
                    (this.top.pendingMarks + this.top.activeMarks).forEach { m ->
                        if (rule.clearMark!!(m)) remove = m.addToSet(remove)
                    }
                } else {
                    add = this.parser.schema.marks[rule.mark]!!.create(rule.attrs).addToSet(add)
                }
                if (rule.consuming == false) {
                    after = rule
                } else {
                    break
                }
            }
        }
        return add to remove
    }

    // Look up a handler for the given node. If none are found, return
    // false. Otherwise, apply it, use its return value to drive the way
    // the node's content is wrapped, and return true.
    @Suppress("ComplexMethod")
    fun addElementByRule(dom: Element, rule: ParseRule, continueAfter: ParseRule?) {
        var sync = false
        var nodeType: NodeType? = null
        var mark: Mark? = null
        val ruleNode = rule.node
        if (ruleNode != null) {
            nodeType = this.parser.schema.nodeType(ruleNode)
            if (!nodeType.isLeaf) {
                sync = this.enter(nodeType, rule.attrs?.takeIf { it.isNotEmpty() }, rule.preserveWhitespace)
            } else if (!this.insertNode(nodeType.create(rule.attrs))) {
                this.leafFallback(dom)
            }
        } else {
            val markType = this.parser.schema.marks[rule.mark!!]
            mark = markType?.create(rule.attrs)?.also { addPendingMark(it) }
        }
        val startIn = this.top

        if (nodeType?.isLeaf == true) {
            this.findInside(dom)
        } else if (continueAfter != null) {
            this.addElement(dom, continueAfter)
        } else if (rule.getContent != null) {
            this.findInside(dom)
            rule.getContent!!.invoke(dom, this.parser.schema)?.forEach { node, _, _ -> this.insertNode(node) }
        } else {
            var contentDOM = dom
            val contentElement = rule.contentElement
            if (contentElement is ContentElement.StringContentElement) {
                val xPath = XPathFactory.newInstance().newXPath()
                contentDOM = xPath.evaluate(contentElement.s, dom, XPathConstants.NODE) as Element
            } else if (contentElement is ContentElement.FunctionContentElement) {
                contentDOM = contentElement.func(dom)
            } else if (contentElement is ContentElement.ElementContentElement) {
                contentDOM = contentElement.element
            }
            this.findAround(dom, contentDOM, true)
            this.addAll(contentDOM)
        }
        if (sync && this.sync(startIn)) this.open--
        if (mark != null) this.removePendingMark(mark, startIn)
    }

    // Add all child nodes between `startIndex` and `endIndex` (or the
    // whole node, if not given). If `sync` is passed, use it to
    // synchronize after every block element.
    fun addAll(parent: DOMNode, startIndex: Int? = null, endIndex: Int? = null) {
        var index = startIndex ?: 0
        var dom: DOMNode? = if (startIndex != null) parent.childNode(startIndex) else parent.firstChild()
        val end = endIndex?.let { parent.childNode(endIndex) }
        while (dom != end) {
            this.findAtPoint(parent, index)
            this.addDOM(dom!!)
            dom = dom.nextSibling()
            ++index
        }
        this.findAtPoint(parent, index)
    }

    // Try to find a way to fit the given node type into the current
    // context. May add intermediate wrappers and/or leave non-solid
    // nodes that we're in.
    @Suppress("LoopWithTooManyJumpStatements")
    fun findPlace(node: Node): Boolean {
        var route: List<NodeType>? = null
        var sync: NodeContext? = null
        for (depth in this.open downTo 0) {
            val cx = this.nodes[depth]
            val found = cx.findWrapping(node)
            if (found != null && (route == null || route.size > found.size)) {
                route = found
                sync = cx
                if (found.isEmpty()) break
            }
            if (cx.solid) break
        }
        if (route == null) return false
        this.sync(sync!!)
        route.forEach {
            this.enterInner(it, null, false)
        }
        return true
    }

    // Try to insert the given node, adjusting the context when needed.
    fun insertNode(node: Node): Boolean {
        if (node.isInline && this.needsBlock && this.top.type == null) {
            val block = this.textblockFromContext()
            if (block != null) this.enterInner(block)
        }
        if (this.findPlace(node)) {
            this.closeExtra()
            val top = this.top
            top.applyPending(node.type)
            if (top.match != null) top.match = top.match!!.matchType(node.type)
            var marks = top.activeMarks
            for (i in 0 until node.marks.size) {
                if (top.type == null || top.type.allowsMarkType(node.marks[i].type)) {
                    marks = node.marks[i].addToSet(marks)
                }
            }
            top.content.add(node.mark(marks))
            return true
        }
        return false
    }

    // Try to start a node of the given type, adjusting the context when
    // necessary.
    fun enter(type: NodeType, attrs: Attrs?, preserveWS: PreserveWhitespace?): Boolean {
        val ok = this.findPlace(type.create(attrs))
        if (ok) this.enterInner(type, attrs, true, preserveWS)
        return ok
    }

    // Open a node of the given type
    fun enterInner(
        type: NodeType,
        attrs: Attrs? = null,
        solid: Boolean = false,
        preserveWS: PreserveWhitespace? = null
    ) {
        this.closeExtra()
        val top = this.top
        top.applyPending(type)
        top.match = top.match?.matchType(type)
        var options = wsOptionsFor(type, preserveWS, top.options)
        if ((top.options and OPT_OPEN_LEFT) != 0 && top.content.size == 0) {
            options = options or OPT_OPEN_LEFT
        }
        this.nodes.add(NodeContext(type, attrs, top.activeMarks, top.pendingMarks, solid, null, options))
        this.open++
    }

    // Make sure all nodes above this.open are finished and added to
    // their parents
    fun closeExtra(openEnd: Boolean = false) {
        var i = this.nodes.size - 1
        if (i > this.open) {
            while (i > this.open) {
                this.nodes[i - 1].content.add(this.nodes[i].finish(openEnd) as Node)
                i--
            }
            this.nodes = this.nodes.take(this.open + 1).toMutableList()
        }
    }

    fun finish(): Any {
        this.open = 0
        this.closeExtra(this.isOpen)
        return this.nodes[0].finish(this.isOpen || this.options.topOpen == true)
    }

    fun sync(to: NodeContext): Boolean {
        for (i in this.open downTo 0) {
            if (this.nodes[i] == to) {
                this.open = i
                return true
            }
        }
        return false
    }

    val currentPos: Int
        get() {
            this.closeExtra()
            var pos = 0
            for (i in this.open downTo 0) {
                val content = this.nodes[i].content
                for (j in content.size - 1 downTo 0)
                    pos += content[j].nodeSize
                if (i != 0) pos++
            }
            return pos
        }

    fun findAtPoint(parent: DOMNode, offset: Int) {
        this.find?.forEach {
            if (it.node == parent && it.offset == offset) {
                it.pos = this.currentPos
            }
        }
    }

    fun findInside(parent: DOMNode) {
        this.find?.forEach {
            if (it.pos == null && (parent is Element) && parent.children().contains(it.node)) {
                it.pos = this.currentPos
            }
        }
    }

    @Suppress("MagicNumber", "NestedBlockDepth", "UnusedPrivateMember")
    fun findAround(parent: DOMNode, content: DOMNode, before: Boolean) {
//        if (parent != content) {
//            this.find?.forEach {
//                if (it.pos == null && (parent is Element) && parent.children().contains(it.node)) {
//                    val pos = content.compareDocumentPosition(it.node).toInt()
//                    if ((pos and (if (before) 2 else 4)) != 0) {
//                        it.pos = this.currentPos
//                    }
//                }
//            }
//        }
    }

    fun findInText(textNode: com.fleeksoft.ksoup.nodes.TextNode) {
        this.find?.forEach {
            if (it.node == textNode) {
                it.pos = this.currentPos - (textNode.getWholeText().length - it.offset)
            }
        }
    }

    // Determines whether the given context string matches this context.
    @Suppress("ComplexMethod", "NestedBlockDepth")
    fun matchesContext(context: String): Boolean {
        if (context.indexOf("|") > -1) {
            val regex = "\\s*\\|\\s*".toRegex()
            return context.split(regex).firstOrNull {
                matchesContext(it)
            } != null
        }

        val parts = context.split("/")
        val option = this.options.context
        val useRoot = !this.isOpen && (option == null || option.parent.type == this.nodes[0].type)
        val minDepth = -(if (option != null) option.depth + 1 else 0) + (if (useRoot) 0 else 1)

        @Suppress("ReturnCount")
        fun match(i: Int, depth: Int): Boolean {
            var depth = depth
            for (i in i downTo 0) {
                val part = parts[i]
                if (part == "") {
                    if (i == parts.size - 1 || i == 0) {
                        continue
                    }
                    while (depth >= minDepth) {
                        if (match(i - 1, depth)) {
                            return true
                        }
                        depth--
                    }
                    return false
                } else {
                    val next = if (depth > 0 || (depth == 0 && useRoot)) {
                        this.nodes[depth].type
                    } else if (option != null && depth >= minDepth) {
                        option.node(depth - minDepth).type
                    } else {
                        null
                    }
                    if (next == null || (next.name != part && next.groups.indexOf(part) == -1)) {
                        return false
                    }
                    depth--
                }
            }
            return true
        }

        return match(parts.size - 1, this.open)
    }

    @Suppress("ReturnCount")
    fun textblockFromContext(): NodeType? {
        val context = this.options.context
        if (context != null) {
            for (d in context.depth downTo 0) {
                val deflt = context.node(d).contentMatchAt(context.indexAfter(d)).defaultType
                if (deflt != null && deflt.isTextblock && deflt.defaultAttrs.isNotEmpty()) return deflt
            }
        }
        for (name in this.parser.schema.nodes) {
            val type = this.parser.schema.nodeType(name.key)
            if (type.isTextblock && type.defaultAttrs.isNotEmpty()) return type
        }
        return null
    }

    fun addPendingMark(mark: Mark) {
        val found = findSameMarkInSet(mark, this.top.pendingMarks)
        if (found != null) this.top.stashMarks.add(found)
        this.top.pendingMarks = mark.addToSet(this.top.pendingMarks)
    }

    fun removePendingMark(mark: Mark, upto: NodeContext) {
        for (depth in this.open downTo 0) {
            val level = this.nodes[depth]
            val found = level.pendingMarks.lastIndexOf(mark)
            if (found > -1) {
                level.pendingMarks = mark.removeFromSet(level.pendingMarks)
            } else {
                level.activeMarks = mark.removeFromSet(level.activeMarks)
                val stashMark = level.popFromStashMark(mark)
                if (stashMark != null && level.type != null && level.type.allowsMarkType(stashMark.type)) {
                    level.activeMarks = stashMark.addToSet(level.activeMarks)
                }
            }
            if (level == upto) {
                break
            }
        }
    }
}

// Kludge to work around directly nested list nodes produced by some
// tools and allowed by browsers to mean that the nested list is
// actually part of the list item above it.
fun normalizeList(dom: DOMNode) {
    var child = dom.firstChild()
    var prevItem: DOMNode? = null
    while (child != null) {
        val name = if (child is Element) child.nodeName().lowercase() else null
        if (name != null && listTags.contains(name) && prevItem != null) {
            (prevItem as Element).appendChild(child)
            child = prevItem
        } else if (name == "li") {
            prevItem = child
        } else if (name != null) {
            prevItem = null
        }
        child = child.nextSibling()
    }
}

// Apply a CSS selector.
fun matches(dom: DOMNode, selector: String): Boolean {
    return (dom as? Element)?.`is`(selector) ?: false
//    return htmlDomElementMatches(dom, selector)
}

// Tokenize a style attribute into property/value pairs.
fun parseStyles(style: String): List<String> {
    return buildList {
        RegexPatterns.STYLE_REGEX.findAll(style).forEach {
            add(it.groupValues[1])
            add(it.groupValues[2])
        }
    }
}

// fun copy(obj: {[prop: string]: any}) {
//     val copy: {[prop: string]: any} = {}
//     for (val prop in obj) copy[prop] = obj[prop]
//     return copy
// }

// Used when finding a mark at the top level of a fragment parse.
// Checks whether it would be reasonable to apply a given mark type to
// a given node, by looking at the way the mark occurs in the schema.
fun markMayApply(markType: MarkType, nodeType: NodeType): Boolean {
    val nodes = nodeType.schema.nodes
    for (name in nodes.keys) {
        val parent = nodes[name]
        if (parent?.allowsMarkType(markType) != true) continue
        val seen = mutableListOf<ContentMatch>()
        if (scan(nodeType, seen, parent.contentMatch)) return true
    }
    return false
}

@Suppress("ReturnCount")
private fun scan(nodeType: NodeType, seen: MutableList<ContentMatch>, match: ContentMatch): Boolean {
    seen.add(match)
    for (i in 0 until match.edgeCount) {
        val (type, next) = match.edge(i)
        if (type == nodeType) return true
        if (seen.indexOf(next) < 0 && scan(nodeType, seen, next)) return true
    }
    return false
}

fun findSameMarkInSet(mark: Mark, set: List<Mark>) = set.firstOrNull { it == mark }