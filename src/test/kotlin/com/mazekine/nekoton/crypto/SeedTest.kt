package com.mazekine.nekoton.crypto

import kotlin.test.*

class SeedTest {
    
    @Test
    fun testBip39SeedGeneration() {
        val seed = Bip39Seed.generate()
        assertEquals(12, seed.wordCount)
    }
    
    @Test
    fun testBip39SeedFromPhrase() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = Bip39Seed(phrase)
        
        assertEquals(12, seed.wordCount)
        assertTrue(seed.toString().contains(phrase))
    }
    
    @Test
    fun testBip39SeedKeyDerivation() {
        val seed = Bip39Seed.generate()
        val keyPair = seed.derive("m/44'/396'/0'/0/0")
        
        assertNotNull(keyPair)
        assertNotNull(keyPair.publicKey)
        assertEquals(32, keyPair.getSecretKey().size)
    }
    
    @Test
    fun testBip39SeedInvalidPhrase() {
        assertFailsWith<IllegalArgumentException> {
            Bip39Seed("invalid phrase with wrong word count")
        }
    }
    
    @Test
    fun testLegacySeedGeneration() {
        val seed = LegacySeed.generate()
        
        assertEquals(24, seed.wordCount)
    }
    
    @Test
    fun testLegacySeedFromPhrase() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = LegacySeed(phrase)
        
        assertEquals(24, seed.wordCount)
    }
    
    @Test
    fun testLegacySeedKeyDerivation() {
        val seed = LegacySeed.generate()
        val keyPair = seed.derive()
        
        assertNotNull(keyPair)
        assertNotNull(keyPair.publicKey)
        assertEquals(32, keyPair.getSecretKey().size)
    }
    
    @Test
    fun testSeedEquality() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed1 = Bip39Seed(phrase)
        val seed2 = Bip39Seed(phrase)
        
        assertEquals(seed1, seed2)
        assertEquals(seed1.hashCode(), seed2.hashCode())
    }
    
    @Test
    fun testSeedToString() {
        val seed = Bip39Seed.generate()
        val seedString = seed.toString()
        
        assertTrue(seedString.startsWith("Bip39Seed("))
    }
    
    @Test
    fun testDeterministicKeyDerivation() {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed1 = Bip39Seed(phrase)
        val seed2 = Bip39Seed(phrase)
        
        val keyPair1 = seed1.derive("m/44'/396'/0'/0/0")
        val keyPair2 = seed2.derive("m/44'/396'/0'/0/0")
        
        assertEquals(keyPair1.publicKey, keyPair2.publicKey)
        assertContentEquals(keyPair1.getSecretKey(), keyPair2.getSecretKey())
    }
}
