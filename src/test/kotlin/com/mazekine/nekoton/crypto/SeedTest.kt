package com.mazekine.nekoton.crypto

import kotlin.test.*
import java.security.MessageDigest

class SeedTest {

    // ----------------- helpers -----------------

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    // ----------------- Bip39Seed: construction -----------------

    @Test
    fun bip39_fromPhrase_has12Words_andEchoesInToString() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = Bip39Seed(phrase)
        assertEquals(12, seed.wordCount)
        assertTrue(seed.toString().startsWith("Bip39Seed("))
        assertTrue(seed.toString().contains(phrase))
    }

    @Test
    fun bip39_generate_has12Words() {
        val seed = Bip39Seed.generate()
        assertEquals(12, seed.wordCount)
    }

    @Test
    fun bip39_invalidWordCount_throws() {
        assertFailsWith<IllegalArgumentException> {
            Bip39Seed("invalid phrase with wrong word count")
        }
    }

    @Test
    fun bip39_invalidWord_throws() {
        val bad =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon zzzz"
        assertFailsWith<IllegalArgumentException> { Bip39Seed(bad) }
    }

    // ----------------- Bip39Seed: derivation (SLIP-0010/ed25519) -----------------

    @Test
    fun bip39_derive_returnsKeyPair_andSecretIs32Bytes() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = Bip39Seed(phrase)

        val kp = seed.derive("m/44'/396'/0'/0/0")
        assertNotNull(kp.publicKey)
        assertEquals(32, kp.getSecretKey().size)
    }

    @Test
    fun bip39_derivation_isDeterministic_forSamePhraseAndPath() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val s1 = Bip39Seed(phrase)
        val s2 = Bip39Seed(phrase)

        val k1 = s1.derive("m/44'/396'/0'/0/0")
        val k2 = s2.derive("m/44'/396'/0'/0/0")

        assertEquals(k1.publicKey, k2.publicKey)
        assertContentEquals(k1.getSecretKey(), k2.getSecretKey())
    }

    @Test
    fun slip10_autoHardens_nonHardenedSegments_inCompatMode() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // 1) BIP-39 seed (64 bytes)
        val seed64 = Bip39Seed.bip39Seed(phrase) // internal helper

        // 2) Derive with compat (auto-harden) vs strict (all apostrophes)
        val compat = Bip39Seed.slip10Ed25519Derive(seed64, "m/44'/396'/0'/0/0", strict = false)
        val strict = Bip39Seed.slip10Ed25519Derive(seed64, "m/44'/396'/0'/0'/0'", strict = true)

        assertContentEquals(strict, compat, "compat path must equal fully hardened strict path")
    }

    @Test
    fun slip10_strictMode_rejects_nonHardenedSegments() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed64 = Bip39Seed.bip39Seed(phrase)

        assertFailsWith<IllegalArgumentException> {
            Bip39Seed.slip10Ed25519Derive(seed64, "m/44'/396'/0'/0/0", strict = true)
        }
    }

    @Test
    fun entropyToMnemonic_rejectsWrongEntropySize() {
        // 12 words require exactly 128-bit entropy
        val wrong = ByteArray(20) { it.toByte() } // 160 bits
        assertFailsWith<IllegalArgumentException> {
            Bip39Seed.entropyToMnemonic(wrong, Bip39Seed.WORD_COUNT)
        }
    }

    @Test
    fun pathForAccount_formatsCorrectly() {
        assertEquals("m/44'/396'/0'/0/5", Bip39Seed.pathForAccount(5))
    }

    // ----------------- LegacySeed -----------------

    @Test
    fun legacy_generate_has24Words() {
        val seed = LegacySeed.generate()
        assertEquals(24, seed.wordCount)
    }

    @Test
    fun legacy_fromPhrase_has24Words_andToStringMentionsType() {
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = LegacySeed(phrase)
        assertEquals(24, seed.wordCount)
        assertTrue(seed.toString().startsWith("LegacySeed("))
    }

    @Test
    fun legacy_derive_producesKeyPair_andSecretIs32Bytes() {
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = LegacySeed(phrase)
        val kp = seed.derive()
        assertNotNull(kp.publicKey)
        assertEquals(32, kp.getSecretKey().size)
    }

    @Test
    fun legacy_derivation_isDeterministic_forSamePhrase() {
        val phrase =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val s1 = LegacySeed(phrase)
        val s2 = LegacySeed(phrase)

        val k1 = s1.derive()
        val k2 = s2.derive()

        assertEquals(k1.publicKey, k2.publicKey)
        assertContentEquals(k1.getSecretKey(), k2.getSecretKey())
    }
}
