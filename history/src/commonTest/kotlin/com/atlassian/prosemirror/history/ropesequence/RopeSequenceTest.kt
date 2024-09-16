package com.atlassian.prosemirror.history.ropesequence

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test

class RopeSequenceTest {

    fun appendBuild(n: Int): RopeSequence<Int> {
        var rope = RopeSequence.empty<Int>()
        for (i in 0 until n) {
            rope = rope.append(listOf(i))
        }
        return rope
    }

    fun dequeBuild(n: Int): RopeSequence<Int> {
        var mid = n shr 1
        var rope = RopeSequence.empty<Int>()
        var from = mid - 1
        var to = mid
        while (to < n) {
            rope = rope.append(listOf(to))
            if (from >= 0) rope = RopeSequence.from(listOf(from)).append(rope)
            from--
            to++
        }
        return rope
    }

    fun flatBuild(n: Int): RopeSequence<Int> {
        val arr = buildList {
            for (i in 0 until n) add(i)
        }
        return RopeSequence.from(arr)
    }

    fun checkForEach(rope: RopeSequence<Int>, name: String, start: Int, end: Int, offset: Int) {
        var cur = start
        rope.forEach({ elt, i ->
            assertThat(elt, displayActual = { "Proper element at $cur in $name. Expected ${cur + offset} but was $it." })
                .isEqualTo(cur + offset)
            assertThat(cur, displayActual = { "Accurate index passed. Expected $i but was $it." }).isEqualTo(i)
            cur += 1
            true
        }, start, end)
        assertThat(cur, displayActual = { "Enough elements iterated in $name. Expected $end but was $it." }).isEqualTo(end)
        rope.forEach({ elt, i ->
            cur -= 1
            assertThat(
                elt,
                displayActual = { "Proper element during reverse iter at $cur in $name. Expected ${cur + offset} but was $it." }
            )
            .isEqualTo(cur + offset)
            assertThat(cur, displayActual = { "Accurate index passed by reverse iter. Expected $i but was $it." })
                .isEqualTo(i)
            true
        }, end, start)
        assertThat(cur, displayActual = { "Enough elements reverse-iterated in $name -- $cur $start. Expected $start but was $it." })
            .isEqualTo(start)
    }

    fun check(rope: RopeSequence<Int>, size: Int, name: String, offset: Int = 0) {
        assertThat(rope.length, displayActual = { "Size of $name should be ${rope.length} but was $size." })
            .isEqualTo(size)
        for (i in 0 until rope.length) {
            assertThat(
                rope.get(i),
                displayActual = { "Field at $i in $name. Expected ${offset + i} but was $it." }
            ).isEqualTo(offset + i)
        }
        checkForEach(rope, name, 0, rope.length, offset)
        val e = min(10, floor(size.toDouble() / 100).toInt())
        for (i in 0 until e) {
            val start = Random.nextInt(size - 1)
            val end = start + Random.nextInt(size - start)
            checkForEach(rope, "$name-$start-$end", start, end, offset)
            check(rope.slice(start, end), end - start, "$name-sliced-$start-$end", offset + start)
        }
    }

    @Test
    fun checkAppendBuild() {
        check(appendBuild(SIZE), SIZE, "appended")
    }

    @Test
    fun checkdequeBuild() {
        check(dequeBuild(SIZE), SIZE, "dequed")
    }

    @Test
    fun checkFlatBuild() {
        check(flatBuild(SIZE), SIZE, "flat")
    }

    @Test
    fun checkSmalAndEmpty() {
        val small = RopeSequence.from(listOf(1, 2, 4))
        val empty = RopeSequence.empty<Int>()
        assertThat(small.append(empty), displayActual = { "ID append. Expected $small but was $it." }).isEqualTo(small)
        assertThat(small.prepend(empty), displayActual = { "ID prepend. Expected $small but was $it." }).isEqualTo(small)
        assertThat(empty.append(empty), displayActual = { "Empty append. Expected $empty but was $it." }).isEqualTo(empty)
        assertThat(small.slice(0, 0), displayActual = { "Empty slice. Expected $empty but was $it." }).isEqualTo(empty)

        var sum = 0
        small.forEach(
            { v, _ ->
                if (v == 2) {
                    false
                } else {
                    sum += v
                    true
                }
            }
        )
        assertThat(sum, displayActual = { "abort iteration. Expected 1 but was $it." }).isEqualTo(1)

        assertThat(
            small.map({ x, _ -> x + 1 }),
            displayActual = { "mapping. Expected ${listOf(2, 3, 5)} but was $it." }
        ).isEqualTo(listOf(2, 3, 5))
    }

    companion object {
        const val SIZE = 10000
    }
}
