package com.atlassian.prosemirror.transform

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.CustomNodeBuildCompanion
import com.atlassian.prosemirror.testbuilder.CustomNodeBuilder
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeBuilder
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.schema
import com.atlassian.prosemirror.util.safeMode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("LargeClass")
class TransformTest {

    @BeforeTest
    fun setup() {
        safeMode = true
    }

    // region addMark
    fun add(doc: Node, mark: Mark, expect: Node) {
        testTransform(Transform(doc).addMark(pos(doc, "a")!!, pos(doc, "b")!!, mark), expect)
    }

    @Test
    fun `should add a mark`() {
        add(
            doc { p { +"hello <a>there<b>!" } },
            schema.mark("strong"),
            doc { p { +"hello " + strong { +"there" } + "!" } }
        )
    }

    @Test
    fun `should only add a mark once`() {
        add(
            doc { p { +"hello " + strong { +"<a>there" } + "!<b>" } },
            schema.mark("strong"),
            doc { p { +"hello " + strong { +"there!" } } }
        )
    }

    @Test
    fun `should join overlapping marks`() {
        add(
            doc { p { +"one <a>two " + em { +"three<b> four" } } },
            schema.mark("strong"),
            doc { p { +"one " + strong { +"two " + em { +"three" } } + em { +" four" } } }
        )
    }

    @Test
    fun `should overwrite marks with different attributes`() {
        add(
            doc { p { +"this is a " + a { +"<a>link<b>" } } },
            schema.mark("link", mapOf("href" to "bar")),
            doc { p { +"this is a " + ah(mapOf("href" to "bar")) { +"link" } } }
        )
    }

    @Test
    fun `can add a mark in a nested node`() {
        add(
            doc { p { +"before" } + blockquote { p { +"the variable is called <a>i<b>" } } + p { +"after" } },
            schema.mark("code"),
            doc { p { +"before" } + blockquote { p { +"the variable is called " + code { +"i" } } } + p { +"after" } }
        )
    }

    @Test
    fun `can add a mark across blocks`() {
        add(
            doc { p { +"hi <a>this" } + blockquote { p { +"is" } } + p { +"a docu<b>ment" } + p { +"!" } },
            schema.mark("em"),
            doc {
                p { +"hi " + em { +"this" } } +
                    blockquote { p { em { +"is" } } } +
                    p { em { +"a docu" } + "ment" } +
                    p { +"!" }
            }
        )
    }

    @Test
    fun `does not remove non-excluded marks of the same type`() {
        val schema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "text*"),
                    "text" to NodeSpecImpl()
                ),
                marks = mapOf(
                    "comment" to MarkSpecImpl(
                        excludes = "",
                        attrs = mapOf("id" to AttributeSpecImpl())
                    )
                )
            )
        )
        val tr = Transform(
            schema.node(
                "doc",
                null,
                schema.text("hi", listOf(schema.mark("comment", mapOf("id" to 10))))
            )
        )
        tr.addMark(0, 2, schema.mark("comment", mapOf("id" to 20)))
        assertThat(tr.doc.firstChild!!.marks.size).isEqualTo(2)
    }

    @Test
    fun `can remove multiple excluded marks`() {
        val schema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "text*"),
                    "text" to NodeSpecImpl()
                ),
                marks = mapOf(
                    "big" to MarkSpecImpl(
                        excludes = "small1 small2"
                    ),
                    "small1" to MarkSpecImpl(),
                    "small2" to MarkSpecImpl()
                )
            )
        )
        val tr = Transform(
            schema.node(
                "doc",
                null,
                schema.text("hi", listOf(schema.mark("small1"), schema.mark("small2")))
            )
        )
        assertThat(tr.doc.firstChild!!.marks.size).isEqualTo(2)
        tr.addMark(0, 2, schema.mark("big"))
        assertThat(tr.doc.firstChild!!.marks.size).isEqualTo(1)
        assertThat(tr.doc.firstChild!!.marks.first().type.name).isEqualTo("big")
    }
// endregion

    // region removeMark
    fun rem(doc: Node, mark: Mark? = null, expect: Node) {
        val from = pos(doc, "a") ?: 0
        val to = pos(doc, "b") ?: (doc.nodeSize - 2)
        testTransform(Transform(doc).removeMark(from, to, mark), expect)
    }

    @Test
    fun `can cut a gap`() {
        rem(
            doc { p { em { +"hello <a>world<b>!" } } },
            schema.mark("em"),
            doc { p { em { +"hello " } + "world" + em { +"!" } } }
        )
    }

    @Test
    fun `doesn't do anything when there's no mark`() {
        rem(
            doc { p { em { +"hello" } + " <a>world<b>!" } },
            schema.mark("em"),
            doc { p { em { +"hello" } + " <a>world<b>!" } }
        )
    }

    @Test
    fun `can remove marks from nested nodes`() {
        rem(
            doc { p { em { +"one " + strong { +"<a>two<b>" } + " three" } } },
            schema.mark("strong"),
            doc { p { em { +"one two three" } } }
        )
    }

    @Test
    fun `can remove a link`() {
        rem(
            doc { p { +"<a>hello " + a { +"link<b>" } } },
            schema.mark("link", mapOf("href" to "foo")),
            doc { p { +"hello link" } }
        )
    }

    @Test
    fun `doesn't remove a non-matching link`() {
        rem(
            doc { p { +"<a>hello " + a { +"link<b>" } } },
            schema.mark("link", mapOf("href" to "bar")),
            doc { p { +"hello " + a { +"link" } } }
        )
    }

    @Test
    fun `can remove across blocks`() {
        rem(
            doc {
                blockquote { p { em { +"much <a>em" } } + p { em { +"here too" } } } +
                    p { +"between" + em { +"..." } } +
                    p { em { +"end<b>" } }
            },
            schema.mark("em"),
            doc { blockquote { p { em { +"much " } + "em" } + p { +"here too" } } + p { +"between..." } + p { +"end" } }
        )
    }

    @Test
    fun `can remove everything`() {
        rem(
            doc { p { +"<a>hello, " + em { +"this is " + strong { +"much" } + " " + a { +"markup<b>" } } } },
            null,
            doc { p { +"<a>hello, this is much markup" } }
        )
    }

    @Test
    fun `can remove more than one mark of the same type from a block`() {
        val schema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "text*"),
                    "text" to NodeSpecImpl()
                ),
                marks = mapOf(
                    "comment" to MarkSpecImpl(
                        excludes = "",
                        attrs = mapOf("id" to AttributeSpecImpl())
                    ),
                    "small1" to MarkSpecImpl(),
                    "small2" to MarkSpecImpl()
                )
            )
        )
        val tr = Transform(
            schema.node(
                "doc",
                null,
                schema.text(
                    "hi",
                    listOf(
                        schema.mark("comment", mapOf("id" to 1)),
                        schema.mark("comment", mapOf("id" to 2))
                    )
                )
            )
        )
        assertThat(tr.doc.firstChild!!.marks.size).isEqualTo(2)
        tr.removeMark(0, 2, schema.marks["comment"])
        assertThat(tr.doc.firstChild!!.marks.size).isEqualTo(0)
    }
// endregion

    // region insert
    fun ins(doc: Node, node: Node, expect: Node) {
        testTransform(Transform(doc).insert(pos(doc, "a")!!, node), expect)
    }

    fun ins(doc: Node, nodes: List<Node>, expect: Node) {
        testTransform(Transform(doc).insert(pos(doc, "a")!!, nodes), expect)
    }

    @Test
    fun `can insert a break`() {
        ins(
            doc { p { +"hello<a>there" } },
            schema.node("hard_break"),
            doc { p { +"hello" + br {} + "<a>there" } }
        )
    }

    @Test
    fun `can insert an empty paragraph at the top`() {
        ins(
            doc { p { +"one" } + "<a>" + p { +"two<2>" } },
            schema.node("paragraph"),
            doc { p { +"one" } + p {} + "<a>" + p { +"two<2>" } }
        )
    }

    @Test
    fun `can insert two block nodes`() {
        ins(
            doc { p { +"one" } + "<a>" + p { +"two<2>" } },
            listOf(
                schema.node("paragraph", null, listOf(schema.text("hi"))),
                schema.node("horizontal_rule")
            ),
            doc { p { +"one" } + p { +"hi" } + hr {} + "<a>" + p { +"two<2>" } }
        )
    }

    @Test
    fun `can insert at the end of a blockquote`() {
        ins(
            doc { blockquote { p { +"he<before>y" } + "<a>" } + p { +"after<after>" } },
            schema.node("paragraph"),
            doc { blockquote { p { +"he<before>y" } + p {} } + p { +"after<after>" } }
        )
    }

    @Test
    fun `can insert at the start of a blockquote`() {
        ins(
            doc { blockquote { +"<a>" + p { +"he<1>y" } } + p { +"after<2>" } },
            schema.node("paragraph"),
            doc { blockquote { p {} + "<a>" + p { +"he<1>y" } } + p { +"after<2>" } }
        )
    }

    @Test
    fun `will wrap a node with the suitable parent`() {
        ins(
            doc { p { +"foo<a>bar" } },
            schema.nodes["list_item"]!!.createAndFill()!!,
            doc { p { +"foo" } + ol { li { p {} } } + p { +"bar" } }
        )
    }
// endregion

    // region delete
    fun del(doc: Node, expect: Node) {
        testTransform(Transform(doc).delete(pos(doc, "a")!!, pos(doc, "b")!!), expect)
    }

    @Test
    fun `can delete a word`() = del(
        doc { p { +"<1>one" } + "<a>" + p { +"tw<2>o" } + "<b>" + p { +"<3>three" } },
        doc { p { +"<1>one" } + "<a><2>" + p { +"<3>three" } }
    )

    @Test
    fun `preserves content constraints`() = del(
        doc { blockquote { +"<a>" + p { +"hi" } + "<b>" } + p { +"x" } },
        doc { blockquote { p {} } + p { +"x" } }
    )

    @Test
    fun `preserves positions after the range`() = del(
        doc { blockquote { p { +"a" } + "<a>" + p { +"b" } + "<b>" } + p { +"c<1>" } },
        doc { blockquote { p { +"a" } } + p { +"c<1>" } }
    )

    @Test
    fun `doesn't join incompatible nodes`() = del(
        doc { pre { +"fo<a>o" } + p { +"b<b>ar" + img {} } },
        doc { pre { +"fo" } + p { +"ar" + img {} } }
    )

    @Test
    fun `doesn't join when marks are incompatible`() = del(
        doc { pre { +"fo<a>o" } + p { em { +"b<b>ar" } } },
        doc { pre { +"fo" } + p { em { +"ar" } } }
    )
    // endregion

    // region join
    private fun join(doc: Node, expect: Node, pos: ((Node, String) -> Int?) = PMNodeBuilder.Companion::pos) {
        testTransform(Transform(doc).join(pos(doc, "a")!!), expect)
    }

    @Test
    fun `can join blocks`() = join(
        doc { blockquote { p { +"<before>a" } } + "<a>" + blockquote { p { +"b" } } + p { +"after<after>" } },
        doc { blockquote { p { +"<before>a" } + "<a>" + p { +"b" } } + p { +"after<after>" } }
    )

    @Test
    fun `can join compatible blocks`() = join(
        doc { h1 { +"foo" } + "<a>" + p { +"bar" } },
        doc { h1 { +"foobar" } }
    )

    @Test
    fun `can join nested blocks`() = join(
        doc {
            blockquote {
                blockquote {
                    p { +"a" } +
                        p { +"b<before>" }
                } +
                    "<a>" +
                    blockquote { p { +"c" } + p { +"d<after>" } }
            }
        },
        doc {
            blockquote { blockquote { p { +"a" } + p { +"b<before>" } + "<a>" + p { +"c" } + p { +"d<after>" } } }
        }
    )

    @Test
    fun `can join lists`() = join(
        doc { ol { li { p { +"one" } } + li { p { +"two" } } } + "<a>" + ol { li { p { +"three" } } } },
        doc { ol { li { p { +"one" } } + li { p { +"two" } } + "<a>" + li { p { +"three" } } } }
    )

    @Test
    fun `can join list items`() = join(
        doc { ol { li { p { +"one" } } + li { p { +"two" } } + "<a>" + li { p { +"three" } } } },
        doc { ol { li { p { +"one" } } + li { p { +"two" } + "<a>" + p { +"three" } } } }
    )

    @Test
    fun `can join textblocks`() = join(
        doc { p { +"foo" } + "<a>" + p { +"bar" } },
        doc { p { +"foo<a>bar" } }
    )

    @Test
    fun `converts newlines to line breaks`() {
        val builder = CustomNodeBuildCompanion(linebreakSchema)
        join(
            builder.doc {
                p { +"one" } + "<a>" + pre { +"two\nthree" }
            },
            builder.doc {
                p { +"one<a>two" + br {} + "three" }
            },
            pos = builder::pos
        )
    }

    @Test
    fun `converts line breaks to newlines`() {
        val builder = CustomNodeBuildCompanion(linebreakSchema)
        join(
            builder.doc {
                pre { +"one" } + "<a>" + p { +"two" + br {} + "three" }
            },
            builder.doc {
                pre { +"one<a>two\nthree" }
            },
            pos = builder::pos
        )
    }
// endregion

    // region split
    fun split(doc: Node, expect: Node, depth: Int = 1, typesAfter: List<NodeBase>? = null) {
        testTransform(Transform(doc).split(pos(doc, "a")!!, depth, typesAfter), expect)
    }

    @Suppress("SwallowedException")
    fun splitFail(doc: Node, depth: Int = 1, typesAfter: List<NodeBase>? = null) {
        assertFailsWith(TransformError::class) {
            Transform(doc).split(pos(doc, "a")!!, depth, typesAfter)
        }
    }

    @Test
    fun `can split a textblock`() = split(
        doc { p { +"foo<a>bar" } },
        doc { p { +"foo" } + p { +"<a>bar" } }
    )

    @Test
    fun `correctly maps positions`() = split(
        doc { p { +"<1>a" } + p { +"<2>foo<a>bar<3>" } + p { +"<4>b" } },
        doc { p { +"<1>a" } + p { +"<2>foo" } + p { +"<a>bar<3>" } + p { +"<4>b" } }
    )

    @Test
    fun `can split two deep`() = split(
        doc { blockquote { blockquote { p { +"foo<a>bar" } } } + p { +"after<1>" } },
        doc { blockquote { blockquote { p { +"foo" } } + blockquote { p { +"<a>bar" } } } + p { +"after<1>" } },
        2
    )

    @Test
    fun `can split three deep`() = split(
        doc { blockquote { blockquote { p { +"foo<a>bar" } } } + p { +"after<1>" } },
        doc {
            blockquote { blockquote { p { +"foo" } } } +
                blockquote { blockquote { p { +"<a>bar" } } } +
                p { +"after<1>" }
        },
        3
    )

    @Test
    fun `can split at end`() = split(
        doc { blockquote { p { +"hi<a>" } } },
        doc { blockquote { p { +"hi" } + p { +"<a>" } } }
    )

    @Test
    fun `can split at start`() = split(
        doc { blockquote { p { +"<a>hi" } } },
        doc { blockquote { p {} + p { +"<a>hi" } } }
    )

    @Test
    fun `can split inside a list item`() = split(
        doc { ol { li { p { +"one<1>" } } + li { p { +"two<a>three" } } + li { p { +"four<2>" } } } },
        doc { ol { li { p { +"one<1>" } } + li { p { +"two" } + p { +"<a>three" } } + li { p { +"four<2>" } } } }
    )

    @Test
    fun `can split a list item`() = split(
        doc { ol { li { p { +"one<1>" } } + li { p { +"two<a>three" } } + li { p { +"four<2>" } } } },
        doc {
            ol {
                li { p { +"one<1>" } } +
                    li { p { +"two" } } +
                    li { p { +"<a>three" } } +
                    li { p { +"four<2>" } }
            }
        },
        2
    )

    @Test
    fun `respects the type param`() = split(
        doc { h1 { +"hell<a>o!" } },
        doc { h1 { +"hell" } + p { +"<a>o!" } },
        typesAfter = listOf(NodeBase(type = schema.nodes["paragraph"]!!))
    )

    @Test
    fun `preserves content constraints before`() {
        safeMode = false
        splitFail(doc { blockquote { +"<a>" + p { +"x" } } })
    }

    @Test
    fun `preserves content constraints after`() {
        safeMode = false
        splitFail(doc { blockquote { p { +"x" } + "<a>" } })
    }
    // endregion

    // region lift
    fun lift(doc: Node, expect: Node) {
        val range = doc.resolve(pos(doc, "a")!!)
            .blockRange(doc.resolve(pos(doc, "b") ?: pos(doc, "a")!!))
        testTransform(Transform(doc).lift(range!!, liftTarget(range)!!), expect)
    }

    @Test
    fun `can lift a block out of the middle of its parent`() = lift(
        doc { blockquote { p { +"<before>one" } + p { +"<a>two" } + p { +"<after>three" } } },
        doc { blockquote { p { +"<before>one" } } + p { +"<a>two" } + blockquote { p { +"<after>three" } } }
    )

    @Test
    fun `can lift a block from the start of its parent`() = lift(
        doc { blockquote { p { +"<a>two" } + p { +"<after>three" } } },
        doc { p { +"<a>two" } + blockquote { p { +"<after>three" } } }
    )

    @Test
    fun `can lift a block from the end of its parent`() = lift(
        doc { blockquote { p { +"<before>one" } + p { +"<a>two" } } },
        doc { blockquote { p { +"<before>one" } } + p { +"<a>two" } }
    )

    @Test
    fun `can lift a single child`() = lift(
        doc { blockquote { p { +"<a>t<in>wo" } } },
        doc { p { +"<a>t<in>wo" } }
    )

    @Test
    fun `can lift multiple blocks`() = lift(
        doc { blockquote { blockquote { p { +"on<a>e" } + p { +"tw<b>o" } } + p { +"three" } } },
        doc { blockquote { p { +"on<a>e" } + p { +"tw<b>o" } + p { +"three" } } }
    )

    @Test
    fun `finds a valid range from a lopsided selection`() = lift(
        doc { p { +"start" } + blockquote { blockquote { p { +"a" } + p { +"<a>b" } } + p { +"<b>c" } } },
        doc { p { +"start" } + blockquote { p { +"a" } + p { +"<a>b" } } + p { +"<b>c" } }
    )

    @Test
    fun `can lift from a nested node`() = lift(
        doc {
            blockquote {
                blockquote {
                    p { +"<1>one" } +
                        p { +"<a>two" } +
                        p { +"<3>three" } +
                        p { +"<b>four" } +
                        p { +"<5>five" }
                }
            }
        },
        doc {
            blockquote {
                blockquote { p { +"<1>one" } } +
                    p { +"<a>two" } +
                    p { +"<3>three" } +
                    p { +"<b>four" } +
                    blockquote { p { +"<5>five" } }
            }
        }
    )

    @Test
    fun `can lift from a list`() = lift(
        doc { ul { li { p { +"one" } } + li { p { +"two<a>" } } + li { p { +"three" } } } },
        doc { ul { li { p { +"one" } } } + p { +"two<a>" } + ul { li { p { +"three" } } } }
    )

    @Test
    fun `can lift from a mixed list`() = lift(
        doc {
            ul {
                li { p { +"l1" } + ol { li { p { +"l2" } } } } +
                    li { p { +"<a>" } + p { } + ol { li { p { +"l12" } } } }
            }
        },
        doc {
            ul {
                li { p { +"l1" } + ol { li { p { +"l2" } } } }
            } +
                p { } +
                ul {
                    li { p { } + ol { li { p { +"l12" } } } }
                }
        }
    )

    @Test
    fun `can lift from the end of a list`() = lift(
        doc { ul { li { p { +"a" } } + li { p { +"b<a>" } } + "<1>" } },
        doc { ul { li { p { +"a" } } } + p { +"b<a>" } + "<1>" }
    )
// endregion

    // region wrap
    fun wrap(doc: Node, expect: Node, type: String, attrs: Attrs? = null) {
        val range = doc.resolve(pos(doc, "a")!!)
            .blockRange(doc.resolve(pos(doc, "b") ?: pos(doc, "a")!!))!!
        testTransform(Transform(doc).wrap(range, findWrapping(range, schema.nodeType(type), attrs)!!), expect)
    }

    @Test
    fun `can wrap in a blockquote`() = wrap(
        doc { p { +"one" } + p { +"<a>two" } + p { +"three" } },
        doc { p { +"one" } + blockquote { p { +"<a>two" } } + p { +"three" } },
        "blockquote"
    )

    @Test
    fun `can wrap two paragraphs`() = wrap(
        doc { p { +"one<1>" } + p { +"<a>two" } + p { +"<b>three" } + p { +"four<4>" } },
        doc { p { +"one<1>" } + blockquote { p { +"<a>two" } + p { +"three" } } + p { +"four<4>" } },
        "blockquote"
    )

    @Test
    fun `can wrap in a list`() = wrap(
        doc { p { +"<a>one" } + p { +"<b>two" } },
        doc { ol { li { p { +"<a>one" } + p { +"<b>two" } } } },
        "ordered_list"
    )

    @Test
    fun `can wrap in a nested list`() = wrap(
        doc {
            ol {
                li { p { +"<1>one" } } +
                    li { p { +"..." } + p { +"<a>two" } + p { +"<b>three" } } +
                    li { p { +"<4>four" } }
            }
        },
        doc {
            ol {
                li { p { +"<1>one" } } +
                    li { p { +"..." } + ol { li { p { +"<a>two" } + p { +"<b>three" } } } } +
                    li { p { +"<4>four" } }
            }
        },
        "ordered_list"
    )

    @Test
    fun `includes half-covered parent nodes`() = wrap(
        doc { blockquote { p { +"<1>one" } + p { +"two<a>" } } + p { +"three<b>" } },
        doc { blockquote { blockquote { p { +"<1>one" } + p { +"two<a>" } } + p { +"three<b>" } } },
        "blockquote"
    )
    // endregion

    // region setBlockType
    private fun type(
        doc: Node,
        expect: Node,
        nodeType: String,
        attrs: Attrs? = null,
        pos: ((Node, String) -> Int?) = PMNodeBuilder.Companion::pos,
        useSchema: Schema = schema
    ) {
        testTransform(
            Transform(doc).setBlockType(
                pos.invoke(doc, "a")!!,
                pos.invoke(doc, "b") ?: pos.invoke(doc, "a")!!,
                useSchema.nodes[nodeType]!!,
                attrs
            ),
            expect
        )
    }

    private fun type(
        doc: Node,
        expect: Node,
        nodeType: String,
        attrs: (Node) -> Attrs,
        pos: ((Node, String) -> Int?) = PMNodeBuilder.Companion::pos,
        useSchema: Schema = schema
    ) {
        testTransform(
            Transform(doc).setBlockType(
                pos.invoke(doc, "a")!!,
                pos.invoke(doc, "b") ?: pos.invoke(doc, "a")!!,
                useSchema.nodes[nodeType]!!,
                attrs
            ),
            expect
        )
    }

    @Test
    fun `can change a single textblock`() = type(
        doc { p { +"am<a> i" } },
        doc { h2 { +"am i" } },
        "heading",
        mapOf("level" to 2)
    )

    @Test
    fun `can change multiple blocks`() = type(
        doc { h1 { +"<a>hello" } + p { +"there" } + p { +"<b>you" } + p { +"end" } },
        doc { pre { +"hello" } + pre { +"there" } + pre { +"you" } + p { +"end" } },
        "code_block"
    )

    @Test
    fun `can change a wrapped block`() = type(
        doc { blockquote { p { +"one<a>" } + p { +"two<b>" } } },
        doc { blockquote { h1 { +"one<a>" } + h1 { +"two<b>" } } },
        "heading",
        mapOf("level" to 1)
    )

    @Test
    fun `clears markup when necessary`() = type(
        doc { p { +"hello<a> " + em { +"world" } } },
        doc { pre { +"hello world" } },
        "code_block"
    )

    @Test
    fun `removes non-allowed nodes`() = type(
        doc { p { +"<a>one" + img {} + "two" + img {} + "three" } },
        doc { pre { +"onetwothree" } },
        "code_block"
    )

    @Test
    fun `removes newlines in non-code`() = type(
        doc { pre { +"<a>one\ntwo\nthree" } },
        doc { p { +"one two three" } },
        "paragraph"
    )

    @Test
    fun `only clears markup when needed`() = type(
        doc { p { +"hello<a> " + em { +"world" } } },
        doc { h1 { +"hello<a> " + em { +"world" } } },
        "heading",
        mapOf("level" to 1)
    )

    @Test
    fun `works after another step`() {
        val d = doc { p { +"f<x>oob<y>ar" } + p { +"baz<a>" } }
        val tr = Transform(d).delete(pos(d, "x")!!, pos(d, "y")!!)
        val pos = tr.mapping.map(pos(d, "a")!!)
        tr.setBlockType(pos, pos, schema.nodes["heading"]!!, mapOf("level" to 1))
        testTransform(tr, doc { p { +"f<x><y>ar" } + h1 { +"baz<a>" } })
    }

    @Test
    fun `skips nodes that can't be changed due to constraints`() {
        type(
            doc { p { +"<a>hello" + img {} } + p { +"okay" } + ul { li { p { +"foo<b>" } } } },
            doc { pre { +"<a>hello" } + pre { +"okay" } + ul { li { p { +"foo<b>" } } } },
            "code_block"
        )
    }

    @Test
    fun `converts newlines to linebreak replacements when appropriate`() {
        val builder = CustomNodeBuildCompanion(linebreakSchema)
        type(
            builder.doc {
                pre { +"<a>one\ntwo\nthree" }
            },
            builder.doc {
                p { +"<a>one" + br {} + "two" + br {} + "three" }
            },
            "paragraph",
            pos = builder::pos,
            useSchema = linebreakSchema
        )

        type(
            builder.doc {
                p { +"<a>one\ntwo" }
            },
            builder.doc {
                pre { +"<a>one\ntwo" }
            },
            "code_block",
            pos = builder::pos,
            useSchema = linebreakSchema
        )
    }

    @Test
    fun `converts linebreak replacements to newlines when appropriate`() {
        val builder = CustomNodeBuildCompanion(linebreakSchema)
        type(
            builder.doc {
                p { +"<a>one" + br {} + "two" + br {} + "three" }
            },
            builder.doc {
                pre { +"one\ntwo\nthree<a>" }
            },
            "code_block",
            pos = builder::pos,
            useSchema = linebreakSchema
        )

        type(
            builder.doc {
                p { +"<a>one" + br {} + "two" + br {} + "three" }
            },
            builder.doc {
                h1 { +"<a>one" + br {} + "two" + br {} + "three" }
            },
            "heading",
            mapOf("level" to 1),
            pos = builder::pos,
            useSchema = linebreakSchema
        )
    }

    @Test
    fun `can base attributes on previous attributes`() = type(
        doc { +"<a>" + h1 { +"a" } + p { +"b" } + "<b>" },
        doc { h2 { +"a" } + h1 { +"b" } },
        "heading",
        { node -> mapOf("level" to ((node.attrs["level"] as? Int) ?: 0) + 1) }
    )
    // endregion

    // region setNodeMarkup
    fun markup(doc: Node, expect: Node, type: String, attrs: Attrs? = null) {
        testTransform(
            Transform(doc).setNodeMarkup(
                pos(doc, "a")!!,
                schema.nodes[type]!!,
                attrs
            ),
            expect
        )
    }

    @Test
    fun `can change a textblock`() = markup(
        doc { +"<a>" + p { +"foo" } },
        doc { h1 { +"foo" } },
        "heading",
        mapOf("level" to 1)
    )

    @Test
    fun `can change an inline node`() = markup(
        doc { p { +"foo<a>" + img {} + "bar" } },
        doc { p { +"foo" + img(mapOf("src" to "bar", "alt" to "y")) {} + "bar" } },
        "image",
        mapOf("src" to "bar", "alt" to "y")
    )

    // endregion

    // region replace
    // repl
    // @param doc
    // @param source
    // @param expect
    // @param useDocFirstChild if true, use the first child of the doc as the target.
    // Useful because NodeBuilder only adds tags are added to the root doc.
    // @param useSourceFirstChild if true, use the first child of the source as the target.
    // Useful because NodeBuilder only adds tags are added to the root doc.
    // @param useExpectFirstChild if true, use the first child of the expect as the target.
    // Useful because NodeBuilder only adds tags are added to the root doc.
    fun repl(
        doc: Node,
        source: Node,
        expect: Node,
        useDocFirstChild: Boolean = false,
        useSourceFirstChild: Boolean = false,
        useExpectFirstChild: Boolean = false
    ) {
        val sourceOffset = if (useSourceFirstChild) -1 else 0
        val slice = if (useSourceFirstChild) {
            source.firstChild!!
        } else {
            source
        }.slice(pos(source, "a")!! + sourceOffset, pos(source, "b")!! + sourceOffset)
        repl(doc, slice, expect, useDocFirstChild, useExpectFirstChild)
    }

    fun repl(
        doc: Node,
        source: Slice?,
        expect: Node,
        useDocFirstChild: Boolean = false,
        useExpectFirstChild: Boolean = false
    ) {
        val slice = source ?: Slice.empty
        val docOffset = if (useDocFirstChild) -1 else 0
        val theDoc = if (useDocFirstChild) {
            doc.firstChild!!
        } else {
            doc
        }
        testTransform(
            Transform(theDoc).replace(
                pos(doc, "a")!! + docOffset,
                (pos(doc, "b") ?: pos(doc, "a")!!) + docOffset,
                slice
            ),
            expect,
            useExpectFirstChild
        )
    }

    @Test
    fun `can delete text`() = repl(
        doc { p { +"hell<a>o y<b>ou" } },
        null,
        doc { p { +"hell<a><b>ou" } }
    )

    @Test
    fun `can append two blockquotes`() = repl(
        doc { blockquote { p { +"hell<a>" } } + p { +"<b>o" } },
        null,
        doc { blockquote { p { +"hell<a><b>o" } } }
    )

    @Test
    fun `can append two paragraphs`() = repl(
        doc { p { +"hell<a>" } + p { +"<b>o" } },
        null,
        doc { p { +"hell<a><b>o" } }
    )

    @Test
    fun `replace can join blocks`() = repl(
        doc { p { +"hell<a>o" } + p { +"y<b>ou" } },
        null,
        doc { p { +"hell<a><b>ou" } }
    )

    @Test
    fun `can delete right-leaning lopsided regions`() = repl(
        doc { blockquote { p { +"ab<a>c" } } + "<b>" + p { +"def" } },
        null,
        doc { blockquote { p { +"ab<a>" } } + "<b>" + p { +"def" } }
    )

    @Test
    fun `can delete left-leaning lopsided regions`() = repl(
        doc { p { +"abc" } + "<a>" + blockquote { p { +"d<b>ef" } } },
        null,
        doc { p { +"abc" } + "<a>" + blockquote { p { +"<b>ef" } } }
    )

    @Test
    fun `can overwrite text`() = repl(
        doc { p { +"hell<a>o y<b>ou" } },
        doc { p { +"<a>i k<b>" } },
        doc { p { +"hell<a>i k<b>ou" } }
    )

    @Test
    fun `can insert text`() = repl(
        doc { p { +"hell<a><b>o" } },
        doc { p { +"<a>i k<b>" } },
        doc { p { +"helli k<a><b>o" } }
    )

    @Test
    fun `can add a textblock`() = repl(
        doc { p { +"hello<a>you" } },
        doc { +"<a>" + p { +"there" } + "<b>" },
        doc { p { +"hello" } + p { +"there" } + p { +"<a>you" } }
    )

    @Test
    fun `can insert while joining textblocks`() = repl(
        doc { h1 { +"he<a>llo" } + p { +"arg<b>!" } },
        doc { p { +"1<a>2<b>3" } },
        doc { h1 { +"he2!" } }
    )

    @Test
    fun `will match open list items`() = repl(
        doc { ol { li { p { +"one<a>" } } + li { p { +"three" } } } },
        doc { ol { li { p { +"<a>half" } } + li { p { +"two" } } + "<b>" } },
        doc { ol { li { p { +"onehalf" } } + li { p { +"two" } } + li { p { +"three" } } } }
    )

    @Test
    fun `merges blocks across deleted content`() = repl(
        doc { p { +"a<a>" } + p { +"b" } + p { +"<b>c" } },
        null,
        doc { p { +"a<a><b>c" } }
    )

    @Test
    fun `can merge text down from nested nodes`() = repl(
        doc { h1 { +"wo<a>ah" } + blockquote { p { +"ah<b>ha" } } },
        null,
        doc { h1 { +"wo<a><b>ha" } }
    )

    @Test
    fun `can merge text up into nested nodes`() = repl(
        doc { blockquote { p { +"foo<a>bar" } } + p { +"middle" } + h1 { +"quux<b>baz" } },
        null,
        doc { blockquote { p { +"foo<a><b>baz" } } }
    )

    @Test
    fun `will join multiple levels when possible`() = repl(
        doc {
            blockquote {
                ul {
                    li { p { +"a" } } +
                        li { p { +"b<a>" } } +
                        li { p { +"c" } } +
                        li { p { +"<b>d" } } +
                        li { p { +"e" } }
                }
            }
        },
        null,
        doc { blockquote { ul { li { p { +"a" } } + li { p { +"b<a><b>d" } } + li { p { +"e" } } } } }
    )

    @Test
    fun `will join list and paragraph`() = repl(
        doc {
            ul {
                li { p { +"b<a>" } }
            }
            p { +"<b>d" }
        },
        null,
        doc { ul { li { p { +"bd" } } } }
    )

    @Test
    fun `will join paragraph and codeblock`() = repl(
        doc {
            p { +"b<a>" }
            pre { +"<b>d" }
        },
        null,
        doc { p { +"bd" } }
    )

    @Test
    fun `can replace a piece of text`() = repl(
        doc { p { +"he<before>llo<a> w<after>orld" } },
        doc { p { +"<a> big<b>" } },
        doc { p { +"he<before>llo big w<after>orld" } }
    )

    @Test
    fun `respects open empty nodes at the edges`() = repl(
        doc { p { +"one<a>two" } },
        doc { p { +"<a>" } + p { +"hello" } + p { +"<b>b" } },
        doc { p { +"one" } + p { +"hello" } + p { +"<a>two" } }
    )

    @Test
    fun `can completely overwrite a paragraph`() = repl(
        doc { p { +"one<a>" } + p { +"t<inside>wo" } + p { +"<b>three<end>" } },
        doc { p { +"a<a>" } + p { +"TWO" } + p { +"<b>b" } },
        doc { p { +"one<a>" } + p { +"TWO" } + p { +"<inside>three<end>" } }
    )

    @Test
    fun `joins marks`() = repl(
        doc { p { +"foo " + em { +"bar<a>baz" } + "<b> quux" } },
        doc { p { +"foo " + em { +"xy<a>zzy" } + " foo<b>" } },
        doc { p { +"foo " + em { +"barzzy" } + " foo quux" } }
    )

    @Test
    fun `can replace a text with a break`() = repl(
        doc { p { +"foo<a>b<inside>b<b>bar" } },
        doc { p { +"<a>" + br {} + "<b>" } },
        doc { p { +"foo" + br {} + "<inside>bar" } }
    )

    @Test
    fun `can join different blocks`() = repl(
        doc { h1 { +"hell<a>o" } + p { +"by<b>e" } },
        null,
        doc { h1 { +"helle" } }
    )

    @Test
    fun `can restore a list parent`() = repl(
        doc { h1 { +"hell<a>o" } + p { +"<b>" } },
        doc { ol { li { p { +"on<a>e" } } + li { p { +"tw<b>o" } } } },
        doc { h1 { +"helle" } + ol { li { p { +"tw" } } } }
    )

    @Test
    fun `can restore a list parent and join text after it`() = repl(
        doc { h1 { +"hell<a>o" } + p { +"yo<b>u" } },
        doc { ol { li { p { +"on<a>e" } } + li { p { +"tw<b>o" } } } },
        doc { h1 { +"helle" } + ol { li { p { +"twu" } } } }
    )

    @Test
    fun `can insert a block into an empty block`() = repl(
        doc { p { +"a" } + p { +"<a>" } + p { +"b" } },
        doc { p { +"x<a>y<b>z" } },
        doc { p { +"a" } + p { +"y<a>" } + p { +"b" } }
    )

    @Test
    fun `doesn't change the nesting of blocks after the selection`() = repl(
        doc { p { +"one<a>" } + p { +"two" } + p { +"three" } },
        doc { p { +"outside<a>" } + blockquote { p { +"inside<b>" } } },
        doc { p { +"one" } + blockquote { p { +"inside" } } + p { +"two" } + p { +"three" } }
    )

    @Test
    fun `can close a parent node`() = repl(
        doc { blockquote { p { +"b<a>c" } + p { +"d<b>e" } + p { +"f" } } },
        doc { blockquote { p { +"x<a>y" } } + p { +"after" } + "<b>" },
        doc { blockquote { p { +"b<a>y" } } + p { +"after" } + blockquote { p { +"<b>e" } + p { +"f" } } }
    )

    @Test
    fun `accepts lopsided regions`() = repl(
        doc { blockquote { p { +"b<a>c" } + p { +"d<b>e" } + p { +"f" } } },
        doc { blockquote { p { +"x<a>y" } } + p { +"z<b>" } },
        doc { blockquote { p { +"b<a>y" } } + p { +"z<b>e" } + blockquote { p { +"f" } } }
    )

    @Test
    fun `can close nested parent nodes`() = repl(
        doc { blockquote { blockquote { p { +"one" } + p { +"tw<a>o" } + p { +"t<b>hree<3>" } + p { +"four<4>" } } } },
        doc { ol { li { p { +"hello<a>world" } } + li { p { +"bye" } } } + p { +"ne<b>xt" } },
        doc {
            blockquote {
                blockquote {
                    p { +"one" } +
                        p { +"tw<a>world" } +
                        ol { li { p { +"bye" } } } +
                        p { +"ne<b>hree<3>" } +
                        p { +"four<4>" }
                }
            }
        }
    )

    @Test
    fun `will close open nodes to the right`() = repl(
        doc { p { +"x" } + "<a>" },
        doc { +"<a>" + ul { li { p { +"a" } } + li { +"<b>" + p { +"b" } } } },
        doc { p { +"x" } + ul { li { p { +"a" } } + li { p { } } } + "<a>" }
    )

    @Test
    fun `can delete the whole document`() = repl(
        doc { p { +"<a>" } + h1 { +"hi" } + p { +"you" } + p { +"<b>" } },
        null,
        doc { p {} }
    )

    @Test
    fun `preserves an empty parent to the left`() = repl(
        doc { blockquote { +"<a>" + p { +"hi" } } + p { +"b<b>x" } },
        doc { p { +"<a>hi<b>" } },
        doc { blockquote { p { +"hix" } } }
    )

    @Test
    fun `drops an empty parent to the right`() = repl(
        doc { p { +"x<a>hi" } + blockquote { p { +"yy" } + "<b>" } + p { +"c" } },
        doc { p { +"<a>hi<b>" } },
        doc { p { +"xhi" } + p { +"c" } }
    )

    @Test
    fun `drops an empty node at the start of the slice`() = repl(
        doc { p { +"<a>x" } },
        doc { blockquote { p { +"hi" } + "<a>" } + p { +"b<b>" } },
        doc { p {} + p { +"bx" } }
    )

    @Test
    fun `drops an empty node at the end of the slice`() = repl(
        doc { p { +"<a>x" } },
        doc { p { +"b<a>" } + blockquote { +"<b>" + p { +"hi" } } },
        doc { p { } + blockquote { p { } } + p { +"x" } }
    )

    @Test
    fun `does nothing when given an unfittable slice`() {
        val doc = doc { p { +"<a>x" } }
        val source = doc { blockquote { } + hr { } }
        val expectedDoc = doc { p { +"x" } }
        PMNodeBuilder.Companion.tags()
        repl(
            doc,
            Slice(source.content, 0, 0),
            expectedDoc,
            useDocFirstChild = true,
            useExpectFirstChild = true
        )
    }

    @Test
    fun `doesn't drop content when things only fit at the top level`() = repl(
        doc { p { +"foo" } + "<a>" + p { +"bar<b>" } },
        doc { ol { li { p { +"<a>a" } } + li { p { +"b<b>" } } } },
        doc { p { +"foo" } + p { +"a" } + ol { li { p { +"b" } } } },
        useSourceFirstChild = true
    )

    @Test
    fun `preserves openEnd when top isn't placed`() = repl(
        doc { ul { li { p { +"ab<a>cd" } } + li { p { +"ef<b>gh" } } } },
        doc { ul { li { p { +"ABCD" } } + li { p { +"EFGH" } } } }.slice(5, 13, true),
        doc { ul { li { p { +"abCD" } } + li { p { +"EFgh" } } } }
    )

    @Test
    fun `will auto-close a list item when it fits in a list`() = repl(
        doc { ul { li { p { +"foo" } } + "<a>" + li { p { +"bar" } } } },
        doc { ul { li { p { +"a<a>bc" } } + li { p { +"de<b>f" } } } },
        doc { ul { li { p { +"foo" } } + li { p { +"bc" } } + li { p { +"de" } } + li { p { +"bar" } } } },
        useSourceFirstChild = true
    )

    @Test
    fun `finds the proper openEnd value when unwrapping a deep slice`() = repl(
        doc { +"<a>" + p {} + "<b>" },
        doc { blockquote { blockquote { blockquote { p { +"hi" } } } } }.slice(3, 6, true),
        doc { p { +"hi" } }
    )

    // A schema that allows marks on top-level block nodes
    private val ms = Schema(
        SchemaSpec(
            nodes = schema.spec.nodes + mapOf(
                "doc" to (schema.spec.nodes["doc"] as NodeSpecImpl).copy(marks = "_")
            ),
            marks = schema.spec.marks
        )
    )

    @Test
    fun `preserves marks on block nodes`() {
        val tr = Transform(
            ms.node(
                "doc",
                null,
                listOf(
                    ms.node("paragraph", null, listOf(ms.text("hey")), listOf(ms.mark("em"))),
                    ms.node("paragraph", null, listOf(ms.text("ok")), listOf(ms.mark("strong")))
                )
            )
        )
        tr.replace(2, 7, tr.doc.slice(2, 7))
        assertEquals(tr.doc, tr.before)
    }

    @Test
    fun `preserves marks on open slice block nodes`() {
        val tr = Transform(
            ms.node(
                "doc",
                null,
                listOf(
                    ms.node("paragraph", null, listOf(ms.text("a")))
                )
            )
        )
        tr.replace(
            3,
            3,
            ms.node(
                "doc",
                null,
                listOf(
                    ms.node("paragraph", null, listOf(ms.text("b")), listOf(ms.mark("em")))
                )
            ).slice(1, 3)
        )
        assertEquals(tr.doc.childCount, 2)
        assertEquals(tr.doc.lastChild!!.marks.size, 1)
    }

    private fun NodeBuilder<CustomNodeBuilder>.b(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) = node("body", func)
    private fun NodeBuilder<CustomNodeBuilder>.h(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
        node("heading", func, mapOf("level" to 1))

    @Test
    fun `can unwrap a paragraph when replacing into a strict schema`() {
        val builder = CustomNodeBuildCompanion(hbSchema)
        val doc = builder.doc {
            h { +"Head" }
            b { p { +"Content" } }
        }
        val tr = Transform(doc)
        tr.replace(0, tr.doc.content.size, tr.doc.slice(7, 16))
        assertEquals(
            tr.doc,
            builder.doc {
                h { +"Content" }
                b { p {} }
            }
        )
    }

    @Test
    fun `can unwrap a body after a placed node`() {
        val builder = CustomNodeBuildCompanion(hbSchema)
        val tr = Transform(
            builder.doc {
                h { +"Head" }
                b { p { +"Content" } }
            }
        )
        tr.replace(7, 7, tr.doc.slice(0, tr.doc.content.size))
        assertEquals(
            tr.doc,
            builder.doc {
                h { +"Head" }
                b { h { +"Head" } + p { +"Content" } + p { +"Content" } }
            }
        )
    }

    @Test
    fun `can wrap a paragraph in a body even when it's not the first node`() {
        val builder = CustomNodeBuildCompanion(hbSchema)
        val tr = Transform(
            builder.doc {
                h { +"Head" }
                b { p { +"One" } + p { +"Two" } }
            }
        )
        tr.replace(0, tr.doc.content.size, tr.doc.slice(8, 16))
        assertEquals(
            tr.doc,
            builder.doc {
                h { +"One" }
                b { p { +"Two" } }
            }
        )
    }

    @Test
    fun `can split a fragment and place its children in different parents`() {
        val builder = CustomNodeBuildCompanion(hbSchema)
        val tr = Transform(
            builder.doc {
                h { +"Head" }
                b { h { +"One" } + p { +"Two" } }
            }
        )
        tr.replace(0, tr.doc.content.size, tr.doc.slice(7, 17))
        assertEquals(
            tr.doc,
            builder.doc {
                h { +"One" }
                b { p { +"Two" } }
            }
        )
    }

    @Test
    fun `will insert filler nodes before a node when necessary`() {
        val builder = CustomNodeBuildCompanion(hbSchema)
        val tr = Transform(
            builder.doc {
                h { +"Head" }
                b { p { +"One" } }
            }
        )
        tr.replace(0, tr.doc.content.size, tr.doc.slice(6, tr.doc.content.size))
        assertEquals(
            tr.doc,
            builder.doc {
                h { }
                b { p { +"One" } }
            }
        )
    }

    @Test
    fun `doesn't fail when moving text would solve an unsatisfied content constraint`() {
        val s = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "title" to NodeSpecImpl("text*"),
                    "doc" to NodeSpecImpl("title? block*")
                )
            )
        )
        val tr = Transform(s.node("doc", null, s.node("title", null, s.text("hi"))))
        tr.replace(
            1,
            1,
            s.node(
                "bullet_list",
                null,
                listOf(
                    s.node("list_item", null, s.node("paragraph", null, s.text("one"))),
                    s.node("list_item", null, s.node("paragraph", null, s.text("two"))),
                )
            ).slice(2, 12)
        )
        assertThat(tr.steps.size > 0).isTrue()
    }

    @Test
    fun `doesn't fail when pasting a half-open slice with a title and a code block into an empty title`() {
        val s = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "title" to NodeSpecImpl("text*"),
                    "doc" to NodeSpecImpl("title? block*")
                )
            )
        )
        val tr = Transform(s.node("doc", null, s.node("title", null, emptyList())))
        tr.replace(
            1,
            1,
            s.node(
                "doc",
                null,
                listOf(
                    s.node("title", null, s.text("title")),
                    s.node("code_block", null, s.text("two"))
                )
            ).slice(1)
        )
        assertThat(tr.steps.size > 0).isTrue()
    }

    @Test
    fun `doesn't fail when pasting a half-open slice with a heading and a code block into an empty title`() {
        val s = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "title" to NodeSpecImpl("text*"),
                    "doc" to NodeSpecImpl("title? block*")
                )
            )
        )
        val tr = Transform(s.node("doc", null, listOf(s.node("title"))))
        tr.replace(
            1,
            1,
            s.node(
                "doc",
                null,
                listOf(
                    s.node("heading", mapOf("level" to 1), s.text("heading")),
                    s.node("code_block", null, s.text("code"))
                )
            ).slice(1)
        )
        assertThat(tr.steps.size > 0).isTrue()
    }

    @Test
    fun `can handle replacing in nodes with fixed content`() {
        val s = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl("block+"),
                    "a" to NodeSpecImpl("inline*"),
                    "b" to NodeSpecImpl("inline*"),
                    "block" to NodeSpecImpl("a b"),
                    "text" to NodeSpecImpl(group = "inline")
                )
            )
        )

        val doc = s.node(
            "doc",
            null,
            listOf(
                s.node(
                    "block",
                    null,
                    listOf(
                        s.node("a", null, listOf(s.text("aa"))),
                        s.node("b", null, listOf(s.text("bb")))
                    )
                )
            )
        )
        val from = 3
        val to = doc.content.size
        val tr = Transform(doc).replace(from, to, doc.slice(from, to))
        assertEquals(tr.doc, doc)
    }

    @Test
    fun `keeps isolating nodes together`() {
        val s = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "iso" to NodeSpecImpl(
                        group = "block",
                        content = "block+",
                        isolating = true
                    )
                )
            )
        )
        val doc = s.node("doc", null, listOf(s.node("paragraph", null, listOf(s.text("one")))))
        val iso = Fragment.from(s.node("iso", null, listOf(s.node("paragraph", null, listOf(s.text("two"))))))
        assertEquals(
            Transform(doc).replace(2, 3, Slice(iso, 2, 0)).doc,
            s.node(
                "doc",
                null,
                listOf(
                    s.node("paragraph", null, listOf(s.text("o"))),
                    s.node("iso", null, listOf(s.node("paragraph", null, listOf(s.text("two"))))),
                    s.node("paragraph", null, listOf(s.text("e")))
                )
            )
        )
        assertEquals(
            Transform(doc).replace(2, 3, Slice(iso, 2, 2)).doc,
            s.node("doc", null, listOf(s.node("paragraph", null, listOf(s.text("otwoe")))))
        )
    }
    // endregion

    // region replaceRange
    private fun replRange(
        doc: Node,
        source: Node,
        expect: Node,
        useSourceFirstChild: Boolean = false,
        pos: (Node, String) -> Int? = PMNodeBuilder.Companion::pos
    ) {
        val theSource = if (useSourceFirstChild) {
            source.firstChild!!
        } else {
            source
        }
        val sourceOffset = if (useSourceFirstChild) -1 else 0
        val slice = theSource.slice(pos(source, "a")!! + sourceOffset, pos(source, "b")!! + sourceOffset, true)
        testTransform(
            Transform(doc).replaceRange(pos(doc, "a")!!, pos(doc, "b") ?: pos(doc, "a")!!, slice),
            expect
        )
    }

    @Test
    fun `replaces inline content`() = replRange(
        doc { p { +"foo<a>b<b>ar" } },
        doc { p { +"<a>xx<b>" } },
        doc { p { +"foo<a>xx<b>ar" } },
        useSourceFirstChild = true
    )

    @Test
    fun `replaces an empty paragraph with a heading`() = replRange(
        doc { p { +"<a>" } },
        doc { h1 { +"<a>text<b>" } },
        doc { h1 { +"text" } }
    )

    @Test
    fun `replaces a fully selected paragraph with a heading`() = replRange(
        doc { p { +"<a>abc<b>" } },
        doc { h1 { +"<a>text<b>" } },
        doc { h1 { +"text" } }
    )

    @Test
    fun `recreates a list when overwriting a paragraph`() = replRange(
        doc { p { +"<a>" } },
        doc { ul { li { p { +"<a>foobar<b>" } } } },
        doc { ul { li { p { +"foobar" } } } }
    )

    @Test
    fun `drops context when it doesn't fit`() = replRange(
        doc { ul { li { p { +"<a>" } } + li { p { +"b" } } } },
        doc { h1 { +"<a>h<b>" } },
        doc { ul { li { p { +"h<a>" } } + li { p { +"b" } } } }
    )

    @Test
    fun `can replace a node when endpoints are in different children`() = replRange(
        doc {
            p { +"a" } +
                ul { li { p { +"<a>b" } } + li { p { +"c" } + blockquote { p { +"d<b>" } } } } +
                p { +"e" }
        },
        doc { h1 { +"<a>x<b>" } },
        doc { p { +"a" } + h1 { +"x" } + p { +"e" } }
    )

    @Test
    fun `keeps defining context when inserting at the start of a textblock`() = replRange(
        doc { p { +"<a>foo" } },
        doc { ul { li { p { +"<a>one" } } + li { p { +"two<b>" } } } },
        doc { ul { li { p { +"one" } } + li { p { +"twofoo" } } } }
    )

    @Test
    fun `keeps defining context when it doesn't matches the parent markup`() {
        val blockquoteSchema = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "blockquote" to NodeSpecImpl(
                        content = "block+",
                        group = "block",
                        definingForContent = true,
                        definingAsContext = false,
                        attrs = mapOf("color" to AttributeSpecImpl("color", "black"))
                    )
                ),
                marks = schema.spec.marks
            )
        )

        fun NodeBuilder<CustomNodeBuilder>.b1(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            node("blockquote", func, mapOf("color" to "#100"))
        fun NodeBuilder<CustomNodeBuilder>.b2(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            node("blockquote", func, mapOf("color" to "#200"))
        fun NodeBuilder<CustomNodeBuilder>.b3(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            node("blockquote", func, mapOf("color" to "#300"))
        fun NodeBuilder<CustomNodeBuilder>.b4(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            node("blockquote", func, mapOf("color" to "#400"))
        fun NodeBuilder<CustomNodeBuilder>.b5(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            node("blockquote", func, mapOf("color" to "#500"))
        fun NodeBuilder<CustomNodeBuilder>.b6(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            node("blockquote", func, mapOf("color" to "#600"))

        val builder = CustomNodeBuildCompanion(blockquoteSchema)
        val source = builder.doc {
            b1 { p { +"<a>b1" } }
            b2 { p { +"b2<b>" } }
        }

        val before1 = builder.doc {
            b3 { p { +"b3" } }
            b4 { p { +"<a>" } }
        }
        val before2 = builder.doc {
            b5 { p { +"b5" } }
            b3 { p { +"b3" } }
            b4 { p { +"<a>" } }
        }
        val before3 = builder.doc {
            b6 { p { +"b6" } }
            b5 { p { +"b5" } }
            b3 { p { +"b3" } }
            b4 { p { +"<a>" } }
        }

        val expect1 = builder.doc {
            b3 { p { +"b3" } }
            b1 { p { +"b1" } }
            b2 { p { +"b2" } }
        }
        val expect2 = builder.doc {
            b5 { p { +"b5" } }
            b3 { p { +"b3" } }
            b1 { p { +"b1" } }
            b2 { p { +"b2" } }
        }
        val expect3 = builder.doc {
            b6 { p { +"b6" } }
            b5 { p { +"b5" } }
            b3 { p { +"b3" } }
            b1 { p { +"b1" } }
            b2 { p { +"b2" } }
        }

        replRange(before1, source, expect1, pos = builder::pos)
        replRange(before2, source, expect2, pos = builder::pos)
        replRange(before3, source, expect3, pos = builder::pos)
    }

    @Test
    fun `drops defining context when it matches the parent structure`() = replRange(
        doc { blockquote { p { +"<a>" } } },
        doc { blockquote { p { +"<a>one<b>" } } },
        doc { blockquote { p { +"one" } } }
    )

    @Test
    fun `drops defining context when it matches the parent structure in a nested context`() = replRange(
        doc { ul { li { p { +"list1" } + blockquote { p { +"<a>" } } } } },
        doc { blockquote { p { +"<a>one<b>" } } },
        doc { ul { li { p { +"list1" } + blockquote { p { +"one" } } } } }
    )

    @Test
    fun `drops defining context when it matches the parent structure in a deep nested context`() = replRange(
        doc { ul { li { p { +"list1" } + ul { li { p { +"list2" } + blockquote { p { +"<a>" } } } } } } },
        doc { blockquote { p { +"<a>one<b>" } } },
        doc { ul { li { p { +"list1" } + ul { li { p { +"list2" } + blockquote { p { +"one" } } } } } } }
    )

    @Test
    fun `closes open nodes at the start`() = replRange(
        doc { +"<a>" + p { +"abc" } + "<b>" },
        doc { ul { li { +"<a>" } } + p { +"def" } + "<b>" },
        doc { ul { li { p {} } } + p { +"def" } }
    )
    // endregion

    // region replaceRangeWith
    private fun replRangeWith(doc: Node, node: Node, expect: Node) {
        testTransform(
            Transform(doc).replaceRangeWith(pos(doc, "a")!!, pos(doc, "b") ?: pos(doc, "a")!!, node),
            expect
        )
    }

    @Test
    fun `can insert an inline node`() = replRangeWith(
        doc { p { +"fo<a>o" } },
        doc { img {} }.firstChild!!,
        doc { p { +"fo" + img {} + "<a>o" } }
    )

    @Test
    fun `can replace content with an inline node`() = replRangeWith(
        doc { p { +"<a>fo<b>o" } },
        doc { img {} }.firstChild!!,
        doc { p { +"<a>" + img {} + "o" } }
    )

    @Test
    fun `can replace a block node with an inline node`() = replRangeWith(
        doc { +"<a>" + blockquote { p { +"a" } } + "<b>" },
        doc { img {} }.firstChild!!,
        doc { p { img {} } }
    )

    @Test
    fun `can replace a block node with a block node`() = replRangeWith(
        doc { +"<a>" + blockquote { p { +"a" } } + "<b>" },
        doc { hr {} }.firstChild!!,
        doc { hr {} }
    )

    @Test
    fun `can insert a block quote in the middle of text`() = replRangeWith(
        doc { p { +"foo<a>bar" } },
        doc { hr {} }.firstChild!!,
        doc { p { +"foo" } + hr {} + p { +"bar" } }
    )

    @Test
    fun `can replace empty parents with a block node`() = replRangeWith(
        doc { blockquote { p { +"<a>" } } },
        doc { hr {} }.firstChild!!,
        doc { blockquote { hr {} } }
    )

    @Test
    fun `can move an inserted block forward out of parent nodes`() = replRangeWith(
        doc { h1 { +"foo<a>" } },
        doc { hr {} }.firstChild!!,
        doc { h1 { +"foo" } + hr {} }
    )

    @Test
    fun `can move an inserted block backward out of parent nodes`() = replRangeWith(
        doc { p { +"a" } + blockquote { p { +"<a>b" } } },
        doc { hr {} }.firstChild!!,
        doc { p { +"a" } + blockquote { hr {} + p { +"b" } } }
    )
    // endregion

    // region deleteRange
    private fun delRange(doc: Node, expect: Node) {
        testTransform(
            Transform(doc).deleteRange(pos(doc, "a")!!, pos(doc, "b") ?: pos(doc, "a")!!),
            expect
        )
    }

    @Test
    fun `deletes the given range`() = delRange(
        doc { p { +"fo<a>o" } + p { +"b<b>ar" } },
        doc { p { +"fo<a><b>ar" } }
    )

    @Test
    fun `deletes empty parent nodes`() = delRange(
        doc { blockquote { ul { li { +"<a>" + p { +"foo" } + "<b>" } } + p { +"x" } } },
        doc { blockquote { +"<a><b>" + p { +"x" } } }
    )

    @Test
    fun `doesn't delete parent nodes that can be empty`() = delRange(
        doc { p { +"<a>foo<b>" } },
        doc { p { +"<a><b>" } }
    )

    @Test
    fun `is okay with deleting empty ranges`() = delRange(
        doc { p { +"<a><b>" } },
        doc { p { +"<a><b>" } }
    )

    @Test
    fun `will delete a whole covered node even if selection ends are in different nodes`() = delRange(
        doc { ul { li { p { +"<a>foo" } } + li { p { +"bar<b>" } } } + p { +"hi" } },
        doc { p { +"hi" } }
    )

    @Test
    fun `leaves wrapping textblock when deleting all text in it`() = delRange(
        doc { p { +"a" } + p { +"<a>b<b>" } },
        doc { p { +"a" } + p {} }
    )

    @Test
    fun `expands to cover the whole parent node`() = delRange(
        doc { p { +"a" } + blockquote { blockquote { p { +"<a>foo" } } + p { +"bar<b>" } } + p { +"b" } },
        doc { p { +"a" } + p { +"b" } }
    )

    @Test
    fun `expands to cover the whole document`() = delRange(
        doc { h1 { +"<a>foo" } + p { +"bar" } + blockquote { p { +"baz<b>" } } },
        doc { p {} }
    )

    @Test
    fun `doesn't expand beyond same-depth textblocks`() = delRange(
        doc { h1 { +"<a>foo" } + p { +"bar" } + p { +"baz<b>" } },
        doc { h1 {} }
    )

    @Test
    fun `deletes the open token when deleting from start to past end of block`() = delRange(
        doc { h1 { +"<a>foo" } + p { +"b<b>ar" } },
        doc { p { +"ar" } }
    )

    @Test
    fun `doesn't delete the open token when the range end is at end of its own block`() = delRange(
        doc { p { +"one" } + h1 { +"<a>two" } + blockquote { p { +"three<b>" } } + p { +"four" } },
        doc { p { +"one" } + h1 {} + p { +"four" } }
    )

    @Test
    fun `doesn't break text-joining by inappropriate expansion`() = delRange(
        doc { ol { li { p { +"<a>One" } + ol { li { p { +"Tw<b>o" } } } } } },
        doc { ol { li { p { +"o" } } } }
    )
    // endregion

    // region addNodeMark
    private fun addNodeMark(doc: Node, mark: Mark, expect: Node) {
        testTransform(Transform(doc).addNodeMark(pos(doc, "a")!!, mark), expect)
    }

    @Test
    fun `adds a mark`() = addNodeMark(
        doc { p { +"<a>" + img {} } },
        schema.mark("em"),
        doc { p { +"<a>" + em { img {} } } }
    )

    @Test
    fun `doesn't duplicate a mark`() = addNodeMark(
        doc { p { +"<a>" + em { img {} } } },
        schema.mark("em"),
        doc { p { +"<a>" + em { img {} } } }
    )

    @Test
    fun `replaces a mark`() = addNodeMark(
        doc { p { +"<a>" + a { img {} } } },
        schema.mark("link", mapOf("href" to "x")),
        doc { p { +"<a>" + a("x") { img {} } } }
    )
    // endregion

    // region removeNodeMark
    private fun rm(doc: Node, mark: Mark, expect: Node) {
        testTransform(Transform(doc).removeNodeMark(pos(doc, "a")!!, mark), expect)
    }

    @Test
    fun `removes a mark`() = rm(
        doc { p { +"<a>" + em { img {} } } },
        schema.mark("em"),
        doc { p { +"<a>" + img {} } }
    )

    @Test
    fun `doesn't do anything when there is no mark`() = rm(
        doc { p { +"<a>" + img {} } },
        schema.mark("em"),
        doc { p { +"<a>" + img {} } }
    )

    @Test
    fun `can remove a mark from multiple marks`() = rm(
        doc { p { +"<a>" + em { a { img {} } } } },
        schema.mark("em"),
        doc { p { +"<a>" + a { img {} } } }
    )
    // endregion

    // region setNodeAttribute
    private fun setNodeAttribute(doc: Node, attr: String, value: Any, expect: Node) {
        testTransform(Transform(doc).setNodeAttribute(pos(doc, "a")!!, attr, value), expect)
    }

    @Test
    fun `setNodeAttribute - sets an attribute`() = setNodeAttribute(
        doc { +"<a>" + h1 { +"a" } },
        "level",
        2,
        doc { +"<a>" + h2 { +"a" } }
    )
    // endregion

    // region setDocAttribute
    private fun setDocAttribute(doc: Node, attr: String, value: Any, expect: Node) {
        testTransform(Transform(doc).setDocAttribute(attr, value), expect)
    }

    @Test
    fun `setDocAttribute - sets an attribute`() {
        val schema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(
                        "text*",
                        attrs = mapOf("meta" to AttributeSpecImpl(null))
                    ),
                    "text" to NodeSpecImpl()
                )
            )
        )
        val builder = CustomNodeBuildCompanion(schema)

        setDocAttribute(
            builder.doc { },
            "meta",
            "hello",
            builder.doc {}.copy(attrs = mapOf("meta" to "hello"))
        )
    }
    // endregion

    companion object {
        // A schema that enforces a heading and a body at the top level
        private val hbSchema = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "doc" to (schema.spec.nodes["doc"] as NodeSpecImpl).copy(content = "heading body"),
                    "body" to NodeSpecImpl("block+")
                )
            )
        )

        val linebreakSchema = Schema(
            SchemaSpec(
                nodes = schema.spec.nodes + mapOf(
                    "hard_break" to (schema.spec.nodes["hard_break"]!! as NodeSpecImpl)
                        .copy(linebreakReplacement = true)
                )
            )
        )
    }
}
