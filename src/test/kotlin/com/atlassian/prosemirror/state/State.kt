package com.atlassian.prosemirror.state

import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos

// Wrapper object to make writing state tests easier.
fun selFor(doc: Node): Selection {
    val nodePos = pos("a")
    if (nodePos != null) {
        val resolvedNodePos = doc.resolve(nodePos)
        return if (resolvedNodePos.parent.inlineContent) {
            val bPos = pos("b")
            TextSelection(resolvedNodePos, if (bPos != null) doc.resolve(bPos) else resolvedNodePos)
        } else {
            NodeSelection(resolvedNodePos)
        }
    }
    return Selection.atStart(doc)
}

class TestState {

    var state: PMEditorState
    val doc
        get() = this.state.doc
    val selection: Selection
        get() = this.state.selection
    val tr: Transaction
        get() = this.state.tr

    constructor(config: EditorStateConfig) {
        if (config.selection == null && config.doc != null) {
            config.selection = selFor(config.doc!!)
        }
        this.state = PMEditorState.create(config)
    }

    fun apply(tr: Transaction) {
        this.state = this.state.apply(tr)
    }

    fun command(cmd: (state: PMEditorState, (tr: Transaction) -> Unit) -> Unit) {
        cmd(this.state) { tr -> this.apply(tr) }
    }

    fun type(text: String) {
        this.apply(this.tr.insertText(text))
    }

    fun deleteSelection() {
        this.apply(this.state.tr.deleteSelection())
    }

    fun textSel(anchor: Int, head: Int? = null) {
        val sel = TextSelection.create(this.state.doc, anchor, head)
        this.state = this.state.apply(this.state.tr.setSelection(sel))
    }

    fun nodeSel(pos: Int) {
        val sel = NodeSelection.create(this.state.doc, pos)
        this.state = this.state.apply(this.state.tr.setSelection(sel))
    }
}

class EditorStateConfigImpl(
    override val schema: Schema? = null,
    override val doc: Node? = null,
    override var selection: Selection? = null,
    override val storedMarks: List<Mark>? = null,
    override val plugins: List<Plugin<*>> = emptyList()
) : EditorStateConfig
