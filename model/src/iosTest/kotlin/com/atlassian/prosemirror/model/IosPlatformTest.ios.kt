package com.atlassian.prosemirror.model

import kotlin.test.Test
import kotlin.test.assertTrue

class IosPlatformTest {
    @Test
    fun testExample() {
        assertTrue(getPlatform().name.contains("iOS"), "Check iOS is mentioned")
    }
}
