package com.broxus.nekoton.models

import kotlinx.serialization.Serializable

/**
 * Represents a blockchain address in the TON/Everscale network.
 * 
 * This class provides functionality for working with blockchain addresses,
 * including parsing, validation, and conversion between different formats.
 * 
 * @property workchain The workchain ID (-1 for masterchain, 0 for basechain)
 * @property address The 256-bit address as a byte array
 */
@Serializable
data class Address(
    val workchain: Int,
    val address: ByteArray
) {
    init {
        require(address.size == 32) { "Address must be exactly 32 bytes" }
        require(workchain in -128..127) { "Workchain must be in range -128..127" }
    }

    /**
     * Creates an Address from a string representation.
     * 
     * @param addressString The address string in format "workchain:address"
     * @return Address instance
     * @throws IllegalArgumentException if the address format is invalid
     */
    constructor(addressString: String) : this(
        parseWorkchain(addressString),
        parseAddress(addressString)
    )

    /**
     * Converts the address to its string representation.
     * 
     * @return String representation in format "workchain:address"
     */
    override fun toString(): String {
        val hexAddress = address.joinToString("") { "%02x".format(it) }
        return "$workchain:$hexAddress"
    }

    /**
     * Converts the address to a user-friendly format.
     * 
     * @param bounceable Whether the address should be bounceable
     * @param testOnly Whether this is a test-only address
     * @return Base64-encoded user-friendly address
     */
    fun toUserFriendly(bounceable: Boolean = true, testOnly: Boolean = false): String {
        // Implementation would require TON address encoding logic
        // This is a placeholder for the actual implementation
        TODO("User-friendly address encoding not yet implemented")
    }

    /**
     * Checks if this address equals another address.
     * Custom equals implementation to handle ByteArray comparison.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Address

        if (workchain != other.workchain) return false
        if (!address.contentEquals(other.address)) return false

        return true
    }

    /**
     * Generates hash code for the address.
     * Custom hashCode implementation to handle ByteArray.
     */
    override fun hashCode(): Int {
        var result = workchain
        result = 31 * result + address.contentHashCode()
        return result
    }

    companion object {
        /**
         * Creates an Address from a hex string.
         * 
         * @param workchain The workchain ID
         * @param hexAddress The address as a hex string
         * @return Address instance
         */
        fun fromHex(workchain: Int, hexAddress: String): Address {
            val cleanHex = hexAddress.removePrefix("0x")
            require(cleanHex.length == 64) { "Hex address must be 64 characters (32 bytes)" }
            
            val addressBytes = cleanHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
            
            return Address(workchain, addressBytes)
        }

        /**
         * Parses workchain from address string.
         */
        private fun parseWorkchain(addressString: String): Int {
            val parts = addressString.split(":")
            require(parts.size == 2) { "Address must be in format 'workchain:address'" }
            return parts[0].toInt()
        }

        /**
         * Parses address bytes from address string.
         */
        private fun parseAddress(addressString: String): ByteArray {
            val parts = addressString.split(":")
            require(parts.size == 2) { "Address must be in format 'workchain:address'" }
            
            val hexAddress = parts[1]
            require(hexAddress.length == 64) { "Address part must be 64 hex characters" }
            
            return hexAddress.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
}
