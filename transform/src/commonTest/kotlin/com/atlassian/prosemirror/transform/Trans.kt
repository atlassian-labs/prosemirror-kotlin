package com.atlassian.prosemirror.transform

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.tags

fun invert(transform: Transform): Transform {
    val out = Transform(transform.doc)
    for (i in transform.steps.size - 1 downTo 0) {
        out.step(transform.steps[i].invert(transform.docs[i]))
    }
    return out
}

fun testMapping(mapping: Mapping, pos: Int, newPos: Int) {
    val mapped = mapping.map(pos, 1)
    assertThat(mapped).isEqualTo(newPos)

    val remap = Mapping(mapping.maps.map { m -> m.invert() })
    var mapFrom = mapping.maps.size
    for (i in mapping.maps.size - 1 downTo 0) {
        remap.appendMap(mapping.maps[i], --mapFrom)
    }
    assertThat(remap.map(pos, 1)).isEqualTo(pos)
}

fun testStepJSON(tr: Transform) {
    val newTR = Transform(tr.before)
    tr.steps.forEach { step ->
        newTR.step(Step.fromJSON(tr.doc.type.schema, step.toJSON())!!)
    }
    assertThat(tr.doc).isEqualTo(newTR.doc)
}

fun testTransform(tr: Transform, expect: Node, useExpectFirstChild: Boolean = false) {
    val theExpect = if (useExpectFirstChild) expect.firstChild!! else expect
    val expectOffset = if (useExpectFirstChild) -1 else 0
    assertThat(tr.doc).isEqualTo(theExpect)
    assertThat(invert(tr).doc).isEqualTo(tr.before)

    testStepJSON(tr)

    tags(expect).forEach { tag ->
        val beforePos = pos(tr.before, tag)
        val afterPos = pos(expect, tag)!! + expectOffset
        testMapping(tr.mapping, beforePos!!, afterPos)
    }
}
