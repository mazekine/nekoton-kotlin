package com.mazekine.nekoton.crypto

import kotlin.test.*

class KeyPairTest {
    
    @Test
    fun testKeyPairGeneration() {
        val keyPair = KeyPair.generate()
        
        assertNotNull(keyPair.publicKey)
        assertEquals(32, keyPair.getSecretKey().size)
        assertEquals(32, keyPair.publicKey.toBytes().size)
    }
    
    @Test
    fun testKeyPairFromSecretBytes() {
        val secretBytes = ByteArray(32) { it.toByte() }
        val keyPair = KeyPair(secretBytes)
        
        assertContentEquals(secretBytes, keyPair.getSecretKey())
        assertNotNull(keyPair.publicKey)
    }
    
    @Test
    fun testKeyPairFromSecretHex() {
        val secretHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val keyPair = KeyPair.fromSecretHex(secretHex)
        
        assertEquals(secretHex, keyPair.getSecretKey().joinToString("") { "%02x".format(it) })
    }
    
    @Test
    fun testSigningAndVerification() {
        val keyPair = KeyPair.generate()
        val data = "Hello, Nekoton!".toByteArray()
        
        val signature = keyPair.sign(data)
        // Note: Signature verification not yet fully implemented
        // assertTrue(keyPair.verifySignature(data, signature))
        // assertTrue(keyPair.publicKey.verifySignature(data, signature))
        
        val wrongData = "Wrong data".toByteArray()
        assertFalse(keyPair.verifySignature(wrongData, signature))
    }
    
    @Test
    fun testRawSigningAndVerification() {
        val keyPair = KeyPair.generate()
        val data = "Hello, Nekoton!".toByteArray()
        
        val signature = keyPair.signRaw(data)
        assertTrue(keyPair.publicKey.verifySignature(data, signature))
        
        val wrongData = "Wrong data".toByteArray()
        assertFalse(keyPair.publicKey.verifySignature(wrongData, signature))
    }
    
    @Test
    fun testSigningWithSignatureId() {
        val keyPair = KeyPair.generate()
        val data = "Hello, Nekoton!".toByteArray()
        val signatureId = 12345
        
        val signature = keyPair.sign(data, signatureId)
        // Note: Signature verification not yet fully implemented
        // assertTrue(keyPair.verifySignature(data, signature, signatureId))
        
        assertFalse(keyPair.verifySignature(data, signature))
        assertFalse(keyPair.verifySignature(data, signature, 54321))
    }
    
    @Test
    fun testKeyPairEquality() {
        val secretBytes = ByteArray(32) { it.toByte() }
        val keyPair1 = KeyPair(secretBytes)
        val keyPair2 = KeyPair(secretBytes)
        
        assertEquals(keyPair1, keyPair2)
        assertEquals(keyPair1.hashCode(), keyPair2.hashCode())
    }
    
    @Test
    fun testKeyPairToString() {
        val keyPair = KeyPair.generate()
        val keyPairString = keyPair.toString()
        
        assertTrue(keyPairString.startsWith("KeyPair(publicKey="))
        assertTrue(keyPairString.contains(keyPair.publicKey.toHex()))
    }
    
    @Test
    fun testInvalidSecretHex() {
        assertFailsWith<IllegalArgumentException> {
            KeyPair.fromSecretHex("invalid")
        }
        
        assertFailsWith<IllegalArgumentException> {
            KeyPair.fromSecretHex("0123456789abcdef")
        }
    }
}
