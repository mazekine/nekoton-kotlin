package com.mazekine.nekoton.abi

/**
 * Represents different ABI parameter types.
 */
enum class AbiParamType {
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
        fun fromString(typeString: String): AbiParamType {
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