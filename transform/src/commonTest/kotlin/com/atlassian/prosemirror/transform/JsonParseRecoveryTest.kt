package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.JsonParseRecovery
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkCreator
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeCreator
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec
import com.atlassian.prosemirror.model.parser.JSON
import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class JsonParseRecoveryTest {
    private fun json(value: String) = JSON.parseToJsonElement(value).jsonObject

    private class ReferenceMark(type: MarkType, attrs: Attrs) : Mark(type, attrs) {
        val key = attrs["key"] as String
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

    private fun schema(): Schema {
        val opaqueAttrs = mapOf(
            "original" to AttributeSpecImpl(),
            "hasContent" to AttributeSpecImpl(false),
            "hasMarks" to AttributeSpecImpl(false)
        )
        val spec = SchemaSpec(
            nodes = mapOf(
                "doc" to NodeSpecImpl(content = "block+"),
                "paragraph" to NodeSpecImpl(content = "inline*", group = "block"),
                "text" to NodeSpecImpl(group = "inline"),
                "token" to NodeSpecImpl(inline = true, group = "inline"),
                "widget" to NodeSpecImpl(
                    inline = true, group = "inline", attrs = mapOf("source" to AttributeSpecImpl())
                ),
                "panel" to NodeSpecImpl(
                    content = "paragraph+", group = "block", attrs = mapOf("level" to AttributeSpecImpl())
                ),
                "unsupportedBlock" to NodeSpecImpl(content = "paragraph*", group = "block", attrs = opaqueAttrs),
                "unsupportedInline" to NodeSpecImpl(inline = true, group = "inline", attrs = opaqueAttrs)
            ),
            marks = mapOf(
                "opaque" to MarkSpecImpl(attrs = mapOf("original" to AttributeSpecImpl()), excludes = ""),
                "reference" to MarkSpecImpl(attrs = mapOf("key" to AttributeSpecImpl("")), excludes = "")
            ),
            unsupportedMark = "opaque"
        )
        return Schema(
            spec,
            object : JsonParseRecovery() {
                override fun isValidMark(type: MarkType, json: JsonObject): Boolean {
                    val key = (json["attrs"] as? JsonObject)?.get("key") as? JsonPrimitive
                    return key?.isString == true
                }

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
        ).also { schema ->
            schema.marks["reference"]!!.creator = object : MarkCreator<ReferenceMark> {
                override fun create(type: MarkType, attrs: Attrs) = ReferenceMark(type, attrs)
            }
            schema.marks["opaque"]!!.creator = object : MarkCreator<OpaqueMark> {
                override fun create(type: MarkType, attrs: Attrs) = OpaqueMark(type, attrs)
            }
            listOf("unsupportedBlock", "unsupportedInline").forEach { name ->
                schema.nodes[name]!!.creator = object : NodeCreator<OpaqueNode> {
                    override fun create(type: NodeType, attrs: Attrs, content: Fragment?, marks: List<Mark>) =
                        OpaqueNode(type, attrs, content, marks)
                }
            }
        }
    }

    private fun document(schema: Schema, marks: List<JsonObject> = emptyList()): Node = Node.fromJSON(
        schema,
        json(
            """
            {"type":"doc","content":[{"type":"paragraph","content":[
                {"type":"text","text":"text","marks":[${marks.joinToString()}]}
            ]}]}
            """
        )
    )

    private fun step(schema: Schema, type: String, mark: JsonObject, node: Boolean = false): Step {
        // Initialize the parsers used by the public Step.fromJSON registry.
        AddMarkStep
        RemoveMarkStep
        AddNodeMarkStep
        RemoveNodeMarkStep
        val original = buildJsonObject {
            put("stepType", type)
            put("mark", mark)
            if (node) {
                put("pos", 1)
            } else {
                put("from", 1)
                put("to", 5)
            }
        }
        val parsed = assertNotNull(Step.fromJSON(schema, original))
        assertEquals(original, parsed.toJSON())
        return parsed
    }

    private fun apply(step: Step, doc: Node): Node {
        val result = step.apply(doc)
        assertNull(result.failed)
        return assertNotNull(result.doc)
    }

    @Test
    fun `both add and remove mark steps recover with matching equality and support inversion`() {
        val schema = schema()
        listOf(
            """{"type":"reference"}""",
            """{"type":"reference","attrs":{}}""",
            """{"type":"reference","attrs":{"key":null}}""",
            """{"type":"reference","attrs":{"key":123,"future":true}}"""
        ).map(::json).forEach { original ->
            val doc = document(schema)
            val add = assertIs<AddMarkStep>(step(schema, "addMark", original))
            val remove = assertIs<RemoveMarkStep>(step(schema, "removeMark", original))
            assertIs<OpaqueMark>(add.mark)
            assertEquals(add.mark, remove.mark)
            val added = apply(add, doc)
            assertEquals(original, added.firstChild!!.firstChild!!.marks.single().toJSON())
            assertEquals(doc, apply(remove, added))
            assertEquals(doc, apply(add.invert(doc), added))
            val undoRemoval = assertNotNull(Step.fromJSON(schema, remove.invert(added).toJSON()))
            assertEquals(added, apply(undoRemoval, doc))
        }
    }

    @Test
    fun `remove step matches an initially recovered mark and leaves distinct neighbors intact`() {
        val schema = schema()
        val invalid = json("""{"type":"reference"}""")
        val otherInvalid = json("""{"type":"reference","attrs":{"key":null}}""")
        val valid = json("""{"type":"reference","attrs":{"key":"valid"}}""")
        val doc = document(schema, listOf(invalid, otherInvalid, valid))
        val remove = step(schema, "removeMark", invalid)
        val removed = apply(remove, doc)
        val marks = removed.firstChild!!.firstChild!!.marks
        assertEquals(listOf(otherInvalid, valid), marks.map { it.toJSON() })
        assertEquals("valid", assertIs<ReferenceMark>(marks[1]).key)
        val restored = apply(remove.invert(doc), removed)
        assertEquals(
            listOf(invalid, otherInvalid, valid).sortedBy { it.toString() },
            restored.firstChild!!.firstChild!!.marks.map { it.toJSON() }.sortedBy { it.toString() }
        )
        assertEquals(removed, apply(remove, restored))
    }

    @Test
    fun `unrelated text edit preserves malformed marks and missing attribute presence`() {
        val schema = schema()
        listOf(
            """{"type":"reference"}""",
            """{"type":"reference","attrs":{}}""",
            """{"type":"reference","attrs":{"key":null,"future":{"value":1}}}"""
        ).forEach { mark ->
            fun content(text: String) = json(
                """
                {"type":"doc","content":[
                    {"type":"paragraph","content":[{"type":"text","text":"readable","marks":[$mark]}]},
                    {"type":"paragraph","content":[{"type":"text","text":"$text"}]}
                ]}
                """
            )
            val doc = Node.fromJSON(schema, content("edit"))
            val edited = Transform(doc).insert(doc.content.size - 1, schema.text("!")).doc
            assertEquals("readableedit!", edited.textContent)
            assertEquals(content("edit!"), edited.toJSON())
            assertEquals(edited, Node.fromJSON(schema, edited.toJSON()))
        }
    }

    @Test
    fun `replace and replace around step slices share recovery`() {
        val schema = schema()
        val original = json("""{"type":"reference","attrs":{"key":123}}""")
        val slice = json("""{"content":[{"type":"text","text":"added","marks":[$original]}]}""")
        ReplaceStep
        ReplaceAroundStep
        val replace = json("""{"stepType":"replace","from":1,"to":1,"slice":$slice}""")
        val parsed = assertIs<ReplaceStep>(Step.fromJSON(schema, replace))
        assertIs<OpaqueMark>(parsed.slice.content.firstChild!!.marks.single())
        assertEquals(replace, parsed.toJSON())
        val edited = apply(parsed, document(schema))
        assertEquals(original, edited.firstChild!!.firstChild!!.marks.single().toJSON())
        val around = json(
            """{"stepType":"replaceAround","from":1,"to":1,"gapFrom":1,"gapTo":1,"insert":0,"slice":$slice}"""
        )
        val parsedAround = assertIs<ReplaceAroundStep>(Step.fromJSON(schema, around))
        assertIs<OpaqueMark>(parsedAround.slice.content.firstChild!!.marks.single())
        assertEquals(around, parsedAround.toJSON())
    }

    @Test
    fun `node mark add and remove steps share recovery`() {
        val schema = schema()
        val doc = Node.fromJSON(
            schema,
            json("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"token"}]}]}""")
        )
        val original = json("""{"type":"reference"}""")
        val add = assertIs<AddNodeMarkStep>(step(schema, "addNodeMark", original, node = true))
        val remove = assertIs<RemoveNodeMarkStep>(step(schema, "removeNodeMark", original, node = true))
        assertIs<OpaqueMark>(add.mark)
        assertEquals(add.mark, remove.mark)
        assertEquals(doc, apply(remove, apply(add, doc)))
    }

    @Test
    fun `recovered block children remain editable and serialize current content`() {
        val schema = schema()
        fun content(first: String, second: String) = json(
            """
            {"type":"doc","content":[
                {"type":"panel","content":[{"type":"paragraph","content":[
                    {"type":"text","text":"$first","marks":[{"type":"reference"}]}
                ]}]},
                {"type":"paragraph","content":[{"type":"text","text":"$second"}]}
            ]}
            """
        )
        val original = content("readable", "edit")
        val doc = Node.fromJSON(schema, original)
        assertIs<OpaqueNode>(doc.firstChild)
        assertEquals(original, doc.toJSON())
        val unrelatedEdit = Transform(doc).insert(doc.content.size - 1, schema.text("!")).doc
        assertEquals(content("readable", "edit!"), unrelatedEdit.toJSON())
        val marks = doc.firstChild!!.firstChild!!.firstChild!!.marks
        val insideEdit = Transform(doc).insert(3, schema.text("!", marks)).doc
        assertEquals(content("r!eadable", "edit"), insideEdit.toJSON())
        assertEquals(insideEdit, Node.fromJSON(schema, insideEdit.toJSON()))
    }

    @Test
    fun `node recovery is shared by replacement step slices`() {
        val schema = schema()
        val raw = json("""{"type":"widget"}""")
        val slice = json("""{"content":[$raw]}""")
        ReplaceStep
        ReplaceAroundStep
        val original = json("""{"stepType":"replace","from":1,"to":1,"slice":$slice}""")
        val replace = assertIs<ReplaceStep>(Step.fromJSON(schema, original))
        assertIs<OpaqueNode>(replace.slice.content.firstChild)
        assertEquals(original, replace.toJSON())
        val edited = apply(replace, document(schema))
        assertEquals(raw, edited.firstChild!!.firstChild!!.toJSON())
        val around = json(
            """{"stepType":"replaceAround","from":1,"to":1,"gapFrom":1,"gapTo":1,"insert":0,"slice":$slice}"""
        )
        val replaceAround = assertIs<ReplaceAroundStep>(Step.fromJSON(schema, around))
        assertIs<OpaqueNode>(replaceAround.slice.content.firstChild)
        assertEquals(around, replaceAround.toJSON())
    }
}
