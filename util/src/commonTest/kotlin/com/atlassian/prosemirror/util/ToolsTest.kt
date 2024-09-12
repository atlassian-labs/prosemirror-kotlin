package com.atlassian.prosemirror.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ToolsTest {
    @Test
    fun sliceSimpleCase() {
        assertThat("Hello world".slice(2, 5)).isEqualTo("llo")
    }
}
