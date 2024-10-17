package com.atlassian.prosemirror.transform

import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.RangeError
import com.atlassian.prosemirror.model.ReplaceError
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.Slice
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

val stepsByID = mutableMapOf<String, StepJsonParser<*>>()

interface StepJsonParser<T : Step> {
    fun fromJSON(schema: Schema, json: JsonObject): T
}

// A step object represents an atomic change. It generally applies only to the document it was
// created for, since the positions stored in it will only make sense for that document.
//
// New steps are defined by creating classes that extend `Step`, overriding the `apply`, `invert`,
// `map`, `getMap` and `fromJSON` methods, and registering your class with a unique
// JSON-serialization identifier using [`Step.jsonID`](#transform.Step^jsonID).
abstract class Step {

    // The start of the unmarked range.
    abstract val from: Int

    // The end of the unmarked range.
    abstract val to: Int

    // Applies this step to the given document, returning a result object that either indicates
    // failure, if the step can not be applied to this document, or indicates success by containing
    // a transformed document.
    abstract fun apply(doc: Node): StepResult

    // Get the step map that represents the changes made by this step, and which can be used to
    // transform between positions in the old and the new document.
    open fun getMap(): StepMap {
        return StepMap.empty
    }

    // Create an inverted version of this step. Needs the document as it was before the step as
    // argument.
    abstract fun invert(doc: Node): Step

    // Map this step through a mappable thing, returning either a version of that step with its
    // positions adjusted, or `null` if the step was entirely deleted by the mapping.
    abstract fun map(mapping: Mappable): Step?

    // Try to merge this step with another one, to be applied directly after it. Returns the merged
    // step when possible, null if the steps can't be merged.
    open fun merge(other: Step): Step? {
        return null
    }

    // Create a JSON-serializeable representation of this step. When defining this for a custom
    // subclass, make sure the result object includes the step type's
    // [JSON id](#transform.Step^jsonID) under the `stepType` property.
    abstract fun toJSON(): JsonObject

    companion object {
        // Deserialize a step from its JSON representation. Will call through to the step class' own
        // implementation of this method.
        @Suppress("UnusedPrivateMember")
        fun fromJSON(schema: Schema, json: JsonObject?): Step? {
            if (json?.containsKey("stepType") != true) throw RangeError("Invalid input for Step.fromJSON")
            val stepType = json["stepType"]!!.jsonPrimitive.content
            val stepJsonParser: StepJsonParser<*> =
                stepsByID[stepType] ?: return null
            // TODO log error or throw
            // throw RangeError("No step type $stepType defined")
            return stepJsonParser.fromJSON(schema, json)
        }

        // To be able to serialize steps to JSON, each step needs a string ID to attach to its JSON
        // representation. Use this method to register an ID for your step classes. Try to pick
        // something that's unlikely to clash with steps from other modules.
        fun jsonID(id: String, stepJsonParser: StepJsonParser<*>) {
            if (id in stepsByID) throw RangeError("Duplicate use of step JSON ID $id")
            stepsByID[id] = stepJsonParser
        }
    }
}

// The result of [applying](#transform.Step.apply) a step. Contains either a new document or a
// failure value.
class StepResult internal constructor(
    // The transformed document, if successful.
    val doc: Node?,
    // The failure message, if unsuccessful.
    val failed: String?
) {

    companion object {
        // Create a successful step result.
        fun ok(doc: Node) = StepResult(doc, null)

        // Create a failed step result.
        fun fail(message: String) = StepResult(null, message)

        // Call [`Node.replace`](#model.Node.replace) with the given arguments. Create a successful
        // result if it succeeds, and a failed one if it throws a `ReplaceError`.
        fun fromReplace(doc: Node, from: Int, to: Int, slice: Slice): StepResult {
            return try {
                ok(doc.replace(from, to, slice))
            } catch (e: ReplaceError) {
                fail(e.message)
            } catch (e: RangeError) {
                // TODO: check if still need this catch after updating to latest prosemirror-transform
                fail(e.message ?: "RangeError")
            }
        }
    }
}
