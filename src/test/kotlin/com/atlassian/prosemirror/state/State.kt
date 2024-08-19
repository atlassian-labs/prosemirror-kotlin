package com.atlassian.prosemirror.state

import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.testbuilder.PMNodeBuilder.Companion.pos

// import { EditorState, Selection, TextSelection, NodeSelection } from "prosemirror-state";
// // Wrapper object to make writing state tests easier.
// export function selFor(doc) {
//     let a = doc.tag.a;
//     if (a != null) {
//         let $a = doc.resolve(a);
//         if ($a.parent.inlineContent)
//         return new TextSelection($a, doc.tag.b != null ? doc.resolve(doc.tag.b) : undefined);
//         else
//         return new NodeSelection($a);
//     }
//     return Selection.atStart(doc);
// }
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

// export class TestState {
//     constructor(config) {
//         if (!config.selection && config.doc)
//             config.selection = selFor(config.doc);
//         this.state = EditorState.create(config);
//     }
//     apply(tr) {
//         this.state = this.state.apply(tr);
//     }
//     command(cmd) {
//         cmd(this.state, tr => this.apply(tr));
//     }
//     type(text) {
//         this.apply(this.tr.insertText(text));
//     }
//     deleteSelection() {
//         this.apply(this.state.tr.deleteSelection());
//     }
//     textSel(anchor, head) {
//         let sel = TextSelection.create(this.state.doc, anchor, head);
//         this.state = this.state.apply(this.state.tr.setSelection(sel));
//     }
//     nodeSel(pos) {
//         let sel = NodeSelection.create(this.state.doc, pos);
//         this.state = this.state.apply(this.state.tr.setSelection(sel));
//     }
//     get doc() { return this.state.doc; }
//     get selection() { return this.state.selection; }
//     get tr() { return this.state.tr; }
// }
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
