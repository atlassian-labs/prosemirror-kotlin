@file:Suppress("MaxLineLength", "ThrowsCount")

package com.atlassian.prosemirror.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.br
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.p
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.schema
import com.atlassian.prosemirror.util.verbose
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.fail

val customSchemaSpec = SchemaSpec(
    nodes = mapOf(
        "doc" to NodeSpecImpl(content = "paragraph+"),
        "paragraph" to NodeSpecImpl(content = "(text|contact)*"),
        "text" to NodeSpecImpl(
            toDebugString = { "custom_text" }
        ),
        "contact" to NodeSpecImpl(
            inline = true,
            attrs = mapOf(
                "name" to AttributeSpecImpl("default"),
                "email" to AttributeSpecImpl() // no default value intentional
            ),
            leafText = { node -> "${node.attr<String>("name")} <${node.attr<String>("email")}>" }
        ),
        "hard_break" to NodeSpecImpl(
            toDebugString = { "custom_hard_break" }
        )
    )
)
private val customSchema = Schema(customSchemaSpec)

class NodeTest {
    @BeforeTest
    fun beforeTest() {
        verbose = true
        PMNodeBuilder.clean()
    }

    @Test
    fun nests() {
        assertThat(
            doc { ul { li { p { +"hey" } + p {} } + li { p { +"foo" } } } }.toString()
        ).isEqualTo(
            """doc{bullet_list{list_item{paragraph{"hey"}, paragraph}, list_item{paragraph{"foo"}}}}"""
        )
    }

    @Test
    fun `shows inline children`() {
        assertThat(
            doc { p { +"foo" + img {} + br {} + "bar" } }.toString()
        ).isEqualTo(
            """doc{paragraph{"foo", image(src=img.png), hard_break, "bar"}}"""
        )
    }

    @Test
    fun `shows marks`() {
        assertThat(
            doc { p { +"foo" + em { +"bar" + strong { +"quux" } } + code { +"baz" } } }.toString()
        ).isEqualTo(
            """doc{paragraph{"foo", em("bar"), em(strong("quux")), code("baz")}}"""
        )
    }

    fun cut(doc: Node, cut: Node) {
        assertThat(doc.cut(pos("a") ?: 0, pos("b"))).isEqualTo(cut)
    }

    @Test
    fun `extracts a full block`() =
        cut(
            doc { p { +"foo" } + "<a>" + p { +"bar" } + "<b>" + p { +"baz" } },
            doc { p { +"bar" } }
        )

    @Test
    fun `cuts text`() =
        cut(
            doc { p { +"0" } + p { +"foo<a>bar<b>baz" } + p { +"2" } },
            doc { p { +"bar" } }
        )

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `cuts deeply`() =
        cut(
            doc {
                blockquote {
                    ul { li { p { +"a" } + p { +"b<a>c" } } + li { p { +"d" } } + "<b>" + li { p { +"e" } } } + p { +"3" }
                }
            },
            doc { blockquote { ul { li { p { +"c" } } + li { p { +"d" } } } } }
        )

    @Test
    fun `works from the left`() =
        cut(
            doc { blockquote { p { +"foo<b>bar" } } },
            doc { blockquote { p { +"foo" } } }
        )

    @Test
    fun `works to the right`() =
        cut(
            doc { blockquote { p { +"foo<a>bar" } } },
            doc { blockquote { p { +"bar" } } }
        )

    @Test
    fun `preserves marks`() =
        cut(
            doc { p { +"foo" + em { +"ba<a>r" + img {} + strong { +"baz" } + br {} } + "qu<b>ux" + code { +"xyz" } } },
            doc { p { em { +"r" + img {} + strong { +"baz" } + br {} } + "qu" } }
        )

    fun between(doc: Node, vararg nodes: String) {
        var i = 0
        doc.nodesBetween(pos("a")!!, pos("b")!!, { node, pos, parent, index ->
            if (i == nodes.size) {
                fail("More nodes iterated than listed (${node.type.name})")
            }
            val compare = if (node.isText) node.text else node.type.name
            if (compare != nodes[i++]) {
                fail("Expected " + Json.encodeToString(nodes[i - 1]) + ", got " + Json.encodeToString(compare))
            }
            if (!node.isText && doc.nodeAt(pos) != node) {
                fail("Pos " + pos + " does not point at node " + node + " " + doc.nodeAt(pos))
            }
            false
        })
    }

    @Test
    fun `iterates over text`() = between(doc { p { +"foo<a>bar<b>baz" } }, "paragraph", "foobarbaz")

    @Test
    fun `descends multiple levels`() = between(
        doc { blockquote { ul { li { p { +"f<a>oo" } } + p { +"b" } + "<b>" } + p { +"c" } } },
        "blockquote",
        "bullet_list",
        "list_item",
        "paragraph",
        "foo",
        "paragraph",
        "b"
    )

    @Test
    fun `iterates over inline nodes`() = between(
        doc {
            p {
                em { +"x" } + "f<a>oo" + em { +"bar" + img {} + strong { +"baz" } + br {} } + "quux" +
                    code { +"xy<b>z" }
            }
        },
        "paragraph",
        "foo",
        "bar",
        "image",
        "baz",
        "hard_break",
        "quux",
        "xyz"
    )

    @Test
    fun `works with leafText`() {
        val d = customSchema.nodes["doc"]!!.createChecked(
            emptyMap(),
            listOf(
                customSchema.nodes["paragraph"]!!.createChecked(
                    emptyMap(),
                    listOf(
                        customSchema.text("Hello "),
                        customSchema.nodes["contact"]!!.createChecked(
                            mapOf(
                                "name" to "Alice",
                                "email" to "alice@example.com"
                            )
                        )
                    )
                )
            )
        )
        assertThat(d.textBetween(0, d.content.size)).isEqualTo("Hello Alice <alice@example.com>")
    }

    @Test
    fun `should ignore leafText when passing a custom leafText`() {
        val d = customSchema.nodes["doc"]!!.createChecked(
            emptyMap(),
            listOf(
                customSchema.nodes["paragraph"]!!.createChecked(
                    emptyMap(),
                    listOf(
                        customSchema.text("Hello "),
                        customSchema.nodes["contact"]!!.createChecked(
                            mapOf(
                                "name" to "Alice",
                                "email" to "alice@example.com"
                            )
                        )
                    )
                )
            )
        )
        assertThat(d.textBetween(0, d.content.size, "", "<anonymous>")).isEqualTo("Hello <anonymous>")
    }
    // TODO: convert the following tests
//    it("adds block separator around empty paragraphs", () => {
//        ist(doc(p("one"), p(), p("two")).textBetween(0, 12, "\n"), "one\n\ntwo")
//    })
//
//    it("adds block separator around leaf nodes", () => {
//        ist(doc(p("one"), hr(), hr(), p("two")).textBetween(0, 12, "\n", "---"), "one\n---\n---\ntwo")
//    })
//
//    it("doesn't add block separator around non-rendered leaf nodes", () => {
//        ist(doc(p("one"), hr(), hr(), p("two")).textBetween(0, 12, "\n"), "one\ntwo")
//    })

    @Test
    fun `works on a whole doc`() {
        assertThat(doc { p { +"foo" } }.textContent).isEqualTo("foo")
    }

    @Test
    fun `works on a text node`() {
        assertThat(schema.text("foo").textContent).isEqualTo("foo")
    }

    @Test
    fun `works on a nested element`() {
        assertThat(doc { ul { li { p { +"hi" } } + li { p { em { +"a" } + "b" } } } }.textContent).isEqualTo("hiab")
    }

    // TODO: convert the following tests
//    describe("check", () => {
//        it("notices invalid content", () => {
//            ist.throws(() => doc(li("x")).check(),
//            /Invalid content for node doc/)
//        })
//
//        it("notices marks in wrong places", () => {
//            ist.throws(() => doc(schema.nodes.paragraph.create(null, [], [schema.marks.em.create()])).check(),
//            /Invalid content for node doc/)
//        })
//
//        it("notices incorrect sets of marks", () => {
//            ist.throws(() => schema.text("a", [schema.marks.em.create(), schema.marks.em.create()]).check(),
//            /Invalid collection of marks/)
//        })
//
//        it("notices wrong attribute types", () => {
//            ist.throws(() => schema.nodes.image.create({src: true}).check(),
//            /Expected value of type string for attribute src on type image, got boolean/)
//        })
//    })

    fun from(arg: Node, expect: Node) {
        assertThat(expect.copy(Fragment.from(arg))).isEqualTo(expect)
    }

    fun from(arg: List<Node>, expect: Node) {
        assertThat(expect.copy(Fragment.from(arg))).isEqualTo(expect)
    }

    fun from(arg: Fragment?, expect: Node) {
        assertThat(expect.copy(Fragment.from(arg))).isEqualTo(expect)
    }

    @Test
    fun `wraps a single node`() = from(schema.node("paragraph"), doc { p {} })

    @Test
    fun `wraps an array`() = from(listOf(schema.node("hard_break"), schema.text("foo")), p { br {} + "foo" })

    @Test
    fun `preserves a fragment`() = from(doc { p { +"foo" } }.content, doc { p { +"foo" } })

    @Test
    fun `accepts null`() = from(null, p {})

    @Test
    fun `joins adjacent text`() = from(listOf(schema.text("a"), schema.text("b")), p { +"ab" })

    fun roundTrip(doc: Node) {
        val json = doc.toJSON()
        assertThat(schema.nodeFromJSON(json)).isEqualTo(doc)
    }

    @Test
    fun `can serialize a simple node`() = roundTrip(doc { p { +"foo" } })

    @Test
    fun `can serialize marks`() = roundTrip(doc { p { +"foo" + em { +"bar" + strong { +"baz" } } + " " + a { +"x" } } })

    @Test
    fun `can serialize inline leaf nodes`() = roundTrip(doc { p { +"foo" + em { img {} + "bar" } } })

    @Test
    fun `can serialize block leaf nodes`() = roundTrip(doc { p { +"a" } + hr {} + p { +"b" } + p {} })

    @Test
    fun `can serialize nested nodes`() =
        roundTrip(
            doc {
                blockquote {
                    ul { li { p { +"a" } + p { +"b" } } + li { p { img {} } } + p { +"c" } } + p { +"d" }
                }
            }
        )

    @Test
    fun `should have the default toString method - text`() {
        assertThat(schema.text("hello").toString()).isEqualTo("\"hello\"")
    }

    @Test
    fun `should have the default toString method - br`() {
        assertThat(br {}.toString()).isEqualTo("hard_break")
    }

    @Test
    fun `should be able to redefine it from NodeSpec by specifying toDebugString method`() {
        assertThat(customSchema.text("hello").toString()).isEqualTo("custom_text")
    }

    @Test
    fun `should be respected by Fragment`() {
        val nodes = listOf(
            customSchema.text("hello"),
            customSchema.nodes["hard_break"]!!.createChecked(),
            customSchema.text("world")
        )
        assertThat(Fragment.fromArray(nodes).toString()).isEqualTo("<custom_text, custom_hard_break, custom_text>")
    }

    @Test
    fun `should custom the textContent of a leaf node`() {
        val contact =
            customSchema.nodes["contact"]!!.createChecked(mapOf("name" to "Bob", "email" to "bob@example.com"))
        val paragraph = customSchema.nodes["paragraph"]!!.createChecked(
            emptyMap(),
            listOf(
                customSchema.text("Hello "),
                contact
            )
        )
        assertThat(contact.textContent).isEqualTo("Bob <bob@example.com>")
        assertThat(paragraph.textContent).isEqualTo("Hello Bob <bob@example.com>")
    }

    @Test
    fun `should use default if attr does not exist`() {
        val d = createContactTestDoc(mapOf(
            "email" to "alice@example.com"
        ))
        val contactNode = d.child(0).child(0)
        // Use default regardless of <T> being nullable
        assertThat(contactNode.attr<String>("name")).isEqualTo("default")
        assertThat(contactNode.attr<String?>("name")).isEqualTo("default")
    }

    @Test
    fun `maybe use default if attr is null`() {
        val d = createContactTestDoc(mapOf(
            "name" to null,
            "email" to "alice@example.com"
        ))
        val contactNode = d.child(0).child(0)
        // If attr is null and default null, then only return null if we're asked for nullable - otherwise use
        // nodeSpec's default
        assertThat(contactNode.attr<String>("name")).isEqualTo("default")
        assertThat(contactNode.attr<String>("name", "default2")).isEqualTo("default2")
        assertThat(contactNode.attr<String?>("name")).isNull()
        assertThat(contactNode.attr<String?>("name", "default2")).isEqualTo("default2")
    }

    @Test
    fun `should use default if attr is wrong type`() {
        val d = createContactTestDoc(mapOf(
            "name" to 123,
            "email" to 123
        ))
        val contactNode = d.child(0).child(0)
        // Use default due to wrong type, or null if no default and <T> is nullable
        assertThat(contactNode.attr<String>("name")).isEqualTo("default")
        assertThat(contactNode.attr<String>("email", "default")).isEqualTo("default")
        assertThat(contactNode.attr<String?>("email")).isNull()
    }

    @Suppress("UnusedPrivateMember")
    @Test
    fun `throw IllegalArgumentException if default cannot be resolved`() {
        val d = createContactTestDoc(mapOf(
            "email" to null
        ))
        val contactNode = d.child(0).child(0)
        // If attr is null, default null, and nodeSpec default null, then we cannot return if <T> is not nullable
        val caughtException = assertFails("Expected a IllegalArgumentException") {
            contactNode.attr<String>("email")
        }
        assertThat(caughtException).isInstanceOf(IllegalArgumentException::class)
    }

    private fun createContactTestDoc(attrs: Map<String, Any?>) = customSchema.nodes["doc"]!!.createChecked(
        emptyMap(),
        listOf(
            customSchema.nodes["paragraph"]!!.createChecked(
                emptyMap(),
                listOf(
                    customSchema.nodes["contact"]!!.createChecked(attrs)
                )
            )
        )
    )
}
