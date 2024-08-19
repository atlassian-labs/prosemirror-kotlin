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

// import {Mark, MarkType, Slice, Fragment, NodeType} from "prosemirror-model"

// import {Step} from "./step"
// import {Transform} from "./transform"
// import {AddMarkStep, RemoveMarkStep} from "./mark_step"
// import {ReplaceStep} from "./replace_step"

// export function addMark(tr: Transform, from: number, to: number, mark: Mark) {
//     let removed: Step[] = [], added: Step[] = []
//     let removing: RemoveMarkStep | undefined, adding: AddMarkStep | undefined
//     tr.doc.nodesBetween(from, to, (node, pos, parent) => {
//         if (!node.isInline) return
//         let marks = node.marks
//                 if (!mark.isInSet(marks) && parent!.type.allowsMarkType(mark.type)) {
//         let start = Math.max(pos, from), end = Math.min(pos + node.nodeSize, to)
//         let newSet = mark.addToSet(marks)
//
//         for (let i = 0; i < marks.length; i++) {
//         if (!marks[i].isInSet(newSet)) {
//             if (removing && removing.to == start && removing.mark.eq(marks[i]))
//                 (removing as any).to = end
//             else
//                 removed.push(removing = new RemoveMarkStep(start, end, marks[i]))
//         }
//     }
//
//         if (adding && adding.to == start)
//             (adding as any).to = end
//         else
//             added.push(adding = new AddMarkStep(start, end, mark))
//     }
//     })
//
//     removed.forEach(s => tr.step(s))
//     added.forEach(s => tr.step(s))
// }
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

// export function removeMark(tr: Transform, from: number, to: number, mark?: Mark | MarkType | null) {
//     let matched: {style: Mark, from: number, to: number, step: number}[] = [], step = 0
//     tr.doc.nodesBetween(from, to, (node, pos) => {
//         if (!node.isInline) return
//         step++
//         let toRemove = null
//         if (mark instanceof MarkType) {
//             let set = node.marks, found
//             while (found = mark.isInSet(set)) {
//                 ;(toRemove || (toRemove = [])).push(found)
//                 set = found.removeFromSet(set)
//             }
//         } else if (mark) {
//             if (mark.isInSet(node.marks)) toRemove = [mark]
//         } else {
//             toRemove = node.marks
//         }
//         if (toRemove && toRemove.length) {
//             let end = Math.min(pos + node.nodeSize, to)
//             for (let i = 0; i < toRemove.length; i++) {
//                 let style = toRemove[i], found
//                 for (let j = 0; j < matched.length; j++) {
//                 let m = matched[j]
//                 if (m.step == step - 1 && style.eq(matched[j].style)) found = m
//             }
//                 if (found) {
//                     found.to = end
//                     found.step = step
//                 } else {
//                     matched.push({style, from: Math.max(pos, from), to: end, step})
//                 }
//             }
//         }
//     })
//     matched.forEach(m => tr.step(new RemoveMarkStep(m.from, m.to, m.style)))
// }
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

// export function clearIncompatible(tr: Transform, pos: number, parentType: NodeType,
//      match = parentType.contentMatch) {
//     let node = tr.doc.nodeAt(pos)!
//     let delSteps: Step[] = [], cur = pos + 1
//     for (let i = 0; i < node.childCount; i++) {
//         let child = node.child(i), end = cur + child.nodeSize
//         let allowed = match.matchType(child.type)
//         if (!allowed) {
//             delSteps.push(new ReplaceStep(cur, end, Slice.empty))
//         } else {
//             match = allowed
//             for (let j = 0; j < child.marks.length; j++) if (!parentType.allowsMarkType(child.marks[j].type))
//             tr.step(new RemoveMarkStep(cur, end, child.marks[j]))
//         }
//         cur = end
//     }
//     if (!match.validEnd) {
//         let fill = match.fillBefore(Fragment.empty, true)
//         tr.replace(cur, cur, new Slice(fill!, 0, 0))
//     }
//     for (let i = delSteps.length - 1; i >= 0; i--) tr.step(delSteps[i])
// }
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
