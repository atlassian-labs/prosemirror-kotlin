package com.atlassian.prosemirror.model

import kotlin.math.min

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
