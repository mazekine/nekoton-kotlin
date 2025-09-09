package com.mazekine.nekoton.abi

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Represents a function call with input and output data.
 *
 * @property function The function ABI
 * @property input Input parameters
 * @property output Output parameters
 */
@Serializable
data class FunctionCall(
    val function: FunctionAbi,
    val input: Map<String, @Contextual Any>,
    val output: Map<String, @Contextual Any>
){
    /**
     * Returns a human-readable representation of this call.
     */
    override fun toString(): String {
        return "FunctionCall(${function.name}, input=${input.keys}, output=${output.keys})"
    }
}