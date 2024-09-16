package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.ContentMatch
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Slice
import kotlin.math.max
import kotlin.math.min

@Suppress("ComplexMethod")
fun addMark(tr: Transform, from: Int, to: Int, mark: Mark) {
    val removed = mutableListOf<Step>()
    val added = mutableListOf<Step>()
    var removing: RemoveMarkStep? = null
    var adding: AddMarkStep? = null
    tr.doc.nodesBetween(from, to, { node: Node, pos: Int, parent: Node?, index: Int ->
        if (!node.isInline) return@nodesBetween true
        val marks = node.marks
        if (!mark.isInSet(marks) && parent!!.type.allowsMarkType(mark.type)) {
            val start = max(pos, from)
            val end = min(pos + node.nodeSize, to)
            val newSet = mark.addToSet(marks)

            for (mark in marks) {
                if (!mark.isInSet(newSet)) {
                    val r = removing
                    if (r != null && r.to == start && r.mark == mark) {
                        r.to = end
                    } else {
                        removing = RemoveMarkStep(start, end, mark).also { removed.add(it) }
                    }
                }
            }

            val currentAdding = adding
            if (currentAdding != null && currentAdding.to == start) {
                currentAdding.to = end
            } else {
                adding = AddMarkStep(start, end, mark).also { added.add(it) }
            }
        }
        false
    })

    removed.forEach { s -> tr.step(s) }
    added.forEach { s -> tr.step(s) }
}

data class MatchedMark(val style: Mark, val from: Int, var to: Int, var step: Int)
fun removeMark(tr: Transform, from: Int, to: Int, mark: Mark?) {
    val matched = mutableListOf<MatchedMark>()
    var step = 0
    tr.doc.nodesBetween(from, to, { node: Node, pos: Int, parent: Node?, index: Int ->
        if (!node.isInline) return@nodesBetween true
        step++
        var toRemove: List<Mark>? = null
        if (mark != null) {
            if (mark.isInSet(node.marks)) toRemove = listOf(mark)
        } else {
            toRemove = node.marks
        }
        if (toRemove != null && toRemove.isNotEmpty()) {
            val end = min(pos + node.nodeSize, to)
            for (style in toRemove) {
                var found: MatchedMark? = null
                for (m in matched) {
                    if (m.step == step - 1 && style == m.style) found = m
                }
                if (found != null) {
                    found.to = end
                    found.step = step
                } else {
                    matched.add(MatchedMark(style, from = max(pos, from), to = end, step))
                }
            }
        }
        false
    })
    matched.forEach { m ->
        tr.step(RemoveMarkStep(m.from, m.to, m.style))
    }
}

fun removeMark(tr: Transform, from: Int, to: Int, mark: MarkType?) {
    data class MatchedMark(val style: Mark, val from: Int, var to: Int, var step: Int)
    val matched = mutableListOf<MatchedMark>()
    var step = 0
    tr.doc.nodesBetween(from, to, { node: Node, pos: Int, parent: Node?, index: Int ->
        if (!node.isInline) return@nodesBetween false
        step++
        var toRemove: MutableList<Mark>? = null
        if (mark != null) {
            var set = node.marks
            var found = mark.isInSet(set)
            while (found != null) {
                if (toRemove == null) toRemove = mutableListOf()
                toRemove.add(found)
                set = found.removeFromSet(set)
                found = mark.isInSet(set)
            }
        } else {
            toRemove = node.marks.toMutableList()
        }
        if (toRemove != null && toRemove.isNotEmpty()) {
            val end = min(pos + node.nodeSize, to)
            for (style in toRemove) {
                var found: MatchedMark? = null
                for (m in matched) {
                    if (m.step == step - 1 && style == m.style) found = m
                }
                if (found != null) {
                    found.to = end
                    found.step = step
                } else {
                    matched.add(MatchedMark(style, from = max(pos, from), to = end, step))
                }
            }
        }
        false
    })
    matched.forEach { m ->
        tr.step(RemoveMarkStep(m.from, m.to, m.style))
    }
}

fun clearIncompatible(tr: Transform, pos: Int, parentType: NodeType, match: ContentMatch? = parentType.contentMatch) {
    var match = match ?: parentType.contentMatch
    val node = tr.doc.nodeAt(pos)!!
    val delSteps = mutableListOf<Step>()
    var cur = pos + 1
    for (i in 0 until node.childCount) {
        val child = node.child(i)
        val end = cur + child.nodeSize
        val allowed = match.matchType(child.type)
        if (allowed == null) {
            delSteps.add(ReplaceStep(cur, end, Slice.empty))
        } else {
            match = allowed
            for (j in 0 until child.marks.size)
                if (!parentType.allowsMarkType(child.marks[j].type)) {
                    tr.step(RemoveMarkStep(cur, end, child.marks[j]))
                }
        }
        cur = end
    }
    if (!match.validEnd) {
        val fill = match.fillBefore(Fragment.empty, true)
        tr.replace(cur, cur, Slice(fill!!, 0, 0))
    }
    for (i in delSteps.size - 1 downTo 0) {
        tr.step(delSteps[i])
    }
}
