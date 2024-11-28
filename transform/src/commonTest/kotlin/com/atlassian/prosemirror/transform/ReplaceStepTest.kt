package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.schema as testSchema
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplaceStepTest {
    // region ReplaceAroundStep.map
    private fun test(doc: Node, change: (tr: Transform) -> Unit, otherChange: (tr: Transform) -> Unit, expected: Node) {
        val trA = Transform(doc)
        val trB = Transform(doc)
        change(trA)
        otherChange(trB)
        val result = Transform(trB.doc).step(trA.steps[0].map(trB.mapping)!!).doc
        assertEquals(result, expected)
    }

    @Test
    fun `doesn't break wrap steps on insertions`() {
        test(
            doc { p { +"a" } },
            { tr -> tr.wrap(tr.doc.resolve(1).blockRange()!!, listOf(NodeBase(testSchema.nodes["blockquote"]!!))) },
            { tr -> tr.insert(0, doc { p { +"b" } }.firstChild!!) },
            doc { p { +"b" } + blockquote { p { +"a" } } }
        )
    }

    @Test
    fun `doesn't overwrite content inserted at start of unwrap step`() {
        test(
            doc { blockquote { p { +"a" } } },
            { tr -> tr.lift(tr.doc.resolve(2).blockRange()!!, 0) },
            { tr -> tr.insert(2, testSchema.text("x")) },
            doc { p { +"xa" } }
        )
    }
    // endregion
}
