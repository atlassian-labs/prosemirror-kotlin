package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.model.parser.JSON
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Opt-in recovery for known nodes and marks with invalid attributes, using the schema's existing
 * unsupported types. Unknown types use the same recovery factories.
 *
 * Validation receives the original JSON before attribute decoding, defaults, or typed creation.
 * By default it checks attribute-object shape and the presence of every attribute without a default.
 * Override validation for additional input requirements, including fields required by an external
 * format even when the ProseMirror schema gives them defaults. Returning false requests recovery;
 * policy and creator exceptions propagate. Unfamiliar attributes are not rejected.
 *
 * The default factories reuse [SchemaSpec.unknownMarkAttrs] and [SchemaSpec.unknownNodeAttrs].
 * Override them to retain the complete original JSON in the application's unsupported representation,
 * including missing fields and explicit nulls. Recovery attributes must be stable across document
 * and step parsing for equality and removal. Internal IDs are separate from equality.
 *
 * Factories receive the selected unsupported type, parsed children, and parsed marks. They are not
 * validated recursively. Programmatic [MarkType.create] and [NodeType.create] do not use this policy.
 * Strict JSON parsing checks the resulting type; internal IDs are restored when requested.
 */
open class JsonParseRecovery {
    open fun isValidMark(type: MarkType, json: JsonObject): Boolean = hasRequiredAttributes(type.attrs, json)

    open fun isValidNode(type: NodeType, json: JsonObject): Boolean = hasRequiredAttributes(type.attrs, json)

    open fun createMark(type: MarkType, json: JsonObject): Mark {
        val name = json["type"]?.jsonPrimitive?.contentOrNull
        val attrs = readAttributes(json)
        val fallbackAttrs = if (name != null) {
            type.schema.spec.unknownMarkAttrs?.invoke(name, attrs) ?: attrs
        } else {
            attrs
        }
        return type.create(fallbackAttrs)
    }

    open fun createNode(type: NodeType, json: JsonObject, content: Fragment, marks: List<Mark>): Node {
        val name = json["type"]?.jsonPrimitive?.contentOrNull
        val attrs = readAttributes(json)
        val fallbackAttrs = if (name != null) {
            type.schema.spec.unknownNodeAttrs?.invoke(name, attrs) ?: attrs
        } else {
            attrs
        }
        return type.create(fallbackAttrs, content, marks)
    }
}

private fun hasRequiredAttributes(attrs: Map<String, Attribute>, json: JsonObject): Boolean {
    val raw = json["attrs"]
    if (raw != null && raw !is JsonObject) return false
    return attrs.all { (name, attr) -> !attr.isRequired || raw?.containsKey(name) == true }
}

private fun readAttributes(json: JsonObject): Attrs? = (json["attrs"] as? JsonObject)?.let {
    JSON.decodeFromJsonElement(it)
}
