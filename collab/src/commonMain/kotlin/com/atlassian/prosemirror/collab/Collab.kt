package com.atlassian.prosemirror.collab

import com.atlassian.prosemirror.state.EditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.PluginKey
import com.atlassian.prosemirror.state.PluginSpec
import com.atlassian.prosemirror.state.StateField
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.transform.Step
import com.atlassian.prosemirror.transform.Transform
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Rebaseable(
    val step: Step,
    val inverted: Step,
    val origin: Transform
)

/** Undo a given set of steps, apply a set of other steps, and then
 * redo them @internal
 */
fun rebaseSteps(steps: List<Rebaseable>, over: List<Step>, transform: Transform): List<Rebaseable> {
    for (i in steps.size - 1 downTo 0) transform.step(steps[i].inverted)
    for (step in over) transform.step(step)
    val result = mutableListOf<Rebaseable>()
    var mapFrom = steps.size
    steps.forEachIndexed { i, step ->
        val mapped = step.step.map(transform.mapping.slice(mapFrom))
        mapFrom--
        if (mapped != null && transform.maybeStep(mapped).failed == null) {
            transform.mapping.setMirror(mapFrom, transform.steps.size - 1)
            result.add(Rebaseable(mapped, mapped.invert(transform.docs[transform.docs.size - 1]), steps[i].origin))
        }
    }
    return result
}

/**
 * This state field accumulates changes that have to be sent to the
 * central authority in the collaborating group and makes it possible
 * to integrate changes made by peers into our local document. It is
 * defined by the plugin, and will be available as the `collab` field
 * in the resulting editor state.
 */
data class CollabState(
    /**
     * The version number of the last update received from the central
     * authority. Starts at 0 or the value of the `version` property
     * in the option object, for the editor's value when the option
     * was enabled.
     */
    val version: Int,
    /**
     * The local steps that havent been successfully sent to the
     * server yet.
     */
    val unconfirmed: List<Rebaseable>
) {
    constructor(config: CollabConfig) : this(config.version, emptyList())
}

fun unconfirmedFrom(transform: Transform): List<Rebaseable> {
    val result = mutableListOf<Rebaseable>()
    transform.steps.forEachIndexed { index, step ->
        result.add(
            Rebaseable(step, step.invert(transform.docs[index]), transform)
        )
    }
    return result
}

val collabKey = PluginKey<CollabState>("collab")

data class CollabConfig(
    /**
     * The starting version number of the collaborative editing.
     * Defaults to 0.
     */
    val version: Int = 0,

    /**
     * This client's ID, used to distinguish its changes from those of
     * other clients. Defaults to a random 32-bit number.
     */
    val clientID: String?
)

/**
 * Creates a plugin that enables the collaborative editing framework
 * for the editor.
 */
fun collab(config: CollabConfig = CollabConfig(0, null)): CollabPlugin {
    return CollabPlugin(config)
}

open class CollabPlugin(
    override val spec: CollabPluginSpec
) : Plugin<CollabState>(spec) {
    @OptIn(ExperimentalUuidApi::class)
    constructor(config: CollabConfig) : this(
        CollabPluginSpec(
            CollabConfig(
                version = config.version,
                clientID = config.clientID ?: Uuid.random().toString()
            )
        )
    )

    override fun toString(): String {
        return "CollabPlugin: ${spec.config}"
    }
}

open class CollabPluginSpec(conf: CollabConfig) : PluginSpec<CollabState>() {
    override val key = collabKey
    override val state: StateField<CollabState> = object : StateField<CollabState> {
        override fun init(config: EditorStateConfig, instance: PMEditorState): CollabState {
            return CollabState(conf.version, emptyList())
        }

        override fun apply(
            tr: Transaction,
            value: CollabState,
            oldState: PMEditorState,
            newState: PMEditorState
        ): CollabState {
            val state = tr.getMeta(collabKey)
            return when {
                state != null -> state as CollabState
                shouldUpdateState(tr) -> CollabState(value.version, value.unconfirmed + unconfirmedFrom(tr))
                else -> value
            }
        }
    }

    override val additionalProps: Map<String, Any> = mapOf(
        "config" to conf,
        // This is used to notify the history plugin to not merge steps,
        // so that the history can be rebased.
        "historyPreserveItems" to true
    )

    val config: CollabConfig by additionalProps
    val historyPreserveItems: Boolean by additionalProps

    protected open fun shouldUpdateState(tr: Transaction) = tr.docChanged
}

/**
 * Create a transaction that represents a set of new steps received from
 * the authority. Applying this transaction moves the state forward to
 * adjust to the authority's view of the document.
 */
fun receiveTransaction(
    state: PMEditorState,
    steps: List<Step>,
    clientIDs: List<String>,
//    options: {
    /**
     * When enabled (the default is `false`), if the current
     * selection is a [text selection](#state.TextSelection), its
     * sides are mapped with a negative bias for this transaction, so
     * that content inserted at the cursor ends up after the cursor.
     * Users usually prefer this, but it isn't done by default for
     * reasons of backwards compatibility.
     */
    mapSelectionBackward: Boolean = false
//    } = {}
): Transaction {
    var steps = steps
    // Pushes a set of steps (received from the central authority) into
    // the editor state (which should have the collab plugin enabled).
    // Will recognize its own changes, and confirm unconfirmed steps as
    // appropriate. Remaining unconfirmed steps will be rebased over
    // remote steps.
    val collabState = collabKey.getState(state)!!
    val version = collabState.version + steps.size
    val collabPlugin = collabKey.get(state) as CollabPlugin
    val ourID: String? = collabPlugin.spec.config.clientID

    // Find out which prefix of the steps originated with us
    var ours = 0
    while (ours < clientIDs.size && clientIDs[ours] == ourID) ++ours
    var unconfirmed = collabState.unconfirmed.drop(ours)
    steps = if (ours != 0) steps.drop(ours) else steps

    // If all steps originated with us, we're done.
    if (steps.isEmpty()) {
        return state.tr.setMeta(collabKey, CollabState(version, unconfirmed))
    }

    val nUnconfirmed = unconfirmed.size
    val tr = state.tr
    unconfirmed = if (nUnconfirmed != 0) {
        rebaseSteps(unconfirmed, steps, tr)
    } else {
        for (step in steps) {
            tr.step(step)
        }
        emptyList()
    }

    val newCollabState = CollabState(version, unconfirmed)
    if (mapSelectionBackward && state.selection is TextSelection) {
        tr.setSelection(
            TextSelection.between(
                tr.doc.resolve(tr.mapping.map(state.selection.anchor, -1)),
                tr.doc.resolve(tr.mapping.map(state.selection.head, -1)),
                bias = -1
            )
        )
//        ;(tr as any).updated &= ~1   #TODO Aleksei - what is this line about?
    }
    return tr.setMeta("rebased", nUnconfirmed).setMeta("addToHistory", false).setMeta(collabKey, newCollabState)
}

/**
 * Provides data describing the editor's unconfirmed steps, which need
 * to be sent to the central authority. Returns null when there is
 * nothing to send.
 * `origins` holds the _original_ transactions that produced each
 * steps. This can be useful for looking up time stamps and other
 * metadata for the steps, but note that the steps may have been
 * rebased, whereas the origin transactions are still the old,
 * unchanged objects.
 */
fun sendableSteps(state: PMEditorState): SendableSteps? {
    val collabState = collabKey.getState(state) as CollabState
    if (collabState.unconfirmed.isEmpty()) return null
    return SendableSteps(
        version = collabState.version,
        steps = collabState.unconfirmed.map { it.step },
        clientID = (collabKey.get(state) as CollabPlugin).spec.config.clientID,
        origins = collabState.unconfirmed.map { s -> s.origin as Transaction }
    )
}

data class SendableSteps(
    val version: Int,
    val steps: List<Step>,
    val clientID: String?,
    val origins: List<Transaction>
)

/**
 * Get the version up to which the collab plugin has synced with the central authority.
 */
fun getVersion(state: PMEditorState): Int {
    return collabKey.getState(state)!!.version
}
