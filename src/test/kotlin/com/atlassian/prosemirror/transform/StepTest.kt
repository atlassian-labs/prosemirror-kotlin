package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.schema
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

val testDoc = doc { p { +"foobar" } }

fun mkStep(from: Int, to: Int, value: String?): Step {
    return when (value) {
        "+em" -> AddMarkStep(from, to, schema.marks["em"]!!.create())
        "-em" -> RemoveMarkStep(from, to, schema.marks["em"]!!.create())
        else -> ReplaceStep(
            from,
            to,
            if (value == null) Slice.empty else Slice(Fragment.from(schema.text(value)), 0, 0)
        )
    }
}

class StepTest {
    fun yes(from1: Int, to1: Int, val1: String?, from2: Int, to2: Int, val2: String?) {
        val step1 = mkStep(from1, to1, val1)
        val step2 = mkStep(from2, to2, val2)
        val merged = step1.merge(step2)
        assertThat(merged).isNotNull
        assertThat(merged!!.apply(testDoc).doc).isEqualTo(step2.apply(step1.apply(testDoc).doc!!).doc)
    }

    fun no(from1: Int, to1: Int, val1: String?, from2: Int, to2: Int, val2: String?) {
        val step1 = mkStep(from1, to1, val1)
        val step2 = mkStep(from2, to2, val2)
        assertThat(step1.merge(step2)).isNull()
    }

    @Test
    fun `merges typing changes`() = yes(2, 2, "a", 3, 3, "b")

    @Test
    fun `merges inverse typing`() = yes(2, 2, "a", 2, 2, "b")

    @Test
    fun `doesn't merge separated typing`() = no(2, 2, "a", 4, 4, "b")

    @Test
    fun `doesn't merge inverted separated typing`() = no(3, 3, "a", 2, 2, "b")

    @Test
    fun `merges adjacent backspaces`() = yes(3, 4, null, 2, 3, null)

    @Test
    fun `merges adjacent deletes`() = yes(2, 3, null, 2, 3, null)

    @Test
    fun `doesn't merge separate backspaces`() = no(1, 2, null, 2, 3, null)

    @Test
    fun `merges backspace and type`() = yes(2, 3, null, 2, 2, "x")

    @Test
    fun `merges longer adjacent inserts`() = yes(2, 2, "quux", 6, 6, "baz")

    @Test
    fun `merges inverted longer inserts`() = yes(2, 2, "quux", 2, 2, "baz")

    @Test
    fun `merges longer deletes`() = yes(2, 5, null, 2, 4, null)

    @Test
    fun `merges inverted longer deletes`() = yes(4, 6, null, 2, 4, null)

    @Test
    fun `merges overwrites`() = yes(3, 4, "x", 4, 5, "y")

    @Test
    fun `merges adding adjacent styles`() = yes(1, 2, "+em", 2, 4, "+em")

    @Test
    fun `merges adding overlapping styles`() = yes(1, 3, "+em", 2, 4, "+em")

    @Test
    fun `doesn't merge separate styles`() = no(1, 2, "+em", 3, 4, "+em")

    @Test
    fun `merges removing adjacent styles`() = yes(1, 2, "-em", 2, 4, "-em")

    @Test
    fun `merges removing overlapping styles`() = yes(1, 3, "-em", 2, 4, "-em")

    @Test
    fun `doesn't merge removing separate styles`() = no(1, 2, "-em", 3, 4, "-em")
}
