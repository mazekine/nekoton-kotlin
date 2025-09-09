package com.mazekine.nekoton.abi

import kotlinx.serialization.Serializable

/**
 * Represents an ABI version with major and minor components.
 *
 * Examples: 2.0, 2.1, 2.2, 2.3
 */
@Serializable
data class AbiVersion(val major: Int, val minor: Int = 0) {
    override fun toString(): String = "$major.$minor"

    companion object {
        /** Predefined known versions */
        val V2_0 = AbiVersion(2, 0)
        val V2_1 = AbiVersion(2, 1)
        val V2_2 = AbiVersion(2, 2)
        val V2_3 = AbiVersion(2, 3)

        /** Parses version strings like "2", "2.2", "2.3" */
        fun parse(value: String): AbiVersion {
            val parts = value.trim().split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return AbiVersion(major, minor)
        }
    }
}