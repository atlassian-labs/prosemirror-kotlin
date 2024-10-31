package com.atlassian.prosemirror.history

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.state.Command
import com.atlassian.prosemirror.state.EmptyEditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.schema
import com.atlassian.prosemirror.testbuilder.schema as testSchema
import com.atlassian.prosemirror.transform.ReplaceStep
import kotlin.test.Test

class HistoryTest {

    val plugin = HistoryPlugin()

    fun mkState(
        doc: Node? = doc { p { } },
        config: HistoryOptionsConfig? = null,
        additionalPlugins: List<Plugin<*>> = emptyList(),
        preserveItems: Boolean = false
    ): PMEditorState {
        val plugins = mutableListOf<Plugin<*>>(if (config != null) HistoryPlugin(config) else plugin)
        if (preserveItems) plugins.add(TestPlugin(mapOf("historyPreserveItems" to true)))
        return PMEditorState.create(
            EmptyEditorStateConfig(
                schema = testSchema,
                plugins = plugins + additionalPlugins,
                doc = doc
            )
        )
    }

    fun type(state: PMEditorState, text: String): PMEditorState {
        return state.apply(state.tr.insertText(text))
    }

    fun command(state: PMEditorState, command: Command): PMEditorState {
        var state = state
        command(state) { tr ->
            state = state.apply(tr)
        }
        return state
    }

    fun compress(state: PMEditorState) {
        // NOTE: This is mutating stuff that shouldn't be mutated. Not safe
        // to do outside of these tests.
        plugin.getState(state)?.done = plugin.getState(state)!!.done.compress()
    }

    @Test
    fun `enables undo`() {
        var state = mkState()
        state = type(state, "a")
        state = type(state, "b")
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
    }

    @Test
    fun `enables redo`() {
        var state = mkState()
        state = type(state, "a")
        state = type(state, "b")
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
    }

    @Test
    fun `tracks multiple levels of history`() {
        var state = mkState()
        state = type(state, "a")
        state = type(state, "b")
        state = state.apply(state.tr.insertText("c", 1))
        assertThat(state.doc).isEqualTo(doc { p { +"cab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"cab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
    }

    @Test
    fun `starts a new event when newGroupDelay elapses`() {
        var state = mkState(null, HistoryOptionsConfig(newGroupDelay = 1000))
        state = state.apply(state.tr.insertText("a").setTime(1000))
        state = state.apply(state.tr.insertText("b").setTime(1600))
        assertThat(undoDepth(state)).isEqualTo(1)
        state = state.apply(state.tr.insertText("c").setTime(2700))
        assertThat(undoDepth(state)).isEqualTo(2)
        state = command(state, undo)
        state = state.apply(state.tr.insertText("d").setTime(2800))
        assertThat(undoDepth(state)).isEqualTo(2)
    }

    @Test
    fun `starts a new event for non-adjacent changes`() {
        var state = mkState(doc { p { +"abc" } }, HistoryOptionsConfig(newGroupDelay = 1000))
        state = state.apply(state.tr.insertText("x", 1))
        state = state.apply(state.tr.insertText("y", 5))
        assertThat(undoDepth(state)).isEqualTo(2)
    }

    @Test
    fun `doesn't get confused by non-replacement steps when checking adjacency`() {
        var state = mkState(doc { p { } }, HistoryOptionsConfig(newGroupDelay = 1000))
        state = state.apply(
            state.tr.insertText("x", 1).addMark(1, 2, schema.marks["em"]?.create()!!) as Transaction
        )
        state = state.apply(
            state.tr.insertText("y", 2).addMark(2, 3, schema.marks["em"]?.create()!!) as Transaction
        )
        assertThat(undoDepth(state)).isEqualTo(1)
    }

    @Test
    fun `allows changes that aren't part of the history`() {
        var state = mkState()
        state = type(state, "hello")
        state = state.apply(state.tr.insertText("oops", 1).setMeta("addToHistory", false))
        state = state.apply(state.tr.insertText("!", 10).setMeta("addToHistory", false))
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"oops!" } })
    }

    @Test
    fun `doesn't get confused by an undo not adding any redo item`() {
        var state = mkState()
        state = state.apply(state.tr.insertText("foo"))
        assertThat(state.doc).isEqualTo(doc { p { +"foo" } })
        state = state.transaction {
            replaceWith(1, 4, schema.text("bar"))
            setMeta("addToHistory", false)
        }
        state = command(state, undo)
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"bar" } })
    }

    fun unsyncedComplex(state: PMEditorState, doCompress: Boolean) {
        var state = state
        state = type(state, "hello")
        state = state.apply(closeHistory(state.tr))
        state = type(state, "!")
        state = state.apply(state.tr.insertText("....", 1).setMeta("addToHistory", false))
        state = state.transaction {
            split(3)
        }

        assertThat(state.doc).isEqualTo(doc { p { +".." } + p { +"..hello!" } })
        state = state.transaction {
            split(2)
            setMeta("addToHistory", false)
        }
        if (doCompress) compress(state)
        state = command(state, undo)
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"." } + p { +"...hello" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"." } + p { +"..." } })
    }

    @Test
    fun `can handle complex editing sequences`() {
        unsyncedComplex(mkState(), false)
    }

    @Test
    fun `can handle complex editing sequences with compression`() {
        unsyncedComplex(mkState(), true)
    }

    @Test
    fun `supports overlapping edits`() {
        var state = mkState()
        state = type(state, "hello")
        state = state.apply(closeHistory(state.tr))
        state = state.transaction { delete(1, 6) }
        assertThat(state.doc).isEqualTo(doc { p { } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"hello" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
    }

    @Test
    fun `supports overlapping edits that aren't collapsed`() {
        var state = mkState()
        state = state.transaction {
            insertText("h", 1)
            setMeta("addToHistory", false)
        }
        state = type(state, "ello")
        state = state.apply(closeHistory(state.tr))
        state = state.transaction { delete(1, 6) }
        assertThat(state.doc).isEqualTo(doc { p { } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"hello" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"h" } })
    }

    @Test
    fun `supports overlapping unsynced deletes`() {
        var state = mkState()
        state = type(state, "hi")
        state = state.apply(closeHistory(state.tr))
        state = type(state, "hello")
        state = state.transaction {
            delete(1, 8)
            setMeta("addToHistory", false)
        }
        assertThat(state.doc).isEqualTo(doc { p { } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
    }

    @Test
    fun `can go back and forth through history multiple times`() {
        var state = mkState()
        state = type(state, "one")
        state = type(state, " two")
        state = state.apply(closeHistory(state.tr))
        state = type(state, " three")
        state = state.apply(state.tr.insertText("zero ", 1))
        state = state.apply(closeHistory(state.tr))
        state = state.transaction { split(1) }
        state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, 1)))
        state = type(state, "top")
        for (i in 0 until 6) {
            val re = i % 2
            repeat(4) {
                state = command(state, if (re != 0) redo else undo)
            }
            assertThat(state.doc).isEqualTo(
                if (re != 0) {
                    doc { p { +"top" } + p { +"zero one two three" } }
                } else {
                    doc { p { } }
                }
            )
        }
    }

    @Test
    fun `supports non-tracked changes next to tracked changes`() {
        var state = mkState()
        state = type(state, "o")
        state = state.transaction { split(1) }
        state = state.apply(state.tr.insertText("zzz", 4).setMeta("addToHistory", false))
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"zzz" } })
    }

    @Test
    fun `can go back and forth through history when preserving items`() {
        var state = mkState()
        state = type(state, "one")
        state = type(state, " two")
        state = state.apply(closeHistory(state.tr))
        state = state.apply(state.tr.insertText("xxx", state.selection.head).setMeta("addToHistory", false))
        state = type(state, " three")
        state = state.apply(state.tr.insertText("zero ", 1))
        state = state.apply(closeHistory(state.tr))
        state = state.transaction { split(1) }
        state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, 1)))
        state = type(state, "top")
        state = state.apply(state.tr.insertText("yyy", 1).setMeta("addToHistory", false))
        for (i in 0 until 3) {
            if (i == 2) compress(state)
            repeat(4) {
                state = command(state, undo)
            }
            assertThat(state.doc).isEqualTo(doc { p { +"yyyxxx" } })
            repeat(4) {
                state = command(state, redo)
            }
            assertThat(state.doc).isEqualTo(doc { p { +"yyytop" } + p { +"zero one twoxxx three" } })
        }
    }

    @Test
    fun `restores selection on undo`() {
        var state = mkState()
        state = type(state, "hi")
        state = state.apply(closeHistory(state.tr))
        state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, 1, 3)))
        val selection = state.selection
        state = state.transaction {
            replaceWith(selection.from, selection.to, schema.text("hello"))
        }
        val selection2 = state.selection
        state = command(state, undo)
        assertThat(state.selection).isEqualTo(selection)
        state = command(state, redo)
        assertThat(state.selection).isEqualTo(selection2)
    }

    @Test
    fun `rebases selection on undo`() {
        var state = mkState()
        state = type(state, "hi")
        state = state.apply(closeHistory(state.tr))
        state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, 1, 3)))
        state = state.transaction { insert(1, schema.text("hello")) }
        state = state.transaction {
            insert(1, schema.text("---"))
            setMeta("addToHistory", false)
        }
        state = command(state, undo)
        assertThat(state.selection.head).isEqualTo(6)
    }

    @Test
    fun `handles change overwriting in item-preserving mode`() {
        var state = mkState(null, HistoryOptionsConfig(), preserveItems = true)
        state = type(state, "a")
        state = type(state, "b")
        state = state.apply(closeHistory(state.tr))
        state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, 1, 3)))
        state = type(state, "c")
        state = command(state, undo)
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
    }

    @Test
    fun `supports querying for the undo and redo depth`() {
        var state = mkState()
        state = type(state, "a")
        assertThat(undoDepth(state)).isEqualTo(1)
        assertThat(redoDepth(state)).isEqualTo(0)
        state = state.apply(state.tr.insertText("b", 1).setMeta("addToHistory", false))
        assertThat(undoDepth(state)).isEqualTo(1)
        assertThat(redoDepth(state)).isEqualTo(0)
        state = command(state, undo)
        assertThat(undoDepth(state)).isEqualTo(0)
        assertThat(redoDepth(state)).isEqualTo(1)
        state = command(state, redo)
        assertThat(undoDepth(state)).isEqualTo(1)
        assertThat(redoDepth(state)).isEqualTo(0)
    }

    @Test
    fun `all functions gracefully handle EditorStates without history`() {
        val state = PMEditorState.create(EmptyEditorStateConfig(schema = testSchema))
        assertThat(undoDepth(state)).isEqualTo(0)
        assertThat(redoDepth(state)).isEqualTo(0)
        assertThat(undo(state, null)).isFalse()
        assertThat(redo(state, null)).isFalse()
    }

    @Test
    fun `truncates history`() {
        var state = mkState(null, HistoryOptionsConfig(depth = 2))
        for (i in 1 until 40) {
            state = type(state, "a")
            state = state.apply(closeHistory(state.tr))
            assertThat(undoDepth(state)).isEqualTo((i - 2) % 21 + 2)
        }
    }

    @Test
    fun `supports transactions with multiple steps`() {
        var state = mkState()
        state = state.apply(state.tr.insertText("a").insertText("b"))
        state = state.apply(state.tr.insertText("c", 1))
        assertThat(state.doc).isEqualTo(doc { p { +"cab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { } })
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"cab" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"ab" } })
    }

    @Test
    fun `combines appended transactions in the event started by the base transaction`() {
        var state = mkState(
            doc = doc { p { +"x" } },
            additionalPlugins = listOf(
                object : TestPlugin() {
                    override val appendTransaction = { _: List<Transaction>, _: PMEditorState, state: PMEditorState ->
                        if (state.doc.content.size == 4) {
                            state.tr.apply { insert(1, schema.text("A")) }
                        } else {
                            null
                        }
                    }
                }
            )
        )
        state = state.transaction { insert(2, schema.text("I")) }
        assertThat(state.doc).isEqualTo(doc { p { +"AxI" } })
        assertThat(undoDepth(state)).isEqualTo(1)
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"x" } })
    }

    @Test
    fun `includes transactions appended to undo in the redo history`() {
        var state = mkState(
            doc = doc { p { +"x" } },
            additionalPlugins = listOf(
                object : TestPlugin() {
                    override val appendTransaction = { trs: List<Transaction>, _: PMEditorState, state: PMEditorState ->
                        val add = trs[0].getMeta("add")
                        if (add != null) {
                            state.tr.apply { insert(1, schema.text(add.toString())) }
                        } else {
                            null
                        }
                    }
                }
            )
        )
        state = state.transaction {
            insert(2, schema.text("I"))
            setMeta("add", "A")
        }
        assertThat(state.doc).isEqualTo(doc { p { +"AxI" } })
        undo(state) { tr -> state = state.apply(tr.setMeta("add", "B")) }
        assertThat(state.doc).isEqualTo(doc { p { +"Bx" } })
        redo(state) { tr -> state = state.apply(tr.setMeta("add", "C")) }
        assertThat(state.doc).isEqualTo(doc { p { +"CAxI" } })
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"Bx" } })
    }

    @Test
    fun `doesn't close the history on appended transactions`() {
        var state = mkState(
            doc = doc { p { +"x" } },
            additionalPlugins = listOf(
                object : TestPlugin() {
                    override val appendTransaction = { trs: List<Transaction>, _: PMEditorState, state: PMEditorState ->
                        val add = trs[0].getMeta("add")
                        if (add != null) {
                            state.tr.apply { insert(1, schema.text(add.toString())) }
                        } else {
                            null
                        }
                    }
                }
            )
        )
        state = state.transaction {
            insert(2, schema.text("R"))
            setMeta("add", "A")
        }
        state = state.transaction {
            insert(3, schema.text("M"))
        }
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"x" } })
    }

    @Test
    fun `supports rebasing`() {
        // This test simulates a collab editing session where the local editor
        // receives a step (`right`) that's on top of the parent step (`base`) of
        // the last local step (`left`).

        // Shared base step
        var state = mkState()
        state = type(state, "base")
        state = state.apply(closeHistory(state.tr))
        val baseDoc = state.doc

        // Local unconfirmed step
        //
        //        - left
        //       /
        // base -
        //       \
        //        - right
        val rightStep = ReplaceStep(5, 5, Slice(Fragment.from(schema.text(" right")), 0, 0))
        state = state.transaction { step(rightStep) }
        assertThat(state.doc).isEqualTo(doc { p { +"base right" } })
        assertThat(undoDepth(state)).isEqualTo(2)
        val leftStep = ReplaceStep(1, 1, Slice(Fragment.from(schema.text("left ")), 0, 0))

        // Receive remote step and rebase local unconfirmed step
        //
        // base --> left --> right'
        val tr = state.tr
        tr.step(rightStep.invert(baseDoc))
        tr.step(leftStep)
        tr.step(rightStep.map(tr.mapping.slice(1))!!)
        tr.mapping.setMirror(0, tr.steps.size - 1)
        tr.setMeta("addToHistory", false)
        tr.setMeta("rebased", 1)
        state = state.apply(tr)
        assertThat(state.doc).isEqualTo(doc { p { +"left base right" } })
        assertThat(undoDepth(state)).isEqualTo(2)

        // Undo local unconfirmed step
        //
        // base --> left
        state = command(state, undo)
        assertThat(state.doc).isEqualTo(doc { p { +"left base" } })

        // Redo local unconfirmed step
        //
        // base --> left --> right'
        state = command(state, redo)
        assertThat(state.doc).isEqualTo(doc { p { +"left base right" } })
    }

    @Test
    fun `properly maps selection when rebasing`() {
        var state = mkState(doc { p { +"123456789ABCD" } })
        state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, 6, 13)))
        state = state.transaction { delete(6, 13) }
        var rebase = state.tr.apply {
            insert(6, schema.text("6789ABC"))
            insert(14, schema.text("E"))
            delete(6, 13)
            setMeta("rebased", 1)
            setMeta("addToHistory", false)
        }
        rebase.mapping.setMirror(0, 2)
        state = state.apply(rebase)
        state = command(state, undo)
    }
}
