package com.mazekine.nekoton.crypto

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.crypto.Bip39Seed.Companion.entropyToMnemonic
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Base class for mnemonic seed phrases used for key derivation.
 *
 * Public API preserved.
 */
@Serializable
abstract class Seed(
    protected val words: List<String>
) {
    val wordCount: Int get() = words.size

    override fun toString(): String = words.joinToString(" ")

    /**
     * Derives a key pair from this seed.
     *
     * @param path Optional derivation path (for BIP39)
     */
    abstract fun derive(path: String? = null): KeyPair
}

/* ----------------------------- LEGACY (24 words) ----------------------------- */
/**
 * Legacy seed implementation for TON wallets.
 *
 * NOTE: Legacy phrase generation below is NOT a published standard. We keep it
 * deterministic and secure enough for dev/test, but PROD should prefer the
 * Native path (or supply real legacy generation rules).
 */
@Serializable
@ConsistentCopyVisibility
data class LegacySeed private constructor(private val seedWords: List<String>) : Seed(seedWords) {

    init {
        require(seedWords.size == WORD_COUNT) { "Legacy seed must have exactly $WORD_COUNT words" }
    }

    constructor(phrase: String) : this(splitWords(phrase, WORD_COUNT))

    override fun derive(path: String?): KeyPair {
        val phrase = seedWords.joinToString(" ")

        // HMAC-SHA512 keyed by phrase
        val mac = Mac.getInstance(HMAC_SHA512)
        val secretKey = SecretKeySpec(phrase.toByteArray(StandardCharsets.UTF_8), HMAC_SHA512)
        mac.init(secretKey)
        val password = mac.doFinal() // 64 bytes

        // PBKDF2-HMAC-SHA512 with a fixed salt
        val derivedKey = pbkdf2Sha512(
            password,
            SALT_LEGACY.toByteArray(StandardCharsets.UTF_8),
            PBKDF_ITERATIONS_LEGACY,
            64
        )

        // First 32 bytes -> Ed25519 secret
        val privateKeyBytes = derivedKey.copyOf(32)
        // Wipe buffers we no longer need
        zeroize(password); zeroize(derivedKey)

        return KeyPair.fromSecret(privateKeyBytes)
    }

    override fun toString(): String = "LegacySeed('${super.toString()}')"

    companion object {
        const val WORD_COUNT = 24

        private const val PBKDF_ITERATIONS_LEGACY = 100_000
        private const val SALT_LEGACY = "TON default seed"
        private val RNG: SecureRandom by lazy(LazyThreadSafetyMode.PUBLICATION) { SecureRandom() }

        /**
         * Generates a new random legacy seed.
         * Uses BIP-39 wordlist for words but **not** BIP-39 derivation.
         * If Native is present in your environment and exposes a legacy generator,
         * prefer that instead of this fallback.
         */
        fun generate(): LegacySeed {
            val entropy = ByteArray(32).also { RNG.nextBytes(it) } // 256 bits
            // Use BIP-39 encoding for 24 words for stable, checksumed output
            val words = entropyToMnemonic(entropy, WORD_COUNT)
            return LegacySeed(words)
        }
    }
}

/* ------------------------------ BIP-39 (12 words) ------------------------------ */
/**
 * BIP39 seed implementation for hierarchical deterministic wallets.
 *
 * Follows BIP-39 for mnemonic generation/seed derivation on the fallback path.
 * The Native path remains the preferred source of truth in production.
 */
@Serializable
@ConsistentCopyVisibility
data class Bip39Seed private constructor(private val seedWords: List<String>) : Seed(seedWords) {

    init {
        require(seedWords.size == WORD_COUNT) { "BIP39 seed must have exactly $WORD_COUNT words" }
        // Validate all words are in the official list (defensive)
        seedWords.forEach {
            require(BIP39_WORDLIST_BINARY_SEARCH.contains(it)) { "Word '$it' not in BIP39 wordlist" }
        }
    }

    constructor(phrase: String) : this(splitWords(phrase, WORD_COUNT))

    override fun derive(path: String?): KeyPair {
        val derivationPath = path ?: DEFAULT_PATH
        val phrase = seedWords.joinToString(" ")

        return if (Native.isInitialized()) {
            val secretBytes = Native.deriveBip39KeyPair(phrase, derivationPath)
            KeyPair.fromSecret(secretBytes)
        } else {
            // 1) BIP-39 seed (64 bytes)
            val seed = bip39Seed(phrase)

            // 2) SLIP-0010 ed25519 derivation along the path
            val derivedKey = slip10Ed25519Derive(seed, derivationPath, strict = STRICT_SLIP10_ED25519)

            zeroize(seed)
            KeyPair.fromSecret(derivedKey)
        }
    }


    override fun toString(): String = "Bip39Seed('${super.toString()}')"

    companion object {
        const val WORD_COUNT = 12
        private const val DEFAULT_PATH = "m/44'/396'/0'/0/0"
        private val RNG: SecureRandom by lazy(LazyThreadSafetyMode.PUBLICATION) { SecureRandom() }

        fun generate(): Bip39Seed {
            return if (Native.isInitialized()) {
                Bip39Seed(Native.generateBip39Mnemonic(WORD_COUNT.toLong()))
            } else {
                val entropy = ByteArray(16).also { RNG.nextBytes(it) } // 128 bits
                Bip39Seed(entropyToMnemonic(entropy, WORD_COUNT))
            }
        }

        /** Standard path for an account index. */
        fun pathForAccount(accountId: Int): String = "m/44'/396'/0'/0/$accountId"

        /* ---------- BIP-39 helpers (standards-compliant) ---------- */

        /** Generate BIP-39 mnemonic from entropy (12 or 24 words). */
        internal fun entropyToMnemonic(entropy: ByteArray, targetWordCount: Int): List<String> {
            val (bits, checksumBits) = when (targetWordCount) {
                12 -> 128 to 4   // 128-bit entropy -> 4 checksum bits -> 12 words
                24 -> 256 to 8   // 256-bit entropy -> 8 checksum bits -> 24 words
                else -> throw IllegalArgumentException("Unsupported word count: $targetWordCount")
            }
            require(entropy.size * 8 == bits) { "Entropy must be $bits bits for $targetWordCount words" }

            // Compute checksum = first ENT/32 bits of SHA-256(entropy)
            val hash = sha256(entropy)
            val cs = (bits / 32)
            val totalBits = bits + cs

            // Build bitstream: entropy || checksum
            val stream = BooleanArray(totalBits)
            // Entropy bits
            for (i in 0 until bits) {
                val byte = entropy[i ushr 3].toInt() and 0xFF
                stream[i] = ((byte shr (7 - (i and 7))) and 1) == 1
            }
            // Checksum bits
            for (i in 0 until cs) {
                val bit = (hash[0].toInt() shr (7 - i)) and 1
                stream[bits + i] = bit == 1
            }

            // Split into 11-bit indices
            val words = ArrayList<String>(targetWordCount)
            var accum = 0
            var accBits = 0
            var idx = 0
            while (idx < totalBits) {
                accum = (accum shl 1) or (if (stream[idx]) 1 else 0)
                accBits++
                if (accBits == 11) {
                    words.add(BIP39_WORDLIST[accum])
                    accum = 0
                    accBits = 0
                }
                idx++
            }
            return words
        }

        /** Compute BIP-39 seed = PBKDF2(mnemonic, "mnemonic"+passphrase, 2048, 64). */
        internal fun bip39Seed(mnemonic: String, passphrase: String = ""): ByteArray {
            val m = nfkd(mnemonic)
            val p = nfkd(passphrase)
            val salt = ("mnemonic" + p).toByteArray(StandardCharsets.UTF_8)
            val seed = pbkdf2Sha512(m.toByteArray(StandardCharsets.UTF_8), salt, 2048, 64)
            zeroize(salt)
            return seed
        }

        /**
         * Simplified path derivation placeholder.
         * DO NOT USE in production where Native is unavailable. Replace with SLIP-0010/ed25519 derivation.
         */
        internal fun deriveKeyFromPathSimplified(seed: ByteArray, path: String): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(seed)
            md.update(path.toByteArray(StandardCharsets.UTF_8))
            return md.digest().copyOf(32)
        }

        private const val HARDENED_OFFSET = 0x80000000.toInt()
        /**
         * If true, throw when a non-hardened index appears (strict SLIP-0010).
         * If false (default), auto-harden any non-hardened segment to preserve compatibility
         * with paths like m/44'/396'/0'/0/0 commonly used in TON/Everscale wallets.
         */
        private const val STRICT_SLIP10_ED25519 = false

        /** SLIP-0010 master key for ed25519: HMAC-SHA512("ed25519 seed", seed) -> (k, c). */
        internal fun slip10Ed25519Master(seed: ByteArray): Pair<ByteArray, ByteArray> {
            val key = "ed25519 seed".toByteArray(StandardCharsets.UTF_8)
            val I = hmacSha512(key, seed) // 64 bytes
            val k = I.copyOfRange(0, 32)
            val c = I.copyOfRange(32, 64)
            zeroize(I)
            return k to c
        }

        /** Child derivation (private/hardened only) for ed25519. */
        internal fun slip10Ed25519CkdPriv(
            parentKey: ByteArray,
            parentChainCode: ByteArray,
            index: Int,
            strict: Boolean
        ): Pair<ByteArray, ByteArray> {
            // Harden if needed (SLIP-0010 defines ONLY hardened for ed25519)
            val idx = if ((index and HARDENED_OFFSET) != 0) index
            else if (strict) throw IllegalArgumentException("Non-hardened index not supported for ed25519 SLIP-0010: $index")
            else index or HARDENED_OFFSET

            // data = 0x00 || parentKey(32) || ser32(idx)
            val data = ByteArray(1 + 32 + 4)
            data[0] = 0x00
            System.arraycopy(parentKey, 0, data, 1, 32)
            writeIntBE(idx, data, 33)

            val I = hmacSha512(parentChainCode, data) // 64 bytes
            zeroize(data)

            val childKey = I.copyOfRange(0, 32)
            val childCC  = I.copyOfRange(32, 64)
            zeroize(I)
            return childKey to childCC
        }

        /** Derive a 32-byte ed25519 private key along a BIP-32 style path using SLIP-0010. */
        internal fun slip10Ed25519Derive(seed: ByteArray, path: String, strict: Boolean = false): ByteArray {
            val (mKey, mCC) = slip10Ed25519Master(seed)
            var key = mKey
            var cc  = mCC

            val indexes = parsePathEd25519(path)
            for (rawIndex in indexes) {
                val (k, c) = slip10Ed25519CkdPriv(key, cc, rawIndex, strict)
                zeroize(key); zeroize(cc)
                key = k; cc = c
            }
            // final private key = key (32 bytes)
            return key
        }

        /** Parse "m/44'/396'/0'/0/0" into int indexes (hardened if "'"/"h"/"H" suffix). */
        private fun parsePathEd25519(path: String): IntArray {
            val p = path.trim()
            require(p.isNotEmpty()) { "Path must not be empty" }

            val parts = p.split("/")
            require(parts.isNotEmpty()) { "Invalid path: $path" }
            require(parts[0] == "m" || parts[0] == "M") { "Path must start with 'm': $path" }

            if (parts.size == 1) return IntArray(0)

            val out = IntArray(parts.size - 1)
            var i = 1
            while (i < parts.size) {
                val seg = parts[i]
                require(seg.isNotEmpty()) { "Empty path segment at position $i" }

                val hardened = seg.endsWith("'") || seg.endsWith("h") || seg.endsWith("H")
                val numPart  = if (hardened) seg.dropLast(1) else seg

                // Allow leading '+'; disallow negative
                require(!numPart.startsWith("-")) { "Negative index not allowed: $seg" }
                val n = numPart.toLongOrNull() ?: throw IllegalArgumentException("Invalid index: $seg")
                require(n in 0..0x7FFF_FFFFL) { "Index out of range: $seg" }

                var idx = n.toInt()
                if (hardened) idx = idx or HARDENED_OFFSET
                out[i - 1] = idx
                i++
            }
            return out
        }

        /** Write big-endian int into buffer at offset. */
        private fun writeIntBE(value: Int, dest: ByteArray, offset: Int) {
            dest[offset]     = (value ushr 24).toByte()
            dest[offset + 1] = (value ushr 16).toByte()
            dest[offset + 2] = (value ushr  8).toByte()
            dest[offset + 3] =  value.toByte()
        }
    }
}

/* ------------------------------ Utilities (shared) ------------------------------ */

private const val HMAC_SHA512 = "HmacSHA512"

private fun splitWords(phrase: String, expectedCount: Int): List<String> {
    // Faster whitespace splitter than Regex for common ASCII cases
    val parts = phrase.trim().split(Regex("\\s+"))
    require(parts.size == expectedCount) { "Expected $expectedCount words, got ${parts.size}" }
    return parts
}

/** PBKDF2(HMAC-SHA512) via JCA; falls back to a local HMAC loop if unavailable. */
private fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    try {
        val spec = PBEKeySpec(
            // password encoded as chars for the API; keep binary by mapping each byte to char
            password.map { (it.toInt() and 0xFF).toChar() }.toCharArray(),
            salt, iterations, keyLength * 8
        )
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val key = skf.generateSecret(spec).encoded
        // wipe intermediate char array
        java.util.Arrays.fill(spec.password, '\u0000')
        return key
    } catch (_: Exception) {
        // Fallback to manual HMAC loop (your original approach)
        val mac = Mac.getInstance(HMAC_SHA512).apply { init(SecretKeySpec(password, HMAC_SHA512)) }

        val result = ByteArray(keyLength)
        val blockLen = mac.macLength
        val blocks = (keyLength + blockLen - 1) / blockLen

        var outOffset = 0
        for (i in 1..blocks) {
            // U1 = PRF(P, S || INT(i))
            mac.update(salt)
            mac.update(intToBytesBE(i))
            var u = mac.doFinal()
            val block = u.copyOf() // T = U1
            // U2..Uc
            for (j in 2..iterations) {
                u = mac.doFinal(u) // PRF(P, U_{j-1})
                for (k in u.indices) block[k] = (block[k].toInt() xor u[k].toInt()).toByte()
            }
            val len = min(blockLen, keyLength - outOffset)
            System.arraycopy(block, 0, result, outOffset, len)
            outOffset += len
            zeroize(block); zeroize(u)
        }
        return result
    }
}

/** big-endian INT(i) */
private fun intToBytesBE(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte()
)

private fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

/** NFC/NFKD as mandated by BIP-39. */
private fun nfkd(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD)

/** Best-effort zeroization; not guaranteed by JVM, but helps. */
private fun zeroize(a: ByteArray) {
    for (i in a.indices) a[i] = 0
}

/** HMAC-SHA512(key, data) → 64 bytes. */
private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(key, "HmacSHA512"))
    return mac.doFinal(data)
}

/* ------------------------------ BIP-39 wordlist ------------------------------ */

/**
 * Complete BIP39 wordlist loaded from resource file (must contain exactly 2048 lines).
 */
private val BIP39_WORDLIST: List<String> by lazy {
    val stream = object {}.javaClass.getResourceAsStream("/bip39-wordlist.txt")
        ?: throw IllegalStateException("Could not load BIP39 wordlist from resources")
    stream.bufferedReader().use { r -> r.readLines().filter { it.isNotBlank() } }.also { wl ->
        require(wl.size == 2048) { "BIP39 wordlist must contain exactly 2048 words, found ${wl.size}" }
    }
}

/**
 * Binary-searchable view of the wordlist for quick validation without allocations.
 */
private object BIP39_WORDLIST_BINARY_SEARCH {
    fun contains(word: String): Boolean {
        var lo = 0
        var hi = BIP39_WORDLIST.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = BIP39_WORDLIST[mid].compareTo(word)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }
}
