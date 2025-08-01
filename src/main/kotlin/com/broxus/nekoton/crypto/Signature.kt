package com.broxus.nekoton.crypto

import kotlinx.serialization.Serializable

/**
 * Represents an Ed25519 signature in the TON/Everscale network.
 * 
 * This class encapsulates signature data and provides methods for
 * encoding, decoding, and working with cryptographic signatures.
 * 
 * @property signatureBytes The 64-byte Ed25519 signature
 */
@Serializable
data class Signature(
    private val signatureBytes: ByteArray
) {
    init {
        require(signatureBytes.size == SIGNATURE_SIZE) { "Signature must be exactly $SIGNATURE_SIZE bytes" }
    }

    /**
     * Creates a Signature from a hex string.
     * 
     * @param hex The signature as a hex string (with or without 0x prefix)
     */
    constructor(hex: String) : this(parseHexSignature(hex))

    /**
     * Gets the signature bytes.
     * 
     * @return The 64-byte signature
     */
    fun toBytes(): ByteArray = signatureBytes.copyOf()

    /**
     * Converts the signature to a hex string.
     * 
     * @return Hex representation of the signature
     */
    fun toHex(): String = signatureBytes.joinToString("") { "%02x".format(it) }

    /**
     * Encodes the signature to the specified format.
     * 
     * @param encoding The encoding format (hex, base64)
     * @return Encoded signature string
     */
    fun encode(encoding: String = "hex"): String {
        return when (encoding.lowercase()) {
            "hex" -> toHex()
            "base64" -> java.util.Base64.getEncoder().encodeToString(signatureBytes)
            else -> throw IllegalArgumentException("Unsupported encoding: $encoding")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Signature

        return signatureBytes.contentEquals(other.signatureBytes)
    }

    override fun hashCode(): Int {
        return signatureBytes.contentHashCode()
    }

    override fun toString(): String {
        return "Signature(${toHex()})"
    }

    companion object {
        /**
         * Size of Ed25519 signature in bytes.
         */
        const val SIGNATURE_SIZE = 64

        /**
         * Creates a Signature from a byte array.
         * 
         * @param bytes The signature bytes
         * @return Signature instance
         */
        fun fromBytes(bytes: ByteArray): Signature = Signature(bytes)

        /**
         * Creates a Signature from an encoded string.
         * 
         * @param value The encoded signature string
         * @param encoding The encoding format (hex, base64)
         * @return Signature instance
         */
        fun fromString(value: String, encoding: String = "hex"): Signature {
            val bytes = when (encoding.lowercase()) {
                "hex" -> parseHexSignature(value)
                "base64" -> java.util.Base64.getDecoder().decode(value)
                else -> throw IllegalArgumentException("Unsupported encoding: $encoding")
            }
            return Signature(bytes)
        }

        /**
         * Parses a hex string to signature bytes.
         */
        private fun parseHexSignature(hex: String): ByteArray {
            val cleanHex = hex.removePrefix("0x")
            require(cleanHex.length == SIGNATURE_SIZE * 2) { "Hex signature must be ${SIGNATURE_SIZE * 2} characters" }
            
            return cleanHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
}
