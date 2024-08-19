package com.atlassian.prosemirror.history

import com.atlassian.prosemirror.history.ropesequence.RopeSequence
import com.atlassian.prosemirror.state.Command
import com.atlassian.prosemirror.state.EditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.PluginKey
import com.atlassian.prosemirror.state.PluginSpec
import com.atlassian.prosemirror.state.SelectionBookmark
import com.atlassian.prosemirror.state.StateField
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.transform.Mapping
import com.atlassian.prosemirror.transform.Step
import com.atlassian.prosemirror.transform.StepMap
import com.atlassian.prosemirror.transform.Transform

import kotlin.math.max
import kotlin.math.min

/**
 * ProseMirror's history isn't simply a way to roll back to a previous
 * state, because ProseMirror supports applying changes without adding
 * them to the history (for example during collaboration).
 * To this end, each 'Branch' (one for the undo history and one for
 * the redo history) keeps an array of 'Items', which can optionally
 * hold a step (an actual undoable change), and always hold a position
 * map (which is needed to move changes below them to apply to the
 * current document).
 * An item that has both a step and a selection bookmark is the start
 * of an 'event' — a group of changes that will be undone or redone at
 * once. (It stores only the bookmark, since that way we don't have to
 * provide a document until the selection is actually applied, which
 * is useful when compressing.)
 */

// Used to schedule history compression
const val MAX_EMPTY_ITEMS = 500

data class PopEventResult(
    val remaining: Branch,
    val transform: Transaction,
    val selection: SelectionBookmark
)

class Branch(val items: RopeSequence<Item>, val eventCount: Int) {

    // Pop the latest event off the branch's history and apply it
    // to a document transform.
    fun popEvent(state: PMEditorState, preserveItems: Boolean): PopEventResult? {
        if (this.eventCount == 0) return null

        var end = this.items.length
        while (true) {
            val next = this.items.get(end - 1)
            if (next?.selection != null) {
                end--
                break
            }
            end--
        }

        var remap: Mapping? = null
        var mapFrom: Int? = null
        if (preserveItems) {
            remap = this.remapping(end, this.items.length)
            mapFrom = remap.maps.size
        }
        val transform = state.tr
        var selection: SelectionBookmark? = null
        var remaining: Branch? = null
        val addAfter = mutableListOf<Item>()
        val addBefore = mutableListOf<Item>()

        this.items.forEach({ item, i ->
            if (item.step == null) {
                if (remap == null) {
                    remap = this.remapping(end, i + 1)
                    mapFrom = remap!!.maps.size
                }
                mapFrom = mapFrom!! - 1
                addBefore.add(item)
            } else {
                if (remap != null) {
                    addBefore.add(Item(item.map))
                    val step = item.step.map(remap!!.slice(mapFrom!!))
                    var map: StepMap? = null

                    if (step != null && transform.maybeStep(step).doc != null) {
                        map = transform.mapping.maps[transform.mapping.maps.size - 1]
                        addAfter.add(Item(map, null, null, addAfter.size + addBefore.size))
                    }
                    mapFrom = mapFrom!! - 1
                    if (map != null) remap!!.appendMap(map, mapFrom)
                } else {
                    transform.maybeStep(item.step)
                }

                if (item.selection != null) {
                    selection = if (remap != null) item.selection.map(remap!!.slice(mapFrom!!)) else item.selection
                    remaining = Branch(
                        this.items.slice(0, end).append(addBefore.reversed() + addAfter),
                        this.eventCount - 1
                    )
                    return@forEach false
                }
            }
            true
        }, this.items.length, 0)

        return PopEventResult(remaining!!, transform, selection!!)
    }

    // Create a new branch with the given transform added.
    fun addTransform(
        transform: Transform,
        selection: SelectionBookmark?,
        histOptions: HistoryOptions,
        preserveItems: Boolean
    ): Branch {
        var thisSelection = selection
        val newItems = mutableListOf<Item>()
        var eventCount = this.eventCount
        var oldItems = this.items
        var lastItem = if (!preserveItems && oldItems.length != 0) {
            oldItems.get(oldItems.length - 1)
        } else {
            null
        }

        for (i in 0 until transform.steps.size) {
            val step = transform.steps[i].invert(transform.docs[i])
            var item = Item(transform.mapping.maps[i], step, thisSelection)
            val merged = lastItem?.merge(item)
            if (merged != null) {
                item = merged
                if (i != 0) {
                    newItems.removeLast()
                } else {
                    oldItems = oldItems.slice(0, oldItems.length - 1)
                }
            }
            newItems.add(item)
            if (thisSelection != null) {
                eventCount++
                thisSelection = null
            }
            if (!preserveItems) {
                lastItem = item
            }
        }
        val overflow = eventCount - histOptions.depth
        if (overflow > DEPTH_OVERFLOW) {
            oldItems = cutOffEvents(oldItems, overflow)
            eventCount = eventCount - overflow
        }
        return Branch(oldItems.append(newItems), eventCount)
    }

    fun remapping(from: Int, to: Int): Mapping {
        val maps = Mapping()
        this.items.forEach({ item, i ->
            val mirrorPos = if (item.mirrorOffset != null && i - item.mirrorOffset >= from) {
                maps.maps.size - item.mirrorOffset
            } else {
                null
            }
            maps.appendMap(item.map, mirrorPos)
            true
        }, from, to)
        return maps
    }

    fun addMaps(array: List<StepMap>): Branch {
        if (this.eventCount == 0) return this
        return Branch(this.items.append(array.map { map -> Item(map) }), this.eventCount)
    }

    /** When the collab module receives remote changes, the history has
     * to know about those, so that it can adjust the steps that were
     * rebased on top of the remote changes, and include the position
     * maps for the remote changes in its array of items.
     */
    fun rebased(rebasedTransform: Transform, rebasedCount: Int): Branch {
        if (this.eventCount == 0) return this

        val rebasedItems = mutableListOf<Item>()
        val start = max(0, this.items.length - rebasedCount)

        val mapping = rebasedTransform.mapping
        var newUntil = rebasedTransform.steps.size
        var eventCount = this.eventCount
        this.items.forEach(
            { item, _ ->
                if (item.selection != null) eventCount--
                true
            },
            start
        )

        var iRebased = rebasedCount
        this.items.forEach({ item, _ ->
            val pos = mapping.getMirror(--iRebased) ?: return@forEach true
            newUntil = min(newUntil, pos)
            val map = mapping.maps[pos]
            if (item.step != null) {
                val step = rebasedTransform.steps[pos].invert(rebasedTransform.docs[pos])
                val selection = item.selection?.map(mapping.slice(iRebased + 1, pos))
                if (selection != null) eventCount++
                rebasedItems.add(Item(map, step, selection))
            } else {
                rebasedItems.add(Item(map))
            }
        }, start)

        val newMaps = mutableListOf<Item>()
        for (i in rebasedCount until newUntil) {
            newMaps.add(Item(mapping.maps[i]))
        }
        val items = this.items.slice(0, start).append(newMaps).append(rebasedItems)
        var branch = Branch(items, eventCount)

        if (branch.emptyItemCount() > MAX_EMPTY_ITEMS) {
            branch = branch.compress(this.items.length - rebasedItems.size)
        }
        return branch
    }

    fun emptyItemCount(): Int {
        var count = 0
        this.items.forEach({ item, _ ->
            if (item.step == null) {
                count++
            }
            true
        })
        return count
    }

    /** Compressing a branch means rewriting it to push the air (map-only
     * items) out. During collaboration, these naturally accumulate
     * because each remote change adds one. The `upto` argument is used
     * to ensure that only the items below a given level are compressed,
     * because `rebased` relies on a clean, untouched set of items in
     * order to associate old items with rebased steps
     */
    fun compress(upto: Int = this.items.length): Branch {
        val remap = this.remapping(0, upto)
        var mapFrom = remap.maps.size
        val items = mutableListOf<Item>()
        var events = 0
        this.items.forEach({ item, i ->
            if (i >= upto) {
                items.add(item)
                if (item.selection != null) events++
            } else if (item.step != null) {
                val step = item.step.map(remap.slice(mapFrom))
                val map = step?.getMap()
                mapFrom--
                if (map != null) remap.appendMap(map, mapFrom)
                if (step != null) {
                    val selection = item.selection?.map(remap.slice(mapFrom))
                    if (selection != null) events++
                    val newItem = Item(map!!.invert(), step, selection)
                    val merged = items.lastOrNull()?.merge(newItem)
                    val last = items.size - 1
                    if (merged != null) {
                        items[last] = merged
                    } else {
                        items.add(newItem)
                    }
                }
            } else {
                mapFrom--
            }
            true
        }, this.items.length, 0)
        return Branch(RopeSequence.from(items.reversed()), events)
    }

    override fun toString(): String {
        return "Branch(items: ${items::class.simpleName} eventCount:$eventCount)"
    }

    companion object {
        val empty = Branch(RopeSequence.empty(), 0)
    }
}

fun cutOffEvents(items: RopeSequence<Item>, n: Int): RopeSequence<Item> {
    var cnt = n
    var cutPoint: Int? = null
    items.forEach({ item, i ->
        if (item.selection != null && (cnt-- == 0)) {
            cutPoint = i
            false
        } else {
            true
        }
    })
    return items.slice(cutPoint!!)
}

class Item(
    // The (forward) step map for this item.
    val map: StepMap,
    // The inverted step
    val step: Step? = null,
    // If this is non-null, this item is the start of a group, and
    // this selection is the starting selection for the group (the one
    // that was active before the first step was applied)
    val selection: SelectionBookmark? = null,
    // If this item is the inverse of a previous mapping on the stack,
    // this points at the inverse's offset
    val mirrorOffset: Int? = null
) {

    fun merge(other: Item): Item? {
        if (this.step != null && other.step != null && other.selection == null) {
            val step = other.step.merge(this.step)
            if (step != null) {
                return Item(step.getMap().invert(), step, this.selection)
            }
        }
        return null
    }

    override fun toString(): String {
        return "Item(map=$map, step=$step, selection=$selection, mirrorOffset=$mirrorOffset)"
    }
}

/** The value of the state field that tracks undo/redo history for that
 * state. Will be stored in the plugin state when the history plugin
 * is active.
 */
data class HistoryState(
    var done: Branch,
    val undone: Branch,
    val prevRanges: List<IntRange>?,
    val prevTime: Long,
    val prevComposition: Int
)

const val DEPTH_OVERFLOW = 20

/** Record a transformation in undo history. */
@Suppress("ReturnCount", "ComplexMethod", "LongMethod")
fun applyTransaction(
    history: HistoryState,
    state: PMEditorState,
    tr: Transaction,
    options: HistoryOptions
): HistoryState {
    var thisHistory = history
    val historyTr = tr.getMeta(historyKey) as? HistoryPluginData
    var rebased: Int? = null
    if (historyTr != null) {
        return historyTr.historyState
    }

    if (tr.getMeta(closeHistoryKey) != null) {
        thisHistory = HistoryState(thisHistory.done, thisHistory.undone, null, 0, -1)
    }

    val appended = tr.getMeta("appendedTransaction") as? Transaction

    if (tr.steps.size == 0) {
        return thisHistory
    } else if (appended?.getMeta(historyKey) != null) {
        val appendedHistoryData = appended.getMeta(historyKey) as HistoryPluginData
        if (appendedHistoryData.redo) {
            return HistoryState(
                thisHistory.done.addTransform(tr, null, options, mustPreserveItems(state)),
                thisHistory.undone,
                rangesFor(tr.mapping.maps),
                thisHistory.prevTime,
                thisHistory.prevComposition
            )
        } else {
            return HistoryState(
                thisHistory.done,
                thisHistory.undone.addTransform(tr, null, options, mustPreserveItems(state)),
                null,
                thisHistory.prevTime,
                thisHistory.prevComposition
            )
        }
    } else if (tr.getMeta("addToHistory") != false && appended?.getMeta("addToHistory") != false) {
        // Group transforms that occur in quick succession into one event.
        val composition = tr.getMeta("composition") as? Int
        val newGroup = thisHistory.prevTime == 0L ||
            (
                appended == null && thisHistory.prevComposition != composition &&
                    (
                        thisHistory.prevTime < tr.time - options.newGroupDelay ||
                            !isAdjacentTo(tr, thisHistory.prevRanges!!)
                        )
                )
        val prevRanges = if (appended != null) {
            mapRanges(thisHistory.prevRanges, tr.mapping)
        } else {
            rangesFor(tr.mapping.maps)
        }
        val done = thisHistory.done.addTransform(
            tr,
            if (newGroup) state.selection.getBookmark() else null,
            options,
            mustPreserveItems(state)
        )
        return HistoryState(
            done,
            Branch.empty,
            prevRanges,
            tr.time,
            composition ?: thisHistory.prevComposition
        )
    } else if (tr.getMeta("rebased")?.also { rebased = it as Int } != null) {
        // Used by the collab module to tell the history that some of its
        // content has been rebased.
        return HistoryState(
            thisHistory.done.rebased(tr, rebased!!),
            thisHistory.undone.rebased(tr, rebased!!),
            mapRanges(thisHistory.prevRanges, tr.mapping),
            thisHistory.prevTime,
            thisHistory.prevComposition
        )
    } else {
        return HistoryState(
            thisHistory.done.addMaps(tr.mapping.maps),
            thisHistory.undone.addMaps(tr.mapping.maps),
            mapRanges(thisHistory.prevRanges, tr.mapping),
            thisHistory.prevTime,
            thisHistory.prevComposition
        )
    }
}

@Suppress("ReturnCount")
fun isAdjacentTo(transform: Transform, prevRanges: List<IntRange>): Boolean {
    if (prevRanges.isEmpty()) return false
    if (!transform.docChanged) return true
    var adjacent = false
    transform.mapping.maps[0].forEach { start, end, _, _ ->
        prevRanges.forEach { range ->
            if (start <= range.last && end >= range.first) {
                adjacent = true
            }
        }
    }
    return adjacent
}

fun rangesFor(maps: List<StepMap>): List<IntRange> {
    return buildList {
        for (i in maps.size - 1 downTo 0) {
            if (this.size > 0) break
            maps[i].forEach { _, _, from, to ->
                add(from..to)
            }
        }
    }
}

fun mapRanges(ranges: List<IntRange>?, mapping: Mapping): List<IntRange>? {
    if (ranges.isNullOrEmpty()) return null
    return buildList {
        ranges.forEach { range ->
            val from = mapping.map(range.first, 1)
            val to = mapping.map(range.last, -1)
            if (from <= to) add(from..to)
        }
    }
}

/** Apply the latest event from one branch to the document and shift the event
 * onto the other branch.
 */
fun histTransaction(history: HistoryState, state: PMEditorState, redo: Boolean): Transaction? {
    val preserveItems = mustPreserveItems(state)
    val histOptions = (historyKey.get(state)!!.spec as HistoryPluginSpec).config
    val pop = (if (redo) history.undone else history.done).popEvent(state, preserveItems) ?: return null

    val selection = pop.selection.resolve(pop.transform.doc)
    val added = (if (redo) history.done else history.undone)
        .addTransform(pop.transform, state.selection.getBookmark(), histOptions, preserveItems)

    val newHist = HistoryState(
        if (redo) added else pop.remaining,
        if (redo) pop.remaining else added,
        null,
        0,
        -1
    )
    return pop.transform.setSelection(selection)
        .setMeta(historyKey, HistoryPluginData(redo, historyState = newHist))
}

data class HistoryPluginData(val redo: Boolean, val historyState: HistoryState)

/** Check whether any plugin in the given state has a
// `historyPreserveItems` property in its spec, in which case we must
// preserve steps exactly as they came in, so that they can be
// rebased.
// Since this maintains a reference to plugins, the cachedPreserveItems/cachedPreserveItemsPlugins data is stored in
// PMEditorState to avoid memory leaks if they were kept static here
*/
@Suppress("NestedBlockDepth")
fun mustPreserveItems(state: PMEditorState): Boolean = with(state) {
    if (cachedPreserveItemsPlugins != plugins) {
        cachedPreserveItems = false
        cachedPreserveItemsPlugins = plugins
        for (plugin in plugins) {
            if (plugin.spec.additionalProps["historyPreserveItems"] == true) {
                cachedPreserveItems = true
                break
            }
        }
    }
    return cachedPreserveItems
}

/** Set a flag on the given transaction that will prevent further steps
// from being appended to an existing history event (so that they
// require a separate undo command to undo).
 */
fun closeHistory(tr: Transaction): Transaction {
    return tr.setMeta(closeHistoryKey, true)
}

val historyKey = PluginKey<HistoryState>("history")
val closeHistoryKey = PluginKey<HistoryState>("closeHistory")

interface HistoryOptions {
    // The amount of history events that are collected before the
    // oldest events are discarded. Defaults to 100.
    val depth: Int

    // The delay between changes after which a new group should be
    // started. Defaults to 500 (milliseconds). Note that when changes
    // aren't adjacent, a new group is always started.
    val newGroupDelay: Int
}

data class HistoryOptionsConfig(
    override val depth: Int = 100,
    override val newGroupDelay: Int = 500
) : HistoryOptions

/** Returns a plugin that enables the undo history for an editor. The
// plugin will track undo and redo stacks, which can be used with the
// [`undo`](#history.undo) and [`redo`](#history.redo) commands.
//
// You can set an `"addToHistory"` [metadata
// property](#state.Transaction.setMeta) of `false` on a transaction
// to prevent it from being rolled back by undo.
*/
class HistoryPlugin(config: HistoryOptionsConfig = HistoryOptionsConfig()) :
    Plugin<HistoryState>(HistoryPluginSpec(config))

class HistoryPluginSpec(conf: HistoryOptionsConfig) : PluginSpec<HistoryState>() {
    override val state: StateField<HistoryState> = object : StateField<HistoryState> {
        override fun init(config: EditorStateConfig, instance: PMEditorState): HistoryState {
            return HistoryState(Branch.empty, Branch.empty, null, 0, -1)
        }

        override fun apply(
            tr: Transaction,
            value: HistoryState,
            oldState: PMEditorState,
            newState: PMEditorState
        ): HistoryState {
            return applyTransaction(value, oldState, tr, conf)
        }
    }

    override val key: PluginKey<HistoryState> = historyKey

    override val additionalProps: Map<String, Any> = mapOf(
        "config" to conf
    )

    val config: HistoryOptionsConfig by additionalProps
}

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
fun buildCommand(redo: Boolean, scroll: Boolean): Command {
    return { state, dispatch ->
        val hist = historyKey.getState(state)
        if (hist == null || (if (redo) hist.undone else hist.done).eventCount == 0) {
            false
        } else {
            if (dispatch != null) {
                histTransaction(hist, state, redo)?.let {
                    dispatch(
                        if (scroll) {
                            it.scrollIntoView()
                        } else {
                            it
                        }
                    )
                }
            }
            true
        }
    }
}

/** A command function that undoes the last change, if any. */
val undo: Command = buildCommand(redo = false, scroll = true)

/** A command function that redoes the last undone change, if any. */
val redo: Command = buildCommand(redo = true, scroll = true)

/** A command function that undoes the last change. Don't scroll the selection into view. */
val undoNoScroll: Command = buildCommand(redo = false, scroll = false)

/** A command function that redoes the last undone change. Don't scroll the selection into view. */
val redoNoScroll: Command = buildCommand(redo = true, scroll = false)

/** The amount of undoable events available in a given state. */
fun undoDepth(state: PMEditorState): Int {
    val hist = historyKey.getState(state)
    return hist?.done?.eventCount ?: 0
}

/** The amount of redoable events available in a given editor state. */
fun redoDepth(state: PMEditorState): Int {
    val hist = historyKey.getState(state)
    return hist?.undone?.eventCount ?: 0
}
