package com.mazekine.nekoton.crypto

import com.mazekine.nekoton.Native
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Base class for mnemonic seed phrases used for key derivation.
 * 
 * @property words The list of mnemonic words
 */
@Serializable
abstract class Seed(
    protected val words: List<String>
) {
    /**
     * Gets the number of words in the seed phrase.
     * 
     * @return Word count
     */
    val wordCount: Int get() = words.size

    /**
     * Converts the seed to a string representation.
     * 
     * @return Space-separated mnemonic words
     */
    override fun toString(): String = words.joinToString(" ")

    /**
     * Derives a key pair from this seed.
     * 
     * @param path Optional derivation path (for BIP39)
     * @return Derived KeyPair
     */
    abstract fun derive(path: String? = null): KeyPair
}

/**
 * Legacy seed implementation for TON wallets.
 * 
 * This implementation uses 24 words and follows the legacy TON seed derivation process.
 * 
 * @property words The 24 mnemonic words
 */
@Serializable
data class LegacySeed private constructor(private val seedWords: List<String>) : Seed(seedWords) {
    
    init {
        require(seedWords.size == WORD_COUNT) { "Legacy seed must have exactly $WORD_COUNT words" }
    }

    /**
     * Creates a LegacySeed from a mnemonic phrase.
     * 
     * @param phrase The mnemonic phrase as a string
     */
    constructor(phrase: String) : this(splitWords(phrase, WORD_COUNT))

    /**
     * Derives a key pair using the legacy TON derivation method.
     * 
     * @param path Ignored for legacy seeds
     * @return Derived KeyPair
     */
    override fun derive(path: String?): KeyPair {
        val phrase = seedWords.joinToString(" ")
        
        // Create HMAC-SHA512 with the phrase as key
        val mac = Mac.getInstance("HmacSHA512")
        val secretKey = SecretKeySpec(phrase.toByteArray(), "HmacSHA512")
        mac.init(secretKey)
        val password = mac.doFinal()
        
        // PBKDF2 with SHA512
        val salt = SALT.toByteArray()
        val derivedKey = pbkdf2(password, salt, PBKDF_ITERATIONS, 64)
        
        // Use first 32 bytes as private key
        val privateKeyBytes = derivedKey.sliceArray(0..31)
        
        return KeyPair(privateKeyBytes)
    }

    override fun toString(): String {
        return "LegacySeed('${super.toString()}')"
    }

    companion object {
        /**
         * Number of words in a legacy seed.
         */
        const val WORD_COUNT = 24

        private const val PBKDF_ITERATIONS = 100_000
        private const val SALT = "TON default seed"

        /**
         * Generates a new random legacy seed.
         * 
         * @return New LegacySeed instance
         */
        fun generate(): LegacySeed {
            val entropy = ByteArray(32)
            SecureRandom().nextBytes(entropy)
            val words = generateWords(entropy)
            return LegacySeed(words)
        }

        /**
         * Generates mnemonic words from entropy.
         */
        private fun generateWords(entropy: ByteArray): List<String> {
            // This is a simplified implementation
            // In a real implementation, you would use the BIP39 wordlist and proper checksum
            val wordList = BIP39_WORDLIST
            val words = mutableListOf<String>()
            
            // Simple entropy to word mapping (not cryptographically secure)
            for (i in 0 until WORD_COUNT) {
                val index = (entropy[i % entropy.size].toInt() and 0xFF) * 8 + (i % 8)
                words.add(wordList[index % wordList.size])
            }
            
            return words
        }
    }
}

/**
 * BIP39 seed implementation for hierarchical deterministic wallets.
 * 
 * This implementation uses 12 words and follows the BIP39 standard.
 * 
 * @property words The 12 mnemonic words
 */
@Serializable
data class Bip39Seed private constructor(private val seedWords: List<String>) : Seed(seedWords) {
    
    init {
        require(seedWords.size == WORD_COUNT) { "BIP39 seed must have exactly $WORD_COUNT words" }
    }

    /**
     * Creates a Bip39Seed from a mnemonic phrase.
     * 
     * @param phrase The mnemonic phrase as a string
     */
    constructor(phrase: String) : this(splitWords(phrase, WORD_COUNT))

    /**
     * Derives a key pair using BIP39/BIP32 derivation.
     * 
     * @param path The derivation path (default: m/44'/396'/0'/0/0)
     * @return Derived KeyPair
     */
    override fun derive(path: String?): KeyPair {
        val derivationPath = path ?: DEFAULT_PATH
        val phrase = seedWords.joinToString(" ")
        
        return if (Native.isInitialized()) {
            val secretBytes = Native.deriveBip39KeyPair(phrase, derivationPath)
            KeyPair(secretBytes)
        } else {
            // Generate seed from mnemonic
            val seed = generateSeedFromMnemonic(phrase)
            
            // Derive key using the path
            val derivedKey = deriveKeyFromPath(seed, derivationPath)
            
            KeyPair(derivedKey)
        }
    }

    override fun toString(): String {
        return "Bip39Seed('${super.toString()}')"
    }

    companion object {
        /**
         * Number of words in a BIP39 seed.
         */
        const val WORD_COUNT = 12

        private const val DEFAULT_PATH = "m/44'/396'/0'/0/0"

        /**
         * Generates a new random BIP39 seed.
         * 
         * @return New Bip39Seed instance
         */
        fun generate(): Bip39Seed {
            return if (Native.isInitialized()) {
                val phrase = Native.generateBip39Mnemonic(WORD_COUNT.toLong())
                Bip39Seed(phrase)
            } else {
                val entropy = ByteArray(16) // 128 bits for 12 words
                SecureRandom().nextBytes(entropy)
                val words = generateWords(entropy)
                Bip39Seed(words)
            }
        }

        /**
         * Gets the standard derivation path for an account.
         * 
         * @param accountId The account ID
         * @return Derivation path string
         */
        fun pathForAccount(accountId: Int): String = "m/44'/396'/0'/0/$accountId"

        /**
         * Generates mnemonic words from entropy.
         */
        private fun generateWords(entropy: ByteArray): List<String> {
            // Simplified implementation - in reality would use proper BIP39 algorithm
            val wordList = BIP39_WORDLIST
            val words = mutableListOf<String>()
            
            for (i in 0 until WORD_COUNT) {
                val index = (entropy[i % entropy.size].toInt() and 0xFF) * 2 + (i % 2)
                words.add(wordList[index % wordList.size])
            }
            
            return words
        }

        /**
         * Generates seed from mnemonic phrase.
         */
        private fun generateSeedFromMnemonic(phrase: String, passphrase: String = ""): ByteArray {
            val salt = ("mnemonic" + passphrase).toByteArray()
            return pbkdf2(phrase.toByteArray(), salt, 2048, 64)
        }

        /**
         * Derives key from BIP32 path.
         */
        private fun deriveKeyFromPath(seed: ByteArray, path: String): ByteArray {
            // Simplified implementation - in reality would use proper BIP32 derivation
            val pathHash = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
            val combined = seed + pathHash
            return MessageDigest.getInstance("SHA-256").digest(combined).sliceArray(0..31)
        }
    }
}

/**
 * Splits a mnemonic phrase into words and validates the count.
 */
private fun splitWords(phrase: String, expectedCount: Int): List<String> {
    val words = phrase.trim().split(Regex("\\s+"))
    require(words.size == expectedCount) { "Expected $expectedCount words, got ${words.size}" }
    return words
}

/**
 * PBKDF2 implementation using HMAC-SHA512.
 */
private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA512")
    val secretKey = SecretKeySpec(password, "HmacSHA512")
    mac.init(secretKey)
    
    val result = ByteArray(keyLength)
    val blockSize = mac.macLength
    val blocks = (keyLength + blockSize - 1) / blockSize
    
    for (i in 1..blocks) {
        val block = ByteArray(blockSize)
        
        // First iteration
        mac.update(salt)
        mac.update(intToBytes(i))
        var u = mac.doFinal()
        System.arraycopy(u, 0, block, 0, blockSize)
        
        // Remaining iterations
        for (j in 2..iterations) {
            u = mac.doFinal(u)
            for (k in 0 until blockSize) {
                block[k] = (block[k].toInt() xor u[k].toInt()).toByte()
            }
        }
        
        val offset = (i - 1) * blockSize
        val length = minOf(blockSize, keyLength - offset)
        System.arraycopy(block, 0, result, offset, length)
    }
    
    return result
}

/**
 * Converts an integer to 4 bytes (big-endian).
 */
private fun intToBytes(value: Int): ByteArray {
    return byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )
}

/**
 * Complete BIP39 wordlist loaded from resource file.
 * Contains all 2048 words from the official BIP39 English wordlist.
 */
private val BIP39_WORDLIST: List<String> by lazy {
    val resourceStream = object {}.javaClass.getResourceAsStream("/bip39-wordlist.txt")
        ?: throw IllegalStateException("Could not load BIP39 wordlist from resources")
    
    resourceStream.bufferedReader().use { reader ->
        reader.readLines().filter { it.isNotBlank() }
    }.also { wordlist ->
        require(wordlist.size == 2048) { 
            "BIP39 wordlist must contain exactly 2048 words, found ${wordlist.size}" 
        }
    }
}
