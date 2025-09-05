package com.mazekine.nekoton.crypto

import com.mazekine.nekoton.Native
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.util.Locale

/**
 * Represents an Ed25519 public key used for cryptographic operations in the TON/Everscale network.
 *
 * This class provides functionality for public key operations including signature verification,
 * encoding/decoding, and conversion between different formats.
 *
 * NOTE: Instances are immutable from the outside. Use factory methods to avoid leaking mutable buffers.
 */
@Serializable
data class PublicKey(
    /** Backing bytes for the 32-byte public key. Treat as immutable. */
    @SerialName("key_bytes")
    private val keyBytes: ByteArray
) {
    init {
        require(keyBytes.size == KEY_SIZE) { "Public key must be exactly $KEY_SIZE bytes" }
    }

    /** Creates a PublicKey from a hex string (with or without 0x prefix). */
    constructor(hex: String) : this(parseHexKey(hex))

    /** Creates a PublicKey from a BigInteger (left-pads or truncates to 32 bytes). */
    constructor(value: BigInteger) : this(
        value.toByteArray().let { bytes ->
            when {
                bytes.size == KEY_SIZE -> bytes
                bytes.size  > KEY_SIZE -> bytes.copyOfRange(bytes.size - KEY_SIZE, bytes.size) // keep last 32
                else -> ByteArray(KEY_SIZE).also { dest ->                                   // left-pad with zeros
                    System.arraycopy(bytes, 0, dest, KEY_SIZE - bytes.size, bytes.size)
                }
            }
        }
    )

    /** Returns a defensive copy of the 32-byte public key. */
    fun toBytes(): ByteArray = keyBytes.copyOf()

    /** Hex representation (lowercase, no prefix). */
    fun toHex(): String = BytesCodec.hexEncode(keyBytes)

    /** BigInteger view (positive). */
    fun toBigInteger(): BigInteger =
        BigInteger.fromByteArray(keyBytes, sign = com.ionspin.kotlin.bignum.integer.Sign.POSITIVE)

    /**
     * Verifies a signature against the provided data.
     *
     * @param data Raw data that was signed (caller is responsible for hashing if applicable).
     * @param signature Signature bytes wrapper.
     * @param signatureId Optional signature ID to be appended to `data` before verification.
     * @return true if the signature is valid; false otherwise.
     */
    fun verifySignature(data: ByteArray, signature: Signature, signatureId: Int? = null): Boolean {
        return try {
            if (Native.isInitialized()) {
                val sigId = signatureId?.toLong() ?: 0L
                Native.verifySignature(keyBytes, data, signature.toBytes(), sigId)
            } else {
                val publicKeyParams = Ed25519PublicKeyParameters(keyBytes, 0)
                val signer = Ed25519Signer().apply { init(false, publicKeyParams) }

                val dataToVerify =
                    if (signatureId != null) appendSignatureId(data, signatureId) else data

                signer.update(dataToVerify, 0, dataToVerify.size)
                signer.verifySignature(signature.toBytes())
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encodes the public key to a string.
     *
     * @param encoding Encoding type
     */
    fun encode(encoding: Encoding = Encoding.HEX): String = when (encoding) {
        Encoding.HEX    -> toHex()
        Encoding.BASE64 -> java.util.Base64.getEncoder().encodeToString(keyBytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PublicKey
        return keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int = keyBytes.contentHashCode()

    override fun toString(): String = toHex()

    companion object {
        /** Size of Ed25519 public key in bytes. */
        const val KEY_SIZE = 32

        /** Factory that defensively copies the input. */
        fun fromBytes(bytes: ByteArray): PublicKey = PublicKey(bytes.copyOf())

        /** Factory for hex/base64 strings. */
        fun fromString(value: String, encoding: Encoding = Encoding.HEX): PublicKey =
            PublicKey(BytesCodec.decode(value, encoding))

        /** Parse hex string to bytes; accepts optional "0x" prefix. */
        private fun parseHexKey(hex: String): ByteArray =
            BytesCodec.hexDecode(hex, expectedBytes = KEY_SIZE)

        /** Append 4-byte big-endian signatureId to data with a single allocation. */
        private fun appendSignatureId(data: ByteArray, signatureId: Int): ByteArray {
            val out = ByteArray(data.size + 4)
            System.arraycopy(data, 0, out, 0, data.size)
            out[data.size]     = (signatureId ushr 24).toByte()
            out[data.size + 1] = (signatureId ushr 16).toByte()
            out[data.size + 2] = (signatureId ushr 8).toByte()
            out[data.size + 3] = signatureId.toByte()
            return out
        }
    }
}