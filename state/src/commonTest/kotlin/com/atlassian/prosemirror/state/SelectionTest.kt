package com.atlassian.prosemirror.state

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.schema
import kotlin.test.BeforeTest
import kotlin.test.Test

class SelectionTest {
    @BeforeTest
    fun beforeTest() {
        PMNodeBuilder.clean()
    }

    @Test
    fun `should follow changes`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"hi" } }, schema = schema))
        state.apply(state.tr.insertText("xy", 1))
        assertThat(state.selection.head).isEqualTo(3)
        assertThat(state.selection.anchor).isEqualTo(3)
        state.apply(state.tr.insertText("zq", 1))
        assertThat(state.selection.head).isEqualTo(5)
        assertThat(state.selection.anchor).isEqualTo(5)
        state.apply(state.tr.insertText("uv", 7))
        assertThat(state.selection.head).isEqualTo(5)
        assertThat(state.selection.anchor).isEqualTo(5)
    }

    @Test
    fun `should move after inserted content`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"hi" } }, schema = schema))
        state.textSel(2, 3)
        state.apply(state.tr.insertText("o"))
        assertThat(state.selection.head).isEqualTo(3)
        assertThat(state.selection.anchor).isEqualTo(3)
    }

    @Test
    fun `moves after an inserted leaf node`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"foobar" } }, schema = schema))
        state.textSel(4)
        state.apply(state.tr.replaceSelectionWith(schema.node("horizontal_rule")))
        assertThat(state.doc).isEqualTo(doc { p { +"foo" } + hr {} + p { +"bar" } })
        assertThat(state.selection.head).isEqualTo(7)
        state.textSel(10)
        state.apply(state.tr.replaceSelectionWith(schema.node("horizontal_rule")))
        assertThat(state.doc).isEqualTo(doc { p { +"foo" } + hr {} + p { +"bar" } + hr {} })
        assertThat(state.selection.from).isEqualTo(11)
    }

    @Test
    fun `allows typing over a leaf node`() {
        val state =
            TestState(EditorStateConfigImpl(doc = doc { p { +"a" } + "<a>" + hr {} + p { +"b" } }, schema = schema))
        state.nodeSel(3)
        state.apply(state.tr.replaceSelectionWith(schema.text("x")))
        assertThat(state.doc).isEqualTo(doc { p { +"a" } + p { +"x" } + p { +"b" } })
        assertThat(state.selection.head).isEqualTo(5)
        assertThat(state.selection.anchor).isEqualTo(5)
    }

    @Test
    fun `allows deleting a selected block`() {
        val state = TestState(
            EditorStateConfigImpl(
                doc = doc { p { +"foo" } + ul { li { p { +"bar" } } + li { p { +"baz" } } + li { p { +"quux" } } } },
                schema = schema
            )
        )
        state.nodeSel(0)
        state.deleteSelection()
        assertThat(state.doc).isEqualTo(doc { ul { li { p { +"bar" } } + li { p { +"baz" } } + li { p { +"quux" } } } })
        assertThat(state.selection.head).isEqualTo(3)
        state.nodeSel(2)
        state.deleteSelection()
        assertThat(state.doc).isEqualTo(doc { ul { li { p { +"baz" } } + li { p { +"quux" } } } })
        assertThat(state.selection.head).isEqualTo(3)
        state.nodeSel(9)
        state.deleteSelection()
        assertThat(state.doc).isEqualTo(doc { ul { li { p { +"baz" } } } })
        assertThat(state.selection.head).isEqualTo(6)
        state.nodeSel(0)
        state.deleteSelection()
        assertThat(state.doc).isEqualTo(doc { p {} })
    }

    @Test
    fun `preserves the marks of a deleted selection`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"foo" + em { +"<a>bar<b>" } + "baz" } }))
        state.deleteSelection()
        assertThat(state.state.storedMarks!!.size).isEqualTo(1)
    }

    @Test
    fun `doesn't preserve non-inclusive marks of a deleted selection`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"foo" + a { em { +"<a>bar<b>" } } + "baz" } }))
        state.deleteSelection()
        assertThat(state.state.storedMarks!!.size).isEqualTo(1)
    }

    @Test
    fun `doesn't preserve marks when deleting a selection at the end of a block`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"foo" + em { +"bar<a>" } } + p { +"b<b>az" } }))
        state.deleteSelection()
        assertThat(state.state.storedMarks).isNull()
    }

    @Test
    fun `drops non-inclusive marks at the end of a deleted span when appropriate`() {
        val state =
            TestState(EditorStateConfigImpl(doc = doc { p { +"foo" + a { +"ba" + em { +"<a>r<b>" } } + "baz" } }))
        state.deleteSelection()
        assertThat(state.state.storedMarks!!.joinToString { it.type.name }).isEqualTo("em")
    }

    @Test
    fun `keeps non-inclusive marks when still inside them`() {
        val state =
            TestState(EditorStateConfigImpl(doc = doc { p { +"foo" + a { +"b" + em { +"<a>a<b>" } + "r" } + "baz" } }))
        state.deleteSelection()
        assertThat(state.state.storedMarks!!.size).isEqualTo(2)
    }

    @Test
    fun `preserves marks when typing over marked text`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"foo " + em { +"<a>bar<b>" } + " baz" } }))
        state.apply(state.tr.insertText("quux"))
        assertThat(state.doc).isEqualTo(doc { p { +"foo " + em { +"quux" } + " baz" } })
        state.apply(state.tr.insertText("bar", 5, 9))
        assertThat(state.doc).isEqualTo(doc { p { +"foo " + em { +"bar" } + " baz" } })
    }

    @Test
    fun `allows deleting a leaf`() {
        val state =
            TestState(EditorStateConfigImpl(doc = doc { p { +"a" } + hr {} + hr {} + p { +"b" } }, schema = schema))
        state.nodeSel(3)
        state.deleteSelection()
        assertThat(state.doc).isEqualTo(doc { p { +"a" } + hr {} + p { +"b" } })
        assertThat(state.selection.from).isEqualTo(3)
        state.deleteSelection()
        assertThat(state.doc).isEqualTo(doc { p { +"a" } + p { +"b" } })
        assertThat(state.selection.head).isEqualTo(4)
    }

    @Test
    fun `properly handles deleting the selection`() {
        val state = TestState(
            EditorStateConfigImpl(
                doc = doc { p { +"foo" + img {} + "bar" } + blockquote { p { +"hi" } } + p { +"ay" } },
                schema = schema
            )
        )
        state.nodeSel(4)
        state.apply(state.tr.deleteSelection())
        assertThat(state.doc).isEqualTo(doc { p { +"foobar" } + blockquote { p { +"hi" } } + p { +"ay" } })
        assertThat(state.selection.head).isEqualTo(4)
        state.nodeSel(9)
        state.apply(state.tr.deleteSelection())
        assertThat(state.doc).isEqualTo(doc { p { +"foobar" } + p { +"ay" } })
        assertThat(state.selection.from).isEqualTo(9)
        state.nodeSel(8)
        state.apply(state.tr.deleteSelection())
        assertThat(state.doc).isEqualTo(doc { p { +"foobar" } })
        assertThat(state.selection.from).isEqualTo(7)
    }

    @Test
    fun `can replace inline selections`() {
        val state = TestState(
            EditorStateConfigImpl(
                doc = doc { p { +"foo" + img {} + "bar" + img {} + "baz" } },
                schema = schema
            )
        )
        state.nodeSel(4)
        state.apply(state.tr.replaceSelectionWith(schema.node("hard_break")))
        assertThat(state.doc).isEqualTo(doc { p { +"foo" + br {} + "bar" + img {} + "baz" } })
        assertThat(state.selection.head).isEqualTo(5)
        assertThat(state.selection.empty).isTrue()
        state.nodeSel(8)
        state.apply(state.tr.insertText("abc"))
        assertThat(state.doc).isEqualTo(doc { p { +"foo" + br {} + "barabcbaz" } })
        assertThat(state.selection.head).isEqualTo(11)
        assertThat(state.selection.empty).isTrue()
        state.nodeSel(0)
        state.apply(state.tr.insertText("xyz"))
        assertThat(state.doc).isEqualTo(doc { p { +"xyz" } })
    }

    @Test
    fun `can replace a block selection`() {
        val state = TestState(
            EditorStateConfigImpl(
                doc = doc { p { +"abc" } + hr {} + hr {} + blockquote { p { +"ow" } } },
                schema = schema
            )
        )
        state.nodeSel(5)
        state.apply(state.tr.replaceSelectionWith(schema.node("code_block")))
        assertThat(state.doc).isEqualTo(doc { p { +"abc" } + pre {} + hr {} + blockquote { p { +"ow" } } })
        assertThat(state.selection.from).isEqualTo(7)
        state.nodeSel(8)
        state.apply(state.tr.replaceSelectionWith(schema.node("paragraph")))
        assertThat(state.doc).isEqualTo(doc { p { +"abc" } + pre {} + hr {} + p {} })
        assertThat(state.selection.from).isEqualTo(9)
    }

    @Test
    fun `puts the cursor after the inserted text when inserting a list item`() {
        val state = TestState(EditorStateConfigImpl(doc = doc { p { +"<a>abc" } }))
        val source = doc { ul { li { p { +"<a>def<b>" } } } }
        state.apply(state.tr.replaceSelection(source.slice(pos("a")!!, pos("b")!!, true)))
        assertThat(state.selection.from).isEqualTo(6)
    }

    @Test
    fun `uses arguments when possible`() {
        val d = doc { p { +"f<a>o<b>o" } }
        val s = TextSelection.between(d.resolve(pos("b")!!), d.resolve(pos("a")!!))
        assertThat(s.anchor).isEqualTo(pos("b")!!)
        assertThat(s.head).isEqualTo(pos("a")!!)
    }

    @Test
    fun `will adjust when necessary`() {
        val d = doc { +"<a>" + p { +"foo" } }
        val s = TextSelection.between(d.resolve(pos("a")!!), d.resolve(pos("a")!!))
        assertThat(s.anchor).isEqualTo(1)
    }

    @Test
    fun `uses bias when adjusting`() {
        val d = doc { p { +"foo" } + "<a>" + p { +"bar" } }
        val pos = d.resolve(pos("a")!!)
        val sUp = TextSelection.between(pos, pos, -1)
        assertThat(sUp.anchor).isEqualTo(4)
        val sDown = TextSelection.between(pos, pos, 1)
        assertThat(sDown.anchor).isEqualTo(6)
    }

    @Test
    fun `will fall back to a node selection`() {
        val d = doc { hr {} + "<a>" }
        val s = TextSelection.between(d.resolve(pos("a")!!), d.resolve(pos("a")!!))
        assertThat(d.firstChild).isEqualTo((s as NodeSelection).node)
    }

    @Test
    fun `will collapse towards the other argument`() {
        val d = doc { +"<a>" + p { +"foo" } + "<b>" }
        var s = TextSelection.between(d.resolve(pos("a")!!), d.resolve(pos("b")!!))
        assertThat(s.anchor).isEqualTo(1)
        assertThat(s.head).isEqualTo(4)
        s = TextSelection.between(d.resolve(pos("b")!!), d.resolve(pos("a")!!))
        assertThat(s.anchor).isEqualTo(4)
        assertThat(s.head).isEqualTo(1)
    }
}
