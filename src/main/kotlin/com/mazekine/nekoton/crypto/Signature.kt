package com.mazekine.nekoton.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.Locale

/**
 * Represents an Ed25519 signature (64 bytes) with encoding helpers.
 */
@Serializable
data class Signature(
    @SerialName("bytes")
    private val signatureBytes: ByteArray
) {
    init {
        require(signatureBytes.size == SIGNATURE_SIZE) {
            "Signature must be exactly $SIGNATURE_SIZE bytes"
        }
    }

    /** Construct from hex (with or without 0x prefix). */
    constructor(hex: String) : this(parseHexSignature(hex))

    /** Defensive copy of raw bytes. */
    fun toBytes(): ByteArray = signatureBytes.copyOf()

    /** Lowercase hex string (no prefix). */
    fun toHex(): String = BytesCodec.hexEncode(signatureBytes)

    /** Encode using a shared enum (preferred). */
    fun encode(encoding: Encoding = Encoding.HEX): String =
        BytesCodec.encode(signatureBytes, encoding)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false
        return signatureBytes.contentEquals(other.signatureBytes)
    }

    override fun hashCode(): Int = signatureBytes.contentHashCode()

    override fun toString(): String = "Signature(${toHex()})"

    companion object {
        /** Size of an Ed25519 signature in bytes. */
        const val SIGNATURE_SIZE = 64

        /** Defensive factory from bytes. */
        fun fromBytes(bytes: ByteArray): Signature = Signature(bytes.copyOf())

        /** Factory from string with enum encoding. */
        fun fromString(value: String, encoding: Encoding = Encoding.HEX): Signature =
            Signature(BytesCodec.decode(value, encoding))


        /** Parse hex string to raw signature bytes (accepts optional 0x/0X prefix). */
        private fun parseHexSignature(hex: String): ByteArray =
            BytesCodec.hexDecode(hex, expectedBytes = SIGNATURE_SIZE)
    }
}
