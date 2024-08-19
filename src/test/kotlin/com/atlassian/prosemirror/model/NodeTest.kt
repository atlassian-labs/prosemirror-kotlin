@file:Suppress("MaxLineLength", "ThrowsCount")

package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.br
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.p
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.schema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import kotlin.test.BeforeTest
import kotlin.test.Test

// import ist from "ist";
// import { Fragment, Schema } from "prosemirror-model";
// import { schema, eq, doc, blockquote, p, li, ul, em, strong, code, a, br, hr, img } from "prosemirror-test-builder";
// let customSchema = new Schema({
//     nodes: {
//         doc: { content: "paragraph+" },
//         paragraph: { content: "(text|contact)*" },
//         text: { toDebugString() { return 'custom_text'; } },
//         contact: {
//         inline: true,
//         attrs: { name: {}, email: {} },
//         leafText(node) { return `${node.attrs.name} <${node.attrs.email}>`; }
//     },
//         hard_break: { toDebugString() { return 'custom_hard_break'; } }
//     },
// });
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
        "name" to AttributeSpecImpl(),
        "email" to AttributeSpecImpl()
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
    PMNodeBuilder.clean()
  }

  // describe("Node", () => {
//  region toString
//     describe("toString", () => {
//         it("nests", () => {
//             ist(doc(ul(li(p("hey"), p()), li(p("foo")))).toString(), 'doc(bullet_list(list_item(paragraph("hey"), paragraph), list_item(paragraph("foo"))))');
//         });
//         it("shows inline children", () => {
//             ist(doc(p("foo", img(), br(), "bar")).toString(), 'doc(paragraph("foo", image, hard_break, "bar"))');
//         });
//         it("shows marks", () => {
//             ist(doc(p("foo", em("bar", strong("quux")), code("baz"))).toString(), 'doc(paragraph("foo", em("bar"), em(strong("quux")), code("baz")))');
//         });
//     });
// endregion
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

  // region cut
//    describe("cut", () => {
//        function cut(doc, cut) {
//        ist(doc.cut(doc.tag.a || 0, doc.tag.b), cut, eq);
//    }
//        it("extracts a full block", () => cut(doc(p("foo"), "<a>", p("bar"), "<b>", p("baz")), doc(p("bar"))));
//        it("cuts text", () => cut(doc(p("0"), p("foo<a>bar<b>baz"), p("2")), doc(p("bar"))));
//        it("cuts deeply", () => cut(doc(blockquote(ul(li(p("a"), p("b<a>c")), li(p("d")), "<b>", li(p("e"))), p("3"))), doc(blockquote(ul(li(p("c")), li(p("d")))))));
//        it("works from the left", () => cut(doc(blockquote(p("foo<b>bar"))), doc(blockquote(p("foo")))));
//        it("works to the right", () => cut(doc(blockquote(p("foo<a>bar"))), doc(blockquote(p("bar")))));
//        it("preserves marks", () => cut(doc(p("foo", em("ba<a>r", img(), strong("baz"), br()), "qu<b>ux", code("xyz"))), doc(p(em("r", img(), strong("baz"), br()), "qu"))));
//    });
// endregion
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

  // region between
//    describe("between", () => {
//        function between(doc, ...nodes) {
//        let i = 0;
//        doc.nodesBetween(doc.tag.a, doc.tag.b, (node, pos) => {
//        if (i == nodes.length)
//            throw new Error("More nodes iterated than listed (" + node.type.name + ")");
//        let compare = node.isText ? node.text : node.type.name;
//        if (compare != nodes[i++])
//            throw new Error("Expected " + JSON.stringify(nodes[i - 1]) + ", got " + JSON.stringify(compare));
//        if (!node.isText && doc.nodeAt(pos) != node)
//            throw new Error("Pos " + pos + " does not point at node " + node + " " + doc.nodeAt(pos));
//    });
//    }
//        it("iterates over text", () => between(doc(p("foo<a>bar<b>baz")), "paragraph", "foobarbaz"));
//        it("descends multiple levels", () => between(doc(blockquote(ul(li(p("f<a>oo")), p("b"), "<b>"), p("c"))), "blockquote", "bullet_list", "list_item", "paragraph", "foo", "paragraph", "b"));
//        it("iterates over inline nodes", () => between(doc(p(em("x"), "f<a>oo", em("bar", img(), strong("baz"), br()), "quux", code("xy<b>z"))), "paragraph", "foo", "bar", "image", "baz", "hard_break", "quux", "xyz"));
//    });
// endregion
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

  // region textBetween
//    describe("textBetween", () => {
//        it("works when passing a custom function as leafText", () => {
//            const d = doc(p("foo", img(), br()));
//            ist(d.textBetween(0, d.content.size, '', (node) => {
//                if (node.type.name === 'image')
//                    return '<image>';
//                if (node.type.name === 'hard_break')
//                    return '<break>';
//                return "";
//            }), 'foo<image><break>');
//        });
//        it("works with leafText", () => {
//            const d = customSchema.nodes.doc.createChecked({}, [
//            customSchema.nodes.paragraph.createChecked({}, [
//                customSchema.text("Hello "),
//                customSchema.nodes.contact.createChecked({ name: "Alice", email: "alice@example.com" })
//            ])
//            ]);
//            ist(d.textBetween(0, d.content.size), 'Hello Alice <alice@example.com>');
//        });
//        it("should ignore leafText when passing a custom leafText", () => {
//            const d = customSchema.nodes.doc.createChecked({}, [
//            customSchema.nodes.paragraph.createChecked({}, [
//                customSchema.text("Hello "),
//                customSchema.nodes.contact.createChecked({ name: "Alice", email: "alice@example.com" })
//            ])
//            ]);
//            ist(d.textBetween(0, d.content.size, '', '<anonymous>'), 'Hello <anonymous>');
//        });
//    });
// endregion
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

  // region textContent
//    describe("textContent", () => {
//        it("works on a whole doc", () => {
//            ist(doc(p("foo")).textContent, "foo");
//        });
//        it("works on a text node", () => {
//            ist(schema.text("foo").textContent, "foo");
//        });
//        it("works on a nested element", () => {
//            ist(doc(ul(li(p("hi")), li(p(em("a"), "b")))).textContent, "hiab");
//        });
//    });
// endregion
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

  // region from
//    describe("from", () => {
//        function from(arg, expect) {
//        ist(expect.copy(Fragment.from(arg)), expect, eq);
//    }
//        it("wraps a single node", () => from(schema.node("paragraph"), doc(p())));
//        it("wraps an array", () => from([schema.node("hard_break"), schema.text("foo")], p(br, "foo")));
//        it("preserves a fragment", () => from(doc(p("foo")).content, doc(p("foo"))));
//        it("accepts null", () => from(null, p()));
//        it("joins adjacent text", () => from([schema.text("a"), schema.text("b")], p("ab")));
//    });
// endregion
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

  // region toJSON
//    describe("toJSON", () => {
//        function roundTrip(doc) {
//            ist(schema.nodeFromJSON(doc.toJSON()), doc, eq);
//        }
//        it("can serialize a simple node", () => roundTrip(doc(p("foo"))));
//        it("can serialize marks", () => roundTrip(doc(p("foo", em("bar", strong("baz")), " ", a("x")))));
//        it("can serialize inline leaf nodes", () => roundTrip(doc(p("foo", em(img(), "bar")))));
//        it("can serialize block leaf nodes", () => roundTrip(doc(p("a"), hr(), p("b"), p())));
//        it("can serialize nested nodes", () => roundTrip(doc(blockquote(ul(li(p("a"), p("b")), li(p(img()))), p("c")), p("d"))));
//    });
// endregion
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

  // region toString
//    describe("toString", () => {
//        it("should have the default toString method [text]", () => ist(schema.text("hello").toString(), "\"hello\""));
//        it("should have the default toString method [br]", () => ist(br().toString(), "hard_break"));
//        it("should be able to redefine it from NodeSpec by specifying toDebugString method", () => ist(customSchema.text("hello").toString(), "custom_text"));
//        it("should be respected by Fragment", () => ist(Fragment.fromArray([customSchema.text("hello"), customSchema.nodes.hard_break.createChecked(), customSchema.text("world")]), "<custom_text, custom_hard_break, custom_text>"));
//    });
// endregion
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

  // region leafText
//    describe("leafText", () => {
//        it("should custom the textContent of a leaf node", () => {
//            let contact = customSchema.nodes.contact.createChecked({ name: "Bob", email: "bob@example.com" });
//            let paragraph = customSchema.nodes.paragraph.createChecked({}, [customSchema.text('Hello '), contact]);
//            ist(contact.textContent, "Bob <bob@example.com>");
//            ist(paragraph.textContent, "Hello Bob <bob@example.com>");
//        });
//    });
// endregion
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
// });
}
