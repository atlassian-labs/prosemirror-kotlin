package com.atlassian.prosemirror.state

import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.transform.Step
import com.atlassian.prosemirror.transform.Transform
import com.atlassian.prosemirror.util.getMilliseconds

//  Commands are functions that take a state and a an optional transaction dispatch function and...
//
//   - determine whether they apply to this state
//   - if not, return false
//   - if `dispatch` was passed, perform their effect, possibly by passing a transaction to `dispatch`
//   - return true
typealias Command = (
    state: PMEditorState,
    dispatch: ((tr: Transaction) -> Unit)?
//  In some cases, the editor view is passed as a third argument.
//     view: EditorView?
) -> Boolean

const val UPDATED_SEL = 1
const val UPDATED_MARKS = 2
const val UPDATED_SCROLL = 4

// An editor state transaction, which can be applied to a state to create an updated state. Use
// [`EditorState.tr`](#state.EditorState.tr) to create an instance.
//
// Transactions track changes to the document (they are a subclass of
// [`Transform`](#transform.Transform)), but also other state changes, like selection updates and
// adjustments of the set of [stored marks](#state.EditorState.storedMarks). In addition, you can
// store metadata properties in a transaction, which are extra pieces of information that client
// code or plugins can use to describe what a transaction represents, so that they can update their
// [own state](#state.StateField) accordingly.
//
// The [editor view](#view.EditorView) uses a few metadata properties: it will attach a property
// `"pointer"` with the value `true` to selection transactions directly caused by mouse or touch
// input, and a `"uiEvent"` property of that may be `"paste"`, `"cut"`, or `"drop"`.
class Transaction : Transform {
    // The timestamp associated with this transaction, in the same format as `Date.now()`.
    var time: Long

    private var curSelection: Selection

    // The step count for which the current selection is valid.
    private var curSelectionFor = 0

    // Bitfield to track which aspects of the state were updated by
    // this transaction.
    private var updated = 0

    // Object used to store metadata properties for the transaction.
    private val meta = mutableMapOf<String, Any>()

    // The stored marks set by this transaction, if any.
    var storedMarks: List<Mark>?

    internal constructor(state: PMEditorState) : super(state.doc) {
        this.time = getMilliseconds()
        this.curSelection = state.selection
        this.storedMarks = state.storedMarks
    }

    // The transaction's current selection. This defaults to the editor selection
    // [mapped](#state.Selection.map) through the steps in the transaction, but can be overwritten
    // with [`setSelection`](#state.Transaction.setSelection).
    val selection: Selection
        get() {
            if (this.curSelectionFor < this.steps.size) {
                this.curSelection = this.curSelection.map(this.doc, this.mapping.slice(this.curSelectionFor))
                this.curSelectionFor = this.steps.size
            }
            return this.curSelection
        }

    // Returns true if this transaction doesn't contain any metadata, and can thus safely be
    // extended.
    val isGeneric
        get() = this.meta.isNotEmpty()

    // True when this transaction has had `scrollIntoView` called on it.
    val scrolledIntoView
        get() = (this.updated and UPDATED_SCROLL) > 0

    // Update the transaction's current selection. Will determine the selection that the editor gets
    // when the transaction is applied.
    fun setSelection(selection: Selection) = this.apply {
        if (selection._from.doc.nodeId != this.doc.nodeId) {
            throw RangeError("Selection passed to setSelection must point at the current document")
        }
        val changed = this.curSelection != selection
        this.curSelection = selection
        this.curSelectionFor = this.steps.size
        this.updated = (this.updated or UPDATED_SEL) and UPDATED_MARKS.inv()
        if (changed) {
            this.storedMarks = null
        }
    }

    // Whether the selection was explicitly updated by this transaction.
    val selectionSet
        get() = (this.updated and UPDATED_SEL) > 0

    // Set the current stored marks.
    fun setStoredMarks(marks: List<Mark>?) = this.apply {
        this.storedMarks = marks
        this.updated = this.updated or UPDATED_MARKS
    }

    // Make sure the current stored marks or, if that is null, the marks at the selection, match the
    // given set of marks. Does nothing if this is already the case.
    fun ensureMarks(marks: List<Mark>) = this.apply {
        if (!Mark.sameSet(this.storedMarks ?: this.selection._from.marks(), marks)) {
            this.setStoredMarks(marks)
        }
    }

    // Add a mark to the set of stored marks.
    fun addStoredMark(mark: Mark) = this.apply {
        ensureMarks(mark.addToSet(this.storedMarks ?: this.selection._head.marks()))
    }

    // Remove a mark or mark type from the set of stored marks.
    fun removeStoredMark(mark: Mark) = this.apply {
        this.ensureMarks(mark.removeFromSet(this.storedMarks ?: this.selection._head.marks()))
    }

    fun removeStoredMark(mark: MarkType) = this.apply {
        this.ensureMarks(mark.removeFromSet(this.storedMarks ?: this.selection._head.marks()))
    }

    // Whether the stored marks were explicitly set for this transaction.
    val storedMarksSet: Boolean
        get() = (this.updated and UPDATED_MARKS) > 0

    internal override fun addStep(step: Step, doc: Node) {
        super.addStep(step, doc)
        this.updated = this.updated and UPDATED_MARKS.inv()
        this.storedMarks = null
    }

    // Update the timestamp for the transaction.
    fun setTime(time: Long) = this.apply {
        this.time = time
    }

    // Replace the current selection with the given slice.
    fun replaceSelection(slice: Slice) = this.apply {
        this.selection.replace(this, slice)
    }

    // Replace the selection with the given node. When `inheritMarks` is true and the content is
    // inline, it inherits the marks from the place where it is inserted.
    fun replaceSelectionWith(node: Node, inheritMarks: Boolean = true) = this.apply {
        val selection = this.selection
        var node = node
        if (inheritMarks) {
            node = node.mark(
                this.storedMarks ?: (
                    if (selection.empty) {
                        selection._from.marks()
                    } else {
                        selection._from.marksAcross(selection._to) ?: Mark.none
                    }
                    )
            )
        }
        selection.replaceWith(this, node)
    }

    // Delete the selection.
    fun deleteSelection() = this.apply {
        this.selection.replace(this)
    }

    // Replace the given range, or the selection if no range is given, with a text node containing
    // the given string.
    @Suppress("NestedBlockDepth")
    fun insertText(text: String, from: Int? = null, to: Int? = null): Transaction = this.apply {
        val schema = this.doc.type.schema
        if (from == null) {
            if (text.isEmpty()) return this.deleteSelection()
            return this.replaceSelectionWith(schema.text(text), true)
        } else {
            val to: Int = to ?: from
            if (text.isEmpty()) return this.apply { this.deleteRange(from, to) }
            var marks = this.storedMarks
            if (marks == null) {
                val resolved = this.doc.resolve(from)
                marks = if (to == from) resolved.marks() else resolved.marksAcross(this.doc.resolve(to))
            }
            this.replaceRangeWith(from, to, schema.text(text, marks))
            if (!this.selection.empty) this.setSelection(Selection.near(this.selection._to))
            return this
        }
    }

    // Store a metadata property in this transaction, keyed either by name or by plugin.
    fun setMeta(key: String, value: Any) = this.apply {
        this.meta[key] = value
    }

    fun setMeta(key: Plugin<*>, value: Any) = this.apply {
        this.meta[key.key] = value
    }

    fun setMeta(key: PluginKey<*>, value: Any) = this.apply {
        this.meta[key.key] = value
    }

    // Retrieve a metadata property for a given name or plugin.
    fun getMeta(key: String): Any? {
        return this.meta[key]
    }

    fun getMeta(key: Plugin<*>): Any? {
        return this.meta[key.key]
    }

    fun getMeta(key: PluginKey<*>): Any? {
        return this.meta[key.key]
    }

    // Indicate that the editor should scroll the selection into view
    // when updated to the state produced by this transaction.
    fun scrollIntoView() = this.apply {
        this.updated = this.updated or UPDATED_SCROLL
    }
}
