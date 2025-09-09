package com.mazekine.nekoton.abi

import kotlinx.serialization.Serializable

/**
 * Represents an ABI parameter definition.
 *
 * @property name The parameter name
 * @property type The parameter type
 * @property components Optional components for complex types (tuples, arrays)
 */
@Serializable
data class AbiParam(
    val name: String,
    val type: String,
    val components: List<AbiParam>? = null
) {
    /**
     * Checks if this parameter is a complex type (tuple or array).
     *
     * @return true if the parameter has components
     */
    fun isComplex(): Boolean = components != null

    /**
     * Checks if this parameter is an array type.
     *
     * @return true if the type ends with []
     */
    fun isArray(): Boolean = type.endsWith("[]")

    /**
     * Checks if this parameter is a tuple type.
     *
     * @return true if the type is "tuple"
     */
    fun isTuple(): Boolean = type == "tuple"

    /**
     * Gets the base type without array notation.
     *
     * @return The base type string
     */
    fun getBaseType(): String = type.removeSuffix("[]")

    override fun toString(): String {
        return "AbiParam(name='$name', type='$type')"
    }
}