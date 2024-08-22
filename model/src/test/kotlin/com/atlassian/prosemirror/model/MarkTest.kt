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

val em_ = schema.mark("em")
val strong = schema.mark("strong")
fun link(href: String, title: String? = null) =
    schema.mark("link", mapOf("href" to href, "title" to title))

val code = schema.mark("code")

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

    @Test
    fun `returns true for two empty sets`() {
        assertThat(Mark.sameSet(emptyList(), emptyList())).isTrue
    }

    @Test
    fun `returns true for simple identical sets`() {
        assertThat(Mark.sameSet(listOf(em_, strong), listOf(em_, strong))).isTrue
    }

    @Test
    fun `returns false for different sets`() {
        assertThat(!Mark.sameSet(listOf(em_, strong), listOf(em_, code))).isTrue
    }

    @Test
    fun `returns false when set size differs`() {
        assertThat(!Mark.sameSet(listOf(em_, strong), listOf(em_, strong, code))).isTrue
    }

    @Test
    fun `recognizes identical links in set`() {
        assertThat(Mark.sameSet(listOf(link("http://foo"), code), listOf(link("http://foo"), code))).isTrue
    }

    @Test
    fun `recognizes different links in set`() {
        assertThat(!Mark.sameSet(listOf(link("http://foo"), code), listOf(link("http://bar"), code))).isTrue
    }

    @Test
    fun `considers identical links to be the same`() {
        assertThat(link("http://foo")).isEqualTo(link("http://foo"))
    }

    @Test
    fun `considers different links to differ`() {
        assertThat(link("http://foo")).isNotEqualTo(link("http://bar"))
    }

    @Test
    fun `considers links with different titles to differ`() {
        assertThat(link("http://foo", "A")).isNotEqualTo(link("http://foo", "B"))
    }

    @Test
    fun `can add to the empty set`() {
        assertThat(Mark.sameSet(em_.addToSet(emptyList()), listOf(em_))).isTrue
    }

    @Test
    fun `is a no-op when the added thing is in set`() {
        assertThat(Mark.sameSet(em_.addToSet(listOf(em_)), listOf(em_))).isTrue
    }

    @Test
    fun `adds marks with lower rank before others`() {
        assertThat(Mark.sameSet(em_.addToSet(listOf(strong)), listOf(em_, strong))).isTrue
    }

    @Test
    fun `adds marks with higher rank after others`() {
        assertThat(Mark.sameSet(strong.addToSet(listOf(em_)), listOf(em_, strong))).isTrue
    }

    @Test
    fun `replaces different marks with new attributes`() {
        assertThat(
            Mark.sameSet(
                link("http://bar").addToSet(listOf(link("http://foo"), em_)),
                listOf(link("http://bar"), em_)
            )
        ).isTrue
    }

    @Test
    fun `does nothing when adding an existing link`() {
        assertThat(
            Mark.sameSet(
                link("http://foo").addToSet(listOf(em_, link("http://foo"))),
                listOf(em_, link("http://foo"))
            )
        ).isTrue
    }

    @Test
    fun `puts code marks at the end`() {
        assertThat(
            Mark.sameSet(
                code.addToSet(listOf(em_, strong, link("http://foo"))),
                listOf(em_, strong, link("http://foo"), code)
            )
        ).isTrue
    }

    @Test
    fun `puts marks with middle rank in the middle`() {
        assertThat(Mark.sameSet(strong.addToSet(listOf(em_, code)), listOf(em_, strong, code))).isTrue
    }

    @Test
    fun `allows nonexclusive instances of marks with the same type`() {
        assertThat(Mark.sameSet(remark2.addToSet(listOf(remark1)), listOf(remark1, remark2))).isTrue
    }

    @Test
    fun `doesn't duplicate identical instances of nonexclusive marks`() {
        assertThat(Mark.sameSet(remark1.addToSet(listOf(remark1)), listOf(remark1))).isTrue
    }

    @Test
    fun `clears all others when adding a globally-excluding mark`() {
        assertThat(Mark.sameSet(user1.addToSet(listOf(remark1, customEm)), listOf(user1))).isTrue
    }

    @Test
    fun `does not allow adding another mark to a globally-excluding mark`() {
        assertThat(Mark.sameSet(customEm.addToSet(listOf(user1)), listOf(user1))).isTrue
    }

    @Test
    fun `does overwrite a globally-excluding mark when adding another instance`() {
        assertThat(Mark.sameSet(user2.addToSet(listOf(user1)), listOf(user2))).isTrue
    }

    @Test
    fun `doesn't add anything when another mark excludes the added mark`() {
        assertThat(Mark.sameSet(customEm.addToSet(listOf(remark1, customStrong)), listOf(remark1, customStrong))).isTrue
    }

    @Test
    fun `remove excluded marks when adding a mark`() {
        assertThat(Mark.sameSet(customStrong.addToSet(listOf(remark1, customEm)), listOf(remark1, customStrong))).isTrue
    }

    @Test
    fun `is a no-op for the empty set`() {
        assertThat(Mark.sameSet(em_.removeFromSet(emptyList()), emptyList())).isTrue
    }

    @Test
    fun `can remove the last mark from a set`() {
        assertThat(Mark.sameSet(em_.removeFromSet(listOf(em_)), emptyList())).isTrue
    }

    @Test
    fun `is a no-op when the mark isn't in the set`() {
        assertThat(Mark.sameSet(strong.removeFromSet(listOf(em_)), listOf(em_))).isTrue
    }

    @Test
    fun `can remove a mark with attributes`() {
        assertThat(
            Mark.sameSet(link("http://foo").removeFromSet(listOf(link("http://foo"))), emptyList())
        ).isTrue
    }

    @Test
    fun `doesn't remove a mark when its attrs differ`() {
        assertThat(
            Mark.sameSet(
                link("http://foo", "title").removeFromSet(listOf(link("http://foo"))),
                listOf(link("http://foo"))
            )
        ).isTrue
    }

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

    @Test
    fun `omits non-inclusive marks at end of mark`() {
        assertThat(Mark.sameSet(customDoc.resolve(4).marks(), listOf(customStrong))).isTrue
    }

    @Test
    fun `includes non-inclusive marks inside a text node`() {
        assertThat(Mark.sameSet(customDoc.resolve(3).marks(), listOf(remark1, customStrong))).isTrue
    }

    @Test
    fun `omits non-inclusive marks at the end of a line`() {
        assertThat(Mark.sameSet(customDoc.resolve(20).marks(), emptyList())).isTrue
    }

    @Test
    fun `includes non-inclusive marks between two marked nodes`() {
        assertThat(Mark.sameSet(customDoc.resolve(15).marks(), listOf(remark1))).isTrue
    }

    @Test
    fun `excludes non-inclusive marks at a point where mark attrs change`() {
        assertThat(Mark.sameSet(customDoc.resolve(25).marks(), emptyList())).isTrue
    }
}
