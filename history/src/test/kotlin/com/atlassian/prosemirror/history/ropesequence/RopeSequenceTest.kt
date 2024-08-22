package com.atlassian.prosemirror.history.ropesequence

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

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
            assertThat(elt).overridingErrorMessage("Proper element at $cur in $name").isEqualTo(cur + offset)
            assertThat(cur).overridingErrorMessage("Accurate index passed").isEqualTo(i)
            cur += 1
            true
        }, start, end)
        assertThat(cur).overridingErrorMessage("Enough elements iterated in $name").isEqualTo(end)
        rope.forEach({ elt, i ->
            cur -= 1
            assertThat(elt)
                .overridingErrorMessage("Proper element during reverse iter at $cur in $name")
                .isEqualTo(cur + offset)
            assertThat(cur)
                .overridingErrorMessage("Accurate index passed by reverse iter")
                .isEqualTo(i)
            true
        }, end, start)
        assertThat(cur)
            .overridingErrorMessage("Enough elements reverse-iterated in $name -- $cur $start")
            .isEqualTo(start)
    }

    fun check(rope: RopeSequence<Int>, size: Int, name: String, offset: Int = 0) {
        assertThat(rope.length)
            .overridingErrorMessage("Size of $name should be ${rope.length} but was $size")
            .isEqualTo(size)
        for (i in 0 until rope.length) {
            assertThat(rope.get(i)).overridingErrorMessage("Field at $i in $name").isEqualTo(offset + i)
        }
        checkForEach(rope, name, 0, rope.length, offset)
        val e = min(10, floor(size.toDouble() / 100).toInt())
        for (i in 0 until e) {
            var start = floor(Math.random() * size).toInt()
            val end = start + ceil(Math.random() * (size - start)).toInt()
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
        assertThat(small.append(empty)).overridingErrorMessage("ID append").isEqualTo(small)
        assertThat(small.prepend(empty)).overridingErrorMessage("ID prepend").isEqualTo(small)
        assertThat(empty.append(empty)).overridingErrorMessage("empty append").isEqualTo(empty)
        assertThat(small.slice(0, 0)).overridingErrorMessage("empty slice").isEqualTo(empty)

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
        assertThat(sum).overridingErrorMessage("abort iteration").isEqualTo(1)

        assertThat(small.map({ x, _ -> x + 1 })).overridingErrorMessage("mapping").isEqualTo(listOf(2, 3, 5))
    }

    companion object {
        const val SIZE = 10000
    }
}
