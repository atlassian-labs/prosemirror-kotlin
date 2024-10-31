package com.atlassian.prosemirror.testbuilder

import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Schema

class CustomNodeBuilder(
    pos: Int = 0,
    marks: List<Mark> = emptyList(),
    override val schema: Schema
) : NodeBuilder<CustomNodeBuilder>(pos, marks, schema) {
    override val checked: Boolean
        get() = false

    override fun create(pos: Int, marks: List<Mark>, schema: Schema): NodeBuilder<CustomNodeBuilder> {
        return CustomNodeBuilder(pos, marks, schema)
    }
}

class CustomNodeBuildCompanion(schema: Schema) : NodeBuildCompanion<CustomNodeBuilder>(schema) {
    override val checked: Boolean
        get() = false

    override fun create(): CustomNodeBuilder {
        return CustomNodeBuilder(schema = schema)
    }
}
