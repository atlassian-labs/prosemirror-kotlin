package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.model.parser.JSON
import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class MarkParsingRecoveryTest {
    private val spec = SchemaSpec(
        nodes = mapOf(
            "doc" to NodeSpecImpl(content = "paragraph+"),
            "paragraph" to NodeSpecImpl(content = "text*"),
            "text" to NodeSpecImpl()
        ),
        marks = mapOf(
            "opaque" to MarkSpecImpl(
                attrs = mapOf("original" to AttributeSpecImpl()),
                excludes = ""
            ),
            "reference" to MarkSpecImpl(
                attrs = mapOf(
                    "key" to AttributeSpecImpl("", validateString = "String"),
                    "kind" to AttributeSpecImpl("inline", validateString = "String")
                ),
                excludes = ""
            )
        ),
        unsupportedMark = "opaque",
        unknownMarkAttrs = { name, attrs -> attrs.orEmpty() + ("unknownType" to name) }
    )

    private fun json(value: String) = JSON.parseToJsonElement(value).jsonObject

    private class ReferenceMark(type: MarkType, attrs: Attrs) : Mark(type, attrs) {
        val key = attrs["key"] as String
    }

    // Keep the raw data in equality attributes, excluding PM's internal identity just as ordinary
    // marks do. The caller owns this representation and its serialization, not ProseMirror.
    private class OpaqueMark(type: MarkType, attrs: Attrs) : Mark(type, attrs) {
        override fun toJSON(withId: Boolean): JsonObject {
            val original = attrs["original"] as JsonObject
            return if (withId) JsonObject(original + ("id" to JsonPrimitive(markId.id))) else original
        }
    }

    private fun recover(schema: Schema, original: JsonObject): Mark = schema.marks["opaque"]!!.create(
        mapOf("original" to JsonObject(original - "id"))
    )

    private open inner class Recovery : JsonParseRecovery() {
        override fun isValidMark(type: MarkType, json: JsonObject): Boolean {
            val attrs = json["attrs"] as? JsonObject
            val key = attrs?.get("key") as? JsonPrimitive
            return key?.isString == true && attrs["kind"] == JsonPrimitive("inline")
        }

        override fun createMark(type: MarkType, json: JsonObject): Mark = recover(type.schema, json)
    }

    private fun schema(policy: JsonParseRecovery? = Recovery(), onCreate: () -> Unit = {}): Schema {
        return Schema(spec, policy).also { schema ->
            schema.marks["reference"]!!.creator = object : MarkCreator<ReferenceMark> {
                override fun create(type: MarkType, attrs: Attrs): ReferenceMark {
                    onCreate()
                    return ReferenceMark(type, attrs)
                }
            }
            schema.marks["opaque"]!!.creator = object : MarkCreator<OpaqueMark> {
                override fun create(type: MarkType, attrs: Attrs) = OpaqueMark(type, attrs)
            }
        }
    }

    @Test
    fun `without a policy parsing retains defaults unknown fallback and strict checks`() {
        val schema = Schema(spec)
        val defaulted = Mark.fromJSON(schema, json("""{"type":"reference"}"""))
        assertEquals(mapOf("key" to "", "kind" to "inline"), defaulted.attrs)
        assertEquals(defaulted, Mark.fromJSON(schema, json("""{"type":"REFERENCE"}""")))
        val unknown = Mark.fromJSON(schema, json("""{"type":"future","attrs":{"new":123}}"""))
        assertEquals("opaque", unknown.type.name)
        assertEquals(mapOf("new" to 123, "unknownType" to "future"), unknown.attrs)
        assertFailsWith<RangeError> {
            Mark.fromJSON(schema, json("""{"type":"reference","attrs":{"key":123}}"""), check = true)
        }
        assertEquals(123, Mark.fromJSON(schema, json("""{"type":"reference","attrs":{"key":123}}""")).attrs["key"])
        assertFailsWith<RangeError> { Mark.fromJSON(schema, null) }
    }

    @Test
    fun `default recovery accepts defaulted marks and retains existing unknown fallback`() {
        val standard = Schema(spec)
        val declining = Schema(spec, JsonParseRecovery())
        listOf(
            """{"type":"reference"}""",
            """{"type":"reference","attrs":{"key":"valid","extra":"retained"}}""",
            """{"type":"future","attrs":{"extra":true}}"""
        ).forEach { input ->
            assertEquals(Mark.fromJSON(standard, json(input)).toJSON(), Mark.fromJSON(declining, json(input)).toJSON())
        }
    }

    @Test
    fun `policy sees unmodified malformed JSON before decoding defaults or typed creation`() {
        var creations = 0
        val observed = mutableListOf<JsonObject>()
        val schema = schema(
            policy = object : Recovery() {
                override fun isValidMark(type: MarkType, json: JsonObject): Boolean {
                    observed.add(json)
                    return super.isValidMark(type, json)
                }
            },
            onCreate = { creations++ }
        )
        listOf(
            """{"type":"reference"}""",
            """{"type":"reference","attrs":{}}""",
            """{"type":"reference","attrs":null}""",
            """{"type":"reference","attrs":[]}""",
            """{"type":"reference","attrs":"bad"}""",
            """{"type":"reference","attrs":{"kind":"inline"}}""",
            """{"type":"reference","attrs":{"key":null,"kind":"inline"}}""",
            """{"type":"reference","attrs":{"key":123,"kind":"inline"}}""",
            """{"type":"reference","attrs":{"key":"a"}}""",
            """{"type":"reference","attrs":{"key":"a","kind":"future","extra":{"x":null}},"future":true}"""
        ).forEach { input ->
            val original = json(input)
            val mark = Mark.fromJSON(schema, original)
            assertIs<OpaqueMark>(mark)
            assertSame(original, observed.last())
            assertEquals(original, mark.toJSON())
        }
        assertEquals(0, creations)
    }

    @Test
    fun `valid marks remain typed and retain unfamiliar attributes`() {
        val original = json("""{"type":"reference","attrs":{"key":"valid","kind":"inline","future":[1,2]}}""")
        val mark = Mark.fromJSON(schema(), original)
        assertEquals("valid", assertIs<ReferenceMark>(mark).key)
        assertEquals(original, mark.toJSON())
    }

    @Test
    fun `unknown and invalid marks share the same configured fallback factory`() {
        val schema = Schema(
            spec,
            object : JsonParseRecovery() {
                override fun isValidMark(type: MarkType, json: JsonObject) = false

                override fun createMark(type: MarkType, json: JsonObject): Mark {
                    assertEquals("opaque", type.name)
                    return Mark(type, mapOf("original" to json))
                }
            }
        )
        listOf("reference", "other", "future").forEach { name ->
            val original = json("""{"type":"$name"}""")
            assertEquals(original, Mark.fromJSON(schema, original).attrs["original"])
        }
    }

    @Test
    fun `internal IDs reach the policy and are restored only when requested`() {
        val original = json("""{"type":"reference","id":"stored-id","attrs":{"key":null}}""")
        var observed: JsonObject? = null
        val schema = schema(
            policy = object : Recovery() {
                override fun isValidMark(type: MarkType, json: JsonObject): Boolean {
                    observed = json
                    return super.isValidMark(type, json)
                }
            }
        )
        val withId = Mark.fromJSON(schema, original, withId = true)
        assertSame(original, observed)
        assertEquals(MarkId("stored-id"), withId.markId)
        assertEquals(original, withId.toJSON(withId = true))
        val withoutId = Mark.fromJSON(schema, original)
        assertNotEquals(MarkId("stored-id"), withoutId.markId)
        assertEquals(JsonObject(original - "id"), withoutId.toJSON())
        assertEquals(withId, withoutId)
        val normal = Mark.fromJSON(Schema(spec), json("""{"type":"reference","id":"normal-id"}"""), withId = true)
        assertEquals(MarkId("normal-id"), normal.markId)
    }

    @Test
    fun `strict parsing checks the recovered type and still rejects invalid recovery attributes`() {
        val original = json("""{"type":"reference","attrs":{"key":123}}""")
        assertIs<OpaqueMark>(Mark.fromJSON(schema(), original, check = true))
        val schema = Schema(
            spec,
            object : JsonParseRecovery() {
                override fun isValidMark(type: MarkType, json: JsonObject) = false
                override fun createMark(type: MarkType, json: JsonObject) = Mark(type, mapOf("undeclared" to true))
            }
        )
        Mark.fromJSON(schema, original)
        assertFailsWith<RangeError> { Mark.fromJSON(schema, original, check = true) }
        assertFailsWith<RangeError> {
            Mark.fromJSON(
                schema(),
                json("""{"type":"reference","attrs":{"key":"ok","kind":"inline","extra":1}}"""),
                check = true
            )
        }
    }

    @Test
    fun `policy and unrelated creator failures propagate unchanged`() {
        val failure = IllegalStateException("policy failure")
        val schema = Schema(
            spec,
            object : JsonParseRecovery() {
                override fun isValidMark(type: MarkType, json: JsonObject): Boolean = throw failure
            }
        )
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> {
                Mark.fromJSON(schema, json("""{"type":"reference"}"""))
            }
        )
        val creatorFailure = IllegalStateException("creator failure")
        val declining = Schema(spec, JsonParseRecovery())
        declining.marks["reference"]!!.creator = object : MarkCreator<Mark> {
            override fun create(type: MarkType, attrs: Attrs): Mark = throw creatorFailure
        }
        assertSame(
            creatorFailure,
            assertFailsWith<IllegalStateException> {
                Mark.fromJSON(declining, json("""{"type":"reference"}"""))
            }
        )
    }

    @Test
    fun `factory creation bypasses recovery including factories used by the policy`() {
        var calls = 0
        val schema = schema(
            policy = object : Recovery() {
                override fun isValidMark(type: MarkType, json: JsonObject): Boolean {
                    calls++
                    return super.isValidMark(type, json)
                }
            }
        )
        assertIs<ReferenceMark>(schema.marks["reference"]!!.create())
        assertEquals(0, calls)
        assertIs<OpaqueMark>(Mark.fromJSON(schema, json("""{"type":"reference"}""")))
        assertEquals(1, calls)
    }

    @Test
    fun `documents fragments and slices share recovery and round trip raw marks`() {
        val schema = schema()
        val original = json(
            """
            {"type":"doc","content":[{"type":"paragraph","content":[{
                "type":"text","text":"readable","marks":[
                    {"type":"reference"},
                    {"type":"reference","attrs":{"key":null}},
                    {"type":"reference","attrs":{"key":"valid","kind":"inline"}}
                ]
            }]}]}
            """
        )
        val doc = Node.fromJSON(schema, original)
        val marks = doc.firstChild!!.firstChild!!.marks
        assertIs<OpaqueMark>(marks[0])
        assertIs<OpaqueMark>(marks[1])
        assertIs<ReferenceMark>(marks[2])
        assertEquals("readable", doc.textContent)
        assertEquals(original, doc.toJSON())
        assertEquals(original, Node.fromJSON(schema, original, check = true).toJSON())
        val content = original["content"] as JsonArray
        assertEquals(content, Fragment.fromJSON(schema, content).toJSON())
        val slice = JsonObject(mapOf("content" to content))
        assertEquals(slice, Slice.fromJSON(schema, slice).toJSON())
    }

    @Test
    fun `distinct raw data stays distinct and can coexist with a valid mark`() {
        val schema = schema()
        val inputs = listOf(
            """{"type":"reference"}""",
            """{"type":"reference","attrs":{}}""",
            """{"type":"reference","attrs":{"key":null}}""",
            """{"type":"reference","attrs":{"key":123}}""",
            """{"type":"reference","attrs":{"key":"ok","kind":"inline"}}"""
        ).map(::json)
        val marks = inputs.map { Mark.fromJSON(schema, it) }
        val set = marks.fold(emptyList<Mark>()) { set, mark -> mark.addToSet(set) }
        assertEquals(marks.size, set.size)
        marks.forEachIndexed { index, mark ->
            assertEquals(mark, Mark.fromJSON(schema, inputs[index]))
            assertEquals(marks.size - 1, mark.removeFromSet(set).size)
        }
    }
}
