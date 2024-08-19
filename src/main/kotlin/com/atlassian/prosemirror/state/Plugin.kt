package com.atlassian.prosemirror.state

/**
 * This is the type passed to the [`Plugin`](#state.Plugin)
 * constructor. It provides a definition for a plugin.
 */
abstract class PluginSpec<PluginState> {

    /**
     * Allows a plugin to define a [state field](#state.StateField), an
     * extra slot in the state object in which it can keep its own data.
     */
    abstract val state: StateField<PluginState>?

    /**
     * Can be used to make this a keyed plugin. You can have only one
     * plugin with a given key in a given state, but it is possible to
     * access the plugin's configuration and state through the key,
     * without having access to the plugin instance object.
     */
    abstract val key: PluginKey<PluginState>?

    /**
     * When the plugin needs to interact with the editor view, or
     * set something up in the DOM, use this field. The function
     * will be called when the plugin's state is associated with an
     * editor view.
     */
//    val view: ((view: EditorView) -> PluginView)?,

    /**
     * Additional properties are allowed on plugin specs, which can be
     * read via [`Plugin.spec`](#state.Plugin.spec).
     */
    open val additionalProps: Map<String, Any> = mapOf()
}

/**
 * Plugins bundle functionality that can be added to an editor.
 * They are part of the [editor state](#state.EditorState) and
 * may influence that state and the view that contains it.
 */
open class Plugin<PluginState>(
    // The plugin's [spec object](#state.PluginSpec).
    pluginSpec: PluginSpec<PluginState>
) {

    open val spec: PluginSpec<PluginState> = pluginSpec

    internal val key: String

    /**
     * Allows the plugin to append another transaction to be applied
     * after the given array of transactions. When another plugin
     * appends a transaction after this was called, it is called again
     * with the new state and new transactions—but only the new
     * transactions, i.e. it won't be passed transactions that it
     * already saw.
     */
    internal open val appendTransaction: ((List<Transaction>, PMEditorState, PMEditorState) -> Transaction?)? = null

    /**
     * When present, this will be called before a transaction is
     * applied by the state, allowing the plugin to cancel it (by
     * returning false).
     */
    open val filterTransaction: ((tr: Transaction, state: PMEditorState) -> Boolean)? = null

    init {
        key = pluginSpec.key?.key ?: createKey("plugin")
    }

    // Extract the plugin's state field from an editor state.
    fun getState(state: PMEditorState): PluginState? {
        return state.stateFields[key] as PluginState?
    }
}

// A plugin spec may provide a state field (under its [`state`](#state.PluginSpec.state) property)
// of this type, which describes the state it wants to keep. Functions provided here are always
// called with the plugin instance as their `this` binding.
interface StateField<T> {
    // Initialize the value of the field. `config` will be the object
    // passed to [`EditorState.create`](#state.EditorState^create). Note
    // that `instance` is a half-initialized state instance, and will
    // not have values for plugin fields initialized after this one.
    fun init(config: EditorStateConfig, instance: PMEditorState): T

    // Apply the given transaction to this state field, producing a new
    // field value. Note that the `newState` argument is again a partially
    // constructed state does not yet contain the state from plugins
    // coming after this one.
    fun apply(tr: Transaction, value: T, oldState: PMEditorState, newState: PMEditorState): T
}

// ALEKSEI: to avoid having json parsing methods implemented as nullable lambdas we just extend StateField interface
interface StateFieldWithJSON<T> : StateField<T> {
    // Convert this field to JSON.
    fun toJSON(value: T): Any?

    // Deserialize the JSON representation of this field. Note that the
    // `state` argument is again a half-initialized state.
    fun fromJSON(config: EditorStateConfig, value: Any, state: PMEditorState): T?
}

val keys = mutableMapOf<String, Int>()

fun createKey(name: String): String {
    val value = keys[name]
    return if (value == null) {
        keys[name] = 0
        "$name$"
    } else {
        val newValue = value + 1
        keys[name] = newValue
        "$name$$newValue"
    }
}

/**
 * A key is used to [tag](#state.PluginSpec.key) plugins in a way
 * that makes it possible to find them, given an editor state.
 * Assigning a key does mean only one plugin of that type can be
 * active in a state.
 */
class PluginKey<PluginState>(name: String = "key") {

    internal val key: String = createKey(name)

    /**
     * Get the active plugin with this key, if any, from an editor state.
     */
    fun get(state: PMEditorState): Plugin<PluginState>? {
        return state.config.pluginsByKey[this.key] as Plugin<PluginState>?
    }

    /**
     * Get the plugin's state from an editor state.
     */
    fun getState(state: PMEditorState): PluginState? {
        return state.stateFields[key] as PluginState?
    }
}
