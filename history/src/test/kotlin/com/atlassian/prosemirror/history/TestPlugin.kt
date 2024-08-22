package com.atlassian.prosemirror.history

import com.atlassian.prosemirror.state.EditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.PluginKey
import com.atlassian.prosemirror.state.PluginSpec
import com.atlassian.prosemirror.state.StateField
import com.atlassian.prosemirror.state.Transaction

class TestPluginState

val testPluginKey = PluginKey<TestPluginState>("testPlugin")

class TestPluginSpec(
    override val additionalProps: Map<String, Any>
) : PluginSpec<TestPluginState>() {
    override val state: StateField<TestPluginState> = object : StateField<TestPluginState> {
        override fun init(config: EditorStateConfig, instance: PMEditorState): TestPluginState {
            return TestPluginState()
        }

        override fun apply(
            tr: Transaction,
            value: TestPluginState,
            oldState: PMEditorState,
            newState: PMEditorState
        ): TestPluginState {
            return value
        }
    }

    override val key: PluginKey<TestPluginState> = testPluginKey
}

open class TestPlugin(
    additionalProps: Map<String, Any> = emptyMap()
) : Plugin<TestPluginState>(TestPluginSpec(additionalProps))
