package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.model.parser.JSON
import java.io.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

// import {compareDeep} from "./comparedeep"
// import {Attrs, MarkType, Schema} from "./schema"

// region Mark
// /// A mark is a piece of information that can be attached to a node,
// /// such as it being emphasized, in code font, or a link. It has a
// /// type and optionally a set of attributes that provide further
// /// information (such as the target of the link). Marks are created
// /// through a `Schema`, which controls which types exist and which
// /// attributes they have.
// export class Mark {
//     /// @internal
//     constructor(
//         /// The type of this mark.
//         readonly type: MarkType,
//         /// The attributes associated with this mark.
//         readonly attrs: Attrs
//     ) {}
//
//     /// Given a set of marks, create a new set which contains this one as
//     /// well, in the right position. If this mark is already in the set,
//     /// the set itself is returned. If any marks that are set to be
//     /// [exclusive](#model.MarkSpec.excludes) with this mark are present,
//     /// those are replaced by this one.
//     addToSet(set: readonly Mark[]): readonly Mark[] {
//         let copy, placed = false
//         for (let i = 0; i < set.length; i++) {
//             let other = set[i]
//             if (this.eq(other)) return set
//             if (this.type.excludes(other.type)) {
//                 if (!copy) copy = set.slice(0, i)
//             } else if (other.type.excludes(this.type)) {
//                 return set
//             } else {
//                 if (!placed && other.type.rank > this.type.rank) {
//                     if (!copy) copy = set.slice(0, i)
//                     copy.push(this)
//                     placed = true
//                 }
//                 if (copy) copy.push(other)
//             }
//         }
//         if (!copy) copy = set.slice()
//         if (!placed) copy.push(this)
//         return copy
//     }
//
//     /// Remove this mark from the given set, returning a new set. If this
//     /// mark is not in the set, the set itself is returned.
//     removeFromSet(set: readonly Mark[]): readonly Mark[] {
//         for (let i = 0; i < set.length; i++)
//         if (this.eq(set[i]))
//             return set.slice(0, i).concat(set.slice(i + 1))
//         return set
//     }
//
//     /// Test whether this mark is in the given set of marks.
//     isInSet(set: readonly Mark[]) {
//         for (let i = 0; i < set.length; i++)
//         if (this.eq(set[i])) return true
//         return false
//     }
//
//     /// Test whether this mark has the same type and attributes as
//     /// another mark.
//     eq(other: Mark) {
//         return this == other ||
//                 (this.type == other.type && compareDeep(this.attrs, other.attrs))
//     }
//
//     /// Convert this mark to a JSON-serializeable representation.
//     toJSON(): any {
//         let obj: any = {type: this.type.name}
//         for (let _ in this.attrs) {
//             obj.attrs = this.attrs
//             break
//         }
//         return obj
//     }
//
//     /// Deserialize a mark from JSON.
//     static fromJSON(schema: Schema, json: any) {
//         if (!json) throw new RangeError("Invalid input for Mark.fromJSON")
//         let type = schema.marks[json.type]
//         if (!type) throw new RangeError(`There is no mark type ${json.type} in this schema`)
//         return type.create(json.attrs)
//     }
//
//     /// Test whether two sets of marks are identical.
//     static sameSet(a: readonly Mark[], b: readonly Mark[]) {
//         if (a == b) return true
//         if (a.length != b.length) return false
//         for (let i = 0; i < a.length; i++)
//         if (!a[i].eq(b[i])) return false
//         return true
//     }
//
//     /// Create a properly sorted mark set from null, a single mark, or an
//     /// unsorted array of marks.
//     static setFrom(marks?: Mark | readonly Mark[] | null): readonly Mark[] {
//         if (!marks || Array.isArray(marks) && marks.length == 0) return Mark.none
//         if (marks instanceof Mark) return [marks]
//         let copy = marks.slice()
//         copy.sort((a, b) => a.type.rank - b.type.rank)
//         return copy
//     }
//
//     /// The empty set of marks.
//     static none: readonly Mark[] = []
// }
// endregion

interface UnsupportedMark {
    var originalMarkName: String?
}

// Indicates that text that contains that mark should not be serialised to Json when
// sanitised = true parameter is provided
interface JsonOmitMark

@JvmInline
value class MarkId(val id: String) : Serializable

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
    var markId: MarkId = MarkId("${type.name}&${UUID.randomUUID()}")

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
        fun fromJSON(schema: Schema, json: JsonObject?, withId: Boolean = false): Mark {
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
            return if (withId && id != null) {
                type.create(attrs).also {
                    it.markId = MarkId(id)
                    (it as? UnsupportedMark)?.originalMarkName = jsonType
                }
            } else {
                type.create(attrs).also { (it as? UnsupportedMark)?.originalMarkName = jsonType }
            }
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
