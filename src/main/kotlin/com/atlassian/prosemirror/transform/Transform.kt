package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.ContentMatch
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.NodeRange
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Slice

class TransformError(message: String, cause: Throwable? = null) : Error(message, cause)

// Abstraction to build up and track an array of [steps](#transform.Step) representing a document
// transformation.
//
// Most transforming methods return the `Transform` object itself, so that they can be chained.
open class Transform(
    // Create a transform that starts with the given document.
    // The current document (the result of applying the steps in the transform).
    var doc: Node
) {
    // The steps in this transform.
    val steps: MutableList<Step> = mutableListOf()

    // The documents before each of the steps.
    val docs: MutableList<Node> = mutableListOf()

    // A mapping with the maps for each of the steps in this transform.
    val mapping: Mapping = Mapping()

    // The starting document.
    val before: Node
        get() = this.docs.firstOrNull() ?: this.doc

    // True when the document has been changed (when there are any steps).
    val docChanged: Boolean
        get() = this.steps.isNotEmpty()

    // Apply a new step in this transform, saving the result. Throws an error when the step fails.
    fun step(step: Step): Transform {
        val result = this.maybeStep(step)
        if (result.failed != null) {
            throw TransformError(result.failed)
        }
        return this
    }

    // Try to apply a step in this transformation, ignoring it if it fails. Returns the step result.
    fun maybeStep(step: Step): StepResult {
        val result = step.apply(this.doc)
        if (result.failed == null) this.addStep(step, result.doc!!)
        return result
    }

    internal open fun addStep(step: Step, doc: Node) {
        this.docs.add(this.doc)
        this.steps.add(step)
        this.mapping.appendMap(step.getMap())
        this.doc = doc
    }

    // Replace the part of the document between `from` and `to` with the given `slice`.
    fun replace(from: Int, to: Int = from, slice: Slice = Slice.empty): Transform {
        val step = replaceStep(this.doc, from, to, slice)
        if (step != null) this.step(step)
        return this
    }

    // Replace the given range with the given content, which may be a fragment, node, or array of nodes.
    fun replaceWith(from: Int, to: Int, content: Fragment): Transform {
        return this.replace(from, to, Slice(Fragment.from(content), 0, 0))
    }

    fun replaceWith(from: Int, to: Int, content: Node): Transform {
        return this.replace(from, to, Slice(Fragment.from(content), 0, 0))
    }

    fun replaceWith(from: Int, to: Int, content: List<Node>): Transform {
        return this.replace(from, to, Slice(Fragment.from(content), 0, 0))
    }

    // Delete the content between the given positions.
    fun delete(from: Int, to: Int): Transform {
        return this.replace(from, to, Slice.empty)
    }

    // Insert the given content at the given position.
    fun insert(pos: Int, content: Fragment) = this.replaceWith(pos, pos, content)
    fun insert(pos: Int, content: Node) = this.replaceWith(pos, pos, content)
    fun insert(pos: Int, content: List<Node>) = this.replaceWith(pos, pos, content)

    // Replace a range of the document with a given slice, using `from`, `to`, and the slice's
    // [`openStart`](#model.Slice.openStart) property as hints, rather than fixed start and end
    // points. This method may grow the replaced area or close open nodes in the slice in order to
    // get a fit that is more in line with WYSIWYG expectations, by dropping fully covered parent
    // nodes of the replaced region when they are marked
    // [non-defining as context](#model.NodeSpec.definingAsContext), or including an open parent
    // node from the slice that _is_ marked as
    // [defining its content](#model.NodeSpec.definingForContent).
    //
    // This is the method, for example, to handle paste. The similar
    // [`replace`](#transform.Transform.replace) method is a more primitive tool which will _not_
    // move the start and end of its given range, and is useful in situations where you need more
    // precise control over what happens.
    fun replaceRange(from: Int, to: Int, slice: Slice) = this.apply {
        replaceRange(this, from, to, slice)
    }

    // Replace the given range with a node, but use `from` and `to` as hints, rather than precise
    // positions. When from and to are the same and are at the start or end of a parent node in
    // which the given node doesn't fit, this method may _move_ them out towards a parent that does
    // allow the given node to be placed. When the given range completely covers a parent node, this
    // method may completely replace that parent node.
    fun replaceRangeWith(from: Int, to: Int, node: Node) = this.apply {
        replaceRangeWith(this, from, to, node)
    }

    // Delete the given range, expanding it to cover fully covered parent nodes until a valid
    // replace is found.
    fun deleteRange(from: Int, to: Int) = this.apply {
        deleteRange(this, from, to)
    }

    // Split the content in the given range off from its parent, if there is sibling content before
    // or after it, and move it up the tree to the depth specified by `target`. You'll probably want
    // to use [`liftTarget`](#transform.liftTarget) to compute `target`, to make sure the lift is
    // valid.
    fun lift(range: NodeRange, target: Int) = this.apply {
        lift(this, range, target)
    }

    // Join the blocks around the given position. If depth is 2, their last and first siblings are
    // also joined, and so on.
    fun join(pos: Int, depth: Int = 1) = this.apply {
        join(this, pos, depth)
    }

    // Wrap the given [range](#model.NodeRange) in the given set of wrappers. The wrappers are a
    // assumed to be valid in this position, and should probably be computed with
    // [`findWrapping`](#transform.findWrapping).
    fun wrap(range: NodeRange, wrappers: List<NodeBase>) = this.apply {
        wrap(this, range, wrappers)
    }

    // Set the type of all textblocks (partly) between `from` and `to` to the given node type with
    // the given attributes.
    fun setBlockType(from: Int, to: Int = from, type: NodeType, attrs: Attrs? = null) = this.apply {
        setBlockType(this, from, to, type, attrs)
    }

    // Change the type, attributes, and/or marks of the node at `pos`. When `type` isn't given, the
    // existing node type is preserved,
    fun setNodeMarkup(pos: Int, type: NodeType?, attrs: Attrs? = null, marks: List<Mark>? = null) = this.apply {
        setNodeMarkup(this, pos, type, attrs, marks)
    }

    // Split the node at the given position, and optionally, if `depth` is greater than one, any
    // number of nodes above that. By default, the parts split off will inherit the node type of the
    // original node. This can be changed by passing an array of types and attributes to use after
    // the split.
    fun split(pos: Int, depth: Int = 1, typesAfter: List<NodeBase>? = null) = this.apply {
        split(this, pos, depth, typesAfter)
    }

    // Add the given mark to the inline content between `from` and `to`.
    fun addMark(from: Int, to: Int, mark: Mark) = this.apply {
        addMark(this, from, to, mark)
    }

    // Remove marks from inline nodes between `from` and `to`. When `mark` is a single mark, remove
    // precisely that mark. When it is a mark type, remove all marks of that type. When it is null,
    // remove all marks of any type.
    fun removeMark(from: Int, to: Int, mark: Mark?) = this.apply {
        removeMark(this, from, to, mark)
    }

    fun removeMark(from: Int, to: Int, mark: MarkType?) = this.apply {
        removeMark(this, from, to, mark)
    }

    // Removes all marks and nodes from the content of the node at `pos` that don't match the given
    // new parent node type. Accepts an optional starting [content match](#model.ContentMatch) as
    // third argument.
    fun clearIncompatible(pos: Int, parentType: NodeType, match: ContentMatch? = null) = this.apply {
        clearIncompatible(this, pos, parentType, match)
    }

    companion object {
        const val TAG = "PM Transform"
    }
}
