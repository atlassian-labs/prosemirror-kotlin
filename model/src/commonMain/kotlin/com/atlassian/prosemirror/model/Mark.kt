package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.model.parser.JSON
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface UnsupportedMark {
    var originalMarkName: String?
}

@JvmInline
@kotlinx.serialization.Serializable
value class MarkId(val id: String)

// A mark is a piece of information that can be attached to a node, such as it being emphasized, in
// code font, or a link. It has a type and optionally a set of attributes that provide further
// information (such as the target of the link). Marks are created through a `Schema`, which
// controls which types exist and which attributes they have.
@Suppress("EqualsWithHashCodeExist")
open class Mark constructor(
    // The type of this mark.
    val type: MarkType,
    // The attributes associated with this mark.
    val attrs: Attrs
) {
    @OptIn(ExperimentalUuidApi::class)
    var markId: MarkId = MarkId("${type.name}&${Uuid.random()}")

    // Given a set of marks, create a new set which contains this one as well, in the right
    // position. If this mark is already in the set, the set itself is returned. If any marks that
    // are set to be [exclusive](#model.MarkSpec.excludes) with this mark are present, those are
    // replaced by this one.
    @Suppress("NestedBlockDepth", "ReturnCount")
    fun addToSet(set: List<Mark>): List<Mark> {
        var copy: MutableList<Mark>? = null
        var placed = false
        for (i in set.indices) {
            val other = set[i]
            if (this == other) return set
            if (this.type.excludes(other.type)) {
                if (copy == null) copy = set.slice(0 until i).toMutableList()
            } else if (other.type.excludes(this.type)) {
                return set
            } else {
                if (!placed && other.type.rank > this.type.rank) {
                    if (copy == null) copy = set.slice(0 until i).toMutableList()
                    copy.add(this)
                    placed = true
                }
                copy?.add(other)
            }
        }
        if (copy == null) copy = set.toMutableList()
        if (!placed) copy.add(this)
        return copy.toList()
    }

    // Remove this mark from the given set, returning a new set. If this mark is not in the set, the
    // set itself is returned.
    fun removeFromSet(set: List<Mark>): List<Mark> {
        return set - this
    }

    // Test whether this mark is in the given set of marks.
    fun isInSet(set: List<Mark>): Boolean {
        return set.contains(this)
    }

    // Test whether this mark has the same type and attributes as  another mark.
    override fun equals(other: Any?): Boolean {
        return (this === other) ||
            (other is Mark && this.type == other.type && compareDeep(this.attrs, other.attrs))
    }

    // Convert this mark to a JSON-serializable representation.
    open fun toJSON(withId: Boolean = false) = buildJsonObject {
        toJson(this, withId)
    }

    protected open fun toJson(builder: JsonObjectBuilder, withId: Boolean = false) = builder.apply {
        put("type", type.name)
        if (withId) {
            put("id", markId.id)
        }
        if (attrs.isNotEmpty()) {
            put("attrs", JSON.encodeToJsonElement(attrs))
        }
    }

    companion object {
        // The empty set of marks.
        val none = emptyList<Mark>()

        // Deserialize a mark from JSON.
        fun fromJSON(schema: Schema, json: JsonObject?, withId: Boolean = false, check: Boolean = false): Mark {
            if (json == null) throw RangeError("Invalid input for Mark.fromJSON")
            val jsonType = json["type"]?.jsonPrimitive?.contentOrNull
            val type = schema.marks[jsonType]
                ?: schema.marks[jsonType?.lowercase()]
                ?: schema.marks[schema.spec.unsupportedMark]
                ?: throw RangeError(
                    "There is no mark type '$jsonType' in this schema and 'unsupportedMark' not defined as well"
                )
            val attrs: Attrs? = json["attrs"]?.let { JSON.decodeFromJsonElement(it) }
            val id = json["id"]?.jsonPrimitive?.contentOrNull
            val mark = if (withId && id != null) {
                type.create(attrs).also {
                    it.markId = MarkId(id)
                    (it as? UnsupportedMark)?.originalMarkName = jsonType
                }
            } else {
                type.create(attrs).also { (it as? UnsupportedMark)?.originalMarkName = jsonType }
            }
            if (check) {
                type.checkAttrs(mark.attrs)
            }
            return mark
        }

        // Test whether two sets of marks are identical.
        fun sameSet(a: List<Mark>, b: List<Mark>): Boolean {
            return a == b // Aleksei: TODO confirm if it works fine
//            if (a == b) return true
//            if (a.length != b.length) return false
//            for (let i = 0; i < a.length; i++)
//            if (!a[i].eq(b[i])) return false
//            return true
        }

        // Create a properly sorted mark set from null, a single mark, or an
        // unsorted array of marks.
        fun setFrom(marks: Mark?): List<Mark> {
            if (marks == null) return none
            return listOf(marks)
        }

        fun setFrom(marks: List<Mark>?): List<Mark> {
            if (marks.isNullOrEmpty()) return none
            return marks.sortedWith { a, b -> a.type.rank - b.type.rank }
        }
    }
}
