package com.atlassian.prosemirror.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.atlassian.prosemirror.testbuilder.AttributeSpecImpl
import com.atlassian.prosemirror.testbuilder.CustomNodeBuildCompanion
import com.atlassian.prosemirror.testbuilder.CustomNodeBuilder
import com.atlassian.prosemirror.testbuilder.MarkSpecImpl
import com.atlassian.prosemirror.testbuilder.NodeBuilder
import com.atlassian.prosemirror.testbuilder.NodeSpecImpl
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.doc
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos
import com.atlassian.prosemirror.testbuilder.schema as testSchema
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node as DOMNode
import com.fleeksoft.ksoup.nodes.TextNode
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DomTest {
    //region DOMParser
    fun test(doc: Node, html: String) {
        val schema = doc.type.schema
        val innerHTML = DOMSerializer.fromSchema(schema).serializeFragmentToHtml(doc.content)
        assertThat(innerHTML).isEqualTo(html)
        val parsedDoc = DOMParser.fromSchema(schema).parseHtml(innerHTML)
        assertThat(parsedDoc).isEqualTo(doc)
    }

    @Test
    fun `can represent simple node`() {
        test(doc { p { +"hello" } }, "<p>hello</p>")
    }

    @Test
    fun `can represent a line break`() {
        test(doc { p { +"hi" + br {} + "there" } }, "<p>hi<br>there</p>")
    }

    @Test
    fun `can represent an image`() {
        test(
            doc { p { +"hi" + img(mapOf("alt" to "x")) {} + "there" } },
            "<p>hi<img src=\"img.png\" alt=\"x\">there</p>"
        )
    }

    @Test
    fun `joins styles`() {
        test(
            doc { p { +"one" + strong { +"two" + em { +"three" } } + em { +"four" } + "five" } },
            "<p>one<strong>two</strong><em><strong>three</strong>four</em>five</p>"
        )
    }

    @Test
    fun `can represent links`() {
        // custom link mark that has a title=null attribute
        fun NodeBuilder<PMNodeBuilder>.aWithTitle(href: String = "foo", func: NodeBuilder<PMNodeBuilder>.() -> Unit) =
            mark("link", func, attrs = mapOf("href" to href, "title" to null))

        test(
            // TypeScript code: doc(p("a ", a({href: "foo"}, "big ", a({href: "bar"}, "nested"), " link")))
            // converts to the code below because each node cannot have more than 1 Link mark
            doc {
                p {
                    +"a " +
                        aWithTitle(href = "foo") { +"big " } +
                        aWithTitle(href = "bar") { +"nested" } +
                        aWithTitle(href = "foo") { +" link" }
                }
            },
            "<p>a <a href=\"foo\">big </a><a href=\"bar\">nested</a><a href=\"foo\"> link</a></p>"
        )
    }

    @Test
    fun `can represent and unordered list`() {
        test(
            doc {
                ul {
                    li { p { +"one" } } +
                        li { p { +"two" } } +
                        li { p { +"three" + strong { +"!" } } }
                } +
                    p { +"after" }
            },
            "<ul><li><p>one</p></li><li><p>two</p></li><li><p>three<strong>!</strong></p></li></ul><p>after</p>"
        )
    }

    @Test
    fun `can represent an ordered list`() {
        test(
            doc {
                ol {
                    li { p { +"one" } } +
                        li { p { +"two" } } +
                        li { p { +"three" + strong { +"!" } } }
                } +
                    p { +"after" }
            },
            "<ol><li><p>one</p></li><li><p>two</p></li><li><p>three<strong>!</strong></p></li></ol><p>after</p>"
        )
    }

    @Test
    fun `can represent a blockquote`() {
        test(
            doc { blockquote { p { +"hello" } + p { +"bye" } } },
            "<blockquote><p>hello</p><p>bye</p></blockquote>"
        )
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `can represent a nested blockquote`() {
        test(
            doc { blockquote { blockquote { blockquote { p { +"he said" } } } + p { +"i said" } } },
            "<blockquote><blockquote><blockquote><p>he said</p></blockquote></blockquote><p>i said</p></blockquote>"
        )
    }

    @Test
    fun `can represent headings`() {
        test(
            doc { h1 { +"one" } + h2 { +"two" } + p { +"text" } },
            "<h1>one</h1><h2>two</h2><p>text</p>"
        )
    }

    @Test
    fun `can represent inline code`() {
        test(
            doc { p { +"text and " + code { +"code that is " + em { +"emphasized" } + "..." } } },
            "<p>text and <code>code that is </code><em><code>emphasized</code></em><code>...</code></p>"
        )
    }

    @Test
    fun `can represent a code block`() {
        test(
            doc { blockquote { pre { +"some code" } } + p { +"and" } },
            "<blockquote><pre><code>some code</code></pre></blockquote><p>and</p>"
        )
    }

    @Test
    fun `supports leaf nodes in marks`() {
        test(
            doc { p { em { +"hi" + br {} + "x" } } },
            "<p><em>hi<br>x</em></p>"
        )
    }

    @Test
    fun `doesn't collapse non-breaking spaces`() {
        test(
            doc { p { +"\u00a0 \u00a0hello\u00a0" } },
            "<p>&nbsp; &nbsp;hello&nbsp;</p>"
        )
    }

    @Test
    fun `can parse marks on block nodes`() {
        val schemaWithComment = Schema(
            SchemaSpec(
                nodes = testSchema.spec.nodes + mapOf(
                    "doc" to (testSchema.spec.nodes["doc"] as NodeSpecImpl).copy(marks = "comment")
                ),
                marks = testSchema.spec.marks + mapOf(
                    "comment" to MarkSpecImpl(
                        parseDOM = listOf(TagParseRuleImpl(tag = "div.comment")),
                        toDOM = { _, _ ->
                            DOMOutputSpec.ArrayDOMOutputSpec(listOf("div", mapOf("class" to "comment"), 0))
                        }
                    )
                )
            )
        )

        fun NodeBuilder<CustomNodeBuilder>.comment(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) =
            mark("comment", func)

        val doc = CustomNodeBuildCompanion(schemaWithComment).doc {
            p { +"one" } + this.comment { p { +"two" } + p { strong { +"three" } } } + p { +"four" }
        }
        test(
            doc,
            "<p>one</p><div class=\"comment\"><p>two</p><p><strong>three</strong></p></div><p>four</p>"
        )
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `parses unique non-exclusive same-typed marks`() {
        val commentSchema = Schema(
            SchemaSpec(
                nodes = testSchema.spec.nodes,
                marks = testSchema.spec.marks + mapOf(
                    "comment" to MarkSpecImpl(
                        attrs = mapOf("id" to AttributeSpecImpl(default = null)),
                        parseDOM = listOf(
                            TagParseRuleImpl(
                                tag = "span.comment",
                                getNodeAttrs = { dom ->
                                    val id = dom.attribute("data-id")?.int() ?: 10
                                    ParseRuleMatch(mapOf("id" to id))
                                },
                            )
                        ),
                        excludes = "",
                        toDOM = { mark, _ ->
                            DOMOutputSpec.ArrayDOMOutputSpec(
                                listOf(
                                    "span",
                                    mapOf("class" to "comment", "data-id" to mark.attrs["id"]),
                                    0
                                )
                            )
                        }
                    )
                )
            )
        )
        val doc = commentSchema.nodes["doc"]!!.createAndFill(
            attrs = null,
            content = listOf(
                commentSchema.nodes["paragraph"]!!.createAndFill(
                    attrs = null,
                    content = listOf(
                        commentSchema.text(
                            text = "double comment",
                            marks = listOf(
                                commentSchema.marks["comment"]!!.create(mapOf("id" to 1)),
                                commentSchema.marks["comment"]!!.create(mapOf("id" to 2))
                            )
                        )
                    ),
                    marks = null
                )!!
            ),
            marks = null
        )!!
        test(
            doc,
            "<p><span class=\"comment\" data-id=\"1\"><span class=\"comment\" data-id=\"2\">double comment</span></span></p>"
        )
    }

    @Test
    fun `serializes non-spanning marks correctly`() {
        val markSchema = Schema(
            SchemaSpec(
                nodes = testSchema.spec.nodes,
                marks = testSchema.spec.marks + mapOf(
                    "test" to MarkSpecImpl(
                        parseDOM = listOf(TagParseRuleImpl(tag = "test")),
                        toDOM = { _, _ -> DOMOutputSpec.ArrayDOMOutputSpec(listOf("test", 0)) },
                        spanning = false
                    )
                )
            )
        )
        val b = CustomNodeBuildCompanion(markSchema)

        fun NodeBuilder<CustomNodeBuilder>.test(func: NodeBuilder<CustomNodeBuilder>.() -> Unit) = mark("test", func)

        test(
            b.doc { p { test { +"a" + img(mapOf("src" to "x")) {} + "b" } } },
            "<p><test>a</test><test><img src=\"x\"></test><test>b</test></p>"
        )
    }

    // Skipping the following tests because we don't support them yet
//        it("serializes an element and an attribute with XML namespace", () => {
//            let xmlnsSchema = new Schema({
//                nodes: {
//                doc: { content: "svg*" }, text: {},
//                "svg": {
//                parseDOM: [{tag: "svg", namespace: 'http://www.w3.org/2000/svg'}],
//                group: 'block',
//                toDOM() { return ["http://www.w3.org/2000/svg svg", ["use", { "http://www.w3.org/1999/xlink href": "#svg-id" }]] },
//            },
//            },
//            })
//
//            let b = builders(xmlnsSchema) as any
//            let d = b.doc(b.svg())
//            test(d, '<svg xmlns="http://www.w3.org/2000/svg"><use xmlns:ns1="http://www.w3.org/1999/xlink" ns1:href="#svg-id"/></svg>', xmlDocument)()
//
//            let dom = xmlDocument.createElement('div')
//            dom.appendChild(DOMSerializer.fromSchema(xmlnsSchema).serializeFragment(d.content, {document: xmlDocument}))
//            ist(dom.querySelector('svg').namespaceURI, 'http://www.w3.org/2000/svg')
//            ist(dom.querySelector('use').namespaceURI, 'http://www.w3.org/2000/svg')
//            ist(dom.querySelector('use').attributes[0].namespaceURI, 'http://www.w3.org/1999/xlink')
//        })

    fun recover(html: String, doc: Node, options: ParseOptions = ParseOptionsImpl()) {
        val schema = doc.type.schema
        val parsedDoc = DOMParser.fromSchema(schema).parseHtml(html, options)
        assertThat(parsedDoc).isEqualTo(doc)
    }

    @Test
    fun `can recover a list item`() {
        recover(
            "<ol><p>Oh no</p></ol>",
            doc { ol { li { p { +"Oh no" } } } }
        )
    }

    @Test
    fun `wraps a list item in a list`() {
        recover(
            "<li>hey</li>",
            doc { ol { li { p { +"hey" } } } }
        )
    }

    @Test
    fun `can turn divs into paragraphs`() {
        recover(
            "<div>hi</div><div>bye</div>",
            doc { p { +"hi" } + p { +"bye" } }
        )
    }

    @Test
    fun `interprets i and b as emphasis and strong`() {
        recover(
            "<p><i>hello <b>there</b></i></p>",
            doc { p { em { +"hello " + strong { +"there" } } } }
        )
    }

    @Test
    fun `wraps stray text in a paragraph`() {
        recover(
            "hi",
            doc { p { +"hi" } }
        )
    }

    @Test
    fun `ignores an extra wrapping _div_`() {
        recover(
            "<div><p>one</p><p>two</p></div>",
            doc { p { +"one" } + p { +"two" } }
        )
    }

    @Test
    fun `ignores meaningless whitespace`() {
        recover(
            " <blockquote> <p>woo  \n  <em> hooo</em></p> </blockquote> ",
            doc { blockquote { p { +"woo " + em { +"hooo" } } } }
        )
    }

    @Test
    fun `removes whitespace after a hard break`() {
        recover(
            "<p>hello<br>\n  world</p>",
            doc { p { +"hello" + br {} + "world" } }
        )
    }

    @Test
    fun `converts br nodes to newlines when they would otherwise be ignored`() {
        recover(
            "<pre>foo<br>bar</pre>",
            doc { pre { +"foo\nbar" } }
        )
    }

    @Test
    fun `finds a valid place for invalid content`() {
        recover(
            "<ul><li>hi</li><p>whoah</p><li>again</li></ul>",
            doc { ul { li { p { +"hi" } } + li { p { +"whoah" } } + li { p { +"again" } } } }
        )
    }

    @Test
    fun `moves nodes up when they don't fit the current context`() {
        recover(
            "<div>hello<hr/>bye</div>",
            doc { p { +"hello" } + hr {} + p { +"bye" } }
        )
    }

    @Test
    fun `doesn't ignore whitespace-only text nodes`() {
        recover(
            "<p><em>one</em> <strong>two</strong></p>",
            doc { p { em { +"one" } + " " + strong { +"two" } } }
        )
    }

    @Test
    fun `can handle stray tab characters`() {
        recover(
            "<p> <b>&#09;</b></p>",
            doc { p { } }
        )
    }

    @Test
    fun `normalizes random spaces`() {
        recover(
            "<p><b>1 </b>  </p>",
            doc { p { strong { +"1" } } }
        )
    }

    @Test
    fun `can parse an empty code block`() {
        recover(
            "<pre></pre>",
            doc { pre { } }
        )
    }

    @Test
    fun `preserves trailing space in a code block`() {
        recover(
            "<pre>foo\n</pre>",
            doc { pre { +"foo\n" } }
        )
    }

    @Test
    fun `normalizes newlines when preserving whitespace`() {
        recover(
            "<p>foo  bar\nbaz</p>",
            doc { p { +"foo  bar baz" } },
            options = ParseOptionsImpl(preserveWhitespace = PreserveWhitespace.YES)
        )
    }

    @Test
    fun `ignores script tags`() {
        recover(
            "<p>hello<script>alert('x')</script>!</p>",
            doc { p { +"hello!" } }
        )
    }

    @Test
    fun `can handle a head body input structure`() {
        recover(
            "<head><title>T</title><meta charset='utf8'/></head><body>hi</body>",
            doc { p { +"hi" } }
        )
    }

    @Test
    fun `only applies a mark once`() {
        recover(
            "<p>A <strong>big <strong>strong</strong> monster</strong>.</p>",
            doc { p { +"A " + strong { +"big strong monster" } + "." } }
        )
    }

    @Test
    fun `interprets font-style italic as em`() {
        recover(
            "<p><span style='font-style: italic'>Hello</span>!</p>",
            doc { p { em { +"Hello" } + "!" } }
        )
    }

    @Test
    fun `interprets font-weight bold as strong`() {
        recover(
            "<p style='font-weight: bold'>Hello</p>",
            doc { p { strong { +"Hello" } } }
        )
    }

    @Test
    fun `allows clearing of pending marks`() {
        recover(
            "<blockquote style='font-style: italic'><p style='font-style: normal'>One</p><p>Two</p></blockquote>",
            doc { blockquote { p { +"One" } + p { em { +"Two" } } } }
        )
    }

    @Test
    fun `allows clearing of active marks`() {
        recover(
            "<ul><li style='font-style:italic'><p><span>Foo</span><span></span>" +
                "<span style='font-style:normal'>Bar</span></p></li></ul>",
            doc { ul { li { p { em { +"Foo" } + "Bar" } } } }
        )
    }

    @Test
    fun `ignores unknown inline tags`() {
        recover(
            "<p><u>a</u>bc</p>",
            doc { p { +"abc" } }
        )
    }

    @Test
    fun `keeps applying a mark for the all of the node's content`() {
        recover(
            "<p><strong><span>xx</span>bar</strong></p>",
            doc { p { strong { +"xxbar" } } }
        )
    }

    @Test
    fun `doesn't ignore whitespace-only nodes in preserveWhitespace full mode`() {
        recover(
            "<span> </span>x",
            doc { p { +" x" } },
            options = ParseOptionsImpl(preserveWhitespace = PreserveWhitespace.FULL)
        )
    }

    @Test
    fun `closes block with inline content on seeing block-level children`() {
        recover(
            "<div><br><div>CCC</div><div>DDD</div><br></div>",
            doc { p { br {} } + p { +"CCC" } + p { +"DDD" } + p { br {} } }
        )
    }

    private fun parse(html: String, options: ParseOptions, doc: Node) {
        val schema = doc.type.schema
        val dom = doc().createElement("div")
        dom.html(html)
        val result = DOMParser.fromSchema(schema).parse(dom, options)
        assertThat(result).isEqualTo(doc)
    }

    @Test
    fun `accepts the topNode option`() {
        parse(
            "<li>wow</li><li>such</li>",
            ParseOptionsImpl(topNode = testSchema.nodes["bullet_list"]!!.createAndFill()!!),
            doc { ul { li { p { +"wow" } } + li { p { +"such" } } } }.firstChild!!
        )
    }

    @Test
    fun `accepts the topMatch option`() {
        val item = testSchema.nodes["list_item"]!!.createAndFill()!!
        parse(
            "<ul><li>x</li></ul>",
            ParseOptionsImpl(topNode = item, topMatch = item.contentMatchAt(1)!!),
            doc { li { ul { li { p { +"x" } } } } }.firstChild!!
        )
    }

    @Test
    fun `accepts from and to options`() {
        parse(
            "<hr><p>foo</p><p>bar</p><img>",
            ParseOptionsImpl(from = 1, to = 3),
            doc { p { +"foo" } + p { +"bar" } }
        )
    }

    @Test
    fun `accepts the preserveWhitespace option`() {
        parse(
            "foo   bar",
            ParseOptionsImpl(preserveWhitespace = PreserveWhitespace.YES),
            doc { p { +"foo   bar" } }
        )
    }

    private fun open(
        html: String,
        nodes: List<Node>,
        openStart: Int,
        openEnd: Int,
        options: ParseOptions = ParseOptionsImpl()
    ) {
        val schema = testSchema
        val dom = doc().createElement("div")
        dom.html(html)
        val result = DOMParser.fromSchema(schema).parseSlice(dom, options)
        assertThat(result).isEqualTo(
            Slice(
                Fragment.from(nodes),
                openStart,
                openEnd
            )
        )
    }

    @Test
    fun `can parse an open slice`() {
        open("foo", listOf(testSchema.text("foo")), 0, 0)
    }

    @Test
    fun `will accept weird siblings`() {
        val doc = doc { p { +"bar" } }
        open("foo<p>bar</p>", listOf(testSchema.text("foo"), doc.firstChild!!), 0, 1)
    }

    @Test
    fun `will open all the way to the inner nodes`() {
        val doc = doc { ul { li { p { +"foo" } } + li { p { +"bar" + br {} } } } }
        open(
            "<ul><li>foo</li><li>bar<br></li></ul>",
            doc.content.content,
            3,
            3
        )
    }

    @Test
    fun `accepts content open to the left`() {
        val doc = doc { li { ul { li { p { +"a" } } } } }
        open("<li><ul><li>a</li></ul></li>", listOf(doc.firstChild!!), 4, 4)
    }

    @Test
    fun `accepts content open to the right`() {
        val doc = doc { li { p { +"foo" } } + li {} }
        open("<li>foo</li><li></li>", doc.content.content, 2, 1)
    }

    @Test
    fun `will create textblocks for block nodes`() {
        val doc = doc { p { +"foo" } + p { +"bar" } }
        open("<div><div>foo</div><div>bar</div></div>", doc.content.content, 1, 1)
    }

    @Test
    fun `can parse marks at the start of defaulted textblocks`() {
        val doc = doc { p { +"foo" } + p { em { +"bar" } } }
        open("<div>foo</div><div><em>bar</em></div>", doc.content.content, 1, 1)
    }

    @Test
    fun `will not apply invalid marks to nodes`() {
        val doc = doc { ul { li { p { strong { +"foo" } } } } }
        open("<ul style='font-weight: bold'><li>foo</li></ul>", listOf(doc.firstChild!!), 3, 3)
    }

    @Test
    fun `will apply pending marks from parents to all children`() {
        val doc = doc { ul { li { p { strong { +"foo" } } } + li { p { strong { +"bar" } } } } }
        open("<ul style='font-weight: bold'><li>foo</li><li>bar</li></ul>", doc.content.content, 3, 3)
    }

    @Test
    fun `can parse nested mark with same type`() {
        val doc = doc { p { strong { +"foobarbaz" } } }
        open(
            "<p style='font-weight: bold'>foo<strong style='font-weight: bold;'>bar</strong>baz</p>",
            doc.content.content,
            1,
            1
        )
    }

    @Test
    fun `drops block-level whitespace`() {
        open("<div> </div>", listOf(), 0, 0, ParseOptionsImpl(preserveWhitespace = PreserveWhitespace.YES))
    }

    @Test
    fun `keeps whitespace in inline elements`() {
        val doc = doc { p { strong { +" " } } }
        open(
            "<b> </b>",
            listOf(doc.firstChild!!.firstChild!!),
            0,
            0,
            ParseOptionsImpl(preserveWhitespace = PreserveWhitespace.YES)
        )
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `can parse nested mark with same type but different attrs`() {
        val markSchema = Schema(
            SchemaSpec(
                nodes = testSchema.spec.nodes,
                marks = testSchema.spec.marks + mapOf(
                    "s" to MarkSpecImpl(
                        attrs = mapOf("data-s" to AttributeSpecImpl(default = "tag")),
                        excludes = "",
                        parseDOM = listOf(
                            TagParseRuleImpl(tag = "s"),
                            StyleParseRuleImpl(
                                style = "text-decoration",
                                getStyleAttrs = {
                                    ParseRuleMatch(mapOf("data-s" to "style"))
                                }
                            )
                        )
                    )
                )
            )
        )
        val b = CustomNodeBuildCompanion(markSchema)
        val dom = doc().createElement("div")
        dom.html("<p style='text-decoration: line-through;'>o<s style='text-decoration: line-through;'>o</s>o</p>")
        var result = DOMParser.fromSchema(markSchema).parseSlice(dom)
        assertThat(result).isEqualTo(
            Slice(
                Fragment.from(
                    b.schema.nodes["paragraph"]!!.createAndFill(
                        attrs = null,
                        content = Fragment.from(
                            listOf(
                                b.schema.text("o", listOf(b.schema.marks["s"]!!.create(mapOf("data-s" to "style")))),
                                b.schema.text(
                                    "o",
                                    listOf(
                                        b.schema.marks["s"]!!.create(mapOf("data-s" to "style")),
                                        b.schema.marks["s"]!!.create(mapOf("data-s" to "tag"))
                                    )
                                ),
                                b.schema.text(
                                    "o",
                                    listOf(b.schema.marks["s"]!!.create(mapOf("data-s" to "style")))
                                )
                            )
                        )
                    )!!
                ),
                1,
                1
            )
        )

        dom.html(
            "<p><span style='text-decoration: line-through;'><s style='text-decoration: line-through;'>o</s>o</span>o</p>"
        )
        result = DOMParser.fromSchema(markSchema).parseSlice(dom)
        assertThat(result).isEqualTo(
            Slice(
                Fragment.from(
                    b.schema.nodes["paragraph"]!!.createAndFill(
                        attrs = null,
                        content = Fragment.from(
                            listOf(
                                b.schema.text(
                                    "o",
                                    listOf(
                                        b.schema.marks["s"]!!.create(mapOf("data-s" to "style")),
                                        b.schema.marks["s"]!!.create(mapOf("data-s" to "tag"))
                                    )
                                ),
                                b.schema.text("o", listOf(b.schema.marks["s"]!!.create(mapOf("data-s" to "style")))),
                                b.schema.text("o")
                            )
                        )
                    )!!
                ),
                1,
                1
            )
        )
    }

    @Test
    fun `can temporary shadow a mark with another configuration of the same type`() {
        val markSchema = Schema(
            SchemaSpec(
                nodes = testSchema.spec.nodes,
                marks = mapOf(
                    "color" to MarkSpecImpl(
                        attrs = mapOf("color" to AttributeSpecImpl()),
                        parseDOM = listOf(
                            StyleParseRuleImpl(
                                style = "color",
                                getStyleAttrs = { ParseRuleMatch(mapOf("color" to it)) }
                            )
                        )
                    )
                )
            )
        )
        val b = CustomNodeBuildCompanion(markSchema)
        val dom = doc().createElement("div")
        dom.html("<p><span style='color: red'>abc<span style='color: blue'>def</span>ghi</span></p>")
        val result = DOMParser.fromSchema(markSchema).parse(dom)
        assertThat(result).isEqualTo(
            b.schema.nodes["doc"]!!.create(
                null,
                listOf(
                    b.schema.nodes["paragraph"]!!.create(
                        attrs = null,
                        content =
                        listOf(
                            b.schema.text("abc", listOf(b.schema.marks["color"]!!.create(mapOf("color" to "red")))),
                            b.schema.text("def", listOf(b.schema.marks["color"]!!.create(mapOf("color" to "blue")))),
                            b.schema.text("ghi", listOf(b.schema.marks["color"]!!.create(mapOf("color" to "red"))))
                        )
                    )
                )
            ),
        )
    }

    private fun find(html: String, doc: Node) {
        val schema = doc.type.schema
        val dom = doc().createElement("div")
        dom.html(html)
        val tag = dom.selectFirst("var")!!
        val prev = tag.previousElementSibling()
        val next = tag.nextElementSibling()
        val pos = if (prev is TextNode && next is TextNode) {
            val prevText = prev.text()
            prev.text(prevText + next.text())
            next.remove()
            ParseOptionPosition(prev, offset = prevText.length, pos = null)
        } else {
            ParseOptionPosition(tag.parent()!!, offset = tag.parent()!!.childNodes().indexOf(tag), pos = null)
        }
        tag.remove()
        val result = DOMParser.fromSchema(schema).parse(dom, ParseOptionsImpl(findPositions = listOf(pos)))
        assertThat(result).isEqualTo(doc)
        assertThat(pos.pos).isEqualTo(pos(doc, "a"))
    }

    @Test
    fun `can find a position at the start of a paragraph`() {
        find("<p><var></var>hello</p>", doc { p { +"<a>hello" } })
    }

    @Test
    fun `can find a position at the end of a paragraph`() {
        find("<p>hello<var></var></p>", doc { p { +"hello<a>" } })
    }

    @Test
    fun `can find a position inside text`() {
        find("<p>hel<var></var>lo</p>", doc { p { +"hel<a>lo" } })
    }

    @Test
    fun `can find a position inside an ignored node`() {
        find("<p>hi</p><object><var></var>foo</object><p>ok</p>", doc { p { +"hi" } + "<a>" + p { +"ok" } })
    }

    @Test
    fun `can find a position between nodes`() {
        find(
            "<ul><li>foo</li><var></var><li>bar</li></ul>",
            doc { ul { li { p { +"foo" } } + "<a>" + li { p { +"bar" } } } }
        )
    }

    @Test
    fun `can find a position at the start of the document`() {
        find("<var></var><p>hi</p>", doc { +"<a>" + p { +"hi" } })
    }

    @Test
    fun `can find a position at the end of the document`() {
        find("<p>hi</p><var></var>", doc { p { +"hi" } + "<a>" })
    }

    @Test
    fun `uses a custom top node when parsing`() {
        val quoteSchema = Schema(
            SchemaSpec(
                nodes = testSchema.spec.nodes,
                marks = testSchema.spec.marks,
                topNode = "blockquote"
            )
        )
        val quote = quoteSchema.nodes["blockquote"]!!.create(
            attrs = null,
            content = listOf(
                quoteSchema.nodes["paragraph"]!!.create(
                    attrs = null,
                    content = listOf(quoteSchema.text("hello"))
                )
            )
        )
        test(quote, "<p>hello</p>")
    }

    private fun contextParser(context: String) = DOMParser(
        testSchema,
        listOf(TagParseRuleImpl(tag = "foo", node = "horizontal_rule", context = context)) +
            DOMParser.schemaRules(testSchema)
    )

    private fun domFrom(html: String) = doc().createElement("div").html(html)

    @Test
    fun `recognizes context restrictions`() {
        val result = contextParser("blockquote/").parse(
            domFrom("<foo></foo><blockquote><foo></foo><p><foo></foo></p></blockquote>")
        )
        val expected = doc { blockquote { hr {} + p {} } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `accepts group names in contexts`() {
        val result = contextParser("block/").parse(
            domFrom("<foo></foo><blockquote><foo></foo><p></p></blockquote>")
        )
        val expected = doc { blockquote { hr {} + p {} } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `understands nested context restrictions`() {
        val result = contextParser("blockquote/ordered_list//").parse(
            domFrom("<foo></foo><blockquote><foo></foo><ol><li><p>a</p><foo></foo></li></ol></blockquote>")
        )
        val expected = doc { blockquote { ol { li { p { +"a" } + hr {} } } } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `understands double slashes in context restrictions`() {
        val result = contextParser("blockquote//list_item/").parse(
            domFrom("<foo></foo><blockquote><foo></foo><ol><foo></foo><li><p>a</p><foo></foo></li></ol></blockquote>")
        )
        val expected = doc { blockquote { ol { li { p { +"a" } + hr {} } } } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `understands pipes in context restrictions`() {
        val result = contextParser("list_item/|blockquote/").parse(
            domFrom("<foo></foo><blockquote><p></p><foo></foo></blockquote><ol><li><p>a</p><foo></foo></li></ol>")
        )
        val expected = doc { blockquote { p {} + hr {} } + ol { li { p { +"a" } + hr {} } } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `uses the passed context`() {
        val cxDoc = doc { blockquote { +"<a>" + hr {} } }
        val result = contextParser("doc//blockquote/").parse(
            domFrom("<blockquote><foo></foo></blockquote>"),
            ParseOptionsImpl(
                topNode = testSchema.nodes["blockquote"]!!.createAndFill()!!,
                context = cxDoc.resolve(pos(cxDoc, "a")!!)
            )
        )
        val expected = doc { blockquote { blockquote { hr {} } } }
        assertThat(result).isEqualTo(expected.firstChild!!)
    }

    @Test
    fun `uses the passed context when parsing a slice`() {
        val cxDoc = doc { blockquote { +"<a>" + hr {} } }
        val result = contextParser("doc//blockquote/").parseSlice(
            domFrom("<foo></foo>"),
            ParseOptionsImpl(
                context = cxDoc.resolve(pos(cxDoc, "a")!!)
            )
        )
        val expected = Slice(
            Fragment.from(doc { blockquote { hr {} } }.firstChild!!.content),
            0,
            0
        )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `can close parent nodes from a rule`() {
        val closeParser = DOMParser(
            testSchema,
            listOf(TagParseRuleImpl(tag = "br", closeParent = true)) + DOMParser.schemaRules(testSchema)
        )
        val result = closeParser.parse(domFrom("<p>one<br>two</p>"))
        val expected = doc { p { +"one" } + p { +"two" } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `supports non-consuming node rules`() {
        val parser = DOMParser(
            testSchema,
            listOf(TagParseRuleImpl(tag = "ol", consuming = false, node = "blockquote")) +
                DOMParser.schemaRules(testSchema)
        )
        val result = parser.parse(domFrom("<ol><p>one</p></ol>"))
        val expected = doc { blockquote { ol { li { p { +"one" } } } } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `supports non-consuming style rules`() {
        val parser = DOMParser(
            testSchema,
            listOf(
                StyleParseRuleImpl(style = "font-weight", consuming = false, mark = "em")
            ) + DOMParser.schemaRules(testSchema)
        )
        val result = parser.parse(domFrom("<p><span style='font-weight: 800'>one</span></p>"))
        val expected = doc { p { em { strong { +"one" } } } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `doesn't get confused by nested mark tags`() {
        recover(
            "<div><strong><strong>A</strong></strong>B</div><span>C</span>",
            doc { p { strong { +"A" } + "B" } + p { +"C" } }
        )
    }

    @Test
    fun `ignores styles on skipped nodes`() {
        val dom = doc().createElement("div")
        dom.html("<p>abc <span style='font-weight: strong'>def</span></p>")
        val result = DOMParser.fromSchema(testSchema).parse(
            dom,
            ParseOptionsImpl(
                ruleFromNode = { node ->
                    if (node is Element && node.nodeName() == "SPAN") {
                        ParseOptionsRuleImpl(skip = node)
                    } else {
                        null
                    }
                }
            )
        )
        val expected = doc { p { +"abc def" } }
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `preserves whitespace in pre elements`() {
        val schema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "block+"),
                    "text" to NodeSpecImpl(group = "inline"),
                    "p" to NodeSpecImpl(group = "block", content = "inline*")
                )
            )
        )
        val result = DOMParser.fromSchema(schema).parse(domFrom("<pre>  hello </pre>   "))
        assertThat(result).isEqualTo(
            schema.node("doc", null, listOf(schema.node("p", null, listOf(schema.text("  hello ")))))
        )
    }

    @Test
    fun `preserves whitespace in nodes styled with white-space`() {
        recover(
            "  <div style='white-space: pre'>  okay  then </div>  <p> x</p>",
            doc { p { +"  okay  then " } + p { +"x" } }
        )
    }
    //endregion

    //region schemaRules
    @Test
    fun `defaults to schema order`() {
        val schema = Schema(
            SchemaSpec(
                marks = mapOf(
                    "em" to MarkSpecImpl(
                        parseDOM = listOf(TagParseRuleImpl(tag = "i"), TagParseRuleImpl(tag = "em"))
                    )
                ),
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "inline*"),
                    "text" to NodeSpecImpl(group = "inline"),
                    "foo" to NodeSpecImpl(
                        group = "inline",
                        inline = true,
                        parseDOM = listOf(TagParseRuleImpl(tag = "foo"))
                    ),
                    "bar" to NodeSpecImpl(
                        group = "inline",
                        inline = true,
                        parseDOM = listOf(TagParseRuleImpl(tag = "bar"))
                    )
                )
            )
        )
        val result = DOMParser.schemaRules(schema).mapNotNull { (it as? TagParseRule)?.tag }.joinToString(" ")
        assertThat(result).isEqualTo("i em foo bar")
    }

    @Test
    fun `understands priority`() {
        val schema = Schema(
            SchemaSpec(
                marks = mapOf(
                    "em" to MarkSpecImpl(
                        parseDOM = listOf(
                            TagParseRuleImpl(tag = "i", priority = 40),
                            TagParseRuleImpl(tag = "em", priority = 70)
                        )
                    )
                ),
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "inline*"),
                    "text" to NodeSpecImpl(group = "inline"),
                    "foo" to NodeSpecImpl(
                        group = "inline",
                        inline = true,
                        parseDOM = listOf(TagParseRuleImpl(tag = "foo"))
                    ),
                    "bar" to NodeSpecImpl(
                        group = "inline",
                        inline = true,
                        parseDOM = listOf(TagParseRuleImpl(tag = "bar", priority = 60))
                    )
                )
            )
        )
        val result = DOMParser.schemaRules(schema).mapNotNull { (it as? TagParseRule)?.tag }.joinToString(" ")
        assertThat(result).isEqualTo("em bar foo i")
    }

    private fun nsParse(doc: DOMNode, namespace: String? = null): Node {
        val schema = Schema(
            SchemaSpec(
                nodes = mapOf(
                    "doc" to NodeSpecImpl(content = "h*"),
                    "text" to NodeSpecImpl(),
                    "h" to NodeSpecImpl(
                        parseDOM = listOf(TagParseRuleImpl(tag = "h", namespace = namespace))
                    )
                )
            )
        )
        return DOMParser.fromSchema(schema).parse(doc)
    }

    @Test
    fun `includes nodes when namespace is correct`() {
        val doc = doc().createElement("doc")
        val h = doc().createElementNS("urn:ns", "h")
        doc.appendChild(h)
        assertThat(nsParse(doc, "urn:ns").childCount).isEqualTo(1)
    }

    @Test
    fun `excludes nodes when namespace is wrong`() {
        val doc = doc().createElement("doc")
        val h = doc().createElementNS("urn:nt", "h")
        doc.appendChild(h)
        assertThat(nsParse(doc, "urn:ns").childCount).isEqualTo(0)
    }

    // Skipping this test because ksoup doesn't allow null namespace
//        it("excludes nodes when namespace is absent", () => {
//            let doc = xmlDocument.createElement("doc")
//            // in HTML documents, createElement gives namespace
//            // 'http://www.w3.org/1999/xhtml' so use createElementNS
//            let h = xmlDocument.createElementNS(null, "h")
//            doc.appendChild(h)
//            ist(nsParse(doc, "urn:ns").childCount, 0)
//        })

    @Test
    fun `exclude nodes when namespace is wrong and xhtml`() {
        val doc = doc().createElement("doc")
        val h = doc().createElementNS("urn:nt", "h")
        doc.appendChild(h)
        assertThat(nsParse(doc, "http://www.w3.org/1999/xhtml").childCount).isEqualTo(0)
    }

    @Test
    fun `exclude nodes when namespace is wrong and empty`() {
        val doc = doc().createElement("doc")
        val h = doc().createElementNS("urn:nt", "h")
        doc.appendChild(h)
        assertThat(nsParse(doc, "").childCount).isEqualTo(0)
    }

    // Skipping this test because ksoup doesn't allow null namespace
//        it("includes nodes when namespace is correct and empty", () => {
//            let doc = xmlDocument.createElement("doc")
//            let h = xmlDocument.createElementNS(null, "h")
//            doc.appendChild(h)
//            ist(nsParse(doc).childCount, 1)
//        })
    //endregion

    //region DOMSerializer
    @Test
    fun `can omit a mark`() {
        val node = noEm.serializeNode(
            doc { p { +"foo" + em { +"bar" } + strong { +"baz" } } }.firstChild!!,
            doc()
        ) as Element
        assertThat(node.html()).isEqualTo("foobar<strong>baz</strong>")
    }

    @Test
    fun `doesn't split other marks for omitted marks`() {
        val node = noEm.serializeNode(
            doc { p { +"foo" + code { +"bar" } + em { code { +"baz" } + "quux" } + "xyz" } }.firstChild!!,
            doc()
        ) as Element
        assertThat(node.html()).isEqualTo("foo<code>barbaz</code>quuxxyz")
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `can render marks with complex structure`() {
        val deepEm = DOMSerializer(
            serializer.nodes,
            serializer.marks + mapOf(
                "em" to { _, _ ->
                    DOMOutputSpec.ArrayDOMOutputSpec(
                        listOf(
                            "em",
                            DOMOutputSpec.ArrayDOMOutputSpec(
                                listOf(
                                    "i",
                                    mapOf("data-emphasis" to true),
                                    0
                                )
                            )
                        )
                    )
                }
            )
        )
        val node = deepEm.serializeNode(
            doc {
                p { strong { +"foo" + code { +"bar" } + em { code { +"baz" } } } + em { +"quux" } + "xyz" }
            }.firstChild!!,
            doc()
        ) as Element
        val expected = "<strong>foo<code>bar</code></strong><em><i data-emphasis=\"true\"><strong><code>baz</code></strong>quux</i></em>xyz"
        assertThat(node.html()).isEqualTo(expected)
    }

    @Test
    fun `refuses to use values from attributes as DOM specs`() {
        val weird = DOMSerializer(
            serializer.nodes + mapOf(
                "image" to { node ->
                    DOMOutputSpec.ArrayDOMOutputSpec(
                        listOf(
                            "span",
                            DOMOutputSpec.ArrayDOMOutputSpec(
                                listOf(
                                    "img",
                                    mapOf("src" to node.attrs["src"])
                                )
                            ),
                            if ((node.attrs["alt"] as? List<Any>) != null) {
                                DOMOutputSpec.ArrayDOMOutputSpec(
                                    node.attrs["alt"] as List<Any>
                                )
                            } else {
                                node.attrs["alt"] ?: ""
                            }
                        ),
                    )
                }
            ),
            serializer.marks
        )
        val ex = assertFailsWith<IllegalArgumentException> {
            weird.serializeNode(
                doc {
                    p {
                        img(
                            mapOf(
                                "src" to "x.png",
                                "alt" to listOf(
                                    "script",
                                    mapOf("src" to "http://evil.com/inject.js")
                                )
                            )
                        ) { }
                    }
                }.firstChild!!.firstChild!!,
                doc()
            )
        }
        assertThat(ex.message?.contains("Using an array from an attribute object as a DOM spec") ?: false).isTrue()
    }

    companion object {
        private val serializer = DOMSerializer.fromSchema(testSchema)
        private val noEm = DOMSerializer(
            serializer.nodes,
            serializer.marks.minus("em")
        )
    }
    //endregion
}

fun com.fleeksoft.ksoup.nodes.Attribute?.int(default: Int? = null): Int? {
    return try {
        this?.value?.takeIf { it.isNotBlank() }?.toInt()
    } catch (ex: NumberFormatException) {
        default
    }
}
