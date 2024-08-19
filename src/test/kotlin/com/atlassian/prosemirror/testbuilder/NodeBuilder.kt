package com.atlassian.prosemirror.testbuilder

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec
import com.atlassian.prosemirror.testbuilder.NodeBuildCompanion.Companion.currentNodeTags
import com.atlassian.prosemirror.util.slice

private val testSchema = Schema(
    SchemaSpec(
        nodes = addListNodes(schemaBasic.spec.nodes, "paragraph block*", "block"),
        marks = schemaBasic.spec.marks
    )
)

val schema = testSchema

abstract class NodeBuildCompanion<T : NodeBuilder<T>>(
    val schema: Schema = testSchema
) {

    private val tags = mutableMapOf<String, Int>()
    private val tagsByNode = mutableMapOf<Node, Map<String, Int>>()
    open val checked = true

    fun clean() {
        tags.clear()
        tagsByNode.clear()
        currentNodeTags.clear()
    }

    fun pos(tag: String) = tags[tag]

    fun pos(node: Node, tag: String) = tagsByNode[node]!![tag]

    fun tags() = tags.keys.toList()

    fun tags(node: Node) = tagsByNode[node]!!.keys.toList()

    abstract fun create(): NodeBuilder<T>

    fun node(nodeName: String, func: NodeBuilder<T>.() -> Unit) = node(nodeName, checked, func)

    fun node(nodeName: String, checked: Boolean, func: NodeBuilder<T>.() -> Unit): Node {
        val builder: NodeBuilder<T> = create()
        return with(builder) {
            func.invoke(this)
            val nodeType: NodeType = schema.nodeType(nodeName)
            if (checked) {
                nodeType.createChecked(attrs = null, content = children, marks = marks)
            } else {
                nodeType.create(attrs = null, content = children, marks = marks)
            }
        }
    }

    fun doc(func: NodeBuilder<T>.() -> Unit): Node {
        currentNodeTags.clear()
        // don't check here, to allow for empty doc
        val res = node("doc", false, func)
        tags.putAll(currentNodeTags)
        tagsByNode[res] = currentNodeTags.toMap()
        return res
    }

    open fun p(func: NodeBuilder<T>.() -> Unit) = node("paragraph", func)

    open fun br(func: NodeBuilder<T>.() -> Unit) = node("hard_break", func)

    companion object {
        val currentNodeTags = mutableMapOf<String, Int>()
    }
}

abstract class NodeBuilder<T : NodeBuilder<T>>(
    var pos: Int = 0,
    val marks: List<Mark> = emptyList(),
    open val schema: Schema = testSchema
) {
    open val checked = true
    val children = mutableListOf<Node>()

    operator fun String.unaryPlus() {
        val value = processTags(this)
        if (value.isNotEmpty()) {
            val node = schema.text(value, marks)
            pos += node.nodeSize
            children.add(node)
        }
    }

    operator fun Unit.plus(node: Node) {
        pos += node.nodeSize
        children.add(node)
    }

    abstract fun create(pos: Int = 0, marks: List<Mark> = emptyList(), schema: Schema): NodeBuilder<T>

    private fun processTags(originalString: String): String {
        val re = Regex("""<(\w+)>""")
        var at = 0
        var out = ""
        re.findAll(originalString).forEach { m ->
            val index = m.range.first
            out += originalString.slice(at, index)
            at = index + m.value.length
            val tag = m.groupValues.first().let { it.slice(1, it.length - 1) }
            currentNodeTags[tag] = pos + out.length
        }
        out += originalString.substring(at)
        // pos += originalString.length - at
        return out
    }

    operator fun Node.unaryPlus() {
        pos += this.nodeSize + if (isLeaf) 1 else 2
        children.add(this)
    }

    operator fun Unit.plus(img: Unit) {
        // empty deliberately
    }

    operator fun Unit.plus(str: String) {
        val value = processTags(str)
        if (str.isEmpty() || value.isNotEmpty()) {
            val node = schema.text(value, marks)
            pos += node.nodeSize
            children.add(node)
        }
    }

    fun node(name: String, func: NodeBuilder<T>.() -> Unit, attrs: Attrs? = null) =
        node(name, this.checked, func, attrs)

    fun node(name: String, checked: Boolean, func: NodeBuilder<T>.() -> Unit, attrs: Attrs? = null) {
        val nb = create(pos + 1, schema = this.schema)
        func.invoke(nb)
        val nodeType: NodeType = schema.nodeType(name)
        val nodeToAdd = if (checked) {
            nodeType.createChecked(content = nb.children, marks = marks, attrs = attrs)
        } else {
            nodeType.create(content = nb.children, marks = marks, attrs = attrs)
        }
        pos += nodeToAdd.nodeSize
        children.add(nodeToAdd)
    }

    fun mark(name: String, func: NodeBuilder<T>.() -> Unit, attrs: Attrs? = null) {
        val markType = schema.marks[name] ?: return
        val mark = markType.create(attrs)
        val nb = create(pos, marks + mark, schema = this.schema)
        func.invoke(nb)
        nb.children.forEach { nodeToAdd ->
            pos += nodeToAdd.nodeSize
        }
        children.addAll(nb.children)
    }

    open fun p(func: NodeBuilder<T>.() -> Unit) = node("paragraph", func)
    open fun li(func: NodeBuilder<T>.() -> Unit) = node("list_item", func)
    open fun ul(func: NodeBuilder<T>.() -> Unit) = node("bullet_list", func)
    open fun ol(func: NodeBuilder<T>.() -> Unit) = node("ordered_list", func)
    open fun img(attrs: Attrs? = null, func: NodeBuilder<T>.() -> Unit) =
        node("image", func, mapOf("src" to "img.png") + (attrs?.toMap() ?: emptyMap()))
    open fun br(func: NodeBuilder<T>.() -> Unit) = node("hard_break", func)
    open fun hr(func: NodeBuilder<T>.() -> Unit) = node("horizontal_rule", func)
    open fun pre(func: NodeBuilder<T>.() -> Unit) = node("code_block", func)
    open fun blockquote(func: NodeBuilder<T>.() -> Unit) = node("blockquote", func)
    open fun em(func: NodeBuilder<T>.() -> Unit) = mark("em", func)
    open fun strong(func: NodeBuilder<T>.() -> Unit) = mark("strong", func)
    open fun underline(func: NodeBuilder<T>.() -> Unit) = mark("underline", func)
    open fun code(func: NodeBuilder<T>.() -> Unit) = mark("code", func)
    open fun a(href: String = "foo", func: NodeBuilder<T>.() -> Unit) =
        mark("link", func, attrs = mapOf("href" to href))

    open fun ah(attrs: Attrs, func: NodeBuilder<T>.() -> Unit) = mark("link", func, attrs = attrs)
    open fun h1(func: NodeBuilder<T>.() -> Unit) = node("heading", func, attrs = mapOf("level" to 1))
    open fun h2(func: NodeBuilder<T>.() -> Unit) = node("heading", func, attrs = mapOf("level" to 2))

    @Suppress("MagicNumber")
    open fun h3(func: NodeBuilder<T>.() -> Unit) = node("heading", func, attrs = mapOf("level" to 3))

    open fun indent(level: Int = 1, func: NodeBuilder<T>.() -> Unit) =
        mark("indentation", func, attrs = mapOf("level" to level))

    open fun indentation(align: String = "center", func: NodeBuilder<T>.() -> Unit) =
        mark("indentation", func, attrs = mapOf("align" to align))

    open fun breakout(mode: String = "wide", func: NodeBuilder<T>.() -> Unit) =
        mark("breakout", func, attrs = mapOf("mode" to mode))

    open fun layoutSection(func: NodeBuilder<T>.() -> Unit) = node("layoutSection", func)

    open fun layoutColumn(width: Double = 50.0, func: NodeBuilder<T>.() -> Unit) =
        node("layoutColumn", func, attrs = mapOf("width" to width))
}

class PMNodeBuilder(
    pos: Int = 0,
    marks: List<Mark> = emptyList(),
    schema: Schema
) : NodeBuilder<PMNodeBuilder>(pos, marks, schema) {
    override val checked: Boolean
        get() = false

    override fun create(pos: Int, marks: List<Mark>, schema: Schema): NodeBuilder<PMNodeBuilder> {
        return PMNodeBuilder(pos, marks, this.schema)
    }

    companion object : NodeBuildCompanion<PMNodeBuilder>(testSchema) {
        override val checked: Boolean
            get() = false

        override fun create(): PMNodeBuilder {
            return PMNodeBuilder(schema = this.schema)
        }
    }
}
