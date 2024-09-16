package com.atlassian.prosemirror.model

import com.fleeksoft.ksoup.nodes.Element

interface Platform {
    val name: String
}

expect fun evaluateXpathNode(s: String, dom: Element): Element
expect fun getPlatform(): Platform
