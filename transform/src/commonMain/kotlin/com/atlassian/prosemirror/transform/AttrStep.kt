package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.Slice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/// Update an attribute in a specific node.
class AttrStep(
    /// The position of the target node.
    val pos: Int,
    /// The attribute to set.
    val attr: String,
    // The attribute's new value.
    val value: Any?
) : Step() {
    override val from: Int
        get() = 0
    override val to: Int
        get() = 0

    override fun apply(doc: Node): StepResult {
        val node = doc.nodeAt(pos) ?: return StepResult.fail("No node at attribute step's position")
        val attrs = node.attrs.toMutableMap()
        attrs[attr] = value
        val updated = node.type.create(attrs, null as Fragment?, node.marks)
        return StepResult.fromReplace(doc, pos, pos + 1, Slice(Fragment.from(updated), 0, if (node.isLeaf) 0 else 1))
    }

    override fun getMap() = StepMap.empty

    override fun invert(doc: Node): Step {
        return AttrStep(pos, attr, doc.nodeAt(pos)!!.attrs[attr])
    }

    override fun map(mapping: Mappable): Step? {
        val pos = mapping.mapResult(this.pos, 1)
        return if (pos.deletedAfter) null else AttrStep(pos.pos, attr, value)
    }

    override fun toJSON() =
        buildJsonObject {
            put("stepType", "attr")
            put("pos", pos)
            put("attr", attr)
            put("value", value)
        }

    companion object : StepJsonParser<AttrStep> {
        init {
            jsonID("attr", this)
        }
        override fun fromJSON(schema: Schema, json: JsonObject): AttrStep {
            val pos = json["pos"]?.jsonPrimitive?.intOrNull ?: throw RangeError("Invalid input for AttrStep.fromJSON")
            val attr = json["attr"]?.jsonPrimitive?.contentOrNull ?: throw RangeError("Invalid input for AttrStep.fromJSON")
            val value = json["value"]?.jsonPrimitive?.anyOrNull()
            return AttrStep(pos, attr, value)
        }
    }
}

/// Update an attribute in the doc node.
class DocAttrStep(
    /// The attribute to set.
    val attr: String,
    // The attribute's new value.
    val value: Any?
) : Step() {
    override val from: Int
        get() = 0
    override val to: Int
        get() = 0

    override fun apply(doc: Node): StepResult {
        val attrs = doc.attrs.toMutableMap()
        attrs[attr] = value
        val updated = doc.type.create(attrs, doc.content, doc.marks)
        return StepResult.ok(updated)
    }

    override fun getMap() = StepMap.empty

    override fun invert(doc: Node): Step {
        return DocAttrStep(attr, doc.attrs[attr])
    }

    override fun map(mapping: Mappable): Step {
        return this
    }

    override fun toJSON() =
        buildJsonObject {
            put("stepType", "docAttr")
            put("attr", attr)
            put("value", value)
        }

    companion object : StepJsonParser<DocAttrStep> {
        init {
            jsonID("docAttr", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): DocAttrStep {
            val attr = json["attr"]?.jsonPrimitive?.contentOrNull ?: throw RangeError("Invalid input for DocAttrStep.fromJSON")
            val value = json["value"]?.jsonPrimitive?.anyOrNull()
            return DocAttrStep(attr, value)
        }
    }
}

fun JsonObjectBuilder.put(key: String, value: Any?): JsonElement? = value?.let {
    when (it) {
        is String -> put(key, it)
        is Int -> put(key, it)
        is Boolean -> put(key, it)
        else -> put(key, it.toString())
    }
}

fun JsonPrimitive.anyOrNull(): Any? = intOrNull ?: booleanOrNull ?: contentOrNull
