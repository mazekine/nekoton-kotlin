package com.mazekine.nekoton.abi

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.models.Cell
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Native-backed implementation of FunctionAbi that uses JNI calls.
 * 
 * @param abiHandle Native ABI handle
 * @param functionName Function name
 */
internal class NativeFunctionAbi(
    private val abiHandle: Long,
    private val functionName: String
) {
    
    val name: String = functionName
    
    val inputs: List<AbiParam> = emptyList()
    
    val outputs: List<AbiParam> = emptyList()
    
    /**
     * Encodes function call with the given inputs.
     * 
     * @param inputs Map of input parameter names to values
     * @return Encoded function call as Cell
     */
    fun encodeCall(inputs: Map<String, Any>): Cell {
        val inputsJson = Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), 
            buildJsonObject {
                inputs.forEach { (key, value) ->
                    put(key, JsonPrimitive(value.toString()))
                }
            })
        
        val encodedBytes = Native.encodeFunctionCall(abiHandle, functionName, inputsJson)
        return Cell.fromBoc(encodedBytes)
    }
    
    /**
     * Decodes function output from the given cell.
     * 
     * @param outputCell Cell containing the function output
     * @return Decoded output as Map
     */
    fun decodeOutput(outputCell: Cell): Map<String, Any> {
        val outputBoc = outputCell.toBoc()
        val outputJson = Native.decodeFunctionOutput(abiHandle, functionName, outputBoc)
        
        return try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(outputJson)
                .mapValues { it.value.toString() }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
