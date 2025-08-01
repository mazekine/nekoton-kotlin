package com.mazekine.nekoton.abi

import com.mazekine.nekoton.models.Cell
import com.mazekine.nekoton.models.CellSlice
import com.mazekine.nekoton.models.Message
import kotlinx.serialization.Serializable

/**
 * Represents an event ABI definition for TON/Everscale smart contracts.
 * 
 * Events are emitted by smart contracts to signal that certain actions
 * have occurred. This class provides functionality to decode event data
 * from blockchain messages.
 * 
 * @property abiVersion The ABI version this event uses
 * @property name The event name
 * @property id The event ID (used for identification in messages)
 * @property inputs List of event parameters
 */
@Serializable
data class EventAbi(
    val abiVersion: AbiVersion,
    val name: String,
    val id: Int,
    val inputs: List<AbiParam>
) {
    /**
     * Decodes event data from a message.
     * 
     * @param message The message containing the event
     * @return Decoded event parameters
     * @throws IllegalArgumentException if the message doesn't contain this event
     */
    fun decodeMessage(message: Message): Map<String, Any> {
        val body = message.body ?: throw IllegalArgumentException("Message has no body")
        return decodeMessageBody(body)
    }

    /**
     * Decodes event data from a message body.
     * 
     * @param body The message body cell
     * @return Decoded event parameters
     * @throws IllegalArgumentException if the body doesn't contain this event
     */
    fun decodeMessageBody(body: Cell): Map<String, Any> {
        val slice = body.beginParse()
        
        // Read and verify event ID
        val eventId = slice.readUint(32).intValue()
        require(eventId == id) { "Event ID mismatch: expected $id, got $eventId" }
        
        // Decode event parameters
        val decodedParams = mutableMapOf<String, Any>()
        for (param in inputs) {
            decodedParams[param.name] = decodeParam(slice, param)
        }
        
        return decodedParams
    }

    /**
     * Decodes input parameters from a cell slice.
     * 
     * @param slice The cell slice to read from
     * @return Decoded event parameters
     */
    fun decodeInput(slice: CellSlice): Map<String, Any> {
        val decodedParams = mutableMapOf<String, Any>()
        for (param in inputs) {
            decodedParams[param.name] = decodeParam(slice, param)
        }
        return decodedParams
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventAbi

        if (name != other.name) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + id
        return result
    }

    override fun toString(): String {
        return "EventAbi(name='$name', id=0x${id.toString(16)})"
    }

    companion object {
        /**
         * Decodes a parameter value from a cell slice.
         */
        private fun decodeParam(slice: CellSlice, param: AbiParam): Any {
            // This would require the actual parameter decoding logic
            TODO("Parameter decoding not yet implemented")
        }
    }
}

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

/**
 * Represents different ABI parameter types.
 */
enum class AbiType {
    /** Unsigned integer */
    UINT,
    /** Signed integer */
    INT,
    /** Boolean value */
    BOOL,
    /** Byte array */
    BYTES,
    /** Fixed-size byte array */
    BYTES_FIXED,
    /** String */
    STRING,
    /** Address */
    ADDRESS,
    /** Cell reference */
    CELL,
    /** Token amount (grams) */
    GRAMS,
    /** Tuple (struct) */
    TUPLE,
    /** Array */
    ARRAY,
    /** Optional value */
    OPTIONAL,
    /** Map/dictionary */
    MAP;

    companion object {
        /**
         * Parses an ABI type string to determine the type.
         * 
         * @param typeString The type string from ABI
         * @return The corresponding AbiType
         */
        fun fromString(typeString: String): AbiType {
            val baseType = typeString.removeSuffix("[]")
            
            return when {
                baseType.startsWith("uint") -> UINT
                baseType.startsWith("int") -> INT
                baseType == "bool" -> BOOL
                baseType == "bytes" -> BYTES
                baseType.startsWith("bytes") && baseType.length > 5 -> BYTES_FIXED
                baseType == "string" -> STRING
                baseType == "address" -> ADDRESS
                baseType == "cell" -> CELL
                baseType == "grams" || baseType == "tokens" -> GRAMS
                baseType == "tuple" -> TUPLE
                baseType.startsWith("map(") -> MAP
                baseType.startsWith("optional(") -> OPTIONAL
                else -> throw IllegalArgumentException("Unknown ABI type: $typeString")
            }
        }
    }
}

/**
 * Utility class for ABI type operations.
 */
object AbiTypeUtils {
    /**
     * Gets the bit size for integer types.
     * 
     * @param typeString The type string (e.g., "uint256", "int32")
     * @return The bit size
     */
    fun getIntegerBitSize(typeString: String): Int {
        val sizeStr = typeString.removePrefix("uint").removePrefix("int")
        return sizeStr.toIntOrNull() ?: throw IllegalArgumentException("Invalid integer type: $typeString")
    }

    /**
     * Gets the byte size for fixed bytes types.
     * 
     * @param typeString The type string (e.g., "bytes32", "bytes4")
     * @return The byte size
     */
    fun getFixedBytesSize(typeString: String): Int {
        val sizeStr = typeString.removePrefix("bytes")
        return sizeStr.toIntOrNull() ?: throw IllegalArgumentException("Invalid bytes type: $typeString")
    }

    /**
     * Checks if a type is a variable-length type.
     * 
     * @param typeString The type string
     * @return true if the type is variable-length
     */
    fun isVariableLength(typeString: String): Boolean {
        val baseType = typeString.removeSuffix("[]")
        return when {
            baseType == "bytes" -> true
            baseType == "string" -> true
            baseType == "cell" -> true
            baseType == "tuple" -> true
            baseType.startsWith("map(") -> true
            baseType.startsWith("optional(") -> true
            else -> false
        }
    }

    /**
     * Parses map type parameters.
     * 
     * @param typeString The map type string (e.g., "map(uint256,address)")
     * @return Pair of key type and value type
     */
    fun parseMapType(typeString: String): Pair<String, String> {
        require(typeString.startsWith("map(") && typeString.endsWith(")")) {
            "Invalid map type: $typeString"
        }
        
        val content = typeString.removePrefix("map(").removeSuffix(")")
        val parts = content.split(",", limit = 2)
        require(parts.size == 2) { "Map type must have key and value types: $typeString" }
        
        return Pair(parts[0].trim(), parts[1].trim())
    }

    /**
     * Parses optional type parameter.
     * 
     * @param typeString The optional type string (e.g., "optional(uint256)")
     * @return The inner type
     */
    fun parseOptionalType(typeString: String): String {
        require(typeString.startsWith("optional(") && typeString.endsWith(")")) {
            "Invalid optional type: $typeString"
        }
        
        return typeString.removePrefix("optional(").removeSuffix(")")
    }
}
