package com.atlassian.prosemirror.state

import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.schema
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.BeforeTest
import kotlin.test.Test

class StateTest {
    @BeforeTest
    fun beforeTest() {
        PMNodeBuilder.clean()
    }

    @Test
    fun `creates a default doc`() {
        val state = PMEditorState.create(EditorStateConfigImpl(schema = schema))
        assertThat(state.doc).isEqualTo(doc { p { } })
    }

    @Test
    fun `creates a default selection`() {
        val state = PMEditorState.create(EditorStateConfigImpl(doc = doc { p { +"foo" } }))
        assertThat(state.selection.from).isEqualTo(1)
        assertThat(state.selection.to).isEqualTo(1)
    }

    @Test
    fun `applies transform transactions`() {
        val state = PMEditorState.create(EditorStateConfigImpl(schema = schema))
        val newState = state.apply(state.tr.insertText("hi"))
        assertThat(state.doc).isEqualTo(doc { p {} })
        assertThat(newState.doc).isEqualTo(doc { p { +"hi" } })
        assertThat(newState.selection.from).isEqualTo(3)
    }

    @Test
    fun `applies set doc transaction`() {
        val state = PMEditorState.create(EditorStateConfigImpl(schema = schema))
        val docToReplace = doc { p { +"test content" } }
        val newState = with(state) {
            apply(
                tr.apply {
                    replaceWith(0, this.doc.nodeSize - 2, docToReplace)
                }
            )
        }
        assertThat(newState.doc).isEqualTo(docToReplace)
    }

    @Test
    fun `applies replace doc transaction4`() {
        val state = PMEditorState.create(EditorStateConfigImpl(schema = schema))
        state.doc = doc { p { +"test content" } }
        val docToReplace = doc { p { +"fixed content" } }
        val newState = with(state) {
            apply(
                tr.apply {
                    replaceWith(0, this.doc.nodeSize - 2, docToReplace)
                }
            )
        }
        assertThat(newState.doc).isEqualTo(docToReplace)
    }

    @Test
    fun `supports specifying and persisting storedMarks`() {
        val state = PMEditorState.create(
            EditorStateConfigImpl(
                doc = doc { p { +"ok" } },
                storedMarks = listOf(schema.mark("em"))
            )
        )
        assertThat(state.storedMarks!!.size).isEqualTo(1)
        val copy = PMEditorState.fromJSON(EditorStateConfigImpl(schema = schema), state.toJSON())
        assertThat(copy.storedMarks!!.size).isEqualTo(1)
    }
}
