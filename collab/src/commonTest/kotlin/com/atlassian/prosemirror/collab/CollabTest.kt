package com.atlassian.prosemirror.collab

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.state.EmptyEditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.Selection
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.schema
import com.atlassian.prosemirror.transform.Step
import kotlin.test.Test
<<<<<<<< HEAD:collab/src/test/kotlin/com/atlassian/prosemirror/collab/CollabTest.kt
import org.assertj.core.api.Assertions.assertThat
========
>>>>>>>> main:collab/src/commonTest/kotlin/com/atlassian/prosemirror/collab/CollabTest.kt

class DummyServer {
    val states = mutableListOf<PMEditorState>()
    val plugins = mutableListOf<Plugin<*>>()
    var steps = emptyList<Step>()
    val clientIDs = mutableListOf<String>()
    val delayed = mutableListOf<Int>()

    constructor(doc: Node? = null, n: Int = 2) {
        repeat(n) {
            val plugin = collab()
            this.plugins.add(plugin)
            this.states.add(
                PMEditorState.create(
                    EmptyEditorStateConfig(doc = doc, schema = schema, plugins = listOf(plugin))
                )
            )
        }
    }

    fun sync(n: Int) {
        val state = this.states[n]
        val version = (this.plugins[n].getState(state) as CollabState).version
        if (version != this.steps.size) {
            val tr = receiveTransaction(state, this.steps.drop(version), this.clientIDs.drop(version))
            this.states[n] = state.apply(tr)
        }
    }

    fun send(n: Int) {
        val sendable = sendableSteps(this.states[n])
        if (sendable != null && sendable.version == this.steps.size) {
            this.steps = this.steps + sendable.steps
            repeat(sendable.steps.size) {
                this.clientIDs.add(sendable.clientID!!)
            }
        }
    }

    fun broadcast(n: Int) {
        if (this.delayed.indexOf(n) > -1) return
        this.sync(n)
        this.send(n)
        for (i in 0 until this.states.size) {
            if (i != n) this.sync(i)
        }
    }

    fun update(n: Int, f: ((state: PMEditorState) -> Transaction)) {
        this.states[n] = this.states[n].apply(f(this.states[n]))
        this.broadcast(n)
    }

    fun type(n: Int, text: String, pos: Int? = null) {
        this.update(n) { s ->
            s.tr.insertText(text, pos ?: s.selection.head)
        }
    }

    fun conv(doc: Node) {
        this.states.forEach { state -> assertThat(state.doc).isEqualTo(doc) }
    }

    fun conv(innerContent: String) {
        val doc = doc { p { +innerContent } }
        this.states.forEach { state -> assertThat(state.doc).isEqualTo(doc) }
    }

    fun delay(n: Int, f: () -> Unit) {
        this.delayed.add(n)
        f()
        this.delayed.removeLast()
        this.broadcast(n)
    }
}

fun sel(near: Int) = { s: PMEditorState ->
    s.tr.setSelection(Selection.near(s.doc.resolve(near)))
}

class CollabTest {
    @Test
    fun `converges for simple changes`() {
        val s = DummyServer()
        s.type(0, "hi")
        s.type(1, "ok", 3)
        s.type(0, "!", 5)
        s.type(1, "...", 1)
        s.conv("...hiok!")
    }

    @Test
    fun `converges for multiple local changes`() {
        val s = DummyServer()
        s.type(0, "hi")
        s.delay(0) {
            s.type(0, "A")
            s.type(1, "X")
            s.type(0, "B")
            s.type(1, "Y")
        }
        s.conv("hiXYAB")
    }

    @Test
    fun `converges with three peers`() {
        val s = DummyServer(n = 3)
        s.type(0, "A")
        s.type(1, "U")
        s.type(2, "X")
        s.type(0, "B")
        s.type(1, "V")
        s.type(2, "C")
        s.conv("AUXBVC")
    }

    @Test
    fun `converges with three peers with multiple steps`() {
        val s = DummyServer(n = 3)
        s.type(0, "A")
        s.delay(1) {
            s.type(1, "U")
            s.type(2, "X")
            s.type(0, "B")
            s.type(1, "V")
            s.type(2, "C")
        }
        s.conv("AXBCUV")
    }

// TODO EASY: convert after History (Undo/Redo) plugin is implemented

//        it("supports undo", () => {
//            let s = new DummyServer
//            s.type(0, "A")
//            s.type(1, "B")
//            s.type(0, "C")
//            s.undo(1)
//            s.conv("AC")
//            s.type(1, "D")
//            s.type(0, "E")
//            s.conv("ACDE")
//        })

//        it("supports redo", () => {
//            let s = new DummyServer
//            s.type(0, "A")
//            s.type(1, "B")
//            s.type(0, "C")
//            s.undo(1)
//            s.redo(1)
//            s.type(1, "D")
//            s.type(0, "E")
//            s.conv("ABCDE")
//        })
//
//        it("supports deep undo", () => {
//            let s = new DummyServer(doc(p("hello"), p("bye")))
//            s.update(0, sel(6))
//            s.update(1, sel(11))
//            s.type(0, "!")
//            s.type(1, "!")
//            s.update(0, s => closeHistory(s.tr))
//            s.delay(0, () => {
//                s.type(0, " ...")
//                s.type(1, " ,,,")
//            })
//            s.update(0, s => closeHistory(s.tr))
//            s.type(0, "*")
//            s.type(1, "*")
//            s.undo(0)
//            s.conv(doc(p("hello! ..."), p("bye! ,,,*")))
//            s.undo(0)
//            s.undo(0)
//            s.conv(doc(p("hello"), p("bye! ,,,*")))
//            s.redo(0)
//            s.redo(0)
//            s.redo(0)
//            s.conv(doc(p("hello! ...*"), p("bye! ,,,*")))
//            s.undo(0)
//            s.undo(0)
//            s.conv(doc(p("hello!"), p("bye! ,,,*")))
//            s.undo(1)
//            s.conv(doc(p("hello!"), p("bye")))
//        })
//
//        it("support undo with clashing events", () => {
//            let s = new DummyServer(doc(p("hello")))
//            s.update(0, sel(6))
//            s.type(0, "A")
//            s.delay(0, () => {
//                s.type(0, "B", 4)
//                s.type(0, "C", 5)
//                s.type(0, "D", 1)
//                s.update(1, s => s.tr.delete(2, 5))
//            })
//            s.conv("DhoA")
//            s.undo(0)
//            s.undo(0)
//            s.conv("ho")
//            ist(s.states[0].selection.head, 3)
//        })
//
//        it("handles conflicting steps", () => {
//            let s = new DummyServer(doc(p("abcde")))
//            s.delay(0, () => {
//                s.update(0, s => s.tr.delete(3, 4))
//                s.type(0, "x")
//                s.update(1, s => s.tr.delete(2, 5))
//            })
//            s.undo(0)
//            s.undo(0)
//            s.conv(doc(p("ae")))
//        })
//
//        it("can undo simultaneous typing", () => {
//            let s = new DummyServer(doc(p("A"), p("B")))
//            s.update(0, sel(2))
//            s.update(1, sel(5))
//            s.delay(0, () => {
//                s.type(0, "1")
//                s.type(0, "2")
//                s.type(1, "x")
//                s.type(1, "y")
//            })
//            s.conv(doc(p("A12"), p("Bxy")))
//            s.undo(0)
//            s.conv(doc(p("A"), p("Bxy")))
//            s.undo(1)
//            s.conv(doc(p("A"), p("B")))
//        })
//    })
}
