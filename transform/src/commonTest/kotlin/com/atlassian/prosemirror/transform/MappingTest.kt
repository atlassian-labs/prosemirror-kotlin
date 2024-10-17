package com.atlassian.prosemirror.transform

<<<<<<<< HEAD:transform/src/test/kotlin/com/atlassian/prosemirror/transform/MappingTest.kt
========
import assertk.assertThat
import assertk.assertions.isEqualTo
>>>>>>>> main:transform/src/commonTest/kotlin/com/atlassian/prosemirror/transform/MappingTest.kt
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat

data class Case(val from: Int, val to: Int, val bias: Int = 0, val lossy: Boolean = false)

fun testMapping(mapping: Mapping, vararg cases: Case) {
    val inverted = mapping.invert()
    for (case in cases) {
        with(case) {
            assertThat(mapping.map(from, bias)).isEqualTo(to)
            if (!lossy) {
                assertThat(inverted.map(to, bias)).isEqualTo(from)
            }
        }
    }
}

fun testDel(mapping: Mapping, pos: Int, side: Int, flags: String) {
    val r = mapping.mapResult(pos, side)
    var found = ""
    if (r.deleted) found += "d"
    if (r.deletedBefore) found += "b"
    if (r.deletedAfter) found += "a"
    if (r.deletedAcross) found += "x"
    assertThat(found).isEqualTo(flags)
}

fun mk(vararg args: List<Int>, mirror: Pair<Int, Int>? = null): Mapping {
    val mapping = Mapping()
    args.forEach { arg ->
        mapping.appendMap(StepMap(arg))
    }
    mirror?.let {
        mapping.setMirror(it.first, it.second)
    }
    return mapping
}

class MappingTest {
    @Test
    fun `can map through a single insertion`() =
        testMapping(mk(listOf(2, 0, 4)), Case(0, 0), Case(2, 6), Case(2, 2, -1), Case(3, 7))

    @Test
    fun `can map through a single deletion`() =
        testMapping(
            mk(listOf(2, 4, 0)),
            Case(0, 0),
            Case(2, 2, -1),
            Case(3, 2, 1, true),
            Case(6, 2, 1),
            Case(6, 2, -1, true),
            Case(7, 3)
        )

    @Test
    fun `can map through a single replace`() =
        testMapping(
            mk(listOf(2, 4, 4)),
            Case(0, 0),
            Case(2, 2, 1),
            Case(4, 6, 1, true),
            Case(4, 2, -1, true),
            Case(6, 6, -1),
            Case(8, 8)
        )

    @Test
    fun `can map through a mirrorred delete-insert`() =
        testMapping(
            mk(listOf(2, 4, 0), listOf(2, 0, 4), mirror = Pair(0, 1)),
            Case(0, 0),
            Case(2, 2),
            Case(4, 4),
            Case(6, 6),
            Case(7, 7)
        )

    @Test
    fun `cap map through a mirrorred insert-delete`() =
        testMapping(
            mk(listOf(2, 0, 4), listOf(2, 4, 0), mirror = Pair(0, 1)),
            Case(0, 0),
            Case(2, 2),
            Case(3, 3)
        )

    @Test
    fun `can map through an delete-insert with an insert in between`() =
        testMapping(
            mk(listOf(2, 4, 0), listOf(1, 0, 1), listOf(3, 0, 4), mirror = Pair(0, 2)),
            Case(0, 0),
            Case(1, 2),
            Case(4, 5),
            Case(6, 7),
            Case(7, 8)
        )

    @Test
    fun `assigns the correct deleted flags when deletions happen before`() {
        testDel(mk(listOf(0, 2, 0)), 2, -1, "db")
        testDel(mk(listOf(0, 2, 0)), 2, 1, "b")
        testDel(mk(listOf(0, 2, 2)), 2, -1, "db")
        testDel(mk(listOf(0, 1, 0), listOf(0, 1, 0)), 2, -1, "db")
        testDel(mk(listOf(0, 1, 0)), 2, -1, "")
    }

    @Test
    fun `assigns the correct deleted flags when deletions happen after`() {
        testDel(mk(listOf(2, 2, 0)), 2, -1, "a")
        testDel(mk(listOf(2, 2, 0)), 2, 1, "da")
        testDel(mk(listOf(2, 2, 2)), 2, 1, "da")
        testDel(mk(listOf(2, 1, 0), listOf(2, 1, 0)), 2, 1, "da")
        testDel(mk(listOf(3, 2, 0)), 2, -1, "")
    }

    @Test
    fun `assigns the correct deleted flags when deletions happen across`() {
        testDel(mk(listOf(0, 4, 0)), 2, -1, "dbax")
        testDel(mk(listOf(0, 4, 0)), 2, 1, "dbax")
        testDel(mk(listOf(0, 4, 0)), 2, 1, "dbax")
        testDel(mk(listOf(0, 1, 0), listOf(4, 1, 0), listOf(0, 3, 0)), 2, 1, "dbax")
    }

    @Test
    fun `assigns the correct deleted flags when deletions happen around`() {
        testDel(mk(listOf(4, 1, 0), listOf(0, 1, 0)), 2, -1, "")
        testDel(mk(listOf(2, 1, 0), listOf(0, 2, 0)), 2, -1, "dba")
        testDel(mk(listOf(2, 1, 0), listOf(0, 1, 0)), 2, -1, "a")
        testDel(mk(listOf(3, 1, 0), listOf(0, 2, 0)), 2, -1, "db")
    }
}
