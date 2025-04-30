package com.atlassian.prosemirror.state

import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.transform.Transform
import com.atlassian.prosemirror.util.safeMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class FieldDesc<T>(
    val name: String,
    val init: (config: EditorStateConfig, instance: PMEditorState) -> T,
    val apply: (tr: Transaction, value: T, oldState: PMEditorState, newState: PMEditorState) -> T
) {
    constructor(name: String, desc: StateField<T>) : this(
        name,
        desc::init,
        desc::apply
    )
}

// Object wrapping the part of a state object that stays the same
// across transactions. Stored in the state's `config` property.
class Configuration(val schema: Schema, plugins: List<Plugin<*>>? = null) {
    val plugins: List<Plugin<*>> = plugins ?: emptyList()
    val fields: List<FieldDesc<out Any?>>
    val pluginsByKey: Map<String, Plugin<*>>

    init {
        val byKey = mutableMapOf<String, Plugin<*>>()
        this.plugins.forEach { plugin ->
            if (byKey[plugin.key] != null) {
                throw RangeError("Adding different instances of a keyed plugin (" + plugin.key + ")")
            }
            byKey[plugin.key] = plugin
        }
        pluginsByKey = byKey.toMap()

        fields = baseFields + this.plugins.mapNotNull { plugin ->
            if (plugin.spec.state != null) {
                FieldDesc(plugin.key, plugin.spec.state as StateField<Any>)
            } else {
                null
            }
        }
    }

    companion object {
        val baseFields: List<FieldDesc<out Any?>> = listOf(
            FieldDesc(
                name = "doc",
                init = { config, state -> config.doc ?: config.schema!!.topNodeType.createAndFill()!! },
                apply = { tr, value, oldState, newState -> tr.doc }
            ),

            FieldDesc(
                name = "selection",
                init = { config, state -> config.selection ?: Selection.atStart(state.doc) },
                apply = { tr, value, oldState, newState -> tr.selection }
            ),

            FieldDesc(
                name = "storedMarks",
                init = { config, state -> config.storedMarks },
                apply = { tr, _marks, _old, state ->
                    if ((state.selection as? TextSelection)?._cursor != null) tr.storedMarks else null
                }
            ),

            FieldDesc(
                "scrollToSelection",
                init = { config, state -> 0 },
                apply = { tr, value, oldState, newState -> if (tr.scrolledIntoView) value + 1 else value }
            )
        )
    }
}

// The type of object passed to [`EditorState.create`](#state.EditorState^create).
interface EditorStateConfig {
    // The schema to use (only relevant if no `doc` is specified).
    val schema: Schema?

    // The starting document. Either this or `schema` _must_ be provided.
    val doc: Node?

    // A valid selection in the document.
    var selection: Selection?

    // The initial set of [stored marks](#state.EditorState.storedMarks).
    val storedMarks: List<Mark>?

    // The plugins that should be active in this state.
    val plugins: List<Plugin<*>>
}

class EmptyEditorStateConfig(
    override val schema: Schema? = null,
    override val doc: Node? = null,
    override var selection: Selection? = null,
    override val storedMarks: List<Mark>? = null,
    override val plugins: List<Plugin<*>> = emptyList()
) : EditorStateConfig

// The state of a ProseMirror editor is represented by an object of this type. A state is a
// persistent data structure—it isn't updated, but rather a new state value is computed from an old
// one using the [`apply`](#state.EditorState.apply) method.
//
// A state holds a number of built-in fields, and plugins can [define](#state.PluginSpec.state)
// additional fields.
class PMEditorState internal constructor(
    internal val config: Configuration
) {

    val stateFields = mutableMapOf<String, Any?>()

    // The current document.
    var doc: Node by stateFields

    // The selection.
    var selection: Selection by stateFields

    // A set of marks to apply to the next input. Will be null when no explicit marks have been set.
    var storedMarks: List<Mark>? by stateFields

    // The schema of the state's document.
    val schema: Schema
        get() = this.config.schema

    // The plugins that are active in this state.
    val plugins: List<Plugin<*>>
        get() = this.config.plugins

    // Used by History#mustPreserveItems to avoid recalculating plugins
    var cachedPreserveItems = false
    var cachedPreserveItemsPlugins: List<Plugin<*>>? = null

    // Start a [transaction](#state.Transaction) from this state.
    val tr: Transaction
        get() = Transaction(this)

    // Apply the given transaction to produce a new state.
    fun apply(tr: Transaction): PMEditorState {
        return this.applyTransaction(tr).state
    }

    fun transaction(f: Transaction.() -> Transform): PMEditorState {
        return this.applyTransaction(tr.apply { f() }).state
    }

    @Suppress("UnusedPrivateMember", "FunctionOnlyReturningConstant")
    fun filterTransaction(tr: Transaction, ignore: Int = -1): Boolean {
        for (i in this.config.plugins.indices) {
            if (i != ignore) {
                val plugin = this.config.plugins[i]
                val filterFunction = plugin.filterTransaction
                if (filterFunction != null && !filterFunction.invoke(tr, this)) {
                    return false
                }
            }
        }
        return true
    }

    // Verbose variant of [`apply`](#state.EditorState.apply) that returns the precise transactions
    // that were applied (which might be influenced by the
    // [transaction hooks](#state.PluginSpec.filterTransaction) of plugins) along with the new state.
    @Suppress("TooGenericExceptionCaught")
    fun applyTransaction(rootTr: Transaction): ApplyTransactionResult {
        return try {
            applyTransactionInternal(rootTr)
        } catch (e: Exception) {
            if (!safeMode) throw e
            ApplyTransactionResult(state = this, transactions = emptyList()) // empty transaction result
        }
    }

    @Suppress("NestedBlockDepth", "ComplexMethod")
    private fun applyTransactionInternal(rootTr: Transaction): ApplyTransactionResult {
        if (!this.filterTransaction(rootTr)) return ApplyTransactionResult(state = this, transactions = emptyList())

        val trs = mutableListOf(rootTr)
        var newState = this.applyInner(rootTr)
        var seen: MutableList<SeenResult>? = null
        // This loop repeatedly gives plugins a chance to respond to
        // transactions as new transactions are added, making sure to only
        // pass the transactions the plugin did not see before.
        while (true) {
            var haveNew = false
            for (i in this.config.plugins.indices) {
                val plugin = this.config.plugins[i]
                val appendTransactionFunction = plugin.appendTransaction
                if (appendTransactionFunction != null) {
                    val n = if (seen != null) seen[i].n else 0
                    val oldState = if (seen != null) seen[i].state else this
                    val tr = if (n < trs.size) {
                        appendTransactionFunction.invoke(if (n != 0) trs.drop(n) else trs, oldState, newState)
                    } else {
                        null
                    }
                    if (tr != null && newState.filterTransaction(tr, i)) {
                        tr.setMeta("appendedTransaction", rootTr)
                        if (seen == null) {
                            seen = mutableListOf()
                            for (j in this.config.plugins.indices) {
                                seen.add(
                                    if (j < i) {
                                        SeenResult(state = newState, n = trs.size)
                                    } else {
                                        SeenResult(state = this, n = 0)
                                    }
                                )
                            }
                        }
                        trs.add(tr)
                        newState = newState.applyInner(tr)
                        haveNew = true
                    }
                    if (seen != null) seen[i] = SeenResult(state = newState, n = trs.size)
                }
            }
            if (!haveNew) return ApplyTransactionResult(state = newState, transactions = trs)
        }
    }

    private data class SeenResult(val n: Int, val state: PMEditorState)

    data class ApplyTransactionResult(val state: PMEditorState, val transactions: List<Transaction>)

    internal fun applyInner(tr: Transaction): PMEditorState {
        if (tr.before != this.doc) throw RangeError("Applying a mismatched transaction")
        val newInstance = PMEditorState(this.config)
        val fields = this.config.fields
        fields.forEach { field ->
            @Suppress("UNCHECKED_CAST")
            val f: FieldDesc<Any?> = field as FieldDesc<Any?>
            newInstance.stateFields[field.name] = f.apply(tr, stateFields[field.name], this, newInstance)
        }
        newInstance.storedMarks = tr.storedMarks
        return newInstance
    }

    // Create a new state based on this one, but with an adjusted set of active plugins. State
    // fields that exist in both sets of plugins are kept unchanged. Those that no longer exist are
    // dropped, and those that are new are initialized using their [`init`](#state.StateField.init)
    // method, passing in the new configuration object..
    fun reconfigure(
        config: EditorStateConfig
//        config: {
//            // New set of active plugins.
//            plugins?: readonly Plugin[]
//        }
    ): PMEditorState {
        val newConfiguration = Configuration(this.schema) // this.schema, config.plugins
        val fields = newConfiguration.fields
        val instance = PMEditorState(newConfiguration)
        fields.forEach { field ->
            val name = field.name
            instance.stateFields.getOrPut(name) {
                field.init(config, instance)
            }
        }
        return instance
    }

    // Serialize this state to JSON. If you want to serialize the state of plugins, pass an object
    // mapping property names to use in the resulting JSON object to plugin objects. The argument
    // may also be a string or number, in which case it is ignored, to support the way
    // `JSON.stringify` calls `toString` methods.
    fun toJSON() = buildJsonObject { // toJSON(pluginFields?: {[propName: string]: Plugin})
        put("doc", doc.toJSON())
        put("selection", selection.toJSON())
        storedMarks?.let { marks ->
            put(
                "storedMarks",
                buildJsonArray {
                    marks.forEach { mark ->
                        add(mark.toJSON())
                    }
                }
            )
        }
//        if (pluginFields && typeof pluginFields == 'object') for (let prop in pluginFields) {
//            if (prop == "doc" || prop == "selection")
//                throw new RangeError("The JSON fields `doc` and `selection` are reserved")
//            let plugin = pluginFields[prop], state = plugin.spec.state
//            if (state && state.toJSON) result[prop] = state.toJSON.call(plugin, (this as any)[plugin.key])
//        }
    }

    companion object {
        const val TAG = "PMEditorState"

        // Create a new state.
        @Suppress("ktlint:standard:property-naming")
        fun create(config: EditorStateConfig): PMEditorState {
            val _config = Configuration(
                if (config.doc != null) config.doc!!.type.schema else config.schema!!,
                config.plugins
            )
            val instance = PMEditorState(_config)
            for (field in _config.fields) {
                instance.stateFields[field.name] = field.init(config, instance)
            }
            return instance
        }

        // Deserialize a JSON representation of a state. `config` should have at least a `schema`
        // field, and should contain array of plugins to initialize the state with. `pluginFields`
        // can be used to deserialize the state of plugins, by associating plugin instances with the
        // property names they use in the JSON object.
        @Suppress("ktlint:standard:property-naming")
        fun fromJSON(
            config: EditorStateConfig,
            json: JsonObject?
            /*pluginFields?: {[propName: string]: Plugin}*/
        ): PMEditorState {
            json ?: throw RangeError("Invalid input for EditorState.fromJSON")
            val configSchema = config.schema ?: throw RangeError("Required config field 'schema' missing")
            val _config = Configuration(configSchema) // configSchema, config.plugins
            val instance = PMEditorState(_config)
            _config.fields.forEach { field ->
                if (field.name == "doc") {
                    instance.doc = Node.fromJSON(configSchema, json["doc"]!!.jsonObject)
                } else if (field.name == "selection") {
                    instance.selection = Selection.fromJSON(instance.doc, json["selection"]!!.jsonObject)
                } else if (field.name == "storedMarks") {
                    if (json.containsKey("storedMarks")) {
                        instance.storedMarks = json["storedMarks"]!!.jsonArray.map {
                            configSchema.markFromJSON(it.jsonObject)
                        }
                    }
                } else {
//                    if (pluginFields) for (let prop in pluginFields) {
//                        let plugin = pluginFields[prop], state = plugin.spec.state
//                        if (plugin.key == field.name && state && state.fromJSON &&
//                            Object.prototype.hasOwnProperty.call(json, prop)) {
//                            // This field belongs to a plugin mapped to a JSON field, read it from there.
//                            ;(instance as any)[field.name] = state.fromJSON.call(plugin, config, json[prop], instance)
//                            return
//                        }
//                    }
                    instance.stateFields[field.name] = field.init(config, instance)
                }
            }
            return instance
        }
    }
}
