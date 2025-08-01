package com.mazekine.nekoton.crypto

import com.mazekine.nekoton.Native
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest

/**
 * Represents an Ed25519 public key used for cryptographic operations in the TON/Everscale network.
 * 
 * This class provides functionality for public key operations including signature verification,
 * encoding/decoding, and conversion between different formats.
 * 
 * @property keyBytes The 32-byte Ed25519 public key
 */
@Serializable
data class PublicKey(
    private val keyBytes: ByteArray
) {
    init {
        require(keyBytes.size == KEY_SIZE) { "Public key must be exactly $KEY_SIZE bytes" }
    }

    /**
     * Creates a PublicKey from a hex string.
     * 
     * @param hex The public key as a hex string (with or without 0x prefix)
     */
    constructor(hex: String) : this(parseHexKey(hex))

    /**
     * Creates a PublicKey from a BigInteger.
     * 
     * @param value The public key as a BigInteger
     */
    constructor(value: BigInteger) : this(value.toByteArray().let { bytes ->
        when {
            bytes.size == KEY_SIZE -> bytes
            bytes.size > KEY_SIZE -> bytes.takeLast(KEY_SIZE).toByteArray()
            else -> ByteArray(KEY_SIZE - bytes.size) + bytes
        }
    })

    /**
     * Gets the public key bytes.
     * 
     * @return The 32-byte public key
     */
    fun toBytes(): ByteArray = keyBytes.copyOf()

    /**
     * Converts the public key to a hex string.
     * 
     * @return Hex representation of the public key
     */
    fun toHex(): String = keyBytes.joinToString("") { "%02x".format(it) }

    /**
     * Converts the public key to a BigInteger.
     * 
     * @return The public key as a BigInteger
     */
    fun toBigInteger(): BigInteger = BigInteger.fromByteArray(keyBytes, sign = com.ionspin.kotlin.bignum.integer.Sign.POSITIVE)

    /**
     * Verifies a signature against the provided data.
     * 
     * @param data The data that was signed
     * @param signature The signature to verify
     * @param signatureId Optional signature ID for extended verification
     * @return true if the signature is valid
     */
    fun verifySignature(data: ByteArray, signature: Signature, signatureId: Int? = null): Boolean {
        return try {
            if (Native.isInitialized()) {
                val sigId = signatureId?.toLong() ?: 0L
                Native.verifySignature(keyBytes, data, signature.toBytes(), sigId)
            } else {
                val publicKeyParams = Ed25519PublicKeyParameters(keyBytes, 0)
                val signer = Ed25519Signer()
                signer.init(false, publicKeyParams)
                
                val dataToVerify = if (signatureId != null) {
                    extendSignatureWithId(data, signatureId)
                } else {
                    data
                }
                
                signer.update(dataToVerify, 0, dataToVerify.size)
                signer.verifySignature(signature.toBytes())
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes the public key to the specified format.
     * 
     * @param encoding The encoding format (hex, base64)
     * @return Encoded public key string
     */
    fun encode(encoding: String = "hex"): String {
        return when (encoding.lowercase()) {
            "hex" -> toHex()
            "base64" -> java.util.Base64.getEncoder().encodeToString(keyBytes)
            else -> throw IllegalArgumentException("Unsupported encoding: $encoding")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PublicKey

        return keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int {
        return keyBytes.contentHashCode()
    }

    override fun toString(): String {
        return toHex()
    }

    companion object {
        /**
         * Size of Ed25519 public key in bytes.
         */
        const val KEY_SIZE = 32

        /**
         * Creates a PublicKey from a byte array.
         * 
         * @param bytes The public key bytes
         * @return PublicKey instance
         */
        fun fromBytes(bytes: ByteArray): PublicKey = PublicKey(bytes)

        /**
         * Creates a PublicKey from an encoded string.
         * 
         * @param value The encoded public key string
         * @param encoding The encoding format (hex, base64)
         * @return PublicKey instance
         */
        fun fromString(value: String, encoding: String = "hex"): PublicKey {
            val bytes = when (encoding.lowercase()) {
                "hex" -> parseHexKey(value)
                "base64" -> java.util.Base64.getDecoder().decode(value)
                else -> throw IllegalArgumentException("Unsupported encoding: $encoding")
            }
            return PublicKey(bytes)
        }

        /**
         * Parses a hex string to bytes.
         */
        private fun parseHexKey(hex: String): ByteArray {
            val cleanHex = hex.removePrefix("0x")
            require(cleanHex.length == KEY_SIZE * 2) { "Hex public key must be ${KEY_SIZE * 2} characters" }
            
            return cleanHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        /**
         * Extends signature data with signature ID for verification.
         */
        private fun extendSignatureWithId(data: ByteArray, signatureId: Int): ByteArray {
            val idBytes = ByteArray(4)
            idBytes[0] = (signatureId shr 24).toByte()
            idBytes[1] = (signatureId shr 16).toByte()
            idBytes[2] = (signatureId shr 8).toByte()
            idBytes[3] = signatureId.toByte()
            
            return data + idBytes
        }
    }
}
