package com.atlassian.prosemirror.model

import com.fleeksoft.ksoup.nodes.Element
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class AndroidPlatform : Platform {
    override val name: String = "Jvm ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun evaluateXpathNode(s: String, dom: Element): Element {
    val xPath = XPathFactory.newInstance().newXPath()
    return xPath.evaluate(s, dom, XPathConstants.NODE) as Element
}
