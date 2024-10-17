package com.atlassian.prosemirror.model

import com.fleeksoft.ksoup.nodes.Element
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun evaluateXpathNode(s: String, dom: Element): Element {
    // TODO implement properly
//    val xPath = XPathFactory.newInstance().newXPath()
//    xPath.evaluate(contentElement.s, dom, XPathConstants.NODE) as Element
    return dom
}
