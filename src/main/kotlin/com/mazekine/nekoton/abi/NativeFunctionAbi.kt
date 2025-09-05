package com.mazekine.nekoton.abi

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.models.Cell
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import java.util.Base64

/**
 * Native-backed implementation of a function ABI that uses JNI calls.
 *
 * Encode entry points:
 *  - encodeCall(Map<String, Any?>)            — convenience; converts Kotlin types to JsonElement
 *  - encodeCallJson(Map<String, JsonElement>) — zero-copy for already-typed JSON
 *  - encodeCallRawJson(String)                — fastest path if you already have a JSON string
 *
 * Decode entry points:
 *  - decodeOutputJson(Cell)                   — typed map of JsonElements
 *  - decodeOutputAs<T>(Cell)                  — deserialize to a @Serializable DTO
 */
internal class NativeFunctionAbi(
    private val abiHandle: Long,
    private val functionName: String,
) {
    init {
        require(abiHandle != 0L) { "abiHandle must be non-zero" }
        require(functionName.isNotBlank()) { "functionName must not be blank" }
    }

    val name: String = functionName

    // Function param metadata placeholders (wire from native if needed)
    val inputs: List<AbiParam> = emptyList()
    val outputs: List<AbiParam> = emptyList()

    /** Encode call from a Kotlin map (numbers, booleans, lists, maps, ByteArray, Cell, etc.). */
    fun encodeCall(inputs: Map<String, Any?>): Cell =
        encodeCallJson(inputs.mapValues { (_, v) -> anyToJsonElement(v) })

    /** Encode call from a pre-built JsonElement map (no extra boxing). */
    fun encodeCallJson(inputs: Map<String, JsonElement>): Cell {
        val json = JSON.encodeToString(JsonObject(inputs))
        return encodeCallRawJson(json)
    }

    /** Fastest path if you already have a JSON string for the inputs. */
    fun encodeCallRawJson(inputsJson: String): Cell {
        val encodedBytes = runCatching {
            Native.encodeFunctionCall(abiHandle, functionName, inputsJson)
        }.getOrElse { e ->
            throw IllegalStateException("encodeFunctionCall failed for '$functionName'", e)
        }
        return Cell.fromBoc(encodedBytes)
    }

    /** Decode output into a typed JSON map (preserves numbers, booleans, nested structures). */
    fun decodeOutputJson(outputCell: Cell): Map<String, JsonElement> {
        val outputBoc = outputCell.toBoc()
        val outputJson = runCatching {
            Native.decodeFunctionOutput(abiHandle, functionName, outputBoc)
        }.getOrElse { e ->
            throw IllegalStateException("decodeFunctionOutput failed for '$functionName'", e)
        }

        val element = runCatching { JSON.parseToJsonElement(outputJson) }
            .getOrElse { return emptyMap() }
        val obj = element as? JsonObject ?: return emptyMap()

        // Preserve insertion order (useful for tests/debug)
        val out = LinkedHashMap<String, JsonElement>(obj.size)
        for ((k, v) in obj) out[k] = v
        return out
    }

    /** Inline, reified convenience wrapper. */
    inline fun <reified T> decodeOutputAs(outputCell: Cell): T =
        decodeOutputAs(outputCell, serializer())

    /** Non-inline worker that can access private members safely. */
    fun <T> decodeOutputAs(outputCell: Cell, deserializer: DeserializationStrategy<T>): T {
        val outputBoc = outputCell.toBoc()
        val outputJson = runCatching {
            Native.decodeFunctionOutput(abiHandle, functionName, outputBoc)
        }.getOrElse { e ->
            throw IllegalStateException("decodeFunctionOutput failed for '$functionName'", e)
        }
        return JSON.decodeFromString(deserializer, outputJson)
    }

    // -------------------- internals --------------------

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value

        // primitives
        is Boolean -> JsonPrimitive(value)
        is Int, is Long, is Short, is Byte -> JsonPrimitive((value as Number).toLong())
        is Float, is Double -> JsonPrimitive((value as Number).toDouble())
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)

        // binary helpers
        is ByteArray -> JsonPrimitive(Base64.getEncoder().encodeToString(value))
        is Cell -> JsonPrimitive(Base64.getEncoder().encodeToString(value.toBoc()))

        // collections
        is Array<*> -> JsonArray(value.map { anyToJsonElement(it) })
        is Iterable<*> -> JsonArray(value.map { anyToJsonElement(it) })
        is Map<*, *> -> buildJsonObject {
            for ((k, v) in value) {
                if (k is String) put(k, anyToJsonElement(v))
            }
        }

        // last resort: string form
        else -> JsonPrimitive(value.toString())
    }

    private companion object {
        val JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }
    }
}
