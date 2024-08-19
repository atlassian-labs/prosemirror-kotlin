package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.schema
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.BeforeTest
import kotlin.test.Test

// import { Mark, Schema } from "prosemirror-model";
// import { schema, doc, p, em, a } from "prosemirror-test-builder";
// import ist from "ist";

// let em_ = schema.mark("em");
// let strong = schema.mark("strong");
// let link = (href, title) => schema.mark("link", { href, title });
// let code = schema.mark("code");
val em_ = schema.mark("em")
val strong = schema.mark("strong")
fun link(href: String, title: String? = null) =
    schema.mark("link", mapOf("href" to href, "title" to title))
val code = schema.mark("code")

// let customSchema = new Schema({
//     nodes: { doc: { content: "paragraph+" }, paragraph: { content: "text*" }, text: {} },
//     marks: {
//         remark: { attrs: { id: {} }, excludes: "", inclusive: false },
//         user: { attrs: { id: {} }, excludes: "_" },
//         strong: { excludes: "em-group" },
//         em: { group: "em-group" }
//     }
// }), custom = customSchema.marks;
private val customSchema = Schema(
    SchemaSpec(
        nodes = mapOf(
            "doc" to NodeSpecImpl(
                content = "paragraph+"
            ),
            "paragraph" to NodeSpecImpl(
                content = "text*"
            ),
            "text" to NodeSpecImpl()
        ),
        marks = mapOf(
            "remark" to MarkSpecImpl(
                attrs = mapOf("id" to AttributeSpecImpl()),
                excludes = "",
                inclusive = false
            ),
            "user" to MarkSpecImpl(
                attrs = mapOf("id" to AttributeSpecImpl()),
                excludes = "_"
            ),
            "strong" to MarkSpecImpl(
                excludes = "em-group"
            ),
            "em" to MarkSpecImpl(
                group = "em-group"
            )
        )
    )
)
val custom = customSchema.marks

// let remark1 = custom.remark.create({ id: 1 }), remark2 = custom.remark.create({ id: 2 }), user1 = custom.user.create({ id: 1 }), user2 = custom.user.create({ id: 2 }), customEm = custom.em.create(), customStrong = custom.strong.create();
val remark1 = custom["remark"]!!.create(mapOf("id" to 1))
val remark2 = custom["remark"]!!.create(mapOf("id" to 2))
val user1 = custom["user"]!!.create(mapOf("id" to 1))
val user2 = custom["user"]!!.create(mapOf("id" to 2))
val customEm = custom["em"]!!.create()
val customStrong = custom["strong"]!!.create()

class MarkTest {
    @BeforeTest
    fun beforeTest() {
        PMNodeBuilder.clean()
    }

// describe("Mark", () => {
// region sameSet
//    describe("sameSet", () => {
//        it("returns true for two empty sets", () => ist(Mark.sameSet([], [])));
//        it("returns true for simple identical sets", () => ist(Mark.sameSet([em_, strong], [em_, strong])));
//        it("returns false for different sets", () => ist(!Mark.sameSet([em_, strong], [em_, code])));
//        it("returns false when set size differs", () => ist(!Mark.sameSet([em_, strong], [em_, strong, code])));
//        it("recognizes identical links in set", () => ist(Mark.sameSet([link("http://foo"), code], [link("http://foo"), code])));
//        it("recognizes different links in set", () => ist(!Mark.sameSet([link("http://foo"), code], [link("http://bar"), code])));
//    });
// endregion
    @Test fun `returns true for two empty sets`() {
        assertThat(Mark.sameSet(emptyList(), emptyList())).isTrue
    }

    @Test fun `returns true for simple identical sets`() {
        assertThat(Mark.sameSet(listOf(em_, strong), listOf(em_, strong))).isTrue
    }

    @Test fun `returns false for different sets`() {
        assertThat(!Mark.sameSet(listOf(em_, strong), listOf(em_, code))).isTrue
    }

    @Test fun `returns false when set size differs`() {
        assertThat(!Mark.sameSet(listOf(em_, strong), listOf(em_, strong, code))).isTrue
    }

    @Test fun `recognizes identical links in set`() {
        assertThat(Mark.sameSet(listOf(link("http://foo"), code), listOf(link("http://foo"), code))).isTrue
    }

    @Test fun `recognizes different links in set`() {
        assertThat(!Mark.sameSet(listOf(link("http://foo"), code), listOf(link("http://bar"), code))).isTrue
    }

// region eq
//    describe("eq", () => {
//        it("considers identical links to be the same", () => ist(link("http://foo").eq(link("http://foo"))));
//        it("considers different links to differ", () => ist(!link("http://foo").eq(link("http://bar"))));
//        it("considers links with different titles to differ", () => ist(!link("http://foo", "A").eq(link("http://foo", "B"))));
//    });
// endregion
    @Test fun `considers identical links to be the same`() {
        assertThat(link("http://foo")).isEqualTo(link("http://foo"))
    }

    @Test fun `considers different links to differ`() {
        assertThat(link("http://foo")).isNotEqualTo(link("http://bar"))
    }

    @Test fun `considers links with different titles to differ`() {
        assertThat(link("http://foo", "A")).isNotEqualTo(link("http://foo", "B"))
    }

// region addToSet
//    describe("addToSet", () => {
//        it("can add to the empty set", () => ist(em_.addToSet([]), [em_], Mark.sameSet));
//        it("is a no-op when the added thing is in set", () => ist(em_.addToSet([em_]), [em_], Mark.sameSet));
//        it("adds marks with lower rank before others", () => ist(em_.addToSet([strong]), [em_, strong], Mark.sameSet));
//        it("adds marks with higher rank after others", () => ist(strong.addToSet([em_]), [em_, strong], Mark.sameSet));
//        it("replaces different marks with new attributes", () => ist(link("http://bar").addToSet([link("http://foo"), em_]), [link("http://bar"), em_], Mark.sameSet));
//        it("does nothing when adding an existing link", () => ist(link("http://foo").addToSet([em_, link("http://foo")]), [em_, link("http://foo")], Mark.sameSet));
//        it("puts code marks at the end", () => ist(code.addToSet([em_, strong, link("http://foo")]), [em_, strong, link("http://foo"), code], Mark.sameSet));
//        it("puts marks with middle rank in the middle", () => ist(strong.addToSet([em_, code]), [em_, strong, code], Mark.sameSet));
//        it("allows nonexclusive instances of marks with the same type", () => ist(remark2.addToSet([remark1]), [remark1, remark2], Mark.sameSet));
//        it("doesn't duplicate identical instances of nonexclusive marks", () => ist(remark1.addToSet([remark1]), [remark1], Mark.sameSet));
//        it("clears all others when adding a globally-excluding mark", () => ist(user1.addToSet([remark1, customEm]), [user1], Mark.sameSet));
//        it("does not allow adding another mark to a globally-excluding mark", () => ist(customEm.addToSet([user1]), [user1], Mark.sameSet));
//        it("does overwrite a globally-excluding mark when adding another instance", () => ist(user2.addToSet([user1]), [user2], Mark.sameSet));
//        it("doesn't add anything when another mark excludes the added mark", () => ist(customEm.addToSet([remark1, customStrong]), [remark1, customStrong], Mark.sameSet));
//        it("remove excluded marks when adding a mark", () => ist(customStrong.addToSet([remark1, customEm]), [remark1, customStrong], Mark.sameSet));
//    });
// endregion
    @Test fun `can add to the empty set`() {
        assertThat(Mark.sameSet(em_.addToSet(emptyList()), listOf(em_))).isTrue
    }

    @Test fun `is a no-op when the added thing is in set`() {
        assertThat(Mark.sameSet(em_.addToSet(listOf(em_)), listOf(em_))).isTrue
    }

    @Test fun `adds marks with lower rank before others`() {
        assertThat(Mark.sameSet(em_.addToSet(listOf(strong)), listOf(em_, strong))).isTrue
    }

    @Test fun `adds marks with higher rank after others`() {
        assertThat(Mark.sameSet(strong.addToSet(listOf(em_)), listOf(em_, strong))).isTrue
    }

    @Test fun `replaces different marks with new attributes`() {
        assertThat(
            Mark.sameSet(
                link("http://bar").addToSet(listOf(link("http://foo"), em_)),
                listOf(link("http://bar"), em_)
            )
        ).isTrue
    }

    @Test fun `does nothing when adding an existing link`() {
        assertThat(
            Mark.sameSet(
                link("http://foo").addToSet(listOf(em_, link("http://foo"))),
                listOf(em_, link("http://foo"))
            )
        ).isTrue
    }

    @Test fun `puts code marks at the end`() {
        assertThat(
            Mark.sameSet(
                code.addToSet(listOf(em_, strong, link("http://foo"))),
                listOf(em_, strong, link("http://foo"), code)
            )
        ).isTrue
    }

    @Test fun `puts marks with middle rank in the middle`() {
        assertThat(Mark.sameSet(strong.addToSet(listOf(em_, code)), listOf(em_, strong, code))).isTrue
    }

    @Test fun `allows nonexclusive instances of marks with the same type`() {
        assertThat(Mark.sameSet(remark2.addToSet(listOf(remark1)), listOf(remark1, remark2))).isTrue
    }

    @Test fun `doesn't duplicate identical instances of nonexclusive marks`() {
        assertThat(Mark.sameSet(remark1.addToSet(listOf(remark1)), listOf(remark1))).isTrue
    }

    @Test fun `clears all others when adding a globally-excluding mark`() {
        assertThat(Mark.sameSet(user1.addToSet(listOf(remark1, customEm)), listOf(user1))).isTrue
    }

    @Test fun `does not allow adding another mark to a globally-excluding mark`() {
        assertThat(Mark.sameSet(customEm.addToSet(listOf(user1)), listOf(user1))).isTrue
    }

    @Test fun `does overwrite a globally-excluding mark when adding another instance`() {
        assertThat(Mark.sameSet(user2.addToSet(listOf(user1)), listOf(user2))).isTrue
    }

    @Test fun `doesn't add anything when another mark excludes the added mark`() {
        assertThat(Mark.sameSet(customEm.addToSet(listOf(remark1, customStrong)), listOf(remark1, customStrong))).isTrue
    }

    @Test fun `remove excluded marks when adding a mark`() {
        assertThat(Mark.sameSet(customStrong.addToSet(listOf(remark1, customEm)), listOf(remark1, customStrong))).isTrue
    }

// region removeFromSet
//    describe("removeFromSet", () => {
//        it("is a no-op for the empty set", () => ist(Mark.sameSet(em_.removeFromSet([]), [])));
//        it("can remove the last mark from a set", () => ist(Mark.sameSet(em_.removeFromSet([em_]), [])));
//        it("is a no-op when the mark isn't in the set", () => ist(Mark.sameSet(strong.removeFromSet([em_]), [em_])));
//        it("can remove a mark with attributes", () => ist(Mark.sameSet(link("http://foo").removeFromSet([link("http://foo")]), [])));
//        it("doesn't remove a mark when its attrs differ", () => ist(Mark.sameSet(link("http://foo", "title").removeFromSet([link("http://foo")]), [link("http://foo")])));
//    });
// endregion
    @Test fun `is a no-op for the empty set`() {
        assertThat(Mark.sameSet(em_.removeFromSet(emptyList()), emptyList())).isTrue
    }

    @Test fun `can remove the last mark from a set`() {
        assertThat(Mark.sameSet(em_.removeFromSet(listOf(em_)), emptyList())).isTrue
    }

    @Test fun `is a no-op when the mark isn't in the set`() {
        assertThat(Mark.sameSet(strong.removeFromSet(listOf(em_)), listOf(em_))).isTrue
    }

    @Test fun `can remove a mark with attributes`() {
        assertThat(
            Mark.sameSet(link("http://foo").removeFromSet(listOf(link("http://foo"))), emptyList())
        ).isTrue
    }

    @Test fun `doesn't remove a mark when its attrs differ`() {
        assertThat(
            Mark.sameSet(
                link("http://foo", "title").removeFromSet(listOf(link("http://foo"))),
                listOf(link("http://foo"))
            )
        ).isTrue
    }

// region ResolvedPos.marks
//    describe("ResolvedPos.marks", () => {
//        function isAt(doc, mark, result) {
//        ist(mark.isInSet(doc.resolve(doc.tag.a).marks()), result);
//    }
//        it("recognizes a mark exists inside marked text", () => isAt(doc(p(em("fo<a>o"))), em_, true));
//        it("recognizes a mark doesn't exist in non-marked text", () => isAt(doc(p(em("fo<a>o"))), strong, false));
//        it("considers a mark active after the mark", () => isAt(doc(p(em("hi"), "<a> there")), em_, true));
//        it("considers a mark inactive before the mark", () => isAt(doc(p("one <a>", em("two"))), em_, false));
//        it("considers a mark active at the start of the textblock", () => isAt(doc(p(em("<a>one"))), em_, true));
//        it("notices that attributes differ", () => isAt(doc(p(a("li<a>nk"))), link("http://baz"), false));
//        let customDoc = customSchema.node("doc", null, [
//        customSchema.node("paragraph", null, [
//            customSchema.text("one", [remark1, customStrong]), customSchema.text("two")
//        ]),
//        customSchema.node("paragraph", null, [
//            customSchema.text("one"), customSchema.text("two", [remark1]), customSchema.text("three", [remark1])
//        ]),
//        customSchema.node("paragraph", null, [
//            customSchema.text("one", [remark2]), customSchema.text("two", [remark1])
//        ])
//        ]);
//        it("omits non-inclusive marks at end of mark", () => ist(Mark.sameSet(customDoc.resolve(4).marks(), [customStrong])));
//        it("includes non-inclusive marks inside a text node", () => ist(Mark.sameSet(customDoc.resolve(3).marks(), [remark1, customStrong])));
//        it("omits non-inclusive marks at the end of a line", () => ist(Mark.sameSet(customDoc.resolve(20).marks(), [])));
//        it("includes non-inclusive marks between two marked nodes", () => ist(Mark.sameSet(customDoc.resolve(15).marks(), [remark1])));
//        it("excludes non-inclusive marks at a point where mark attrs change", () => ist(Mark.sameSet(customDoc.resolve(25).marks(), [])));
//    });
// });
// endregion
    fun isAt(doc: Node, mark: Mark, result: Boolean) {
        assertThat(mark.isInSet(doc.resolve(pos("a")!!).marks())).isEqualTo(result)
    }

    @Test
    fun `recognizes a mark exists inside marked text`() = isAt(doc { p { em { +"fo<a>o" } } }, em_, true)

    @Test
    fun `recognizes a mark doesn't exist in non-marked text`() = isAt(doc { p { em { +"fo<a>o" } } }, strong, false)

    @Test
    fun `considers a mark active after the mark`() = isAt(doc { p { em { +"hi" } + "<a> there" } }, em_, true)

    @Test
    fun `considers a mark inactive before the mark`() = isAt(doc { p { +"one <a>" + em { +"two" } } }, em_, false)

    @Test
    fun `considers a mark active at the start of the textblock`() = isAt(doc { p { em { +"<a>one" } } }, em_, true)

    @Test
    fun `notices that attributes differ`() = isAt(doc { p { a { +"li<a>nk" } } }, link("http://baz"), false)

    val customDoc = customSchema.node(
        "doc",
        null,
        listOf(
            customSchema.node(
                "paragraph",
                null,
                listOf(
                    customSchema.text("one", listOf(remark1, customStrong)),
                    customSchema.text("two")
                )
            ),
            customSchema.node(
                "paragraph",
                null,
                listOf(
                    customSchema.text("one"),
                    customSchema.text("two", listOf(remark1)),
                    customSchema.text("three", listOf(remark1))
                )
            ),
            customSchema.node(
                "paragraph",
                null,
                listOf(
                    customSchema.text("one", listOf(remark2)),
                    customSchema.text("two", listOf(remark1))
                )
            )
        )
    )

    @Test fun `omits non-inclusive marks at end of mark`() {
        assertThat(Mark.sameSet(customDoc.resolve(4).marks(), listOf(customStrong))).isTrue
    }

    @Test fun `includes non-inclusive marks inside a text node`() {
        assertThat(Mark.sameSet(customDoc.resolve(3).marks(), listOf(remark1, customStrong))).isTrue
    }

    @Test fun `omits non-inclusive marks at the end of a line`() {
        assertThat(Mark.sameSet(customDoc.resolve(20).marks(), emptyList())).isTrue
    }

    @Test fun `includes non-inclusive marks between two marked nodes`() {
        assertThat(Mark.sameSet(customDoc.resolve(15).marks(), listOf(remark1))).isTrue
    }

    @Test fun `excludes non-inclusive marks at a point where mark attrs change`() {
        assertThat(Mark.sameSet(customDoc.resolve(25).marks(), emptyList())).isTrue
    }
}
