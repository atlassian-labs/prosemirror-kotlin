package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.model.parser.JSON
import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class JsonParseRecoveryTest {
    private val spec = SchemaSpec(
        nodes = mapOf(
            "doc" to NodeSpecImpl(content = "block+"),
            "paragraph" to NodeSpecImpl(content = "inline*", group = "block"),
            "text" to NodeSpecImpl(group = "inline"),
            "widget" to NodeSpecImpl(group = "block", attrs = mapOf("source" to AttributeSpecImpl())),
            "container" to NodeSpecImpl(
                content = "inline*", group = "block", attrs = mapOf("level" to AttributeSpecImpl())
            ),
            "badge" to NodeSpecImpl(inline = true, group = "inline", attrs = mapOf("label" to AttributeSpecImpl())),
            "unsupportedBlock" to NodeSpecImpl(content = "inline*", group = "block", attrs = opaqueAttrs),
            "unsupportedInline" to NodeSpecImpl(inline = true, group = "inline", attrs = opaqueAttrs),
            "unsupportedLeaf" to NodeSpecImpl(group = "block", attrs = opaqueAttrs)
        ),
        marks = mapOf(
            "link" to MarkSpecImpl(attrs = mapOf("href" to AttributeSpecImpl())),
            "tag" to MarkSpecImpl(attrs = mapOf("token" to AttributeSpecImpl())),
            "optional" to MarkSpecImpl(attrs = mapOf("color" to AttributeSpecImpl("blue"))),
            "unsupportedMark" to MarkSpecImpl(attrs = mapOf("original" to AttributeSpecImpl()), excludes = "")
        ),
        unknownNodeAttrs = { name, attrs -> attrs.orEmpty() + ("originalType" to name) },
        unknownMarkAttrs = { name, attrs -> attrs.orEmpty() + ("originalType" to name) }
    )

    private fun json(value: String) = JSON.parseToJsonElement(value).jsonObject

    private open class LosslessRecovery : JsonParseRecovery() {
        override fun createMark(type: MarkType, json: JsonObject): Mark =
            type.create(mapOf("original" to JsonObject(json - "id")))

        override fun createNode(type: NodeType, json: JsonObject, content: Fragment, marks: List<Mark>): Node =
            type.create(
                mapOf(
                    "original" to JsonObject(json - "id" - "content" - "marks"),
                    "hasContent" to json.containsKey("content"),
                    "hasMarks" to json.containsKey("marks")
                ),
                content,
                marks
            )
    }

    private class OpaqueMark(type: MarkType, attrs: Attrs) : Mark(type, attrs) {
        override fun toJSON(withId: Boolean): JsonObject {
            val original = attrs["original"] as JsonObject
            return if (withId) JsonObject(original + ("id" to JsonPrimitive(markId.id))) else original
        }
    }

    private class OpaqueNode(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>) :
        Node(type, attrs, content, marks) {
        override fun toJson(builder: JsonObjectBuilder, withId: Boolean) = builder.apply {
            (attrs["original"] as JsonObject).forEach { (key, value) -> put(key, value) }
            if (withId) put("id", nodeId.id)
            if (content.size != 0 || attrs["hasContent"] == true) put("content", content.toJSON(withId))
            if (marks.isNotEmpty() || attrs["hasMarks"] == true) {
                put("marks", JsonArray(marks.map { it.toJSON(withId) }))
            }
        }
    }

    private fun schema(policy: JsonParseRecovery? = LosslessRecovery(), schemaSpec: SchemaSpec = spec): Schema {
        return Schema(schemaSpec, policy).also { schema ->
            schema.marks["unsupportedMark"]!!.creator = object : MarkCreator<OpaqueMark> {
                override fun create(type: MarkType, attrs: Attrs) = OpaqueMark(type, attrs)
            }
            listOf("unsupportedBlock", "unsupportedInline", "unsupportedLeaf").forEach { name ->
                schema.nodes[name]!!.creator = object : NodeCreator<OpaqueNode> {
                    override fun create(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>) =
                        OpaqueNode(type, attrs, content, marks)
                }
            }
        }
    }

    @Test
    fun `all schema-required attributes recover without per-type or per-attribute guards`() {
        val schema = schema()
        listOf("link", "tag").forEach { name ->
            val raw = json("""{"type":"$name"}""")
            val mark = Mark.fromJSON(schema, raw)
            assertIs<OpaqueMark>(mark)
            assertEquals(raw, mark.toJSON())
        }
        mapOf("widget" to "unsupportedBlock", "container" to "unsupportedBlock", "badge" to "unsupportedInline")
            .forEach { (name, fallback) ->
                val raw = json("""{"type":"$name"}""")
                val node = Node.fromJSON(schema, raw)
                assertIs<OpaqueNode>(node)
                assertEquals(fallback, node.type.name)
                assertEquals(raw, node.toJSON())
            }
    }

    @Test
    fun `default factories reuse the unknown attribute adapters for invalid and unknown input`() {
        val schema = Schema(spec, JsonParseRecovery())
        listOf("link", "future").forEach { name ->
            val mark = Mark.fromJSON(schema, json("""{"type":"$name"}"""))
            assertEquals("unsupportedMark", mark.type.name)
            assertEquals(name, mark.attrs["originalType"])
        }
        listOf("widget", "future").forEach { name ->
            val node = Node.fromJSON(schema, json("""{"type":"$name"}"""))
            assertEquals(name, node.attrs["originalType"])
        }
    }

    @Test
    fun `valid required values defaults and unfamiliar attributes remain usable`() {
        val schema = schema()
        val mark = Mark.fromJSON(schema, json("""{"type":"link","attrs":{"href":"url","future":true}}"""))
        assertEquals("link", mark.type.name)
        assertEquals(mapOf("href" to "url", "future" to true), mark.attrs)
        assertEquals("blue", Mark.fromJSON(schema, json("""{"type":"optional"}""")).attrs["color"])
        val raw = json("""{"type":"widget","attrs":{"source":"url","future":[1,2]}}""")
        assertEquals(raw, Node.fromJSON(schema, raw).toJSON())
        // Presence and nullability are distinct schema rules; a present null is not a missing field.
        assertEquals("widget", Node.fromJSON(schema, json("""{"type":"widget","attrs":{"source":null}}""")).type.name)
    }

    @Test
    fun `validation sees original node data before decoding or typed creation`() {
        val observed = mutableListOf<JsonObject>()
        val schema = schema(object : LosslessRecovery() {
            override fun isValidNode(type: NodeType, json: JsonObject): Boolean {
                observed.add(json)
                return super.isValidNode(type, json)
            }
        })
        schema.nodes["widget"]!!.creator = object : NodeCreator<Node> {
            override fun create(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>): Node =
                error("The invalid typed node must not be created")
        }
        listOf(
            """{"type":"widget"}""",
            """{"type":"widget","attrs":{}}""",
            """{"type":"widget","attrs":null}""",
            """{"type":"widget","attrs":[]}""",
            """{"type":"widget","attrs":"bad","extra":{"value":null}}"""
        ).forEach { input ->
            val raw = json(input)
            assertEquals(raw, Node.fromJSON(schema, raw).toJSON())
            assertSame(raw, observed.last())
        }
    }

    @Test
    fun `external node requirements can reject absent null and wrong values despite PM defaults`() {
        val defaultedWidget = NodeSpecImpl(group = "block", attrs = mapOf("source" to AttributeSpecImpl("default")))
        val defaulted = spec.copy(nodes = spec.nodes + ("widget" to defaultedWidget))
        val policy = object : LosslessRecovery() {
            override fun isValidNode(type: NodeType, json: JsonObject): Boolean {
                if (type.name != "widget") return super.isValidNode(type, json)
                return ((json["attrs"] as? JsonObject)?.get("source") as? JsonPrimitive)?.isString == true
            }
        }
        val schema = schema(policy, defaulted)
        listOf(
            """{"type":"widget"}""",
            """{"type":"widget","attrs":{"source":null}}""",
            """{"type":"widget","attrs":{"source":123}}"""
        ).forEach { input ->
            val raw = json(input)
            val node = Node.fromJSON(schema, raw)
            assertEquals("unsupportedBlock", node.type.name)
            assertEquals(raw, node.toJSON())
        }
        val raw = json("""{"type":"widget","attrs":{"source":"valid","future":true}}""")
        assertEquals("widget", Node.fromJSON(schema, raw).type.name)
        assertEquals(raw, Node.fromJSON(schema, raw).toJSON())
    }

    @Test
    fun `schema and per-call selectors also classify invalid known nodes`() {
        val selected = mutableListOf<String>()
        val schema = schema(
            schemaSpec = spec.copy(unknownNodeFallback = { name, _ ->
                selected.add(name)
                "unsupportedLeaf"
            })
        )
        assertEquals("unsupportedLeaf", Node.fromJSON(schema, json("""{"type":"widget"}""")).type.name)
        assertEquals(listOf("widget"), selected)
        val overridden = Node.fromJSON(schema, json("""{"type":"widget"}"""), unknownNodeFallback = { _, _ ->
            "unsupportedBlock"
        })
        assertEquals("unsupportedBlock", overridden.type.name)
        assertEquals(listOf("widget"), selected)
        // Returning null from a per-call selector uses the normal fallback, without retrying the schema selector.
        val defaulted = Node.fromJSON(schema, json("""{"type":"badge"}"""), unknownNodeFallback = { _, _ -> null })
        assertEquals("unsupportedInline", defaulted.type.name)
        assertEquals(listOf("widget"), selected)
    }

    @Test
    fun `unknown node heuristic remains content field based`() {
        val schema = schema()
        assertEquals("unsupportedInline", Node.fromJSON(schema, json("""{"type":"future"}""")).type.name)
        assertEquals("unsupportedBlock", Node.fromJSON(schema, json("""{"type":"future","content":[]}""")).type.name)
        val raw = json("""{"type":"future","attrs":null,"future":true}""")
        assertEquals(raw, Node.fromJSON(schema, raw).toJSON())
    }

    @Test
    fun `strict checks target recovered nodes and restore internal IDs`() {
        val schema = schema()
        val raw = json("""{"type":"widget","id":"stored-node"}""")
        val node = Node.fromJSON(schema, raw, withId = true, check = true)
        assertEquals(NodeId("stored-node"), node.nodeId)
        assertEquals(raw, node.toJSONWithId())
        val invalidRecovery = schema(object : JsonParseRecovery() {
            override fun createNode(type: NodeType, json: JsonObject, content: Fragment, marks: List<Mark>): Node =
                Node(type, mapOf("undeclared" to true), content, marks)
        })
        assertFailsWith<RangeError> { Node.fromJSON(invalidRecovery, raw, check = true) }
    }

    @Test
    fun `node policy and unrelated creator failures remain visible`() {
        val failure = RangeError("policy failure")
        val schema = schema(object : LosslessRecovery() {
            override fun isValidNode(type: NodeType, json: JsonObject): Boolean = throw failure
        })
        val raw = json("""{"type":"widget","attrs":{"source":"url"}}""")
        assertSame(failure, assertFailsWith<RangeError> { Node.fromJSON(schema, raw) })
        val factoryFailure = RangeError("recovery factory failure")
        val invalid = schema(object : LosslessRecovery() {
            override fun createNode(type: NodeType, json: JsonObject, content: Fragment, marks: List<Mark>): Node =
                throw factoryFailure
        })
        assertSame(
            factoryFailure,
            assertFailsWith<RangeError> {
                Node.fromJSON(invalid, json("""{"type":"widget"}"""))
            }
        )
        val ordinary = schema()
        val creatorFailures = listOf(IllegalStateException("creator failure"), RangeError("creator range failure"))
        creatorFailures.forEach { creatorFailure ->
            ordinary.nodes["widget"]!!.creator = object : NodeCreator<Node> {
                override fun create(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>): Node =
                    throw creatorFailure
            }
            assertSame(creatorFailure, assertFails { Node.fromJSON(ordinary, raw) })
        }
    }

    @Test
    fun `programmatic node creation and fallback factories bypass validation`() {
        val observed = mutableListOf<String>()
        val schema = schema(object : LosslessRecovery() {
            override fun isValidNode(type: NodeType, json: JsonObject): Boolean {
                observed.add(type.name)
                return super.isValidNode(type, json)
            }
        })
        schema.nodes["widget"]!!.create()
        assertTrue(observed.isEmpty())
        Node.fromJSON(schema, json("""{"type":"widget"}"""))
        assertEquals(listOf("widget"), observed)
    }

    @Test
    fun `without recovery required attribute parsing remains unchanged`() {
        val schema = Schema(spec)
        assertEquals("link", Mark.fromJSON(schema, json("""{"type":"link"}""")).type.name)
        assertEquals("widget", Node.fromJSON(schema, json("""{"type":"widget"}""")).type.name)
        // Existing no-policy creator recovery remains compatible.
        schema.nodes["widget"]!!.creator = object : NodeCreator<Node> {
            override fun create(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>): Node =
                throw RangeError("legacy creator failure")
        }
        assertEquals("unsupportedInline", Node.fromJSON(schema, json("""{"type":"widget"}""")).type.name)
    }

    companion object {
        private val opaqueAttrs = mapOf(
            "original" to AttributeSpecImpl(),
            "hasContent" to AttributeSpecImpl(false),
            "hasMarks" to AttributeSpecImpl(false)
        )
    }
}
