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

/**
 * Represents an Ed25519 key pair used for cryptographic operations in the TON/Everscale network.
 * 
 * This class provides functionality for key pair generation, signing operations,
 * and signature verification.
 * 
 * @property privateKey The Ed25519 private key parameters
 * @property publicKey The corresponding public key
 */
@Serializable
data class KeyPair(
    val publicKey: PublicKey
) {
    @kotlinx.serialization.Transient
    private var privateKey: Ed25519PrivateKeyParameters = Ed25519PrivateKeyParameters(ByteArray(32), 0)

    /**
     * Creates a KeyPair from a private key byte array.
     * 
     * @param secretBytes The 32-byte private key
     */
    constructor(secretBytes: ByteArray) : this(
        if (Native.isInitialized()) {
            val publicBytes = Native.publicKeyFromSecret(secretBytes)
            PublicKey(publicBytes)
        } else {
            PublicKey(Ed25519PrivateKeyParameters(secretBytes, 0).generatePublicKey().encoded)
        }
    ) {
        this.privateKey = Ed25519PrivateKeyParameters(secretBytes, 0)
    }

    /**
     * Internal constructor for generated key pairs.
     */
    internal constructor(privateKey: Ed25519PrivateKeyParameters, publicKey: PublicKey) : this(publicKey) {
        this.privateKey = privateKey
    }

    /**
     * Gets the private key bytes.
     * 
     * @return The 32-byte private key
     */
    fun getSecretKey(): ByteArray = privateKey.encoded

    /**
     * Signs data with the private key.
     * 
     * @param data The data to sign
     * @param signatureId Optional signature ID for extended signing
     * @return The signature
     */
    fun sign(data: ByteArray, signatureId: Int? = null): Signature {
        return if (Native.isInitialized()) {
            val sigId = signatureId?.toLong() ?: 0L
            val signatureBytes = Native.signData(privateKey.encoded, data, sigId)
            Signature(signatureBytes)
        } else {
            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            
            // Hash the data first (similar to Python implementation)
            val hashedData = MessageDigest.getInstance("SHA-256").digest(data)
            
            val dataToSign = if (signatureId != null) {
                extendSignatureWithId(hashedData, signatureId)
            } else {
                hashedData
            }
            
            signer.update(dataToSign, 0, dataToSign.size)
            val signatureBytes = signer.generateSignature()
            
            Signature(signatureBytes)
        }
    }

    /**
     * Signs raw data without hashing.
     * 
     * @param data The raw data to sign
     * @param signatureId Optional signature ID for extended signing
     * @return The signature
     */
    fun signRaw(data: ByteArray, signatureId: Int? = null): Signature {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        
        val dataToSign = if (signatureId != null) {
            extendSignatureWithId(data, signatureId)
        } else {
            data
        }
        
        signer.update(dataToSign, 0, dataToSign.size)
        val signatureBytes = signer.generateSignature()
        
        return Signature(signatureBytes)
    }

    /**
     * Verifies a signature against the provided data using this key pair's public key.
     * 
     * @param data The data that was signed
     * @param signature The signature to verify
     * @param signatureId Optional signature ID for extended verification
     * @return true if the signature is valid
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

    override fun hashCode(): Int {
        return publicKey.hashCode()
    }

    override fun toString(): String {
        return "KeyPair(publicKey=${publicKey.toHex()})"
    }

    companion object {
        /**
         * Generates a new random Ed25519 key pair.
         * 
         * @return A new KeyPair instance
         */
        fun generate(): KeyPair {
            return if (Native.isInitialized()) {
                val secretBytes = Native.generateKeyPair()
                KeyPair(secretBytes)
            } else {
                val keyPairGenerator = Ed25519KeyPairGenerator()
                keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))
                
                val keyPair = keyPairGenerator.generateKeyPair()
                val privateKey = keyPair.private as Ed25519PrivateKeyParameters
                val publicKeyBytes = (keyPair.public as Ed25519PublicKeyParameters).encoded
                
                KeyPair(privateKey, PublicKey(publicKeyBytes))
            }
        }

        /**
         * Creates a KeyPair from a private key hex string.
         * 
         * @param secretHex The private key as a hex string
         * @return KeyPair instance
         */
        fun fromSecretHex(secretHex: String): KeyPair {
            val cleanHex = secretHex.removePrefix("0x")
            require(cleanHex.length == 64) { "Private key hex must be 64 characters" }
            
            val secretBytes = cleanHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
            
            return KeyPair(secretBytes)
        }

        /**
         * Extends signature data with signature ID.
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
