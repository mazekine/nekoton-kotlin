package com.mazekine.nekoton.abi

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