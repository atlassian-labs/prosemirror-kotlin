package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.Slice
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.max
import kotlin.math.min

fun mapFragment(fragment: Fragment, f: (child: Node, parent: Node, i: Int) -> Node, parent: Node): Fragment {
    val mapped = mutableListOf<Node>()
    for (i in 0 until fragment.childCount) {
        var child = fragment.child(i)
        if (child.content.size != 0) child = child.copy(mapFragment(child.content, f, child))
        if (child.isInline) child = f(child, parent, i)
        mapped.add(child)
    }
    return Fragment.fromArray(mapped)
}

// Add a mark to all inline content between two positions.
class AddMarkStep(
    // Create a mark step.
    // The start of the marked range.
    override val from: Int,
    // The end of the marked range.
    override var to: Int,
    // The mark to add.
    val mark: Mark
) : Step() {

    override fun apply(doc: Node): StepResult {
        val oldSlice = doc.slice(this.from, this.to)
        val resolvedFrom = doc.resolve(this.from)
        val nodeParent = resolvedFrom.node(resolvedFrom.sharedDepth(this.to))
        val slice = Slice(
            mapFragment(oldSlice.content, { node: Node, parent: Node, i: Int ->
                if (!node.isAtom || !parent.type.allowsMarkType(this.mark.type)) {
                    node
                } else {
                    node.mark(this.mark.addToSet(node.marks))
                }
            }, nodeParent),
            oldSlice.openStart,
            oldSlice.openEnd
        )
        return StepResult.fromReplace(doc, this.from, this.to, slice)
    }

    override fun invert(doc: Node): Step {
        return RemoveMarkStep(this.from, this.to, this.mark)
    }

    override fun map(mapping: Mappable): Step? {
        val from = mapping.mapResult(this.from, 1)
        val to = mapping.mapResult(this.to, -1)
        if (from.deleted && to.deleted || from.pos >= to.pos) return null
        return AddMarkStep(from.pos, to.pos, this.mark)
    }

    @Suppress("ComplexCondition")
    override fun merge(other: Step): Step? {
        return if (other is AddMarkStep &&
            other.mark == this.mark &&
            this.from <= other.to &&
            this.to >= other.from
        ) {
            AddMarkStep(
                min(this.from, other.from),
                max(this.to, other.to),
                this.mark
            )
        } else {
            null
        }
    }

    override fun toJSON(): JsonObject {
        return buildJsonObject {
            put("stepType", "addMark")
            put("mark", mark.toJSON())
            put("from", from)
            put("to", to)
        }
    }

    companion object : StepJsonParser<AddMarkStep> {
        init {
            jsonID("addMark", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): AddMarkStep {
            val from: Int? = json["from"]?.jsonPrimitive?.int
            val to: Int? = json["to"]?.jsonPrimitive?.int
            if (from == null || to == null) {
                throw RangeError("Invalid input for AddMarkStep.fromJSON")
            }
            return AddMarkStep(from, to, schema.markFromJSON(json["mark"]?.jsonObject))
        }
    }
}

// Remove a mark from all inline content between two positions.
class RemoveMarkStep(
    // Create a mark-removing step.
    // The start of the unmarked range.
    override val from: Int,
    // The end of the unmarked range.
    override var to: Int,
    // The mark to remove.
    val mark: Mark
) : Step() {

    override fun apply(doc: Node): StepResult {
        val oldSlice = doc.slice(this.from, this.to)
        val slice = Slice(
            mapFragment(oldSlice.content, { node: Node, parent: Node, i: Int ->
                node.mark(this.mark.removeFromSet(node.marks))
            }, doc),
            oldSlice.openStart,
            oldSlice.openEnd
        )
        return StepResult.fromReplace(doc, this.from, this.to, slice)
    }

    override fun invert(doc: Node): Step {
        return AddMarkStep(this.from, this.to, this.mark)
    }

    override fun map(mapping: Mappable): Step? {
        val from = mapping.mapResult(this.from, 1)
        val to = mapping.mapResult(this.to, -1)
        if (from.deleted && to.deleted || from.pos >= to.pos) return null
        return RemoveMarkStep(from.pos, to.pos, this.mark)
    }

    @Suppress("ComplexCondition")
    override fun merge(other: Step): Step? {
        return if (other is RemoveMarkStep &&
            other.mark == this.mark &&
            this.from <= other.to && this.to >= other.from
        ) {
            RemoveMarkStep(min(this.from, other.from), max(this.to, other.to), this.mark)
        } else {
            null
        }
    }

    override fun toJSON() = buildJsonObject {
        put("stepType", "removeMark")
        put("mark", mark.toJSON())
        put("from", from)
        put("to", to)
    }

    companion object : StepJsonParser<RemoveMarkStep> {
        init {
            jsonID("removeMark", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): RemoveMarkStep {
            val from = json.get("from")?.jsonPrimitive?.intOrNull
            val to = json.get("to")?.jsonPrimitive?.intOrNull
            if (from == null || to == null) {
                throw RangeError("Invalid input for RemoveMarkStep.fromJSON")
            }
            return RemoveMarkStep(from, to, schema.markFromJSON(json["mark"]?.jsonObject))
        }
    }
}
/// Add a mark to a specific node.
class AddNodeMarkStep(
    /// The position of the target node.
    val pos: Int,
    /// The mark to add.
    val mark: Mark
) : Step() {
    override val from: Int
        get() = 0
    override val to: Int
        get() = 0

    override fun apply(doc: Node): StepResult {
        val node = doc.nodeAt(pos) ?: return StepResult.fail("No node at mark step's position")
        val updated = node.type.create(node.attrs, null as Fragment?, mark.addToSet(node.marks))
        return StepResult.fromReplace(doc, pos, pos + 1, Slice(Fragment.from(updated), 0, if (node.isLeaf) 0 else 1))
    }

    override fun invert(doc: Node): Step {
        val node = doc.nodeAt(pos)
        if (node != null) {
            val newSet = mark.addToSet(node.marks)
            if (newSet.size == node.marks.size) {
                for (i in node.marks.indices) {
                    if (!node.marks[i].isInSet(newSet)) {
                        return AddNodeMarkStep(pos, node.marks[i])
                    }
                }
                return AddNodeMarkStep(pos, mark)
            }
        }
        return RemoveNodeMarkStep(pos, mark)
    }

    override fun map(mapping: Mappable): Step? {
        val pos = mapping.mapResult(this.pos, 1)
        return if (pos.deletedAfter) null else AddNodeMarkStep(pos.pos, this.mark)
    }

    override fun toJSON() = buildJsonObject {
        put("stepType", "addNodeMark")
        put("pos", pos)
        put("mark", mark.toJSON())
    }

    companion object : StepJsonParser<AddNodeMarkStep> {
        init {
            jsonID("addNodeMark", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): AddNodeMarkStep {
            val pos = json.get("pos")?.jsonPrimitive?.intOrNull ?: throw RangeError("Invalid input for AddNodeMarkStep.fromJSON")
            return AddNodeMarkStep(pos, schema.markFromJSON(json["mark"]?.jsonObject))
        }
    }
}

/// Remove a mark from a specific node.
class RemoveNodeMarkStep(
    /// The position of the target node.
    val pos: Int,
    /// The mark to remove.
    val mark: Mark
) : Step() {
    override val from: Int
        get() = 0
    override val to: Int
        get() = 0

    override fun apply(doc: Node): StepResult {
        val node = doc.nodeAt(pos) ?: return StepResult.fail("No node at mark step's position")
        val updated = node.type.create(node.attrs, null as Fragment?, mark.removeFromSet(node.marks))
        return StepResult.fromReplace(doc, pos, pos + 1, Slice(Fragment.from(updated), 0, if (node.isLeaf) 0 else 1))
    }

    override fun invert(doc: Node): Step {
        val node = doc.nodeAt(pos)
        if (node == null || !mark.isInSet(node.marks)) return this
        return AddNodeMarkStep(pos, mark)
    }

    override fun map(mapping: Mappable): Step? {
        val pos = mapping.mapResult(this.pos, 1)
        return if (pos.deletedAfter) null else RemoveNodeMarkStep(pos.pos, this.mark)
    }

    override fun toJSON() = buildJsonObject {
            put("stepType", "removeNodeMark")
            put("pos", pos)
            put("mark", mark.toJSON())
        }

    companion object : StepJsonParser<RemoveNodeMarkStep> {
        init {
            jsonID("removeNodeMark", this)
        }

        override fun fromJSON(schema: Schema, json: JsonObject): RemoveNodeMarkStep {
            val pos = json.get("pos")?.jsonPrimitive?.intOrNull ?: throw RangeError("Invalid input for RemoveNodeMarkStep.fromJSON")
            return RemoveNodeMarkStep(pos, schema.markFromJSON(json["mark"]?.jsonObject))
        }
    }
}
