package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.p
import com.atlassian.prosemirror.testbuilder.schema
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

fun get(expr: String) = ContentMatch.parse(expr, schema.nodes)

fun match(expr: String, types: String): Boolean {
    var m: ContentMatch? = get(expr)
    val ts: List<NodeType> = if (types.isNotEmpty()) {
        types.split(" ").mapNotNull { t -> schema.nodes[t] }
    } else {
        emptyList()
    }
    for (type in ts)
        m = m?.matchType(type)
    return m?.validEnd == true
}

fun valid(expr: String, types: String) {
    assertThat(match(expr, types)).isTrue
}

fun invalid(expr: String, types: String) {
    assertThat(match(expr, types)).isFalse
}

fun fill(expr: String, before: Node, after: Node, result: Node?) {
    val filled = get(expr).matchFragment(before.content)?.fillBefore(after.content, true)
    if (result != null) {
        assertThat(filled).isEqualTo(result.content)
    } else {
        assertThat(filled).isNull()
    }
}

fun fill3(expr: String, before: Node, mid: Node, after: Node, left: Node?, right: Node? = null) {
    val content = get(expr)
    val a = content.matchFragment(before.content)?.fillBefore(mid.content)
    val b = a?.let {
        content.matchFragment(before.content.append(it).append(mid.content))?.fillBefore(after.content, true)
    }
    if (left != null && right != null) {
        assertThat(a).isEqualTo(left.content)
        assertThat(b).isEqualTo(right.content)
    } else {
        assertThat(b).isNull()
    }
}

class ContentTest {
    @Test
    fun `accepts empty content for the empty expr`() =
        valid("", "")

    @Test
    fun `doesn't accept content in the empty expr`() =
        invalid("", "image")

    @Test
    fun `matches nothing to an asterisk`() =
        valid("image*", "")

    @Test
    fun `matches one element to an asterisk`() =
        valid("image*", "image")

    @Test
    fun `matches multiple elements to an asterisk`() =
        valid("image*", "image image image image")

    @Test
    fun `only matches appropriate elements to an asterisk`() =
        invalid("image*", "image text")

    @Test
    fun `matches group members to a group`() =
        valid("inline*", "image text")

    @Test
    fun `doesn't match non-members to a group`() =
        invalid("inline*", "paragraph")

    @Test
    fun `matches an element to a choice expression`() =
        valid("(paragraph | heading)", "paragraph")

    @Test
    fun `doesn't match unmentioned elements to a choice expr`() =
        invalid("(paragraph | heading)", "image")

    @Test
    fun `matches a simple sequence`() =
        valid("paragraph horizontal_rule paragraph", "paragraph horizontal_rule paragraph")

    @Test
    fun `fails when a sequence is too long`() =
        invalid("paragraph horizontal_rule", "paragraph horizontal_rule paragraph")

    @Test
    fun `fails when a sequence is too short`() =
        invalid("paragraph horizontal_rule paragraph", "paragraph horizontal_rule")

    @Test
    fun `fails when a sequence starts incorrectly`() =
        invalid("paragraph horizontal_rule", "horizontal_rule paragraph horizontal_rule")

    @Test
    fun `accepts a sequence asterisk matching zero elements`() =
        valid("heading paragraph*", "heading")

    @Test
    fun `accepts a sequence asterisk matching multiple elts`() =
        valid("heading paragraph*", "heading paragraph paragraph")

    @Test
    fun `accepts a sequence plus matching one element`() =
        valid("heading paragraph+", "heading paragraph")

    @Test
    fun `accepts a sequence plus matching multiple elts`() =
        valid("heading paragraph+", "heading paragraph paragraph")

    @Test
    fun `fails when a sequence plus has no elements`() =
        invalid("heading paragraph+", "heading")

    @Test
    fun `fails when a sequence plus misses its start`() =
        invalid("heading paragraph+", "paragraph paragraph")

    @Test
    fun `accepts an optional element being present`() =
        valid("image?", "image")

    @Test
    fun `accepts an optional element being missing`() =
        valid("image?", "")

    @Test
    fun `fails when an optional element is present twice`() =
        invalid("image?", "image image")

    @Test
    fun `accepts a nested repeat`() =
        valid("(heading paragraph+)+", "heading paragraph heading paragraph paragraph")

    @Test
    fun `fails on extra input after a nested repeat`() =
        invalid("(heading paragraph+)+", "heading paragraph heading paragraph paragraph horizontal_rule")

    @Test
    fun `accepts a matching count`() =
        valid("hard_break{2}", "hard_break hard_break")

    @Test
    fun `rejects a count that comes up short`() =
        invalid("hard_break{2}", "hard_break")

    @Test
    fun `rejects a count that has too many elements`() =
        invalid("hard_break{2}", "hard_break hard_break hard_break")

    @Test
    fun `accepts a count on the lower bound`() =
        valid("hard_break{2, 4}", "hard_break hard_break")

    @Test
    fun `accepts a count on the upper bound`() =
        valid("hard_break{2, 4}", "hard_break hard_break hard_break hard_break")

    @Test
    fun `accepts a count between the bounds`() =
        valid("hard_break{2, 4}", "hard_break hard_break hard_break")

    @Test
    fun `rejects a sequence with too few elements`() =
        invalid("hard_break{2, 4}", "hard_break")

    @Test
    fun `rejects a sequence with too many elements`() =
        invalid("hard_break{2, 4}", "hard_break hard_break hard_break hard_break hard_break")

    @Test
    fun `rejects a sequence with a bad element after it`() =
        invalid("hard_break{2, 4} text*", "hard_break hard_break image")

    @Test
    fun `accepts a sequence with a matching element after it`() =
        valid("hard_break{2, 4} image?", "hard_break hard_break image")

    @Test
    fun `accepts an open range`() =
        valid("hard_break{2,}", "hard_break hard_break")

    @Test
    fun `accepts an open range matching many`() =
        valid("hard_break{2,}", "hard_break hard_break hard_break hard_break")

    @Test
    fun `rejects an open range with too few elements`() =
        invalid("hard_break{2,}", "hard_break")

    @Test
    fun `returns the empty fragment when things match`() =
        fill("paragraph horizontal_rule paragraph", doc { p {} + hr {} }, doc { p {} }, doc {})

    @Test
    fun `adds a node when necessary`() =
        fill("paragraph horizontal_rule paragraph", doc { p {} }, doc { p {} }, doc { hr {} })

    @Test
    fun `accepts an asterisk across the bound`() =
        fill("hard_break*", p { br {} }, p { br {} }, p {})

    @Test
    fun `accepts an asterisk only on the left`() =
        fill("hard_break*", p { br {} }, p {}, p {})

    @Test
    fun `accepts an asterisk only on the right`() =
        fill("hard_break*", p {}, p { br {} }, p {})

    @Test
    fun `accepts an asterisk with no elements`() =
        fill("hard_break*", p {}, p {}, p {})

    @Test
    fun `accepts a plus across the bound`() =
        fill("hard_break+", p { br {} }, p { br {} }, p {})

    @Test
    fun `adds an element for a content-less plus`() =
        fill("hard_break+", p {}, p {}, p { br {} })

    @Test
    fun `fails for a mismatched plus`() =
        fill("hard_break+", p {}, p { img {} }, null)

    @Test
    fun `accepts asterisk with content on both sides`() =
        fill("heading* paragraph*", doc { h1 {} }, doc { p {} }, doc {})

    @Test
    fun `accepts asterisk with no content after`() =
        fill("heading* paragraph*", doc { h1 {} }, doc {}, doc {})

    @Test
    fun `accepts plus with content on both sides`() =
        fill("heading+ paragraph+", doc { h1 {} }, doc { p {} }, doc {})

    @Test
    fun `accepts plus with no content after`() =
        fill("heading+ paragraph+", doc { h1 {} }, doc {}, doc { p {} })

    @Test
    fun `adds elements to match a count`() =
        fill("hard_break{3}", p { br {} }, p { br {} }, p { br {} })

    @Test
    fun `fails when there are too many elements`() =
        fill("hard_break{3}", p { br {} + br {} }, p { br {} + br {} }, null)

    @Test
    fun `adds elements for two counted groups`() =
        fill("code_block{2} paragraph{2}", doc { pre {} }, doc { p {} }, doc { pre {} + p {} })

    @Test
    fun `doesn't include optional elements`() =
        fill("heading paragraph? horizontal_rule", doc { h1 {} }, doc {}, doc { hr {} })

    @Test
    fun `completes a sequence`() = fill3(
        "paragraph horizontal_rule paragraph horizontal_rule paragraph",
        doc { p {} },
        doc { p {} },
        doc { p {} },
        doc { hr {} },
        doc { hr {} }
    )

    @Test
    fun `accepts plus across two bounds`() =
        fill3("code_block+ paragraph+", doc { pre {} }, doc { pre {} }, doc { p {} }, doc {}, doc {})

    @Test
    fun `fills a plus from empty input`() =
        fill3("code_block+ paragraph+", doc {}, doc {}, doc {}, doc {}, doc { pre {} + p {} })

    @Test
    fun `completes a count`() = fill3(
        "code_block{3} paragraph{3}",
        doc { pre {} },
        doc { p {} },
        doc {},
        doc { pre {} + pre {} },
        doc { p {} + p {} }
    )

    @Test
    fun `fails on non-matching elements`() = fill3("paragraph*", doc { p {} }, doc { pre {} }, doc { p {} }, null)

    @Test
    fun `completes a plus across two bounds`() =
        fill3("paragraph{4}", doc { p {} }, doc { p {} }, doc { p {} }, doc {}, doc { p {} })

    @Test
    fun `refuses to complete an overflown count across two bounds`() =
        fill3("paragraph{2}", doc { p {} }, doc { p {} }, doc { p {} }, null)
}
