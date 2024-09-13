package com.atlassian.prosemirror.model

import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class NodeSelectionTest {
    fun Node.findNode(f: (Node) -> Boolean): Pair<Node, Int>? {
        var result: Pair<Node, Int>? = null
        descendants { node, pos, _, _ ->
            if (f(node)) {
                result = node to pos
            }
            true
        }
        return result
    }

    @Test
    fun `char based position resolve`() {
        val doc = sampleDoc()
        val fooParagraph = doc.findNode { it.firstChild?.text?.startsWith("foo") == true }!!.first
        val fooContent = fooParagraph.textContent
        assertThat(
            doc.textBetween(
                doc.resolve(fooParagraph, 2)!!.pos,
                doc.resolve(fooParagraph, 4)!!.pos
            )
        ).isEqualTo(fooContent.substring(2, 4))
        assertThat(
            doc.textBetween(
                doc.resolve(fooParagraph, 1)!!.pos,
                doc.resolve(fooParagraph, 6)!!.pos
            )
        ).isEqualTo(fooContent.substring(1, 6))
        assertThat(
            doc.textBetween(
                doc.resolve(fooParagraph, 3)!!.pos,
                doc.resolve(fooParagraph, 8)!!.pos
            )
        ).isEqualTo(fooContent.substring(3, 8))
        assertThat(
            doc.textBetween(
                doc.resolve(fooParagraph, 11)!!.pos,
                doc.resolve(fooParagraph, 13)!!.pos
            )
        ).isEqualTo(fooContent.substring(11, 13))
    }

    @Test
    fun `empty block node resolove`() {
        val doc = emptyCodeBlockDoc()
        val codeBlock = doc.findNode { it.type.name == "code_block" }?.first!!
        assertThat(doc.resolve(codeBlock, 0)?.node(1)).isEqualTo(codeBlock)
    }

    @Test
    fun `text node resolve`() {
        val doc = simpleSampleDoc()
        val barTextNode = doc.findNode {
            it.text == "bar"
        }!!.first
        assertThat(doc.resolve(barTextNode, 0)!!.pos).isEqualTo(4)
    }

    @Test
    fun `toCharInd method logic`() {
        val doc = sampleDoc()
        val (fooParagraph, pos) = doc.findNode { it.firstChild?.text?.startsWith("foo") == true }!!
        assertThat(fooParagraph.toCharInd(doc.resolve(fooParagraph, 2)!!.pos - pos)).isEqualTo(2)
        assertThat(fooParagraph.toCharInd(doc.resolve(fooParagraph, 8)!!.pos - pos)).isEqualTo(8)
        assertThat(fooParagraph.toCharInd(doc.resolve(fooParagraph, 13)!!.pos - pos)).isEqualTo(13)
        assertThat(fooParagraph.toCharInd(doc.resolve(fooParagraph, 0)!!.pos - pos)).isEqualTo(0)
    }

    @Test
    fun `textBetween across nodes`() {
        val doc = sampleDoc()
        val fooParagraph = doc.findNode { it.firstChild?.text?.startsWith("foo") == true }!!.first
        val bParagraph = doc.findNode { it.firstChild?.text?.startsWith("b") == true }!!.first
        assertThat(
            doc.textBetween(
                doc.resolve(fooParagraph, 2)!!.pos,
                doc.resolve(bParagraph, 1)!!.pos
            )
        ).isEqualTo("obarbaz \nx endb")
    }

    private fun sampleDoc() = doc {
        blockquote {
            ul {
                li {
                    p { +"a" } +
                        p {
                            +"f<a>oo" +
                                em { +"b<b>ar" + strong { +"<c>baz<d>" } } +
                                " " +
                                br { } +
                                a { +"x" } +
                                " end"
                        }
                    p { +"b" }
                } +
                    li {
                        p {
                            img {}
                        }
                    } +
                    p { +"c" }
            } + p { +"d" }
        }
    }

    private fun simpleSampleDoc() = doc {
        p {
            +"f<a>oo" +
                em { +"b<b>ar" + strong { +"<c>baz<d>" } } +
                " " +
                br { } +
                a { +"x" } +
                " end"
        }
    }

    private fun emptyCodeBlockDoc() = doc {
        p { +"yeet" }
        pre {}
    }
}
