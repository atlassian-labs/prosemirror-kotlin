package com.atlassian.prosemirror.model

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmPlatformTest {
    @Test
    fun testExample() {
        assertTrue(getPlatform().name.contains("Jvm"), "Check Jvm is mentioned")
    }
}
