package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.schema as testSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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

    @Test
    fun `replace step uses per-call unknown node fallback`() {
        ReplaceStep
        val step = Step.fromJSON(
            fallbackSchema,
            Json.parseToJsonElement(
                """
                    {
                      "stepType": "replace",
                      "from": 0,
                      "to": 0,
                      "slice": {
                        "content": [{ "type": "unknownLeaf" }]
                      }
                    }
                """.trimIndent()
            ).jsonObject,
            unknownNodeFallback = { unknownNodeType, _ ->
                if (unknownNodeType == "unknownLeaf") "fallbackLeaf" else null
            }
        ) as ReplaceStep

        assertEquals("fallbackLeaf", step.slice.content.firstChild?.type?.name)
    }

    @Test
    fun `replace around step uses per-call unknown node fallback recursively`() {
        ReplaceAroundStep
        val step = Step.fromJSON(
            fallbackSchema,
            Json.parseToJsonElement(
                """
                    {
                      "stepType": "replaceAround",
                      "from": 0,
                      "to": 0,
                      "gapFrom": 0,
                      "gapTo": 0,
                      "insert": 0,
                      "slice": {
                        "content": [{
                          "type": "unknownContainer",
                          "content": [{ "type": "unknownLeaf" }]
                        }]
                      }
                    }
                """.trimIndent()
            ).jsonObject,
            unknownNodeFallback = { unknownNodeType, _ ->
                when (unknownNodeType) {
                    "unknownLeaf" -> "fallbackLeaf"
                    "unknownContainer" -> "fallbackContainer"
                    else -> null
                }
            }
        ) as ReplaceAroundStep

        val container = step.slice.content.firstChild!!
        assertEquals("fallbackContainer", container.type.name)
        assertEquals("fallbackLeaf", container.firstChild?.type?.name)
    }

    private companion object {
        val fallbackSchema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "block+"),
                    "text" to NodeSpecImpl(),
                    "fallbackInline" to NodeSpecImpl(inline = true, group = "inline"),
                    "fallbackLeaf" to NodeSpecImpl(group = "block"),
                    "fallbackContainer" to NodeSpecImpl(content = "block*", group = "block")
                ),
                unsupportedNode = "fallbackContainer",
                unsupportedInlineNode = "fallbackInline",
                unknownNodeFallback = { _, _ -> "fallbackContainer" }
            )
        )
    }
}
