package com.atlassian.prosemirror.collab

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.tags
import com.atlassian.prosemirror.testbuilder.schema
import com.atlassian.prosemirror.transform.Transform
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

fun runRebase(transforms: List<Transform>, expected: Node) {
    val before = transforms.first().before
    val full = Transform(before)
    transforms.forEach { transform ->
        val rebased = Transform(transform.doc)
        val start = transform.steps.size + full.steps.size
        rebaseSteps(
            transform.steps.mapIndexed { i, s ->
                Rebaseable(step = s, inverted = s.invert(transform.docs[i]), origin = transform)
            },
            full.steps,
            rebased
        )
        for (i in start until rebased.steps.size) {
            full.step(rebased.steps[i])
        }
    }

    assertThat(full.doc).isEqualTo(expected)

    tags(before).forEach { tag ->
        val mapped = full.mapping.mapResult(pos(before, tag)!!)
        val exp = pos(expected, tag)
        if (mapped.deleted) {
            if (exp != null) error("Tag $tag was unexpectedly deleted")
        } else {
            if (exp == null) error("Tag $tag is not actually deleted")
            assertThat(mapped.pos).isEqualTo(exp)
        }
    }
}

fun <T> permute(array: List<T>): List<List<T>> {
    if (array.size < 2) return listOf(array)
    val result = mutableListOf<List<T>>()
    for (i in array.indices) {
        val others = permute(array.subList(0, i) + array.drop(i + 1))
        for (element in others) {
            result.add(listOf(array[i]) + element)
        }
    }
    return result
}

fun rebase(doc: Node, vararg clients: ((tr: Transform) -> Transform), expected: Node) {
    runRebase(clients.asList().map { func -> func(Transform(doc)) }, expected)
}

fun rebase_(doc: Node, vararg clients: ((tr: Transform) -> Transform), expected: Node) {
    permute(clients.asList().map { func -> func(Transform(doc)) })
        .forEach { transforms -> runRebase(transforms, expected) }
}

fun type(tr: Transform, pos: Int, text: String) =
    tr.replaceWith(pos, pos, schema.text(text))

fun wrap(tr: Transform, pos: Int, type: String): Transform {
    val resolvedPos = tr.doc.resolve(pos)
    return tr.wrap(resolvedPos.blockRange(resolvedPos)!!, listOf(NodeBase(type = schema.nodes[type]!!)))
}

class RebaseStepsTest {
    @Test
    fun `supports concurrent typing`() {
        rebase_(
            doc = doc { p { +"h<1>ell<2>o" } },
            { tr -> type(tr, 2, "X") },
            { tr -> type(tr, 5, "Y") },
            expected = doc { p { +"hX<1>ellY<2>o" } }
        )
    }

    @Test
    fun `support multiple concurrently typed chars`() {
        rebase_(
            doc = doc { p { +"h<1>ell<2>o" } },
            { tr -> type(type(type(tr, 2, "X"), 3, "Y"), 4, "Z") },
            { tr -> type(type(tr, 5, "U"), 6, "V") },
            expected = doc { p { +"hXYZ<1>ellUV<2>o" } }
        )
    }

    @Test
    fun `supports three concurrent typers`() {
        rebase_(
            doc = doc { p { +"h<1>ell<2>o th<3>ere" } },
            { tr -> type(tr, 2, "X") },
            { tr -> type(tr, 5, "Y") },
            { tr -> type(tr, 9, "Z") },
            expected = doc { p { +"hX<1>ellY<2>o thZ<3>ere" } }
        )
    }

    @Test
    fun `handles wrapping of changed blocks`() {
        rebase_(
            doc = doc { p { +"<1>hell<2>o<3>" } },
            { tr -> type(tr, 5, "X") },
            { tr -> wrap(tr, 1, "blockquote") },
            expected = doc { blockquote { p { +"<1>hellX<2>o<3>" } } }
        )
    }

    @Test
    fun `handles insertions in deleted content`() {
        rebase_(
            doc = doc { p { +"hello<1> wo<2>rld<3>!" } },
            { tr -> tr.delete(6, 12) },
            { tr -> type(tr, 9, "X") },
            expected = doc { p { +"hello<3>!" } }
        )
    }

    @Test
    fun `allows deleting the same content twice`() {
        rebase(
            doc = doc { p { +"hello<1> wo<2>rld<3>!" } },
            { tr -> tr.delete(6, 12) },
            { tr -> tr.delete(6, 12) },
            expected = doc { p { +"hello<3>!" } }
        )
    }

    @Test
    fun `isn't confused by joining a block that's being edited`() {
        rebase_(
            doc = doc { ul { li { p { +"one" } } + "<1>" + li { p { +"tw<2>o" } } } },
            { tr -> type(tr, 12, "A") },
            { tr -> tr.join(8) },
            expected = doc { ul { li { p { +"one" } + p { +"twA<2>o" } } } }
        )
    }

    @Test
    fun `supports typing concurrently with marking`() {
        rebase(
            doc = doc { p { +"hello <1>wo<2>rld<3>" } },
            { tr -> tr.addMark(7, 12, schema.mark("em")) },
            { tr -> type(tr, 9, "_") },
            expected = doc { p { +"hello <1>" + em { +"wo" } + "_<2>" + em { +"rld<3>" } } }
        )
    }

    @Test
    fun `doesn't unmark marks added concurrently`() {
        rebase(
            doc = doc { p { em { +"<1>hello" } + " world<2>" } },
            { tr -> tr.addMark(1, 12, schema.mark("em")) },
            { tr -> tr.removeMark(1, 12, schema.mark("em")) },
            expected = doc { p { +"<1>hello" + em { +" world<2>" } } }
        )
    }

    @Test
    fun `doesn't mark concurrently unmarked text`() {
        rebase(
            doc = doc { p { +"<1>hello " + em { +"world<2>" } } },
            { tr -> tr.removeMark(1, 12, schema.mark("em")) },
            { tr -> tr.addMark(1, 12, schema.mark("em")) },
            expected = doc { p { em { +"<1>hello " } + "world<2>" } }
        )
    }

    @Test
    fun `deletes inserts in replaced context`() {
        val initialDoc = doc {
            p { +"b<before>efore" } +
                blockquote { ul { li { p { +"o<1>ne" } } + li { p { +"t<2>wo" } } + li { p { +"thr<3>ee" } } } } +
                p { +"a<after>fter" }
        }

        val pos1 = pos("1")!!
        val pos2 = pos("2")!!
        val pos3 = pos("3")!!
        rebase(
            doc = initialDoc,
            { tr ->
                tr.replace(pos1, pos3, doc { p { +"a" } + blockquote { p { +"b" } } + p { +"c" } }.slice(2, 9))
            },
            { tr -> type(tr, pos2, "ayay") },
            expected = doc {
                p { +"b<before>efore" } +
                    blockquote { ul { li { p { +"o" } + blockquote { p { +"b" } } + p { +"<3>ee" } } } } +
                    p { +"a<after>fter" }
            }
        )
    }

    @Test
    fun `maps through inserts`() {
        rebase_(
            doc = doc { p { +"X<1>X<2>X" } },
            { tr -> type(tr, 2, "hello") },
            { tr -> type(tr, 3, "goodbye").delete(4, 7) },
            expected = doc { p { +"Xhello<1>Xgbye<2>X" } }
        )
    }

    @Test
    fun `handle concurrent removal of blocks`() {
        val initDoc = doc { p { +"a" } + "<1>" + p { +"b" } + "<2>" + p { +"c" } }
        val pos1 = pos("1")!!
        val pos2 = pos("2")!!
        rebase(
            doc = initDoc,
            { tr -> tr.delete(pos1, pos2) },
            { tr -> tr.delete(pos1, pos2) },
            expected = doc { p { +"a" } + "<2>" + p { +"c" } }
        )
    }

    @Test
    fun `discards edits in removed blocks`() {
        val initDoc = doc { p { +"a" } + "<1>" + p { +"b<2>" } + "<3>" + p { +"c" } }
        val pos1 = pos("1")!!
        val pos2 = pos("2")!!
        val pos3 = pos("3")!!
        rebase_(
            doc = initDoc,
            { tr -> tr.delete(pos1, pos3) },
            { tr -> type(tr, pos2, "ay") },
            expected = doc { p { +"a" } + "<3>" + p { +"c" } }
        )
    }

    @Test
    fun `preserves double block inserts`() {
        rebase(
            doc = doc { p { +"a" } + "<1>" + p { +"b" } },
            { tr -> tr.replaceWith(3, 3, schema.node("paragraph")) },
            { tr -> tr.replaceWith(3, 3, schema.node("paragraph")) },
            expected = doc { p { +"a" } + p {} + p {} + "<1>" + p { +"b" } }
        )
    }
}
