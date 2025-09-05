package com.mazekine.nekoton.crypto

import com.mazekine.nekoton.Native
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

/**
 * Represents an Ed25519 key pair used for cryptographic operations in the TVM network.
 *
 * Optimized to:
 *  - avoid duplicate private-key allocations
 *  - separate native vs BC storage via a Signer strategy
 *  - validate input and defensively copy secrets
 */
@Serializable
data class KeyPair(
    val publicKey: PublicKey
) {
    /**
     * Signer strategy hides how/where the secret is stored.
     */
    @kotlinx.serialization.Transient
    private lateinit var signer: Signer

    private constructor(publicKey: PublicKey, signer: Signer) : this(publicKey) {
        this.signer = signer
    }

    /**
     * (Deprecated) Old secondary ctor. Use [fromSecret] instead.
     */
    @Deprecated("Use KeyPair.fromSecret(secret) for clearer ownership & fewer allocations")
    constructor(secretBytes: ByteArray) : this(
        publicKeyFromSecret(secretBytes)
    ) {
        // Body runs after we’ve delegated to the primary constructor; now set the signer.
        val copy = secretBytes.copyOf()
        signer = if (Native.isInitialized()) {
            NativeSigner(copy)
        } else {
            BcSigner(Ed25519PrivateKeyParameters(copy, 0))
        }
    }

    /**
     * Returns a defensive copy of the 32-byte private key (if available).
     * Note: callers should wipe the returned array when done.
     */
    fun getSecretKey(): ByteArray = signer.secret()?.copyOf()
        ?: throw UnsupportedOperationException("Secret key is not accessible")

    /**
     * Signs data with the private key.
     *
     * @param data The data to sign
     * @param signatureId Optional signature ID for extended signing
     * @return The signature
     */
    fun sign(data: ByteArray, signatureId: Int? = null): Signature {
        // Hash first (kept for parity with previous behavior)
        val hashed = MessageDigest.getInstance("SHA-256").digest(data)
        val toSign = if (signatureId != null) extendSignatureWithId(hashed, signatureId) else hashed
        return Signature(signer.sign(toSign, signatureId))
    }

    /**
     * Signs raw data without hashing.
     */
    fun signRaw(data: ByteArray, signatureId: Int? = null): Signature {
        val toSign = if (signatureId != null) extendSignatureWithId(data, signatureId) else data
        return Signature(signer.sign(toSign, signatureId))
    }

    /**
     * Verifies a signature against the provided data using this key pair's public key.
     */
    fun verifySignature(data: ByteArray, signature: Signature, signatureId: Int? = null): Boolean {
        return publicKey.verifySignature(data, signature, signatureId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KeyPair
        return publicKey == other.publicKey
    }

    override fun hashCode(): Int = publicKey.hashCode()

    override fun toString(): String = "KeyPair(publicKey=${publicKey.toHex()})"

    // --------- Strategy types ---------

    private interface Signer {
        fun sign(data: ByteArray, signatureId: Int? = null): ByteArray
        fun secret(): ByteArray? = null
        fun destroy() {}
    }

    /**
     * Pure-JVM (Bouncy Castle) signer, keeps a single Ed25519PrivateKeyParameters instance.
     */
    private class BcSigner(
        private val priv: Ed25519PrivateKeyParameters
    ) : Signer {
        override fun sign(data: ByteArray, signatureId: Int?): ByteArray {
            val s = Ed25519Signer()
            s.init(true, priv)
            s.update(data, 0, data.size)
            return s.generateSignature()
        }
        override fun secret(): ByteArray = priv.encoded
    }

    /**
     * Native signer stores only a defensive copy of the 32-byte secret and uses Native.signData.
     */
    private class NativeSigner(
        private var secretCopy: ByteArray // owned; may be zeroized on destroy()
    ) : Signer {
        override fun sign(data: ByteArray, signatureId: Int?): ByteArray {
            val sigId = signatureId?.toLong() ?: 0L
            return Native.signData(secretCopy, data, sigId)
        }
        override fun secret(): ByteArray = secretCopy
        override fun destroy() {
            Arrays.fill(secretCopy, 0)
        }
    }

    companion object {
        // Used ONLY by the deprecated ctor so it can delegate safely.
        private fun publicKeyFromSecret(secret: ByteArray): PublicKey {
            require(secret.size == 32) { "Ed25519 secret must be 32 bytes" }
            return if (Native.isInitialized()) {
                PublicKey(Native.publicKeyFromSecret(secret))
            } else {
                val tmpPriv = Ed25519PrivateKeyParameters(secret, 0)
                PublicKey(tmpPriv.generatePublicKey().encoded)
            }
        }

        /**
         * Preferred constructor: creates a KeyPair from a 32-byte Ed25519 private key.
         * - Validates size
         * - Defensively copies the secret
         * - Avoids duplicate private-key allocations
         * - Uses Native path if available; otherwise BC
         */
        fun fromSecret(secret: ByteArray): KeyPair {
            require(secret.size == 32) { "Ed25519 secret must be 32 bytes" }
            val copy = secret.copyOf()
            return if (Native.isInitialized()) {
                val pub = Native.publicKeyFromSecret(copy)
                KeyPair(PublicKey(pub), NativeSigner(copy))
            } else {
                val priv = Ed25519PrivateKeyParameters(copy, 0)
                val pub = priv.generatePublicKey().encoded
                KeyPair(PublicKey(pub), BcSigner(priv))
            }
        }

        /**
         * Generates a new random Ed25519 key pair.
         */
        fun generate(): KeyPair {
            return if (Native.isInitialized()) {
                val secret = Native.generateKeyPair() // returns 32-byte secret
                fromSecret(secret)
            } else {
                val keyPairGenerator = Ed25519KeyPairGenerator()
                keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))

                val kp = keyPairGenerator.generateKeyPair()
                val priv = kp.private as Ed25519PrivateKeyParameters
                val pub = (kp.public as Ed25519PublicKeyParameters).encoded

                // Use BC signer directly (no duplicate allocation)
                KeyPair(PublicKey(pub), BcSigner(priv))
            }
        }

        /**
         * Creates a KeyPair from a private key hex string.
         */
        fun fromSecretHex(secretHex: String): KeyPair {
            val cleanHex = secretHex.removePrefix("0x")
            require(cleanHex.length == 64) { "Private key hex must be 64 characters" }
            val secretBytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return fromSecret(secretBytes)
        }

        /**
         * Extends signature data with signature ID.
         */
        private fun extendSignatureWithId(data: ByteArray, signatureId: Int): ByteArray {
            val idBytes = byteArrayOf(
                (signatureId shr 24).toByte(),
                (signatureId shr 16).toByte(),
                (signatureId shr 8).toByte(),
                signatureId.toByte()
            )
            return data + idBytes
        }
    }
}
