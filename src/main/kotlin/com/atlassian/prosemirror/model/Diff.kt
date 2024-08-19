package com.atlassian.prosemirror.model

// import java.lang.Integer.min
import kotlin.math.min

// import {Fragment} from "./fragment"

// export function findDiffStart(a: Fragment, b: Fragment, pos: number): number | null {
//     for (let i = 0;; i++) {
//         if (i == a.childCount || i == b.childCount)
//             return a.childCount == b.childCount ? null : pos
//
//         let childA = a.child(i), childB = b.child(i)
//         if (childA == childB) { pos += childA.nodeSize; continue }
//
//         if (!childA.sameMarkup(childB)) return pos
//
//         if (childA.isText && childA.text != childB.text) {
//             for (let j = 0; childA.text![j] == childB.text![j]; j++)
//             pos++
//             return pos
//         }
//         if (childA.content.size || childB.content.size) {
//             let inner = findDiffStart(childA.content, childB.content, pos + 1)
//             if (inner != null) return inner
//         }
//         pos += childA.nodeSize
//     }
// }
fun findDiffStart(a: Fragment, b: Fragment, pos: Int): Int? {
    var currentPos = pos
    var i = 0
    while (true) {
        if (i == a.childCount || i == b.childCount) {
            return if (a.childCount == b.childCount) null else currentPos
        }

        val childA = a.child(i)
        val childB = b.child(i)
        if (childA == childB) {
            currentPos += childA.nodeSize
            continue
        }

        if (!childA.sameMarkup(childB)) return currentPos

        if (childA.isText && childA.text != childB.text) {
            var j = 0
            while (childA.text!![j] == childB.text!![j]) {
                j++
                currentPos++
            }
            return currentPos
        }
        if (childA.content.size != 0 || childB.content.size != 0) {
            val inner = findDiffStart(childA.content, childB.content, currentPos + 1)
            if (inner != null) return inner
        }
        currentPos += childA.nodeSize
        i++
    }
}

// export function findDiffEnd(a: Fragment, b: Fragment, posA: number, posB: number): {a: number, b: number} | null {
//     for (let iA = a.childCount, iB = b.childCount;;) {
//         if (iA == 0 || iB == 0)
//             return iA == iB ? null : {a: posA, b: posB}
//
//         let childA = a.child(--iA), childB = b.child(--iB), size = childA.nodeSize
//         if (childA == childB) {
//             posA -= size; posB -= size
//             continue
//         }
//
//         if (!childA.sameMarkup(childB)) return {a: posA, b: posB}
//
//         if (childA.isText && childA.text != childB.text) {
//             let same = 0, minSize = Math.min(childA.text!.length, childB.text!.length)
//             while (same < minSize && childA.text![childA.text!.length - same - 1] ==
//                  childB.text![childB.text!.length - same - 1]) {
//                 same++; posA--; posB--
//             }
//             return {a: posA, b: posB}
//         }
//         if (childA.content.size || childB.content.size) {
//             let inner = findDiffEnd(childA.content, childB.content, posA - 1, posB - 1)
//             if (inner) return inner
//         }
//         posA -= size; posB -= size
//     }
// }
@Suppress("ComplexMethod", "ReturnCount")
fun findDiffEnd(a: Fragment, b: Fragment, posA: Int, posB: Int): Pair<Int, Int>? {
    var posA = posA
    var posB = posB
    var iA = a.childCount
    var iB = b.childCount
    while (true) {
        if (iA == 0 || iB == 0) {
            return if (iA == iB) null else posA to posB
        }

        val childA = a.child(--iA)
        val childB = b.child(--iB)
        val size = childA.nodeSize
        if (childA == childB) {
            posA -= size
            posB -= size
            continue
        }

        if (!childA.sameMarkup(childB)) return posA to posB

        if (childA.isText && childA.text != childB.text) {
            val childAText = childA.text!!
            val childBText = childB.text!!

            var same = 0
            val minSize = min(childAText.length, childBText.length)
            while (
                same < minSize &&
                childAText[childAText.length - same - 1] == childBText[childBText.length - same - 1]
            ) {
                same++
                posA--
                posB--
            }
            return posA to posB
        }
        if (childA.content.size != 0 || childB.content.size != 0) {
            val inner = findDiffEnd(childA.content, childB.content, posA - 1, posB - 1)
            if (inner != null) return inner
        }
        posA -= size
        posB -= size
    }
}
