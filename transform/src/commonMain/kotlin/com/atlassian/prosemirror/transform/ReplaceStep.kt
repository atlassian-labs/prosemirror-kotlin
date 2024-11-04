package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.Slice
import kotlin.math.max
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Replace a part of the document with a slice of new content.
data class ReplaceStep(
    // The given `slice` should fit the 'gap' between `from` and `to`—the depths must line up, and
    // the surrounding nodes must be able to be joined with the open sides of the slice. When
    // `structure` is true, the step will fail if the content between from and to is not just a
    // sequence of closing and then opening tokens (this is to guard against rebased replace steps
    // overwriting something they weren't supposed to).

    // The start position of the replaced range.
    override val from: Int,
    // The end position of the replaced range.
    override val to: Int,
    // The slice to insert.
    val slice: Slice,
    internal val structure: Boolean = false
) : Step() {
    override fun apply(doc: Node): StepResult {
        if (this.structure && contentBetween(doc, this.from, this.to)) {
            return StepResult.fail("Structure replace would overwrite content")
        }
        return StepResult.fromReplace(doc, this.from, this.to, this.slice)
    }

    override fun getMap() = StepMap(listOf(this.from, this.to - this.from, this.slice.size))

    override fun invert(doc: Node): ReplaceStep {
        return ReplaceStep(this.from, this.from + this.slice.size, doc.slice(this.from, this.to))
    }

    override fun map(mapping: Mappable): ReplaceStep? {
        val from = mapping.mapResult(this.from, 1)
        val to = mapping.mapResult(this.to, -1)
        if (from.deletedAcross && to.deletedAcross) return null
        return ReplaceStep(from.pos, max(from.pos, to.pos), this.slice)
    }

    @Suppress("ReturnCount")
    override fun merge(other: Step): ReplaceStep? {
        if (other !is ReplaceStep || other.structure || this.structure) return null

        if (this.from + this.slice.size == other.from && this.slice.openEnd == 0 && other.slice.openStart == 0) {
            val slice = if (this.slice.size + other.slice.size == 0) {
                Slice.empty
            } else {
                Slice(this.slice.content.append(other.slice.content), this.slice.openStart, other.slice.openEnd)
            }
            return ReplaceStep(this.from, this.to + (other.to - other.from), slice, this.structure)
        } else if (other.to == this.from && this.slice.openStart == 0 && other.slice.openEnd == 0) {
            val slice = if (this.slice.size + other.slice.size == 0) {
                Slice.empty
            } else {
                Slice(other.slice.content.append(this.slice.content), other.slice.openStart, this.slice.openEnd)
            }
            return ReplaceStep(other.from, this.to, slice, this.structure)
        } else {
            return null
        }
    }

    override fun toJSON(): JsonObject {
        return buildJsonObject {
            put("stepType", "replace")
            put("from", from)
            put("to", to)
            if (slice.size != 0) slice.toJSON()?.let { json -> put("slice", json) }
            if (structure) put("structure", true)
        }
    }

    companion object : StepJsonParser<ReplaceStep> {
        init {
            jsonID("replace", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): ReplaceStep {
            val from = json["from"]?.jsonPrimitive?.int
            val to = json["to"]?.jsonPrimitive?.int
            if (from == null || to == null) {
                throw RangeError("Invalid input for ReplaceStep.fromJSON")
            }
            val slice = json["slice"]?.jsonObject
            val structure = json["structure"]?.jsonPrimitive?.booleanOrNull ?: false
            return ReplaceStep(from, to, Slice.fromJSON(schema, slice), structure)
        }
    }
}

// Replace a part of the document with a slice of content, but preserve a range of the replaced
// content by moving it into the slice.
class ReplaceAroundStep(
    // Create a replace-around step with the given range and gap. `insert` should be the point in
    // the slice into which the content of the gap should be moved. `structure` has the same meaning
    // as it has in the [`ReplaceStep`](#transform.ReplaceStep) class.

    // The start position of the replaced range.
    override val from: Int,
    // The end position of the replaced range.
    override val to: Int,
    // The start of preserved range.
    val gapFrom: Int,
    // The end of preserved range.
    val gapTo: Int,
    // The slice to insert.
    val slice: Slice,
    // The position in the slice where the preserved range should be inserted.
    val insert: Int,
    internal val structure: Boolean = false
) : Step() {

    @Suppress("ReturnCount")
    override fun apply(doc: Node): StepResult {
        if (this.structure &&
            (contentBetween(doc, this.from, this.gapFrom) || contentBetween(doc, this.gapTo, this.to))
        ) {
            return StepResult.fail("Structure gap-replace would overwrite content")
        }

        val gap = doc.slice(this.gapFrom, this.gapTo)
        if (gap.openStart != 0 || gap.openEnd != 0) {
            return StepResult.fail("Gap is not a flat range")
        }
        val inserted = this.slice.insertAt(this.insert, gap.content)
            ?: return StepResult.fail("Content does not fit in gap")
        return StepResult.fromReplace(doc, this.from, this.to, inserted)
    }

    override fun getMap() = StepMap(
        listOf(
            this.from,
            this.gapFrom - this.from,
            this.insert,
            this.gapTo,
            this.to - this.gapTo,
            this.slice.size - this.insert
        )
    )

    override fun invert(doc: Node): ReplaceAroundStep {
        val gap = this.gapTo - this.gapFrom
        return ReplaceAroundStep(
            from = this.from,
            to = this.from + this.slice.size + gap,
            gapFrom = this.from + this.insert,
            gapTo = this.from + this.insert + gap,
            slice = doc.slice(this.from, this.to).removeBetween(this.gapFrom - this.from, this.gapTo - this.from),
            insert = this.gapFrom - this.from,
            structure = this.structure
        )
    }

    @Suppress("ComplexCondition")
    override fun map(mapping: Mappable): ReplaceAroundStep? {
        val from = mapping.mapResult(this.from, 1)
        val to = mapping.mapResult(this.to, -1)
        val gapFrom = if (this.from == this.gapFrom) from.pos else mapping.map(this.gapFrom, -1)
        val gapTo = if (this.to == this.gapTo) to.pos else mapping.map(this.gapTo, 1)
        if ((from.deletedAcross && to.deletedAcross) || gapFrom < from.pos || gapTo > to.pos) return null
        return ReplaceAroundStep(from.pos, to.pos, gapFrom, gapTo, this.slice, this.insert, this.structure)
    }

    override fun toJSON() = buildJsonObject {
        put("stepType", "replaceAround")
        put("from", from)
        put("to", to)
        put("gapFrom", gapFrom)
        put("gapTo", gapTo)
        put("insert", insert)
        if (slice.size != 0) slice.toJSON()?.let { put("slice", it) }
        if (structure) put("structure", true)
    }

    companion object : StepJsonParser<ReplaceAroundStep> {
        init {
            jsonID("replaceAround", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): ReplaceAroundStep {
            val from = json["from"]?.jsonPrimitive?.int ?: 0
            val to = json["to"]?.jsonPrimitive?.int ?: 0
            val gapFrom = json["gapFrom"]?.jsonPrimitive?.int ?: 0
            val gapTo = json["gapTo"]?.jsonPrimitive?.int ?: 0
            val slice = json["slice"]?.jsonObject
            val insert = json["insert"]?.jsonPrimitive?.int ?: 0
            val structure = json["structure"]?.jsonPrimitive?.booleanOrNull ?: false
            return ReplaceAroundStep(
                from,
                to,
                gapFrom,
                gapTo,
                Slice.fromJSON(schema, slice),
                insert,
                structure
            )
        }
    }
}

fun contentBetween(doc: Node, from: Int, to: Int): Boolean {
    val resolvedFrom = doc.resolve(from)
    var dist = to - from
    var depth = resolvedFrom.depth
    while (dist > 0 && depth > 0 && resolvedFrom.indexAfter(depth) == resolvedFrom.node(depth).childCount) {
        depth--
        dist--
    }
    if (dist > 0) {
        var next = resolvedFrom.node(depth).maybeChild(resolvedFrom.indexAfter(depth))
        while (dist > 0) {
            if (next == null || next.isLeaf) return true
            next = next.firstChild
            dist--
        }
    }
    return false
}
