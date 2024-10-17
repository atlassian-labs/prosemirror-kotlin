package com.atlassian.prosemirror.transform

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.NodeRange
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.schemaBasic
import kotlin.test.Test

val schema = Schema(
    SchemaSpec(
        nodes = mapOf(
            "doc" to NodeSpecImpl(content = "head? block* sect* closing?"),
            "para" to NodeSpecImpl(content = "text*", group = "block"),
            "head" to NodeSpecImpl(content = "text*", marks = ""),
            "figure" to NodeSpecImpl(content = "caption figureimage", group = "block"),
            "quote" to NodeSpecImpl(content = "block+", group = "block"),
            "figureimage" to NodeSpecImpl(),
            "caption" to NodeSpecImpl(content = "text*", marks = ""),
            "sect" to NodeSpecImpl(content = "head block* sect*"),
            "closing" to NodeSpecImpl(content = "text*"),
            "text" to schemaBasic.spec.nodes["text"]!!,
            "fixed" to NodeSpecImpl(content = "head para closing", group = "block")
        ),
        marks = mapOf(
            "em" to MarkSpecImpl()
        )
    )
)

fun n(name: String, vararg content: Node) = schema.nodes[name]!!.create(null, content.asList())

fun t(str: String, em: Boolean = false) = schema.text(str, if (em) listOf(schema.mark("em")) else null)

val doc = n(
    "doc", // 0
    n("head", t("Head")), // 6
    n("para", t("Intro")), // 13
    n(
        "sect", // 14
        n("head", t("Section head")), // 28
        n(
            "sect", // 29
            n("head", t("Subsection head")), // 46
            n("para", t("Subtext")), // 55
            n(
                "figure", // 56
                n("caption", t("Figure caption")), // 72
                n("figureimage") // 74
            ),
            n("quote", n("para", t("!"))) // 81
        )
    ),
    n(
        "sect", // 82
        n("head", t("S2")), // 86
        n("para", t("Yes")) // 92
    ),
    n("closing", t("fin")) // 97
)

fun range(pos: Int, end: Int? = null): NodeRange? {
    return if (end == null) {
        doc.resolve(pos).blockRange()
    } else {
        doc.resolve(pos).blockRange(doc.resolve(end))
    }
}

class CanSplitTest {
    fun yes(pos: Int, depth: Int = 1, after: String? = null) {
        assertThat(
            canSplit(
                doc,
                pos,
                depth,
                if (after == null) null else listOf(NodeBase(type = schema.nodes[after]!!))
            )
        )
    }

    fun no(pos: Int, depth: Int = 1, after: String? = null) {
        assertThat(
            canSplit(doc, pos, depth, if (after == null) null else listOf(NodeBase(type = schema.nodes[after]!!)))
        ).isFalse()
    }

    @Test
    fun `can't at start`() = no(0)

    @Test
    fun `can't in head`() = no(3)

    @Test
    fun `can by making head a para`() = yes(3, 1, "para")

    @Test
    fun `can't on top level`() = no(6)

    @Test
    fun `can in regular para`() = yes(8)

    @Test
    fun `can't at start of section`() = no(14)

    @Test
    fun `can't in section head`() = no(17)

    @Test
    fun `can if also splitting the section`() = yes(17, 2)

    @Test
    fun `can if making the remaining head a para`() = yes(18, 1, "para")

    @Test
    fun `can't after the section head`() = no(46)

    @Test
    fun `can in the first section para`() = yes(48)

    @Test
    fun `can't in the figure caption`() = no(60)

    @Test
    fun `can't if it also splits the figure`() = no(62, 2)

    @Test
    fun `can't after the figure caption`() = no(72)

    @Test
    fun `can in the first para in a quote`() = yes(76)

    @Test
    fun `can if it also splits the quote`() = yes(77, 2)

    @Test
    fun `can't at the end of the document`() = no(97)

    @Test
    fun `doesn't return true when the split-off content doesn't fit in the given node type`() {
        val s = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes.toMutableMap().apply {
                    put("title", NodeSpecImpl(content = "text*"))
                    put("chapter", NodeSpecImpl(content = "title scene+"))
                    put("scene", NodeSpecImpl(content = "para+"))
                    put("doc", NodeSpecImpl(content = "chapter+"))
                }
            )
        )
        assertThat(
            canSplit(
                s.node(
                    "doc",
                    null,
                    s.node(
                        "chapter",
                        null,
                        listOf(
                            s.node("title", null, s.text("title")),
                            s.node("scene", null, s.node("para", null, s.text("scene")))
                        )
                    )
                ),
                4,
                1,
                listOf(NodeBase(type = s.nodes["scene"]!!))
            )
        ).isFalse()
    }
}

class LiftTargetTest {
    fun yes(pos: Int) {
        val r = range(pos)
        assertThat(r != null && liftTarget(r).let { it != null && it != 0 }).isTrue()
    }

    fun no(pos: Int) {
        val r = range(pos)
        assertThat(r != null && liftTarget(r).let { it != null && it != 0 }).isFalse()
    }

    @Test
    fun `can't at the start of the doc`() = no(0)

    @Test
    fun `can't in the heading`() = no(3)

    @Test
    fun `can't in a subsection para`() = no(52)

    @Test
    fun `can't in a figure caption`() = no(70)

    @Test
    fun `can from a quote`() = yes(76)

    @Test
    fun `can't in a section head`() = no(86)
}

class FindWrappingsTest {
    fun yes(pos: Int, end: Int, type: String) {
        val r = range(pos, end)
        assertThat(r != null && findWrapping(r, schema.nodes[type]!!) != null).isTrue()
    }

    fun no(pos: Int, end: Int, type: String) {
        val r = range(pos, end)
        assertThat(r == null || findWrapping(r, schema.nodes[type]!!) == null).isTrue()
    }

    @Test
    fun `can wrap the whole doc in a section`() = yes(0, 92, "sect")

    @Test
    fun `can't wrap a head before a para in a section`() = no(4, 4, "sect")

    @Test
    fun `can wrap a top paragraph in a quote`() = yes(8, 8, "quote")

    @Test
    fun `can't wrap a section head in a quote`() = no(18, 18, "quote")

    @Test
    fun `can wrap a figure in a quote`() = yes(55, 74, "quote")

    @Test
    fun `can't wrap a head in a figure`() = no(90, 90, "figure")
}

class StructureTransformTest {
    fun repl(doc: Node, from: Int, to: Int, content: Node?, openStart: Int, openEnd: Int, result: Node) {
        val slice = if (content != null) Slice(content.content, openStart, openEnd) else Slice.empty
        val tr = Transform(doc).replace(from, to, slice)
        assertThat(tr.doc).isEqualTo(result)
    }

    @Test
    fun `automatically adds a heading to a section`() = repl(
        n("doc", n("sect", n("head", t("foo")), n("para", t("bar")))),
        6,
        6,
        n("doc", n("sect"), n("sect")),
        1,
        1,
        n("doc", n("sect", n("head", t("foo"))), n("sect", n("head"), n("para", t("bar"))))
    )

    @Test
    fun `suppresses impossible inputs`() = repl(
        n("doc", n("para", t("a")), n("para", t("b"))),
        3,
        3,
        n("doc", n("closing", t("."))),
        0,
        0,
        n("doc", n("para", t("a")), n("para", t("b")))
    )

    @Test
    fun `adds necessary nodes to the left`() = repl(
        n("doc", n("sect", n("head", t("foo")), n("para", t("bar")))),
        1,
        3,
        n("doc", n("sect"), n("sect", n("head", t("hi")))),
        1,
        2,
        n("doc", n("sect", n("head")), n("sect", n("head", t("hioo")), n("para", t("bar"))))
    )

    @Test
    fun `adds a caption to a figure`() = repl(
        n("doc"),
        0,
        0,
        n("doc", n("figure", n("figureimage"))),
        1,
        0,
        n("doc", n("figure", n("caption"), n("figureimage")))
    )

    @Test
    fun `adds an image to a figure`() = repl(
        n("doc"),
        0,
        0,
        n("doc", n("figure", n("caption"))),
        0,
        1,
        n("doc", n("figure", n("caption"), n("figureimage")))
    )

    @Test
    fun `can join figures`() = repl(
        n("doc", n("figure", n("caption"), n("figureimage")), n("figure", n("caption"), n("figureimage"))),
        3,
        8,
        null,
        0,
        0,
        n("doc", n("figure", n("caption"), n("figureimage")))
    )

    @Test
    fun `adds necessary nodes to a parent node`() = repl(
        n("doc", n("sect", n("head"), n("figure", n("caption"), n("figureimage")))),
        7,
        9,
        n("doc", n("para", t("hi"))),
        0,
        0,
        n("doc", n("sect", n("head"), n("figure", n("caption"), n("figureimage")), n("para", t("hi"))))
    )
}
